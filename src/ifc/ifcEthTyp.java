package ifc;

import addr.addrMac;
import addr.addrType;
import cfg.cfgAll;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.Comparator;
import pack.packHolder;
import tab.tabGen;
import tab.tabQos;
import user.userFormat;
import util.bits;
import util.counter;
import util.debugger;
import util.history;
import util.logger;
import util.notifier;
import util.state;

/**
 * ethertype multiplexer
 *
 * @author matecsaba
 */
public class ifcEthTyp implements Runnable, ifcUp {

    /**
     * forced mtu size
     */
    public int forcedMTU = 0;

    /**
     * forced bandwidth
     */
    public long forcedBW = 0;

    /**
     * forced shutdown
     */
    public boolean forcedDN = false;

    /**
     * forced mac address
     */
    public addrMac forcedMac;

    /**
     * log state change
     */
    public boolean logStateChg = false;

    /**
     * ingress qos
     */
    public tabQos qosIn;

    /**
     * egress qos
     */
    public tabQos qosOut;

    /**
     * mac security
     */
    public ifcMacSec macSec;

    /**
     * monitor session target
     */
    public ifcEthTyp monSes = null;

    /**
     * monitor buffer data
     */
    public byte[] monBufD = null;

    /**
     * monitor buffer pointer
     */
    public int monBufP;

    /**
     * snap code
     */
    public final static int snap = 0xaaaa;

    private notifier notif = new notifier();

    private int need2run;

    private state.states lastState;

    private ifcDn lower = new ifcNull();

    private ifcEthTypET defUpper;

    private tabGen<ifcEthTypET> etTyps;

    private tabGen<ifcEthTypLLC> llcTyps;

    private tabGen<ifcEthTypSNAP> snapTyps;

    private boolean promiscous;

    private counter totCntr = new counter();

    private counter cntr = new counter();

    private history hstry = new history();

    private final String name;

    private RandomAccessFile logFile = null;

    /**
     * looped packets dropped
     */
    protected static int loopDrops;

    /**
     * get history handler
     *
     * @return history handler
     */
    public history getHistory() {
        return hstry;
    }

    public counter getCounter() {
        return cntr;
    }

    /**
     * get promiscous state
     *
     * @return state
     */
    public boolean getPromisc() {
        return promiscous;
    }

    /**
     * get hardware address
     *
     * @return hw address
     */
    public addrType getHwAddr() {
        if (forcedMac != null) {
            return forcedMac;
        }
        return lower.getHwAddr();
    }

    public String toString() {
        return name;
    }

    /**
     * set worker interface
     *
     * @param parent worker interface
     */
    public void setParent(ifcDn parent) {
        lower = parent;
        lastState = lower.getState();
        propagateState();
    }

    /**
     * get state of interface
     *
     * @return state of line protocol
     */
    public state.states getState() {
        if (forcedDN) {
            return state.states.admin;
        }
        return lastState;
    }

    public void setState(state.states stat) {
        stat = state.toUsable(stat);
        if (lastState == stat) {
            return;
        }
        cntr.stateChange(stat);
        lastState = stat;
        if (forcedDN) {
            return;
        }
        propagateState();
    }

    /**
     * propagate current state;
     */
    public void propagateState() {
        state.states s = lastState;
        if (forcedDN) {
            s = state.states.admin;
        }
        if (logStateChg) {
            logger.warn("interface " + name + " change to " + state.conv2string(s));
        }
        for (int i = 0; i < etTyps.size(); i++) {
            etTyps.get(i).upper.setState(s);
        }
        for (int i = 0; i < llcTyps.size(); i++) {
            llcTyps.get(i).upper.setState(s);
        }
        for (int i = 0; i < snapTyps.size(); i++) {
            snapTyps.get(i).upper.setState(s);
        }
        if (defUpper.upper != null) {
            defUpper.upper.setState(s);
        }
    }

    /**
     * close this interface
     */
    public void closeUp() {
        lastState = state.states.close;
        qosIn = null;
        qosOut = null;
        timerUpdate();
        for (int i = 0; i < etTyps.size(); i++) {
            try {
                etTyps.get(i).upper.closeUp();
            } catch (Exception e) {
            }
        }
        for (int i = 0; i < llcTyps.size(); i++) {
            try {
                llcTyps.get(i).upper.closeUp();
            } catch (Exception e) {
            }
        }
        for (int i = 0; i < snapTyps.size(); i++) {
            try {
                snapTyps.get(i).upper.closeUp();
            } catch (Exception e) {
            }
        }
        try {
            defUpper.upper.closeUp();
        } catch (Exception e) {
        }
    }

    /**
     * update timer thread
     */
    public synchronized void timerUpdate() {
        if ((qosIn != null) || (qosOut != null) || (macSec != null)) {
            need2run |= 1;
        } else {
            need2run &= 0xfe;
        }
        if ((need2run & 2) != 0) {
            return;
        }
        if (debugger.ifcEthTypTraf) {
            logger.debug("starting");
        }
        need2run |= 2;
        new Thread(this).start();
    }

    public void run() {
        long sec = 0;
        for (;;) {
            if ((need2run & 1) == 0) {
                break;
            }
            long lst = 0;
            long tim = bits.getTime();
            if (qosOut != null) {
                for (;;) {
                    packHolder pck = qosOut.dequeuePack(tim);
                    if (pck == null) {
                        break;
                    }
                    cntr.tx(pck);
                    lower.sendPack(pck);
                }
                if (lst < qosOut.lastLeft) {
                    lst = qosOut.lastLeft;
                }
            }
            if (qosIn != null) {
                for (;;) {
                    packHolder pck = qosIn.dequeuePack(tim);
                    if (pck == null) {
                        break;
                    }
                    doRxPack(pck);
                }
                if (lst < qosIn.lastLeft) {
                    lst = qosIn.lastLeft;
                }
            }
            if ((lst < 1) || (lst > 10000)) {
                lst = 10000;
            }
            notif.misleep((int) lst);
            if ((macSec != null) && ((tim - sec) > 5000)) {
                packHolder pck = macSec.doSync();
                if (pck != null) {
                    cntr.tx(pck);
                    lower.sendPack(pck);
                }
                sec = tim;
            }
        }
        need2run &= 0xfd;
        if (debugger.ifcEthTypTraf) {
            logger.debug("stopped");
        }
    }

    /**
     * set filter criteria
     *
     * @param promisc need all packet (promiscous mode)
     */
    public void setFilter(boolean promisc) {
        promisc = defUpper.promiscous;
        for (int i = 0; i < etTyps.size(); i++) {
            promisc |= etTyps.get(i).promiscous;
        }
        for (int i = 0; i < llcTyps.size(); i++) {
            promisc |= llcTyps.get(i).promiscous;
        }
        for (int i = 0; i < snapTyps.size(); i++) {
            promisc |= snapTyps.get(i).promiscous;
        }
        if (promiscous == promisc) {
            return;
        }
        promiscous = promisc;
        lower.setFilter(promisc);
        if (debugger.ifcEthTypTraf) {
            logger.debug("set filter to " + promisc);
        }
    }

    /**
     * create new multiplexer
     *
     * @param nam name of interface
     */
    public ifcEthTyp(String nam) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("started");
        }
        name = "" + nam;
        lower = null;
        defUpper = new ifcEthTypET(null, null);
        promiscous = false;
        lastState = state.states.up;
        etTyps = new tabGen<ifcEthTypET>();
        llcTyps = new tabGen<ifcEthTypLLC>();
        snapTyps = new tabGen<ifcEthTypSNAP>();
    }

    private void doRxPack(packHolder pck) {
        int typ = pck.msbGetW(0); // ether type
        pck.ETHtype = typ;
        if (debugger.ifcEthTypTraf) {
            logger.debug("rx type=" + bits.toHexW(typ));
        }
        ifcEthTypET et = new ifcEthTypET(null, null);
        et.ethTyp = typ;
        et = etTyps.find(et);
        if (et != null) {
            et.doRxPack(pck);
            return;
        }
        if (typ > 1500) {
            defUpper.doRxPack(pck);
            return;
        }
        typ += 2;
        if (typ > pck.dataSize()) {
            cntr.drop(pck, counter.reasons.tooSmall);
            return;
        }
        pck.setDataSize(typ);
        typ = pck.msbGetW(2); // llc type
        pck.ETHtype = typ;
        if (debugger.ifcEthTypTraf) {
            logger.debug("rx llc=" + bits.toHexW(typ));
        }
        ifcEthTypLLC llc = new ifcEthTypLLC(null, null);
        llc.llcTyp = typ;
        llc = llcTyps.find(llc);
        if (llc != null) {
            llc.doRxPack(pck);
            return;
        }
        if (typ != snap) {
            defUpper.doRxPack(pck);
            return;
        }
        typ = pck.msbGetD(5) >>> 8; // organization id
        pck.ETHtype = typ;
        if (debugger.ifcEthTypTraf) {
            logger.debug("rx snap=" + bits.toHexD(typ));
        }
        ifcEthTypSNAP snp = new ifcEthTypSNAP(null, null);
        snp.snapTyp = typ;
        snp = snapTyps.find(snp);
        if (snp != null) {
            snp.doRxPack(pck);
            return;
        }
        if (typ != 0) {
            defUpper.doRxPack(pck);
            return;
        }
        pck.getSkip(8);
        typ = pck.msbGetW(0); // ether type
        pck.ETHtype = typ;
        if (debugger.ifcEthTypTraf) {
            logger.debug("rx (embedded)type=" + bits.toHexW(typ));
        }
        et = new ifcEthTypET(null, null);
        et.ethTyp = typ;
        et = etTyps.find(et);
        if (et != null) {
            et.doRxPack(pck);
        } else {
            defUpper.doRxPack(pck);
        }
    }

    /**
     * send one packet to the network
     *
     * @param pck packet to send
     */
    public void doTxPack(packHolder pck) {
        if (lastState != state.states.up) {
            cntr.drop(pck, counter.reasons.notUp);
            return;
        }
        if (forcedDN) {
            cntr.drop(pck, counter.reasons.notUp);
            return;
        }
        pck.INTsent++;
        if (pck.INTsent > 64) {
            loopDrops++;
            cntr.drop(pck, counter.reasons.tooLong);
            return;
        }
        if (logFile != null) {
            try {
                logFile.write(pck.convertToPcap(bits.getTime() + cfgAll.timeServerOffset, true));
            } catch (Exception e) {
            }
        }
        if (monBufD != null) {
            putMonBufPck(pck.convertToPcap(bits.getTime() + cfgAll.timeServerOffset, true));
        }
        if (monSes != null) {
            monSes.doTxPack(pck.copyBytes(true, true));
        }
        if (macSec != null) {
            if (macSec.doEncrypt(pck)) {
                return;
            }
        }
        if (qosOut == null) {
            cntr.tx(pck);
            lower.sendPack(pck);
            return;
        }
        qosOut.classifyPack(pck);
        qosOut.enqueuePack(pck);
        notif.wakeup();
    }

    public void recvPack(packHolder pck) {
        cntr.rx(pck);
        if (macSec != null) {
            if (macSec.doDecrypt(pck)) {
                cntr.drop(pck, counter.reasons.badSum);
                return;
            }
        }
        if (lastState != state.states.up) {
            cntr.drop(pck, counter.reasons.notUp);
            return;
        }
        if (forcedDN) {
            cntr.drop(pck, counter.reasons.notUp);
            return;
        }
        if (logFile != null) {
            try {
                logFile.write(pck.convertToPcap(bits.getTime() + cfgAll.timeServerOffset, true));
            } catch (Exception e) {
            }
        }
        if (monBufD != null) {
            putMonBufPck(pck.convertToPcap(bits.getTime() + cfgAll.timeServerOffset, true));
        }
        if (monSes != null) {
            monSes.doTxPack(pck.copyBytes(true, true));
        }
        if (qosIn == null) {
            doRxPack(pck);
            return;
        }
        qosIn.classifyPack(pck);
        qosIn.enqueuePack(pck);
        notif.wakeup();
    }

    /**
     * add one ethertype handler
     *
     * @param typ ethertype value that will served (-1=default)
     * @param name name of the protocol
     * @param ifc interface handler that should be notified when packet arrives
     * @return ethertype handler
     */
    public ifcEthTypET addET(int typ, String name, ifcUp ifc) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("add ethertype=" + bits.toHexW(typ));
        }
        ifcEthTypET ntry = new ifcEthTypET(this, ifc);
        ntry.ethTyp = typ;
        ntry.name = name;
        ifc.setParent(ntry);
        if (typ == -1) {
            defUpper = ntry;
            setFilter(false);
            return ntry;
        }
        ifcEthTypET old = etTyps.add(ntry);
        if (old != null) {
            return old;
        }
        setFilter(false);
        return ntry;
    }

    /**
     * update one ethertype handler
     *
     * @param typ ethertype value that will served (-1=default)
     * @param ifc interface handler that should be notified when packet arrives
     * @return ethertype handler
     */
    public ifcEthTypET updateET(int typ, ifcUp ifc) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("update ethertype=" + bits.toHexW(typ));
        }
        if (typ == -1) {
            defUpper.ethTyp = typ;
            ifc.setParent(defUpper);
            return defUpper;
        }
        ifcEthTypET ntry = new ifcEthTypET(this, ifc);
        ntry.ethTyp = typ;
        ntry = etTyps.find(ntry);
        if (ntry == null) {
            return null;
        }
        ntry.upper = ifc;
        ifc.setParent(ntry);
        return ntry;
    }

    /**
     * remove one ethertype handler
     *
     * @param typ ethertype value that should deleted (-1=default)
     * @return true interface handler that was used (null=error)
     */
    public ifcUp delET(int typ) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("del ethertype=" + bits.toHexW(typ));
        }
        ifcEthTypET ntry = new ifcEthTypET(null, null);
        if (typ == -1) {
            ntry = defUpper;
            defUpper = new ifcEthTypET(null, null);
        } else {
            ntry.ethTyp = typ;
            ntry = etTyps.del(ntry);
            if (ntry == null) {
                return null;
            }
        }
        try {
            ntry.upper.closeUp();
        } catch (Exception e) {
        }
        setFilter(false);
        return ntry.upper;
    }

    /**
     * add one llc handler
     *
     * @param typ llc value that will served
     * @param name name of the protocol
     * @param ifc interface handler that should be notified when packet arrives
     * @return llc handler
     */
    public ifcEthTypLLC addLLC(int typ, String name, ifcUp ifc) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("add llc=" + bits.toHexW(typ));
        }
        ifcEthTypLLC ntry = new ifcEthTypLLC(this, ifc);
        ntry.llcTyp = typ;
        ntry.name = name;
        ifc.setParent(ntry);
        ifcEthTypLLC old = llcTyps.add(ntry);
        if (old != null) {
            return old;
        }
        setFilter(false);
        return ntry;
    }

    /**
     * update one llc handler
     *
     * @param typ llc value that will served
     * @param ifc interface handler that should be notified when packet arrives
     * @return llc handler
     */
    public ifcEthTypLLC updateLLC(int typ, ifcUp ifc) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("update llc=" + bits.toHexW(typ));
        }
        ifcEthTypLLC ntry = new ifcEthTypLLC(this, ifc);
        ntry.llcTyp = typ;
        ntry = llcTyps.find(ntry);
        if (ntry == null) {
            return null;
        }
        ntry.upper = ifc;
        ifc.setParent(ntry);
        return ntry;
    }

    /**
     * remove one llc handler
     *
     * @param typ llc value that should deleted
     * @return true interface handler that was used (null=error)
     */
    public ifcUp delLLC(int typ) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("del llc=" + bits.toHexW(typ));
        }
        ifcEthTypLLC ntry = new ifcEthTypLLC(null, null);
        ntry.llcTyp = typ;
        ntry = llcTyps.del(ntry);
        if (ntry == null) {
            return null;
        }
        try {
            ntry.upper.closeUp();
        } catch (Exception e) {
        }
        setFilter(false);
        return ntry.upper;
    }

    /**
     * add one snap handler
     *
     * @param typ snap value that will served
     * @param name name of the protocol
     * @param ifc interface handler that should be notified when packet arrives
     * @return snap handler
     */
    public ifcEthTypSNAP addSNAP(int typ, String name, ifcUp ifc) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("add snap=" + bits.toHexD(typ));
        }
        ifcEthTypSNAP ntry = new ifcEthTypSNAP(this, ifc);
        ntry.snapTyp = typ;
        ntry.name = name;
        ifc.setParent(ntry);
        ifcEthTypSNAP old = snapTyps.add(ntry);
        if (old != null) {
            return old;
        }
        setFilter(false);
        return ntry;
    }

    /**
     * update one snap handler
     *
     * @param typ snap value that will served
     * @param ifc interface handler that should be notified when packet arrives
     * @return snap handler
     */
    public ifcEthTypSNAP updateSNAP(int typ, ifcUp ifc) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("update snap=" + bits.toHexD(typ));
        }
        ifcEthTypSNAP ntry = new ifcEthTypSNAP(this, ifc);
        ntry.snapTyp = typ;
        ntry = snapTyps.find(ntry);
        if (ntry == null) {
            return null;
        }
        ntry.upper = ifc;
        ifc.setParent(ntry);
        return ntry;
    }

    /**
     * remove one snap handler
     *
     * @param typ snap value that should deleted
     * @return true interface handler that was used (null=error)
     */
    public ifcUp delSNAP(int typ) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("del snap=" + bits.toHexD(typ));
        }
        ifcEthTypSNAP ntry = new ifcEthTypSNAP(null, null);
        ntry.snapTyp = typ;
        ntry = snapTyps.del(ntry);
        if (ntry == null) {
            return null;
        }
        try {
            ntry.upper.closeUp();
        } catch (Exception e) {
        }
        setFilter(false);
        return ntry.upper;
    }

    /**
     * get size of mtu
     *
     * @return mtu size
     */
    public int getMTUsize() {
        if (forcedMTU > 0) {
            return forcedMTU;
        }
        return lower.getMTUsize();
    }

    /**
     * get interface bandwidth
     *
     * @return bandwidth
     */
    public long getBandwidth() {
        if (forcedBW > 0) {
            return forcedBW;
        }
        return lower.getBandwidth();
    }

    /**
     * get show results
     *
     * @return table
     */
    public userFormat getShTypes() {
        userFormat l = new userFormat("|", "type|value|handler|tx|rx|drop");
        if (defUpper.upper != null) {
            l.add(defUpper.dump());
        }
        for (int i = 0; i < etTyps.size(); i++) {
            l.add(etTyps.get(i).dump());
        }
        for (int i = 0; i < llcTyps.size(); i++) {
            l.add(llcTyps.get(i).dump());
        }
        for (int i = 0; i < snapTyps.size(); i++) {
            l.add(snapTyps.get(i).dump());
        }
        return l;
    }

    /**
     * get shot results
     *
     * @return text
     */
    public String getShHeads() {
        String s = "";
        if (promiscous) {
            s = "promisc, ";
        }
        return name + " is " + s + state.conv2string(getState()) + cntr.getShHead();
    }

    /**
     * reinit file logger
     *
     * @param s name of capture file
     * @return false if successful, true if error happened
     */
    public boolean initLog(String s) {
        try {
            logFile.close();
        } catch (Exception e) {
        }
        if (logFile != null) {
            logFile = null;
            return true;
        }
        try {
            RandomAccessFile f = new RandomAccessFile(new File(s), "rw");
            f.setLength(0);
            f.write(packHolder.getPcapHeader(104));
            logFile = f;
        } catch (Exception e) {
        }
        return logFile == null;
    }

    /**
     * get monitor buffer size
     *
     * @return bytes
     */
    public int getMonBufSize() {
        if (monBufD == null) {
            return -1;
        }
        return monBufD.length;
    }

    private synchronized void putMonBufPck(byte[] pck) {
        byte[] trg = monBufD;
        if (trg == null) {
            return;
        }
        for (int i = 0; i < pck.length; i++) {
            trg[monBufP] = pck[i];
            monBufP = (monBufP + 1) % trg.length;
        }
    }

    /**
     * clear counters
     */
    public void clearCounter() {
        for (int i = 0; i < etTyps.size(); i++) {
            etTyps.get(i).cntr.clear();
        }
        for (int i = 0; i < llcTyps.size(); i++) {
            llcTyps.get(i).cntr.clear();
        }
        for (int i = 0; i < snapTyps.size(); i++) {
            snapTyps.get(i).cntr.clear();
        }
        totCntr = totCntr.plus(cntr);
        cntr.clear();
    }

    /**
     * get total counters
     *
     * @return total counters
     */
    public counter getTotalCounter() {
        return totCntr.plus(cntr);
    }

}

class ifcEthTypET implements ifcDn, Comparator<ifcEthTypET> {

    public int ethTyp;

    public String name;

    public ifcUp upper = new ifcNull();

    public boolean promiscous;

    public counter cntr = new counter();

    private ifcEthTyp lower;

    public ifcEthTypET(ifcEthTyp parent, ifcUp server) {
        lower = parent;
        upper = server;
    }

    public int compare(ifcEthTypET v1, ifcEthTypET v2) {
        if (v1.ethTyp < v2.ethTyp) {
            return -1;
        }
        if (v1.ethTyp > v2.ethTyp) {
            return +1;
        }
        return 0;
    }

    public String dump() {
        return "ethtyp|" + bits.toHexW(ethTyp) + "|" + name + "|" + cntr.getShBsum();
    }

    public String toString() {
        return "" + lower;
    }

    public counter getCounter() {
        return cntr;
    }

    public addrType getHwAddr() {
        return lower.getHwAddr();
    }

    public state.states getState() {
        return lower.getState();
    }

    public void flapped() {
    }

    public void setUpper(ifcUp server) {
        upper = server;
        upper.setParent(this);
    }

    public void setFilter(boolean promisc) {
        promiscous = promisc;
        lower.setFilter(promisc);
    }

    public long getBandwidth() {
        return lower.getBandwidth();
    }

    public int getMTUsize() {
        return lower.getMTUsize();
    }

    public void closeDn() {
        lower.delET(ethTyp);
    }

    public void sendPack(packHolder pck) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("tx type=" + bits.toHexW(ethTyp));
        }
        cntr.tx(pck);
        pck.ETHtype = ethTyp;
        lower.doTxPack(pck);
    }

    public void doRxPack(packHolder pck) {
        cntr.rx(pck);
        if (upper == null) {
            cntr.drop(pck, counter.reasons.noIface);
            return;
        }
        upper.recvPack(pck);
    }

}

class ifcEthTypLLC implements ifcDn, Comparator<ifcEthTypLLC> {

    public int llcTyp;

    public String name;

    public ifcUp upper = new ifcNull();

    public boolean promiscous;

    public counter cntr = new counter();

    private ifcEthTyp lower;

    public ifcEthTypLLC(ifcEthTyp parent, ifcUp server) {
        lower = parent;
        upper = server;
    }

    public int compare(ifcEthTypLLC v1, ifcEthTypLLC v2) {
        if (v1.llcTyp < v2.llcTyp) {
            return -1;
        }
        if (v1.llcTyp > v2.llcTyp) {
            return +1;
        }
        return 0;
    }

    public String dump() {
        return "llc|" + bits.toHexW(llcTyp) + "|" + name + "|" + cntr.getShBsum();
    }

    public String toString() {
        return "" + lower;
    }

    public counter getCounter() {
        return cntr;
    }

    public addrType getHwAddr() {
        return lower.getHwAddr();
    }

    public state.states getState() {
        return lower.getState();
    }

    public void flapped() {
    }

    public void setUpper(ifcUp server) {
        upper = server;
        upper.setParent(this);
    }

    public void setFilter(boolean promisc) {
        promiscous = promisc;
        lower.setFilter(promisc);
    }

    public long getBandwidth() {
        return lower.getBandwidth();
    }

    public int getMTUsize() {
        int i = lower.getMTUsize();
        if (i > 1497) {
            i = 1497;
        }
        return i;
    }

    public void closeDn() {
        lower.delLLC(llcTyp);
    }

    public void sendPack(packHolder pck) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("tx llc=" + bits.toHexW(llcTyp));
        }
        pck.getSkip(2);
        cntr.tx(pck);
        pck.msbPutW(0, pck.dataSize() + 3); // size of packet
        pck.msbPutW(2, llcTyp); // llc type
        pck.putByte(4, 0x03); // control flags
        pck.putSkip(5);
        pck.merge2beg();
        pck.ETHtype = llcTyp;
        lower.doTxPack(pck);
    }

    public void doRxPack(packHolder pck) {
        pck.getSkip(5);
        cntr.rx(pck);
        if (upper == null) {
            cntr.drop(pck, counter.reasons.noIface);
            return;
        }
        pck.msbPutW(0, llcTyp);
        pck.putSkip(2);
        pck.merge2beg();
        upper.recvPack(pck);
    }

}

class ifcEthTypSNAP implements ifcDn, Comparator<ifcEthTypSNAP> {

    public int snapTyp;

    public String name;

    public ifcUp upper = new ifcNull();

    public boolean promiscous;

    public counter cntr = new counter();

    private ifcEthTyp lower;

    public ifcEthTypSNAP(ifcEthTyp parent, ifcUp server) {
        lower = parent;
        upper = server;
    }

    public int compare(ifcEthTypSNAP v1, ifcEthTypSNAP v2) {
        if (v1.snapTyp < v2.snapTyp) {
            return -1;
        }
        if (v1.snapTyp > v2.snapTyp) {
            return +1;
        }
        return 0;
    }

    public String dump() {
        return "snap|" + bits.toHexD(snapTyp) + "|" + name + "|" + cntr.getShBsum();
    }

    public String toString() {
        return "" + lower;
    }

    public counter getCounter() {
        return cntr;
    }

    public addrType getHwAddr() {
        return lower.getHwAddr();
    }

    public state.states getState() {
        return lower.getState();
    }

    public void flapped() {
    }

    public void setUpper(ifcUp server) {
        upper = server;
        upper.setParent(this);
    }

    public void setFilter(boolean promisc) {
        promiscous = promisc;
        lower.setFilter(promisc);
    }

    public long getBandwidth() {
        return lower.getBandwidth();
    }

    public int getMTUsize() {
        int i = lower.getMTUsize();
        if (i > 1494) {
            i = 1494;
        }
        return i;
    }

    public void closeDn() {
        lower.delSNAP(snapTyp);
    }

    public void sendPack(packHolder pck) {
        if (debugger.ifcEthTypTraf) {
            logger.debug("tx snap=" + bits.toHexD(snapTyp));
        }
        cntr.tx(pck);
        pck.msbPutW(0, pck.dataSize() + 6); // size of packet
        pck.msbPutW(2, ifcEthTyp.snap); // llc type
        pck.putByte(4, 0x03); // control flags
        pck.msbPutD(5, snapTyp << 8); // organization code
        pck.putSkip(8);
        pck.merge2beg();
        pck.ETHtype = snapTyp;
        lower.doTxPack(pck);
    }

    public void doRxPack(packHolder pck) {
        pck.getSkip(8);
        cntr.rx(pck);
        if (upper == null) {
            cntr.drop(pck, counter.reasons.noIface);
            return;
        }
        upper.recvPack(pck);
    }

}

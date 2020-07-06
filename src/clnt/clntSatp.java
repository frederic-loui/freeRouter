package clnt;

import addr.addrEmpty;
import addr.addrIP;
import addr.addrType;
import cfg.cfgIfc;
import cfg.cfgVrf;
import cry.cryEncrGeneric;
import cry.cryHashGeneric;
import ifc.ifcDn;
import ifc.ifcNull;
import ifc.ifcUp;
import ip.ipFwdIface;
import pack.packHolder;
import prt.prtGenConn;
import prt.prtServP;
import prt.prtUdp;
import sec.secTransform;
import user.userTerminal;
import util.bits;
import util.counter;
import util.logger;
import util.state;

/**
 * secure anycast tunneling protocol
 *
 * @author matecsaba
 */
public class clntSatp implements Runnable, prtServP, ifcDn {

    /**
     * port number
     */
    public static final int port = 4445;

    /**
     * upper layer
     */
    public ifcUp upper = new ifcNull();

    /**
     * target of tunnel
     */
    public String target = null;

    /**
     * vrf of target
     */
    public cfgVrf vrf = null;

    /**
     * source interface
     */
    public cfgIfc srcIfc = null;

    /**
     * source port
     */
    public int prtL;

    /**
     * target port
     */
    public int prtR;

    /**
     * sending ttl value, -1 means maps out
     */
    public int sendingTTL = 255;

    /**
     * sending tos value, -1 means maps out
     */
    public int sendingTOS = -1;

    /**
     * preshared secret
     */
    public String preshared;

    /**
     * transform set to use
     */
    public secTransform transform;

    /**
     * counter
     */
    public counter cntr = new counter();

    private prtGenConn conn;

    private addrIP localAddr;

    private addrIP remoteAddr;

    private ipFwdIface fwdIfc;

    private prtUdp fwdUdp;

    private boolean working = true;

    private int cphrSiz;

    private int hashSiz;

    private cryHashGeneric hashRx;

    private cryEncrGeneric cphrRx;

    private cryHashGeneric hashTx;

    private cryEncrGeneric cphrTx;

    private int seqRx;

    private int seqTx;

    private int timRx;

    private int timTx;

    private int endptRx;

    private int endptTx;

    public String toString() {
        return "satp to " + target;
    }

    /**
     * get hw address
     *
     * @return hw address
     */
    public addrType getHwAddr() {
        return new addrEmpty();
    }

    /**
     * set filter
     *
     * @param promisc promiscous mode
     */
    public void setFilter(boolean promisc) {
    }

    /**
     * get state
     *
     * @return state
     */
    public state.states getState() {
        return state.states.up;
    }

    /**
     * close interface
     */
    public void closeDn() {
        clearState();
    }

    /**
     * flap interface
     */
    public void flapped() {
        clearState();
    }

    /**
     * set upper layer
     *
     * @param server upper layer
     */
    public void setUpper(ifcUp server) {
        upper = server;
        upper.setParent(this);
    }

    /**
     * get counter
     *
     * @return counter
     */
    public counter getCounter() {
        return cntr;
    }

    /**
     * get mtu size
     *
     * @return mtu size
     */
    public int getMTUsize() {
        return 1400;
    }

    /**
     * get bandwidth
     *
     * @return bandwidth
     */
    public long getBandwidth() {
        return 8000000;
    }

    /**
     * send packet
     *
     * @param pck packet
     */
    public synchronized void sendPack(packHolder pck) {
        pck.merge2beg();
        if (conn == null) {
            return;
        }
        int i = pck.dataSize() % cphrSiz;
        i = cphrSiz - i;
        pck.putFill(0, i, i - 1); // padding
        pck.putSkip(i);
        pck.merge2end();
        byte[] buf = new byte[cphrSiz];
        for (i = 0; i < buf.length; i++) {
            buf[i] = (byte) bits.randomB();
        }
        pck.putCopy(buf, 0, 0, buf.length);
        pck.putSkip(buf.length);
        pck.merge2beg();
        pck.encrData(cphrTx, 0, pck.dataSize());
        pck.msbPutD(0, seqTx);
        pck.msbPutD(4, endptTx);
        pck.putSkip(8);
        pck.merge2beg();
        seqTx++;
        hashTx.init();
        pck.hashData(hashTx, 0, pck.dataSize());
        byte[] hsh = hashTx.finish();
        pck.putCopy(hsh, 0, 0, hsh.length);
        pck.putSkip(hsh.length);
        pck.merge2end();
        cntr.tx(pck);
        pck.putDefaults();
        conn.send2net(pck);
    }

    /**
     * start connection
     */
    public void workStart() {
        new Thread(this).start();
    }

    /**
     * stop connection
     */
    public void workStop() {
        working = false;
        clearState();
    }

    public void run() {
        for (;;) {
            if (!working) {
                break;
            }
            try {
                clearState();
                workDoer();
            } catch (Exception e) {
                logger.traceback(e);
            }
            clearState();
            bits.sleep(1000);
        }
    }

    private void workDoer() {
        byte[] buf1 = preshared.getBytes();
        byte[] buf2 = new byte[0];
        for (; buf2.length < 1024;) {
            cryHashGeneric hsh = transform.getHash();
            hsh.init();
            hsh.update(buf1);
            buf1 = hsh.finish();
            buf2 = bits.byteConcat(buf2, buf1);
        }
        cphrTx = transform.getEncr();
        cphrRx = transform.getEncr();
        byte[] res = buf2;
        buf1 = new byte[cphrTx.getKeySize()];
        buf2 = new byte[cphrTx.getBlockSize()];
        int pos = buf1.length + buf2.length;
        bits.byteCopy(res, 0, buf1, 0, buf1.length);
        bits.byteCopy(res, buf1.length, buf2, 0, buf2.length);
        cphrTx.init(buf1, buf2, true);
        cphrRx.init(buf1, buf2, false);
        cphrSiz = buf2.length;
        hashSiz = transform.getHash().getHashSize();
        buf1 = new byte[hashSiz];
        buf2 = new byte[hashSiz];
        bits.byteCopy(res, pos, buf1, 0, buf1.length);
        bits.byteCopy(res, pos, buf2, 0, buf2.length);
        hashTx = transform.getHmac(buf1);
        hashRx = transform.getHmac(buf2);
        endptTx = bits.randomB();
        addrIP trg = userTerminal.justResolv(target, 0);
        if (trg == null) {
            return;
        }
        prtUdp udp = vrf.getUdp(trg);
        ipFwdIface fwdIfc = null;
        if (srcIfc != null) {
            fwdIfc = srcIfc.getFwdIfc(trg);
        }
        if (prtR == 0) {
            prtR = port;
        }
        if (prtL == 0) {
            prtL = prtR;
        }
        if (prtL == -1) {
            prtL = 0;
        }
        conn = udp.packetConnect(this, fwdIfc, prtL, trg, prtR, "satp", null, -1);
        if (conn == null) {
            return;
        }
        conn.timeout = 120000;
        conn.sendTOS = sendingTOS;
        conn.sendTTL = sendingTTL;
        for (;;) {
            if (conn == null) {
                return;
            }
            if (conn.txBytesFree() < 0) {
                return;
            }
            bits.sleep(1000);
        }
    }

    private synchronized void clearState() {
        if (conn != null) {
            conn.setClosing();
        }
        conn = null;
        seqRx = 0;
        seqTx = 0;
        timTx = (int) (bits.getTime() / 1000);
    }

    /**
     * close interface
     *
     * @param ifc interface
     */
    public void closedInterface(ipFwdIface ifc) {
    }

    /**
     * accept connection
     *
     * @param id connection
     * @return false on success, true on error
     */
    public boolean datagramAccept(prtGenConn id) {
        return true;
    }

    /**
     * connection ready
     *
     * @param id connection
     */
    public void datagramReady(prtGenConn id) {
    }

    /**
     * connection closed
     *
     * @param id connection
     */
    public void datagramClosed(prtGenConn id) {
    }

    /**
     * work connection
     *
     * @param id connection
     */
    public void datagramWork(prtGenConn id) {
    }

    /**
     * received packet
     *
     * @param id connection
     * @param pck packet
     * @return false on success, true on error
     */
    public synchronized boolean datagramRecv(prtGenConn id, packHolder pck) {
        if (pck.dataSize() < (hashSiz + cphrSiz + 8)) {
            logger.info("got too small from " + target);
            cntr.drop(pck, counter.reasons.badLen);
            return false;
        }
        byte[] sum = new byte[hashSiz];
        int i = pck.dataSize() - hashSiz;
        pck.getCopy(sum, 0, i, hashSiz);
        pck.setDataSize(i);
        hashRx.init();
        pck.hashData(hashRx, 0, pck.dataSize());
        if (bits.byteComp(sum, 0, hashRx.finish(), 0, hashSiz) != 0) {
            logger.info("got bad hash from " + target);
            cntr.drop(pck, counter.reasons.badSum);
            return false;
        }
        seqRx = pck.msbGetD(0);
        endptRx = pck.msbGetD(4);
        pck.getSkip(8);
        if ((pck.dataSize() % cphrSiz) != 0) {
            logger.info("got bad padding from " + target);
            cntr.drop(pck, counter.reasons.badLen);
            return false;
        }
        pck.encrData(cphrRx, 0, pck.dataSize());
        pck.getSkip(cphrSiz);
        i = pck.dataSize() - 1;
        pck.setDataSize(i - pck.getByte(i));
        cntr.rx(pck);
        upper.recvPack(pck);
        return false;
    }

}

package clnt;

import addr.addrEmpty;
import addr.addrIP;
import addr.addrType;
import ifc.ifcDn;
import ifc.ifcNull;
import ifc.ifcUp;
import ip.ipFwd;
import ip.ipFwdBier;
import pack.packHolder;
import tab.tabGen;
import util.cmds;
import util.counter;
import util.debugger;
import util.logger;
import util.notifier;
import util.state;

/**
 * mpls bier tunnel client
 *
 * @author matecsaba
 */
public class clntMplsBier implements Runnable, ifcDn {

    /**
     * upper layer
     */
    public ifcUp upper = new ifcNull();

    /**
     * forwarder
     */
    public ipFwd fwdCor;

    /**
     * source id
     */
    public int srcId = 0;

    /**
     * experimental value, -1 means maps out
     */
    public int expr = -1;

    /**
     * ttl value
     */
    public int ttl = 255;

    /**
     * counter
     */
    public counter cntr = new counter();

    private boolean working = false;

    private ipFwdBier bier;

    private tabGen<addrIP> targets = new tabGen<addrIP>();

    private notifier notif1 = new notifier();

    private notifier notif2 = new notifier();

    public String toString() {
        return "bier to " + getTargets();
    }

    /**
     * get hw address
     *
     * @return address
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
    }

    /**
     * flap interface
     */
    public void flapped() {
    }

    /**
     * set upper
     *
     * @param server upper
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
        return 1500;
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
    public void sendPack(packHolder pck) {
        cntr.tx(pck);
        if (expr >= 0) {
            pck.MPLSexp = expr;
        }
        if (ttl >= 0) {
            pck.MPLSttl = ttl;
        }
        pck.ETHtype = pck.msbGetW(0);
        pck.getSkip(2);
        bier.sendPack(pck);
    }

    /**
     * set targets
     *
     * @param s targets
     */
    public void setTargets(String s) {
        cmds c = new cmds("adrs", s);
        for (;;) {
            s = c.word();
            if (s.length() < 1) {
                break;
            }
            addrIP a = new addrIP();
            if (a.fromString(s)) {
                continue;
            }
            targets.add(a);
        }
    }

    /**
     * get targets
     *
     * @return targets
     */
    public String getTargets() {
        String s = "";
        for (int i = 0; i < targets.size(); i++) {
            s += " " + targets.get(i);
        }
        return s.trim();
    }

    /**
     * start connection
     */
    public void workStart() {
        if (debugger.clntMplsBierTraf) {
            logger.debug("starting work");
        }
        working = true;
        bier = new ipFwdBier(fwdCor, srcId);
        for (int i = 0; i < targets.size(); i++) {
            bier.addPeer(targets.get(i), -1);
        }
        new Thread(this).start();
    }

    /**
     * wait until setup complete
     *
     * @param tim time to wait
     */
    public void wait4setup(int tim) {
        notif2.misleep(tim);
    }

    /**
     * stop connection
     */
    public void workStop() {
        if (debugger.clntMplsBierTraf) {
            logger.debug("stop work");
        }
        working = false;
        notif1.wakeup();
    }

    public void run() {
        for (;;) {
            if (!working) {
                break;
            }
            try {
                bier.updatePeers();
            } catch (Exception e) {
                logger.traceback(e);
            }
            notif1.sleep(10000);
        }
    }

}

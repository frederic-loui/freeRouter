package serv;

import java.util.List;
import pack.packHolder;
import pack.packNetflow;
import pipe.pipeLine;
import pipe.pipeSide;
import prt.prtGenConn;
import prt.prtServS;
import tab.tabGen;
import tab.tabSession;
import tab.tabSessionEntry;
import user.userFilter;
import user.userHelping;
import util.bits;
import util.cmds;
import util.logger;

/**
 * netflow (rfc3954) server
 *
 * @author matecsaba
 */
public class servNetflow extends servGeneric implements prtServS {

    /**
     * sessions
     */
    public tabSession connects = new tabSession(true, 60000);

    /**
     * defaults text
     */
    public final static String[] defaultL = {
        "server netflow .*! port " + packNetflow.port,
        "server netflow .*! protocol " + proto2string(protoAllDgrm),
        "server netflow .*! no timeout",
        "server netflow .*! no mac",
        "server netflow .*! no before",
        "server netflow .*! no after"
    };

    /**
     * defaults filter
     */
    public static tabGen<userFilter> defaultF;

    /**
     * get defaults filter
     *
     * @return filter
     */
    public tabGen<userFilter> srvDefFlt() {
        return defaultF;
    }

    /**
     * get name
     *
     * @return name
     */
    public String srvName() {
        return "netflow";
    }

    /**
     * get port
     *
     * @return port
     */
    public int srvPort() {
        return packNetflow.port;
    }

    /**
     * get protocol
     *
     * @return protocol
     */
    public int srvProto() {
        return protoAllDgrm;
    }

    /**
     * initialize
     *
     * @return false on success, true on error
     */
    public boolean srvInit() {
        connects.startTimer();
        return genStrmStart(this, new pipeLine(32768, true), 0);
    }

    /**
     * deinitialize
     *
     * @return false on success, true on error
     */
    public boolean srvDeinit() {
        connects.stopTimer();
        return genericStop(0);
    }

    /**
     * get configuration
     *
     * @param beg beginning
     * @param lst list
     */
    public void srvShRun(String beg, List<String> lst) {
        connects.getConfig(lst, beg);
    }

    /**
     * configure
     *
     * @param cmd command
     * @return false on success, true on error
     */
    public boolean srvCfgStr(cmds cmd) {
        connects.fromString(cmd);
        return false;
    }

    /**
     * get help
     *
     * @param l help
     */
    public void srvHelp(userHelping l) {
        l.add("1 2  timeout                      set timeout");
        l.add("2 .    <num>                      timeout in ms");
        l.add("1 .  before                       log on session start");
        l.add("1 .  after                        log on session stop");
    }

    /**
     * start connection
     *
     * @param pipe pipeline
     * @param id connection
     * @return false on success, true on error
     */
    public boolean srvAccept(pipeSide pipe, prtGenConn id) {
        pipe.setTime(120000);
        pipe.lineRx = pipeSide.modTyp.modeCRLF;
        pipe.lineTx = pipeSide.modTyp.modeCRLF;
        new servNetflowConn(this, pipe);
        return false;
    }

    /**
     * got message
     *
     * @param pckB packet to read
     * @param pckF netflow parser
     */
    protected void gotPack(packHolder pckB, packNetflow pckF) {
        List<tabSessionEntry> lst = pckF.parsePacket(pckB);
        long tim = bits.getTime();
        for (int i = 0; i < lst.size(); i++) {
            tabSessionEntry ntry = lst.get(i);
            tabSessionEntry old = connects.doSess(ntry, true);
            if (old == null) {
                continue;
            }
            if (ntry.dir != old.dir) {
                ntry = ntry.reverseCounts();
            }
            old.addCounts(ntry);
        }
    }

}

class servNetflowConn implements Runnable {

    private servNetflow parent;

    private pipeSide pipe;

    public servNetflowConn(servNetflow prnt, pipeSide pip) {
        parent = prnt;
        pipe = pip;
        new Thread(this).start();
    }

    public void run() {
        packHolder pckB = new packHolder(true, true);
        packNetflow pckF = new packNetflow();
        try {
            pipe.wait4ready(120000);
            for (;;) {
                pckB.clear();
                pckB = pipe.readPacket(pckB, 0, true);
                if (pckB == null) {
                    break;
                }
                parent.gotPack(pckB, pckF);
            }
        } catch (Exception e) {
            logger.traceback(e);
        }
        pipe.setClose();
    }

}

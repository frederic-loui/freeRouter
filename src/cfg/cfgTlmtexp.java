package cfg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pack.packHolder;
import pipe.pipeLine;
import pipe.pipeSide;
import serv.servStreamingMdt;
import tab.tabGen;
import user.userExec;
import user.userFormat;
import user.userReader;
import util.bits;
import util.cmds;
import util.extMrkLng;
import util.extMrkLngEntry;
import util.protoBuf;
import util.protoBufEntry;

/**
 * telemetry exporter
 *
 * @author matecsaba
 */
public class cfgTlmtexp implements Comparator<cfgTlmtexp> {

    /**
     * name of telemetry export
     */
    public String name;

    /**
     * command
     */
    public String command;

    /**
     * prefix
     */
    public String prefix;

    /**
     * url
     */
    public String url;

    /**
     * path
     */
    public String path;
    /**
     * skip
     */
    public int skip;

    /**
     * key column
     */
    public int keyC;

    /**
     * key name
     */
    public String keyN;

    /**
     * key path
     */
    public String keyP;

    /**
     * columns
     */
    public tabGen<cfgTlmtexpCol> cols;

    /**
     * replacers
     */
    public tabGen<cfgTlmtexpRep> reps;

    /**
     * last reported
     */
    public long last;

    /**
     * time elapsed
     */
    public int time;

    /**
     * reports generated
     */
    public int cnt;

    /**
     * create new telemetry export
     */
    public cfgTlmtexp() {
        cols = new tabGen<cfgTlmtexpCol>();
        reps = new tabGen<cfgTlmtexpRep>();
        skip = 1;
    }

    public int compare(cfgTlmtexp o1, cfgTlmtexp o2) {
        return o1.name.toLowerCase().compareTo(o2.name.toLowerCase());
    }

    /**
     * do config line
     *
     * @param cmd line
     */
    public void doCfgLine(cmds cmd) {
        String s = cmd.word();
        if (s.equals("command")) {
            command = cmd.getRemaining();
            return;
        }
        if (s.equals("skip")) {
            skip = bits.str2num(cmd.word());
            return;
        }
        if (s.equals("key")) {
            keyC = bits.str2num(cmd.word());
            keyN = cmd.word();
            keyP = cmd.word();
            return;
        }
        if (s.equals("replace")) {
            cfgTlmtexpRep rep = new cfgTlmtexpRep(cmd.word());
            rep.trg = cmd.word();
            reps.add(rep);
            return;
        }
        if (!s.equals("column")) {
            cmd.badCmd();
            return;
        }
        cfgTlmtexpCol col = new cfgTlmtexpCol(bits.str2num(cmd.word()));
        cfgTlmtexpCol oldc = cols.add(col);
        if (oldc != null) {
            col = oldc;
        }
        s = cmd.word();
        if (s.equals("name")) {
            col.nam = cmd.word();
            return;
        }
        if (s.equals("type")) {
            col.typ = servStreamingMdt.string2type(cmd.word());
            return;
        }
        if (s.equals("split")) {
            col.splS = cmd.word();
            col.splL = cmd.word();
            col.splR = cmd.word();
            return;
        }
        if (s.equals("replace")) {
            cfgTlmtexpRep rep = new cfgTlmtexpRep(cmd.word());
            rep.trg = cmd.word();
            col.reps.add(rep);
            return;
        }
    }

    /**
     * get result
     *
     * @return result
     */
    public List<String> getResult() {
        if (command == null) {
            return null;
        }
        pipeLine pl = new pipeLine(1024 * 1024, false);
        pipeSide pip = pl.getSide();
        pip.lineTx = pipeSide.modTyp.modeCRLF;
        pip.lineRx = pipeSide.modTyp.modeCRorLF;
        userReader rdr = new userReader(pip, null);
        rdr.tabMod = userFormat.tableMode.raw;
        rdr.height = 0;
        userExec exe = new userExec(pip, rdr);
        exe.privileged = true;
        pip.setTime(120000);
        String a = exe.repairCommand(command);
        exe.executeCommand(a);
        pip = pl.getSide();
        pl.setClose();
        pip.lineTx = pipeSide.modTyp.modeCRLF;
        pip.lineRx = pipeSide.modTyp.modeCRtryLF;
        List<String> lst = new ArrayList<String>();
        for (;;) {
            if (pip.ready2rx() < 1) {
                break;
            }
            a = pip.lineGet(1);
            if (a.length() < 1) {
                continue;
            }
            lst.add(a);
        }
        return lst;
    }

    private void doMetricKvGpb(packHolder pck2, packHolder pck3, int typ, String nam, String val) {
        protoBuf pb2 = new protoBuf();
        pb2.putField(servStreamingMdt.fnName, protoBufEntry.tpBuf, nam.getBytes());
        switch (typ) {
            case servStreamingMdt.fnByte:
                pb2.putField(typ, protoBufEntry.tpBuf, val.getBytes());
                break;
            case servStreamingMdt.fnString:
                pb2.putField(typ, protoBufEntry.tpBuf, val.getBytes());
                break;
            case servStreamingMdt.fnBool:
                pb2.putField(typ, protoBufEntry.tpInt, bits.str2num(val));
                break;
            case servStreamingMdt.fnUint32:
            case servStreamingMdt.fnUint64:
                pb2.putField(typ, protoBufEntry.tpInt, bits.str2long(val));
                break;
            case servStreamingMdt.fnSint32:
            case servStreamingMdt.fnSint64:
                pb2.putField(typ, protoBufEntry.tpInt, protoBuf.toZigzag(bits.str2long(val)));
                break;
            case servStreamingMdt.fnDouble:
                double d;
                try {
                    d = Double.parseDouble(val);
                } catch (Exception e) {
                    return;
                }
                pb2.putField(typ, protoBufEntry.tpInt, Double.doubleToLongBits(d));
                break;
            case servStreamingMdt.fnFloat:
                float f;
                try {
                    f = Float.parseFloat(val);
                } catch (Exception e) {
                    return;
                }
                pb2.putField(typ, protoBufEntry.tpInt, Float.floatToIntBits(f));
                break;
            default:
                return;
        }
        pck3.clear();
        pb2.toPacket(pck3);
        pb2.clear();
        pb2.putField(servStreamingMdt.fnFields, protoBufEntry.tpBuf, pck3.getCopy());
        pb2.toPacket(pck2);
        pb2.clear();
    }

    private void doMetricNetConf(extMrkLng res, String nam, String val) {
        res.data.add(new extMrkLngEntry(nam, "", val));
    }

    private List<String> doSplitLine(String a) {
        cmds cm = new cmds("tele", a);
        List<String> cl = new ArrayList<String>();
        for (;;) {
            a = cm.word(";");
            if (a.length() < 1) {
                break;
            }
            cl.add(a);
        }
        return cl;
    }

    private static String doReplaces(String a, tabGen<cfgTlmtexpRep> reps) {
        for (int i = 0; i < reps.size(); i++) {
            cfgTlmtexpRep rep = reps.get(i);
            a = a.replaceAll(rep.src, rep.trg);
        }
        return a;
    }

    private packHolder doLineKvGpb(String a) {
        List<String> cl = doSplitLine(a);
        int cls = cl.size();
        if (keyC >= cls) {
            return null;
        }
        protoBuf pb = new protoBuf();
        a = doReplaces(cl.get(keyC), reps);
        packHolder pck1 = new packHolder(true, true);
        packHolder pck2 = new packHolder(true, true);
        packHolder pck3 = new packHolder(true, true);
        pb.putField(servStreamingMdt.fnName, protoBufEntry.tpBuf, keyN.getBytes());
        pb.putField(servStreamingMdt.fnString, protoBufEntry.tpBuf, a.getBytes());
        pb.toPacket(pck1);
        pb.clear();
        pb.putField(servStreamingMdt.fnName, protoBufEntry.tpBuf, servStreamingMdt.nmDat.getBytes());
        pb.toPacket(pck2);
        pb.clear();
        for (int o = 0; o < cols.size(); o++) {
            cfgTlmtexpCol cc = cols.get(o);
            if (cl.size() <= cc.num) {
                continue;
            }
            a = doReplaces(cl.get(cc.num), cc.reps);
            if (cc.splS == null) {
                doMetricKvGpb(pck2, pck3, cc.typ, cc.nam, a);
                continue;
            }
            int i = a.indexOf(cc.splS);
            if (i < 0) {
                doMetricKvGpb(pck2, pck3, cc.typ, cc.nam, a);
                continue;
            }
            doMetricKvGpb(pck2, pck3, cc.typ, cc.nam + cc.splL, a.substring(0, i));
            doMetricKvGpb(pck2, pck3, cc.typ, cc.nam + cc.splR, a.substring(i + cc.splS.length(), a.length()));
        }
        protoBuf pb2 = new protoBuf();
        pb2.putField(servStreamingMdt.fnName, protoBufEntry.tpBuf, servStreamingMdt.nmKey.getBytes());
        pb2.putField(servStreamingMdt.fnFields, protoBufEntry.tpBuf, pck1.getCopy());
        pck3.clear();
        pb2.toPacket(pck3);
        pb2.clear();
        pb.putField(servStreamingMdt.fnFields, protoBufEntry.tpBuf, pck3.getCopy());
        pb.putField(servStreamingMdt.fnFields, protoBufEntry.tpBuf, pck2.getCopy());
        pck3.clear();
        pb.toPacket(pck3);
        return pck3;
    }

    private void doLineNetConf(extMrkLng res, String beg, String a) {
        List<String> cl = doSplitLine(a);
        int cls = cl.size();
        if (keyC >= cls) {
            return;
        }
        a = doReplaces(cl.get(keyC), reps);
        res.data.add(new extMrkLngEntry(beg + keyN, "", a));
        for (int o = 0; o < cols.size(); o++) {
            cfgTlmtexpCol cc = cols.get(o);
            if (cl.size() <= cc.num) {
                continue;
            }
            a = doReplaces(cl.get(cc.num), cc.reps);
            if (cc.splS == null) {
                doMetricNetConf(res, beg + path + "/" + cc.nam, a);
                continue;
            }
            int i = a.indexOf(cc.splS);
            if (i < 0) {
                doMetricNetConf(res, beg + path + "/" + cc.nam, a);
                continue;
            }
            doMetricNetConf(res, beg + path + "/" + cc.nam + cc.splL, a.substring(0, i));
            doMetricNetConf(res, beg + path + "/" + cc.nam + cc.splR, a.substring(i + cc.splS.length(), a.length()));
        }
    }

    /**
     * generate report
     *
     * @return report, null on error
     */
    public packHolder getReportKvGpb() {
        last = bits.getTime();
        List<String> res = getResult();
        for (int i = 0; i < skip; i++) {
            if (res.size() < 1) {
                break;
            }
            res.remove(0);
        }
        packHolder pck = new packHolder(true, true);
        protoBuf pb = new protoBuf();
        pb.putField(servStreamingMdt.rpStart, protoBufEntry.tpInt, last);
        pb.putField(servStreamingMdt.rpNodeStr, protoBufEntry.tpBuf, cfgAll.hostName.getBytes());
        pb.putField(servStreamingMdt.rpSubsStr, protoBufEntry.tpBuf, name.getBytes());
        pb.putField(servStreamingMdt.rpEnc, protoBufEntry.tpBuf, (prefix + ":" + path).getBytes());
        pb.toPacket(pck);
        pb.clear();
        for (int i = 0; i < res.size(); i++) {
            packHolder ln = doLineKvGpb(res.get(i));
            if (ln == null) {
                continue;
            }
            pb.putField(servStreamingMdt.rpKvgpb, protoBufEntry.tpBuf, ln.getCopy());
            pb.toPacket(pck);
            pb.clear();
        }
        long tim = bits.getTime();
        pb.putField(servStreamingMdt.rpStop, protoBufEntry.tpInt, tim);
        pb.toPacket(pck);
        time = (int) (tim - last);
        return pck;
    }

    /**
     * generate report
     *
     * @param rep report
     * @param beg beginning
     */
    public void getReportNetConf(extMrkLng rep, String beg) {
        List<String> res = getResult();
        for (int i = 0; i < skip; i++) {
            if (res.size() < 1) {
                break;
            }
            res.remove(0);
        }
        for (int i = 0; i < res.size(); i++) {
            doLineNetConf(rep, beg, res.get(i));
        }
    }

    /**
     * get show
     *
     * @return result
     */
    public List<String> getShow() {
        List<String> res = new ArrayList<String>();
        res.add("command=" + command);
        res.add("path=" + path);
        res.add("prefix=" + prefix);
        res.add("url=" + url);
        res.add("asked=" + cnt + " times");
        res.add("reply=" + time + " ms");
        res.add("output:");
        res.addAll(getResult());
        res.add("result:" + getReportKvGpb().dump());
        return res;
    }

}

class cfgTlmtexpRep implements Comparator<cfgTlmtexpRep> {

    public final String src;

    public String trg;

    public cfgTlmtexpRep(String n) {
        src = n;
    }

    public int compare(cfgTlmtexpRep o1, cfgTlmtexpRep o2) {
        return o1.src.compareTo(o2.src);
    }

}

class cfgTlmtexpCol implements Comparator<cfgTlmtexpCol> {

    public final int num;

    public String nam;

    public String splS;

    public String splL;

    public String splR;

    public int typ = servStreamingMdt.fnSint64;

    public tabGen<cfgTlmtexpRep> reps = new tabGen<cfgTlmtexpRep>();

    public cfgTlmtexpCol(int n) {
        num = n;
    }

    public int compare(cfgTlmtexpCol o1, cfgTlmtexpCol o2) {
        if (o1.num < o2.num) {
            return -1;
        }
        if (o1.num > o2.num) {
            return +1;
        }
        return 0;
    }

}

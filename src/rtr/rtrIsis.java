package rtr;

import addr.addrClns;
import addr.addrIP;
import addr.addrIPv4;
import addr.addrIPv6;
import addr.addrIsis;
import addr.addrPrefix;
import auth.authLocal;
import cfg.cfgAll;
import cfg.cfgPrfxlst;
import cfg.cfgRoump;
import cfg.cfgRouplc;
import cfg.cfgRtr;
import ifc.ifcEthTyp;
import ip.ipCor4;
import ip.ipCor6;
import ip.ipFwd;
import ip.ipFwdIface;
import ip.ipRtr;
import java.util.ArrayList;
import java.util.List;
import pack.packHolder;
import tab.tabGen;
import tab.tabLabel;
import tab.tabLabelBier;
import tab.tabLabelNtry;
import tab.tabRoute;
import tab.tabRouteAttr;
import tab.tabRouteEntry;
import user.userFlash;
import user.userFormat;
import user.userHelping;
import util.bits;
import util.cmds;
import util.debugger;
import util.logger;
import util.shrtPthFrst;
import util.state;
import util.typLenVal;

/**
 * intermediate system to intermediate system (rfc1142) protocol
 *
 * @author matecsaba
 */
public class rtrIsis extends ipRtr {

    /**
     * llc type
     */
    public static final int llcTyp = 0xfefe;

    /**
     * eth type
     */
    public static final int ethTyp = 0x00fe;

    /**
     * protocol discriminator
     */
    public static final int protDist = 0x83;

    /**
     * external distance
     */
    public int distantExt;

    /**
     * intra-level distance
     */
    public int distantInt;

    /**
     * net number
     */
    public addrClns netEntTit = new addrClns();

    /**
     * levels allowed (bitmask)
     */
    public int operateLevel;

    /**
     * maximum level addresses
     */
    public int maxAreaAddr;

    /**
     * use wide metric
     */
    public boolean metricWide;

    /**
     * use multi topology
     */
    public boolean multiTopo;

    /**
     * level id
     */
    protected addrClns areaID = new addrClns();

    /**
     * router id
     */
    protected addrIsis routerID = new addrIsis();

    /**
     * traffic engineering id
     */
    public addrIP traffEngID = new addrIP();

    /**
     * segment routing maximum
     */
    public int segrouMax = 0;

    /**
     * segment routing base
     */
    public int segrouBase = 0;

    /**
     * bier length
     */
    public int bierLen = 0;

    /**
     * bier maximum
     */
    public int bierMax = 0;

    /**
     * forwarding core
     */
    public final ipFwd fwdCore;

    /**
     * other afi router
     */
    public final rtrIsisOther other;

    /**
     * route type
     */
    protected final tabRouteAttr.routeType rouTyp;

    /**
     * router number
     */
    protected final int rtrNum;

    /**
     * list of interfaces
     */
    protected tabGen<rtrIsisIface> ifaces;

    /**
     * level1 information
     */
    protected rtrIsisLevel level1;

    /**
     * level2 information
     */
    protected rtrIsisLevel level2;

    /**
     * segment routing labels
     */
    protected tabLabelNtry[] segrouLab;

    /**
     * bier labels
     */
    protected tabLabelNtry[] bierLab;

    /**
     * create one isis process
     *
     * @param forwarder the ip protocol
     * @param otherfwd the other ip protocol
     * @param id process id
     */
    public rtrIsis(ipFwd forwarder, ipFwd otherfwd, int id) {
        netEntTit.fromString("00.0000.0000.0000.00");
        fwdCore = forwarder;
        distantExt = 115;
        distantInt = 115;
        operateLevel = 3;
        maxAreaAddr = 3;
        metricWide = true;
        ifaces = new tabGen<rtrIsisIface>();
        rtrNum = id;
        switch (fwdCore.ipVersion) {
            case ipCor4.protocolVersion:
                rouTyp = tabRouteAttr.routeType.isis4;
                break;
            case ipCor6.protocolVersion:
                rouTyp = tabRouteAttr.routeType.isis6;
                break;
            default:
                rouTyp = null;
                break;
        }
        other = new rtrIsisOther(this, otherfwd);
        level1 = new rtrIsisLevel(this, 1);
        level2 = new rtrIsisLevel(this, 2);
        routerCreateComputed();
        fwdCore.routerAdd(this, rouTyp, id);
        level1.startNow();
        level2.startNow();
    }

    /**
     * convert to string
     *
     * @return string
     */
    public String toString() {
        return "isis on " + fwdCore;
    }

    /**
     * convert level to string
     *
     * @param i level
     * @return string
     */
    public static String level2string(int i) {
        switch (i) {
            case 0:
                return "none";
            case 1:
                return "level1";
            case 2:
                return "level2";
            case 3:
                return "both";
            default:
                return "unknown=" + i;
        }
    }

    /**
     * convert string to level
     *
     * @param s string
     * @return level
     */
    public static int string2level(String s) {
        if (s.equals("level1")) {
            return 1;
        }
        if (s.equals("level2")) {
            return 2;
        }
        if (s.equals("both")) {
            return 3;
        }
        return 0;
    }

    /**
     * get tlv handler
     *
     * @return tlv handler
     */
    protected static typLenVal getTlv() {
        return new typLenVal(0, 8, 8, 8, 1, 0, 2, 1, 0, 512, true);
    }

    /**
     * get max area address
     *
     * @return number
     */
    public int getMaxAreaAddr() {
        if (maxAreaAddr == 3) {
            return 0;
        }
        return maxAreaAddr;
    }

    /**
     * get ip protocol version
     *
     * @return protocol version
     */
    public int getProtoVer() {
        return fwdCore.ipVersion;
    }

    /**
     * get nlpid value
     *
     * @param other other afi
     * @return nlpid
     */
    protected int getNLPIDval(boolean other) {
        if (other ^ (fwdCore.ipVersion == ipCor4.protocolVersion)) {
            return ipCor4.protocolNLPID;
        } else {
            return ipCor6.protocolNLPID;
        }
    }

    /**
     * get nlpid list
     *
     * @param other other afi also
     * @return nlpid
     */
    protected byte[] getNLPIDlst(boolean other) {
        byte[] buf;
        if (!other) {
            buf = new byte[1];
            buf[0] = (byte) getNLPIDval(false);
        } else {
            buf = new byte[2];
            buf[0] = (byte) getNLPIDval(false);
            buf[1] = (byte) getNLPIDval(true);
        }
        return buf;
    }

    /**
     * get multi topology value
     *
     * @param other other afi
     * @return mt
     */
    protected int getMTopoVal(boolean other) {
        if (other ^ (fwdCore.ipVersion == ipCor4.protocolVersion)) {
            return 0;
        } else {
            return 2;
        }
    }

    /**
     * get multi topology list
     *
     * @param other other afi also
     * @param flg flags
     * @return mt
     */
    protected byte[] getMTopoLst(boolean other, int flg) {
        byte[] buf;
        if (!other) {
            buf = new byte[2];
            bits.msbPutW(buf, 0, getMTopoVal(false) | flg);
        } else {
            buf = new byte[4];
            bits.msbPutW(buf, 0, getMTopoVal(false) | flg);
            bits.msbPutW(buf, 2, getMTopoVal(true) | flg);
        }
        return buf;
    }

    /**
     * get level handler
     *
     * @param level level number
     * @return level handler
     */
    protected rtrIsisLevel getLevel(int level) {
        if (level == 1) {
            return level1;
        } else {
            return level2;
        }
    }

    /**
     * am i attached to other areas
     *
     * @return true if yes, false if not
     */
    protected boolean amIattach() {
        for (int i = 0; i < ifaces.size(); i++) {
            rtrIsisIface ifc = ifaces.get(i);
            if (ifc == null) {
                continue;
            }
            for (int o = 0; o < ifc.neighs.size(); o++) {
                rtrIsisNeigh nei = ifc.neighs.get(o);
                if (nei == null) {
                    continue;
                }
                if (nei.peerAdjState != rtrIsisNeigh.statUp) {
                    continue;
                }
                if (!nei.foreignArea) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * have neighbor in level
     *
     * @param lev level number
     * @return true if yes, false if no
     */
    protected boolean haveNeighbor(int lev) {
        for (int i = 0; i < ifaces.size(); i++) {
            rtrIsisIface ifc = ifaces.get(i);
            if (ifc == null) {
                continue;
            }
            for (int o = 0; o < ifc.neighs.size(); o++) {
                rtrIsisNeigh nei = ifc.neighs.get(o);
                if (nei == null) {
                    continue;
                }
                if (nei.peerAdjState != rtrIsisNeigh.statUp) {
                    continue;
                }
                if (nei.level.level != lev) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * get default route
     *
     * @param other other afi
     * @return prefix
     */
    protected addrPrefix<addrIP> getDefaultRoute(boolean other) {
        if (other ^ (fwdCore.ipVersion == ipCor4.protocolVersion)) {
            return addrPrefix.ip4toIP(addrPrefix.defaultRoute4());
        } else {
            return addrPrefix.ip6toIP(addrPrefix.defaultRoute6());
        }
    }

    /**
     * read interface addresses
     *
     * @param other other afi
     * @param tlv tlv to read
     * @return addresses, null if nothing
     */
    protected tabGen<addrIP> getAddrIface(boolean other, typLenVal tlv) {
        tabGen<addrIP> l = new tabGen<addrIP>();
        addrIP adr = new addrIP();
        if (other ^ (fwdCore.ipVersion == ipCor4.protocolVersion)) {
            if (tlv.valTyp != rtrIsisLsp.tlvIpv4addr) {
                return null;
            }
            for (int i = 0; i < tlv.valSiz;) {
                addrIPv4 a = new addrIPv4();
                a.fromBuf(tlv.valDat, i);
                adr.fromIPv4addr(a);
                i += addrIPv4.size;
                l.add(adr);
            }
        } else {
            if (tlv.valTyp != rtrIsisLsp.tlvIpv6addr) {
                return null;
            }
            for (int i = 0; i < tlv.valSiz;) {
                addrIPv6 a = new addrIPv6();
                a.fromBuf(tlv.valDat, i);
                adr.fromIPv6addr(a);
                i += addrIPv6.size;
                l.add(adr);
            }
        }
        return l;
    }

    /**
     * read interface address
     *
     * @param other other afi
     * @param tlv tlv to read
     * @param trg where to save address
     */
    protected void getAddrIface(boolean other, typLenVal tlv, addrIP trg) {
        tabGen<addrIP> lst = getAddrIface(other, tlv);
        if (lst == null) {
            return;
        }
        addrIP adr = lst.get(0);
        if (adr == null) {
            return;
        }
        trg.fromBuf(adr.getBytes(), 0);
    }

    /**
     * write interface address
     *
     * @param other other afi
     * @param adr address to write
     * @return generated tlv
     */
    protected typLenVal putAddrIface(boolean other, addrIP adr) {
        typLenVal tlv = getTlv();
        if (other ^ (fwdCore.ipVersion == ipCor4.protocolVersion)) {
            addrIPv4 a = adr.toIPv4();
            tlv.valTyp = rtrIsisLsp.tlvIpv4addr;
            tlv.valSiz = addrIPv4.size;
            a.toBuffer(tlv.valDat, 0);
        } else {
            addrIPv6 a = adr.toIPv6();
            tlv.valTyp = rtrIsisLsp.tlvIpv6addr;
            tlv.valSiz = addrIPv6.size;
            a.toBuffer(tlv.valDat, 0);
        }
        return tlv;
    }

    private void getAddrReachS(typLenVal tlv, int pos, int len, tabRouteEntry<addrIP> prf) {
        packHolder pck = new packHolder(true, true);
        pck.putCopy(tlv.valDat, pos, 0, len);
        pck.putSkip(len);
        pck.merge2beg();
        tlv = getTlv();
        for (;;) {
            if (tlv.getBytes(pck)) {
                break;
            }
            if (tlv.valTyp == 1) { // tag
                prf.best.tag = bits.msbGetD(tlv.valDat, 0);
                continue;
            }
            rtrIsisSr.getPref(tlv, prf);
            rtrIsisBr.getPref(tlv, prf);
        }
    }

    private int getAddrReach4(typLenVal tlv, int pos, tabRouteEntry<addrIP> prf) {
        prf.best.metric = bits.msbGetD(tlv.valDat, pos + 0); // metric
        int i = bits.getByte(tlv.valDat, pos + 4); // prefix length
        if ((i & 0x80) != 0) { // updown bit
            prf.best.rouSrc |= 2;
        }
        int o = i & 0x3f;
        addrIPv4 adr = new addrIPv4();
        adr.fromBuf(tlv.valDat, pos + 5); // address
        prf.prefix = addrPrefix.ip4toIP(new addrPrefix<addrIPv4>(adr, o));
        pos += ((o + 7) / 8) + 5;
        if ((i & 0x40) == 0) { // subtlvs
            return pos;
        }
        o = bits.getByte(tlv.valDat, pos);
        getAddrReachS(tlv, pos + 1, o, prf);
        pos += o + 1;
        return pos;
    }

    private int getAddrReach6(typLenVal tlv, int pos, tabRouteEntry<addrIP> prf) {
        prf.best.metric = bits.msbGetD(tlv.valDat, pos + 0); // metric
        int i = bits.getByte(tlv.valDat, pos + 4); // flags
        if ((i & 0x80) != 0) { // updown bit
            prf.best.rouSrc |= 2;
        }
        if ((i & 0x40) != 0) { // external bit
            prf.best.rouSrc |= 1;
        }
        int o = bits.getByte(tlv.valDat, pos + 5); // prefix length
        addrIPv6 adr = new addrIPv6();
        adr.fromBuf(tlv.valDat, pos + 6); // address
        prf.prefix = addrPrefix.ip6toIP(new addrPrefix<addrIPv6>(adr, o));
        pos += ((o + 7) / 8) + 6;
        if ((i & 0x20) == 0) { // subtlvs
            return pos;
        }
        o = bits.getByte(tlv.valDat, pos);
        getAddrReachS(tlv, pos + 1, o, prf);
        pos += o + 1;
        return pos;
    }

    /**
     * read hostname
     *
     * @param tlv tlv to read
     * @return hostname, null if nothing
     */
    protected static String getHostname(typLenVal tlv) {
        if (tlv.valTyp != rtrIsisLsp.tlvHostName) {
            return null;
        }
        return tlv.getStr();
    }

    /**
     * read reachable addresses
     *
     * @param other other afi
     * @param tlv tlv to read
     * @return addresses, null if nothing
     */
    protected tabGen<tabRouteEntry<addrIP>> getAddrReach(boolean other, typLenVal tlv) {
        tabGen<tabRouteEntry<addrIP>> l = new tabGen<tabRouteEntry<addrIP>>();
        if (other ^ (fwdCore.ipVersion != ipCor4.protocolVersion)) {
            if (multiTopo) {
                if (tlv.valTyp != rtrIsisLsp.tlvMtIpv6reach) {
                    return null;
                }
                if ((bits.msbGetW(tlv.valDat, 0) & 0xfff) != getMTopoVal(other)) {
                    return null;
                }
                for (int i = 2; i < tlv.valSiz;) {
                    tabRouteEntry<addrIP> prf = new tabRouteEntry<addrIP>();
                    i = getAddrReach6(tlv, i, prf);
                    l.add(prf);
                }
                return l;
            }
            if (tlv.valTyp != rtrIsisLsp.tlvIpv6reach) {
                return null;
            }
            for (int i = 0; i < tlv.valSiz;) {
                tabRouteEntry<addrIP> prf = new tabRouteEntry<addrIP>();
                i = getAddrReach6(tlv, i, prf);
                l.add(prf);
            }
            return l;
        }
        if (multiTopo) {
            if (tlv.valTyp != rtrIsisLsp.tlvMtIpv4reach) {
                return null;
            }
            if ((bits.msbGetW(tlv.valDat, 0) & 0xfff) != getMTopoVal(other)) {
                return null;
            }
            for (int i = 2; i < tlv.valSiz;) {
                tabRouteEntry<addrIP> prf = new tabRouteEntry<addrIP>();
                i = getAddrReach4(tlv, i, prf);
                l.add(prf);
            }
            return l;
        }
        if (metricWide) {
            if (tlv.valTyp != rtrIsisLsp.tlvExtIpv4reach) {
                return null;
            }
            for (int i = 0; i < tlv.valSiz;) {
                tabRouteEntry<addrIP> prf = new tabRouteEntry<addrIP>();
                i = getAddrReach4(tlv, i, prf);
                l.add(prf);
            }
            return l;
        }
        int ext;
        switch (tlv.valTyp) {
            case rtrIsisLsp.tlvIpv4extReach:
                ext = 1;
                break;
            case rtrIsisLsp.tlvIpv4intReach:
                ext = 0;
                break;
            default:
                return null;
        }
        for (int i = 0; i < tlv.valSiz;) {
            tabRouteEntry<addrIP> prf = new tabRouteEntry<addrIP>();
            int o = bits.getByte(tlv.valDat, i + 0);
            prf.best.metric = o & 0x3f; // default metric
            prf.best.rouSrc = ext;
            if ((o & 0x80) != 0) { // updown bit
                prf.best.rouSrc |= 2;
            }
            addrIPv4 a1 = new addrIPv4();
            addrIPv4 a2 = new addrIPv4();
            a1.fromBuf(tlv.valDat, i + 4); // address
            a2.fromBuf(tlv.valDat, i + 8); // netmask
            i += 12;
            prf.prefix = addrPrefix.ip4toIP(new addrPrefix<addrIPv4>(a1, a2.toNetmask()));
            l.add(prf);
        }
        return l;
    }

    private void putAddrReach4(typLenVal tlv, int pos, addrPrefix<addrIPv4> prf, boolean down, int met, byte[] subs) {
        bits.msbPutD(tlv.valDat, pos + 0, met); // metric
        met = prf.maskLen;
        if (down) {
            met |= 0x80;
        }
        if (subs.length > 0) {
            met |= 0x40;
        }
        bits.putByte(tlv.valDat, pos + 4, met); // prefix length
        prf.network.toBuffer(tlv.valDat, pos + 5); // address
        tlv.valSiz = ((prf.maskLen + 7) / 8) + 5 + pos;
        if (subs.length < 1) {
            return;
        }
        tlv.valDat[tlv.valSiz] = (byte) subs.length; // length
        bits.byteCopy(subs, 0, tlv.valDat, tlv.valSiz + 1, subs.length); // value
        tlv.valSiz += subs.length + 1;
    }

    private void putAddrReach6(typLenVal tlv, int pos, addrPrefix<addrIPv6> prf, boolean ext, boolean down, int met, byte[] subs) {
        bits.msbPutD(tlv.valDat, pos + 0, met); // metric
        met = 0;
        if (down) {
            met |= 0x80;
        }
        if (ext) {
            met |= 0x40;
        }
        if (subs.length > 0) {
            met |= 0x20;
        }
        bits.putByte(tlv.valDat, pos + 4, met); // flags
        bits.putByte(tlv.valDat, pos + 5, prf.maskLen); // prefix length
        prf.network.toBuffer(tlv.valDat, pos + 6); // address
        tlv.valSiz = ((prf.maskLen + 7) / 8) + 6 + pos;
        if (subs.length < 1) {
            return;
        }
        tlv.valDat[tlv.valSiz] = (byte) subs.length; // length
        bits.byteCopy(subs, 0, tlv.valDat, tlv.valSiz + 1, subs.length); // value
        tlv.valSiz += subs.length + 1;
    }

    /**
     * write reachable address
     *
     * @param tag tag value
     * @return generated tlv
     */
    protected byte[] putAddrTag(int tag) {
        packHolder pck = new packHolder(true, true);
        typLenVal tlv = rtrIsis.getTlv();
        tlv.valTyp = 1;
        tlv.valSiz = 4;
        bits.msbPutD(tlv.valDat, 0, tag);
        tlv.putThis(pck);
        pck.merge2beg();
        return pck.getCopy();
    }

    /**
     * write reachable address
     *
     * @param other other afi
     * @param pref address to write
     * @param flg flags, 1=ext, 2=updown
     * @param met metric
     * @param subs subtlvs
     * @return generated tlv
     */
    protected typLenVal putAddrReach(boolean other, addrPrefix<addrIP> pref, int flg, int met, byte[] subs) {
        final boolean down = (flg & 2) != 0;
        final boolean ext = (flg & 1) != 0;
        typLenVal tlv = getTlv();
        if (other ^ (fwdCore.ipVersion != ipCor4.protocolVersion)) {
            if (multiTopo) {
                bits.msbPutW(tlv.valDat, 0, getMTopoVal(other));
                putAddrReach6(tlv, 2, addrPrefix.ip2ip6(pref), ext, down, met, subs);
                tlv.valTyp = rtrIsisLsp.tlvMtIpv6reach;
                return tlv;
            }
            putAddrReach6(tlv, 0, addrPrefix.ip2ip6(pref), ext, down, met, subs);
            tlv.valTyp = rtrIsisLsp.tlvIpv6reach;
            return tlv;
        }
        if (multiTopo) {
            bits.msbPutW(tlv.valDat, 0, getMTopoVal(other));
            putAddrReach4(tlv, 2, addrPrefix.ip2ip4(pref), down, met, subs);
            tlv.valTyp = rtrIsisLsp.tlvMtIpv4reach;
            return tlv;
        }
        if (metricWide) {
            putAddrReach4(tlv, 0, addrPrefix.ip2ip4(pref), down, met, subs);
            tlv.valTyp = rtrIsisLsp.tlvExtIpv4reach;
            return tlv;
        }
        met &= 0x3f;
        if (down) {
            met |= 0x80;
        }
        bits.putByte(tlv.valDat, 0, met); // default metric
        bits.putByte(tlv.valDat, 1, 0x80); // delay metric
        bits.putByte(tlv.valDat, 2, 0x80); // expense metric
        bits.putByte(tlv.valDat, 3, 0x80); // error metric
        addrPrefix<addrIPv4> a4 = addrPrefix.ip2ip4(pref);
        a4.network.toBuffer(tlv.valDat, 4); // address
        a4.mask.toBuffer(tlv.valDat, 8); // netmask
        tlv.valSiz = 12;
        if (ext) {
            tlv.valTyp = rtrIsisLsp.tlvIpv4extReach;
        } else {
            tlv.valTyp = rtrIsisLsp.tlvIpv4intReach;
        }
        return tlv;
    }

    private int getISneighE(typLenVal tlv, int pos, rtrIsisLsp nei) {
        nei.srcID.fromBuf(tlv.valDat, pos + 0); // neighbor id
        nei.nodID = bits.getByte(tlv.valDat, pos + 6); // pseudonode id
        nei.lspNum = bits.msbGetD(tlv.valDat, pos + 7) >>> 8; // metric
        pos += bits.getByte(tlv.valDat, pos + 10) + 11; // subtlvs
        return pos;
    }

    /**
     * read is neighbors
     *
     * @param tlv tlv to read
     * @return neighbors, null if nothing
     */
    protected tabGen<rtrIsisLsp> getISneigh(typLenVal tlv) {
        tabGen<rtrIsisLsp> l = new tabGen<rtrIsisLsp>();
        if (multiTopo) {
            switch (tlv.valTyp) {
                case rtrIsisLsp.tlvMtIsNeigh:
                case rtrIsisLsp.tlvMtNeighAttr:
                    break;
                default:
                    return null;
            }
            if ((bits.msbGetW(tlv.valDat, 0) & 0xfff) != getMTopoVal(false)) {
                return null;
            }
            for (int i = 2; i < tlv.valSiz;) {
                rtrIsisLsp n = new rtrIsisLsp();
                i = getISneighE(tlv, i, n);
                l.add(n);
            }
            return l;
        }
        if (metricWide) {
            switch (tlv.valTyp) {
                case rtrIsisLsp.tlvExtIsNeigh:
                case rtrIsisLsp.tlvIsNeighAttr:
                    break;
                default:
                    return null;
            }
            for (int i = 0; i < tlv.valSiz;) {
                rtrIsisLsp n = new rtrIsisLsp();
                i = getISneighE(tlv, i, n);
                l.add(n);
            }
            return l;
        }
        if (tlv.valTyp != rtrIsisLsp.tlvIsNeigh) {
            return null;
        }
        for (int i = 1; i < tlv.valSiz;) {
            rtrIsisLsp n = new rtrIsisLsp();
            n.lspNum = bits.getByte(tlv.valDat, i + 0) & 0x3f; // metric
            n.srcID.fromBuf(tlv.valDat, i + 4); // neighbor id
            n.nodID = bits.getByte(tlv.valDat, i + 10); // pseudonode id
            i += 11;
            l.add(n);
        }
        return l;
    }

    private void putISneighE(typLenVal tlv, int pos, addrIsis nei, int nod, int met, byte[] subs) {
        nei.toBuffer(tlv.valDat, pos + 0); // neighbor id
        bits.putByte(tlv.valDat, pos + 6, nod); // pseudonode id
        bits.msbPutD(tlv.valDat, pos + 7, (met << 8) | subs.length); // metric
        bits.byteCopy(subs, 0, tlv.valDat, pos + 11, subs.length);
        tlv.valSiz = 11 + subs.length + pos;
    }

    /**
     * write is neighbor
     *
     * @param nei neighbor address
     * @param nod node address
     * @param met metric
     * @param subs subtlvs
     * @return generated tlv
     */
    protected typLenVal putISneigh(addrIsis nei, int nod, int met, byte[] subs) {
        typLenVal tlv = getTlv();
        if (multiTopo) {
            bits.msbPutW(tlv.valDat, 0, getMTopoVal(false));
            putISneighE(tlv, 2, nei, nod, met, subs);
            tlv.valTyp = rtrIsisLsp.tlvMtIsNeigh;
            return tlv;
        }
        if (metricWide) {
            putISneighE(tlv, 0, nei, nod, met, subs);
            tlv.valTyp = rtrIsisLsp.tlvExtIsNeigh;
            return tlv;
        }
        bits.putByte(tlv.valDat, 0, 0); // reserved
        bits.putByte(tlv.valDat, 1, met & 0x3f); // default metric
        bits.putByte(tlv.valDat, 2, 0x80); // delay metric
        bits.putByte(tlv.valDat, 3, 0x80); // expense metric
        bits.putByte(tlv.valDat, 4, 0x80); // error metric
        nei.toBuffer(tlv.valDat, 5); // neighbor id
        bits.putByte(tlv.valDat, 11, nod); // pseudonode id
        tlv.valTyp = rtrIsisLsp.tlvIsNeigh;
        tlv.valSiz = 12;
        return tlv;
    }

    /**
     * read is alias
     *
     * @param tlv tlv to read
     * @return neighbor address, null if nothing
     */
    protected addrIsis getISalias(typLenVal tlv) {
        if (tlv.valTyp != rtrIsisLsp.tlvIsAlias) {
            return null;
        }
        addrIsis adr = new addrIsis();
        adr.fromBuf(tlv.valDat, 0); // neighbor id
        return adr;
    }

    /**
     * write is alias
     *
     * @param nei neighbor address
     * @return generated tlv
     */
    protected typLenVal putISalias(addrIsis nei) {
        typLenVal tlv = getTlv();
        nei.toBuffer(tlv.valDat, 0); // neighbor id
        bits.putByte(tlv.valDat, 6, 0); // subtlvs
        tlv.valTyp = rtrIsisLsp.tlvIsAlias;
        tlv.valSiz = 7;
        return tlv;
    }

    /**
     * find a free circuit id
     *
     * @param p2p point2point link
     * @return circuit id
     */
    protected int getCircuitId(boolean p2p) {
        if (p2p) {
            return 1;
        }
        int i;
        for (;;) {
            i = bits.randomB();
            if (i <= 1) {
                continue;
            }
            if (i >= 255) {
                continue;
            }
            int p = -1;
            for (int o = 0; o < ifaces.size(); o++) {
                rtrIsisIface ntry = ifaces.get(o);
                if (ntry == null) {
                    continue;
                }
                if (ntry.circuitID == i) {
                    p = o;
                }
            }
            if (p < 0) {
                return i;
            }
        }
    }

    /**
     * create computed
     */
    public synchronized void routerCreateComputed() {
        if (debugger.rtrIsisEvnt) {
            logger.debug("create table");
        }
        tabRoute<addrIP> tab1 = new tabRoute<addrIP>("isis");
        tabRoute<addrIP> tab2 = new tabRoute<addrIP>("isis");
        tab1.mergeFrom(tabRoute.addType.ecmp, level1.routes, null, true, tabRouteAttr.distanLim);
        tab1.mergeFrom(tabRoute.addType.ecmp, level2.routes, null, true, tabRouteAttr.distanLim);
        if (other.enabled) {
            tab2.mergeFrom(tabRoute.addType.ecmp, level1.oroutes, null, true, tabRouteAttr.distanLim);
            tab2.mergeFrom(tabRoute.addType.ecmp, level2.oroutes, null, true, tabRouteAttr.distanLim);
        }
        if (segrouLab != null) {
            for (int i = 0; i < segrouLab.length; i++) {
                boolean b = false;
                if (level1.segrouUsd != null) {
                    b |= level1.segrouUsd[i];
                }
                if (level2.segrouUsd != null) {
                    b |= level2.segrouUsd[i];
                }
                if (!b) {
                    segrouLab[i].setFwdDrop(7);
                }
            }
        }
        if (bierLab != null) {
            int o = 0;
            int p = 0;
            for (int i = ifaces.size() - 1; i >= 0; i--) {
                rtrIsisIface ifc = ifaces.get(i);
                if (ifc == null) {
                    continue;
                }
                if (ifc.brIndex > 0) {
                    o = ifc.brIndex;
                }
                if (ifc.brOthIdx > 0) {
                    p = ifc.brOthIdx;
                }
            }
            tabLabelBier res = new tabLabelBier(bierLab[0].label, tabLabelBier.num2bsl(bierLen));
            res.idx = o;
            res.idx2 = p;
            res.mergeFrom(level1.bierRes);
            res.mergeFrom(level2.bierRes);
            for (int i = 0; i < bierLab.length; i++) {
                bierLab[i].setBierMpls(19, fwdCore, res);
            }
        }
        tab1.setProto(routerProtoTyp, routerProcNum);
        tab1.preserveTime(routerComputedU);
        routerComputedU = tab1;
        routerComputedM = tab1;
        fwdCore.routerChg(this);
        tab2.setProto(routerProtoTyp, routerProcNum);
        tab2.preserveTime(other.routerComputedU);
        other.routerComputedU = tab2;
        other.routerComputedM = tab2;
        other.fwd.routerChg(other);
    }

    /**
     * redistribution changed
     */
    public void routerRedistChanged() {
        genLsps(3);
    }

    /**
     * others changed
     */
    public void routerOthersChanged() {
    }

    /**
     * get help
     *
     * @param l list
     */
    public void routerGetHelp(userHelping l) {
        l.add("1 2   net-id                      specify network entity title");
        l.add("2 .     <addr>                    router id");
        l.add("1 2   traffeng-id                 specify traffic engineering id");
        l.add("2 .     <addr>                    te id");
        l.add("1 2   is-type                     specify is type");
        l.add("2 .     level1                    station router");
        l.add("2 .     level2                    area router");
        l.add("2 .     both                      area and station router");
        l.add("1 2   afi-other                   select other to advertise");
        l.add("2 .     enable                    enable processing");
        l.add("2 3   distance                    specify default distance");
        l.add("3 4     <num>                     intra-area distance");
        l.add("4 .       <num>                   external distance");
        cfgRtr.getRedistHelp(l, 1);
        l.add("1 2   max-area-addrs              maximum area addresses");
        l.add("2 .     <num>                     number of addresses");
        l.add("1 2   distance                    specify default distance");
        l.add("2 3     <num>                     intra-area distance");
        l.add("3 .       <num>                   external distance");
        l.add("1 .   metric-wide                 advertise wide metrics");
        l.add("1 .   multi-topology              advertise multi topology");
        l.add("1 2   segrout                     segment routing parameters");
        l.add("2 3,.   <num>                     maximum index");
        l.add("3 4       base                    specify base");
        l.add("4 3,.       <num>                 label base");
        l.add("1 2   bier                        bier parameters");
        l.add("2 3     <num>                     bitstring length");
        l.add("3 .       <num>                   maximum index");
        l.add("1 2   level1                      change level1 parameters");
        l.add("1 2   level2                      change level2 parameters");
        l.add("1 2   both                        change l1 and l2 parameters");
        l.add("2 .     spf-bidir                 spf bidir check");
        l.add("2 .     spf-topolog               spf topology logging");
        l.add("2 .     spf-hops                  spf hops disallow");
        l.add("2 .     spf-ecmp                  spf ecmp allow");
        l.add("2 3     spf-log                   spf log size");
        l.add("3 .       <num>                   number of entries");
        l.add("2 3     lsp-mtu                   maximum lsp size");
        l.add("3 .       <num>                   size of lsp in bytes");
        l.add("2 3     lsp-password              lsp authentication");
        l.add("3 .       <text>                  text to use");
        l.add("2 3     lsp-refresh               lsp refresh time");
        l.add("3 .       <num>                   age in ms");
        l.add("2 3     lsp-lifetime              lsp life time");
        l.add("3 .       <num>                   age in ms");
        l.add("2 .     set-overload              signal that exclude from spf");
        l.add("2 .     set-attached              signal that route all packets");
        l.add("2 .     allow-attached            accept others signal that route all packets");
        l.add("2 .     clear-attached            never signal that route all packets");
        l.add("2 .     traffeng                  configure for traffic engineering");
        l.add("2 .     segrout                   configure for segment routing");
        l.add("2 .     bier                      configure for bier");
        l.add("2 .     hostname                  advertise hostname");
        l.add("2 .     inter-level               advertise inter-level routes");
        l.add("2 .     default-originate         advertise default route");
        l.add("2 3     route-map-from            process prefixes from this level");
        l.add("3 .       <name>                  name of route map");
        l.add("2 3     route-map-into            process prefixes into this level");
        l.add("3 .       <name>                  name of route map");
        l.add("2 3     route-policy-from         process prefixes from this level");
        l.add("3 .       <name>                  name of route policy");
        l.add("2 3     route-policy-into         process prefixes into this level");
        l.add("3 .       <name>                  name of route policy");
        l.add("2 3     prefix-list-from          filter prefixes from this level");
        l.add("3 .       <name>                  name of prefix list");
        l.add("2 3     prefix-list-into          filter prefixes into this level");
        l.add("3 .       <name>                  name of prefix list");
        l.add("2 .     other-default-originate   advertise default route");
        l.add("2 3     other-route-map-from      process prefixes from this level");
        l.add("3 .       <name>                  name of route map");
        l.add("2 3     other-route-map-into      process prefixes into this level");
        l.add("3 .       <name>                  name of route map");
        l.add("2 3     other-route-policy-from   process prefixes from this level");
        l.add("3 .       <name>                  name of route policy");
        l.add("2 3     other-route-policy-into   process prefixes into this level");
        l.add("3 .       <name>                  name of route policy");
        l.add("2 3     other-prefix-list-from    filter prefixes from this level");
        l.add("3 .       <name>                  name of prefix list");
        l.add("2 3     other-prefix-list-into    filter prefixes into this level");
        l.add("3 .       <name>                  name of prefix list");
    }

    /**
     * get config
     *
     * @param l list
     * @param beg beginning
     * @param filter filter
     */
    public void routerGetConfig(List<String> l, String beg, int filter) {
        l.add(beg + "net-id " + netEntTit);
        l.add(beg + "traffeng-id " + traffEngID);
        l.add(beg + "is-type " + level2string(operateLevel));
        l.add(beg + "max-area-addrs " + maxAreaAddr);
        cmds.cfgLine(l, !metricWide, beg, "metric-wide", "");
        cmds.cfgLine(l, !multiTopo, beg, "multi-topology", "");
        String a = "";
        if (segrouBase != 0) {
            a += " base " + segrouBase;
        }
        cmds.cfgLine(l, segrouMax < 1, beg, "segrout", "" + segrouMax + a);
        cmds.cfgLine(l, bierMax < 1, beg, "bier", bierLen + " " + bierMax);
        l.add(beg + "distance " + distantInt + " " + distantExt);
        getConfig(level2, l, beg, filter);
        getConfig(level1, l, beg, filter);
        other.getConfig(l, beg + "afi-other ");
    }

    /**
     * configure
     *
     * @param cmd command
     * @return false if success, true if error
     */
    public boolean routerConfigure(cmds cmd) {
        String s = cmd.word();
        if (s.equals("net-id")) {
            netEntTit.fromString(cmd.word());
            areaID = netEntTit.getArea();
            routerID = netEntTit.getNode();
            genLsps(3);
            return false;
        }
        if (s.equals("traffeng-id")) {
            traffEngID.fromString(cmd.word());
            genLsps(3);
            return false;
        }
        if (s.equals("distance")) {
            distantInt = bits.str2num(cmd.word());
            distantExt = bits.str2num(cmd.word());
            return false;
        }
        if (s.equals("is-type")) {
            operateLevel = string2level(cmd.word());
            genLsps(3);
            return false;
        }
        if (s.equals("max-area-addrs")) {
            maxAreaAddr = bits.str2num(cmd.word());
            return false;
        }
        if (s.equals("metric-wide")) {
            metricWide = true;
            genLsps(3);
            return false;
        }
        if (s.equals("multi-topology")) {
            multiTopo = true;
            metricWide = true;
            genLsps(3);
            return false;
        }
        if (s.equals("segrout")) {
            tabLabel.release(segrouLab, 7);
            segrouMax = bits.str2num(cmd.word());
            segrouBase = 0;
            for (;;) {
                s = cmd.word();
                if (s.length() < 1) {
                    break;
                }
                if (s.equals("base")) {
                    segrouBase = bits.str2num(cmd.word());
                    continue;
                }
            }
            segrouLab = tabLabel.allocate(7, segrouBase, segrouMax);
            genLsps(3);
            return false;
        }
        if (s.equals("bier")) {
            tabLabel.release(bierLab, 19);
            bierLen = tabLabelBier.normalizeBsl(bits.str2num(cmd.word()));
            bierMax = bits.str2num(cmd.word());
            bierLab = tabLabel.allocate(19, (bierMax + bierLen - 1) / bierLen);
            genLsps(3);
            return false;
        }
        if (s.equals("level1")) {
            return doConfig(level1, cmd, false);
        }
        if (s.equals("level2")) {
            return doConfig(level2, cmd, false);
        }
        if (s.equals("both")) {
            cmds c = cmd.copyBytes(false);
            boolean res = doConfig(level1, cmd, false);
            return res | doConfig(level2, c, false);
        }
        if (s.equals("afi-other")) {
            s = cmd.word();
            if (s.equals("enable")) {
                other.register2ip();
                genLsps(3);
                return false;
            }
            if (s.equals("distance")) {
                other.distantInt = bits.str2num(cmd.word());
                other.distantExt = bits.str2num(cmd.word());
                return false;
            }
            if (cfgRtr.doCfgRedist(other, false, s, cmd)) {
                cmd.badCmd();
            }
            genLsps(3);
            return false;
        }
        if (!s.equals("no")) {
            return true;
        }
        s = cmd.word();
        if (s.equals("metric-wide")) {
            metricWide = false;
            multiTopo = false;
            genLsps(3);
            return false;
        }
        if (s.equals("multi-topology")) {
            multiTopo = false;
            genLsps(3);
            return false;
        }
        if (s.equals("segrout")) {
            tabLabel.release(segrouLab, 7);
            segrouLab = null;
            segrouMax = 0;
            segrouBase = 0;
            genLsps(3);
            return false;
        }
        if (s.equals("bier")) {
            tabLabel.release(bierLab, 19);
            bierLab = null;
            bierLen = 0;
            bierMax = 0;
            genLsps(3);
            return false;
        }
        if (s.equals("level1")) {
            return doConfig(level1, cmd, true);
        }
        if (s.equals("level2")) {
            return doConfig(level2, cmd, true);
        }
        if (s.equals("both")) {
            cmds c = cmd.copyBytes(false);
            boolean res = doConfig(level1, cmd, true);
            return res | doConfig(level2, c, true);
        }
        if (s.equals("afi-other")) {
            s = cmd.word();
            if (s.equals("enable")) {
                other.unregister2ip();
                genLsps(3);
                return false;
            }
            if (cfgRtr.doCfgRedist(other, true, s, cmd)) {
                cmd.badCmd();
            }
            genLsps(3);
            return false;
        }
        return true;
    }

    /**
     * get configuration
     *
     * @param lev level to use
     * @param l list to update
     * @param beg beginning string
     * @param filter filter defaults
     */
    public void getConfig(rtrIsisLevel lev, List<String> l, String beg, int filter) {
        String s = "level" + lev.level + " ";
        l.add(beg + s + "spf-log " + lev.lastSpf.logSize);
        cmds.cfgLine(l, lev.lastSpf.topoLog.get() == 0, beg, s + "spf-topolog", "");
        cmds.cfgLine(l, lev.lastSpf.bidir.get() == 0, beg, s + "spf-bidir", "");
        cmds.cfgLine(l, lev.lastSpf.hops.get() == 0, beg, s + "spf-hops", "");
        cmds.cfgLine(l, lev.lastSpf.ecmp.get() == 0, beg, s + "spf-ecmp", "");
        cmds.cfgLine(l, !lev.overloaded, beg, s + "set-overload", "");
        cmds.cfgLine(l, !lev.attachedSet, beg, s + "set-attached", "");
        cmds.cfgLine(l, !lev.attachedClr, beg, s + "clear-attached", "");
        cmds.cfgLine(l, !lev.attachedAlw, beg, s + "allow-attached", "");
        cmds.cfgLine(l, !lev.traffEng, beg, s + "traffeng", "");
        cmds.cfgLine(l, !lev.segrouEna, beg, s + "segrout", "");
        cmds.cfgLine(l, !lev.bierEna, beg, s + "bier", "");
        cmds.cfgLine(l, !lev.hostname, beg, s + "hostname", "");
        cmds.cfgLine(l, !lev.interLevels, beg, s + "inter-level", "");
        cmds.cfgLine(l, !lev.defOrigin, beg, s + "default-originate", "");
        cmds.cfgLine(l, !lev.odefOrigin, beg, s + "other-default-originate", "");
        l.add(beg + s + "lsp-mtu " + lev.maxLspSize);
        cmds.cfgLine(l, lev.lspPassword == null, beg, s + "lsp-password", authLocal.passwdEncode(lev.lspPassword, (filter & 2) != 0));
        l.add(beg + s + "lsp-refresh " + lev.lspRefresh);
        l.add(beg + s + "lsp-lifetime " + lev.lspLifetime);
        cmds.cfgLine(l, lev.prflstFrom == null, beg, s + "prefix-list-from", "" + lev.prflstFrom);
        cmds.cfgLine(l, lev.prflstInto == null, beg, s + "prefix-list-into", "" + lev.prflstInto);
        cmds.cfgLine(l, lev.roumapFrom == null, beg, s + "route-map-from", "" + lev.roumapFrom);
        cmds.cfgLine(l, lev.roumapInto == null, beg, s + "route-map-into", "" + lev.roumapInto);
        cmds.cfgLine(l, lev.roupolFrom == null, beg, s + "route-policy-from", "" + lev.roupolFrom);
        cmds.cfgLine(l, lev.roupolInto == null, beg, s + "route-policy-into", "" + lev.roupolInto);
        cmds.cfgLine(l, lev.oprflstFrom == null, beg, s + "other-prefix-list-from", "" + lev.oprflstFrom);
        cmds.cfgLine(l, lev.oprflstInto == null, beg, s + "other-prefix-list-into", "" + lev.oprflstInto);
        cmds.cfgLine(l, lev.oroumapFrom == null, beg, s + "other-route-map-from", "" + lev.oroumapFrom);
        cmds.cfgLine(l, lev.oroumapInto == null, beg, s + "other-route-map-into", "" + lev.oroumapInto);
        cmds.cfgLine(l, lev.oroupolFrom == null, beg, s + "other-route-policy-from", "" + lev.oroupolFrom);
        cmds.cfgLine(l, lev.oroupolInto == null, beg, s + "other-route-policy-into", "" + lev.oroupolInto);
    }

    /**
     * do configuration
     *
     * @param lev level to use
     * @param cmd command to do
     * @param negated negated
     * @return result
     */
    public boolean doConfig(rtrIsisLevel lev, cmds cmd, boolean negated) {
        String s = cmd.word();
        if (s.equals("spf-log")) {
            lev.lastSpf.logSize.set(bits.str2num(cmd.word()));
            if (negated) {
                lev.lastSpf.logSize.set(0);
            }
            return false;
        }
        if (s.equals("spf-topolog")) {
            if (negated) {
                lev.lastSpf.topoLog.set(0);
            } else {
                lev.lastSpf.topoLog.set(1);
            }
            return false;
        }
        if (s.equals("spf-bidir")) {
            if (negated) {
                lev.lastSpf.bidir.set(0);
            } else {
                lev.lastSpf.bidir.set(1);
            }
            lev.schedWork(3);
            return false;
        }
        if (s.equals("spf-hops")) {
            if (negated) {
                lev.lastSpf.hops.set(0);
            } else {
                lev.lastSpf.hops.set(1);
            }
            lev.schedWork(3);
            return false;
        }
        if (s.equals("spf-ecmp")) {
            if (negated) {
                lev.lastSpf.ecmp.set(0);
            } else {
                lev.lastSpf.ecmp.set(1);
            }
            lev.schedWork(3);
            return false;
        }
        if (s.equals("set-overload")) {
            lev.overloaded = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("set-attached")) {
            lev.attachedSet = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("allow-attached")) {
            lev.attachedAlw = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("clear-attached")) {
            lev.attachedClr = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("lsp-mtu")) {
            lev.maxLspSize = bits.str2num(cmd.word());
            lev.schedWork(3);
            return false;
        }
        if (s.equals("lsp-password")) {
            if (negated) {
                lev.lspPassword = null;
            } else {
                lev.lspPassword = authLocal.passwdDecode(cmd.word());
            }
            lev.schedWork(3);
            return false;
        }
        if (s.equals("lsp-refresh")) {
            lev.lspRefresh = bits.str2num(cmd.word());
            lev.schedWork(3);
            return false;
        }
        if (s.equals("lsp-lifetime")) {
            lev.lspLifetime = bits.str2num(cmd.word());
            lev.schedWork(3);
            return false;
        }
        if (s.equals("traffeng")) {
            lev.traffEng = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("segrout")) {
            lev.segrouEna = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("bier")) {
            lev.bierEna = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("hostname")) {
            lev.hostname = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("default-originate")) {
            lev.defOrigin = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("other-default-originate")) {
            lev.odefOrigin = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("inter-level")) {
            lev.interLevels = !negated;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("prefix-list-from")) {
            if (negated) {
                lev.prflstFrom = null;
                lev.schedWork(7);
                return false;
            }
            cfgPrfxlst ntry = cfgAll.prfxFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such prefix list");
                return false;
            }
            lev.prflstFrom = ntry.prflst;
            lev.schedWork(7);
            return false;
        }
        if (s.equals("prefix-list-into")) {
            if (negated) {
                lev.prflstInto = null;
                lev.schedWork(3);
                return false;
            }
            cfgPrfxlst ntry = cfgAll.prfxFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such prefix list");
                return false;
            }
            lev.prflstInto = ntry.prflst;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("route-map-from")) {
            if (negated) {
                lev.roumapFrom = null;
                lev.schedWork(7);
                return false;
            }
            cfgRoump ntry = cfgAll.rtmpFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such route map");
                return false;
            }
            lev.roumapFrom = ntry.roumap;
            lev.schedWork(7);
            return false;
        }
        if (s.equals("route-map-into")) {
            if (negated) {
                lev.roumapInto = null;
                lev.schedWork(3);
                return false;
            }
            cfgRoump ntry = cfgAll.rtmpFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such route map");
                return false;
            }
            lev.roumapInto = ntry.roumap;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("route-policy-from")) {
            if (negated) {
                lev.roupolFrom = null;
                lev.schedWork(7);
                return false;
            }
            cfgRouplc ntry = cfgAll.rtplFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such route policy");
                return false;
            }
            lev.roupolFrom = ntry.rouplc;
            lev.schedWork(7);
            return false;
        }
        if (s.equals("route-policy-into")) {
            if (negated) {
                lev.roupolInto = null;
                lev.schedWork(3);
                return false;
            }
            cfgRouplc ntry = cfgAll.rtplFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such route policy");
                return false;
            }
            lev.roupolInto = ntry.rouplc;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("other-prefix-list-from")) {
            if (negated) {
                lev.oprflstFrom = null;
                lev.schedWork(7);
                return false;
            }
            cfgPrfxlst ntry = cfgAll.prfxFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such prefix list");
                return false;
            }
            lev.oprflstFrom = ntry.prflst;
            lev.schedWork(7);
            return false;
        }
        if (s.equals("other-prefix-list-into")) {
            if (negated) {
                lev.oprflstInto = null;
                lev.schedWork(3);
                return false;
            }
            cfgPrfxlst ntry = cfgAll.prfxFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such prefix list");
                return false;
            }
            lev.oprflstInto = ntry.prflst;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("other-route-map-from")) {
            if (negated) {
                lev.oroumapFrom = null;
                lev.schedWork(7);
                return false;
            }
            cfgRoump ntry = cfgAll.rtmpFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such route map");
                return false;
            }
            lev.oroumapFrom = ntry.roumap;
            lev.schedWork(7);
            return false;
        }
        if (s.equals("other-route-map-into")) {
            if (negated) {
                lev.oroumapInto = null;
                lev.schedWork(3);
                return false;
            }
            cfgRoump ntry = cfgAll.rtmpFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such route map");
                return false;
            }
            lev.oroumapInto = ntry.roumap;
            lev.schedWork(3);
            return false;
        }
        if (s.equals("other-route-policy-from")) {
            if (negated) {
                lev.oroupolFrom = null;
                lev.schedWork(7);
                return false;
            }
            cfgRouplc ntry = cfgAll.rtplFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such route policy");
                return false;
            }
            lev.oroupolFrom = ntry.rouplc;
            lev.schedWork(7);
            return false;
        }
        if (s.equals("other-route-policy-into")) {
            if (negated) {
                lev.oroupolInto = null;
                lev.schedWork(3);
                return false;
            }
            cfgRouplc ntry = cfgAll.rtplFind(cmd.word(), false);
            if (ntry == null) {
                cmd.error("no such route policy");
                return false;
            }
            lev.oroupolInto = ntry.rouplc;
            lev.schedWork(3);
            return false;
        }
        return true;
    }

    /**
     * stop work
     */
    public void routerCloseNow() {
        level1.stopNow();
        level2.stopNow();
        other.unregister2ip();
        for (int i = 0; i < ifaces.size(); i++) {
            rtrIsisIface ntry = ifaces.get(i);
            if (ntry == null) {
                continue;
            }
            ntry.restartTimer(true);
            ntry.unregister2eth();
            ntry.closeNeighbors();
        }
        tabLabel.release(segrouLab, 7);
        tabLabel.release(bierLab, 19);
    }

    /**
     * generate lsps in all areas
     *
     * @param todo todo to pass
     */
    protected void genLsps(int todo) {
        todo &= 3;
        level1.schedWork(todo);
        level2.schedWork(todo);
    }

    /**
     * add isis interface
     *
     * @param iface forwarding interface
     * @param otherifc other forwarding interface
     * @param eth ethertype interface
     * @return interface handler
     */
    public rtrIsisIface addInterface(ipFwdIface iface, ipFwdIface otherifc, ifcEthTyp eth) {
        if (iface == null) {
            return null;
        }
        rtrIsisIface ifc = new rtrIsisIface(this, iface, otherifc, eth);
        rtrIsisIface old = ifaces.add(ifc);
        if (old != null) {
            return old;
        }
        ifc.register2eth();
        ifc.restartTimer(false);
        genLsps(3);
        return ifc;
    }

    /**
     * delete isis interface
     *
     * @param iface forwarding interface
     * @param otherifc other forwarding interface
     */
    public void delInterface(ipFwdIface iface, ipFwdIface otherifc) {
        rtrIsisIface ifc = new rtrIsisIface(this, iface, otherifc, null);
        ifc = ifaces.del(ifc);
        if (ifc == null) {
            return;
        }
        ifc.restartTimer(true);
        ifc.unregister2eth();
        ifc.closeNeighbors();
        genLsps(3);
    }

    /**
     * list neighbors
     *
     * @return list of neighbors
     */
    public userFormat showNeighs() {
        userFormat l = new userFormat("|", "interface|mac address|level|routerid|ip address|other address|state|uptime");
        for (int o = 0; o < ifaces.size(); o++) {
            rtrIsisIface ifc = ifaces.get(o);
            if (ifc == null) {
                continue;
            }
            for (int i = 0; i < ifc.neighs.size(); i++) {
                rtrIsisNeigh nei = ifc.neighs.get(i);
                if (nei == null) {
                    continue;
                }
                l.add(ifc.upper + "|" + nei.ethAddr + "|" + nei.level.level + "|" + nei.rtrID + "|" + nei.ifcAddr + "|" + nei.ofcAddr + "|" + nei.peerAdjState + "|" + bits.timePast(nei.upTime));
            }
        }
        return l;
    }

    /**
     * find neighbor
     *
     * @param adr address
     * @param lev level
     * @return neighbor
     */
    public rtrIsisNeigh findNeigh(addrIP adr, int lev) {
        for (int o = 0; o < ifaces.size(); o++) {
            rtrIsisIface ifc = ifaces.get(o);
            if (ifc == null) {
                continue;
            }
            for (int i = 0; i < ifc.neighs.size(); i++) {
                rtrIsisNeigh nei = ifc.neighs.get(i);
                if (nei == null) {
                    continue;
                }
                if (nei.level.level != lev) {
                    continue;
                }
                if (adr.compare(adr, nei.ifcAddr) == 0) {
                    return nei;
                }
            }
        }
        return null;
    }

    /**
     * list interfaces
     *
     * @return list of interfaces
     */
    public userFormat showIfaces() {
        userFormat l = new userFormat("|", "interface|neighbors");
        for (int i = 0; i < ifaces.size(); i++) {
            rtrIsisIface ifc = ifaces.get(i);
            l.add(ifc.iface + "|" + ifc.neighs.size());
        }
        return l;
    }

    /**
     * list database
     *
     * @param level level number
     * @param cmd entry to find
     * @return list of entry
     */
    public List<String> showDatabase(int level, cmds cmd) {
        addrIsis ned = new addrIsis();
        ned.fromString(cmd.word());
        rtrIsisLevel lev = getLevel(level);
        List<String> l = new ArrayList<String>();
        for (int i = 0; i < lev.lsps.size(); i++) {
            rtrIsisLsp ntry = lev.lsps.get(i);
            if (ntry == null) {
                continue;
            }
            if (ned.compare(ned, ntry.srcID) != 0) {
                continue;
            }
            l.add("" + ntry);
            packHolder pck = new packHolder(true, true);
            pck.putSkip(ntry.writeData(pck, 0));
            pck.merge2beg();
            userFlash.buf2hex(l, pck.getCopy(), 0);
            rtrIsisDump.dumpLsp(l, pck);
        }
        return l;
    }

    /**
     * list database
     *
     * @param level level number
     * @return list of database
     */
    public userFormat showDatabase(int level) {
        userFormat l = new userFormat("|", "lspid|sequence|flags|len|time");
        rtrIsisLevel lev = getLevel(level);
        for (int i = 0; i < lev.lsps.size(); i++) {
            rtrIsisLsp ntry = lev.lsps.get(i);
            if (ntry == null) {
                continue;
            }
            l.add("" + ntry);
        }
        return l;
    }

    /**
     * list routes
     *
     * @param level level number
     * @return list of routes
     */
    public tabRoute<addrIP> showRoute(int level) {
        rtrIsisLevel lev = getLevel(level);
        return lev.routes;
    }

    /**
     * list other routes
     *
     * @param level level number
     * @return list of routes
     */
    public tabRoute<addrIP> showOroute(int level) {
        rtrIsisLevel lev = getLevel(level);
        return lev.oroutes;
    }

    /**
     * show spf
     *
     * @param level level number
     * @return log of spf
     */
    public userFormat showSpfStat(int level) {
        rtrIsisLevel lev = getLevel(level);
        return lev.lastSpf.listStatistics();
    }

    /**
     * show spf
     *
     * @param level level number
     * @param cmd entry to find
     * @return log of spf
     */
    public userFormat showSpfTopo(int level, cmds cmd) {
        rtrIsisLevel lev = getLevel(level);
        if (cmd.size() < 1) {
            return lev.lastSpf.listTopology();
        }
        rtrIsisLevelSpf ned = new rtrIsisLevelSpf(new addrIsis(), 0);
        ned.fromString(cmd.word());
        return lev.lastSpf.listTopology(ned);
    }

    /**
     * show log
     *
     * @param level level number
     * @return log of spf
     */
    public userFormat showSpfLog(int level) {
        rtrIsisLevel lev = getLevel(level);
        return lev.lastSpf.listUsages();
    }

    /**
     * show tree
     *
     * @param level level number
     * @return tree of spf
     */
    public List<String> showSpfTree(int level) {
        rtrIsisLevel lev = getLevel(level);
        return lev.lastSpf.listTree();
    }

    /**
     * show tree
     *
     * @param level level number
     * @param cmd entry to find
     * @return tree of spf
     */
    public List<String> showSpfOtherTree(int level, cmds cmd) {
        rtrIsisLevel lev = getLevel(level);
        shrtPthFrst<rtrIsisLevelSpf> spf = lev.lastSpf.copyBytes();
        rtrIsisLevelSpf ned = new rtrIsisLevelSpf(new addrIsis(), 0);
        ned.fromString(cmd.word());
        spf.doCalc(ned, null);
        return spf.listTree();
    }

    /**
     * show topology
     *
     * @param level level number
     * @param cmd entry to find
     * @return log of spf
     */
    public userFormat showSpfOtherTopo(int level, cmds cmd) {
        rtrIsisLevel lev = getLevel(level);
        shrtPthFrst<rtrIsisLevelSpf> spf = lev.lastSpf.copyBytes();
        rtrIsisLevelSpf ned = new rtrIsisLevelSpf(new addrIsis(), 0);
        ned.fromString(cmd.word());
        spf.doCalc(ned, null);
        if (cmd.size() < 1) {
            return spf.listTopology();
        }
        ned = new rtrIsisLevelSpf(new addrIsis(), 0);
        ned.fromString(cmd.word());
        return spf.listTopology(ned);
    }

    /**
     * show graph
     *
     * @param level level number
     * @return graph of spf
     */
    public List<String> showSpfGraph(int level) {
        rtrIsisLevel lev = getLevel(level);
        return lev.lastSpf.listGraphviz();
    }

    /**
     * get neighbor count
     *
     * @return count
     */
    public int routerNeighCount() {
        int o = 0;
        for (int i = 0; i < ifaces.size(); i++) {
            o += ifaces.get(i).neighs.size();
        }
        return o;
    }

    /**
     * list neighbors
     *
     * @param tab list
     */
    public void routerNeighList(tabRoute<addrIP> tab) {
        for (int o = 0; o < ifaces.size(); o++) {
            rtrIsisIface ifc = ifaces.get(o);
            if (ifc == null) {
                continue;
            }
            if (ifc.iface.lower.getState() != state.states.up) {
                continue;
            }
            for (int i = 0; i < ifc.neighs.size(); i++) {
                rtrIsisNeigh nei = ifc.neighs.get(i);
                if (nei == null) {
                    continue;
                }
                tabRouteEntry<addrIP> ntry = new tabRouteEntry<addrIP>();
                ntry.prefix = new addrPrefix<addrIP>(nei.ifcAddr, addrIP.size * 8);
                tabRoute.addUpdatedEntry(tabRoute.addType.better, tab, rtrBgpUtil.sfiUnicast, 0, ntry, true, null, null, routerAutoMesh);
            }
        }
    }

    /**
     * get interface count
     *
     * @return count
     */
    public int routerIfaceCount() {
        return ifaces.size();
    }

    /**
     * get list of link states
     *
     * @param tab table to update
     * @param level level number
     * @param asn asn
     * @param adv advertiser
     */
    public void routerLinkStates(tabRoute<addrIP> tab, int level, int asn, addrIPv4 adv) {
        rtrIsisLevel lev = getLevel(level);
        lev.lastSpf.listLinkStates(tab, lev.level, -1, asn, adv, addrIsis.size);
    }

}

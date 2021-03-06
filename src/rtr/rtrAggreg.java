package rtr;

import addr.addrIP;
import addr.addrIPv4;
import addr.addrIPv6;
import ip.ipCor4;
import ip.ipCor6;
import ip.ipFwd;
import ip.ipRtr;
import java.util.List;
import tab.tabRoute;
import tab.tabRouteAttr;
import tab.tabRouteEntry;
import user.userHelping;
import util.bits;
import util.cmds;

/**
 * auto aggregate creator
 *
 * @author matecsaba
 */
public class rtrAggreg extends ipRtr {

    /**
     * distance
     */
    public int distance;

    /**
     * nexthop
     */
    public addrIP nextHop;

    /**
     * netmask
     */
    public int netMask;

    /**
     * netmask addition
     */
    public final int maskAdd;

    /**
     * the forwarder protocol
     */
    public final ipFwd fwdCore;

    /**
     * route type
     */
    protected final tabRouteAttr.routeType rouTyp;

    /**
     * router number
     */
    protected final int rtrNum;

    /**
     * create aggregator process
     *
     * @param forwarder forwarder to update
     * @param id process id
     */
    public rtrAggreg(ipFwd forwarder, int id) {
        fwdCore = forwarder;
        rtrNum = id;
        switch (fwdCore.ipVersion) {
            case ipCor4.protocolVersion:
                rouTyp = tabRouteAttr.routeType.aggreg4;
                maskAdd = new addrIP().maxBits() - new addrIPv4().maxBits();
                break;
            case ipCor6.protocolVersion:
                rouTyp = tabRouteAttr.routeType.aggreg6;
                maskAdd = new addrIP().maxBits() - new addrIPv6().maxBits();
                break;
            default:
                rouTyp = null;
                maskAdd = 0;
                break;
        }
        routerComputedU = new tabRoute<addrIP>("rx");
        routerComputedM = new tabRoute<addrIP>("rx");
        routerComputedF = new tabRoute<addrIP>("rx");
        distance = 254;
        nextHop = new addrIP();
        netMask = 0;
        routerCreateComputed();
        fwdCore.routerAdd(this, rouTyp, id);
    }

    /**
     * convert to string
     *
     * @return string
     */
    public String toString() {
        return "aggreg on " + fwdCore;
    }

    private void doPrefix(tabRouteEntry<addrIP> ntry, tabRoute<addrIP> tab) {
        if (ntry == null) {
            return;
        }
        if (ntry.prefix.maskLen <= (maskAdd + netMask)) {
            return;
        }
        ntry = ntry.copyBytes(tabRoute.addType.notyet);
        ntry.best.rouTyp = rouTyp;
        ntry.best.protoNum = rtrNum;
        ntry.prefix.setMask(maskAdd + netMask);
        if (distance > 0) {
            ntry.best.distance = distance;
        }
        if (!nextHop.isEmpty()) {
            ntry.best.nextHop = nextHop.copyBytes();
        }
        tab.add(tabRoute.addType.better, ntry, true, false);
    }

    /**
     * create computed
     */
    public synchronized void routerCreateComputed() {
        tabRoute<addrIP> resU = new tabRoute<addrIP>("computed");
        tabRoute<addrIP> resM = new tabRoute<addrIP>("computed");
        for (int i = 0; i < routerRedistedU.size(); i++) {
            doPrefix(routerRedistedU.get(i), resU);
        }
        for (int i = 0; i < routerRedistedM.size(); i++) {
            doPrefix(routerRedistedM.get(i), resM);
        }
        routerDoAggregates(rtrBgpUtil.sfiUnicast, resU, resU, fwdCore.commonLabel, null, 0);
        routerDoAggregates(rtrBgpUtil.sfiMulticast, resM, resM, fwdCore.commonLabel, null, 0);
        resU.preserveTime(routerComputedU);
        resM.preserveTime(routerComputedM);
        routerComputedU = resU;
        routerComputedM = resM;
        fwdCore.routerChg(this);
    }

    /**
     * redistribution changed
     */
    public void routerRedistChanged() {
        routerCreateComputed();
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
        l.add("1 2   distance                    specify default distance");
        l.add("2 .     <num>                     distance");
        l.add("1 2   nexthop                     specify default nexthop");
        l.add("2 .     <addr>                    nexthop");
        l.add("1 2   netmask                     specify netmask to use");
        l.add("2 .     <num>                     mask bits");
    }

    /**
     * get config
     *
     * @param l list
     * @param beg beginning
     * @param filter filter
     */
    public void routerGetConfig(List<String> l, String beg, int filter) {
        l.add(beg + "netmask " + netMask);
        l.add(beg + "distance " + distance);
        l.add(beg + "nexthop " + nextHop);
    }

    /**
     * configure
     *
     * @param cmd command
     * @return false if success, true if error
     */
    public boolean routerConfigure(cmds cmd) {
        String s = cmd.word();
        boolean negated = false;
        if (s.equals("no")) {
            s = cmd.word();
            negated = true;
        }
        if (s.equals("distance")) {
            distance = bits.str2num(cmd.word());
            return false;
        }
        if (s.equals("nexthop")) {
            nextHop = new addrIP();
            if (negated) {
                return false;
            }
            nextHop.fromString(cmd.word());
            return false;
        }
        if (s.equals("netmask")) {
            netMask = bits.str2num(cmd.word());
            return false;
        }
        return true;
    }

    /**
     * stop work
     */
    public void routerCloseNow() {
    }

    /**
     * get neighbor count
     *
     * @return count
     */
    public int routerNeighCount() {
        return 0;
    }

    /**
     * get neighbor list
     *
     * @param tab list
     */
    public void routerNeighList(tabRoute<addrIP> tab) {
    }

    /**
     * get interface count
     *
     * @return count
     */
    public int routerIfaceCount() {
        return 0;
    }

    /**
     * get list of link states
     *
     * @param tab table to update
     * @param par parameter
     * @param asn asn
     * @param adv advertiser
     */
    public void routerLinkStates(tabRoute<addrIP> tab, int par, int asn, addrIPv4 adv) {
    }

}

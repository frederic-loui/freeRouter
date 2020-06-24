#include <arpa/inet.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <netinet/in.h>
#include <pthread.h>
#include <pcap.h>
#include <unistd.h>
#include "utils.h"
#include "table.h"


#define maxPorts 128
#define preBuff 64

int commandSock;
pthread_t threadRaw[maxPorts];
char *ifaceName[maxPorts];
int ifaceId[maxPorts];
pcap_t *ifacePcap[maxPorts];
long int byteRx[maxPorts];
long int packRx[maxPorts];
long int byteTx[maxPorts];
long int packTx[maxPorts];
int ports;



void err(char*buf) {
    printf("%s\n", buf);
    exit(1);
}



struct mpls_entry {
    int label;
    int command;    // 1=vrf, 2=pop, 3=swap
    int nexthop;
    int vrf;
    int ver;
    int swap;
};

struct table_head mpls_table;

int mpls_compare(void *ptr1, void *ptr2) {
    struct mpls_entry *ntry1 = ptr1;
    struct mpls_entry *ntry2 = ptr2;
    if (ntry1->label < ntry2->label) return -1;
    if (ntry1->label > ntry2->label) return +1;
    return 0;
}


struct portvrf_entry {
    int port;
    int command;    // 1=vrf, 2=bridge
    int vrf;
    int bridge;
};

struct table_head portvrf_table;

int portvrf_compare(void *ptr1, void *ptr2) {
    struct portvrf_entry *ntry1 = ptr1;
    struct portvrf_entry *ntry2 = ptr2;
    if (ntry1->port < ntry2->port) return -1;
    if (ntry1->port > ntry2->port) return +1;
    return 0;
}


struct route4_entry {
    int vrf;
    int mask;
    int addr;
    int command;    // 1=route, 2=punt, 3=mpls1, 4=mpls3
    int nexthop;
    int label1;
    int label2;
};

struct table_head route4_table;

int route4_compare(void *ptr1, void *ptr2) {
    struct route4_entry *ntry1 = ptr1;
    struct route4_entry *ntry2 = ptr2;
    if (ntry1->vrf < ntry2->vrf) return -1;
    if (ntry1->vrf > ntry2->vrf) return +1;
    if (ntry1->mask < ntry2->mask) return -1;
    if (ntry1->mask > ntry2->mask) return +1;
    if (ntry1->addr < ntry2->addr) return -1;
    if (ntry1->addr > ntry2->addr) return +1;
    return 0;
}



struct route6_entry {
    int vrf;
    int mask;
    int addr1;
    int addr2;
    int addr3;
    int addr4;
    int command;    // 1=route, 2=punt, 3=mpls1, 4=mpls3
    int nexthop;
    int label1;
    int label2;
};

struct table_head route6_table;

int route6_compare(void *ptr1, void *ptr2) {
    struct route6_entry *ntry1 = ptr1;
    struct route6_entry *ntry2 = ptr2;
    if (ntry1->vrf < ntry2->vrf) return -1;
    if (ntry1->vrf > ntry2->vrf) return +1;
    if (ntry1->mask < ntry2->mask) return -1;
    if (ntry1->mask > ntry2->mask) return +1;
    if (ntry1->addr4 < ntry2->addr4) return -1;
    if (ntry1->addr4 > ntry2->addr4) return +1;
    if (ntry1->addr3 < ntry2->addr3) return -1;
    if (ntry1->addr3 > ntry2->addr3) return +1;
    if (ntry1->addr2 < ntry2->addr2) return -1;
    if (ntry1->addr2 > ntry2->addr2) return +1;
    if (ntry1->addr1 < ntry2->addr1) return -1;
    if (ntry1->addr1 > ntry2->addr1) return +1;
    return 0;
}


struct neigh_entry {
    int id;
    int vrf;
    int port;
    unsigned char smac[6];
    unsigned char dmac[6];
};

struct table_head neigh_table;

int neigh_compare(void *ptr1, void *ptr2) {
    struct neigh_entry *ntry1 = ptr1;
    struct neigh_entry *ntry2 = ptr2;
    if (ntry1->id < ntry2->id) return -1;
    if (ntry1->id > ntry2->id) return +1;
    return 0;
}


struct bridge_entry {
    int id;
    int port;
    unsigned char mac[6];
};

struct table_head bridge_table;

int bridge_compare(void *ptr1, void *ptr2) {
    struct bridge_entry *ntry1 = ptr1;
    struct bridge_entry *ntry2 = ptr2;
    if (ntry1->id < ntry2->id) return -1;
    if (ntry1->id > ntry2->id) return +1;
    if (ntry1->mac[5] < ntry2->mac[5]) return -1;
    if (ntry1->mac[5] > ntry2->mac[5]) return +1;
    if (ntry1->mac[4] < ntry2->mac[4]) return -1;
    if (ntry1->mac[4] > ntry2->mac[4]) return +1;
    if (ntry1->mac[3] < ntry2->mac[3]) return -1;
    if (ntry1->mac[3] > ntry2->mac[3]) return +1;
    if (ntry1->mac[2] < ntry2->mac[2]) return -1;
    if (ntry1->mac[2] > ntry2->mac[2]) return +1;
    if (ntry1->mac[1] < ntry2->mac[1]) return -1;
    if (ntry1->mac[1] > ntry2->mac[1]) return +1;
    if (ntry1->mac[0] < ntry2->mac[0]) return -1;
    if (ntry1->mac[0] > ntry2->mac[0]) return +1;
    return 0;
}


struct vlan_entry {
    int id;
    int vlan;
    int port;
    long pack;
    long byte;
};

struct table_head vlanin_table;

struct table_head vlanout_table;

int vlanin_compare(void *ptr1, void *ptr2) {
    struct vlan_entry *ntry1 = ptr1;
    struct vlan_entry *ntry2 = ptr2;
    if (ntry1->port < ntry2->port) return -1;
    if (ntry1->port > ntry2->port) return +1;
    if (ntry1->vlan < ntry2->vlan) return -1;
    if (ntry1->vlan > ntry2->vlan) return +1;
    return 0;
}

int vlanout_compare(void *ptr1, void *ptr2) {
    struct vlan_entry *ntry1 = ptr1;
    struct vlan_entry *ntry2 = ptr2;
    if (ntry1->id < ntry2->id) return -1;
    if (ntry1->id > ntry2->id) return +1;
    return 0;
}



void send2cpu(unsigned char *bufD, int bufS, int port) {
    put16bits(bufD, preBuff - 2, port);
    pcap_sendpacket(ifacePcap[0], &bufD[preBuff - 2], bufS + 2);
    packTx[0]++;
    byteTx[0] += bufS + 2;
}

#ifdef basicLoop

void doBasicLoop(int * param) {
    int port = *param;
    struct pcap_pkthdr head;
    const unsigned char *pack;
    unsigned char bufD[16384];
    int bufS;
    unsigned int addrLen;
    struct vlan_entry vlan_ntry;
    struct vlan_entry *vlan_res;
    int index;
    for (;;) {
        pack = pcap_next(ifacePcap[port], &head);
        bufS = head.caplen;
        if (bufS < 0) break;
        memmove(&bufD[preBuff], pack, bufS);
        packRx[port]++;
        byteRx[port] += bufS;
        send2cpu(bufD, bufS, port);
        if (get16bits(bufD, preBuff + 12) != 0x8100) continue;
        vlan_ntry.port = port;
        vlan_ntry.vlan = get16bits(bufD, preBuff + 14) & 0xfff;
        index = table_find(&vlanin_table, &vlan_ntry);
        if (index < 0) continue;
        vlan_res = table_get(&vlanin_table, index);
        vlan_res->pack++;
        vlan_res->byte += bufS;
    }
    err("port thread exited");
}

#else

int masks[] = {
        0x00000000,
        0x80000000, 0xc0000000, 0xe0000000, 0xf0000000,
        0xf8000000, 0xfc000000, 0xfe000000, 0xff000000,
        0xff800000, 0xffc00000, 0xffe00000, 0xfff00000,
        0xfff80000, 0xfffc0000, 0xfffe0000, 0xffff0000,
        0xffff8000, 0xffffc000, 0xffffe000, 0xfffff000,
        0xfffff800, 0xfffffc00, 0xfffffe00, 0xffffff00,
        0xffffff80, 0xffffffc0, 0xffffffe0, 0xfffffff0,
        0xfffffff8, 0xfffffffc, 0xfffffffe, 0xffffffff
};

void doPortLoop(int * param) {
    int port = *param;
    struct pcap_pkthdr head;
    const unsigned char *pack;
    unsigned char bufD[16384];
    unsigned char buf2[preBuff];
    int bufS;
    int bufP;
    int ethtyp;
    unsigned int addrLen;
    struct mpls_entry mpls_ntry;
    struct portvrf_entry portvrf_ntry;
    struct route4_entry route4_ntry;
    struct route6_entry route6_ntry;
    struct neigh_entry neigh_ntry;
    struct vlan_entry vlan_ntry;
    struct bridge_entry bridge_ntry;
    struct mpls_entry *mpls_res;
    struct portvrf_entry *portvrf_res;
    struct route4_entry *route4_res;
    struct route6_entry *route6_res;
    struct neigh_entry *neigh_res;
    struct vlan_entry *vlan_res;
    struct bridge_entry *bridge_res;
    int index;
    int label;
    int prt;
    for (;;) {
        pack = pcap_next(ifacePcap[port], &head);
        bufS = head.caplen;
        if (bufS < 0) break;
        memmove(&bufD[preBuff], pack, bufS);
        bufP = preBuff;
        packRx[port]++;
        byteRx[port] += bufS;
        bufP += 6 * 2; // dmac, smac
        prt = port;
ethtyp_rx:
        ethtyp = get16bits(bufD, bufP);
        bufP += 2;
        switch (ethtyp) {
            case 0x8847:
mpls_rx:
                label = get32bits(bufD, bufP);
                bufP += 4;
                mpls_ntry.label = (label >> 12) & 0xfffff;
                index = table_find(&mpls_table, &mpls_ntry);
                if (index < 0) break;
                mpls_res = table_get(&mpls_table, index);
                switch (mpls_res->command) {
                    case 1: // route
                        route4_ntry.vrf = mpls_res->vrf;
                        route6_ntry.vrf = mpls_res->vrf;
                        if ((label & 0x100) == 0) goto mpls_rx;
                        switch (mpls_res->ver) {
                            case 4:
                                ethtyp = 0x800;
                                goto ipv4_rx;
                            case 6:
                                ethtyp = 0x86dd;
                                goto ipv6_rx;
                            default:
                                ethtyp = 0;
                                break;
                        }
                        break;
                    case 2: // pop
                        switch (mpls_res->ver) {
                            case 4:
                                ethtyp = 0x800;
                                break;
                            case 6:
                                ethtyp = 0x86dd;
                                break;
                            default:
                                ethtyp = 0;
                                break;
                        }
                        neigh_ntry.id = mpls_res->nexthop;
                        goto ethtyp_tx;
                        break;
                    case 3: // swap
                        bufP -= 4;
                        label = (label & 0xfff) | (mpls_res->swap << 12);
                        put32bits(bufD, bufP, label);
                        neigh_ntry.id = mpls_res->nexthop;
ethtyp_tx:
                        bufP -= 2;
                        put16bits(bufD, bufP, ethtyp);
nexthop_tx:
                        index = table_find(&neigh_table, &neigh_ntry);
                        if (index < 0) break;
                        neigh_res = table_get(&neigh_table, index);
                        prt = neigh_res->port;
                        vlan_ntry.id = prt;
                        index = table_find(&vlanout_table, &vlan_ntry);
                        if (index >= 0) {
                            vlan_res = table_get(&vlanout_table, index);
                            bufP -= 2;
                            put16bits(bufD, bufP, vlan_res->vlan);
                            bufP -= 2;
                            put16bits(bufD, bufP, 0x8100);
                            prt = vlan_res->port;
                            vlan_res->pack++;
                            vlan_res->byte += bufS;
                        }
                        if (prt >= ports) break;
                        bufP -= 6;
                        memmove(&bufD[bufP], &neigh_res->smac, 6);
                        bufP -= 6;
                        memmove(&bufD[bufP], &neigh_res->dmac, 6);
                        pcap_sendpacket(ifacePcap[prt], &bufD[bufP], bufS - bufP + preBuff);
                        packTx[prt]++;
                        byteTx[prt] += bufS;
                        break;
                     default:
                        break;
                }
                break;
            case 0x8100:
                vlan_ntry.port = prt;
                vlan_ntry.vlan = get16bits(bufD, bufP) & 0xfff;
                bufP += 2;
                index = table_find(&vlanin_table, &vlan_ntry);
                if (index < 0) break;
                vlan_res = table_get(&vlanin_table, index);
                prt = vlan_res->id;
                vlan_res->pack++;
                vlan_res->byte += bufS;
                goto ethtyp_rx;
                break;
            case 0x800:
                portvrf_ntry.port = prt;
                index = table_find(&portvrf_table, &portvrf_ntry);
                if (index < 0) break;
                portvrf_res = table_get(&portvrf_table, index);
                if (portvrf_res->command == 2) goto bridge;
                route4_ntry.vrf = portvrf_res->vrf;
ipv4_rx:
                route4_ntry.addr = get32bits(bufD, bufP + 16);
                for (int i = 32; i >= 0; i--) {
                    route4_ntry.mask = i;
                    route4_ntry.addr &= masks[i];
                    index = table_find(&route4_table, &route4_ntry);
                    if (index < 0) continue;
                    route4_res = table_get(&route4_table, index);
                    switch (route4_res->command) {
                        case 1: // route
                            neigh_ntry.id = route4_res->nexthop;
                            goto ethtyp_tx;
                        case 2: // punt
                            goto cpu;
                        case 3: // mpls1
                            ethtyp = 0x8847;
                            bufP -= 4;
                            label = 0x1ff | (route4_res->label1 << 12);
                            put32bits(bufD, bufP, label);
                            neigh_ntry.id = route4_res->nexthop;
                            goto ethtyp_tx;
                        case 4: // mpls2
                            ethtyp = 0x8847;
                            bufP -= 4;
                            label = 0x1ff | (route4_res->label2 << 12);
                            put32bits(bufD, bufP, label);
                            bufP -= 4;
                            label = 0xff | (route4_res->label1 << 12);
                            put32bits(bufD, bufP, label);
                            neigh_ntry.id = route4_res->nexthop;
                            goto ethtyp_tx;
                    }
                    break;
                }
                break;
            case 0x86dd:
                portvrf_ntry.port = prt;
                index = table_find(&portvrf_table, &portvrf_ntry);
                if (index < 0) break;
                portvrf_res = table_get(&portvrf_table, index);
                if (portvrf_res->command == 2) goto bridge;
                route6_ntry.vrf = portvrf_res->vrf;
ipv6_rx:
                route6_ntry.addr1 = get32bits(bufD, bufP + 24);
                route6_ntry.addr2 = get32bits(bufD, bufP + 28);
                route6_ntry.addr3 = get32bits(bufD, bufP + 32);
                route6_ntry.addr4 = get32bits(bufD, bufP + 36);
                for (int i = 32; i >= 0; i--) {
                    route6_ntry.mask = 96 + i;
                    route6_ntry.addr4 &= masks[i];
                    index = table_find(&route6_table, &route6_ntry);
                    if (index < 0) continue;
                    goto ipv6_hit;
                }
                for (int i = 32; i >= 0; i--) {
                    route6_ntry.mask = 64 + i;
                    route6_ntry.addr3 &= masks[i];
                    index = table_find(&route6_table, &route6_ntry);
                    if (index < 0) continue;
                    goto ipv6_hit;
                }
                for (int i = 32; i >= 0; i--) {
                    route6_ntry.mask = 32 + i;
                    route6_ntry.addr2 &= masks[i];
                    index = table_find(&route6_table, &route6_ntry);
                    if (index < 0) continue;
                    goto ipv6_hit;
                }
                for (int i = 32; i >= 0; i--) {
                    route6_ntry.mask = i;
                    route6_ntry.addr1 &= masks[i];
                    index = table_find(&route6_table, &route6_ntry);
                    if (index < 0) continue;
ipv6_hit:
                    route6_res = table_get(&route6_table, index);
                    switch (route6_res->command) {
                        case 1: // route
                            neigh_ntry.id = route6_res->nexthop;
                            goto ethtyp_tx;
                        case 2: // punt
                            goto cpu;
                        case 3: // mpls1
                            ethtyp = 0x8847;
                            bufP -= 4;
                            label = 0x1ff | (route6_res->label1 << 12);
                            put32bits(bufD, bufP, label);
                            neigh_ntry.id = route6_res->nexthop;
                            goto ethtyp_tx;
                        case 4: // mpls2
                            ethtyp = 0x8847;
                            bufP -= 4;
                            label = 0x1ff | (route6_res->label2 << 12);
                            put32bits(bufD, bufP, label);
                            bufP -= 4;
                            label = 0xff | (route6_res->label1 << 12);
                            put32bits(bufD, bufP, label);
                            neigh_ntry.id = route6_res->nexthop;
                            goto ethtyp_tx;
                    }
                    break;
                }
                break;
            case 0x65580000:
bridge:
                bridge_ntry.id = portvrf_res->bridge;
                memmove(&bridge_ntry.mac, &bufD[preBuff], 6);
                memmove(&buf2[0], &bufD[preBuff], 12);
                index = table_find(&bridge_table, &bridge_ntry);
                if (index < 0) goto cpu;
                bridge_res = table_get(&bridge_table, index);
                bufP -= 2;
                prt = bridge_res->port;
                vlan_ntry.id = prt;
                index = table_find(&vlanout_table, &vlan_ntry);
                if (index >= 0) {
                    vlan_res = table_get(&vlanout_table, index);
                    bufP -= 2;
                    put16bits(bufD, bufP, vlan_res->vlan);
                    bufP -= 2;
                    put16bits(bufD, bufP, 0x8100);
                    prt = vlan_res->port;
                    vlan_res->pack++;
                    vlan_res->byte += bufS;
                }
                if (prt >= ports) break;
                bufP -= 12;
                memmove(&bufD[bufP], &buf2[0], 12);
                pcap_sendpacket(ifacePcap[prt], &bufD[bufP], bufS - bufP + preBuff);
                packTx[prt]++;
                byteTx[prt] += bufS;
                break;
            default:
cpu:
                send2cpu(bufD, bufS, port);
                break;
        }
    }
    err("port thread exited");
}

#endif

void doHostLoop() {
    struct pcap_pkthdr head;
    const unsigned char *pack;
    unsigned char bufD[16384];
    int bufS;
    unsigned int addrLen;
    struct vlan_entry vlan_ntry;
    struct vlan_entry *vlan_res;
    int index;
    int prt;
    for (;;) {
        pack = pcap_next(ifacePcap[0], &head);
        bufS = head.caplen;
        if (bufS < 0) break;
        memmove(&bufD[preBuff], pack, bufS);
        packRx[0]++;
        byteRx[0] += bufS;
        prt = get16bits(bufD, preBuff);
        if (prt >= ports) continue;
        pcap_sendpacket(ifacePcap[prt], &bufD[preBuff + 2], bufS - 2);
        packTx[prt]++;
        byteTx[prt] += bufS - 2;
        if (get16bits(bufD, preBuff + 14) != 0x8100) continue;
        vlan_ntry.port = prt;
        vlan_ntry.vlan = get16bits(bufD, preBuff + 16) & 0xfff;
        index = table_find(&vlanin_table, &vlan_ntry);
        if (index < 0) continue;
        vlan_res = table_get(&vlanout_table, index);
        vlan_res->pack++;
        vlan_res->byte += bufS;
    }
    err("host thread exited");
}


void doStatLoop() {
    FILE *commands = fdopen(commandSock, "w");
    if (commands == NULL) err("failed to open file");
    for (;;) {
        sleep(1);
        for (int i = 0; i < ports; i++) {
            fprintf(commands, "counter %i %li %li %li %li\r\n", i, packRx[i], byteRx[i], packTx[i], byteTx[i]);
        }
        for (int i=0; i<vlanin_table.size; i++) {
            struct vlan_entry *intry = table_get(&vlanin_table, i);
            struct vlan_entry *ontry = table_get(&vlanout_table, i);
            fprintf(commands, "counter %i %li %li %li %li\r\n", intry->id, intry->pack, intry->byte, ontry->pack, ontry->byte);
        }
        fflush(commands);
    }
    err("stat thread exited");
}


void str2mac(unsigned char *dst, unsigned char *src) {
    sscanf(src, "%hhx:%hhx:%hhx:%hhx:%hhx:%hhx", &dst[0], &dst[1], &dst[2], &dst[3], &dst[4], &dst[5]);
}

void mac2str(unsigned char *src, unsigned char *dst) {
    snprintf(dst, 128, "%02x:%02x:%02x:%02x:%02x:%02x", src[0], src[1], src[2], src[3], src[4], src[5]);
}


void doSockLoop() {
    FILE *commands = fdopen(commandSock, "rw");
    if (commands == NULL) err("failed to open file");
    unsigned char buf[1024];
    unsigned char buf2[1024];
    unsigned char* arg[128];
    int cnt;
    for (;;) {
        strcpy(buf, "quit\r\n");
        fgets(buf, sizeof(buf), commands);
        cnt = 0;
        arg[0] = &buf[0];
        int i = 0;
        int o = 0;
        for (;;) {
            switch (buf[i]) {
                case 0:
                case 10:
                case 13:
                    o = 1;
                case ' ':
                case '/':
                case '_':
                    buf[i] = 0;
                    cnt++;
                    arg[cnt] = &buf[i + 1];
                    break;
            }
            if (o > 0) break;
            i++;
        }
        printf("rx: ");
        for (int i=0; i < cnt; i++) printf("'%s' ",arg[i]);
        printf("\n");
        int del = strcmp(arg[1], "del");
        struct mpls_entry mpls_ntry;
        memset(&mpls_ntry, 0, sizeof(mpls_ntry));
        struct portvrf_entry portvrf_ntry;
        memset(&portvrf_ntry, 0, sizeof(portvrf_ntry));
        struct route4_entry route4_ntry;
        memset(&route4_ntry, 0, sizeof(route4_ntry));
        struct route6_entry route6_ntry;
        memset(&route6_ntry, 0, sizeof(route6_ntry));
        struct neigh_entry neigh_ntry;
        memset(&neigh_ntry, 0, sizeof(neigh_ntry));
        struct vlan_entry vlan_ntry;
        memset(&vlan_ntry, 0, sizeof(vlan_ntry));
        struct bridge_entry bridge_ntry;
        memset(&bridge_ntry, 0, sizeof(bridge_ntry));
        if (strcmp(arg[0], "quit") == 0) {
            break;
        }
        if (strcmp(arg[0], "mylabel4") == 0) {
            mpls_ntry.label = atoi(arg[2]);
            mpls_ntry.vrf = atoi(arg[3]);
            mpls_ntry.ver = 4;
            mpls_ntry.command = 1;
            if (del == 0) table_del(&mpls_table, &mpls_ntry); else table_add(&mpls_table, &mpls_ntry);
            continue;
        }
        if (strcmp(arg[0], "mylabel6") == 0) {
            mpls_ntry.label = atoi(arg[2]);
            mpls_ntry.vrf = atoi(arg[3]);
            mpls_ntry.ver = 6;
            mpls_ntry.command = 1;
            if (del == 0) table_del(&mpls_table, &mpls_ntry); else table_add(&mpls_table, &mpls_ntry);
            continue;
        }
        if (strcmp(arg[0], "unlabel4") == 0) {
            mpls_ntry.label = atoi(arg[2]);
            mpls_ntry.nexthop = atoi(arg[3]);
            mpls_ntry.ver = 4;
            mpls_ntry.command = 2;
            if (del == 0) table_del(&mpls_table, &mpls_ntry); else table_add(&mpls_table, &mpls_ntry);
            continue;
        }
        if (strcmp(arg[0], "unlabel6") == 0) {
            mpls_ntry.label = atoi(arg[2]);
            mpls_ntry.nexthop = atoi(arg[3]);
            mpls_ntry.ver = 6;
            mpls_ntry.command = 2;
            if (del == 0) table_del(&mpls_table, &mpls_ntry); else table_add(&mpls_table, &mpls_ntry);
            continue;
        }
        if (strcmp(arg[0], "label4") == 0) {
            mpls_ntry.label = atoi(arg[2]);
            mpls_ntry.nexthop = atoi(arg[3]);
            mpls_ntry.swap = atoi(arg[5]);
            mpls_ntry.ver = 4;
            mpls_ntry.command = 3;
            if (del == 0) table_del(&mpls_table, &mpls_ntry); else table_add(&mpls_table, &mpls_ntry);
            continue;
        }
        if (strcmp(arg[0], "label6") == 0) {
            mpls_ntry.label = atoi(arg[2]);
            mpls_ntry.nexthop = atoi(arg[3]);
            mpls_ntry.swap = atoi(arg[5]);
            mpls_ntry.ver = 6;
            mpls_ntry.command = 3;
            if (del == 0) table_del(&mpls_table, &mpls_ntry); else table_add(&mpls_table, &mpls_ntry);
            continue;
        }
        if (strcmp(arg[0], "portvrf") == 0) {
            portvrf_ntry.command = 1;
            portvrf_ntry.port = atoi(arg[2]);
            portvrf_ntry.vrf = atoi(arg[3]);
            if (del == 0) table_del(&portvrf_table, &portvrf_ntry); else table_add(&portvrf_table, &portvrf_ntry);
            continue;
        }
        if (strcmp(arg[0], "portbridge") == 0) {
            portvrf_ntry.command = 2;
            portvrf_ntry.port = atoi(arg[2]);
            portvrf_ntry.bridge = atoi(arg[3]);
            if (del == 0) table_del(&portvrf_table, &portvrf_ntry); else table_add(&portvrf_table, &portvrf_ntry);
            continue;
        }
        if (strcmp(arg[0], "bridgemac") == 0) {
            bridge_ntry.id = atoi(arg[2]);
            str2mac(bridge_ntry.mac, arg[3]);
            bridge_ntry.port = atoi(arg[4]);
            if (del == 0) table_del(&bridge_table, &bridge_ntry); else table_add(&bridge_table, &bridge_ntry);
            continue;
        }
        if (strcmp(arg[0], "portvlan") == 0) {
            vlan_ntry.id= atoi(arg[2]);
            vlan_ntry.port = atoi(arg[3]);
            vlan_ntry.vlan = atoi(arg[4]);
            if (del == 0) table_del(&vlanin_table, &vlan_ntry); else table_add(&vlanin_table, &vlan_ntry);
            if (del == 0) table_del(&vlanout_table, &vlan_ntry); else table_add(&vlanout_table, &vlan_ntry);
            continue;
        }
        if (strcmp(arg[0], "myaddr4") == 0) {
            inet_pton(AF_INET, arg[2], buf2);
            route4_ntry.addr = get32bits(buf2, 0);
            route4_ntry.mask = atoi(arg[3]);
            route4_ntry.vrf = atoi(arg[5]);
            route4_ntry.command = 2;
            if (del == 0) table_del(&route4_table, &route4_ntry); else table_add(&route4_table, &route4_ntry);
            continue;
        }
        if (strcmp(arg[0], "route4") == 0) {
            inet_pton(AF_INET, arg[2], buf2);
            route4_ntry.addr = get32bits(buf2, 0);
            route4_ntry.mask = atoi(arg[3]);
            route4_ntry.nexthop = atoi(arg[4]);
            route4_ntry.vrf = atoi(arg[6]);
            route4_ntry.command = 1;
            if (del == 0) table_del(&route4_table, &route4_ntry); else table_add(&route4_table, &route4_ntry);
            continue;
        }
        if (strcmp(arg[0], "labroute4") == 0) {
            inet_pton(AF_INET, arg[2], buf2);
            route4_ntry.addr = get32bits(buf2, 0);
            route4_ntry.mask = atoi(arg[3]);
            route4_ntry.nexthop = atoi(arg[4]);
            route4_ntry.vrf = atoi(arg[6]);
            route4_ntry.label1 = atoi(arg[7]);
            route4_ntry.command = 3;
            if (del == 0) table_del(&route4_table, &route4_ntry); else table_add(&route4_table, &route4_ntry);
            continue;
        }
        if (strcmp(arg[0], "vpnroute4") == 0) {
            inet_pton(AF_INET, arg[2], buf2);
            route4_ntry.addr = get32bits(buf2, 0);
            route4_ntry.mask = atoi(arg[3]);
            route4_ntry.nexthop = atoi(arg[4]);
            route4_ntry.vrf = atoi(arg[6]);
            route4_ntry.label1 = atoi(arg[7]);
            route4_ntry.label2 = atoi(arg[8]);
            route4_ntry.command = 4;
            if (del == 0) table_del(&route4_table, &route4_ntry); else table_add(&route4_table, &route4_ntry);
            continue;
        }
        if (strcmp(arg[0], "neigh4") == 0) {
            route4_ntry.nexthop = atoi(arg[2]);
            inet_pton(AF_INET, arg[3], buf2);
            route4_ntry.addr = get32bits(buf2, 0);
            route4_ntry.mask = 32;
            route4_ntry.vrf = atoi(arg[5]);
            route4_ntry.command = 1;
            neigh_ntry.id = route4_ntry.nexthop;
            neigh_ntry.vrf = route4_ntry.vrf;
            neigh_ntry.port = atoi(arg[7]);
            str2mac(neigh_ntry.dmac, arg[4]);
            str2mac(neigh_ntry.smac, arg[6]);
            if (del == 0) table_del(&route4_table, &route4_ntry); else table_add(&route4_table, &route4_ntry);
            if (del == 0) table_del(&neigh_table, &neigh_ntry); else table_add(&neigh_table, &neigh_ntry);
            continue;
        }
        if (strcmp(arg[0], "myaddr6") == 0) {
            inet_pton(AF_INET6, arg[2], buf);
            route6_ntry.addr1 = get32bits(buf, 0);
            route6_ntry.addr2 = get32bits(buf, 4);
            route6_ntry.addr3 = get32bits(buf, 8);
            route6_ntry.addr4 = get32bits(buf, 12);
            route6_ntry.mask = atoi(arg[3]);
            route6_ntry.vrf = atoi(arg[5]);
            route6_ntry.command = 2;
            if (del == 0) table_del(&route6_table, &route6_ntry); else table_add(&route6_table, &route6_ntry);
            continue;
        }
        if (strcmp(arg[0], "route6") == 0) {
            inet_pton(AF_INET6, arg[2], buf2);
            route6_ntry.addr1 = get32bits(buf2, 0);
            route6_ntry.addr2 = get32bits(buf2, 4);
            route6_ntry.addr3 = get32bits(buf2, 8);
            route6_ntry.addr4 = get32bits(buf2, 12);
            route6_ntry.mask = atoi(arg[3]);
            route6_ntry.nexthop = atoi(arg[4]);
            route6_ntry.vrf = atoi(arg[6]);
            route6_ntry.command = 1;
            if (del == 0) table_del(&route6_table, &route6_ntry); else table_add(&route6_table, &route6_ntry);
            continue;
        }
        if (strcmp(arg[0], "labroute6") == 0) {
            inet_pton(AF_INET6, arg[2], buf2);
            route6_ntry.addr1 = get32bits(buf2, 0);
            route6_ntry.addr2 = get32bits(buf2, 4);
            route6_ntry.addr3 = get32bits(buf2, 8);
            route6_ntry.addr4 = get32bits(buf2, 12);
            route6_ntry.mask = atoi(arg[3]);
            route6_ntry.nexthop = atoi(arg[4]);
            route6_ntry.vrf = atoi(arg[6]);
            route6_ntry.label1 = atoi(arg[7]);
            route6_ntry.command = 3;
            if (del == 0) table_del(&route6_table, &route6_ntry); else table_add(&route6_table, &route6_ntry);
            continue;
        }
        if (strcmp(arg[0], "vpnroute6") == 0) {
            inet_pton(AF_INET6, arg[2], buf2);
            route6_ntry.addr1 = get32bits(buf2, 0);
            route6_ntry.addr2 = get32bits(buf2, 4);
            route6_ntry.addr3 = get32bits(buf2, 8);
            route6_ntry.addr4 = get32bits(buf2, 12);
            route6_ntry.mask = atoi(arg[3]);
            route6_ntry.nexthop = atoi(arg[4]);
            route6_ntry.vrf = atoi(arg[6]);
            route6_ntry.label1 = atoi(arg[7]);
            route6_ntry.label2 = atoi(arg[8]);
            route6_ntry.command = 4;
            if (del == 0) table_del(&route6_table, &route6_ntry); else table_add(&route6_table, &route6_ntry);
            continue;
        }
        if (strcmp(arg[0], "neigh6") == 0) {
            route6_ntry.nexthop = atoi(arg[2]);
            inet_pton(AF_INET6, arg[3], buf2);
            route6_ntry.addr1 = get32bits(buf2, 0);
            route6_ntry.addr2 = get32bits(buf2, 4);
            route6_ntry.addr3 = get32bits(buf2, 8);
            route6_ntry.addr4 = get32bits(buf2, 12);
            route6_ntry.mask = 128;
            route6_ntry.vrf = atoi(arg[5]);
            route6_ntry.command = 1;
            neigh_ntry.id = route6_ntry.nexthop;
            neigh_ntry.vrf = route6_ntry.vrf;
            neigh_ntry.port = atoi(arg[7]);
            str2mac(neigh_ntry.dmac, arg[4]);
            str2mac(neigh_ntry.smac, arg[6]);
            if (del == 0) table_del(&route6_table, &route6_ntry); else table_add(&route6_table, &route6_ntry);
            if (del == 0) table_del(&neigh_table, &neigh_ntry); else table_add(&neigh_table, &neigh_ntry);
            continue;
        }
    }
    err("command thread exited");
}


void doMainLoop() {
    unsigned char buf[1024];
    unsigned char buf2[1024];

doer:
    printf("> ");
    buf[0] = 0;
    int i = scanf("%s", buf);
    if (i < 1) {
        sleep(1);
        goto doer;
    }
    switch (buf[0]) {
        case 0:
            goto doer;
            break;
        case 'H':
        case 'h':
        case '?':
            printf("commands:\n");
            printf("h - this help\n");
            printf("q - exit process\n");
            printf("i - interface counters\n");
            printf("p - display portvrf table\n");
            printf("b - display bridge table\n");
            printf("m - display mpls table\n");
            printf("4 - display ipv4 table\n");
            printf("6 - display ipv6 table\n");
            printf("n - display nexthop table\n");
            printf("v - display vlan table\n");
            break;
        case 'Q':
        case 'q':
            err("exiting");
            break;
        case 'i':
        case 'I':
            printf("                           iface         rx         tx         rx         tx\n");
            for (int i=0; i<ports; i++) {
                printf("%32s %10li %10li %10li %10li\n", ifaceName[i], packRx[i], packTx[i], byteRx[i], byteTx[i]);
            }
            break;
        case 'm':
        case 'M':
            printf("     label ip        vrf cmd       swap    nexthop\n");
            for (int i=0; i<mpls_table.size; i++) {
                struct mpls_entry *ntry = table_get(&mpls_table, i);
                printf("%10i %2i %10i %3i %10i %10i\n", ntry->label, ntry->ver, ntry->vrf, ntry->command, ntry->swap, ntry->nexthop);
            }
            break;
        case 'p':
        case 'P':
            printf("      port cmd        vrf     bridge\n");
            for (int i=0; i<portvrf_table.size; i++) {
                struct portvrf_entry *ntry = table_get(&portvrf_table, i);
                printf("%10i %3i %10i %10i\n", ntry->port, ntry->command, ntry->vrf, ntry->bridge);
            }
            break;
        case 'n':
        case 'N':
            printf("        id        vrf       port              smac              dmac\n");
            for (int i=0; i<neigh_table.size; i++) {
                struct neigh_entry *ntry = table_get(&neigh_table, i);
                mac2str(ntry->smac, buf);
                mac2str(ntry->dmac, buf2);
                printf("%10i %10i %10i %s %s\n", ntry->id, ntry->vrf, ntry->port, &buf, &buf2);
            }
            break;
        case 'b':
        case 'B':
            printf("    bridge               mac       port\n");
            for (int i=0; i<bridge_table.size; i++) {
                struct bridge_entry *ntry = table_get(&bridge_table, i);
                mac2str(ntry->mac, buf);
                printf("%10i %s %10i\n", ntry->id, buf, ntry->port);
            }
            break;
        case 'v':
        case 'V':
            printf("        id       vlan       port\n");
            for (int i=0; i<vlanin_table.size; i++) {
                struct vlan_entry *ntry = table_get(&vlanin_table, i);
                printf("%10i %10i %10i\n", ntry->id, ntry->vlan, ntry->port);
            }
            break;
        case '4':
            printf("            addr msk        vrf cmd    nexthop     label1     label2\n");
            for (int i=0; i<route4_table.size; i++) {
                struct route4_entry *ntry = table_get(&route4_table, i);
                put32bits(buf2, 0, ntry->addr);
                inet_ntop(AF_INET, &buf2[0], &buf[0], sizeof(buf));
                printf("%16s %3i %10i %3i %10i %10i %10i\n", &buf, ntry->mask, ntry->vrf, ntry->command, ntry->nexthop, ntry->label1, ntry->label2);
            }
            break;
        case '6':
            printf("                                    addr msk        vrf cmd    nexthop     label1     label2\n");
            for (int i=0; i<route6_table.size; i++) {
                struct route6_entry *ntry = table_get(&route6_table, i);
                put32bits(buf2, 0, ntry->addr1);
                put32bits(buf2, 4, ntry->addr2);
                put32bits(buf2, 8, ntry->addr3);
                put32bits(buf2, 12, ntry->addr4);
                inet_ntop(AF_INET6, &buf2[0], &buf[0], sizeof(buf));
                printf("%40s %3i %10i %3i %10i %10i %10i\n", &buf, ntry->mask, ntry->vrf, ntry->command, ntry->nexthop, ntry->label1, ntry->label2);
            }
            break;
        default:
            printf("unknown command '%s', try ?\n", buf);
            break;
    }
    printf("\n");

    goto doer;
}


int main(int argc, char **argv) {
    unsigned char errbuf[PCAP_ERRBUF_SIZE + 1];

    ports = 0;
    for (int i=3; i < argc; i++) {
        ifaceName[ports] = malloc(1024);
        strcpy(ifaceName[ports], argv[i]);
        ports++;
    }

    if (ports < 2) {
        err("using: dp <addr> <port> <ifc0> <ifc1> [ifcN] ...");
    }

    table_init(&mpls_table, sizeof(struct mpls_entry), &mpls_compare);
    table_init(&portvrf_table, sizeof(struct portvrf_entry), &portvrf_compare);
    table_init(&route4_table, sizeof(struct route4_entry), &route4_compare);
    table_init(&route6_table, sizeof(struct route6_entry), &route6_compare);
    table_init(&neigh_table, sizeof(struct neigh_entry), &neigh_compare);
    table_init(&vlanin_table, sizeof(struct vlan_entry), &vlanin_compare);
    table_init(&vlanout_table, sizeof(struct vlan_entry), &vlanout_compare);
    table_init(&bridge_table, sizeof(struct bridge_entry), &bridge_compare);

    int port = atoi(argv[2]);
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof (addr));
    if (inet_aton(argv[1], &addr.sin_addr) == 0) err("bad addr address");
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    printf("connecting %s %i.\n", inet_ntoa(addr.sin_addr), port);
    commandSock = socket(AF_INET, SOCK_STREAM, 0);
    if (commandSock < 0) err("unable to open socket");
    if(connect(commandSock, (struct sockaddr*)&addr, sizeof(addr)) < 0) err("failed to connect socket");

    for (int i=0; i < ports; i++) {
        printf("opening interface %s.\n", ifaceName[i]);
        ifacePcap[i] = pcap_create(ifaceName[i], errbuf);
        if (ifacePcap[i] == NULL) err("unable to open interface");
        if (pcap_set_snaplen(ifacePcap[i], 65536) < 0) err("unable to set snaplen");
        if (pcap_set_promisc(ifacePcap[i], 1) < 0) err("unable to set promisc");
        if (pcap_set_immediate_mode(ifacePcap[i], 1) < 0) err("unable to set immediate");
        if (pcap_activate(ifacePcap[i]) < 0) err("activation failed");
        if (pcap_setdirection(ifacePcap[i], PCAP_D_IN) < 0) err("unable to set direction");
        byteRx[i] = 0;
        packRx[i] = 0;
        byteTx[i] = 0;
        packTx[i] = 0;
        ifaceId[i] = i;
    }

    pthread_t threadSock;
    if (pthread_create(&threadSock, NULL, (void*) & doSockLoop, NULL)) err("error creating socket thread");
    pthread_t threadStat;
    if (pthread_create(&threadStat, NULL, (void*) & doStatLoop, NULL)) err("error creating status thread");

    if (pthread_create(&threadRaw[0], NULL, (void*) & doHostLoop, NULL)) err("error creating host thread");
    for (int i=1; i < ports; i++) {
#ifdef basicLoop
        if (pthread_create(&threadRaw[i], NULL, (void*) & doBasicLoop, &ifaceId[i])) err("error creating port thread");
#else
        if (pthread_create(&threadRaw[i], NULL, (void*) & doPortLoop, &ifaceId[i])) err("error creating port thread");
#endif
    }

    doMainLoop();
}

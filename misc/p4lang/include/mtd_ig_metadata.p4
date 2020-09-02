#ifndef _INGRESS_METADATA_P4_
#define _INGRESS_METADATA_P4_

/*
 * User defined metadata type
 */
struct ingress_metadata_t {
    SubIntId_t ingress_id;
    SubIntId_t source_id;
    SubIntId_t target_id;
    SubIntId_t aclport_id;
    NextHopId_t nexthop_id;
    SubIntId_t outport_id;
    SubIntId_t bridge_id;
    SubIntId_t bridge_src;
    SubIntId_t bridge_trg;
    bit<4> hash_id;
    ethertype_t ethertype;
    switch_vrf_t vrf;
    label_t mpls_label;
    bit<1>  dropping;
    bit<1>  punting;
    bit<1>  natted;
    bit<1>  need_recir;
    bit<3>  mpls_op_type;
    bit<3>  srv_op_type;
    bit<16> vlan_size;
    bit<1>  mpls0_remove;
    bit<1>  mpls1_remove;
    bit<1>  pppoe_ctrl_valid;
    bit<1>  pppoe_data_valid;
    bit<1>  mpls0_valid;
    bit<1>  mpls1_valid;
    bit<1>  llc_valid;
    bit<1>  arp_valid;
    bit<1>  ipv4_valid;
    bit<1>  ipv6_valid;
    layer4_port_t  layer4_srcprt;
    layer4_port_t  layer4_dstprt;
    bit<16> layer4_length;
}

#endif // _INGRESS_METADATA_P4_

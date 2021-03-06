/*
 * Copyright 2019-present GT RARE project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed On an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef _IG_CTL_PBR_P4_
#define _IG_CTL_PBR_P4_

#ifdef HAVE_PBR

control IngressControlPBR(inout headers hdr, inout ingress_metadata_t ig_md,
                          in ingress_intrinsic_metadata_t ig_intr_md)
{


    action act_normal() {
    }

    action act_setvrf(switch_vrf_t vrf_id) {
        ig_md.vrf = vrf_id;
    }

    action act_sethop(switch_vrf_t vrf_id, NextHopId_t nexthop_id) {
        ig_md.vrf = vrf_id;
        ig_md.nexthop_id = nexthop_id;
        ig_md.ipv4_valid = 0;
        ig_md.ipv6_valid = 0;
    }



    table tbl_ipv4_pbr {
        key = {
ig_md.vrf:
            exact;
hdr.ipv4.protocol:
            ternary;
hdr.ipv4.src_addr:
            ternary;
hdr.ipv4.dst_addr:
            ternary;
ig_md.layer4_srcprt:
            ternary;
ig_md.layer4_dstprt:
            ternary;
        }
        actions = {
            act_normal;
            act_setvrf;
            act_sethop;
            @defaultonly NoAction;
        }
        size = IPV4_PBRACL_TABLE_SIZE;
        const default_action = NoAction();
    }

    table tbl_ipv6_pbr {
        key = {
ig_md.vrf:
            exact;
hdr.ipv6.next_hdr:
            ternary;
hdr.ipv6.src_addr:
            ternary;
hdr.ipv6.dst_addr:
            ternary;
ig_md.layer4_srcprt:
            ternary;
ig_md.layer4_dstprt:
            ternary;
        }
        actions = {
            act_normal;
            act_setvrf;
            act_sethop;
            @defaultonly NoAction;
        }
        size = IPV6_PBRACL_TABLE_SIZE;
        const default_action = NoAction();
    }

    apply {
        if (ig_md.ipv4_valid==1)  {
            tbl_ipv4_pbr.apply();
        } else if (ig_md.ipv6_valid==1)  {
            tbl_ipv6_pbr.apply();
        }
    }
}

#endif

#endif // _IG_CTL_PBR_P4_


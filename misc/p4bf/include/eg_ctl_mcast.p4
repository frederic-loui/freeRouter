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

#ifndef _EG_CTL_MCAST_P4_
#define _EG_CTL_MCAST_P4_

#ifdef HAVE_MCAST

control EgressControlMcast(inout egress_headers_t hdr, inout egress_metadata_t eg_md,
                           in egress_intrinsic_metadata_t eg_intr_md,
                           inout egress_intrinsic_metadata_for_deparser_t eg_dprsr_md)
{

    action act_rawip(mac_addr_t dst_mac_addr, mac_addr_t src_mac_addr) {
        hdr.ethernet.src_mac_addr = src_mac_addr;
        hdr.ethernet.dst_mac_addr = dst_mac_addr;
        eg_md.target_id = (SubIntId_t)eg_intr_md.egress_rid;
    }


    table tbl_mcast {
        key = {
hdr.internal.session:
            exact;
eg_intr_md.egress_rid:
            exact;
        }
        actions = {
            act_rawip;
            @defaultonly NoAction;
        }
        size = IPV4_MCAST_TABLE_SIZE + IPV6_MCAST_TABLE_SIZE;
        const default_action = NoAction();
    }


    apply {

        tbl_mcast.apply();

    }


}

#endif

#endif // _EG_CTL_MCAST_P4_
#!/bin/sh
qemu-system-x86_64 -enable-kvm -k en-us -m 1024 -netdev user,id=a1 -device virtio-net-pci,netdev=a1 -no-reboot -kernel ../../binImg/rtr.krn -initrd ../../binImg/rtr.ird

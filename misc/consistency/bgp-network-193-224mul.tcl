set a [exec "show ipv4 bgp 1955 multicast allroute 193.224.0.0/15 | inc preference"]
puts "$a"

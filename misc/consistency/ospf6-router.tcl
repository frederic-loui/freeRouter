set a [exec "show ipv6 ospf 100 database 0 | inc router"]
puts "$a"

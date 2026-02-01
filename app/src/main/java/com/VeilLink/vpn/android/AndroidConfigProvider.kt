package com.veillink.vpn.android

import com.veillink.vpn.common.domain.ConfigProvider

/**
 * TODO: прокси/PAC/WPAD на сети при разрешенной локалке может раскрыть трафик. Поправить.
 * Плюс там есть приколы с этими же корпоративными сетями, когда они хотят весь трафик жестко гнать через свою проксю
 */
class AndroidConfigProvider : ConfigProvider {
    override suspend fun fetchConfigText(): String = DUMMY_XRAY_CONFIG.trimIndent()

    companion object {
        /*private const val DUMMY_XRAY_CONFIG ="""
            {
  "dns": {
    "final": "dns-remote",
    "independent_cache": true,
    "rules": [
      {
        "domain": [
          "dns.google"
        ],
        "server": "dns-direct"
      },
      {
        "outbound": [
          "any"
        ],
        "server": "dns-direct"
      }
    ],
    "servers": [
      {
        "address": "rcode://success",
        "tag": "dns-block"
      },
      {
        "address": "local",
        "detour": "direct",
        "tag": "dns-local"
      },
      {
        "address": "https://223.5.5.5/dns-query",
        "address_resolver": "dns-local",
        "detour": "direct",
        "strategy": "ipv4_only",
        "tag": "dns-direct"
      },
      {
        "address": "https://dns.google/dns-query",
        "address_resolver": "dns-direct",
        "strategy": "ipv4_only",
        "tag": "dns-remote"
      }
    ]
  },
  "inbounds": [
    {
      "domain_strategy": "",
      "endpoint_independent_nat": true,
      "inet4_address": [
        "172.19.0.1/28"
      ],
      "mtu": 1280,
      "sniff": true,
      "sniff_override_destination": false,
      "stack": "gvisor",
      "tag": "tun-in",
      "type": "tun"
    },
    {
      "domain_strategy": "",
      "listen": "127.0.0.1",
      "listen_port": 2080,
      "sniff": true,
      "sniff_override_destination": false,
      "tag": "mixed-in",
      "type": "mixed"
    }
  ],
  "log": {
    "level": "debug"
  },
  "outbounds": [
    {
      "domain_strategy": "prefer_ipv4",
      "flow": "xtls-rprx-vision",
      "packet_encoding": "",
      "server": "45.144.233.180",
      "server_port": 443,
      "tls": {
        "enabled": true,
        "insecure": false,
        "reality": {
          "enabled": true,
          "public_key": "bSGc-vlYhr_eRmYH_FqVjkRrpe2p9DwGCnvZj6a7yAw",
          "short_id": "a3b7c9d2e4f6a8b1"
        },
        "server_name": "www.cloudflare.com",
        "utls": {
          "enabled": true,
          "fingerprint": "chrome"
        }
      },
      "uuid": "11111111-2222-3333-4444-555555555555",
      "tag": "proxy",
      "type": "vless"
    },
    {
      "tag": "direct",
      "type": "direct"
    },
    {
      "tag": "bypass",
      "type": "direct"
    }
  ],
  "route": {
    "auto_detect_interface": false,
    "rule_set": [],
    "rules": [
      {
        "action": "hijack-dns",
        "port": [
          53
        ]
      },
      {
        "action": "hijack-dns",
        "protocol": [
          "dns"
        ]
      },
      {
        "action": "reject",
        "network": [
          "udp"
        ],
        "port": [
          443
        ],
        "port_range": []
      },
      {
        "action": "reject",
        "ip_cidr": [
          "224.0.0.0/3",
          "ff00::/8"
        ],
        "source_ip_cidr": [
          "224.0.0.0/3",
          "ff00::/8"
        ]
      }
    ]
  },
  "experimental": {
    "cache_file": {
      "enabled": true,
      "path": "/data/user/0/com.veillink.vpn/cache/cache.db"
    }
  },
}""";*/
        private const val DUMMY_XRAY_CONFIG = """
{
  "log": {
    "level": "debug"
  },
  "inbounds": [
    {
      "type": "tun",
      "tag": "tun-in",
      "stack":"gvisor",
      "endpoint_independent_nat": true,
      "address": [
        "172.19.0.1/30",
        "fdfe:dcba:9876::1/126"
      ],
      "mtu": 1300,
      "sniff": true
    }
  ],
  "outbounds": [
    {
      "type": "urltest",
      "tag": "auto",
      "outbounds": ["proxy"],
      "url": "https://cp.cloudflare.com/generate_204",
      "interval": "5m"
    },
    {
      "domain_strategy": "prefer_ipv4",
      "type": "vless",
      "tag": "proxy",
      "server": "45.144.233.180",
      "server_port": 443,
      "uuid": "11111111-2222-3333-4444-555555555555",
      "flow": "xtls-rprx-vision",
      "packet_encoding": "",
      "tls": {
        "enabled": true,
        "server_name": "www.cloudflare.com",
        "utls": {
          "enabled": true,
          "fingerprint": "chrome"
        },
        "reality": {
          "enabled": true,
          "public_key": "bSGc-vlYhr_eRmYH_FqVjkRrpe2p9DwGCnvZj6a7yAw",
          "short_id": "a3b7c9d2e4f6a8b1"
        }
      }
    },
    {
      "type": "direct",
      "tag": "direct"
    }
  ],
  "dns": {
  "servers": [
    {
      "tag": "local",
      "type": "local",
      "detour": "direct"
    },
    {
      "tag": "cf",
      "type": "https",
      "server": "1.1.1.1",
      "path": "/dns-query",
      "detour": "proxy",
      "tls": { "server_name": "cloudflare-dns.com" }
    }
  ],
  "rules": [
    { "domain_suffix": ["lan", "local"], "server": "local" }
  ],
  "final": "cf",
  "strategy": "ipv4_only"
},
  "route": {
    "auto_detect_interface": false,
    "final": "auto",
    "rules": [
      {
        "port": [53],
        "action": "hijack-dns"
      },
      {
        "protocol": "dns",
        "action": "hijack-dns"
      },
      {
        "ip_cidr": ["172.19.0.2/32"],
        "port": [853],
        "action": "reject"
      },
      {
        "ip_version": 6,
        "action": "reject"
      },
      {
        "ip_cidr": [
          "127.0.0.0/8"
        ],
        "outbound": "direct"
      },
      {
        "ip_is_private": true,
        "outbound": "direct"
      }
    ]
  },
  "experimental": {
    "cache_file": {
      "enabled": true,
      "path": "/data/user/0/com.veillink.vpn/cache/cache.db"
    }
  },
  "ntp": {
    "enabled": true,
    "server": "time.google.com",
    "server_port": 123,
    "detour": "proxy"
  }
}""";
    }
}
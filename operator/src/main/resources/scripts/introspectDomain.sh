#!/bin/sh

request_body=$(cat <<EOF
>>>  /u01/introspect/domain1/userConfigNodeManager.secure
#WebLogic User Configuration File; 2
#Thu Oct 04 21:07:06 GMT 2018
weblogic.management.username={AES}fq11xKVoE927O07IUKhQ00d4A8QY598Dvd+KSnHNTEA\=
weblogic.management.password={AES}LIxVY+aqI8KBkmlBTwkvAnQYQs4PS0FX3Ili4uLBggo\=

>>> EOF

@[2018-10-04T21:07:06.864 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/userKeyNodeManager.secure

>>>  /u01/introspect/domain1/userKeyNodeManager.secure
BPtNabkCIIc2IJp/TzZ9TzbUHG7O3xboteDytDO3XnwNhumdSpaUGKmcbusdmbOUY+4J2kteu6xJPWTzmNRAtg==

>>> EOF

@[2018-10-04T21:07:06.867 UTC][introspectDomain.py:105] Printing file /u01/introspect/domain1/topology.yaml

>>>  /u01/introspect/domain1/topology.yaml
domainValid: true
domain:
  name: "base_domain"
  adminServerName: "admin-server"
  configuredClusters:
    "mycluster":
      port: 8001
      servers:
        "managed-server1": {}
        "managed-server2": {}
  dynamicClusters: {}
  servers:
    "admin-server":
      port: 7001

>>> EOF
EOF
)

echo "$request_body"
exit 0

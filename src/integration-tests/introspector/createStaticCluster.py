# Copyright (c) 2018, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# purpose:
#
#    invoke on-line WLST to create a configured static cluster and two managed
#      servers that reference it.
#
#    NOTE: The static cluster must be configured using on-line WLST instead of
#    off-line WLST in order to reproduce OWLS 85530. On-line WLST produces the
#    following cluster configuration:
#
#    <cluster>
#      <name>c1</name>
#      <cluster-messaging-mode>unicast</cluster-messaging-mode>
#      <dynamic-servers>
#        <maximum-dynamic-server-count>0</maximum-dynamic-server-count>
#      </dynamic-servers>
#    </cluster>
#
#    assumes the admin user/pass was encoded to a userConfig/userKey by the
#      introspector these files have been mounted to /weblogic-operator/introspector
#
# usage:
#    wlst.sh createStaticCluster.py <url> <cluster_name>
#
# sample usage:
#    wlst.sh createStaticCluster.py t3://domain1-admin-server:7001 c1
#

import sys

url = sys.argv[1]
cluster_name = sys.argv[2]

connect(userConfigFile='/weblogic-operator/introspector/userConfigNodeManager.secure',userKeyFile='/tmp/userKeyNodeManager.secure.bin',url=url)

edit()
startEdit()

cl=cmo.createCluster(cluster_name)

# Create managed servers
for index in range(0, 2):
  cd('/')

  msIndex = index+1
  name = '%s%s' % ('ms', msIndex)

  create(name, 'Server')
  cd('/Servers/%s/' % name )
  print('managed server name is %s' % name);
  set('ListenPort', 8001)
  set('ListenAddress', '')
  set('Cluster', cl)

save()
activate()

print 'ok'
exit(exitcode=0)

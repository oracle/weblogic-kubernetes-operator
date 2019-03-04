# Copyright 2018, 2019, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.a

#
# purpose:
#
#    invoke on-line WLST to check if a datasource is working
#
#    assumes the admin user/pass was encoded to a userConfig/userKey by the 
#      introspector these files have been mounted to /weblogic-operator/introspector
#
# usage:
#    wlst.sh checkDataSource.py url server-name data-source-name
#
# sample usage:
#    wlst.sh checkDataSource.py t3://domain1-admin-server:7001 admin-server mysqlDS
#

import sys

url = sys.argv[1]
server_name = sys.argv[2]
data_source_name = sys.argv[3]

connect(userConfigFile='/weblogic-operator/introspector/userConfigNodeManager.secure',userKeyFile='/tmp/userKeyNodeManager.secure.bin',url=url)

serverRuntime()

cd('/JDBCServiceRuntime/' + server_name + '/JDBCDataSourceRuntimeMBeans/' + data_source_name)

ret=cmo.testPool()

if ret is None:
  print 'ok'
  exit(exitcode=0)
else:
  print 'error: ' + ret
  exit(exitcode=1)

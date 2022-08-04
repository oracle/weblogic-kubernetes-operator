# Copyright (c) 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import sys
import traceback

def create_new_dyncluster():
  try:
    if connected == 'false':
      print 'connecting to admin server'
      connect(admin_username, admin_password, 't3://' + admin_host + ':' + admin_port)

      edit()
      startEdit()
      cd('/')

      #
      # Create the server template for the dynamic servers.
      #
      dynamicServerTemplate=cmo.createServerTemplate("dynamic-cluster-server-template")

      #
      # Create the dynamic cluster and set the dynamic servers.
      #
      dynCluster=cmo.createCluster(new_cluster_name)
      dynServers=dynCluster.getDynamicServers()
      dynServers.setServerTemplate(dynamicServerTemplate)
      dynServers.setServerNamePrefix(new_ms_name_prefix)
      dynServers.setCalculatedListenPorts(false)
      dynServers.setDynamicClusterSize(2)
      dynServers.setMaxDynamicClusterSize(2)
      dynServers.setMaximumDynamicServerCount(2)
      dynServers.setMinDynamicClusterSize(1)

      dynamicServerTemplate.setCluster(dynCluster)

      save()
      activate()
      exit()

  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    exit(exitcode=1)
  except:
    print 'create new dynamic cluster failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

create_new_dyncluster()
exit()

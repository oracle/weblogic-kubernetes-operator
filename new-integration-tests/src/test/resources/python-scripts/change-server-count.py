# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

def change_server_count():
  try:
    print 'connecting to admin server'
    connect(admin_username, admin_password, t3url)
    edit()
    startEdit()
    cd('/Clusters/' + cluster_name + '/DynamicServers/' + cluster_name)
    cmo.setDynamicClusterSize(6)
    cmo.setMaxDynamicClusterSize(6)
    cmo.setMinDynamicClusterSize(2)
    save()
    activate()
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    usage()
    exit(exitcode=1)
  except:
    print 'Deployment failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

if __name__== "main": 
  change_server_count()
  exit()

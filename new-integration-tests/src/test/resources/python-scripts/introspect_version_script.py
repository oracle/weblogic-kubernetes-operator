# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import sys, traceback

def change_server_count():
  try:
    connect_to_adminserver()
    edit()
    startEdit()
    cd('/Clusters/' + cluster_name + '/DynamicServers/' + cluster_name)
    cmo.setDynamicClusterSize(int(max_cluster_size))
    cmo.setMaxDynamicClusterSize(int(max_cluster_size))
    save()
    activate()
    disconnect()
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])    
    exit(exitcode=1)
  except:
    print 'Updating cluster size failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

def change_admin_port():
  try:
    connect_to_adminserver()
    admin_server_name = get_admin_server_name()
    edit()
    startEdit()
    cd('/Servers/' + admin_server_name)
    cmo.setListenPort(int(new_admin_port))
    save()
    activate()
    disconnect()
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    exit(exitcode=1)
  except:
    print 'Changing admin port failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

def create_static_cluster():
  try:    
    connect_to_adminserver()
    edit()
    startEdit()
    cd('/')
    cmo.createCluster(cluster_name)
    cd('/Clusters/' + cluster_name)
    cmo.setClusterMessagingMode('unicast')

    for count in range(1, int(server_count) + 1):
      server = server_prefix + str(count)
      cd('/')
      cmo.createServer(server)

      cd('/Servers/' + server)
      cmo.setListenAddress('')
      cmo.setListenPort(8001)
      cmo.setCluster(getMBean('/Clusters/' + cluster_name))

    save()
    activate()
    disconnect()
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    exit(exitcode=1)
  except:
    print 'Creating new cluster ' + cluster_name + ' failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

def connect_to_adminserver():
  try:
    if connected == 'false':    
      print 'connecting to admin server'
      connect(admin_username, admin_password, 't3://' + admin_host + ':' + admin_port)
  except NameError, e:
    print('Apparently properties not set.')
    print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
    exit(exitcode=1)
  except:
    print 'Connecting to admin server failed'
    print dumpStack()
    apply(traceback.print_exception, sys.exc_info())
    exit(exitcode=1)

def get_admin_server_name():
  connect_to_adminserver()
  serverRuntime()
  cd('/')
  admin_server_name = cmo.getName()
  serverConfig()
  return admin_server_name

if __name__== "main":
  if(test_name == 'change_server_count'):
    change_server_count()
  if(test_name == 'change_admin_port'):
    change_admin_port()
  if(test_name == 'create_cluster'):
    create_static_cluster()
  exit()

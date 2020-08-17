# Copyright (c) 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import os
from weblogic.management.configuration import TargetMBean

admin_username=sys.argv[1]
admin_password=sys.argv[2]
admin_t3_url=sys.argv[3]
cluster_name=sys.argv[4]

print 'admin_username: ' + admin_username
print 'admin_password: ' + admin_password
print 'admin_t3_url: ' + admin_t3_url
print 'cluster_name: ' + cluster_name

try:
  print 'connecting to admin server'
  connect(admin_username, admin_password, admin_t3_url)
  print 'Running config-wls-logging-startup.py to config startup for Cluster: %s' % cluster_name
  edit()
  startEdit()

  # test if the startup class is already configured. If this mbean does not exit then
  # this startup class has not yet been configured in this cluster and we can create it
  cd('/')
  startup_class = getMBean('/StartupClasses/LoggingExporterStartupClass')
  if startup_class == None:
    cmo.createStartupClass('LoggingExporterStartupClass')

  cd('/StartupClasses/LoggingExporterStartupClass')

  # configure classname
  cmo.setClassName('weblogic.logging.exporter.Startup')

  # set the target(s) for this startuo class
  set('Targets',jarray.array([ObjectName('com.bea:Name='+cluster_name+',Type=Cluster')], ObjectName))

  save()
  activate(block='true')

  print('Done configuring startup of WebLogic logging exporter for Cluster: %s' % cluster_name);
  disconnect()
except NameError, e:
  print('Apparently properties not set.')
  print('Please check the property: ', sys.exc_info()[0], sys.exc_info()[1])
  exit(exitcode=1)
except:
  print 'set failed'
  print dumpStack()
  apply(traceback.print_exception, sys.exc_info())
  exit(exitcode=1)

exit()

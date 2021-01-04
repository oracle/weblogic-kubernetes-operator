# Copyright (c) 2020, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
  
def createDataSource(dsName, dsURL, dsDriver, dsUser, dsPassword, dsTarget):
    cd('/')
    cmo.createJDBCSystemResource(dsName)
    cd('/JDBCSystemResources/'+dsName+'/JDBCResource/'+dsName)
    cmo.setName(dsName)
    
    cd('/JDBCSystemResources/'+dsName+'/JDBCResource/'+dsName+'/JDBCDataSourceParams/'+dsName)
    set('JNDINames',jarray.array([String('jdbc/'+dsName)], String))
    
    cd('/JDBCSystemResources/'+dsName+'/JDBCResource/'+dsName)
    cmo.setDatasourceType('GENERIC')
    
    cd('/JDBCSystemResources/'+dsName+'/JDBCResource/'+dsName+'/JDBCDriverParams/'+dsName)
    cmo.setUrl(dsURL)
    
    cmo.setDriverName(dsDriver)
    set('Password', dsPassword)
    
    cd('/JDBCSystemResources/'+dsName+'/JDBCResource/'+dsName+'/JDBCConnectionPoolParams/'+dsName)
    cmo.setTestTableName('SQL SELECT 1\r\n\r\n')
    
    cd('/JDBCSystemResources/'+dsName+'/JDBCResource/'+dsName+'/JDBCDriverParams/'+dsName+'/Properties/'+dsName)
    cmo.createProperty('user')
    
    cd('/JDBCSystemResources/'+dsName+'/JDBCResource/'+dsName+'/JDBCDriverParams/'+dsName+'/Properties/'+dsName+'/Properties/user')
    cmo.setValue(dsUser)
    
    cd('/JDBCSystemResources/'+dsName+'/JDBCResource/'+dsName+'/JDBCDataSourceParams/'+dsName)
    cmo.setGlobalTransactionsProtocol('OnePhaseCommit')
    
    cd('/JDBCSystemResources/'+dsName)
    set('Targets',jarray.array([ObjectName('com.bea:Name=' + dsTarget + ',Type=Cluster')], ObjectName))


admin_t3_url = 't3://'+admin_host+':'+admin_port
try:
  connect(admin_username, admin_password, admin_t3_url)
  edit()
  startEdit()
  createDataSource(dsName, dsUrl, dsDriver, dsUser, dsPassword, dsTarget)
  activate()
  disconnect()
  exit()
except:
  print 'Creating JDBC datasource failed'
  print dumpStack()
  apply(traceback.print_exception, sys.exc_info())
  exit(exitcode=1)
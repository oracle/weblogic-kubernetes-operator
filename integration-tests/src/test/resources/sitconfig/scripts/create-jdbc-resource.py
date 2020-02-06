# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
  
def createDataSource(dsName, dsURL, dsDriver, dsUser, dsPassword, dsTarget):
    edit()
    startEdit()
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
    set('Targets',jarray.array([ObjectName('com.bea:Name=admin-server,Type=Server'), ObjectName('com.bea:Name=cluster-1,Type=Cluster')], ObjectName))
    
    save()    
    activate()    

connect('weblogic', 'welcome1', 't3://DOMAINUID-admin-server:7001')
createDataSource('JdbcTestDataSource-1', 'JDBC_URL', 'com.mysql.cj.jdbc.Driver', 'root', 'root123', 'cluster-1')
disconnect()
exit()
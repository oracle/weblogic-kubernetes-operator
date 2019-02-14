# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This python script is used to create a WebLogic domain

# This version of createDomain demonstrates sit-config override and NM rework
# by commenting out all settings related to tho listen addresses, log file locations,
# and NM setup.

# Lines marked with "subst-ignore-missing" are not flagged as a failure
# by the macro substitution script when they have an undefined macro.


# Read the domain secrets

file = open('/weblogic-operator/secrets/username', 'r')		
admin_username = file.read()		
file.close()		
		
file = open('/weblogic-operator/secrets/password', 'r')		
admin_password = file.read()		
file.close()

print('domain_path        : [%s]' % '${DOMAIN_HOME}');
print('domain_name        : [${DOMAIN_NAME}]');
print('admin_username     : [%s]' % admin_username);
print('admin_port         : [${ADMIN_PORT}]');
print('cluster_name       : [%s]' % '${CLUSTER_NAME}');
print('server_port        : [%s]' % ${MANAGED_SERVER_PORT});
print('cluster_type       : [%s]' % '${CLUSTER_TYPE}');

# Open default domain template
# ============================
readTemplate("/u01/oracle/wlserver/common/templates/wls/wls.jar")

set('Name', '${DOMAIN_NAME}')
setOption('DomainName', '${DOMAIN_NAME}')
#create('${DOMAIN_NAME}','Log')
#cd('/Log/${DOMAIN_NAME}');

# Configure the Administration Server
# ===================================
cd('/Servers/AdminServer')
#set('ListenAddress', '${DOMAIN_UID}-${ADMIN_NAME}')  # what override should set the value too
#set('ListenAddress', 'unresolvable-dns-name')        # an invalid value, unesolvable as a DNS name
#set('ListenAddress', 'junk')                         # an invalid value, unesolvable as a DNS name
set('ListenPort', ${ADMIN_PORT})
set('Name', '${ADMIN_NAME}')
set('AdministrationPort', ${ADMINISTRATION_PORT})
set('AdministrationPortEnabled', 'true')

cd('/Servers/${ADMIN_NAME}')
set('MaxMessageSize',999999)
create('T3Channel1', 'NetworkAccessPoint')
cd('/Servers/${ADMIN_NAME}/NetworkAccessPoints/T3Channel1')
set('ListenAddress', 'unresolvable-dns-name')   #an invalid value, and an unresolvable DNS name
set('ListenPort', ${T3CHANNEL1_PORT})
set('PublicPort', 22222)
set('PublicAddress', 'unresolvable-dns-name')   #an invalid value, and an unresolvable DNS name

cd('/Servers/${ADMIN_NAME}')
create('T3Channel2', 'NetworkAccessPoint')
cd('/Servers/${ADMIN_NAME}/NetworkAccessPoints/T3Channel2')
set('ListenAddress', 'junk')                    #an invalid value, but a resolvable DNS name
set('ListenPort', ${T3CHANNEL2_PORT})
set('PublicPort', 22222)
set('PublicAddress', 'junk')                    #an invalid value, but a resolvable DNS name

cd('/Servers/${ADMIN_NAME}')
create('T3Channel3', 'NetworkAccessPoint')
cd('/Servers/${ADMIN_NAME}/NetworkAccessPoints/T3Channel3')
#set('ListenAddress', 'junk')                   #not setting this value at all, commented out on purpose
set('ListenPort', ${T3CHANNEL3_PORT})
#set('PublicPort', 40014)                       #not setting this value at all, commented out on purpose
#set('PublicAddress', 'junk')                   #not setting this value at all, commented out on purpose

cd('/Servers/${ADMIN_NAME}')
ssl = create('${ADMIN_NAME}','SSL')
cd('/Servers/${ADMIN_NAME}/SSL/${ADMIN_NAME}')
set('Enabled', 'true')

#cd('/Servers/${ADMIN_NAME}')
#create('${ADMIN_NAME}', 'Log')
#cd('/Servers/${ADMIN_NAME}/Log/${ADMIN_NAME}')
#set('FileName', '${LOG_HOME}/${ADMIN_NAME}.log')


# Set the admin user's username and password
# ==========================================
# Password required - otherwise script will fail.
cd('/Security/${DOMAIN_NAME}/User/weblogic')
cmo.setName(admin_username)
cmo.setPassword(admin_password)


# Configure a couple of non-clustered managed servers
# ==========================================
cd('/')
mname='standalone1'
create(mname, 'Server')
cd('/Servers/%s/' % mname )
# set('ListenAddress', '${DOMAIN_UID}-%s' % mname)
set('ListenPort', 6123)
set('MaxMessageSize', 7777777)

cd('/')
mname='standalone2'
create(mname, 'Server')
cd('/Servers/%s/' % mname )
# set('ListenAddress', '${DOMAIN_UID}-%s' % mname)
set('ListenPort', 6124)
# set('MaxMessageSize', 7777777) # deliberately unset, so should be at the default of 10000000


# Write the domain and close the domain template
# ==============================================
setOption('OverwriteDomain', 'true')

# Setup a diagnostics module
# ============================================
def createWLDFModule(moduleName):
  cd('/')
  print 'create WLDFSystemResource'
  create(moduleName, 'WLDFSystemResource')
  cd('/WLDFSystemResource/' + moduleName)
  #set('Target',dsTarget)
  cd('/WLDFSystemResource/' + moduleName + '/WLDFResource/' + moduleName)
  cmo.setName(moduleName)

createWLDFModule('myWLDF')

# Setup a datasource
# ============================================
def createDataSource(dsName,dsJNDI,dsHost,dsSID,dsTarget):
  cd('/')
  print 'create JDBCSystemResource'
  create(dsName, 'JDBCSystemResource')
  cd('/JDBCSystemResource/' + dsName)
  #set('Target',dsTarget)
  cd('/JDBCSystemResource/' + dsName + '/JdbcResource/' + dsName)
  cmo.setName(dsName)

  print 'create JDBCDataSourceParams'
  cd('/JDBCSystemResource/' + dsName + '/JdbcResource/' + dsName)
  create('testDataSourceParams','JDBCDataSourceParams')
  cd('JDBCDataSourceParams/NO_NAME_0')
  set('JNDIName', java.lang.String(dsJNDI))
  set('GlobalTransactionsProtocol', java.lang.String('None'))

  print 'create JDBCDriverParams'
  cd('/JDBCSystemResource/' + dsName + '/JdbcResource/' + dsName)
  create('testDriverParams','JDBCDriverParams')
  cd('JDBCDriverParams/NO_NAME_0')
  set('DriverName','oracle.jdbc.OracleDriver')
  set('URL','jdbc:oracle:thin:@' + dsHost + ':1521:' + dsSID)
  #set('PasswordEncrypted', 'manager')
  set('UseXADataSourceInterface', 'false')
 
  print 'create JDBCDriverParams Properties'
  create('testProperties','Properties')
  cd('Properties/NO_NAME_0')
  create('user','Property')
  cd('Property')
  cd('user')
  set('Value', 'scott')
 
  print 'create JDBCConnectionPoolParams'
  cd('/JDBCSystemResource/' + dsName + '/JdbcResource/' + dsName)
  create('testJdbcConnectionPoolParams','JDBCConnectionPoolParams')
  cd('JDBCConnectionPoolParams/NO_NAME_0')
  set('TestTableName','SQL SELECT 1 FROM DUAL')
  set('InitialCapacity', 0)
  set('MinCapacity', 0)

createDataSource('testDS','testDS','myoriginalhostname','myoriginalsid','${ADMIN_NAME}')

# Create a cluster
# ======================
cd('/')
cl=create('${CLUSTER_NAME}', 'Cluster')

if '${CLUSTER_TYPE}' == "CONFIGURED":

  # Create managed servers
  for index in range(0, ${CONFIGURED_MANAGED_SERVER_COUNT}):
    cd('/')

    msIndex = index+1
    name = '${MANAGED_SERVER_NAME_BASE}%s' % msIndex
    # name_svc = '${MANAGED_SERVER_NAME_BASE}%s' % msIndex

    create(name, 'Server')
    cd('/Servers/%s/' % name )
    print('managed server name is %s' % name);
    # set('ListenAddress', '${DOMAIN_UID}-%s' % name_svc)
    set('ListenPort', ${MANAGED_SERVER_PORT})
    set('NumOfRetriesBeforeMSIMode', 0)
    set('RetryIntervalBeforeMSIMode', 1)
    set('Cluster', '${CLUSTER_NAME}')

    #create(name,'Log')
    #cd('/Servers/%s/Log/%s' % (name, name))
    #set('FileName', '${LOG_HOME}/%s.log' % name)
else:
  print('Configuring Dynamic Cluster %s' % '${CLUSTER_NAME}')

  templateName = '${CLUSTER_NAME}' + "-template"
  print('Creating Server Template: %s' % templateName)
  st1=create(templateName, 'ServerTemplate')
  print('Done creating Server Template: %s' % templateName)
  cd('/ServerTemplates/%s' % templateName)
  cmo.setListenPort(${MANAGED_SERVER_PORT})
  #cmo.setListenAddress('${DOMAIN_UID}-${MANAGED_SERVER_NAME_BASE}${id}') # subst-ignore-missing
  cmo.setCluster(cl)
  print('Done setting attributes for Server Template: %s' % templateName);


  cd('/Clusters/%s' % '${CLUSTER_NAME}')
  create('${CLUSTER_NAME}', 'DynamicServers')
  cd('DynamicServers/%s' % '${CLUSTER_NAME}')
  set('ServerTemplate', st1)
  set('ServerNamePrefix', "${MANAGED_SERVER_NAME_BASE}")
  set('DynamicClusterSize', ${CONFIGURED_MANAGED_SERVER_COUNT})
  set('MaxDynamicClusterSize', ${CONFIGURED_MANAGED_SERVER_COUNT})
  set('CalculatedListenPorts', false)
  #set('Id', 1)

  print('Done setting attributes for Dynamic Cluster: %s' % '${CLUSTER_NAME}');

# Write Domain
# ============
writeDomain('${DOMAIN_HOME}')
closeTemplate()
print 'Domain Created'

# Update Domain
readDomain('${DOMAIN_HOME}')
cd('/')
cmo.setProductionModeEnabled(${PRODUCTION_MODE_ENABLED})
updateDomain()
closeDomain()
print 'Domain Updated'
print 'Done'

# Exit WLST
# =========
exit()


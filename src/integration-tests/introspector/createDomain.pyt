# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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
#set('ListenAddress', '${DOMAIN_UID}-${ADMIN_NAME}')
#set('ListenAddress', 'invalid-${DOMAIN_UID}-${ADMIN_NAME}')
set('ListenPort', ${ADMIN_PORT})
set('Name', '${ADMIN_NAME}')

create('T3Channel', 'NetworkAccessPoint')
cd('/Servers/${ADMIN_NAME}/NetworkAccessPoints/T3Channel')
set('PublicPort', ${T3_CHANNEL_PORT})
set('PublicAddress', '${T3_PUBLIC_ADDRESS}')
#set('ListenAddress', '${DOMAIN_UID}-${ADMIN_NAME}')
#set('ListenAddress', 'invalid-${DOMAIN_UID}-${ADMIN_NAME}')
set('ListenPort', ${T3_CHANNEL_PORT})

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

# Write the domain and close the domain template
# ==============================================
setOption('OverwriteDomain', 'true')

# Configure the node manager
# ==========================
# cd('/NMProperties')
# set('ListenAddress','0.0.0.0')
# set('ListenPort',5556)
# set('CrashRecoveryEnabled', 'true')
# set('NativeVersionEnabled', 'true')
# set('StartScriptEnabled', 'false')
# set('SecureListener', 'false')
# set('LogLevel', 'FINEST')
# set('DomainsDirRemoteSharingEnabled', 'true')

# Set the Node Manager user name and password (domain name will change after writeDomain)
# cd('/SecurityConfiguration/base_domain')
# set('NodeManagerUsername', admin_username)
# set('NodeManagerPasswordEncrypted', admin_password)

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


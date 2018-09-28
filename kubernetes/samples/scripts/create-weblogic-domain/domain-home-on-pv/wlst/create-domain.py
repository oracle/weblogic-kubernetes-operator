# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This python script is used to create a WebLogic domain

# Read the domain secrets from the common python file
execfile("%CREATE_DOMAIN_SCRIPT_DIR%/read-domain-secret.py")

server_port        = %MANAGED_SERVER_PORT%
domain_path        = os.environ.get("DOMAIN_HOME")
cluster_name       = "%CLUSTER_NAME%"
number_of_ms       = %CONFIGURED_MANAGED_SERVER_COUNT%
cluster_type       = "%CLUSTER_TYPE%"
domain_logs        = os.environ.get("DOMAIN_LOGS_DIR")

print('domain_path        : [%s]' % domain_path);
print('domain_name        : [%DOMAIN_NAME%]');
print('admin_username     : [%s]' % admin_username);
print('admin_port         : [%ADMIN_PORT%]');
print('cluster_name       : [%s]' % cluster_name);
print('server_port        : [%s]' % server_port);
print('cluster_type       : [%s]' % cluster_type);

# Open default domain template
# ============================
readTemplate("/u01/oracle/wlserver/common/templates/wls/wls.jar")

set('Name', '%DOMAIN_NAME%')
setOption('DomainName', '%DOMAIN_NAME%')
create('%DOMAIN_NAME%','Log')
cd('/Log/%DOMAIN_NAME%');
set('FileName', '%DOMAIN_LOGS_DIR%/%DOMAIN_NAME%.log')

# Configure the Administration Server
# ===================================
cd('/Servers/AdminServer')
set('ListenAddress', '%DOMAIN_UID%-%ADMIN_SERVER_NAME_SVC%')
set('ListenPort', %ADMIN_PORT%)
set('Name', '%ADMIN_SERVER_NAME%')

create('T3Channel', 'NetworkAccessPoint')
cd('/Servers/%ADMIN_SERVER_NAME%/NetworkAccessPoints/T3Channel')
set('PublicPort', %T3_CHANNEL_PORT%)
set('PublicAddress', '%T3_PUBLIC_ADDRESS%')
set('ListenAddress', '%DOMAIN_UID%-%ADMIN_SERVER_NAME_SVC%')
set('ListenPort', %T3_CHANNEL_PORT%)

cd('/Servers/%ADMIN_SERVER_NAME%')
create('%ADMIN_SERVER_NAME%', 'Log')
cd('/Servers/%ADMIN_SERVER_NAME%/Log/%ADMIN_SERVER_NAME%')
set('FileName', '%DOMAIN_LOGS_DIR%/%ADMIN_SERVER_NAME%.log')

# Set the admin user's username and password
# ==========================================
cd('/Security/%DOMAIN_NAME%/User/weblogic')
cmo.setName(admin_username)
cmo.setPassword(admin_password)

# Write the domain and close the domain template
# ==============================================
setOption('OverwriteDomain', 'true')

# Configure the node manager
# ==========================
cd('/NMProperties')
set('ListenAddress','0.0.0.0')
set('ListenPort',5556)
set('CrashRecoveryEnabled', 'true')
set('NativeVersionEnabled', 'true')
set('StartScriptEnabled', 'false')
set('SecureListener', 'false')
set('LogLevel', 'FINEST')
set('DomainsDirRemoteSharingEnabled', 'true')

# Set the Node Manager user name and password (domain name will change after writeDomain)
cd('/SecurityConfiguration/base_domain')
set('NodeManagerUsername', admin_username)
set('NodeManagerPasswordEncrypted', admin_password)

# Create a cluster
# ======================
cd('/')
cl=create(cluster_name, 'Cluster')

if cluster_type == "CONFIGURED":

  # Create managed servers
  for index in range(0, number_of_ms):
    cd('/')

    msIndex = index+1
    name = '%MANAGED_SERVER_NAME_BASE%%s' % msIndex
    name_svc = '%MANAGED_SERVER_NAME_BASE_SVC%%s' % msIndex

    create(name, 'Server')
    cd('/Servers/%s/' % name )
    print('managed server name is %s' % name);
    set('ListenAddress', '%DOMAIN_UID%-%s' % name_svc)
    set('ListenPort', server_port)
    set('NumOfRetriesBeforeMSIMode', 0)
    set('RetryIntervalBeforeMSIMode', 1)
    set('Cluster', cluster_name)

    create(name,'Log')
    cd('/Servers/%s/Log/%s' % (name, name))
    set('FileName', '%DOMAIN_LOGS_DIR%/%s.log' % name)
else:
  print('Configuring Dynamic Cluster %s' % cluster_name)

  templateName = cluster_name + "-template"
  print('Creating Server Template: %s' % templateName)
  st1=create(templateName, 'ServerTemplate')
  print('Done creating Server Template: %s' % templateName)
  cd('/ServerTemplates/%s' % templateName)
  cmo.setListenPort(server_port)
  cmo.setListenAddress('%DOMAIN_UID%-%MANAGED_SERVER_NAME_BASE_SVC%${id}')
  cmo.setCluster(cl)
  print('Done setting attributes for Server Template: %s' % templateName);


  cd('/Clusters/%s' % cluster_name)
  create(cluster_name, 'DynamicServers')
  cd('DynamicServers/%s' % cluster_name)
  set('ServerTemplate', st1)
  set('ServerNamePrefix', "%MANAGED_SERVER_NAME_BASE%")
  set('DynamicClusterSize', number_of_ms)
  set('MaxDynamicClusterSize', number_of_ms)
  set('CalculatedListenPorts', false)
  set('Id', 1)

  print('Done setting attributes for Dynamic Cluster: %s' % cluster_name);

# Write Domain
# ============
writeDomain(domain_path)
closeTemplate()
print 'Domain Created'

# Update Domain
readDomain(domain_path)
cd('/')
cmo.setProductionModeEnabled(%PRODUCTION_MODE_ENABLED%)
updateDomain()
closeDomain()
print 'Domain Updated'
print 'Done'

# Exit WLST
# =========
exit()

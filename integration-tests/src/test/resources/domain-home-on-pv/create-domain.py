# Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

def getEnvVar(var):
  val=os.environ.get(var)
  if val==None:
    print "ERROR: Env var ",var, " not set."
    sys.exit(1)
  return val

# This python script is used to create a WebLogic domain

domain_uid                   = getEnvVar("DOMAIN_UID")
server_port                  = int(getEnvVar("MANAGED_SERVER_PORT"))
domain_path                  = getEnvVar("DOMAIN_HOME")
cluster_name                 = getEnvVar("CLUSTER_NAME")
admin_server_name            = getEnvVar("ADMIN_SERVER_NAME")
admin_server_name_svc        = getEnvVar("ADMIN_SERVER_NAME_SVC")
admin_port                   = int(getEnvVar("ADMIN_PORT"))
domain_name                  = getEnvVar("DOMAIN_NAME")
t3_channel_port              = int(getEnvVar("T3_CHANNEL_PORT"))
t3_public_address            = getEnvVar("T3_PUBLIC_ADDRESS")
number_of_ms                 = int(getEnvVar("CONFIGURED_MANAGED_SERVER_COUNT"))
cluster_type                 = getEnvVar("CLUSTER_TYPE")
managed_server_name_base     = getEnvVar("MANAGED_SERVER_NAME_BASE")
managed_server_name_base_svc = getEnvVar("MANAGED_SERVER_NAME_BASE_SVC")
domain_logs                  = getEnvVar("DOMAIN_LOGS_DIR")
script_dir                   = getEnvVar("CREATE_DOMAIN_SCRIPT_DIR")
production_mode_enabled      = getEnvVar("PRODUCTION_MODE_ENABLED")

# Read the domain secrets from the common python file
execfile('%s/read-domain-secret.py' % script_dir)

print('domain_path        : [%s]' % domain_path);
print('domain_name        : [%s]' % domain_name);
print('admin_server_name  : [%s]' % admin_server_name);
print('admin_username     : [%s]' % admin_username);
print('admin_port         : [%s]' % admin_port);
print('cluster_name       : [%s]' % cluster_name);
print('server_port        : [%s]' % server_port);
# Open default domain template
# ============================
readTemplate("/u01/oracle/wlserver/common/templates/wls/wls.jar")

set('Name', domain_name)
setOption('DomainName', domain_name)

# Configure the Administration Server
# ===================================
cd('/Servers/AdminServer')
set('ListenPort', admin_port)
set('Name', admin_server_name)

create('T3Channel', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/T3Channel' % admin_server_name)
set('PublicPort', t3_channel_port)
set('PublicAddress', t3_public_address)
set('ListenPort', t3_channel_port)

# Set the admin user's username and password
# ==========================================
cd('/Security/%s/User/weblogic' % domain_name)
cmo.setName(admin_username)
cmo.setPassword(admin_password)

# Write the domain and close the domain template
# ==============================================
setOption('OverwriteDomain', 'true')

# Create a cluster
# ======================
cd('/')
cl=create(cluster_name, 'Cluster')

if cluster_type == "CONFIGURED":

  # Create managed servers
  for index in range(0, number_of_ms):
    cd('/')

    msIndex = index+1
    name = '%s%s' % (managed_server_name_base, msIndex)
    name_svc = '%s%s' % (managed_server_name_base_svc, msIndex)

    create(name, 'Server')
    cd('/Servers/%s/' % name )
    print('managed server name is %s' % name);
    set('ListenPort', server_port)
    set('NumOfRetriesBeforeMSIMode', 0)
    set('RetryIntervalBeforeMSIMode', 1)
    set('Cluster', cluster_name)

else:
  print('Configuring Dynamic Cluster %s' % cluster_name)

  templateName = cluster_name + "-template"
  print('Creating Server Template: %s' % templateName)
  st1=create(templateName, 'ServerTemplate')
  print('Done creating Server Template: %s' % templateName)
  cd('/ServerTemplates/%s' % templateName)
  cmo.setListenPort(server_port)
  cmo.setCluster(cl)
  print('Done setting attributes for Server Template: %s' % templateName);

  templateChannelName = cluster_name + "-NAP"
  print('Creating Server Template NAP: %s' % cluster_name + "-NAP")
  create(templateChannelName, 'NetworkAccessPoint')
  cd('NetworkAccessPoints/%s' % templateChannelName)
  set('PublicPort', server_port + 10)
  set('ListenPort', server_port + 10)
  print('Done creating Server Template NAP: %s' % cluster_name + "-NAP")

  cd('/Clusters/%s' % cluster_name)
  create(cluster_name, 'DynamicServers')
  cd('DynamicServers/%s' % cluster_name)
  set('ServerTemplate', st1)
  set('ServerNamePrefix', managed_server_name_base)
  set('DynamicClusterSize', number_of_ms)
  set('MaxDynamicClusterSize', number_of_ms)
  set('CalculatedListenPorts', false)
  # set('Id', 1)

  print('Done setting attributes for Dynamic Cluster: %s' % cluster_name);

# Write Domain
# ============
writeDomain(domain_path)
closeTemplate()
print 'Domain Created'

# Update Domain
readDomain(domain_path)
cd('/')
if production_mode_enabled == "true":
  cmo.setProductionModeEnabled(true)
else: 
  cmo.setProductionModeEnabled(false)
updateDomain()
closeDomain()
print 'Domain Updated'
print 'Done'

# Exit WLST
# =========
exit()

# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

def getEnvVar(var):
  val=os.environ.get(var)
  if val==None:
    print "ERROR: Env var ",var, " not set."
    sys.exit(1)
  return val

# This python script is used to create a WebLogic domain

#domain_uid                   = getEnvVar("DOMAIN_UID")
server_port                  = int(os.environ.get("MANAGED_SERVER_PORT"))
domain_path                  = os.environ.get("DOMAIN_HOME")
cluster_name                 = CLUSTER_NAME
admin_server_name            = ADMIN_NAME
#admin_server_name_svc        = getEnvVar("ADMIN_SERVER_NAME_SVC")
admin_port                   = int(os.environ.get("ADMIN_PORT"))
domain_name                  = os.environ.get("DOMAIN_NAME")
t3_channel_port              = int(T3_CHANNEL_PORT)
t3_public_address            = T3_PUBLIC_ADDRESS
number_of_ms                 = int(CONFIGURED_MANAGED_SERVER_COUNT)
cluster_type                 = CLUSTER_TYPE
managed_server_name_base     = MANAGED_SERVER_NAME_BASE
#managed_server_name_base_svc = MANAGED_SERVER_NAME_BASE_SVC
#domain_logs                  = getEnvVar("DOMAIN_LOGS_DIR")
#script_dir                   = getEnvVar("CREATE_DOMAIN_SCRIPT_DIR")
production_mode_enabled      = PRODUCTION_MODE_ENABLED

# antaryami.panigrahi@oracle.com
cluster_name_2                 = "dataCluster"
managed_server_name_base_2     = "new-" + managed_server_name_base
#managed_server_name_base_svc_2  = "new-" + managed_server_name_base_svc

# Read the domain secrets from the common python file
#execfile('%s/read-domain-secret.py' % script_dir)

print('domain_path        : [%s]' % domain_path);
print('domain_name        : [%s]' % domain_name);
print('admin_server_name  : [%s]' % admin_server_name);
print('admin_username     : [%s]' % username);
print('admin_port         : [%s]' % admin_port);
print('cluster_name       : [%s]' % cluster_name);
print('cluster_name_2     : [%s]' % cluster_name_2);
print('server_port        : [%s]' % server_port);
# Open default domain template
# ============================
readTemplate("/u01/oracle/wlserver/common/templates/wls/wls.jar")

set('Name', domain_name)
setOption('DomainName', domain_name)
create(domain_name,'Log')
cd('/Log/%s' % domain_name);
set('FileName', '%s.log' % (domain_name))

# Configure the Administration Server
# ===================================
cd('/Servers/AdminServer')
#set('ListenAddress', '%s-%s' % (domain_uid, admin_server_name_svc))
set('ListenPort', admin_port)
set('Name', admin_server_name)

create('T3Channel', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/T3Channel' % admin_server_name)
set('PublicPort', t3_channel_port)
set('PublicAddress', t3_public_address)
#set('ListenAddress', '%s-%s' % (domain_uid, admin_server_name_svc))
set('ListenPort', t3_channel_port)

#cd('/Servers/%s' % admin_server_name)
#create(admin_server_name, 'Log')
#cd('/Servers/%s/Log/%s' % (admin_server_name, admin_server_name))
#set('FileName', '%s/%s.log' % (domain_logs, admin_server_name))

# Set the admin user's username and password
# ==========================================
cd('/Security/%s/User/weblogic' % domain_name)
cmo.setName(username)
cmo.setPassword(password)

# Write the domain and close the domain template
# ==============================================
setOption('OverwriteDomain', 'true')

# Create a cluster
# ======================
cd('/')
cl=create(cluster_name, 'Cluster')
c2=create(cluster_name_2, 'Cluster')

# Create Independent Managed Server
name = '%s' %(managed_server_name_base)
#name_svc = '%s' %(managed_server_name_base_svc)
cd('/')
create(name, 'Server')
cd('/Servers/%s/' % name )
print('managed server name is %s' % name);
#set('ListenAddress', '%s-%s' % (domain_uid, name_svc))
set('ListenPort', server_port)
set('NumOfRetriesBeforeMSIMode', 0)
set('RetryIntervalBeforeMSIMode', 1)

if cluster_type == "CONFIGURED":

  # Create managed servers
  for index in range(0, number_of_ms):
    cd('/')

    msIndex = index+1
    name = '%s%s' % (managed_server_name_base, msIndex)
#    name_svc = '%s%s' % (managed_server_name_base_svc, msIndex)

    create(name, 'Server')
    cd('/Servers/%s/' % name )
    print('managed server name is %s' % name);
#    set('ListenAddress', '%s-%s' % (domain_uid, name_svc))
    set('ListenPort', server_port)
    set('NumOfRetriesBeforeMSIMode', 0)
    set('RetryIntervalBeforeMSIMode', 1)
    set('Cluster', cluster_name)

  for index in range(0, number_of_ms):
    cd('/')

    msIndex = index+1
    name = '%s%s' % (managed_server_name_base_2, msIndex)
#    name_svc = '%s%s' % (managed_server_name_base_svc_2, msIndex)

    create(name, 'Server')
    cd('/Servers/%s/' % name )
    print('managed server name is %s' % name);
#    set('ListenAddress', '%s-%s' % (domain_uid, name_svc))
    set('ListenPort', server_port)
    set('NumOfRetriesBeforeMSIMode', 0)
    set('RetryIntervalBeforeMSIMode', 1)
    set('Cluster', cluster_name_2)
    #create(name,'Log')
    #cd('/Servers/%s/Log/%s' % (name, name))
    #set('FileName', '%s/%s.log' % (domain_logs,name))
else:
  print('Configuring Dynamic Cluster %s' % cluster_name)

  templateName = cluster_name + "-template"
  print('Creating Server Template: %s' % templateName)
  st1=create(templateName, 'ServerTemplate')
  print('Done creating Server Template: %s' % templateName)
  cd('/ServerTemplates/%s' % templateName)
  cmo.setListenPort(server_port)
#  cmo.setListenAddress('%s-%s${id}' % (domain_uid, managed_server_name_base_svc))
  cmo.setCluster(cl)
  #create(templateName,'Log')
  #cd('Log/%s' % templateName)
  #set('FileName', '%s/%s${id}.log' % (domain_logs, managed_server_name_base))
  #print('Done setting attributes for Server Template: %s' % templateName);

  cd('/Clusters/%s' % cluster_name)
  create(cluster_name, 'DynamicServers')
  cd('DynamicServers/%s' % cluster_name)
  set('ServerTemplate', st1)
  set('ServerNamePrefix', managed_server_name_base)
  set('DynamicClusterSize', number_of_ms)
  set('MaxDynamicClusterSize', number_of_ms)
  set('CalculatedListenPorts', false)
  set('Id', 1)

  print('Done setting attributes for Dynamic Cluster: %s' % cluster_name);

  print('Configuring Dynamic Cluster %s' % cluster_name_2)
  templateName = cluster_name_2 + "-template"
  print('Creating Server Template: %s' % templateName)
  st1=create(templateName, 'ServerTemplate')
  print('Done creating Server Template: %s' % templateName)
  cd('/ServerTemplates/%s' % templateName)
  cmo.setListenPort(server_port)
#  cmo.setListenAddress('%s-%s${id}' % (domain_uid, managed_server_name_base_svc_2))
  cmo.setCluster(c2)

  #create(templateName,'Log')
  #cd('Log/%s' % templateName)
  #set('FileName', '%s/%s${id}.log' % (domain_logs, managed_server_name_base_2))
  #print('Done setting attributes for Server Template: %s' % templateName);

  cd('/Clusters/%s' % cluster_name_2)
  create(cluster_name_2, 'DynamicServers')
  cd('DynamicServers/%s' % cluster_name_2)
  set('ServerTemplate', st1)
  set('ServerNamePrefix', managed_server_name_base_2)
  set('DynamicClusterSize', number_of_ms)
  set('MaxDynamicClusterSize', number_of_ms)
  set('CalculatedListenPorts', false)
  set('Id', 1)

  print('Done setting attributes for Dynamic Cluster: %s' % cluster_name_2);

cd('/')
create('CoherenceCluster', 'CoherenceClusterSystemResource')

cd('/Clusters/%s' % cluster_name)
set('CoherenceClusterSystemResource','CoherenceCluster')

cd('/CoherenceClusterSystemResource/CoherenceCluster')
set('Target', '%s' % cluster_name)

cd('/Clusters/%s' % cluster_name)
create(cluster_name, 'CoherenceTier')
cd('CoherenceTier/%s' % cluster_name)
cmo.setLocalStorageEnabled(false)

cd('/Clusters/%s' % cluster_name_2)
set('CoherenceClusterSystemResource', 'CoherenceCluster')

cd('/CoherenceClusterSystemResource/CoherenceCluster')
set('Target', '%s' % cluster_name_2)

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

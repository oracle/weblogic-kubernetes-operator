# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# This python script is used to create a WebLogic domain

domain_name                  = os.environ.get("DOMAIN_NAME")
domain_path                  = os.environ.get("DOMAIN_HOME")
admin_username               = os.environ.get("ADMIN_USER")
admin_password               = os.environ.get("ADMIN_PWD")

host_name                    = "localhost"    
cluster_type                 = "DYNAMIC"

cluster_name                 = "cluster-1"
admin_server_name            = "admin-server"
admin_port                   = 7001
t3_channel_port              = 30012
t3_public_address            = host_name   

managed_server_name_base     = "managed-server"
server_port                  = 8001
number_of_ms                 = 3
production_mode_enabled      = "true"

print('domain_path        : [%s]' % domain_path);
print('domain_name        : [%s]' % domain_name);
print('admin_server_name  : [%s]' % admin_server_name);
print('admin_username     : [%s]' % admin_username);
print('admin_port         : [%s]' % admin_port);
print('t3_channel_port    : [%s]' % t3_channel_port);
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
set('ListenAddress', '%s' % host_name)
set('ListenPort', admin_port)
set('Name', admin_server_name)

create('T3Channel', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/T3Channel' % admin_server_name)
set('PublicPort', t3_channel_port)
set('PublicAddress', t3_public_address)
set('ListenAddress', host_name)
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

    create(name, 'Server')
    cd('/Servers/%s/' % name )
    print('managed server name is %s' % name);
    set('ListenAddress', host_name)
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
  cmo.setListenAddress(host_name)
  cmo.setCluster(cl)
  print('Done setting attributes for Server Template: %s' % templateName);


  cd('/Clusters/%s' % cluster_name)
  create(cluster_name, 'DynamicServers')
  cd('DynamicServers/%s' % cluster_name)
  set('ServerTemplate', st1)
  set('ServerNamePrefix', managed_server_name_base)
  set('DynamicClusterSize', number_of_ms)
  set('MaxDynamicClusterSize', number_of_ms)
  set('CalculatedListenPorts', false)

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


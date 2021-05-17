#Copyright (c) 2021, Oracle and/or its affiliates.
#Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# Offline WLST script for creating a WebLogic Domain
# Domain, as defined in DOMAIN_NAME, will be created in this script. Name defaults to 'base_domain'.
#
# ==============================================

ssl_enabled                   = SSL_ENABLED
server_port                   = int(MANAGED_SERVER_PORT)
managed_server_ssl_port       = int(MANAGED_SERVER_SSL_PORT)
domain_path                   = DOMAIN_HOME
cluster_name                  = CLUSTER_NAME
admin_server_name             = ADMIN_NAME
admin_port                    = int(ADMIN_PORT)
admin_server_ssl_port         = int(ADMIN_SERVER_SSL_PORT)
username                      = ADMIN_USER_NAME
password                      = ADMIN_USER_PASS
domain_name                   = DOMAIN_NAME
t3_channel_port               = int(T3_CHANNEL_PORT)
t3_public_address             = T3_PUBLIC_ADDRESS
number_of_ms                  = int(CONFIGURED_MANAGED_SERVER_COUNT)
cluster_type                  = CLUSTER_TYPE
managed_server_name_base      = MANAGED_SERVER_NAME_BASE
production_mode_enabled       = PRODUCTION_MODE_ENABLED

print('domain_path              : [%s]' % domain_path);
print('domain_name              : [%s]' % domain_name);
print('ssl_enabled              : [%s]' % ssl_enabled);
print('admin_server_name        : [%s]' % admin_server_name);
print('admin_port               : [%s]' % admin_port);
print('admin_server_ssl_port    : [%s]' % admin_server_ssl_port);
print('cluster_name             : [%s]' % cluster_name);
print('server_port              : [%s]' % server_port);
print('managed_server_ssl_port  : [%s]' % managed_server_ssl_port);
print('number_of_ms             : [%s]' % number_of_ms);
print('cluster_type             : [%s]' % cluster_type);
print('managed_server_name_base : [%s]' % managed_server_name_base);
print('production_mode_enabled  : [%s]' % production_mode_enabled);
print('t3_channel_port          : [%s]' % t3_channel_port);
print('t3_public_address        : [%s]' % t3_public_address);

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
set('ListenPort', admin_port)
set('Name', admin_server_name)


create('T3Channel', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/T3Channel' % admin_server_name)
set('PublicPort', t3_channel_port)
set('PublicAddress', t3_public_address)
set('ListenPort', t3_channel_port)

if (ssl_enabled == 'true'):
    print 'Enabling SSL in the Admin server...'
    cd('/Servers/' + admin_server_name)
    create(admin_server_name, 'SSL')
    cd('/Servers/' + admin_server_name + '/SSL/' + admin_server_name)
    set('ListenPort', admin_server_ssl_port)
    set('Enabled', 'True')

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

if cluster_type == "CONFIGURED":

  # Create managed servers
  for index in range(0, number_of_ms):
    cd('/')
    msIndex = index+1

    cd('/')
    name = '%s%s' % (managed_server_name_base, msIndex)

    create(name, 'Server')
    cd('/Servers/%s/' % name )
    print('managed server name is %s' % name);
    set('ListenPort', server_port)
    set('NumOfRetriesBeforeMSIMode', 0)
    set('RetryIntervalBeforeMSIMode', 1)
    set('Cluster', cluster_name)

    if (ssl_enabled == 'true'):
      print 'Enabling SSL in the managed server...'
      create(name, 'SSL')
      cd('/Servers/' + name+ '/SSL/' + name)
      set('ListenPort', managed_server_ssl_port)
      set('Enabled', 'True')

else:
  print('Configuring Dynamic Cluster %s' % cluster_name)

  templateName = cluster_name + "-template"
  print('Creating Server Template: %s' % templateName)
  st1=create(templateName, 'ServerTemplate')
  print('Done creating Server Template: %s' % templateName)
  cd('/ServerTemplates/%s' % templateName)
  cmo.setListenPort(server_port)
  if (ssl_enabled == 'true'):
    cmo.getSSL().setEnabled(true)
    cmo.getSSL().setListenPort(managed_server_ssl_port)
  cmo.setCluster(cl)

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

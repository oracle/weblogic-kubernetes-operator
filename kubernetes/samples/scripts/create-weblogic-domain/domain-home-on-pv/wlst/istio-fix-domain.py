# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

def getEnvVar(var):
  val=os.environ.get(var)
  if val==None:
    print "ERROR: Env var ",var, " not set."
    sys.exit(1)
  return val

def toDNS1123Legal(address):
  return address.lower().replace('_','-')

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
# Update Domain for Istio
readDomain(domain_path)


cd('/Servers/' + admin_server_name)
delete('T3Channel', 'NetworkAccessPoint')
create('T3Channel', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/T3Channel' % admin_server_name)
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + admin_server_name))
set('ListenAddress', '127.0.0.1')
set('ListenPort', t3_channel_port)
set('Protocol', 't3')

print("Done updating Admin Server's configuration");

updateDomain()
closeDomain()
print 'Domain Updated for Istio'
print 'Done'


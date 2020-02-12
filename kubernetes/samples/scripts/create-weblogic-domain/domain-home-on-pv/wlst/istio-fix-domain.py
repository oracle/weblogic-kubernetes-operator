# Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
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
istio_readiness_port         = int(getEnvVar("ISTIO_READINESS_PORT"))

print('istio_readiness_port : [%s]' % istio_readiness_port);

# Update Domain for Istio
readDomain(domain_path)

cd('/Servers/' + admin_server_name)
create('istio-probe', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/istio-probe' % admin_server_name)
set('Protocol', 'http')
set('ListenAddress', '127.0.0.1')
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + admin_server_name))
set('ListenPort', istio_readiness_port)
set('HttpEnabledForThisProtocol', true)
set('TunnelingEnabled', false)
set('OutboundEnabled', false)
set('Enabled', true)
set('TwoWaySslEnabled', false)
set('ClientCertificateEnforced', false)

cd('/Servers/' + admin_server_name)
create('istio-t3', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/istio-t3' % admin_server_name)
set('ListenAddress', '127.0.0.1')
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + admin_server_name))
set('ListenPort', admin_port)
set('TunnelingEnabled', false)
set('OutboundEnabled', false)
set('Enabled', true)
set('TwoWaySslEnabled', false)
set('ClientCertificateEnforced', false)

cd('/Servers/' + admin_server_name)
create('istio-ldap', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/istio-ldap' % admin_server_name)
set('Protocol', "ldap")
set('ListenAddress', '127.0.0.1')
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + admin_server_name))
set('ListenPort', admin_port)
set('HttpEnabledForThisProtocol', true)
set('TunnelingEnabled', false)
set('OutboundEnabled', false)
set('Enabled', true)
set('TwoWaySslEnabled', false)
set('ClientCertificateEnforced', false)

cd('/Servers/' + admin_server_name)
delete('T3Channel', 'NetworkAccessPoint')
create('istio-T3Channel', 'NetworkAccessPoint')
cd('/Servers/%s/NetworkAccessPoints/istio-T3Channel' % admin_server_name)
set('PublicPort', t3_channel_port)
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + admin_server_name))
set('ListenAddress', '127.0.0.1')
set('ListenPort', t3_channel_port)

print("Done updating Admin Server's configuration");

templateName = cluster_name + "-template"
cd('/ServerTemplates/%s' % templateName)
create('istio-probe', 'NetworkAccessPoint')
cd('/ServerTemplates/%s/NetworkAccessPoints/istio-probe' % templateName)
set('Protocol', 'http')
set('ListenAddress', '127.0.0.1')
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + managed_server_name_base + '${id}'))
set('ListenPort', istio_readiness_port)
set('HttpEnabledForThisProtocol', true)
set('TunnelingEnabled', false)
set('OutboundEnabled', false)
set('Enabled', true)
set('TwoWaySslEnabled', false)
set('ClientCertificateEnforced', false)

cd('/ServerTemplates/%s' % templateName)
create('istio-t3', 'NetworkAccessPoint')
cd('/ServerTemplates/%s/NetworkAccessPoints/istio-t3' % templateName)
set('ListenAddress', '127.0.0.1')
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + managed_server_name_base + '${id}'))
set('ListenPort', server_port)
set('TunnelingEnabled', false)
set('OutboundEnabled', false)
set('Enabled', true)
set('TwoWaySslEnabled', false)
set('ClientCertificateEnforced', false)

cd('/ServerTemplates/%s' % templateName)
create('istio-cluster', 'NetworkAccessPoint')
cd('/ServerTemplates/%s/NetworkAccessPoints/istio-cluster' % templateName)
set('Protocol', "CLUSTER-BROADCAST")
set('ListenAddress', '127.0.0.1')
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + managed_server_name_base + '${id}'))
set('ListenPort', server_port)
set('TunnelingEnabled', false)
set('OutboundEnabled', false)
set('Enabled', true)
set('TwoWaySslEnabled', false)
set('ClientCertificateEnforced', false)

cd('/ServerTemplates/%s' % templateName)
create('istio-http', 'NetworkAccessPoint')
cd('/ServerTemplates/%s/NetworkAccessPoints/istio-http' % templateName)
set('Protocol', 'http')
set('ListenAddress', '127.0.0.1')
set('PublicAddress', toDNS1123Legal(domain_uid + '-' + managed_server_name_base + '${id}'))
set('ListenPort', 31111)
set('TunnelingEnabled', false)
set('OutboundEnabled', false)
set('Enabled', true)
set('TwoWaySslEnabled', false)
set('ClientCertificateEnforced', false)

print("Done updating Managed Server's template: %s", templateName);

updateDomain()
closeDomain()
print 'Domain Updated for Istio'
print 'Done'

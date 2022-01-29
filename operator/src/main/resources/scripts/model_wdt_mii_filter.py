# Copyright (c) 2018, 2022, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#   This is a model-in-image WDT filter for overriding WLS configuration, it
#   replaces 'situational configuration overrides'.
#
#   This code is used by the operator during introspection for MII to manipulate
#   the domain model.  It generates domain configuration information that's
#   useful for running the domain, setting up its networking, and for overriding
#   specific parts of its configuration so that it can run in k8s.
#
#   For more details, see the Model Filters description in the WebLogic Deploy
#   Tooling in Github.
#
# ---------------------
# Prerequisites/Inputs:
# ---------------------
#
#   A domain model as a Jython dictionary.
#
#   The following env vars are required:
#     DOMAIN_UID         - completely unique id for this domain
#     DOMAIN_HOME        - path for the domain configuration
#     LOG_HOME           - path to override WebLogic server log locations
#     CREDENTIALS_SECRET_NAME  - name of secret containing credentials
#
#   The following env vars are optional:
#     ACCESS_LOG_IN_LOG_HOME - HTTP access log files will be written to
#                              the logHome directory.
#     DATA_HOME - in-pod location for data storage of default and custom file
#                 stores.
#     CREDENTIALS_SECRET_PATH - directory path to secret containing credentials
#
# ---------------------------------
# Result
# ---------------------------------
#
#   The configuration overrides are directly modified in the domain model and
#   include listen addresses, log file locations, etc.  The WebLogic Deploy
#   Tooling will then generate/update the domain with the appropriate
#   configuration.
#

import copy
import inspect
import os
import sys

tmp_callerframerecord = inspect.stack()[0]    # 0 represents this line # 1 represents line at caller
tmp_info = inspect.getframeinfo(tmp_callerframerecord[0])
tmp_scriptdir=os.path.dirname(tmp_info[0])
sys.path.append(tmp_scriptdir)

env = None
ISTIO_NAP_NAMES = ['tcp-cbt', 'tcp-ldap', 'tcp-iiop', 'tcp-snmp', 'http-default', 'tcp-default', 'https-secure', 'tls-ldaps', 'tls-default', 'tls-cbts', 'tls-iiops', 'https-admin']


class OfflineWlstEnv(object):

  def open(self, model):

    self.model = model

    # before doing anything, get each env var and verify it exists

    self.DOMAIN_UID               = self.getEnv('DOMAIN_UID')
    self.DOMAIN_HOME              = self.getEnv('DOMAIN_HOME')
    self.LOG_HOME                 = self.getEnv('LOG_HOME')
    self.ACCESS_LOG_IN_LOG_HOME   = self.getEnvOrDef('ACCESS_LOG_IN_LOG_HOME', 'true')
    self.DATA_HOME                = self.getEnvOrDef('DATA_HOME', "")
    self.CREDENTIALS_SECRET_NAME  = self.getEnv('CREDENTIALS_SECRET_NAME')

    # initialize globals
    self.CREDENTIALS_SECRET_PATH = self.getEnvOrDef('CREDENTIALS_SECRET_PATH', '/weblogic-operator/secrets')
    self.TOPOLOGY_YAML_PATH = '/weblogic-operator/introspectormii/topology.yaml'
    self.DOMAIN_NAME = None

    if model and 'topology' in model:
      self.DOMAIN_NAME = model['topology']['Name']

    if self.DOMAIN_NAME is None and os.path.exists(self.TOPOLOGY_YAML_PATH):
      self.readDomainNameFromTopologyYaml(self.TOPOLOGY_YAML_PATH)

  def readDomainNameFromTopologyYaml(self, path):
    file = open(path, 'r')
    content = file.readlines()
    file.close()
    # access line containing domain name and strip leading and trailing spaces
    line = content[2].strip()
    # create key-value pair (e.g. name="sample-domain1")
    (key, val) = line.split()
    if key == 'name:':
      # strip leading and trailing double quotes from value
      self.DOMAIN_NAME = val.strip('"')

  def getDomainName(self):
    return self.DOMAIN_NAME

  def getDomainUID(self):
    return self.DOMAIN_UID

  def getDomainHome(self):
    return self.DOMAIN_HOME

  def getDomainLogHome(self):
    return self.LOG_HOME

  def getDataHome(self):
    return self.DATA_HOME

  def isAccessLogInLogHome(self):
    return self.ACCESS_LOG_IN_LOG_HOME == 'true'

  def readFile(self, path):
    file = open(path, 'r')
    contents = file.read()
    file.close()
    return contents

  def getEnv(self, name):
    val = os.getenv(name)
    if val is None or val == "null":
      print("SEVERE: Env var %s not set." % name)
      sys.exit(1)
    return val

  def getEnvOrDef(self, name, deflt):
    val = os.getenv(name)
    if val == None or val == "null" or len(val) == 0:
      return deflt
    return val

  def toDNS1123Legal(self, address):
    return address.lower().replace('_','-')

  def getModel(self):
    return self.model

class SecretManager(object):

  def __init__(self, env):
    self.env = env

  def readCredentialsSecret(self, key):
    path = self.env.CREDENTIALS_SECRET_PATH + '/' + key
    return self.env.readFile(path)


def filter_model(model):
  if model is not None:
    if getOfflineWlstEnv() is None:
        initOfflineWlstEnv(model)

    initSecretManager(env)

    if model and 'resources' in model:
      customizeCustomFileStores(model)

    if model and 'topology' in model:
      topology = model['topology']
      customizeNodeManagerCreds(topology)
      customizeDomainLogPath(topology)

      if 'Cluster' in topology:
        # If Istio enabled, inject replication channel for each cluster
        # before creating the corresponding NAP for each server and
        # server-template
        customizeIstioClusters(model)

      if 'Server' in topology:
        customizeServers(model)

      if 'ServerTemplate' in topology:
        customizeServerTemplates(model)

def initOfflineWlstEnv(model):
  global env
  env = OfflineWlstEnv()
  env.open(model)


def getOfflineWlstEnv():
  if env is not None:
    return env
  return None


def setOfflineWlstEnv(offlineWlstEnv):
  env = offlineWlstEnv

def initSecretManager(env):
  global secret_manager
  secret_manager = SecretManager(env)

def customizeServerTemplates(model):
  topology = model['topology']
  if 'ServerTemplate' not in topology:
    return

  serverTemplates = topology['ServerTemplate']
  template_names = serverTemplates.keys()
  if template_names is not None:
    for template_name in template_names:
      template = serverTemplates[template_name]
      cluster_name = getClusterNameOrNone(template)
      if cluster_name is not None:
        customizeServerTemplate(topology, template, template_name)



def customizeServerTemplate(topology, template, template_name):
  server_name_prefix = getServerNamePrefix(topology, template)
  domain_uid = env.getDomainUID()
  customizeLog(server_name_prefix + "${id}", template)
  customizeAccessLog(server_name_prefix + "${id}", template)
  customizeDefaultFileStore(template)
  listen_address=env.toDNS1123Legal(domain_uid + "-" + server_name_prefix + "${id}")
  setServerListenAddress(template, listen_address)
  customizeNetworkAccessPoints(template, listen_address)
  customizeManagedIstioNetworkAccessPoint(template, listen_address)
  customizeIstioReplicationChannel(template, template_name, listen_address)
  if getCoherenceClusterSystemResourceOrNone(topology, template) is not None:
    customizeCoherenceMemberConfig(template, listen_address)

def customizeIstioClusters(model):
  istio_enabled = env.getEnvOrDef("ISTIO_ENABLED", "false")
  if istio_enabled == 'false':
    return
  if 'topology' in model and 'Cluster' in model['topology']:
    for cluster in model['topology']['Cluster']:
      if 'ReplicationChannel' not in model['topology']['Cluster'][cluster]:
        model['topology']['Cluster'][cluster]['ReplicationChannel'] = {}

      repl_channel = model['topology']['Cluster'][cluster]['ReplicationChannel']
      if repl_channel is None or len(repl_channel) == 0:
        model['topology']['Cluster'][cluster]['ReplicationChannel'] = 'istiorepl'

def getServerNamePrefix(topology, template):
  server_name_prefix = None
  cluster_name = getClusterNameOrNone(template)
  if cluster_name is not None:
    cluster = getClusterOrNone(topology, cluster_name)
    if cluster is not None:
      dynamicServer = getDynamicServerOrNone(cluster)
      if dynamicServer is not None:
        server_name_prefix = getDynamicServerPropertyOrNone(dynamicServer, 'ServerNamePrefix')

  return server_name_prefix


def customizeNodeManagerCreds(topology):
  username = getSecretManager().readCredentialsSecret('username')
  pwd = getSecretManager().readCredentialsSecret('password')

  if not ('SecurityConfiguration' in topology):
    topology['SecurityConfiguration'] = {}

  topology['SecurityConfiguration']['NodeManagerUsername'] = username
  topology['SecurityConfiguration']['NodeManagerPasswordEncrypted'] = pwd


def customizeDomainLogPath(topology):
  customizeLog(env.getDomainName(), topology)


def customizeLog(name, topologyOrServer):
  if name is None:
    # domain name is req'd to create domain log configuration.
    # Missing domain name indicates our model is a fragment
    return

  logs_dir = env.getDomainLogHome()
  if logs_dir is None or len(logs_dir) == 0:
    return

  if 'Log' not in topologyOrServer:
    topologyOrServer['Log'] = {}

  topologyOrServer['Log']['FileName'] = logs_dir + "/" + name + ".log"


def customizeCustomFileStores(model):
  customizeFileStores(model['resources'])


def customizeFileStores(resources):
  data_dir = env.getDataHome()
  if data_dir is None or len(data_dir) == 0:
    # do not override if dataHome not specified or empty ("")
    return

  if 'FileStore' not in resources:
    return

  filestores = resources['FileStore']
  names = filestores.keys()
  for name in names:
    filestore = filestores[name]
    customizeFileStore(filestore, data_dir)


def customizeFileStore(filestore, data_dir):
  filestore['Directory'] = data_dir


def customizeServers(model):
  if 'Server' not in model['topology']:
    return

  servers = model['topology']['Server']
  names = servers.keys()
  for name in names:
    server = servers[name]
    customizeServer(model, server, name)


def customizeServer(model, server, name):
  listen_address=env.toDNS1123Legal(env.getDomainUID() + "-" + name)
  adminServer = model['topology']['AdminServerName']
  customizeLog(name, server)
  customizeAccessLog(name, server)
  customizeDefaultFileStore(server)
  setServerListenAddress(server, listen_address)
  customizeNetworkAccessPoints(server,listen_address)
  customizeServerIstioNetworkAccessPoint(server, listen_address)
  if (name == adminServer):
    addAdminChannelPortForwardNetworkAccessPoints(server)
  else:
    customizeIstioReplicationChannel(server, name, listen_address)
  if getCoherenceClusterSystemResourceOrNone(model['topology'], server) is not None:
    customizeCoherenceMemberConfig(server, listen_address)


def customizeCoherenceMemberConfig(server, listen_address):
  if 'CoherenceMemberConfig ' not in server:
    server['CoherenceMemberConfig'] = {}

  cmc = server['CoherenceMemberConfig']
  cmc['UnicastListenAddress'] = listen_address


def getAdministrationPort(server, topology):
  port = 0
  if 'AdministrationPort' in server:
    port = int(server['AdministrationPort'])
  if port == 0 and 'AdministrationPort' in topology:
    port = int(topology['AdministrationPort'])
  if port == 0:
    port =9002
  return port


def isAdministrationPortEnabledForServer(server, topology):
  administrationPortEnabled = False
  if 'AdministrationPortEnabled' in server:
    administrationPortEnabled = server['AdministrationPortEnabled'] == 'true'
  else:
    administrationPortEnabled = isAdministrationPortEnabledForDomain(topology)
  return administrationPortEnabled


def isAdministrationPortEnabledForDomain(topology):
  administrationPortEnabled = False

  if 'AdministrationPortEnabled' in topology:
    administrationPortEnabled = topology['AdministrationPortEnabled'] == 'true'
  else:
    # AdministrationPortEnabled is not explicitly set so going with the default
    # Starting with 14.1.2.0, the domain's AdministrationPortEnabled default is derived from the domain's SecureMode
    administrationPortEnabled = isSecureModeEnabledForDomain(topology)
  return administrationPortEnabled


# Derive the default value for SecureMode of a domain
def isSecureModeEnabledForDomain(topology):
  secureModeEnabled = False
  if 'SecurityConfiguration' in topology and 'SecureMode' in topology['SecurityConfiguration'] and 'SecureModeEnabled' in topology['SecurityConfiguration']['SecureMode']:
    secureModeEnabled = topology['SecurityConfiguration']['SecureMode']['SecureModeEnabled'] == 'true'
  else:
    is_production_mode_enabled = False
    if 'ProductionModeEnabled' in topology:
      is_production_mode_enabled = topology['ProductionModeEnabled'] == 'true'
    secureModeEnabled = is_production_mode_enabled and not env.wlsVersionEarlierThan("14.1.2.0")
  return secureModeEnabled


def getSSLOrNone(server):
  if 'SSL' not in server:
    return None
  return server['SSL']


def _writeIstioNAP(name, server, listen_address, listen_port, protocol, http_enabled="true",
                   bind_to_localhost="true", use_fast_serialization='false', tunneling_enabled='false',
                   outbound_enabled='false'):

  if 'NetworkAccessPoint' not in server:
    server['NetworkAccessPoint'] = {}

  naps = server['NetworkAccessPoint']
  if name not in naps:
    naps[name] = {}

  nap = naps[name]
  nap['Protocol'] = protocol
  if bind_to_localhost == 'true':
    nap['ListenAddress'] = '127.0.0.1'
  else:
    nap['ListenAddress'] = '%s' % (listen_address)
  nap['PublicAddress'] = '%s.%s' % (listen_address, env.getEnvOrDef("ISTIO_POD_NAMESPACE", "default"))
  nap['ListenPort'] = listen_port
  nap['HttpEnabledForThisProtocol'] = http_enabled
  nap['TunnelingEnabled'] = tunneling_enabled
  nap['OutboundEnabled'] = outbound_enabled
  nap['Enabled'] = 'true'
  nap['UseFastSerialization'] = use_fast_serialization

def _get_ssl_listen_port(server):
  ssl = getSSLOrNone(server)
  ssl_listen_port = None
  model = env.getModel()
  if ssl is not None and 'Enabled' in ssl and ssl['Enabled'] == 'true':
    ssl_listen_port = ssl['ListenPort']
    if ssl_listen_port is None:
      ssl_listen_port = "7002"
  elif ssl is None and isSecureModeEnabledForDomain(model['topology']):
    ssl_listen_port = "7002"
  return ssl_listen_port

def customizeServerIstioNetworkAccessPoint(server, listen_address):
  istio_enabled = env.getEnvOrDef("ISTIO_ENABLED", "false")
  if istio_enabled == 'false':
    return
  istio_readiness_port = env.getEnvOrDef("ISTIO_READINESS_PORT", None)
  if istio_readiness_port is None:
    return
  admin_server_port = server['ListenPort']
  # Set the default if it is not provided to avoid nap default to 0 which fails validation.

  if admin_server_port is None:
    admin_server_port = 7001

  # readiness probe
  _writeIstioNAP(name='http-probe', server=server, listen_address=listen_address,
                   listen_port=istio_readiness_port, protocol='http', http_enabled="true")

  # Generate NAP for each protocols
  if istioVersionRequiresLocalHostBindings():
    _writeIstioNAP(name='tcp-ldap', server=server, listen_address=listen_address,
                      listen_port=admin_server_port, protocol='ldap')

    _writeIstioNAP(name='tcp-default', server=server, listen_address=listen_address,
                      listen_port=admin_server_port, protocol='t3')

    _writeIstioNAP(name='http-default', server=server, listen_address=listen_address,
                      listen_port=admin_server_port, protocol='http')

    _writeIstioNAP(name='tcp-snmp', server=server, listen_address=listen_address,
                      listen_port=admin_server_port, protocol='snmp')

    _writeIstioNAP(name='tcp-cbt', server=server, listen_address=listen_address,
                      listen_port=admin_server_port, protocol='CLUSTER-BROADCAST')

    _writeIstioNAP(name='tcp-iiop', server=server, listen_address=listen_address,
                      listen_port=admin_server_port, protocol='iiop')

    ssl_listen_port = _get_ssl_listen_port(server)
    model = env.getModel()

    if ssl_listen_port is not None:
      _writeIstioNAP(name='https-secure', server=server, listen_address=listen_address,
                        listen_port=ssl_listen_port, protocol='https', http_enabled="true")

      _writeIstioNAP(name='tls-ldaps', server=server, listen_address=listen_address,
                        listen_port=ssl_listen_port, protocol='ldaps')

      _writeIstioNAP(name='tls-default', server=server, listen_address=listen_address,
                        listen_port=ssl_listen_port, protocol='t3s')

      _writeIstioNAP(name='tls-cbts', server=server, listen_address=listen_address,
                        listen_port=ssl_listen_port, protocol='CLUSTER-BROADCAST-SECURE')

      _writeIstioNAP(name='tls-iiops', server=server, listen_address=listen_address,
                        listen_port=ssl_listen_port, protocol='iiops')

    if isAdministrationPortEnabledForServer(server, model['topology']):
      _writeIstioNAP(name='https-admin', server=server, listen_address=listen_address,
                        listen_port=getAdministrationPort(server, model['topology']), protocol='https', http_enabled="true")
  else:
    # readiness probe NAP binding to server IP pod address Istio versions >= 1.10.x
    _writeIstioNAP(name='http-probe-ext', server=server, listen_address=listen_address,
                   listen_port=istio_readiness_port, protocol='http', http_enabled="true",
                   bind_to_localhost="false")

def customizeIstioReplicationChannel(server, name, listen_address):
  istio_enabled = env.getEnvOrDef("ISTIO_ENABLED", "false")
  if istio_enabled == 'false' or server['Cluster'] is None:
    return

  # verify if server or server template is associated with a cluster
  cluster_name = getClusterNameOrNone(server)
  if cluster_name is None or len(cluster_name) == 0:
    return

  # verify cluster is defined in the topology of the model
  model = env.getModel()
  if model is None or 'topology' not in model:
    return

  topology = model['topology']
  cluster = getClusterOrNone(topology, cluster_name)
  if cluster is None:
    return

  # verify if a replication channel is defined for the cluster
  if 'ReplicationChannel' not in cluster:
    return

  repl_channel = cluster['ReplicationChannel']
  if repl_channel is not None and repl_channel != 'istiorepl':
    return

  istio_repl_listen_port = env.getEnvOrDef("ISTIO_REPLICATION_PORT", 4564)

  verify_replication_port_conflict(server, name, istio_repl_listen_port)

  if istioVersionRequiresLocalHostBindings():
    _writeIstioNAP(name='istiorepl', server=server, listen_address=listen_address,
                 listen_port=istio_repl_listen_port, protocol='t3', http_enabled="true",
                 bind_to_localhost="true", use_fast_serialization='true',
                 tunneling_enabled='false')
  else:
    _writeIstioNAP(name='istiorepl', server=server, listen_address=listen_address,
                   listen_port=istio_repl_listen_port, protocol='t3', http_enabled="true",
                   bind_to_localhost="false", use_fast_serialization='true',
                   tunneling_enabled='false')

def raise_replication_port_conflict(name, listen_port, replication_port, SSL):
  raise ValueError('Server/ServerTemplate %s %s listen port %s conflicts with default replication channel port %s when '
                   'istio is enabled, please specify a different replication port for istio in '
                   'domain.spec.configuration.istio.replicationPort' % (name, SSL, listen_port, replication_port))

def verify_replication_port_conflict(server, name, replication_port):
  listen_port = server['ListenPort']
  ssl_listen_port = _get_ssl_listen_port(server)

  if listen_port == replication_port:
    raise_replication_port_conflict(name, listen_port, replication_port, '')

  if ssl_listen_port == replication_port:
    raise_replication_port_conflict(name, ssl_listen_port, replication_port, 'SSL')

  if 'NetworkAccessPoint' in server:
    for nap_name in server['NetworkAccessPoint']:
      nap = server['NetworkAccessPoint'][nap_name]
      listen_port = nap['ListenPort']
      ssl_listen_port = _get_ssl_listen_port(nap)

      if listen_port == replication_port:
        raise_replication_port_conflict(nap_name, listen_port, replication_port, '')

      if ssl_listen_port == replication_port:
        raise_replication_port_conflict(nap_name, ssl_listen_port, replication_port, 'SSL')

def customizeManagedIstioNetworkAccessPoint(template, listen_address):
  istio_enabled = env.getEnvOrDef("ISTIO_ENABLED", "false")
  if istio_enabled == 'false':
    return
  istio_readiness_port = env.getEnvOrDef("ISTIO_READINESS_PORT", None)
  if istio_readiness_port is None:
    return
  listen_port = template['ListenPort']
  # Set the default if it is not provided to avoid nap default to 0 which fails validation.
  if listen_port is None:
    listen_port = 7001

  # readiness probe
  _writeIstioNAP(name='http-probe', server=template, listen_address=listen_address,
                   listen_port=istio_readiness_port, protocol='http', http_enabled="true")

  # Generate NAP for each protocols
  if istioVersionRequiresLocalHostBindings():
    _writeIstioNAP(name='tcp-ldap', server=template, listen_address=listen_address,
                 listen_port=listen_port, protocol='ldap')

    _writeIstioNAP(name='tcp-default', server=template, listen_address=listen_address,
                 listen_port=listen_port, protocol='t3')

    _writeIstioNAP(name='http-default', server=template, listen_address=listen_address,
                 listen_port=listen_port, protocol='http')

    _writeIstioNAP(name='tcp-snmp', server=template, listen_address=listen_address,
                 listen_port=listen_port, protocol='snmp')

    _writeIstioNAP(name='tcp-cbt', server=template, listen_address=listen_address,
                 listen_port=listen_port, protocol='CLUSTER-BROADCAST')

    _writeIstioNAP(name='tcp-iiop', server=template, listen_address=listen_address,
                 listen_port=listen_port, protocol='iiop')

    ssl = getSSLOrNone(template)
    ssl_listen_port = None
    model = env.getModel()
    if ssl is not None and 'Enabled' in ssl and ssl['Enabled'] == 'true':
      ssl_listen_port = ssl['ListenPort']
      if ssl_listen_port is None:
        ssl_listen_port = "7002"
    elif ssl is None and isSecureModeEnabledForDomain(model['topology']):
      ssl_listen_port = "7002"

    if ssl_listen_port is not None:
      _writeIstioNAP(name='https-secure', server=template, listen_address=listen_address,
                   listen_port=ssl_listen_port, protocol='https', http_enabled="true")

      _writeIstioNAP(name='tls-ldaps', server=template, listen_address=listen_address,
                   listen_port=ssl_listen_port, protocol='ldaps')

      _writeIstioNAP(name='tls-default', server=template, listen_address=listen_address,
                   listen_port=ssl_listen_port, protocol='t3s')

      _writeIstioNAP(name='tls-cbts', server=template, listen_address=listen_address,
                   listen_port=ssl_listen_port, protocol='CLUSTER-BROADCAST-SECURE')

      _writeIstioNAP(name='tls-iiops', server=template, listen_address=listen_address,
                   listen_port=ssl_listen_port, protocol='iiops')
  else:
    # readiness probe NAP binding to pod IP address for Istio versions >= 1.10.x
    _writeIstioNAP(name='http-probe-ext', server=template, listen_address=listen_address,
                   listen_port=istio_readiness_port, protocol='http', http_enabled="true",
                   bind_to_localhost="false")

def addAdminChannelPortForwardNetworkAccessPoints(server):
  istio_enabled = env.getEnvOrDef("ISTIO_ENABLED", "false")
  admin_channel_port_forwarding_enabled = env.getEnvOrDef("ADMIN_CHANNEL_PORT_FORWARDING_ENABLED", "true")
  if (admin_channel_port_forwarding_enabled == 'false') or \
      (istio_enabled == 'true' and istioVersionRequiresLocalHostBindings()):
    return

  admin_server_port = server['ListenPort']
  # Set the default if it is not provided to avoid nap default to 0 which fails validation.

  if admin_server_port is None:
    admin_server_port = 7001

  model = env.getModel()

  if 'NetworkAccessPoint' not in server:
    server['NetworkAccessPoint'] = {}

  naps = server['NetworkAccessPoint']
  nap_names = list(naps)
  index = 0
  for nap_name in nap_names:
    nap = naps[nap_name]
    if nap['Protocol'] == 'admin':
      index += 1
      customAdminChannelPort = nap['ListenPort']
      _writeAdminChannelPortForwardNAP(name='internal-admin' + str(index), server=server,
                                       listen_port=customAdminChannelPort, protocol='admin')

  if isAdministrationPortEnabledForServer(server, model['topology']):
    _writeAdminChannelPortForwardNAP(name='internal-admin', server=server,
                                     listen_port=getAdministrationPort(server, model['topology']), protocol='admin')
  elif index == 0:
    _writeAdminChannelPortForwardNAP(name='internal-t3', server=server, listen_port=admin_server_port, protocol='t3')

    ssl = getSSLOrNone(server)
    ssl_listen_port = None
    if ssl is not None and 'Enabled' in ssl and ssl['Enabled'] == 'true':
      ssl_listen_port = ssl['ListenPort']
      if ssl_listen_port is None:
        ssl_listen_port = "7002"
    elif ssl is None and isSecureModeEnabledForDomain(model['topology']):
      ssl_listen_port = "7002"

    if ssl_listen_port is not None:
      _writeAdminChannelPortForwardNAP(name='internal-t3s', server=server, listen_port=ssl_listen_port, protocol='t3s')

def _writeAdminChannelPortForwardNAP(name, server, listen_port, protocol):

  if 'NetworkAccessPoint' not in server:
    server['NetworkAccessPoint'] = {}

  naps = server['NetworkAccessPoint']
  if name not in naps:
    naps[name] = {}

  nap = naps[name]
  nap['Protocol'] = protocol
  nap['ListenAddress'] = 'localhost'
  nap['ListenPort'] = listen_port
  nap['HttpEnabledForThisProtocol'] = 'true'
  nap['TunnelingEnabled'] = 'false'
  nap['Enabled'] = 'true'


def customizeNetworkAccessPoints(server, listen_address):
  if 'NetworkAccessPoint' not in server:
    return

  naps = server['NetworkAccessPoint']
  nap_names = naps.keys()
  for nap_name in nap_names:
    nap = naps[nap_name]
    customizeNetworkAccessPoint(nap_name, nap, listen_address)


def customizeNetworkAccessPoint(nap_name, nap, listen_address):
  if nap_name in ISTIO_NAP_NAMES:
    # skip creating ISTIO channels
    return

  istio_enabled = env.getEnvOrDef("ISTIO_ENABLED", "false")
  if istio_enabled == 'true' and istioVersionRequiresLocalHostBindings():
    listen_address = '127.0.0.1'

  # fix NAP listen address
  if 'ListenAddress' in nap:
    original_listen_address = nap['ListenAddress']
    if len(original_listen_address) > 0:
      nap['ListenAddress'] = listen_address

def setServerListenAddress(serverOrTemplate, listen_address):
  serverOrTemplate['ListenAddress'] = listen_address


def customizeDefaultFileStore(server):
  data_dir = env.getDataHome()
  if data_dir is None or len(data_dir) == 0:
    # do not override if dataHome not specified or empty ("")
    return

  if 'DefaultFileStore' not in server:
    server['DefaultFileStore'] = {}

  server['DefaultFileStore']['Directory'] = data_dir


def customizeAccessLog(name, server):
  # do not customize if LOG_HOME is not set
  logs_dir = env.getDomainLogHome()
  if logs_dir is None or len(logs_dir) == 0:
    return

  # customize only if ACCESS_LOG_IN_LOG_HOME is 'true'
  if env.isAccessLogInLogHome():
    if 'WebServer' not in server:
      server['WebServer'] = {}

    web_server = server['WebServer']
    if 'WebServerLog' not in web_server:
      web_server['WebServerLog'] = {}

    web_server_log = web_server['WebServerLog']
    if 'FileName' not in web_server_log:
      web_server_log['FileName'] = {}

    web_server_log['FileName'] = logs_dir + "/" + name + "_access.log"


def getLogOrNone(config):
  if 'Log' not in config:
    return None

  return config['Log']


def getClusterNameOrNone(serverOrTemplate):
  if 'Cluster' not in serverOrTemplate:
    return None

  return serverOrTemplate['Cluster']


def getClusterOrNone(topology, name):
  if 'Cluster' not in topology:
    return

  clusters = topology['Cluster']

  if name in clusters:
    return clusters[name]

  return None

def getCoherenceClusterSystemResourceOrNone(topology, serverOrTemplate):

  cluster_name = getClusterNameOrNone(serverOrTemplate)
  if cluster_name is not None:
    cluster = getClusterOrNone(topology, cluster_name)
    if cluster is not None:
      if 'CoherenceClusterSystemResource' not in cluster:
        return None
      return cluster['CoherenceClusterSystemResource']
    else:
      if 'CoherenceClusterSystemResource' not in serverOrTemplate:
        return None
  else:
    if 'CoherenceClusterSystemResource' not in serverOrTemplate:
      return None
    return serverOrTemplate['CoherenceClusterSystemResource']


def getDynamicServerOrNone(cluster):
  if 'DynamicServers' not in cluster:
    return None

  return cluster['DynamicServers']


def getDynamicServerPropertyOrNone(dynamicServer, name):
  if name not in dynamicServer:
    return None

  return dynamicServer[name]


def getSecretManager():
  return secret_manager

def istioVersionRequiresLocalHostBindings():
  if env.getEnvOrDef("ISTIO_USE_LOCALHOST_BINDINGS", "true") == 'true':
    return True

  return False



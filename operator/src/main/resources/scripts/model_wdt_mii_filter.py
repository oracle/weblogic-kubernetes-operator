# Copyright (c) 2018, 2024, Oracle and/or its affiliates.
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

import inspect
import os
import sys, traceback
import time
from java.lang import System
from java.lang import Boolean

tmp_callerframerecord = inspect.stack()[0]    # 0 represents this line # 1 represents line at caller
tmp_info = inspect.getframeinfo(tmp_callerframerecord[0])
tmp_scriptdir=os.path.dirname(tmp_info[0])
sys.path.append(tmp_scriptdir)

import utils

env = None
ISTIO_NAP_NAMES = ['tcp-cbt', 'tcp-ldap', 'tcp-iiop', 'tcp-snmp', 'http-default', 'tcp-default', 'https-secure', 'tls-ldaps', 'tls-default', 'tls-cbts', 'tls-iiops', 'https-admin']


class OfflineWlstEnv(object):

  def open(self, model):

    self.model = model

    # before doing anything, get each env var and verify it exists

    self.DOMAIN_UID               = self.getEnv('DOMAIN_UID')
    self.DOMAIN_HOME              = self.getEnv('DOMAIN_HOME')
    self.LOG_HOME                 = self.getEnv('LOG_HOME')
    self.LOG_HOME_LAYOUT          = self.getEnvOrDef('LOG_HOME_LAYOUT', 'ByServers')
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

  def getDomainLogHomeLayout(self):
    return self.LOG_HOME_LAYOUT

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
    val = self.getEnvOrDef(name)
    if val is None:
      print("SEVERE: Env var %s not set." % name)
      sys.exit(1)
    return val

  def getEnvOrDef(self, name, deflt=None):
    """
    Get the environment variable and return its value or the default value if the variable is not found.
    :param name: the environment variable name
    :param deflt:
    :return:
    """
    val = os.environ.get(name)
    if val is None or val == "null":
      val = System.getenv(name)
      if val is None or val == "null":
        return deflt
    return val

  def toDNS1123Legal(self, address):
    return address.lower().replace('_','-')

  def getModel(self):
    return self.model

  def wlsVersionEarlierThan(self, version):
    # unconventional import within function definition for unit testing
    from weblogic.management.configuration import LegalHelper
    import weblogic.version as version_helper
    ver = version_helper.getReleaseBuildVersion()
    if isinstance(ver, unicode):
      actual_version = unicode( ver, 'UTF8', 'strict')
    else:
      actual_version = str(ver)
    # WLS Domain versions supported by operator are 12.2.1.3 + patches, 12.2.1.4
    # and 14.1.1.0 so current version will only be one of these that are listed.
    return LegalHelper.versionEarlierThan(actual_version, version)

class SecretManager(object):

  def __init__(self, env):
    self.env = env

  def readCredentialsSecret(self, key):
    path = self.env.CREDENTIALS_SECRET_PATH + '/' + key
    return self.env.readFile(path)


def filter_model(model):

  try:

    if model is not None:

      upgradeServerIfNeeded(model)

      if getOfflineWlstEnv() is None:
        initOfflineWlstEnv(model)

      initSecretManager(env)

      if model and 'resources' in model:
        customizeCustomFileStores(model)

      if model and 'topology' in model:
        topology = model['topology']
        customizeNodeManagerCreds(topology)
        customizeDomainLogPath(topology)

        if 'AdminServerName' in topology:
          admin_server = topology['AdminServerName']
        else:
          # weblogic default
          admin_server = 'AdminServer'
          topology['AdminServerName'] = admin_server

        # cover the odd case that the model doesn't have any server!

        if 'Server' not in topology:
          topology['Server'] = {}

        if admin_server not in topology['Server']:
          topology['Server'][admin_server] = {}

        customizeServers(model)

        if 'ServerTemplate' in topology:
          customizeServerTemplates(model)

  except:
      exc_type, exc_obj, exc_tb = sys.exc_info()
      ee_string = traceback.format_exception(exc_type, exc_obj, exc_tb)
      utils.trace('SEVERE', 'Error in applying MII filter:\n ' + str(ee_string))
      raise


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
  if getCoherenceClusterSystemResourceOrNone(topology, template) is not None:
    customizeCoherenceMemberConfig(template, listen_address)

def customizeIstioClusters(model):
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

  if cluster_name is not None and server_name_prefix is None:
    raise ValueError('ServerNamePrefix is not set in %s' % (cluster_name))

  return server_name_prefix


def customizeNodeManagerCreds(topology):
  username = getSecretManager().readCredentialsSecret('username')
  pwd = getSecretManager().readCredentialsSecret('password')

  if not ('SecurityConfiguration' in topology):
    topology['SecurityConfiguration'] = {}

  topology['SecurityConfiguration']['NodeManagerUsername'] = username
  topology['SecurityConfiguration']['NodeManagerPasswordEncrypted'] = pwd


def customizeDomainLogPath(topology):
  customizeLog(env.getDomainName(), topology, isDomainLog=True)


def customizeLog(name, topologyOrServer, isDomainLog=False):
  if name is None:
    # domain name is req'd to create domain log configuration.
    # Missing domain name indicates our model is a fragment
    return

  logs_dir = env.getDomainLogHome()
  if logs_dir is None or len(logs_dir) == 0:
    return

  if 'Log' not in topologyOrServer:
    topologyOrServer['Log'] = {}

  if env.getDomainLogHomeLayout() == "Flat":
    topologyOrServer['Log']['FileName'] = logs_dir + "/" + name + ".log"
  else:
    if isDomainLog:
      topologyOrServer['Log']['FileName'] = "%s/%s.log" % (logs_dir, name)
    else:
      topologyOrServer['Log']['FileName'] = "%s/servers/%s/logs/%s.log" % (logs_dir, name, name)


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

  adminServer = None
  if 'AdminServerName' in model['topology'] and len(model['topology']['AdminServerName']) > 0:
    adminServer = model['topology']['AdminServerName']

  customizeLog(name, server)
  customizeAccessLog(name, server)
  customizeDefaultFileStore(server)
  setServerListenAddress(server, listen_address)
  customizeNetworkAccessPoints(server,listen_address)
  # If the admin server name is not provided in the WDT model,
  # use the default name 'AdminServer'.
  if (name == adminServer or  name == 'AdminServer'):
    addAdminChannelPortForwardNetworkAccessPoints(server)
  if getCoherenceClusterSystemResourceOrNone(model['topology'], server) is not None:
    customizeCoherenceMemberConfig(server, listen_address)


def customizeCoherenceMemberConfig(server, listen_address):
  if 'CoherenceMemberConfig ' not in server:
    server['CoherenceMemberConfig'] = {}

  cmc = server['CoherenceMemberConfig']
  cmc['UnicastListenAddress'] = listen_address

def upgradeServerIfNeeded(model):
  # The marker file is created earlier when the introspector checking if
  # the secure mode should be false in the config.xml.  This will compare with the incoming model
  # if secure mode is not set (or set to secure in option),  then inject the secure mode
  # to false.  This is done to maintain compatibility from 12.2.1* to 14.1.2 and the file
  # is only created when the deploy version is newer than or equals to 14.1.2

  if os.path.exists('/tmp/mii_domain_upgrade.txt'):
    fh = open('/tmp/mii_domain_upgrade.txt', 'r')
    result = fh.read()
    fh.close()
    found = False
    # if secure mode is not enabled in existing domain
    if result == 'False':
        # check if model has anything set
        # if domainInfo already set to secure or in dev mode then do not set it, prod mode will not be secure
        # regardless of others
        # if the model disabled `ProductionModeEnabled`  specifically now, do nothing
        prod_mode = False
        if 'domainInfo' in model and 'ServerStartMode' in model['domainInfo']:
          mode = model['domainInfo']['ServerStartMode']
          if mode == 'secure' or mode == 'dev':
              return
          else:
              prod_mode = True

        if 'topology' in model:

            topology = model['topology']
            if not prod_mode and 'ProductionModeEnabled' not in topology:
              return

            if 'ProductionModeEnabled' in topology:
              value = topology['ProductionModeEnabled']
              if isinstance(value, str) or isinstance(value, unicode):
                prod_enabled = Boolean.valueOf(value)
              else:
                prod_enabled = value
              if not prod_enabled:
                return

            if 'SecurityConfiguration' in topology:
                if 'SecureMode' in topology['SecurityConfiguration']:
                   if 'SecureModeEnabled' in topology['SecurityConfiguration']['SecureMode']:
                        found = True

            if not found:
                if not 'SecurityConfiguration' in topology:
                    topology['SecurityConfiguration'] = {}
                if not 'SecureMode' in topology['SecurityConfiguration']:
                    topology['SecurityConfiguration']['SecureMode'] = {}
                topology['SecurityConfiguration']['SecureMode']['SecureModeEnabled'] = False

def getAdministrationPort(server, topology):
  port = 0
  if 'AdministrationPort' in server:
    port = int(server['AdministrationPort'])
  if port == 0 and 'AdministrationPort' in topology:
    port = int(topology['AdministrationPort'])
  if port == 0:
    port =9002
  return port


def isAdministrationPortEnabledForServer(server, model):
  administrationPortEnabled = False
  if 'AdministrationPortEnabled' in server:
    administrationPortEnabled = server['AdministrationPortEnabled']
  else:
    administrationPortEnabled = isAdministrationPortEnabledForDomain(model)

  if isinstance(administrationPortEnabled, str) or isinstance(administrationPortEnabled, unicode):
    return Boolean.valueOf(administrationPortEnabled)
  else:
    return administrationPortEnabled


def isAdministrationPortEnabledForDomain(model):
  administrationPortEnabled = False
  topology = model['topology']
  if 'AdministrationPortEnabled' in topology:
    administrationPortEnabled = topology['AdministrationPortEnabled']
  else:
    # AdministrationPortEnabled is not explicitly set so going with the default
    # Starting with 14.1.2.0, the domain's AdministrationPortEnabled default is derived from the domain's SecureMode
    administrationPortEnabled = isSecureModeEnabledForDomain(model)

  if isinstance(administrationPortEnabled, str) or isinstance(administrationPortEnabled, unicode):
    return Boolean.valueOf(administrationPortEnabled)
  else:
    return administrationPortEnabled



# Derive the default value for SecureMode of a domain
def isSecureModeEnabledForDomain(model):
  secureModeEnabled = False
  topology = model['topology']
  domain_info = None
  if 'domainInfo' in model:
    domain_info = model['domainInfo']

  if 'SecurityConfiguration' in topology and 'SecureMode' in topology['SecurityConfiguration'] and 'SecureModeEnabled' in topology['SecurityConfiguration']['SecureMode']:
    secureModeEnabled = topology['SecurityConfiguration']['SecureMode']['SecureModeEnabled']
  elif domain_info and 'ServerStartMode' in domain_info and domain_info['ServerStartMode'] == 'secure':
    secureModeEnabled = True
  else:
    is_production_mode_enabled = False
    if 'ProductionModeEnabled' in topology:
      is_production_mode_enabled = topology['ProductionModeEnabled']
    secureModeEnabled = is_production_mode_enabled and not env.wlsVersionEarlierThan("14.1.2.0")

  if isinstance(secureModeEnabled, str) or isinstance(secureModeEnabled, unicode):
    return Boolean.valueOf(secureModeEnabled)
  else:
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
  if ssl is not None and 'Enabled' in ssl and ssl['Enabled']:
    ssl_listen_port = ssl['ListenPort']
    if ssl_listen_port is None:
      ssl_listen_port = "7002"
  elif ssl is None and isSecureModeEnabledForDomain(model):
    ssl_listen_port = "7002"

  # Check overrride for 14.1.2.x
  if not env.wlsVersionEarlierThan("14.1.2.0") and not isGlobalSSLEnabled():
          return None
  return ssl_listen_port

def isGlobalSSLEnabled(model):
    result=False
    if 'topology' in model:
        if 'SSLEnabled' in model['topology']:
            val = model['topology']['SSLEnabled']
            if isinstance(val, str) or isinstance(val, unicode):
              result = Boolean.valueOf(val)
            else:
              result = val
    return result

def isGlobalListenPortEnabled(model):
    result=False
    if 'topology' in model:
        if 'ListenPortEnabled' in model['topology']:
            val = model['topology']['ListenPortEnabled']
            if isinstance(val, str) or isinstance(val, unicode):
              result = Boolean.valueOf(val)
            else:
              result = val
    return result

def addAdminChannelPortForwardNetworkAccessPoints(server):
  admin_channel_port_forwarding_enabled = env.getEnvOrDef("ADMIN_CHANNEL_PORT_FORWARDING_ENABLED", "true")
  if (admin_channel_port_forwarding_enabled == 'false'):
    return
  # Set the default if it is not provided to avoid nap default to 0 which fails validation.
  admin_server_port = _get_default_listen_port(server)

  model = env.getModel()
  secure_mode = isSecureModeEnabledForDomain(model)
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
  if isAdministrationPortEnabledForServer(server, model):
    _writeAdminChannelPortForwardNAP(name='internal-admin', server=server,
                                     listen_port=getAdministrationPort(server, model['topology']), protocol='admin')
  elif index == 0:
    if not env.wlsVersionEarlierThan("14.1.2.0"):
        if not secure_mode and is_listenport_enabled(server):
          _writeAdminChannelPortForwardNAP(name='internal-t3', server=server, listen_port=admin_server_port, protocol='t3')
        elif secure_mode and (is_listenport_enabled(server) or isGlobalListenPortEnabled(model)):
          _writeAdminChannelPortForwardNAP(name='internal-t3', server=server, listen_port=admin_server_port, protocol='t3')
    else:
        if not secure_mode and is_listenport_enabled(server):
          _writeAdminChannelPortForwardNAP(name='internal-t3', server=server, listen_port=admin_server_port, protocol='t3')

    ssl = getSSLOrNone(server)
    ssl_listen_port = None
    if ssl is not None and 'Enabled' in ssl and ssl['Enabled']:
      ssl_listen_port = ssl['ListenPort']
      if ssl_listen_port is None:
        ssl_listen_port = "7002"
    elif ssl is None and secure_mode:
      ssl_listen_port = "7002"
    # Check override for 14.1.2.x

    if not env.wlsVersionEarlierThan("14.1.2.0") and ssl is None:
        if isGlobalSSLEnabled(model):
            ssl_listen_port = 7002
        else:
            ssl_listen_port = None

    if ssl_listen_port is not None:
      _writeAdminChannelPortForwardNAP(name='internal-t3s', server=server, listen_port=ssl_listen_port, protocol='t3s')


def is_listenport_enabled(server):
  is_listen_port_enabled = True
  if 'ListenPortEnabled' in server:
    val = server['ListenPortEnabled']
    if isinstance(val, str) or isinstance(val, unicode):
      is_listen_port_enabled = Boolean.valueOf(val)
    else:
      is_listen_port_enabled = val

  return is_listen_port_enabled


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
    if env.getDomainLogHomeLayout() == "Flat":
      web_server_log['FileName'] = logs_dir + "/" + name + "_access.log"
    else:
      web_server_log['FileName'] = "%s/servers/%s/logs/%s_access.log" % (logs_dir, name, name)


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
  return False

def _get_default_listen_port(server):
  if 'ListenPort' not in server:
    return 7001
  else:
    return server['ListenPort']
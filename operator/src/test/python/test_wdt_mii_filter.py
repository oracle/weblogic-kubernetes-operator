# Copyright (c) 2021, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import ast
import model_wdt_mii_filter
import os
import unittest


# import yaml


class WdtUpdateFilterCase(unittest.TestCase):

  ISTIO_NAP_NAMES = ['tcp-cbt', 'tcp-ldap', 'tcp-iiop', 'tcp-snmp', 'http-probe', 'http-default', 'tcp-default']

  def setUp(self):
    self.initialize_environment_variables()


  def initialize_environment_variables(self):
    os.environ['DOMAIN_UID'] = 'sample-domain1'
    os.environ['DOMAIN_HOME'] = '/u01/domains/sample-domain1'
    os.environ['LOG_HOME'] = '/u01/logs/sample-domain1'
    os.environ[
      'CREDENTIALS_SECRET_NAME'] = 'sample-domain1-weblogic-credentials'
    os.environ['DOMAIN_SOURCE_TYPE'] = 'FromModel'
    os.environ['DATA_HOME'] = '/u01/datahome'

  def initialize_istio_naps(self):
    istio_naps = {}

    self.add_istio_nap_to_dict(istio_naps, name='http-probe', protocol='http', http_enabled="true")
    self.add_istio_nap_to_dict(istio_naps, name='tcp-ldap', protocol='ldap', http_enabled="false")


  def add_istio_nap_to_dict(self, istio_naps, name, protocol, http_enabled):
    if name in istio_naps:
      return

    istio_naps.setdefault(name, {})
    listen_address=self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + name)
    nap = istio_naps[name]
    nap['Protocol'] = protocol
    nap['ListenAddres'] = '127.0.0.1'
    nap['PublicAddress'] = '%s.%s' % (listen_address, self.env.getEnvOrDef("ISTIO_POD_NAMESPACE", "default"))
    nap['ListenPort'] = self.env.getEnvOrDef("ISTIO_READINESS_PORT", None)
    nap['HttpEnabledForThisProtocol'] = http_enabled
    nap['TunnelingEnabled'] = 'false'
    nap['OutboundEnabled'] = 'false'
    nap['Enabled'] = 'true'
    nap['TwoWaySslEnabled'] = 'false'
    nap['ClientCertificateEnforced'] = 'false'

  def getModel(self):
    # Load model as dictionary
    file = open(r'../resources/model.dynamic_cluster_dict.txt')
    contents = file.read()
    model = ast.literal_eval(contents)
    file.close()

    # Setup mock environment
    mock_env= MockOfflineWlstEnv()
    mock_env.open(model)
    model_wdt_mii_filter.env = mock_env

    return model

  def getStaticModel(self):
    # Load model as dictionary
    file = open(r'../resources/model.static_cluster_dict.txt')
    contents = file.read()
    model = ast.literal_eval(contents)
    file.close()

    # Setup mock environment
    mock_env= MockOfflineWlstEnv()
    mock_env.open(model)
    model_wdt_mii_filter.env = mock_env

    return model

  def getModelWithoutAdminServerName(self):
    # Load model as dictionary
    file = open(r'../resources/model.dict_without_admin_server_name.txt')
    contents = file.read()
    model = ast.literal_eval(contents)
    file.close()

    # Setup mock environment
    mock_env= MockOfflineWlstEnv()
    mock_env.open(model)
    model_wdt_mii_filter.env = mock_env

    return model

  def writeModelAsDict(self, model, file_path):
    f = open(file_path,"w")
    f.write( str(model) )
    f.close()

  def getServerTemplate(self, model):
    template_name = 'cluster-1-template'
    topology = model['topology']
    server_template = topology['ServerTemplate'][template_name]
    return server_template


  def test_get_server_name_prefix(self):
    model = self.getModel()

    server_template = self.getServerTemplate(model)
    server_name_prefix = model_wdt_mii_filter.getServerNamePrefix(model['topology'], server_template)
    self.assertEqual('managed-server', server_name_prefix, "Expected server name prefix to be \'managed-server\'")

  def test_customize_admin_server_static_cluster(self):
    model = self.getStaticModel()

    server_name = 'admin-server'
    server = model['topology']['Server'][server_name]
    model_wdt_mii_filter.customizeServer(model, server, server_name)
    listen_address = server['ListenAddress']
    self.assertEqual('sample-domain1-admin-server', listen_address, "Expected listen address to be \'sample-domain1-admin-server\'")

  def test_customize_log_in_server_template(self):
    model = self.getModel()

    server_template = self.getServerTemplate(model)
    model_wdt_mii_filter.customizeLog("managed-server${id}", server_template)
    template_log_filename = server_template['Log']['FileName']
    self.assertEqual('/u01/logs/sample-domain1/servers/managed-server${id}/logs/managed-server${id}.log',
    template_log_filename, "Expected log file to be at \'/u01/logs/sample-domain1/servers/managed-server${id}/logs/managed-server${id}.log\'")

  def test_customize_log_in_server_template_flatstructure(self):
    os.environ['LOG_HOME_LAYOUT'] = 'Flat'
    model = self.getModel()

    server_template = self.getServerTemplate(model)
    model_wdt_mii_filter.customizeLog("managed-server${id}", server_template)
    template_log_filename = server_template['Log']['FileName']
    self.assertEqual('/u01/logs/sample-domain1/managed-server${id}.log',
                     template_log_filename, "Expected log file to be at \'/u01/logs/sample-domain1/managed-server${id}.log\'")
    os.environ['LOG_HOME_LAYOUT'] = 'ByServers'


  def test_customize_access_log_in_server_template(self):
    model = self.getModel()

    server_template = self.getServerTemplate(model)
    model_wdt_mii_filter.customizeAccessLog("managed-server${id}", server_template)
    template_access_log = server_template['WebServer']['WebServerLog']['FileName']
    self.assertEqual('/u01/logs/sample-domain1/servers/managed-server${id}/logs/managed-server${id}_access.log',
    template_access_log, "Expected log file at \'/u01/logs/sample-domain1/servers/managed-server${id}/logs/managed-server${id}_access.log\'")


  def test_customize_access_log_in_server_template_flatstructure(self):
    os.environ['LOG_HOME_LAYOUT'] = 'Flat'
    model = self.getModel()

    server_template = self.getServerTemplate(model)
    model_wdt_mii_filter.customizeAccessLog("managed-server${id}", server_template)
    template_access_log = server_template['WebServer']['WebServerLog']['FileName']
    self.assertEqual('/u01/logs/sample-domain1/managed-server${id}_access.log',
                     template_access_log, "Expected log file at \'/u01/logs/sample-domain1/managed-server${id}_access.log\'")
    os.environ['LOG_HOME_LAYOUT'] = 'ByServers'

  def test_customize_default_filestore_in_server_template(self):
    model = self.getModel()

    server_template = self.getServerTemplate(model)
    model_wdt_mii_filter.customizeDefaultFileStore(server_template)
    default_filestore = server_template['DefaultFileStore']['Directory']
    self.assertEqual('/u01/datahome', default_filestore, "Expected default file store directory to be \'/u01/datahome\'")

  def test_customize_network_access_points_in_server_template_istio_1_10_or_higher(self):
    try:
      os.environ['ISTIO_ENABLED'] = 'true'
      os.environ['ISTIO_USE_LOCALHOST_BINDINGS'] = 'false'
      model = self.getModel()

      server_template = self.getServerTemplate(model)
      model_wdt_mii_filter.customizeNetworkAccessPoints(server_template, 'sample-domain1-managed-server${id}')
      nap_listen_address = model['topology']['ServerTemplate']['cluster-1-template']['NetworkAccessPoint']['T3Channel']['ListenAddress']
      self.assertEqual('sample-domain1-managed-server${id}', nap_listen_address, "Expected nap listen address to be \'sample-domain1-managed-server${id}\'")
    finally:
      del os.environ['ISTIO_ENABLED']
      del os.environ['ISTIO_USE_LOCALHOST_BINDINGS']


  def test_customize_port_forward_network_access_point_admin_port_enabled(self):
    model = self.getModel()
    server = model['topology']['Server']['admin-server']

    model_wdt_mii_filter.addAdminChannelPortForwardNetworkAccessPoints(server)

    internal_admin_nap_listen_address = server['NetworkAccessPoint']['internal-admin']['ListenAddress']
    self.assertEqual('localhost', internal_admin_nap_listen_address, "Expected nap listen address to be \'localhost\'")
    internal_admin_nap_listen_port = server['NetworkAccessPoint']['internal-admin']['ListenPort']
    self.assertEqual(9002, internal_admin_nap_listen_port, "Expected nap listen port to be \'9002\'")

  def test_customize_port_forward_network_access_point_multiple_custom_admin_channels(self):
    model = self.getModel()
    server = model['topology']['Server']['admin-server']

    model_wdt_mii_filter.addAdminChannelPortForwardNetworkAccessPoints(server)

    internal_custom1_nap_listen_address = server['NetworkAccessPoint']['internal-admin1']['ListenAddress']
    self.assertEqual('localhost', internal_custom1_nap_listen_address, "Expected nap listen address to be \'localhost\'")
    internal_custom1_nap_listen_port = server['NetworkAccessPoint']['internal-admin1']['ListenPort']
    self.assertEqual(7896, internal_custom1_nap_listen_port, "Expected nap listen address to be \'7896\'")

    internal_custom2_nap_listen_address = server['NetworkAccessPoint']['internal-admin2']['ListenAddress']
    self.assertEqual('localhost', internal_custom2_nap_listen_address, "Expected nap listen address to be \'localhost\'")
    internal_custom1_nap_listen_port = server['NetworkAccessPoint']['internal-admin2']['ListenPort']
    self.assertEqual(7897, internal_custom1_nap_listen_port, "Expected nap listen address to be \'7897\'")

  def test_port_forward_network_access_point_when_admin_server_name_not_defined(self):
    model = self.getModelWithoutAdminServerName()
    servers = model['topology']['Server']
    names = servers.keys()
    for name in names:
      server = servers[name]
      model_wdt_mii_filter.customizeServer(model, server, name)

      internal_admin_nap_listen_address = server['NetworkAccessPoint']['internal-admin']['ListenAddress']
      self.assertEqual('localhost', internal_admin_nap_listen_address, "Expected nap listen address to be \'localhost\'")
      internal_admin_nap_listen_port = server['NetworkAccessPoint']['internal-admin']['ListenPort']
      self.assertEqual(9002, internal_admin_nap_listen_port, "Expected nap listen port to be \'9002\'")

  def test_customizeServerTemplates(self):
    model = self.getModel()

    model_wdt_mii_filter.customizeServerTemplates(model)
    listen_address = model['topology']['ServerTemplate']['cluster-1-template']['ListenAddress']
    self.assertEqual('sample-domain1-managed-server${id}', listen_address, "Expected listen address to be \'sample-domain1-managed-server${id}\'")

  def test_customizeServerTemplate(self):
    model = self.getModel()
    topology = model['topology']
    template_name = 'cluster-1-template'

    serverTemplate = topology['ServerTemplate'][template_name]
    model_wdt_mii_filter.customizeServerTemplate(topology, serverTemplate, template_name)

    # verify custom log in server template
    template_log_filename = serverTemplate['Log']['FileName']
    self.assertEqual('/u01/logs/sample-domain1/servers/managed-server${id}/logs/managed-server${id}.log',
    template_log_filename, "Expected log file at \'/u01/logs/sample-domain1/servers/managed-server${id}/logs/managed-server${id}.log\'")

    # verify custom access log in server template
    template_access_log = serverTemplate['WebServer']['WebServerLog']['FileName']
    self.assertEqual('/u01/logs/sample-domain1/servers/managed-server${id}/logs/managed-server${id}_access.log',
    template_access_log, "Expected log file at \'/u01/logs/sample-domain1/servers/managed-server${id}/logs/managed-server${id}.log\'")

    # verify listen address in server template
    listen_address = model['topology']['ServerTemplate'][template_name]['ListenAddress']
    self.assertEqual('sample-domain1-managed-server${id}', listen_address, "Expected listen address to be \'sample-domain1-managed-server${id}\'")

  def test_getClusterOrNone_returns_none(self):
    model = self.getModel()
    cluster = model_wdt_mii_filter.getClusterOrNone(model, "cluster-2")
    self.assertIsNone(cluster, "Did not expect to find cluster named \'cluster-2\'")

  def test_customize_node_manager_creds(self):
    model = self.getModel()
    model_wdt_mii_filter.initSecretManager(model_wdt_mii_filter.getOfflineWlstEnv())
    model_wdt_mii_filter.customizeNodeManagerCreds(model['topology'])
    self.assertEqual(MockOfflineWlstEnv.WLS_CRED_USERNAME, model['topology']['SecurityConfiguration']['NodeManagerUsername'], "Expected node manager username to be \'" + MockOfflineWlstEnv.WLS_CRED_USERNAME + "\'")
    self.assertEqual(MockOfflineWlstEnv.WLS_CRED_PASSWORD, model['topology']['SecurityConfiguration']['NodeManagerPasswordEncrypted'], "Expected node manager password to be \'" + MockOfflineWlstEnv.WLS_CRED_PASSWORD + "\'")

  def test_customizeDomainLogPath(self):
    model = self.getModel()
    model_wdt_mii_filter.customizeDomainLogPath(model['topology'])
    self.assertEqual('/u01/logs/sample-domain1/sample-domain1.log', model['topology']['Log']['FileName'], "Expected domain log file name to be \'/u01/logs/sample-domain1/sample-domain1.log\'")

  def test_customizeLog_whenNoNameProvided(self):
    model = self.getModel()
    model_wdt_mii_filter.customizeLog(None, model['topology'])
    self.assertNotIn('Log', model['topology'], "Did not expect \'Log\' to be configured")

  def test_customizeCustomFileStores(self):
    model = self.getModel()
    model_wdt_mii_filter.customizeCustomFileStores(model)
    self.assertEqual('/u01/datahome', model['resources']['FileStore']['FileStore-0']['Directory'], "Expected custom filestore directory \'/u01/datahome\'")

  def test_customizeServers(self):
    model = self.getModel()
    model_wdt_mii_filter.customizeServers(model)
    self.assertEqual('sample-domain1-admin-server', model['topology']['Server']['admin-server']['ListenAddress'], "Expected server listen address to be  \'sample-domain1-admin-server\'")

  def test_readDomainNameFromTopologyYaml(self):
    model = self.getModel()
    model_wdt_mii_filter.env.readDomainNameFromTopologyYaml('../resources/topology.yaml')
    domain_name = model_wdt_mii_filter.env.getDomainName()
    self.assertEqual('wls-domain1', domain_name, "Expected domain name to be \'wls-domain1\'")

  def test_isAdministrationPortEnabledForDomain(self):
    model = self.getModel()
    self.assertTrue(model_wdt_mii_filter.isAdministrationPortEnabledForDomain(model))

  def test_isAdministrationPortEnabledForServer(self):
    model = self.getModel()

    # disable Administration port for domain
    model['topology']['AdministrationPortEnabled'] = False

    # enable Administration port for server
    model['topology']['Server']['admin-server']['AdministrationPortEnabled'] = True

    self.assertTrue(model_wdt_mii_filter.isAdministrationPortEnabledForServer(model['topology']['Server']['admin-server'], model))

  def test_isAdministrationPortEnabledForServerFromDomainInfo(self):
    model = self.getModel()

    # disable Administration port for domain
    model['domainInfo']['ServerStartMode'] = 'secure'

    self.assertTrue(model_wdt_mii_filter.isAdministrationPortEnabledForServer(model['topology']['Server']['admin-server'], model))

  def test_isSecureModeEnabledForDomain(self):
    model = self.getModel()

    # enable secure mode for Domain
    model['topology']['SecurityConfiguration'] = {}
    model['topology']['SecurityConfiguration']['SecureMode'] = {}
    model['topology']['SecurityConfiguration']['SecureMode']['SecureModeEnabled'] = True

    self.assertTrue(model_wdt_mii_filter.isSecureModeEnabledForDomain(model))


  def test_istioVersionRequiresLocalHostBindings(self):
    model = self.getModel()
    self.assertFalse(model_wdt_mii_filter.istioVersionRequiresLocalHostBindings())

  def test_adminserver_name_missing(self):
    try:
      model = self.getModel()
      topology = model['topology']
      del topology['AdminServerName']
      model_wdt_mii_filter.filter_model(model)
      self.assertEqual('AdminServer', topology['AdminServerName'],
                       "Expected AdminServerName set to AdminServer after filter")
      admin_server_exists = 'AdminServer' in topology['Server']
      self.assertTrue(admin_server_exists, "Expected AdminServer added if AdminServerName is not set")

      topology['AdminServerName'] = 'MyAdminServer'
      model_wdt_mii_filter.filter_model(model)
      self.assertEqual('MyAdminServer', topology['AdminServerName'],
                       "Expected AdminServerName set to MyAdminServer after filter")
      admin_server_exists = 'MyAdminServer' in topology['Server']
      self.assertTrue(admin_server_exists, "Expected MyAdminServer added if AdminServerName is not set")

    except ImportError as ie:
      self.assertTrue(ie is not None)

class MockOfflineWlstEnv(model_wdt_mii_filter.OfflineWlstEnv):

  WLS_CRED_USERNAME = 'weblogic'
  WLS_CRED_PASSWORD = 'password'

  def __init__(self):
    model_wdt_mii_filter.OfflineWlstEnv.__init__(self)

  def encrypt(self, cleartext):
    return cleartext

  def readFile(self, path):
    if path.endswith('username'):
      return self.WLS_CRED_USERNAME

    return self.WLS_CRED_PASSWORD

  def wlsVersionEarlierThan(self, version):
    return False

if __name__ == '__main__':
  unittest.main()

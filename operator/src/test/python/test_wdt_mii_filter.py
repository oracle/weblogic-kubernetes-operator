# Copyright (c) 2021, 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

import ast
import model_wdt_mii_filter
import os
import unittest

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
    self.assertTrue(cluster is None, "Did not expect to find cluster named \'cluster-2\'")

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
    self.assertFalse('Log' in model['topology'], "Did not expect \'Log\' to be configured")

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

  def test_admin_channel_before_2610_honors_server_enabling_override(self):
    self.check_admin_channel_override('14.1.2.0.0', False, True, True)

  def test_admin_channel_before_2610_honors_server_disabling_override(self):
    self.check_admin_channel_override('14.1.2.0.0', True, False, False)

  def test_admin_channel_2610_ignores_server_enabling_override(self):
    self.check_admin_channel_override('26.1.0.0.0', False, True, False)

  def test_admin_channel_2610_ignores_server_disabling_override(self):
    self.check_admin_channel_override('26.1.0.0.0', True, False, True)

  def check_admin_channel_override(self, wls_version, domain_enabled, server_enabled, expected):
    model = self.getModel()
    model_wdt_mii_filter.env.wls_version = wls_version
    model['topology']['AdministrationPortEnabled'] = domain_enabled
    server = model['topology']['Server']['admin-server']
    server['AdministrationPortEnabled'] = server_enabled
    server['AdministrationPort'] = 9100

    model_wdt_mii_filter.addAdminChannelPortForwardNetworkAccessPoints(server)

    naps = server['NetworkAccessPoint']
    self.assertEqual(expected, 'internal-admin' in naps)
    if expected:
      self.assertEqual(9100, naps['internal-admin']['ListenPort'])
    self.assertEqual(7896, naps['internal-admin1']['ListenPort'])
    self.assertEqual(7897, naps['internal-admin2']['ListenPort'])

  def test_server_template_admin_port_override_depends_on_wls_version(self):
    for wls_version in ['14.1.2.0.0', '26.1.0.0.0']:
      for domain_enabled in [False, True]:
        model = self.getModel()
        model_wdt_mii_filter.env.wls_version = wls_version
        model['topology']['AdministrationPortEnabled'] = domain_enabled
        template = self.getServerTemplate(model)
        template['AdministrationPortEnabled'] = not domain_enabled

        expected = not domain_enabled
        if wls_version == '26.1.0.0.0':
          expected = domain_enabled
        self.assertEqual(expected, model_wdt_mii_filter.isAdministrationPortEnabledForServer(template, model))

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

    except ImportError, ie:
      self.assertTrue(ie is not None)

  def forwarding_channels(self, server, topology, version='26.1.0.0.0', mode=None):
    topology.setdefault('Name', 'test-domain')
    model = {'topology': topology}
    if mode is not None:
      model['domainInfo'] = {'ServerStartMode': mode}
    mock_env = MockOfflineWlstEnv()
    mock_env.open(model)
    mock_env.wls_version = version
    model_wdt_mii_filter.env = mock_env
    model_wdt_mii_filter.addAdminChannelPortForwardNetworkAccessPoints(server)
    return server['NetworkAccessPoint']

  def test_26_secure_forwarding_uses_ssl_when_admin_disabled(self):
    for version in ['26.1.0.0.0', '27.1.0.0.0']:
      server = {'ListenPort': 17106}
      naps = self.forwarding_channels(server, {'AdministrationPortEnabled': False}, version, 'secure')
      self.assertFalse('internal-t3' in naps)
      self.assertFalse('internal-admin' in naps)
      self.assertEqual('t3s', naps['internal-t3s']['Protocol'])
      self.assertEqual('7002', naps['internal-t3s']['ListenPort'])

  def test_26_ssl_boolean_values(self):
    for value in [False, 'false', 'FALSE', True, 'true', 'TRUE']:
      server = {'ListenPort': 17130, 'SSL': {'Enabled': value, 'ListenPort': 18130}}
      naps = self.forwarding_channels(server, {})
      self.assertTrue('internal-t3' in naps)
      expected = value in [True, 'true', 'TRUE']
      self.assertEqual(expected, 'internal-t3s' in naps)
      if expected:
        self.assertEqual(18130, naps['internal-t3s']['ListenPort'])

  def test_26_omitted_ssl_enablement_inherits_domain_and_mode(self):
    for ssl in [None, {}, {'ListenPort': 18113}]:
      for mode in ['dev', 'secure']:
        server = {'ListenPort': 17113}
        if ssl is not None:
          server['SSL'] = ssl
        naps = self.forwarding_channels(server, {'AdministrationPortEnabled': False}, mode=mode)
        self.assertEqual(mode == 'secure', 'internal-t3s' in naps)
        self.assertEqual(mode == 'dev', 'internal-t3' in naps)
        if mode == 'secure':
          expected_port = '7002'
          if ssl and 'ListenPort' in ssl:
            expected_port = 18113
          self.assertEqual(expected_port, naps['internal-t3s']['ListenPort'])

  def test_26_channel_overrides(self):
    for value in [False, 'false', True, 'true']:
      expected = value in [True, 'true']
      topology = {'ListenPortEnabled': value, 'SSLEnabled': value, 'AdministrationPortEnabled': False}
      naps = self.forwarding_channels({'ListenPort': 17107}, topology, mode='secure')
      self.assertEqual(expected, 'internal-t3' in naps)
      self.assertEqual(expected, 'internal-t3s' in naps)
      server = {'ListenPort': 17107, 'ListenPortEnabled': not expected, 'SSL': {'Enabled': not expected}}
      naps = self.forwarding_channels(server, topology, mode='secure')
      self.assertEqual(not expected, 'internal-t3' in naps)
      self.assertEqual(not expected, 'internal-t3s' in naps)

  def test_26_production_false_string_does_not_enable_secure_mode(self):
    for version in ['26.1.0.0.0', '27.1.0.0.0']:
      naps = self.forwarding_channels({'ListenPort': 17101}, {'ProductionModeEnabled': 'false'}, version)
      self.assertTrue('internal-t3' in naps)
      self.assertFalse('internal-admin' in naps)
      self.assertFalse('internal-t3s' in naps)

  def test_26_custom_admin_channel_and_disabled_forwarding(self):
    server = {'NetworkAccessPoint': {'custom': {'Protocol': 'admin', 'ListenPort': 19129}}}
    naps = self.forwarding_channels(server, {})
    self.assertEqual(19129, naps['internal-admin1']['ListenPort'])
    self.assertFalse('internal-t3' in naps)
    self.assertFalse('internal-t3s' in naps)
    server = {'NetworkAccessPoint': {'custom': {'ListenPort': 19129}}}
    naps = self.forwarding_channels(server, {'AdministrationPortEnabled': False}, mode='secure')
    self.assertTrue('internal-t3s' in naps)
    os.environ['ADMIN_CHANNEL_PORT_FORWARDING_ENABLED'] = 'false'
    try:
      naps = self.forwarding_channels({'NetworkAccessPoint': {}}, {})
      self.assertEqual({}, naps)
    finally:
      del os.environ['ADMIN_CHANNEL_PORT_FORWARDING_ENABLED']

  def test_26_forwarding_supports_wdt_dictionary_access(self):
    server = {'SSL': MockWdtDictionary({'Enabled': 'true'}),
              'NetworkAccessPoint': {'custom': MockWdtDictionary({'ListenPort': 19129})}}
    naps = self.forwarding_channels(server, {'AdministrationPortEnabled': False}, mode='secure')
    self.assertEqual('7002', naps['internal-t3s']['ListenPort'])
    self.assertFalse('internal-t3' in naps)

  def test_pre_26_forwarding_behavior_is_preserved(self):
    for version in ['12.2.1.4.0', '14.1.1.0.0', '14.1.2.0.0']:
      naps = self.forwarding_channels({'ListenPort': 17106}, {'AdministrationPortEnabled': False}, version, 'secure')
      self.assertEqual(version == '14.1.2.0.0', 'internal-t3' in naps)
      self.assertEqual(version != '14.1.2.0.0', 'internal-t3s' in naps)
      naps = self.forwarding_channels({'SSL': {'Enabled': 'false', 'ListenPort': 18130}}, {}, version)
      self.assertEqual(18130, naps['internal-t3s']['ListenPort'])

class IntrospectDefaultsCase(unittest.TestCase):

  def setUp(self):
    # Load the actual helper definitions without running WLST main or importing its Java dependencies.
    path = os.path.join(os.path.dirname(model_wdt_mii_filter.__file__), 'introspectDomain.py')
    file = open(path)
    source = file.read()
    file.close()
    source = source[source.index('def wlsVersionEarlierThan('):source.index('\ndef main(env):')]
    self.version = MockWlsVersion()
    self.attributes = {'SecureModeEnabled': True}
    self.children = {}
    self.namespace = {'LegalHelper': MockLegalHelper, 'version_helper': self.version,
                      'false': False, 'cd': self.cd, 'ls': self.ls, 'isSet': self.isSet,
                      'server_template_sslports': {'tpl': None},
                      'server_template_listening_ports': {'tpl': None}}
    exec compile(source, path, 'exec') in self.namespace

  def cd(self, path):
    pass

  def isSet(self, attribute):
    return False

  def ls(self, returnType, returnMap):
    if returnType == 'a':
      return self.attributes
    return self.children

  def test_template_ports_are_gated_on_installed_release(self):
    template = MockPortBean('tpl', 7100)
    for version in ['12.2.1.4.0', '14.1.2.0.0', '26.1.0.0.0', '27.1.0.0.0']:
      self.version.version = version
      newer = not MockLegalHelper.versionEarlierThan(version, '26.1.0.0.0')
      for xml_plain, xml_ssl in [(None, None), ('7001', '7002')]:
        self.namespace['server_template_listening_ports']['tpl'] = xml_plain
        self.namespace['server_template_sslports']['tpl'] = xml_ssl
        expected_plain, expected_ssl = 7001, 7002
        if newer:
          expected_plain, expected_ssl = 7100, 8100
        self.assertEqual(expected_plain, self.namespace['getRealListenPort'](template))
        self.assertEqual(expected_ssl, self.namespace['getRealSSLListenPort'](template, 8100))
      self.namespace['server_template_listening_ports']['tpl'] = '17220'
      self.namespace['server_template_sslports']['tpl'] = '18220'
      template.port = 17220
      self.assertEqual(17220, self.namespace['getRealListenPort'](template))
      self.assertEqual(18220, self.namespace['getRealSSLListenPort'](template, 18220))
      template.port = 7100
      self.assertEqual(7002, self.namespace['getRealSSLListenPort'](MockPortBean('AdminServer', 17101), 7002))

  def test_explicit_admin_false_is_honored_only_on_26_and_later(self):
    # Reproduce a secure bean with isSet false, while the effective admin getter is false.
    self.children = MockChildren(['SecureMode'])
    domain = MockDomain(True, False)
    for version in ['14.1.2.0.0', '26.1.0.0.0', '27.1.0.0.0']:
      self.version.version = version
      expected = version == '14.1.2.0.0'
      self.assertEqual(expected, self.namespace['isAdministrationPortEnabledForDomain'](domain))
      domain.admin = True
      self.assertTrue(self.namespace['isAdministrationPortEnabledForDomain'](domain))
      domain.admin = False

  def test_secure_fallback_is_independent_of_admin_on_26(self):
    domain = MockDomain(True, False)
    for version in ['14.1.2.0.0', '26.1.0.0.0', '27.1.0.0.0']:
      self.version.version = version
      self.assertEqual(version != '14.1.2.0.0', self.namespace['isSecureModeEnabledForDomain'](domain))
    domain.production = False
    self.assertFalse(self.namespace['isSecureModeEnabledForDomain'](domain))

  def test_explicit_secure_false_remains_false(self):
    self.children = MockChildren(['SecureMode'])
    self.attributes['SecureModeEnabled'] = False
    for version in ['14.1.2.0.0', '26.1.0.0.0']:
      self.version.version = version
      self.assertFalse(self.namespace['isSecureModeEnabledForDomain'](MockDomain(True, True)))

  def test_omitted_nap_protocol_uses_secure_mode_with_admin_disabled_on_26(self):
    domain = MockDomain(True, False)
    for version in ['14.1.2.0.0', '26.1.0.0.0', '27.1.0.0.0']:
      self.version.version = version
      expected = 't3s'
      if version == '14.1.2.0.0':
        expected = 't3'
      self.assertEqual(expected, self.namespace['getNAPProtocol'](MockNap('custom', 19129), MockPortBean('AdminServer', 17129), domain))

  def test_26_old_domain_version_does_not_use_removed_server_admin_getter(self):
    domain = MockDomain(True, False)
    self.assertFalse(self.namespace['isAdministrationPortEnabledForServer'](MockPortBean('tpl', 7100), domain, True))

  def test_26_effective_listen_enablement_wins_when_isSet_loses_false(self):
    server = MockPortBean('AdminServer', 17115)
    domain = MockDomain(True, True)
    for version in ['14.1.2.0.0', '26.1.0.0.0', '27.1.0.0.0']:
      self.version.version = version
      for server_enabled in [False, True]:
        server.listen_enabled = server_enabled
        for domain_enabled in [False, True]:
          domain.listen_enabled = domain_enabled
          expected = server_enabled
          if version == '14.1.2.0.0':
            expected = domain_enabled
          self.assertEqual(expected, self.namespace['isListenPortEnabledForServer'](server, domain))

class MockWlsVersion(object):
  version = '26.1.0.0.0'

  def getReleaseBuildVersion(self):
    return self.version

class MockLegalHelper(object):
  def versionEarlierThan(actual, required):
    return [int(part) for part in actual.split('.')] < [int(part) for part in required.split('.')]

  versionEarlierThan = staticmethod(versionEarlierThan)

class MockPortBean(object):
  def __init__(self, name, port):
    self.name = name
    self.port = port
    self.listen_enabled = True

  def getName(self):
    return self.name

  def getListenPort(self):
    return self.port

  def getNetworkAccessPoints(self):
    return ['custom']

  def isListenPortEnabled(self):
    return self.listen_enabled

class MockNap(MockPortBean):
  def getProtocol(self):
    return 't3'

class MockDomain(object):
  def __init__(self, production, admin):
    self.production = production
    self.admin = admin
    self.listen_enabled = True

  def getName(self):
    return 'domain'

  def getDomainVersion(self):
    return '14.1.2.0.0'

  def isProductionModeEnabled(self):
    return self.production

  def isAdministrationPortEnabled(self):
    return self.admin

  def isListenPortEnabled(self):
    return self.listen_enabled

class MockChildren(list):
  def isEmpty(self):
    return False

class MockWdtDictionary(dict):
  # WDT PyOrderedDict supports membership and indexing, but its inherited get() fails.
  def get(self, key, default=None):
    raise AssertionError('Use WDT-compatible dictionary access')

class MockOfflineWlstEnv(model_wdt_mii_filter.OfflineWlstEnv):

  WLS_CRED_USERNAME = 'weblogic'
  WLS_CRED_PASSWORD = 'password'

  def __init__(self):
    model_wdt_mii_filter.OfflineWlstEnv.__init__(self)
    self.wls_version = '14.1.2.0.0'

  def encrypt(self, cleartext):
    return cleartext

  def readFile(self, path):
    if path.endswith('username'):
      return self.WLS_CRED_USERNAME

    return self.WLS_CRED_PASSWORD

  def wlsVersionEarlierThan(self, version):
    actual = self.wls_version.split('.')
    required = version.split('.')
    required.extend(['0'] * (len(actual) - len(required)))
    return [int(part) for part in actual] < [int(part) for part in required]

if __name__ == '__main__':
  unittest.main()

# Copyright (c) 2018, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#
#   This code reads the configuration in a WL domain's domain home, and generates
#   multiple files that are copied to stdout.  It also checks whether the domain
#   configuration is 'valid' (suitable for running in k8s).  Finally, it 
#   populates customer supplied 'configOverrides' templates.
#
#   This code is used by the operator to introspect and validate an arbitrary
#   WL domain before its pods are started.  It generates information that's
#   useful for running the domain, setting up its networking, and for overriding
#   specific parts of its configuration so that it can run in k8s.
# 
#   For more details, see the Description in instrospectDomain.sh (which
#   calls this script).
#
# ---------------------
# Prerequisites/Inputs:
# ---------------------
#
#   A WebLogic install.
#
#   A WebLogic domain.
#
#   A running WL node manager at port 5556 (see introspectDomain.sh).
#
#   Plain text WL admin username/password in:
#     /weblogic-operator/secrets/username 
#     /weblogic-operator/secrets/password
#
#   Optional custom sit cfg 'configOverrides' templates in:
#     /weblogic-operator/config-overrides-secrets
#
#   Optional custom sit cfg 'configOverridesSecrets' in:
#     /weblogic-operator/config-overrides-secrets/<secret-name>/<key>
#
#   The following env vars:
#     DOMAIN_UID         - completely unique id for this domain
#     DOMAIN_HOME        - path for the domain configuration
#     LOG_HOME           - path to override WebLogic server log locations
#     CREDENTIALS_SECRET_NAME  - name of secret containing credentials
#
# ---------------------------------
# Outputs (files copied to stdout):
# ---------------------------------
#
#   topology.yaml                  -- Domain configuration summary for operator (server names, etc).
#                                           -and/or-
#                                     Domain validation warnings/errors. 
#
#   Sit-Cfg-CFG--introspector-situational-config.xml  
#                                  -- Automatic sit cfg overrides for domain configuration
#                                     (listen addresses, etc).
#
#   Sit-Cfg-*                      -- Expanded optional configOverrides sit cfg templates
#
#   boot.properties                -- Encoded credentials for starting WL.
#   userConfigNodeManager.secure   -- Encoded credentials for starting NM in a WL pod.
#   userKeyNodeManager.secure      -- Encoded credentials for starting NM in a WL pod.
#
# 
# Note:
#
#   This code partly depends on a node manager so that we can use it to encrypt 
#   the username and password and put them into files that can be used to connect
#   to the node manager later in the server pods (so that the server pods don't
#   have to mount the secret containing the username and password).
#
#   The configuration overrides are specified via situational config file(s), and 
#   include listen addresses, log file locations, etc.  Additional information
#   is provided in other files -- including encrypted credentials, domain
#   topology (server names, etc), and any validation warnings/errors.
#


import base64
import distutils.dir_util
import inspect
import md5
import os
import re
import sys
import traceback
from sets import Set
from xml.dom.minidom import parse

# Include this script's current directory in the import path (so we can import utils, etc.)
# sys.path.append('/weblogic-operator/scripts')

# Alternative way to dynamically get script's current directory
tmp_callerframerecord = inspect.stack()[0]    # 0 represents this line # 1 represents line at caller
tmp_info = inspect.getframeinfo(tmp_callerframerecord[0])
tmp_scriptdir=os.path.dirname(tmp_info[0])
sys.path.append(tmp_scriptdir)

from utils import *
from weblogic.management.configuration import LegalHelper


class OfflineWlstEnv(object):

  def open(self):

    # before doing anything, get each env var and verify it exists 
    global server_template_sslports
    global server_template_listening_ports


    self.DOMAIN_UID               = self.getEnv('DOMAIN_UID')
    self.DOMAIN_HOME              = self.getEnv('DOMAIN_HOME')
    self.LOG_HOME                 = self.getEnv('LOG_HOME')
    self.LOG_HOME_LAYOUT          = self.getEnvOrDef('LOG_HOME_LAYOUT', 'ByServers')
    self.ACCESS_LOG_IN_LOG_HOME   = self.getEnvOrDef('ACCESS_LOG_IN_LOG_HOME', 'true')
    self.DATA_HOME                = self.getEnvOrDef('DATA_HOME', "")
    self.INIT_DOMAIN_ON_PV        = self.getEnvOrDef('INIT_DOMAIN_ON_PV', None)
    self.CREDENTIALS_SECRET_NAME  = self.getEnv('CREDENTIALS_SECRET_NAME')

    # initialize globals

    # The following 3 globals mush match prefix hard coded in startServer.sh
    self.CUSTOM_PREFIX_JDBC = 'Sit-Cfg-JDBC--'
    self.CUSTOM_PREFIX_JMS  = 'Sit-Cfg-JMS--'
    self.CUSTOM_PREFIX_WLDF = 'Sit-Cfg-WLDF--'
    self.CUSTOM_PREFIX_CFG  = 'Sit-Cfg-CFG--'

    self.INTROSPECT_HOME          = '/tmp/introspect/' + self.DOMAIN_UID
    self.TOPOLOGY_FILE            = self.INTROSPECT_HOME + '/topology.yaml'
    self.CM_FILE                  = self.INTROSPECT_HOME + '/' + self.CUSTOM_PREFIX_CFG + 'introspector-situational-config.xml'
    self.BOOT_FILE                = self.INTROSPECT_HOME + '/boot.properties'
    self.USERCONFIG_FILE          = self.INTROSPECT_HOME + '/userConfigNodeManager.secure'
    self.USERKEY_FILE             = self.INTROSPECT_HOME + '/userKeyNodeManager.secure'

    # Model in image attributes

    self.MII_DOMAIN_SECRET_MD5_FILE   = '/tmp/DomainSecret.md5'
    self.MII_DOMAIN_ZIP               = self.INTROSPECT_HOME + '/domainzip.secure'
    self.MII_PRIMORDIAL_DOMAIN_ZIP    = self.INTROSPECT_HOME + '/primordial_domainzip.secure'

    self.MII_INVENTORY_IMAGE_MD5      = self.INTROSPECT_HOME + '/inventory_image.md5'
    self.MII_INVENTORY_CM_MD5         = self.INTROSPECT_HOME + '/inventory_cm.md5'
    self.MII_INVENTORY_PASSPHRASE_MD5 = self.INTROSPECT_HOME + '/inventory_passphrase.md5'
    self.MII_MERGED_MODEL_FILE        = self.INTROSPECT_HOME + '/merged_model.json'
    self.MII_JRF_EWALLET              = self.INTROSPECT_HOME + '/ewallet.p12'
    self.WLS_VERSION                  = self.INTROSPECT_HOME + "/wls.version"
    self.JDK_PATH                     = self.INTROSPECT_HOME + "/jdk.path"
    self.MII_SECRETS_AND_ENV_MD5      = self.INTROSPECT_HOME + "/secrets_and_env.md5"
    self.MII_DOMAINZIP_HASH           = self.INTROSPECT_HOME + "/domainzip_hash"
    self.MII_WDT_CONFIGMAP_PATH       = self.getEnvOrDef('WDT_CONFIGMAP_PATH',
                                                    '/weblogic-operator/wdt-config-map')
    self.DOMAIN_SOURCE_TYPE           = self.getEnvOrDef("DOMAIN_SOURCE_TYPE", None)
    self.WDT_DOMAIN_TYPE              = self.getEnvOrDef('WDT_DOMAIN_TYPE', 'WLS')

    # The following 4 env vars are for unit testing, their defaults are correct for production.
    self.CREDENTIALS_SECRET_PATH = self.getEnvOrDef('CREDENTIALS_SECRET_PATH', '/weblogic-operator/secrets')
    self.CUSTOM_SECRET_ROOT      = self.getEnvOrDef('CUSTOM_SECRET_ROOT', '/weblogic-operator/config-overrides-secrets')
    self.CUSTOM_SITCFG_PATH      = self.getEnvOrDef('CUSTOM_SITCFG_PATH', '/weblogic-operator/config-overrides')
    self.NM_HOST                 = self.getEnvOrDef('NM_HOST', 'localhost')

    # Check environment variable that skip leasing validation
    self.SKIP_LEASING_VALIDATIONS = self.getEnvOrDef('SKIP_LEASING_VALIDATIONS', "False")

    # maintain a list of errors that we include in topology.yaml on completion, if any

    self.errors             = []

    # maintain a list of files that we print on completion when there are no errors

    self.generatedFiles     = []

    # create tmp directory (mkpath == 'mkdir -p') 

    distutils.dir_util.mkpath(self.INTROSPECT_HOME)

    # remove any files that are already in the tmp directory

    for the_file in os.listdir(self.INTROSPECT_HOME):
      the_file_path = os.path.join(self.INTROSPECT_HOME, the_file)
      if os.path.isfile(the_file_path):
        os.unlink(the_file_path)

    server_template_sslports, server_template_listening_ports = get_server_template_listening_ports_from_configxml(self.getDomainHome() + os.sep
                                                                                                                   + 'config' + os.sep + 'config.xml')

    trace("About to load domain from "+self.getDomainHome())
    readDomain(self.getDomainHome())
    self.domain = cmo
    self.DOMAIN_NAME = self.getDomain().getName()

    # this should only be done for model in image case
    if self.DOMAIN_SOURCE_TYPE == "FromModel" or self.INIT_DOMAIN_ON_PV is not None:
      self.handle_JRFOPSSWallet()

  def handle_JRFOPSSWallet(self):

    if self.WDT_DOMAIN_TYPE == 'JRF':
      try:
        # Only export if it is not there already (i.e. have not been copied from the secrets
        if not os.path.exists('/tmp/opsswallet/ewallet.p12'):
          jps_config_file = self.getDomainHome() + '/config/fmwconfig/jps-config.xml'
          if os.path.exists(jps_config_file):
            opss_passphrase_file = self.getEnv('OPSS_KEY_PASSPHRASE')
            opss_passphrase = self.readFile(opss_passphrase_file).strip()
            os.mkdir('/tmp/opsswallet')
            exportEncryptionKey(jpsConfigFile=self.getDomainHome() + '/config/fmwconfig/jps-config.xml', \
                                keyFilePath='/tmp/opsswallet', keyFilePassword=opss_passphrase)
          else:
            trace("SEVERE","No jps-config.xml found, the domain is not a JRF domain, make sure the domain created is a JRF domain.")
            dumpStack()
            sys.exit(1)
      except (IOError), err:
        trace("SEVERE","Error in exporting OPSS key: " + str(err) + ". Make sure the `spec.configuration.opss.walletPasswordSecret' secret used has the key 'walletPassword'.")
        sys.exit(1)
      except:
        trace("SEVERE","Error in exporting OPSS key")
        dumpStack()
        sys.exit(1)

  def close(self):
    closeDomain()

  def getDomain(self):
    return self.domain

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
    return self.ACCESS_LOG_IN_LOG_HOME == 'true';

  def skipLeasingValidations(self):
    return self.SKIP_LEASING_VALIDATIONS.lower() == 'true'

  def addError(self, error):
    self.errors.append(error)

  def getErrors(self):
    return self.errors

  def getClusterOrNone(self,serverOrTemplate):
    try:
      ret = serverOrTemplate.getCluster()
    except:
      trace("Ignoring getCluster() exception, this is expected.")
      ret = None
    return ret

  # Work-around bug in off-line WLST where cluster.getDynamicServers() may throw
  # when there are no 'real' DynamicServers.  Exception looks like:
  #     at com.sun.proxy.$Proxy46.getDynamicServers(Unknown Source)
  #     at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
  #     at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
  #     at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
  #     at java.lang.reflect.Method.invoke(Method.java:498)
  def getDynamicServersOrNone(self,cluster):
    ret = None
    try:
      cd('/Cluster/' + cluster.getName() + '/DynamicServers')
      # DynamicServers MBean can be found under
      # /Cluster/<clusterName>/DynamicServers/<clusterName>/
      # or
      # /Cluster/<clusterName>/DynamicServers/NO_NAME_0/
      childObjs = ls(returnMap='true', returnType='c')
      if not childObjs.isEmpty():
        cd(childObjs[0])
        if get('ServerTemplate') is not None or int(get('DynamicClusterSize')) > 0:
          # Cluster is a dynamic cluster if a ServerTemplate MBean is found or DynamicClusterSize is greater than 0.
          ret = cmo
    except:
      trace("Ignoring cd() exception for cluster '" + cluster.getName() + "' in getDynamicServerOrNone() and returning None.")
    return ret;

  def addGeneratedFile(self, filePath):
    self.generatedFiles.append(filePath)

  def printGeneratedFiles(self):
    for filePath in self.generatedFiles:
      self.printFile(filePath)

  def encrypt(self, cleartext):
    return encrypt(cleartext, self.getDomainHome())

  def readFile(self, path):
    file = open(path, 'r')
    contents = file.read()
    file.close()
    return contents

  def readBinaryFile(self, path):
    file = open(path, 'rb')
    contents = file.read()
    file.close()
    return contents

  def printFile(self, path):
    trace("Printing file " + path)
    print ">>> ",path
    print self.readFile(path)
    print ">>> EOF"
    print

  def getEnv(self, name):
    val = os.getenv(name)
    if val is None or val == "null":
      trace("SEVERE","Env var "+name+" not set.")
      sys.exit(1)
    return val

  def getEnvOrDef(self, name, deflt):
    val = os.getenv(name)
    if val == None or val == "null" or len(val) == 0:
      return deflt
    return val

  def isEnvSet(self, name):
    val = os.getenv(name)
    if val is None or val == "null":
      return False
    return True

  def toDNS1123Legal(self, address):
    return address.lower().replace('_','-')


class SecretManager(object):

  def __init__(self, env):
    self.env = env

  def encrypt(self, cleartext):
    return self.env.encrypt(cleartext)

  def readCredentialsSecret(self, key):
    path = self.env.CREDENTIALS_SECRET_PATH + '/' + key
    return self.env.readFile(path)

class Generator(SecretManager):

  def __init__(self, env, path):
    SecretManager.__init__(self, env)
    self.env = env
    self.path = path
    self.indentStack = [""]
    self.jmsLeasingClusters = Set()

  def open(self):
    self.f =  open(self.path, 'w+')

  def close(self):
    self.f.close()

  def indent(self):
    self.indentStack.append(self.indentPrefix() + "  ")

  def undent(self):
    self.indentStack.pop()

  def indentPrefix(self):
    return self.indentStack[len(self.indentStack)-1]

  def write(self, msg):
    self.f.write(msg)

  def writeln(self, msg):
    self.f.write(self.indentPrefix() + msg + "\n")

  def quote(self, val):
    return "\"" + val + "\""

  def name(self, mbean):
    return "\"" + mbean.getName() + "\"";

  def addGeneratedFile(self):
    return self.env.addGeneratedFile(self.path)

class TopologyGenerator(Generator):

  def __init__(self, env):
    Generator.__init__(self, env, env.TOPOLOGY_FILE)

  def validate(self):
    self.validateAdminServer()
    if not self.env.skipLeasingValidations():
      self.validateJMSResourcesLeasing()
    self.validateClusters()
    self.validateServerCustomChannelName()
    self.validateDynamicClustersDuplicateServerNamePrefix()
    return self.isValid()

  def generate(self):
    self.open()
    try:
      if self.isValid():
        self.generateTopology()
      else:
        self.reportErrors()
      self.close()
      self.addGeneratedFile()
    finally:
      self.close()

  # Work-around bug in off-line WLST where cluster.getDynamicServers() may throw

  def isConsensusLeasing(self, cluster):
    if cluster is not None and cluster.getMigrationBasis() == "consensus":
      return True
    return False

  def getClusterFromSingletonService(self, singletonService):
    try:
      ret = singletonService.getCluster()
    except:
      # exception could be thrown if cluster does not exist
      self.addError("singletonService.getCluster() throws exception");
      trace("Ignoring getCluster() exception and return None.")
      ret = None
    return ret

  def getJTAMigratableTargetMigrationPolicy(self, server_or_serverTemplate, isServerTemplate=False):
    migrationPolicy = None
    try:
      jtaMigratableTarget = server_or_serverTemplate.getJTAMigratableTarget()
      if jtaMigratableTarget is not None:
        migrationPolicy = jtaMigratableTarget.getMigrationPolicy()
    except:
      trace("workaround for exception thrown by getJTAMigratableTarget() in some versions of WebLogic")
      try:
        if isServerTemplate:
          cd('/ServerTemplate')
        else:
          cd('/Server')
        cd(server_or_serverTemplate.getName())
        cd('JTAMigratableTarget/NO_NAME_0')
        migrationPolicy = get('MigrationPolicy')
      except:
        trace("Ingoring exception when trying to get MigrationPolicy of JTAMigratableTarget().")
    return migrationPolicy

  def validateAdminServer(self):
    adminServerName = self.env.getDomain().getAdminServerName()
    if adminServerName is None:
      addError("The admin server name is null.")
      return
    adminServer = None
    for server in self.env.getDomain().getServers():
      self.validateServerTemplateNapListenPortIsSet(server)
      if adminServerName == server.getName():
        adminServer = server
    if adminServer is None:
      addError("The admin server '" + adminServerName + "' does not exist.")
      return
    cluster = self.env.getClusterOrNone(adminServer)
    if cluster is not None:
      self.addError("The admin server " + self.name(adminServer) + " belongs to the WebLogic cluster " + self.name(cluster) + ", the operator does not support having an admin server participate in a cluster.")

  def validateDynamicClustersDuplicateServerNamePrefix(self):
    serverNamePrefixes = []
    for cluster in self.env.getDomain().getClusters():
      dynamicServers = self.env.getDynamicServersOrNone(cluster)
      if dynamicServers is not None:
        if dynamicServers.getServerNamePrefix() is None:
          self.addError("The ServerNamePrefix is not set for WebLogic dynamic cluster " + self.name(cluster) + "'s dynamic servers. The ServerNamePrefix must be set for each WebLogic dynamic cluster.")
        else:
          if dynamicServers.getServerNamePrefix() in serverNamePrefixes:
            self.addError("The ServerNamePrefix '" + dynamicServers.getServerNamePrefix() + "' specified for WebLogic dynamic cluster " + self.name(cluster) + "'s dynamic servers is already in use. The ServerNamePrefix must be unique for each WebLogic dynamic cluster.")
          else:
            serverNamePrefixes.append(dynamicServers.getServerNamePrefix())

  def validateClusters(self):
    for cluster in self.env.getDomain().getClusters():
      self.validateCluster(cluster)

  def validateCluster(self, cluster):
    if self.env.getDynamicServersOrNone(cluster) is None:
      self.validateNonDynamicCluster(cluster)
    else:
      self.validateDynamicCluster(cluster)

  def validateNonDynamicCluster(self, cluster):
    self.validateNonDynamicClusterReferencedByAtLeastOneServer(cluster)
    self.validateNonDynamicClusterNotReferencedByAnyServerTemplates(cluster)
    self.validateNonDynamicClusterServersHaveSameListenPort(cluster)
    self.validateNonDynamicClusterServerHaveSameCustomChannels(cluster)
    if not self.env.skipLeasingValidations():
      self.validateNonDynamicClusterLeasing(cluster)

  def validateNonDynamicClusterReferencedByAtLeastOneServer(self, cluster):
    for server in self.env.getDomain().getServers():
      if self.env.getClusterOrNone(server) is cluster:
        return
    self.addError("The WebLogic configured cluster " + self.name(cluster) + " is not referenced by any servers.  You must have managed servers defined that belong to this cluster.")

  def validateNonDynamicClusterNotReferencedByAnyServerTemplates(self, cluster):
    for template in self.env.getDomain().getServerTemplates():
      if self.env.getClusterOrNone(template) is cluster:
        self.addError("The WebLogic configured cluster " + self.name(cluster) + " is referenced by the server template " + self.name(template) + ", the operator does not support configured clusters referenced by a server template.")

  LISTEN_PORT = 'listen port'
  LISTEN_PORT_ENABLED = 'listen port enabled'
  SSL_LISTEN_PORT = 'ssl listen port'
  SSL_LISTEN_PORT_ENABLED = 'ssl listen port enabled'
  ADMIN_LISTEN_PORT = 'admin listen port'
  ADMIN_LISTEN_PORT_ENABLED = 'admin listen port enabled'

  def validateNonDynamicClusterServersHaveSameListenPort(self, cluster):
    firstServer = None
    firstListenPort = None
    firstListenPortEnabled = None
    firstSslListenPort = None
    firstSslListenPortEnabled = None
    firstAdminPort = None
    firstAdminPortEnabled = None
    for server in self.env.getDomain().getServers():
      if cluster is self.env.getClusterOrNone(server):
        listenPort = getRealListenPort(server)
        listenPortEnabled = isListenPortEnabledForServer(server, self.env.getDomain())
        # default is False
        sslListenPortEnabled = False
        sslListenPort = None
        ssl_listen_port = getSSLPortIfEnabled(server, self.env.getDomain(), is_server_template=False)
        if ssl_listen_port is not None:
          sslListenPort = ssl_listen_port
          sslListenPortEnabled = True

        # WLST does return ssl listen port as None if SSL is disabled

        adminPort = getAdministrationPort(server, self.env.getDomain())
        adminPortEnabled = isAdministrationPortEnabledForServer(server, self.env.getDomain())
        if firstServer is None:
          firstServer = server
          firstListenPort = listenPort
          firstListenPortEnabled = listenPortEnabled
          firstSslListenPort = sslListenPort
          firstSslListenPortEnabled = sslListenPortEnabled
          firstAdminPort = adminPort
          firstAdminPortEnabled = adminPortEnabled
        else:
          if listenPort != firstListenPort:
            self.addError("The WebLogic configured cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + "'s listen port is " + str(firstListenPort) + " but its server " + self.name(server) + "'s listen port is " + str(listenPort) + ". All ports for the same channel in a cluster must be the same.")
          if listenPortEnabled != firstListenPortEnabled:
            self.addError("The WebLogic configured cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + " has listen port enabled: " + self.booleanToString(firstListenPortEnabled) + " but its server " + self.name(server) + "'s listen port enabled: " + self.booleanToString(listenPortEnabled) + ".  Channels in a cluster must be either all enabled or disabled.")
          if sslListenPort is not None and sslListenPort != firstSslListenPort:
             self.addError("The WebLogic configured cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + "'s ssl listen port is " + str(firstSslListenPort) + " but its server " + self.name(server) + "'s ssl listen port is " + str(sslListenPort)
                           + ".  All ports for the same channel in a cluster must be the same and they must either be "
                           + "enabled or disabled uniformly")
          if sslListenPortEnabled != firstSslListenPortEnabled:
            self.addError("The WebLogic configured cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + " has ssl listen port enabled: " + self.booleanToString(firstSslListenPortEnabled) + " but its server " + self.name(server) + "'s ssl listen port enabled: " + self.booleanToString(sslListenPortEnabled)
                          + ".  All ports for the same channel in a cluster must be the same and they must either be "
                          + "enabled or disabled uniformly")
          if adminPort != firstAdminPort:
            self.addError("The WebLogic configured cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + "'s ssl listen port is " + str(firstAdminPort) + " but its server " + self.name(server) + "'s ssl listen port is " + str(adminPort) + ".  All ports for the same channel in a cluster must be the same.")
          if adminPortEnabled != firstAdminPortEnabled:
            self.addError("The WebLogic configured cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + " has ssl listen port enabled: " + self.booleanToString(firstAdminPortEnabled) + " but its server " + self.name(server) + "'s ssl listen port enabled: " + self.booleanToString(adminPortEnabled) + ".  Channels in a cluster must be either all enabled or disabled.")

  def validateNonDynamicClusterServerHaveSameCustomChannels(self, cluster):
     firstServer = None
     serverNap = {}
     for server in self.env.getDomain().getServers():
       if cluster is self.env.getClusterOrNone(server):
         if firstServer is None:
           for nap in server.getNetworkAccessPoints():
             serverNap[nap.getName()] = getNAPProtocol(nap, server, self.env.getDomain()) + "~" + str(nap.getListenPort());
           firstServer = server
         else:
           naps = server.getNetworkAccessPoints()
           if len(naps) != len(serverNap):
             self.addError("The WebLogic configured cluster " + self.name(cluster) + " has mismatched number of network access points in servers " + self.name(firstServer) + " and " + self.name(server) + ". All network access points in a cluster must be the same.")
             return
           else:
             for nap in naps:
               if nap.getName() in serverNap:
                 if serverNap[nap.getName()] != getNAPProtocol(nap, server, self.env.getDomain()) + "~" + str(nap.getListenPort()):
                   self.addError("The WebLogic configured cluster " + self.name(cluster) + " has mismatched network access point " + self.name(nap) + " in servers " + self.name(firstServer) + " and " + self.name(server) + ". All network access points in a cluster must be the same.")
                   return
               else:
                 self.addError("The WebLogic configured cluster " + self.name(cluster) + " has mismatched network access point " + self.name(nap) + " in servers " + self.name(firstServer) + " and " + self.name(server) + ". All network access points in a cluster must be the same.")
                 return

  def validateNonDynamicClusterLeasing(self, cluster):
    leasingRequired = self.validateSingletonServices(cluster)
    for server in self.env.getDomain().getServers():
      if cluster is self.env.getClusterOrNone(server):
        leasingRequired |= self.validateJTAMigrationPolicy(server, cluster)
        self.validateWholeServerMigrationDisabled(server, cluster)
    if cluster.getName() in self.jmsLeasingClusters:
      leasingRequired = True
    if leasingRequired == True:
      self.validateClusterLeasingDataSourceConfigured(cluster)

  def validateDynamicCluster(self, cluster):
    self.validateDynamicClusterReferencedByOneServerTemplate(cluster)
    self.validateDynamicClusterDynamicServersDoNotUseCalculatedListenPorts(cluster)
    self.validateDynamicClusterNotReferencedByAnyServers(cluster)
    if not self.env.skipLeasingValidations():
      self.validateDynamicClusterLeasing(cluster)

  def validateDynamicClusterReferencedByOneServerTemplate(self, cluster):
    server_template=None
    for template in self.env.getDomain().getServerTemplates():
      self.validateServerTemplateNapListenPortIsSet(template)
      if self.env.getClusterOrNone(template) is cluster:
        if server_template is None:
          server_template = template
        else:
          if server_template is not None:
            self.addError("The WebLogic dynamic cluster " + self.name(cluster) + " is referenced the server template " + self.name(server_template) + " and the server template " + self.name(template) + ".")
            return
    if server_template is None:
      self.addError("The WebLogic dynamic cluster " + self.name(cluster) + " is not referenced by any server template.")

  def validateServerTemplateNapListenPortIsSet(self, server_or_template):
    naps = server_or_template.getNetworkAccessPoints()
    for nap in naps:
      if nap.getListenPort() == 0:
        self.addError(
              "Invalid listen port value '"
              + str(nap.getListenPort())
              + "' in the WebLogic Domain for "
              + server_or_template.getName()
              + ' Network Channel '
              + nap.getName()
              + '. Please provide a valid value for the listen port, this is likely because of not specifying the port '
                'value during domain '
                'creation')

  def validateDynamicClusterLeasing(self, cluster):
    leasingRequired = self.validateSingletonServices(cluster)
    serverTemplate = self.getDynamicClusterServerTemplate(cluster)
    if serverTemplate is not None:
      leasingRequired |= self.validateJTAMigrationPolicy(serverTemplate, cluster, True)
      self.validateWholeServerMigrationDisabled(serverTemplate, cluster)
    if cluster.getName() in self.jmsLeasingClusters:
      leasingRequired = True
    if leasingRequired == True:
        self.validateClusterLeasingDataSourceConfigured(cluster)

  def validateWholeServerMigrationDisabled(self, server_or_template, cluster):
    if server_or_template.isAutoMigrationEnabled() == True:
      self.addMigrationError("Automatic whole server migration is enabled in cluster " + self.name(cluster) +
                             " but the feature is not supported by the WebLogic Kubernetes Operator.")

  def validateClusterLeasingDataSourceConfigured(self, cluster):
    if cluster.getDataSourceForAutomaticMigration() is None:
      self.addMigrationError("At least one configured service requires leasing, but datasource for database leasing is not configued in cluster " + self.name(cluster) + ".")

  def validateSingletonServices(self, cluster):
    leasingRequired = False
    singletonServices = self.env.getDomain().getSingletonServices()
    if singletonServices is not None:
      for singletonService in singletonServices:
        if self.getClusterFromSingletonService(singletonService) is cluster:
          leasingRequired = True
          if self.isConsensusLeasing(cluster):
            self.addConsensusLeasingError("Singleton service " + self.name(singletonService) + " is configured in cluster " + self.name(cluster))
    return leasingRequired

  def validateJTAMigrationPolicy(self, server_or_template, cluster, isServerTemplate=False):
    leasingRequired = False
    migrationPolicy = self.getJTAMigratableTargetMigrationPolicy(server_or_template, isServerTemplate)
    # possible values are "manual", "exactly-once", and "failure-recovery".
    # All values except "manual" requires cluster leasing.
    if migrationPolicy is not None and migrationPolicy != "manual":
      leasingRequired = True
      if self.isConsensusLeasing(cluster):
        if isServerTemplate:
          type_str = "server template "
        else:
          type_str = "server "
        self.addConsensusLeasingError("JTA migratable target in " + type_str + self.name(server_or_template)
                                      + " in cluster " + self.name(cluster) + " requires automatic service migration")
    return leasingRequired

  def addMigrationError(self, message):
    self.addError(message + " Set SKIP_LEASING_VALIDATIONS environment variable to 'True' to skip this validation.")

  def addConsensusLeasingError(self, message):
    self.addError(message + ", but cluster is configured to use consensus leasing which is not " +
                  "supported by the WebLogic Kubernetes Operator. " +
                  "Please configure cluster to use database leasing " +
                  "or set SKIP_LEASING_VALIDATIONS environment variable to 'True' to skip this validation.")

  def findCluster(self, name):
    for cluster in self.env.getDomain().getClusters():
      if cluster.getName() == name:
        return cluster
    return None

  def findMigratableTarget(self, name):
    for migratableTarget in self.env.getDomain().getMigratableTargets():
      if migratableTarget.getName() == name:
        return migratableTarget
    return None

  def validateJMSResourcesLeasing(self):
    domain = self.env.getDomain()
    for messagingBridge in domain.getMessagingBridges():
      self.validateJMSResourceTargets(messagingBridge, "Messaging bridge")
    for jdbcStore in domain.getJDBCStores():
      self.validateJMSResourceTargets(jdbcStore, "JDBCStore")
    for fileStore in domain.getFileStores():
      self.validateJMSResourceTargets(fileStore, "FileStore")
    for pathService in domain.getPathServices():
      self.validateJMSResourceTargets(pathService, "Path service", False)
    for jmsServer in domain.getJMSServers():
      self.validateJMSResourceTargets(jmsServer, "JMS Server", False)
    for safAgent in domain.getSAFAgents():
      self.validateJMSResourceTargets(safAgent, "SAF agent", False)

  def validateJMSResourceTargets(self, jmsResource, typeStr, hasMigrationPolicy=True):
    targets = jmsResource.getTargets();
    if targets is not None:
      for target in targets:
        migratableTarget = self.findMigratableTarget(target.getName())
        if migratableTarget is not None:
          # target is a migratable target?
          self.validateJMSResourceMigratableTargeted(jmsResource, migratableTarget, typeStr)
        elif hasMigrationPolicy == True:
          # only check cluster target if JMSResource mbean contains migrationPolicy attribute
          cluster = self.findCluster(target.getName())
          if cluster is not None:
            # target is a cluster
            self.validateJMSResourceClusterTargeted(jmsResource, cluster, typeStr)

  def validateJMSResourceClusterTargeted(self, jmsResource, cluster, typeStr):
    # valid migration policy values are "Off", "On-Failure", and "Always".
    # Cluster leasing must be configured for "On-Failure" and "Always".
    if jmsResource.getMigrationPolicy() is not None and jmsResource.getMigrationPolicy() != "Off":
      self.jmsLeasingClusters.add(cluster.getName())
      if self.isConsensusLeasing(cluster):
        self.addConsensusLeasingError("Automatic service migration is enabled for " + typeStr + " " +
                                      self.name(jmsResource) + " with cluster target " +
                                      self.name(cluster) )

  def validateJMSResourceMigratableTargeted(self, jmsResource, migratableTarget, typeStr):
      # possible migration policy values are "manual", "exactly-once", and "failure-recovery".
      # All values except "manual" requires cluster leasing.
      migrationPolicy = migratableTarget.getMigrationPolicy()
      if migrationPolicy is not None and migrationPolicy != "manual":
        cluster = migratableTarget.getCluster()
        if cluster is not None:
          self.jmsLeasingClusters.add(cluster.getName())
          if self.isConsensusLeasing(cluster):
            self.addConsensusLeasingError("Automatic service migration is enabled for " + typeStr + " " +
                                          self.name(jmsResource) + " with migratable target " +
                                          self.name(migratableTarget) + " that is associated with cluster " +
                                          self.name(cluster) )


  def validateDynamicClusterNotReferencedByAnyServers(self, cluster):
    for server in self.env.getDomain().getServers():
      if self.env.getClusterOrNone(server) is cluster:
        self.addError("The WebLogic dynamic cluster " + self.name(cluster) + " is referenced by configured server " + self.name(server) + ", the operator does not support 'mixed clusters' that host both dynamic (templated) servers and configured servers.")

  def validateDynamicClusterDynamicServersDoNotUseCalculatedListenPorts(self, cluster):
    dynamicServers = self.env.getDynamicServersOrNone(cluster)
    if dynamicServers is not None:
      if dynamicServers.isCalculatedListenPorts() == True:
        self.addError("The WebLogic dynamic cluster " + self.name(cluster) + "'s dynamic servers use calculated listen ports.")

  def validateServerCustomChannelName(self):
    reservedNames = ['default','default-secure','default-admin']
    for server in self.env.getDomain().getServers():
      naps = server.getNetworkAccessPoints()
      for nap in naps:
        if nap.getName() in reservedNames:
          self.addError("The custom channel " + self.name(nap) + " is a reserved name.")

  def isValid(self):
    return len(self.env.getErrors()) == 0

  def addError(self, error):
    self.env.addError(error)

  def reportErrors(self):
    self.writeln("domainValid: false")
    self.writeln("validationErrors:")
    for error in self.env.getErrors():
      self.writeln("- \"" + error.replace("\"", "\\\"") + "\"")

  def generateTopology(self):
    self.writeln("domainValid: true")
    self.addDomain()

  def addDomain(self):
    self.writeln("domain:")
    self.indent()
    self.writeln("name: " + self.name(self.env.getDomain()))
    self.writeln("adminServerName: " + self.quote(self.env.getDomain().getAdminServerName()))
    self.addConfiguredClusters()
    self.addServerTemplates()
    self.addNonClusteredServers()
    self.undent()

  def addConfiguredClusters(self):
    clusters = self.env.getDomain().getClusters()
    if len(clusters) == 0:
      return
    self.writeln("configuredClusters:")
    self.indent()
    for cluster in clusters:
      self.addConfiguredCluster(cluster)
    self.undent()
  
  def getConfiguredClusters(self):
    rtn = []
    for cluster in self.env.getDomain().getClusters():
      if self.env.getDynamicServersOrNone(cluster) is None:
        rtn.append(cluster)
    return rtn

  def addConfiguredCluster(self, cluster):
    self.writeln("- name: " + self.name(cluster))
    dynamicServers = self.env.getDynamicServersOrNone(cluster)
    if dynamicServers is not None:
      self.indent();
      self.writeln("dynamicServersConfig:")
      self.indent()
      self.addDynamicServer(dynamicServers)
      self.undent()
      self.undent()
    servers = self.getClusteredServers(cluster)
    if len(servers) != 0:
      self.indent();
      self.writeln("servers:")
      self.indent()
      for server in servers:
        self.addServer(server)
      self.undent()
      self.undent()

  def addDynamicServer(self, dynamicServer):
    if dynamicServer.getName() is not None:
      name=self.name(dynamicServer)
      self.writeln("name: " + name)
    self.writeln("serverTemplateName: " + self.quote(dynamicServer.getServerTemplate().getName()))
    self.writeln("calculatedListenPorts: " + str(dynamicServer.isCalculatedListenPorts()))
    self.writeln("serverNamePrefix: " + self.quote(dynamicServer.getServerNamePrefix()))
    self.writeln("dynamicClusterSize: " + str(dynamicServer.getDynamicClusterSize()))
    self.writeln("maxDynamicClusterSize: " + str(dynamicServer.getMaxDynamicClusterSize()))
    self.writeln("minDynamicClusterSize: " + str(dynamicServer.getMinDynamicClusterSize()))

  def getClusteredServers(self, cluster):
    rtn = []
    for server in self.env.getDomain().getServers():
      if self.env.getClusterOrNone(server) is cluster:
        rtn.append(server)
    return rtn

  def addServer(self, server, is_server_template=False):
    name=self.name(server)
    self.writeln("- name: " + name)
    if isListenPortEnabledForServer(server, self.env.getDomain(), is_server_template):
      listen_port = getRealListenPort(server)
      self.writeln("  listenPort: " + str(listen_port))
    self.writeln("  listenAddress: " + self.quote(self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + server.getName())))
    if isAdministrationPortEnabledForServer(server, self.env.getDomain(), is_server_template):
      self.writeln("  adminPort: " + str(getAdministrationPort(server, self.env.getDomain())))
    self.addSSL(server, is_server_template)
    self.addNetworkAccessPoints(server, is_server_template)

  def addSSL(self, server, is_server_template):
    '''
    Write the SSL topology information to the topology yaml output
    :param server: Server or ServerTemplate
    '''
    ssl_listen_port = getSSLPortIfEnabled(server, self.env.getDomain(), is_server_template)
    if ssl_listen_port is not None:
      self.indent()
      self.writeln("sslListenPort: " + str(ssl_listen_port))
      self.undent()

  def addServerTemplates(self):
    serverTemplates = self.env.getDomain().getServerTemplates()
    if len(serverTemplates) == 0:
      return
    self.writeln("serverTemplates:")
    self.indent()
    for serverTemplate in serverTemplates:
      if not (self.env.getClusterOrNone(serverTemplate) is None):
        self.addServerTemplate(serverTemplate)
    self.undent()

  def addServerTemplate(self, serverTemplate):
    self.addServer(serverTemplate, is_server_template=True)
    self.writeln("  clusterName: " + self.quote(serverTemplate.getCluster().getName()))

  def addDynamicClusters(self):
    clusters = self.getDynamicClusters()
    if len(clusters) == 0:
      return
    self.writeln("dynamicClusters:")
    self.indent()
    for cluster in clusters:
      self.addDynamicCluster(cluster)
    self.undent()
  
  def getDynamicClusters(self):
    rtn = []
    for cluster in self.env.getDomain().getClusters():
      if self.env.getDynamicServersOrNone(cluster) is not None:
        rtn.append(cluster)
    return rtn

  def addDynamicCluster(self, cluster):
    self.writeln(self.name(cluster) + ":")
    self.indent()
    template = self.findDynamicClusterServerTemplate(cluster)
    dyn_servers = cluster.getDynamicServers()
    listen_port = getRealListenPort(template)
    self.writeln("port: " + str(listen_port))
    self.writeln("maxServers: " + str(dyn_servers.getDynamicClusterSize()))
    self.writeln("baseServerName: " + self.quote(dyn_servers.getServerNamePrefix()))
    self.undent()

  def getDynamicClusterServerTemplate(self, cluster):
    serverTemplate = None
    dynamicServers = self.env.getDynamicServersOrNone(cluster)
    if dynamicServers is not None:
      serverTemplate = dynamicServers.getServerTemplate()
    return serverTemplate

  def findDynamicClusterServerTemplate(self, cluster):
    for template in cmo.getServerTemplates():
      if self.env.getClusterOrNone(template) is cluster:
        return template
    # should never get here - the domain validator already checked that
    # one server template references the cluster
    return None

  def addNonClusteredServers(self):
    # the domain validator already checked that we have a non-clustered admin server
    # therefore we know there will be at least one non-clustered server
    self.writeln("servers:")
    self.indent()
    for server in self.env.getDomain().getServers():
      if self.env.getClusterOrNone(server) is None:
        self.addServer(server)
    self.undent()

  def addNetworkAccessPoints(self, server, is_server_template=False):
    """
    Add network access points for server or server template
    :param server:  server or server template
    """
    naps = server.getNetworkAccessPoints()
    added_nap = False
    if len(naps) != 0:
      added_nap = True
      self.writeln("  networkAccessPoints:")
      self.indent()
      for nap in naps:
        self.addNetworkAccessPoint(server, nap, is_server_template)

    if len(naps) != 0:
      self.undent()

  def addNetworkAccessPoint(self, server, nap, is_server_template):

    # Change the name to follow the istio port naming convention
    nap_protocol = getNAPProtocol(nap, server, self.env.getDomain(), is_server_template)

    name = nap.getName()
    self.writeln("  - name: " + name)
    self.writeln("    protocol: " + self.quote(nap_protocol))

    self.writeln("    listenPort: " + str(nap.getListenPort()))
    self.writeln("    publicPort: " + str(nap.getPublicPort()))


  def addIstioNetworkAccessPoints(self, server, is_server_template, added_nap):
    '''
    Write the container ports information for operator to create the container ports in the topology.xml file
    :param server:   server or template mbean
    :param is_server_template:  true if it is from ServerTemplate
    :param added_nap:  true if there are existing nap section in the output
    '''

    if not added_nap:
      self.writeln("  networkAccessPoints:")
      self.indent()

    self.addIstioNetworkAccessPoint("tcp-ldap", "ldap", getRealListenPort(server), 0)
    self.addIstioNetworkAccessPoint("tcp-default", "t3", getRealListenPort(server), 0)
    # No need to to http default, PodStepContext already handle it
    self.addIstioNetworkAccessPoint("http-default", "http", getRealListenPort(server), 0)
    self.addIstioNetworkAccessPoint("tcp-snmp", "snmp", getRealListenPort(server), 0)
    self.addIstioNetworkAccessPoint("tcp-iiop", "iiop", getRealListenPort(server), 0)

    ssl_listen_port = getSSLPortIfEnabled(server, self.env.getDomain(), is_server_template)

    if ssl_listen_port is not None:
      self.addIstioNetworkAccessPoint("https-secure", "https", ssl_listen_port, 0)
      self.addIstioNetworkAccessPoint("tls-ldaps", "ldaps", ssl_listen_port, 0)
      self.addIstioNetworkAccessPoint("tls-default", "t3s", ssl_listen_port, 0)
      self.addIstioNetworkAccessPoint("tls-iiops", "iiops", ssl_listen_port, 0)

    if isAdministrationPortEnabledForServer(server, self.env.getDomain(), is_server_template):
      self.addIstioNetworkAccessPoint("https-admin", "https", getAdministrationPort(server, self.env.getDomain()), 0)
    return True

  def addIstioNetworkAccessPoint(self, name, protocol, listen_port, public_port):
    self.writeln("  - name: " + name)
    self.writeln("    protocol: " + protocol)
    self.writeln("    listenPort: " + str(listen_port))
    self.writeln("    publicPort: " + str(public_port))

  def booleanToString(self, bool):
    if bool == 0:
      return "false"
    return "true"

class BootPropertiesGenerator(Generator):

  def __init__(self, env):
    Generator.__init__(self, env, env.BOOT_FILE)

  def generate(self):
    self.open()
    try:
      self.addBootProperties()
      self.close()
      self.addGeneratedFile()
    finally:
      self.close()

  def addBootProperties(self):
    self.writeln("username=" + self.encrypt(self.readCredentialsSecret("username")))
    self.writeln("password=" + self.encrypt(self.readCredentialsSecret("password")))

class UserConfigAndKeyGenerator(Generator):

  def __init__(self, env):
    Generator.__init__(self, env, env.USERKEY_FILE)
    self.env = env

  def generate(self):
    if not self.env.NM_HOST:
      # user config&key generation has been disabled for test purposes
      return
    self.open()
    try:
      # first, generate UserConfig file and add it, also generate UserKey file
      self.addUserConfigAndKey()
      self.close()
      # now add UserKey file
      self.addGeneratedFile()
    finally:
      self.close()

  def addUserConfigAndKey(self):
    username = self.readCredentialsSecret("username")
    password = self.readCredentialsSecret("password")
    nm_host = 'localhost'
    nm_port = '5556' 
    domain_name = self.env.getDomain().getName()
    domain_home = self.env.getDomainHome()
    isNodeManager = "true"
    userConfigFile = self.env.USERCONFIG_FILE
    userKeyFileBin = self.env.USERKEY_FILE + '.bin'

    trace("nmConnect " + username + ", " + nm_host + ", " + nm_port + ", " + domain_name + ", " + domain_home)
    nmConnect(username, password, nm_host, nm_port, domain_name, domain_home, 'plain')
    try:
      # the following storeUserConfig WLST command generates two files:
      storeUserConfig(userConfigFile, userKeyFileBin, isNodeManager)

      # user config is already a text file, so directly add it to the generated file list
      self.env.addGeneratedFile(userConfigFile)

      # but key is a binary, so we b64 it - and the caller of this method will add the file
      userKey = self.env.readBinaryFile(userKeyFileBin)
      b64 = ""
      for s in base64.encodestring(userKey).splitlines():
        b64 = b64 + s
      self.writeln(b64)
    finally:
      nmDisconnect()

class MII_DomainConfigGenerator(Generator):

  def __init__(self, env):
    Generator.__init__(self, env, env.MII_DOMAIN_ZIP)
    self.env = env
    self.domain_home = self.env.getDomainHome()
  def generate(self):
    self.open()
    try:
      self.addDomainConfig()
      self.close()
      self.addGeneratedFile()
    finally:
      self.close()

  def addDomainConfig(self):
    kubernetes_platform = self.env.getEnvOrDef("KUBERNETES_PLATFORM", "")
    if (str(kubernetes_platform).upper() == 'OPENSHIFT'):
      os.system("chmod -R g=u %s" % self.domain_home)

    # Note: only config type is needed fmwconfig, security is excluded because it's in the primordial and contain
    # all the many policies files
    packcmd = "tar -pczf /tmp/domain.tar.gz %s/config/config.xml %s/config/jdbc/ %s/config/jms %s/config/coherence " \
              "%s/config/diagnostics %s/config/startup %s/config/configCache %s/config/nodemanager " \
              "%s/wlsdeploy/applications/*.xml " \
              "%s/config/security %s/config/fmwconfig/servers/*/logging.xml" % ( self.domain_home,
              self.domain_home, self.domain_home, self.domain_home, self.domain_home, self.domain_home,
              self.domain_home, self.domain_home, self.domain_home, self.domain_home, self.domain_home)
    os.system(packcmd)
    domain_data = self.env.readBinaryFile("/tmp/domain.tar.gz")
    b64 = ""
    for s in base64.encodestring(domain_data).splitlines():
      b64 = b64 + s
    self.writeln(b64)
    domainzip_hash = md5.new(domain_data).hexdigest()
    fh = open("/tmp/domainzip_hash", "w")
    fh.write(domainzip_hash)
    fh.close()
    trace('done zipping up domain ')


class JRFOpssWalletFileGenerator(Generator):

  def __init__(self, env):
    Generator.__init__(self, env, env.MII_JRF_EWALLET)
    self.env = env
    self.domain_home = self.env.getDomainHome()
  def generate(self):
    self.open()
    try:
      self.addWallet()
      self.close()
      self.addGeneratedFile()
    finally:
      self.close()

  def addWallet(self):
    wallet_data = self.env.readBinaryFile("/tmp/opsswallet/ewallet.p12")
    b64 = ""
    for s in base64.encodestring(wallet_data).splitlines():
      b64 = b64 + s
    self.writeln(b64)
    trace("done writing opss key")


class MII_PrimordialDomainGenerator(Generator):

  def __init__(self, env):
    Generator.__init__(self, env, env.MII_PRIMORDIAL_DOMAIN_ZIP)
    self.env = env
    self.domain_home = self.env.getDomainHome()
  def generate(self):
    self.open()
    try:
      self.addPrimordialDomain()
      self.close()
      self.addGeneratedFile()
    finally:
      self.close()

  def addPrimordialDomain(self):
    primordial_domain_data = self.env.readBinaryFile("/tmp/prim_domain.tar.gz")
    b64 = ""
    for s in base64.encodestring(primordial_domain_data).splitlines():
      b64 = b64 + s
    self.writeln(b64)
    trace("done writing primordial domain")


class MII_IntrospectCMFileGenerator(Generator):

  def __init__(self, env, inventory, fromfile):
    Generator.__init__(self, env, inventory)
    self.env = env
    self.fromfile = fromfile

  def generate(self):
    self.open()
    try:
      rc = self.addFile()
      self.close()
      if rc is not None:
        self.addGeneratedFile()
    finally:
      self.close()

  def addFile(self):
    if os.path.exists(self.fromfile):
      file_str = self.env.readFile(self.fromfile)
      self.writeln(file_str)
      return "hasfile"
    else:
      return None


class SitConfigGenerator(Generator):

  def __init__(self, env):
    Generator.__init__(self, env, env.CM_FILE)

  def generate(self):
    self.open()
    try:
      self.addSitCfg()
      self.close()
      self.addGeneratedFile()
    finally:
      self.close()

  def addSitCfg(self):
    self.addSitCfgXml()

  def addSitCfgXml(self):
    self.writeln("<?xml version='1.0' encoding='UTF-8'?>")
    self.writeln("<d:domain xmlns:d=\"http://xmlns.oracle.com/weblogic/domain\" xmlns:f=\"http://xmlns.oracle.com/weblogic/domain-fragment\" xmlns:s=\"http://xmlns.oracle.com/weblogic/situational-config\">")
    self.indent()
    self.writeln("<s:expiration> 2099-07-16T19:20+01:00 </s:expiration>")
    #self.writeln("<d:name>" + self.env.DOMAIN_NAME + "</d:name>")
    self.customizeNodeManagerCreds()
    self.customizeDomainLogPath()
    self.customizeCustomFileStores()
    self.customizeServers()
    self.customizeServerTemplates()
    self.customizeIstioClusters()
    self.undent()
    self.writeln("</d:domain>")

  def customizeNodeManagerCreds(self):
    username = self.readCredentialsSecret('username')
    password = self.encrypt(self.readCredentialsSecret('password'))
    self.writeln("<d:security-configuration>")
    self.indent()
    self.writeln("<d:node-manager-user-name f:combine-mode=\"replace\">" + username + "</d:node-manager-user-name>")
    self.writeln("<d:node-manager-password-encrypted f:combine-mode=\"replace\">" + password + "</d:node-manager-password-encrypted>")
    self.undent()
    self.writeln("</d:security-configuration>")

  def customizeDomainLogPath(self):
    self.customizeLog(self.env.getDomain().getName(), self.env.getDomain(), true)

  def customizeCustomFileStores(self):
    self.customizeFileStores(self.env.getDomain())

  def customizeServers(self):
    for server in self.env.getDomain().getServers():
      self.customizeServer(server)

  def customizeIstioClusters(self):
    for cluster in self.env.getDomain().getClusters():
      repl_channel_name = cluster.getReplicationChannel()
      # Skip configuring replication channel if 'istiorepl' already defined
      # or replication chcannel is not the default name 'ReplicationChannel'
      # which basically means not configured.
      if repl_channel_name is not None and (repl_channel_name == 'istiorepl'
          or repl_channel_name != 'ReplicationChannel'):
        continue
      self.writeln("<d:cluster>")
      self.indent()
      self.writeln("<d:name>" + cluster.getName() + "</d:name>")
      self.undent()
      self.writeln("</d:cluster>")

  def writeListenAddress(self, originalValue, newValue):
    repVerb="\"replace\""
    if originalValue is None or len(originalValue)==0:
      repVerb="\"add\""
    self.writeln("<d:listen-address f:combine-mode=" + repVerb + ">" + newValue + "</d:listen-address>")

  def writeTwoWaySSLEnabled(self, nap):
    # default twoWaySSLEnabled = 'false'
    if nap.isTwoWaySSLEnabled() == true:
      twoWaySSLEnabled = 'true'
      self.writeln("<d:two-way-ssl-enabled f:combine-mode=\"add\">" + twoWaySSLEnabled + "</d:two-way-ssl-enabled>")

  def writeClientCertificateEnforced(self, nap):
    # default clientCertificateEnforced = 'false'
    if nap.isClientCertificateEnforced() == true:
      clientCertificateEnforced = 'true'
      self.writeln("<d:client-certificate-enforced f:combine-mode=\"add\">" + clientCertificateEnforced + "</d:client-certificate-enforced>")

  def writeChannelIdentityCustomized(self, nap):
    # default channelIdentityCustomized = 'false'
    if nap.isChannelIdentityCustomized() == true:
      channelIdentityCustomized = 'true'
      self.writeln("<d:channel-identity-customized f:combine-mode=\"add\">" + channelIdentityCustomized + "</d:channel-identity-customized>")

  def writeCustomPrivateKeyAlias(self, nap):
    customPrivateKeyAlias = nap.getCustomPrivateKeyAlias()
    if customPrivateKeyAlias is not None:
      self.writeln("<d:custom-private-key-alias f:combine-mode=\"add\">" + customPrivateKeyAlias + "</d:custom-private-key-alias>")

  def writeCustomPrivateKeyPassPhraseEncrypted(self, nap):
    customPriveKeyPassPhraseEncrypted = nap.getCustomPrivateKeyPassPhraseEncrypted()
    if customPriveKeyPassPhraseEncrypted is not None and len(customPriveKeyPassPhraseEncrypted) > 0:
      self.writeln("<d:custom-private-key-pass-phrase-encrypted f:combine-mode=\"add\">" + customPriveKeyPassPhraseEncrypted + "</d:custom-private-key-pass-phrase-encrypted>")

  def writeCustomIdentityKeyStoreType(self, nap):
    customIdentityKeyStoreType = nap.getCustomIdentityKeyStoreType()
    if customIdentityKeyStoreType is not None and len(customIdentityKeyStoreType) > 0:
      self.writeln("<d:custom-identity-key-store-type f:combine-mode=\"add\">" + customIdentityKeyStoreType + "</d:custom-identity-key-store-type>")

  def writeCustomIdentityKeyStorePassPhraseEncrypted(self, nap):
    customIdentityKeyStorePassPhraseEncrypted = nap.getCustomIdentityKeyStorePassPhraseEncrypted()
    if customIdentityKeyStorePassPhraseEncrypted is not None and len(customIdentityKeyStorePassPhraseEncrypted) > 0:
      self.writeln("<d:custom-identity-key-store-pass-phrase-encrypted f:combine-mode=\"add\">" + customIdentityKeyStorePassPhraseEncrypted + "</d:custom-identity-key-store-pass-phrase-encrypted>")

  def writeHostnameVerificationIgnored(self, nap):
    # default hostnameVerificationIgnored = 'false'
    if nap.isHostnameVerificationIgnored() == true:
      hostnameVerificationIgnored = 'true'
      self.writeln("<d:hostname-verification-ignored f:combine-mode=\"add\">" + hostnameVerificationIgnored + "</d:hostname-verification-ignored>")

  def writeHostnameVerifier(self, nap):
    hostnameVerifier = nap.getHostnameVerifier()
    if hostnameVerifier is not None and len(hostnameVerifier) > 0:
      self.writeln("<d:hostname-verifier f:combine-mode=\"add\">" + hostnameVerifier + "</d:hostname-verifier>")

  def writeCiphersuites(self, nap):
    ciphersuites = nap.getCiphersuites()
    if ciphersuites is not None:
      for cipher in ciphersuites:
        self.writeln("<d:ciphersuite f:combine-mode=\"add\">" + cipher + "</d:ciphersuite>")

  def writeAllowUnencryptedNullCipher(self, nap):
    # default allowUnencryptedNullCipher = 'false'
    if nap.isAllowUnencryptedNullCipher() == true:
      allowUnencryptedNullCipher = 'true'
      self.writeln("<d:allow-unencrypted-null-cipher f:combine-mode=\"add\">" + allowUnencryptedNullCipher + "</d:allow-unencrypted-null-cipher>")

  def writeInboundCertificateValidation(self, nap):
    inboundCertificateValidation = nap.getInboundCertificateValidation()
    if inboundCertificateValidation is not None and len(inboundCertificateValidation) > 0:
      self.writeln("<d:inbound-certificate-validation f:combine-mode=\"add\">" + inboundCertificateValidation + "</d:inbound-certificate-validation>")

  def writeOutboundCertificateValidation(self, nap):
    outboundCertificateValidation = nap.getOutboundCertificateValidation()
    if outboundCertificateValidation is not None and len(outboundCertificateValidation) > 0:
      self.writeln("<d:outbound-certificate-validation f:combine-mode=\"add\">" + outboundCertificateValidation + "</d:outbound-certificate-validation>")


  def customizeServer(self, server):
    name=server.getName()
    listen_address=self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + name)
    admin_server_name = self.env.getDomain().getAdminServerName()
    self.writeln("<d:server>")
    self.indent()
    self.writeln("<d:name>" + name + "</d:name>")
    self.customizeLog(name, server, false)
    self.customizeAccessLog(name)
    self.customizeDefaultFileStore(server)
    self.writeListenAddress(server.getListenAddress(),listen_address)
    self.customizeNetworkAccessPoints(server,listen_address)
    if server.getName() == admin_server_name:
      self.addAdminChannelPortForwardNetworkAccessPoints(server)
    if self.getCoherenceClusterSystemResourceOrNone(server) is not None:
      self.customizeCoherenceMemberConfig(server.getCoherenceMemberConfig(),listen_address)
    self.undent()
    self.writeln("</d:server>")

  def customizeServerTemplates(self):
    for template in self.env.getDomain().getServerTemplates():
      if not (self.env.getClusterOrNone(template) is None):
        self.customizeServerTemplate(template)

  def customizeServerTemplate(self, template):
    name=template.getName()
    server_name_prefix=self.env.getDynamicServersOrNone(template.getCluster()).getServerNamePrefix()
    listen_address=self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + server_name_prefix + "${id}")
    self.writeln("<d:server-template>")
    self.indent()
    self.writeln("<d:name>" + name + "</d:name>")
    self.customizeLog(server_name_prefix + "${id}", template, false)
    self.customizeAccessLog(server_name_prefix + "${id}")
    self.customizeDefaultFileStore(template)
    self.writeListenAddress(template.getListenAddress(),listen_address)
    self.customizeNetworkAccessPoints(template,listen_address)
    if self.getCoherenceClusterSystemResourceOrNone(template) is not None:
      self.customizeCoherenceMemberConfig(template.getCoherenceMemberConfig(), listen_address)
    self.undent()
    self.writeln("</d:server-template>")

  def customizeNetworkAccessPoints(self, server, listen_address):
    for nap in server.getNetworkAccessPoints():
      self.customizeNetworkAccessPoint(nap,listen_address)

  def customizeNetworkAccessPoint(self, nap, listen_address):
    # Don't bother 'add' a nap listen-address, only do a 'replace'.
    # If we try 'add' this appears to mess up an attempt to 
    #   'add' PublicAddress/Port via custom sit-cfg.
    # FWIW there's theoretically no need to 'add' or 'replace' when empty
    #   since the runtime default is the server listen-address.

    nap_name=nap.getName()

    # replace listen address to bind to server pod IP
    if not (nap.getListenAddress() is None) and len(nap.getListenAddress()) > 0:
        self.writeln("<d:network-access-point>")
        self.indent()
        self.writeln("<d:name>" + nap_name + "</d:name>")
        self.writeListenAddress("force a replace",listen_address)
        self.undent()
        self.writeln("</d:network-access-point>")

  def writeSecureNetworkAccessPointConfiguration(self, nap):
    protocol = nap.getProtocol()
    if protocol.endswith('s') or protocol == 'admin':
      self.writeTwoWaySSLEnabled(nap)
      self.writeClientCertificateEnforced(nap)
      self.writeChannelIdentityCustomized(nap)
      self.writeCustomPrivateKeyAlias(nap)
      self.writeCustomPrivateKeyPassPhraseEncrypted(nap)
      self.writeCustomIdentityKeyStoreType(nap)
      self.writeCustomIdentityKeyStorePassPhraseEncrypted(nap)
      self.writeHostnameVerificationIgnored(nap)
      self.writeHostnameVerifier(nap)
      self.writeCiphersuites(nap)
      self.writeAllowUnencryptedNullCipher(nap)
      self.writeInboundCertificateValidation(nap)
      self.writeOutboundCertificateValidation(nap)


  def _getNapConfigOverrideAction(self, svr, testname):
    replace_action = 'f:combine-mode="replace"'
    add_action = 'f:combine-mode="add"'
    found = False
    for nap in svr.getNetworkAccessPoints():
      if nap.getName() == testname:
        found = True
        break

    if found:
      trace("SEVERE","Found NetworkAccessPoint with name %s in the WebLogic Domain, this is an internal name used by the WebLogic Kubernetes Operator, please remove it from your domain and try again." % testname)
      sys.exit(1)
    else:
      return add_action, "add"

  def _getPortForwardNapConfigOverrideAction(self, svr, testname):
    add_action = 'f:combine-mode="add"'
    found = False
    for nap in svr.getNetworkAccessPoints():
      if nap.getName() == testname:
        found = True
        break

    if found:
      trace("SEVERE","Found NetworkAccessPoint with name %s in the WebLogic Domain, this is an internal name used by the WebLogic Kubernetes Operator, please remove it from your domain and try again." % testname)
      sys.exit(1)
    else:
      return add_action, "add"

  def _writeAdminChannelPortForwardNAP(self, name, server, listen_port, protocol):
    action, type = self._getPortForwardNapConfigOverrideAction(server, name)

    # For add, we must put the combine mode as add
    # For replace, we must omit it
    if type == "add":
      self.writeln('<d:network-access-point %s>' % action)
    else:
      self.writeln('<d:network-access-point>')

    self.indent()
    if type == "add":
      self.writeln('<d:name %s>%s</d:name>' % (action, name))
    else:
      self.writeln('<d:name>%s</d:name>' % name)

    self.indent()
    self.writeln('<d:name %s>%s</d:name>' % (action, name))

    self.writeln('<d:protocol %s>%s</d:protocol>' % (action, protocol))
    self.writeln('<d:listen-address %s>localhost</d:listen-address>' % action)
    self.writeln('<d:listen-port %s>%s</d:listen-port>' % (action, listen_port))
    self.writeln('<d:http-enabled-for-this-protocol %s>true</d:http-enabled-for-this-protocol>' %
                 (action))
    self.writeln('<d:outbound-enabled %s>false</d:outbound-enabled>' % action)
    self.writeln('<d:enabled %s>true</d:enabled>' % action)
    self.undent()
    self.writeln('</d:network-access-point>')


  def getCoherenceClusterSystemResourceOrNone(self, serverOrTemplate):
    try:
      cluster=serverOrTemplate.getCluster()
      if (cluster is not None):
        ret=cluster.getCoherenceClusterSystemResource()
      else:
        ret=serverOrTemplate.getCoherenceClusterSystemResource()
    except:
      trace("Ignoring getCoherenceClusterSystemResource () exception, this is expected.")
      ret = None
    return ret

  def customizeCoherenceMemberConfig(self, coherence_member_config, listen_address):
    repVerb='"add"'
    if (coherence_member_config is None):
      self.writeln('<d:coherence-member-config f:combine-mode=' + repVerb + '>')
      self.indent()
      self.writeln('<d:unicast-listen-address f:combine-mode=' + repVerb + '>%s</d:unicast-listen-address>' % listen_address)
      self.undent()
      self.writeln('</d:coherence-member-config>')
    else:
      unicastAddress=coherence_member_config.getUnicastListenAddress()
      if unicastAddress is not None:
        repVerb='"replace"'

      self.writeln('<d:coherence-member-config>')
      self.indent()
      self.writeln('<d:unicast-listen-address f:combine-mode=' + repVerb + '>%s</d:unicast-listen-address>' % listen_address)
      self.undent()
      self.writeln('</d:coherence-member-config>')



  def addAdminChannelPortForwardNetworkAccessPoints(self, server):

    index = 0
    for nap in server.getNetworkAccessPoints():
      if nap.getProtocol() == "admin":
        index += 1
        port=nap.getListenPort()
        self._writeAdminChannelPortForwardNAP(name='internal-admin' + str(index), server=server,
                                              listen_port=port, protocol='admin')

    if isAdministrationPortEnabledForServer(server, self.env.getDomain()):
      self._writeAdminChannelPortForwardNAP(name='internal-admin', server=server,
                                            listen_port=getAdministrationPort(server, self.env.getDomain()),
                                            protocol='admin')
    elif index == 0:
      admin_server_port = getRealListenPort(server)
      self._writeAdminChannelPortForwardNAP(name='internal-t3', server=server,
                                            listen_port=admin_server_port, protocol='t3')

      ssl_listen_port = getSSLPortIfEnabled(server, self.env.getDomain(), is_server_template=False)

      if ssl_listen_port is not None:
        self._writeAdminChannelPortForwardNAP(name='internal-t3s', server=server,
                                              listen_port=ssl_listen_port, protocol='t3s')


  def _verify_replication_port_conflict(self, server, replication_port, is_server_template):
    name = server.getName()
    listen_port = getRealListenPort(server)
    ssl_listen_port = getSSLPortIfEnabled(server, self.env.getDomain(), is_server_template)
    if listen_port == replication_port:
      raise_replication_port_conflict(name, listen_port, replication_port, '')
    if ssl_listen_port == replication_port:
      raise_replication_port_conflict(name, ssl_listen_port, replication_port, 'SSL')

    if server.getNetworkAccessPoints() is not None:
      for nap in server.getNetworkAccessPoints():
        nap_name = nap.getName()
        listen_port = getRealListenPort(nap)
        ssl_listen_port = getSSLPortIfEnabled(nap, self.env.getDomain(), is_server_template)

        if listen_port == replication_port:
          raise_replication_port_conflict(nap_name, listen_port, replication_port, '')

        if ssl_listen_port == replication_port:
          raise_replication_port_conflict(nap_name, ssl_listen_port, replication_port, 'SSL')


  def getLogOrNone(self,server):
    try:
      ret = server.getLog()
    except:
      trace("Ignoring getLog() exception, this is expected.")
      ret = None
    return ret

  def customizeLog(self, name, bean, isDomainBean=false):
    logs_dir = self.env.getDomainLogHome()
    if logs_dir is None or len(logs_dir) == 0:
      return

    logaction=''
    fileaction=''
    log = self.getLogOrNone(bean)
    if log is None:
      if not isDomainBean:
        # don't know why, but don't need to "add" a missing domain log bean, and adding it causes trouble
        logaction=' f:combine-mode="add"'
      fileaction=' f:combine-mode="add"'
    else:
      if log.getFileName() is None:
        fileaction=' f:combine-mode="add"'
      else:
        fileaction=' f:combine-mode="replace"'

    self.writeln("<d:log" + logaction + ">")
    self.indent()
    if self.env.getDomainLogHomeLayout() == 'Flat':
      self.writeln("<d:file-name" + fileaction + ">" + logs_dir + "/" + name + ".log</d:file-name>")
    else:
      if not isDomainBean:
        self.writeln("<d:file-name%s>%s/servers/%s/logs/%s.log</d:file-name>" % (fileaction, logs_dir, name, name))
      else:
        self.writeln("<d:file-name%s>%s/%s.log</d:file-name>" % (fileaction, logs_dir, name))

    self.undent()
    self.writeln("</d:log>")

  def customizeFileStores(self, domain):
    data_dir = self.env.getDataHome()
    if data_dir is None or len(data_dir) == 0:
      # do not override if dataHome not specified or empty ("")
      return

    for filestore in domain.getFileStores():
      self.customizeFileStore(filestore, data_dir)


  def customizeFileStore(self, filestore, data_dir):
    fileaction=''
    if filestore.getDirectory() is None:
      fileaction=' f:combine-mode="add"'
    else:
      fileaction=' f:combine-mode="replace"'

    self.writeln("<d:file-store>")
    self.indent()
    self.writeln("<d:name>" + filestore.getName() + "</d:name>")
    self.writeln("<d:directory"+ fileaction + ">" + data_dir + "</d:directory>")
    self.undent()
    self.writeln("</d:file-store>")

  def customizeDefaultFileStore(self, bean):
    data_dir = self.env.getDataHome()
    if data_dir is None or len(data_dir) == 0:
      # do not override if dataHome not specified or empty ("")
      return

    dfsaction=''
    fileaction=''
    if bean.getDefaultFileStore() is None:
      # don't know why, but don't need to "add" a missing default file store bean, and adding it causes trouble
      dfsaction=' f:combine-mode="add"'
      fileaction=' f:combine-mode="add"'
    else:
      if bean.getDefaultFileStore().getDirectory() is None:
        fileaction=' f:combine-mode="add"'
      else:
        fileaction=' f:combine-mode="replace"'

    self.writeln("<d:default-file-store" + dfsaction + ">")
    self.indent()
    self.writeln("<d:directory" + fileaction + ">" + data_dir + "</d:directory>")
    self.undent()
    self.writeln("</d:default-file-store>")

  def customizeAccessLog(self, name):
    # do not customize if LOG_HOME is not set
    logs_dir = self.env.getDomainLogHome()
    if logs_dir is None or len(logs_dir) == 0:
      return

    # customize only if ACCESS_LOG_IN_LOG_HOME is 'true'
    if self.env.isAccessLogInLogHome():
      self.writeln("<d:web-server>")
      self.indent()
      self.writeln("<d:web-server-log>")
      self.indent()
      # combine-mode "replace" works regardless of whether web-server and web-server-log is present or not
      if self.env.getDomainLogHomeLayout() == 'Flat':
        self.writeln("<d:file-name f:combine-mode=\"replace\">"
                     + logs_dir + "/" + name + "_access.log</d:file-name>")
      else:
        self.writeln("<d:file-name f:combine-mode=\"replace\">%s/servers/%s/logs/%s_access.log</d:file-name>" % (
          logs_dir, name, name))

      self.undent()
      self.writeln("</d:web-server-log>")
      self.undent()
      self.writeln("</d:web-server>")

class CustomSitConfigIntrospector(SecretManager):

  def __init__(self, env):
    SecretManager.__init__(self, env)
    self.env = env
    self.macroMap={}
    self.macroStr=''
    self.moduleMap={}
    self.moduleStr=''

    # Populate macro map with known secrets and env vars, log them
    #   env macro format:         'env:<somename>'
    #   plain text secret macro:  'secret:<somename>'
    #   encrypted secret macro:   'secret:<somename>:encrypt'

    if os.path.exists(self.env.CUSTOM_SECRET_ROOT):
      for secret_name in os.listdir(self.env.CUSTOM_SECRET_ROOT):
        secret_path = os.path.join(self.env.CUSTOM_SECRET_ROOT, secret_name)
        self.addSecretsFromDirectory(secret_path, secret_name)

    self.addSecretsFromDirectory(self.env.CREDENTIALS_SECRET_PATH, 
                                 self.env.CREDENTIALS_SECRET_NAME)

    self.macroMap['env:DOMAIN_UID']  = self.env.DOMAIN_UID
    self.macroMap['env:DOMAIN_HOME'] = self.env.DOMAIN_HOME
    self.macroMap['env:LOG_HOME']    = self.env.LOG_HOME
    self.macroMap['env:DOMAIN_NAME'] = self.env.DOMAIN_NAME

    keys=self.macroMap.keys()
    keys.sort()
    for key in keys:
      val=self.macroMap[key]
      if self.macroStr:
        self.macroStr+=', '
      self.macroStr+='${' + key + '}'

    trace("Available macros: '" + self.macroStr + "'")

    # Populate module maps with known module files and names, log them

    self.jdbcModuleStr = self.buildModuleTable(
                           'jdbc', 
                           self.env.getDomain().getJDBCSystemResources(),
                           self.env.CUSTOM_PREFIX_JDBC)

    self.jmsModuleStr = self.buildModuleTable(
                           'jms', 
                           self.env.getDomain().getJMSSystemResources(),
                           self.env.CUSTOM_PREFIX_JMS)

    self.wldfModuleStr = self.buildModuleTable(
                           'diagnostics', 
                           self.env.getDomain().getWLDFSystemResources(),
                           self.env.CUSTOM_PREFIX_WLDF)

    trace('Available modules: ' + self.moduleStr)


  def addSecretsFromDirectory(self, secret_path, secret_name):
    if not os.path.isdir(secret_path):
      # The operator pod somehow put a file where we
      # only expected to find a directory mount.
      self.env.addError("Internal Error:  Secret path'" 
                        + secret_path + "'" +
                        + " is not a directory.")
      return
    for the_file in os.listdir(secret_path):
      the_file_path = os.path.join(secret_path, the_file)
      if os.path.isfile(the_file_path):
        val=self.env.readFile(the_file_path)
        key='secret:' + secret_name + "." + the_file
        self.macroMap[key] = val
        self.macroMap[key + ':encrypt'] = self.env.encrypt(val)


  def buildModuleTable(self, moduleTypeStr, moduleResourceBeans, customPrefix):

    # - Populate global 'moduleMap' with key of 'moduletype-modulename.xml'
    #   andvalue of 'module system resource file name' + '-situational-config.xml'.
    # - Populate global 'moduleStr' with list of known modules.
    # - Generate validation error if a module is not located in a config subdirectory
    #   that matches its type (e.g. jdbc modules are expected to be in directory 'jdbc').

    if self.moduleStr:
      self.moduleStr += ', '
    self.moduleStr += 'type.' + moduleTypeStr + "=("
    firstModule=true

    for module in moduleResourceBeans:

      mname=module.getName()
      mfile=module.getDescriptorFileName()

      if os.path.dirname(mfile) != moduleTypeStr:
        self.env.addError(
          "Error, the operator expects module files of type '" + moduleTypeStr + "'"
          + " to be located in directory '" + moduleTypeStr + "/'"
          + ", but the " + moduleTypeStr + " system resource module '" + mname + "'"
          + " is configured with DescriptorFileName='" + mfile + "'.")      

      if mfile.count(".xml") != 1 or mfile.find(".xml") + 4 != len(mfile):
        self.env.addError(
          "Error, the operator expects system resource module files"
          + " to end in '.xml'"
          + ", but the " + moduleTypeStr + " system resource module '" + mname + "'"
          + " is configured with DescriptorFileName='" + mfile + "'.")      

      if not firstModule:
        self.moduleStr += ", "
      firstModule=false
      self.moduleStr += "'" + mname + "'";

      mfile=os.path.basename(mfile)
      mfile=mfile.replace(".xml","-situational-config.xml")
      mfile=customPrefix + mfile

      self.moduleMap[moduleTypeStr + '-' + mname + '.xml'] = mfile

    # end of for loop

    self.moduleStr += ')' 


  def validateUnresolvedMacros(self, file, filestr):

    # Add a validation error if file contents have any unresolved macros
    # that contain a ":" or "." in  their name.  This step  is performed
    # after all known macros  are  already resolved.  (Other  macros are
    # considered  valid  server  template  macros in  config.xml,  so we
    # assume they're supposed to remain in the final sit-cfg xml).

    errstr = ''
    for unknown_macro in re.findall('\${[^}]*:[^}]*}', filestr):
      if errstr:
        errstr += ","
      errstr += unknown_macro
    for unknown_macro in re.findall('\${[^}]*[.][^}]*}', filestr):
      if errstr:
        errstr += ","
      errstr += unknown_macro
    if errstr:
      self.env.addError("Error, unresolvable macro(s) '" + errstr + "'" 
                        + " in custom sit config file '" + file + "'."
                        + " Known macros are '" + self.macroStr + "'.")


  def generateAndValidate(self):

    # For each custom sit-cfg template, generate a file using macro substitution,
    # validate that it has a correponding module if it's a module override file,
    # and validate that all of its 'secret:' and 'env:' macros are resolvable.

    if not os.path.exists(self.env.CUSTOM_SITCFG_PATH):
      return

    # We expect the user to include a 'version.txt' file in their situational
    # config directory.
    #
    # That file is expected to contain '2.0'
    #
    versionPath=os.path.join(self.env.CUSTOM_SITCFG_PATH,"version.txt")
    if not os.path.exists(versionPath):
        self.env.addError("Error, Required file, '"+versionPath+"', does not exist")
    else:
        version=self.env.readFile(versionPath).strip()
        if not version == "2.0":
            # truncate and ellipsify at 75 characters
            version = version[:75] + (version[75:] and '...')
            self.env.addError("Error, "+versionPath+" does not have the value of"
                              + " '2.0'. The current content: '" + version 
                              + "' is not valid.")

    for the_file in os.listdir(self.env.CUSTOM_SITCFG_PATH):

      if the_file == "version.txt":
        continue  

      the_file_path = os.path.join(self.env.CUSTOM_SITCFG_PATH, the_file)

      if not os.path.isfile(the_file_path):
        continue

      trace("Processing custom sit config file '" + the_file + "'")

      # check if file name corresponds with config.xml or a module

      if not self.moduleMap.has_key(the_file) and the_file != "config.xml":
        self.env.addError("Error, custom sit config override file '" + the_file + "'" 
          + " is not named 'config.xml' or has no matching system resource"
          + " module. Custom sit config files must be named 'config.xml'"
          + " to override config.xml or 'moduletype-modulename.xml' to override"
          + " a module. Known module names for each type: " + self.moduleStr + ".")
        continue

      # substitute macros and validate unresolved macros

      file_str = self.env.readFile(the_file_path)
      file_str_orig = 'dummyvalue'
      while file_str != file_str_orig:
        file_str_orig = file_str
        for key,val in self.macroMap.items():
          file_str=file_str.replace('${'+key+'}',val)

      self.validateUnresolvedMacros(the_file, file_str)

      # put resolved template into a file

      genfile = self.env.INTROSPECT_HOME + '/';

      if the_file == 'config.xml':
        genfile += self.env.CUSTOM_PREFIX_CFG + 'custom-situational-config.xml' 
      else:
        genfile += self.moduleMap[the_file]

      gen = Generator(self.env, genfile)
      gen.open()
      gen.write(file_str)
      gen.close()
      gen.addGeneratedFile()

class DomainIntrospector(SecretManager):

  def __init__(self, env):
    SecretManager.__init__(self, env)
    self.env = env

  def introspect(self):
    tg = TopologyGenerator(self.env)

    if tg.validate():
      DOMAIN_SOURCE_TYPE = self.env.getEnvOrDef("DOMAIN_SOURCE_TYPE", None)
      if DOMAIN_SOURCE_TYPE != "FromModel":
        SitConfigGenerator(self.env).generate()
      BootPropertiesGenerator(self.env).generate()
      UserConfigAndKeyGenerator(self.env).generate()

      if DOMAIN_SOURCE_TYPE == "FromModel":
        trace("cfgmap write primordial_domain")
        MII_PrimordialDomainGenerator(self.env).generate()
        trace("cfgmap write domain zip")
        MII_DomainConfigGenerator(self.env).generate()
        trace("cfgmap write merged model")
        MII_IntrospectCMFileGenerator(self.env, self.env.MII_MERGED_MODEL_FILE,
                                      self.env.DOMAIN_HOME +"/wlsdeploy/domain_model.json").generate()
        trace("cfgmap write md5 image")
        MII_IntrospectCMFileGenerator(self.env, self.env.MII_INVENTORY_IMAGE_MD5, '/tmp/inventory_image.md5').generate()
        trace("cfgmap write md5 cm")
        MII_IntrospectCMFileGenerator(self.env, self.env.MII_INVENTORY_CM_MD5, '/tmp/inventory_cm.md5').generate()
        trace("cfgmap write wls version")
        MII_IntrospectCMFileGenerator(self.env, self.env.WLS_VERSION, '/tmp/wls_version').generate()
        trace("cfgmap write jdk_path")
        MII_IntrospectCMFileGenerator(self.env, self.env.JDK_PATH, '/tmp/jdk_path').generate()
        trace("cfgmap write md5 secrets")
        MII_IntrospectCMFileGenerator(self.env, self.env.MII_SECRETS_AND_ENV_MD5, '/tmp/secrets_and_env.md5').generate()
        trace("cfgmap write model hash")
        # Must be called after MII_PrimordialDomainGenerator
        MII_IntrospectCMFileGenerator(self.env, self.env.MII_DOMAINZIP_HASH, '/tmp/domainzip_hash').generate()

      if self.isFromModelAndJRFDomain() or self.isInitializeDomainJRFOnPV():
        trace("cfgmap write JRF wallet")
        JRFOpssWalletFileGenerator(self.env).generate()


    CustomSitConfigIntrospector(self.env).generateAndValidate()

    # If the topology is invalid, the generated topology
    # file contains a list of one or more validation errors
    # instead of a topology.
  
    tg.generate()

  def isInitializeDomainJRFOnPV(self):
    if self.env.WDT_DOMAIN_TYPE == 'JRF' and self.env.INIT_DOMAIN_ON_PV is not None:
      return True
    else:
      return False

  def isFromModelAndJRFDomain(self):
    if self.env.DOMAIN_SOURCE_TYPE == 'FromModel' and self.env.WDT_DOMAIN_TYPE == 'JRF':
      return True
    else:
      return False

def getRealSSLListenPort(server, sslport):
  """
  Return the real listening port that will be used in runtime,
  which can be different than is reported by WLST off-line.

  The difference occurs when a user specifies 7002 in the model
  or wlst offline for a server template when creating the domain,
  which results in an empty entry or 7002 in the config.xml. When subsequently
  using wlst offline to read the domain, the mbean mistakenly
  returns 8100 but the actual listening port is 7002.

  If it is not a server template, then just return from the mbean.

  :param server:  server or server template
  :param sslport: sslport from wlst offline mbean
  :return: listening port
  """
  if server_template_sslports.has_key(server.getName()):
    configxml_ssl_port = server_template_sslports[server.getName()]
    if configxml_ssl_port is None or configxml_ssl_port == "7002":
      return 7002

  if sslport == 0:
    sslport = 7002

  return sslport

def getRealListenPort(template):
  """
  Return the real listening port that will be used in runtime,
  which can be different than is reported by WLST off-line.

  The difference occurs when a user specifies 7001 in the model
  or wlst offline for a server template when creating the domain,
  which results in an empty entry or 7001 entry in the config.xml. When subsequently
  using wlst offline to read the domain, the mbean mistakenly
  returns 7100 but the actual listening port is 7001.

  If it is not a server template, then just return from the mbean.

  :param server:  server or server template
  :return: listening port
  """
  if server_template_listening_ports.has_key(template.getName()):
    port = server_template_listening_ports[template.getName()]
    if port is None or port == "7001":
      return 7001

  port = template.getListenPort()
  # Probably don't need this - unlike NAP that can be 0.
  if port == 0:
    return 7001

  return port


# Derive the default value for SecureMode of a domain
def isSecureModeEnabledForDomain(domain):
  secureModeEnabled = False

  # Do not use domain.getSecurityConfiguration().getSecureMode()
  # it will result in cie error in MII domain created by ServerStartMode: secure
  # switched to use lsa() to avoid cie not exposing the function

  cd('/SecurityConfiguration/' + domain.getName())
  childs = ls(returnType='c', returnMap='true')
  if 'SecureMode' in childs:
    cd('SecureMode/NO_NAME_0')
    attributes = ls(returnType='a', returnMap='true')
    if attributes['SecureModeEnabled']:
      secureModeEnabled = True
  else:
    secureModeEnabled = domain.isProductionModeEnabled() and not LegalHelper.versionEarlierThan(domain.getDomainVersion(), "14.1.2.0")


  return secureModeEnabled

def isAdministrationPortEnabledForDomain(domain):
  administrationPortEnabled = false
  #"if domain.isSet('AdministrationPortEnabled'):" does not work in off-line WLST!
  # Go to the domain root
  cd('/')
  if isSet('AdministrationPortEnabled'):
    administrationPortEnabled = domain.isAdministrationPortEnabled()
  else:
    # AdministrationPortEnabled is not explicitly set so going with the default
    # Starting with 14.1.2.0, the domain's AdministrationPortEnabled default is derived from the domain's SecureMode
    administrationPortEnabled = isSecureModeEnabledForDomain(domain)
  return administrationPortEnabled

def isAdministrationPortEnabledForServer(server, domain, isServerTemplate=False):
  administrationPortEnabled = false
  #"if server.isSet('AdministrationPortEnabled'):" does not work in off-line WLST!
  cd('/')
  if isServerTemplate:
    cd('ServerTemplate')
  else:
    cd('Server')
  cd(server.getName())
  if isSet('AdministrationPortEnabled'):
    administrationPortEnabled = server.isAdministrationPortEnabled()
  else:
    administrationPortEnabled = isAdministrationPortEnabledForDomain(domain)
  return administrationPortEnabled

def getAdministrationPort(server, domain):
  port = server.getAdministrationPort()
  # In off-line WLST, the server's AdministrationPort default value is 0
  if port == 0:
    port = domain.getAdministrationPort()
  return port

def getNAPProtocol(nap, server, domain, is_server_template=False):
  protocol = nap.getProtocol()
  if len(server.getNetworkAccessPoints()) > 0:
    if is_server_template:
      cd('/ServerTemplate/' + server.getName() + '/NetworkAccessPoint/' + nap.getName())
    else:
      cd('/Server/' + server.getName() + '/NetworkAccessPoint/' + nap.getName())
    if not isSet('Protocol') and isSecureModeEnabledForDomain(domain):
      protocol = "t3s"
  return protocol

def isListenPortEnabledForServer(server, domain, is_server_template=False):
  enabled = server.isListenPortEnabled()
  if is_server_template:
    cd('/ServerTemplate')
  else:
    cd('/Server')
  cd(server.getName())
  if not isSet('ListenPortEnabled') and isSecureModeEnabledForDomain(domain):
    enabled = False
  return enabled

def isSSLListenPortEnabled(ssl, domain):
  enabled = False
  if ssl is not None:
    enabled = ssl.isEnabled()
  else:
    if isSecureModeEnabledForDomain(domain):
      enabled = True
  return enabled

def getSSLPortIfEnabled(server, domain, is_server_template=True):
  """
  return the SSL listen port if enabled -
    If SSL is enabled:
      If is_server_template is False then just return the SSL listen port from server mbean.
      If is_server_template is True then return the actual SSL listen port that it listens on.

    If SSL is not configured but domain has SecureMode enabled return 7002.
    If SSL is configured but explicitly disabled, return None.

  :param server: server or server template
  :param domain: domain mbean
  :return: SSL listen port
  """
  ssl = None
  ssl_listen_port = None
  try:
    # this can throw if SSL mbean not there
    ssl = server.getSSL()
    # this can throw if SSL mbean is there but enabled is false
    ssl.getListenPort()
    # this can throw if SSL mbean is there but enabled is false ??
    ssl.isEnabled()
  except:
    pass

  if ssl is not None and ssl.isEnabled():
    if not is_server_template:
      ssl_listen_port = ssl.getListenPort()
    else:
      ssl_listen_port = getRealSSLListenPort(server, ssl.getListenPort())
  elif ssl is None and isSecureModeEnabledForDomain(domain):
    ssl_listen_port = "7002"
  return ssl_listen_port

def get_server_template_listening_ports_from_configxml(config_xml):
  '''
  get_server_tempalate's listening port and ssl port from the config.xml
  :param config_xml:         full path to config.xml
  :return: dictionary of servertemplate ssl port and servertemplate listen port
  '''
  DOMTree = parse(config_xml)
  collection = DOMTree.documentElement

  templates = collection.getElementsByTagName("server-template")
  server_template_ssls = dict()
  server_template_ports = dict()

  # if port is not specified in config.xml, set to None

  for template in templates:
    sslport = None
    port = None
    if template.parentNode.nodeName != 'domain':
      continue
    template_name = template.getElementsByTagName('name')[0].firstChild.nodeValue
    # Get listen port
    listen_ports = template.getElementsByTagName('listen-port')

    for listen_port in listen_ports:
      if listen_port.parentNode.nodeName == 'server-template':
        port = listen_port.firstChild.nodeValue
        break
    server_template_ports[template_name] = port

    # Get ssl port
    ssls = template.getElementsByTagName('ssl')
    if len(ssls) > 0:
      ssl = ssls.item(0)
      listen_port = ssl.getElementsByTagName('listen-port')
      if len(listen_port) > 0:
        sslport = listen_port[0].firstChild.nodeValue
    server_template_ssls[template_name] = sslport

  return server_template_ssls, server_template_ports


def main(env):
  try:
    #  Needs to build the domain first


    env.open()
    try:
      env.addGeneratedFile(env.MII_DOMAIN_SECRET_MD5_FILE)
      DomainIntrospector(env).introspect()
      env.printGeneratedFiles()
      # Important: The following trace's wording must not be changed
      # without a corresponding change to DOMAIN_INTROSPECTION_COMPLETE
      # in the operator.
      trace("Domain introspection complete.")
    finally:
      env.close()
    exit(exitcode=0)
  except WLSTException, e:
    trace("SEVERE","Domain introspection failed with WLST exception: " + str(e))
    print e
    traceback.print_exc()
    dumpStack()
    exit(exitcode=1)
  except:
    trace("SEVERE","Domain introspection unexpectedly failed:")
    traceback.print_exc()
    dumpStack()
    exit(exitcode=1)

main(OfflineWlstEnv())

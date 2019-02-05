# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
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
import sys
import traceback
import inspect
import distutils.dir_util
import os
import shutil
import re
from datetime import datetime

# Include this script's current directory in the import path (so we can import traceUtils, etc.)
# sys.path.append('/weblogic-operator/scripts')

# Alternative way to dynamically get script's current directory
tmp_callerframerecord = inspect.stack()[0]    # 0 represents this line # 1 represents line at caller
tmp_info = inspect.getframeinfo(tmp_callerframerecord[0])
tmp_scriptdir=os.path.dirname(tmp_info[0])
sys.path.append(tmp_scriptdir)

from traceUtils import *

class OfflineWlstEnv(object):

  def open(self):

    # before doing anything, get each env var and verify it exists 

    self.DOMAIN_UID               = self.getEnv('DOMAIN_UID')
    self.DOMAIN_HOME              = self.getEnv('DOMAIN_HOME')
    self.LOG_HOME                 = self.getEnv('LOG_HOME')
    self.CREDENTIALS_SECRET_NAME  = self.getEnv('CREDENTIALS_SECRET_NAME')

    # initialize globals

    # The following 3 globals mush match prefix hard coded in startServer.sh
    self.CUSTOM_PREFIX_JDBC = 'Sit-Cfg-JDBC--'
    self.CUSTOM_PREFIX_JMS  = 'Sit-Cfg-JMS--'
    self.CUSTOM_PREFIX_WLDF = 'Sit-Cfg-WLDF--'
    self.CUSTOM_PREFIX_CFG  = 'Sit-Cfg-CFG--'

    self.INTROSPECT_HOME    = '/tmp/introspect/' + self.DOMAIN_UID
    self.TOPOLOGY_FILE      = self.INTROSPECT_HOME + '/topology.yaml'
    self.CM_FILE            = self.INTROSPECT_HOME + '/' + self.CUSTOM_PREFIX_CFG + 'introspector-situational-config.xml'
    self.BOOT_FILE          = self.INTROSPECT_HOME + '/boot.properties'
    self.USERCONFIG_FILE    = self.INTROSPECT_HOME + '/userConfigNodeManager.secure'
    self.USERKEY_FILE       = self.INTROSPECT_HOME + '/userKeyNodeManager.secure'

    # The following 4 env vars are for unit testing, their defaults are correct for production.
    self.CREDENTIALS_SECRET_PATH = self.getEnvOrDef('CREDENTIALS_SECRET_PATH', '/weblogic-operator/secrets')
    self.CUSTOM_SECRET_ROOT      = self.getEnvOrDef('CUSTOM_SECRET_ROOT', '/weblogic-operator/config-overrides-secrets')
    self.CUSTOM_SITCFG_PATH      = self.getEnvOrDef('CUSTOM_SITCFG_PATH', '/weblogic-operator/config-overrides')
    self.NM_HOST                 = self.getEnvOrDef('NM_HOST', 'localhost')

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

    # load domain home into WLST

    trace("About to load domain from "+self.getDomainHome())
    readDomain(self.getDomainHome())
    self.domain = cmo
    self.DOMAIN_NAME = self.getDomain().getName()

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

  def addError(self, error):
    self.errors.append(error)

  def getErrors(self):
    return self.errors

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
    print 
    print ">>> ",path
    print self.readFile(path)
    print ">>> EOF"
    print

  def getEnv(self, name):
    val = os.getenv(name)
    if val == None or val == "null":
      trace("ERROR: Env var "+name+" not set.")
      sys.exit(1)
    return val

  def getEnvOrDef(self, name, deflt):
    val = os.getenv(name)
    if val == None or val == "null":
      return deflt
    return val

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
    self.validateClusters()
    self.validateServerCustomChannelName()
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
  # when there are no 'real' DynamicServers.  Exception looks like:
  #     at com.sun.proxy.$Proxy46.getDynamicServers(Unknown Source)
  #     at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
  #     at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
  #     at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
  #     at java.lang.reflect.Method.invoke(Method.java:498)
  def getDynamicServersWA(self,cluster):
    try:
      ret = cluster.getDynamicServers()
    except:
      trace("Ignoring getDynamicServers() exception, this is expected.")
      ret = None
    return ret


  def validateAdminServer(self):
    adminServerName = self.env.getDomain().getAdminServerName()
    if adminServerName == None:
      addError("The admin server name is null.")
      return
    adminServer = None
    for server in self.env.getDomain().getServers():
      if adminServerName == server.getName():
        adminServer = server
    if adminServer is None:
      addError("The admin server '" + adminServerName + "' does not exist.")
      return
    cluster = adminServer.getCluster()
    if cluster != None:
      self.addError("The admin server " + self.name(adminServer) + " belongs to the cluster " + self.name(cluster) + ".")

  def validateClusters(self):
    for cluster in self.env.getDomain().getClusters():
      self.validateCluster(cluster)

  def validateCluster(self, cluster):
    if self.getDynamicServersWA(cluster) is None:
      self.validateNonDynamicCluster(cluster)
    else:
      self.validateDynamicCluster(cluster)

  def validateNonDynamicCluster(self, cluster):
    self.validateNonDynamicClusterReferencedByAtLeastOneServer(cluster)
    self.validateNonDynamicClusterNotReferencedByAnyServerTemplates(cluster)
    self.validateNonDynamicClusterServersHaveSameListenPort(cluster)
    self.validateNonDynamicClusterServerHaveSameCustomChannels(cluster)

  def validateNonDynamicClusterReferencedByAtLeastOneServer(self, cluster):
    for server in self.env.getDomain().getServers():
      if server.getCluster() is cluster:
        return
    self.addError("The non-dynamic cluster " + self.name(cluster) + " is not referenced by any servers.")

  def validateNonDynamicClusterNotReferencedByAnyServerTemplates(self, cluster):
    for template in self.env.getDomain().getServerTemplates():
      if template.getCluster() is cluster:
        self.addError("The non-dynamic cluster " + self.name(cluster) + " is referenced by the server template " + self.name(template) + ".")

  LISTEN_PORT = 'listen port'
  LISTEN_PORT_ENABLED = 'listen port enabled'
  SSL_LISTEN_PORT = 'ssl listen port'
  SSL_LISTEN_PORT_ENABLED = 'ssl listen port enabled'
  ADMIN_LISTEN_PORT = 'admin listen port'
  ADMIN_LISTEN_PORT_ENABLED = 'admin listen port enabled'

  def getServerClusterPortPropertyValue(server, clusterListenPortProperty):
    sslListenPort = None
    if server.getSSL() != None:
      sslListenPort = server.getSSL().getListenPort()
    sslListenPortEnabled = None
    if server.getSSL()!= None:
      sslListenPortEnabled = server.getSSL().isListenPortEnabled()
    return {
             LISTEN_PORT: server.getListenPort(),
             LISTEN_PORT_ENABLED: server.isListenPortEnabled(),
             SSL_LISTEN_PORT: sslListenPort,
             SSL_LISTEN_PORT_ENABLED: sslListenPortEnabled,
             ADMIN_LISTEN_PORT: server.getAdministrationPort(),
             ADMIN_LISTEN_PORT_ENABLED: server.isAdministrationPortEnabled()
     }[clusterListenPortProperty]

  def validateNonDynamicClusterServersHaveSameListenPort(self, cluster):
    firstServer = None
    firstListenPort = None
    firstListenPortEnabled = None
    firstSslListenPort = None
    firstSslListenPortEnabled = None
    firstAdminPort = None
    firstAdminPortEnabled = None
    for server in self.env.getDomain().getServers():
      if cluster is server.getCluster():
        listenPort = server.getListenPort()
        listenPortEnabled = server.isListenPortEnabled()
        ssl = server.getSSL()
        sslListenPort = None
        sslListenPortEnabled = None
        if ssl is not None:
              sslListenPort = ssl.getListenPort()
              sslListenPortEnabled = ssl.isEnabled()
        adminPort = server.getAdministrationPort()
        adminPortEnabled = server.isAdministrationPortEnabled()
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
            self.addError("The non-dynamic cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + "'s listen port is " + str(firstListenPort) + " but its server " + self.name(server) + "'s listen port is " + str(listenPort) + ". All ports for the same channel in a cluster must be the same.")
          if listenPortEnabled != firstListenPortEnabled:
            self.addError("The non-dynamic cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + " has listen port enabled: " + self.booleanToString(firstListenPortEnabled) + " but its server " + self.name(server) + "'s listen port enabled: " + self.booleanToString(listenPortEnabled) + ".  Channels in a cluster must be either all enabled or disabled.")
          if sslListenPort != firstSslListenPort:
             self.addError("The non-dynamic cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + "'s ssl listen port is " + str(firstSslListenPort) + " but its server " + self.name(server) + "'s ssl listen port is " + str(sslListenPort) + ".  All ports for the same channel in a cluster must be the same.")
          if sslListenPortEnabled != firstSslListenPortEnabled:
            self.addError("The non-dynamic cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + " has ssl listen port enabled: " + self.booleanToString(firstSslListenPortEnabled) + " but its server " + self.name(server) + "'s ssl listen port enabled: " + self.booleanToString(sslListenPortEnabled) + ".  Channels in a cluster must be either all enabled or disabled.")
          if adminPort != firstAdminPort:
            self.addError("The non-dynamic cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + "'s ssl listen port is " + str(firstAdminPort) + " but its server " + self.name(server) + "'s ssl listen port is " + str(adminPort) + ".  All ports for the same channel in a cluster must be the same.")
          if adminPortEnabled != firstAdminPortEnabled:
            self.addError("The non-dynamic cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + " has ssl listen port enabled: " + self.booleanToString(firstAdminPortEnabled) + " but its server " + self.name(server) + "'s ssl listen port enabled: " + self.booleanToString(adminPortEnabled) + ".  Channels in a cluster must be either all enabled or disabled.")



  def validateClusterServersListenPortProperty(self, cluster, errorMsg, clusterListenPortProperty):
    firstServer = None
    firstListenPortProperty = None
    for server in self.env.getDomain().getServers():
      if cluster is server.getCluster():
        listenPortProperty = getServerClusterPortPropertyValue(server, clusterListenPortProperty)
        if firstServer is None:
          firstServer = server
          firstListenPortProperty = listenPortProperty
        else:
          if listenPortProperty != firstListenPortProperty:
            self.addError(errorMsg.substitute(cluster=self.name(cluster), server1=self.name(firstServer), property=clusterListenPortProperty, value1=str(firstListenPortProperty), server2=self.name(server), value2=str(firstListenPortProperty)))
            return

  def validateNonDynamicClusterServerHaveSameCustomChannels(self, cluster):
     firstServer = None
     serverNap = {}
     for server in self.env.getDomain().getServers():
       if cluster is server.getCluster():
         if firstServer is None:
           for nap in server.getNetworkAccessPoints():
             serverNap[nap.getName()] = nap.getProtocol() + "~" + str(nap.getListenPort());
           firstServer = server
         else:
           naps = server.getNetworkAccessPoints()
           if len(naps) != len(serverNap):
             self.addError("The non-dynamic cluster " + self.name(cluster) + " has mismatched number of network access points in servers " + self.name(firstServer) + " and " + self.name(server) + ". All network access points in a cluster must be the same.")
             return
           else:
             for nap in naps:
               if nap.getName() in serverNap:
                 if serverNap[nap.getName()] != nap.getProtocol() + "~" + str(nap.getListenPort()):
                   self.addError("The non-dynamic cluster " + self.name(cluster) + " has mismatched network access point " + self.name(nap) + " in servers " + self.name(firstServer) + " and " + self.name(server) + ". All network access points in a cluster must be the same.")
                   return
               else:
                 self.addError("The non-dynamic cluster " + self.name(cluster) + " has mismatched network access point " + self.name(nap) + " in servers " + self.name(firstServer) + " and " + self.name(server) + ". All network access points in a cluster must be the same.")
                 return


  def validateDynamicCluster(self, cluster):
    self.validateDynamicClusterReferencedByOneServerTemplate(cluster)
    self.validateDynamicClusterDynamicServersDoNotUseCalculatedListenPorts(cluster)
    self.validateDynamicClusterNotReferencedByAnyServers(cluster)

  def validateDynamicClusterReferencedByOneServerTemplate(self, cluster):
    server_template=None
    for template in self.env.getDomain().getServerTemplates():
      if template.getCluster() is cluster:
        if server_template is None:
          server_template = template
        else:
          if server_template is not None:
            self.addError("The dynamic cluster " + self.name(cluster) + " is referenced the server template " + self.name(server_template) + " and the server template " + self.name(template) + ".")
            return
    if server_template is None:
      self.addError("The dynamic cluster " + self.name(cluster) + "' is not referenced by any server template.")

  def validateDynamicClusterNotReferencedByAnyServers(self, cluster):
    for server in self.env.getDomain().getServers():
      if server.getCluster() is cluster:
        self.addError("The dynamic cluster " + self.name(cluster) + " is referenced by the server " + self.name(server) + ".")

  def validateDynamicClusterDynamicServersDoNotUseCalculatedListenPorts(self, cluster):
    if cluster.getDynamicServers().isCalculatedListenPorts() == True:
      self.addError("The dynamic cluster " + self.name(cluster) + "'s dynamic servers use calculated listen ports.")

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
      if self.getDynamicServersWA(cluster) is None:
        rtn.append(cluster)
    return rtn

  def addConfiguredCluster(self, cluster):
    self.writeln("- name: " + self.name(cluster))
    dynamicServers = self.getDynamicServersWA(cluster)
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
    name=self.name(dynamicServer)
    self.writeln("name: " + name)
    self.writeln("serverTemplateName: " + self.quote(dynamicServer.getServerTemplate().getName()))
    self.writeln("calculatedListenPorts: " + str(dynamicServer.isCalculatedListenPorts()))
    self.writeln("serverNamePrefix: " + self.quote(dynamicServer.getServerNamePrefix()))
    self.writeln("dynamicClusterSize: " + str(dynamicServer.getDynamicClusterSize()))
    self.writeln("maxDynamicClusterSize: " + str(dynamicServer.getMaxDynamicClusterSize()))

  def getClusteredServers(self, cluster):
    rtn = []
    for server in self.env.getDomain().getServers():
      if server.getCluster() is cluster:
        rtn.append(server)
    return rtn

  def addServer(self, server):
    name=self.name(server)
    self.writeln("- name: " + name)
    if server.isListenPortEnabled():
      self.writeln("  listenPort: " + str(server.getListenPort()))
    self.writeln("  listenAddress: " + self.quote(self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + server.getName())))
    if server.isAdministrationPortEnabled():
      self.writeln("  adminPort: " + str(server.getAdministrationPort()))
    self.addSSL(server)
    self.addNetworkAccessPoints(server)

  def addSSL(self, server):
    ssl = server.getSSL()
    if ssl is not None and ssl.isEnabled():
      self.indent()
      self.writeln("sslListenPort: " + str(ssl.getListenPort()))
      self.undent()

  def addServerTemplates(self):
    serverTemplates = self.env.getDomain().getServerTemplates()
    if len(serverTemplates) == 0:
      return
    self.writeln("serverTemplates:")
    self.indent()
    for serverTemplate in serverTemplates:
      self.addServerTemplate(serverTemplate)
    self.undent()

  def addServerTemplate(self, serverTemplate):
    self.addServer(serverTemplate)
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
      if self.getDynamicServersWA(cluster) is not None:
        rtn.append(cluster)
    return rtn

  def addDynamicCluster(self, cluster):
    self.writeln(self.name(cluster) + ":")
    self.indent()
    template = self.findDynamicClusterServerTemplate(cluster)
    dyn_servers = cluster.getDynamicServers()
    self.writeln("port: " + str(template.getListenPort()))
    self.writeln("maxServers: " + str(dyn_servers.getDynamicClusterSize()))
    self.writeln("baseServerName: " + self.quote(dyn_servers.getServerNamePrefix()))
    self.undent()

  def findDynamicClusterServerTemplate(self, cluster):
    for template in cmo.getServerTemplates():
      if template.getCluster() is cluster:
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
      if server.getCluster() is None:
        self.addServer(server)
    self.undent()

  def addNetworkAccessPoints(self, server):
    naps = server.getNetworkAccessPoints()
    if len(naps) == 0:
      return
    self.writeln("  networkAccessPoints:")
    self.indent()
    for nap in naps:
      self.addNetworkAccessPoint(nap)
    self.undent()

  def addNetworkAccessPoint(self, nap):
    name=self.name(nap)
    self.writeln("  - name: " + name)
    self.writeln("    protocol: " + self.quote(nap.getProtocol()))
    self.writeln("    listenPort: " + str(nap.getListenPort()))
    self.writeln("    publicPort: " + str(nap.getPublicPort()))

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
    self.customizeServers()
    self.customizeServerTemplates()
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

  def customizeServers(self):
    for server in self.env.getDomain().getServers():
      self.customizeServer(server)

  def writeListenAddress(self, originalValue, newValue):
    repVerb="\"replace\""
    if originalValue is None or len(originalValue)==0:
      repVerb="\"add\""
    self.writeln("<d:listen-address f:combine-mode=" + repVerb + ">" + newValue + "</d:listen-address>")

  def customizeServer(self, server):
    name=server.getName()
    listen_address=self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + name)
    self.writeln("<d:server>")
    self.indent()
    self.writeln("<d:name>" + name + "</d:name>")
    self.customizeLog(name, server, false)
    self.writeListenAddress(server.getListenAddress(),listen_address)
    self.customizeNetworkAccessPoints(server,listen_address)
    self.undent()
    self.writeln("</d:server>")

  def customizeServerTemplates(self):
    for template in self.env.getDomain().getServerTemplates():
      self.customizeServerTemplate(template)

  def customizeServerTemplate(self, template):
    name=template.getName()
    server_name_prefix=template.getCluster().getDynamicServers().getServerNamePrefix()
    listen_address=self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + server_name_prefix + "${id}")
    self.writeln("<d:server-template>")
    self.indent()
    self.writeln("<d:name>" + name + "</d:name>")
    self.customizeLog(server_name_prefix + "${id}", template, false)
    self.writeListenAddress(template.getListenAddress(),listen_address)
    self.customizeNetworkAccessPoints(template,listen_address)
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
    if not (nap.getListenAddress() is None) and len(nap.getListenAddress()) > 0:
      self.writeln("<d:network-access-point>")
      self.indent()
      self.writeln("<d:name>" + nap_name + "</d:name>")
      self.writeListenAddress("force a replace",listen_address)
      self.undent()
      self.writeln("</d:network-access-point>")

  def customizeLog(self, name, bean, isDomainBean):
    logs_dir = self.env.getDomainLogHome()
    if logs_dir is None or len(logs_dir) == 0:
      return

    logaction=''
    fileaction=''
    if bean.getLog() is None:
      if not isDomainBean:
        # don't know why, but don't need to "add" a missing domain log bean, and adding it causes trouble
        logaction=' f:combine-mode="add"'
      fileaction=' f:combine-mode="add"'
    else:
      if bean.getLog().getFileName() is None:
        fileaction=' f:combine-mode="add"'
      else:
        fileaction=' f:combine-mode="replace"'

    self.writeln("<d:log" + logaction + ">")
    self.indent()
    self.writeln("<d:file-name" + fileaction + ">" + logs_dir + "/" + name + ".log</d:file-name>")
    self.undent()
    self.writeln("</d:log>")

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

    trace("available macros: '" + self.macroStr + "'")

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
        self.env.AddError(
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
      SitConfigGenerator(self.env).generate()
      BootPropertiesGenerator(self.env).generate()
      UserConfigAndKeyGenerator(self.env).generate()

    CustomSitConfigIntrospector(self.env).generateAndValidate()

    # If the topology is invalid, the generated topology
    # file contains a list of one or more validation errors
    # instead of a topology.
  
    tg.generate()


def main(env):
  try:
    env.open()
    try:
      DomainIntrospector(env).introspect()
      env.printGeneratedFiles()
      trace("Domain introspection complete.")
    finally:
      env.close()
    exit(exitcode=0)
  except:
    trace("Domain introspection unexpectedly failed:")
    traceback.print_exc()
    exit(exitcode=1)

main(OfflineWlstEnv())

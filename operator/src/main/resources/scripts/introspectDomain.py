# Copyright 2017, 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
#
# ------------
# Description:
# ------------
#
#   This code reads the configuration in a WL domain's domain home, and generates
#   multiple files that are copied to stdout.  It also checks whether the domain
#   configuration is 'valid' (suitable for running in k8s).
#
#   This code is used by the operator to introspect and validate an arbitrary
#   WL domain before its pods are started.  It generates topology information that's
#   useful for running the domain, setting up its networking, and for overriding
#   specific parts of its configuration so that it can run in k8s.
# 
#   The configuration overrides are specified via situational config file(s), and 
#   include listen addresses, log file locations, etc.  Additional information
#   is provided in other files -- including encrypted credentials, domain
#   topology (server names, etc), and any validation warnings/errors.
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
#   The following env vars:
#     DOMAIN_UID  - completely unique id for this domain
#     DOMAIN_HOME - path for the domain configuration
#     LOG_HOME    - path to override WebLogic server log locations
#
# ---------------------------------
# Outputs (files copied to stdout):
# ---------------------------------
#
#   topology.yaml                  -- Domain configuration summary for operator (server names, etc).
#                                           -and/or-
#                                     Domain validation warnings/errors. 
#   situational-config.xml         -- Overrides for domain configuration (listen addresses, etc).
#   boot.properties                -- Encoded credentials for starting WL.
#   userConfigNodeManager.secure   -- Encoded credentials for starting NM in a WL pod.
#   userKeyNodeManager.secure'     -- Encoded credentials for starting NM in a WL pod.
#
# Note:
#
#   This code partly depends on a node manager so that we can use it to encrypt 
#   the username and password and put them into files that can be used to connect
#   to the node manager later in the server pods (so that the server pods don't
#   have to mount the secret containing the username and password).
#
# TBD:
#
#   It's not clear if this script supports mixed clusters. It seems to assume
#   a cluster with a dynamic server means the cluster is purely dynamic.
#


import base64
import sys
import traceback
import inspect
import distutils.dir_util
import os
import shutil
from datetime import datetime

# Include this script's current directory in the import path (so we can import traceUtils, etc.)
sys.path.append('/weblogic-operator/scripts')

# Alternative way to dynamically get script's current directory
#tmp_callerframerecord = inspect.stack()[0]    # 0 represents this line # 1 represents line at caller
#tmp_info = inspect.getframeinfo(tmp_callerframerecord[0])
#tmp_scriptdir=os.path.dirname(tmp_info[0])
#sys.path.append(tmp_scriptdir)

from traceUtils import *

class OfflineWlstEnv(object):

  def open(self):

    # before doing anything, get each env var and verify it exists 

    self.DOMAIN_UID         = self.getEnv('DOMAIN_UID')
    self.DOMAIN_HOME        = self.getEnv('DOMAIN_HOME')
    self.LOG_HOME           = self.getEnv('LOG_HOME')

    # initialize globals

    self.INTROSPECT_HOME    = '/tmp/introspect/' + self.DOMAIN_UID
    self.TOPOLOGY_FILE      = self.INTROSPECT_HOME + '/topology.yaml'
    self.CM_FILE            = self.INTROSPECT_HOME + '/situational-config.xml'
    self.BOOT_FILE          = self.INTROSPECT_HOME + '/boot.properties'
    self.USERCONFIG_FILE    = self.INTROSPECT_HOME + '/userConfigNodeManager.secure'
    self.USERKEY_FILE       = self.INTROSPECT_HOME + '/userKeyNodeManager.secure'

    # maintain a list of generated files that we print once introspection completes

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

  def close(self):
    closeDomain()

  def getDomain(self):
    return self.domain

  def getIntrospectHome():
    return self.INTROSPECT_HOME

  def getDomainUID(self):
    return self.DOMAIN_UID

  def getDomainHome(self):
    return self.DOMAIN_HOME

  def getDomainLogHome(self):
    return self.LOG_HOME

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

  def readAndEncryptSecret(self, name):
    cleartext = self.readSecret(name)
    return self.env.encrypt(cleartext)

  def readSecret(self, name):
    path = "/weblogic-operator/secrets/" + name
    file = open(path, 'r')
    cleartext = file.read()
    file.close()
    return cleartext

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
    self.errors = []

  def validate(self):
    self.validateAdminServer()
    self.validateClusters()
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

  def validateNonDynamicClusterReferencedByAtLeastOneServer(self, cluster):
    for server in self.env.getDomain().getServers():
      if server.getCluster() is cluster:
        return
    self.addError("The non-dynamic cluster " + self.name(cluster) + " is not referenced by any servers.")

  def validateNonDynamicClusterNotReferencedByAnyServerTemplates(self, cluster):
    for template in self.env.getDomain().getServerTemplates():
      if template.getCluster() is cluster:
        self.addError("The non-dynamic cluster " + self.name(cluster) + " is referenced by the server template " + self.name(template) + ".")

  def validateNonDynamicClusterServersHaveSameListenPort(self, cluster):
    firstServer = None
    firstPort = None
    for server in self.env.getDomain().getServers():
      if cluster is server.getCluster():
        port = server.getListenPort()
        if firstServer is None:
          firstServer = server
          firstPort = port
        else:
          if port != firstPort:
            self.addError("The non-dynamic cluster " + self.name(cluster) + "'s server " + self.name(firstServer) + "'s listen port is " + str(firstPort) + " but its server " + self.name(server) + "'s listen port is " + str(port) + ".")
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

  def isValid(self):
    return len(self.errors) == 0

  def addError(self, error):
    self.errors.append(error)

  def reportErrors(self):
    self.writeln("domainValid: false")
    self.writeln("validationErrors:")
    for error in self.errors:
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
    self.addNonClusteredServers()
    self.undent()

  def addConfiguredClusters(self):
    clusters = self.getConfiguredClusters()
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
    servers = self.getClusteredServers(cluster)
    self.indent();
    self.writeln("servers:")
    self.indent()
    for server in servers:
      self.addClusteredServer(cluster, server)
    self.undent()
    self.undent()

  def getClusteredServers(self, cluster):
    rtn = []
    for server in self.env.getDomain().getServers():
      if server.getCluster() is cluster:
        rtn.append(server)
    return rtn

  def addClusteredServer(self, cluster, server):
    name=self.name(server)
    self.writeln("- name: " + name)
    self.writeln("  listenPort: " + str(server.getListenPort()))
    self.writeln("  listenAddress: " + self.quote(self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + server.getName())))
    self.addNetworkAccessPoints(server)

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
        self.addNonClusteredServer(server)
    self.undent()

  def addNonClusteredServer(self, server):
    name=self.name(server)
    self.writeln("- name: " + name)
    self.writeln("  listenPort: " + str(server.getListenPort()))
    self.writeln("  listenAddress: " + self.quote(self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + server.getName())))
    self.addNetworkAccessPoints(server)

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
    self.writeln("username=" + self.readAndEncryptSecret("username"))
    self.writeln("password=" + self.readAndEncryptSecret("password"))

class UserConfigAndKeyGenerator(Generator):

  def __init__(self, env):
    Generator.__init__(self, env, env.USERKEY_FILE)

  def generate(self):
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
    username = self.readSecret("username")
    password = self.readSecret("password")
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
    self.writeln("<s:expiration> 2999-07-16T19:20+01:00 </s:expiration>")
    self.customizeNodeManagerCreds()
    self.customizeDomainLogPath()
    self.customizeServers()
    self.customizeServerTemplates()
    self.undent()
    self.writeln("</d:domain>")

  def customizeNodeManagerCreds(self):
    admin_username = self.readSecret('username')
    admin_password = self.readAndEncryptSecret('password')
    self.writeln("<d:security-configuration>")
    self.indent()
    self.writeln("<d:node-manager-user-name f:combine-mode=\"replace\">" + admin_username + "</d:node-manager-user-name>")
    self.writeln("<d:node-manager-password-encrypted f:combine-mode=\"replace\">" + admin_password + "</d:node-manager-password-encrypted>")
    self.undent()
    self.writeln("</d:security-configuration>")

  def customizeDomainLogPath(self):
    self.customizeLog(self.env.getDomain().getName())

  def customizeServers(self):
    for server in self.env.getDomain().getServers():
      self.customizeServer(server)

  def customizeServer(self, server):
    name=server.getName()
    listen_address=self.env.toDNS1123Legal(self.env.getDomainUID() + "-" + name)
    self.writeln("<d:server>")
    self.indent()
    self.writeln("<d:name>" + name + "</d:name>")
    self.writeln("<d:listen-address f:combine-mode=\"replace\">" + listen_address + "</d:listen-address>")
    self.customizeLog(name)
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
    #TBD test dynamic cluster mgd server
    self.writeln("<d:listen-address f:combine-mode=\"replace\">" + listen_address + "</d:listen-address>")
    self.customizeLog(server_name_prefix + "${id}")
    self.undent()
    self.writeln("</d:server-template>")

  def customizeLog(self, name):
    logs_dir = self.env.getDomainLogHome()
    if logs_dir is not None:
      self.writeln("<d:log f:combine-mode=\"replace\">")
      self.indent()
      self.writeln("<d:file-name>" + logs_dir + "/" + name + ".log</d:file-name>")
      self.undent()
      self.writeln("</d:log>")

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

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Class that mirrors create-weblogic-domain-inputs.yaml
 *
 * Used to parse create-weblogic-domain-inputs.yaml into java
 * and convert java to create-weblogic-domain-inputs.yaml
 *
 * Note: use strings to represent params that must be ints or booleans at runtime
 * so that we can test more invalid input options (e.g. missing value, not int value)
 *
 * Note: initialize to empty strings and change nulls to empty strings
 * so that when this is written out to a yaml file, the files don't
 * include the literal "null" string. 
 */
public class CreateDomainInputs {

  public static final String LOAD_BALANCER_NONE = "NONE";
  public static final String LOAD_BALANCER_TRAEFIK = "TRAEFIK";
  public static final String LOAD_BALANCER_APACHE = "APACHE";
  public static final String STORAGE_TYPE_HOST_PATH = "HOST_PATH";
  public static final String STORAGE_TYPE_NFS = "NFS";
  public static final String STORAGE_RECLAIM_POLICY_RETAIN = "Retain";
  public static final String STORAGE_RECLAIM_POLICY_DELETE = "Delete";
  public static final String STORAGE_RECLAIM_POLICY_RECYCLE = "Recycle";
  public static final String STARTUP_CONTROL_NONE = "NONE";
  public static final String STARTUP_CONTROL_ALL = "ALL";
  public static final String STARTUP_CONTROL_ADMIN = "ADMIN";
  public static final String STARTUP_CONTROL_SPECIFIED = "SPECIFIED";
  public static final String STARTUP_CONTROL_AUTO = "AUTO";
  public static final String CLUSTER_TYPE_CONFIGURED = "CONFIGURED";
  public static final String CLUSTER_TYPE_DYNAMIC = "DYNAMIC";

  private static final String DEFAULT_INPUTS = "../kubernetes/create-weblogic-domain-inputs.yaml";

  private String adminPort = "";
  private String adminServerName = "";
  private String domainName = "";
  private String domainUID = "";
  private String startupControl = "";
  private String clusterName = "";
  private String clusterType = "";
  private String configuredManagedServerCount = "";
  private String initialManagedServerReplicas = "";
  private String managedServerNameBase = "";
  private String managedServerPort = "";
  private String weblogicDomainStorageReclaimPolicy = "";
  private String weblogicDomainStorageNFSServer = "";
  private String weblogicDomainStoragePath = "";
  private String weblogicDomainStorageSize = "";
  private String weblogicDomainStorageType = "";
  private String productionModeEnabled = "";
  private String weblogicCredentialsSecretName = "";
  private String weblogicImagePullSecretName = "";
  private String t3PublicAddress = "";
  private String t3ChannelPort = "";
  private String exposeAdminT3Channel = "";
  private String adminNodePort = "";
  private String exposeAdminNodePort = "";
  private String namespace = "";
  private String loadBalancer = "";
  private String loadBalancerWebPort = "";
  private String loadBalancerDashboardPort = "";
  private String loadBalancerVolumePath = "";
  private String loadBalancerAppPrepath = "";
  private String javaOptions = "";
  private String version = "";

  public static CreateDomainInputs newInputs() throws Exception {
    return
      readDefaultInputsFile()
        .adminNodePort("30702")
        .adminPort("7002")
        .adminServerName("TestAdminServer")
        .clusterName("TestCluster")
        .clusterType(CLUSTER_TYPE_DYNAMIC)
        .domainName("TestDomain")
        .domainUID("test-domain-uid")
        .javaOptions("TestJavaOptions")
        .loadBalancerDashboardPort("31315")
        .loadBalancerWebPort("31305")
        .configuredManagedServerCount("4")
        .managedServerNameBase("TestManagedServer")
        .managedServerPort("8002")
        .initialManagedServerReplicas("3")
        .weblogicDomainStorageNFSServer("TestDomainStorageNFSServer")
        .namespace("test-domain-namespace")
        .weblogicDomainStoragePath("TestDomainStoragePath")
        .weblogicDomainStorageSize("20Gi")
        .productionModeEnabled("false")
        .weblogicCredentialsSecretName("test-weblogic-credentials")
        .startupControl(STARTUP_CONTROL_ALL)
        .t3PublicAddress("TestT3PublicAddress")
        .t3ChannelPort("30013");
  }

  public static CreateDomainInputs readDefaultInputsFile() throws Exception {
    return readInputsYamlFile(defaultInputsPath());
  }

  public static CreateDomainInputs readInputsYamlFile(Path path) throws Exception {
    Reader r = Files.newBufferedReader(path, Charset.forName("UTF-8"));
    return (CreateDomainInputs)newYaml().loadAs(r, CreateDomainInputs.class);
  }

  private static Path defaultInputsPath() {
    return FileSystems.getDefault().getPath(DEFAULT_INPUTS);
  }

  public String getWeblogicDomainStorageClass() {
    return getDomainUID() + "-weblogic-domain-storage-class";
  }

  public String getWeblogicDomainPersistentVolumeName() {
    return getDomainUID() + "-weblogic-domain-pv";
  }

  public String getWeblogicDomainPersistentVolumeClaimName() {
    return getDomainUID() + "-weblogic-domain-pvc";
  }

  public String getAdminPort() {
    return adminPort;
  }

  public void setAdminPort(String adminPort) {
    this.adminPort = convertNullToEmptyString(adminPort);
  }

  public CreateDomainInputs adminPort(String adminPort) {
    setAdminPort(adminPort);
    return this;
  }

  public String getAdminServerName() {
    return adminServerName;
  }

  public void setAdminServerName(String adminServerName) {
    this.adminServerName = convertNullToEmptyString(adminServerName);
  }

  public CreateDomainInputs adminServerName(String adminServerName) {
    setAdminServerName(adminServerName);
    return this;
  }

  public String getDomainName() {
    return domainName;
  }

  public void setDomainName(String domainName) {
    this.domainName = convertNullToEmptyString(domainName);
  }

  public CreateDomainInputs domainName(String domainName) {
    setDomainName(domainName);
    return this;
  }

  public String getDomainUID() {
    return domainUID;
  }

  public void setDomainUID(String domainUID) {
    this.domainUID = convertNullToEmptyString(domainUID);
  }

  public CreateDomainInputs domainUID(String domainUID) {
    setDomainUID(domainUID);
    return this;
  }

  public String getStartupControl() {
    return startupControl;
  }

  public void setStartupControl(String startupControl) {
    this.startupControl = convertNullToEmptyString(startupControl);
  }

  public CreateDomainInputs startupControl(String startupControl) {
    setStartupControl(startupControl);
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = convertNullToEmptyString(clusterName);
  }

  public CreateDomainInputs clusterName(String clusterName) {
    setClusterName(clusterName);
    return this;
  }

  public String getClusterType() {
    return clusterType;
  }

  public void setClusterType(String clusterType) {
    this.clusterType = convertNullToEmptyString(clusterType);
  }

  public CreateDomainInputs clusterType(String clusterType) {
    setClusterType(clusterType);
    return this;
  }

  public String getConfiguredManagedServerCount() {
    return configuredManagedServerCount;
  }

  public void setConfiguredManagedServerCount(String configuredManagedServerCount) {
    this.configuredManagedServerCount = convertNullToEmptyString(configuredManagedServerCount);
  }

  public CreateDomainInputs configuredManagedServerCount(String configuredManagedServerCount) {
    setConfiguredManagedServerCount(configuredManagedServerCount);
    return this;
  }

  public String getInitialManagedServerReplicas() {
    return initialManagedServerReplicas;
  }

  public void setInitialManagedServerReplicas(String initialManagedServerReplicas) {
    this.initialManagedServerReplicas = convertNullToEmptyString(initialManagedServerReplicas);
  }

  public CreateDomainInputs initialManagedServerReplicas(String initialManagedServerReplicas) {
    setInitialManagedServerReplicas(initialManagedServerReplicas);
    return this;
  }

  public String getManagedServerNameBase() {
    return managedServerNameBase;
  }

  public void setManagedServerNameBase(String managedServerNameBase) {
    this.managedServerNameBase = convertNullToEmptyString(managedServerNameBase);
  }

  public CreateDomainInputs managedServerNameBase(String managedServerNameBase) {
    setManagedServerNameBase(managedServerNameBase);
    return this;
  }

  public String getManagedServerPort() {
    return managedServerPort;
  }

  public void setManagedServerPort(String managedServerPort) {
    this.managedServerPort = convertNullToEmptyString(managedServerPort);
  }

  public CreateDomainInputs managedServerPort(String managedServerPort) {
    setManagedServerPort(managedServerPort);
    return this;
  }

  public String getWeblogicDomainStorageNFSServer() {
    return weblogicDomainStorageNFSServer;
  }

  public void setWeblogicDomainStorageNFSServer(String weblogicDomainStorageNFSServer) {
    this.weblogicDomainStorageNFSServer = convertNullToEmptyString(weblogicDomainStorageNFSServer);
  }

  public CreateDomainInputs weblogicDomainStorageNFSServer(String weblogicDomainStorageNFSServer) {
    setWeblogicDomainStorageNFSServer(weblogicDomainStorageNFSServer);
    return this;
  }

  public String getWeblogicDomainStoragePath() {
    return weblogicDomainStoragePath;
  }

  public void setWeblogicDomainStoragePath(String weblogicDomainStoragePath) {
    this.weblogicDomainStoragePath = convertNullToEmptyString(weblogicDomainStoragePath);
  }

  public CreateDomainInputs weblogicDomainStoragePath(String weblogicDomainStoragePath) {
    setWeblogicDomainStoragePath(weblogicDomainStoragePath);
    return this;
  }

  public String getWeblogicDomainStorageSize() {
    return weblogicDomainStorageSize;
  }

  public void setWeblogicDomainStorageSize(String weblogicDomainStorageSize) {
    this.weblogicDomainStorageSize = convertNullToEmptyString(weblogicDomainStorageSize);
  }

  public CreateDomainInputs weblogicDomainStorageSize(String weblogicDomainStorageSize) {
    setWeblogicDomainStorageSize(weblogicDomainStorageSize);
    return this;
  }

  public String getWeblogicDomainStorageReclaimPolicy() {
    return weblogicDomainStorageReclaimPolicy;
  }

  public void setWeblogicDomainStorageReclaimPolicy(String weblogicDomainStorageReclaimPolicy) {
    this.weblogicDomainStorageReclaimPolicy = convertNullToEmptyString(weblogicDomainStorageReclaimPolicy);
  }

  public CreateDomainInputs weblogicDomainStorageReclaimPolicy(String weblogicDomainStorageReclaimPolicy) {
    setWeblogicDomainStorageReclaimPolicy(weblogicDomainStorageReclaimPolicy);
    return this;
  }

  public String getWeblogicDomainStorageType() {
    return weblogicDomainStorageType;
  }

  public void setWeblogicDomainStorageType(String weblogicDomainStorageType) {
    this.weblogicDomainStorageType = convertNullToEmptyString(weblogicDomainStorageType);
  }

  public CreateDomainInputs weblogicDomainStorageType(String weblogicDomainStorageType) {
    setWeblogicDomainStorageType(weblogicDomainStorageType);
    return this;
  }

  public String getProductionModeEnabled() {
    return productionModeEnabled;
  }

  public void setProductionModeEnabled(String productionModeEnabled) {
    this.productionModeEnabled = convertNullToEmptyString(productionModeEnabled);
  }

  public CreateDomainInputs productionModeEnabled(String productionModeEnabled) {
    setProductionModeEnabled(productionModeEnabled);
    return this;
  }

  public String getWeblogicCredentialsSecretName() {
    return weblogicCredentialsSecretName;
  }

  public void setWeblogicCredentialsSecretName(String weblogicCredentialsSecretName) {
    this.weblogicCredentialsSecretName = convertNullToEmptyString(weblogicCredentialsSecretName);
  }

  public CreateDomainInputs weblogicCredentialsSecretName(String weblogicCredentialsSecretName) {
    setWeblogicCredentialsSecretName(weblogicCredentialsSecretName);
    return this;
  }

  public String getWeblogicImagePullSecretName() {
    return weblogicImagePullSecretName;
  }

  public void setWeblogicImagePullSecretName(String weblogicImagePullSecretName) {
    this.weblogicImagePullSecretName = convertNullToEmptyString(weblogicImagePullSecretName);
  }

  public CreateDomainInputs weblogicImagePullSecretName(String weblogicImagePullSecretName) {
    setWeblogicImagePullSecretName(weblogicImagePullSecretName);
    return this;
  }

  public String getT3PublicAddress() {
    return t3PublicAddress;
  }

  public void setT3PublicAddress(String t3PublicAddress) {
    this.t3PublicAddress = convertNullToEmptyString(t3PublicAddress);
  }

  public CreateDomainInputs t3PublicAddress(String t3PublicAddress) {
    setT3PublicAddress(t3PublicAddress);
    return this;
  }


  public String getT3ChannelPort() {
    return t3ChannelPort;
  }

  public void setT3ChannelPort(String t3ChannelPort) {
    this.t3ChannelPort = convertNullToEmptyString(t3ChannelPort);
  }

  public CreateDomainInputs t3ChannelPort(String t3ChannelPort) {
    setT3ChannelPort(t3ChannelPort);
    return this;
  }

  public String getExposeAdminT3Channel() {
    return exposeAdminT3Channel;
  }

  public void setExposeAdminT3Channel(String exposeAdminT3Channel) {
    this.exposeAdminT3Channel = convertNullToEmptyString(exposeAdminT3Channel);
  }

  public CreateDomainInputs exposeAdminT3Channel(String exposeAdminT3Channel) {
    setExposeAdminT3Channel(exposeAdminT3Channel);
    return this;
  }

  public String getAdminNodePort() {
    return adminNodePort;
  }

  public void setAdminNodePort(String adminNodePort) {
    this.adminNodePort = convertNullToEmptyString(adminNodePort);
  }

  public CreateDomainInputs adminNodePort(String adminNodePort) {
    setAdminNodePort(adminNodePort);
    return this;
  }

  public String getExposeAdminNodePort() {
    return exposeAdminNodePort;
  }

  public void setExposeAdminNodePort(String exposeAdminNodePort) {
    this.exposeAdminNodePort = convertNullToEmptyString(exposeAdminNodePort);
  }

  public CreateDomainInputs exposeAdminNodePort(String exposeAdminNodePort) {
    setExposeAdminNodePort(exposeAdminNodePort);
    return this;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = convertNullToEmptyString(namespace);
  }

  public CreateDomainInputs namespace(String namespace) {
    setNamespace(namespace);
    return this;
  }

  public String getLoadBalancer() {
    return loadBalancer;
  }

  public void setLoadBalancer(String loadBalancer) {
    this.loadBalancer = convertNullToEmptyString(loadBalancer);
  }

  public CreateDomainInputs loadBalancer(String loadBalancer) {
    setLoadBalancer(loadBalancer);
    return this;
  }

  public String getLoadBalancerWebPort() {
    return loadBalancerWebPort;
  }

  public void setLoadBalancerWebPort(String loadBalancerWebPort) {
    this.loadBalancerWebPort = convertNullToEmptyString(loadBalancerWebPort);
  }

  public CreateDomainInputs loadBalancerWebPort(String loadBalancerWebPort) {
    setLoadBalancerWebPort(loadBalancerWebPort);
    return this;
  }

  public String getLoadBalancerDashboardPort() {
    return loadBalancerDashboardPort;
  }

  public void setLoadBalancerDashboardPort(String loadBalancerDashboardPort) {
    this.loadBalancerDashboardPort = convertNullToEmptyString(loadBalancerDashboardPort);
  }

  public CreateDomainInputs loadBalancerDashboardPort(String loadBalancerDashboardPort) {
    setLoadBalancerDashboardPort(loadBalancerDashboardPort);
    return this;
  }

  public String getLoadBalancerVolumePath() {
    return loadBalancerVolumePath;
  }

  public void setLoadBalancerVolumePath(String loadBalancerVolumePath) {
    this.loadBalancerVolumePath = convertNullToEmptyString(loadBalancerVolumePath);
  }

  public CreateDomainInputs loadBalancerVolumePath(String loadBalancerVolumePath) {
    setLoadBalancerVolumePath(loadBalancerVolumePath);
    return this;
  }

  public String getLoadBalancerAppPrepath() {
    return loadBalancerAppPrepath;
  }

  public void setLoadBalancerAppPrepath(String loadBalancerAppPrepath) {
    this.loadBalancerAppPrepath = convertNullToEmptyString(loadBalancerAppPrepath);
  }

  public CreateDomainInputs loadBalancerAppPrepath(String loadBalancerAppPrepath) {
    setLoadBalancerAppPrepath(loadBalancerAppPrepath);
    return this;
  }

  public String getJavaOptions() {
    return javaOptions;
  }

  public void setJavaOptions(String javaOptions) {
    this.javaOptions = convertNullToEmptyString(javaOptions);
  }

  public CreateDomainInputs javaOptions(String javaOptions) {
    setJavaOptions(javaOptions);
    return this;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = convertNullToEmptyString(version);
  }

  public CreateDomainInputs version(String version) {
    setVersion(version);
    return this;
  }

  private String convertNullToEmptyString(String val) {
    return Objects.toString(val, "");
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.Objects;

public abstract class DomainValues {
  public static final String LOAD_BALANCER_NONE = "NONE";
  public static final String LOAD_BALANCER_TRAEFIK = "TRAEFIK";
  public static final String LOAD_BALANCER_APACHE = "APACHE";
  public static final String LOAD_BALANCER_VOYAGER = "VOYAGER";
  public static final String STORAGE_TYPE_HOST_PATH = "HOST_PATH";
  public static final String STORAGE_TYPE_NFS = "NFS";
  public static final String STORAGE_RECLAIM_POLICY_RETAIN = "Retain";
  public static final String STORAGE_RECLAIM_POLICY_DELETE = "Delete";
  public static final String STORAGE_RECLAIM_POLICY_RECYCLE = "Recycle";
  public static final String CLUSTER_TYPE_CONFIGURED = "CONFIGURED";
  public static final String CLUSTER_TYPE_DYNAMIC = "DYNAMIC";

  private String adminPort = "";
  private String adminNodePort = "";
  private String adminServerName = "";
  private String domainName = "";
  private String domainUID = "";
  private String serverStartPolicy = "";
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
  private String exposeAdminNodePort = "";
  private String namespace = "";
  private String loadBalancer = "";
  private String loadBalancerWebPort = "";
  private String loadBalancerDashboardPort = "";
  private String loadBalancerVolumePath = "";
  private String loadBalancerAppPrepath = "";
  private String loadBalancerExposeAdminPort = "";
  private String javaOptions = "";
  private String version = "";
  private String weblogicImage = "";

  public String getAdminPort() {
    return adminPort;
  }

  public void setAdminPort(String adminPort) {
    this.adminPort = convertNullToEmptyString(adminPort);
  }

  public DomainValues adminPort(String adminPort) {
    setAdminPort(adminPort);
    return this;
  }

  public String getAdminNodePort() {
    return adminNodePort;
  }

  public void setAdminNodePort(String adminNodePort) {
    this.adminNodePort = convertNullToEmptyString(adminNodePort);
  }

  public DomainValues adminNodePort(String adminNodePort) {
    setAdminNodePort(adminNodePort);
    return this;
  }

  protected String convertNullToEmptyString(String val) {
    return Objects.toString(val, "");
  }

  public DomainValues withTestDefaults() {
    return this.adminNodePort("30702")
        .adminPort("7002")
        .adminServerName("test-admin-server")
        .clusterName("test-cluster")
        .clusterType(CLUSTER_TYPE_DYNAMIC)
        .domainName("TestDomain")
        .weblogicImage("store/oracle/weblogic:19.1.0.0")
        .domainUID("test-domain-uid")
        .javaOptions("TestJavaOptions")
        .loadBalancerDashboardPort("31315")
        .loadBalancerWebPort("31305")
        .configuredManagedServerCount("4")
        .managedServerNameBase("test-managed-server")
        .managedServerPort("8002")
        .initialManagedServerReplicas("3")
        .weblogicDomainStorageNFSServer("TestDomainStorageNFSServer")
        .namespace("test-domain-namespace")
        .weblogicDomainStoragePath("TestDomainStoragePath")
        .weblogicDomainStorageSize("20Gi")
        .productionModeEnabled("false")
        .weblogicCredentialsSecretName("test-weblogic-credentials")
        .serverStartPolicy("IF_NEEDED")
        .t3PublicAddress("TestT3PublicAddress")
        .t3ChannelPort("30013");
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

  public String getWeblogicDomainJobPersistentVolumeName() {
    return getDomainUID() + "-weblogic-domain-job-pv";
  }

  public String getWeblogicDomainJobPersistentVolumeClaimName() {
    return getDomainUID() + "-weblogic-domain-job-pvc";
  }

  public String getAdminServerName() {
    return adminServerName;
  }

  public void setAdminServerName(String adminServerName) {
    this.adminServerName = convertNullToEmptyString(adminServerName);
  }

  public DomainValues adminServerName(String adminServerName) {
    setAdminServerName(adminServerName);
    return this;
  }

  public String getWeblogicImage() {
    return weblogicImage;
  }

  public void setWeblogicImage(String weblogicImage) {
    this.weblogicImage = convertNullToEmptyString(weblogicImage);
  }

  public DomainValues weblogicImage(String weblogicImage) {
    setWeblogicImage(weblogicImage);
    return this;
  }

  public String getDomainName() {
    return domainName;
  }

  public void setDomainName(String domainName) {
    this.domainName = convertNullToEmptyString(domainName);
  }

  public DomainValues domainName(String domainName) {
    setDomainName(domainName);
    return this;
  }

  public String getDomainUID() {
    return domainUID;
  }

  public void setDomainUID(String domainUID) {
    this.domainUID = convertNullToEmptyString(domainUID);
  }

  public DomainValues domainUID(String domainUID) {
    setDomainUID(domainUID);
    return this;
  }

  public String getServerStartPolicy() {
    return serverStartPolicy;
  }

  public void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = convertNullToEmptyString(serverStartPolicy);
  }

  public DomainValues serverStartPolicy(String serverStartPolicy) {
    setServerStartPolicy(serverStartPolicy);
    return this;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = convertNullToEmptyString(clusterName);
  }

  public DomainValues clusterName(String clusterName) {
    setClusterName(clusterName);
    return this;
  }

  public String getClusterType() {
    return clusterType;
  }

  public void setClusterType(String clusterType) {
    this.clusterType = convertNullToEmptyString(clusterType);
  }

  public DomainValues clusterType(String clusterType) {
    setClusterType(clusterType);
    return this;
  }

  public String getConfiguredManagedServerCount() {
    return configuredManagedServerCount;
  }

  public void setConfiguredManagedServerCount(String configuredManagedServerCount) {
    this.configuredManagedServerCount = convertNullToEmptyString(configuredManagedServerCount);
  }

  public DomainValues configuredManagedServerCount(String configuredManagedServerCount) {
    setConfiguredManagedServerCount(configuredManagedServerCount);
    return this;
  }

  public String getInitialManagedServerReplicas() {
    return initialManagedServerReplicas;
  }

  public void setInitialManagedServerReplicas(String initialManagedServerReplicas) {
    this.initialManagedServerReplicas = convertNullToEmptyString(initialManagedServerReplicas);
  }

  public DomainValues initialManagedServerReplicas(String initialManagedServerReplicas) {
    setInitialManagedServerReplicas(initialManagedServerReplicas);
    return this;
  }

  public String getManagedServerNameBase() {
    return managedServerNameBase;
  }

  public void setManagedServerNameBase(String managedServerNameBase) {
    this.managedServerNameBase = convertNullToEmptyString(managedServerNameBase);
  }

  public DomainValues managedServerNameBase(String managedServerNameBase) {
    setManagedServerNameBase(managedServerNameBase);
    return this;
  }

  public String getManagedServerPort() {
    return managedServerPort;
  }

  public void setManagedServerPort(String managedServerPort) {
    this.managedServerPort = convertNullToEmptyString(managedServerPort);
  }

  public DomainValues managedServerPort(String managedServerPort) {
    setManagedServerPort(managedServerPort);
    return this;
  }

  public String getWeblogicDomainStorageNFSServer() {
    return weblogicDomainStorageNFSServer;
  }

  public void setWeblogicDomainStorageNFSServer(String weblogicDomainStorageNFSServer) {
    this.weblogicDomainStorageNFSServer = convertNullToEmptyString(weblogicDomainStorageNFSServer);
  }

  public DomainValues weblogicDomainStorageNFSServer(String weblogicDomainStorageNFSServer) {
    setWeblogicDomainStorageNFSServer(weblogicDomainStorageNFSServer);
    return this;
  }

  public String getWeblogicDomainStoragePath() {
    return weblogicDomainStoragePath;
  }

  public void setWeblogicDomainStoragePath(String weblogicDomainStoragePath) {
    this.weblogicDomainStoragePath = convertNullToEmptyString(weblogicDomainStoragePath);
  }

  public DomainValues weblogicDomainStoragePath(String weblogicDomainStoragePath) {
    setWeblogicDomainStoragePath(weblogicDomainStoragePath);
    return this;
  }

  public String getWeblogicDomainStorageSize() {
    return weblogicDomainStorageSize;
  }

  public void setWeblogicDomainStorageSize(String weblogicDomainStorageSize) {
    this.weblogicDomainStorageSize = convertNullToEmptyString(weblogicDomainStorageSize);
  }

  public DomainValues weblogicDomainStorageSize(String weblogicDomainStorageSize) {
    setWeblogicDomainStorageSize(weblogicDomainStorageSize);
    return this;
  }

  public String getWeblogicDomainStorageReclaimPolicy() {
    return weblogicDomainStorageReclaimPolicy;
  }

  public void setWeblogicDomainStorageReclaimPolicy(String weblogicDomainStorageReclaimPolicy) {
    this.weblogicDomainStorageReclaimPolicy =
        convertNullToEmptyString(weblogicDomainStorageReclaimPolicy);
  }

  public DomainValues weblogicDomainStorageReclaimPolicy(
      String weblogicDomainStorageReclaimPolicy) {
    setWeblogicDomainStorageReclaimPolicy(weblogicDomainStorageReclaimPolicy);
    return this;
  }

  public String getWeblogicDomainStorageType() {
    return weblogicDomainStorageType;
  }

  public void setWeblogicDomainStorageType(String weblogicDomainStorageType) {
    this.weblogicDomainStorageType = convertNullToEmptyString(weblogicDomainStorageType);
  }

  public DomainValues weblogicDomainStorageType(String weblogicDomainStorageType) {
    setWeblogicDomainStorageType(weblogicDomainStorageType);
    return this;
  }

  public String getProductionModeEnabled() {
    return productionModeEnabled;
  }

  public void setProductionModeEnabled(String productionModeEnabled) {
    this.productionModeEnabled = convertNullToEmptyString(productionModeEnabled);
  }

  public DomainValues productionModeEnabled(String productionModeEnabled) {
    setProductionModeEnabled(productionModeEnabled);
    return this;
  }

  public String getWeblogicCredentialsSecretName() {
    return weblogicCredentialsSecretName;
  }

  public void setWeblogicCredentialsSecretName(String weblogicCredentialsSecretName) {
    this.weblogicCredentialsSecretName = convertNullToEmptyString(weblogicCredentialsSecretName);
  }

  public DomainValues weblogicCredentialsSecretName(String weblogicCredentialsSecretName) {
    setWeblogicCredentialsSecretName(weblogicCredentialsSecretName);
    return this;
  }

  public String getWeblogicImagePullSecretName() {
    return weblogicImagePullSecretName;
  }

  public void setWeblogicImagePullSecretName(String weblogicImagePullSecretName) {
    this.weblogicImagePullSecretName = convertNullToEmptyString(weblogicImagePullSecretName);
  }

  public DomainValues weblogicImagePullSecretName(String weblogicImagePullSecretName) {
    setWeblogicImagePullSecretName(weblogicImagePullSecretName);
    return this;
  }

  public String getT3PublicAddress() {
    return t3PublicAddress;
  }

  public void setT3PublicAddress(String t3PublicAddress) {
    this.t3PublicAddress = convertNullToEmptyString(t3PublicAddress);
  }

  public DomainValues t3PublicAddress(String t3PublicAddress) {
    setT3PublicAddress(t3PublicAddress);
    return this;
  }

  public String getT3ChannelPort() {
    return t3ChannelPort;
  }

  public void setT3ChannelPort(String t3ChannelPort) {
    this.t3ChannelPort = convertNullToEmptyString(t3ChannelPort);
  }

  public DomainValues t3ChannelPort(String t3ChannelPort) {
    setT3ChannelPort(t3ChannelPort);
    return this;
  }

  public String getExposeAdminT3Channel() {
    return exposeAdminT3Channel;
  }

  public void setExposeAdminT3Channel(String exposeAdminT3Channel) {
    this.exposeAdminT3Channel = convertNullToEmptyString(exposeAdminT3Channel);
  }

  public DomainValues exposeAdminT3Channel(String exposeAdminT3Channel) {
    setExposeAdminT3Channel(exposeAdminT3Channel);
    return this;
  }

  public String getExposeAdminNodePort() {
    return exposeAdminNodePort;
  }

  public void setExposeAdminNodePort(String exposeAdminNodePort) {
    this.exposeAdminNodePort = convertNullToEmptyString(exposeAdminNodePort);
  }

  public DomainValues exposeAdminNodePort(String exposeAdminNodePort) {
    setExposeAdminNodePort(exposeAdminNodePort);
    return this;
  }

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = convertNullToEmptyString(namespace);
  }

  public DomainValues namespace(String namespace) {
    setNamespace(namespace);
    return this;
  }

  public String getLoadBalancer() {
    return loadBalancer;
  }

  public void setLoadBalancer(String loadBalancer) {
    this.loadBalancer = convertNullToEmptyString(loadBalancer);
  }

  public DomainValues loadBalancer(String loadBalancer) {
    setLoadBalancer(loadBalancer);
    return this;
  }

  public String getLoadBalancerWebPort() {
    return loadBalancerWebPort;
  }

  public void setLoadBalancerWebPort(String loadBalancerWebPort) {
    this.loadBalancerWebPort = convertNullToEmptyString(loadBalancerWebPort);
  }

  public DomainValues loadBalancerWebPort(String loadBalancerWebPort) {
    setLoadBalancerWebPort(loadBalancerWebPort);
    return this;
  }

  public String getLoadBalancerDashboardPort() {
    return loadBalancerDashboardPort;
  }

  public void setLoadBalancerDashboardPort(String loadBalancerDashboardPort) {
    this.loadBalancerDashboardPort = convertNullToEmptyString(loadBalancerDashboardPort);
  }

  public DomainValues loadBalancerDashboardPort(String loadBalancerDashboardPort) {
    setLoadBalancerDashboardPort(loadBalancerDashboardPort);
    return this;
  }

  public String getLoadBalancerVolumePath() {
    return loadBalancerVolumePath;
  }

  public void setLoadBalancerVolumePath(String loadBalancerVolumePath) {
    this.loadBalancerVolumePath = convertNullToEmptyString(loadBalancerVolumePath);
  }

  public DomainValues loadBalancerVolumePath(String loadBalancerVolumePath) {
    setLoadBalancerVolumePath(loadBalancerVolumePath);
    return this;
  }

  public String getLoadBalancerAppPrepath() {
    return loadBalancerAppPrepath;
  }

  public void setLoadBalancerAppPrepath(String loadBalancerAppPrepath) {
    this.loadBalancerAppPrepath = convertNullToEmptyString(loadBalancerAppPrepath);
  }

  public DomainValues loadBalancerAppPrepath(String loadBalancerAppPrepath) {
    setLoadBalancerAppPrepath(loadBalancerAppPrepath);
    return this;
  }

  public String getLoadBalancerExposeAdminPort() {
    return loadBalancerExposeAdminPort;
  }

  public void setLoadBalancerExposeAdminPort(String loadBalancerExposeAdminPort) {
    this.loadBalancerExposeAdminPort = convertNullToEmptyString(loadBalancerExposeAdminPort);
  }

  public DomainValues loadBalancerExposeAdminPort(String loadBalancerExposeAdminPort) {
    setLoadBalancerExposeAdminPort(loadBalancerExposeAdminPort);
    return this;
  }

  public String getJavaOptions() {
    return javaOptions;
  }

  public void setJavaOptions(String javaOptions) {
    this.javaOptions = convertNullToEmptyString(javaOptions);
  }

  public DomainValues javaOptions(String javaOptions) {
    setJavaOptions(javaOptions);
    return this;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = convertNullToEmptyString(version);
  }

  public DomainValues version(String version) {
    setVersion(version);
    return this;
  }
}

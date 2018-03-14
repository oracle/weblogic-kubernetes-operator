// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Objects;

import static oracle.kubernetes.operator.create.YamlUtils.newYaml;

/**
 * Class that mirrors create-weblogic-operator-inputs.yaml
 *
 * Used to parse create-weblogic-operator-inputs.yaml into java
 * and convert java to create-weblogic-operator-inputs.yaml
 *
 * Note: use strings to represent params that must be ints or booleans at runtime
 * so that we can test more invalid input options (e.g. missing value, not int value)
 *
 * Note: initialize to empty strings and change nulls to empty strings
 * so that when this is written out to a yaml file, the files don't
 * include the literal "null" string. 
 */
public class CreateDomainInputs {

  public static final String LOAD_BALANCER_NONE = "none";
  public static final String LOAD_BALANCER_TRAEFIK = "traefik";
  public static final String PERSISTENCE_TYPE_HOST_PATH = "hostPath";
  public static final String PERSISTENCE_TYPE_NFS = "nfs";
  public static final String STARTUP_CONTROL_NONE = "NONE";
  public static final String STARTUP_CONTROL_ALL = "ALL";
  public static final String STARTUP_CONTROL_ADMIN = "ADMIN";
  public static final String STARTUP_CONTROL_SPECIFIED = "SPECIFIED";
  public static final String STARTUP_CONTROL_AUTO = "AUTO";

  private static final String DEFAULT_INPUTS = "kubernetes/create-weblogic-domain-inputs.yaml";

  private String adminPort = "";
  private String adminServerName = "";
  private String createDomainScript = "";
  private String domainName = "";
  private String domainUid = "";
  private String startupControl = "";
  private String clusterName = "";
  private String managedServerCount = "";
  private String managedServerStartCount = "";
  private String managedServerNameBase = "";
  private String managedServerPort = "";
  private String nfsServer = "";
  private String persistencePath = "";
  private String persistenceSize = "";
  private String persistenceType = "";
  private String persistenceVolumeClaimName = "";
  private String persistenceVolumeName = "";
  private String productionModeEnabled = "";
  private String secretName = "";
  private String imagePullSecretName = "";
  private String t3PublicAddress = "";
  private String t3ChannelPort = "";
  private String exposeAdminT3Channel = "";
  private String adminNodePort = "";
  private String exposeAdminNodePort = "";
  private String namespace = "";
  private String loadBalancer = "";
  private String loadBalancerWebPort = "";
  private String loadBalancerAdminPort = "";
  private String javaOptions = "";

  public static CreateDomainInputs newInputs() throws Exception {
    return
      readDefaultInputsFile()
        .adminNodePort("30702")
        .adminPort("7002")
        .adminServerName("TestAdminServer")
        .clusterName("TestCluster")
        .domainName("TestDomain")
        .domainUid("test-domain-uid")
        .javaOptions("TestJavaOptions")
        .loadBalancerAdminPort("31315")
        .loadBalancerWebPort("31305")
        .managedServerCount("4")
        .managedServerNameBase("TestManagedServer")
        .managedServerPort("8002")
        .managedServerStartCount("3")
        .nfsServer("TestNfsServer")
        .namespace("test-domain-namespace")
        .persistencePath("TestPersistencePath")
        .persistenceSize("20Gi")
        .persistenceVolumeClaimName("test-domain-pvc")
        .persistenceVolumeName("test-domain-pv")
        .productionModeEnabled("false")
        .secretName("test-weblogic-credentials")
        .startupControl(STARTUP_CONTROL_ALL)
        .t3PublicAddress("TestT3PublicAddress")
        .t3ChannelPort("30013");
  }

  public static CreateDomainInputs readDefaultInputsFile() throws IOException {
    Reader r = Files.newBufferedReader(defaultInputsPath(), Charset.forName("UTF-8"));
    return (CreateDomainInputs)newYaml().loadAs(r, CreateDomainInputs.class);
  }

  private static Path defaultInputsPath() {
    return FileSystems.getDefault().getPath(DEFAULT_INPUTS);
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

  public String getCreateDomainScript() {
    return createDomainScript;
  }

  public void setCreateDomainScript(String createDomainScript) {
    this.createDomainScript = convertNullToEmptyString(createDomainScript);
  }

  public CreateDomainInputs createDomainScript(String createDomainScript) {
    setCreateDomainScript(createDomainScript);
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

  public String getDomainUid() {
    return domainUid;
  }

  public void setDomainUid(String domainUid) {
    this.domainUid = convertNullToEmptyString(domainUid);
  }

  public CreateDomainInputs domainUid(String domainUid) {
    setDomainUid(domainUid);
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

  public String getManagedServerCount() {
    return managedServerCount;
  }

  public void setManagedServerCount(String managedServerCount) {
    this.managedServerCount = convertNullToEmptyString(managedServerCount);
  }

  public CreateDomainInputs managedServerCount(String managedServerCount) {
    setManagedServerCount(managedServerCount);
    return this;
  }

  public String getManagedServerStartCount() {
    return managedServerStartCount;
  }

  public void setManagedServerStartCount(String managedServerStartCount) {
    this.managedServerStartCount = convertNullToEmptyString(managedServerStartCount);
  }

  public CreateDomainInputs managedServerStartCount(String managedServerStartCount) {
    setManagedServerStartCount(managedServerStartCount);
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

  public String getNfsServer() {
    return nfsServer;
  }

  public void setNfsServer(String nfsServer) {
    this.nfsServer = convertNullToEmptyString(nfsServer);
  }

  public CreateDomainInputs nfsServer(String nfsServer) {
    setNfsServer(nfsServer);
    return this;
  }

  public String getPersistencePath() {
    return persistencePath;
  }

  public void setPersistencePath(String persistencePath) {
    this.persistencePath = convertNullToEmptyString(persistencePath);
  }

  public CreateDomainInputs persistencePath(String persistencePath) {
    setPersistencePath(persistencePath);
    return this;
  }

  public String getPersistenceSize() {
    return persistenceSize;
  }

  public void setPersistenceSize(String persistenceSize) {
    this.persistenceSize = convertNullToEmptyString(persistenceSize);
  }

  public CreateDomainInputs persistenceSize(String persistenceSize) {
    setPersistenceSize(persistenceSize);
    return this;
  }

  public String getPersistenceType() {
    return persistenceType;
  }

  public void setPersistenceType(String persistenceType) {
    this.persistenceType = convertNullToEmptyString(persistenceType);
  }

  public CreateDomainInputs persistenceType(String persistenceType) {
    setPersistenceType(persistenceType);
    return this;
  }

  public String getPersistenceVolumeClaimName() {
    return persistenceVolumeClaimName;
  }

  public void setPersistenceVolumeClaimName(String persistenceVolumeClaimName) {
    this.persistenceVolumeClaimName = convertNullToEmptyString(persistenceVolumeClaimName);
  }

  public CreateDomainInputs persistenceVolumeClaimName(String persistenceVolumeClaimName) {
    setPersistenceVolumeClaimName(persistenceVolumeClaimName);
    return this;
  }

  public String getPersistenceVolumeName() {
    return persistenceVolumeName;
  }

  public void setPersistenceVolumeName(String persistenceVolumeName) {
    this.persistenceVolumeName = convertNullToEmptyString(persistenceVolumeName);
  }

  public CreateDomainInputs persistenceVolumeName(String persistenceVolumeName) {
    setPersistenceVolumeName(persistenceVolumeName);
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

  public String getSecretName() {
    return secretName;
  }

  public void setSecretName(String secretName) {
    this.secretName = convertNullToEmptyString(secretName);
  }

  public CreateDomainInputs secretName(String secretName) {
    setSecretName(secretName);
    return this;
  }

  public String getImagePullSecretName() {
    return imagePullSecretName;
  }

  public void setImagePullSecretName(String imagePullSecretName) {
    this.imagePullSecretName = convertNullToEmptyString(imagePullSecretName);
  }

  public CreateDomainInputs imagePullSecretName(String imagePullSecretName) {
    setImagePullSecretName(imagePullSecretName);
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

  public String getLoadBalancerAdminPort() {
    return loadBalancerAdminPort;
  }

  public void setLoadBalancerAdminPort(String loadBalancerAdminPort) {
    this.loadBalancerAdminPort = convertNullToEmptyString(loadBalancerAdminPort);
  }

  public CreateDomainInputs loadBalancerAdminPort(String loadBalancerAdminPort) {
    setLoadBalancerAdminPort(loadBalancerAdminPort);
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

  private String convertNullToEmptyString(String val) {
    return Objects.toString(val, "");
  }
}

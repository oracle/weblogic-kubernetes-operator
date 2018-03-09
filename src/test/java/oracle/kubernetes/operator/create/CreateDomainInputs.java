package oracle.kubernetes.operator.create;

import java.util.Objects;

public class CreateDomainInputs {

    private String adminPort;
    private String adminServerName;
    private String createDomainScript;
    private String domainName;
    private String domainUid;
    private String startupControl;
    private String clusterName;
    private String managedServerCount;
    private String managedServerStartCount;
    private String managedServerNameBase;
    private String managedServerPort;
    private String persistencePath;
    private String persistenceSize;
    private String persistenceVolumeClaimName;
    private String persistenceVolumeName;
    private String productionModeEnabled;
    private String secretName;
    private String imagePullSecretName;
    private String t3ChannelPort;
    private String exposeAdminT3Channel;
    private String adminNodePort;
    private String exposeAdminNodePort;
    private String namespace;
    private String loadBalancer;
    private String loadBalancerWebPort;
    private String loadBalancerAdminPort;
    private String javaOptions;

    public String getAdminPort() {
        return adminPort;
    }

    public void setAdminPort(String adminPort) {
        this.adminPort = Objects.toString(adminPort);
    }

    public CreateDomainInputs adminPort(String adminPort) {
        setAdminPort(adminPort);
        return this;
    }

    public String getAdminServerName() {
        return adminServerName;
    }

    public void setAdminServerName(String adminServerName) {
        this.adminServerName = Objects.toString(adminServerName);
    }

    public CreateDomainInputs adminServerName(String adminServerName) {
        setAdminServerName(adminServerName);
        return this;
    }

    public String getCreateDomainScript() {
        return createDomainScript;
    }

    public void setCreateDomainScript(String createDomainScript) {
        this.createDomainScript = Objects.toString(createDomainScript);
    }

    public CreateDomainInputs createDomainScript(String createDomainScript) {
        setCreateDomainScript(createDomainScript);
        return this;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = Objects.toString(domainName);
    }

    public CreateDomainInputs domainName(String domainName) {
        setDomainName(domainName);
        return this;
    }

    public String getDomainUid() {
        return domainUid;
    }

    public void setDomainUid(String domainUid) {
        this.domainUid = Objects.toString(domainUid);
    }

    public CreateDomainInputs domainUid(String domainUid) {
        setDomainUid(domainUid);
        return this;
    }

    public String getStartupControl() {
        return startupControl;
    }

    public void setStartupControl(String startupControl) {
        this.startupControl = Objects.toString(startupControl);
    }

    public CreateDomainInputs startupControl(String startupControl) {
        setStartupControl(startupControl);
        return this;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = Objects.toString(clusterName);
    }

    public CreateDomainInputs clusterName(String clusterName) {
        setClusterName(clusterName);
        return this;
    }

    public String getManagedServerCount() {
        return managedServerCount;
    }

    public void setManagedServerCount(String managedServerCount) {
        this.managedServerCount = Objects.toString(managedServerCount);
    }

    public CreateDomainInputs managedServerCount(String managedServerCount) {
        setManagedServerCount(managedServerCount);
        return this;
    }

    public String getManagedServerStartCount() {
        return managedServerStartCount;
    }

    public void setManagedServerStartCount(String managedServerStartCount) {
        this.managedServerStartCount = Objects.toString(managedServerStartCount);
    }

    public CreateDomainInputs managedServerStartCount(String managedServerStartCount) {
        setManagedServerStartCount(managedServerStartCount);
        return this;
    }

    public String getManagedServerNameBase() {
        return managedServerNameBase;
    }

    public void setManagedServerNameBase(String managedServerNameBase) {
        this.managedServerNameBase = Objects.toString(managedServerNameBase);
    }

    public CreateDomainInputs managedServerNameBase(String managedServerNameBase) {
        setManagedServerNameBase(managedServerNameBase);
        return this;
    }

    public String getManagedServerPort() {
        return managedServerPort;
    }

    public void setManagedServerPort(String managedServerPort) {
        this.managedServerPort = Objects.toString(managedServerPort);
    }

    public CreateDomainInputs managedServerPort(String managedServerPort) {
        setManagedServerPort(managedServerPort);
        return this;
    }

    public String getPersistencePath() {
        return persistencePath;
    }

    public void setPersistencePath(String persistencePath) {
        this.persistencePath = Objects.toString(persistencePath);
    }

    public CreateDomainInputs persistencePath(String persistencePath) {
        setPersistencePath(persistencePath);
        return this;
    }

    public String getPersistenceSize() {
        return persistenceSize;
    }

    public void setPersistenceSize(String persistenceSize) {
        this.persistenceSize = Objects.toString(persistenceSize);
    }

    public CreateDomainInputs persistenceSize(String persistenceSize) {
        setPersistenceSize(persistenceSize);
        return this;
    }

    public String getPersistenceVolumeClaimName() {
        return persistenceVolumeClaimName;
    }

    public void setPersistenceVolumeClaimName(String persistenceVolumeClaimName) {
        this.persistenceVolumeClaimName = Objects.toString(persistenceVolumeClaimName);
    }

    public CreateDomainInputs persistenceVolumeClaimName(String persistenceVolumeClaimName) {
        setPersistenceVolumeClaimName(persistenceVolumeClaimName);
        return this;
    }

    public String getPersistenceVolumeName() {
        return persistenceVolumeName;
    }

    public void setPersistenceVolumeName(String persistenceVolumeName) {
        this.persistenceVolumeName = Objects.toString(persistenceVolumeName);
    }

    public CreateDomainInputs persistenceVolumeName(String persistenceVolumeName) {
        setPersistenceVolumeName(persistenceVolumeName);
        return this;
    }

    public String getProductionModeEnabled() {
        return productionModeEnabled;
    }

    public void setProductionModeEnabled(String productionModeEnabled) {
        this.productionModeEnabled = Objects.toString(productionModeEnabled);
    }

    public CreateDomainInputs productionModeEnabled(String productionModeEnabled) {
        setProductionModeEnabled(productionModeEnabled);
        return this;
    }

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = Objects.toString(secretName);
    }

    public CreateDomainInputs secretName(String secretName) {
        setSecretName(secretName);
        return this;
    }

    public String getImagePullSecretName() {
        return imagePullSecretName;
    }

    public void setImagePullSecretName(String imagePullSecretName) {
        this.imagePullSecretName = Objects.toString(imagePullSecretName);
    }

    public CreateDomainInputs imagePullSecretName(String imagePullSecretName) {
        setImagePullSecretName(imagePullSecretName);
        return this;
    }

    public String getT3ChannelPort() {
        return t3ChannelPort;
    }

    public void setT3ChannelPort(String t3ChannelPort) {
        this.t3ChannelPort = Objects.toString(t3ChannelPort);
    }

    public CreateDomainInputs t3ChannelPort(String t3ChannelPort) {
        setT3ChannelPort(t3ChannelPort);
        return this;
    }

    public String getExposeAdminT3Channel() {
        return exposeAdminT3Channel;
    }

    public void setExposeAdminT3Channel(String exposeAdminT3Channel) {
        this.exposeAdminT3Channel = Objects.toString(exposeAdminT3Channel);
    }

    public CreateDomainInputs exposeAdminT3Channel(String exposeAdminT3Channel) {
        setExposeAdminT3Channel(exposeAdminT3Channel);
        return this;
    }

    public String getAdminNodePort() {
        return adminNodePort;
    }

    public void setAdminNodePort(String adminNodePort) {
        this.adminNodePort = Objects.toString(adminNodePort);
    }

    public CreateDomainInputs adminNodePort(String adminNodePort) {
        setAdminNodePort(adminNodePort);
        return this;
    }

    public String getExposeAdminNodePort() {
        return exposeAdminNodePort;
    }

    public void setExposeAdminNodePort(String exposeAdminNodePort) {
        this.exposeAdminNodePort = Objects.toString(exposeAdminNodePort);
    }

    public CreateDomainInputs exposeAdminNodePort(String exposeAdminNodePort) {
        setExposeAdminNodePort(exposeAdminNodePort);
        return this;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = Objects.toString(namespace);
    }

    public CreateDomainInputs namespace(String namespace) {
        setNamespace(namespace);
        return this;
    }

    public String getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(String loadBalancer) {
        this.loadBalancer = Objects.toString(loadBalancer);
    }

    public CreateDomainInputs loadBalancer(String loadBalancer) {
        setLoadBalancer(loadBalancer);
        return this;
    }

    public String getLoadBalancerWebPort() {
        return loadBalancerWebPort;
    }

    public void setLoadBalancerWebPort(String loadBalancerWebPort) {
        this.loadBalancerWebPort = Objects.toString(loadBalancerWebPort);
    }

    public CreateDomainInputs loadBalancerWebPort(String loadBalancerWebPort) {
        setLoadBalancerWebPort(loadBalancerWebPort);
        return this;
    }

    public String getLoadBalancerAdminPort() {
        return loadBalancerAdminPort;
    }

    public void setLoadBalancerAdminPort(String loadBalancerAdminPort) {
        this.loadBalancerAdminPort = Objects.toString(loadBalancerAdminPort);
    }

    public CreateDomainInputs loadBalancerAdminPortt(String loadBalancerAdminPort) {
        setLoadBalancerAdminPort(loadBalancerAdminPort);
        return this;
    }

    public String getJavaOptions() {
        return javaOptions;
    }

    public void setJavaOptions(String javaOptions) {
        this.javaOptions = Objects.toString(javaOptions);
    }

    public CreateDomainInputs javaOptions(String javaOptions) {
        setJavaOptions(javaOptions);
        return this;
    }
}

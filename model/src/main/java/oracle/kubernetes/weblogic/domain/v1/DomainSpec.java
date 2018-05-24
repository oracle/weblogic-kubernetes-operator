// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1SecretReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** DomainSpec is a description of a domain. */
public class DomainSpec {

  /** Domain unique identifier. Must be unique across the Kubernetes cluster. (Required) */
  @SerializedName("domainUID")
  @Expose
  @NotNull
  private String domainUID;

  /** Domain name (Required) */
  @SerializedName("domainName")
  @Expose
  @NotNull
  private String domainName;

  /**
   * The WebLogic Docker image.
   *
   * <p>Defaults to store/oracle/weblogic:12.2.1.3.
   *
   * @deprecated Use the Server image property.
   */
  @SerializedName("image")
  @Expose
  @Deprecated
  private String image;

  /**
   * The image pull policy for the WebLogic Docker image. Legal values are Always, Never and
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest, IfNotPresent otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @deprecated Use the Server imagePullPolicy property.
   */
  @SerializedName("imagePullPolicy")
  @Expose
  @Deprecated
  private String imagePullPolicy;

  /**
   * Reference to secret containing domain administrator username and password. Secret must contain
   * keys names 'username' and 'password' (Required)
   */
  @SerializedName("adminSecret")
  @Expose
  @Valid
  @NotNull
  private V1SecretReference adminSecret;

  /**
   * Admin server name. Note: Possibly temporary as we could find this value through domain home
   * inspection. (Required)
   */
  @SerializedName("asName")
  @Expose
  @NotNull
  private String asName;

  /**
   * Administration server port. Note: Possibly temporary as we could find this value through domain
   * home inspection. (Required)
   */
  @SerializedName("asPort")
  @Expose
  @NotNull
  private Integer asPort;

  /**
   * List of specific T3 channels to export. Named T3 Channels will be exposed using NodePort
   * Services. The internal and external ports must match; therefore, it is required that the
   * channel's port in the WebLogic configuration be a legal and unique value in the Kubernetes
   * cluster's legal NodePort port range.
   */
  @SerializedName("exportT3Channels")
  @Expose
  @Valid
  private List<String> exportT3Channels = new ArrayList<String>();

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED
   * and AUTO.
   *
   * <ul>
   *   <li>NONE indicates that no servers, including the administration server, will be started.
   *   <li>ADMIN indicates that only the administration server will be started.
   *   <li>ALL indicates that all servers in the domain will be started.
   *   <li>SPECIFIED indicates that the administration server will be started and then additionally
   *       only those servers listed under serverStartup or managed servers belonging to cluster
   *       listed under clusterStartup up to the cluster's replicas field will be started.
   *   <li>AUTO indicates that servers will be started exactly as with SPECIFIED, but then managed
   *       servers belonging to clusters not listed under clusterStartup will be started up to the
   *       replicas field.
   * </ul>
   *
   * <p>Defaults to AUTO.
   *
   * @deprecated Use the servers, clusters, clusterDefaults, nonClusteredServerDefaults and
   *     serverDefaults properties.
   */
  @SerializedName("startupControl")
  @Expose
  @Deprecated
  private String startupControl;

  /**
   * List of server startup details for selected servers.
   *
   * @deprecated Use the servers, clusters, clusterDefaults, nonClusteredServerDefaults and
   *     serverDefaults properties.
   */
  @SerializedName("serverStartup")
  @Expose
  @Valid
  @Deprecated
  private List<ServerStartup> serverStartup = new ArrayList<ServerStartup>();

  /**
   * List of server startup details for selected clusters.
   *
   * @deprecated Use the clusters and clusterDefaults properties.
   */
  @SerializedName("clusterStartup")
  @Expose
  @Valid
  @Deprecated
  private List<ClusterStartup> clusterStartup = new ArrayList<ClusterStartup>();

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusterStartup.
   *
   * @deprecated Use the clusterDefaults property's replicas property.
   */
  @SerializedName("replicas")
  @Expose
  @Deprecated
  private Integer replicas;

  /** The default desired state of servers. */
  @SerializedName("serverDefaults")
  @Expose
  @Valid
  private Server serverDefaults;

  /** The default desired state of non-clustered servers. */
  @SerializedName("nonClusteredServerDefaults")
  @Expose
  @Valid
  private NonClusteredServer nonClusteredServerDefaults;

  /**
   * Maps the name of a non-clustered server to its desired state.
   *
   * <p>The server property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the server in nonClusteredServers property, and the property has
   *       been specified on that server, then use its value.
   *   <li>If not, and the property has been specified on the nonClusteredServerDefaults property,
   *       then use its value.
   *   <li>If not, and the property value has been specified on the serverDefaults property, then
   *       use its value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   */
  @SerializedName("servers")
  @Expose
  @Valid
  private Map<String, NonClusteredServer> servers = new HashMap<String, NonClusteredServer>();

  /** The default desired state of clusters. */
  @SerializedName("clusterDefaults")
  @Expose
  @Valid
  private ClusterParams clusterDefaults;

  /**
   * Maps the name of a cluster to its desired state.
   *
   * <p>The cluster property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the cluster in the clusters property, and the property has been
   *       specified on that cluster, then use its value.
   *   <li>If not, and the property has been specified on the clusterDefaults property, then use its
   *       value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   */
  @SerializedName("clusters")
  @Expose
  @Valid
  private Map<String, Cluster> clusters = new HashMap<String, Cluster>();

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. (Required)
   *
   * @return domain UID
   */
  public String getDomainUID() {
    return domainUID;
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. (Required)
   *
   * @param domainUID domain UID
   */
  public void setDomainUID(String domainUID) {
    this.domainUID = domainUID;
  }

  /**
   * Domain unique identifier. Must be unique across the Kubernetes cluster. (Required)
   *
   * @param domainUID domain UID
   * @return this
   */
  public DomainSpec withDomainUID(String domainUID) {
    this.domainUID = domainUID;
    return this;
  }

  /**
   * Domain name (Required)
   *
   * @return domain name
   */
  public String getDomainName() {
    return domainName;
  }

  /**
   * Domain name (Required)
   *
   * @param domainName domain name
   */
  public void setDomainName(String domainName) {
    this.domainName = domainName;
  }

  /**
   * Domain name (Required)
   *
   * @param domainName domain name
   * @return this
   */
  public DomainSpec withDomainName(String domainName) {
    this.domainName = domainName;
    return this;
  }

  /*
   * The WebLogic Docker image.
   *
   * <p> Defaults to store/oracle/weblogic:12.2.1.3.
   *
   * @deprecated Use the Server image property.
   *
   * @return image
   */
  @Deprecated
  public String getImage() {
    return image;
  }

  /*
   * The WebLogic Docker image.
   *
   * <p> Defaults to store/oracle/weblogic:12.2.1.3.
   *
   * @deprecated Use the Server image property.
   *
   * @param image image
   */
  @Deprecated
  public void setImage(String image) {
    this.image = image;
  }

  /*
   * The WebLogic Docker image.
   *
   * <p> Defaults to store/oracle/weblogic:12.2.1.3.
   *
   * @deprecated Use the Server image property.
   *
   * @param image image
   * @return this
   */
  @Deprecated
  public DomainSpec withImage(String image) {
    this.image = image;
    return this;
  }

  /**
   * The image pull policy for the WebLogic Docker image. Legal values are Always, Never and
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest, IfNotPresent otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @deprecated Use the Server imagePullPolicy property.
   * @return image pull policy
   */
  @Deprecated
  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  /**
   * The image pull policy for the WebLogic Docker image. Legal values are Always, Never and
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest, IfNotPresent otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @deprecated Use the Server imagePullPolicy property.
   * @param imagePullPolicy image pull policy
   */
  @Deprecated
  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  /**
   * The image pull policy for the WebLogic Docker image. Legal values are Always, Never and
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest, IfNotPresent otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @deprecated Use the Server imagePullPolicy property.
   * @param imagePullPolicy image pull policy
   * @return this
   */
  @Deprecated
  public DomainSpec withImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  /**
   * Reference to secret containing domain administrator username and password. Secret must contain
   * keys names 'username' and 'password' (Required)
   *
   * @return admin secret
   */
  public V1SecretReference getAdminSecret() {
    return adminSecret;
  }

  /**
   * Reference to secret containing domain administrator username and password. Secret must contain
   * keys names 'username' and 'password' (Required)
   *
   * @param adminSecret admin secret
   */
  public void setAdminSecret(V1SecretReference adminSecret) {
    this.adminSecret = adminSecret;
  }

  /**
   * Reference to secret containing domain administrator username and password. Secret must contain
   * keys names 'username' and 'password' (Required)
   *
   * @param adminSecret admin secret
   * @return this
   */
  public DomainSpec withAdminSecret(V1SecretReference adminSecret) {
    this.adminSecret = adminSecret;
    return this;
  }

  /**
   * Admin server name. Note: Possibly temporary as we could find this value through domain home
   * inspection. (Required)
   *
   * @return admin server name
   */
  public String getAsName() {
    return asName;
  }

  /**
   * Admin server name. Note: Possibly temporary as we could find this value through domain home
   * inspection. (Required)
   *
   * @param asName admin server name
   */
  public void setAsName(String asName) {
    this.asName = asName;
  }

  /**
   * Admin server name. Note: Possibly temporary as we could find this value through domain home
   * inspection. (Required)
   *
   * @param asName admin server name
   * @return this
   */
  public DomainSpec withAsName(String asName) {
    this.asName = asName;
    return this;
  }

  /**
   * Administration server port. Note: Possibly temporary as we could find this value through domain
   * home inspection. (Required)
   *
   * @return admin server port
   */
  public Integer getAsPort() {
    return asPort;
  }

  /**
   * Administration server port. Note: Possibly temporary as we could find this value through domain
   * home inspection. (Required)
   *
   * @param asPort admin server port
   */
  public void setAsPort(Integer asPort) {
    this.asPort = asPort;
  }

  /**
   * Administration server port. Note: Possibly temporary as we could find this value through domain
   * home inspection. (Required)
   *
   * @param asPort admin server port
   * @return this
   */
  public DomainSpec withAsPort(Integer asPort) {
    this.asPort = asPort;
    return this;
  }

  /**
   * List of specific T3 channels to export. Named T3 Channels will be exposed using NodePort
   * Services. The internal and external ports must match; therefore, it is required that the
   * channel's port in the WebLogic configuration be a legal and unique value in the Kubernetes
   * cluster's legal NodePort port range.
   *
   * @return exported channels
   */
  public List<String> getExportT3Channels() {
    return exportT3Channels;
  }

  /**
   * List of specific T3 channels to export. Named T3 Channels will be exposed using NodePort
   * Services. The internal and external ports must match; therefore, it is required that the
   * channel's port in the WebLogic configuration be a legal and unique value in the Kubernetes
   * cluster's legal NodePort port range.
   *
   * @param exportT3Channels exported channels
   */
  public void setExportT3Channels(List<String> exportT3Channels) {
    this.exportT3Channels = exportT3Channels;
  }

  /**
   * List of specific T3 channels to export. Named T3 Channels will be exposed using NodePort
   * Services. The internal and external ports must match; therefore, it is required that the
   * channel's port in the WebLogic configuration be a legal and unique value in the Kubernetes
   * cluster's legal NodePort port range.
   *
   * @param exportT3Channels exported channels
   * @return this
   */
  public DomainSpec withExportT3Channels(List<String> exportT3Channels) {
    this.exportT3Channels = exportT3Channels;
    return this;
  }

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED
   * and AUTO.
   *
   * <ul>
   *   <li>NONE indicates that no servers, including the administration server, will be started.
   *   <li>ADMIN indicates that only the administration server will be started.
   *   <li>ALL indicates that all servers in the domain will be started.
   *   <li>SPECIFIED indicates that the administration server will be started and then additionally
   *       only those servers listed under serverStartup or managed servers belonging to cluster
   *       listed under clusterStartup up to the cluster's replicas field will be started.
   *   <li>AUTO indicates that servers will be started exactly as with SPECIFIED, but then managed
   *       servers belonging to clusters not listed under clusterStartup will be started up to the
   *       replicas field.
   * </ul>
   *
   * <p>Defaults to AUTO.
   *
   * @deprecated Use the servers, clusters, clusterDefaults, nonClusteredServerDefaults and
   *     serverDefaults properties.
   * @return startup control
   */
  @Deprecated
  public String getStartupControl() {
    return startupControl;
  }

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED
   * and AUTO.
   *
   * <ul>
   *   <li>NONE indicates that no servers, including the administration server, will be started.
   *   <li>ADMIN indicates that only the administration server will be started.
   *   <li>ALL indicates that all servers in the domain will be started.
   *   <li>SPECIFIED indicates that the administration server will be started and then additionally
   *       only those servers listed under serverStartup or managed servers belonging to cluster
   *       listed under clusterStartup up to the cluster's replicas field will be started.
   *   <li>AUTO indicates that servers will be started exactly as with SPECIFIED, but then managed
   *       servers belonging to clusters not listed under clusterStartup will be started up to the
   *       replicas field.
   * </ul>
   *
   * <p>Defaults to AUTO.
   *
   * @deprecated Use the servers, clusters, clusterDefaults, nonClusteredServerDefaults and
   *     serverDefaults properties.
   * @param startupControl startup control
   */
  @Deprecated
  public void setStartupControl(String startupControl) {
    this.startupControl = startupControl;
  }

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED
   * and AUTO.
   *
   * <ul>
   *   <li>NONE indicates that no servers, including the administration server, will be started.
   *   <li>ADMIN indicates that only the administration server will be started.
   *   <li>ALL indicates that all servers in the domain will be started.
   *   <li>SPECIFIED indicates that the administration server will be started and then additionally
   *       only those servers listed under serverStartup or managed servers belonging to cluster
   *       listed under clusterStartup up to the cluster's replicas field will be started.
   *   <li>AUTO indicates that servers will be started exactly as with SPECIFIED, but then managed
   *       servers belonging to clusters not listed under clusterStartup will be started up to the
   *       replicas field.
   * </ul>
   *
   * <p>Defaults to AUTO.
   *
   * @deprecated Use the servers, clusters, clusterDefaults, nonClusteredServerDefaults and
   *     serverDefaults properties.
   * @param startupControl startup control
   * @return this
   */
  @Deprecated
  public DomainSpec withStartupControl(String startupControl) {
    this.startupControl = startupControl;
    return this;
  }

  /**
   * List of server startup details for selected servers.
   *
   * @deprecated Use the servers, clusters, clusterDefaults, nonClusteredServerDefaults and
   *     serverDefaults properties.
   * @return server startup
   */
  @Deprecated
  public List<ServerStartup> getServerStartup() {
    return serverStartup;
  }

  /**
   * List of server startup details for selected servers.
   *
   * @deprecated Use the servers, clusters, clusterDefaults, nonClusteredServerDefaults and
   *     serverDefaults properties.
   * @param serverStartup server startup
   */
  @Deprecated
  public void setServerStartup(List<ServerStartup> serverStartup) {
    this.serverStartup = serverStartup;
  }

  /**
   * List of server startup details for selected servers.
   *
   * @deprecated Use the servers, clusters, clusterDefaults, nonClusteredServerDefaults and
   *     serverDefaults properties.
   * @param serverStartup server startup
   * @return this
   */
  @Deprecated
  public DomainSpec withServerStartup(List<ServerStartup> serverStartup) {
    this.serverStartup = serverStartup;
    return this;
  }

  /**
   * List of server startup details for selected clusters.
   *
   * @deprecated Use the clusters and clusterDefaults properties.
   * @return cluster startup
   */
  @Deprecated
  public List<ClusterStartup> getClusterStartup() {
    return clusterStartup;
  }

  /**
   * List of server startup details for selected clusters.
   *
   * @deprecated Use the clusters and clusterDefaults properties.
   * @param clusterStartup cluster startup
   */
  @Deprecated
  public void setClusterStartup(List<ClusterStartup> clusterStartup) {
    this.clusterStartup = clusterStartup;
  }

  /**
   * List of server startup details for selected clusters.
   *
   * @deprecated Use the clusters and clusterDefaults properties.
   * @param clusterStartup cluster startup
   * @return this
   */
  @Deprecated
  public DomainSpec withClusterStartup(List<ClusterStartup> clusterStartup) {
    this.clusterStartup = clusterStartup;
    return this;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusterStartup.
   *
   * @deprecated Use the clusterDefaults property's replicas property.
   * @return replicas
   */
  @Deprecated
  public Integer getReplicas() {
    return replicas;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusterStartup.
   *
   * @deprecated Use the clusterDefaults property's replicas property.
   * @param replicas replicas
   */
  @Deprecated
  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  /**
   * The desired number of running managed servers in each WebLogic cluster that is not explicitly
   * configured in clusterStartup.
   *
   * @deprecated Use the clusterDefaults property's replicas property.
   * @param replicas replicas
   * @return this
   */
  @Deprecated
  public DomainSpec withReplicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  /**
   * The default desired state of servers.
   *
   * @return server defaults
   */
  public Server getServerDefaults() {
    return serverDefaults;
  }

  /**
   * The default desired state of servers.
   *
   * @param serverDefaults server defaults
   */
  public void setServerDefaults(Server serverDefaults) {
    this.serverDefaults = serverDefaults;
  }

  /**
   * The default desired state of servers.
   *
   * @param serverDefaults server defaults
   * @return this
   */
  public DomainSpec withServerDefaults(Server serverDefaults) {
    this.serverDefaults = serverDefaults;
    return this;
  }

  /**
   * The default desired state of non-clustered servers.
   *
   * @return server defaults
   */
  public NonClusteredServer getNonClusteredServerDefaults() {
    return nonClusteredServerDefaults;
  }

  /**
   * The default desired state of non-clustered servers.
   *
   * @param nonClusteredServerDefaults non-clustered server defaults
   */
  public void setNonClusteredServerDefaults(NonClusteredServer nonClusteredServerDefaults) {
    this.nonClusteredServerDefaults = nonClusteredServerDefaults;
  }

  /**
   * The default desired state of non-clustered servers.
   *
   * @param nonClusteredServerDefaults non-clustered server defaults
   * @return this
   */
  public DomainSpec withNonClusteredServerDefaults(NonClusteredServer nonClusteredServerDefaults) {
    this.nonClusteredServerDefaults = nonClusteredServerDefaults;
    return this;
  }

  /**
   * Maps the name of a non-clustered server to its desired state.
   *
   * <p>The server property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the server in nonClusteredServers property, and the property has
   *       been specified on that server, then use its value.
   *   <li>If not, and the property has been specified on the nonClusteredServerDefaults property,
   *       then use its value.
   *   <li>If not, and the property value has been specified on the serverDefaults property, then
   *       use its value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @return servers
   */
  public Map<String, NonClusteredServer> getServers() {
    return this.servers;
  }

  /**
   * Maps the name of a non-clustered server to its desired state.
   *
   * <p>The server property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the server in nonClusteredServers property, and the property has
   *       been specified on that server, then use its value.
   *   <li>If not, and the property has been specified on the nonClusteredServerDefaults property,
   *       then use its value.
   *   <li>If not, and the property value has been specified on the serverDefaults property, then
   *       use its value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @param clusters clusters
   */
  public void setServers(Map<String, NonClusteredServer> servers) {
    this.servers = servers;
  }

  /**
   * Maps the name of a non-clustered server to its desired state.
   *
   * <p>The server property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the server in nonClusteredServers property, and the property has
   *       been specified on that server, then use its value.
   *   <li>If not, and the property has been specified on the nonClusteredServerDefaults property,
   *       then use its value.
   *   <li>If not, and the property value has been specified on the serverDefaults property, then
   *       use its value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @param clusters clusters
   * @return this
   */
  public DomainSpec withServers(Map<String, NonClusteredServer> servers) {
    this.servers = servers;
    return this;
  }

  /**
   * Maps the name of a non-clustered server to its desired state.
   *
   * <p>The server property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the server in nonClusteredServers property, and the property has
   *       been specified on that server, then use its value.
   *   <li>If not, and the property has been specified on the nonClusteredServerDefaults property,
   *       then use its value.
   *   <li>If not, and the property value has been specified on the serverDefaults property, then
   *       use its value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @param name cluster name
   * @param cluster cluster
   */
  public void setServer(String name, NonClusteredServer server) {
    this.servers.put(name, server);
  }

  /**
   * Maps the name of a non-clustered server to its desired state.
   *
   * <p>The server property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the server in nonClusteredServers property, and the property has
   *       been specified on that server, then use its value.
   *   <li>If not, and the property has been specified on the nonClusteredServerDefaults property,
   *       then use its value.
   *   <li>If not, and the property value has been specified on the serverDefaults property, then
   *       use its value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @param name name
   * @param cluster cluster
   * @return this
   */
  public DomainSpec withServer(String name, NonClusteredServer server) {
    this.servers.put(name, server);
    return this;
  }

  /**
   * The default desired state of clusters.
   *
   * @return cluster defaults
   */
  public ClusterParams getClusterDefaults() {
    return clusterDefaults;
  }

  /**
   * The default desired state of clusters.
   *
   * @param clusterDefaults cluster defaults
   */
  public void setClusterDefaults(ClusterParams clusterDefaults) {
    this.clusterDefaults = clusterDefaults;
  }

  /**
   * The default desired state of clusters.
   *
   * @param clusterDefaults cluster defaults
   * @return this
   */
  public DomainSpec withClusterDefaults(ClusterParams clusterDefaults) {
    this.clusterDefaults = clusterDefaults;
    return this;
  }

  /**
   * Maps the name of a cluster to its desired state.
   *
   * <p>The cluster property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the cluster in the clusters property, and the property has been
   *       specified on that cluster, then use its value.
   *   <li>If not, and the property has been specified on the clusterDefaults property, then use its
   *       value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @return servers
   */
  public Map<String, Cluster> getClusters() {
    return this.clusters;
  }

  /**
   * Maps the name of a cluster to its desired state.
   *
   * <p>The cluster property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the cluster in the clusters property, and the property has been
   *       specified on that cluster, then use its value.
   *   <li>If not, and the property has been specified on the clusterDefaults property, then use its
   *       value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @param servers servers
   */
  public void setClusters(Map<String, Cluster> clusters) {
    this.clusters = clusters;
  }

  /**
   * Maps the name of a cluster to its desired state.
   *
   * <p>The cluster property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the cluster in the clusters property, and the property has been
   *       specified on that cluster, then use its value.
   *   <li>If not, and the property has been specified on the clusterDefaults property, then use its
   *       value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @param servers servers
   * @return this
   */
  public DomainSpec withClusters(Map<String, Cluster> clusters) {
    this.clusters = clusters;
    return this;
  }

  /**
   * Maps the name of a cluster to its desired state.
   *
   * <p>The cluster property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the cluster in the clusters property, and the property has been
   *       specified on that cluster, then use its value.
   *   <li>If not, and the property has been specified on the clusterDefaults property, then use its
   *       value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @param name server name
   * @param server server
   */
  public void setCluster(String name, Cluster cluster) {
    this.clusters.put(name, cluster);
  }

  /**
   * Maps the name of a cluster to its desired state.
   *
   * <p>The cluster property values use the following defaulting rules:
   *
   * <ol>
   *   <li>If there is an entry for the cluster in the clusters property, and the property has been
   *       specified on that cluster, then use its value.
   *   <li>If not, and the property has been specified on the clusterDefaults property, then use its
   *       value.
   *   <li>If not, then use the default value for the property.
   * </ol>
   *
   * @param name name
   * @param server server
   * @return this
   */
  public DomainSpec withCluster(String name, Cluster cluster) {
    this.clusters.put(name, cluster);
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("domainUID", domainUID)
        .append("domainName", domainName)
        .append("image", image)
        .append("imagePullPolicy", imagePullPolicy)
        .append("adminSecret", adminSecret)
        .append("asName", asName)
        .append("asPort", asPort)
        .append("exportT3Channels", exportT3Channels)
        .append("startupControl", startupControl)
        .append("serverStartup", serverStartup)
        .append("clusterStartup", clusterStartup)
        .append("replicas", replicas)
        .append("serverDefaults", serverDefaults)
        .append("nonClusteredServerDefaults", nonClusteredServerDefaults)
        .append("servers", servers)
        .append("clusterDefaults", clusterDefaults)
        .append("clusters", clusters)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(image)
        .append(imagePullPolicy)
        .append(asName)
        .append(clusterDefaults)
        .append(replicas)
        .append(startupControl)
        .append(domainUID)
        .append(clusterStartup)
        .append(asPort)
        .append(servers)
        .append(domainName)
        .append(exportT3Channels)
        .append(serverStartup)
        .append(serverDefaults)
        .append(adminSecret)
        .append(nonClusteredServerDefaults)
        .append(clusters)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof DomainSpec) == false) {
      return false;
    }
    DomainSpec rhs = ((DomainSpec) other);
    return new EqualsBuilder()
        .append(image, rhs.image)
        .append(imagePullPolicy, rhs.imagePullPolicy)
        .append(asName, rhs.asName)
        .append(clusterDefaults, rhs.clusterDefaults)
        .append(replicas, rhs.replicas)
        .append(startupControl, rhs.startupControl)
        .append(domainUID, rhs.domainUID)
        .append(clusterStartup, rhs.clusterStartup)
        .append(asPort, rhs.asPort)
        .append(servers, rhs.servers)
        .append(domainName, rhs.domainName)
        .append(exportT3Channels, rhs.exportT3Channels)
        .append(serverStartup, rhs.serverStartup)
        .append(serverDefaults, rhs.serverDefaults)
        .append(adminSecret, rhs.adminSecret)
        .append(nonClusteredServerDefaults, rhs.nonClusteredServerDefaults)
        .append(clusters, rhs.clusters)
        .isEquals();
  }
}

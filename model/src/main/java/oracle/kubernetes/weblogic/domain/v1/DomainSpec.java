// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1SecretReference;
import java.util.ArrayList;
import java.util.List;
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
  /** WebLogic Docker image. Defaults to store/oracle/weblogic:12.2.1.3 */
  @SerializedName("image")
  @Expose
  private String image;
  /**
   * Image pull policy. One of Always, Never, IfNotPresent. Defaults to Always if :latest tag is
   * specified, or IfNotPresent otherwise. Cannot be updated. More info:
   * https://kubernetes.io/docs/concepts/containers/images#updating-images
   */
  @SerializedName("imagePullPolicy")
  @Expose
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
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED or
   * AUTO. Defaults to AUTO. NONE indicates that no servers, including the administration server,
   * will be started. ADMIN indicates that only the administration server is started. ALL indicates
   * that all servers in the domain will be started. SPECIFIED indicates that the administration
   * server is started and then additionally only those servers listed under serverStartup or
   * managed servers belonging to clusters listed under clusterStartup up to the cluster's replicas
   * field will be started. AUTO indicates that servers will be started exactly as with SPECIFIED,
   * but then managed servers belonging to clusters not listed under clusterStartup will be started
   * up to the replicas field.
   */
  @SerializedName("startupControl")
  @Expose
  private String startupControl;
  /** List of server startup details for selected servers. */
  @SerializedName("serverStartup")
  @Expose
  @Valid
  private List<ServerStartup> serverStartup = new ArrayList<ServerStartup>();
  /** List of server startup details for selected clusters */
  @SerializedName("clusterStartup")
  @Expose
  @Valid
  private List<ClusterStartup> clusterStartup = new ArrayList<ClusterStartup>();
  /**
   * Replicas is the desired number of managed servers running in each WebLogic cluster that is not
   * configured under clusterStartup. Provided so that administrators can scale the Domain resource.
   * Ignored if startupControl is not AUTO.
   */
  @SerializedName("replicas")
  @Expose
  private Integer replicas;

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

  /**
   * WebLogic Docker image. Defaults to store/oracle/weblogic:12.2.1.3
   *
   * @return image
   */
  public String getImage() {
    return image;
  }

  /**
   * WebLogic Docker image. Defaults to store/oracle/weblogic:12.2.1.3
   *
   * @param image image
   */
  public void setImage(String image) {
    this.image = image;
  }

  /**
   * WebLogic Docker image. Defaults to store/oracle/weblogic:12.2.1.3
   *
   * @param image image
   * @return this
   */
  public DomainSpec withImage(String image) {
    this.image = image;
    return this;
  }

  /**
   * Image pull policy. One of Always, Never, IfNotPresent. Defaults to Always if :latest tag is
   * specified, or IfNotPresent otherwise. Cannot be updated. More info:
   * https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @return image pull policy
   */
  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  /**
   * Image pull policy. One of Always, Never, IfNotPresent. Defaults to Always if :latest tag is
   * specified, or IfNotPresent otherwise. Cannot be updated. More info:
   * https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @param imagePullPolicy image pull policy
   */
  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  /**
   * Image pull policy. One of Always, Never, IfNotPresent. Defaults to Always if :latest tag is
   * specified, or IfNotPresent otherwise. Cannot be updated. More info:
   * https://kubernetes.io/docs/concepts/containers/images#updating-images
   *
   * @param imagePullPolicy image pull policy
   * @return this
   */
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
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED or
   * AUTO. Defaults to AUTO. NONE indicates that no servers, including the administration server,
   * will be started. ADMIN indicates that only the administration server is started. ALL indicates
   * that all servers in the domain will be started. SPECIFIED indicates that the administration
   * server is started and then additionally only those servers listed under serverStartup or
   * managed servers belonging to clusters listed under clusterStartup up to the cluster's replicas
   * field will be started. AUTO indicates that servers will be started exactly as with SPECIFIED,
   * but then managed servers belonging to clusters not listed under clusterStartup will be started
   * up to the replicas field.
   *
   * @return startup control
   */
  public String getStartupControl() {
    return startupControl;
  }

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED or
   * AUTO. Defaults to AUTO. NONE indicates that no servers, including the administration server,
   * will be started. ADMIN indicates that only the administration server is started. ALL indicates
   * that all servers in the domain will be started. SPECIFIED indicates that the administration
   * server is started and then additionally only those servers listed under serverStartup or
   * managed servers belonging to clusters listed under clusterStartup up to the cluster's replicas
   * field will be started. AUTO indicates that servers will be started exactly as with SPECIFIED,
   * but then managed servers belonging to clusters not listed under clusterStartup will be started
   * up to the replicas field.
   *
   * @param startupControl startup control
   */
  public void setStartupControl(String startupControl) {
    this.startupControl = startupControl;
  }

  /**
   * Controls which managed servers will be started. Legal values are NONE, ADMIN, ALL, SPECIFIED or
   * AUTO. Defaults to AUTO. NONE indicates that no servers, including the administration server,
   * will be started. ADMIN indicates that only the administration server is started. ALL indicates
   * that all servers in the domain will be started. SPECIFIED indicates that the administration
   * server is started and then additionally only those servers listed under serverStartup or
   * managed servers belonging to clusters listed under clusterStartup up to the cluster's replicas
   * field will be started. AUTO indicates that servers will be started exactly as with SPECIFIED,
   * but then managed servers belonging to clusters not listed under clusterStartup will be started
   * up to the replicas field.
   *
   * @param startupControl startup control
   * @return this
   */
  public DomainSpec withStartupControl(String startupControl) {
    this.startupControl = startupControl;
    return this;
  }

  /**
   * List of server startup details for selected servers.
   *
   * @return server startup
   */
  public List<ServerStartup> getServerStartup() {
    return serverStartup;
  }

  /**
   * List of server startup details for selected servers.
   *
   * @param serverStartup server startup
   */
  public void setServerStartup(List<ServerStartup> serverStartup) {
    this.serverStartup = serverStartup;
  }

  /**
   * List of server startup details for selected servers.
   *
   * @param serverStartup server startup
   * @return this
   */
  public DomainSpec withServerStartup(List<ServerStartup> serverStartup) {
    this.serverStartup = serverStartup;
    return this;
  }

  /**
   * List of server startup details for selected clusters
   *
   * @return cluster startup
   */
  public List<ClusterStartup> getClusterStartup() {
    return clusterStartup;
  }

  /**
   * List of server startup details for selected clusters
   *
   * @param clusterStartup cluster startup
   */
  public void setClusterStartup(List<ClusterStartup> clusterStartup) {
    this.clusterStartup = clusterStartup;
  }

  /**
   * List of server startup details for selected clusters
   *
   * @param clusterStartup cluster startup
   * @return this
   */
  public DomainSpec withClusterStartup(List<ClusterStartup> clusterStartup) {
    this.clusterStartup = clusterStartup;
    return this;
  }

  /**
   * Replicas is the desired number of managed servers running in each WebLogic cluster that is not
   * configured under clusterStartup. Provided so that administrators can scale the Domain resource.
   * Ignored if startupControl is not AUTO.
   *
   * @return replicas
   */
  public Integer getReplicas() {
    return replicas;
  }

  /**
   * Replicas is the desired number of managed servers running in each WebLogic cluster that is not
   * configured under clusterStartup. Provided so that administrators can scale the Domain resource.
   * Ignored if startupControl is not AUTO.
   *
   * @param replicas replicas
   */
  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  /**
   * Replicas is the desired number of managed servers running in each WebLogic cluster that is not
   * configured under clusterStartup. Provided so that administrators can scale the Domain resource.
   * Ignored if startupControl is not AUTO.
   *
   * @param replicas replicas
   * @return this
   */
  public DomainSpec withReplicas(Integer replicas) {
    this.replicas = replicas;
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
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(image)
        .append(imagePullPolicy)
        .append(asName)
        .append(replicas)
        .append(startupControl)
        .append(domainUID)
        .append(clusterStartup)
        .append(asPort)
        .append(domainName)
        .append(exportT3Channels)
        .append(serverStartup)
        .append(adminSecret)
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
        .append(replicas, rhs.replicas)
        .append(startupControl, rhs.startupControl)
        .append(domainUID, rhs.domainUID)
        .append(clusterStartup, rhs.clusterStartup)
        .append(asPort, rhs.asPort)
        .append(domainName, rhs.domainName)
        .append(exportT3Channels, rhs.exportT3Channels)
        .append(serverStartup, rhs.serverStartup)
        .append(adminSecret, rhs.adminSecret)
        .isEquals();
  }
}

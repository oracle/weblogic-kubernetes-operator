// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.Map;
import java.util.Objects;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.ServerStartPolicy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Configuration values shared by multiple levels: domain, admin server, managed server, and
 * cluster.
 *
 * @since 2.0
 */
public abstract class BaseConfiguration extends BaseServerPodConfiguration {

  @Description(
      "Customization affecting the generation of ClusterIP Services for WebLogic Server instances.")
  @SerializedName("serverService")
  @Expose
  private final ServerService serverService = new ServerService();

  /**
   * Tells the operator whether the customer wants to restart the server pods. The value can be any
   * String and it can be defined on domain, cluster or server to restart the different pods. After
   * the value is added, the corresponding pods will be terminated and created again. If customer
   * modifies the value again after the pods were recreated, then the pods will again be terminated
   * and recreated.
   *
   * @since 2.0
   */
  @Description(
      "Changes to this field cause the operator to restart WebLogic Server instances. More info: "
      + "https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-lifecycle/startup/#restarting-servers.")
  private String restartVersion;

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(BaseConfiguration other) {
    if (other == null) {
      return;
    }

    if (overrideStartPolicyFrom(other)) {
      setServerStartPolicy(other.getServerStartPolicy());
    }

    super.fillInFrom(other);
    serverService.fillInFrom(other.serverService);
  }

  private boolean overrideStartPolicyFrom(BaseConfiguration other) {
    if (other.isStartAdminServerOnly()) {
      return false;
    }
    return getServerStartPolicy() == null || other.isStartNever();
  }

  boolean isStartAdminServerOnly() {
    return Objects.equals(getServerStartPolicy(), ServerStartPolicy.ADMIN_ONLY);
  }

  private boolean isStartNever() {
    return Objects.equals(getServerStartPolicy(), ServerStartPolicy.NEVER);
  }

  public abstract ServerStartPolicy getServerStartPolicy();

  /**
   * Tells the operator whether the customer wants the server to be running. For non-clustered
   * servers - the operator will start it if the policy isn't Never. For clustered servers - the
   * operator will start it if the policy is Always or the policy is IfNeeded and the server needs
   * to be started to get to the cluster's replica count..
   *
   * @since 2.0
   * @param serverStartPolicy start policy
   */
  public abstract void setServerStartPolicy(ServerStartPolicy serverStartPolicy);

  public Boolean isPrecreateServerService() {
    return serverService.isPrecreateService();
  }

  void setPrecreateServerService(boolean value) {
    serverService.setIsPrecreateService(value);
  }

  public Map<String, String> getServiceLabels() {
    return serverService.getLabels();
  }

  void addServiceLabel(String name, String value) {
    serverService.addLabel(name, value);
  }

  public Map<String, String> getServiceAnnotations() {
    return serverService.getAnnotations();
  }

  void addServiceAnnotation(String name, String value) {
    serverService.addAnnotations(name, value);
  }

  String getRestartVersion() {
    return restartVersion;
  }

  public void setRestartVersion(String restartVersion) {
    this.restartVersion = restartVersion;
  }

  @Override
  public BaseServerPodConfiguration getServerPodSpec() {
    return super.getServerPodSpec();
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverPod", super.getServerPod())
        .append("serverService", serverService)
        .append("restartVersion", restartVersion)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BaseConfiguration that = (BaseConfiguration) o;

    return new EqualsBuilder()
        .append(super.getServerPod(), that.getServerPod())
        .append(serverService, that.serverService)
        .append(restartVersion, that.restartVersion)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(super.getServerPod())
        .append(serverService)
        .append(restartVersion)
        .toHashCode();
  }
}

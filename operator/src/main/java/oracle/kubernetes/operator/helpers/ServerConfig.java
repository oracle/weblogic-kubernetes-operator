// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ServerConfig describes the desired state of a server. */
public class ServerConfig {

  protected static final String SERVER_START_POLICY_ALWAYS = "ALWAYS";
  protected static final String SERVER_START_POLICY_NEVER = "NEVER";

  private String serverName;
  private String restartedLabel;
  private int nodePort;
  private List<V1EnvVar> env = null;
  private String image;
  private String imagePullPolicy;
  private List<V1LocalObjectReference> imagePullSecrets = null;

  /**
   * Gets server's name.
   *
   * @return server's name
   */
  public String getServerName() {
    return serverName;
  }

  /**
   * Sets the server's name.
   *
   * @param serverName the server's name.
   */
  public void setServerName(String serverName) {
    this.serverName = serverName;
  }

  /**
   * Sets server's name.
   *
   * @param serverName the server's name.
   * @return this
   */
  public ServerConfig withServerName(String serverName) {
    this.serverName = serverName;
    return this;
  }

  /**
   * Gets the label that indicates that a server has been restarted. If a running server's pod does
   * not have this label, then the operator needs to restart the server and attach this label to it.
   *
   * @return restarted label
   */
  public String getRestartedLabel() {
    return restartedLabel;
  }

  /**
   * Sets the label that indicates that a server has been restarted.
   *
   * @param restartedLabel restarted label
   */
  public void setRestartedLabel(String restartedLabel) {
    this.restartedLabel = restartedLabel;
  }

  /**
   * Sets the label that indicates that a server has been restarted.
   *
   * @param restartedLabel restarted label
   * @return this
   */
  public ServerConfig withRestartedLabel(String restartedLabel) {
    this.restartedLabel = restartedLabel;
    return this;
  }

  /**
   * Sets the NodePort for the server. The port on each node on which this managed server will be
   * exposed. If specified, this value must be an unused port. By default, the server will not be
   * exposed outside the Kubernetes cluster.
   *
   * @return node port
   */
  public int getNodePort() {
    return nodePort;
  }

  /**
   * Gets the NodePort for the server.
   *
   * @param nodePort node port
   */
  public void setNodePort(int nodePort) {
    this.nodePort = nodePort;
  }

  /**
   * Gets the NodePort for the server.
   *
   * @param nodePort node port
   * @return this
   */
  public ServerConfig withNodePort(int nodePort) {
    this.nodePort = nodePort;
    return this;
  }

  /**
   * Sets the environment variables to pass while starting this server. If not specified, then the
   * environment variables in config.xml will be used instead.
   *
   * @return env
   */
  public List<V1EnvVar> getEnv() {
    return env;
  }

  /**
   * Sets the environment variables to pass while starting this server.
   *
   * @param env env
   */
  public void setEnv(List<V1EnvVar> env) {
    this.env = env;
  }

  /**
   * Sets the environment variables to pass while starting this server.
   *
   * @param env env
   * @return this
   */
  public ServerConfig withEnv(List<V1EnvVar> env) {
    this.env = env;
    return this;
  }

  /**
   * Gets the WebLogic Docker image.
   *
   * @return image
   */
  public String getImage() {
    return image;
  }

  /**
   * Sets the WebLogic Docker image.
   *
   * @param image image
   */
  public void setImage(String image) {
    this.image = image;
  }

  /**
   * Sets the WebLogic Docker image.
   *
   * @param image image
   * @return this
   */
  public ServerConfig withImage(String image) {
    this.image = image;
    return this;
  }

  /**
   * Gets the image pull policy. Legal values are Always, Never and IfNotPresent.
   *
   * @return image pull policy
   */
  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  /**
   * Sets the image pull policy.
   *
   * @param imagePullPolicy image pull policy
   */
  public void setImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  /**
   * Sets the image pull policy.
   *
   * @param imagePullPolicy image pull policy
   * @return this
   */
  public ServerConfig withImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  /**
   * Gets the list of references to secrets in the same namespace to use for pulling the WebLogic
   * Docker image.
   *
   * @return image pull secrets
   */
  public List<V1LocalObjectReference> getImagePullSecrets() {
    return imagePullSecrets;
  }

  /**
   * Sets the list of references to secrets in the same namespace to use for pulling the WebLogic
   * Docker image.
   *
   * @param imagePullSecrets image pull secrets
   */
  public void setImagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
  }

  /**
   * Sets the list of references to secrets in the same namespace to use for pulling the WebLogic
   * Docker image.
   *
   * @param imagePullSecrets image pull secrets
   * @return this
   */
  public ServerConfig withImagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverName", serverName)
        .append("restartedLabel", restartedLabel)
        .append("nodePort", nodePort)
        .append("env", env)
        .append("image", image)
        .append("imagePullPolicy", imagePullPolicy)
        .append("imagePullSecrets", imagePullSecrets)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(serverName)
        .append(image)
        .append(imagePullPolicy)
        .append(imagePullSecrets)
        .append(restartedLabel)
        .append(env)
        .append(nodePort)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof ServerConfig) == false) {
      return false;
    }
    ServerConfig rhs = ((ServerConfig) other);
    return new EqualsBuilder()
        .append(serverName, rhs.serverName)
        .append(image, rhs.image)
        .append(imagePullPolicy, rhs.imagePullPolicy)
        .append(imagePullSecrets, rhs.imagePullSecrets)
        .append(restartedLabel, rhs.restartedLabel)
        .append(env, rhs.env)
        .append(nodePort, rhs.nodePort)
        .isEquals();
  }
}

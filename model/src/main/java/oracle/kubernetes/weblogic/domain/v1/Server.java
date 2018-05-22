// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1LocalObjectReference;
import java.util.List;
import javax.validation.Valid;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** Server describes the desired state of a server. */
public class Server {

  /**
   * The state the server should be started in when the server needs to be started. Legal values are
   * RUNNING and ADMIN.
   *
   * <p>Defaults to RUNNING.
   */
  @SerializedName("startedServerState")
  @Expose
  private String startedServerState;

  /**
   * Used both to indicate that a server should be restarted and to tell when the operator has
   * restarted the server.
   *
   * <ul>
   *   <li>If not null, and if there is a pod running for the server, and its metadata does not have
   *       a weblogic.serverStarted label with this value, then the operator will restart the server
   *       by deleting the old pod and creating a new one with this label.
   *   <li>If not null, and there is a pod running for the server, but its metadata already has a
   *       weblogic.serverStarted label with this value, then the operator will continue to let the
   *       pod run.
   *   <li>If not null, and there is no pod running for the server, and the operator needs to start
   *       the server, then the operator will start the server by creating a new pod with this
   *       label.
   *   <li>If null, and there is a pod running for the server, and its metadata has a
   *       weblogic.serverStarted label, the operator will continue to let the pod run, and remove
   *       its weblogic.serverStarted label.
   *   <li>If null, and there is a pod running for the server, and its metadata does not have a
   *       weblogic.serverStarted label, the operator will continue to let the pod run.
   *   <li>If null, and there is no pod running for the server, and the operator needs to start the
   *       server, then the operator will start the server by creating a new pod without this label.
   * </ul>
   *
   * <p>Defaults to null.
   */
  @SerializedName("restartedLabel")
  @Expose
  private String restartedLabel;

  /**
   * The port on each node on which this managed server will be exposed. If specified, this value
   * must be an unused port.
   *
   * <p>By default, the server will not be exposed outside the Kubernetes cluster.
   */
  @SerializedName("nodePort")
  @Expose
  private Integer nodePort;

  /**
   * Environment variables to pass while starting this server.
   *
   * <p>If not specified, then the environment variables in config.xml will be used instead.
   */
  @SerializedName("env")
  @Expose
  @Valid
  private List<V1EnvVar> env = null;

  /**
   * The WebLogic Docker image.
   *
   * <p>Defaults to store/oracle/weblogic:12.2.1.3.
   */
  @SerializedName("image")
  @Expose
  private String image;

  /**
   * The image pull policy for the WebLogic Docker image. Legal values are Always, Never and
   * IfNotPresent.
   *
   * <p>Defaults to Always if image ends in :latest, IfNotPresent otherwise.
   *
   * <p>More info: https://kubernetes.io/docs/concepts/containers/images#updating-images
   */
  @SerializedName("imagePullPolicy")
  @Expose
  private String imagePullPolicy;

  /**
   * An optional list of references to secrets in the same namespace to use for pulling the WebLogic
   * Docker image.
   */
  @SerializedName("imagePullSecrets")
  @Expose
  private List<V1LocalObjectReference> imagePullSecrets = null;

  /**
   * Controls how the operator will stop this server. Legal values are GRACEFUL_SHUTDOWN and
   * FORCED_SHUTDOWN.
   *
   * <p>Defaults to FORCED_SHUTDOWN.
   */
  @SerializedName("shutdownPolicy")
  @Expose
  private String shutdownPolicy;

  /**
   * Number of seconds to wait before aborting inflight work and gracefully shutting down the
   * server.
   *
   * <p>Defaults to 0.
   */
  @SerializedName("gracefulShutdownTimeout")
  @Expose
  private Integer gracefulShutdownTimeout;

  /**
   * Whether to ignore pending HTTP sessions during inflight work handling when gracefully shutting
   * down the server.
   *
   * <p>Defaults to false.
   */
  @SerializedName("gracefulShutdownIgnoreSessions")
  @Expose
  private Boolean gracefulShutdownIgnoreSessions;

  /**
   * Whether to wait for all HTTP sessions during inflight work handling when gracefully shutting
   * down the server.
   *
   * <p>Defaults to false.
   */
  @SerializedName("gracefulShutdownWaitForSessions")
  @Expose
  private Boolean gracefulShutdownWaitForSessions;

  /**
   * The state the server should be started in when the server needs to be started. Legal values are
   * RUNNING and ADMIN.
   *
   * <p>Defaults to RUNNING.
   *
   * @return started server state
   */
  public String getStartedServerState() {
    return startedServerState;
  }

  /**
   * The state the server should be started in when the server needs to be started. Legal values are
   * RUNNING and ADMIN.
   *
   * <p>Defaults to RUNNING.
   *
   * @param startedServerState started server state
   */
  public void setStartedServerState(String startedServerState) {
    this.startedServerState = startedServerState;
  }

  /**
   * The state the server should be started in when the server needs to be started. Legal values are
   * RUNNING and ADMIN.
   *
   * <p>Defaults to RUNNING.
   *
   * @param startedServerState started server state
   * @return this
   */
  public Server withStartedServerState(String startedServerState) {
    this.startedServerState = startedServerState;
    return this;
  }

  /*
   * Used both to indicate that a server should be restarted and to tell when the operator has
   * restarted the server.
   *
   * <ul>
   *   <li>If not null, and if there is a pod running for the server, and its metadata does not have
   *       a weblogic.serverStarted label with this value, then the operator will restart the server
   *       by deleting the old pod and creating a new one with this label.
   *   <li>If not null, and there is a pod running for the server, but its metadata already has a
   *       weblogic.serverStarted label with this value, then the operator will continue to let the
   *       pod run.
   *   <li>If not null, and there is no pod running for the server, and the operator needs to start
   *       the server, then the operator will start the server by creating a new pod with this
   *       label.
   *   <li>If null, and there is a pod running for the server, and its metadata has a
   *       weblogic.serverStarted label, the operator will continue to let the pod run, and remove
   *       its weblogic.serverStarted label.
   *   <li>If null, and there is a pod running for the server, and its metadata does not have a
   *       weblogic.serverStarted label, the operator will continue to let the pod run.
   *   <li>If null, and there is no pod running for the server, and the operator needs to start the
   *       server, then the operator will start the server by creating a new pod without this label.
   * </ul>
   *
   * <p>Defaults to null.
   *
   * @return restarted label
   */
  public String getRestartedLabel() {
    return restartedLabel;
  }

  /*
   * Used both to indicate that a server should be restarted and to tell when the operator has
   * restarted the server.
   *
   * <ul>
   *   <li>If not null, and if there is a pod running for the server, and its metadata does not have
   *       a weblogic.serverStarted label with this value, then the operator will restart the server
   *       by deleting the old pod and creating a new one with this label.
   *   <li>If not null, and there is a pod running for the server, but its metadata already has a
   *       weblogic.serverStarted label with this value, then the operator will continue to let the
   *       pod run.
   *   <li>If not null, and there is no pod running for the server, and the operator needs to start
   *       the server, then the operator will start the server by creating a new pod with this
   *       label.
   *   <li>If null, and there is a pod running for the server, and its metadata has a
   *       weblogic.serverStarted label, the operator will continue to let the pod run, and remove
   *       its weblogic.serverStarted label.
   *   <li>If null, and there is a pod running for the server, and its metadata does not have a
   *       weblogic.serverStarted label, the operator will continue to let the pod run.
   *   <li>If null, and there is no pod running for the server, and the operator needs to start the
   *       server, then the operator will start the server by creating a new pod without this label.
   * </ul>
   *
   * <p>Defaults to null.
   *
   * @param restartedLabel restarted label
   */
  public void setRestartedLabel(String restartedLabel) {
    this.restartedLabel = restartedLabel;
  }

  /*
   * Used both to indicate that a server should be restarted and to tell when the operator has
   * restarted the server.
   *
   * <ul>
   *   <li>If not null, and if there is a pod running for the server, and its metadata does not have
   *       a weblogic.serverStarted label with this value, then the operator will restart the server
   *       by deleting the old pod and creating a new one with this label.
   *   <li>If not null, and there is a pod running for the server, but its metadata already has a
   *       weblogic.serverStarted label with this value, then the operator will continue to let the
   *       pod run.
   *   <li>If not null, and there is no pod running for the server, and the operator needs to start
   *       the server, then the operator will start the server by creating a new pod with this
   *       label.
   *   <li>If null, and there is a pod running for the server, and its metadata has a
   *       weblogic.serverStarted label, the operator will continue to let the pod run, and remove
   *       its weblogic.serverStarted label.
   *   <li>If null, and there is a pod running for the server, and its metadata does not have a
   *       weblogic.serverStarted label, the operator will continue to let the pod run.
   *   <li>If null, and there is no pod running for the server, and the operator needs to start the
   *       server, then the operator will start the server by creating a new pod without this label.
   * </ul>
   *
   * <p>Defaults to null.
   *
   * @param restartedLabel restarted label
   * @return this
   */
  public Server withRestartedLabel(String restartedLabel) {
    this.restartedLabel = restartedLabel;
    return this;
  }

  /**
   * The port on each node on which this managed server will be exposed. If specified, this value
   * must be an unused port.
   *
   * <p>By default, the server will not be exposed outside the Kubernetes cluster.
   *
   * @return node port
   */
  public Integer getNodePort() {
    return nodePort;
  }

  /**
   * The port on each node on which this managed server will be exposed. If specified, this value
   * must be an unused port.
   *
   * <p>By default, the server will not be exposed outside the Kubernetes cluster.
   *
   * @param nodePort node port
   */
  public void setNodePort(Integer nodePort) {
    this.nodePort = nodePort;
  }

  /**
   * The port on each node on which this managed server will be exposed. If specified, this value
   * must be an unused port.
   *
   * <p>By default, the server will not be exposed outside the Kubernetes cluster.
   *
   * @param nodePort node port
   */
  public Server withNodePort(Integer nodePort) {
    this.nodePort = nodePort;
    return this;
  }

  /**
   * Environment variables to pass while starting this server.
   *
   * <p>If not specified, then the environment variables in config.xml will be used instead.
   *
   * @return env
   */
  public List<V1EnvVar> getEnv() {
    return env;
  }

  /**
   * Environment variables to pass while starting this server.
   *
   * <p>If not specified, then the environment variables in config.xml will be used instead.
   *
   * @param env env
   */
  public void setEnv(List<V1EnvVar> env) {
    this.env = env;
  }

  /**
   * Environment variables to pass while starting this server.
   *
   * <p>If not specified, then the environment variables in config.xml will be used instead.
   *
   * @param env env
   * @return this
   */
  public Server withEnv(List<V1EnvVar> env) {
    this.env = env;
    return this;
  }

  /**
   * The WebLogic Docker image.
   *
   * <p>Defaults to store/oracle/weblogic:12.2.1.3.
   *
   * @return image
   */
  public String getImage() {
    return image;
  }

  /**
   * The WebLogic Docker image.
   *
   * <p>Defaults to store/oracle/weblogic:12.2.1.3.
   *
   * @param image image
   */
  public void setImage(String image) {
    this.image = image;
  }

  /**
   * The WebLogic Docker image.
   *
   * <p>Defaults to store/oracle/weblogic:12.2.1.3.
   *
   * @param image image
   * @return this
   */
  public Server withImage(String image) {
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
   * @return image pull policy
   */
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
   * @param imagePullPolicy image pull policy
   */
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
   * @param imagePullPolicy image pull policy
   * @return this
   */
  public Server withImagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  /**
   * An optional list of references to secrets in the same namespace to use for pulling the WebLogic
   * Docker image.
   *
   * @return image pull secrets
   */
  public List<V1LocalObjectReference> getImagePullSecrets() {
    return imagePullSecrets;
  }

  /**
   * An optional list of references to secrets in the same namespace to use for pulling the WebLogic
   * Docker image.
   *
   * @param image pull secrets
   */
  public void setImagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
  }

  /**
   * An optional list of references to secrets in the same namespace to use for pulling the WebLogic
   * Docker image.
   *
   * @param image pull secrets
   * @return this
   */
  public Server withImagePullSecrets(List<V1LocalObjectReference> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
    return this;
  }

  /**
   * Controls how the operator will stop this server. Legal values are GRACEFUL_SHUTDOWN and
   * FORCED_SHUTDOWN.
   *
   * <p>Defaults to FORCED_SHUTDOWN.
   *
   * @return shutdown policy
   */
  public String getShutdownPolicy() {
    return shutdownPolicy;
  }

  /**
   * Controls how the operator will stop this server. Legal values are GRACEFUL_SHUTDOWN and
   * FORCED_SHUTDOWN.
   *
   * <p>Defaults to FORCED_SHUTDOWN.
   *
   * @param shutdownPolicy shutdown policy
   */
  public void setShutdownPolicy(String shutdownPolicy) {
    this.shutdownPolicy = shutdownPolicy;
  }

  /**
   * Controls how the operator will stop this server. Legal values are GRACEFUL_SHUTDOWN and
   * FORCED_SHUTDOWN.
   *
   * <p>Defaults to FORCED_SHUTDOWN.
   *
   * @param shutdownPolicy shutdown policy
   * @return this
   */
  public Server withShutdownPolicy(String shutdownPolicy) {
    this.shutdownPolicy = shutdownPolicy;
    return this;
  }

  /**
   * Number of seconds to wait before aborting inflight work and gracefully shutting down the
   * server.
   *
   * <p>Defaults to 0.
   *
   * @return graceful shutdown timeout
   */
  public Integer getGracefulShutdownTimeout() {
    return gracefulShutdownTimeout;
  }

  /**
   * Number of seconds to wait before aborting inflight work and gracefully shutting down the
   * server.
   *
   * <p>Defaults to 0.
   *
   * @param gracefulShutdownTimeout graceful timeout timeout
   */
  public void setGracefulShutdownTimeout(Integer gracefulShutdownTimeout) {
    this.gracefulShutdownTimeout = gracefulShutdownTimeout;
  }

  /**
   * Number of seconds to wait before aborting inflight work and gracefully shutting down the
   * server.
   *
   * <p>Defaults to 0.
   *
   * @param gracefulShutdownTimeout graceful timeout timeout
   * @return this
   */
  public Server withGracefulShutdownTimeout(Integer gracefulShutdownTimeout) {
    this.gracefulShutdownTimeout = gracefulShutdownTimeout;
    return this;
  }

  /**
   * Whether to ignore pending HTTP sessions during inflight work handling when gracefully shutting
   * down the server.
   *
   * <p>Defaults to false.
   *
   * @return graceful shutdown ignore sessions
   */
  public Boolean getGracefulShutdownIgnoreSessions() {
    return gracefulShutdownIgnoreSessions;
  }

  /**
   * Whether to ignore pending HTTP sessions during inflight work handling when gracefully shutting
   * down the server.
   *
   * <p>Defaults to false.
   *
   * @parama gracefulShutdownIgnoreSessions graceful shutdown ignore sessions
   */
  public void setGracefulShutdownIgnoreSessions(Boolean gracefulShutdownIgnoreSessions) {
    this.gracefulShutdownIgnoreSessions = gracefulShutdownIgnoreSessions;
  }

  /**
   * Whether to ignore pending HTTP sessions during inflight work handling when gracefully shutting
   * down the server.
   *
   * <p>Defaults to false.
   *
   * @parama gracefulShutdownIgnoreSessions graceful shutdown ignore sessions
   * @return this
   */
  public Server withGracefulShutdownIgnoreSessions(Boolean gracefulShutdownIgnoreSessions) {
    this.gracefulShutdownIgnoreSessions = gracefulShutdownIgnoreSessions;
    return this;
  }

  /**
   * Whether to wait for all HTTP sessions during inflight work handling when gracefully shutting
   * down the server.
   *
   * <p>Defaults to false.
   *
   * @return graceful shutdown wait for sessions
   */
  public Boolean getGracefulShutdownWaitForSessions() {
    return gracefulShutdownWaitForSessions;
  }

  /**
   * Whether to wait for all HTTP sessions during inflight work handling when gracefully shutting
   * down the server.
   *
   * <p>Defaults to false.
   *
   * @param gracefulShutdownWaitForSessions graceful shutdown wait for sessions
   */
  public void setGracefulShutdownWaitForSessions(Boolean gracefulShutdownWaitForSessions) {
    this.gracefulShutdownWaitForSessions = gracefulShutdownWaitForSessions;
  }

  /**
   * Whether to wait for all HTTP sessions during inflight work handling when gracefully shutting
   * down the server.
   *
   * <p>Defaults to false.
   *
   * @param gracefulShutdownWaitForSessions graceful shutdown wait for sessions
   * @return this
   */
  public Server withGracefulShutdownWaitForSessions(Boolean gracefulShutdownWaitForSessions) {
    this.gracefulShutdownWaitForSessions = gracefulShutdownWaitForSessions;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("startedServerState", startedServerState)
        .append("restartedLabel", restartedLabel)
        .append("nodePort", nodePort)
        .append("env", env)
        .append("image", image)
        .append("imagePullPolicy", imagePullPolicy)
        .append("imagePullSecrets", imagePullSecrets)
        .append("shutdownPolicy", shutdownPolicy)
        .append("gracefulShutdownTimeout", gracefulShutdownTimeout)
        .append("gracefulShutdownIgnoreSessions", gracefulShutdownIgnoreSessions)
        .append("gracefulShutdownWaitForSessions", gracefulShutdownWaitForSessions)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(gracefulShutdownTimeout)
        .append(image)
        .append(imagePullPolicy)
        .append(startedServerState)
        .append(imagePullSecrets)
        .append(restartedLabel)
        .append(gracefulShutdownIgnoreSessions)
        .append(env)
        .append(gracefulShutdownWaitForSessions)
        .append(nodePort)
        .append(shutdownPolicy)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof Server) == false) {
      return false;
    }
    Server rhs = ((Server) other);
    return new EqualsBuilder()
        .append(gracefulShutdownTimeout, rhs.gracefulShutdownTimeout)
        .append(image, rhs.image)
        .append(imagePullPolicy, rhs.imagePullPolicy)
        .append(startedServerState, rhs.startedServerState)
        .append(imagePullSecrets, rhs.imagePullSecrets)
        .append(restartedLabel, rhs.restartedLabel)
        .append(gracefulShutdownIgnoreSessions, rhs.gracefulShutdownIgnoreSessions)
        .append(env, rhs.env)
        .append(gracefulShutdownWaitForSessions, rhs.gracefulShutdownWaitForSessions)
        .append(nodePort, rhs.nodePort)
        .append(shutdownPolicy, rhs.shutdownPolicy)
        .isEquals();
  }
}

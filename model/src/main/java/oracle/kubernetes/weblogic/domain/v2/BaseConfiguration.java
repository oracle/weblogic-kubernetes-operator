// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import static java.util.Collections.emptyList;

import com.google.common.base.Strings;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1LocalObjectReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.validation.Valid;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Configuration values shared by multiple levels: domain, admin server, managed server, and
 * cluster.
 *
 * @since 2.0
 */
public abstract class BaseConfiguration {
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
   * Reference to the secret used to authenticate a request for an image pull.
   *
   * <p>More info:
   * https://kubernetes.io/docs/concepts/containers/images/#referring-to-an-imagepullsecrets-on-a-pod
   */
  @SerializedName("imagePullSecret")
  @Expose
  private V1LocalObjectReference imagePullSecret;

  /**
   * Environment variables to pass while starting a server.
   *
   * @since 2.0
   */
  @SerializedName("env")
  @Expose
  @Valid
  private List<V1EnvVar> env = new ArrayList<>();

  /**
   * Desired startup state. Legal values are RUNNING or ADMIN.
   *
   * @since 2.0
   */
  @SerializedName("serverStartState")
  @Expose
  private String serverStartState;

  /**
   * Tells the operator whether the customer wants the server to be running. For non-clustered
   * servers - the operator will start it if the policy isn't NEVER. For clustered servers - the
   * operator will start it if the policy is ALWAYS or the policy is IF_NEEDED and the server needs
   * to be started to get to the cluster's replica count..
   *
   * @since 2.0
   */
  @SerializedName("serverStartPolicy")
  @Expose
  private String serverStartPolicy;

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(BaseConfiguration other) {
    if (other == null) return;

    if (image == null) image = other.getImage();
    if (imagePullPolicy == null) imagePullPolicy = other.getImagePullPolicy();
    if (imagePullSecret == null) imagePullSecret = other.getImagePullSecret();
    if (serverStartState == null) serverStartState = other.getServerStartState();
    if (serverStartPolicy == null) serverStartPolicy = other.getServerStartPolicy();

    for (V1EnvVar var : getV1EnvVars(other)) addIfMissing(var);
  }

  private List<V1EnvVar> getV1EnvVars(BaseConfiguration configuration) {
    return Optional.ofNullable(configuration.getEnv()).orElse(emptyList());
  }

  private void addIfMissing(V1EnvVar var) {
    if (!hasEnvVar(var.getName())) addEnvVar(var);
  }

  private boolean hasEnvVar(String name) {
    if (env == null) return false;
    for (V1EnvVar var : env) {
      if (var.getName().equals(name)) return true;
    }
    return false;
  }

  /**
   * Returns true if any version 2 configuration fields are specified.
   *
   * @return whether there is version 2 configuration in this instance
   */
  protected boolean hasV2Fields() {
    return serverStartState != null || !env.isEmpty();
  }

  @Nullable
  public String getImage() {
    return image;
  }

  public void setImage(@Nullable String image) {
    this.image = image;
  }

  @Nullable
  public String getImagePullPolicy() {
    return imagePullPolicy;
  }

  public void setImagePullPolicy(@Nullable String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
  }

  @Nullable
  public V1LocalObjectReference getImagePullSecret() {
    return hasImagePullSecret() ? imagePullSecret : null;
  }

  private boolean hasImagePullSecret() {
    return imagePullSecret != null && !Strings.isNullOrEmpty(imagePullSecret.getName());
  }

  public void setImagePullSecret(@Nullable V1LocalObjectReference imagePullSecret) {
    this.imagePullSecret = imagePullSecret;
  }

  @Nullable
  String getServerStartState() {
    return serverStartState;
  }

  void setServerStartState(@Nullable String serverStartState) {
    this.serverStartState = serverStartState;
  }

  @Nullable
  public List<V1EnvVar> getEnv() {
    return env;
  }

  public void setEnv(@Nullable List<V1EnvVar> env) {
    this.env = env;
  }

  void addEnvironmentVariable(String name, String value) {
    addEnvVar(new V1EnvVar().name(name).value(value));
  }

  private void addEnvVar(V1EnvVar var) {
    if (env == null) env = new ArrayList<>();
    env.add(var);
  }

  void setServerStartPolicy(String serverStartPolicy) {
    this.serverStartPolicy = serverStartPolicy;
  }

  String getServerStartPolicy() {
    return Optional.ofNullable(serverStartPolicy).orElse("undefined");
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("image", image)
        .append("imagePullPolicy", imagePullPolicy)
        .append("imagePullSecret", imagePullSecret)
        .append("serverStartState", serverStartState)
        .append("serverStartPolicy", serverStartPolicy)
        .append("env", env)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;

    if (o == null || getClass() != o.getClass()) return false;

    BaseConfiguration that = (BaseConfiguration) o;

    return new EqualsBuilder()
        .append(image, that.image)
        .append(imagePullPolicy, that.imagePullPolicy)
        .append(imagePullSecret, that.imagePullSecret)
        .append(env, that.env)
        .append(serverStartState, that.serverStartState)
        .append(serverStartPolicy, that.serverStartPolicy)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(image)
        .append(imagePullPolicy)
        .append(imagePullSecret)
        .append(env)
        .append(serverStartState)
        .append(serverStartPolicy)
        .toHashCode();
  }
}

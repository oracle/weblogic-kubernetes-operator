// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1EnvVar;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * ClusterStartup describes the desired startup state and passed environment variables for a
 * specific cluster server.
 *
 * @deprecated Use the DomainSpec's clusters and clusterDefaults properties.
 */
@Deprecated
public class ClusterStartup {

  /**
   * Desired startup state for any managed server started in this cluster. Legal values are RUNNING
   * or ADMIN.
   */
  @SerializedName("desiredState")
  @Expose
  private String desiredState;
  /**
   * Name of specific cluster to start. Managed servers in the cluster will be started beginning
   * with replicas instances. (Required)
   */
  @SerializedName("clusterName")
  @Expose
  @NotNull
  private String clusterName;
  /** Replicas is the desired number of managed servers running for this cluster. */
  @SerializedName("replicas")
  @Expose
  private Integer replicas;
  /** Environment variables to pass while starting managed servers in this cluster. */
  @SerializedName("env")
  @Expose
  @Valid
  private List<V1EnvVar> env = new ArrayList<V1EnvVar>();

  /**
   * Desired startup state for any managed server started in this cluster. Legal values are RUNNING
   * or ADMIN.
   *
   * @return Desired state
   */
  public String getDesiredState() {
    return desiredState;
  }

  /**
   * Desired startup state for any managed server started in this cluster. Legal values are RUNNING
   * or ADMIN.
   *
   * @param desiredState Desired status
   */
  public void setDesiredState(String desiredState) {
    this.desiredState = desiredState;
  }

  /**
   * Desired startup state for any managed server started in this cluster. Legal values are RUNNING
   * or ADMIN.
   *
   * @param desiredState Desired status
   * @return this
   */
  public ClusterStartup withDesiredState(String desiredState) {
    this.desiredState = desiredState;
    return this;
  }

  /**
   * Name of specific cluster to start. Managed servers in the cluster will be started beginning
   * with replicas instances. (Required)
   *
   * @return Cluster name
   */
  public String getClusterName() {
    return clusterName;
  }

  /**
   * Name of specific cluster to start. Managed servers in the cluster will be started beginning
   * with replicas instances. (Required)
   *
   * @param clusterName Cluster name
   */
  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  /**
   * Name of specific cluster to start. Managed servers in the cluster will be started beginning
   * with replicas instances. (Required)
   *
   * @param clusterName Cluster name
   * @return this
   */
  public ClusterStartup withClusterName(String clusterName) {
    this.clusterName = clusterName;
    return this;
  }

  /**
   * Replicas is the desired number of managed servers running for this cluster.
   *
   * @return Replicas
   */
  public Integer getReplicas() {
    return replicas;
  }

  /**
   * Replicas is the desired number of managed servers running for this cluster.
   *
   * @param replicas Replicas
   */
  public void setReplicas(Integer replicas) {
    this.replicas = replicas;
  }

  /**
   * Replicas is the desired number of managed servers running for this cluster.
   *
   * @param replicas Replicas
   * @return this
   */
  public ClusterStartup withReplicas(Integer replicas) {
    this.replicas = replicas;
    return this;
  }

  /**
   * Environment variables to pass while starting managed servers in this cluster.
   *
   * @return Environment variables
   */
  public List<V1EnvVar> getEnv() {
    return env;
  }

  /**
   * Environment variables to pass while starting managed servers in this cluster.
   *
   * @param env Environment variables
   */
  public void setEnv(List<V1EnvVar> env) {
    this.env = env;
  }

  /**
   * Environment variables to pass while starting managed servers in this cluster.
   *
   * @param env Environment variables
   * @return this
   */
  public ClusterStartup withEnv(List<V1EnvVar> env) {
    this.env = env;
    return this;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("desiredState", desiredState)
        .append("clusterName", clusterName)
        .append("replicas", replicas)
        .append("env", env)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(desiredState)
        .append(env)
        .append(replicas)
        .append(clusterName)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if ((other instanceof ClusterStartup) == false) {
      return false;
    }
    ClusterStartup rhs = ((ClusterStartup) other);
    return new EqualsBuilder()
        .append(desiredState, rhs.desiredState)
        .append(env, rhs.env)
        .append(replicas, rhs.replicas)
        .append(clusterName, rhs.clusterName)
        .isEquals();
  }
}

// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.models.V1EnvVar;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;


/**
 * ClusterStarup describes the desired startup state and passed environment variables for a specific cluster.
 * 
 */
public class ClusterStartup {

    /**
     * Desired startup state for any managed server started in this cluster. Legal values are RUNNING or ADMIN.
     * 
     */
    @SerializedName("desiredState")
    @Expose
    private String desiredState;
    /**
     * Name of specific cluster to start.  Managed servers in the cluster will be started beginning with replicas instances.
     * (Required)
     * 
     */
    @SerializedName("clusterName")
    @Expose
    @NotNull
    private String clusterName;
    /**
     * Replicas is the desired number of managed servers running for this cluster.
     * 
     */
    @SerializedName("replicas")
    @Expose
    private Integer replicas;
    /**
     * Environment variables to pass while starting managed servers in this cluster.
     * 
     */
    @SerializedName("env")
    @Expose
    @Valid
    private List<V1EnvVar> env = new ArrayList<V1EnvVar>();

    /**
     * Desired startup state for any managed server started in this cluster. Legal values are RUNNING or ADMIN.
     * 
     */
    public String getDesiredState() {
        return desiredState;
    }

    /**
     * Desired startup state for any managed server started in this cluster. Legal values are RUNNING or ADMIN.
     * 
     */
    public void setDesiredState(String desiredState) {
        this.desiredState = desiredState;
    }

    public ClusterStartup withDesiredState(String desiredState) {
        this.desiredState = desiredState;
        return this;
    }

    /**
     * Name of specific cluster to start.  Managed servers in the cluster will be started beginning with replicas instances.
     * (Required)
     * 
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * Name of specific cluster to start.  Managed servers in the cluster will be started beginning with replicas instances.
     * (Required)
     * 
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public ClusterStartup withClusterName(String clusterName) {
        this.clusterName = clusterName;
        return this;
    }

    /**
     * Replicas is the desired number of managed servers running for this cluster.
     * 
     */
    public Integer getReplicas() {
        return replicas;
    }

    /**
     * Replicas is the desired number of managed servers running for this cluster.
     * 
     */
    public void setReplicas(Integer replicas) {
        this.replicas = replicas;
    }

    public ClusterStartup withReplicas(Integer replicas) {
        this.replicas = replicas;
        return this;
    }

    /**
     * Environment variables to pass while starting managed servers in this cluster.
     * 
     */
    public List<V1EnvVar> getEnv() {
        return env;
    }

    /**
     * Environment variables to pass while starting managed servers in this cluster.
     * 
     */
    public void setEnv(List<V1EnvVar> env) {
        this.env = env;
    }

    public ClusterStartup withEnv(List<V1EnvVar> env) {
        this.env = env;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("desiredState", desiredState).append("clusterName", clusterName).append("replicas", replicas).append("env", env).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(desiredState).append(env).append(replicas).append(clusterName).toHashCode();
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
        return new EqualsBuilder().append(desiredState, rhs.desiredState).append(env, rhs.env).append(replicas, rhs.replicas).append(clusterName, rhs.clusterName).isEquals();
    }

}

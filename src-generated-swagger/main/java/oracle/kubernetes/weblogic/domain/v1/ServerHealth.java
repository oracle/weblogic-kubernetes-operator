// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;


/**
 * ServerHealth describes the current status and health of a specific WebLogic server.
 * 
 */
public class ServerHealth {

    /**
     * RFC 3339 date and time at which the server started.
     * 
     */
    @SerializedName("startTime")
    @Expose
    private DateTime startTime;
    /**
     * WebLogic cluster name, if the server is part of a cluster
     * 
     */
    @SerializedName("clusterName")
    @Expose
    private String clusterName;
    /**
     * Name of node that is hosting the Pod containing this WebLogic server.
     * 
     */
    @SerializedName("nodeName")
    @Expose
    private String nodeName;
    /**
     * Current state of this WebLogic server.
     * (Required)
     * 
     */
    @SerializedName("state")
    @Expose
    @NotNull
    private String state;
    /**
     * Server health of this WebLogic server.
     * 
     */
    @SerializedName("health")
    @Expose
    private String health;
    /**
     * Name of subsystem providing symptom information.
     * 
     */
    @SerializedName("subsystemName")
    @Expose
    private String subsystemName;
    /**
     * Symptoms provided by the reporting subsystem.
     * 
     */
    @SerializedName("symptoms")
    @Expose
    @Valid
    private List<String> symptoms = new ArrayList<String>();

    /**
     * RFC 3339 date and time at which the server started.
     * @return start time
     */
    public DateTime getStartTime() {
        return startTime;
    }

    /**
     * RFC 3339 date and time at which the server started.
     * @param startTime start time
     */
    public void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * RFC 3339 date and time at which the server started.
     * @param startTime start time
     * @return this
     */
    public ServerHealth withStartTime(DateTime startTime) {
        this.startTime = startTime;
        return this;
    }

    /**
     * WebLogic cluster name, if the server is part of a cluster
     * @return cluster name
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * WebLogic cluster name, if the server is part of a cluster
     * @param clusterName cluster name
     */
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * WebLogic cluster name, if the server is part of a cluster
     * @param clusterName cluster name
     * @return this
     */
    public ServerHealth withClusterName(String clusterName) {
        this.clusterName = clusterName;
        return this;
    }

    /**
     * Name of node that is hosting the Pod containing this WebLogic server.
     * @return node name
     */
    public String getNodeName() {
        return nodeName;
    }

    /**
     * Name of node that is hosting the Pod containing this WebLogic server.
     * @param nodeName node name
     */
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * Name of node that is hosting the Pod containing this WebLogic server.
     * @param nodeName node name
     * @return this
     */
    public ServerHealth withNodeName(String nodeName) {
        this.nodeName = nodeName;
        return this;
    }

    /**
     * Current state of this WebLogic server.
     * (Required)
     * @return state
     */
    public String getState() {
        return state;
    }

    /**
     * Current state of this WebLogic server.
     * (Required)
     * @param state state
     */
    public void setState(String state) {
        this.state = state;
    }

    /**
     * Current state of this WebLogic server.
     * (Required)
     * @param state state
     * @return this
     */
    public ServerHealth withState(String state) {
        this.state = state;
        return this;
    }

    /**
     * Server health of this WebLogic server.
     * @return health
     */
    public String getHealth() {
        return health;
    }

    /**
     * Server health of this WebLogic server.
     * @param health health
     */
    public void setHealth(String health) {
        this.health = health;
    }

    /**
     * Server health of this WebLogic server.
     * @param health health
     * @return this
     */
    public ServerHealth withHealth(String health) {
        this.health = health;
        return this;
    }

    /**
     * Name of subsystem providing symptom information.
     * @return subsystem name
     */
    public String getSubsystemName() {
        return subsystemName;
    }

    /**
     * Name of subsystem providing symptom information.
     * @param subsystemName subsystem name
     */
    public void setSubsystemName(String subsystemName) {
        this.subsystemName = subsystemName;
    }

    /**
     * Name of subsystem providing symptom information.
     * @param subsystemName subsystem name
     * @return this
     */
    public ServerHealth withSubsystemName(String subsystemName) {
        this.subsystemName = subsystemName;
        return this;
    }

    /**
     * Symptoms provided by the reporting subsystem.
     * @return symptoms
     */
    public List<String> getSymptoms() {
        return symptoms;
    }

    /**
     * Symptoms provided by the reporting subsystem.
     * @param symptoms symptoms
     */
    public void setSymptoms(List<String> symptoms) {
        this.symptoms = symptoms;
    }

    /**
     * Symptoms provided by the reporting subsystem.
     * @param symptoms symptoms
     * @return this
     */
    public ServerHealth withSymptoms(List<String> symptoms) {
        this.symptoms = symptoms;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("startTime", startTime).append("clusterName", clusterName).append("nodeName", nodeName).append("state", state).append("health", health).append("subsystemName", subsystemName).append("symptoms", symptoms).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(nodeName).append(symptoms).append(clusterName).append(health).append(startTime).append(state).append(subsystemName).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ServerHealth) == false) {
            return false;
        }
        ServerHealth rhs = ((ServerHealth) other);
        return new EqualsBuilder().append(nodeName, rhs.nodeName).append(symptoms, rhs.symptoms).append(clusterName, rhs.clusterName).append(health, rhs.health).append(startTime, rhs.startTime).append(state, rhs.state).append(subsystemName, rhs.subsystemName).isEquals();
    }

}

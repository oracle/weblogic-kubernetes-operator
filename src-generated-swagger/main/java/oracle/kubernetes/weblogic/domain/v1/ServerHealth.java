// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
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
    @SerializedName("activationTime")
    @Expose
    private DateTime activationTime;
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
    public DateTime getActivationTime() {
        return activationTime;
    }

    /**
     * RFC 3339 date and time at which the server started.
     * @param activationTime start time
     */
    public void setActivationTime(DateTime activationTime) {
        this.activationTime = activationTime;
    }

    /**
     * RFC 3339 date and time at which the server started.
     * @param startTime start time
     * @return this
     */
    public ServerHealth withActivationTime(DateTime activationTime) {
        this.activationTime = activationTime;
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
        return new ToStringBuilder(this).append("activationTime", activationTime).append("health", health).append("subsystemName", subsystemName).append("symptoms", symptoms).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(symptoms).append(health).append(activationTime).append(subsystemName).toHashCode();
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
        return new EqualsBuilder().append(symptoms, rhs.symptoms).append(health, rhs.health).append(activationTime, rhs.activationTime).append(subsystemName, rhs.subsystemName).isEquals();
    }

}

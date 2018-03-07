// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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
 * DomainStatus represents information about the status of a domain. Status may trail the actual state of a system.
 * 
 */
public class DomainStatus {

    /**
     * Current service state of domain.
     * 
     */
    @SerializedName("conditions")
    @Expose
    @Valid
    private List<DomainCondition> conditions = new ArrayList<DomainCondition>();
    /**
     * A human readable message indicating details about why the domain is in this condition.
     * 
     */
    @SerializedName("message")
    @Expose
    private String message;
    /**
     * A brief CamelCase message indicating details about why the domain is in this state.
     * 
     */
    @SerializedName("reason")
    @Expose
    private String reason;
    /**
     * List of specific server instances that are available.
     * 
     */
    @SerializedName("availableServers")
    @Expose
    @Valid
    private List<String> availableServers = new ArrayList<String>();
    /**
     * List of specific server instances that are configured to be available but that are either not yet available or have failed.
     * 
     */
    @SerializedName("unavailableServers")
    @Expose
    @Valid
    private List<String> unavailableServers = new ArrayList<String>();
    /**
     * List of specific cluster instances where the configured number of replicas are now available.
     * 
     */
    @SerializedName("availableClusters")
    @Expose
    @Valid
    private List<String> availableClusters = new ArrayList<String>();
    /**
     * List of specific cluster instances to configured to be available but for which one or more of the necessary replicas are either not yet available or have failed.
     * 
     */
    @SerializedName("unavailableClusters")
    @Expose
    @Valid
    private List<String> unavailableClusters = new ArrayList<String>();
    /**
     * RFC 3339 date and time at which the operator started the domain.  This will be when the operator begins processing and will precede when the various servers or clusters are available.
     * 
     */
    @SerializedName("startTime")
    @Expose
    private DateTime startTime;

    /**
     * Current service state of domain.
     * 
     */
    public List<DomainCondition> getConditions() {
        return conditions;
    }

    /**
     * Current service state of domain.
     * 
     */
    public void setConditions(List<DomainCondition> conditions) {
        this.conditions = conditions;
    }

    public DomainStatus withConditions(List<DomainCondition> conditions) {
        this.conditions = conditions;
        return this;
    }

    /**
     * A human readable message indicating details about why the domain is in this condition.
     * 
     */
    public String getMessage() {
        return message;
    }

    /**
     * A human readable message indicating details about why the domain is in this condition.
     * 
     */
    public void setMessage(String message) {
        this.message = message;
    }

    public DomainStatus withMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * A brief CamelCase message indicating details about why the domain is in this state.
     * 
     */
    public String getReason() {
        return reason;
    }

    /**
     * A brief CamelCase message indicating details about why the domain is in this state.
     * 
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    public DomainStatus withReason(String reason) {
        this.reason = reason;
        return this;
    }

    /**
     * List of specific server instances that are available.
     * 
     */
    public List<String> getAvailableServers() {
        return availableServers;
    }

    /**
     * List of specific server instances that are available.
     * 
     */
    public void setAvailableServers(List<String> availableServers) {
        this.availableServers = availableServers;
    }

    public DomainStatus withAvailableServers(List<String> availableServers) {
        this.availableServers = availableServers;
        return this;
    }

    /**
     * List of specific server instances that are configured to be available but that are either not yet available or have failed.
     * 
     */
    public List<String> getUnavailableServers() {
        return unavailableServers;
    }

    /**
     * List of specific server instances that are configured to be available but that are either not yet available or have failed.
     * 
     */
    public void setUnavailableServers(List<String> unavailableServers) {
        this.unavailableServers = unavailableServers;
    }

    public DomainStatus withUnavailableServers(List<String> unavailableServers) {
        this.unavailableServers = unavailableServers;
        return this;
    }

    /**
     * List of specific cluster instances where the configured number of replicas are now available.
     * 
     */
    public List<String> getAvailableClusters() {
        return availableClusters;
    }

    /**
     * List of specific cluster instances where the configured number of replicas are now available.
     * 
     */
    public void setAvailableClusters(List<String> availableClusters) {
        this.availableClusters = availableClusters;
    }

    public DomainStatus withAvailableClusters(List<String> availableClusters) {
        this.availableClusters = availableClusters;
        return this;
    }

    /**
     * List of specific cluster instances to configured to be available but for which one or more of the necessary replicas are either not yet available or have failed.
     * 
     */
    public List<String> getUnavailableClusters() {
        return unavailableClusters;
    }

    /**
     * List of specific cluster instances to configured to be available but for which one or more of the necessary replicas are either not yet available or have failed.
     * 
     */
    public void setUnavailableClusters(List<String> unavailableClusters) {
        this.unavailableClusters = unavailableClusters;
    }

    public DomainStatus withUnavailableClusters(List<String> unavailableClusters) {
        this.unavailableClusters = unavailableClusters;
        return this;
    }

    /**
     * RFC 3339 date and time at which the operator started the domain.  This will be when the operator begins processing and will precede when the various servers or clusters are available.
     * 
     */
    public DateTime getStartTime() {
        return startTime;
    }

    /**
     * RFC 3339 date and time at which the operator started the domain.  This will be when the operator begins processing and will precede when the various servers or clusters are available.
     * 
     */
    public void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }

    public DomainStatus withStartTime(DateTime startTime) {
        this.startTime = startTime;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("conditions", conditions).append("message", message).append("reason", reason).append("availableServers", availableServers).append("unavailableServers", unavailableServers).append("availableClusters", availableClusters).append("unavailableClusters", unavailableClusters).append("startTime", startTime).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(availableServers).append(reason).append(unavailableServers).append(availableClusters).append(startTime).append(unavailableClusters).append(conditions).append(message).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof DomainStatus) == false) {
            return false;
        }
        DomainStatus rhs = ((DomainStatus) other);
        return new EqualsBuilder().append(availableServers, rhs.availableServers).append(reason, rhs.reason).append(unavailableServers, rhs.unavailableServers).append(availableClusters, rhs.availableClusters).append(startTime, rhs.startTime).append(unavailableClusters, rhs.unavailableClusters).append(conditions, rhs.conditions).append(message, rhs.message).isEquals();
    }

}

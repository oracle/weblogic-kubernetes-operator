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
     * @return conditions
     */
    public List<DomainCondition> getConditions() {
        return conditions;
    }

    /**
     * Current service state of domain.
     * @param conditions conditions
     */
    public void setConditions(List<DomainCondition> conditions) {
        this.conditions = conditions;
    }

    /**
     * Current service state of domain.
     * @param conditions conditions
     * @return this
     */
    public DomainStatus withConditions(List<DomainCondition> conditions) {
        this.conditions = conditions;
        return this;
    }

    /**
     * A human readable message indicating details about why the domain is in this condition.
     * @return message
     */
    public String getMessage() {
        return message;
    }

    /**
     * A human readable message indicating details about why the domain is in this condition.
     * @param message message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * A human readable message indicating details about why the domain is in this condition.
     * @param message message
     * @return this
     */
    public DomainStatus withMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * A brief CamelCase message indicating details about why the domain is in this state.
     * @return reason
     */
    public String getReason() {
        return reason;
    }

    /**
     * A brief CamelCase message indicating details about why the domain is in this state.
     * @param reason reason
     */
    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * A brief CamelCase message indicating details about why the domain is in this state.
     * @param reason reason
     * @return this
     */
    public DomainStatus withReason(String reason) {
        this.reason = reason;
        return this;
    }

    /**
     * List of specific server instances that are available.
     * @return available servers
     */
    public List<String> getAvailableServers() {
        return availableServers;
    }

    /**
     * List of specific server instances that are available.
     * @param availableServers available servers
     */
    public void setAvailableServers(List<String> availableServers) {
        this.availableServers = availableServers;
    }

    /**
     * List of specific server instances that are available.
     * @param availableServers available servers
     * @return this
     */
    public DomainStatus withAvailableServers(List<String> availableServers) {
        this.availableServers = availableServers;
        return this;
    }

    /**
     * List of specific server instances that are configured to be available but that are either not yet available or have failed.
     * @return unavailable servers
     */
    public List<String> getUnavailableServers() {
        return unavailableServers;
    }

    /**
     * List of specific server instances that are configured to be available but that are either not yet available or have failed.
     * @param unavailableServers unavailable servers
     */
    public void setUnavailableServers(List<String> unavailableServers) {
        this.unavailableServers = unavailableServers;
    }

    /**
     * List of specific server instances that are configured to be available but that are either not yet available or have failed.
     * @param unavailableServers unavailable servers
     * @return this
     */
    public DomainStatus withUnavailableServers(List<String> unavailableServers) {
        this.unavailableServers = unavailableServers;
        return this;
    }

    /**
     * List of specific cluster instances where the configured number of replicas are now available.
     * @return available clusters
     */
    public List<String> getAvailableClusters() {
        return availableClusters;
    }

    /**
     * List of specific cluster instances where the configured number of replicas are now available.
     * @param availableClusters available clusters
     */
    public void setAvailableClusters(List<String> availableClusters) {
        this.availableClusters = availableClusters;
    }

    /**
     * List of specific cluster instances where the configured number of replicas are now available.
     * @param availableClusters available clusters
     * @return this
     */
    public DomainStatus withAvailableClusters(List<String> availableClusters) {
        this.availableClusters = availableClusters;
        return this;
    }

    /**
     * List of specific cluster instances to configured to be available but for which one or more of the necessary replicas are either not yet available or have failed.
     * @return unavailable clusters
     */
    public List<String> getUnavailableClusters() {
        return unavailableClusters;
    }

    /**
     * List of specific cluster instances to configured to be available but for which one or more of the necessary replicas are either not yet available or have failed.
     * @param unavailableClusters unavailable clusters
     */
    public void setUnavailableClusters(List<String> unavailableClusters) {
        this.unavailableClusters = unavailableClusters;
    }

    /**
     * List of specific cluster instances to configured to be available but for which one or more of the necessary replicas are either not yet available or have failed.
     * @param unavailableClusters unavailable clusters
     * @return this
     */
    public DomainStatus withUnavailableClusters(List<String> unavailableClusters) {
        this.unavailableClusters = unavailableClusters;
        return this;
    }

    /**
     * RFC 3339 date and time at which the operator started the domain.  This will be when the operator begins processing and will precede when the various servers or clusters are available.
     * @return start time
     */
    public DateTime getStartTime() {
        return startTime;
    }

    /**
     * RFC 3339 date and time at which the operator started the domain.  This will be when the operator begins processing and will precede when the various servers or clusters are available.
     * @param startTime start time
     */
    public void setStartTime(DateTime startTime) {
        this.startTime = startTime;
    }

    /**
     * RFC 3339 date and time at which the operator started the domain.  This will be when the operator begins processing and will precede when the various servers or clusters are available.
     * @param startTime start time
     * @return this
     */
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

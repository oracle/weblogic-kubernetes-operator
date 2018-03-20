// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v1;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;


/**
 * ServerStatus describes the current status of a specific WebLogic server.
 * 
 */
public class ServerStatus {

    /**
     * WebLogic server name.
     * (Required)
     * 
     */
    @SerializedName("serverName")
    @Expose
    @NotNull
    private String serverName;
    /**
     * State and health of the server.
     * (Required)
     * 
     */
    @SerializedName("health")
    @Expose
    @Valid
    @NotNull
    private ServerHealth health;

    /**
     * WebLogic server name.
     * (Required)
     * @return Server name
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * WebLogic server name.
     * (Required)
     * @param serverName Server name
     */
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    /**
     * WebLogic server name.
     * (Required)
     * @param serverName Server name
     * @return this
     */
    public ServerStatus withServerName(String serverName) {
        this.serverName = serverName;
        return this;
    }

    /**
     * State and health of the server.
     * (Required)
     * @return Health of the server
     */
    public ServerHealth getHealth() {
        return health;
    }

    /**
     * State and health of the server.
     * (Required)
     * @param health Health of the server
     */
    public void setHealth(ServerHealth health) {
        this.health = health;
    }

    /**
     * State and health of the server.
     * (Required)
     * @param health Health of the server
     * @return this
     */
    public ServerStatus withHealth(ServerHealth health) {
        this.health = health;
        return this;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("serverName", serverName).append("health", health).toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(serverName).append(health).toHashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if ((other instanceof ServerStatus) == false) {
            return false;
        }
        ServerStatus rhs = ((ServerStatus) other);
        return new EqualsBuilder().append(serverName, rhs.serverName).append(health, rhs.health).isEquals();
    }

}

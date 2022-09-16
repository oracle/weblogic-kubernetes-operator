// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import oracle.kubernetes.json.Default;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AdminServer extends Server {

  @Description(
      "Customization affecting the generation of a NodePort Service for the Administration Server used to "
          + "expose specific channels or network access points outside the Kubernetes cluster. "
          + "See also `domains.spec.adminServer.serverService` for configuration affecting the generation of "
          + "the ClusterIP Service.")
  private AdminService adminService;

  /**
   * Whether the admin channel port forwarding is enabled.
   */
  @Description(
      "When this flag is enabled, the operator updates the domain's WebLogic configuration for its"
          + " Administration Server to have an admin protocol NetworkAccessPoint with a 'localhost' address for"
          + " each existing admin protocol capable port. This allows external Administration Console and WLST 'T3'"
          + " access when using the 'kubectl port-forward' pattern. Defaults to true.")
  @Default(boolDefault = true)
  private Boolean adminChannelPortForwardingEnabled = true;

  /**
   * Add channel.
   *
   * @param channelName Channel name
   * @param nodePort Node port
   * @return this
   */
  public AdminServer withChannel(String channelName, int nodePort) {
    if (adminService == null) {
      adminService = new AdminService();
    }
    adminService.withChannel(channelName, nodePort);
    return this;
  }

  /**
   * Gets channel names.
   *
   * @return Channel names
   */
  public List<String> getChannelNames() {
    if (adminService == null) {
      return Collections.emptyList();
    }
    List<String> channelNames = new ArrayList<>();
    for (Channel c : adminService.getChannels()) {
      channelNames.add(c.getChannelName());
    }
    return channelNames;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("adminService", adminService)
        .append("adminChannelPortForwardingEnabled", adminChannelPortForwardingEnabled)
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    AdminServer that = (AdminServer) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(adminService, that.adminService)
        .append(adminChannelPortForwardingEnabled, that.adminChannelPortForwardingEnabled)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(adminService)
        .append(adminChannelPortForwardingEnabled)
        .toHashCode();
  }

  /**
   * Create the AdminService.
   * @return the AdminService
   */
  public AdminService createAdminService() {
    if (adminService == null) {
      adminService = new AdminService();
    }
    return adminService;
  }

  public AdminService getAdminService() {
    return adminService;
  }

  /**
   * Test if the admin channel port forwarding is enabled for the admin server.
   *
   * @return adminChannelPortForwardingEnabled
   */
  public boolean isAdminChannelPortForwardingEnabled() {
    return adminChannelPortForwardingEnabled;
  }

  /**
   * Admin channel port forwarding enabled.
   *
   * @param adminChannelPortForwardingEnabled admin channel port forwarding enabled
   */
  public void setAdminChannelPortForwardingEnabled(Boolean adminChannelPortForwardingEnabled) {
    this.adminChannelPortForwardingEnabled = adminChannelPortForwardingEnabled;
  }

}

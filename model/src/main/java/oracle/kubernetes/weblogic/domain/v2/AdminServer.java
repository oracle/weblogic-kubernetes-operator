// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

@Description("AdminServer represents the operator configuration for the admin server.")
public class AdminServer extends Server {

  @Description(
      "Configures which of the admin server's WebLogic admin channels should be exposed outside"
          + " the Kubernetes cluster via a node port service.")
  private AdminService adminService;

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

  /**
   * Gets channels.
   *
   * @return Channels
   */
  public List<Channel> getChannels() {
    if (adminService == null) {
      return Collections.emptyList();
    }
    return adminService.getChannels();
  }

  /**
   * Gets channel.
   *
   * @param channelName Channel name
   * @return Channel
   */
  public Channel getChannel(String channelName) {
    if (adminService != null) {
      for (Channel c : adminService.getChannels()) {
        if (channelName.equals(c.getChannelName())) {
          return c;
        }
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("adminService", adminService)
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
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(adminService)
        .toHashCode();
  }

  public AdminService getAdminService() {
    return adminService;
  }

  public void setAdminService(AdminService adminService) {
    this.adminService = adminService;
  }
}

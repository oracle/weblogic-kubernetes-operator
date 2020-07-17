// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AdminServer extends Server {

  @Description(
      "Customization affecting the generation of the Kubernetes Service for the Administration Server. These settings "
      + "can also specify the creation of a second NodePort Service to expose specific channels or network access "
      + "points outside the Kubernetes cluster.")
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
}

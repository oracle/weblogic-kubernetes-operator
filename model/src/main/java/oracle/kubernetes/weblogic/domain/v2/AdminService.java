// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AdminService {
  @SerializedName("channels")
  @Description(
      "Specifies which of the admin server's WebLogic channels should be exposed outside "
          + "the Kubernetes cluster via a node port service, along with the node port for "
          + "each channel. If not specified, the admin server's node port service will "
          + "not be created.")
  private List<Channel> channels = new ArrayList<>();

  /**
   * Add channel.
   *
   * @param port Port
   * @return this
   */
  public AdminService withChannel(Channel port) {
    channels.add(port);
    return this;
  }

  public AdminService withChannel(String channelName, int nodePort) {
    return withChannel(new Channel().withChannelName(channelName).withNodePort(nodePort));
  }

  public List<Channel> getChannels() {
    return channels;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("channels", channels).toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(Domain.sortOrNull(channels)).toHashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof AdminService)) {
      return false;
    }
    AdminService as = (AdminService) o;
    return new EqualsBuilder()
        .append(Domain.sortOrNull(channels), Domain.sortOrNull(as.channels))
        .isEquals();
  }
}

// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AdminService {
  /** */
  @SerializedName("channels")
  private List<Channel> channels = new ArrayList<>();

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
    return new ToStringBuilder(this).append("channles", channels).toString();
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

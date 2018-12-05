package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Channel {
  public Channel() {}

  public Channel(int nodePort) {
    this.nodePort = nodePort;
  }

  @SerializedName("nodePort")
  private int nodePort;

  public int getNodePort() {
    return nodePort;
  }

  public void setNodePort(int nodePort) {
    this.nodePort = nodePort;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this).append("nodePort", nodePort).toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(nodePort).toHashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    if (!(o instanceof Channel)) {
      return false;
    }
    Channel ch = (Channel) o;
    return new EqualsBuilder().append(nodePort, ch.nodePort).isEquals();
  }
}

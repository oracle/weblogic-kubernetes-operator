package oracle.kubernetes.weblogic.domain.v2;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class AdminService {
  /** */
  @SerializedName("labels")
  private Map<String, String> labels = new HashMap<>();
  /** */
  @SerializedName("annotations")
  private Map<String, String> annotations = new HashMap<>();
  /** */
  @SerializedName("channels")
  private Map<String, Channel> channels = new HashMap<>();

  public AdminService addLabel(String name, String value) {
    labels.put(name, value);
    return this;
  }

  public Map<String, String> getLabels() {
    return Collections.unmodifiableMap(labels);
  }

  public AdminService addAnnotation(String name, String value) {
    annotations.put(name, value);
    return this;
  }

  public Map<String, String> getAnnotations() {
    return Collections.unmodifiableMap(annotations);
  }

  public AdminService addChannel(String name, Channel port) {
    channels.put(name, port);
    return this;
  }

  public Map<String, Channel> getChannels() {
    return channels;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("labels", labels)
        .append("annotations", annotations)
        .append("channles", channels)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder().append(labels).append(annotations).append(channels).toHashCode();
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
        .append(labels, as.labels)
        .append(annotations, as.annotations)
        .append(channels, as.channels)
        .isEquals();
  }
}

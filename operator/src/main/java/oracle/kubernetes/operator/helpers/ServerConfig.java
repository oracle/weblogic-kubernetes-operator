// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.List;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** ServerConfig describes the desired state of a server. */
public class ServerConfig {

  private String serverName;
  private String restartedLabel;
  private int nodePort;
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private List<V1EnvVar> env = null;
  private String image;
  private String imagePullPolicy;
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private List<V1LocalObjectReference> imagePullSecrets = null;

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverName", serverName)
        .append("restartedLabel", restartedLabel)
        .append("nodePort", nodePort)
        .append("env", env)
        .append("image", image)
        .append("imagePullPolicy", imagePullPolicy)
        .append("imagePullSecrets", imagePullSecrets)
        .toString();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
        .append(serverName)
        .append(image)
        .append(imagePullPolicy)
        .append(imagePullSecrets)
        .append(restartedLabel)
        .append(env)
        .append(nodePort)
        .toHashCode();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof ServerConfig rhs)) {
      return false;
    }
    return new EqualsBuilder()
        .append(serverName, rhs.serverName)
        .append(image, rhs.image)
        .append(imagePullPolicy, rhs.imagePullPolicy)
        .append(imagePullSecrets, rhs.imagePullSecrets)
        .append(restartedLabel, rhs.restartedLabel)
        .append(env, rhs.env)
        .append(nodePort, rhs.nodePort)
        .isEquals();
  }
}

// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.processing.EffectiveIntrospectorJobPodSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Configuration values shared by multiple levels: domain, admin server, managed server, and
 * cluster.
 *
 */
public class IntrospectorJobPodConfiguration implements EffectiveIntrospectorJobPodSpec {

  @Description("Customization affecting the generation of the Introspector Job Pod.")
  protected final IntrospectorJobPod serverPod = new IntrospectorJobPod();

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(IntrospectorJobPodConfiguration other) {
    if (other == null) {
      return;
    }

    serverPod.fillInFrom(other.serverPod);
  }

  @Nullable
  public List<V1EnvVar> getEnv() {
    return serverPod.getEnv();
  }

  public void setEnv(@Nullable List<V1EnvVar> env) {
    serverPod.setEnv(env);
  }

  void addEnvironmentVariable(String name, String value) {
    serverPod.addEnvVar(new V1EnvVar().name(name).value(value));
  }

  void addEnvironmentVariable(V1EnvVar envVar) {
    serverPod.addEnvVar(envVar);
  }

  public V1ResourceRequirements getResources() {
    return serverPod.getResourceRequirements();
  }

  void addRequestRequirement(String resource, String quantity) {
    serverPod.addRequestRequirement(resource, quantity);
  }

  void addLimitRequirement(String resource, String quantity) {
    serverPod.addLimitRequirement(resource, quantity);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverPod", serverPod)
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

    IntrospectorJobPodConfiguration that = (IntrospectorJobPodConfiguration) o;

    return new EqualsBuilder()
        .append(serverPod, that.serverPod)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(serverPod)
        .toHashCode();
  }
}

// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.kubernetes.json.Description;
import oracle.kubernetes.operator.processing.EffectiveIntroServerPodSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Configuration values shared by multiple levels: domain, admin server, managed server, and
 * cluster.
 *
 * @since 2.0
 */
public class BaseIntrospectorServerPodConfiguration implements EffectiveIntroServerPodSpec {

  @Description("Customization affecting the generation of Pods for WebLogic Server instances or introspector pod.")
  protected final BaseServerPod introServerPod = new BaseServerPod();

  /**
   * Fills in any undefined settings in this configuration from another configuration.
   *
   * @param other the other configuration which can override this one
   */
  void fillInFrom(BaseIntrospectorServerPodConfiguration other) {
    if (other == null) {
      return;
    }

    introServerPod.fillInFrom(other.introServerPod);
  }

  public BaseServerPod getServerPod() {
    return introServerPod;
  }

  public BaseIntrospectorServerPodConfiguration getServerPodSpec() {
    return this;
  }

  @Nullable
  public List<V1EnvVar> getEnv() {
    return introServerPod.getEnv();
  }

  public void setEnv(@Nullable List<V1EnvVar> env) {
    introServerPod.setEnv(env);
  }

  void addEnvironmentVariable(String name, String value) {
    introServerPod.addEnvVar(new V1EnvVar().name(name).value(value));
  }

  void addEnvironmentVariable(V1EnvVar envVar) {
    introServerPod.addEnvVar(envVar);
  }

  public V1ResourceRequirements getResources() {
    return introServerPod.getResourceRequirements();
  }

  void addRequestRequirement(String resource, String quantity) {
    introServerPod.addRequestRequirement(resource, quantity);
  }

  void addLimitRequirement(String resource, String quantity) {
    introServerPod.addLimitRequirement(resource, quantity);
  }

  @Override
  public List<V1EnvVar> getEnvironmentVariables() {
    return introServerPod.getEnv();
  }

  public Map<String, String> getPodLabels() {
    return introServerPod.getLabels();
  }

  void addPodLabel(String name, String value) {
    introServerPod.addLabel(name, value);
  }

  public Map<String, String> getPodAnnotations() {
    return introServerPod.getAnnotations();
  }

  void addPodAnnotation(String name, String value) {
    introServerPod.addAnnotations(name, value);
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .append("serverPod", introServerPod)
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

    BaseIntrospectorServerPodConfiguration that = (BaseIntrospectorServerPodConfiguration) o;

    return new EqualsBuilder()
        .append(introServerPod, that.introServerPod)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .append(introServerPod)
        .toHashCode();
  }
}

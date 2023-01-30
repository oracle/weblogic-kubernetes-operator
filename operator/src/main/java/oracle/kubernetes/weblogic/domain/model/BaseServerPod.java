// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Capabilities;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import jakarta.validation.Valid;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static java.util.Collections.emptyList;
import static oracle.kubernetes.operator.helpers.PodHelper.createCopy;

class BaseServerPod extends KubernetesResource {

  private static final Comparator<V1EnvVar> ENV_VAR_COMPARATOR =
      Comparator.comparing(V1EnvVar::getName);

  /**
   * Environment variables to pass while starting a server.
   *
   * @since 2.0
   */
  @Valid
  @Description("A list of environment variables to set in the container running a WebLogic Server instance. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-resource/#jvm-memory-and-java-option-environment-variables. "
      + "See `kubectl explain pods.spec.containers.env`.")
  private List<V1EnvVar> env = new ArrayList<>();

  /**
   * Defines the requirements and limits for the pod server.
   *
   * @since 2.0
   */
  @Description("Memory and CPU minimum requirements and limits for the WebLogic Server instance. "
      + "See `kubectl explain pods.spec.containers.resources`.")
  private final V1ResourceRequirements resources =
      new V1ResourceRequirements().limits(new HashMap<>()).requests(new HashMap<>());

  private static void copyValues(V1ResourceRequirements to, V1ResourceRequirements from) {
    if (from != null) {
      if (from.getRequests() != null) {
        from.getRequests().forEach(to.getRequests()::putIfAbsent);
      }
      if (from.getLimits() != null) {
        from.getLimits().forEach(to.getLimits()::putIfAbsent);
      }
    }
  }

  @SuppressWarnings("Duplicates")
  private void copyValues(V1PodSecurityContext to, V1PodSecurityContext from) {
    if (to.getRunAsNonRoot() == null) {
      to.runAsNonRoot(from.getRunAsNonRoot());
    }
    if (to.getFsGroup() == null) {
      to.fsGroup(from.getFsGroup());
    }
    if (to.getRunAsGroup() == null) {
      to.runAsGroup(from.getRunAsGroup());
    }
    if (to.getRunAsUser() == null) {
      to.runAsUser(from.getRunAsUser());
    }
    if (to.getSeLinuxOptions() == null) {
      to.seLinuxOptions(from.getSeLinuxOptions());
    }
    if (to.getSupplementalGroups() == null) {
      to.supplementalGroups(from.getSupplementalGroups());
    }
    if (to.getSysctls() == null) {
      to.sysctls(from.getSysctls());
    }
  }

  @SuppressWarnings("Duplicates")
  private void copyValues(V1SecurityContext to, V1SecurityContext from) {
    if (to.getAllowPrivilegeEscalation() == null) {
      to.allowPrivilegeEscalation(from.getAllowPrivilegeEscalation());
    }
    if (to.getPrivileged() == null) {
      to.privileged(from.getPrivileged());
    }
    if (to.getReadOnlyRootFilesystem() == null) {
      to.readOnlyRootFilesystem(from.getReadOnlyRootFilesystem());
    }
    if (to.getRunAsNonRoot() == null) {
      to.runAsNonRoot(from.getRunAsNonRoot());
    }
    if (to.getCapabilities() == null) {
      to.setCapabilities(from.getCapabilities());
    } else {
      copyValues(to.getCapabilities(), from.getCapabilities());
    }
    if (to.getRunAsGroup() == null) {
      to.runAsGroup(from.getRunAsGroup());
    }
    if (to.getRunAsUser() == null) {
      to.runAsUser(from.getRunAsUser());
    }
    if (to.getSeLinuxOptions() == null) {
      to.seLinuxOptions(from.getSeLinuxOptions());
    }
  }

  private void copyValues(V1Capabilities to, V1Capabilities from) {
    if (from.getAdd() != null) {
      Stream<String> stream = (to.getAdd() != null)
          ? Stream.concat(to.getAdd().stream(), from.getAdd().stream()) : from.getAdd().stream();
      to.setAdd(stream.distinct().collect(Collectors.toList()));
    }

    if (from.getDrop() != null) {
      Stream<String> stream = (to.getDrop() != null)
          ? Stream.concat(to.getDrop().stream(), from.getDrop().stream()) : from.getDrop().stream();
      to.setDrop(stream.distinct().collect(Collectors.toList()));
    }
  }

  void fillInFrom(BaseServerPod serverPod1) {
    for (V1EnvVar envVar : serverPod1.getV1EnvVars()) {
      addIfMissing(envVar);
    }
    fillInFrom((KubernetesResource) serverPod1);
    copyValues(resources, serverPod1.resources);
  }

  private V1Container createWithEnvCopy(V1Container c) {
    return new V1ContainerBuilder(c).withEnv(createCopy(c.getEnv())).build();
  }

  private void addIfMissing(V1EnvVar envVar) {
    if (!hasEnvVar(envVar.getName())) {
      addEnvVar(envVar);
    }
  }

  private List<V1EnvVar> getV1EnvVars() {
    return Optional.ofNullable(getEnv()).orElse(emptyList());
  }

  private boolean hasEnvVar(String name) {
    if (env == null) {
      return false;
    }
    for (V1EnvVar envVar : env) {
      if (envVar.getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  List<V1EnvVar> getEnv() {
    return this.env;
  }

  void setEnv(@Nullable List<V1EnvVar> env) {
    this.env = env;
  }

  void addEnvVar(V1EnvVar envVar) {
    if (this.env == null) {
      setEnv(new ArrayList<>());
    }
    this.env.add(envVar);
  }

  V1ResourceRequirements getResourceRequirements() {
    return resources;
  }

  void addRequestRequirement(String resource, String quantity) {
    resources.putRequestsItem(resource, Quantity.fromString(quantity));
  }

  void addLimitRequirement(String resource, String quantity) {
    resources.putLimitsItem(resource, Quantity.fromString(quantity));
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("env", env)
        .append("resources", resources)
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

    BaseServerPod that = (BaseServerPod) o;

    return new EqualsBuilder()
        .appendSuper(super.equals(o))
        .append(
            DomainResource.sortList(env, ENV_VAR_COMPARATOR),
            DomainResource.sortList(that.env, ENV_VAR_COMPARATOR))
        .append(resources, that.resources)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(DomainResource.sortList(env, ENV_VAR_COMPARATOR))
        .append(resources)
        .toHashCode();
  }
}

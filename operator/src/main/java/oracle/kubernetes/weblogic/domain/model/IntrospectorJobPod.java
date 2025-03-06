// Copyright (c) 2023, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import jakarta.validation.Valid;
import oracle.kubernetes.json.Description;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import static java.util.Collections.emptyList;

class IntrospectorJobPod {

  private static final Comparator<V1EnvVar> ENV_VAR_COMPARATOR =
      Comparator.comparing(V1EnvVar::getName);

  /**
   * Environment variables to pass while starting a server.
   *
   */
  @Valid
  @Description("A list of environment variables to set in the Introspector Job Pod container. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-resource/#jvm-memory-and-java-option-environment-variables. "
      + "See `kubectl explain pods.spec.containers.env`.")
  private List<V1EnvVar> env = new ArrayList<>();

  /**
   * List of sources to populate environment variables in the Introspector Job Pod container.
   *
   */
  @Valid
  @Description("List of sources to populate environment variables in the Introspector Job Pod container. "
      + "The sources include either a config map or a secret. "
      + "The operator will not expand the dependent variables in the 'envFrom' source. "
      + "More details: https://kubernetes.io/docs/tasks/inject-data-application/"
      + "define-environment-variable-container/#define-an-environment-variable-for-a-container. "
      + "Also see: https://oracle.github.io/weblogic-kubernetes-operator/userguide/managing-domains/"
      + "domain-resource/#jvm-memory-and-java-option-environment-variables.")
  private List<V1EnvFromSource> envFrom = null;

  /**
   * Defines the requirements and limits for the pod server.
   *
   */
  @Description("Memory and CPU minimum requirements and limits for the Introspector Job Pod. "
      + "See `kubectl explain pods.spec.containers.resources`.")
  private final V1ResourceRequirements resources =
      new V1ResourceRequirements().limits(new HashMap<>()).requests(new HashMap<>());

  /**
   * PodSecurityContext holds pod-level security attributes and common container settings. Some
   * fields are also present in container.securityContext. Field values of container.securityContext
   * take precedence over field values of PodSecurityContext.
   *
   */
  @Description("Pod-level security attributes. See `kubectl explain pods.spec.securityContext`. "
      + "If no value is specified for this field, the operator will use default "
      + "content for the pod-level `securityContext`. "
      + "More info: https://oracle.github.io/weblogic-kubernetes-operator/security/domain-security/pod-and-container/.")
  private V1PodSecurityContext podSecurityContext = null;

  /**
   * InitContainers holds a list of initialization containers that run after the auxiliary image init
   * container for the introspector job pod.
   *
   */
  @Valid
  @Description("List of init containers for the introspector Job Pod. These containers run after the auxiliary image "
         + "init container. See `kubectl explain pods.spec.initContainers`.")
  private List<V1Container> initContainers = null;

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

  private void copyValues(V1PodSecurityContext from) {
    if (from != null) {
      if (podSecurityContext == null) {
        podSecurityContext = new V1PodSecurityContext();
      }
      if (podSecurityContext.getRunAsNonRoot() == null) {
        podSecurityContext.runAsNonRoot(from.getRunAsNonRoot());
      }
      if (podSecurityContext.getFsGroup() == null) {
        podSecurityContext.fsGroup(from.getFsGroup());
      }
      if (podSecurityContext.getRunAsGroup() == null) {
        podSecurityContext.runAsGroup(from.getRunAsGroup());
      }
      if (podSecurityContext.getRunAsUser() == null) {
        podSecurityContext.runAsUser(from.getRunAsUser());
      }
      if (podSecurityContext.getSeLinuxOptions() == null) {
        podSecurityContext.seLinuxOptions(from.getSeLinuxOptions());
      }
      if (podSecurityContext.getSeLinuxChangePolicy() == null) {
        podSecurityContext.seLinuxChangePolicy(from.getSeLinuxChangePolicy());
      }
      if (podSecurityContext.getSupplementalGroups() == null) {
        podSecurityContext.supplementalGroups(from.getSupplementalGroups());
      }
      if (podSecurityContext.getSupplementalGroupsPolicy() == null) {
        podSecurityContext.supplementalGroupsPolicy(from.getSupplementalGroupsPolicy());
      }
      if (podSecurityContext.getSysctls() == null) {
        podSecurityContext.sysctls(from.getSysctls());
      }
      if (podSecurityContext.getFsGroupChangePolicy() == null) {
        podSecurityContext.fsGroupChangePolicy(from.getFsGroupChangePolicy());
      }
      if (podSecurityContext.getSeccompProfile() == null) {
        podSecurityContext.seccompProfile(from.getSeccompProfile());
      }
      if (podSecurityContext.getWindowsOptions() == null) {
        podSecurityContext.windowsOptions(from.getWindowsOptions());
      }
      if (podSecurityContext.getAppArmorProfile() == null) {
        podSecurityContext.appArmorProfile(from.getAppArmorProfile());
      }
    }
  }

  void fillInFrom(IntrospectorJobPod serverPod1) {
    for (V1EnvVar envVar : serverPod1.getV1EnvVars()) {
      addIfMissing(envVar);
    }
    if (serverPod1.envFrom != null) {
      if (envFrom == null) {
        envFrom = new ArrayList<>();
      }
      envFrom.addAll(serverPod1.envFrom);
    }
    if (serverPod1.initContainers != null) {
      if (initContainers == null) {
        initContainers = new ArrayList<>();
      }
      initContainers.addAll(serverPod1.initContainers);
    }
    copyValues(resources, serverPod1.resources);
    copyValues(serverPod1.podSecurityContext);
  }

  private void addIfMissing(V1EnvVar envVar) {
    if (!hasEnvVar(envVar.getName())) {
      addEnvVar(envVar);
    }
  }

  private List<V1EnvVar> getV1EnvVars() {
    return Optional.ofNullable(getEnv()).orElse(emptyList());
  }

  List<V1Container> getInitContainers() {
    return Optional.ofNullable(initContainers).orElse(emptyList());
  }

  void setInitContainers(@Nullable List<V1Container> initContainers) {
    this.initContainers = initContainers;
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

  List<V1EnvFromSource> getEnvFrom() {
    return this.envFrom;
  }

  void setEnvFrom(@Nullable List<V1EnvFromSource> envFrom) {
    this.envFrom = envFrom;
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

  V1PodSecurityContext getPodSecurityContext() {
    return this.podSecurityContext;
  }

  void setPodSecurityContext(@Nullable V1PodSecurityContext podSecurityContext) {
    this.podSecurityContext = podSecurityContext;
  }

  @Override
  public String toString() {
    return new ToStringBuilder(this)
        .appendSuper(super.toString())
        .append("env", env)
        .append("resources", resources)
        .append("envFrom", envFrom)
        .append("podSecurityContext", podSecurityContext)
        .append("initContainers", initContainers)
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

    IntrospectorJobPod that = (IntrospectorJobPod) o;

    return new EqualsBuilder()
        .append(
            DomainResource.sortList(env, ENV_VAR_COMPARATOR),
            DomainResource.sortList(that.env, ENV_VAR_COMPARATOR))
        .append(resources, that.resources)
        .append(envFrom, that.envFrom)
        .append(podSecurityContext, that.podSecurityContext)
        .append(initContainers, that.initContainers)
        .isEquals();
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder(17, 37)
        .appendSuper(super.hashCode())
        .append(DomainResource.sortList(env, ENV_VAR_COMPARATOR))
        .append(resources)
        .append(envFrom)
        .append(podSecurityContext)
        .append(initContainers)
        .toHashCode();
  }
}
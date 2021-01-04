// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Toleration;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;

public abstract class BasePodStepContext extends StepContextBase {

  BasePodStepContext(DomainPresenceInfo info) {
    super(info);
  }

  final <T> T updateForDeepSubstitution(V1PodSpec podSpec, T target) {
    return getContainer(podSpec)
        .map(c -> doDeepSubstitution(augmentSubVars(deepSubVars(c.getEnv())), target))
        .orElse(target);
  }

  abstract ServerSpec getServerSpec();

  abstract String getContainerName();

  abstract List<String> getContainerCommand();

  abstract List<V1Container> getContainers();

  protected V1Container createContainer(TuningParameters tuningParameters) {
    return new V1Container()
        .name(getContainerName())
        .image(getServerSpec().getImage())
        .imagePullPolicy(getServerSpec().getImagePullPolicy())
        .command(getContainerCommand())
        .env(getEnvironmentVariables(tuningParameters))
        .resources(getServerSpec().getResources())
        .securityContext(getServerSpec().getContainerSecurityContext());
  }

  protected V1PodSpec createPodSpec(TuningParameters tuningParameters) {
    return new V1PodSpec()
        .containers(getContainers())
        .addContainersItem(createContainer(tuningParameters))
        .affinity(getServerSpec().getAffinity())
        .nodeSelector(getServerSpec().getNodeSelectors())
        .serviceAccountName(getServerSpec().getServiceAccountName())
        .nodeName(getServerSpec().getNodeName())
        .schedulerName(getServerSpec().getSchedulerName())
        .priorityClassName(getServerSpec().getPriorityClassName())
        .runtimeClassName(getServerSpec().getRuntimeClassName())
        .tolerations(getTolerations())
        .restartPolicy(getServerSpec().getRestartPolicy())
        .securityContext(getServerSpec().getPodSecurityContext())
        .imagePullSecrets(getServerSpec().getImagePullSecrets());
  }

  private List<V1Toleration> getTolerations() {
    List<V1Toleration> tolerations = getServerSpec().getTolerations();
    return tolerations.isEmpty() ? null : tolerations;
  }


  /**
   * Abstract method to be implemented by subclasses to return a list of configured and additional
   * environment variables to be set up in the pod.
   *
   * @param tuningParameters TuningParameters that can be used when obtaining
   * @return A list of configured and additional environment variables
   */
  abstract List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters);

  /**
   * Return a list of environment variables to be set up in the pod. This method does some
   * processing of the list of environment variables such as token substitution before returning the
   * list.
   *
   * @param tuningParameters TuningParameters containing parameters that may be used in environment
   *     variables
   * @return A List of environment variables to be set up in the pod
   */
  final List<V1EnvVar> getEnvironmentVariables(TuningParameters tuningParameters) {

    List<V1EnvVar> vars = getConfiguredEnvVars(tuningParameters);

    addDefaultEnvVarIfMissing(
        vars, "USER_MEM_ARGS", "-Djava.security.egd=file:/dev/./urandom");

    hideAdminUserCredentials(vars);
    return doDeepSubstitution(varsToSubVariables(vars), vars);
  }

  protected void addEnvVar(List<V1EnvVar> vars, String name, String value) {
    vars.add(new V1EnvVar().name(name).value(value));
  }

  private void addEnvVar(List<V1EnvVar> vars, String name, String value, V1EnvVarSource valueFrom) {
    if ((value != null) && (!value.isEmpty())) {
      addEnvVar(vars, name, value);
    } else {
      addEnvVarWithValueFrom(vars, name, valueFrom);
    }
  }

  private void addEnvVarWithValueFrom(List<V1EnvVar> vars, String name, V1EnvVarSource valueFrom) {
    vars.add(new V1EnvVar().name(name).valueFrom(valueFrom));
  }

  protected void addEnvVarIfTrue(boolean condition, List<V1EnvVar> vars, String name) {
    if (condition) {
      addEnvVar(vars, name, "true");
    }
  }

  protected boolean hasEnvVar(List<V1EnvVar> vars, String name) {
    for (V1EnvVar var : vars) {
      if (name.equals(var.getName())) {
        return true;
      }
    }
    return false;
  }

  protected void addIfMissing(List<V1EnvVar> vars, String name, String value, V1EnvVarSource valueFrom) {
    if (!hasEnvVar(vars, name)) {
      addEnvVar(vars, name, value, valueFrom);
    }
  }

  protected void addDefaultEnvVarIfMissing(List<V1EnvVar> vars, String name, String value) {
    if (!hasEnvVar(vars, name)) {
      addEnvVar(vars, name, value);
    }
  }

  protected V1EnvVar findEnvVar(List<V1EnvVar> vars, String name) {
    for (V1EnvVar var : vars) {
      if (name.equals(var.getName())) {
        return var;
      }
    }
    return null;
  }

  protected void addOrReplaceEnvVar(List<V1EnvVar> vars, String name, String value) {
    V1EnvVar var = findEnvVar(vars, name);
    if (var != null) {
      var.value(value);
    } else {
      addEnvVar(vars, name, value);
    }
  }

  // Hide the admin account's user name and password.
  // Note: need to use null v.s. "" since if you upload a "" to kubectl then download it,
  // it comes back as a null and V1EnvVar.equals returns false even though it's supposed to
  // be the same value.
  // Regardless, the pod ends up with an empty string as the value (v.s. thinking that
  // the environment variable hasn't been set), so it honors the value (instead of using
  // the default, e.g. 'weblogic' for the user name).
  protected void hideAdminUserCredentials(List<V1EnvVar> vars) {
    addEnvVar(vars, "ADMIN_USERNAME", null);
    addEnvVar(vars, "ADMIN_PASSWORD", null);
  }

  protected Map<String, String> varsToSubVariables(List<V1EnvVar> vars) {
    Map<String, String> substitutionVariables = new HashMap<>();
    if (vars != null) {
      for (V1EnvVar envVar : vars) {
        substitutionVariables.put(envVar.getName(), envVar.getValue());
      }
    }

    return substitutionVariables;
  }

  final Map<String, String> deepSubVars(List<V1EnvVar> envVars) {
    return varsToSubVariables(envVars);
  }

  protected Map<String, String> augmentSubVars(Map<String, String> vars) {
    return vars;
  }

  protected Optional<V1Container> getContainer(V1Pod v1Pod) {
    return getContainer(v1Pod.getSpec());
  }

  protected Optional<V1Container> getContainer(V1PodSpec v1PodSpec) {
    return v1PodSpec.getContainers().stream().filter(this::isK8sContainer).findFirst();
  }

  protected boolean isK8sContainer(V1Container c) {
    return getMainContainerName().equals(c.getName());
  }

  protected String getMainContainerName() {
    return KubernetesConstants.CONTAINER_NAME;
  }
}

// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.helpers.LegalNames;

import static java.util.stream.Collectors.toSet;
import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.DEFAULT_SUCCESS_THRESHOLD;
import static oracle.kubernetes.weblogic.domain.model.DomainResource.TOKEN_END_MARKER;
import static oracle.kubernetes.weblogic.domain.model.DomainResource.TOKEN_START_MARKER;

/**
 * Resource validator.
 */
public abstract class Validator {
  static final String ADMIN_SERVER_POD_SPEC_PREFIX = "spec.adminServer.serverPod";
  static final String CLUSTER_SPEC_PREFIX = "spec.clusters";
  static final String MS_SPEC_PREFIX = "spec.managedServers";
  static final String SERVER_POD_CONTAINERS = "].serverPod.containers";
  static final String[] ALLOWED_INTROSPECTOR_ENV_VARS = {"JAVA_OPTIONS", "USER_MEM_ARGS",
      "NODEMGR_JAVA_OPTIONS", "NODEMGR_MEM_ARGS"};
  final List<String> failures = new ArrayList<>();

  void addClusterInvalidMountPaths(ClusterResource cluster) {
    ClusterSpec spec = cluster.getSpec();
    Optional.of(spec).map(ClusterSpec::getAdditionalVolumeMounts)
        .ifPresent(mounts -> mounts.forEach(mount ->
            checkValidMountPath(mount, getEnvNames(spec), getRemainingVolumeMounts(mounts, mount))));
  }

  List<V1VolumeMount> getRemainingVolumeMounts(List<V1VolumeMount> list, V1VolumeMount mount) {
    List<V1VolumeMount> ret = new ArrayList<>();
    for (int i = list.indexOf(mount) + 1; i < list.size(); i++) {
      ret.add(list.get(i));
    }
    return ret;
  }

  @Nonnull
  Set<String> getEnvNames(ClusterSpec spec) {
    return Optional.ofNullable(spec.getEnv()).stream()
        .flatMap(Collection::stream)
        .map(V1EnvVar::getName)
        .collect(toSet());
  }

  void checkValidMountPath(V1VolumeMount mount, Set<String> envNames, List<V1VolumeMount> mounts) {
    if (skipValidation(mount.getMountPath(), envNames)) {
      return;
    }

    if (!new File(mount.getMountPath()).isAbsolute()) {
      failures.add(DomainValidationMessages.badVolumeMountPath(mount));
    }

    mounts.stream().forEach(m -> checkOverlappingMountPaths(mount, m));
  }

  private void checkOverlappingMountPaths(V1VolumeMount mount1, V1VolumeMount mount2) {
    List<String> list1 = getTokensWithCollection(mount1.getMountPath());
    List<String> list2 = getTokensWithCollection(mount2.getMountPath());
    for (int i = 0; i < Math.min(list1.size(), list2.size()); i++) {
      if (!list1.get(i).equals(list2.get(i))) {
        return;
      }
    }

    failures.add(DomainValidationMessages.overlappingVolumeMountPath(mount1, mount2));
  }

  private List<String> getTokensWithCollection(String str) {
    return Collections.list(new StringTokenizer(str, "/")).stream()
        .map(String.class::cast)
        .collect(Collectors.toList());
  }

  boolean skipValidation(String mountPath, Set<String> envNames) {
    StringTokenizer nameList = new StringTokenizer(mountPath, TOKEN_START_MARKER);
    if (!nameList.hasMoreElements()) {
      return false;
    }
    while (nameList.hasMoreElements()) {
      String token = nameList.nextToken();
      if (noMatchingEnvVarName(envNames, token)) {
        return false;
      }
    }
    return true;
  }

  void addClusterReservedEnvironmentVariables(ClusterResource cluster, String prefix) {
    Optional.of(cluster).map(ClusterResource::getSpec)
        .ifPresent(clusterSpec -> checkReservedEnvironmentVariables(clusterSpec, prefix));
  }

  void verifyClusterLivenessProbeSuccessThreshold(ClusterResource cluster, String prefix) {
    Optional.of(cluster).map(ClusterResource::getSpec)
        .flatMap(clusterSpec -> Optional.ofNullable(clusterSpec.getLivenessProbe()))
        .ifPresent(probe -> verifySuccessThresholdValue(probe, prefix));
  }

  void verifyClusterContainerPortNameValidInPodSpec(ClusterResource cluster, String prefix) {
    Optional.of(cluster).map(ClusterResource::getSpec)
        .flatMap(clusterSpec -> Optional.ofNullable(clusterSpec.getContainers()))
        .ifPresent(containers -> containers.forEach(container -> areContainerPortNamesValid(container, prefix)));
  }

  void verifyClusterContainerNameValid(ClusterResource cluster, String prefix) {
    Optional.of(cluster).map(ClusterResource::getSpec)
        .flatMap(clusterSpec -> Optional.ofNullable(clusterSpec.getContainers()))
        .ifPresent(containers -> containers.forEach(container -> isContainerNameReserved(container, prefix)));
  }

  void verifySuccessThresholdValue(ProbeTuning probe, String prefix) {
    if (probe.getSuccessThreshold() != null && probe.getSuccessThreshold() != DEFAULT_SUCCESS_THRESHOLD) {
      failures.add(DomainValidationMessages.invalidLivenessProbeSuccessThresholdValue(
          probe.getSuccessThreshold(), prefix));
    }
  }

  void isContainerNameReserved(V1Container container, String prefix) {
    if (container.getName().equals(WLS_CONTAINER_NAME)) {
      failures.add(DomainValidationMessages.reservedContainerName(container.getName(), prefix));
    }
  }

  void areContainerPortNamesValid(V1Container container, String prefix) {
    Optional.ofNullable(container.getPorts()).ifPresent(portList ->
        portList.forEach(port -> checkPortNameLength(port, container.getName(), prefix)));
  }

  abstract void checkPortNameLength(V1ContainerPort port, String name, String prefix);

  boolean isPortNameTooLong(V1ContainerPort port) {
    return Objects.requireNonNull(port.getName()).length() > LegalNames.LEGAL_CONTAINER_PORT_NAME_MAX_LENGTH;
  }

  private boolean noMatchingEnvVarName(Set<String> varNames, String token) {
    int index = token.indexOf(TOKEN_END_MARKER);
    if (index != -1) {
      String str = token.substring(0, index);
      // IntrospectorJobEnvVars.isReserved() checks env vars in ServerEnvVars too
      return !varNames.contains(str) && !IntrospectorJobEnvVars.isReserved(str);
    }
    return true;
  }

  class EnvironmentVariableCheck {
    private final Predicate<String> isReserved;

    EnvironmentVariableCheck(Predicate<String> isReserved) {
      this.isReserved = isReserved;
    }

    void checkEnvironmentVariables(@Nonnull BaseConfiguration configuration, String prefix) {
      List<String> reservedNames = Optional.ofNullable(configuration.getEnv()).orElse(Collections.emptyList())
          .stream()
          .map(V1EnvVar::getName)
          .filter(isReserved)
          .collect(Collectors.toList());

      if (!reservedNames.isEmpty()) {
        failures.add(DomainValidationMessages.reservedVariableNames(prefix, reservedNames));
      }
    }
  }

  void checkReservedEnvironmentVariables(BaseConfiguration configuration, String prefix) {
    new EnvironmentVariableCheck(ServerEnvVars::isReserved).checkEnvironmentVariables(configuration, prefix);
  }
}

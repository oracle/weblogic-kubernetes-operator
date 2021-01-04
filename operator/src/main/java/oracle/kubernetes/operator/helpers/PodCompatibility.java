// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.helpers.PodHelper.AdminPodStepContext.INTERNAL_OPERATOR_CERT_ENV;

/** A class which defines the compatibility rules for existing vs. specified pods. */
class PodCompatibility extends CollectiveCompatibility {
  PodCompatibility(V1Pod expected, V1Pod actual) {
    add("sha256Hash", AnnotationHelper.getHash(expected), AnnotationHelper.getHash(actual));
    add(new PodMetadataCompatibility(expected.getMetadata(), actual.getMetadata()));
    add(new PodSpecCompatibility(Objects.requireNonNull(expected.getSpec()), Objects.requireNonNull(actual.getSpec())));
  }

  static <T> Set<T> asSet(Collection<T> collection) {
    return (collection == null) ? Collections.emptySet() : new HashSet<>(collection);
  }

  static <T> Set<T> getMissingElements(Collection<T> expected, Collection<T> actual) {
    Set<T> missing = asSet(expected);
    if (actual != null) {
      missing.removeAll(actual);
    }
    return missing;
  }

  static class PodMetadataCompatibility extends CollectiveCompatibility {
    PodMetadataCompatibility(V1ObjectMeta expected, V1ObjectMeta actual) {
      add(new RestartVersion(expected, actual));
    }
  }

  static class RestartVersion implements CompatibilityCheck {
    private final V1ObjectMeta expected;
    private final V1ObjectMeta actual;

    RestartVersion(V1ObjectMeta expected, V1ObjectMeta actual) {
      this.expected = expected;
      this.actual = actual;
    }

    @Override
    public boolean isCompatible() {
      return isLabelSame(DOMAINRESTARTVERSION_LABEL)
          && isLabelSame(CLUSTERRESTARTVERSION_LABEL)
          && isLabelSame(SERVERRESTARTVERSION_LABEL);
    }

    private boolean isLabelSame(String labelName) {
      return Objects.equals(
            Objects.requireNonNull(expected.getLabels()).get(labelName),
            Objects.requireNonNull(actual.getLabels()).get(labelName)
      );
    }

    @Override
    public String getIncompatibility() {
      if (!isLabelSame(DOMAINRESTARTVERSION_LABEL)) {
        return "domain restart label changed.";
      } else if (!isLabelSame(CLUSTERRESTARTVERSION_LABEL)) {
        return "cluster restart label changed.";
      } else if (!isLabelSame(SERVERRESTARTVERSION_LABEL)) {
        return "server restart label changed.";
      } else {
        return null;
      }
    }
  }

  static class PodSpecCompatibility extends CollectiveCompatibility {

    PodSpecCompatibility(V1PodSpec expected, V1PodSpec actual) {
      add("securityContext", expected.getSecurityContext(), actual.getSecurityContext());
      add(
          new CompatibleMaps<>(
              "nodeSelector", expected.getNodeSelector(), actual.getNodeSelector()));
      addSets("volumes", expected.getVolumes(), actual.getVolumes());
      addSets("imagePullSecrets", expected.getImagePullSecrets(), actual.getImagePullSecrets());
      addContainerChecks(expected.getContainers(), actual.getContainers());
    }

    private void addContainerChecks(
        List<V1Container> expectedContainers, List<V1Container> actualContainers) {
      Map<String, V1Container> expected = createMap(expectedContainers);
      Map<String, V1Container> actual = createMap(actualContainers);

      List<CompatibilityCheck> containerChecks = new ArrayList<>();
      for (String name : expected.keySet()) {
        if (actual.containsKey(name)) {
          containerChecks.add(createCompatibilityCheck(expected.get(name), actual.get(name)));
        } else {
          containerChecks.add(new Mismatch("Expected container '%s' not found", name));
        }
      }

      for (String name : actual.keySet()) {
        if (!expected.containsKey(name)) {
          containerChecks.add(new Mismatch("Found unexpected container '%s'", name));
        }
      }

      if (containerChecks.isEmpty()) {
        containerChecks.add(new Mismatch("No containers defined"));
      }

      addAll(containerChecks);
    }

    private ContainerCompatibility createCompatibilityCheck(
        V1Container expected, V1Container actual) {
      return new ContainerCompatibility(expected, actual);
    }

    private Map<String, V1Container> createMap(List<V1Container> containers) {
      if (containers == null) {
        return Collections.emptyMap();
      }

      Map<String, V1Container> map = new HashMap<>();
      for (V1Container container : containers) {
        map.put(container.getName(), container);
      }
      return map;
    }
  }

  static class ContainerCompatibility extends CollectiveCompatibility {
    private final String name;

    ContainerCompatibility(V1Container expected, V1Container actual) {
      this.name = expected.getName();

      add("image", expected.getImage(), actual.getImage());
      add("imagePullPolicy", expected.getImagePullPolicy(), actual.getImagePullPolicy());
      add("securityContext", expected.getSecurityContext(), actual.getSecurityContext());
      add(new Probes("liveness", expected.getLivenessProbe(), actual.getLivenessProbe()));
      add(new Probes("readiness", expected.getReadinessProbe(), actual.getReadinessProbe()));
      add(new EqualResources(expected.getResources(), actual.getResources()));
      addSets("volumeMounts", expected.getVolumeMounts(), actual.getVolumeMounts());
      addSets("ports", expected.getPorts(), actual.getPorts());
      addSetsIgnoring("env", expected.getEnv(), actual.getEnv(), INTERNAL_OPERATOR_CERT_ENV);
      addSets("envFrom", expected.getEnvFrom(), actual.getEnvFrom());
    }

    @Override
    String getHeader() {
      return String.format("In container '%s':%n", name);
    }

    @Override
    String getIndent() {
      return "  ";
    }
  }

  static class Mismatch implements CompatibilityCheck {
    private final String errorMessage;

    Mismatch(String format, Object... params) {
      this.errorMessage = String.format(format, params);
    }

    @Override
    public boolean isCompatible() {
      return false;
    }

    @Override
    public String getIncompatibility() {
      return errorMessage;
    }
  }

  static class Probes implements CompatibilityCheck {
    private final String description;
    private final V1Probe expected;
    private final V1Probe actual;

    Probes(String description, V1Probe expected, V1Probe actual) {
      this.description = description;
      this.expected = expected;
      this.actual = actual;
    }

    @Override
    public boolean isCompatible() {
      return Objects.equals(expected, actual)
          || (Objects.equals(expected.getInitialDelaySeconds(), actual.getInitialDelaySeconds())
              && Objects.equals(expected.getTimeoutSeconds(), actual.getTimeoutSeconds())
              && Objects.equals(expected.getPeriodSeconds(), actual.getPeriodSeconds()));
    }

    @Override
    public String getIncompatibility() {
      return String.format(
          "Expected %s probe with initial delay %d, timeout %d and period %d \n"
              + "       but found initial delay %d, timeout %d and period %d.",
          description,
          expected.getInitialDelaySeconds(),
          expected.getTimeoutSeconds(),
          expected.getPeriodSeconds(),
          actual.getInitialDelaySeconds(),
          actual.getTimeoutSeconds(),
          actual.getPeriodSeconds());
    }
  }

  static class EqualResources implements CompatibilityCheck {
    private final V1ResourceRequirements expected;
    private final V1ResourceRequirements actual;

    EqualResources(V1ResourceRequirements expected, V1ResourceRequirements actual) {
      this.expected = expected;
      this.actual = actual;
    }

    private static Map<String, Quantity> getLimits(V1ResourceRequirements requirements) {
      return requirements == null ? Collections.emptyMap() : requirements.getLimits();
    }

    private static Map<String, Quantity> getRequests(V1ResourceRequirements requirements) {
      return requirements == null ? Collections.emptyMap() : requirements.getRequests();
    }

    @Override
    public boolean isCompatible() {
      return KubernetesUtils.mapEquals(getLimits(expected), getLimits(actual))
          && KubernetesUtils.mapEquals(getRequests(expected), getRequests(actual));
    }

    @Override
    public String getIncompatibility() {
      StringBuilder sb = new StringBuilder();
      if (!KubernetesUtils.mapEquals(getLimits(expected), getLimits(actual))) {
        sb.append(
            String.format(
                "Expected resource limits: %s but found %s",
                getLimits(expected), getLimits(actual)));
      }
      if (!KubernetesUtils.mapEquals(getRequests(expected), getRequests(actual))) {
        sb.append(
            String.format(
                "Expected resource requests: %s but found %s",
                getRequests(expected), getRequests(actual)));
      }
      return sb.toString();
    }
  }
}

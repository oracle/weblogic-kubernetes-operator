// Copyright 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import oracle.kubernetes.operator.LabelConstants;

/** A class which defines the compatability rules for existing vs. specified pods. */
class PodCompatibility extends CollectiveCompatibility {
  PodCompatibility(V1Pod expected, V1Pod actual, List<String> volumesToIgnore) {
    add(new PodMetadataCompatibility(expected.getMetadata(), actual.getMetadata()));
    add(new PodSpecCompatibility(expected.getSpec(), actual.getSpec(), volumesToIgnore));
  }

  static class PodMetadataCompatibility extends CollectiveCompatibility {
    PodMetadataCompatibility(V1ObjectMeta expected, V1ObjectMeta actual) {
      add(new RestartVersion(expected, actual));
      add(new DomainVersion(actual));
    }
  }

  static class DomainVersion implements CompatibilityCheck {
    private final V1ObjectMeta actual;

    DomainVersion(V1ObjectMeta actual) {
      this.actual = actual;
    }

    @Override
    public boolean isCompatible() {
      return VersionHelper.matchesResourceVersion(actual, DEFAULT_DOMAIN_VERSION);
    }

    @Override
    public String getIncompatibility() {
      return String.format(
          "Domain version should be %s but was %s", DEFAULT_DOMAIN_VERSION, getDomainVersion());
    }

    private String getDomainVersion() {
      if (actual == null) return "unspecified";
      if (actual.getLabels() == null) return "unspecified";
      return Optional.ofNullable(actual.getLabels().get(LabelConstants.RESOURCE_VERSION_LABEL))
          .orElse("unspecified");
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
      return Objects.equals(expected.getLabels().get(labelName), actual.getLabels().get(labelName));
    }

    @Override
    public String getIncompatibility() {
      if (!isLabelSame(DOMAINRESTARTVERSION_LABEL)) return "domain restart label changed.";
      if (!isLabelSame(CLUSTERRESTARTVERSION_LABEL)) return "cluster restart label changed.";
      if (!isLabelSame(SERVERRESTARTVERSION_LABEL)) return "server restart label changed.";
      return null;
    }
  }

  static class PodSpecCompatibility extends CollectiveCompatibility {
    private List<String> volumesToIgnore;

    PodSpecCompatibility(V1PodSpec expected, V1PodSpec actual, List<String> volumesToIgnore) {
      this.volumesToIgnore = volumesToIgnore;
      add("securityContext", expected.getSecurityContext(), actual.getSecurityContext());
      add(new EqualsMaps<>("nodeSelector", expected.getNodeSelector(), actual.getNodeSelector()));
      addSets("volumes", expected.getVolumes(), relevantVolumes(actual.getVolumes()));
      addSets("imagePullSecrets", expected.getImagePullSecrets(), actual.getImagePullSecrets());
      addContainerChecks(expected.getContainers(), actual.getContainers());
    }

    private List<V1Volume> relevantVolumes(List<V1Volume> volumes) {
      if (volumes == null) return volumes;
      return volumes
          .stream()
          .filter(m -> !volumesToIgnore.contains(m.getName()))
          .collect(Collectors.toList());
    }

    private void addContainerChecks(
        List<V1Container> expectedContainers, List<V1Container> actualContainers) {
      Map<String, V1Container> expected = createMap(expectedContainers);
      Map<String, V1Container> actual = createMap(actualContainers);

      List<CompatibilityCheck> containerChecks = new ArrayList<>();
      for (String name : expected.keySet())
        if (actual.containsKey(name))
          containerChecks.add(createCompatibilityCheck(expected.get(name), actual.get(name)));
        else containerChecks.add(new Mismatch("Expected container '%s' not found", name));

      for (String name : actual.keySet())
        if (!expected.containsKey(name))
          containerChecks.add(new Mismatch("Found unexpected container '%s'", name));

      if (containerChecks.isEmpty()) containerChecks.add(new Mismatch("No containers defined"));

      addAll(containerChecks);
    }

    private ContainerCompatibility createCompatibilityCheck(
        V1Container expected, V1Container actual) {
      return new ContainerCompatibility(expected, actual, volumesToIgnore);
    }

    private Map<String, V1Container> createMap(List<V1Container> containers) {
      if (containers == null) return Collections.emptyMap();

      Map<String, V1Container> map = new HashMap<>();
      for (V1Container container : containers) map.put(container.getName(), container);
      return map;
    }
  }

  static class ContainerCompatibility extends CollectiveCompatibility {
    private final String name;
    private List<String> volumesToIgnore;

    ContainerCompatibility(V1Container expected, V1Container actual, List<String> volumesToIgnore) {
      this.volumesToIgnore = volumesToIgnore;
      this.name = expected.getName();

      add("image", expected.getImage(), actual.getImage());
      add("imagePullPolicy", expected.getImagePullPolicy(), actual.getImagePullPolicy());
      add("securityContext", expected.getSecurityContext(), actual.getSecurityContext());
      add(new Probes("liveness", expected.getLivenessProbe(), actual.getLivenessProbe()));
      add(new Probes("readiness", expected.getReadinessProbe(), actual.getReadinessProbe()));
      add(new EqualResources(expected.getResources(), actual.getResources()));
      addSets("volumeMounts", expected.getVolumeMounts(), relevantMounts(actual.getVolumeMounts()));
      addSets("ports", expected.getPorts(), actual.getPorts());
      addSets("env", expected.getEnv(), actual.getEnv());
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

    private List<V1VolumeMount> relevantMounts(List<V1VolumeMount> volumeMounts) {
      if (volumeMounts == null) return volumeMounts;
      return volumeMounts
          .stream()
          .filter(m -> !volumesToIgnore.contains(m.getName()))
          .collect(Collectors.toList());
    }
  }

  static class Mismatch implements CompatibilityCheck {
    private String errorMessage;

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

  static class EqualsMaps<K, V> implements CompatibilityCheck {
    private final String description;
    private final Map<K, V> expected;
    private final Map<K, V> actual;

    EqualsMaps(String description, Map<K, V> expected, Map<K, V> actual) {
      this.description = description;
      this.expected = expected;
      this.actual = actual;
    }

    @Override
    public boolean isCompatible() {
      return KubernetesUtils.mapEquals(expected, actual);
    }

    @Override
    public String getIncompatibility() {
      return description + " expected: " + expected + " but was: " + actual;
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

    @Override
    public boolean isCompatible() {
      return KubernetesUtils.mapEquals(getLimits(expected), getLimits(actual))
          && KubernetesUtils.mapEquals(getRequests(expected), getRequests(actual));
    }

    private static Map<String, Quantity> getLimits(V1ResourceRequirements requirements) {
      return requirements == null ? Collections.emptyMap() : requirements.getLimits();
    }

    private static Map<String, Quantity> getRequests(V1ResourceRequirements requirements) {
      return requirements == null ? Collections.emptyMap() : requirements.getRequests();
    }

    @Override
    public String getIncompatibility() {
      StringBuilder sb = new StringBuilder();
      if (!KubernetesUtils.mapEquals(getLimits(expected), getLimits(actual)))
        sb.append(
            String.format(
                "Expected resource limits: %s but found %s",
                getLimits(expected), getLimits(actual)));
      if (!KubernetesUtils.mapEquals(getRequests(expected), getRequests(actual)))
        sb.append(
            String.format(
                "Expected resource requests: %s but found %s",
                getRequests(expected), getRequests(actual)));
      return sb.toString();
    }
  }
}

interface CompatibilityCheck {
  boolean isCompatible();

  String getIncompatibility();
}

abstract class CollectiveCompatibility implements CompatibilityCheck {
  protected List<CompatibilityCheck> checks = new ArrayList<>();

  void add(CompatibilityCheck check) {
    checks.add(check);
  }

  void addAll(Collection<CompatibilityCheck> checks) {
    this.checks.addAll(checks);
  }

  @Override
  public boolean isCompatible() {
    for (CompatibilityCheck check : checks) if (!check.isCompatible()) return false;

    return true;
  }

  @Override
  public String getIncompatibility() {
    final List<String> reasons = new ArrayList<>();
    for (CompatibilityCheck check : checks)
      if (!check.isCompatible()) reasons.add(getIndent() + check.getIncompatibility());
    return reasons.isEmpty() ? null : getHeader() + String.join("\n", reasons);
  }

  <T> void addSets(String description, List<T> expected, List<T> actual) {
    add(new EqualSets<>(description, expected, actual));
  }

  protected <T> void add(String description, T expected, T actual) {
    add(new Equality(description, expected, actual));
  }

  String getHeader() {
    return "";
  }

  String getIndent() {
    return "";
  }
}

class Equality implements CompatibilityCheck {
  private final String description;
  private final Object expected;
  private final Object actual;

  Equality(String description, Object expected, Object actual) {
    this.description = description;
    this.expected = expected;
    this.actual = actual;
  }

  @Override
  public boolean isCompatible() {
    return Objects.equals(expected, actual);
  }

  @Override
  public String getIncompatibility() {
    return description + " expected: " + expected + " but was: " + actual;
  }
}

class EqualSets<T> implements CompatibilityCheck {
  private String description;
  private final Collection<T> expected;
  private final Collection<T> actual;

  EqualSets(String description, Collection<T> expected, Collection<T> actual) {
    this.description = description;
    this.expected = expected;
    this.actual = actual;
  }

  @Override
  public boolean isCompatible() {
    if (expected == actual) return true;
    return asSet(expected).equals(asSet(actual));
  }

  private static <T> Set<T> asSet(Collection<T> collection) {
    return (collection == null) ? Collections.emptySet() : new HashSet<>(collection);
  }

  @Override
  public String getIncompatibility() {
    return String.format("%s expected: %s but was: %s", description, expected, actual);
  }
}

// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERRESTARTVERSION_LABEL;

/**
 * Determines whether a given server has completed rolling.
 */
class RollStateComputation {

  private static final List<String> ROLL_LABELS = List.of(
        INTROSPECTION_STATE_LABEL, DOMAINRESTARTVERSION_LABEL, CLUSTERRESTARTVERSION_LABEL, SERVERRESTARTVERSION_LABEL);

  private final DomainPresenceInfo info;

  RollStateComputation(DomainPresenceInfo info) {
    this.info = info;
  }

  /**
   * Returns true if the pod for the named server is up-to-date.
   * @param serverName the name of the server
   */
  boolean isRollCompleteFor(String serverName) {
    final ServerSpec serverSpec = getServerSpec(serverName);
    final V1Pod serverPod = info.getServerPod(serverName);
    return hasExpectedLabels(serverSpec, serverPod) && hasMatchingImages(serverSpec, serverPod);
  }

  ServerSpec getServerSpec(String serverName) {
    return info.getDomain().getServer(serverName, getClusterFor(serverName));
  }

  // Returns the name of the cluster containing the specified server.
  String getClusterFor(@Nonnull String serverName) {
    return Optional.ofNullable(info.getDomain())
          .map(Domain::getStatus)
          .map(DomainStatus::getServers).orElse(Collections.emptyList()).stream()
          .filter(s -> serverName.equals(s.getServerName()))
          .findFirst()
          .map(ServerStatus::getClusterName)
          .orElse(null);
  }

  private boolean hasExpectedLabels(ServerSpec serverSpec, V1Pod serverPod) {
    Map<String, String> expectedLabels = createExpectedLabels(serverSpec);
    Map<String, String> actualLabels = getActualLabels(serverPod);
    return expectedLabels.equals(actualLabels);
  }

  // Returns a map containing the labels (whose keys are in ROLL_LABELS) that the pod should have once it rolls.
  private Map<String, String> createExpectedLabels(ServerSpec serverSpec) {
    return new ExpectedLabelFactory(info, serverSpec).getExpectedLabels();
  }

  static class ExpectedLabelFactory {

    final Map<String, String> expectedLabels = new HashMap<>();

    ExpectedLabelFactory(DomainPresenceInfo info, ServerSpec serverSpec) {
      addLabelIfNeeded(INTROSPECTION_STATE_LABEL, info.getDomain().getIntrospectVersion());
      addLabelIfNeeded(DOMAINRESTARTVERSION_LABEL, serverSpec.getDomainRestartVersion());
      addLabelIfNeeded(CLUSTERRESTARTVERSION_LABEL, serverSpec.getClusterRestartVersion());
      addLabelIfNeeded(SERVERRESTARTVERSION_LABEL, serverSpec.getServerRestartVersion());
    }

    public Map<String, String> getExpectedLabels() {
      return expectedLabels;
    }

    private void addLabelIfNeeded(String labelName, String value) {
      Optional.ofNullable(value).ifPresent(v -> expectedLabels.put(labelName, v));
    }
  }

  // Returns a map containing a subset of the pod labels whose keys are in ROLL_LABELS ignoring any with null values.
  private Map<String, String> getActualLabels(V1Pod serverPod) {
    return Optional.ofNullable(serverPod)
          .map(V1Pod::getMetadata)
          .map(V1ObjectMeta::getLabels)
          .map(Map::entrySet).orElse(Collections.emptySet()).stream()
          .filter(e -> ROLL_LABELS.contains(e.getKey()))
          .filter(e -> e.getValue() != null)
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private boolean hasMatchingImages(ServerSpec serverSpec, V1Pod serverPod) {
    return serverSpec.getImage().equals(serverPod.getSpec().getContainers().get(0).getImage());
  }
}

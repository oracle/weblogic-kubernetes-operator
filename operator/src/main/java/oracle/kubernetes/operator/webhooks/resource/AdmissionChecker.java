// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.jetbrains.annotations.NotNull;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_REPLICAS_CANNOT_BE_HONORED;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_REPLICAS_TOO_HIGH;

/**
 * AdmissionChecker provides the common validation functionality for the validating webhook. It takes an existing
 * resource and a proposed resource and returns a result to indicate if the proposed changes are allowed, and if not,
 * what the problem is.
 *
 * <p>Currently it checks the following:
 * <ul>
 * <li>The proposed replicas settings at the domain level and/or cluster level can be honored by WebLogic domain config.
 * </li>
 * </ul>
 * </p>
 */

public abstract class AdmissionChecker {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  final List<String> messages = new ArrayList<>();
  final List<String> warnings = new ArrayList<>();
  final String domainUid;

  /** Construct a AdmissionChecker. */
  protected AdmissionChecker(@NotNull String domainUid) {
    this.domainUid = domainUid;
  }

  abstract AdmissionResponse validate();

  String createMessage() {
    return perLine(messages);
  }

  boolean areAllClusterReplicaCountsValid(@NotNull DomainResource domain) {
    return getClusterStatusList(domain).stream().allMatch(c -> isClusterReplicaCountValid(domain, c));
  }

  @NotNull
  private List<ClusterStatus> getClusterStatusList(@NotNull DomainResource domain) {
    return Optional.of(domain)
        .map(DomainResource::getStatus)
        .map(DomainStatus::getClusters)
        .orElse(Collections.emptyList());
  }

  private Boolean isClusterReplicaCountValid(@NotNull DomainResource domain, @NotNull ClusterStatus status) {
    boolean isValid =
        getProposedReplicaCount(domain, getCluster(domain, status.getClusterName())) <= getClusterSize(status);
    if (!isValid) {
      messages.add(LOGGER.formatMessage(CLUSTER_REPLICAS_CANNOT_BE_HONORED,
          domainUid, status.getClusterName(), getClusterSize(status)));
      warnings.add(LOGGER.formatMessage(CLUSTER_REPLICAS_TOO_HIGH,
          domainUid, status.getClusterName(), getClusterSize(status)));
    }
    return isValid;
  }

  private ClusterSpec getCluster(@NotNull DomainResource domain, String clusterName) {
    return Optional.of(domain).map(DomainResource::getSpec)
        .map(DomainSpec::getClusters)
        .orElse(Collections.emptyList())
        .stream().filter(c -> nameMatches(c, clusterName)).findAny().orElse(null);
  }

  private boolean nameMatches(ClusterSpec clusterSpec, String clusterName) {
    return Optional.ofNullable(clusterSpec).map(ClusterSpec::getClusterName).orElse("").equals(clusterName);
  }

  private int getClusterSize(ClusterStatus clusterStatus) {
    return Optional.ofNullable(clusterStatus).map(ClusterStatus::getMaximumReplicas).orElse(0);
  }

  private int getProposedReplicaCount(@NotNull DomainResource domain, ClusterSpec clusterSpec) {
    return Optional.ofNullable(clusterSpec).map(ClusterSpec::getReplicas).orElse(getDomainReplicaCount(domain));
  }

  private int getDomainReplicaCount(@NotNull DomainResource domain) {
    return Optional.of(domain).map(DomainResource::getSpec).map(DomainSpec::getReplicas).orElse(0);
  }

  private String perLine(List<String> errors) {
    return String.join(lineSeparator(), errors);
  }
}

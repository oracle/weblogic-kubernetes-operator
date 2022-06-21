// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.jetbrains.annotations.NotNull;

import static java.lang.System.lineSeparator;

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
  final List<String> messages = new ArrayList<>();
  final List<String> warnings = new ArrayList<>();
  final String domainUid;

  /** Construct a AdmissionChecker. */
  protected AdmissionChecker(@NotNull String domainUid) {
    this.domainUid = domainUid;
  }

  abstract AdmissionResponse validate();

  abstract boolean isProposedChangeAllowed();

  String createMessage() {
    return perLine(messages);
  }

  int getClusterSize(ClusterStatus clusterStatus) {
    return Optional.ofNullable(clusterStatus).map(ClusterStatus::getMaximumReplicas).orElse(0);
  }

  int getProposedReplicaCount(@NotNull DomainResource domain, ClusterSpec clusterSpec) {
    return Optional.ofNullable(clusterSpec).map(ClusterSpec::getReplicas).orElse(getDomainReplicaCount(domain));
  }

  int getDomainReplicaCount(@NotNull DomainResource domain) {
    return Optional.of(domain).map(DomainResource::getSpec).map(DomainSpec::getReplicas).orElse(0);
  }

  private String perLine(List<String> errors) {
    return String.join(lineSeparator(), errors);
  }
}

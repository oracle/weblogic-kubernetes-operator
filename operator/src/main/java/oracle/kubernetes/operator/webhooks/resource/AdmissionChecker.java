// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.ApiException;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.weblogic.domain.model.ClusterList;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;

import static java.lang.System.lineSeparator;

/**
 * AdmissionChecker provides the common validation functionality for the validating webhook. It takes an existing
 * resource and a proposed resource and returns a result to indicate if the proposed resource or changes are allowed,
 * and if not, what the problem is.
 */

public abstract class AdmissionChecker {
  final List<String> messages = new ArrayList<>();

  /** Construct a AdmissionChecker. */
  protected AdmissionChecker() {
    // no-op
  }

  /**
   * Validating a proposed request.
   *
   * @return a AdmissionResponse object
   */
  abstract AdmissionResponse validate();

  /**
   * Validate a new resource or a proposed resource against an existing resource. It returns true if the new resource
   * or proposed changes in the proposed resource can be honored, otherwise, returns false.
   *
   * @return true if valid, otherwise false
   */
  public abstract boolean isProposedChangeAllowed();

  boolean hasNoFatalValidationErrors(DomainResource proposedDomain) {
    List<String> failures = proposedDomain.getFatalValidationFailures();
    messages.addAll(failures);
    return failures.isEmpty();
  }

  public static List<ClusterResource> getClusters(String namespace) throws ApiException {
    return Optional.of(new CallBuilder().listCluster(namespace))
        .map(ClusterList::getItems).orElse(Collections.emptyList());
  }

  String createMessage() {
    return perLine(messages);
  }

  Optional<Integer> getClusterSizeOptional(ClusterStatus clusterStatus) {
    return Optional.ofNullable(clusterStatus).map(ClusterStatus::getMaximumReplicas);
  }

  int getClusterSize(ClusterStatus clusterStatus) {
    return getClusterSizeOptional(clusterStatus).orElse(0);
  }

  int getDomainReplicaCount(@Nonnull DomainResource domain) {
    return Optional.of(domain).map(DomainResource::getSpec).map(DomainSpec::getReplicas).orElse(1);
  }

  private String perLine(List<String> errors) {
    return String.join(lineSeparator(), errors);
  }
}

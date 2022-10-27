// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;

import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_REPLICAS_CANNOT_BE_HONORED;

/**
 * ClusterUpdateAdmissionChecker provides the validation functionality for the validating webhook. It takes an existing
 * cluster resource and a proposed cluster resource and returns a result to indicate if the proposed changes are
 * allowed, and if not, hat the problem is.
 *
 * <p>Currently it checks the following:
 * <ul>
 * <li>The proposed replicas settings at the domain level and/or cluster level can be honored by WebLogic domain config.
 * </li>
 * </ul>
 * </p>
 */

public class ClusterUpdateAdmissionChecker extends ClusterScaleAdmissionChecker {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");

  private final ClusterResource existingCluster;

  /** Construct a ClusterUpdateAdmissionChecker. */
  public ClusterUpdateAdmissionChecker(@Nonnull ClusterResource existingCluster,
                                       @Nonnull ClusterResource proposedCluster) {
    super(proposedCluster);
    this.existingCluster = existingCluster;
  }

  @Override
  AdmissionResponse validate() {
    LOGGER.fine("Validating ClusterResource " + proposedCluster + " against " + existingCluster);

    return validateIt();
  }

  @Override
  public boolean isProposedChangeAllowed() {
    return isUnchanged() || super.isProposedChangeAllowed();
  }

  @Override
  public String getErrorMessage() {
    return CLUSTER_REPLICAS_CANNOT_BE_HONORED;
  }

  private boolean isUnchanged() {
    return isSpecUnchanged();
  }

  private boolean isSpecUnchanged() {
    return Optional.of(existingCluster)
        .map(ClusterResource::getSpec)
        .map(this::isProposedSpecUnchanged)
        .orElse(false);
  }

  private boolean isProposedSpecUnchanged(@Nonnull ClusterSpec existingSpec) {
    return Objects.equals(existingSpec, proposedCluster.getSpec());
  }
}

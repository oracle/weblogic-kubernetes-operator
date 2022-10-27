// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks.resource;

import javax.annotation.Nonnull;

import oracle.kubernetes.operator.webhooks.model.AdmissionResponse;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;

import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_SCALE_REPLICAS_TOO_HIGH;

/**
 * ClusterScaleAdmissionChecker provides the validation functionality for the validating webhook. It takes a proposed
 * cluster resource and returns a result to indicate if the proposed changes are allowed, and if not, hat the problem
 * is.
 *
 * <p>Currently it checks the following:
 * <ul>
 * <li>The proposed replicas settings at the domain level and/or cluster level can be honored by WebLogic domain config.
 * </li>
 * </ul>
 * </p>
 */

public class ClusterScaleAdmissionChecker extends ClusterAdmissionChecker {

  /** Construct a ClusterUpdateAdmissionChecker. */
  public ClusterScaleAdmissionChecker(@Nonnull ClusterResource proposedCluster) {
    this.proposedCluster = proposedCluster;
  }

  @Override
  AdmissionResponse validate() {
    LOGGER.fine("Validating ClusterResource " + proposedCluster);

    return validateIt();
  }

  @Override
  public boolean isProposedChangeAllowed() {
    return skipValidation(proposedCluster.getStatus()) || isReplicaCountValid();
  }

  @Override
  public String getErrorMessage() {
    return CLUSTER_SCALE_REPLICAS_TOO_HIGH;
  }
}

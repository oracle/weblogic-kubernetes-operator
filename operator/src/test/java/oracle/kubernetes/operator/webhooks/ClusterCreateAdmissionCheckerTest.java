// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import oracle.kubernetes.operator.webhooks.resource.ClusterCreateAdmissionChecker;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterCreateAdmissionCheckerTest extends AdmissionCheckerTestBase {

  @Override
  void setupCheckers() {
    proposedCluster.setStatus(null);
    clusterChecker = new ClusterCreateAdmissionChecker(proposedCluster);
  }

  @Test
  void whenNewClusterCreated_returnTrue() {
    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenNewClusterCreatedWithInvalidReplicas_returnTrue() {
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }
}

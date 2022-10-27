// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import oracle.kubernetes.operator.webhooks.resource.ClusterCreateAdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.DomainCreateAdmissionChecker;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class DomainCreateAdmissionCheckerTest extends DomainAdmissionCheckerTestBase {

  @Override
  void setupCheckers() {
    proposedDomain.setStatus(null);
    proposedCluster.setStatus(null);
    domainChecker = new DomainCreateAdmissionChecker(proposedDomain);
    clusterChecker = new ClusterCreateAdmissionChecker(proposedCluster);
  }

  @Test
  void whenNewDomainCreated_returnTrue() {
    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenNewDomainCreatedWithInvalidReplicas_returnTrue() {
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(domainChecker.isProposedChangeAllowed(), equalTo(true));
  }
}

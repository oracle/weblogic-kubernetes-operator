// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.webhooks.resource.ClusterUpdateAdmissionChecker;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.GOOD_REPLICAS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterUpdateAdmissionCheckerTest extends ClusterScaleAdmissionCheckerTest {
  @Override
  void setupCheckers() {
    clusterChecker = new ClusterUpdateAdmissionChecker(existingCluster, proposedCluster);
  }

  // ClusterResource validation that is not part of ClusterScaleAdmissionCheckerTest

  @Test
  void whenClusterReplicasChangedToUnsetAndDomainReplicasValid_returnTrue() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(GOOD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedToUnsetAndDomainReplicasInvalid_returnFalse() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedToUnsetAndReadDomainFailed404_returnFalseWithException() {
    testSupport.defineResources(proposedDomain);
    proposedDomain.getSpec().withReplicas(BAD_REPLICAS);
    existingCluster.getSpec().withReplicas(2);
    proposedCluster.getSpec().withReplicas(null);

    testSupport.failOnList(KubernetesTestSupport.DOMAIN, NS, HTTP_FORBIDDEN);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(true));
  }

}

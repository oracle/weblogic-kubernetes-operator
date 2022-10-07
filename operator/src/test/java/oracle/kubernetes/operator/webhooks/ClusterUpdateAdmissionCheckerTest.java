// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.webhooks.resource.ClusterUpdateAdmissionChecker;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.GOOD_REPLICAS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterUpdateAdmissionCheckerTest extends AdmissionCheckerTestBase {
  private static final String WARN_MESSAGE_PATTERN_DOMAIN =
      "Change request to domain resource '%s' causes the replica count of each cluster in '%s' to exceed its cluster "
          + "size '%s' respectively";

  @Override
  void setupCheckers() {
    clusterChecker = new ClusterUpdateAdmissionChecker(existingCluster, proposedCluster);
  }

  // ClusterResource validation

  @Test
  void whenClusterReplicasChangedAndValid_returnTrue() {
    testSupport.defineResources(existingDomain);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedAndInvalid_returnFalse() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChanged_proposedClusterHasNoStatus_returnTrue() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);
    proposedCluster.setStatus(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

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

  @Test
  void whenClusterReplicasChangedValidAndReadDomainFailed404_returnTrueNoException() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);

    testSupport.failOnRead(KubernetesTestSupport.DOMAIN, UID, NS, HTTP_FORBIDDEN);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((ClusterUpdateAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }
}

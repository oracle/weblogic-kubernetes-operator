// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.webhooks.resource.ClusterAdmissionChecker;
import oracle.kubernetes.operator.webhooks.resource.ClusterScaleAdmissionChecker;
import org.junit.jupiter.api.Test;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.GOOD_REPLICAS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterScaleAdmissionCheckerTest extends AdmissionCheckerTestBase {
  @Override
  void setupCheckers() {
    clusterChecker = new ClusterScaleAdmissionChecker(proposedCluster);
  }

  @Test
  void whenClusterReplicasChangedAndValid_returnTrue() {
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterReplicasChangedAndInvalid_returnFalse() {
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
    assertThat(((ClusterAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChanged_proposedClusterHasNoStatus_returnTrue() {
    proposedCluster.getSpec().withReplicas(BAD_REPLICAS);
    proposedCluster.setStatus(null);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((ClusterAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }

  @Test
  void whenClusterReplicasChangedValidAndReadDomainFailed404_returnTrueNoException() {
    testSupport.defineResources(proposedDomain);
    proposedCluster.getSpec().withReplicas(GOOD_REPLICAS);

    testSupport.failOnRead(KubernetesTestSupport.DOMAIN, UID, NS, HTTP_FORBIDDEN);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
    assertThat(((ClusterAdmissionChecker)clusterChecker).hasException(), equalTo(false));
  }
}

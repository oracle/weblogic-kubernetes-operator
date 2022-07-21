// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.RetryStrategyStub;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNAVAILABLE;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CLUSTER_STATUS;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterResourceStatusUpdaterTest {
  private static final String NAME = UID;
  public static final String CLUSTER = "cluster-1";
  private final TerminalStep endStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final ClusterResource cluster = createClusterResource(CLUSTER);
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(NAME);
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(ClientFactoryStub.install());

    domain.setStatus(new DomainStatus());
    testSupport.addDomainPresenceInfo(info);
    testSupport.defineResources(domain, cluster);
  }

  @AfterEach
  void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  void addTopologyToPacket(WlsDomainConfig domainConfig) {
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
  }

  private void updateClusterResourceStatus() {
    testSupport.runSteps(ClusterResourceStatusUpdater.createClusterResourceStatusUpdaterStep(endStep));
  }

  @Test
  void statusStep_updateClusterResourceStatusFromDomainStatus() {
    ClusterStatus newStatus = new ClusterStatus().withMinimumReplicas(0).withMaximumReplicas(8)
        .withClusterName(CLUSTER).withReplicas(2).withReadyReplicas(1).withReplicasGoal(2);
    domain.getStatus().addCluster(newStatus);
    cluster.withStatus(null);
    info.addClusterResource(cluster);

    updateClusterResourceStatus();

    ClusterResource clusterResource = testSupport
        .getResourceWithName(KubernetesTestSupport.CLUSTER, NAME + '-' + CLUSTER);
    assertThat(clusterResource,  notNullValue());
    assertThat(clusterResource.getStatus(), equalTo(newStatus));
  }

  @Test
  void whenMultipleStaticClusterResources_statusStep_updatesAllClusterResourceStatusesFromDomainStatus() {
    ClusterStatus[] newStatuses = new ClusterStatus[3];
    for (int i = 1; i <= 3; i++) {
      String clusterName = "cluster" + i;
      newStatuses[i - 1] = new ClusterStatus().withMinimumReplicas(0).withMaximumReplicas(i)
          .withClusterName(clusterName).withReplicas(2).withReadyReplicas(1).withReplicasGoal(2);
      domain.getStatus().addCluster(newStatuses[i - 1]);
      ClusterResource resource = createClusterResource(clusterName)
          .withStatus(null);
      info.addClusterResource(resource);
      testSupport.defineResources(resource);
    }

    updateClusterResourceStatus();

    for (int i = 1; i <= 3; i++) {
      ClusterResource clusterResource = testSupport
          .getResourceWithName(KubernetesTestSupport.CLUSTER, NAME + "-cluster" + i);
      assertThat(clusterResource,  notNullValue());
      assertThat(clusterResource.getStatus(), equalTo(newStatuses[i - 1]));
    }
  }

  @Test
  void whenNoClusterResourceInDomainPresenceInfo_doNothing() {
    domain.getStatus().addCluster(new ClusterStatus().withMinimumReplicas(0).withMaximumReplicas(5)
        .withClusterName(CLUSTER).withReplicas(2).withReadyReplicas(1).withReplicasGoal(2));

    updateClusterResourceStatus();

    ClusterResource clusterResource = testSupport
        .getResourceWithName(KubernetesTestSupport.CLUSTER, NAME + '-' + "cluster-1");
    assertThat(clusterResource,  notNullValue());
    assertThat(clusterResource.getStatus(), nullValue());
  }

  @Test
  void whenNoClusterStatusInDomainStatus_doNothing() {
    info.addClusterResource(cluster);

    updateClusterResourceStatus();

    ClusterResource clusterResource = testSupport
        .getResourceWithName(KubernetesTestSupport.CLUSTER, NAME + '-' + CLUSTER);
    assertThat(clusterResource,  notNullValue());
    assertThat(clusterResource.getStatus(), nullValue());
  }

  @Test
  void onFailedReplaceStatus_reportUnrecoverableFailure() {
    cluster.withStatus(new ClusterStatus().withMinimumReplicas(0).withMaximumReplicas(8)
        .withClusterName(CLUSTER).withReplicas(2).withReadyReplicas(1).withReplicasGoal(2));
    info.addClusterResource(cluster);
    domain.getStatus().addCluster(new ClusterStatus());
    testSupport.failOnReplaceStatus(CLUSTER_STATUS, NAME + '-' + CLUSTER, NS, HTTP_INTERNAL_ERROR);

    updateClusterResourceStatus();

    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void onFailedReplaceStatus_retryRequest() {
    ClusterStatus newStatus = new ClusterStatus().withMinimumReplicas(0).withMaximumReplicas(8)
        .withClusterName(CLUSTER).withReplicas(2).withReadyReplicas(1).withReplicasGoal(2);
    domain.getStatus().addCluster(newStatus);
    cluster.withStatus(null);
    info.addClusterResource(cluster);
    retryStrategy.setNumRetriesLeft(1);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnReplaceStatus(CLUSTER_STATUS, NAME + '-' + CLUSTER, NS, HTTP_UNAVAILABLE);

    updateClusterResourceStatus();

    ClusterResource clusterResource = testSupport
        .getResourceWithName(KubernetesTestSupport.CLUSTER, NAME + '-' + CLUSTER);
    assertThat(clusterResource,  notNullValue());
    assertThat(clusterResource.getStatus(), equalTo(newStatus));
  }

  private ClusterResource createClusterResource(String clusterName) {
    return new ClusterResource()
        .withMetadata(new V1ObjectMeta().namespace(DomainProcessorTestSetup.NS).name(
            ClusterResourceStatusUpdaterTest.NAME + '-' + clusterName))
        .spec(new ClusterSpec().withClusterName(clusterName));
  }
}

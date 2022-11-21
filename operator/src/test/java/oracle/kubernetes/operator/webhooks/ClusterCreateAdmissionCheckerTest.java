// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.webhooks;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.webhooks.resource.ClusterCreateAdmissionChecker;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.webhooks.AdmissionWebhookTestSetUp.BAD_REPLICAS;
import static oracle.kubernetes.weblogic.domain.model.ServerEnvVars.SERVER_NAME;
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

  @Test
  void whenClusterHasNoVolumeMountPath_returnTrue() {
    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterVolumeMountPathInvalid_returnFalse() {
    proposedCluster.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(BAD_MOUNT_PATH));

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenClusterVolumeMountPathEmpty_returnFalse() {
    proposedCluster.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(""));

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenClusterVolumeMountPathValid_returnTrue() {
    proposedCluster.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(GOOD_MOUNT_PATH));

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainVolumeMountPathContainsToken_returnTrue() {
    proposedCluster.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(MOUNT_PATH_WITH_TOKEN));

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenDomainVolumeMountPathContainsTokenInMiddle_returnTrue() {
    proposedCluster.getSpec().getAdditionalVolumeMounts()
        .add(new V1VolumeMount().name(MOUNT_NAME).mountPath(MOUNT_PATH_WITH_TOKEN_2));

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterSpecHasNoEnvs_returnTrue() {
    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterSpecHasReservedEnvs_returnFalse() {
    List<V1EnvVar> list = createEnvVarListWithReservedName();
    proposedCluster.getSpec().setEnv(list);

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Nonnull
  private List<V1EnvVar> createEnvVarListWithReservedName() {
    List<V1EnvVar> list = new ArrayList<>();
    list.add(new V1EnvVar().name(SERVER_NAME).value("haha"));
    return list;
  }

  @Test
  void whenClusterHasReservedContainerName_returnFalse() {
    proposedCluster.getSpec().getContainers().add(new V1Container().name(WLS_CONTAINER_NAME));

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenClusterHasInvalidContainerPortName_returnFalse() {
    setInvalidContainerPortName();

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
  }

  @Test
  void whenClusterHasValidContainerPortName_returnTrue() {
    setValidContainerPortName();

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterHasNoLivenessProbeSuccessThreshold_returnTrue() {
    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  @Test
  void whenClusterHasInvalidLivenessProbeSuccessThreshold_returnFalse() {
    setInvalidLivenessProbeSuccessThreshold();

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(false));
  }

  private void setInvalidLivenessProbeSuccessThreshold() {
    proposedCluster.getSpec().setLivenessProbeThresholds(2, 1);
  }

  private void setValidLivenessProbeSuccessThreshold() {
    proposedCluster.getSpec().setLivenessProbeThresholds(1, 1);
  }

  @Test
  void whenClusterHasValidLivenessProbeSuccessThreshold_returnTrue() {
    setValidLivenessProbeSuccessThreshold();

    assertThat(clusterChecker.isProposedChangeAllowed(), equalTo(true));
  }

  private void setInvalidContainerPortName() {
    proposedCluster.getSpec().getContainers()
        .add(new V1Container().name(GOOD_CONTAINER_NAME).addPortsItem(new V1ContainerPort().name(BAD_PORT_NAME)));
  }

  private void setValidContainerPortName() {
    proposedCluster.getSpec().getContainers()
        .add(new V1Container().name(GOOD_CONTAINER_NAME).addPortsItem(new V1ContainerPort().name(GOOD_PORT_NAME)));
  }
}

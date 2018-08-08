// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_REPLACED;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Pod;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import org.junit.Before;
import org.junit.Test;

public class ManagedPodHelperTest extends PodHelperTestBase {

  private static final String SERVER_NAME = "ms1";
  private static final int LISTEN_PORT = 8001;
  private static final String ITEM1 = "item1";
  private static final String ITEM2 = "item2";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";
  private static final String CLUSTER_NAME = "test-cluster";

  public ManagedPodHelperTest() {
    super(SERVER_NAME, LISTEN_PORT);
  }

  @Before
  public void augmentPacket() {
    testSupport.addToPacket(ProcessingConstants.SERVER_SCAN, createServerConfig());
  }

  private WlsServerConfig createServerConfig() {
    return new WlsServerConfig(SERVER_NAME, LISTEN_PORT, null, null, false, null, null);
  }

  @Override
  String getPodCreatedMessageKey() {
    return MANAGED_POD_CREATED;
  }

  @Override
  String getPodExistsMessageKey() {
    return MANAGED_POD_EXISTS;
  }

  @Override
  String getPodReplacedMessageKey() {
    return MANAGED_POD_REPLACED;
  }

  @Override
  void expectStepsAfterCreation() {
    expectReplaceDomain();
  }

  @Override
  FiberTestSupport.StepFactory getStepFactory() {
    return PodHelper::createManagedPodStep;
  }

  @SuppressWarnings("unchecked")
  private void expectReplaceDomain() {
    testSupport
        .createCannedResponse("replaceDomain")
        .withNamespace(NS)
        .ignoringBody()
        .returning(new Domain());
  }

  @Override
  V1Pod createPodModel() {
    return new V1Pod().metadata(createPodMetadata()).spec(createPodSpec());
  }

  @Override
  List<String> createStartCommand() {
    return Arrays.asList(
        "/weblogic-operator/scripts/startServer.sh",
        UID,
        getServerName(),
        DOMAIN_NAME,
        ADMIN_SERVER,
        ADMIN_PORT.toString());
  }

  @Test
  public void whenManagedPodCreated_containerHasStartServerCommand() {
    assertThat(
        getCreatedPodSpecContainer().getCommand(),
        contains(
            "/weblogic-operator/scripts/startServer.sh",
            UID,
            getServerName(),
            DOMAIN_NAME,
            ADMIN_SERVER,
            Integer.toString(ADMIN_PORT)));
  }

  @Test
  public void whenPacketHasEnvironmentItems_createManagedPodStartupWithThem() {
    testSupport.addToPacket(
        ProcessingConstants.ENVVARS,
        Arrays.asList(toEnvVar(ITEM1, VALUE1), toEnvVar(ITEM2, VALUE2)));

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar(ITEM1, VALUE1), hasEnvVar(ITEM2, VALUE2)));
  }

  private V1EnvVar toEnvVar(String name, String value) {
    return envItem(name, value);
  }

  @Test
  public void whenPacketHasClusterConfig_managedPodHasClusterLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_SCAN, new WlsClusterConfig(CLUSTER_NAME));

    assertThat(
        getCreatedPod().getMetadata().getLabels(),
        hasEntry(LabelConstants.CLUSTERNAME_LABEL, CLUSTER_NAME));
  }

  @Test
  public void whenExistingManagedPodHasBadVersion_designateForRoll() {
    verifyRollManagedPodWhen(pod -> pod.getMetadata().putLabelsItem(RESOURCE_VERSION_LABEL, "??"));
  }

  @Test
  public void whenExistingManagedPodSpecHasNoContainers_replaceIt() {
    verifyRollManagedPodWhen((pod) -> pod.getSpec().setContainers(null));
  }

  @Test
  public void whenExistingManagedPodSpecHasNoContainersWithExpectedName_replaceIt() {
    verifyRollManagedPodWhen((pod) -> getSpecContainer(pod).setName("???"));
  }

  private V1Container getSpecContainer(V1Pod pod) {
    return pod.getSpec().getContainers().get(0);
  }

  @Test
  public void whenExistingManagedPodSpecContainerHasWrongImage_replaceIt() {
    verifyRollManagedPodWhen((pod) -> getSpecContainer(pod).setImage(VERSIONED_IMAGE));
  }

  @Test
  public void whenExistingManagedPodSpecContainerHasWrongImagePullPolicy_replaceIt() {
    verifyRollManagedPodWhen((pod) -> getSpecContainer(pod).setImagePullPolicy("NONE"));
  }

  @Test
  public void whenExistingManagedPodSpecContainerHasNoPorts_replaceIt() {
    verifyRollManagedPodWhen((pod) -> getSpecContainer(pod).setPorts(Collections.emptyList()));
  }

  @Test
  public void whenExistingManagedPodSpecContainerHasExtraPort_replaceIt() {
    verifyRollManagedPodWhen((pod) -> getSpecContainer(pod).addPortsItem(definePort(1234)));
  }

  private V1ContainerPort definePort(int port) {
    return new V1ContainerPort().protocol("TCP").containerPort(port);
  }

  @Test
  public void whenExistingManagedPodSpecContainerHasIncorrectPort_replaceIt() {
    verifyRollManagedPodWhen(
        (pod) -> getSpecContainer(pod).getPorts().get(0).setContainerPort(1234));
  }

  @Test
  public void whenExistingManagedPodSpecContainerHasWrongEnvVariable_replaceIt() {
    verifyRollManagedPodWhen((pod) -> getSpecContainer(pod).getEnv().get(0).setValue("???"));
  }

  @Test
  public void whenExistingManagedPodSpecContainerHasWrongEnvFrom_replaceIt() {
    verifyRollManagedPodWhen((pod) -> getSpecContainer(pod).envFrom(Collections.emptyList()));
  }

  @SuppressWarnings("unchecked")
  private void verifyRollManagedPodWhen(PodMutator mutator) {
    Map<String, StepAndPacket> rolling = new HashMap<>();
    testSupport.addToPacket(SERVERS_TO_ROLL, rolling);

    V1Pod existingPod = createPodModel();
    mutator.mutate(existingPod);
    expectReadPod(getPodName()).returning(existingPod);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(rolling, not(anEmptyMap()));
  }
}

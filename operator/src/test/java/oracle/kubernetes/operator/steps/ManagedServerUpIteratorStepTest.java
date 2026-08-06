// Copyright (c) 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collections;
import java.util.Map;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.TO_BE_ROLLED_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ManagedServerUpIteratorStepTest {
  private static final String NAMESPACE = "namespace";
  private static final String DOMAIN_UID = "domain1";
  private static final String ADMIN_SERVER = "admin-server";
  private static final String CLUSTER = "cluster-1";

  @Test
  void whenManagedPodIsMissingAndDomainHasRetryableFailure_dontRequeue() {
    DomainResource domain = new DomainResource().withStatus(
        new DomainStatus().addCondition(new DomainCondition(FAILED).withReason(KUBERNETES)));
    Packet packet = new Packet();
    packet.put(ProcessingConstants.DOMAIN_PRESENCE_INFO, new DomainPresenceInfo(domain));

    Result result = new ManagedServerUpIteratorStep.ManagedPodReadyStep("managed-server1", null).apply(packet);

    assertThat(result.isRequeue(), is(false));
  }

  @Test
  void whenStartupConcurrencyIsUnlimited_startupSlotDoesNotDependOnSchedulingOrReadiness() {
    ManagedServerUpIteratorStep.StartManagedServersStep step = createStartStep(0, 3);

    assertThat(step.hasStartupSlot(createPacket()), is(true));
  }

  @Test
  void whenStartupConcurrencyIsLimited_noSlotIsAvailableAtTheLimit() {
    ManagedServerUpIteratorStep.StartManagedServersStep step = createStartStep(2, 2);

    assertThat(step.hasStartupSlot(createPacket()), is(false));
  }

  @Test
  void whenStartupConcurrencyIsLimited_readyServerReleasesAStartupSlot() {
    ManagedServerUpIteratorStep.StartManagedServersStep step = createStartStep(2, 2);
    Packet packet = createPacket();
    getDomainPresenceInfo(packet).setServerPod("managed-server1", createReadyPod("managed-server1"));

    assertThat(step.hasStartupSlot(packet), is(true));
  }

  @Test
  void whenNewMakeRightCycleStarts_preservePendingRollFromPreviousCycle() {
    Packet firstPacket = createPacket();
    DomainPresenceInfo info = getDomainPresenceInfo(firstPacket);
    info.setServerPod("managed-server1", createReadyPod("managed-server1")
        .metadata(new V1ObjectMeta()
            .name(DOMAIN_UID + "-managed-server1")
            .namespace(NAMESPACE)
            .putLabelsItem(CLUSTERNAME_LABEL, CLUSTER)
            .putLabelsItem(SERVERNAME_LABEL, "managed-server1")
            .putAnnotationsItem(TO_BE_ROLLED_LABEL, "true")));
    ManagedServerUpIteratorStep step = new ManagedServerUpIteratorStep(Collections.emptyList(), null);
    step.initialServersToRoll(firstPacket);
    Map<String, Fiber.StepAndPacket> firstRolls = firstPacket.getValue(SERVERS_TO_ROLL);
    Fiber.StepAndPacket pendingRoll = new Fiber.StepAndPacket(null, new Packet());
    firstRolls.put("managed-server1", pendingRoll);

    Packet secondPacket = createPacket(info);
    step.initialServersToRoll(secondPacket);

    Map<String, Fiber.StepAndPacket> secondRolls = secondPacket.getValue(SERVERS_TO_ROLL);
    assertThat(secondRolls, sameInstance(firstRolls));
    assertThat(secondRolls.get("managed-server1"), sameInstance(pendingRoll));
  }

  @Test
  void whenNewMakeRightCycleStarts_discardCompletedRollFromPreviousCycle() {
    Packet firstPacket = createPacket();
    DomainPresenceInfo info = getDomainPresenceInfo(firstPacket);
    info.setServerPod("managed-server1", createReadyPod("managed-server1"));
    ManagedServerUpIteratorStep step = new ManagedServerUpIteratorStep(Collections.emptyList(), null);
    step.initialServersToRoll(firstPacket);
    Map<String, Fiber.StepAndPacket> firstRolls = firstPacket.getValue(SERVERS_TO_ROLL);
    firstRolls.put("managed-server1", new Fiber.StepAndPacket(null, new Packet()));

    Packet secondPacket = createPacket(info);
    step.initialServersToRoll(secondPacket);

    Map<String, Fiber.StepAndPacket> secondRolls = secondPacket.getValue(SERVERS_TO_ROLL);
    assertThat(secondRolls, sameInstance(firstRolls));
    assertThat(secondRolls.isEmpty(), is(true));
  }

  private Packet createPacket() {
    DomainResource domain = new DomainResource()
        .withMetadata(new V1ObjectMeta().namespace(NAMESPACE).name(DOMAIN_UID))
        .withSpec(new DomainSpec().withDomainUid(DOMAIN_UID));
    return createPacket(new DomainPresenceInfo(domain));
  }

  private Packet createPacket(DomainPresenceInfo info) {
    Packet packet = new Packet();
    packet.put(ProcessingConstants.DOMAIN_PRESENCE_INFO, info);
    packet.put(
        ProcessingConstants.DOMAIN_TOPOLOGY,
        new WlsDomainConfig("base-domain").withAdminServer(ADMIN_SERVER, "admin-server", 7001));
    return packet;
  }

  private DomainPresenceInfo getDomainPresenceInfo(Packet packet) {
    return (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
  }

  private V1Pod createReadyPod(String serverName) {
    return new V1Pod()
        .metadata(
            new V1ObjectMeta()
                .name(DOMAIN_UID + "-" + serverName)
                .namespace(NAMESPACE)
                .putLabelsItem(CLUSTERNAME_LABEL, CLUSTER)
                .putLabelsItem(SERVERNAME_LABEL, serverName))
        .status(
            new V1PodStatus()
                .phase("Running")
                .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
  }

  private ManagedServerUpIteratorStep.StartManagedServersStep createStartStep(
      int maxConcurrency, int numStarted) {
    ManagedServerUpIteratorStep.StartManagedServersStep step =
        new ManagedServerUpIteratorStep.StartManagedServersStep(
            CLUSTER, maxConcurrency, Collections.emptyList(), null);
    step.numStarted.set(numStarted);
    return step;
  }
}

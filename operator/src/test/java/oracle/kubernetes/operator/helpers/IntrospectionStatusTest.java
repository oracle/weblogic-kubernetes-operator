// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodConditionBuilder;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatusBuilder;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.LabelConstants.JOBNAME_LABEL;
import static oracle.kubernetes.operator.helpers.LegalNames.toJobIntrospectorName;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** Tests updates to a domain status from progress of the introspection job. */
public class IntrospectionStatusTest {
  private static final String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
  private static final Random random = new Random();

  private static final String IMAGE_NAME = "abc";
  private static final String MESSAGE = "asdf";
  private static final String IMAGE_PULL_FAILURE = "ErrImagePull";
  private static final String UNSCHEDULABLE = "Unschedulable";
  private static final String IMAGE_PULL_BACKOFF = "ImagePullBackoff";
  private static final String DEADLINE_EXCEEDED = "DeadlineExceeded";
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainProcessorImpl processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport));

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());

    domain.setStatus(new DomainStatus().withMessage("").withReason(""));
    presenceInfoMap.put(NS, Map.of(UID, new DomainPresenceInfo(domain)));
    testSupport.defineResources(domain);
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenNewIntrospectorJobPodStatusContainerStatusesNull_ignoreIt() {
    V1Pod introspectorJobPod = createIntrospectorJobPod(createWaitingState(IMAGE_PULL_FAILURE, MESSAGE));
    Objects.requireNonNull(introspectorJobPod.getStatus()).containerStatuses(null);

    processor.dispatchPodWatch(WatchEvent.createAddedEvent(introspectorJobPod).toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), emptyOrNullString());
    assertThat(updatedDomain.getStatus().getMessage(), emptyOrNullString());
  }

  @Test
  public void whenNewIntrospectorJobPodStatusNull_ignoreIt() {
    V1Pod introspectorJobPod = createIntrospectorJobPod(UID);

    processor.dispatchPodWatch(WatchEvent.createAddedEvent(introspectorJobPod).toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), emptyOrNullString());
    assertThat(updatedDomain.getStatus().getMessage(), emptyOrNullString());
  }

  @Test
  public void whenNewIntrospectorJobPodCreatedWithErrImagePullStatus_patchDomain() {
    processor.dispatchPodWatch(
        WatchEvent.createAddedEvent(
            createIntrospectorJobPod(createWaitingState(IMAGE_PULL_FAILURE, MESSAGE)))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(IMAGE_PULL_FAILURE));
    assertThat(updatedDomain.getStatus().getMessage(), equalTo(MESSAGE));
  }

  @Test
  public void whenNewIntrospectorJobPodCreatedWithNullMessage_ignoreIt() {
    processor.dispatchPodWatch(
        WatchEvent.createAddedEvent(
            createIntrospectorJobPod(createWaitingState(IMAGE_PULL_BACKOFF, null)))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), emptyOrNullString());
    assertThat(updatedDomain.getStatus().getMessage(), emptyOrNullString());
  }

  @Test
  public void whenNewIntrospectorJobPodCreatedWithImagePullBackupStatus_patchDomain() {
    processor.dispatchPodWatch(
        WatchEvent.createAddedEvent(
            createIntrospectorJobPod(createWaitingState(IMAGE_PULL_BACKOFF, MESSAGE)))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(IMAGE_PULL_BACKOFF));
    assertThat(updatedDomain.getStatus().getMessage(), equalTo(MESSAGE));
  }

  @Test
  public void whenIntrospectorJobPodPendingWithUnschedulableStatus_patchDomain() {
    processor.dispatchPodWatch(
        WatchEvent.createModifiedEvent(
            createIntrospectorJobPodWithConditions(createPodConditions(UNSCHEDULABLE, MESSAGE)))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(UNSCHEDULABLE));
    assertThat(updatedDomain.getStatus().getMessage(), equalTo(MESSAGE));
  }

  @Test
  public void whenIntrospectorJobPodPhaseFailed_patchDomain() {
    processor.dispatchPodWatch(
        WatchEvent.createModifiedEvent(
            createIntrospectorJobPodWithPhase("Failed", DEADLINE_EXCEEDED))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(DEADLINE_EXCEEDED));
    assertThat(updatedDomain.getStatus().getMessage(), equalTo(MESSAGE));
  }

  @Test
  public void whenNewJobPodFailedBecauseDeletionAfterDeadlineExceeded_dontPatchDomain() {
    processor.dispatchPodWatch(
        WatchEvent.createModifiedEvent(
            createIntrospectorJobPodWithPhase("Failed", DEADLINE_EXCEEDED))
            .toWatchResponse());
    processor.dispatchPodWatch(
        WatchEvent.createModifiedEvent(
            createIntrospectorJobPodWithPhaseReasonMessage(
                "Failed", null, null, createTerminatedState("Error", null)))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(DEADLINE_EXCEEDED));
    assertThat(updatedDomain.getStatus().getMessage(), equalTo(MESSAGE));
  }

  @Test
  public void whenNewJobPodFailedWithoutTerminatedAfterDeadlineExceeded_patchDomain() {
    processor.dispatchPodWatch(
        WatchEvent.createModifiedEvent(
            createIntrospectorJobPodWithPhase("Failed", DEADLINE_EXCEEDED))
            .toWatchResponse());
    processor.dispatchPodWatch(
        WatchEvent.createModifiedEvent(
            createIntrospectorJobPodWithPhase("Failed", "Unknown"))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo("Unknown"));
    assertThat(updatedDomain.getStatus().getMessage(), equalTo(MESSAGE));
  }


  @Test
  public void whenNewIntrospectorJobPodStatusReasonNullAfterImagePullFailure_patchDomain() {
    processor.dispatchPodWatch(
        WatchEvent.createAddedEvent(
            createIntrospectorJobPod(createWaitingState(IMAGE_PULL_FAILURE, MESSAGE)))
            .toWatchResponse());

    processor.dispatchPodWatch(
        WatchEvent.createModifiedEvent(createIntrospectorJobPod(createWaitingState(null, null)))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), emptyOrNullString());
    assertThat(updatedDomain.getStatus().getMessage(), emptyOrNullString());
  }

  private V1Pod createIntrospectorJobPod(V1ContainerState waitingState) {
    return createIntrospectorJobPod(UID)
        .status(
            new V1PodStatusBuilder()
                .addNewContainerStatus()
                .withImage(IMAGE_NAME)
                .withName(toJobIntrospectorName(UID))
                .withReady(false)
                .withState(waitingState)
                .endContainerStatus()
                .build());
  }

  @SuppressWarnings("SameParameterValue")
  private V1Pod createIntrospectorJobPod(String domainUid) {
    return AnnotationHelper.withSha256Hash(
        new V1Pod()
            .metadata(
                withIntrospectorJobLabels(
                    new V1ObjectMeta()
                        .name(toJobIntrospectorName(domainUid) + getPodSuffix())
                        .namespace(NS),
                    domainUid))
            .spec(new V1PodSpec()));
  }

  private V1Pod createIntrospectorJobPodWithConditions(V1PodCondition condition) {
    return createIntrospectorJobPod(UID)
        .status(
            new V1PodStatusBuilder()
                .withConditions(condition)
                .build());
  }

  @SuppressWarnings("SameParameterValue")
  private V1Pod createIntrospectorJobPodWithPhase(String phase, String reason) {
    return createIntrospectorJobPod(UID)
        .status(
            new V1PodStatusBuilder()
                .withPhase(phase)
                .withReason(reason)
                .withMessage(MESSAGE)
                .build());
  }

  private V1Pod createIntrospectorJobPodWithPhaseReasonMessage(
      String phase, String reason, String message, V1ContainerState conState) {
    return createIntrospectorJobPod(UID)
        .status(
            new V1PodStatusBuilder()
                .withPhase(phase)
                .withReason(reason)
                .withMessage(message)
                .addNewContainerStatus()
                .withImage(IMAGE_NAME)
                .withName(toJobIntrospectorName(UID))
                .withReady(false)
                .withState(conState)
                .endContainerStatus()
                .build());
  }

  private V1ContainerState createTerminatedState(String reason, String message) {
    return new V1ContainerStateBuilder()
        .withNewTerminated().withReason(reason)
        .withMessage(message)
        .endTerminated()
        .build();
  }

  private V1ContainerState createWaitingState(String reason, String message) {
    return new V1ContainerStateBuilder()
        .withNewWaiting()
        .withReason(reason)
        .withMessage(message)
        .endWaiting()
        .build();
  }

  @SuppressWarnings("SameParameterValue")
  private V1PodCondition createPodConditions(String reason, String message) {
    return new V1PodConditionBuilder()
        .withReason(reason)
        .withMessage(message)
        .build();
  }


  private String getPodSuffix() {
    return "-" + randomAlphabetic(5);
  }

  private static String randomAlphabetic(int length) {
    if (length < 1) {
      throw new IllegalArgumentException();
    }

    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      int rndCharAt = random.nextInt(CHAR_LOWER.length());
      char rndChar = CHAR_LOWER.charAt(rndCharAt);
      sb.append(rndChar);
    }

    return sb.toString();
  }

  private V1ObjectMeta withIntrospectorJobLabels(V1ObjectMeta meta, String domainUid) {
    return KubernetesUtils.withOperatorLabels(domainUid, meta)
        .putLabelsItem(JOBNAME_LABEL, toJobIntrospectorName(domainUid));
  }
}

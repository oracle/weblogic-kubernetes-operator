// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatusBuilder;
import oracle.kubernetes.operator.DomainProcessorDelegateStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.LabelConstants.JOBNAME_LABEL;
import static oracle.kubernetes.operator.helpers.LegalNames.toJobIntrospectorName;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** Tests updates to a domain status from progress of the introspection job. */
public class IntrospectionStatusTest {
  private static final String IMAGE_NAME = "abc";
  private static final String MESSAGE = "asdf";
  private static final String IMAGE_PULL_FAILURE = "ErrImagePull";
  private static final String IMAGE_PULL_BACKOFF = "ImagePullBackoff";
  private List<Memento> mementos = new ArrayList<>();
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private Map<String, Map<String, DomainPresenceInfo>> presenceInfoMap = new HashMap<>();
  private Domain domain = DomainProcessorTestSetup.createTestDomain();
  private DomainProcessorImpl processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport));

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
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

  @After
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenNewIntrospectorJobPodStatusContainerStatusesNull_ignoreIt() {
    V1Pod introspectorJobPod = createIntrospectorJobPod(createWaitingState(IMAGE_PULL_FAILURE, MESSAGE));
    introspectorJobPod.getStatus().containerStatuses(null);
    
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
  public void whenNewIntrospectorJobPodStatusReasonNullAfterImagePullFailure_dontPatchDomain() {
    processor.dispatchPodWatch(
        WatchEvent.createAddedEvent(
                createIntrospectorJobPod(createWaitingState(IMAGE_PULL_FAILURE, MESSAGE)))
            .toWatchResponse());

    processor.dispatchPodWatch(
        WatchEvent.createModifiedEvent(createIntrospectorJobPod(createWaitingState(null, null)))
            .toWatchResponse());

    Domain updatedDomain = testSupport.getResourceWithName(KubernetesTestSupport.DOMAIN, UID);
    assertThat(updatedDomain.getStatus().getReason(), equalTo(IMAGE_PULL_FAILURE));
    assertThat(updatedDomain.getStatus().getMessage(), equalTo(MESSAGE));
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

  private V1ContainerState createWaitingState(String reason, String message) {
    return new V1ContainerStateBuilder()
        .withNewWaiting()
        .withReason(reason)
        .withMessage(message)
        .endWaiting()
        .build();
  }

  private String getPodSuffix() {
    return "-" + RandomStringUtils.randomAlphabetic(5).toLowerCase();
  }

  private V1ObjectMeta withIntrospectorJobLabels(V1ObjectMeta meta, String domainUid) {
    return KubernetesUtils.withOperatorLabels(domainUid, meta)
        .putLabelsItem(JOBNAME_LABEL, toJobIntrospectorName(domainUid));
  }
}

// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.watcher;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.watcher.JobWatcher.NULL_LISTENER;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the JobWatcher. */
class JobWatcherTest extends WatcherTestBase implements WatchListener<V1Job> {

  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("234");
  private OffsetDateTime clock = SystemClock.now();
  private final V1Job cachedJob = createJob();
  private static final String LATEST_IMAGE = "image:latest";

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final DomainResource domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    addMemento(testSupport.install());
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

  }

  @Override
  @AfterEach
  public void tearDown() throws Exception {
    super.tearDown();

    testSupport.throwOnCompletionFailure();
  }

  private DomainPresenceInfo createDomainPresenceInfo(
      DomainResource domain) {
    DomainPresenceInfo dpi = new DomainPresenceInfo(domain);
    return dpi;
  }

  private DomainResource createDomain() {
    return new DomainResource()
        .withMetadata(new V1ObjectMeta().name(UID).namespace(NS))
        .withSpec(createDomainSpec());
  }

  private DomainSpec createDomainSpec() {
    DomainSpec spec =
        new DomainSpec()
            .withDomainUid(UID)
            .withImage(LATEST_IMAGE)
            .withDomainHomeSourceType(DomainSourceType.PERSISTENT_VOLUME);
    spec.setServerStartPolicy(ServerStartPolicy.IF_NEEDED);

    return spec;
  }

  private V1Job createJob() {
    return new V1Job().metadata(new V1ObjectMeta().name("test").creationTimestamp(getCurrentTime()));
  }

  private OffsetDateTime getCurrentTime() {
    return clock;
  }

  @Override
  public void receivedResponse(Watch.Response<V1Job> response) {
    recordCallBack(response);
  }

  @Test
  void initialRequest_specifiesStartingResourceVersionAndStandardLabelSelector() {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRequestParameters().get(0),
        both(hasEntry("resourceVersion", INITIAL_RESOURCE_VERSION.toString()))
            .and(hasEntry("labelSelector", asList(DOMAINUID_LABEL, CREATEDBYOPERATOR_LABEL))));
  }

  private String asList(String... selectors) {
    return String.join(",", selectors);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected <T> T createObjectWithMetaData(V1ObjectMeta metaData) {
    return (T) new V1Job().metadata(metaData);
  }

  @Override
  protected JobWatcher createWatcher(String ns, AtomicBoolean stopping, BigInteger rv) {
    return JobWatcher.create(this, ns, rv.toString(), tuning, NULL_LISTENER, stopping);
  }

  @Test
  void whenJobHasNoStatus_reportNotComplete() {
    assertThat(JobWatcher.isComplete(cachedJob), is(false));
  }

  @Test
  void whenJobHasNoCondition_reportNotComplete() {
    cachedJob.status(new V1JobStatus());

    assertThat(JobWatcher.isComplete(cachedJob), is(false));
  }

  @Test
  void whenJobConditionTypeFailed_reportNotComplete() {
    cachedJob.status(new V1JobStatus().addConditionsItem(new V1JobCondition().type("Failed")));

    assertThat(JobWatcher.isComplete(cachedJob), is(false));
  }

  @Test
  void whenJobConditionStatusFalse_reportNotComplete() {
    cachedJob.status(
        new V1JobStatus().addConditionsItem(
            new V1JobCondition().type("Complete").status("False")));

    assertThat(JobWatcher.isComplete(cachedJob), is(false));
  }

  @Test
  void whenJobConditionTypeFailedWithTrueStatus_reportFailed() {
    markJobConditionFailed(cachedJob);

    assertThat(JobWatcher.isFailed(cachedJob), is(true));
  }

  @Test
  void whenJobConditionTypeFailedWithNoStatus_reportNotFailed() {
    cachedJob.status(new V1JobStatus().addConditionsItem(
        new V1JobCondition().type("Failed").status("")));

    assertThat(JobWatcher.isFailed(cachedJob), is(false));
  }

  @Test
  void whenJobHasStatusWithNoConditionsAndNotFailed_reportNotFailed() {
    cachedJob.status(new V1JobStatus().conditions(Collections.emptyList()));

    assertThat(JobWatcher.isFailed(cachedJob), is(false));
  }


  @Test
  void whenJobRunningAndReadyConditionIsTrue_reportComplete() {
    markJobCompleted(cachedJob);

    assertThat(JobWatcher.isComplete(cachedJob), is(true));
  }

  private V1Job markJobCompleted(V1Job job) {
    return job.status(new V1JobStatus().addConditionsItem(createCondition("Complete")));
  }

  private V1JobCondition createCondition(String type) {
    return new V1JobCondition().type(type).status("True");
  }

  private V1Job markJobConditionFailed(V1Job job) {
    return setFailedConditionWithReason(job, null);
  }

  private V1Job markJobTimedOut(V1Job job) {
    return markJobTimedOut(job, "DeadlineExceeded");
  }

  @SuppressWarnings("SameParameterValue")
  private V1Job markJobTimedOut(V1Job job, String reason) {
    return setFailedWithReason(job, reason);
  }

  private V1Job setFailedWithReason(V1Job job, String reason) {
    return job.status(new V1JobStatus().failed(1).addConditionsItem(
        createCondition("Failed").reason(reason)));
  }

  @SuppressWarnings("SameParameterValue")
  private V1Job setFailedConditionWithReason(V1Job job, String reason) {
    return job.status(new V1JobStatus().conditions(
            List.of(new V1JobCondition().type("Failed").status("True").reason(reason))));
  }

  @Test
  void whenJobHasNoStatus_reportNotFailed() {
    assertThat(JobWatcher.isFailed(cachedJob), is(false));
  }

  @Test
  void whenJobHasFailedCount_reportFailed() {
    cachedJob.status(new V1JobStatus().failed(1));

    assertThat(JobWatcher.isFailed(cachedJob), is(true));
  }

  @Test
  void whenJobHasFailedReason_getFailedReasonReturnsIt() {
    setFailedWithReason(cachedJob, "AReason");

    assertThat(JobWatcher.getFailedReason(cachedJob), is("AReason"));
  }

  @Test
  void whenJobHasNoFailedReason_getFailedReasonReturnsNull() {
    setFailedWithReason(cachedJob, null);

    assertThat(JobWatcher.getFailedReason(cachedJob), nullValue());
  }

  @Test
  void whenJobHasNoFailedCondition_getFailedReasonReturnsNull() {
    cachedJob.status(new V1JobStatus().addConditionsItem(createCondition("Complete")));

    assertThat(JobWatcher.getFailedReason(cachedJob), nullValue());
  }

  @Test
  void whenJobHasNoJobCondition_getFailedReasonReturnsNull() {
    cachedJob.status(new V1JobStatus().conditions(Collections.emptyList()));

    assertThat(JobWatcher.getFailedReason(cachedJob), nullValue());
  }

  @SuppressWarnings("unused")
  private V1Job createCompletedJobWithDifferentTimestamp(V1Job job) {
    clock = clock.plusSeconds(1);
    return markJobCompleted(createJob());
  }

  public void receivedEvents_areSentToListeners() {
    // Override as JobWatcher doesn't currently implement listener for callback
  }

  public void receivedEvents_areNotSentToListenersWhenWatchersPaused() {
    // Override as JobWatcher doesn't currently implement listener for callback
  }
}

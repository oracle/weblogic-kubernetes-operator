// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.JobHelper;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.steps.WatchDomainIntrospectorJobReadyStep;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.JobWatcher.NULL_LISTENER;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.JOBWATCHER_COMPONENT_NAME;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.ABORTED;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
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
  private final TerminalStep terminalStep = new TerminalStep();
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

  private JobWatcher createWatcher(AtomicBoolean stopping) {
    return createWatcher("ns", stopping, INITIAL_RESOURCE_VERSION);
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

  private V1Job dontChangeJob(V1Job job) {
    return job;
  }

  private V1Job markJobCompleted(V1Job job) {
    return job.status(new V1JobStatus().addConditionsItem(createCondition("Complete")));
  }

  private V1JobCondition createCondition(String type) {
    return new V1JobCondition().type(type).status("True");
  }

  private V1Job markJobFailed(V1Job job) {
    return setFailedWithReason(job, null);
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

  @Test
  void waitForReady_returnsAStep() {
    JobWatcher watcher = createWatcher(new AtomicBoolean(true));

    assertThat(watcher.waitForReady(cachedJob, null), instanceOf(Step.class));
  }

  @Test
  void whenWaitForReadyAppliedToReadyJob_performNextStep() {
    startWaitForReady(this::markJobCompleted);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenWaitForReadyAppliedToIncompleteJob_dontPerformNextStep() {
    startWaitForReady(this::dontChangeJob);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenWaitForReadyAppliedToTimedOutJobWithDeadlineExceeded_terminateWithException() {
    startWaitForReady(job -> markJobTimedOut(job, "DeadlineExceeded"));

    assertThat(terminalStep.wasRun(), is(false));
    testSupport.verifyCompletionThrowable(JobWatcher.DeadlineExceededException.class);
  }

  @Test
  void whenWaitForReadyAppliedToFailedJob_performNextStep() {
    startWaitForReady(this::markJobFailed);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenWaitForReadyAppliedToJobWithFailedCondition_performNextStep() {
    startWaitForReady(this::markJobConditionFailed);

    assertThat(terminalStep.wasRun(), is(true));
  }

  // Starts the waitForReady step with job modified as needed
  private void startWaitForReady(Function<V1Job,V1Job> jobFunction) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    JobWatcher watcher = createWatcher(stopping);
    V1Job cachedJob = jobFunction.apply(createJob());

    try {
      testSupport.runSteps(watcher.waitForReady(cachedJob, terminalStep));
    } finally {
      stopping.set(true);
    }
  }

  @Test
  void whenJobCompletedOnFirstRead_performNextStep() {
    startWaitForReadyThenReadJob(this::markJobCompleted);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenJobInProcessOnFirstRead_dontPerformNextStep() {
    startWaitForReadyThenReadJob(this::dontChangeJob);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenJobWithFluentdInProcessOnFirstRead_performNextStep() {
    startWaitForReadyWithJobPodFluentdThenReadJob(this::dontChangeJob);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenJobTimedOutOnFirstRead_terminateWithException() {
    startWaitForReadyThenReadJob(this::markJobTimedOut, JobHelper.readIntrospectorResults(terminalStep));

    testSupport.verifyCompletionThrowable(JobWatcher.DeadlineExceededException.class);
  }

  @Test
  void whenJobFailedOnFirstRead_performNextStep() {
    startWaitForReadyThenReadJob(this::markJobFailed);

    assertThat(terminalStep.wasRun(), is(true));
  }

  // Starts the waitForReady step with an incomplete job cached, but a modified one in kubernetes
  private void startWaitForReadyThenReadJob(Function<V1Job,V1Job> jobFunction) {
    startWaitForReadyThenReadJob(jobFunction, terminalStep);
  }

  private void startWaitForReadyThenReadJob(Function<V1Job,V1Job> jobFunction, Step nextStep) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    JobWatcher watcher = createWatcher(stopping);

    V1Job persistedJob = jobFunction.apply(createJob());
    testSupport.defineResources(persistedJob);

    try {
      testSupport.runSteps(watcher.waitForReady(cachedJob, nextStep));
    } finally {
      stopping.set(true);
    }
  }

  private void startWatchDomainIntrospectorJobReadyStep(Function<V1Job,V1Job> jobFunction) {
    V1Job persistedJob = jobFunction.apply(createJob());
    testSupport.defineResources(persistedJob);
    testSupport.defineResources(domain);
    testSupport.getPacket().put(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB, this.dontChangeJob(persistedJob));
    domain.getOrCreateStatus().addCondition(new DomainCondition(FAILED).withReason(ABORTED).withMessage("ugh"));

    testSupport.runSteps(testSupport.getPacket(), new WatchDomainIntrospectorJobReadyStep());
  }

  private void startWaitForReadyWithJobPodFluentdThenReadJob(Function<V1Job,V1Job> jobFunction) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    JobWatcher watcher = createWatcher(stopping);

    V1Job persistedJob = jobFunction.apply(createJob());
    testSupport.defineResources(persistedJob);
    V1Pod jobPod = new V1Pod().metadata(new V1ObjectMeta().name(persistedJob.getMetadata().getName()));
    FluentdUtils.defineFluentdJobContainersCompleteStatus(jobPod, persistedJob.getMetadata().getName(),
            true, true);
    testSupport.defineResources(jobPod);

    try {
      testSupport.runSteps(watcher.waitForReady(cachedJob, terminalStep));
    } finally {
      stopping.set(true);
    }
  }

  @Test
  void whenReceivedDeadlineExceededResponse_terminateWithException() {
    sendJobModifiedWatchAfterWaitForReady(this::markJobTimedOut, JobHelper.readIntrospectorResults(terminalStep));

    testSupport.verifyCompletionThrowable(JobWatcher.DeadlineExceededException.class);
  }

  @Test
  void whenReceivedFailedWithNoReasonResponse_performNextStep() {
    sendJobModifiedWatchAfterWaitForReady(this::markJobFailed);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenReceivedCompleteResponse_performNextStep() {
    sendJobModifiedWatchAfterWaitForReady(this::markJobCompleted);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  void whenReceivedCallbackForDifferentCompletedJob_ignoreIt() {
    sendJobModifiedWatchAfterWaitForReady(this::createCompletedJobWithDifferentTimestamp);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenReceivedCallbackForIncompleteJob_ignoreIt() {
    sendJobModifiedWatchAfterWaitForReady(this::dontChangeJob);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenJobWithReady_performNextStep() {
    testSupport.addComponent(JOBWATCHER_COMPONENT_NAME, JobAwaiterStepFactory.class, new JobAwaiterStepFactoryStub());
    startWatchDomainIntrospectorJobReadyStep(this::dontChangeJob);

    assertThat(getDomainStatus().hasConditionWithType(FAILED), is(false));
  }

  private DomainStatus getDomainStatus() {
    return ((DomainResource)testSupport.getResources(DOMAIN).get(0)).getStatus();
  }

  @SuppressWarnings("unused")
  private V1Job createCompletedJobWithDifferentTimestamp(V1Job job) {
    clock = clock.plusSeconds(1);
    return markJobCompleted(createJob());
  }

  // Starts the waitForReady step with an incomplete job and sends a watch indicating that the job has changed
  private void sendJobModifiedWatchAfterWaitForReady(Function<V1Job,V1Job> modifier) {
    sendJobModifiedWatchAfterWaitForReady(modifier, terminalStep);
  }

  private void sendJobModifiedWatchAfterWaitForReady(Function<V1Job,V1Job> modifier, Step nextStep) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    JobWatcher watcher = createWatcher(stopping);
    testSupport.defineResources(cachedJob);

    try {
      testSupport.runSteps(watcher.waitForReady(cachedJob, nextStep));
      watcher.receivedResponse(new Watch.Response<>("MODIFIED", modifier.apply(createJob())));
    } finally {
      stopping.set(true);
    }
  }

  public void receivedEvents_areSentToListeners() {
    // Override as JobWatcher doesn't currently implement listener for callback
  }

  public void receivedEvents_areNotSentToListenersWhenWatchersPaused() {
    // Override as JobWatcher doesn't currently implement listener for callback
  }

  private static class JobAwaiterStepFactoryStub implements JobAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Job job, Step next) {
      return next;
    }
  }

}

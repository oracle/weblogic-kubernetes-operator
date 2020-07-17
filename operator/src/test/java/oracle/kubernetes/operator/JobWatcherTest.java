// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.math.BigInteger;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Test;

import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the JobWatcher. */
public class JobWatcherTest extends WatcherTestBase implements WatchListener<V1Job> {

  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("234");
  private static final String NS = "ns1";
  private static final String VERSION = "123";
  private V1Job cachedJob = createJob();
  private long clock;

  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    addMemento(testSupport.install());
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();

    testSupport.throwOnCompletionFailure();
  }

  private V1Job createJob() {
    return new V1Job().metadata(new V1ObjectMeta().name("test").creationTimestamp(getCurrentTime()));
  }

  private DateTime getCurrentTime() {
    return new DateTime(clock);
  }

  @Override
  public void receivedResponse(Watch.Response<V1Job> response) {
    recordCallBack(response);
  }

  @Test
  public void initialRequest_specifiesStartingResourceVersionAndStandardLabelSelector() {
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
    return JobWatcher.create(this, ns, rv.toString(), tuning, stopping);
  }

  private JobWatcher createWatcher(AtomicBoolean stopping) {
    return JobWatcher.create(this, "ns", INITIAL_RESOURCE_VERSION.toString(), tuning, stopping);
  }

  @Test
  public void whenJobHasNoStatus_reportNotComplete() {
    assertThat(JobWatcher.isComplete(cachedJob), is(false));
  }

  @Test
  public void whenJobHasNoCondition_reportNotComplete() {
    cachedJob.status(new V1JobStatus());

    assertThat(JobWatcher.isComplete(cachedJob), is(false));
  }

  @Test
  public void whenJobConditionTypeFailed_reportNotComplete() {
    cachedJob.status(new V1JobStatus().addConditionsItem(new V1JobCondition().type("Failed")));

    assertThat(JobWatcher.isComplete(cachedJob), is(false));
  }

  @Test
  public void whenJobConditionStatusFalse_reportNotComplete() {
    cachedJob.status(
        new V1JobStatus().addConditionsItem(new V1JobCondition().type("Complete").status("False")));

    assertThat(JobWatcher.isComplete(cachedJob), is(false));
  }

  @Test
  public void whenJobRunningAndReadyConditionIsTrue_reportComplete() {
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

  private V1Job markJobTimedOut(V1Job job) {
    return setFailedWithReason(job, "DeadlineExceeded");
  }

  private V1Job setFailedWithReason(V1Job job, String reason) {
    return job.status(new V1JobStatus().failed(1).addConditionsItem(createCondition("Failed").reason(reason)));
  }

  @Test
  public void whenJobHasNoStatus_reportNotFailed() {
    assertThat(JobWatcher.isFailed(cachedJob), is(false));
  }

  @Test
  public void whenJobHasFailedCount_reportFailed() {
    cachedJob.status(new V1JobStatus().failed(1));

    assertThat(JobWatcher.isFailed(cachedJob), is(true));
  }

  @Test
  public void whenJobHasFailedReason_getFailedReasonReturnsIt() {
    setFailedWithReason(cachedJob, "AReason");

    assertThat(JobWatcher.getFailedReason(cachedJob), is("AReason"));
  }

  @Test
  public void whenJobHasNoFailedReason_getFailedReasonReturnsNull() {
    setFailedWithReason(cachedJob, null);

    assertThat(JobWatcher.getFailedReason(cachedJob), nullValue());
  }

  @Test
  public void whenJobHasNoFailedCondition_getFailedReasonReturnsNull() {
    cachedJob.status(new V1JobStatus().addConditionsItem(createCondition("Complete")));

    assertThat(JobWatcher.getFailedReason(cachedJob), nullValue());
  }

  @Test
  public void whenJobHasNoJobCondition_getFailedReasonReturnsNull() {
    cachedJob.status(new V1JobStatus().conditions(Collections.emptyList()));

    assertThat(JobWatcher.getFailedReason(cachedJob), nullValue());
  }

  @Test
  public void waitForReady_returnsAStep() {
    JobWatcher watcher = createWatcher(new AtomicBoolean(true));

    assertThat(watcher.waitForReady(cachedJob, null), instanceOf(Step.class));
  }

  @Test
  public void whenWaitForReadyAppliedToReadyJob_performNextStep() {
    startWaitForReady(this::markJobCompleted);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenWaitForReadyAppliedToIncompleteJob_dontPerformNextStep() {
    startWaitForReady(this::dontChangeJob);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenWaitForReadyAppliedToTimedOutJob_terminateWithException() {
    startWaitForReady(this::markJobTimedOut);

    assertThat(terminalStep.wasRun(), is(false));
    testSupport.verifyCompletionThrowable(JobWatcher.DeadlineExceededException.class);
  }

  @Test
  public void whenWaitForReadyAppliedToFailedJob_performNextStep() {
    startWaitForReady(this::markJobFailed);

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
  public void whenJobCompletedOnFirstRead_performNextStep() {
    startWaitForReadyThenReadJob(this::markJobCompleted);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenJobInProcessOnFirstRead_dontPerformNextStep() {
    startWaitForReadyThenReadJob(this::dontChangeJob);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenJobTimedOutOnFirstRead_terminateWithException() {
    startWaitForReadyThenReadJob(this::markJobTimedOut);

    assertThat(terminalStep.wasRun(), is(false));
    testSupport.verifyCompletionThrowable(JobWatcher.DeadlineExceededException.class);
  }

  @Test
  public void whenJobFailedOnFirstRead_performNextStep() {
    startWaitForReadyThenReadJob(this::markJobFailed);

    assertThat(terminalStep.wasRun(), is(true));
  }

  // Starts the waitForReady step with an incomplete job cached, but a modified one in kubernetes
  private void startWaitForReadyThenReadJob(Function<V1Job,V1Job> jobFunction) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    JobWatcher watcher = createWatcher(stopping);

    V1Job persistedJob = jobFunction.apply(createJob());
    testSupport.defineResources(persistedJob);

    try {
      testSupport.runSteps(watcher.waitForReady(cachedJob, terminalStep));
    } finally {
      stopping.set(true);
    }
  }

  @Test
  public void whenReceivedDeadlineExceededResponse_terminateWithException() {
    sendJobModifiedWatchAfterWaitForReady(this::markJobTimedOut);

    assertThat(terminalStep.wasRun(), is(false));
    testSupport.verifyCompletionThrowable(JobWatcher.DeadlineExceededException.class);
  }

  @Test
  public void whenReceivedFailedWithNoReasonResponse_performNextStep() {
    sendJobModifiedWatchAfterWaitForReady(this::markJobFailed);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenReceivedCompleteResponse_performNextStep() {
    sendJobModifiedWatchAfterWaitForReady(this::markJobCompleted);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenReceivedCallbackForDifferentCompletedJob_ignoreIt() {
    sendJobModifiedWatchAfterWaitForReady(this::createCompletedJobWithDifferentTimestamp);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenReceivedCallbackForIncompleteJob_ignoreIt() {
    sendJobModifiedWatchAfterWaitForReady(this::dontChangeJob);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @SuppressWarnings("unused")
  private V1Job createCompletedJobWithDifferentTimestamp(V1Job job) {
    clock++;
    return markJobCompleted(createJob());
  }

  // Starts the waitForReady step with an incomplete job and sends a watch indicating that the job has changed
  private void sendJobModifiedWatchAfterWaitForReady(Function<V1Job,V1Job> modifier) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    JobWatcher watcher = createWatcher(stopping);
    testSupport.defineResources(cachedJob);

    try {
      testSupport.runSteps(watcher.waitForReady(cachedJob, terminalStep));
      watcher.receivedResponse(new Watch.Response<>("MODIFIED", modifier.apply(createJob())));
    } finally {
      stopping.set(true);
    }
  }

  @Test
  public void afterFactoryDefined_createWatcherForDomain() {
    AtomicBoolean stopping = new AtomicBoolean(true);
    JobWatcher.defineFactory(this, tuning, s -> stopping);
    Domain domain = new Domain().withMetadata(new V1ObjectMeta().namespace(NS).resourceVersion(VERSION));

    assertThat(JobWatcher.getOrCreateFor(domain), notNullValue());
  }

  @Test
  public void afterWatcherCreated_itIsCached() {
    AtomicBoolean stopping = new AtomicBoolean(true);
    JobWatcher.defineFactory(this, tuning, ns -> stopping);
    Domain domain =
        new Domain().withMetadata(new V1ObjectMeta().namespace(NS).resourceVersion(VERSION));
    JobWatcher firstWatcher = JobWatcher.getOrCreateFor(domain);

    assertThat(JobWatcher.getOrCreateFor(domain), sameInstance(firstWatcher));
  }

  @SuppressWarnings({"rawtypes"})
  public void receivedEvents_areSentToListeners() {
    // Override as JobWatcher doesn't currently implement listener for callback
  }

}

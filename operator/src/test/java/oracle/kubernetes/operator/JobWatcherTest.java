// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobCondition;
import io.kubernetes.client.models.V1JobStatus;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.hamcrest.Matchers;
import org.joda.time.DateTime;
import org.junit.Test;

import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.core.IsNull.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the JobWatcher. */
public class JobWatcherTest extends WatcherTestBase implements WatchListener<V1Job> {

  private static final int INITIAL_RESOURCE_VERSION = 234;
  private static final String NS = "ns1";
  private static final String VERSION = "123";
  private Packet packet;
  private V1Job job = new V1Job().metadata(new V1ObjectMeta().name("test").creationTimestamp(new DateTime()));
  private FiberTestSupport fiberTestSupport = new FiberTestSupport();

  public void setUp() throws Exception {
    super.setUp();
    packet = new Packet();
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
        both(hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)))
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
  protected JobWatcher createWatcher(String ns, AtomicBoolean stopping, int rv) {
    return JobWatcher.create(this, ns, Integer.toString(rv), tuning, stopping);
  }

  private JobWatcher createWatcher(AtomicBoolean stopping) {
    return JobWatcher.create(this, "ns", Integer.toString(INITIAL_RESOURCE_VERSION), tuning, stopping);
  }

  @Test
  public void whenJobHasNoStatus_reportNotComplete() {
    assertThat(JobWatcher.isComplete(job), is(false));
  }

  @Test
  public void whenJobHasNoCondition_reportNotComplete() {
    job.status(new V1JobStatus());

    assertThat(JobWatcher.isComplete(job), is(false));
  }

  @Test
  public void whenJobConditionTypeFailed_reportNotComplete() {
    job.status(new V1JobStatus().addConditionsItem(new V1JobCondition().type("Failed")));

    assertThat(JobWatcher.isComplete(job), is(false));
  }

  @Test
  public void whenJobConditionStatusFalse_reportNotComplete() {
    job.status(
        new V1JobStatus().addConditionsItem(new V1JobCondition().type("Complete").status("False")));

    assertThat(JobWatcher.isComplete(job), is(false));
  }

  @Test
  public void whenJobRunningAndReadyConditionIsTrue_reportComplete() {
    makeJobReady(job);

    assertThat(JobWatcher.isComplete(job), is(true));
  }

  private void makeJobReady(V1Job job) {
    List<V1JobCondition> conditions =
        Collections.singletonList(new V1JobCondition().type("Complete").status("True"));
    job.status(new V1JobStatus().conditions(conditions));
  }

  private void makeJobFailed(V1Job job, String reason) {
    List<V1JobCondition> conditions =
        Collections.singletonList(new V1JobCondition().type("Failed").status("True").reason(reason));
    job.status(new V1JobStatus().failed(1).conditions(conditions));
  }

  @Test
  public void whenJobHasNoStatus_reportNotFailed() {
    assertThat(JobWatcher.isFailed(job), is(false));
  }

  @Test
  public void whenJobHasFailedCount_reportFailed() {
    job.status(new V1JobStatus().failed(1));

    assertThat(JobWatcher.isFailed(job), is(true));
  }

  @Test
  public void whenJobHasFailedReason_getFailedReasonReturnsIt() {
    makeJobFailed(job, "DeadlineExceeded");

    assertThat(JobWatcher.getFailedReason(job), is("DeadlineExceeded"));
  }

  @Test
  public void whenJobHasNoFailedReason_getFailedReasonReturnsNull() {
    makeJobFailed(job, null);

    assertThat(JobWatcher.getFailedReason(job), nullValue());
  }

  @Test
  public void whenJobHasNoFailedCondition_getFailedReasonReturnsNull() {
    job.status(new V1JobStatus().addConditionsItem(new V1JobCondition().type("Complete").status("True")));

    assertThat(JobWatcher.getFailedReason(job), nullValue());
  }

  @Test
  public void whenJobHasNoJobCondition_getFailedReasonReturnsNull() {
    job.status(new V1JobStatus().conditions(Collections.EMPTY_LIST));

    assertThat(JobWatcher.getFailedReason(job), nullValue());
  }

  @Test
  public void waitForReady_returnsAStep() {
    AtomicBoolean stopping = new AtomicBoolean(true);
    JobWatcher watcher =
        JobWatcher.create(this, "ns", Integer.toString(INITIAL_RESOURCE_VERSION), tuning, stopping);

    assertThat(watcher.waitForReady(job, null), Matchers.instanceOf(Step.class));
  }

  @Test
  public void whenWaitForReadyAppliedToReadyJob_performNextStep() {
    AtomicBoolean stopping = new AtomicBoolean(false);
    JobWatcher watcher =
        JobWatcher.create(this, "ns", Integer.toString(INITIAL_RESOURCE_VERSION), tuning, stopping);

    makeJobReady(job);

    ListeningTerminalStep listeningStep = new ListeningTerminalStep(stopping);
    Step step = watcher.waitForReady(job, listeningStep);
    NextAction nextAction = step.apply(packet);
    nextAction.getNext().apply(packet);

    assertThat(listeningStep.wasPerformed, is(true));
  }

  @Test
  public void whenReceivedDeadlineExceededResponse_doNotPerformNextStep() {
    doReceivedResponseTest((j) -> makeJobFailed(j, "DeadlineExceeded"), false);
  }

  @Test
  public void whenReceivedFailedWithNoReasonResponse_performNextStep() {
    doReceivedResponseTest((j) -> makeJobFailed(j, null), true);
  }

  @Test
  public void whenReceivedCompleteResponse_performNextStep() {
    doReceivedResponseTest((j) -> makeJobReady(j), true);
  }

  private void doReceivedResponseTest(Consumer<V1Job> jobStatusUpdater, final boolean expectedResult) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    JobWatcher watcher =
        JobWatcher.create(this, "ns", Integer.toString(INITIAL_RESOURCE_VERSION), tuning, stopping);

    ListeningTerminalStep listeningStep = new ListeningTerminalStep(stopping);
    Step step = watcher.waitForReady(job, listeningStep);

    // run WaitForReadyStep.apply() and the doSuspend() inside apply() to set up Complete callback
    fiberTestSupport.runSteps(step);

    jobStatusUpdater.accept(job);

    watcher.receivedResponse(new Watch.Response<>("MODIFIED", job));
    assertThat(listeningStep.wasPerformed, is(expectedResult));
  }

  @Test
  public void afterFactoryDefined_createWatcherForDomain() {
    AtomicBoolean stopping = new AtomicBoolean(true);
    JobWatcher.defineFactory(this, tuning, ns -> stopping);
    Domain domain =
        new Domain().withMetadata(new V1ObjectMeta().namespace(NS).resourceVersion(VERSION));

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

  static class ListeningTerminalStep extends Step {
    private boolean wasPerformed = false;

    ListeningTerminalStep(AtomicBoolean stopping) {
      super(null);
      stopping.set(true);
    }

    @Override
    public NextAction apply(Packet packet) {
      wasPerformed = true;
      return doEnd(packet);
    }
  }
}

// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.logging.LogRecord;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ContainerState;
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated;
import io.kubernetes.client.openapi.models.V1ContainerStateWaiting;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.helpers.LegalNames.DEFAULT_INTROSPECTOR_JOB_NAME_SUFFIX;
import static oracle.kubernetes.operator.logging.MessageKeys.EXECUTE_MAKE_RIGHT_DOMAIN;
import static oracle.kubernetes.operator.logging.MessageKeys.INTROSPECTOR_POD_FAILED;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the PodWatcher. */
public class PodWatcherTest extends WatcherTestBase implements WatchListener<V1Pod> {

  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("234");
  private static final String NS = "ns";
  private static final String NAME = "test";
  private static final int RECHECK_SECONDS = 10;
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();
  private final List<LogRecord> logRecords = new java.util.ArrayList<>();

  @Override
  protected TestUtils.ConsoleHandlerMemento configureOperatorLogger() {
    return super.configureOperatorLogger()
          .collectLogMessages(logRecords, getMessageKeys())
          .withLogLevel(java.util.logging.Level.FINE)
          .ignoringLoggedExceptions(ApiException.class);
  }

  @Override
  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    addMemento(testSupport.install());
  }

  private String[] getMessageKeys() {
    return new String[] {
        getPodFailedMessageKey(),
        getMakeRightDomainStepKey()
    };
  }

  private String getPodFailedMessageKey() {
    return INTROSPECTOR_POD_FAILED;
  }

  private String getMakeRightDomainStepKey() {
    return EXECUTE_MAKE_RIGHT_DOMAIN;
  }

  @Override
  public void receivedResponse(Watch.Response<V1Pod> response) {
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
    return (T) new V1Pod().metadata(metaData);
  }

  @Override
  protected PodWatcher createWatcher(String ns, AtomicBoolean stopping, BigInteger rv) {
    return PodWatcher.create(this, ns, rv.toString(), tuning, this, stopping);
  }

  private PodWatcher createWatcher(AtomicBoolean stopping) {
    return PodWatcher.create(this, NS, INITIAL_RESOURCE_VERSION.toString(), tuning, this, stopping);
  }

  @Test
  public void waitForReady_returnsAStep() {
    AtomicBoolean stopping = new AtomicBoolean(true);
    PodWatcher watcher = createWatcher(stopping);

    assertThat(watcher.waitForReady(createPod(), null), Matchers.instanceOf(Step.class));
  }

  private V1Pod createPod() {
    return new V1Pod().metadata(new V1ObjectMeta().namespace(NS).name(NAME));
  }

  private V1Pod createIntrospectorPod() {
    return new V1Pod().metadata(new V1ObjectMeta().namespace(NS).name(NAME + DEFAULT_INTROSPECTOR_JOB_NAME_SUFFIX));
  }

  @Test
  public void whenPodInitiallyReady_waitForReadyProceedsImmediately() {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);

    V1Pod pod = createPod();
    markPodReady(pod);

    try {
      testSupport.runSteps(watcher.waitForReady(pod, terminalStep));

      assertThat(terminalStep.wasRun(), is(true));
    } finally {
      stopping.set(true);
    }
  }

  private V1Pod dontChangePod(V1Pod pod) {
    return pod;
  }

  private V1Pod markPodReady(V1Pod pod) {
    return pod.status(new V1PodStatus().phase("Running").addConditionsItem(createCondition("Ready")));
  }

  private V1Pod addContainerStateWaitingMessage(V1Pod pod) {
    return pod.status(new V1PodStatus()
        .containerStatuses(java.util.Collections.singletonList(
            new V1ContainerStatus()
                .ready(false)
                .state(new V1ContainerState().waiting(
                    new V1ContainerStateWaiting().message("Error"))))));
  }

  private V1Pod addContainerStateTerminatedReason(V1Pod pod) {
    return pod.status(new V1PodStatus()
        .containerStatuses(java.util.Collections.singletonList(
            new V1ContainerStatus()
                .ready(false)
                .state(new V1ContainerState().terminated(
                    new V1ContainerStateTerminated().reason("Error"))))));
  }

  @SuppressWarnings("SameParameterValue")
  private V1PodCondition createCondition(String type) {
    return new V1PodCondition().type(type).status("True");
  }

  @Test
  public void whenPodReadyWhenWaitCreated_performNextStep() {
    startWaitForReady(this::markPodReady);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenPodNotReadyWhenWaitCreated_dontPerformNextStep() {
    startWaitForReady(this::dontChangePod);

    assertThat(terminalStep.wasRun(), is(false));
  }

  private void startWaitForReady(Function<V1Pod, V1Pod> modifier) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);

    testSupport.defineResources(modifier.apply(createPod()));

    try {
      testSupport.runSteps(watcher.waitForReady(createPod(), terminalStep));

    } finally {
      stopping.set(true);
    }
  }

  @Test
  public void whenPodReadyOnFirstRead_runNextStep() {
    startWaitForReadyThenReadPod(this::markPodReady);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenPodNotReadyOnFirstRead_dontRunNextStep() {
    startWaitForReadyThenReadPod(this::dontChangePod);

    assertThat(terminalStep.wasRun(), is(false));
  }

  private void startWaitForReadyThenReadPod(Function<V1Pod,V1Pod> modifier) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);

    V1Pod persistedPod = modifier.apply(createPod());
    testSupport.defineResources(persistedPod);

    try {
      testSupport.runSteps(watcher.waitForReady(createPod(), terminalStep));
    } finally {
      stopping.set(true);
    }
  }

  @Test
  public void whenPodReadyLater_runNextStep() {
    sendPodModifiedWatchAfterWaitForReady(this::markPodReady);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenPodCreatedAndReadyLater_runNextStep() {
    sendPodModifiedWatchAfterResourceCreatedAndWaitForReady(this::markPodReady);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenPodCreatedAndNotReadyAfterTimeout_executeMakeRightDomain() {
    executeWaitForReady();

    testSupport.setTime(10, TimeUnit.SECONDS);

    assertThat(terminalStep.wasRun(), is(true));
    assertThat(logRecords, containsFine(getMakeRightDomainStepKey()));
  }

  @Test
  public void whenPodNotReadyLater_dontRunNextStep() {
    sendPodModifiedWatchAfterWaitForReady(this::dontChangePod);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenPodNotReadyLaterAndThenReady_runNextStep() {
    sendPodModifiedWatchAfterWaitForReady(this::dontChangePod, this::markPodReady);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenPodNotReadyLaterAndThenReadyButNoWatchEvent_runNextStep() {
    makeModifiedPodReadyWithNoWatchEvent(this::markPodReady);

    testSupport.setTime(RECHECK_SECONDS, TimeUnit.SECONDS);

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenIntrospectPodNotReadyWithTerminatedReason_logPodStatus() {
    sendIntrospectorPodModifiedWatchAfterWaitForReady(this::addContainerStateTerminatedReason);

    assertThat(terminalStep.wasRun(), is(false));
    assertThat(logRecords, containsInfo(getPodFailedMessageKey()));
  }

  @Test
  public void whenIntrospectPodNotReadyWithWaitingMessage_logPodStatus() {
    sendIntrospectorPodModifiedWatchAfterWaitForReady(this::addContainerStateWaitingMessage);

    assertThat(terminalStep.wasRun(), is(false));
    assertThat(logRecords, containsInfo(getPodFailedMessageKey()));
  }

  // Starts the waitForReady step with an incomplete pod and sends a watch indicating that the pod has changed
  @SafeVarargs
  private void sendPodModifiedWatchAfterWaitForReady(Function<V1Pod,V1Pod>... modifiers) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);
    testSupport.defineResources(createPod());

    try {
      testSupport.runSteps(watcher.waitForReady(createPod(), terminalStep));
      for (Function<V1Pod,V1Pod> modifier : modifiers) {
        watcher.receivedResponse(new Watch.Response<>("MODIFIED", modifier.apply(createPod())));
      }
    } finally {
      stopping.set(true);
    }
  }

  // Starts the waitForReady step with an uncreated pod and sends a watch indicating that the pod has changed
  @SafeVarargs
  private void sendPodModifiedWatchAfterResourceCreatedAndWaitForReady(Function<V1Pod,V1Pod>... modifiers) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);

    try {
      testSupport.addDomainPresenceInfo(new DomainPresenceInfo(NS, "domain1"));
      testSupport.runSteps(watcher.waitForReady(NAME, terminalStep));
      for (Function<V1Pod,V1Pod> modifier : modifiers) {
        watcher.receivedResponse(new Watch.Response<>("MODIFIED", modifier.apply(createPod())));
      }
    } finally {
      stopping.set(true);
    }
  }

  // Starts the waitForReady step with an uncreated pod and sends a watch indicating that the pod has changed
  private void executeWaitForReady() {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);

    try {
      testSupport.addDomainPresenceInfo(new DomainPresenceInfo(NS, "domain1"));
      testSupport.runSteps(watcher.waitForReady(NAME, terminalStep));
    } finally {
      stopping.set(true);
    }
  }

  // Simulates a pod that is ready but where Kubernetes has failed to send the watch event
  @SafeVarargs
  private void makeModifiedPodReadyWithNoWatchEvent(Function<V1Pod,V1Pod>... modifiers) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);
    V1Pod pod = createPod();
    testSupport.defineResources(pod);

    try {
      testSupport.runSteps(watcher.waitForReady(createPod(), terminalStep));
      for (Function<V1Pod,V1Pod> modifier : modifiers) {
        modifier.apply(pod);
      }
    } finally {
      stopping.set(true);
    }
  }

  // Starts the waitForReady step with an incomplete pod and sends a watch indicating that the pod has changed
  @SafeVarargs
  private void sendIntrospectorPodModifiedWatchAfterWaitForReady(Function<V1Pod,V1Pod>... modifiers) {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);
    testSupport.defineResources(createIntrospectorPod());

    try {
      testSupport.runSteps(watcher.waitForReady(createIntrospectorPod(), terminalStep));
      for (Function<V1Pod,V1Pod> modifier : modifiers) {
        watcher.receivedResponse(new Watch.Response<>("MODIFIED", modifier.apply(createIntrospectorPod())));
      }
    } finally {
      stopping.set(true);
    }
  }

  @Test
  public void whenPodDeletedOnFirstRead_runNextStep() {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);

    try {
      testSupport.runSteps(watcher.waitForDelete(createPod(), terminalStep));

      assertThat(terminalStep.wasRun(), is(true));
    } finally {
      stopping.set(true);
    }
  }

  @Test
  public void whenPodNotDeletedOnFirstRead_dontRunNextStep() {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);

    testSupport.defineResources(createPod());
    try {
      testSupport.runSteps(watcher.waitForDelete(createPod(), terminalStep));

      assertThat(terminalStep.wasRun(), is(false));
    } finally {
      stopping.set(true);
    }
  }

  @Test
  public void whenPodDeletedLater_runNextStep() {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher = createWatcher(stopping);

    testSupport.defineResources(createPod());

    try {
      testSupport.runSteps(watcher.waitForDelete(createPod(), terminalStep));
      watcher.receivedResponse(new Watch.Response<>("DELETED", createPod()));

      assertThat(terminalStep.wasRun(), is(true));
    } finally {
      stopping.set(true);
    }
  }

}

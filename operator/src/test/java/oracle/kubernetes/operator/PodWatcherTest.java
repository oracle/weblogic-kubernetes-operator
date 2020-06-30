// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.builders.WatchEvent;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the PodWatcher. */
public class PodWatcherTest extends WatcherTestBase implements WatchListener<V1Pod> {

  private static final BigInteger INITIAL_RESOURCE_VERSION = new BigInteger("234");
  private static final String NS = "ns";
  private static final String NAME = "test";
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final TerminalStep terminalStep = new TerminalStep();

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    addMemento(testSupport.install());
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
  public void whenPodNotReadyLater_dontRunNextStep() {
    sendPodModifiedWatchAfterWaitForReady(this::dontChangePod);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenPodNotReadyLaterAndThenReady_runNextStep() {
    sendPodModifiedWatchAfterWaitForReady(this::dontChangePod, this::markPodReady);

    assertThat(terminalStep.wasRun(), is(true));
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

  private Runnable reportPodIsNowDeleted(PodWatcher watcher) {
    return () -> watcher.receivedResponse(WatchEvent.createDeleteEvent(createPod()).toWatchResponse());
  }

}

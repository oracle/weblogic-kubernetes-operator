// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodCondition;
import io.kubernetes.client.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.hamcrest.Matchers;
import org.junit.Test;

import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

/** This test class verifies the behavior of the PodWatcher. */
public class PodWatcherTest extends WatcherTestBase implements WatchListener<V1Pod> {

  private static final int INITIAL_RESOURCE_VERSION = 234;
  private static final String NS = "ns";
  private Packet packet;
  private V1Pod pod = new V1Pod().metadata(new V1ObjectMeta().name("test"));

  public void setUp() throws Exception {
    super.setUp();
    packet = new Packet();
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
        both(hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)))
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
  protected PodWatcher createWatcher(String ns, AtomicBoolean stopping, int rv) {
    return PodWatcher.create(this, ns, Integer.toString(rv), tuning, this, stopping);
  }

  private PodWatcher createWatcher(AtomicBoolean stopping) {
    return PodWatcher.create(this, NS, Integer.toString(INITIAL_RESOURCE_VERSION), tuning, this, stopping);
  }

  @Test
  public void waitForReady_returnsAStep() {
    AtomicBoolean stopping = new AtomicBoolean(true);
    PodWatcher watcher =
        PodWatcher.create(
            this, "ns", Integer.toString(INITIAL_RESOURCE_VERSION), tuning, this, stopping);

    assertThat(watcher.waitForReady(pod, null), Matchers.instanceOf(Step.class));
  }

  @Test
  public void whenWaitForReadyAppliedToReadyPod_performNextStep() {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher =
        PodWatcher.create(
            this, "ns", Integer.toString(INITIAL_RESOURCE_VERSION), tuning, this, stopping);

    makePodReady(pod);

    ListeningTerminalStep listeningStep = new ListeningTerminalStep(stopping);
    Step step = watcher.waitForReady(pod, listeningStep);
    NextAction nextAction = step.apply(packet);
    nextAction.getNext().apply(packet);

    assertThat(listeningStep.wasPerformed, is(true));
  }

  private void makePodReady(V1Pod pod) {
    List<V1PodCondition> conditions =
        Collections.singletonList(new V1PodCondition().type("Ready").status("True"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));
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

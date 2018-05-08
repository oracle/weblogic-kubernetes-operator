// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.google.common.collect.ImmutableMap;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodCondition;
import io.kubernetes.client.models.V1PodStatus;
import io.kubernetes.client.util.Watch;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.watcher.WatchListener;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.hamcrest.Matchers;
import org.junit.Test;

/** This test class verifies the behavior of the PodWatcher. */
public class PodWatcherTest extends WatcherTestBase implements WatchListener<V1Pod> {

  private static final int INITIAL_RESOURCE_VERSION = 234;
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
  public void initialRequest_specifiesStartingResourceVersionAndStandardLabelSelector()
      throws Exception {
    sendInitialRequest(INITIAL_RESOURCE_VERSION);

    assertThat(
        StubWatchFactory.getRecordedParameters().get(0),
        both(hasEntry("resourceVersion", Integer.toString(INITIAL_RESOURCE_VERSION)))
            .and(
                hasEntry(
                    "labelSelector",
                    asList(
                        LabelConstants.DOMAINUID_LABEL, LabelConstants.CREATEDBYOPERATOR_LABEL))));
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
  protected PodWatcher createWatcher(
      String nameSpace, AtomicBoolean stopping, int initialResourceVersion) {
    return PodWatcher.create(
        this, nameSpace, Integer.toString(initialResourceVersion), this, stopping);
  }

  @Test
  public void whenPodHasNoStatus_reportNotReady() throws Exception {
    assertThat(PodWatcher.isReady(pod), is(false));
  }

  @Test
  public void whenPodPhaseNotRunning_reportNotReady() throws Exception {
    pod.status(new V1PodStatus());

    assertThat(PodWatcher.isReady(pod), is(false));
  }

  @Test
  public void whenPodRunningButNoConditionsDefined_reportNotReady() throws Exception {
    pod.status(new V1PodStatus().phase("Running"));

    assertThat(PodWatcher.isReady(pod), is(false));
  }

  @Test
  public void whenPodRunningButNoReadyConditionsDefined_reportNotReady() throws Exception {
    List<V1PodCondition> conditions = Collections.singletonList(new V1PodCondition().type("Huge"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));

    assertThat(PodWatcher.isReady(pod), is(false));
  }

  @Test
  public void whenPodRunningButReadyConditionIsNotTrue_reportNotReady() throws Exception {
    List<V1PodCondition> conditions =
        Collections.singletonList(new V1PodCondition().type("Ready").status("False"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));

    assertThat(PodWatcher.isReady(pod), is(false));
  }

  @Test
  public void whenPodRunningAndReadyConditionIsTrue_reportReady() throws Exception {
    makePodReady(pod);

    assertThat(PodWatcher.isReady(pod), is(true));
  }

  private void makePodReady(V1Pod pod) {
    List<V1PodCondition> conditions =
        Collections.singletonList(new V1PodCondition().type("Ready").status("True"));
    pod.status(new V1PodStatus().phase("Running").conditions(conditions));
  }

  @Test
  public void whenPodHasNoStatus_reportNotFailed() throws Exception {
    assertThat(PodWatcher.isFailed(pod), is(false));
  }

  @Test
  public void whenPodPhaseNotFailed_reportNotFailed() throws Exception {
    pod.status(new V1PodStatus().phase("Running"));

    assertThat(PodWatcher.isFailed(pod), is(false));
  }

  @Test
  public void whenPodPhaseIsFailed_reportFailed() throws Exception {
    pod.status(new V1PodStatus().phase("Failed"));

    assertThat(PodWatcher.isFailed(pod), is(true));
  }

  @Test
  public void whenPodHasNoDomainUid_returnNull() throws Exception {
    assertThat(PodWatcher.getPodDomainUID(pod), nullValue());
  }

  @Test
  public void whenPodHasDomainUid_returnIt() throws Exception {
    pod.getMetadata().labels(ImmutableMap.of(DOMAINUID_LABEL, "domain1"));

    assertThat(PodWatcher.getPodDomainUID(pod), equalTo("domain1"));
  }

  @Test
  public void whenPodHasNoServerName_returnNull() throws Exception {
    assertThat(PodWatcher.getPodServerName(pod), nullValue());
  }

  @Test
  public void whenPodHasServerName_returnIt() throws Exception {
    pod.getMetadata().labels(ImmutableMap.of(SERVERNAME_LABEL, "myserver"));

    assertThat(PodWatcher.getPodServerName(pod), equalTo("myserver"));
  }

  @Test
  public void waitForReady_returnsAStep() throws Exception {
    AtomicBoolean stopping = new AtomicBoolean(true);
    PodWatcher watcher =
        PodWatcher.create(this, "ns", Integer.toString(INITIAL_RESOURCE_VERSION), this, stopping);

    assertThat(watcher.waitForReady(pod, null), Matchers.instanceOf(Step.class));
  }

  @Test
  public void WhenWaitForReadyAppliedToReadyPod_performNextStep() throws Exception {
    AtomicBoolean stopping = new AtomicBoolean(false);
    PodWatcher watcher =
        PodWatcher.create(this, "ns", Integer.toString(INITIAL_RESOURCE_VERSION), this, stopping);

    makePodReady(pod);

    ListeningTerminalStep listeningStep = new ListeningTerminalStep(stopping);
    Step step = watcher.waitForReady(pod, listeningStep);
    NextAction nextAction = step.apply(packet);
    nextAction.getNext().apply(packet);

    assertThat(listeningStep.wasPerformed, is(true));
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

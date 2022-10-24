// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.meterware.pseudoserver.HttpUserAgentTest;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.STARTING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.UNKNOWN_STATE;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ServerStatusReaderTest extends HttpUserAgentTest {
  private static final String NS = "namespace";
  private final TerminalStep endStep = new TerminalStep();
  private final KubernetesExecFactoryFake execFactory = new KubernetesExecFactoryFake();
  private final ReadServerHealthStepFactoryFake stepFactory = new ReadServerHealthStepFactoryFake();
  private final FiberTestSupport testSupport = new FiberTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainResource domain =
      new DomainResource().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(new DomainSpec());
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(execFactory.install());
    mementos.add(StaticStubSupport.install(ServerStatusReader.class, "stepFactory", stepFactory));
    mementos.add(TuningParametersStub.install());
    mementos.add(ClientFactoryStub.install());

    testSupport.addDomainPresenceInfo(info);
  }

  private V1Pod createPod(String serverName) {
    return new V1Pod().metadata(withNames(new V1ObjectMeta().namespace(NS), serverName));
  }

  private V1Pod createPodWithDeletionTimestamp(String serverName) {
    return new V1Pod().metadata(withNames(new V1ObjectMeta().namespace(NS).deletionTimestamp(OffsetDateTime.now()),
        serverName));
  }

  private V1ObjectMeta withNames(V1ObjectMeta objectMeta, String serverName) {
    return objectMeta
        .name(LegalNames.toPodName(UID, serverName))
        .putLabelsItem(LabelConstants.SERVERNAME_LABEL, serverName);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void whenNoServersPresent_packetContainsEmptyStateAndHealthMaps() {
    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat((Map<?, ?>) packet.get(SERVER_STATE_MAP), Matchers.anEmptyMap());
    assertThat((Map<?, ?>) packet.get(SERVER_HEALTH_MAP), Matchers.anEmptyMap());
  }

  @Test
  void whenServersLackPods_packetContainsEmptyStateAndHealthMaps() {
    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat((Map<?, ?>) packet.get(SERVER_STATE_MAP), Matchers.anEmptyMap());
    assertThat((Map<?, ?>) packet.get(SERVER_HEALTH_MAP), Matchers.anEmptyMap());
  }

  @Test
  void whenServerPodsReturnNodeManagerStatus_recordInStateMap() {
    info.setServerPod("server1", createPod("server1"));
    info.setServerPod("server2", createPod("server2"));
    execFactory.defineResponse("server1", "server1 status");
    execFactory.defineResponse("server2", "server2 status");

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", "server1 status"));
    assertThat(serverStates, hasEntry("server2", "server2 status"));
  }

  @Test
  void createDomainStatusReaderStep_initializesRemainingServersHealthRead_withNumServers() {
    info.setServerPod("server1", createPod("server1"));
    info.setServerPod("server2", createPod("server2"));

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat(
        ((AtomicInteger) packet.get(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ)).get(),
        is(2));
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> getServerStates(Packet packet) {
    return (Map<String, String>) packet.get(SERVER_STATE_MAP);
  }

  @Test
  void whenPodNotReadyAndHasLastKnownState_recordInStateMap() {
    info.setServerPod("server1", createPod("server1"));
    info.updateLastKnownServerStatus("server1", "not ready yet");

    execFactory.defineResponse("server1", "still not ready yet");

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", "still not ready yet"));
  }

  @Test
  void whenWebLogicServerProcessExitedAndPodBeingDeleted_recordInStateMap() {
    info.setServerPod("server1", createPod("server1"));
    info.updateLastKnownServerStatus("server1", "not ready yet");
    info.setServerPodBeingDeleted("server1", true);

    execFactory.defineResponse("server1", "Shutdown", 1);

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", SHUTDOWN_STATE));
  }

  @Test
  void whenWebLogicServerProcessExitedAndPodHasDeletionTimestamp_recordInStateMap() {
    info.setServerPod("server1", createPodWithDeletionTimestamp("server1"));
    info.updateLastKnownServerStatus("server1", "not ready yet");

    execFactory.defineResponse("server1", "Shutdown", 1);

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", SHUTDOWN_STATE));
  }

  @Test
  void whenWebLogicServerStateFileNotFoundAndPodNotBeingDeleted_recordInStateMap() {
    info.setServerPod("server1", createPod("server1"));
    info.updateLastKnownServerStatus("server1", "not ready yet");

    execFactory.defineResponse("server1", "Shutdown", 2);

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", STARTING_STATE));
  }

  @Test
  void whenReadStateFailedWithOutOfMemoryAndPodNotBeingDeleted_recordInStateMap() {
    info.setServerPod("server1", createPod("server1"));
    info.updateLastKnownServerStatus("server1", "not ready yet");

    execFactory.defineResponse("server1", "Shutdown", 137);

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", UNKNOWN_STATE));
  }

  private void setReadyStatus(V1Pod pod) {
    pod.setStatus(
        new V1PodStatus()
            .phase("Running")
            .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
  }

  @Test
  void whenPodIsReady_startHealthStepForIt() {
    info.setServerPod("server1", createPod("server1"));
    setReadyStatus(info.getServerPod("server1"));

    execFactory.defineResponse("server1", "RUNNING");

    testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat(stepFactory.serverNames, contains("server1"));
  }

  static class ReadServerHealthStepFactoryFake implements Function<Step, Step> {
    final List<String> serverNames = new ArrayList<>();

    @Override
    public Step apply(Step next) {
      return new Step() {
        @Override
        public NextAction apply(Packet packet) {
          serverNames.add((String) packet.get(ProcessingConstants.SERVER_NAME));
          return doNext(packet);
        }
      };
    }
  }

  abstract static class ProcessStub extends Process {
    private final String response;
    private final Integer exitCode;

    public ProcessStub(String response, Integer exitCode) {
      this.response = response;
      this.exitCode = exitCode;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public int exitValue() {
      return exitCode;
    }

    @Override
    public void destroy() {
    }
  }
}

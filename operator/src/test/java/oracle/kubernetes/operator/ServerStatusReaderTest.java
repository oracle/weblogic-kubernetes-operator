// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.meterware.pseudoserver.HttpUserAgentTest;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.utils.KubernetesExec;
import oracle.kubernetes.operator.utils.KubernetesExecFactory;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ServerStatusReaderTest extends HttpUserAgentTest {
  private static final String NS = "namespace";
  private static final String UID = "uid";
  private final TerminalStep endStep = new TerminalStep();
  private final KubernetesExecFactoryFake execFactory = new KubernetesExecFactoryFake();
  private final ReadServerHealthStepFactoryFake stepFactory = new ReadServerHealthStepFactoryFake();
  private FiberTestSupport testSupport = new FiberTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private Domain domain =
      new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(new DomainSpec());
  private DomainPresenceInfo info = new DomainPresenceInfo(domain);

  /**
   * Setup test.
   * @throws NoSuchFieldException on no such field
   */
  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.install(ServerStatusReader.class, "EXEC_FACTORY", execFactory));
    mementos.add(StaticStubSupport.install(ServerStatusReader.class, "STEP_FACTORY", stepFactory));
    mementos.add(TuningParametersStub.install());

    testSupport.addDomainPresenceInfo(info);
  }

  private V1Pod createPod(String serverName) {
    return new V1Pod().metadata(withNames(new V1ObjectMeta().namespace(NS), serverName));
  }

  private V1ObjectMeta withNames(V1ObjectMeta objectMeta, String serverName) {
    return objectMeta
        .name(LegalNames.toPodName(UID, serverName))
        .putLabelsItem(LabelConstants.SERVERNAME_LABEL, serverName);
  }

  /**
   * Tear down test.
   * @throws Exception on failure
   */
  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenNoServersPresent_packetContainsEmptyStateAndHealthMaps() {
    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat((Map<?, ?>) packet.get(SERVER_STATE_MAP), Matchers.anEmptyMap());
    assertThat((Map<?, ?>) packet.get(SERVER_HEALTH_MAP), Matchers.anEmptyMap());
  }

  @Test
  public void whenServersLackPods_packetContainsEmptyStateAndHealthMaps() {
    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat((Map<?, ?>) packet.get(SERVER_STATE_MAP), Matchers.anEmptyMap());
    assertThat((Map<?, ?>) packet.get(SERVER_HEALTH_MAP), Matchers.anEmptyMap());
  }

  @Test
  public void whenServerPodsReturnNodeManagerStatus_recordInStateMap() {
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
  public void createDomainStatusReaderStep_initializesRemainingServersHealthRead_withNumServers() {
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
  public void whenPodNotReadyAndHasLastKnownState_recordInStateMap() {
    info.setServerPod("server1", createPod("server1"));
    info.updateLastKnownServerStatus("server1", "not ready yet");

    execFactory.defineResponse("server1", "still not ready yet");

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", "still not ready yet"));
  }

  private void setReadyStatus(V1Pod pod) {
    pod.setStatus(
        new V1PodStatus()
            .phase("Running")
            .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
  }

  @Test
  public void whenPodIsReady_startHealthStepForIt() {
    info.setServerPod("server1", createPod("server1"));
    setReadyStatus(info.getServerPod("server1"));

    execFactory.defineResponse("server1", "RUNNING");

    testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat(stepFactory.serverNames, contains("server1"));
  }

  static class ReadServerHealthStepFactoryFake implements Function<Step, Step> {
    List<String> serverNames = new ArrayList<>();

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

  static class KubernetesExecFactoryFake implements KubernetesExecFactory {
    private Map<String, String> responses = new HashMap<>();

    void defineResponse(String serverName, String response) {
      responses.put(LegalNames.toPodName(UID, serverName), response);
    }

    @Override
    public KubernetesExec create(ApiClient client, V1Pod pod, String containerName) {
      return new KubernetesExec() {
        @Override
        public Process exec(String... command) {
          return createStub(ProcessStub.class, getResponse(pod.getMetadata().getName()));
        }

        private String getResponse(String name) {
          return Optional.ofNullable(responses.get(name)).orElse("** unknown pod **");
        }
      };
    }
  }

  abstract static class ProcessStub extends Process {
    private String response;

    public ProcessStub(String response) {
      this.response = response;
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(response.getBytes(Charset.forName("UTF-8")));
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {
    }
  }
}

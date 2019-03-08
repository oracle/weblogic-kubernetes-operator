// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.pseudoserver.HttpUserAgentTest;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodCondition;
import io.kubernetes.client.models.V1PodStatus;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.utils.KubernetesExec;
import oracle.kubernetes.operator.utils.KubernetesExecFactory;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ServerStatusReaderTest extends HttpUserAgentTest {
  private static final String NS = "namespace";
  private FiberTestSupport testSupport = new FiberTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private final TerminalStep endStep = new TerminalStep();
  private final KubernetesExecFactoryFake execFactory = new KubernetesExecFactoryFake();
  private final ReadServerHealthStepFactoryFake stepFactory = new ReadServerHealthStepFactoryFake();

  private Domain domain =
      new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(new DomainSpec());
  private DomainPresenceInfo info = new DomainPresenceInfo(domain);

  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(StaticStubSupport.install(ServerStatusReader.class, "EXEC_FACTORY", execFactory));
    mementos.add(StaticStubSupport.install(ServerStatusReader.class, "STEP_FACTORY", stepFactory));

    info.getServers().putIfAbsent("server1", createServerKubernetesObjects("server1"));
    info.getServers().putIfAbsent("server2", createServerKubernetesObjects("server2"));
    testSupport.addDomainPresenceInfo(info);
  }

  private ServerKubernetesObjects createServerKubernetesObjects(String serverName) {
    ServerKubernetesObjects sko = new ServerKubernetesObjects();
    sko.getPod().set(new V1Pod().metadata(new V1ObjectMeta().namespace(NS).name(serverName)));
    return sko;
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenNoServersPresent_packetContainsEmptyStateAndHealthMaps() {
    info.getServers().clear();

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat((Map<?, ?>) packet.get(SERVER_STATE_MAP), Matchers.anEmptyMap());
    assertThat((Map<?, ?>) packet.get(SERVER_HEALTH_MAP), Matchers.anEmptyMap());
  }

  @Test
  public void whenServersLackPods_packetContainsEmptyStateAndHealthMaps() {
    for (ServerKubernetesObjects sko : info.getServers().values()) sko.getPod().set(null);

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    assertThat((Map<?, ?>) packet.get(SERVER_STATE_MAP), Matchers.anEmptyMap());
    assertThat((Map<?, ?>) packet.get(SERVER_HEALTH_MAP), Matchers.anEmptyMap());
  }

  @Test
  public void whenServerPodsReturnNodeManagerStatus_recordInStateMap() {
    execFactory.defineResponse("server1", "server1 status");
    execFactory.defineResponse("server2", "server2 status");

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", "server1 status"));
    assertThat(serverStates, hasEntry("server2", "server2 status"));
  }

  @SuppressWarnings("unchecked")
  private Map<String, String> getServerStates(Packet packet) {
    return (Map<String, String>) packet.get(SERVER_STATE_MAP);
  }

  @Test
  public void whenPodNotReadyAndHasLastKnownState_recordInStateMap() {
    info.getServers().get("server1").getLastKnownStatus().set("not ready yet");

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", "not ready yet"));
  }

  @Test
  public void whenPodIsReady_recordInStateMap() {
    setReadyStatus(info.getServers().get("server1").getPod().get());

    Packet packet =
        testSupport.runSteps(ServerStatusReader.createDomainStatusReaderStep(info, 0, endStep));

    Map<String, String> serverStates = getServerStates(packet);
    assertThat(serverStates, hasEntry("server1", WebLogicConstants.RUNNING_STATE));
  }

  private void setReadyStatus(V1Pod pod) {
    pod.setStatus(
        new V1PodStatus()
            .phase("Running")
            .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
  }

  @Test
  public void whenPodIsReady_startHealthStepForIt() {
    setReadyStatus(info.getServers().get("server1").getPod().get());

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

    void defineResponse(String podName, String response) {
      responses.put(podName, response);
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
    public void destroy() {}
  }
}

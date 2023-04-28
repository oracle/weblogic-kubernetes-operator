// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.makeright;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.ClientFactoryStub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.PodHelperTestBase;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.hamcrest.Description;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVICE_TYPE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.SERVICE;
import static oracle.kubernetes.operator.makeright.DomainUpPlanTest.ContainerPortMatcher.hasContainerPort;
import static oracle.kubernetes.operator.makeright.DomainUpPlanTest.NoContainerPortMatcher.hasNoContainerPort;
import static oracle.kubernetes.operator.makeright.DomainUpPlanTest.NoServicePortMatcher.hasNoServicePort;
import static oracle.kubernetes.operator.makeright.DomainUpPlanTest.ServicePortMatcher.hasServicePort;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;

class DomainUpPlanTest {
  public static final String ADMIN_SERVER_NAME = UID + "-admin";
  public static final String EXTERNAL_SERVICE_NAME = UID + "-admin-ext";
  public static final String WEBLOGIC_DOMAIN_NAME = "domain";
  public static final String WEBLOGIC_SERVER_NAME = "admin";
  public static final String DEFAULT_PORT_NAME = "default";
  public static final String NAP_NAME_1 = "nap1";
  public static final String NAP_NAME_2 = "nap2";
  public static final int NAP_PORT_1 = 8054;
  public static final int NAP_PORT_2 = 8064;
  public static final int DEFAULT_PORT = 7001;

  private final TerminalStep adminStep = new TerminalStep();
  private final TerminalStep managedServersStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain)
                    .withWebLogicCredentialsSecret("secret");
  private final DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo(domain);

  private DomainPresenceStep getDomainPresenceStep() {
    return DomainPresenceStep.createDomainPresenceStep(adminStep, managedServersStep);
  }

  @BeforeEach
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(ClientFactoryStub.install());
    mementos.add(testSupport.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(TuningParametersStub.install());

    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(domainPresenceInfo);

    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        new PodHelperTestBase.PassthroughPodAwaiterStepFactory());
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void whenStartPolicyNull_runAdminStepOnly() {
    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(true));
    assertThat(managedServersStep.wasRun(), is(false));
  }

  @Test
  void whenNotShuttingDown_runAdminStepOnly() {
    configurator.setShuttingDown(false);

    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(true));
    assertThat(managedServersStep.wasRun(), is(false));
  }

  @Test
  void whenShuttingDown_runManagedServersStepOnly() {
    configurator.setShuttingDown(true);

    testSupport.runSteps(getDomainPresenceStep());

    assertThat(adminStep.wasRun(), is(false));
    assertThat(managedServersStep.wasRun(), is(true));
  }

  @Test
  void whenAdminPodCreated_hasListenPort() throws NoSuchFieldException {
    mementos.add(UnitTestHash.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(WEBLOGIC_DOMAIN_NAME);
    configSupport.addWlsServer(WEBLOGIC_SERVER_NAME, NAP_PORT_1);
    configSupport.setAdminServerName(WEBLOGIC_SERVER_NAME);
    Step plan =
        DomainProcessorImpl.bringAdminServerUp(new NullPodWaiter());
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.runSteps(plan);

    List<V1Pod> resources = testSupport.getResources(POD);
    assertThat(resources.get(0), hasContainerPort(NAP_PORT_1));
  }

  @Test
  void whenAdminPodWithNAPCreated_hasExternalListenPortAndService() throws NoSuchFieldException {
    mementos.add(UnitTestHash.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(WEBLOGIC_DOMAIN_NAME);
    configSupport.addWlsServer(WEBLOGIC_SERVER_NAME, DEFAULT_PORT);
    configSupport.setAdminServerName(WEBLOGIC_SERVER_NAME);
    configSupport.getAdminServer()
        .addNetworkAccessPoint(new NetworkAccessPoint(NAP_NAME_1, "t3", NAP_PORT_1, 8085));
    domain.getSpec().getOrCreateAdminServer().withChannel(NAP_NAME_1, NAP_PORT_1);
    Step plan =
        DomainProcessorImpl.bringAdminServerUp(new NullPodWaiter());
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.runSteps(plan);

    assertThat(testSupport.<V1Pod>getResources(POD).get(0), hasContainerPort(NAP_PORT_1));
    assertThat(testSupport.<V1Service>getResources(SERVICE).get(0), hasServicePort(NAP_PORT_1));
  }

  @Test
  void whenAdminPodNapsRemoved_listenPortAndServiceRemoved() throws NoSuchFieldException {
    mementos.add(UnitTestHash.install());
    establishExistingPodsAndServices();

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(WEBLOGIC_DOMAIN_NAME);
    configSupport.addWlsServer(WEBLOGIC_SERVER_NAME, DEFAULT_PORT);
    configSupport.setAdminServerName(WEBLOGIC_SERVER_NAME);
    domain.getSpec().getOrCreateAdminServer();

    Step plan =
        DomainProcessorImpl.bringAdminServerUp(new NullPodWaiter());
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.runSteps(plan);

    assertThat(testSupport.<V1Pod>getResources(POD).get(0), hasNoContainerPort(NAP_PORT_1));
    assertThat(getServiceByName(testSupport.<V1Service>getResources(SERVICE), EXTERNAL_SERVICE_NAME), empty());
  }

  @Test
  void whenAdminPodNapRemoved_listenPortAndServicePortRemoved() throws NoSuchFieldException {
    mementos.add(UnitTestHash.install());
    establishExistingPodsAndServices();

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(WEBLOGIC_DOMAIN_NAME);
    configSupport.addWlsServer(WEBLOGIC_SERVER_NAME, DEFAULT_PORT);
    configSupport.setAdminServerName(WEBLOGIC_SERVER_NAME);
    configSupport.getAdminServer()
        .addNetworkAccessPoint(new NetworkAccessPoint(NAP_NAME_1, "t3", NAP_PORT_1, 8055));
    domain.getSpec().getOrCreateAdminServer().withChannel(NAP_NAME_1, NAP_PORT_1);

    Step plan =
        DomainProcessorImpl.bringAdminServerUp(new NullPodWaiter());
    testSupport.addToPacket(DOMAIN_TOPOLOGY, configSupport.createDomainConfig());
    testSupport.runSteps(plan);

    assertThat(testSupport.<V1Pod>getResources(POD).get(0), hasContainerPort(NAP_PORT_1));
    assertThat(testSupport.<V1Service>getResources(SERVICE).get(0), hasServicePort(NAP_PORT_1));
    assertThat(testSupport.<V1Pod>getResources(POD).get(0), hasNoContainerPort(NAP_PORT_2));
    assertThat(testSupport.<V1Service>getResources(SERVICE).get(0), hasNoServicePort(NAP_PORT_2));
  }

  private void establishExistingPodsAndServices() {
    Map<String, String> labels = getLabels();
    V1Pod pod = new V1Pod().metadata(new V1ObjectMeta().name(ADMIN_SERVER_NAME).namespace(NS).labels(labels)).spec(
        new V1PodSpec().addContainersItem(new V1Container().name("weblogic-server")
                .addPortsItem(new V1ContainerPort().name(NAP_NAME_1).containerPort(NAP_PORT_1))
                .addPortsItem(new V1ContainerPort().name(NAP_NAME_2).containerPort(NAP_PORT_2))
                .addPortsItem(new V1ContainerPort().name(DEFAULT_PORT_NAME).containerPort(DEFAULT_PORT)))
            .hostname(ADMIN_SERVER_NAME).nodeName("Node1"));
    Map<String, String> labels2 = getLabels();
    labels2.put(SERVICE_TYPE_LABEL, "EXTERNAL");
    V1Service extService = new V1Service().metadata(new V1ObjectMeta().name(EXTERNAL_SERVICE_NAME)
        .namespace(NS).labels(labels2)).spec(new V1ServiceSpec().type("NodePort")
        .addPortsItem(new V1ServicePort().name(NAP_NAME_1).port(NAP_PORT_1).nodePort(NAP_PORT_1))
        .addPortsItem(new V1ServicePort().name(NAP_NAME_2).port(NAP_PORT_2).nodePort(NAP_PORT_2)));

    testSupport.defineResources(pod, extService);
    domainPresenceInfo.setServerPod(WEBLOGIC_SERVER_NAME, pod);
    domainPresenceInfo.setExternalService(WEBLOGIC_SERVER_NAME, extService);
  }

  private List<V1Service> getServiceByName(List<V1Service> resources, String name) {
    return resources.stream().filter(s -> s.getMetadata().getName().equals(name)).collect(Collectors.toList());
  }

  @NotNull
  private Map<String, String> getLabels() {
    Map<String, String> labels = new HashMap<>();
    labels.put(DOMAINNAME_LABEL, WEBLOGIC_DOMAIN_NAME);
    labels.put(DOMAINUID_LABEL, UID);
    labels.put(CREATEDBYOPERATOR_LABEL, "true");
    labels.put(SERVERNAME_LABEL, WEBLOGIC_SERVER_NAME);
    return labels;
  }

  static class NullPodWaiter implements PodAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return null;
    }

    @Override
    public Step waitForReady(String podName, Step next) {
      return null;
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return null;
    }

    @Override
    public Step waitForServerShutdown(String serverName, DomainResource domain, Step next) {
      return null;
    }
  }

  @SuppressWarnings("unused")
  static class ContainerPortMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Pod> {
    private final int expectedPort;

    private ContainerPortMatcher(int expectedPort) {
      this.expectedPort = expectedPort;
    }

    static ContainerPortMatcher hasContainerPort(int expectedPort) {
      return new ContainerPortMatcher(expectedPort);
    }

    @Override
    protected boolean matchesSafely(V1Pod item, Description mismatchDescription) {
      if (getContainerPorts(item).stream().anyMatch(p -> p.getContainerPort() == expectedPort)) {
        return true;
      }

      mismatchDescription.appendText("No matching port found in pod ").appendText(item.toString());
      return false;
    }

    private List<V1ContainerPort> getContainerPorts(V1Pod item) {
      return Optional.ofNullable(item.getSpec())
            .map(V1PodSpec::getContainers)
            .map(c -> c.get(0))
            .map(V1Container::getPorts)
            .orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("Pod with container port ").appendValue(expectedPort);
    }
  }

  @SuppressWarnings("unused")
  static class NoContainerPortMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Pod> {
    private final int unexpectedPort;

    private NoContainerPortMatcher(int unexpectedPort) {
      this.unexpectedPort = unexpectedPort;
    }

    static NoContainerPortMatcher hasNoContainerPort(int unexpectedPort) {
      return new NoContainerPortMatcher(unexpectedPort);
    }

    @Override
    protected boolean matchesSafely(V1Pod item, Description mismatchDescription) {
      if (getContainerPorts(item).stream().noneMatch(p -> p.getContainerPort() == unexpectedPort)) {
        return true;
      }

      mismatchDescription.appendText("Unexpected port found in pod ").appendText(item.toString());
      return false;
    }

    private List<V1ContainerPort> getContainerPorts(V1Pod item) {
      return Optional.ofNullable(item.getSpec())
          .map(V1PodSpec::getContainers)
          .map(c -> c.get(0))
          .map(V1Container::getPorts)
          .orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("Pod with unexpected container port ").appendValue(unexpectedPort);
    }
  }

  @SuppressWarnings("unused")
  static class ServicePortMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Service> {
    private final int expectedPort;

    private ServicePortMatcher(int expectedPort) {
      this.expectedPort = expectedPort;
    }

    static ServicePortMatcher hasServicePort(int expectedPort) {
      return new ServicePortMatcher(expectedPort);
    }

    @Override
    protected boolean matchesSafely(V1Service item, Description mismatchDescription) {
      if (getServicePorts(item).stream().anyMatch(p -> p.getPort() == expectedPort)) {
        return true;
      }

      mismatchDescription.appendText("No matching port found in pod ").appendText(item.toString());
      return false;
    }

    private List<V1ServicePort> getServicePorts(V1Service item) {
      return Optional.ofNullable(item.getSpec())
          .map(V1ServiceSpec::getPorts)
          .orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("Pod with container port ").appendValue(expectedPort);
    }
  }

  @SuppressWarnings("unused")
  static class NoServicePortMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.openapi.models.V1Service> {
    private final int unexpectedPort;

    private NoServicePortMatcher(int unexpectedPort) {
      this.unexpectedPort = unexpectedPort;
    }

    static NoServicePortMatcher hasNoServicePort(int unexpectedPort) {
      return new NoServicePortMatcher(unexpectedPort);
    }

    @Override
    protected boolean matchesSafely(V1Service item, Description mismatchDescription) {
      if (getServicePorts(item).stream().noneMatch(p -> p.getPort() == unexpectedPort)) {
        return true;
      }

      mismatchDescription.appendText("Unexpected port found in pod ").appendText(item.toString());
      return false;
    }

    private List<V1ServicePort> getServicePorts(V1Service item) {
      return Optional.ofNullable(item.getSpec())
          .map(V1ServiceSpec::getPorts)
          .orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("Pod with unexpected container port ").appendValue(unexpectedPort);
    }
  }

  @SuppressWarnings("unused")
  static class StepChainMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<oracle.kubernetes.operator.work.Step> {
    private final String[] expectedSteps;

    private StepChainMatcher(String[] expectedSteps) {
      this.expectedSteps = expectedSteps;
    }

    static StepChainMatcher hasChainWithStep(String expectedStep) {
      return hasChainWithStepsInOrder(expectedStep);
    }

    static StepChainMatcher hasChainWithStepsInOrder(String... expectedSteps) {
      return new StepChainMatcher(expectedSteps);
    }

    @Override
    protected boolean matchesSafely(Step item, Description mismatchDescription) {
      List<String> steps = chainStepNames(item);

      int index = 0;
      for (String step : expectedSteps) {
        int nextIndex = steps.indexOf(step);
        if (nextIndex < index) {
          mismatchDescription.appendValueList("found steps: ", ", ", ".", steps);
          return false;
        }
        index = nextIndex;
      }

      return true;
    }

    private List<String> chainStepNames(Step step) {
      List<String> stepNames = new ArrayList<>();
      while (step != null) {
        stepNames.add(step.getClass().getSimpleName());
        step = step.getNext();
      }
      return stepNames;
    }

    @Override
    public void describeTo(Description description) {
      if (expectedSteps.length == 1) {
        description.appendText("expected step ").appendValue(expectedSteps[0]);
      } else {
        description.appendValueList(
            "expected steps in order to include: ", ",", ".", expectedSteps);
      }
    }
  }

}

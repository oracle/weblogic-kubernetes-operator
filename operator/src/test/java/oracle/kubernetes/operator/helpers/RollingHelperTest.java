// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.PodHelper.ManagedPodStepContext;
import oracle.kubernetes.operator.helpers.PodHelperTestBase.PassthroughPodAwaiterStepFactory;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_REPLACED;
import static oracle.kubernetes.utils.LogMatcher.containsInOrder;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class RollingHelperTest {

  private static final String ADMIN_SERVER = "ADMIN_SERVER";
  private static final Integer ADMIN_PORT = 7001;
  private static final String CLUSTER_NAME = "test-cluster";
  private static final String CREDENTIALS_SECRET_NAME = "webLogicCredentialsSecretName";
  private static final boolean INCLUDE_SERVER_OUT_IN_POD_LOG = true;
  private static final String LATEST_IMAGE = "image:latest";
  private static final int LISTEN_PORT = 8001;
  private static final String NS = "namespace";
  private static final String SERVER1_NAME = "ess_server1";
  private static final String SERVER2_NAME = "ess_server2";
  private static final String SERVER10_NAME = "ess_server10";
  private static final List<String> SERVER_NAMES = Arrays.asList(SERVER10_NAME, SERVER1_NAME, SERVER2_NAME);
  private static final String DOMAIN_NAME = "domain1";
  private static final String UID = "uid1";

  private final Domain domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  private final TerminalStep terminalStep = new TerminalStep();
  private final Map<String, StepAndPacket> rolling = new HashMap<>();

  protected final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  protected final List<Memento> mementos = new ArrayList<>();
  protected final List<LogRecord> logRecords = new ArrayList<>();

  private WlsDomainConfig domainTopology;

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport.addWlsServer(ADMIN_SERVER, ADMIN_PORT);
    configSupport.setAdminServerName(ADMIN_SERVER);
    SERVER_NAMES.forEach(s -> configSupport.addWlsServer(s, LISTEN_PORT));
    configSupport.addWlsCluster(CLUSTER_NAME, SERVER10_NAME, SERVER1_NAME, SERVER2_NAME);

    testSupport.defineResources(domain);
    domainTopology = configSupport.createDomainConfig();
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        new PassthroughPodAwaiterStepFactory());

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  private Domain createDomain() {
    return new Domain().withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME)).withSpec(createDomainSpec());
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec()
        .withDomainUid(UID)
        .withWebLogicCredentialsSecret(new V1SecretReference().name(CREDENTIALS_SECRET_NAME))
        .withIncludeServerOutInPodLog(INCLUDE_SERVER_OUT_IN_POD_LOG)
        .withImage(LATEST_IMAGE);
  }

  private V1Pod createPod(Packet packet) {
    return new PodHelper.ManagedPodStepContext(null, packet).getPodModel();
  }

  private V1Pod createPodModel(String serverName) {
    testSupport
        .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainTopology)
        .addToPacket(SERVER_SCAN, domainTopology.getServerConfig(serverName))
        .addDomainPresenceInfo(domainPresenceInfo);
    return createPod(testSupport.getPacket());
  }

  private DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    return new DomainPresenceInfo(domain);
  }

  private String[] getMessageKeys() {
    return new String[] {
        MANAGED_POD_REPLACED
    };
  }

  private Step.StepAndPacket createRollingStepAndPacket(String serverName) {
    Packet packet = testSupport.getPacket().copy();
    packet.put(SERVER_SCAN, domainTopology.getServerConfig(serverName));
    return new Step.StepAndPacket(DomainStatusUpdater.createProgressingStep(
        DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON,
        false,
        new ManagedPodStepContext(terminalStep, packet).createCyclePodStep(
            testSupport.getResourceWithName(
                KubernetesTestSupport.POD,
                LegalNames.toPodName(UID, serverName)), null)), packet);
  }

  private void initializeExistingPods() {
    SERVER_NAMES.forEach(this::initializeExistingPod);
  }

  private void initializeExistingPod(String serverName) {
    V1Pod pod = createPodModel(serverName);

    testSupport.defineResources(pod);
    pod.setStatus(new V1PodStatus().phase("Running").addConditionsItem(
        new V1PodCondition().type("Ready").status("True")));
    domainPresenceInfo.setServerPod(serverName, pod);
  }

  @Test
  public void verifyThatManagedServerPodsAreReplacedInOrder() {
    initializeExistingPods();
    testSupport.addToPacket(SERVERS_TO_ROLL, rolling);
    SERVER_NAMES.forEach(s -> rolling.put(s, createRollingStepAndPacket(s)));

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(logRecords, containsInOrder(
        containsInfo(MANAGED_POD_REPLACED, SERVER1_NAME),
        containsInfo(MANAGED_POD_REPLACED, SERVER2_NAME),
        containsInfo(MANAGED_POD_REPLACED, SERVER10_NAME)
    ));
  }

  @Test
  public void verifyThatWhenRollingIsEmpty_NoManagedServerPodsAreReplaced() {
    initializeExistingPods();
    testSupport.addToPacket(SERVERS_TO_ROLL, rolling);

    testSupport.runSteps(RollingHelper.rollServers(rolling, terminalStep));

    assertThat(logRecords, empty());
  }

}

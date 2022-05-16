// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ServerStartPolicy;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.ClusterSpec;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER;
import static oracle.kubernetes.common.logging.MessageKeys.MONITORING_EXPORTER_CONFLICT_SERVER;
import static oracle.kubernetes.common.logging.MessageKeys.NO_CLUSTER_IN_DOMAIN;
import static oracle.kubernetes.common.logging.MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainFailureReason.TOPOLOGY_MISMATCH;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventConstants.TOPOLOGY_MISMATCH_ERROR;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class TopologyValidationStepTest {
  private static final String ADMIN_SERVER = "admin-server";
  private static final String MANAGED_SERVER1 = "managed-server1";
  private static final String DYNAMIC_CLUSTER_NAME = "dyn-cluster-1";
  private static final String SERVER_TEMPLATE_NAME = "server-template";
  private static final String DOMAIN_NAME = "domain";
  private static final int ADMIN_SERVER_PORT_NUM = 7001;
  private static final int MANAGED_SERVER1_PORT_NUM = 8001;
  private static final int SERVER_TEMPLATE_PORT_NUM = 9001;

  private final Domain domain = DomainProcessorTestSetup.createTestDomain();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final TerminalStep terminalStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private TestUtils.ConsoleHandlerMemento consoleControl;
  private final WlsDomainConfig domainConfig =
      new WlsDomainConfigSupport(DOMAIN_NAME)
          .withAdminServerName(ADMIN_SERVER)
          .withWlsServer(ADMIN_SERVER, ADMIN_SERVER_PORT_NUM)
          .withWlsServer(MANAGED_SERVER1, MANAGED_SERVER1_PORT_NUM)
          .withDynamicWlsCluster(DYNAMIC_CLUSTER_NAME, SERVER_TEMPLATE_NAME, SERVER_TEMPLATE_PORT_NUM)
          .createDomainConfig();

  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();

  @BeforeEach
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords,
        NO_CLUSTER_IN_DOMAIN, NO_MANAGED_SERVER_IN_DOMAIN,
        MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER, MONITORING_EXPORTER_CONFLICT_SERVER);
    mementos.add(consoleControl);
    mementos.add(testSupport.install());
    mementos.add(SystemClockTestSupport.installClock());

    testSupport.defineResources(domain);
    testSupport.addDomainPresenceInfo(info);
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "namespaceEventK8SObjects", nsEventObjects));
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }


  @Test
  void whenClusterDoesNotExistInDomain_logWarning() {
    domain.getSpec().withCluster(createCluster("no-such-cluster"));

    runTopologyValidationStep();

    assertTopologyMismatchReported(NO_CLUSTER_IN_DOMAIN, "no-such-cluster");
  }

  private void runTopologyValidationStep() {
    testSupport.runSteps(DomainValidationSteps.createValidateDomainTopologyStep(terminalStep));
  }

  private void assertTopologyMismatchReported(String messageKey, Object... parameters) {
    final String message = getFormattedMessage(messageKey, parameters);

    assertThat(domain, hasCondition(FAILED).withReason(TOPOLOGY_MISMATCH).withMessageContaining(message));
    assertThat(logRecords, containsWarning(messageKey).withParams(parameters));
    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT).withMessageContaining(TOPOLOGY_MISMATCH_ERROR, message));
  }

  @Test
  void removeOldTopologyFailures() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(TOPOLOGY_MISMATCH));

    runTopologyValidationStep();

    assertThat(domain, not(hasCondition(FAILED).withReason(TOPOLOGY_MISMATCH)));
  }

  @Test
  void preserveTopologyFailuresThatStillExist() {
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    final OffsetDateTime initialTime = SystemClock.now();
    final String message = getFormattedMessage(NO_CLUSTER_IN_DOMAIN, "no-such-cluster");
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(TOPOLOGY_MISMATCH).withMessage(message));

    SystemClockTestSupport.increment();
    domain.getSpec().withCluster(createCluster("no-such-cluster"));
    runTopologyValidationStep();

    assertThat(domain, hasCondition(FAILED).withMessageContaining(message).atTime(initialTime));
  }

  @Test
  void whenServerDoesNotExistInDomain_logWarning() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));

    runTopologyValidationStep();

    assertTopologyMismatchReported(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server");
  }

  private DomainConfigurator configureDomain(Domain domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }

  @Test
  void whenMonitoringExporterPortConflictsWithAdminServerPort_logWarningAndGenerateEvent() {
    configureDomain(domain).withMonitoringExporterConfiguration("queries:\n").withMonitoringExporterPort(7001);

    runTopologyValidationStep();

    assertTopologyMismatchReported(MONITORING_EXPORTER_CONFLICT_SERVER, 7001, ADMIN_SERVER, ADMIN_SERVER_PORT_NUM);
  }

  @Test
  void whenMonitoringExporterPortConflictsWithManagedServerPort_logWarningAndGenerateEvent() {
    configureDomain(domain).withMonitoringExporterConfiguration("queries:\n").withMonitoringExporterPort(8001);

    runTopologyValidationStep();

    assertTopologyMismatchReported(MONITORING_EXPORTER_CONFLICT_SERVER,
                       8001, MANAGED_SERVER1, MANAGED_SERVER1_PORT_NUM);
  }

  @Test
  void portNumbersInMonitoringExportConflictServerMessage_areFormattedWithoutCommas() {
    assertThat(
          getFormattedMessage(MONITORING_EXPORTER_CONFLICT_SERVER, 8001, MANAGED_SERVER1, 8001),
          stringContainsInOrder("8001", MANAGED_SERVER1, "8001"));
  }

  @Test
  void whenMonitoringExporterPortConflictsWithClusterServerTemplatePort_logWarningAndGenerateEvent() {
    configureDomain(domain).withMonitoringExporterConfiguration("queries:\n").withMonitoringExporterPort(9001);

    runTopologyValidationStep();

    assertTopologyMismatchReported(MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER,
                       9001, DYNAMIC_CLUSTER_NAME, SERVER_TEMPLATE_PORT_NUM);
  }

  @Test
  void portNumbersInMonitoringExportConflictDynamicClusterMessage_areFormattedWithoutCommas() {
    assertThat(
          getFormattedMessage(MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER, 9001, DYNAMIC_CLUSTER_NAME, 9001),
          stringContainsInOrder("9001", DYNAMIC_CLUSTER_NAME, "9001"));
  }

  @Test
  void whenServerDoesNotExistInDomain_logWarningAndCreateEvent() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));

    runTopologyValidationStep();

    assertTopologyMismatchReported(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server");
  }

  @Test
  void whenClusterDoesNotExistInDomain_logWarningAndCreateEvent() {
    domain.getSpec().withCluster(createCluster("no-such-cluster"));

    runTopologyValidationStep();

    assertTopologyMismatchReported(NO_CLUSTER_IN_DOMAIN, "no-such-cluster");
  }

  @Test
  void whenBothServerAndClusterDoNotExistInDomain_createEventWithBothWarnings() {
    consoleControl.ignoreMessage(NO_MANAGED_SERVER_IN_DOMAIN);
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("no-such-server"));
    domain.getSpec().withCluster(createCluster("no-such-cluster"));

    runTopologyValidationStep();

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT)
                .withMessageContaining(TOPOLOGY_MISMATCH_ERROR,
                      getFormattedMessage(NO_CLUSTER_IN_DOMAIN, "no-such-cluster"),
                      getFormattedMessage(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server")));
  }

  @Test
  void whenIsExplicitRecheck_doNotCreateEvent() {
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    setExplicitRecheck();
    domain.getSpec().withCluster(createCluster("no-such-cluster"));

    runTopologyValidationStep();

    assertThat(testSupport, not(hasEvent(DOMAIN_FAILED_EVENT)));
  }

  @SuppressWarnings("SameParameterValue")
  private ClusterSpec createCluster(String clusterName) {
    ClusterSpec cluster = new ClusterSpec();
    cluster.setClusterName(clusterName);
    cluster.setReplicas(1);
    cluster.setServerStartPolicy(ServerStartPolicy.IF_NEEDED);
    return cluster;
  }

  private String getFormattedMessage(String msgId, Object... params) {
    LoggingFacade logger = LoggingFactory.getLogger("Operator", "Operator");
    return logger.formatMessage(msgId, params);
  }

  private void setExplicitRecheck() {
    testSupport.addToPacket(MAKE_RIGHT_DOMAIN_OPERATION,
        Stub.createStub(ExplicitRecheckMakeRightDomainOperationStub.class));
  }

  abstract static class ExplicitRecheckMakeRightDomainOperationStub implements
        MakeRightDomainOperation {

    @Override
    public boolean isExplicitRecheck() {
      return true;
    }
  }
}

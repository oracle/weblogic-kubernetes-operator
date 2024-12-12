// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.ClusterStatus;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainCondition;
import oracle.kubernetes.weblogic.domain.model.DomainConditionFailureInfo;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_CLUSTER_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_SERVER_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER;
import static oracle.kubernetes.common.logging.MessageKeys.MONITORING_EXPORTER_CONFLICT_SERVER;
import static oracle.kubernetes.common.logging.MessageKeys.NO_AVAILABLE_PORT_TO_USE_FOR_REST;
import static oracle.kubernetes.common.logging.MessageKeys.NO_CLUSTER_IN_DOMAIN;
import static oracle.kubernetes.common.logging.MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN;
import static oracle.kubernetes.common.logging.MessageKeys.REPLICAS_TOO_HIGH_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.TOO_MANY_REPLICAS_FAILURE;
import static oracle.kubernetes.common.logging.MessageKeys.TOPOLOGY_MISMATCH_EVENT_ERROR;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ServerStartPolicy.IF_NEEDED;
import static oracle.kubernetes.operator.helpers.LegalNames.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
import static oracle.kubernetes.operator.helpers.LegalNames.EXTERNAL_SERVICE_NAME_SUFFIX_PARAM;
import static oracle.kubernetes.operator.helpers.LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH;
import static oracle.kubernetes.operator.helpers.WlsConfigValidator.CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM;
import static oracle.kubernetes.operator.utils.WlsDomainConfigSupport.DEFAULT_LISTEN_PORT;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionMatcher.hasCondition;
import static oracle.kubernetes.weblogic.domain.model.DomainConditionType.FAILED;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.REPLICAS_TOO_HIGH;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.TOPOLOGY_MISMATCH;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class TopologyValidationStepTest {
  private static final String ADMIN_SERVER = "admin-server";
  private static final String MANAGED_SERVER1 = "managed-server1";
  private static final String DYNAMIC_CLUSTER_NAME = "dyn-cluster-1";
  private static final String SERVER_TEMPLATE_NAME = "server-template";
  private static final String CLUSTER_MANAGED_SERVER_NAME = "ms";
  private static final String DOMAIN_NAME = "domain";
  private static final int LEGAL_NAME_INFIX_SIZE = 1;
  private static final int SERVER_SERVICE_MAX_CHARS = LEGAL_DNS_LABEL_NAME_MAX_LENGTH - LEGAL_NAME_INFIX_SIZE;
  private static final int PADDED_SERVER_NUM = "999".length();   // room to allow for server numbers up to 999
  private static final int CLUSTER_NAME_INFIX_SIZE = "-cluster-".length();
  private static final int CLUSTER_SERVICE_MAX_CHARS = LEGAL_DNS_LABEL_NAME_MAX_LENGTH - CLUSTER_NAME_INFIX_SIZE;
  private static final String LISTEN_ADDRESS = "myhost";
  private static final int ADMIN_SERVER_PORT_NUM = 8001;
  private static final int SERVER_TEMPLATE_PORT_NUM = 9001;
  private static final int PLAIN_PORT_NUM = 7002;
  private static final int SSL_PORT_NUM = 7003;
  private static final int SERVER_ADMIN_PORT_NUM = 7004;
  private static final int NAP_ADMIN_PORT_NUM = 7005;
  private static final int NAP_PORT_NUM = 7006;
  private static final int EXPORTER_PORT_NUM = 8800;
  private static final String CLUSTER1 = "cluster1";
  private static final String CLUSTER2 = "cluster2";

  private final DomainResource domain = DomainProcessorTestSetup.createTestDomain();
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
          .withWlsCluster(CLUSTER1)  // test will add servers to this cluster
          .withWlsCluster(CLUSTER2, CLUSTER_MANAGED_SERVER_NAME)
          .withDynamicWlsCluster(DYNAMIC_CLUSTER_NAME, SERVER_TEMPLATE_NAME, SERVER_TEMPLATE_PORT_NUM)
          .createDomainConfig();

  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private final Map<String, KubernetesEventObjects> nsEventObjects = new ConcurrentHashMap<>();

  // a name that will be created by a *_NAME scenario. See TopologyScenario
  private String createdName;

  @BeforeEach
  public void setUp() throws Exception {
    consoleControl = TestUtils.silenceOperatorLogger().collectLogMessages(logRecords,
        ILLEGAL_SERVER_SERVICE_NAME_LENGTH, ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH, ILLEGAL_CLUSTER_SERVICE_NAME_LENGTH,
        NO_AVAILABLE_PORT_TO_USE_FOR_REST, NO_CLUSTER_IN_DOMAIN, NO_MANAGED_SERVER_IN_DOMAIN,
        MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER, MONITORING_EXPORTER_CONFLICT_SERVER);
    mementos.add(consoleControl);
    mementos.add(testSupport.install());
    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(TuningParametersStub.install());

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


  enum ServerPort {
    PLAIN_PORT {
      @Override
      void setPort(WlsServerConfig server) {
        server.setListenPort(PLAIN_PORT_NUM);
      }
    },
    SSL_PORT {
      @Override
      void setPort(WlsServerConfig server) {
        server.setSslListenPort(SSL_PORT_NUM);
      }
    },
    ADMIN_PORT {
      @Override
      void setPort(WlsServerConfig server) {
        server.setAdminPort(SERVER_ADMIN_PORT_NUM);
      }
    },
    NAP_FOR_ADMIN {
      @Override
      void setPort(WlsServerConfig server) {
        server.addNetworkAccessPoint("admin-nap", "admin", NAP_ADMIN_PORT_NUM);
      }
    },
    NAP_FOR_T3 {
      @Override
      void setPort(WlsServerConfig server) {
        server.addNetworkAccessPoint("t3-nap", "t3", NAP_PORT_NUM);
      }
    };

    abstract void setPort(WlsServerConfig server);
  }

  enum TopologyCase {
    ADMIN_SERVER_NAME {
      @Override
      void setName(WlsDomainConfig domainConfig, String name, Integer clusterSize) {
        super.setName(domainConfig, name, clusterSize);
        domainConfig.setAdminServerName(name);
      }
    },
    STANDALONE_SERVER_NAME {
      @Override
      void setName(WlsDomainConfig domainConfig, String name, Integer clusterSize) {
        super.setName(domainConfig, name, clusterSize);
        domainConfig.addWlsServer(name, "domain1-" + name, 8001);
      }
    },
    CLUSTERED_SERVER_NAME {
      @Override
      void setName(WlsDomainConfig domainConfig, String name, Integer clusterSize) {
        if (clusterSize == null) {
          throw new IllegalArgumentException("Must specify a cluster size for a " + name() + " scenario");
        }
        for (int i = 1; i <= clusterSize; i++) {
          domainConfig.getClusterConfig(CLUSTER1).addWlsServer(name + i, "domain1-" + name + "-" + i, 8001);
        }
      }
    },
    CLUSTER_NAME {
      @Override
      void setName(WlsDomainConfig domainConfig, String name, Integer clusterSize) {
        super.setName(domainConfig, name, clusterSize);
        domainConfig.withCluster(new WlsClusterConfig(name));
      }
    },
    LISTENING_PORT {
      @Override
      void buildFor(TopologyScenario scenario) {
        scenario.buildPortScenario();
      }
    },
    CONFIGURATION {
      @Override
      void buildFor(TopologyScenario scenario) {
        scenario.buildConfigurationScenario();
      }
    },
    MONITORING_EXPORTER {
      @Override
      void buildFor(TopologyScenario scenario) {
        scenario.buildExporterScenario();
      }
    },
    REPLICAS {
      @Override
      void buildFor(TopologyScenario scenario) {
        scenario.buildReplicasScenario();
      }
    };

    void setName(WlsDomainConfig domainConfig, String name, Integer clusterSize) {
      if (clusterSize != null) {
        throw new IllegalArgumentException("Must not specify a cluster size for a " + name() + " scenario");
      }
    }

    void buildFor(TopologyScenario scenario) {
      scenario.buildNameLengthScenario();
    }

    void setPorts(WlsDomainConfig domainConfig, ServerPort[] ports) {
      WlsServerConfig server = createServerWithNoPorts();
      domainConfig.getClusterConfig(CLUSTER1).addServerConfig(server);
      for (ServerPort port : ports) {
        port.setPort(server);
      }
    }

    @Nonnull
    private WlsServerConfig createServerWithNoPorts() {
      WlsServerConfig server = new WlsServerConfig(MANAGED_SERVER1, LISTEN_ADDRESS, 0);
      server.setListenPort(null);
      return server;
    }

    private DomainConfigurator configureDomain(DomainResource domain) {
      return DomainConfiguratorFactory.forDomain(domain);
    }
  }

  class TopologyScenario {

    private static final String ALPHABET = "Abcdefghijklmnopqrstuvwxyz";

    private final TopologyCase topologyCase;
    private int totalLength;
    private Integer clusterSize;
    private ServerPort[] ports;
    private String unknownClusterName;
    private String unknownServerName;
    private int exportPortNum;
    private final List<ClusterCase> clusterCases = new ArrayList<>();

    public TopologyScenario(TopologyCase topologyCase) {
      this.topologyCase = topologyCase;
    }

    TopologyScenario withExternalServiceEnabled() {
      configureDomain(domain).configureAdminServer().configureAdminService().withChannel("default");
      return this;
    }

    private DomainConfigurator configureDomain(DomainResource domain) {
      return new DomainCommonConfigurator(domain);
    }

    TopologyScenario withExternalServiceSuffix(String suffix) {
      TuningParametersStub.setParameter(EXTERNAL_SERVICE_NAME_SUFFIX_PARAM, suffix);
      return this;
    }

    TopologyScenario withLengthIncludingUid(int totalLength) {
      this.totalLength = totalLength;
      return this;
    }

    TopologyScenario withClusterSize(int clusterSize) {
      this.clusterSize = clusterSize;
      return this;
    }

    TopologyScenario withClusterPaddingDisabled() {
      TuningParametersStub.setParameter(CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM, "false");
      return this;
    }

    TopologyScenario withPorts(ServerPort... ports) {
      this.ports = ports;
      return this;
    }

    ClusterCase withStaticCluster(int numServers) {
      return addClusterCase(new StaticClusterCase(numServers));
    }

    private ClusterCase addClusterCase(ClusterCase clusterCase) {
      clusterCases.add(clusterCase);
      return clusterCase;
    }

    @SuppressWarnings("SameParameterValue")
    TopologyScenario withClusterConfiguration(String unknownClusterName) {
      this.unknownClusterName = unknownClusterName;
      return this;
    }

    @SuppressWarnings("SameParameterValue")
    TopologyScenario withServerConfiguration(String unknownServerName) {
      this.unknownServerName = unknownServerName;
      return this;
    }

    TopologyScenario withPort(int portNum) {
      this.exportPortNum = portNum;
      this.ports = ServerPort.values();
      return this;
    }

    void buildNameLengthScenario() {
      topologyCase.setName(domainConfig, createName(), clusterSize);
    }

    void buildPortScenario() {
      topologyCase.setPorts(domainConfig, ports);
    }

    void buildConfigurationScenario() {
      if (unknownClusterName != null) {
        topologyCase.configureDomain(domain)
              .configureCluster(info, unknownClusterName).withReplicas(1).withServerStartPolicy(IF_NEEDED);
      }
      if (unknownServerName != null) {
        topologyCase.configureDomain(domain).configureServer(unknownServerName);
      }
    }

    void buildExporterScenario() {
      configureDomain(domain)
            .withMonitoringExporterConfiguration("queries:\n").withMonitoringExporterPort(exportPortNum);
      WlsServerConfig serverConfig = new WlsServerConfig(MANAGED_SERVER1, "here", PLAIN_PORT_NUM);
      for (ServerPort port : ports) {
        port.setPort(serverConfig);
      }
      domainConfig.withServer(serverConfig, false);
    }

    void buildReplicasScenario() {
      final DomainConfigurator domainConfigurator = configureDomain(domain);
      for (ClusterCase cluster : clusterCases) {
        domainConfigurator.configureCluster(info, cluster.clusterName).withReplicas(cluster.numReplicas);
        cluster.addCluster(domainConfig);
        testSupport.defineResources(info.getClusterResource(cluster.clusterName));
      }
    }

    void build() {
      topologyCase.buildFor(this);
      ensureNoClustersAreEmpty();
    }

    @Nonnull
    private String createName() {
      return createdName = createNameWithLength(totalLength - domain.getDomainUid().length());
    }

    private String createNameWithLength(int length) {
      return StringUtils.repeat(ALPHABET, (1 + length / ALPHABET.length())).substring(0, length);
    }

    abstract class ClusterCase {
      private final String clusterName;
      private final int maxNumServers;
      private int numReplicas;

      ClusterCase(String clusterName, int maxNumServers) {
        this.clusterName = clusterName;
        this.maxNumServers = maxNumServers;
      }

      TopologyScenario withReplicas(int numReplicas) {
        this.numReplicas = numReplicas;
        return TopologyScenario.this;
      }

      String getClusterName() {
        return clusterName;
      }

      int getMaxNumServers() {
        return maxNumServers;
      }

      abstract void addCluster(WlsDomainConfig domainConfig);
    }

    class StaticClusterCase extends ClusterCase {

      StaticClusterCase(int maxNumServers) {
        super(CLUSTER1, maxNumServers);
      }

      @Override
      void addCluster(WlsDomainConfig domainConfig) {
        final WlsClusterConfig clusterConfig = domainConfig.getClusterConfig(getClusterName());

        for (int i = 1; i <= getMaxNumServers(); i++) {
          clusterConfig.addWlsServer("static-" + i, "static-port-" + i, 8001);
        }
      }
    }
  }

  private void ensureNoClustersAreEmpty() {
    for (String clusterName : domainConfig.getClusterNames()) {
      final WlsClusterConfig clusterConfig = domainConfig.getClusterConfig(clusterName);
      if (clusterConfig.getClusterSize() <= 0) {
        clusterConfig.addWlsServer(clusterName + "-0", "here", 1);
      }
    }
  }

  TopologyScenario defineScenario(TopologyCase topologyCase) {
    return new TopologyScenario(topologyCase);
  }

  @Test
  void whenClusterDoesNotExistInDomain_logWarning() {
    defineScenario(TopologyCase.CONFIGURATION)
          .withClusterConfiguration("no-such-cluster")
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(NO_CLUSTER_IN_DOMAIN, "no-such-cluster", "no-such-cluster");
  }

  @Test
  void whenClusterExistInDomain_clusterStatusContainsMaxReplicas() {
    defineScenario(TopologyCase.REPLICAS)
        .withStaticCluster(10).withReplicas(5).build();
    testSupport.defineResources();
    runTopologyValidationStep();

    assertMaxReplicasSet();
  }

  private void runTopologyValidationStep() {
    testSupport.runSteps(DomainValidationSteps.createValidateDomainTopologySteps(terminalStep));
  }

  private void assertTopologyMismatchReported(String messageKey, Object... parameters) {
    final String message = getFormattedMessage(messageKey, parameters);

    assertThat(domain, hasCondition(FAILED).withReason(TOPOLOGY_MISMATCH).withMessageContaining(message));
    assertThat(logRecords, containsWarning(messageKey).withParams(parameters));
    assertThat(testSupport,
        hasEvent(DOMAIN_FAILED_EVENT)
            .withMessageContaining(getLocalizedString(TOPOLOGY_MISMATCH_EVENT_ERROR), message));
  }

  private void assertMaxReplicasSet() {
    DomainResource domain = testSupport.<DomainResource>getResources(KubernetesTestSupport.DOMAIN).get(0);
    ClusterResource cluster = testSupport.<ClusterResource>getResources(KubernetesTestSupport.CLUSTER).get(0);
    MatcherAssert.assertThat(getClusterStatusMaxReplicas(domain, cluster), equalTo(10));
    MatcherAssert.assertThat(getClusterStatusMaxReplicas(cluster), equalTo(10));
  }

  private Integer getClusterStatusMaxReplicas(DomainResource domain, ClusterResource cluster) {
    return Optional.ofNullable(domain.getStatus()).map(DomainStatus::getClusters).orElse(Collections.emptyList())
        .stream().filter(c -> c.getClusterName().equals(cluster.getClusterName())).findAny()
        .map(ClusterStatus::getMaximumReplicas).orElse(0);
  }

  private Integer getClusterStatusMaxReplicas(ClusterResource cluster) {
    return Optional.ofNullable(cluster)
        .map(ClusterResource::getStatus)
        .map(ClusterStatus::getMaximumReplicas)
        .orElse(0);
  }

  @Test
  void removeOldTopologyFailures() {
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(TOPOLOGY_MISMATCH));

    runTopologyValidationStep();

    assertThat(domain, not(hasCondition(FAILED).withReason(TOPOLOGY_MISMATCH)));
  }

  @Test
  void removeOldReplicasTooHighFailures() {
    ensureNoClustersAreEmpty();
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(REPLICAS_TOO_HIGH));

    runTopologyValidationStep();

    assertThat(domain, not(hasCondition(FAILED).withReason(REPLICAS_TOO_HIGH)));
  }

  @Test
  void preserveTopologyFailuresThatStillExist() {
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    final OffsetDateTime initialTime = SystemClock.now();
    final String message = getFormattedMessage(NO_CLUSTER_IN_DOMAIN, "no-such-cluster", "no-such-cluster");
    domain.getStatus().addCondition(new DomainCondition(FAILED).withReason(TOPOLOGY_MISMATCH)
        .withFailureInfo(domain.getSpec()).withMessage(message));

    SystemClockTestSupport.increment();
    defineScenario(TopologyCase.CONFIGURATION)
          .withClusterConfiguration("no-such-cluster")
          .build();
    runTopologyValidationStep();

    assertThat(domain, hasCondition(FAILED).withMessageContaining(message)
        .withFailureInfo(new DomainConditionFailureInfo(domain.getSpec())).atTime(initialTime));
  }

  // Name length validation tests ensure that the total number of characters in the UID and server/cluster names,
  // combined with any additional formatting involved in creating legal names, does not exceed the Kubernetes limit.
  @Test
  void whenExternalServiceDomainUidPlusASNameNotExceedMaxAllowed_externalServiceDisabled_dontReportError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  private void assertNoTopologyMismatchReported() {
    assertThat(domain, not(hasCondition(FAILED)));
    assertThat(logRecords, empty());
    assertThat(testSupport, not(hasEvent(DOMAIN_FAILED_EVENT)));
  }

  @Test
  void whenDomainUidPlusASNameNotExceedMaxAllowed_externalServiceEnabled_dontReportError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX.length())
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void withExternalServiceEnabled_whenLengthExceedsServiceLimit_reportAdminServerNameTooLongForExternalService() {
    consoleControl.ignoreMessage(ILLEGAL_SERVER_SERVICE_NAME_LENGTH);
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    reportExternalServiceNameTooLong(SERVER_SERVICE_MAX_CHARS - DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX.length());
  }

  private void reportExternalServiceNameTooLong(int limit) {
    assertTopologyFailureReported(ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH, UID, createdName, limit);
  }

  private void assertTopologyFailureReported(String messageKey, Object... params) {
    runTopologyValidationStep();

    assertTopologyMismatchReported(messageKey, params);
  }

  @Test
  void withExternalServiceEnabled_whenLengthExceedsServiceLimit_reportAdminServerNameTooLongForServerService() {
    consoleControl.ignoreMessage(ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH);
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    reportServerServiceNameTooLong();
  }

  private void reportServerServiceNameTooLong() {
    assertTopologyFailureReported(ILLEGAL_SERVER_SERVICE_NAME_LENGTH, UID, createdName, SERVER_SERVICE_MAX_CHARS);
  }

  @Test
  void whenDomainUidPlusASNameExceedMaxAllowed_externalServiceDisabled_reportServiceNameTooLongOnly() {
    consoleControl.trackMessage(ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH);
    consoleControl.trackMessage(ILLEGAL_SERVER_SERVICE_NAME_LENGTH);
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    reportServerServiceNameTooLong();
  }

  @Test
  void whenDomainUidPlusASNameOnlyExternalServiceExceedMaxAllowed_reportOneError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX.length() + 1)
          .build();

    reportExternalServiceNameTooLong(SERVER_SERVICE_MAX_CHARS - DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX.length());
  }

  @Test
  void whenDomainUidPlusASNameNotExceedMaxAllowedWithCustomSuffix_dontReportError() {
    final String customSuffix = "-external";

    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withExternalServiceSuffix(customSuffix)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - customSuffix.length())
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainUidPlusASNameNotExceedMaxAllowedWithEmptyCustomSuffix_dontReportError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withExternalServiceSuffix("")
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void withCustomExternalServiceSuffix_whenLengthExceedsServiceLimit_reportAdminServerNameTooLongForServerService() {
    consoleControl.ignoreMessage(ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH);
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withExternalServiceSuffix("-external")
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    reportServerServiceNameTooLong();
  }

  @Test
  void withCustomExternalServiceSuffix_whenLengthExceedsServiceLimit_reportAdminServerNameTooLongForExternalService() {
    consoleControl.ignoreMessage(ILLEGAL_SERVER_SERVICE_NAME_LENGTH);
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withExternalServiceSuffix("-external")
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    reportExternalServiceNameTooLong(SERVER_SERVICE_MAX_CHARS - "-external".length());
  }

  @Test
  void whenDomainUidPlusMSNameNotExceedMaxAllowed_dontReportError() {
    defineScenario(TopologyCase.STANDALONE_SERVER_NAME)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainUidPlusMSNameExceedsMaxAllowed_reportError() {
    defineScenario(TopologyCase.STANDALONE_SERVER_NAME)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    reportServerServiceNameTooLong();
  }

  @Test
  void whenDomainUidPlusMSNameNotExceedMaxAllowedWithClusterSize9_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(9)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize9_reportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(9)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM + 1)
          .build();

    reportClusteredServerNameTooLong("9");
    logRecords.clear();
  }

  private void reportClusteredServerNameTooLong(String maxIndex) {
    assertTopologyFailureReported(ILLEGAL_SERVER_SERVICE_NAME_LENGTH, UID, createdName + maxIndex,
          SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM + maxIndex.length());
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize9ButClusterPaddingDisabled_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(9)
          .withClusterPaddingDisabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - "1".length())
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize9EvenWithPaddingDisabled_reportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(9)
          .withClusterPaddingDisabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS)
          .build();

    assertTopologyFailureReported(ILLEGAL_SERVER_SERVICE_NAME_LENGTH,
                         UID, createdName + "9", SERVER_SERVICE_MAX_CHARS);
    logRecords.clear();
  }

  @Test
  void whenDomainUidPlusMSNameNotExceedMaxAllowedWithClusterSize99_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(99)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize99_reportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(99)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM + 1)
          .build();

    reportClusteredServerNameTooLong("99");
    logRecords.clear();
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize99ButClusterPaddingDisabled_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(99)
          .withClusterPaddingDisabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - "11".length())
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize100_noExtrSpaceShouldBeReserved_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(100)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - "100".length())
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainUidPlusClusterNameNotExceedMaxAllowed_dontReportError() {
    defineScenario(TopologyCase.CLUSTER_NAME)
          .withLengthIncludingUid(CLUSTER_SERVICE_MAX_CHARS)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainUidPlusClusterNameExceedMaxAllowed_reportError() {
    consoleControl.ignoreMessage(ILLEGAL_SERVER_SERVICE_NAME_LENGTH);
    defineScenario(TopologyCase.CLUSTER_NAME)
          .withLengthIncludingUid(CLUSTER_SERVICE_MAX_CHARS + 1)
          .build();

    reportClusterServiceNameTooLong();
  }

  private void reportClusterServiceNameTooLong() {
    assertTopologyFailureReported(ILLEGAL_CLUSTER_SERVICE_NAME_LENGTH, UID, createdName, CLUSTER_SERVICE_MAX_CHARS);
  }

  // Port validation requires that every server has a port that can be used for admin traffic.

  @Test
  void whenDomainServerHasListenPort_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.PLAIN_PORT)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainServerHasSSLListenPort_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.SSL_PORT)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainServerHasAdminPort_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.ADMIN_PORT)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainServerHasAdminNAP_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.NAP_FOR_ADMIN)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainServerHasMultiplePorts_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.SSL_PORT, ServerPort.NAP_FOR_ADMIN, ServerPort.NAP_FOR_T3)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenDomainServerNoAvailablePortForREST_reportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.NAP_FOR_T3)
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(NO_AVAILABLE_PORT_TO_USE_FOR_REST, UID, MANAGED_SERVER1);
  }

  // Monitoring exporter tests ensure that the port configured for the exporter is not used in the topology.
  @Test
  void portNumberInMonitoringExportConflictServerMessage_isFormattedWithoutCommas() {
    assertThat(
          getFormattedMessage(MONITORING_EXPORTER_CONFLICT_SERVER, 8001, MANAGED_SERVER1),
          containsString("8001"));
  }

  @Test
  void portNumberInMonitoringExportConflictDynamicClusterMessage_isFormattedWithoutCommas() {
    assertThat(
          getFormattedMessage(MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER, 9001, DYNAMIC_CLUSTER_NAME),
          containsString("9001"));
  }

  @Test
  void whenMonitoringExporterPortConflictsWithServerPlainTextPort_reportError() {
    defineScenario(TopologyCase.MONITORING_EXPORTER)
          .withPort(PLAIN_PORT_NUM)
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(MONITORING_EXPORTER_CONFLICT_SERVER, PLAIN_PORT_NUM, MANAGED_SERVER1);
  }

  @Test
  void whenMonitoringExporterPortNotUsedInTopology_doNotReport() {
    defineScenario(TopologyCase.MONITORING_EXPORTER)
          .withPort(EXPORTER_PORT_NUM)
          .build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenMonitoringExporterPortConflictsWithServerSslPort_reportError() {
    defineScenario(TopologyCase.MONITORING_EXPORTER)
          .withPort(SSL_PORT_NUM)
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(MONITORING_EXPORTER_CONFLICT_SERVER, SSL_PORT_NUM, MANAGED_SERVER1);
  }

  @Test
  void whenMonitoringExporterPortConflictsWithServerAdminPort_reportError() {
    defineScenario(TopologyCase.MONITORING_EXPORTER)
          .withPort(SERVER_ADMIN_PORT_NUM)
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(MONITORING_EXPORTER_CONFLICT_SERVER, SERVER_ADMIN_PORT_NUM, MANAGED_SERVER1);
  }

  @Test
  void whenMonitoringExporterPortConflictsWithNapPort_reportError() {
    defineScenario(TopologyCase.MONITORING_EXPORTER)
          .withPort(NAP_PORT_NUM)
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(MONITORING_EXPORTER_CONFLICT_SERVER, NAP_PORT_NUM, MANAGED_SERVER1);
  }

  @Test
  void whenMonitoringExporterPortConflictsWithClusteredServerPlainTextPort_reportError() {
    defineScenario(TopologyCase.MONITORING_EXPORTER)
          .withPort(DEFAULT_LISTEN_PORT)
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(
          MONITORING_EXPORTER_CONFLICT_SERVER, DEFAULT_LISTEN_PORT, CLUSTER_MANAGED_SERVER_NAME);
  }

  @Test
  void whenMonitoringExporterPortConflictsWithClusterServerTemplatePort_reportError() {
    defineScenario(TopologyCase.MONITORING_EXPORTER)
          .withPort(SERVER_TEMPLATE_PORT_NUM)
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER,
          SERVER_TEMPLATE_PORT_NUM, DYNAMIC_CLUSTER_NAME);
  }

  @Test
  void whenMonitoringExporterPortConflictsWithClusterServerTemplatePort_logWarningAndGenerateEvent() {
    defineScenario(TopologyCase.MONITORING_EXPORTER)
          .withPort(SERVER_TEMPLATE_PORT_NUM)
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(
          MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER, SERVER_TEMPLATE_PORT_NUM, DYNAMIC_CLUSTER_NAME);
  }

  @Test
  void whenServerDoesNotExistInDomain_logWarningAndCreateEvent() {
    defineScenario(TopologyCase.CONFIGURATION)
          .withServerConfiguration("no-such-server")
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server");
  }

  @Test
  void whenClusterDoesNotExistInDomain_logWarningAndCreateEvent() {
    defineScenario(TopologyCase.CONFIGURATION)
          .withClusterConfiguration("no-such-cluster")
          .build();

    runTopologyValidationStep();

    assertTopologyMismatchReported(NO_CLUSTER_IN_DOMAIN, "no-such-cluster", "no-such-cluster");
  }

  @Test
  void whenBothServerAndClusterDoNotExistInDomain_createEventWithBothWarnings() {
    consoleControl.ignoreMessage(NO_MANAGED_SERVER_IN_DOMAIN);
    consoleControl.ignoreMessage(NO_CLUSTER_IN_DOMAIN);
    defineScenario(TopologyCase.CONFIGURATION)
          .withServerConfiguration("no-such-server")
          .withClusterConfiguration("no-such-cluster")
          .build();

    runTopologyValidationStep();

    assertThat(testSupport, hasEvent(DOMAIN_FAILED_EVENT)
                .withMessageContaining(getLocalizedString(TOPOLOGY_MISMATCH_EVENT_ERROR),
                      getFormattedMessage(NO_CLUSTER_IN_DOMAIN, "no-such-cluster", "no-such-cluster"),
                      getFormattedMessage(NO_MANAGED_SERVER_IN_DOMAIN, "no-such-server")));
  }

  @Test
  void whenConfiguredStaticReplicasInTopologyRange_dontReportReplicasTooHigh() {
    consoleControl.trackMessage(TOO_MANY_REPLICAS_FAILURE);
    defineScenario(TopologyCase.REPLICAS)
        .withStaticCluster(10).withReplicas(5).build();

    runTopologyValidationStep();

    assertNoTopologyMismatchReported();
  }

  @Test
  void whenConfiguredStaticReplicasAboveLimit_reportReplicasTooHigh() {
    consoleControl.trackMessage(TOO_MANY_REPLICAS_FAILURE);
    defineScenario(TopologyCase.REPLICAS)
        .withStaticCluster(5).withReplicas(10).build();

    runTopologyValidationStep();

    assertReplicasTooHighReported(10, CLUSTER1, 5);
  }

  private void assertReplicasTooHighReported(Object... parameters) {
    final String message = getFormattedMessage(TOO_MANY_REPLICAS_FAILURE, parameters);

    assertThat(domain, hasCondition(FAILED).withReason(REPLICAS_TOO_HIGH).withMessageContaining(message));
    assertThat(logRecords, containsWarning(TOO_MANY_REPLICAS_FAILURE).withParams(parameters));
    assertThat(testSupport,
        hasEvent(DOMAIN_FAILED_EVENT)
            .withMessageContaining(getLocalizedString(REPLICAS_TOO_HIGH_EVENT_ERROR), message));
  }

  // todo compute ReplicasTooHigh
  // todo remove ReplicasTooHigh

  /*

  @Test
  void whenReplicaCountExceedsMaxReplicasForDynamicCluster_addFailedAndCompletedFalseCondition() {
    info.setReplicaCount("cluster1", 5);
    defineScenario().withDynamicCluster("cluster1", 0, 4).build();

    updateDomainStatus();

    assertThat(getRecordedDomain(), hasCondition(FAILED)
        .withReason(REPLICAS_TOO_HIGH).withMessageContaining("cluster1"));
    assertThat(getRecordedDomain(), hasCondition(COMPLETED).withStatus(FALSE));
  }
   */

  private String getFormattedMessage(String msgId, Object... params) {
    LoggingFacade logger = LoggingFactory.getLogger("Operator", "Operator");
    return logger.formatMessage(msgId, params);
  }

}

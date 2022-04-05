// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.helpers.LegalNames.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX;
import static oracle.kubernetes.operator.helpers.LegalNames.EXTERNAL_SERVICE_NAME_SUFFIX_PARAM;
import static oracle.kubernetes.operator.helpers.LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH;
import static oracle.kubernetes.operator.helpers.WlsConfigValidator.CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class WlsConfigValidationTest {
  private static final String ADMIN_SERVER_NAME = "admin";
  private static final String CLUSTER = "cluster";
  private static final int LEGAL_NAME_INFIX_SIZE = 1;
  private static final int SERVER_SERVICE_MAX_CHARS = LEGAL_DNS_LABEL_NAME_MAX_LENGTH - LEGAL_NAME_INFIX_SIZE;
  private static final int PADDED_SERVER_NUM = "999".length();   // room to allow for server numbers up to 999
  private static final int CLUSTER_NAME_INFIX_SIZE = "-cluster-".length();
  private static final int CLUSTER_SERVICE_MAX_CHARS = LEGAL_DNS_LABEL_NAME_MAX_LENGTH - CLUSTER_NAME_INFIX_SIZE;

  private final Domain domain = createTestDomain();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final WlsDomainConfig domainConfig = createDomainConfig();

  private static WlsDomainConfig createDomainConfig() {
    return new WlsDomainConfig("base_domain")
          .withAdminServer(ADMIN_SERVER_NAME, "domain1-admin-server", 7001)
          .withCluster(new WlsClusterConfig(CLUSTER));
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  enum ServerPort {
    PLAIN_PORT {
      @Override
      void setPort(WlsServerConfig server, int portNum) {
        server.setListenPort(portNum);
      }
    },
    SSL_PORT {
      @Override
      void setPort(WlsServerConfig server, int portNum) {
        server.setSslListenPort(portNum);
      }
    },
    ADMIN_PORT {
      @Override
      void setPort(WlsServerConfig server, int portNum) {
        server.setAdminPort(portNum);
      }
    },
    NAP_FOR_ADMIN {
      @Override
      void setPort(WlsServerConfig server, int portNum) {
        server.addNetworkAccessPoint("admin-nap", "admin", portNum);
      }
    },
    NAP_FOR_T3 {
      @Override
      void setPort(WlsServerConfig server, int portNum) {
        server.addNetworkAccessPoint("t3-nap", "t3", portNum);
      }
    };

    abstract void setPort(WlsServerConfig server, int portNum);
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
          domainConfig.getClusterConfig(CLUSTER).addWlsServer(name + i, "domain1-" + name + "-" + i, 8001);
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
      int portNum = 7001;
      WlsServerConfig server = createServerWithNoPorts();
      domainConfig.getClusterConfig(CLUSTER).addServerConfig(server);
      for (ServerPort port : ports) {
        port.setPort(server, portNum);
      }
    }

    @NotNull
    private WlsServerConfig createServerWithNoPorts() {
      WlsServerConfig server = new WlsServerConfig("server", "server", 0);
      server.setListenPort(null);
      return server;
    }
  }

  class TopologyScenario {

    private static final String ALPHABET = "Abcdefghijklmnopqrstuvwxyz";

    private final TopologyCase topologyCase;
    private int totalLength;
    private Integer clusterSize;
    private ServerPort[] ports;

    public TopologyScenario(TopologyCase topologyCase) {
      this.topologyCase = topologyCase;
    }

    TopologyScenario withExternalServiceEnabled() {
      configureDomain(domain).configureAdminServer().configureAdminService().withChannel("default");
      return this;
    }

    private DomainConfigurator configureDomain(Domain domain) {
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

    void buildNameLengthScenario() {
      topologyCase.setName(domainConfig, createName(), clusterSize);
    }

    void buildPortScenario() {
      topologyCase.setPorts(domainConfig, ports);
    }

    void build() {
      topologyCase.buildFor(this);
    }

    @NotNull
    private String createName() {
      return createNameWithLength(totalLength - domain.getDomainUid().length());
    }

    private String createNameWithLength(int length) {
      return StringUtils.repeat(ALPHABET, (1 + length / ALPHABET.length())).substring(0, length);
    }
  }

  TopologyScenario defineScenario(TopologyCase topologyCase) {
    return new TopologyScenario(topologyCase);
  }

  // Name length validation tests ensure that the total number of characters in the UID and server/cluster names,
  // combined with any additional formatting involved in creating legal names, does not exceed the Kubernetes limit.
  @Test
  void whenExternalServiceDomainUidPlusASNameNotExceedMaxAllowed_externalServiceDisabled_dontReportError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  private List<String> getWlsConfigValidationFailures() {
    testSupport.addToPacket(DOMAIN_TOPOLOGY, domainConfig);
    testSupport.addDomainPresenceInfo(new DomainPresenceInfo(domain));
    return new WlsConfigValidator(testSupport.getPacket()).getFailures();
  }

  @Test
  void whenDomainUidPlusASNameNotExceedMaxAllowed_externalServiceEnabled_dontReportError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX.length())
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusASNameExceedMaxAllowed_externalServiceEnabled_reportTwoErrors() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    assertThat(getWlsConfigValidationFailures(),
          both(hasItem(stringContainsInOrder("DomainUID ", UID, "server name", "exceeds maximum allowed length")))
                .and(hasItem(stringContainsInOrder("DomainUID ", UID, "admin server name",
                      "exceeds maximum allowed length"))));
  }

  @Test
  void whenDomainUidPlusASNameExceedMaxAllowed_externalServiceDisabled_reportOneError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    assertThat(getWlsConfigValidationFailures(), contains(
          stringContainsInOrder("DomainUID ", UID, "server name", "exceeds maximum allowed length")));
  }

  @Test
  void whenDomainUidPlusASNameOnlyExternalServiceExceedMaxAllowed_reportOneError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX.length() + 1)
          .build();

    assertThat(getWlsConfigValidationFailures(),
          contains(stringContainsInOrder("DomainUID ", UID, "admin server name", "exceeds maximum allowed length")));
  }

  @Test
  void whenDomainUidPlusASNameNotExceedMaxAllowedWithCustomSuffix_dontReportError() {
    final String customSuffix = "-external";

    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withExternalServiceSuffix(customSuffix)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - customSuffix.length())
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusASNameNotExceedMaxAllowedWithEmptyCustomSuffix_dontReportError() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withExternalServiceSuffix("")
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusASNameExceedMaxAllowedWithCustomSuffix_reportTwoErrors() {
    defineScenario(TopologyCase.ADMIN_SERVER_NAME)
          .withExternalServiceEnabled()
          .withExternalServiceSuffix("-external")
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    assertThat(getWlsConfigValidationFailures(),
          both(hasItem(stringContainsInOrder(UID, "server name", "exceeds maximum allowed length")))
                .and(hasItem(stringContainsInOrder(UID, "admin server name", "exceeds maximum allowed length"))));
  }

  @Test
  void whenDomainUidPlusMSNameNotExceedMaxAllowed_dontReportError() {
    defineScenario(TopologyCase.STANDALONE_SERVER_NAME)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusMSNameExceedsMaxAllowed_reportError() {
    defineScenario(TopologyCase.STANDALONE_SERVER_NAME)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS + 1)
          .build();

    assertThat(getWlsConfigValidationFailures(),
          contains(stringContainsInOrder("DomainUID ", UID, "server name", "exceeds maximum allowed length")));
  }

  @Test
  void whenDomainUidPlusMSNameNotExceedMaxAllowedWithClusterSize9_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(9)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize9_reportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(9)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM + 1)
          .build();

    assertThat(getWlsConfigValidationFailures(),
          hasItem(stringContainsInOrder("DomainUID ", UID, "server name", "exceeds maximum allowed length")));
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize9ButClusterPaddingDisabled_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(9)
          .withClusterPaddingDisabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - "1".length())
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize9EvenWithPaddingDisabled_reportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(9)
          .withClusterPaddingDisabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS)
          .build();

    assertThat(getWlsConfigValidationFailures(),
          hasItem(stringContainsInOrder("DomainUID ", UID, "server name", "exceeds maximum allowed length")));
  }

  @Test
  void whenDomainUidPlusMSNameNotExceedMaxAllowedWithClusterSize99_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(99)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize99_reportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(99)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - PADDED_SERVER_NUM + 1)
          .build();

    assertThat(getWlsConfigValidationFailures(),
          hasItem(stringContainsInOrder("DomainUID ", UID, "server name", "exceeds maximum allowed length")));
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize99ButClusterPaddingDisabled_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(99)
          .withClusterPaddingDisabled()
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - "11".length())
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusMSNameExceedMaxAllowedWithClusterSize100_noExtrSpaceShouldBeReserved_dontReportError() {
    defineScenario(TopologyCase.CLUSTERED_SERVER_NAME)
          .withClusterSize(100)
          .withLengthIncludingUid(SERVER_SERVICE_MAX_CHARS - "100".length())
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusClusterNameNotExceedMaxAllowed_dontReportError() {
    defineScenario(TopologyCase.CLUSTER_NAME)
          .withLengthIncludingUid(CLUSTER_SERVICE_MAX_CHARS)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainUidPlusClusterNameExceedMaxAllowed_reportError() {
    defineScenario(TopologyCase.CLUSTER_NAME)
          .withLengthIncludingUid(CLUSTER_SERVICE_MAX_CHARS + 1)
          .build();

    assertThat(getWlsConfigValidationFailures(), contains(stringContainsInOrder(
          "DomainUID ", UID, "cluster name", "exceeds maximum allowed length")));
  }

  // Port validation requires that every server has a port that can be used for admin traffic.

  @Test
  void whenDomainServerHasListenPort_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.PLAIN_PORT)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainServerHasSSLListenPort_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.SSL_PORT)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainServerHasAdminPort_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.ADMIN_PORT)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainServerHasAdminNAP_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.NAP_FOR_ADMIN)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainServerHasMultiplePorts_dontReportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.SSL_PORT, ServerPort.NAP_FOR_ADMIN, ServerPort.NAP_FOR_T3)
          .build();

    assertThat(getWlsConfigValidationFailures(), empty());
  }

  @Test
  void whenDomainServerNoAvailablePortForREST_reportError() {
    defineScenario(TopologyCase.LISTENING_PORT)
          .withPorts(ServerPort.NAP_FOR_T3)
          .build();

    assertThat(getWlsConfigValidationFailures(), contains(stringContainsInOrder(
          "DomainUID", UID, "server",
          "does not have a port available for the operator to send REST calls.")));
  }

}

// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainCommonConfigurator;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINRESTARTVERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERRESTARTVERSION_LABEL;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class RollStateComputationTest {

  private static final String OLD_IMAGE = "expectedImage:oldRevision";
  private static final String EXPECTED_IMAGE = "expectedImage:expectedRevision";
  private static final String TEST_SERVER = "ms1";

  private final Domain domain = createDomain();
  private final WlsDomainConfig domainConfig = createWlsDomainConfig();
  private final DomainPresenceInfo info = new DomainPresenceInfo(domain);
  private final RollStateComputation computation = new RollStateComputation(info);

  private final List<Memento> mementos = new ArrayList<>();

  @Nonnull
  private Domain createDomain() {
    return DomainProcessorTestSetup.createTestDomain()
          .withSpec(new DomainSpec().withImage(EXPECTED_IMAGE))
          .withStatus(new DomainStatus()
                .addServer(new ServerStatus().withServerName("ms1").withClusterName("cluster1"))
                .addServer(new ServerStatus().withServerName("ms2").withClusterName("cluster1"))
                .addServer(new ServerStatus().withServerName("ms3").withClusterName("cluster2"))
                .addServer(new ServerStatus().withServerName("solo")));
  }

  @Nonnull
  private WlsDomainConfig createWlsDomainConfig() {
    final WlsDomainConfig wlsDomainConfig = new WlsDomainConfig(domain.getDomainUid())
          .withAdminServer("admin", "adminHost", 8001);
    wlsDomainConfig.addWlsServer(TEST_SERVER, "localhost", 7001);
    return wlsDomainConfig;
  }

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(TuningParametersStub.install());

    info.setServerPod(TEST_SERVER, PodHelper.createManagedServerPodModel(createPacket()));
  }

  @Nonnull
  private Packet createPacket() {
    Packet packet = new Packet();
    info.addToPacket(packet);
    packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, domainConfig);
    packet.put(ProcessingConstants.SERVER_SCAN, domainConfig.getServerConfig(TEST_SERVER));
    return packet;
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void canGetClusterFromServerStatus() {
    assertThat(computation.getClusterFor("ms1"), equalTo("cluster1"));
    assertThat(computation.getClusterFor("ms2"), equalTo("cluster1"));
    assertThat(computation.getClusterFor("ms3"), equalTo("cluster2"));
    assertThat(computation.getClusterFor("solo"), nullValue());
  }

  @Test
  void canGetServerSpecFromDomain() {
    domain.getSpec().setNodeName("default");
    configureDomain().configureServer("solo").withNodeName("node0");
    configureDomain().configureServer("ms1").withNodeName("node1");
    configureDomain().configureCluster("cluster1").withNodeName("node2");

    assertThat(computation.getServerSpec("ms1").getNodeName(), equalTo("node1"));
    assertThat(computation.getServerSpec("ms2").getNodeName(), equalTo("node2"));
    assertThat(computation.getServerSpec("ms3").getNodeName(), equalTo("default"));
    assertThat(computation.getServerSpec("solo").getNodeName(), equalTo("node0"));
  }

  private DomainConfigurator configureDomain() {
    return new DomainCommonConfigurator(domain);
  }

  @Test
  void whenServerPodHasExpectedValues_reportRollComplete() {
    configureDomain().withIntrospectVersion("1");
    configureDomain().withRestartVersion("2");
    configureDomain().configureCluster("cluster1").withRestartVersion("3");
    configureDomain().configureServer(TEST_SERVER).withRestartVersion("4");
    setPodLabel(INTROSPECTION_STATE_LABEL, "1");
    setPodLabel(DOMAINRESTARTVERSION_LABEL, "2");
    setPodLabel(CLUSTERRESTARTVERSION_LABEL, "3");
    setPodLabel(SERVERRESTARTVERSION_LABEL, "4");

    assertThat(computation.isRollCompleteFor(TEST_SERVER), is(true));
  }

  private void setPodLabel(String labelName, String labelValue) {
    Objects.requireNonNull(info.getServerPod(TEST_SERVER).getMetadata()).putLabelsItem(labelName, labelValue);
  }

  @Test
  void whenServerPodHasOldImage_reportRollNotComplete() {
    info.getServerPod(TEST_SERVER).getSpec().getContainers().get(0).image(OLD_IMAGE);

    assertThat(computation.isRollCompleteFor(TEST_SERVER), is(false));
  }

  @Test
  void whenDomainHasIntrospectionVersionButServerPodDoesNot_reportRollNotComplete() {
    info.getDomain().getSpec().setIntrospectVersion("1");

    assertThat(computation.isRollCompleteFor(TEST_SERVER), is(false));
  }

  @Test
  void whenServerPodIntrospectionVersionDoesNotMatchDomain_reportRollNotComplete() {
    info.getDomain().getSpec().setIntrospectVersion("2");
    setPodLabel(INTROSPECTION_STATE_LABEL, "1");

    assertThat(computation.isRollCompleteFor(TEST_SERVER), is(false));
  }

  @Test
  void whenServerPodHasDomainRestartVersionButDomainDoesNot_reportRollNotComplete() {
    setPodLabel(DOMAINRESTARTVERSION_LABEL, "1");

    assertThat(computation.isRollCompleteFor(TEST_SERVER), is(false));
  }

  @Test
  void whenServerPodClusterRestartVersionDoesNotMatchCurrentCluster_reportRollNotComplete() {
    configureDomain().configureCluster("cluster1").withRestartVersion("3");
    setPodLabel(CLUSTERRESTARTVERSION_LABEL, "4");

    assertThat(computation.isRollCompleteFor(TEST_SERVER), is(false));
  }

  @Test
  void whenServerPodServerRestartVersionDoesNotMatchCurrentServer_reportRollNotComplete() {
    configureDomain().configureServer(TEST_SERVER).withRestartVersion("6");
    setPodLabel(SERVERRESTARTVERSION_LABEL, "7");

    assertThat(computation.isRollCompleteFor(TEST_SERVER), is(false));
  }

  // todo support auxiliary images
}

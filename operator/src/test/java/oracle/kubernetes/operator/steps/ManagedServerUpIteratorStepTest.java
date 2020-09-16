// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.ClusterConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ManagedServerUpIteratorStepTest {

  protected static final String DOMAIN_NAME = "domain1";
  private static final String NS = "namespace";
  private static final String UID = "uid1";
  protected static final String KUBERNETES_UID = "12345";
  private static final String ADMIN = "asName";
  private static final String CLUSTER = "cluster1";
  private static final boolean INCLUDE_SERVER_OUT_IN_POD_LOG = true;
  private static final String CREDENTIALS_SECRET_NAME = "webLogicCredentialsSecretName";
  private static final String LATEST_IMAGE = "image:latest";
  private static final String MS_PREFIX = "ms";
  private static final String MS1 = MS_PREFIX + "1";
  private static final String MS2 = MS_PREFIX + "2";
  private static final String MS3 = MS_PREFIX + "3";
  private static final String MS4 = MS_PREFIX + "4";
  private static final int MAX_SERVERS = 5;
  private static final int PORT = 8001;
  private static final String[] MANAGED_SERVER_NAMES =
          IntStream.rangeClosed(1, MAX_SERVERS)
                  .mapToObj(ManagedServerUpIteratorStepTest::getManagedServerName).toArray(String[]::new);

  @Nonnull
  private static String getManagedServerName(int n) {
    return MS_PREFIX + n;
  }

  private final Domain domain = createDomain();
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private final WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);

  private final Step nextStep = new TerminalStep();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfoWithServers();
  private final WlsDomainConfig domainConfig = createDomainConfig();

  private static WlsDomainConfig createDomainConfig() {
    WlsClusterConfig clusterConfig = new WlsClusterConfig(CLUSTER);
    for (String serverName : MANAGED_SERVER_NAMES) {
      clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
    }
    return new WlsDomainConfig("base_domain")
            .withAdminServer(ADMIN, "domain1-admin-server", 7001)
            .withCluster(clusterConfig);
  }

  private DomainPresenceInfo createDomainPresenceInfoWithServers(String... serverNames) {
    DomainPresenceInfo dpi = new DomainPresenceInfo(domain);
    addServer(dpi, ADMIN);
    Arrays.asList(serverNames).forEach(serverName -> addServer(dpi, serverName));
    return dpi;
  }

  private Domain createDomain() {
    return new Domain()
            .withApiVersion(KubernetesConstants.DOMAIN_VERSION)
            .withKind(KubernetesConstants.DOMAIN)
            .withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME).uid(KUBERNETES_UID))
            .withSpec(createDomainSpec());
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec()
            .withDomainUid(UID)
            .withWebLogicCredentialsSecret(new V1SecretReference().name(CREDENTIALS_SECRET_NAME))
            .withIncludeServerOutInPodLog(INCLUDE_SERVER_OUT_IN_POD_LOG)
            .withImage(LATEST_IMAGE);
  }

  private static void addServer(DomainPresenceInfo domainPresenceInfo, String serverName) {
    if (serverName.equals(ADMIN)) {
      domainPresenceInfo.setServerPod(serverName, createReadyPod(serverName));
    } else {
      domainPresenceInfo.setServerPod(serverName, createPod(serverName));
    }
  }

  private static V1Pod createReadyPod(String serverName) {
    return new V1Pod().metadata(withNames(new V1ObjectMeta().namespace(NS), serverName))
            .spec(new V1PodSpec().nodeName("Node1"))
            .status(new V1PodStatus().phase("Running")
            .addConditionsItem(new V1PodCondition().type("Ready").status("True")));
  }

  private static V1Pod createPod(String serverName) {
    return new V1Pod().metadata(withNames(new V1ObjectMeta().namespace(NS), serverName));
  }

  private static V1ObjectMeta withNames(V1ObjectMeta objectMeta, String serverName) {
    return objectMeta
            .name(LegalNames.toPodName(UID, serverName))
            .putLabelsItem(LabelConstants.SERVERNAME_LABEL, serverName);
  }

  /**
   * Setup env for tests.
   * @throws NoSuchFieldException if TestStepFactory fails to install
   */
  @Before
  public void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(ApiException.class));
    mementos.add(TuningParametersStub.install());
    mementos.add(testSupport.install());

    testSupport.defineResources(domain);
    testSupport
            .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainConfig)
            .addDomainPresenceInfo(domainPresenceInfo);
  }

  /**
   * Cleanup env after tests.
   * @throws Exception if test support failed
   */
  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

    testSupport.throwOnCompletionFailure();
  }

  private void makePodReady(String serverName) {
    domainPresenceInfo.getServerPod(serverName).status(new V1PodStatus().phase("Running"));
    Objects.requireNonNull(domainPresenceInfo.getServerPod(serverName).getStatus())
            .addConditionsItem(new V1PodCondition().status("True").type("Ready"));
  }

  private void schedulePod(String serverName, String nodeName) {
    Objects.requireNonNull(domainPresenceInfo.getServerPod(serverName).getSpec()).setNodeName(nodeName);
  }

  @Test
  public void withConcurrencyOf1_bothClusteredServersScheduleAndStartSequentially() {
    configureCluster(CLUSTER).withMaxConcurrentStartup(1);
    //addWlsCluster(CLUSTER, 8001, MS1, MS2);
    addWlsCluster(CLUSTER, 8001, MS1, MS2);

    invokeStepWithServerStartupInfos(createServerStartupInfosForCluster(CLUSTER,MS1, MS2));

    assertThat(MS1 + " pod", domainPresenceInfo.getServerPod(MS1), notNullValue());
    schedulePod(MS1, "Node1");
    testSupport.setTime(100, TimeUnit.MILLISECONDS);
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), nullValue());
    makePodReady(MS1);
    testSupport.setTime(10, TimeUnit.SECONDS);
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), notNullValue());
  }

  @Test
  public void withConcurrencyOf0_clusteredServersScheduleSequentiallyAndStartConcurrently() {
    configureCluster(CLUSTER).withMaxConcurrentStartup(0);
    addWlsCluster(CLUSTER, PORT, MS1, MS2);

    invokeStepWithServerStartupInfos(createServerStartupInfosForCluster(CLUSTER,MS1, MS2));

    assertThat(MS1 + " pod", domainPresenceInfo.getServerPod(MS1), notNullValue());
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), nullValue());
    schedulePod(MS1, "Node1");
    testSupport.setTime(100, TimeUnit.MILLISECONDS);
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), notNullValue());
  }

  @Test
  public void withConcurrencyOf2_clusteredServersScheduleSequentiallyAndStartConcurrently() {
    configureCluster(CLUSTER).withMaxConcurrentStartup(2);
    addWlsCluster(CLUSTER, PORT, MS1, MS2);

    invokeStepWithServerStartupInfos(createServerStartupInfosForCluster(CLUSTER, MS1, MS2));

    assertThat(MS1 + " pod", domainPresenceInfo.getServerPod(MS1), notNullValue());
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), nullValue());
    schedulePod(MS1, "Node1");
    testSupport.setTime(100, TimeUnit.MILLISECONDS);
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), notNullValue());
  }

  @Test
  public void withConcurrencyOf2_4clusteredServersScheduleSequentiallyAndStartIn2Threads() {
    configureCluster(CLUSTER).withMaxConcurrentStartup(2);
    addWlsCluster(CLUSTER, PORT, MS1, MS2, MS3, MS4);

    invokeStepWithServerStartupInfos(createServerStartupInfosForCluster(CLUSTER, MS1, MS2, MS3, MS4));
    assertThat(MS1 + " pod", domainPresenceInfo.getServerPod(MS1), notNullValue());
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), nullValue());
    schedulePod(MS1, "Node1");
    testSupport.setTime(100, TimeUnit.MILLISECONDS);
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), notNullValue());
    assertThat(MS3 + " pod", domainPresenceInfo.getServerPod(MS3), nullValue());
    schedulePod(MS2, "Node2");
    testSupport.setTime(100, TimeUnit.MILLISECONDS);
    assertThat(MS3 + " pod", domainPresenceInfo.getServerPod(MS3), nullValue());
    makePodReady(MS1);
    testSupport.setTime(10, TimeUnit.SECONDS);
    assertThat(MS3 + " pod", domainPresenceInfo.getServerPod(MS3), notNullValue());
    assertThat(MS4 + " pod", domainPresenceInfo.getServerPod(MS4), nullValue());
    makePodReady(MS2);
    schedulePod(MS3, "Node3");
    testSupport.setTime(10, TimeUnit.SECONDS);
    assertThat(MS4 + " pod", domainPresenceInfo.getServerPod(MS4), notNullValue());
  }

  @Test
  public void withMultipleClusters_differentClusterScheduleAndStartDifferently() {
    final String CLUSTER2 = "cluster2";

    configureCluster(CLUSTER).withMaxConcurrentStartup(0);
    configureCluster(CLUSTER2).withMaxConcurrentStartup(1);

    addWlsCluster(CLUSTER, PORT, MS1, MS2);
    addWlsCluster(CLUSTER2, PORT, MS3, MS4);

    Collection<ServerStartupInfo> serverStartupInfos = createServerStartupInfosForCluster(CLUSTER, MS1, MS2);
    serverStartupInfos.addAll(createServerStartupInfosForCluster(CLUSTER2, MS3, MS4));
    invokeStepWithServerStartupInfos(serverStartupInfos);

    assertThat(MS1 + " pod", domainPresenceInfo.getServerPod(MS1), notNullValue());
    assertThat(MS3 + " pod", domainPresenceInfo.getServerPod(MS3), notNullValue());
    schedulePod(MS1, "Node1");
    schedulePod(MS3, "Node2");
    testSupport.setTime(100, TimeUnit.MILLISECONDS);
    assertThat(MS2 + " pod", domainPresenceInfo.getServerPod(MS2), notNullValue());
    assertThat(MS4 + " pod", domainPresenceInfo.getServerPod(MS4), nullValue());
    //makePodReady(MS3);
    //k8sTestSupport.setTime(10, TimeUnit.SECONDS);
    //assertThat(MS4 + " pod", domainPresenceInfo.getServerPod(MS4), notNullValue());
  }

  @Test
  public void maxClusterConcurrentStartup_doesNotApplyToNonClusteredServers() {
    domain.getSpec().setMaxClusterConcurrentStartup(1);

    addWlsServers(MS3, MS4);

    invokeStepWithServerStartupInfos(createServerStartupInfos(MS3, MS4));

    assertThat(MS3 + " pod", domainPresenceInfo.getServerPod(MS3), notNullValue());
    schedulePod(MS3, "Node2");
    testSupport.setTime(200, TimeUnit.MILLISECONDS);
    assertThat(MS3 + " pod", domainPresenceInfo.getServerPod(MS3), notNullValue());
  }

  @NotNull
  private Collection<ServerStartupInfo> createServerStartupInfosForCluster(String clusterName, String... servers) {
    Collection<ServerStartupInfo> serverStartupInfos = new ArrayList<>();
    Arrays.stream(servers).forEach(server ->
            serverStartupInfos.add(
                new ServerStartupInfo(configSupport.getWlsServer(clusterName, server),
                    clusterName,
                    domain.getServer(server, clusterName))
            )
    );
    return serverStartupInfos;
  }

  @NotNull
  private Collection<ServerStartupInfo> createServerStartupInfos(String... servers) {
    Collection<ServerStartupInfo> serverStartupInfos = new ArrayList<>();
    Arrays.stream(servers).forEach(server ->
        serverStartupInfos.add(
            new ServerStartupInfo(configSupport.getWlsServer(server),
                null,
                domain.getServer(server, null))
        )
    );
    return serverStartupInfos;
  }

  private void invokeStepWithServerStartupInfos(Collection<ServerStartupInfo> startupInfos) {
    ManagedServerUpIteratorStep step = new ManagedServerUpIteratorStep(startupInfos, nextStep);
    testSupport.runSteps(step);
  }

  private ClusterConfigurator configureCluster(String clusterName) {
    return configurator.configureCluster(clusterName);
  }

  private void addWlsServers(String... serverNames) {
    Arrays.asList(serverNames).forEach(this::addWlsServer);
  }

  private void addWlsServer(String serverName) {
    configSupport.addWlsServer(serverName, 8001);
  }

  private void addWlsCluster(String clusterName, int port, String... serverNames) {

    configSupport.addWlsCluster(clusterName, port, serverNames);
  }
}
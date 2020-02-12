// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.helpers.AnnotationHelper;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.rest.ScanCacheStub;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class DomainProcessorTest {
  private static final String ADMIN_NAME = "admin";
  private static final String CLUSTER = "cluster";
  private static final int MAX_SERVERS = 5;
  private static final String MS_PREFIX = "managed-server";
  private static final int MIN_REPLICAS = 2;
  private static final int NUM_ADMIN_SERVERS = 1;
  private static final int NUM_JOB_PODS = 1;
  private static final String[] MANAGED_SERVER_NAMES =
      IntStream.rangeClosed(1, MAX_SERVERS).mapToObj(n -> MS_PREFIX + n).toArray(String[]::new);

  private List<Memento> mementos = new ArrayList<>();
  private KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private DomainConfigurator domainConfigurator;
  private Map<String, DomainPresenceInfo> presenceInfoMap = new HashMap<>();
  private DomainProcessorImpl processor =
      new DomainProcessorImpl(DomainProcessorDelegateStub.createDelegate(testSupport));
  private Domain domain = DomainProcessorTestSetup.createTestDomain();

  private static WlsDomainConfig createDomainConfig() {
    WlsClusterConfig clusterConfig = new WlsClusterConfig(CLUSTER);
    for (String serverName : MANAGED_SERVER_NAMES) {
      clusterConfig.addServerConfig(new WlsServerConfig(serverName, "domain1-" + serverName, 8001));
    }
    return new WlsDomainConfig("base_domain")
        .withAdminServer(ADMIN_NAME, "domain1-admin-server", 7001)
        .withCluster(clusterConfig);
  }

  /**
   * Setup test environment.
   * @throws Exception if StaticStubSupport fails to install
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(TestUtils.silenceOperatorLogger());
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "DOMAINS", presenceInfoMap));
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(UnitTestHash.install());
    mementos.add(ScanCacheStub.install());

    domainConfigurator = DomainConfiguratorFactory.forDomain(domain);
    testSupport.defineResources(domain);
    new DomainProcessorTestSetup(testSupport).defineKubernetesResources(createDomainConfig());
    DomainProcessorTestSetup.defineRequiredResources(testSupport);
  }

  /**
   * Cleanup test environment.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
  }

  @Test
  public void whenDomainConfiguredForMaxServers_establishMatchingPresence() {
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MAX_SERVERS);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, false, false);

    assertServerPodAndServicePresent(info, ADMIN_NAME);
    for (String serverName : MANAGED_SERVER_NAMES) {
      assertServerPodAndServicePresent(info, serverName);
    }

    assertThat(info.getClusterService(CLUSTER), notNullValue());
  }

  @Test
  public void whenDomainScaledDown_removeExcessPodsAndServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS);
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, false, false);

    assertThat((int) getServerServices().count(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  public void whenDomainScaledDown_withPreCreateServerService_doesNotRemoveServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    domainConfigurator.configureCluster(CLUSTER).withReplicas(MIN_REPLICAS).withPrecreateServerService(true);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, false, false);

    assertThat((int) getServerServices().count(), equalTo(MAX_SERVERS + NUM_ADMIN_SERVERS));
    assertThat(getRunningPods().size(), equalTo(MIN_REPLICAS + NUM_ADMIN_SERVERS + NUM_JOB_PODS));
  }

  @Test
  public void whenDomainShutDown_removeAllPodsAndServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, true, true);

    assertThat(getRunningServices(), empty());
    assertThat(getRunningPods(), empty());
  }

  @Test
  public void whenDomainShutDown_ignoreNonOperatorServices() {
    defineServerResources(ADMIN_NAME);
    Arrays.stream(MANAGED_SERVER_NAMES).forEach(this::defineServerResources);
    testSupport.defineResources(createNonOperatorService());

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, true, true);

    assertThat(getRunningServices(), contains(createNonOperatorService()));
    assertThat(getRunningPods(), empty());
  }

  private V1Service createNonOperatorService() {
    return new V1Service()
        .metadata(
            new V1ObjectMeta()
                .name("do-not-delete-service")
                .namespace(DomainProcessorTestSetup.NS)
                .putLabelsItem("serviceType", "SERVER")
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "false")
                .putLabelsItem(DOMAINNAME_LABEL, DomainProcessorTestSetup.UID)
                .putLabelsItem(DOMAINUID_LABEL, DomainProcessorTestSetup.UID)
                .putLabelsItem(RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION)
                .putLabelsItem(SERVERNAME_LABEL, ADMIN_NAME))
        .spec(new V1ServiceSpec().type(ServiceHelper.CLUSTER_IP_TYPE));
  }

  @Test
  public void onUpgradeFromV20_updateExternalService() {
    domainConfigurator.configureAdminServer().configureAdminService().withChannel("name", 30701);
    testSupport.defineResources(createV20ExternalService());
    domainConfigurator.configureCluster(CLUSTER).withReplicas(MAX_SERVERS);

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, false, false);

    assertThat(info.getExternalService(ADMIN_NAME), notNullValue());
  }

  // todo after external service created, if adminService deleted, delete service

  // problem - ServiceType doesn't know what this is, so does not
  // add it to DomainPresenceInfo, so does not delete it!
  private V1Service createV20ExternalService() {
    return new V1Service()
        .metadata(
            new V1ObjectMeta()
                .name(LegalNames.toExternalServiceName(DomainProcessorTestSetup.UID, ADMIN_NAME))
                .namespace(DomainProcessorTestSetup.NS)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true")
                .putLabelsItem(DOMAINNAME_LABEL, DomainProcessorTestSetup.UID)
                .putLabelsItem(DOMAINUID_LABEL, DomainProcessorTestSetup.UID)
                .putLabelsItem(RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION)
                .putLabelsItem(SERVERNAME_LABEL, ADMIN_NAME))
        .spec(
            new V1ServiceSpec()
                .type(ServiceHelper.NODE_PORT_TYPE)
                .addPortsItem(new V1ServicePort().nodePort(30701)));
  }

  private Stream<V1Service> getServerServices() {
    return getRunningServices().stream().filter(ServiceHelper::isServerService);
  }

  private List<V1Service> getRunningServices() {
    return testSupport.getResources(KubernetesTestSupport.SERVICE);
  }

  private List<V1Pod> getRunningPods() {
    return testSupport.getResources(KubernetesTestSupport.POD);
  }

  private void defineServerResources(String serverName) {
    testSupport.defineResources(createServerPod(serverName), createServerService(serverName));
  }

  private V1Pod createServerPod(String serverName) {
    return AnnotationHelper.withSha256Hash(
        new V1Pod()
            .metadata(
                withServerLabels(
                    new V1ObjectMeta()
                        .name(LegalNames.toPodName(DomainProcessorTestSetup.UID, serverName))
                        .namespace(DomainProcessorTestSetup.NS),
                    serverName))
            .spec(new V1PodSpec()));
  }

  private V1ObjectMeta withServerLabels(V1ObjectMeta meta, String serverName) {
    return KubernetesUtils.withOperatorLabels(DomainProcessorTestSetup.UID, meta)
        .putLabelsItem(SERVERNAME_LABEL, serverName);
  }

  private V1Service createServerService(String serverName) {
    return AnnotationHelper.withSha256Hash(
        new V1Service()
            .metadata(
                withServerLabels(
                    new V1ObjectMeta()
                        .name(
                            LegalNames.toServerServiceName(
                                DomainProcessorTestSetup.UID, serverName))
                        .namespace(DomainProcessorTestSetup.NS),
                    serverName)));
  }

  private void assertServerPodAndServicePresent(DomainPresenceInfo info, String serverName) {
    assertThat(serverName + " server service", info.getServerService(serverName), notNullValue());
    assertThat(serverName + " pod", info.getServerPod(serverName), notNullValue());
  }

  @Test
  public void whenDomainIsNotValid_dontBringUpServers() {
    defineDuplicateServerNames();

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, false, false);

    assertServerPodAndServiceNotPresent(info, ADMIN_NAME);
    for (String serverName : MANAGED_SERVER_NAMES) {
      assertServerPodAndServiceNotPresent(info, serverName);
    }
  }

  private void assertServerPodAndServiceNotPresent(DomainPresenceInfo info, String serverName) {
    assertThat(serverName + " server service", info.getServerService(serverName), nullValue());
    assertThat(serverName + " pod", info.getServerPod(serverName), nullValue());
  }

  @Test
  public void whenDomainIsNotValid_updateStatus() {
    defineDuplicateServerNames();

    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    processor.makeRightDomainPresence(info, true, false, false);

    Domain updatedDomain = testSupport.getResourceWithName(DOMAIN, UID);
    assertThat(getStatusReason(updatedDomain), equalTo("ErrBadDomain"));
    assertThat(getStatusMessage(updatedDomain), stringContainsInOrder("managedServers", "ms1"));
  }

  private String getStatusReason(Domain updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(Domain::getStatus).map(DomainStatus::getReason).orElse(null);
  }

  private String getStatusMessage(Domain updatedDomain) {
    return Optional.ofNullable(updatedDomain).map(Domain::getStatus).map(DomainStatus::getMessage).orElse(null);
  }

  private void defineDuplicateServerNames() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
  }
}

// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_REPLACED;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1Status;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.AdminServerConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({"SameParameterValue"})
public class ServiceHelperTest {

  private static final String NS = "namespace";
  private static final String TEST_CLUSTER = "cluster-1";
  private static final int TEST_NODE_PORT = 7002;
  private static final int BAD_NODE_PORT = 9900;
  private static final int TEST_PORT = 7000;
  private static final int BAD_PORT = 9999;
  private static final String DOMAIN_NAME = "domain1";
  private static final String TEST_SERVER_NAME = "server1";
  private static final String SERVICE_NAME = "service1";
  private static final String UID = "uid1";
  private static final String BAD_VERSION = "bad-version";
  private static final String UNREADY_ENDPOINTS_ANNOTATION =
      "service.alpha.kubernetes.io/tolerate-unready-endpoints";
  private static final String ADMIN_SERVER = "ADMIN_SERVER";
  private static final String[] MESSAGE_KEYS = {
    CLUSTER_SERVICE_CREATED,
    CLUSTER_SERVICE_EXISTS,
    CLUSTER_SERVICE_REPLACED,
    ADMIN_SERVICE_CREATED,
    MANAGED_SERVICE_CREATED,
    ADMIN_SERVICE_EXISTS,
    MANAGED_SERVICE_EXISTS,
    ADMIN_SERVICE_REPLACED,
    MANAGED_SERVICE_REPLACED
  };

  private DomainPresenceInfo domainPresenceInfo = createPresenceInfo();
  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private final TerminalStep terminalStep = new TerminalStep();
  private RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private List<LogRecord> logRecords = new ArrayList<>();
  private WlsDomainConfig domainConfig;

  public ServiceHelperTest() {}

  @Before
  public void setUp() throws Exception {
    configureAdminServer().configureAdminService().withChannel("default", TEST_NODE_PORT);
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, MESSAGE_KEYS)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport.addWlsServer(ADMIN_SERVER, TEST_PORT);
    configSupport.addWlsServer(TEST_SERVER_NAME, TEST_PORT);
    configSupport.addWlsCluster(TEST_CLUSTER, TEST_SERVER_NAME);
    configSupport.setAdminServerName(ADMIN_SERVER);

    domainConfig = configSupport.createDomainConfig();
    testSupport
        .addToPacket(CLUSTER_NAME, TEST_CLUSTER)
        .addToPacket(SERVER_NAME, TEST_SERVER_NAME)
        .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainConfig)
        .addToPacket(SERVER_SCAN, domainConfig.getServerConfig(TEST_SERVER_NAME))
        .addDomainPresenceInfo(domainPresenceInfo);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  private DomainPresenceInfo createPresenceInfo() {
    return new DomainPresenceInfo(
        new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(createDomainSpec()));
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUID(UID);
  }

  private AdminServerConfigurator configureAdminServer() {
    return configureDomain().configureAdminServer();
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  // ------ service deletion --------

  @Test
  public void afterDeleteServiceStepRun_removeServiceFromSko() {
    expectDeleteServiceCall().returning(new V1Status());
    ServerKubernetesObjects sko = createSko(createMinimalService());

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, terminalStep));

    assertThat(sko.getService().get(), nullValue());
  }

  private CallTestSupport.CannedResponse expectDeleteServiceCall() {
    return testSupport
        .createCannedResponse("deleteService")
        .withName(SERVICE_NAME)
        .withNamespace(NS)
        .withBody(new V1DeleteOptions());
  }

  private V1Service createMinimalService() {
    return new V1Service().metadata(new V1ObjectMeta().name(SERVICE_NAME));
  }

  private ServerKubernetesObjects createSko(V1Service service) {
    ServerKubernetesObjects sko = new ServerKubernetesObjects();
    sko.getService().set(service);
    return sko;
  }

  @Test
  public void whenServiceNotFound_removeServiceFromSko() {
    expectDeleteServiceCall().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    ServerKubernetesObjects sko = createSko(createMinimalService());

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, terminalStep));

    assertThat(sko.getService().get(), nullValue());
  }

  @Test
  public void whenDeleteFails_reportCompletionFailure() {
    expectDeleteServiceCall().failingWithStatus(HTTP_BAD_REQUEST);
    ServerKubernetesObjects sko = createSko(createMinimalService());

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenDeleteServiceStepRunWithNoService_doNotSendDeleteCall() {
    ServerKubernetesObjects sko = createSko(null);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, terminalStep));

    assertThat(sko.getService().get(), nullValue());
  }

  @Test
  public void afterDeleteServiceStepRun_runSpecifiedNextStep() {
    ServerKubernetesObjects sko = createSko(null);

    testSupport.runSteps(ServiceHelper.deleteServicesStep(sko, terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }

  // ------ cluster service creation --------

  @Test
  public void onClusterStepRunWithNoService_createIt() {
    initializeClusterServiceFromRecord(null);
    expectSuccessfulCreateClusterService();

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, createClusterService()));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_CREATED));
  }

  @Test
  public void onClusterStepRunWithNoService_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeClusterServiceFromRecord(null);
    expectCreateClusterService().failingWithStatus(401);

    Step forClusterStep = ServiceHelper.createForClusterStep(terminalStep);
    testSupport.runSteps(forClusterStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void onClusterStepRunWithMatchingService_addToDomainPresenceInfo() {
    V1Service service =
        new V1Service()
            .spec(createClusterServiceSpec())
            .metadata(
                new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION));
    initializeClusterServiceFromRecord(service);

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, service));
    assertThat(logRecords, containsFine(CLUSTER_SERVICE_EXISTS));
  }

  @Test
  public void onClusterStepRunWithMatchingServiceWithoutSpecType_addToDomainPresenceInfo() {
    V1Service service =
        new V1Service()
            .spec(createUntypedClusterServiceSpec())
            .metadata(
                new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION));
    initializeClusterServiceFromRecord(service);

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, service));
    assertThat(logRecords, containsFine(CLUSTER_SERVICE_EXISTS));
  }

  @Test
  public void onClusterStepRunWithServiceWithBadVersion_replaceIt() {
    verifyClusterServiceReplaced(this::withBadVersion);
  }

  @Test
  public void onClusterStepRunWithServiceWithBadSpecType_replaceIt() {
    verifyClusterServiceReplaced(this::withBadSpecType);
  }

  @Test
  public void onClusterStepRunWithServiceWithBadPort_replaceIt() {
    verifyClusterServiceReplaced(this::withBadPort);
  }

  @Test
  public void onClusterStepRunWithServiceWithLabelAdded_replaceIt() {
    configureClusterWithLabel("anyLabel", "anyValue");
    verifyClusterServiceReplaced(createClusterService(), withLabel(createClusterService()));
  }

  @Test
  public void onClusterStepRunWithServiceWithLabelValueChanged_replaceIt() {
    final String newLabelValue = "newValue";
    configureClusterWithLabel("anyLabel", newLabelValue);
    verifyClusterServiceReplaced(
        withLabel(createClusterService()), withLabel(createClusterService(), newLabelValue));
  }

  @Test
  public void onClusterStepRunWithServiceWithLabelRemoved_replaceIt() {
    verifyClusterServiceReplaced(this::withLabel);
  }

  @Test
  public void onClusterStepRunWithServiceWithAnnotationAdded_replaceIt() {
    configureClusterWithAnnotation("anyAnnotation", "anyValue");
    verifyClusterServiceReplaced(createClusterService(), withAnnotation(createClusterService()));
  }

  @Test
  public void onClusterStepRunWithServiceWithAnnotationValueChanged_replaceIt() {
    final String newAnnotationValue = "newValue";
    configureClusterWithAnnotation("anyAnnotation", newAnnotationValue);
    verifyClusterServiceReplaced(
        withAnnotation(createClusterService()),
        withAnnotation(createClusterService(), newAnnotationValue));
  }

  @Test
  public void onClusterStepRunWithServiceWithAnnotationRemoved_replaceIt() {
    verifyClusterServiceReplaced(this::withAnnotation);
  }

  @Test
  public void whenAttemptToReplaceBadClusterServiceFailsOnDelete_reportCompletionFailure() {
    V1Service existingService = createClusterServiceWithBadPort();
    initializeClusterServiceFromRecord(existingService);
    expectDeleteService(getClusterServiceName()).failingWithStatus(HTTP_BAD_REQUEST);

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenAttemptToReplaceBadClusterServiceFindsServiceMissing_replaceItAnyway() {
    initializeClusterServiceFromRecord(createClusterServiceWithBadPort());
    expectDeleteService(getClusterServiceName()).failingWithStatus(HTTP_NOT_FOUND);
    expectSuccessfulCreateClusterService();
    V1Service expectedService = createClusterService();

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, expectedService));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_REPLACED));
  }

  private CallTestSupport.CannedResponse expectReadClusterService() {
    return expectReadService(getClusterServiceName());
  }

  private void initializeClusterServiceFromRecord(V1Service service) {
    if (service == null) {
      domainPresenceInfo.getClusters().remove(TEST_CLUSTER);
    } else {
      domainPresenceInfo.getClusters().put(TEST_CLUSTER, service);
    }
  }

  private String getClusterServiceName() {
    return LegalNames.toClusterServiceName(UID, TEST_CLUSTER);
  }

  private V1ServiceSpec createClusterServiceSpec() {
    return createUntypedClusterServiceSpec().type("ClusterIP");
  }

  private V1ServiceSpec createUntypedClusterServiceSpec() {
    return new V1ServiceSpec()
        .putSelectorItem(DOMAINUID_LABEL, UID)
        .putSelectorItem(CLUSTERNAME_LABEL, TEST_CLUSTER)
        .putSelectorItem(CREATEDBYOPERATOR_LABEL, "true")
        .ports(
            Collections.singletonList(
                new V1ServicePort().port(TEST_PORT).name("default").protocol("TCP")));
  }

  private void expectSuccessfulCreateClusterService() {
    expectCreateClusterService().returning(createClusterService());
  }

  private CallTestSupport.CannedResponse expectCreateClusterService() {
    return expectCreateService(createClusterService());
  }

  private V1Service createClusterService() {
    return new V1Service()
        .spec(createClusterServiceSpec())
        .metadata(
            new V1ObjectMeta()
                .name(getClusterServiceName())
                .namespace(NS)
                .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(CLUSTERNAME_LABEL, TEST_CLUSTER)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"));
  }

  private void expectDeleteServiceSuccessful(String serviceName) {
    expectDeleteService(serviceName).returning(new V1Status());
  }

  private CallTestSupport.CannedResponse expectDeleteService(String serviceName) {
    return testSupport
        .createCannedResponse("deleteService")
        .withNamespace(NS)
        .withName(serviceName)
        .withBody(new V1DeleteOptions());
  }

  private V1Service createClusterServiceWithBadVersion() {
    return new V1Service()
        .spec(createClusterServiceSpec())
        .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, BAD_VERSION));
  }

  private V1Service createClusterServiceWithBadPort() {
    return new V1Service()
        .spec(createSpecWithBadPort())
        .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION));
  }

  private V1ServiceSpec createSpecWithBadPort() {
    return new V1ServiceSpec()
        .type("ClusterIP")
        .ports(Collections.singletonList(new V1ServicePort().port(BAD_PORT)));
  }

  // ------ per-server service creation --------

  @Test
  public void onServerStepRunWithNoService_createIt() {
    verifyMissingServerServiceCreated(createServerService());
  }

  private void verifyMissingServerServiceCreated(V1Service newService) {
    initializeServiceFromRecord(null);
    expectCreateService(newService).returning(newService);

    testSupport.runSteps(ServiceHelper.createForServerStep(terminalStep));

    assertThat(logRecords, containsInfo(MANAGED_SERVICE_CREATED));
  }

  @Test
  public void whenSupported_createServerServiceWithPublishNotReadyAddresses() {
    testSupport.addVersion(new KubernetesVersion(1, 8));

    verifyMissingServerServiceCreated(withPublishNotReadyAddresses(createServerService()));
  }

  private V1Service withPublishNotReadyAddresses(V1Service service) {
    service.getSpec().setPublishNotReadyAddresses(true);
    return service;
  }

  private V1Service withNodePort(V1Service service, int nodePort) {
    service.getSpec().type("NodePort").clusterIP(null);
    service
        .getSpec()
        .getPorts()
        .stream()
        .findFirst()
        .ifPresent(servicePort -> servicePort.setNodePort(nodePort));
    return service;
  }

  @Test
  public void onServerStepRunWithNoService_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeServiceFromRecord(null);
    expectCreateServerService().failingWithStatus(HTTP_BAD_REQUEST);

    Step forServerStep = ServiceHelper.createForServerStep(terminalStep);
    testSupport.runSteps(forServerStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void onServerStepRunWithMatchingService_addToSko() {
    initializeServiceFromRecord(createServerService());

    testSupport.runSteps(ServiceHelper.createForServerStep(terminalStep));

    assertThat(logRecords, containsFine(MANAGED_SERVICE_EXISTS));
  }

  @Test
  public void onServerStepRunWithServiceWithBadVersion_replaceIt() {
    verifyServerServiceReplaced(this::withBadVersion);
  }

  @Test
  public void onServerStepRunWithServiceWithLabelAdded_replaceIt() {
    configureManagedServerWithLabel("anyLabel", "anyValue");
    verifyServerServiceReplaced(createServerService(), withLabel(createServerService()));
  }

  @Test
  public void onServerStepRunWithServiceWithLabelValueChanged_replaceIt() {
    final String newLabelValue = "newValue";
    configureManagedServerWithLabel("anyLabel", newLabelValue);
    verifyServerServiceReplaced(
        withLabel(createServerService()), withLabel(createServerService(), newLabelValue));
  }

  @Test
  public void onServerStepRunWithServiceWithLabelRemoved_replaceIt() {
    verifyServerServiceReplaced(this::withLabel);
  }

  @Test
  public void onServerStepRunWithServiceWithAnnotationAdded_replaceIt() {
    configureManagedServerWithAnnotation("anyAnnotation", "anyValue");
    verifyServerServiceReplaced(createServerService(), withAnnotation(createServerService()));
  }

  @Test
  public void onServerStepRunWithServiceWithAnnotationValueChanged_replaceIt() {
    final String newAnnotationValue = "newValue";
    configureManagedServerWithAnnotation("anyAnnotation", newAnnotationValue);
    verifyServerServiceReplaced(
        withAnnotation(createServerService()),
        withAnnotation(createServerService(), newAnnotationValue));
  }

  @Test
  public void onServerStepRunWithServiceWithAnnotationRemoved_replaceIt() {
    verifyServerServiceReplaced(this::withAnnotation);
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithBadVersion_replaceIt() {
    verifyExternalServiceReplaced(this::withBadVersion);
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithBadPort_replaceIt() {
    verifyExternalServiceReplaced(this::withBadPort);
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithBadNodePort_replaceIt() {
    verifyExternalServiceReplaced(this::withBadNodePort);
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithLabelAdded_replaceIt() {
    configureAdminServerWithLabel("anyLabel", "anyValue");
    verifyExternalServiceReplaced(createAdminService(), withLabel(createAdminService()));
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithLabelValueChanged_replaceIt() {
    final String newLabelValue = "newValue";
    configureAdminServerWithLabel("anyLabel", newLabelValue);
    verifyExternalServiceReplaced(
        withLabel(createAdminService()), withLabel(createAdminService(), newLabelValue));
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithLabelRemoved_replaceIt() {
    verifyExternalServiceReplaced(this::withLabel);
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithAnnotationAdded_replaceIt() {
    configureAdminServerWithAnnotation("anyAnnotation", "anyValue");
    verifyExternalServiceReplaced(createAdminService(), withAnnotation(createAdminService()));
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithAnnotationValueChanged_replaceIt() {
    final String newAnnotationValue = "newValue";
    configureAdminServerWithAnnotation("anyAnnotation", newAnnotationValue);
    verifyExternalServiceReplaced(
        withAnnotation(createAdminService()),
        withAnnotation(createAdminService(), newAnnotationValue));
  }

  @Test
  public void onExternalServiceStepRunWithServiceWithAnnotationRemoved_replaceIt() {
    verifyExternalServiceReplaced(this::withAnnotation);
  }

  private void verifyServerServiceReplaced(V1Service oldService, V1Service newService) {
    initializeServiceFromRecord(oldService);
    expectDeleteServiceSuccessful(getServerServiceName());
    expectSuccessfulCreateService(newService);

    testSupport.runSteps(ServiceHelper.createForServerStep(terminalStep));

    assertThat(logRecords, containsInfo(MANAGED_SERVICE_REPLACED));
  }

  private void verifyExternalServiceReplaced(ServiceMutator mutator) {
    verifyExternalServiceReplaced(mutator.change(createAdminService()), createAdminService());
  }

  private void verifyExternalServiceReplaced(V1Service oldService, V1Service newService) {
    initializeAdminServiceFromRecord(oldService);
    expectDeleteServiceSuccessful(getAdminServiceName());
    expectSuccessfulCreateService(newService);

    testSupport.addToPacket(SERVER_SCAN, domainConfig.getServerConfig(ADMIN_SERVER));
    testSupport.addToPacket(SERVER_NAME, ADMIN_SERVER);
    testSupport.runSteps(ServiceHelper.createForExternalServiceStep(terminalStep));

    assertThat(logRecords, containsInfo(ADMIN_SERVICE_REPLACED));
  }

  private void verifyServerServiceReplaced(ServiceMutator mutator) {
    verifyServerServiceReplaced(mutator.change(createServerService()), createServerService());
  }

  private void verifyClusterServiceReplaced(ServiceMutator mutator) {
    verifyClusterServiceReplaced(mutator.change(createClusterService()), createClusterService());
  }

  private void verifyClusterServiceReplaced(V1Service oldService, V1Service newService) {
    initializeClusterServiceFromRecord(oldService);
    expectDeleteServiceSuccessful(getClusterServiceName());
    expectCreateService(newService).returning(newService);

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, newService));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_REPLACED));
  }

  private V1ServiceSpec createServerServiceSpec() {
    return createUntypedServerServiceSpec(TEST_SERVER_NAME).type("ClusterIP").clusterIP("None");
  }

  private V1ServiceSpec createUntypedServerServiceSpec(String serverName) {
    return new V1ServiceSpec()
        .putSelectorItem(DOMAINUID_LABEL, UID)
        .putSelectorItem(SERVERNAME_LABEL, serverName)
        .putSelectorItem(CREATEDBYOPERATOR_LABEL, "true")
        .ports(
            Collections.singletonList(
                new V1ServicePort().port(TEST_PORT).name("default").protocol("TCP")));
  }

  private void initializeServiceFromRecord(V1Service service) {
    domainPresenceInfo.getServers().put(TEST_SERVER_NAME, createSko(service));
  }

  private void initializeAdminServiceFromRecord(V1Service service) {
    domainPresenceInfo.getServers().put(ADMIN_SERVER, createSko(service));
  }

  private String getServerServiceName() {
    return LegalNames.toServerServiceName(UID, TEST_SERVER_NAME);
  }

  private String getAdminServiceName() {
    return LegalNames.toExternalServiceName(UID, ADMIN_SERVER);
  }

  private void expectSuccessfulCreateService(V1Service service) {
    expectCreateService(service).returning(service);
  }

  private CallTestSupport.CannedResponse expectCreateServerService() {
    return expectCreateService(createServerService());
  }

  private CallTestSupport.CannedResponse expectCreateService(V1Service service) {
    return testSupport.createCannedResponse("createService").withNamespace(NS).withBody(service);
  }

  private V1Service createServerService() {
    return createServerService(createServerServiceSpec());
  }

  private V1Service createServerService(V1ServiceSpec serviceSpec) {
    return new V1Service()
        .spec(serviceSpec)
        .metadata(
            new V1ObjectMeta()
                .name(getServerServiceName())
                .namespace(NS)
                .putAnnotationsItem(UNREADY_ENDPOINTS_ANNOTATION, "true")
                .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V2)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(CLUSTERNAME_LABEL, TEST_CLUSTER)
                .putLabelsItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"));
  }

  private V1Service createAdminService() {
    return createAdminService(createAdminServiceSpec());
  }

  private V1Service createAdminService(V1ServiceSpec serviceSpec) {
    return new V1Service()
        .spec(serviceSpec)
        .metadata(
            new V1ObjectMeta()
                .name(getAdminServiceName())
                .namespace(NS)
                .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V2)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(SERVERNAME_LABEL, ADMIN_SERVER)
                .putLabelsItem(CLUSTERNAME_LABEL, TEST_CLUSTER)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"));
  }

  private V1ServiceSpec createAdminServiceSpec() {
    final V1ServiceSpec serviceSpec = createUntypedServerServiceSpec(ADMIN_SERVER).type("NodePort");

    serviceSpec.getPorts().stream().findAny().ifPresent(port -> port.setNodePort(TEST_NODE_PORT));

    return serviceSpec;
  }

  interface ServiceMutator {
    V1Service change(V1Service original);
  }

  private V1Service withBadVersion(V1Service service) {
    service.getMetadata().putLabelsItem(RESOURCE_VERSION_LABEL, BAD_VERSION);
    return service;
  }

  private V1Service withBadSpecType(V1Service service) {
    service.getSpec().type("BadType");
    return service;
  }

  private V1Service withBadPort(V1Service service) {
    List<V1ServicePort> ports = service.getSpec().getPorts();
    assertThat(ports.size(), is(1));

    ports.stream().findAny().get().setPort(BAD_PORT);
    return service;
  }

  private V1Service withBadNodePort(V1Service service) {
    List<V1ServicePort> ports = service.getSpec().getPorts();
    assertThat(ports.size(), is(1));

    ports.stream().findAny().get().setNodePort(BAD_NODE_PORT);
    return service;
  }

  private V1Service withLabel(V1Service service) {
    return withLabel(service, "anyValue");
  }

  private V1Service withLabel(V1Service service, String labelValue) {
    final String labelName = "anyLabel";

    assertThat(service.getMetadata().getLabels(), not(hasKey(labelName)));
    service.getMetadata().putLabelsItem(labelName, labelValue);
    assertThat(service.getMetadata().getLabels(), hasKey(labelName));

    return service;
  }

  private V1Service withAnnotation(V1Service service) {
    return withAnnotation(service, "anyValue");
  }

  private V1Service withAnnotation(V1Service service, String value) {
    final String annotationName = "anyAnnotation";

    assertThat(service.getMetadata().getAnnotations(), not(hasKey(annotationName)));
    service.getMetadata().putAnnotationsItem(annotationName, value);
    assertThat(service.getMetadata().getAnnotations(), hasKey(annotationName));

    return service;
  }

  private void configureClusterWithLabel(String label, String value) {
    configureDomain().configureCluster(TEST_CLUSTER).withServiceLabel(label, value);
  }

  private void configureClusterWithAnnotation(String annotation, String value) {
    configureDomain().configureCluster(TEST_CLUSTER).withServiceAnnotation(annotation, value);
  }

  private void configureManagedServerWithLabel(String label, String value) {
    configureDomain().configureServer(TEST_SERVER_NAME).withServiceLabel(label, value);
  }

  private void configureManagedServerWithAnnotation(String annotation, String value) {
    configureDomain().configureServer(TEST_SERVER_NAME).withServiceAnnotation(annotation, value);
  }

  private void configureAdminServerWithLabel(String label, String value) {
    configureDomain().configureAdminServer().withServiceLabel(label, value);
  }

  private void configureAdminServerWithAnnotation(String annotation, String value) {
    configureDomain().configureAdminServer().withServiceAnnotation(annotation, value);
  }

  private CallTestSupport.CannedResponse expectReadService(String serviceName) {
    return testSupport.createCannedResponse("readService").withNamespace(NS).withName(serviceName);
  }
}

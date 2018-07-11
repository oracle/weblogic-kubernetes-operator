// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.LabelConstants.CHANNELNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAINUID_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.CLUSTER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.NETWORK_ACCESS_POINT;
import static oracle.kubernetes.operator.ProcessingConstants.NODE_PORT;
import static oracle.kubernetes.operator.ProcessingConstants.PORT;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_NAME;
import static oracle.kubernetes.operator.VersionConstants.DOMAIN_V1;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CLUSTER_SERVICE_REPLACED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_REPLACED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
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
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.work.AsyncCallTestSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SuppressWarnings({"unchecked", "SameParameterValue"})
public class ServiceHelperTest {

  private static final String NS = "namespace";
  private static final String TEST_CLUSTER = "cluster-1";
  private static final int TEST_PORT = 7000;
  private static final int BAD_PORT = 9999;
  private static final String DOMAIN_NAME = "domain1";
  private static final String TEST_SERVER_NAME = "server1";
  private static final String SERVICE_NAME = "service1";
  private static final String UID = "uid1";
  private static final String BAD_VERSION = "bad-version";
  private static final String UNREADY_ENDPOINTS_ANNOTATION =
      "service.alpha.kubernetes.io/tolerate-unready-endpoints";
  private static final int TEST_NODE_PORT = 1234;
  private static final String NAP_NAME = "test-nap";
  private static final String PROTOCOL = "http";
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
  private NetworkAccessPoint networkAccessPoint =
      new NetworkAccessPoint(NAP_NAME, PROTOCOL, TEST_PORT, TEST_NODE_PORT);
  private List<LogRecord> logRecords = new ArrayList<>();

  public ServiceHelperTest() {}

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, MESSAGE_KEYS)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());

    testSupport
        .addToPacket(CLUSTER_NAME, TEST_CLUSTER)
        .addToPacket(SERVER_NAME, TEST_SERVER_NAME)
        .addToPacket(PORT, TEST_PORT)
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
    return new DomainSpec().withDomainName(DOMAIN_NAME).withDomainUID(UID).withAsName(ADMIN_SERVER);
  }

  // ------ service deletion --------

  @Test
  public void afterDeleteServiceStepRun_removeServiceFromSko() {
    expectDeleteServiceCall().returning(new V1Status());
    ServerKubernetesObjects sko = createSko(createMinimalService());

    testSupport.runSteps(ServiceHelper.deleteServiceStep(sko, terminalStep));

    assertThat(sko.getService().get(), nullValue());
  }

  private AsyncCallTestSupport.CannedResponse expectDeleteServiceCall() {
    return testSupport
        .createCannedResponse("deleteService")
        .withName(SERVICE_NAME)
        .withNamespace(NS);
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

    testSupport.runSteps(ServiceHelper.deleteServiceStep(sko, terminalStep));

    assertThat(sko.getService().get(), nullValue());
  }

  @Test
  public void whenDeleteFails_reportCompletionFailure() {
    expectDeleteServiceCall().failingWithStatus(HTTP_BAD_REQUEST);
    ServerKubernetesObjects sko = createSko(createMinimalService());

    testSupport.runSteps(ServiceHelper.deleteServiceStep(sko, terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenDeleteServiceStepRunWithNoService_doNotSendDeleteCall() {
    ServerKubernetesObjects sko = createSko(null);

    testSupport.runSteps(ServiceHelper.deleteServiceStep(sko, terminalStep));

    assertThat(sko.getService().get(), nullValue());
  }

  @Test
  public void afterDeleteServiceStepRun_runSpecifiedNextStep() {
    ServerKubernetesObjects sko = createSko(null);

    testSupport.runSteps(ServiceHelper.deleteServiceStep(sko, terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }

  // ------ cluster service creation --------

  @Test
  public void onClusterStepRunWithNoService_createIt() {
    expectReadClusterService().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectSuccessfulCreateClusterService();

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, createClusterService()));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_CREATED));
  }

  @Test
  public void whenReadClusterServiceFails_reportThrowable() {
    expectReadClusterService().failingWithStatus(HTTP_BAD_REQUEST);

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void onClusterStepRunWithNoService_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadClusterService().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateClusterService().failingWithStatus(401);

    Step forClusterStep = ServiceHelper.createForClusterStep(terminalStep);
    testSupport.runSteps(forClusterStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(forClusterStep));
  }

  @Test
  public void onClusterStepRunWithMatchingService_addToDomainPresenceInfo() {
    V1Service service =
        new V1Service()
            .spec(createClusterServiceSpec())
            .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1));
    expectReadClusterService().returning(service);

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, service));
    assertThat(logRecords, containsFine(CLUSTER_SERVICE_EXISTS));
  }

  @Test
  public void onClusterStepRunWithMatchingServiceWithoutSpecType_addToDomainPresenceInfo() {
    V1Service service =
        new V1Service()
            .spec(createUntypedClusterServiceSpec())
            .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1));
    expectReadClusterService().returning(service);

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, service));
    assertThat(logRecords, containsFine(CLUSTER_SERVICE_EXISTS));
  }

  @Test
  public void onClusterStepRunWithServiceWithBadVersion_replaceIt() {
    expectReadClusterService().returning(createClusterServiceWithBadVersion());
    expectDeleteServiceSuccessful(getClusterServiceName());
    expectSuccessfulCreateClusterService();

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, createClusterService()));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_REPLACED));
  }

  @Test
  public void onClusterStepRunWithServiceWithBadSpecType_replaceIt() {
    expectReadClusterService().returning(createClusterServiceWithBadSpecType());
    expectDeleteServiceSuccessful(getClusterServiceName());
    expectSuccessfulCreateClusterService();

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, createClusterService()));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_REPLACED));
  }

  @Test
  public void onClusterStepRunWithServiceWithBadPort_replaceIt() {
    expectReadClusterService().returning(createClusterServiceWithBadPort());
    expectDeleteServiceSuccessful(getClusterServiceName());
    expectSuccessfulCreateClusterService();

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, createClusterService()));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_REPLACED));
  }

  @Test
  public void whenAttemptToReplaceBadClusterServiceFailsOnDelete_reportCompletionFailure() {
    expectReadClusterService().returning(createClusterServiceWithBadPort());
    expectDeleteService(getClusterServiceName()).failingWithStatus(HTTP_BAD_REQUEST);

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenAttemptToReplaceBadClusterServiceFindsServiceMissing_replaceItAnyway() {
    expectReadClusterService().returning(createClusterServiceWithBadPort());
    expectDeleteService(getClusterServiceName()).failingWithStatus(HTTP_NOT_FOUND);
    expectSuccessfulCreateClusterService();

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, createClusterService()));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_REPLACED));
  }

  private AsyncCallTestSupport.CannedResponse expectReadClusterService() {
    return expectReadService(getClusterServiceName());
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
        .ports(Collections.singletonList(new V1ServicePort().port(TEST_PORT)));
  }

  private void expectSuccessfulCreateClusterService() {
    expectCreateClusterService().returning(createClusterService());
  }

  private AsyncCallTestSupport.CannedResponse expectCreateClusterService() {
    return expectCreateService(createClusterService());
  }

  private V1Service createClusterService() {
    return new V1Service()
        .spec(createClusterServiceSpec())
        .metadata(
            new V1ObjectMeta()
                .name(getClusterServiceName())
                .namespace(NS)
                .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(CLUSTERNAME_LABEL, TEST_CLUSTER)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"));
  }

  private void expectDeleteServiceSuccessful(String serviceName) {
    expectDeleteService(serviceName).returning(new V1Status());
  }

  private AsyncCallTestSupport.CannedResponse expectDeleteService(String serviceName) {
    return testSupport
        .createCannedResponse("deleteService")
        .withNamespace(NS)
        .withName(serviceName);
  }

  private V1Service createClusterServiceWithBadVersion() {
    return new V1Service()
        .spec(createClusterServiceSpec())
        .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, BAD_VERSION));
  }

  private V1Service createClusterServiceWithBadSpecType() {
    return new V1Service()
        .spec(new V1ServiceSpec().type("BadType"))
        .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1));
  }

  private V1Service createClusterServiceWithBadPort() {
    return new V1Service()
        .spec(createSpecWithBadPort())
        .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1));
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
    expectReadServerService().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateService(newService).returning(newService);

    testSupport.runSteps(ServiceHelper.createForServerStep(terminalStep));

    assertThat(getServerKubernetesObjects().getService().get(), equalTo(newService));
    assertThat(logRecords, containsInfo(MANAGED_SERVICE_CREATED));
  }

  @Test
  public void whenSupported_createServerServiceWithPublishNotReadyAddresses() {
    testSupport.addVersion(new HealthCheckHelper.KubernetesVersion(1, 8));

    verifyMissingServerServiceCreated(withPublishNotReadyAddresses(createServerService()));
  }

  private V1Service withPublishNotReadyAddresses(V1Service service) {
    service.getSpec().setPublishNotReadyAddresses(true);
    return service;
  }

  @Test
  public void whenNodePortSpecified_createServerServiceWithNodePort() {
    testSupport.addToPacket(NODE_PORT, TEST_NODE_PORT);

    verifyMissingServerServiceCreated(withNodePort(createServerService(), TEST_NODE_PORT));
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
  public void whenReadServerServiceFails_reportThrowable() {
    expectReadServerService().failingWithStatus(HTTP_BAD_REQUEST);

    testSupport.runSteps(ServiceHelper.createForServerStep(terminalStep));

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void onServerStepRunWithNoService_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadServerService().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateServerService().failingWithStatus(HTTP_BAD_REQUEST);

    Step forServerStep = ServiceHelper.createForServerStep(terminalStep);
    testSupport.runSteps(forServerStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(forServerStep));
  }

  @Test
  public void onServerStepRunWithMatchingService_addToSko() {
    V1Service service =
        new V1Service()
            .spec(createServerServiceSpec())
            .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DOMAIN_V1));
    expectReadServerService().returning(service);

    testSupport.runSteps(ServiceHelper.createForServerStep(terminalStep));

    assertThat(getServerKubernetesObjects().getService().get(), equalTo(service));
    assertThat(logRecords, containsFine(MANAGED_SERVICE_EXISTS));
  }

  @Test
  public void onServerStepRunWithServiceWithBadVersion_replaceIt() {
    verifyServerServiceReplaced(this::withBadVersion);
  }

  private void verifyServerServiceReplaced(V1Service oldService, V1Service newService) {
    expectReadServerService().returning(oldService);
    expectDeleteServiceSuccessful(getServerServiceName());
    expectSuccessfulCreateService(newService);

    testSupport.runSteps(ServiceHelper.createForServerStep(terminalStep));

    assertThat(getServerKubernetesObjects().getService().get(), equalTo(newService));
    assertThat(logRecords, containsInfo(MANAGED_SERVICE_REPLACED));
  }

  private void verifyServerServiceReplaced(ServiceMutator mutator) {
    verifyServerServiceReplaced(mutator.change(createServerService()), createServerService());
  }

  @Test
  public void onServerStepRunWithServiceWithoutNodePort_replaceIt() {
    testSupport.addToPacket(NODE_PORT, TEST_NODE_PORT);

    verifyServerServiceReplaced(
        createServerService(), withNodePort(createServerService(), TEST_NODE_PORT));
  }

  @Test
  public void onServerStepRunWithServiceWithWrongNodePort_replaceIt() {
    testSupport.addToPacket(NODE_PORT, TEST_NODE_PORT);

    verifyServerServiceReplaced(
        withNodePort(createServerService(), BAD_PORT),
        withNodePort(createServerService(), TEST_NODE_PORT));
  }

  private ServerKubernetesObjects getServerKubernetesObjects() {
    return ServerKubernetesObjectsManager.getOrCreate(domainPresenceInfo, TEST_SERVER_NAME);
  }

  private V1ServiceSpec createServerServiceSpec() {
    return createUntypedServerServiceSpec().type("ClusterIP").clusterIP("None");
  }

  private V1ServiceSpec createUntypedServerServiceSpec() {
    return new V1ServiceSpec()
        .putSelectorItem(DOMAINUID_LABEL, UID)
        .putSelectorItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
        .putSelectorItem(CREATEDBYOPERATOR_LABEL, "true")
        .ports(Collections.singletonList(new V1ServicePort().port(TEST_PORT)));
  }

  private AsyncCallTestSupport.CannedResponse expectReadServerService() {
    return expectReadService(getServerServiceName());
  }

  private String getServerServiceName() {
    return LegalNames.toServerServiceName(UID, TEST_SERVER_NAME);
  }

  private void expectSuccessfulCreateService(V1Service service) {
    expectCreateService(service).returning(service);
  }

  private AsyncCallTestSupport.CannedResponse expectCreateServerService() {
    return expectCreateService(createServerService());
  }

  private AsyncCallTestSupport.CannedResponse expectCreateService(V1Service service) {
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
                .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"));
  }

  // ------ external channel service creation --------

  @Test
  public void onExternalChannelStepRunWithNoService_createIt() {
    verifyMissingExternalChannelServiceCreated(createExternalChannelService());
  }

  private void verifyMissingExternalChannelServiceCreated(V1Service newService) {
    expectReadExternalChannelService().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateService(newService).returning(newService);

    testSupport.runSteps(ServiceHelper.createForExternalChannelStep(terminalStep));

    assertThat(getServerKubernetesObjects().getChannels(), hasEntry(NAP_NAME, newService));
    assertThat(logRecords, containsInfo(MANAGED_SERVICE_CREATED));
  }

  @Test
  public void onExternalChannelStepRunWithMatchingService_addToSko() {
    V1Service service = createExternalChannelService();
    expectReadExternalChannelService().returning(service);

    testSupport.runSteps(ServiceHelper.createForExternalChannelStep(terminalStep));

    assertThat(getServerKubernetesObjects().getChannels(), hasEntry(NAP_NAME, service));
    assertThat(logRecords, containsFine(MANAGED_SERVICE_EXISTS));
  }

  @Test
  public void onExternalChannelStepRunWithServiceWithBadVersion_replaceIt() {
    verifyExternalChannelServiceReplaced(this::withBadVersion);
  }

  interface ServiceMutator {
    V1Service change(V1Service original);
  }

  private void verifyExternalChannelServiceReplaced(ServiceMutator mutator) {
    V1Service newService = createExternalChannelService();
    expectReadExternalChannelService().returning(mutator.change(createExternalChannelService()));
    expectDeleteServiceSuccessful(getExternalChannelServiceName());
    expectSuccessfulCreateService(newService);

    testSupport.runSteps(ServiceHelper.createForExternalChannelStep(terminalStep));

    assertThat(getServerKubernetesObjects().getChannels(), hasEntry(NAP_NAME, newService));
    assertThat(logRecords, containsInfo(MANAGED_SERVICE_REPLACED));
  }

  private V1Service withBadVersion(V1Service service) {
    service.getMetadata().putLabelsItem(RESOURCE_VERSION_LABEL, BAD_VERSION);
    return service;
  }

  private AsyncCallTestSupport.CannedResponse expectReadExternalChannelService() {
    testSupport.addToPacket(NETWORK_ACCESS_POINT, networkAccessPoint);
    return expectReadService(getExternalChannelServiceName());
  }

  private V1Service createExternalChannelService() {
    return createExternalChannelService(createExternalChannelServiceSpec());
  }

  private V1Service createExternalChannelService(V1ServiceSpec serviceSpec) {
    return new V1Service()
        .spec(serviceSpec)
        .metadata(
            new V1ObjectMeta()
                .name(getExternalChannelServiceName())
                .namespace(NS)
                .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
                .putLabelsItem(CHANNELNAME_LABEL, NAP_NAME)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"));
  }

  private String getExternalChannelServiceName() {
    return LegalNames.toNAPName(UID, TEST_SERVER_NAME, networkAccessPoint);
  }

  private V1ServiceSpec createExternalChannelServiceSpec() {
    return new V1ServiceSpec()
        .putSelectorItem(DOMAINUID_LABEL, UID)
        .putSelectorItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
        .putSelectorItem(CREATEDBYOPERATOR_LABEL, "true")
        .type("NodePort")
        .ports(
            Collections.singletonList(
                new V1ServicePort().port(TEST_PORT).nodePort(TEST_NODE_PORT)));
  }

  private AsyncCallTestSupport.CannedResponse expectReadService(String serviceName) {
    return testSupport.createCannedResponse("readService").withNamespace(NS).withName(serviceName);
  }
}

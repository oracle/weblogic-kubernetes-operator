// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

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
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.ServiceMutator;
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
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1Status;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SuppressWarnings({"SameParameterValue"})
@RunWith(Parameterized.class)
public class ServiceUpdateTest {

  private static final String NS = "namespace";
  private static final String TEST_CLUSTER = "cluster-1";
  private static final int TEST_PORT = 7000;
  private static final int BAD_PORT = 9999;
  private static final String DOMAIN_NAME = "domain1";
  private static final String TEST_SERVER_NAME = "server1";
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
  private static NetworkAccessPoint networkAccessPoint =
      new NetworkAccessPoint(NAP_NAME, PROTOCOL, TEST_PORT, TEST_NODE_PORT);
  private List<LogRecord> logRecords = new ArrayList<>();

  private static final Supplier<V1Service> defaultClusterServiceSupplier =
      ServiceUpdateTest::createClusterService;

  private static final Supplier<V1Service> defaultServerServiceSupplier =
      () -> withNodePort(createServerService(), TEST_NODE_PORT);

  private static final Supplier<V1Service> defaultExternalChannelServiceSupplier =
      ServiceUpdateTest::createExternalChannelService;

  private static final Supplier<V1Service> serverServiceSupplierWithBadNodePort =
      () -> withNodePort(createServerService(), BAD_PORT);

  private static final ServiceMutator nodePortMutator =
      service -> withNodePort(service, TEST_NODE_PORT);

  private static final ServiceMutator labelMutator =
      service -> {
        service.getMetadata().putLabelsItem("anyLabel", "anyLabelValue");
        return service;
      };

  private static final ServiceMutator annotationMutator =
      service -> {
        service.getMetadata().putAnnotationsItem("anyAnnotation", "anyAnnotationValue");
        return service;
      };

  private static final ServiceMutator selectorMutator =
      service -> {
        service.getSpec().putSelectorItem("anyName", "anyValue");
        return service;
      };

  @Parameters(name = "{index}: {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {
            "Current cluster has \"bad\" version",
            ServiceCategory.CLUSTER,
            defaultClusterServiceSupplier,
            (ServiceMutator) ServiceUpdateTest::createClusterServiceWithBadVersion,
          },
          {
            "Current cluster has \"bad\" spec type",
            ServiceCategory.CLUSTER,
            defaultClusterServiceSupplier,
            (ServiceMutator) ServiceUpdateTest::createClusterServiceWithBadSpecType,
          },
          {
            "Current cluster has \"bad\" port",
            ServiceCategory.CLUSTER,
            defaultClusterServiceSupplier,
            (ServiceMutator) ServiceUpdateTest::createClusterServiceWithBadPort,
          },
          {
            "Current cluster has an additional label",
            ServiceCategory.CLUSTER,
            defaultClusterServiceSupplier,
            labelMutator,
          },
          {
            "Current cluster has an additional annotation",
            ServiceCategory.CLUSTER,
            defaultClusterServiceSupplier,
            annotationMutator,
          },
          {
            "Current cluster has an additional selector",
            ServiceCategory.CLUSTER,
            defaultClusterServiceSupplier,
            selectorMutator,
          },
          {
            "Current server has \"bad\" version",
            ServiceCategory.SERVER,
            (Supplier<V1Service>) () -> withBadVersion(defaultServerServiceSupplier.get()),
            (ServiceMutator) service -> defaultServerServiceSupplier.get(),
          },
          {
            "Current server does not have node port",
            ServiceCategory.SERVER,
            (Supplier<V1Service>) () -> createServerService(),
            nodePortMutator,
          },
          {
            "Current server has \"bad\" node port",
            ServiceCategory.SERVER,
            serverServiceSupplierWithBadNodePort,
            nodePortMutator,
          },
          {
            "Current server has an additional label",
            ServiceCategory.SERVER,
            (Supplier<V1Service>) () -> labelMutator.change(defaultServerServiceSupplier.get()),
            (ServiceMutator) service -> defaultServerServiceSupplier.get(),
          },
          {
            "Current server has an additional annotation",
            ServiceCategory.SERVER,
            (Supplier<V1Service>)
                () -> annotationMutator.change(defaultServerServiceSupplier.get()),
            (ServiceMutator) service -> defaultServerServiceSupplier.get(),
          },
          {
            "Current external channel has \"bad\" version",
            ServiceCategory.EXTERNAL_CHANNEL,
            (Supplier<V1Service>) () -> withBadVersion(defaultExternalChannelServiceSupplier.get()),
            (ServiceMutator) service -> defaultExternalChannelServiceSupplier.get(),
          },
        });
  }

  @Parameter public String description;

  @Parameter(1)
  public ServiceCategory serviceCategory;

  @Parameter(2)
  public Supplier<V1Service> serviceSupplier;

  @Parameter(3)
  public ServiceMutator serviceMutator;

  @Before
  public void setUp() {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, MESSAGE_KEYS)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport.addWlsServer(ADMIN_SERVER);
    configSupport.setAdminServerName(ADMIN_SERVER);

    testSupport
        .addToPacket(CLUSTER_NAME, TEST_CLUSTER)
        .addToPacket(SERVER_NAME, TEST_SERVER_NAME)
        .addToPacket(PORT, TEST_PORT)
        .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, configSupport.createDomainConfig())
        .addDomainPresenceInfo(domainPresenceInfo);
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  public void onStepRunWithMutatedService_replaceIt() {
    switch (serviceCategory) {
      case CLUSTER:
        verifyClusterServiceReplaced();
        break;
      case SERVER:
        verifyServerServiceReplaced();
        break;
      case EXTERNAL_CHANNEL:
        verifyExternalChannelReplaced();
        break;
      default:
        throw new IllegalArgumentException("Service category not supported: " + serviceCategory);
    }
  }

  private void verifyClusterServiceReplaced() {
    initializeClusterServiceFromRecord(serviceMutator.change(serviceSupplier.get()));
    expectDeleteServiceSuccessful(getClusterServiceName());
    expectSuccessfulCreateClusterService(serviceSupplier.get());

    testSupport.runSteps(ServiceHelper.createForClusterStep(terminalStep));

    assertThat(domainPresenceInfo.getClusters(), hasEntry(TEST_CLUSTER, createClusterService()));
    assertThat(logRecords, containsInfo(CLUSTER_SERVICE_REPLACED));
  }

  private void verifyServerServiceReplaced() {
    initializeServiceFromRecord(serviceSupplier.get());
    expectDeleteServiceSuccessful(getServerServiceName());
    expectSuccessfulCreateService(serviceMutator.change(serviceSupplier.get()));

    testSupport.addToPacket(NODE_PORT, TEST_NODE_PORT);
    testSupport.runSteps(ServiceHelper.createForServerStep(terminalStep));

    assertThat(logRecords, containsInfo(MANAGED_SERVICE_REPLACED));
  }

  private void verifyExternalChannelReplaced() {
    initializeExternalChannelServiceFromRecord(serviceSupplier.get());
    expectDeleteServiceSuccessful(getExternalChannelServiceName());
    expectSuccessfulCreateService(serviceMutator.change(serviceSupplier.get()));

    testSupport.runSteps(ServiceHelper.createForExternalChannelStep(terminalStep));

    assertThat(logRecords, containsInfo(MANAGED_SERVICE_REPLACED));
  }

  private static V1Service createClusterServiceWithBadVersion(V1Service service) {
    return service
        .spec(createClusterServiceSpec())
        .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, BAD_VERSION));
  }

  private static V1ServiceSpec createClusterServiceSpec() {
    return createUntypedClusterServiceSpec().type("ClusterIP");
  }

  private static V1ServiceSpec createUntypedClusterServiceSpec() {
    return new V1ServiceSpec()
        .putSelectorItem(DOMAINUID_LABEL, UID)
        .putSelectorItem(CLUSTERNAME_LABEL, TEST_CLUSTER)
        .putSelectorItem(CREATEDBYOPERATOR_LABEL, "true")
        .ports(Collections.singletonList(new V1ServicePort().port(TEST_PORT)));
  }

  private static String getClusterServiceName() {
    return LegalNames.toClusterServiceName(UID, TEST_CLUSTER);
  }

  private DomainPresenceInfo createPresenceInfo() {
    return new DomainPresenceInfo(
        new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(createDomainSpec()));
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec().withDomainUID(UID);
  }

  private ServerKubernetesObjects createSko(V1Service service) {
    ServerKubernetesObjects sko = new ServerKubernetesObjects();
    sko.getService().set(service);
    return sko;
  }

  private void initializeClusterServiceFromRecord(V1Service service) {
    if (service == null) {
      domainPresenceInfo.getClusters().remove(TEST_CLUSTER);
    } else {
      domainPresenceInfo.getClusters().put(TEST_CLUSTER, service);
    }
  }

  private void expectSuccessfulCreateClusterService(V1Service service) {
    expectCreateService(service).returning(service);
  }

  private static V1Service createClusterService() {
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

  private static V1Service createClusterServiceWithBadSpecType(V1Service service) {
    return service
        .spec(new V1ServiceSpec().type("BadType"))
        .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION));
  }

  private static V1Service createClusterServiceWithBadPort(V1Service service) {
    return service
        .spec(createSpecWithBadPort())
        .metadata(new V1ObjectMeta().putLabelsItem(RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION));
  }

  private static V1ServiceSpec createSpecWithBadPort() {
    return new V1ServiceSpec()
        .type("ClusterIP")
        .ports(Collections.singletonList(new V1ServicePort().port(BAD_PORT)));
  }

  private static V1Service withNodePort(V1Service service, int nodePort) {
    service.getSpec().type("NodePort").clusterIP(null);
    service
        .getSpec()
        .getPorts()
        .stream()
        .findFirst()
        .ifPresent(servicePort -> servicePort.setNodePort(nodePort));
    return service;
  }

  private static V1ServiceSpec createServerServiceSpec() {
    return createUntypedServerServiceSpec().type("ClusterIP").clusterIP("None");
  }

  private static V1ServiceSpec createUntypedServerServiceSpec() {
    return new V1ServiceSpec()
        .putSelectorItem(DOMAINUID_LABEL, UID)
        .putSelectorItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
        .putSelectorItem(CREATEDBYOPERATOR_LABEL, "true")
        .ports(Collections.singletonList(new V1ServicePort().port(TEST_PORT)));
  }

  private void initializeServiceFromRecord(V1Service service) {
    domainPresenceInfo.getServers().put(TEST_SERVER_NAME, createSko(service));
  }

  private static String getServerServiceName() {
    return LegalNames.toServerServiceName(UID, TEST_SERVER_NAME);
  }

  private void expectSuccessfulCreateService(V1Service service) {
    expectCreateService(service).returning(service);
  }

  private CallTestSupport.CannedResponse expectCreateService(V1Service service) {
    return testSupport.createCannedResponse("createService").withNamespace(NS).withBody(service);
  }

  private static V1Service createServerService() {
    return createServerService(createServerServiceSpec());
  }

  private static V1Service createServerService(V1ServiceSpec serviceSpec) {
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
                .putLabelsItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"));
  }

  private static V1Service withBadVersion(V1Service service) {
    service.getMetadata().putLabelsItem(RESOURCE_VERSION_LABEL, BAD_VERSION);
    return service;
  }

  private void initializeExternalChannelServiceFromRecord(V1Service service) {
    testSupport.addToPacket(NETWORK_ACCESS_POINT, networkAccessPoint);
    ServerKubernetesObjects sko = domainPresenceInfo.getServers().get(TEST_SERVER_NAME);
    if (sko == null) {
      sko = createSko(null);
      domainPresenceInfo.getServers().put(TEST_SERVER_NAME, sko);
    }
    if (service == null) {
      sko.getChannels().remove(NAP_NAME);
    } else {
      sko.getChannels().put(NAP_NAME, service);
    }
  }

  private static V1Service createExternalChannelService() {
    return createExternalChannelService(createExternalChannelServiceSpec());
  }

  private static V1Service createExternalChannelService(V1ServiceSpec serviceSpec) {
    return new V1Service()
        .spec(serviceSpec)
        .metadata(
            new V1ObjectMeta()
                .name(getExternalChannelServiceName())
                .namespace(NS)
                .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V2)
                .putLabelsItem(DOMAINUID_LABEL, UID)
                .putLabelsItem(DOMAINNAME_LABEL, DOMAIN_NAME)
                .putLabelsItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
                .putLabelsItem(CHANNELNAME_LABEL, NAP_NAME)
                .putLabelsItem(CREATEDBYOPERATOR_LABEL, "true"));
  }

  private static String getExternalChannelServiceName() {
    return LegalNames.toNAPName(UID, TEST_SERVER_NAME, networkAccessPoint);
  }

  private static V1ServiceSpec createExternalChannelServiceSpec() {
    return new V1ServiceSpec()
        .putSelectorItem(DOMAINUID_LABEL, UID)
        .putSelectorItem(SERVERNAME_LABEL, TEST_SERVER_NAME)
        .putSelectorItem(CREATEDBYOPERATOR_LABEL, "true")
        .type("NodePort")
        .ports(
            Collections.singletonList(
                new V1ServicePort().port(TEST_PORT).nodePort(TEST_NODE_PORT)));
  }

  private enum ServiceCategory {
    CLUSTER,
    SERVER,
    ADMIN_SERVER,
    EXTERNAL_CHANNEL
  }
}

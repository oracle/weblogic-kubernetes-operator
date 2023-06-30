// Copyright (c) 2019, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.kubernetes.operator.ModelInImageDomainType;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.LegalNames;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.DomainProcessorTestSetup.NS;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.UID;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestCluster;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.createTestDomain;
import static oracle.kubernetes.operator.DomainProcessorTestSetup.setupCluster;
import static oracle.kubernetes.operator.DomainSourceType.FROM_MODEL;
import static oracle.kubernetes.operator.DomainSourceType.IMAGE;
import static oracle.kubernetes.operator.DomainSourceType.PERSISTENT_VOLUME;
import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.WebLogicConstants.JRF;
import static oracle.kubernetes.operator.WebLogicConstants.WLS;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.getAuxiliaryImage;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DomainValidationTest extends DomainValidationTestBase {

  private static final String ENV_NAME1 = "MY_ENV";
  private static final String RAW_VALUE_1 = "123";
  private static final String RAW_MOUNT_PATH_1 = "$(DOMAIN_HOME)/servers/$(SERVER_NAME)";
  private static final String RAW_MOUNT_PATH_2 = "$(MY_ENV)/bin";
  private static final String BAD_MOUNT_PATH_1 = "$DOMAIN_HOME/servers/$SERVER_NAME";
  private static final String BAD_MOUNT_PATH_2 = "$(DOMAIN_HOME/servers/$(SERVER_NAME";
  private static final String BAD_MOUNT_PATH_3 = "$()DOMAIN_HOME/servers/SERVER_NAME";
  private static final String LONG_CONTAINER_PORT_NAME = "long-container-port-name";
  public static final String UID2 = "test-domain2";
  public static final String CLUSTER_1 = "Cluster-1";
  public static final String CLUSTER_2 = "Cluster-2";
  public static final String CLUSTER_3 = "Cluster-3";

  private final DomainResource domain = createTestDomain();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    resourceLookup.defineResource(SECRET_NAME, V1Secret.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, V1ConfigMap.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, V1ConfigMap.class, NS);
    configureDomain(domain)
          .withWebLogicCredentialsSecret(SECRET_NAME);
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenManagerServerSpecsHaveUniqueNames_dontReportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms2"));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenManagerServerSpecsHaveDuplicateNames_reportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("ms1"));

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("managedServers", "ms1")));
  }

  @Test
  void whenManagerServerSpecsHaveDns1123DuplicateNames_reportError() {
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("Server-1"));
    domain.getSpec().getManagedServers().add(new ManagedServer().withServerName("server_1"));

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("managedServers", "server-1")));
  }

  @Test
  void whenDomainConfiguredWithAuxiliaryImageAndVolumeMountExists_reportError() {
    configureDomain(domain)
          .withAdditionalVolumeMount("test", DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)
          .withRuntimeEncryptionSecret("mysecret")
          .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")));

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("auxiliary images", "mountPath", "already in use")));
  }

  @Test
  void whenDomainConfiguredWithAuxiliaryImageAndDomainHomeInImage_noErrorReported() {
    configureDomain(domain).withDomainHomeSourceType(IMAGE)
          .withModelConfigMap("wdt-cm")
          .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenMoreThanOneAuxiliaryImageSetsSourceWDTInstallHome_reportError() {
    List<AuxiliaryImage> auxiliaryImages = new ArrayList<>();
    auxiliaryImages.add(new AuxiliaryImage().image("image1").sourceWDTInstallHome("/wdtInstallHome1"));
    auxiliaryImages.add(new AuxiliaryImage().image("image2").sourceWDTInstallHome("/wdtInstallHome2"));

    configureDomainWithRuntimeEncryptionSecret(domain)
          .withAuxiliaryImages(auxiliaryImages);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("More than one auxiliary image under",
                "'spec.configuration.model.auxiliaryImages'",
                "sets a 'sourceWDTInstallHome'")));
  }

  @Test
  void whenMoreThanOneDomainCreationImageSetsSourceWDTInstallHome_reportError() {
    List<DomainCreationImage> domainCreationImages = new ArrayList<>();
    domainCreationImages.add(new DomainCreationImage().image("image1").sourceWDTInstallHome("/wdtInstallHome1"));
    domainCreationImages.add(new DomainCreationImage().image("image2").sourceWDTInstallHome("/wdtInstallHome2"));

    configureDomainWithRuntimeEncryptionSecret(domain)
        .withInitializeDomainOnPv(new InitializeDomainOnPV()
            .domain(new DomainOnPV().domainCreationImages(domainCreationImages)));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("More than one domain creation image under",
            "'spec.configuration.initializeDomainOnPV.domain.domainCreationImages'",
            "sets a 'sourceWDTInstallHome'")));
  }

  @Test
  void whenTwoAuxiliaryImageSetsSourceWDTInstallHomeAndOneIsNone_noErrorReported() {
    List<AuxiliaryImage> auxiliaryImages = new ArrayList<>();
    auxiliaryImages.add(new AuxiliaryImage().image("image1").sourceWDTInstallHome("/wdtInstallHome1"));
    auxiliaryImages.add(new AuxiliaryImage().image("image2").sourceWDTInstallHome("None"));

    configureDomainWithRuntimeEncryptionSecret(domain)
          .withAuxiliaryImages(auxiliaryImages);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenTwoDomainCreationImageSetsSourceWDTInstallHomeAndOneIsNone_noErrorReported() {
    List<DomainCreationImage> domainCreationImages = new ArrayList<>();
    domainCreationImages.add(new DomainCreationImage().image("image1").sourceWDTInstallHome("/wdtInstallHome1"));
    domainCreationImages.add(new DomainCreationImage().image("image2").sourceWDTInstallHome("None"));

    configureDomainWithRuntimeEncryptionSecret(domain)
        .withInitializeDomainOnPv(new InitializeDomainOnPV()
            .domain(new DomainOnPV().domainCreationImages(domainCreationImages)));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void wheOnlyOneAuxiliaryImageSetsSourceWDTInstallHome_noErrorReported() {
    List<AuxiliaryImage> auxiliaryImages = new ArrayList<>();
    auxiliaryImages.add(new AuxiliaryImage().image("image1"));
    auxiliaryImages.add(new AuxiliaryImage().image("image2").sourceWDTInstallHome("/wdtInstallHome1"));

    configureDomainWithRuntimeEncryptionSecret(domain)
          .withAuxiliaryImages(auxiliaryImages);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void wheOnlyOneDomainCreationImageSetsSourceWDTInstallHome_noErrorReported() {
    List<DomainCreationImage> domainCreationImages = new ArrayList<>();
    domainCreationImages.add(new DomainCreationImage().image("image1"));
    domainCreationImages.add(new DomainCreationImage().image("image2").sourceWDTInstallHome("/wdtInstallHome1"));

    configureDomainWithRuntimeEncryptionSecret(domain)
        .withInitializeDomainOnPv(new InitializeDomainOnPV()
            .domain(new DomainOnPV().domainCreationImages(domainCreationImages)));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenModelHomePlacedUnderWDTInstallHome_reportError() {
    configureDomainWithRuntimeEncryptionSecret(domain)
          .withWDTInstallationHome("/aux")
          .withModelHome("/aux/y");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("modelHome", "is invalid",
                "modelHome must be outside the directory for the wdtInstallHome")));
  }

  private DomainConfigurator configureDomainWithRuntimeEncryptionSecret(DomainResource domain) {
    return configureDomain(domain)
          .withRuntimeEncryptionSecret("mysecret");
  }

  @Test
  void whenWDTInstallHomePlacedUnderModelHome_reportError() {
    configureDomainWithRuntimeEncryptionSecret(domain)
          .withWDTInstallationHome("/aux/y")
          .withModelHome("/aux");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("wdtInstallHome", "is invalid",
                "wdtInstallHome must be outside the directory for the modelHome")));
  }

  @Test
  void whenClusterSpecsHaveUniqueNames_dontReportError() {
    addClusterWithName("cluster1");
    addClusterWithName("cluster2");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenClusterSpecsHaveDuplicateNames_reportError() {
    addClusterWithName("cluster1");
    addClusterWithName("cluster1");

    assertThat(domain.getValidationFailures(resourceLookup), hasSize(2));
    assertTrue(domain.getValidationFailures(resourceLookup).get(0).contains("cluster1"));
  }

  private void addClusterWithName(String clusterName) {
    resourceLookup.defineResource(clusterName, ClusterResource.class, NS);
    domain.getSpec().getClusters().add(new V1LocalObjectReference().name(clusterName));
  }

  @Test
  void whenClusterSpecsHaveDns1123DuplicateNames_reportError() {
    addClusterWithName("Cluster-1");
    addClusterWithName("cluster_1");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("clusters", "cluster-1")));
  }

  @Test
  void whenLogHomeDisabled_dontReportError() {
    configureDomain(domain).withLogHomeEnabled(false);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenPortForwardingDisabled_dontReportError() {
    configureDomain(domain).withAdminChannelPortForwardingEnabled(false);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenVolumeMountHasNonValidPath_reportError() {
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "shared/logs");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("shared/logs", "sharedlogs")));
  }

  @Test
  void whenVolumeMountHasLogHomeDirectory_dontReportError() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/shared/logs/mydomain");
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "/shared/logs");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenNoVolumeMountHasSpecifiedLogHomeDirectory_reportError() {
    configureDomain(domain).withLogHomeEnabled(true).withLogHome("/private/log/mydomain");
    configureDomain(domain).withAdditionalVolumeMount("sharedlogs", "/shared/logs");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("log home", "/private/log/mydomain")));
  }

  @Test
  void whenNoVolumeMountHasImplicitLogHomeDirectory_reportError() {
    configureDomain(domain).withLogHomeEnabled(true);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("log home", "/shared/logs/" + UID)));
  }

  @Test
  void whenDomainHasAdditionalVolumeMountsWithInvalidChar_1_reportError() {
    configureDomain(domain)
          .withAdditionalVolumeMount("volume1", BAD_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder(
                "The mount path", BAD_MOUNT_PATH_1, "volume1", "of domain resource", "is not valid")));
  }

  @Test
  void whenDomainHasAdditionalVolumeMountsWithInvalidChar_2_reportError() {
    configureDomain(domain)
          .withAdditionalVolumeMount("volume2", BAD_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder(
                "The mount path", BAD_MOUNT_PATH_2, "volume2", "of domain resource", "is not valid")));
  }

  @Test
  void whenDomainHasAdditionalVolumeMountsWithInvalidChar_3_reportError() {
    configureDomain(domain)
          .withAdditionalVolumeMount("volume3", BAD_MOUNT_PATH_3);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder(
                "The mount path", BAD_MOUNT_PATH_3, "volume3", "of domain resource", "is not valid")));
  }

  @Test
  void whenDomainHasAdditionalVolumeMountsWithReservedVariables_dontReportError() {
    configureDomain(domain)
          .withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainHasAdditionalVolumeMountsWithCustomVariables_dontReportError() {
    configureDomain(domain)
          .withEnvironmentVariable(ENV_NAME1, RAW_VALUE_1)
          .withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainHasAdditionalVolumeMountsWithNonExistingVariables_reportError() {
    configureDomain(domain)
          .withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder(
                "The mount path", RAW_MOUNT_PATH_2, "volume1", "of domain resource", "is not valid")));
  }

  @Test
  void whenDomainAdminServerHasAdditionalVolumeMountsWithInvalidChar_reportError() {
    configureDomain(domain)
          .configureAdminServer()
          .getAdminServer()
          .addAdditionalVolumeMount("volume1", BAD_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("The mount path", BAD_MOUNT_PATH_1,
                "volume1", "of domain resource", "is not valid")));
  }

  @Test
  void whenDomainAdminServerHasAdditionalVolumeMountsWithReservedVariables_dontReportError() {
    configureDomain(domain)
          .configureAdminServer()
          .getAdminServer()
          .addAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainAdminServerHasAdditionalVolumeMountsWithCustomVariables_dontReportError() {
    configureDomain(domain)
          .withEnvironmentVariable(ENV_NAME1, RAW_VALUE_1)
          .configureAdminServer()
          .getAdminServer()
          .addAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainAdminServerHasAdditionalVolumeMountsWithNonExistingVariables_reportError() {
    configureDomain(domain)
          .configureAdminServer()
          .getAdminServer()
          .addAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("The mount path", "volume1", "of domain resource", "is not valid")));
  }

  @Test
  void whenClusteredServerPodHasAdditionalVolumeMountsWithInvalidChar_reportError() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
          .configureCluster(info, "Cluster-1").withAdditionalVolumeMount("volume1", BAD_MOUNT_PATH_1);
    info.getReferencedClusters().forEach(resourceLookup::defineResource);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("The mount path", "of domain resource", "is not valid")));
  }

  @Test
  void whenClusteredServerPodHasAdditionalVolumeMountsWithReservedVariables_dontReportError() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
          .configureCluster(info,"Cluster-1").withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);
    ClusterResource cluster1 = createTestCluster("Cluster-1");
    resourceLookup.defineResource(cluster1);
    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenClusteredServerPodHasAdditionalVolumeMountsWithCustomVariables_dontReportError() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
          .withEnvironmentVariable(ENV_NAME1, RAW_VALUE_1)
          .configureCluster(info, "Cluster-1").withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);
    ClusterResource cluster1 = createTestCluster("Cluster-1");
    resourceLookup.defineResource(cluster1);
    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenClusteredServerPodHasAdditionalVolumeMountsWithNonExistingVariables_reportError() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
          .configureCluster(info, "Cluster-1").withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_2);
    info.getReferencedClusters().forEach(resourceLookup::defineResource);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("The mount path", "volume1", "of domain resource", "is not valid")));
  }

  @Test
  void whenNonReservedEnvironmentVariableSpecifiedAtDomainLevel_dontReportError() {
    configureDomain(domain).withEnvironmentVariable("testname", "testValue");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenReservedEnvironmentVariablesSpecifiedAtDomainLevel_reportError() {
    configureDomain(domain)
          .withEnvironmentVariable("ADMIN_NAME", "testValue")
          .withEnvironmentVariable("INTROSPECT_HOME", "/shared/home/introspection");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("variables", "ADMIN_NAME", "INTROSPECT_HOME", "spec.serverPod.env", "are")));
  }

  @Test
  void whenReservedEnvironmentVariablesSpecifiedForAdminServer_reportError() {
    configureDomain(domain)
          .configureAdminServer()
          .withEnvironmentVariable("LOG_HOME", "testValue")
          .withEnvironmentVariable("NAMESPACE", "badValue");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(
                stringContainsInOrder("variables", "LOG_HOME", "NAMESPACE", "spec.adminServer.serverPod.env", "are")));
  }

  @Test
  void whenReservedEnvironmentVariablesSpecifiedAtServerLevel_reportError() {
    configureDomain(domain)
          .configureServer("ms1")
          .withEnvironmentVariable("SERVER_NAME", "testValue");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("variable", "SERVER_NAME", "spec.managedServers[ms1].serverPod.env", "is")));
  }

  @Test
  void whenReservedEnvironmentVariablesSpecifiedAtClusterLevel_reportError() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
          .configureCluster(info,"cluster1")
          .withEnvironmentVariable("DOMAIN_HOME", "testValue");
    info.getReferencedClusters().forEach(resourceLookup::defineResource);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("variable", "DOMAIN_HOME", "spec.clusters[cluster1].serverPod.env", "is")));
  }

  @Test
  void whenWebLogicCredentialsSecretNameFound_dontReportError() {
    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWebLogicCredentialsSecretNamespaceUndefined_useDomainNamespace() {
    configureDomain(domain)
          .withWebLogicCredentialsSecret(SECRET_NAME);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenLivenessProbeSuccessThresholdValueInvalidForDomain_reportError() {
    configureDomain(domain).withDefaultLivenessProbeThresholds(2, 3);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("Invalid value", "2", "liveness probe success threshold",
                "adminServer")));
  }

  @Test
  void whenLivenessProbeSuccessThresholdValueInvalidForCluster_reportError() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
          .configureCluster(info, "cluster-1")
          .withLivenessProbeSettings(5, 4, 3).withLivenessProbeThresholds(2, 3);
    info.getReferencedClusters().forEach(resourceLookup::defineResource);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("Invalid value", "2", "liveness probe success threshold",
                "cluster-1")));
  }

  @Test
  void whenLivenessProbeSuccessThresholdValueInvalidForServer_reportError() {
    configureDomain(domain)
          .configureServer("managed-server1")
          .withLivenessProbeSettings(5, 4, 3).withLivenessProbeThresholds(2, 3);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("Invalid value", "2", "liveness probe success threshold",
                "managed-server1")));
  }

  @Test
  void whenReservedContainerNameUsedForDomain_reportError() {
    configureDomain(domain)
          .withContainer(new V1Container().name(WLS_CONTAINER_NAME));

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("container name", WLS_CONTAINER_NAME, "adminServer",
                "is reserved", "operator")));
  }

  @Test
  void whenReservedContainerNameUsedForCluster_reportError() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
          .configureCluster(info,"cluster-1")
          .withContainer(new V1Container().name(WLS_CONTAINER_NAME));
    info.getReferencedClusters().forEach(resourceLookup::defineResource);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("container name", WLS_CONTAINER_NAME, "cluster-1",
                "is reserved", "operator")));
  }

  @Test
  void whenReservedContainerNameUsedForManagedServer_reportError() {
    configureDomain(domain)
          .configureServer("managed-server1")
          .withContainer(new V1Container().name(WLS_CONTAINER_NAME));

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("container name", WLS_CONTAINER_NAME, "managed-server1",
                "is reserved", "operator")));
  }

  @Test
  void whenContainerPortNameExceedsMaxLength_ForAdminServerContainer_reportError() {
    configureDomain(domain)
          .withContainer(new V1Container().name("Test")
                .ports(List.of(new V1ContainerPort().name(LONG_CONTAINER_PORT_NAME))));

    assertThat(domain.getValidationFailures(resourceLookup), contains(stringContainsInOrder(
          "Container port name ", LONG_CONTAINER_PORT_NAME, "domainUID", UID, "adminServer", "Test",
          "exceeds maximum allowed length '15'")));
  }

  @Test
  void whenContainerPortNameExceedsMaxLength_ForClusteredServerContainer_reportError() {
    DomainPresenceInfo info = new DomainPresenceInfo(domain);
    configureDomain(domain)
          .configureCluster(info,"cluster-1")
          .withContainer(new V1Container().name("Test")
                .ports(List.of(new V1ContainerPort().name(LONG_CONTAINER_PORT_NAME))));
    info.getReferencedClusters().forEach(resourceLookup::defineResource);

    assertThat(domain.getValidationFailures(resourceLookup), contains(stringContainsInOrder(
          "Container port name ", LONG_CONTAINER_PORT_NAME, "domainUID", UID, "cluster-1", "Test",
          "exceeds maximum allowed length '15'")));
  }

  @Test
  void whenContainerPortNameExceedsMaxLength_ForManagedServerContainer_reportError() {
    configureDomain(domain)
          .configureServer("managed-server1")
          .withContainer(new V1Container().name("Test")
                .ports(List.of(new V1ContainerPort().name(LONG_CONTAINER_PORT_NAME))));

    assertThat(domain.getValidationFailures(resourceLookup), contains(stringContainsInOrder(
          "Container port name ", LONG_CONTAINER_PORT_NAME, "domainUID", UID, "managed-server1", "Test",
          "exceeds maximum allowed length '15'")));
  }

  @Test
  void whenWebLogicCredentialsSecretNameNotFound_reportError() {
    resourceLookup.undefineResource(SECRET_NAME, V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("WebLogicCredentials", SECRET_NAME, "not found", NS)));
  }

  @Test
  void whenImagePullSecretExists_dontReportError() {
    resourceLookup.defineResource("a-secret", V1Secret.class, NS);
    configureDomain(domain).withDefaultImagePullSecret(new V1LocalObjectReference().name("a-secret"));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenConfigOverrideSecretSpecifiedButDoesNotExist_reportError() {
    configureDomain(domain).withConfigOverrideSecrets("override-secret");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("ConfigOverride", "override-secret", "not found", NS)));

  }

  @Test
  void whenConfigOverrideSecretExists_dontReportError() {
    resourceLookup.defineResource("override-secret", V1Secret.class, NS);
    configureDomain(domain).withConfigOverrideSecrets("override-secret");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenConfigOverrideCmExistsTypeImage_dontReportError() {
    resourceLookup.defineResource("overrides-cm-image", V1ConfigMap.class, NS);
    configureDomain(domain).withConfigOverrides("overrides-cm-image").withDomainHomeSourceType(IMAGE);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenConfigOverrideCmExistsTypeFromModel_reportError() {
    resourceLookup.defineResource("overrides-cm-model", V1ConfigMap.class, NS);
    resourceLookup.defineResource("wdt-cm-secret", V1Secret.class, NS);
    configureDomain(domain).withConfigOverrides("overrides-cm-model")
          .withRuntimeEncryptionSecret("wdt-cm-secret")
          .withDomainHomeSourceType(FROM_MODEL);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("Configuration overridesConfigMap",
                "overrides-cm", "not supported", "FromModel")));
  }

  @Test
  void whenWdtConfigMapExists_fromModel_dontReportError() {
    resourceLookup.defineResource("wdt-cm", V1ConfigMap.class, NS);
    resourceLookup.defineResource("wdt-cm-secret-model1", V1Secret.class, NS);
    configureDomain(domain)
          .withRuntimeEncryptionSecret("wdt-cm-secret-model1")
          .withModelConfigMap("wdt-cm")
          .withDomainHomeSourceType(FROM_MODEL);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWdtConfigMapSpecifiedButDoesNotExist_fromModel_reportError() {
    resourceLookup.defineResource("wdt-cm-secret-model2", V1Secret.class, NS);
    configureDomain(domain).withRuntimeEncryptionSecret("wdt-cm-secret-model2")
          .withModelConfigMap("wdt-configmap")
          .withDomainHomeSourceType(FROM_MODEL);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("ConfigMap", "wdt-configmap", "spec.configuration.model.configMap",
                "not found", NS)));
  }

  @Test
  void whenWdtConfigMapSpecifiedButDoesNotExist_Image_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(IMAGE)
          .withModelConfigMap("wdt-configmap");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenRuntimeEncryptionSecretSpecifiedButDoesNotExist_Image_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(IMAGE)
          .withRuntimeEncryptionSecret("runtime-secret");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenRuntimeEncryptionSecretUnspecified_Image_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(IMAGE);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenRuntimeEncryptionSecretSpecifiedButDoesNotExist_fromModel_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
          .withRuntimeEncryptionSecret("runtime-secret");

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("RuntimeEncryption", "runtime-secret", "not found", NS)));
  }

  @Test
  void whenRuntimeEncryptionSecretExists_fromModel_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
          .withRuntimeEncryptionSecret("runtime-good-secret");
    resourceLookup.defineResource("runtime-good-secret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenRuntimeEncryptionSecretUnspecified_fromModel_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("spec.configuration.model.runtimeEncryptionSecret",
                "must be specified", "FromModel")));
  }

  @Test
  void whenWalletPasswordSecretSpecifiedButDoesNotExist_fromModel_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
          .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
          .withOpssWalletPasswordSecret("wallet-password-secret-missing");

    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("secret", "wallet-password-secret-missing", "not found", NS)));
  }

  @Test
  void whenWalletFileSecretSpecifiedButDoesNotExist_Image_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
          .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
          .withOpssWalletFileSecret("wallet-file-secret-missing");

    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("secret",
                "wallet-file-secret-missing", "not found", NS)));
  }

  @Test
  void whenWalletPasswordSecretExists_fromModel_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
          .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
          .withOpssWalletPasswordSecret("wallet-password-secret-good");
    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);
    defineWalletPasswordSecretWithRequiredData("wallet-password-secret-good");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWalletPasswordSecretExistsButWalletPasswordKeyMissing_fromModel_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withOpssWalletPasswordSecret("wallet-password-secret-good");
    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);
    resourceLookup.defineResource("wallet-password-secret-good", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OPSS wallet password secret",
            "configuration.opss.walletPasswordSecret", "wallet-password-secret-good")));
  }

  @Test
  void whenWalletPasswordSecretExistsButWalletPasswordKeyPresent_fromModel_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withOpssWalletPasswordSecret("wallet-password-secret-good");
    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);
    defineWalletPasswordSecretWithRequiredData("wallet-password-secret-good");

    assertThat(domain.getValidationFailures(resourceLookup),empty());
  }

  private void defineWalletPasswordSecretWithRequiredData(String s2) {
    resourceLookup.defineResource(s2, V1Secret.class, NS);
    V1Secret secret = resourceLookup.getSecrets().stream()
        .filter(s -> isSpecifiedSecret(s, s2, NS)).findFirst().get();
    Map<String, byte[]> data = new HashMap<>();
    data.put("walletPassword", "123456".getBytes(StandardCharsets.UTF_8));
    secret.setData(data);
  }

  boolean isSpecifiedSecret(V1Secret secret, String name, String namespace) {
    return hasMatchingMetadata(secret.getMetadata(), name, namespace);
  }

  private boolean hasMatchingMetadata(V1ObjectMeta metadata, String name, String namespace) {
    return metadata != null
        && Objects.equals(name, metadata.getName())
        && Objects.equals(namespace, metadata.getNamespace());
  }

  @Test
  void whenWalletFileSecretExists_fromModel_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
          .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
          .withOpssWalletFileSecret("wallet-file-secret-good");
    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);
    resourceLookup.defineResource("wallet-file-secret-good", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWalletPasswordSecretUnspecified_fromModel_jrf_reportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withDomainType(ModelInImageDomainType.JRF);
    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
          contains(stringContainsInOrder("spec.configuration.opss.walletPasswordSecret",
                "must be specified", "FromModel", "JRF")));
  }

  @Test
  void whenWalletFileSecretUnspecified_fromModel_jrf_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(FROM_MODEL)
        .withDomainType(ModelInImageDomainType.JRF)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withOpssWalletPasswordSecret("wallet-password-secret-good");

    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);
    defineWalletPasswordSecretWithRequiredData("wallet-password-secret-good");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWalletPasswordSecretUnspecified_Image_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(IMAGE)
          .withOpssWalletFileSecret("wallet-file-secret");

    resourceLookup.defineResource("wallet-file-secret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWalletPasswordSecretUnspecified_fromModel_wls_dontReportError() {
    configureDomain(domain).withDomainHomeSourceType(IMAGE)
        .withRuntimeEncryptionSecret("runtime-encryption-secret-good")
        .withDomainType(ModelInImageDomainType.WLS)
        .withOpssWalletFileSecret("wallet-file-secret");

    resourceLookup.defineResource("runtime-encryption-secret-good", V1Secret.class, NS);
    resourceLookup.defineResource("wallet-file-secret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainUidExceedMaxAllowed_reportError() {
    String domainUID = "mydomainthatislongerthan46charactersandshouldfail";
    DomainResource myDomain = createTestDomain(domainUID);
    configureDomain(myDomain)
        .withDomainHomeSourceType(IMAGE)
        .withWebLogicCredentialsSecret(SECRET_NAME)
        .withDomainType(ModelInImageDomainType.WLS)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    assertThat(myDomain.getValidationFailures(resourceLookup), contains(stringContainsInOrder(
          "DomainUID ", domainUID, "exceeds maximum allowed length")));
  }

  @Test
  void whenDomainUidExceedMaxAllowedWithCustomSuffix_reportError() {
    String domainUID = "mydomainthatislongerthan42charactersandshould";
    DomainResource myDomain = createTestDomain(domainUID);
    configureDomain(myDomain)
        .withDomainHomeSourceType(IMAGE)
        .withWebLogicCredentialsSecret(SECRET_NAME)
        .withDomainType(ModelInImageDomainType.WLS)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    TuningParametersStub.setParameter(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "introspect-domain-job");
    assertThat(myDomain.getValidationFailures(resourceLookup), contains(stringContainsInOrder(
          "DomainUID ", domainUID, "exceeds maximum allowed length")));
  }

  @Test
  void whenDomainUidNotExceedMaxAllowedWithCustomSuffix_dontReportError() {
    String domainUID = "mydomainthatislongerthan42charactersandshould";
    DomainResource myDomain = createTestDomain(domainUID);
    configureDomain(myDomain)
        .withDomainHomeSourceType(IMAGE)
        .withWebLogicCredentialsSecret(SECRET_NAME)
        .withDomainType(ModelInImageDomainType.WLS)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    TuningParametersStub.setParameter(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "-job");
    assertThat(myDomain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainUidNotExceedMaxAllowedWithEmptyCustomSuffix_dontReportError() {
    String domainUID = "mydomainthatislongerthan42charactersandshould";
    DomainResource myDomain = createTestDomain(domainUID);
    configureDomain(myDomain)
        .withDomainHomeSourceType(IMAGE)
        .withWebLogicCredentialsSecret(SECRET_NAME)
        .withDomainType(ModelInImageDomainType.WLS)
        .configureAdminServer()
        .configureAdminService()
        .withChannel("default");

    TuningParametersStub.setParameter(LegalNames.INTROSPECTOR_JOB_NAME_SUFFIX_PARAM, "");
    assertThat(myDomain.getValidationFailures(resourceLookup),  empty());
  }

  @Test
  void whenDomainConfiguredWithFluentdWithoutCredentials_reportError() {
    configureDomain(domain)
        .withFluentdConfiguration(false, null, null, null,
            null);
    domain.getValidationFailures(resourceLookup);
    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("When fluentdSpecification is specified in the domain "
            + "spec, a secret containing elastic search credentials must be specified in",
            "spec.fluentdSpecification.elasticSearchCredentials")));
  }

  @Test
  void whenClusterReferenceNotFound_reportError() {
    resourceLookup.defineResource(domain);

    setupCluster(domain, new String[] {"cluster-1"});

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Cluster resource", "cluster-1", "not found", NS)));
  }

  @Test
  void whenClusterReferenceInDifferentNamespace_reportError() {
    ClusterResource cluster1 = createTestCluster("cluster-1", "NS2");
    resourceLookup.defineResource(domain);
    resourceLookup.defineResource(cluster1);

    setupCluster(domain, new String[] {"cluster-1"});

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Cluster resource", "cluster-1", "not found", NS)));
  }

  @Test
  void whenClusterReferenceDifferentName_reportError() {
    ClusterResource cluster1 = createTestCluster("cluster-2");
    resourceLookup.defineResource(domain);
    resourceLookup.defineResource(cluster1);

    setupCluster(domain, new String[] {"cluster-1"});

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Cluster resource", "cluster-1", "not found", NS)));
  }

  private DomainConfigurator configureDomain(DomainResource domain) {
    return new DomainCommonConfigurator(domain);
  }

  @Test
  void whenTwoDomainsHaveSameClusterReference_reportError() {
    DomainResource domain2 = createTestDomain(UID2);
    ClusterResource cluster1 = createTestCluster(CLUSTER_1);
    defineResources(domain, domain2, cluster1);

    ClusterResource[] clusters = new ClusterResource[] {cluster1};
    setupCluster(domain, clusters);
    setupCluster(domain2, clusters);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("cluster resource", CLUSTER_1, "it is used by", UID2)));
  }

  @Test
  void whenTwoDomainsReferenceDifferentClusterResources_noFailureReported() {
    DomainResource domain2 = createTestDomain(UID2);
    ClusterResource cluster1 = createTestCluster(CLUSTER_1);
    ClusterResource cluster2 = createTestCluster(CLUSTER_2);
    defineResources(domain, domain2, cluster1, cluster2);

    setupCluster(domain, new ClusterResource[] {cluster1});
    setupCluster(domain2, new ClusterResource[] {cluster2});

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenTwoDomainsHaveOverlapClusterResourceReferences_reportError() {
    DomainResource domain2 = createTestDomain(UID2);
    ClusterResource cluster1 = createTestCluster(CLUSTER_1);
    ClusterResource cluster2 = createTestCluster(CLUSTER_2);
    ClusterResource cluster3 = createTestCluster(CLUSTER_3);
    defineResources(domain, domain2, cluster1, cluster2, cluster3);

    setupCluster(domain, new ClusterResource[] {cluster1, cluster2});
    setupCluster(domain2, new ClusterResource[] {cluster2, cluster3});

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("cluster resource", CLUSTER_2, "it is used by", UID2)));
  }

  @Test
  void whenTwoDomainsHaveOverlapClusterResourceReferences_withClusterName_reportError() {
    String wlsClusterName1 = "c1";
    String wlsClusterName2 = "c2";
    String wlsClusterName3 = "c3";
    DomainResource domain2 = createTestDomain(UID2);
    ClusterResource cluster1 = createTestCluster(CLUSTER_1);
    cluster1.getSpec().setClusterName(wlsClusterName1);
    ClusterResource cluster2 = createTestCluster(CLUSTER_2);
    cluster2.getSpec().setClusterName(wlsClusterName2);
    ClusterResource cluster3 = createTestCluster(CLUSTER_3);
    cluster3.getSpec().setClusterName(wlsClusterName3);
    defineResources(domain, domain2, cluster1, cluster2, cluster3);

    setupCluster(domain, new ClusterResource[] {cluster1, cluster2});
    setupCluster(domain2, new ClusterResource[] {cluster2, cluster3});

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("cluster resource", CLUSTER_2, "it is used by", UID2)));
  }

  @Test
  void whenUnsupportedIntrospectorEnvVarDefined_reportError() {
    configureDomain(domain).configureIntrospector()
        .withEnvironmentVariable(new V1EnvVar().name("Test1").value("Test1"))
        .withEnvironmentVariable(new V1EnvVar().name("Test2").value("Test2"));
    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Unsupported", "environment variable", "Test1", "Test2", "defined")));
  }

  @Test
  void whenDomainCreationConfigMapExists_InitPvDomain_dontReportError() {
    resourceLookup.defineResource("domain-creation-cm", V1ConfigMap.class, NS);
    defineWalletPasswordSecretWithRequiredData("wpSecret");
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret")
        .withDomainCreationConfigMap("domain-creation-cm");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainCreationConfigMapSpecifiedButDoesNotExist_initPvDomain_reportError() {
    defineWalletPasswordSecretWithRequiredData("wpSecret");
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret")
        .withDomainCreationConfigMap("domain-creation-cm");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("ConfigMap", "domain-creation-cm",
            "spec.configuration.initializeDomainOnPV.domain.domainCreationConfigMap",
            "not found", NS)));
  }

  @Test
  void whenWalletFileSecretSpecifiedButDoesNotExist_initPvDomain_domainTypeWLS_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVType(WLS)
        .withInitializeDomainOnPVOpssWalletFileSecret("wfSecret");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("secret", "wfSecret", "not found", NS)));
  }

  @Test
  void whenWalletFileSecretSpecifiedButDoesNotExist_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVOpssWalletFileSecret("wfSecret")
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("secret", "wfSecret", "not found", NS)));
  }

  @Test
  void whenWalletFileSecretExists_initPvDomain_domainTypeWLS_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVType(WLS)
        .withInitializeDomainOnPVOpssWalletFileSecret("wfSecret");

    resourceLookup.defineResource("wfSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWalletPasswordSecretSpecifiedButDoesNotExist_initPvDomain_reportError() {
    configureDomain(domain).withLogHomeEnabled(false)
        .withDomainHomeSourceType(PERSISTENT_VOLUME)
        .withOpssWalletPasswordSecret("wpSecret");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("secret", "wpSecret", "not found", NS)));
  }

  @Test
  void whenWalletPasswordSecretExists_initPvDomain_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWalletPasswordSecretNotSpecified_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().domain(new DomainOnPV()));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("secret",
            "spec.configuration.initializeDomainOnPV.domain.opss.walletPasswordSecret", "must be specified",
            "spec.configuration.initializeDomainOnPV.domain.domainType", "JRF")));
  }

  @Test
  void whenWalletPasswordSecretNotSpecified_initPvDomain_domainTypeWLS_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVType(WLS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenWalletPasswordSecretSpecifiedButNoMatchingSecret_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVType(JRF)
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");
    resourceLookup.defineResource("wpSecret", V1Secret.class, NS);
    resourceLookup.getSecrets().stream()
        .filter(s -> isSpecifiedSecret(s, "wpSecret", NS)).findFirst().ifPresent(s -> s.setMetadata(null));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OpssWalletPassword secret", "wpSecret", "not found", NS)));
  }

  @Test
  void whenWalletPasswordSecretSpecifiedButNoMatchingSecretInNS_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVType(JRF)
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");
    resourceLookup.defineResource("wpSecret", V1Secret.class, "NS2");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OpssWalletPassword secret", "wpSecret", "not found", NS)));
  }

  @Test
  void whenWalletPasswordSecretExistsButWalletPasswordKeyMissing_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");

    resourceLookup.defineResource("wpSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OPSS wallet password secret",
            "configuration.initializeDomainOnPV.domain.opss.walletPasswordSecret", "wpSecret")));
  }

  @Test
  void whenWalletPasswordSecretExistsAndWalletPasswordKeyPresent_initPvDomain_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenBothMiiOpssAndInitPvDomainOpssWalletPasswordSpecified_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withOpssWalletPasswordSecret("wpWallet")
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");
    resourceLookup.defineResource("wfSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OPSS secrets",
            "spec.configuration.initializeDomainOnPV.domain.opss",
            "spec.configuration.opss", "JRF")));
  }

  @Test
  void whenBothMiiOpssWalletFileAndInitPvDomainOpssWalletPasswordSpecified_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withOpssWalletFileSecret("wfWallet")
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");
    resourceLookup.defineResource("wfSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OPSS secrets",
            "spec.configuration.initializeDomainOnPV.domain.opss",
            "spec.configuration.opss", "JRF")));
  }

  @Test
  void whenBothMiiOpssWalletFileAndInitPvDomainOpssWalletFilePasswordSpecified_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withOpssWalletFileSecret("wfWallet")
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret")
        .withInitializeDomainOnPVOpssWalletFileSecret("wfSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");
    resourceLookup.defineResource("wfSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OPSS secrets",
            "spec.configuration.initializeDomainOnPV.domain.opss",
            "spec.configuration.opss", "JRF")));
  }

  @Test
  void whenBothMiiOpssWalletFilePasswordAndInitPvDomainOpssWalletPasswordSpecified_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withOpssWalletFileSecret("wfWallet")
        .withOpssWalletPasswordSecret("wpSecret")
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");
    resourceLookup.defineResource("wfSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OPSS secrets",
            "spec.configuration.initializeDomainOnPV.domain.opss",
            "spec.configuration.opss", "JRF")));
  }

  @Test
  void whenBothMiiOpssWalletFilePasswordAndInitPvDomainOpssWalletFilePasswordSpecified_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withOpssWalletFileSecret("wfWallet")
        .withOpssWalletPasswordSecret("wpSecret")
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret")
        .withInitializeDomainOnPVOpssWalletFileSecret("wfSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");
    resourceLookup.defineResource("wfSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("OPSS secrets",
            "spec.configuration.initializeDomainOnPV.domain.opss",
            "spec.configuration.opss", "JRF")));
  }

  @Test
  void whenModelSpecified_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPV(getInitPvDomainWithDomainTypeWLS())
        .withModel(new Model());

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("configuration",
            "spec.configuration.model", "not allowed",
            "spec.configuration.initializeDomainOnPV",
            "specified")));
  }

  private InitializeDomainOnPV getInitPvDomainWithDomainTypeWLS() {
    return new InitializeDomainOnPV().domain(new DomainOnPV().domainType(WLS));
  }

  @Test
  void whenModelNotSpecified_initPvDomain_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPV(getInitPvDomainWithDomainTypeWLS());

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenPersistentVolumeNameNotSpecifiedUnderInitPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolume(new PersistentVolume()));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Persistent volume",
            "spec.configuration.initializeDomainOnPV.persistentVolume", "is invalid", "metadata.name",
            "must be specified")));
  }

  @Test
  void whenPersistentVolumeCapacityNotSpecifiedUnderInitPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolume(
            new PersistentVolume().metadata(new V1ObjectMeta().name("Test"))
                .spec(new PersistentVolumeSpec().storageClassName("SC"))));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Persistent volume Test",
            "is invalid", "spec.capacity", "must be specified")));
  }

  @Test
  void whenPersistentVolumeStorageClassNotSpecifiedUnderInitPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolume(
            new PersistentVolume().metadata(new V1ObjectMeta().name("Test")).spec(new PersistentVolumeSpec()
                .capacity(Collections.singletonMap("storage", new Quantity("50Gi"))))));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Persistent volume Test",
            "is invalid", "spec.storageClass", "must be specified")));
  }

  @Test
  void whenPersistentVolumeClaimNameNotSpecifiedUnderInitPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolumeClaim(new PersistentVolumeClaim()));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Persistent volume claim",
            "spec.configuration.initializeDomainOnPV.persistentVolumeClaim", "is invalid", "metadata.name",
            "must be specified")));
  }

  @Test
  void whenPersistentVolumeClaimResourcesNotSpecifiedUnderInitPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolumeClaim(
            new PersistentVolumeClaim().metadata(new V1ObjectMeta().name("Test"))
                .spec(new PersistentVolumeClaimSpec().storageClassName("SC"))));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Persistent volume claim Test",
            "is invalid", "spec.resources", "must be specified")));
  }

  @Test
  void whenPersistentVolumeClaimStorageClassNotSpecifiedUnderInitPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolumeClaim(
            new PersistentVolumeClaim().metadata(new V1ObjectMeta().name("Test")).spec(new PersistentVolumeClaimSpec()
                .resources(createResources()))));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("Persistent volume claim Test",
            "is invalid", "spec.storageClass", "must be specified")));
  }

  @Test
  void whenMultipleVolumeMountHaveOverlappingMountPath_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withAdditionalVolumeMount("volume1", "/domain-path1")
        .withAdditionalVolumeMount("volume2", "/domain-path1/dir1");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("The mount path", "/domain-path1", "in entry",
            "volume", "and the mount path", "in entry", "volume", "are", "overlapping.")));
  }

  @Test
  void whenMultipleVolumeMountHaveSameMountPath_initPvDomain_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV())
        .withAdditionalVolumeMount("volume1", "/domain-path1/dir1")
        .withAdditionalVolumeMount("volume2", "/domain-path1/dir1");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("The mount path", "/domain-path1", "in entry",
            "volume", "and the mount path", "in entry", "volume", "are", "overlapping.")));
  }

  private DomainConfigurator configuredDomainWithInitializeDomainOnPV() {
    return configureDomain(domain).withLogHomeEnabled(false)
        .withDomainHomeSourceType(PERSISTENT_VOLUME)
        .withInitializeDomainOnPv(new InitializeDomainOnPV())
        .withAdditionalVolumeMount("sharedDomains", "/shared");
  }


  private DomainConfigurator configuredDomainWithInitializeDomainOnPVWithPVCVolume() {
    return configuredDomainWithInitializeDomainOnPV()
        .withAdditionalPvClaimVolume("pvcVolume", "Test");
  }

  @Test
  void whenMultipleVolumeMountHaveNoOverlappingMountPath_initPvDomain_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withAdditionalVolumeMount("volume2", "/domain-path2");

    assertThat(domain.getValidationFailures(resourceLookup),empty());
  }

  public static V1ResourceRequirements createResources() {
    return new V1ResourceRequirements().requests(Collections.singletonMap("storage", new Quantity("5Gi")));
  }

  @Test
  void whenVolumeMountHasDomainHomeDirectory_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withDomainHome("/shared/domains/mydomain");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenNoVolumeMountHasSpecifiedDomainHomeDirectory_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withDomainHome("/private/domains/mydomain");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("domain home", "/private/domains/mydomain")));
  }

  @Test
  void whenVolumeMountHasNoValidDomainHomeDirectoryParentDir_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withAdditionalVolumeMount("volume2", "/private")
        .withDomainHome("/private/mydomain");

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("domain home", "/private/mydomain")));
  }

  @Test
  void whenVolumeMountHasValidDomainHomeDirectoryParentDir_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withAdditionalVolumeMount("volume2", "/private")
        .withDomainHome("/private/wls/domains/mydomain");

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainTypeJRFAndCreateIfNotExistsDomainAndRCU_initDomainOnPV_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().domain(
            new DomainOnPV().domainType(JRF).createMode(CreateIfNotExists.DOMAIN_AND_RCU)))
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret")
        .withInitializeDomainOnPVOpssWalletFileSecret("wfSecret");
    defineWalletPasswordSecretWithRequiredData("wpSecret");
    resourceLookup.defineResource("wfSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainTypeJRFAndCreateIfNotExistsDomain_initDomainOnPV_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().domain(
            new DomainOnPV().domainType(JRF).createMode(CreateIfNotExists.DOMAIN)))
        .withInitializeDomainOnPVOpssWalletPasswordSecret("wpSecret")
        .withInitializeDomainOnPVOpssWalletFileSecret("wfSecret");

    defineWalletPasswordSecretWithRequiredData("wpSecret");
    resourceLookup.defineResource("wfSecret", V1Secret.class, NS);

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenDomainTypeWLSAndCreateIfNotExistsDomainAndRCU_initDomainOnPV_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().domain(
            new DomainOnPV().domainType(WLS).createMode(CreateIfNotExists.DOMAIN_AND_RCU)));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("spec.configuration.initializeDomainOnPV.domain.createIfNotExists",
            "DomainAndRCU", "WLS")));
  }

  @Test
  void whenServerPodHasNoVolumesForPVCWhenInitPVCSpecified_initDomainOnPV_reportError() {
    configuredDomainWithInitializeDomainOnPV()
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolumeClaim(
            new PersistentVolumeClaim().metadata(new V1ObjectMeta().name("Test")).spec(new PersistentVolumeClaimSpec()
                .resources(createResources()).storageClassName("mystoreage"))));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("spec.configuration.initializeDomainOnPV", "there is no volume",
            "spec.configuration.initializeDomainOnPV.persistentVolumeClaim")));
  }

  @Test
  void whenServerPodHasNoVolumesForPVC_initDomainOnPV_reportError() {
    configuredDomainWithInitializeDomainOnPV();

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("spec.configuration.initializeDomainOnPV", "at least one of the volumes",
            "PVC")));
  }

  @Test
  void whenServerPodHasNoMatchVolumesForPVC_initDomainOnPV_reportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume()
        .withAdditionalVolume("sharedDomains", "/shared/domains")
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolumeClaim(
            new PersistentVolumeClaim()
                .metadata(new V1ObjectMeta().name("TestPVC")).spec(new PersistentVolumeClaimSpec()
                .resources(createResources()).storageClassName("mystoreage"))));

    assertThat(domain.getValidationFailures(resourceLookup),
        contains(stringContainsInOrder("spec.configuration.initializeDomainOnPV", "there is no volume",
            "spec.configuration.initializeDomainOnPV.persistentVolumeClaim", "TestPVC")));
  }

  @Test
  void whenServerPodHasMatchVolumesForPVC_initDomainOnPV_dontReportError() {
    configuredDomainWithInitializeDomainOnPV()
        .withAdditionalPvClaimVolume("sharedDomains", "Test")
        .withInitializeDomainOnPv(new InitializeDomainOnPV().persistentVolumeClaim(
            new PersistentVolumeClaim().metadata(new V1ObjectMeta().name("Test")).spec(new PersistentVolumeClaimSpec()
                .resources(createResources()).storageClassName("mystoreage"))));

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @Test
  void whenServerPodHasPVCVolumesWhenPVCNotSpecified_initDomainOnPV_dontReportError() {
    configuredDomainWithInitializeDomainOnPVWithPVCVolume();

    assertThat(domain.getValidationFailures(resourceLookup), empty());
  }

  @SafeVarargs
  private <T> void defineResources(T... resources) {
    for (T resource : resources) {
      resourceLookup.defineResource((KubernetesObject) resource);
    }
  }

}

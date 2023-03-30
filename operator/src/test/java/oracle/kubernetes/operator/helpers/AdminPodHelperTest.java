// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX;
import static oracle.kubernetes.common.CommonConstants.COMPATIBILITY_MODE;
import static oracle.kubernetes.common.logging.MessageKeys.ADMIN_POD_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.ADMIN_POD_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.ADMIN_POD_PATCHED;
import static oracle.kubernetes.common.logging.MessageKeys.ADMIN_POD_REPLACED;
import static oracle.kubernetes.common.logging.MessageKeys.CYCLING_POD;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.KUBERNETES_EVENT_ERROR;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.operator.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_ROLL_STARTING_EVENT;
import static oracle.kubernetes.operator.EventConstants.POD_CYCLE_STARTING_EVENT;
import static oracle.kubernetes.operator.EventMatcher.hasEvent;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.operator.helpers.DomainIntrospectorJobTest.TEST_VOLUME_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.POD_CYCLE_STARTING;
import static oracle.kubernetes.operator.helpers.Matchers.hasContainer;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasInitContainer;
import static oracle.kubernetes.operator.helpers.Matchers.hasInitContainerWithEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasPvClaimVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolumeMount;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
class AdminPodHelperTest extends PodHelperTestBase {
  private static final String INTERNAL_OPERATOR_CERT_ENV_NAME = "INTERNAL_OPERATOR_CERT";

  private static final String RAW_VALUE_1 = "value-$(SERVER_NAME)";
  private static final String END_VALUE_1 = "value-ADMIN_SERVER";
  private static final String RAW_MOUNT_PATH_1 = "$(DOMAIN_HOME)/servers/$(SERVER_NAME)";
  private static final String END_MOUNT_PATH_1 = "/u01/oracle/user_projects/domains/servers/ADMIN_SERVER";
  public static final String CUSTOM_MOUNT_PATH2 = "/common1";

  public AdminPodHelperTest() {
    super(ADMIN_SERVER, ADMIN_PORT);
  }

  @Override
  String getCreatedMessageKey() {
    return ADMIN_POD_CREATED;
  }

  @Override
  FiberTestSupport.StepFactory getStepFactory() {
    return PodHelper::createAdminPodStep;
  }

  @Override
  ServerConfigurator configureServer() {
    return getConfigurator().configureAdminServer();
  }

  @Override
  String getExistsMessageKey() {
    return ADMIN_POD_EXISTS;
  }

  @Override
  String getPatchedMessageKey() {
    return ADMIN_POD_PATCHED;
  }

  @Override
  String getReplacedMessageKey() {
    return ADMIN_POD_REPLACED;
  }

  @Override
  void setServerPort(int port) {
    getServerTopology().setAdminPort(port);
  }

  @Override
  String getReferencePlainPortPodYaml_3_0() {
    return ReferenceObjects.ADMIN_PLAINPORT_POD_3_0;
  }

  @Override
  String getReferencePlainPortPodYaml_3_1() {
    return ReferenceObjects.ADMIN_PLAINPORT_POD_3_1;
  }

  @Override
  String getReferenceSslPortPodYaml_3_0() {
    return ReferenceObjects.ADMIN_SSLPORT_POD_3_0;
  }

  @Override
  String getReferenceSslPortPodYaml_3_1() {
    return ReferenceObjects.ADMIN_SSLPORT_POD_3_1;
  }

  @Override
  String getReferenceMiiPodYaml() {
    return ReferenceObjects.ADMIN_MII_POD_3_1;
  }

  @Override
  String getReferenceMiiAuxImagePodYaml_3_3() {
    return ReferenceObjects.ADMIN_MII_AUX_IMAGE_POD_3_3;
  }

  @Override
  String getReferenceMiiConvertedAuxImagePodYaml_3_4() {
    return ReferenceObjects.ADMIN_MII_CONVERTED_AUX_IMAGE_POD_3_4;
  }

  @Override
  String getReferenceMiiConvertedAuxImagePodYaml_3_4_1() {
    return ReferenceObjects.ADMIN_MII_CONVERTED_AUX_IMAGE_POD_3_4_1;
  }

  @Override
  String getReferenceMiiAuxImagePodYaml_4_0() {
    return ReferenceObjects.ADMIN_MII_AUX_IMAGE_POD_4_0;
  }

  @Override
  String getReferenceIstioMonitoringExporterTcpProtocol() {
    return ReferenceObjects.ADMIN_ISTIO_MONITORING_EXPORTER_TCP_PROTOCOL;
  }

  String getDomainValidationFailedKey() {
    return DOMAIN_VALIDATION_FAILED;
  }

  @Test // REG I don't understand why this is only true for the admin server
  void whenConfigurationAddsEnvironmentVariable_replacePod() {
    initializeExistingPod();

    configureServer().withEnvironmentVariable("test", "???");

    verifyPodReplaced();
  }

  @Test
  void whenPodReplacedAndDomainNotRolling_generateRollStartedEvent() {
    initializeExistingPod();
    getConsoleHandlerMemento().ignoreMessage(getReplacedMessageKey());
    configureServer().withEnvironmentVariable("test", "???");

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getEvents().stream().anyMatch(this::isDomainRollStartedEvent), is(true));
  }

  private boolean isDomainRollStartedEvent(CoreV1Event e) {
    return DOMAIN_ROLL_STARTING_EVENT.equals(e.getReason());
  }

  @Override
  protected void verifyPodReplaced() {
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getReplacedMessageKey()));
  }

  @Override
  protected void verifyPodNotReplacedWhen(PodMutator mutator) {
    V1Pod existingPod = createPod(testSupport.getPacket());
    mutator.mutate(existingPod);
    initializeExistingPod(existingPod);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsFine(getExistsMessageKey()));
  }

  @Override
  V1Pod createPod(Packet packet) {
    return new PodHelper.AdminPodStepContext(null, packet).getPodModel();
  }

  @Test
  void whenDeleteReportsNotFound_replaceAdminPod() {
    initializeExistingPod(getIncompatiblePod());
    testSupport.failOnDelete(KubernetesTestSupport.POD, getPodName(), NS, HTTP_NOT_FOUND);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getReplacedMessageKey()));
  }

  private V1Pod getIncompatiblePod() {
    V1Pod existingPod = createTestPodModel();
    Objects.requireNonNull(existingPod.getSpec()).setContainers(null);
    return existingPod;
  }

  @Test
  void whenAdminPodDeletionFails_unrecoverableFailureOnUnauthorized() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeExistingPod(getIncompatiblePod());
    testSupport.failOnDelete(KubernetesTestSupport.POD, getPodName(), NS, HTTP_UNAUTHORIZED);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void whenAdminPodReplacementFails() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeExistingPod(getIncompatiblePod());
    testSupport.failOnCreate(KubernetesTestSupport.POD, NS, HTTP_INTERNAL_ERROR);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    assertThat(getDomain(),
          hasStatus().withReason(KUBERNETES).withMessageContaining("create", "pod", NS));
  }

  @Test
  void whenAdminPodReplacementFails_generateFailedEvent() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeExistingPod(getIncompatiblePod());
    testSupport.failOnCreate(KubernetesTestSupport.POD, NS, HTTP_INTERNAL_ERROR);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(KUBERNETES_EVENT_ERROR)));
  }

  @Test
  void whenAdminPodCreated_specHasPodNameAsHostName() {
    assertThat(getCreatedPodSpec().getHostname(), equalTo(getPodName()));
  }

  private V1PodSpec getCreatedPodSpec() {
    return getCreatedPod().getSpec();
  }

  @Test
  void whenAdminPodCreated_containerHasStartServerCommand() {
    assertThat(
        getCreatedPodSpecContainer().getCommand(),
        contains("/weblogic-operator/scripts/startServer.sh"));
  }

  @Test
  void whenAdminPodCreated_hasOperatorCertEnvVariable() {
    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        hasEnvVar(INTERNAL_OPERATOR_CERT_ENV_NAME, InMemoryCertificates.INTERNAL_CERT_DATA));
  }

  @Test
  void whenAdminPodCreatedWithAdminPortEnabled_adminServerPortSecureEnvVarIsTrue() {
    getServerTopology().setAdminPort((Integer) 9002);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("ADMIN_SERVER_PORT_SECURE", "true"));
  }

  @Test
  void whenAdminPodCreatedWithNullAdminPort_adminServerPortSecureEnvVarIsNotSet() {
    getServerTopology().setAdminPort(null);
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("ADMIN_SERVER_PORT_SECURE", "true")));
  }

  @Test
  void whenAdminPodCreatedWithAdminServerHasSslPortEnabled_adminServerPortSecureEnvVarIsTrue() {
    getServerTopology().setSslListenPort(9999);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("ADMIN_SERVER_PORT_SECURE", "true"));
  }

  @Test
  void whenAdminPodCreatedWithAdminServerHasNullSslPort_adminServerPortSecureEnvVarIsNotSet() {
    getServerTopology().setSslListenPort(null);
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("ADMIN_SERVER_PORT_SECURE", "true")));
  }

  @Test
  void whenDomainPresenceHasNoEnvironmentItems_createAdminPodStartupWithDefaultItems() {
    assertThat(getCreatedPodSpecContainer().getEnv(), not(empty()));
  }

  @Test
  void whenDomainHasEnvironmentItems_createAdminPodStartupWithThem() {
    configureAdminServer()
        .withEnvironmentVariable("item1", "value1")
        .withEnvironmentVariable("item2", "value2");

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar("item1", "value1"), hasEnvVar("item2", "value2")));
  }

  private ServerConfigurator configureAdminServer() {
    return getConfigurator().configureAdminServer();
  }

  @Test
  void whenDomainHasEnvironmentItemsWithVariables_createAdminPodStartupWithThem() {
    configureAdminServer()
        .withEnvironmentVariable("item1", "find uid1 at $(DOMAIN_HOME)")
        .withEnvironmentVariable("item2", "$(SERVER_NAME) is $(ADMIN_NAME):$(ADMIN_PORT)");

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(
            hasEnvVar("item1", "find uid1 at /u01/oracle/user_projects/domains"),
            hasEnvVar("item2", "ADMIN_SERVER is ADMIN_SERVER:7001")));
  }

  @Test
  void whenDomainHasEnvironmentItemsWithVariable_createPodShouldNotChangeItsValue()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    final String itemRawValue = "find uid1 at $(DOMAIN_HOME)";
    configureAdminServer().withEnvironmentVariable("item1", itemRawValue);

    getCreatedPod();

    getConfiguredDomainSpec().getAdminServer().getEnv();
    assertThat(
        getConfiguredDomainSpec().getAdminServer().getEnv(),
        hasEnvVar("item1", itemRawValue));
  }

  @Test
  void whenDomainHasValueFromEnvironmentItems_createAdminPodStartupWithThem() {
    V1EnvVar configMapKeyRefEnvVar = createConfigMapKeyRefEnvVar("VARIABLE1", "my-env", "VAR1");
    V1EnvVar secretKeyRefEnvVar = createSecretKeyRefEnvVar("VARIABLE2", "my-secret", "VAR2");
    V1EnvVar fieldRefEnvVar = createFieldRefEnvVar("MY_NODE_IP", "status.hostIP");

    configureAdminServer()
        .withEnvironmentVariable(configMapKeyRefEnvVar)
        .withEnvironmentVariable(secretKeyRefEnvVar)
        .withEnvironmentVariable(fieldRefEnvVar);

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasItem(configMapKeyRefEnvVar), hasItem(secretKeyRefEnvVar), hasItem(fieldRefEnvVar)));
  }

  @Test
  void whenDomainHasValueFromEnvironmentItemsWithVariables_createAdminPodStartupWithSubstitutions() {
    V1EnvVar configMapKeyRefEnvVar = createConfigMapKeyRefEnvVar("VARIABLE1", "my-env", RAW_VALUE_1);
    V1EnvVar secretKeyRefEnvVar = createSecretKeyRefEnvVar("VARIABLE2", "my-secret", RAW_VALUE_1);
    V1EnvVar fieldRefEnvVar = createFieldRefEnvVar("MY_NODE_IP", RAW_VALUE_1);

    configureAdminServer()
        .withEnvironmentVariable(configMapKeyRefEnvVar)
        .withEnvironmentVariable(secretKeyRefEnvVar)
        .withEnvironmentVariable(fieldRefEnvVar);

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(
          hasItem(createConfigMapKeyRefEnvVar("VARIABLE1", "my-env", END_VALUE_1)),
          hasItem(createSecretKeyRefEnvVar("VARIABLE2", "my-secret", END_VALUE_1)),
          hasItem(createFieldRefEnvVar("MY_NODE_IP", END_VALUE_1))
        )
    );
  }

  @Test
  void whenDomainHasValueFromEnvironmentItemsWithVariables_createPodShouldNotChangeTheirValues()
      throws Exception {
    V1EnvVar configMapKeyRefEnvVar = createConfigMapKeyRefEnvVar("VARIABLE1", "my-env", RAW_VALUE_1);
    V1EnvVar secretKeyRefEnvVar = createSecretKeyRefEnvVar("VARIABLE2", "my-secret", RAW_VALUE_1);
    V1EnvVar fieldRefEnvVar = createFieldRefEnvVar("MY_NODE_IP", RAW_VALUE_1);

    configureAdminServer()
        .withEnvironmentVariable(configMapKeyRefEnvVar)
        .withEnvironmentVariable(secretKeyRefEnvVar)
        .withEnvironmentVariable(fieldRefEnvVar);

    assertThat(
        getConfiguredDomainSpec().getAdminServer().getEnv(),
        allOf(
            hasItem(createConfigMapKeyRefEnvVar("VARIABLE1", "my-env", RAW_VALUE_1)),
            hasItem(createSecretKeyRefEnvVar("VARIABLE2", "my-secret", RAW_VALUE_1)),
            hasItem(createFieldRefEnvVar("MY_NODE_IP", RAW_VALUE_1))
        )
    );
  }

  @Test
  void whenAdminServerHasAdditionalVolumesWithReservedVariables_createAdminPodStartupWithSubstitutions() {
    configureAdminServer()
        .withAdditionalVolume("volume1", "/source-$(SERVER_NAME)")
        .withAdditionalVolume("volume2", "/source-$(DOMAIN_NAME)");

    assertThat(
        Objects.requireNonNull(getCreatedPod().getSpec()).getVolumes(),
        allOf(
            hasVolume("volume1", "/source-ADMIN_SERVER"),
            hasVolume("volume2", "/source-domain1")));
  }

  @Test
  void whenAdminServerHasAdditionalVolumeMountsWithReservedVariables_createAdminPodStartupWithSubstitutions() {
    configureAdminServer()
        .withAdditionalVolumeMount("volume1", RAW_MOUNT_PATH_1);

    assertThat(getCreatedPodSpecContainer().getVolumeMounts(), hasVolumeMount("volume1", END_MOUNT_PATH_1));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithCustomVariables_createAdminPodStartupWithSubstitutions() {
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, V1ConfigMap.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, V1ConfigMap.class, NS);

    configureAdminServer()
        .withEnvironmentVariable(ENV_NAME1, GOOD_MY_ENV_VALUE)
        .withAdditionalVolume("volume1", VOLUME_PATH_1)
        .withAdditionalVolumeMount("volume1", VOLUME_MOUNT_PATH_1);

    testSupport.runSteps(PodHelper.createAdminPodStep(terminalStep));

    assertThat(testSupport.getResources(KubernetesTestSupport.POD).isEmpty(), is(false));
    assertThat(logRecords, containsInfo(getCreatedMessageKey()));
    assertThat(Objects.requireNonNull(getCreatedPod().getSpec()).getContainers().get(0).getVolumeMounts(),
        hasVolumeMount("volume1", END_VOLUME_MOUNT_PATH_1));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithCustomVariablesContainInvalidValue_reportValidationError() {
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, V1ConfigMap.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, V1ConfigMap.class, NS);

    configureAdminServer()
        .withEnvironmentVariable(ENV_NAME1, BAD_MY_ENV_VALUE)
        .withAdditionalVolume("volume1", VOLUME_PATH_1)

        .withAdditionalVolumeMount("volume1", VOLUME_MOUNT_PATH_1);

    testSupport.runSteps(PodHelper.createAdminPodStep(terminalStep));

    assertThat(testSupport.getResources(KubernetesTestSupport.POD).isEmpty(), is(true));
    assertThat(getDomain().getStatus().getReason(), is(DOMAIN_INVALID.toString()));
    assertThat(logRecords, containsSevere(getDomainValidationFailedKey()));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithCustomVariablesContainInvalidValue_createFailedEvent() {
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, V1ConfigMap.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, V1ConfigMap.class, NS);

    configureAdminServer()
        .withEnvironmentVariable(ENV_NAME1, BAD_MY_ENV_VALUE)
        .withAdditionalVolume("volume1", VOLUME_PATH_1)

        .withAdditionalVolumeMount("volume1", VOLUME_MOUNT_PATH_1);

    testSupport.runSteps(PodHelper.createAdminPodStep(terminalStep));
    logRecords.clear();

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(DOMAIN_INVALID_EVENT_ERROR)));
  }

  @Test
  void createAdminPodStartupWithNullAdminUsernamePasswordEnvVarsValues() {
    configureAdminServer();

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  void whenDomainHasAdditionalPvClaimVolumesWitVariables_createManagedPodWithThem() {
    getConfigurator()
        .withAdditionalPvClaimVolume("volume-$(SERVER_NAME)", "$(SERVER_NAME)-claim");

    assertThat(
        getCreatedPodSpec().getVolumes(),
        hasPvClaimVolume("volume-admin-server", "admin-server-claim"));
  }

  @Test
  void whenServerHasAdditionalVolumes_createAdminPodWithThem() {
    configureAdminServer()
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPodSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  void whenServerHasAdditionalVolumeMounts_createAdminPodWithThem() {
    configureAdminServer()
        .withAdditionalVolumeMount("volume1", "/destination-path1")
        .withAdditionalVolumeMount("volume2", "/destination-path2");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/destination-path1"),
            hasVolumeMount("volume2", "/destination-path2")));
  }

  @Test
  void whenPodHasDuplicateVolumes_createAdminPodWithCombination() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/domain-path1")
        .withAdditionalVolume("volume2", "/domain-path2")
        .configureAdminServer()
        .withAdditionalVolume("volume2", "/server-path");

    assertThat(
        getCreatedPodSpec().getVolumes(),
        allOf(hasVolume("volume1", "/domain-path1"), hasVolume("volume2", "/server-path")));
  }

  @Test
  void whenPodHasDuplicateVolumeMounts_createAdminPodWithCombination() {
    getConfigurator()
        .withAdditionalVolumeMount("volume1", "/domain-path1")
        .withAdditionalVolumeMount("volume2", "/domain-path2")
        .configureAdminServer()
        .withAdditionalVolumeMount("volume2", "/server-path");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/domain-path1"), hasVolumeMount("volume2", "/server-path")));
  }

  @Test
  void whenDomainHasInitContainers_createAdminPodWithThem() {
    getConfigurator()
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasInitContainer("container1", "busybox", ADMIN_SERVER, "sh", "-c", "echo admin server && sleep 120"),
            hasInitContainer("container2", "oraclelinux", ADMIN_SERVER, "ls /oracle")));
  }

  @Test
  void whenDomainAndServerHaveAuxiliaryImages_createAdminPodWithInitContainersInCorrectOrderAndVolumeMounts() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");
    Map<String, Object> auxiliaryImage2 =
        createAuxiliaryImage("wdt-image:v2", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createSpecWithAdminServer(
                            Collections.singletonList(auxiliaryImageVolume), Collections.singletonList(auxiliaryImage),
                            createServerPodWithAuxImages(Collections.singletonList(auxiliaryImage2)))));

    assertThat(getCreatedPodSpecInitContainers(),
        allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1",
                "IfNotPresent",
                AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, ADMIN_SERVER),
            Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                "wdt-image:v2",
                "IfNotPresent",
                AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, ADMIN_SERVER)));
    assertThat(Objects.requireNonNull(getCreatedPod().getSpec()).getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName(TEST_VOLUME_NAME)).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName(TEST_VOLUME_NAME))
                    .mountPath(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenServerHasAuxiliaryImageVolumeWithMountPath_createPodWithVolumeMountHavingCorrectMountPath() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(CUSTOM_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent",
            CUSTOM_COMMAND_SCRIPT);

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createSpecWithAdminServer(Collections.singletonList(auxiliaryImageVolume), new ArrayList<>(),
                            createServerPodWithAuxImages(Collections.singletonList(auxiliaryImage)))));

    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(COMPATIBILITY_MODE
                    + getAuxiliaryImageVolumeName()).mountPath(CUSTOM_MOUNT_PATH)));
  }

  @Test
  void whenDomainWithEnvVarHasInitContainers_verifyAdminPodInitContainersHaveEnvVar() {
    getConfigurator().withEnvironmentVariable("item1", "value1")
            .withInitContainer(
                    createContainer("container1", "busybox", "sh",
                            "-c", "echo admin server && sleep 120"))
            .withInitContainer(createContainer("container2", "oraclelinux",
                    "ls /oracle"));

    assertThat(
            getCreatedPodSpecInitContainers(),
            allOf(
                    hasInitContainerWithEnvVar("container1", "busybox", ADMIN_SERVER,
                            new V1EnvVar().name("item1").value("value1"),
                            "sh", "-c", "echo admin server && sleep 120"),
                    hasInitContainerWithEnvVar("container2", "oraclelinux", ADMIN_SERVER,
                            new V1EnvVar().name("item1").value("value1"),  "ls /oracle")));
  }

  @Test
  void whenServerHasInitContainers_createAdminPodWithThem() {
    getConfigurator()
        .configureAdminServer()
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasInitContainer("container1", "busybox", ADMIN_SERVER, "sh", "-c", "echo admin server && sleep 120"),
            hasInitContainer("container2", "oraclelinux", ADMIN_SERVER, "ls /oracle")));
  }

  @Test
  void whenDomainHasAuxiliaryImages_createAdminPodWithInitContainersInCorrectOrderAndVolumeMounts() {
    getConfigurator()
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1", "wdt-image:v2"));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "IfNotPresent"),
                    Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                            "wdt-image:v2", "IfNotPresent")));
    assertThat(Objects.requireNonNull(getCreatedPod().getSpec()).getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasAuxiliaryImageVolumeWithMountPath_createPodWithVolumeMountHavingCorrectMountPath() {
    getConfigurator()
            .withAuxiliaryImageVolumeMountPath(CUSTOM_MOUNT_PATH)
            .withAuxiliaryImages(Collections.singletonList((getAuxiliaryImage("wdt-image:v2"))));

    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).mountPath(CUSTOM_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasLegcayAuxiliaryImages_createAdminPodWithInitContainersInCorrectOrderAndVolumeMounts() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");
    Map<String, Object> auxiliaryImage2 =
        createAuxiliaryImage("wdt-image:v2", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            Arrays.asList(auxiliaryImage, auxiliaryImage2))));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1", "IfNotPresent",
                    AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, ADMIN_SERVER),
                Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                    "wdt-image:v2", "IfNotPresent",
                    AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, ADMIN_SERVER)));
    assertThat(Objects.requireNonNull(getCreatedPod().getSpec()).getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName("test")).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName("test"))
                    .mountPath(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasLegacyAuxiliaryImageVolumeWithMountPath_createPodWithVolumeMountHavingCorrectMountPath() {
    getConfigurator()
            .withAuxiliaryImageVolumeMountPath(CUSTOM_MOUNT_PATH)
            .withAuxiliaryImages(Collections.singletonList((getAuxiliaryImage("wdt-image:v2"))));

    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME, CUSTOM_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");
    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            List.of(auxiliaryImage))));

    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName("test"))
                    .mountPath(CUSTOM_MOUNT_PATH)));
  }

  @Test
  void whenServerWithEnvVarHasInitContainers_verifyAdminPodInitContainersHaveEnvVar() {
    getConfigurator().withEnvironmentVariable("item1", "value1")
            .configureAdminServer()
            .withInitContainer(
                   createContainer("container1", "busybox", "sh", "-c",
                           "echo admin server && sleep 120"))
            .withInitContainer(createContainer("container2", "oraclelinux",
                    "ls /oracle"));

    assertThat(
            getCreatedPodSpecInitContainers(),
            allOf(
                    hasInitContainerWithEnvVar("container1", "busybox", ADMIN_SERVER,
                            new V1EnvVar().name("item1").value("value1"),
                            "sh", "-c", "echo admin server && sleep 120"),
                    hasInitContainerWithEnvVar("container2", "oraclelinux", ADMIN_SERVER,
                            new V1EnvVar().name("item1").value("value1"), "ls /oracle")));
  }

  @Test
  void whenServerHasDuplicateInitContainers_createAdminPodWithCombination() {
    getConfigurator()
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureAdminServer()
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasInitContainer("container1", "busybox", ADMIN_SERVER, "sh", "-c", "echo admin server && sleep 120"),
            hasInitContainer("container2", "oraclelinux", ADMIN_SERVER, "ls /oracle")));
  }

  @Test
  void whenDomainHasContainers_createAdminPodWithThem() {
    getConfigurator()
        .withContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  void whenServerHasContainers_createAdminPodWithThem() {
    getConfigurator()
        .configureAdminServer()
        .withContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  void whenServerHasDuplicateContainers_createAdminPodWithCombination() {
    getConfigurator()
        .withContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureAdminServer()
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  void whenDomainHasLabels_createAdminPodWithThem() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");
    Map<String, String> podLabels = getCreatedPodMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "domain-label-value2"));
  }

  private V1ObjectMeta getCreatedPodMetadata() {
    return getCreatedPod().getMetadata();
  }

  @Test
  void whenDomainHasAnnotations_createAdminPodWithThem() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    Map<String, String> podAnnotations = getCreatedPodMetadata().getAnnotations();

    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "domain-annotation-value2"));
  }

  @Test
  void whenServerHasLabels_createAdminPodWithThem() {
    configureAdminServer()
        .withPodLabel("label1", "server-label-value1")
        .withPodLabel("label2", "server-label-value2");

    Map<String, String> podLabels = getCreatedPodMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "server-label-value1"));
    assertThat(podLabels, hasEntry("label2", "server-label-value2"));
  }

  @Test
  void whenServerHasAnnotations_createAdminPodWithThem() {
    configureAdminServer()
        .withPodAnnotation("annotation1", "server-annotation-value1")
        .withPodAnnotation("annotation2", "server-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPodMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "server-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "server-annotation-value2"));
  }

  @Test
  void whenPodHasDuplicateLabels_createAdminPodWithCombination() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2")
        .configureAdminServer()
        .withPodLabel("label2", "server-label-value1");

    Map<String, String> podLabels = getCreatedPodMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "server-label-value1"));
  }

  @Test
  void whenPodHasDuplicateAnnotations_createAdminPodWithCombination() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2")
        .configureAdminServer()
        .withPodAnnotation("annotation2", "server-annotation-value1");

    Map<String, String> podAnnotations = getCreatedPodMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "server-annotation-value1"));
  }

  @Test
  void whenPodHasCustomLabelConflictWithInternal_createAdminPodWithInternal() {
    getConfigurator()
        .configureAdminServer()
        .withPodLabel(LabelConstants.CREATEDBYOPERATOR_LABEL, "server-label-value1")
        .withPodLabel("label1", "server-label-value1");

    Map<String, String> podLabels = getCreatedPodMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"));
    assertThat(podLabels, hasEntry("label1", "server-label-value1"));
  }

  @Test
  void whenDomainAndAdminHasRestartVersion_createAdminPodWithRestartVersionLabel() {
    getConfigurator()
        .withRestartVersion("domainRestartV1")
        .configureAdminServer()
        .withRestartVersion("adminRestartV1");

    Map<String, String> podLabels = getCreatedPodMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.DOMAINRESTARTVERSION_LABEL, "domainRestartV1"));
    assertThat(podLabels, hasEntry(LabelConstants.SERVERRESTARTVERSION_LABEL, "adminRestartV1"));
    assertThat(podLabels, hasKey(not(LabelConstants.CLUSTERRESTARTVERSION_LABEL)));
  }


  @Test
  void whenDomainHomeChanged_podCycleEventCreatedWithCorrectMessage()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    initializeExistingPod();
    getConfiguredDomainSpec().setDomainHome("adfgg");

    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    assertThat(testSupport, hasEvent(POD_CYCLE_STARTING_EVENT).inNamespace(NS).withMessageContaining(getPodName()));
  }

  @Test
  void whenDomainHomeChanged_podCycleEventCreatedWithCorrectNS()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    initializeExistingPod();
    getConfiguredDomainSpec().setDomainHome("adfgg");

    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    assertContainsEventWithNamespace(POD_CYCLE_STARTING, NS);
  }

  @Test
  void whenDomainHomeChanged_generateExpectedLogMessage()
      throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
    getConsoleHandlerMemento().trackMessage(CYCLING_POD);
    initializeExistingPod();
    getConfiguredDomainSpec().setDomainHome("adfgg");

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getReplacedMessageKey()));
    assertThat(logRecords, containsInfo(CYCLING_POD));
  }

  private V1Pod createTestPodModel() {
    return new V1Pod().metadata(createPodMetadata()).spec(createPodSpec());
  }

  @Override
  V1PodSpec createPodSpec() {
    return super.createPodSpec().hostname("localhost");
  }

  @Override
  V1Container createPodSpecContainer() {
    return super.createPodSpecContainer()
        .addEnvItem(envItem(INTERNAL_OPERATOR_CERT_ENV_NAME, null));
  }

  @Override
  List<String> createStartCommand() {
    return Collections.singletonList("/weblogic-operator/scripts/startServer.sh");
  }

  // todo test that changing the cert in tuning parameters does not change the hash

}

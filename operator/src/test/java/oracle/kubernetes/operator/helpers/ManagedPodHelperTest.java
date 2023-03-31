// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodAffinityTerm;
import io.kubernetes.client.openapi.models.V1PodAntiAffinity;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_INVALID_EVENT_ERROR;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.MANAGED_POD_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.MANAGED_POD_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.MANAGED_POD_PATCHED;
import static oracle.kubernetes.common.logging.MessageKeys.MANAGED_POD_REPLACED;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.LabelConstants.TO_BE_ROLLED_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;
import static oracle.kubernetes.operator.helpers.AdminPodHelperTest.CUSTOM_MOUNT_PATH2;
import static oracle.kubernetes.operator.helpers.DomainIntrospectorJobTest.TEST_VOLUME_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.ManagedPodHelperTest.JavaOptMatcher.hasJavaOption;
import static oracle.kubernetes.operator.helpers.Matchers.hasContainer;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasInitContainer;
import static oracle.kubernetes.operator.helpers.Matchers.hasInitContainerWithEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasPvClaimVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasResourceQuantity;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolumeMount;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("ConstantConditions")
class ManagedPodHelperTest extends PodHelperTestBase {

  private static final String SERVER_NAME = "ess_server1";
  private static final int LISTEN_PORT = 8001;
  private static final String SIP_CLEAR = "sip-clear";
  private static final String SIP_SECURE = "sip-secure";
  private static final String ITEM1 = "item1";
  private static final String ITEM2 = "item2";
  private static final String ITEM3 = "item3";
  private static final String VALUE1 = "value1";
  private static final String VALUE2 = "value2";
  private static final String RAW_VALUE_1 = "find uid1 at $(DOMAIN_HOME)";
  private static final String END_VALUE_1 = "find uid1 at /u01/oracle/user_projects/domains";
  private static final String RAW_VALUE_2 = "$(SERVER_NAME) is not $(ADMIN_NAME):$(ADMIN_PORT)";
  private static final String END_VALUE_2 = "ess_server1 is not ADMIN_SERVER:7001";
  private static final String RAW_VALUE_3 = "ess-base-$(SERVER_NAME)";
  private static final String END_VALUE_3 = "ess-base-ess_server1";
  private static final String END_VALUE_3_DNS1123 = "ess-base-ess-server1";
  private static final String RAW_VALUE_4 = "$(SERVER_NAME)-volume";
  private static final String END_VALUE_4_DNS1123 = "ess-server1-volume";
  private static final String CLUSTER_NAME = "test-cluster";

  public ManagedPodHelperTest() {
    super(SERVER_NAME, LISTEN_PORT);
  }

  @Override
  String getCreatedMessageKey() {
    return MANAGED_POD_CREATED;
  }

  @Override
  String getExistsMessageKey() {
    return MANAGED_POD_EXISTS;
  }

  @Override
  String getPatchedMessageKey() {
    return MANAGED_POD_PATCHED;
  }

  @Override
  String getReplacedMessageKey() {
    return MANAGED_POD_REPLACED;
  }

  @Override
  String getDomainValidationFailedKey() {
    return DOMAIN_VALIDATION_FAILED;
  }

  @Override
  FiberTestSupport.StepFactory getStepFactory() {
    return PodHelper::createManagedPodStep;
  }

  @Override
  ServerConfigurator configureServer() {
    return configureServer(getConfigurator(), SERVER_NAME);
  }

  @SuppressWarnings("SameParameterValue")
  private ServerConfigurator configureServer(DomainConfigurator configurator, String serverName) {
    return configurator.configureServer(serverName);
  }

  @Override
  List<String> createStartCommand() {
    return Collections.singletonList("/weblogic-operator/scripts/startServer.sh");
  }

  @Test
  void whenPodNeedsToRoll_addRollLabel() {
    initializeExistingPod();
    configureServer().withRestartVersion("123");

    assertThat(getCreatedPod().getMetadata().getLabels(), hasEntry(TO_BE_ROLLED_LABEL, "true"));
  }

  @Test
  void whenPodNeedsToRollAndAlreadyMarkedForRoll_dontUpdateRollLabel() {
    initializeExistingPod();
    configureServer().withRestartVersion("123");
    final V1Pod pod = (V1Pod) testSupport.getResources(KubernetesTestSupport.POD).get(0);
    pod.getMetadata().putLabelsItem(TO_BE_ROLLED_LABEL, "true");
    testSupport.doOnUpdate(KubernetesTestSupport.POD, this::reportUnexpectedUpdate);

    assertThat(getCreatedPod().getMetadata().getLabels(), hasEntry(TO_BE_ROLLED_LABEL, "true"));
  }

  private void reportUnexpectedUpdate(@Nonnull Object object) {
    throw new RuntimeException("unexpected update to pod " + getPodName((KubernetesObject) object));
  }

  @Nonnull
  private String getPodName(@Nonnull KubernetesObject object) {
    return Optional.ofNullable(object.getMetadata()).map(V1ObjectMeta::getName).orElse("<unknown>");
  }

  @Test
  void whenManagedPodCreated_containerHasStartServerCommand() {
    assertThat(
        getCreatedPodSpecContainer().getCommand(),
        contains("/weblogic-operator/scripts/startServer.sh"));
  }

  @Test
  void whenPacketHasEnvironmentItems_createManagedPodStartupWithThem() {
    testSupport.addToPacket(
        ProcessingConstants.ENVVARS,
        Arrays.asList(toEnvVar(ITEM1, VALUE1), toEnvVar(ITEM2, VALUE2)));

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar(ITEM1, VALUE1), hasEnvVar(ITEM2, VALUE2)));
  }

  private V1EnvVar toEnvVar(String name, String value) {
    return envItem(name, value);
  }

  @Test
  void whenPacketHasEnvironmentItemsWithVariables_createManagedPodStartupWithSubstitutedValues() {
    testSupport.addToPacket(
        ProcessingConstants.ENVVARS,
        Arrays.asList(toEnvVar(ITEM1, RAW_VALUE_1), toEnvVar(ITEM2, RAW_VALUE_2), toEnvVar(ITEM3, RAW_VALUE_3)));

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar(ITEM1, END_VALUE_1), hasEnvVar(ITEM2, END_VALUE_2), hasEnvVar(ITEM3, END_VALUE_3)));
  }

  @Test
  void whenPacketHasValueFromEnvironmentItems_createManagedPodStartupWithThem() {
    V1EnvVar configMapKeyRefEnvVar = createConfigMapKeyRefEnvVar("VARIABLE1", "my-env", "VAR1");
    V1EnvVar secretKeyRefEnvVar = createSecretKeyRefEnvVar("VARIABLE2", "my-secret", "VAR2");
    V1EnvVar fieldRefEnvVar = createFieldRefEnvVar("MY_NODE_IP", "status.hostIP");


    testSupport.addToPacket(
        ProcessingConstants.ENVVARS,
        Arrays.asList(configMapKeyRefEnvVar, secretKeyRefEnvVar, fieldRefEnvVar));

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasItem(configMapKeyRefEnvVar), hasItem(secretKeyRefEnvVar), hasItem(fieldRefEnvVar)));
  }

  @Test
  void whenPacketHasValueFromEnvironmentItemsWithVariables_createManagedPodStartupWithSubstitutions() {
    V1EnvVar configMapKeyRefEnvVar = createConfigMapKeyRefEnvVar(ITEM1, "my-env", RAW_VALUE_1);
    V1EnvVar secretKeyRefEnvVar = createSecretKeyRefEnvVar(ITEM2, "my-secret", RAW_VALUE_2);
    V1EnvVar fieldRefEnvVar = createFieldRefEnvVar(ITEM3, RAW_VALUE_3);

    testSupport.addToPacket(
        ProcessingConstants.ENVVARS,
        Arrays.asList(configMapKeyRefEnvVar, secretKeyRefEnvVar, fieldRefEnvVar));

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(
            hasItem(createConfigMapKeyRefEnvVar(ITEM1, "my-env", END_VALUE_1)),
            hasItem(createSecretKeyRefEnvVar(ITEM2, "my-secret", END_VALUE_2)),
            hasItem(createFieldRefEnvVar(ITEM3, END_VALUE_3))
        )
    );
  }

  @Test
  void whenClusterHasAdditionalVolumesWithReservedVariables_createManagedPodWithSubstitutions() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withAdditionalVolume("volume1", "/source-$(SERVER_NAME)")
        .withAdditionalVolume("volume2", "/source-$(DOMAIN_NAME)");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(
            hasVolume("volume1", "/source-" + SERVER_NAME),
            hasVolume("volume2", "/source-domain1")));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithReservedVariables_createManagedPodWithSubstitutions() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/source-$(SERVER_NAME)")
        .withAdditionalVolume("volume2", "/source-$(DOMAIN_NAME)");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(
            hasVolume("volume1", "/source-" + SERVER_NAME),
            hasVolume("volume2", "/source-domain1")));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithCustomVariables_createManagedPodWithSubstitutions() {
    resourceLookup.defineResource(SECRET_NAME, V1Secret.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, V1ConfigMap.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, V1ConfigMap.class, NS);

    V1EnvVar envVar = new V1EnvVar().name(ENV_NAME1).value(GOOD_MY_ENV_VALUE);
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.singletonList(envVar));

    getConfigurator()
        .withWebLogicCredentialsSecret(SECRET_NAME)
        .withAdditionalVolume("volume1", VOLUME_PATH_1)
        .withAdditionalVolumeMount("volume1", VOLUME_MOUNT_PATH_1);

    testSupport.runSteps(PodHelper.createManagedPodStep(terminalStep));

    assertThat(testSupport.getResources(KubernetesTestSupport.POD).isEmpty(), is(false));
    assertThat(logRecords, containsInfo(getCreatedMessageKey()));
    assertThat(getCreatedPod().getSpec().getContainers().get(0).getVolumeMounts(),
        hasVolumeMount("volume1", END_VOLUME_MOUNT_PATH_1));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithCustomVariablesContainInvalidValue_reportValidationError() {
    resourceLookup.defineResource(SECRET_NAME, V1Secret.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, V1ConfigMap.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, V1ConfigMap.class, NS);

    V1EnvVar envVar = new V1EnvVar().name(ENV_NAME1).value(BAD_MY_ENV_VALUE);
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.singletonList(envVar));

    getConfigurator()
        .withWebLogicCredentialsSecret(SECRET_NAME)
        .withAdditionalVolume("volume1", VOLUME_PATH_1)
        .withAdditionalVolumeMount("volume1", VOLUME_MOUNT_PATH_1);

    testSupport.runSteps(PodHelper.createManagedPodStep(terminalStep));

    assertThat(testSupport.getResources(KubernetesTestSupport.POD).isEmpty(), is(true));
    assertThat(getDomain().getStatus().getReason(), is(DOMAIN_INVALID.toString()));
    assertThat(logRecords, containsSevere(getDomainValidationFailedKey()));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithCustomVariablesContainInvalidValue_createFailedEvent() {
    resourceLookup.defineResource(SECRET_NAME, V1Secret.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_MODEL, V1ConfigMap.class, NS);
    resourceLookup.defineResource(OVERRIDES_CM_NAME_IMAGE, V1ConfigMap.class, NS);

    V1EnvVar envVar = new V1EnvVar().name(ENV_NAME1).value(BAD_MY_ENV_VALUE);
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.singletonList(envVar));

    getConfigurator()
        .withWebLogicCredentialsSecret(SECRET_NAME)
        .withAdditionalVolume("volume1", VOLUME_PATH_1)
        .withAdditionalVolumeMount("volume1", VOLUME_MOUNT_PATH_1);

    testSupport.runSteps(PodHelper.createManagedPodStep(terminalStep));
    logRecords.clear();

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(DOMAIN_INVALID_EVENT_ERROR)));
  }

  @Test
  void whenClusterHasLabelsWithVariables_createManagedPodWithSubstitutions() {
    V1EnvVar envVar = toEnvVar("TEST_ENV", "test-value");
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.singletonList(envVar));

    V1Container container = new V1Container()
        .name("test")
        .addCommandItem("/bin/bash")
        .addArgsItem("echo")
        .addArgsItem("This server is $(SERVER_NAME) and has $(TEST_ENV)");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .withLogHomeEnabled(true)
        .withContainer(container)
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withPodLabel("myCluster", "my-$(CLUSTER_NAME)")
        .withPodLabel("logHome", "$(LOG_HOME)");

    V1Pod pod = getCreatedPod();
    assertThat(
        pod.getMetadata().getLabels(),
        allOf(
            hasEntry("myCluster", "my-" + CLUSTER_NAME),
            hasEntry("logHome", "/shared/logs/" +  UID)));
    Optional<V1Container> o = pod.getSpec().getContainers()
        .stream().filter(c -> "test".equals(c.getName())).findFirst();
    assertThat(o.orElseThrow().getArgs(), hasItem("This server is " +  SERVER_NAME + " and has test-value"));
    assertThat(container.getArgs(), hasItem("This server is $(SERVER_NAME) and has $(TEST_ENV)")
    );
  }

  @Test
  void createManagedPodStartupWithNullAdminUsernamePasswordEnvVarsValues() {
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.emptyList());

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  void whenPacketHasEnvironmentItemsWithVariable_createManagedPodShouldNotChangeItsValue() {
    V1EnvVar envVar = toEnvVar(ITEM1, RAW_VALUE_1);
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.singletonList(envVar));

    getCreatedPodSpecContainer();

    assertThat(envVar.getValue(), is(RAW_VALUE_1));
  }

  @Test
  void whenPacketHasClusterConfig_managedPodHasClusterLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getMetadata().getLabels(),
        hasEntry(LabelConstants.CLUSTERNAME_LABEL, CLUSTER_NAME));
  }

  @Test
  void whenDomainHasAdditionalVolumesWithVariables_createManagedPodWithThem() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/$(SERVER_NAME)/source-path1/")
        .withAdditionalVolume("volume2", "/$(SERVER_NAME)/source-path2/")
        .withAdditionalVolume(RAW_VALUE_4, "/source-path3/");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(
            hasVolume("volume1", "/ess_server1/source-path1/"),
            hasVolume("volume2", "/ess_server1/source-path2/"),
            hasVolume(END_VALUE_4_DNS1123, "/source-path3/")));
  }

  @Test
  void whenDomainHasAdditionalPvClaimVolumesWitVariables_createManagedPodWithThem() {
    getConfigurator()
        .withAdditionalPvClaimVolume(RAW_VALUE_4, RAW_VALUE_3);

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        hasPvClaimVolume(END_VALUE_4_DNS1123, END_VALUE_3_DNS1123));
  }

  @Test
  void whenDomainHasAdditionalVolumeMounts_createManagedPodWithThem() {
    getConfigurator()
        .withAdditionalVolumeMount("volume1", "/destination-path1")
        .withAdditionalVolumeMount("volume2", "/destination-path2");
    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/destination-path1"),
            hasVolumeMount("volume2", "/destination-path2")));
  }

  @Test
  void whenClusterHasAdditionalVolumes_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  void whenClusterHasAdditionalVolumeMounts_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withAdditionalVolumeMount("volume1", "/destination-path1")
        .withAdditionalVolumeMount("volume2", "/destination-path2");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/destination-path1"),
            hasVolumeMount("volume2", "/destination-path2")));
  }

  @Test
  void whenServerHasAdditionalVolumes_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  void whenServerHasAdditionalVolumeMounts_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withAdditionalVolumeMount("volume1", "/destination-path1")
        .withAdditionalVolumeMount("volume2", "/destination-path2");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/destination-path1"),
            hasVolumeMount("volume2", "/destination-path2")));
  }

  @Test
  void whenPodHasDuplicateVolumes_createManagedPodWithCombination() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/domain-path1")
        .withAdditionalVolume("volume2", "/domain-path2")
        .withAdditionalVolume("volume3", "/domain-path3")
        .configureServer(SERVER_NAME)
        .withAdditionalVolume("volume3", "/server-path");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withAdditionalVolume("volume2", "/cluster-path");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(
            hasVolume("volume1", "/domain-path1"),
            hasVolume("volume2", "/cluster-path"),
            hasVolume("volume3", "/server-path")));
  }

  @Test
  void whenPodHasDuplicateVolumeMounts_createManagedPodWithCombination() {
    getConfigurator()
        .withAdditionalVolumeMount("volume1", "/domain-path1")
        .withAdditionalVolumeMount("volume2", "/domain-path2")
        .withAdditionalVolumeMount("volume3", "/domain-path3")
        .configureServer(SERVER_NAME)
        .withAdditionalVolumeMount("volume3", "/server-path");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withAdditionalVolumeMount("volume2", "/cluster-path");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/domain-path1"),
            hasVolumeMount("volume2", "/cluster-path"),
            hasVolumeMount("volume3", "/server-path")));
  }

  @Test
  void whenDomainHasInitContainers_createPodWithThem() {
    getConfigurator()
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasInitContainer("container1", "busybox", SERVER_NAME, "sh", "-c", "echo managed server && sleep 120"),
            hasInitContainer("container2", "oraclelinux", SERVER_NAME, "ls /oracle")));
  }

  @Test
  void whenServerHasInitContainers_createPodWithThem() {
    getConfigurator()
        .configureServer(SERVER_NAME)
        .withInitContainer(
            createContainer(
                "container1", "busybox",  "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasInitContainer("container1", "busybox", SERVER_NAME, "sh", "-c", "echo managed server && sleep 120"),
            hasInitContainer("container2", "oraclelinux", SERVER_NAME, "ls /oracle")));
  }

  @Test
  void whenInitContainersHaveEnvVar_verifyInitContainersAfterPopulatingEnvStillHaveOriginalEnvVar() {
    V1EnvVar envVar = toEnvVar(ITEM1, END_VALUE_1);
    List<V1EnvVar> envVars = new ArrayList<>();
    envVars.add(envVar);
    getConfigurator()
            .configureServer(SERVER_NAME)
            .withInitContainer(
                    createContainer("container1", "busybox",
                            "sh", "-c",
                            "echo managed server && sleep 120").env(envVars))
            .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle").env(envVars));

    assertThat(
            getCreatedPodSpecInitContainers(),
            allOf(
                    hasInitContainerWithEnvVar("container1", "busybox", SERVER_NAME, envVar,
                            "sh", "-c", "echo managed server && sleep 120"),
                    hasInitContainerWithEnvVar("container2", "oraclelinux", SERVER_NAME, envVar,
                            "ls /oracle")));
  }

  @Test
  void whenInitContainersHaveEnvVar_verifyInitContainersEnvVarTakesPrecedenceOverPreConfiguredEnvVar() {
    V1EnvVar envVar = toEnvVar("DOMAIN_NAME", "LOCAL_DOMAIN_NAME");
    List<V1EnvVar> envVars = new ArrayList<>();
    envVars.add(envVar);
    getConfigurator()
            .configureServer(SERVER_NAME)
            .withInitContainer(
                    createContainer("container1", "busybox",
                            "sh", "-c",
                            "echo managed server && sleep 120").env(envVars))
            .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle").env(envVars));

    assertThat(
            getCreatedPodSpecInitContainers(),
            allOf(
                    hasInitContainerWithEnvVar("container1", "busybox", SERVER_NAME, envVar,
                            "sh", "-c", "echo managed server && sleep 120"),
                    hasInitContainerWithEnvVar("container2", "oraclelinux", SERVER_NAME, envVar,
                            "ls /oracle")));
  }

  @Test
  void whenServerWithEnvVarHasInitContainers_verifyInitContainersHaveEnvVar() {
    V1EnvVar envVar = toEnvVar(ITEM1, END_VALUE_1);
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.singletonList(envVar));

    getConfigurator()
            .configureServer(SERVER_NAME)
            .withInitContainer(
                    createContainer("container1", "busybox",
                            "sh", "-c",
                            "echo managed server && sleep 120"))
            .withInitContainer(createContainer("container2", "oraclelinux",
                    "ls /oracle"));

    assertThat(
            getCreatedPodSpecInitContainers(),
            allOf(
                    hasInitContainerWithEnvVar("container1", "busybox", SERVER_NAME, envVar,
                            "sh", "-c", "echo managed server && sleep 120"),
                    hasInitContainerWithEnvVar("container2", "oraclelinux", SERVER_NAME, envVar,
                            "ls /oracle")));
  }

  @Test
  void whenServerHasDuplicateInitContainers_createPodWithCombination() {
    getConfigurator()
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasInitContainer("container1", "busybox", SERVER_NAME, "sh", "-c", "echo managed server && sleep 120"),
            hasInitContainer("container2", "oraclelinux", SERVER_NAME, "ls /oracle")));
  }

  @Test
  void whenClusterHasInitContainers_createPodWithThem() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withInitContainer(
            createContainer(
                "container1", "busybox",  "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasInitContainer("container1", "busybox", SERVER_NAME, "sh", "-c", "echo managed server && sleep 120"),
            hasInitContainer("container2", "oraclelinux", SERVER_NAME, "ls /oracle")));
  }

  @Test
  void whenServerAndClusterHasDuplicateInitContainers_createPodWithCombination() {
    getConfigurator()
        .withInitContainer(
            createContainer(
                "container1", "busybox","sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"))
        .withInitContainer(createContainer("container3", "oraclelinux", "ls /cluster"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasInitContainer("container1", "busybox", SERVER_NAME, "sh", "-c", "echo cluster && sleep 120"),
            hasInitContainer("container2", "oraclelinux", SERVER_NAME, "ls /oracle"),
            hasInitContainer("container3", "oraclelinux", SERVER_NAME, "ls /cluster")));
  }

  @Test
  void whenDomainHasContainers_createPodWithThem() {
    getConfigurator()
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  void whenServerHasContainers_createPodWithThem() {
    getConfigurator()
        .configureServer(SERVER_NAME)
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  void whenServerHasDuplicateContainers_createPodWithCombination() {
    getConfigurator()
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  void whenClusterHasContainers_createPodWithThem() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  void whenServerAndClusterHasDuplicateContainers_createPodWithCombination() {
    getConfigurator()
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"))
        .withContainer(createContainer("container3", "oraclelinux", "ls /cluster"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle"),
            hasContainer("container3", "oraclelinux", "ls /cluster")));
  }

  @Test
  void whenDomainHasLabels_createManagedPodWithThem() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "domain-label-value2"));
  }

  @Test
  void whenDomainHasAnnotations_createManagedPodWithThem() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "domain-annotation-value2"));
  }

  @Test
  void whenClusterHasLabels_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withPodLabel("label1", "cluster-label-value1")
        .withPodLabel("label2", "cluster-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "cluster-label-value1"));
    assertThat(podLabels, hasEntry("label2", "cluster-label-value2"));
  }

  @Test
  void whenClusterHasRestartVersion_createManagedPodWithRestartVersionLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(domainPresenceInfo, CLUSTER_NAME).withRestartVersion("clusterRestartV1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.CLUSTERRESTARTVERSION_LABEL, "clusterRestartV1"));
    assertThat(podLabels, hasKey(not(LabelConstants.DOMAINRESTARTVERSION_LABEL)));
    assertThat(podLabels, hasKey(not(LabelConstants.SERVERRESTARTVERSION_LABEL)));
  }

  @Test
  void whenDomainHasRestartVersion_createManagedPodWithRestartVersionLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().withRestartVersion("domainRestartV1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.DOMAINRESTARTVERSION_LABEL, "domainRestartV1"));
    assertThat(podLabels, hasKey(not(LabelConstants.CLUSTERRESTARTVERSION_LABEL)));
    assertThat(podLabels, hasKey(not(LabelConstants.SERVERRESTARTVERSION_LABEL)));
  }

  @Test
  void whenClusterHasAnnotations_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withPodAnnotation("annotation1", "cluster-annotation-value1")
        .withPodAnnotation("annotation2", "cluster-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "cluster-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "cluster-annotation-value2"));
  }

  @Test
  void whenServerHasLabels_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withPodLabel("label1", "server-label-value1")
        .withPodLabel("label2", "server-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "server-label-value1"));
    assertThat(podLabels, hasEntry("label2", "server-label-value2"));
  }

  @Test
  void whenServerHasAnnotations_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withPodAnnotation("annotation1", "server-annotation-value1")
        .withPodAnnotation("annotation2", "server-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "server-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "server-annotation-value2"));
  }

  @Test
  void whenPodHasDuplicateLabels_createManagedPodWithCombination() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2")
        .withPodLabel("label3", "domain-label-value3")
        .configureServer(SERVER_NAME)
        .withPodLabel("label3", "server-label-value1");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withPodLabel("label2", "cluster-label-value1")
        .withPodLabel("label3", "cluster-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "cluster-label-value1"));
    assertThat(podLabels, hasEntry("label3", "server-label-value1"));
  }

  @Test
  void whenPodHasDuplicateAnnotations_createManagedPodWithCombination() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2")
        .withPodAnnotation("annotation3", "domain-annotation-value3")
        .configureServer(SERVER_NAME)
        .withPodAnnotation("annotation3", "server-annotation-value1");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withPodAnnotation("annotation2", "cluster-annotation-value1")
        .withPodAnnotation("annotation3", "cluster-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "cluster-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation3", "server-annotation-value1"));
  }

  @Test
  void whenPodHasCustomLabelConflictWithInternal_createManagedPodWithInternal() {
    getConfigurator()
        .configureServer((SERVER_NAME))
        .withPodLabel(LabelConstants.CREATEDBYOPERATOR_LABEL, "server-label-value1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"));
  }

  @Test
  void whenClusterHasNoAffinity_createdPodHasDefaultAntiAffinity() {
    getConfigurator().configureCluster(domainPresenceInfo, CLUSTER_NAME);

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(getCreatePodAffinity(), is(getDefaultAntiAffinity()));
  }

  private V1Affinity getDefaultAntiAffinity() {
    return new AffinityHelper().clusterName(CLUSTER_NAME).domainUID(UID).getAntiAffinity();
  }

  @Test
  void whenClusterHasAffinity_createPodWithIt() {
    getConfigurator().configureCluster(domainPresenceInfo, CLUSTER_NAME).withAffinity(affinity);
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(getCreatePodAffinity(), is(affinity));
  }

  @Test
  void whenClusterHasNodeSelector_createPodWithIt() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withNodeSelector("os_arch", "x86_64");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getNodeSelector(),
        hasEntry("os_arch", "x86_64"));
  }

  @Test
  void whenClusterHasNodeName_createPodWithIt() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withNodeName("kube-01");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getNodeName(),
        is("kube-01"));
  }

  @Test
  void whenClusterHasSchedulerName_createPodWithIt() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withSchedulerName("my-scheduler");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getSchedulerName(),
        is("my-scheduler"));
  }

  @Test
  void whenClusterHasRuntimeClassName_createPodWithIt() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withRuntimeClassName("RuntimeClassName");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getRuntimeClassName(),
        is("RuntimeClassName"));
  }

  @Test
  void whenClusterHasPriorityClassName_createPodWithIt() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withPriorityClassName("PriorityClassName");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getPriorityClassName(),
        is("PriorityClassName"));
  }

  @Test
  void whenClusterHasRestartPolicy_createPodWithIt() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withRestartPolicy("Always");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getRestartPolicy(),
        is("Always"));
  }

  @Test
  void whenClusterHasPodSecurityContext_createPodWithIt() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withPodSecurityContext(podSecurityContext);
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getSecurityContext(),
        is(podSecurityContext));
  }

  @Test
  void whenClusterHasContainerSecurityContext_createContainersWithIt() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withContainerSecurityContext(containerSecurityContext);
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    getCreatedPodSpecContainers()
        .forEach(c -> assertThat(
            c.getSecurityContext(),
            is(containerSecurityContext)));
  }

  @Test
  void whenClusterHasResources_createContainersWithThem() {
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withLimitRequirement("cpu", "1Gi")
        .withRequestRequirement("memory", "250m");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    List<V1Container> containers = getCreatedPodSpecContainers();

    containers.forEach(c -> assertThat(c.getResources().getLimits(), hasResourceQuantity("cpu", "1Gi")));
    containers.forEach(c -> assertThat(c.getResources().getRequests(), hasResourceQuantity("memory", "250m")));
  }

  @Test
  @Disabled("FIXME: Test requires webhook v8 domain to Cluster resource conversion")
  void whenDomainAndClusterHaveLegacyAuxImages_createManagedPodsWithInitContainersInCorrectOrderAndVolumeMounts() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");
    Map<String, Object> auxiliaryImage2 =
        createAuxiliaryImage("wdt-image:v2", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createSpecWithClusters(
                            Collections.singletonList(auxiliaryImageVolume), Collections.singletonList(auxiliaryImage),
                            createClusterSpecWithAuxImages(Collections.singletonList(auxiliaryImage2), CLUSTER_NAME))));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(getCreatedPodSpecInitContainers(),
        allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1", "IfNotPresent",
                AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, SERVER_NAME),
            Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                "wdt-image:v2", "IfNotPresent",
                AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, SERVER_NAME)));
    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName()).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName())
                    .mountPath(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenClusterHasLegacyAuxiliaryImageAndVolumeHasMountPath_volumeMountsCreatedWithSpecifiedMountPath() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME, CUSTOM_MOUNT_PATH2);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                        List.of(auxiliaryImage))));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName()).mountPath(CUSTOM_MOUNT_PATH2)));
  }

  @Test
  void whenDomainAndManagedServersHaveLegacyAuxImages_createManagedPodsWithInitContainersInCorrectOrderAndMounts() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");
    Map<String, Object> auxiliaryImage2 =
        createAuxiliaryImage("wdt-image:v2", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createSpecWithManagedServers(
                            Collections.singletonList(auxiliaryImageVolume), Collections.singletonList(auxiliaryImage),
                            createManagedServersSpecWithAuxImages(
                                    Collections.singletonList(auxiliaryImage2), SERVER_NAME))));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "IfNotPresent",
                    AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, SERVER_NAME),
                    Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                            "wdt-image:v2", "IfNotPresent",
                        AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, SERVER_NAME)));
    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName()).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName())
                    .mountPath(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasAuxImages_createManagedPodsWithInitContainersInCorrectOrderAndVolumeMounts() {
    getConfigurator()
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1", "wdt-image:v2"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1", "IfNotPresent"),
                Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                        "wdt-image:v2", "IfNotPresent")));
    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasAuxiliaryImageAndVolumeHasMountPath_volumeMountsCreatedWithSpecifiedMountPath() {
    getConfigurator().withAuxiliaryImageVolumeMountPath(CUSTOM_MOUNT_PATH2)
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v2")));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).mountPath(CUSTOM_MOUNT_PATH2)));
  }

  @Test
  void whenClusterHasAffinityWithVariables_createManagedPodWithSubstitutions() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withAffinity(
            new V1Affinity().podAntiAffinity(
                new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
                    Collections.singletonList(
                          createWeightedPodAffinityTerm("weblogic.clusterName", "$(CLUSTER_NAME)")))));

    assertThat(getCreatePodAffinity(), is(
        new AffinityHelper().clusterName(CLUSTER_NAME).getAntiAffinity()));
  }

  V1WeightedPodAffinityTerm createWeightedPodAffinityTerm(String key, String valuesItem) {
    return new V1WeightedPodAffinityTerm().weight(100).podAffinityTerm(
          new V1PodAffinityTerm().labelSelector(
                new V1LabelSelector().matchExpressions(
                      Collections.singletonList(new V1LabelSelectorRequirement()
                            .key(key)
                            .operator("In")
                            .addValuesItem(valuesItem))))
                .topologyKey("kubernetes.io/hostname"));
  }

  @Test
  void whenClusterHasEmptyAffinity_createClusteredManagedPodWithEmptyAffinity() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
           .withAffinity(new V1Affinity());

    assertThat(getCreatePodAffinity(), is(new V1Affinity()));
  }

  @Test
  void whenDomainHasAffinityAndClusterHasEmptyAffinity_createClusteredManagedPodWithEmptyAffinity() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .withAffinity(
            new V1Affinity().podAntiAffinity(
                new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
                    Collections.singletonList(
                          createWeightedPodAffinityTerm("weblogic.domainUID", "$(DOMAIN_UID)")))))
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
          .withAffinity(new V1Affinity());

    assertThat(getCreatePodAffinity(), is(new V1Affinity()));
  }

  @Test
  void whenDomainHasAffinityAndClusterHasNoAffinity_createManagedPodsWithDomainLevelAffinityPolicies() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .withAffinity(
            new V1Affinity().podAntiAffinity(
                new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
                    Collections.singletonList(
                        createWeightedPodAffinityTerm("weblogic.domainUID", "$(DOMAIN_UID)")))))
        .configureCluster(domainPresenceInfo, CLUSTER_NAME);

    V1Affinity expectedValue = new V1Affinity().podAntiAffinity(
        new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
            List.of(createWeightedPodAffinityTerm("weblogic.domainUID", UID))));

    assertThat(getCreatePodAffinity(), is(expectedValue));
  }

  @Test
  void whenDomainAndClusterBothHaveAffinityWithVariables_createManagedPodWithClusterAffinityAndSubstitutions() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .withAffinity(
            new V1Affinity().podAntiAffinity(
                new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
                    Collections.singletonList(
                        createWeightedPodAffinityTerm("weblogic.domainUID", "$(DOMAIN_UID)")))))
        .configureCluster(domainPresenceInfo, CLUSTER_NAME)
        .withAffinity(
            new V1Affinity().podAntiAffinity(
                new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
                    Collections.singletonList(
                        createWeightedPodAffinityTerm("weblogic.clusterName", "$(CLUSTER_NAME)")))));

    V1Affinity expectedValue = new V1Affinity().podAntiAffinity(
        new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
            List.of(createWeightedPodAffinityTerm("weblogic.clusterName", CLUSTER_NAME))));

    assertThat(getCreatePodAffinity(), is(expectedValue));
  }

  @Test
  void whenDomainHasMonitoringExporterConfiguration_createContainerWithExporterSidecar() {
    defineExporterConfiguration();

    assertThat(getExporterContainer(), is(notNullValue()));
  }

  @Test
  void whenPlaintextPortAvailable_monitoringExporterSpecifiesIt() {
    defineExporterConfiguration();

    assertThat(getExporterContainer(), hasJavaOption("-DWLS_PORT=" + LISTEN_PORT));
  }

  @Test
  void whenOnlySslPortAvailable_monitoringExporterSpecifiesIt() {
    getServerTopology().setListenPort(null);
    getServerTopology().setSslListenPort(7002);
    defineExporterConfiguration();

    assertThat(getExporterContainer(),
          both(hasJavaOption("-DWLS_PORT=7002")).and(hasJavaOption("-DWLS_SECURE=true")));
  }

  @Test
  void whenOnlyAdminAndSslPortsAvailable_monitoringExporterSpecifiesAdminPort() {
    getServerTopology().setListenPort(null);
    getServerTopology().setSslListenPort(7002);
    getServerTopology().setAdminPort(8001);
    defineExporterConfiguration();

    assertThat(getExporterContainer(),
               both(hasJavaOption("-DWLS_PORT=8001")).and(hasJavaOption("-DWLS_SECURE=true")));
  }


  @Override
  void setServerPort(int port) {
    getServerTopology().setListenPort(port);
  }

  @Override
  String getReferencePlainPortPodYaml_3_0() {
    return ReferenceObjects.MANAGED_PLAINPORT_POD_3_0;
  }

  @Override
  String getReferencePlainPortPodYaml_3_1() {
    return ReferenceObjects.MANAGED_PLAINPORT_POD_3_1;
  }

  @Override
  String getReferenceSslPortPodYaml_3_0() {
    return ReferenceObjects.MANAGED_SSLPORT_POD_3_0;
  }

  @Override
  String getReferenceSslPortPodYaml_3_1() {
    return ReferenceObjects.MANAGED_SSLPORT_POD_3_1;
  }

  @Override
  String getReferenceMiiPodYaml() {
    return ReferenceObjects.MANAGED_MII_POD_3_1;
  }

  @Override
  String getReferenceMiiAuxImagePodYaml_3_3() {
    return ReferenceObjects.MANAGED_MII_AUX_IMAGE_POD_3_3;
  }

  @Override
  String getReferenceMiiAuxImagePodYaml_4_0() {
    return ReferenceObjects.MANAGED_MII_AUX_IMAGE_POD_4_0;
  }

  @Override
  String getReferenceMiiConvertedAuxImagePodYaml_3_4() {
    return ReferenceObjects.MANAGED_MII_CONVERTED_AUX_IMAGE_POD_3_4;
  }

  @Override
  String getReferenceMiiConvertedAuxImagePodYaml_3_4_1() {
    return ReferenceObjects.MANAGED_MII_CONVERTED_AUX_IMAGE_POD_3_4_1;
  }

  @Override
  String getReferenceIstioMonitoringExporterTcpProtocol() {
    return ReferenceObjects.MANAGED_ISTIO_MONITORING_EXPORTER_TCP_PROTOCOL;
  }

  @Override
  protected void verifyPodReplaced() {
    assertThat(computePodsToRoll(), not(anEmptyMap()));
  }

  private Map<String, StepAndPacket> computePodsToRoll() {
    Map<String, StepAndPacket> rolling = new HashMap<>();
    testSupport.addToPacket(SERVERS_TO_ROLL, rolling);

    testSupport.runSteps(getStepFactory(), terminalStep);
    return rolling;
  }

  @Override
  protected void verifyPodNotReplacedWhen(PodMutator mutator) {
    V1Pod existingPod = createPod(testSupport.getPacket());
    mutator.mutate(existingPod);
    initializeExistingPod(existingPod);

    Map<String, StepAndPacket> rolling = computePodsToRoll();

    assertThat(rolling, is(anEmptyMap()));
    assertThat(logRecords, containsFine(getExistsMessageKey()));
  }

  @Override
  V1Pod createPod(Packet packet) {
    return createManagedServerPodModel(packet);
  }

  private static V1Pod createManagedServerPodModel(Packet packet) {
    return new PodHelper.ManagedPodStepContext(null, packet).getPodModel();
  }

  @Test
  void whenPodCreated_containerHasTwoPortsForSip() {
    addSipPorts();

    V1Container v1Container = getCreatedPodSpecContainer();

    assertThat(v1Container.getPorts().stream()
        .filter(p -> p.getName().endsWith(SIP_CLEAR)).count(), equalTo(2L));
    assertThat(v1Container.getPorts().stream()
        .filter(p -> p.getName().equals(SIP_CLEAR)).findFirst().orElseThrow().getProtocol(),
        equalTo("TCP"));
    assertThat(v1Container.getPorts().stream()
        .filter(p -> p.getName().equals("udp-" + SIP_CLEAR))
        .findFirst().orElseThrow().getProtocol(), equalTo("UDP"));
  }

  void addSipPorts() {
    getServerTopology()
        .addNetworkAccessPoint(SIP_CLEAR, "sip", 8003)
        .addNetworkAccessPoint(SIP_SECURE, "sips", 8004);
  }

  @Test
  void whenPodCreated_containerHasTwoPortsForSips() {
    addSipPorts();
    V1Container v1Container = getCreatedPodSpecContainer();

    assertThat(v1Container.getPorts().stream()
        .filter(p -> p.getName().endsWith(SIP_SECURE)).count(), equalTo(2L));
    assertThat(v1Container.getPorts().stream()
        .filter(p -> p.getName().equals(SIP_SECURE)).findFirst().orElseThrow().getProtocol(),
        equalTo("TCP"));
    assertThat(v1Container.getPorts().stream()
        .filter(p -> p.getName().equals("udp-" + SIP_SECURE))
        .findFirst().orElseThrow().getProtocol(), equalTo("UDP"));
  }

  @SuppressWarnings("unused")
  static class JavaOptMatcher extends TypeSafeDiagnosingMatcher<V1Container> {
    private final String expectedOption;

    private JavaOptMatcher(String expectedOption) {
      this.expectedOption = expectedOption;
    }

    static JavaOptMatcher hasJavaOption(String expectedOption) {
      return new JavaOptMatcher(expectedOption);
    }

    @Override
    protected boolean matchesSafely(V1Container container, Description mismatchDescription) {
      if (getJavaOptions(container).contains(expectedOption)) {
        return true;
      } else {
        mismatchDescription.appendText("JAVA_OPTS is ").appendValue(getJavaOptEnv(container));
        return false;
      }
    }

    private List<String> getJavaOptions(V1Container container) {
      return Optional.of(getJavaOptEnv(container))
            .map(s -> s.split(" "))
            .map(Arrays::asList)
            .orElse(Collections.emptyList());
    }

    @Nonnull
    private String getJavaOptEnv(V1Container container) {
      return getEnvironmentVariables(container).stream()
            .filter(env -> "JAVA_OPTS".equals(env.getName()))
            .map(V1EnvVar::getValue)
            .findFirst()
            .orElse("");
    }

    @Nonnull
    private List<V1EnvVar> getEnvironmentVariables(V1Container container) {
      return Optional.of(container).map(V1Container::getEnv).orElse(Collections.emptyList());
    }

    @Override
    public void describeTo(Description description) {
      description.appendText("JAVA_OPTS containing ").appendValue(expectedOption);
    }
  }

}

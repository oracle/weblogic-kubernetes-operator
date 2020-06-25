// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodAffinityTerm;
import io.kubernetes.client.openapi.models.V1PodAntiAffinity;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import org.junit.Test;

import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;
import static oracle.kubernetes.operator.WebLogicConstants.ADMIN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.helpers.Matchers.hasContainer;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasPvClaimVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasResourceQuantity;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolumeMount;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_PATCHED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_POD_REPLACED;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anEmptyMap;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ManagedPodHelperTest extends PodHelperTestBase {

  private static final String SERVER_NAME = "ess_server1";
  private static final int LISTEN_PORT = 8001;
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
  FiberTestSupport.StepFactory getStepFactory() {
    return PodHelper::createManagedPodStep;
  }

  @Override
  ServerConfigurator configureServer() {
    return configureServer(getConfigurator(), SERVER_NAME);
  }

  @Override
  protected ServerConfigurator configureServer(DomainConfigurator configurator, String serverName) {
    return configurator.configureServer(serverName);
  }

  @Override
  V1Pod createTestPodModel() {
    return new V1Pod().metadata(createPodMetadata()).spec(createPodSpec());
  }

  @Override
  List<String> createStartCommand() {
    return Collections.singletonList("/weblogic-operator/scripts/startServer.sh");
  }

  @Test
  public void whenManagedPodCreated_containerHasStartServerCommand() {
    assertThat(
        getCreatedPodSpecContainer().getCommand(),
        contains("/weblogic-operator/scripts/startServer.sh"));
  }

  @Test
  public void whenPacketHasEnvironmentItems_createManagedPodStartupWithThem() {
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
  public void whenPacketHasEnvironmentItemsWithVariables_createManagedPodStartupWithSubstitutedValues() {
    testSupport.addToPacket(
        ProcessingConstants.ENVVARS,
        Arrays.asList(toEnvVar(ITEM1, RAW_VALUE_1), toEnvVar(ITEM2, RAW_VALUE_2), toEnvVar(ITEM3, RAW_VALUE_3)));

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar(ITEM1, END_VALUE_1), hasEnvVar(ITEM2, END_VALUE_2), hasEnvVar(ITEM3, END_VALUE_3)));
  }

  @Test
  public void whenPacketHasValueFromEnvironmentItems_createManagedPodStartupWithThem() {
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
  public void whenPacketHasValueFromEnvironmentItemsWithVariables_createManagedPodStartupWithSubstitutions() {
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
  public void whenClusterHasAdditionalVolumesWithVariables_createManagedPodWithSubstitutions() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolume("volume1", "/source-$(SERVER_NAME)")
        .withAdditionalVolume("volume2", "/source-$(DOMAIN_NAME)");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(
            hasVolume("volume1", "/source-" + SERVER_NAME),
            hasVolume("volume2", "/source-domain1")));
  }

  @Test
  public void whenClusterHasLabelsWithVariables_createManagedPodWithSubstitutions() {
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
        .configureCluster(CLUSTER_NAME)
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
  public void createManagedPodStartupWithNullAdminUsernamePasswordEnvVarsValues() {
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.emptyList());

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  public void whenPacketHasEnvironmentItemsWithVariable_createManagedPodShouldNotChangeItsValue() {
    V1EnvVar envVar = toEnvVar(ITEM1, RAW_VALUE_1);
    testSupport.addToPacket(ProcessingConstants.ENVVARS, Collections.singletonList(envVar));

    getCreatedPodSpecContainer();

    assertThat(envVar.getValue(), is(RAW_VALUE_1));
  }

  @Test
  public void whenPacketHasClusterConfig_managedPodHasClusterLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getMetadata().getLabels(),
        hasEntry(LabelConstants.CLUSTERNAME_LABEL, CLUSTER_NAME));
  }

  @Test
  public void whenDomainHasAdditionalVolumesWithVariables_createManagedPodWithThem() {
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
  public void whenDomainHasAdditionalPvClaimVolumesWitVariables_createManagedPodWithThem() {
    getConfigurator()
        .withAdditionalPvClaimVolume(RAW_VALUE_4, RAW_VALUE_3);

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasPvClaimVolume(END_VALUE_4_DNS1123, END_VALUE_3_DNS1123)));
  }

  @Test
  public void whenDomainHasAdditionalVolumeMounts_createManagedPodWithThem() {
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
  public void whenClusterHasAdditionalVolumes_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  public void whenClusterHasAdditionalVolumeMounts_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolumeMount("volume1", "/destination-path1")
        .withAdditionalVolumeMount("volume2", "/destination-path2");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/destination-path1"),
            hasVolumeMount("volume2", "/destination-path2")));
  }

  @Test
  public void whenServerHasAdditionalVolumes_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  public void whenServerHasAdditionalVolumeMounts_createManagedPodWithThem() {
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
  public void whenPodHasDuplicateVolumes_createManagedPodWithCombination() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/domain-path1")
        .withAdditionalVolume("volume2", "/domain-path2")
        .withAdditionalVolume("volume3", "/domain-path3")
        .configureServer(SERVER_NAME)
        .withAdditionalVolume("volume3", "/server-path");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolume("volume2", "/cluster-path");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(
            hasVolume("volume1", "/domain-path1"),
            hasVolume("volume2", "/cluster-path"),
            hasVolume("volume3", "/server-path")));
  }

  @Test
  public void whenPodHasDuplicateVolumeMounts_createManagedPodWithCombination() {
    getConfigurator()
        .withAdditionalVolumeMount("volume1", "/domain-path1")
        .withAdditionalVolumeMount("volume2", "/domain-path2")
        .withAdditionalVolumeMount("volume3", "/domain-path3")
        .configureServer(SERVER_NAME)
        .withAdditionalVolumeMount("volume3", "/server-path");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAdditionalVolumeMount("volume2", "/cluster-path");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
            hasVolumeMount("volume1", "/domain-path1"),
            hasVolumeMount("volume2", "/cluster-path"),
            hasVolumeMount("volume3", "/server-path")));
  }

  @Test
  public void whenDesiredStateIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().withServerStartState(ADMIN_STATE);

    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("STARTUP_MODE", ADMIN_STATE));
  }

  @Test
  public void whenServerDesiredStateIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().configureServer(SERVER_NAME).withServerStartState(ADMIN_STATE);

    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("STARTUP_MODE", ADMIN_STATE));
  }

  @Test
  public void whenDesiredStateIsRunningServerIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator()
        .withServerStartState(RUNNING_STATE)
        .configureServer(SERVER_NAME)
        .withServerStartState(ADMIN_STATE);

    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("STARTUP_MODE", ADMIN_STATE));
  }

  @Test
  public void whenDesiredStateIsAdminServerIsRunning_createPodWithStartupModeEnvironment() {
    getConfigurator()
        .withServerStartState(ADMIN_STATE)
        .configureServer(SERVER_NAME)
        .withServerStartState(RUNNING_STATE);

    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("STARTUP_MODE", ADMIN_STATE)));
  }

  @Test
  public void whenClusterDesiredStateIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().configureServer(SERVER_NAME);

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(CLUSTER_NAME).withServerStartState(ADMIN_STATE);

    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("STARTUP_MODE", ADMIN_STATE));
  }

  @Test
  public void whenClusterDesiredStateIsRunningServerIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().configureServer(SERVER_NAME).withServerStartState(ADMIN_STATE);

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(CLUSTER_NAME).withServerStartState(RUNNING_STATE);

    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("STARTUP_MODE", ADMIN_STATE));
  }

  @Test
  public void whenClusterDesiredStateIsAdminServerIsRunning_createPodWithStartupModeEnvironment() {
    getConfigurator().configureServer(SERVER_NAME).withServerStartState(RUNNING_STATE);

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(CLUSTER_NAME).withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("STARTUP_MODE", ADMIN_STATE)));
  }

  @Test
  public void whenDomainHasInitContainers_createPodWithThem() {
    getConfigurator()
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerHasInitContainers_createPodWithThem() {
    getConfigurator()
        .configureServer(SERVER_NAME)
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerHasDuplicateInitContainers_createPodWithCombination() {
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
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenClusterHasInitContainers_createPodWithThem() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo managed server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerAndClusterHasDuplicateInitContainers_createPodWithCombination() {
    getConfigurator()
        .withInitContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"))
        .withInitContainer(createContainer("container3", "oraclelinux", "ls /cluster"));
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo cluster && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle"),
            hasContainer("container3", "oraclelinux", "ls /cluster")));
  }

  @Test
  public void whenDomainHasContainers_createPodWithThem() {
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
  public void whenServerHasContainers_createPodWithThem() {
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
  public void whenServerHasDuplicateContainers_createPodWithCombination() {
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
  public void whenClusterHasContainers_createPodWithThem() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
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
  public void whenServerAndClusterHasDuplicateContainers_createPodWithCombination() {
    getConfigurator()
        .withContainer(
            createContainer(
                "container1", "busybox", "sh", "-c", "echo managed server && sleep 120"))
        .withContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureServer(SERVER_NAME)
        .withContainer(createContainer("container2", "oraclelinux", "ls /oracle"));
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
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
  public void whenDomainHasLabels_createManagedPodWithThem() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "domain-label-value2"));
  }

  @Test
  public void whenDomainHasAnnotations_createManagedPodWithThem() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "domain-annotation-value2"));
  }

  @Test
  public void whenClusterHasLabels_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodLabel("label1", "cluster-label-value1")
        .withPodLabel("label2", "cluster-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "cluster-label-value1"));
    assertThat(podLabels, hasEntry("label2", "cluster-label-value2"));
  }

  @Test
  public void whenClusterHasRestartVersion_createManagedPodWithRestartVersionLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().configureCluster(CLUSTER_NAME).withRestartVersion("clusterRestartV1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.CLUSTERRESTARTVERSION_LABEL, "clusterRestartV1"));
    assertThat(podLabels, hasKey(not(LabelConstants.DOMAINRESTARTVERSION_LABEL)));
    assertThat(podLabels, hasKey(not(LabelConstants.SERVERRESTARTVERSION_LABEL)));
  }

  @Test
  public void whenDomainHasRestartVersion_createManagedPodWithRestartVersionLabel() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator().withRestartVersion("domainRestartV1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.DOMAINRESTARTVERSION_LABEL, "domainRestartV1"));
    assertThat(podLabels, hasKey(not(LabelConstants.CLUSTERRESTARTVERSION_LABEL)));
    assertThat(podLabels, hasKey(not(LabelConstants.SERVERRESTARTVERSION_LABEL)));
  }

  @Test
  public void whenClusterHasAnnotations_createManagedPodWithThem() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodAnnotation("annotation1", "cluster-annotation-value1")
        .withPodAnnotation("annotation2", "cluster-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "cluster-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "cluster-annotation-value2"));
  }

  @Test
  public void whenServerHasLabels_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withPodLabel("label1", "server-label-value1")
        .withPodLabel("label2", "server-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "server-label-value1"));
    assertThat(podLabels, hasEntry("label2", "server-label-value2"));
  }

  @Test
  public void whenServerHasAnnotations_createManagedPodWithThem() {
    configureServer(getConfigurator(), SERVER_NAME)
        .withPodAnnotation("annotation1", "server-annotation-value1")
        .withPodAnnotation("annotation2", "server-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "server-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "server-annotation-value2"));
  }

  @Test
  public void whenPodHasDuplicateLabels_createManagedPodWithCombination() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2")
        .withPodLabel("label3", "domain-label-value3")
        .configureServer(SERVER_NAME)
        .withPodLabel("label3", "server-label-value1");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodLabel("label2", "cluster-label-value1")
        .withPodLabel("label3", "cluster-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "cluster-label-value1"));
    assertThat(podLabels, hasEntry("label3", "server-label-value1"));
  }

  @Test
  public void whenPodHasDuplicateAnnotations_createManagedPodWithCombination() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2")
        .withPodAnnotation("annotation3", "domain-annotation-value3")
        .configureServer(SERVER_NAME)
        .withPodAnnotation("annotation3", "server-annotation-value1");

    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodAnnotation("annotation2", "cluster-annotation-value1")
        .withPodAnnotation("annotation3", "cluster-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "cluster-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation3", "server-annotation-value1"));
  }

  @Test
  public void whenPodHasCustomLabelConflictWithInternal_createManagedPodWithInternal() {
    getConfigurator()
        .configureServer((SERVER_NAME))
        .withPodLabel(LabelConstants.CREATEDBYOPERATOR_LABEL, "server-label-value1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"));
  }

  @Test
  public void whenClusterHasAffinity_createPodWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAffinity(affinity);
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getAffinity(),
        is(affinity));
  }

  @Test
  public void whenClusterHasNodeSelector_createPodWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withNodeSelector("os_arch", "x86_64");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getNodeSelector(),
        hasEntry("os_arch", "x86_64"));
  }

  @Test
  public void whenClusterHasNodeName_createPodWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withNodeName("kube-01");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getNodeName(),
        is("kube-01"));
  }

  @Test
  public void whenClusterHasSchedulerName_createPodWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withSchedulerName("my-scheduler");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getSchedulerName(),
        is("my-scheduler"));
  }

  @Test
  public void whenClusterHasRuntimeClassName_createPodWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withRuntimeClassName("RuntimeClassName");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getRuntimeClassName(),
        is("RuntimeClassName"));
  }

  @Test
  public void whenClusterHasPriorityClassName_createPodWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPriorityClassName("PriorityClassName");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getPriorityClassName(),
        is("PriorityClassName"));
  }

  @Test
  public void whenClusterHasRestartPolicy_createPodWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withRestartPolicy("Always");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getRestartPolicy(),
        is("Always"));
  }

  @Test
  public void whenClusterHasPodSecurityContext_createPodWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withPodSecurityContext(podSecurityContext);
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    assertThat(
        getCreatedPod().getSpec().getSecurityContext(),
        is(podSecurityContext));
  }

  @Test
  public void whenClusterHasContainerSecurityContext_createContainersWithIt() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withContainerSecurityContext(containerSecurityContext);
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    getCreatedPodSpecContainers()
        .forEach(c -> assertThat(
            c.getSecurityContext(),
            is(containerSecurityContext)));
  }

  @Test
  public void whenClusterHasResources_createContainersWithThem() {
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withLimitRequirement("cpu", "1Gi")
        .withRequestRequirement("memory", "250m");
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);

    List<V1Container> containers = getCreatedPodSpecContainers();

    containers.forEach(c -> assertThat(c.getResources().getLimits(), hasResourceQuantity("cpu", "1Gi")));
    containers.forEach(c -> assertThat(c.getResources().getRequests(), hasResourceQuantity("memory", "250m")));
  }

  @Test
  public void whenClusterHasAffinityWithVariables_createManagedPodWithSubstitutions() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .configureCluster(CLUSTER_NAME)
        .withAffinity(
            new V1Affinity().podAntiAffinity(
                new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
                    Collections.singletonList(new V1WeightedPodAffinityTerm().weight(100).podAffinityTerm(
                        new V1PodAffinityTerm().labelSelector(
                            new V1LabelSelector().matchExpressions(
                                Collections.singletonList(new V1LabelSelectorRequirement()
                                    .key("weblogic.clusterName")
                                    .operator("In")
                                    .addValuesItem("$(CLUSTER_NAME)"))))
                            .topologyKey("kubernetes.io/hostname"))))));

    V1Affinity expectedValue = new V1Affinity().podAntiAffinity(
        new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
            Collections.singletonList(new V1WeightedPodAffinityTerm().weight(100).podAffinityTerm(
                new V1PodAffinityTerm().labelSelector(
                    new V1LabelSelector().matchExpressions(
                        Collections.singletonList(new V1LabelSelectorRequirement()
                            .key("weblogic.clusterName")
                            .operator("In")
                            .addValuesItem(CLUSTER_NAME))))
                    .topologyKey("kubernetes.io/hostname")))));

    assertThat(
        getCreatedPod().getSpec().getAffinity(),
        is(expectedValue));
  }

  @Test
  public void whenDomainAndClusterBothHaveAffinityWithVariables_createManagedPodWithSubstitutions() {
    testSupport.addToPacket(ProcessingConstants.CLUSTER_NAME, CLUSTER_NAME);
    getConfigurator()
        .withAffinity(
            new V1Affinity().podAntiAffinity(
                new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
                    Collections.singletonList(new V1WeightedPodAffinityTerm().weight(100).podAffinityTerm(
                        new V1PodAffinityTerm().labelSelector(
                            new V1LabelSelector().matchExpressions(
                                Collections.singletonList(new V1LabelSelectorRequirement()
                                    .key("weblogic.domainUID")
                                    .operator("In")
                                    .addValuesItem("$(DOMAIN_UID)"))))
                            .topologyKey("kubernetes.io/hostname"))))))
        .configureCluster(CLUSTER_NAME)
        .withAffinity(
            new V1Affinity().podAntiAffinity(
                new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
                    Collections.singletonList(new V1WeightedPodAffinityTerm().weight(100).podAffinityTerm(
                        new V1PodAffinityTerm().labelSelector(
                            new V1LabelSelector().matchExpressions(
                                Collections.singletonList(new V1LabelSelectorRequirement()
                                    .key("weblogic.clusterName")
                                    .operator("In")
                                    .addValuesItem("$(CLUSTER_NAME)"))))
                            .topologyKey("kubernetes.io/hostname"))))));

    V1Affinity expectedValue = new V1Affinity().podAntiAffinity(
        new V1PodAntiAffinity().preferredDuringSchedulingIgnoredDuringExecution(
            Arrays.asList(
                new V1WeightedPodAffinityTerm().weight(100).podAffinityTerm(
                    new V1PodAffinityTerm().labelSelector(
                        new V1LabelSelector().matchExpressions(
                            Collections.singletonList(new V1LabelSelectorRequirement()
                                .key("weblogic.clusterName")
                                .operator("In")
                                .addValuesItem(CLUSTER_NAME))))
                        .topologyKey("kubernetes.io/hostname")),
                new V1WeightedPodAffinityTerm().weight(100).podAffinityTerm(
                  new V1PodAffinityTerm().labelSelector(
                    new V1LabelSelector().matchExpressions(
                        Collections.singletonList(new V1LabelSelectorRequirement()
                            .key("weblogic.domainUID")
                            .operator("In")
                            .addValuesItem(UID))))
                    .topologyKey("kubernetes.io/hostname")))));

    assertThat(
        getCreatedPod().getSpec().getAffinity(),
        is(expectedValue));
  }

  @Override
  void setServerPort(int port) {
    getServerTopology().setListenPort(port);
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
    return new PodHelper.ManagedPodStepContext(null, packet).getPodModel();
  }
}

// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import org.junit.Test;

import static oracle.kubernetes.operator.WebLogicConstants.ADMIN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.helpers.Matchers.hasContainer;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasPvClaimVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolumeMount;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_POD_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_POD_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_POD_PATCHED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_POD_REPLACED;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings("SameParameterValue")
public class AdminPodHelperTest extends PodHelperTestBase {
  private static final String INTERNAL_OPERATOR_CERT_ENV_NAME = "INTERNAL_OPERATOR_CERT";

  static final String RAW_VALUE_1 = "value-$(SERVER_NAME)";
  static final String END_VALUE_1 = "value-ADMIN_SERVER";

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
  protected ServerConfigurator configureServer(DomainConfigurator configurator, String serverName) {
    return configurator.configureAdminServer();
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

  @Test // REG I don't understand why this is only true for the admin server
  public void whenConfigurationAddsEnvironmentVariable_replacePod() {
    initializeExistingPod();

    configureServer().withEnvironmentVariable("test", "???");

    verifyPodReplaced();
  }

  @Override
  protected void verifyPodReplaced() {
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        new NullPodAwaiterStepFactory(terminalStep));

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getReplacedMessageKey()));
  }

  @Override
  protected void verifyPodNotReplacedWhen(PodMutator mutator) {
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        new NullPodAwaiterStepFactory(terminalStep));

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
  public void whenDeleteReportsNotFound_replaceAdminPod() {
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        new NullPodAwaiterStepFactory(terminalStep));

    initializeExistingPod(getIncompatiblePod());
    testSupport.failOnDelete(KubernetesTestSupport.POD, getPodName(), NS, CallBuilder.NOT_FOUND);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getReplacedMessageKey()));
  }

  private V1Pod getIncompatiblePod() {
    V1Pod existingPod = createTestPodModel();
    existingPod.getSpec().setContainers(null);
    return existingPod;
  }

  @Test
  public void whenAdminPodDeletionFails_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeExistingPod(getIncompatiblePod());
    testSupport.failOnDelete(KubernetesTestSupport.POD, getPodName(), NS, 401);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenAdminPodReplacementFails_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeExistingPod(getIncompatiblePod());
    testSupport.failOnCreate(KubernetesTestSupport.POD, getPodName(), NS, 401);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenAdminPodCreated_specHasPodNameAsHostName() {
    assertThat(getCreatedPod().getSpec().getHostname(), equalTo(getPodName()));
  }

  @Test
  public void whenAdminPodCreated_containerHasStartServerCommand() {
    assertThat(
        getCreatedPodSpecContainer().getCommand(),
        contains("/weblogic-operator/scripts/startServer.sh"));
  }

  @Test
  public void whenAdminPodCreated_hasOperatorCertEnvVariable() {
    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        hasEnvVar(INTERNAL_OPERATOR_CERT_ENV_NAME, InMemoryCertificates.INTERNAL_CERT_DATA));
  }

  @Test
  public void whenAdminPodCreatedWithAdminPortEnabled_adminServerPortSecureEnvVarIsTrue() {
    getServerTopology().setAdminPort((Integer) 9002);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("ADMIN_SERVER_PORT_SECURE", "true"));
  }

  @Test
  public void whenAdminPodCreatedWithNullAdminPort_adminServerPortSecureEnvVarIsNotSet() {
    getServerTopology().setAdminPort(null);
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("ADMIN_SERVER_PORT_SECURE", "true")));
  }

  @Test
  public void whenAdminPodCreatedWithAdminServerHasSslPortEnabled_adminServerPortSecureEnvVarIsTrue() {
    getServerTopology().setSslListenPort(9999);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("ADMIN_SERVER_PORT_SECURE", "true"));
  }

  @Test
  public void whenAdminPodCreatedWithAdminServerHasNullSslPort_adminServerPortSecureEnvVarIsNotSet() {
    getServerTopology().setSslListenPort(null);
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("ADMIN_SERVER_PORT_SECURE", "true")));
  }

  @Test
  public void whenDomainPresenceHasNoEnvironmentItems_createAdminPodStartupWithDefaultItems() {
    assertThat(getCreatedPodSpecContainer().getEnv(), not(empty()));
  }

  @Test
  public void whenDomainHasEnvironmentItems_createAdminPodStartupWithThem() {
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
  public void whenDomainHasEnvironmentItemsWithVariables_createAdminPodStartupWithThem() {
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
  public void whenDomainHasEnvironmentItemsWithVariable_createPodShouldNotChangeItsValue()
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
  public void whenDomainHasValueFromEnvironmentItems_createAdminPodStartupWithThem() {
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
  public void whenDomainHasValueFromEnvironmentItemsWithVariables_createAdminPodStartupWithSubstitutions() {
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
  public void whenDomainHasValueFromEnvironmentItemsWithVariables_createPodShouldNotChangeTheirValues()
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
  public void createAdminPodStartupWithNullAdminUsernamePasswordEnvVarsValues() {
    configureAdminServer();

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  public void whenDomainHasAdditionalPvClaimVolumesWitVariables_createManagedPodWithThem() {
    getConfigurator()
        .withAdditionalPvClaimVolume("volume-$(SERVER_NAME)", "$(SERVER_NAME)-claim");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasPvClaimVolume("volume-admin-server", "admin-server-claim")));
  }

  @Test
  public void whenServerHasAdditionalVolumes_createAdminPodWithThem() {
    configureAdminServer()
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  public void whenServerHasAdditionalVolumeMounts_createAdminPodWithThem() {
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
  public void whenPodHasDuplicateVolumes_createAdminPodWithCombination() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/domain-path1")
        .withAdditionalVolume("volume2", "/domain-path2")
        .configureAdminServer()
        .withAdditionalVolume("volume2", "/server-path");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/domain-path1"), hasVolume("volume2", "/server-path")));
  }

  @Test
  public void whenPodHasDuplicateVolumeMounts_createAdminPodWithCombination() {
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
  public void whenDesiredStateIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), hasEnvVar("STARTUP_MODE", ADMIN_STATE));
  }

  @Test
  public void whenServerDesiredStateIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator().configureAdminServer().withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), hasEnvVar("STARTUP_MODE", ADMIN_STATE));
  }

  @Test
  public void whenDesiredStateIsRunningServerIsAdmin_createPodWithStartupModeEnvironment() {
    getConfigurator()
        .withServerStartState(RUNNING_STATE)
        .configureAdminServer()
        .withServerStartState(ADMIN_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), hasEnvVar("STARTUP_MODE", ADMIN_STATE));
  }

  @Test
  public void whenDesiredStateIsAdminServerIsRunning_createPodWithStartupModeEnvironment() {
    getConfigurator()
        .withServerStartState(ADMIN_STATE)
        .configureAdminServer()
        .withServerStartState(RUNNING_STATE);

    assertThat(
        getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("STARTUP_MODE", ADMIN_STATE)));
  }

  @Test
  public void whenDomainHasInitContainers_createAdminPodWithThem() {
    getConfigurator()
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerHasInitContainers_createAdminPodWithThem() {
    getConfigurator()
        .configureAdminServer()
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenServerHasDuplicateInitContainers_createAdminPodWithCombination() {
    getConfigurator()
        .withInitContainer(
            createContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"))
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /top"))
        .configureAdminServer()
        .withInitContainer(createContainer("container2", "oraclelinux", "ls /oracle"));

    assertThat(
        getCreatedPodSpecInitContainers(),
        allOf(
            hasContainer("container1", "busybox", "sh", "-c", "echo admin server && sleep 120"),
            hasContainer("container2", "oraclelinux", "ls /oracle")));
  }

  @Test
  public void whenDomainHasContainers_createAdminPodWithThem() {
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
  public void whenServerHasContainers_createAdminPodWithThem() {
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
  public void whenServerHasDuplicateContainers_createAdminPodWithCombination() {
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
  public void whenDomainHasLabels_createAdminPodWithThem() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2");
    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "domain-label-value2"));
  }

  @Test
  public void whenDomainHasAnnotations_createAdminPodWithThem() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2");
    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();

    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "domain-annotation-value2"));
  }

  @Test
  public void whenServerHasLabels_createAdminPodWithThem() {
    configureAdminServer()
        .withPodLabel("label1", "server-label-value1")
        .withPodLabel("label2", "server-label-value2");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "server-label-value1"));
    assertThat(podLabels, hasEntry("label2", "server-label-value2"));
  }

  @Test
  public void whenServerHasAnnotations_createAdminPodWithThem() {
    configureAdminServer()
        .withPodAnnotation("annotation1", "server-annotation-value1")
        .withPodAnnotation("annotation2", "server-annotation-value2");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "server-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "server-annotation-value2"));
  }

  @Test
  public void whenPodHasDuplicateLabels_createAdminPodWithCombination() {
    getConfigurator()
        .withPodLabel("label1", "domain-label-value1")
        .withPodLabel("label2", "domain-label-value2")
        .configureAdminServer()
        .withPodLabel("label2", "server-label-value1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry("label1", "domain-label-value1"));
    assertThat(podLabels, hasEntry("label2", "server-label-value1"));
  }

  @Test
  public void whenPodHasDuplicateAnnotations_createAdminPodWithCombination() {
    getConfigurator()
        .withPodAnnotation("annotation1", "domain-annotation-value1")
        .withPodAnnotation("annotation2", "domain-annotation-value2")
        .configureAdminServer()
        .withPodAnnotation("annotation2", "server-annotation-value1");

    Map<String, String> podAnnotations = getCreatedPod().getMetadata().getAnnotations();
    assertThat(podAnnotations, hasEntry("annotation1", "domain-annotation-value1"));
    assertThat(podAnnotations, hasEntry("annotation2", "server-annotation-value1"));
  }

  @Test
  public void whenPodHasCustomLabelConflictWithInternal_createAdminPodWithInternal() {
    getConfigurator()
        .withPodLabel(LabelConstants.RESOURCE_VERSION_LABEL, "domain-label-value1")
        .configureAdminServer()
        .withPodLabel(LabelConstants.CREATEDBYOPERATOR_LABEL, "server-label-value1")
        .withPodLabel("label1", "server-label-value1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(
        podLabels,
        hasEntry(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION));
    assertThat(podLabels, hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true"));
    assertThat(podLabels, hasEntry("label1", "server-label-value1"));
  }

  @Test
  public void whenDomainAndAdminHasRestartVersion_createAdminPodWithRestartVersionLabel() {
    getConfigurator()
        .withRestartVersion("domainRestartV1")
        .configureAdminServer()
        .withRestartVersion("adminRestartV1");

    Map<String, String> podLabels = getCreatedPod().getMetadata().getLabels();
    assertThat(podLabels, hasEntry(LabelConstants.DOMAINRESTARTVERSION_LABEL, "domainRestartV1"));
    assertThat(podLabels, hasEntry(LabelConstants.SERVERRESTARTVERSION_LABEL, "adminRestartV1"));
    assertThat(podLabels, hasKey(not(LabelConstants.CLUSTERRESTARTVERSION_LABEL)));
  }

  @Override
  V1Pod createTestPodModel() {
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

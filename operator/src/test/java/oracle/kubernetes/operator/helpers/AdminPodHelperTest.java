// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_POD_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_POD_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_POD_PATCHED;
import static oracle.kubernetes.operator.logging.MessageKeys.ADMIN_POD_REPLACED;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1EnvFromSource;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Status;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import org.junit.Test;

@SuppressWarnings("SameParameterValue")
public class AdminPodHelperTest extends PodHelperTestBase {
  private static final String INTERNAL_OPERATOR_CERT_FILE_PARAM = "internalOperatorCert";
  private static final String INTERNAL_OPERATOR_CERT_ENV_NAME = "INTERNAL_OPERATOR_CERT";
  private static final String CERTFILE = "certfile";

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
  void expectStepsAfterCreation() {}

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
  protected void verifyReplacePodWhen(PodMutator mutator) {
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        (pod, next) -> terminalStep);

    V1Pod existingPod = createPodModel();
    mutator.mutate(existingPod);
    initializeExistingPod(existingPod);
    expectDeletePod(getPodName()).returning(new V1Status());
    expectCreatePod(podWithName(getPodName())).returning(createPodModel());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getReplacedMessageKey()));
  }

  @Override
  protected void verifyPodNotReplacedWhen(PodMutator mutator) {
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        (pod, next) -> terminalStep);

    V1Pod existingPod = createPodModel();
    mutator.mutate(existingPod);
    initializeExistingPod(existingPod);

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsFine(getExistsMessageKey()));
  }

  private CallTestSupport.CannedResponse expectDeletePod(String podName) {
    return testSupport
        .createCannedResponse("deletePod")
        .withNamespace(NS)
        .ignoringBody()
        .withName(podName);
  }

  @Test
  public void whenDeleteReportsNotFound_replaceAdminPod() {
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        (pod, next) -> terminalStep);

    initializeExistingPod(getIncompatiblePod());
    expectDeletePod(getPodName()).failingWithStatus(CallBuilder.NOT_FOUND);
    expectCreatePod(podWithName(getPodName())).returning(createPodModel());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getReplacedMessageKey()));
  }

  private V1Pod getIncompatiblePod() {
    V1Pod existingPod = createPodModel();
    existingPod.getSpec().setContainers(null);
    return existingPod;
  }

  @Test
  public void whenAdminPodDeletionFails_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeExistingPod(getIncompatiblePod());
    expectDeletePod(getPodName()).failingWithStatus(401);
    expectStepsAfterCreation();

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenAdminPodReplacementFails_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    initializeExistingPod(getIncompatiblePod());
    expectDeletePod(getPodName()).returning(new V1Status());
    expectCreatePod(podWithName(getPodName())).failingWithStatus(401);
    expectStepsAfterCreation();

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenExistingAdminPodSpecHasUnknownAddedVolumes_replaceIt() {
    verifyReplacePodWhen((pod) -> pod.getSpec().addVolumesItem(new V1Volume().name("dummy")));
  }

  @Test
  public void whenExistingAdminPodSpecHasK8sVolume_ignoreIt() {
    verifyPodNotReplacedWhen(
        (pod) -> {
          pod.getSpec().addVolumesItem(new V1Volume().name("k8s"));
          getSpecContainer(pod)
              .addVolumeMountsItem(
                  new V1VolumeMount()
                      .name("k8s")
                      .mountPath(PodDefaults.K8S_SERVICE_ACCOUNT_MOUNT_PATH));
        });
  }

  @Test
  public void whenExistingAdminPodSpecHasUnusedImagePullSecret_replaceIt() {
    verifyReplacePodWhen(
        (pod) ->
            pod.getSpec().addImagePullSecretsItem(new V1LocalObjectReference().name("secret")));
  }

  @Test
  public void whenExistingAdminPodSpecHasNoContainers_replaceIt() {
    verifyReplacePodWhen((pod) -> pod.getSpec().setContainers(null));
  }

  @Test
  public void whenExistingAdminPodSpecHasNoContainersWithExpectedName_replaceIt() {
    verifyReplacePodWhen((pod) -> getSpecContainer(pod).setName("???"));
  }

  private V1Container getSpecContainer(V1Pod pod) {
    return pod.getSpec().getContainers().get(0);
  }

  @Test
  public void whenExistingAdminPodSpecHasUnneededVolumeMount_replaceIt() {
    verifyReplacePodWhen(
        (pod) -> getSpecContainer(pod).addVolumeMountsItem(new V1VolumeMount().name("dummy")));
  }

  @Test
  public void whenExistingAdminPodSpecHasK8sVolumeMount_ignoreIt() {
    verifyPodNotReplacedWhen(
        (pod) ->
            getSpecContainer(pod)
                .addVolumeMountsItem(
                    new V1VolumeMount()
                        .name("dummy")
                        .mountPath(PodDefaults.K8S_SERVICE_ACCOUNT_MOUNT_PATH)));
  }

  @Test
  public void whenExistingAdminPodSpecContainerHasWrongImage_replaceIt() {
    verifyReplacePodWhen((pod) -> getSpecContainer(pod).setImage(VERSIONED_IMAGE));
  }

  @Test
  public void whenExistingAdminPodSpecContainerHasWrongImagePullPolicy_replaceIt() {
    verifyReplacePodWhen((pod) -> getSpecContainer(pod).setImagePullPolicy("NONE"));
  }

  @Test
  public void whenExistingAdminPodSpecContainerHasNoPorts_replaceIt() {
    verifyReplacePodWhen((pod) -> getSpecContainer(pod).setPorts(Collections.emptyList()));
  }

  @Test
  public void whenExistingAdminPodSpecContainerHasExtraPort_replaceIt() {
    verifyReplacePodWhen((pod) -> getSpecContainer(pod).addPortsItem(definePort(1234)));
  }

  private V1ContainerPort definePort(int port) {
    return new V1ContainerPort().protocol("TCP").containerPort(port);
  }

  @Test
  public void whenExistingAdminPodSpecContainerHasIncorrectPort_replaceIt() {
    verifyReplacePodWhen((pod) -> getSpecContainer(pod).getPorts().get(0).setContainerPort(1234));
  }

  @Test
  public void whenExistingAdminPodSpecContainerHasWrongEnvVariable_replaceIt() {
    verifyReplacePodWhen((pod) -> getSpecContainer(pod).getEnv().get(0).setValue("???"));
  }

  @Test
  public void whenExistingAdminPodSpecContainerHasWrongEnvFrom_replaceIt() {
    verifyReplacePodWhen(
        (pod) -> getSpecContainer(pod).envFrom(Collections.singletonList(new V1EnvFromSource())));
  }

  @Test
  public void whenExistingAdminPodSpecContainerHasRestartVersion_replaceIt() {
    verifyReplacePodWhen(
        (pod) ->
            pod.getMetadata()
                .putLabelsItem(LabelConstants.SERVERRESTARTVERSION_LABEL, "adminRestartV1"));
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
    putTuningParameter(INTERNAL_OPERATOR_CERT_FILE_PARAM, CERTFILE);
    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        hasEnvVar(INTERNAL_OPERATOR_CERT_ENV_NAME, CERTFILE));
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
  public void createAdminPodStartupWithNullAdminUsernamePasswordEnvVarsValues() {
    configureAdminServer();

    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(hasEnvVar("ADMIN_USERNAME", null), hasEnvVar("ADMIN_PASSWORD", null)));
  }

  @Test
  public void whenDomainHasAdditionalVolumes_createAdminPodWithThem() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  public void whenDomainHasAdditionalVolumeMounts_createAdminPodWithThem() {
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
  protected void onAdminExpectListPersistentVolume() {
    expectListPersistentVolume().returning(createPersistentVolumeList());
  }

  @Override
  V1Pod createPodModel() {
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

  @Override
  protected ServerConfigurator getServerConfigurator(
      DomainConfigurator configurator, String serverName) {
    return configurator.configureAdminServer();
  }
}

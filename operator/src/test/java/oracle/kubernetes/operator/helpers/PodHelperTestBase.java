// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.LogMatcher.containsFine;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.CONTAINER_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.helpers.AnnotationHelper.SHA256_ANNOTATION;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.ProbeMatcher.hasExpectedTuning;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.VolumeMountMatcher.readOnlyVolumeMount;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.VolumeMountMatcher.writableVolumeMount;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SIT_CONFIG_MAP_VOLUME_SUFFIX;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.LIVENESS_INITIAL_DELAY;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.LIVENESS_PERIOD;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.LIVENESS_TIMEOUT;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.READINESS_INITIAL_DELAY;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.READINESS_PERIOD;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.READINESS_TIMEOUT;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ExecAction;
import io.kubernetes.client.models.V1HTTPGetAction;
import io.kubernetes.client.models.V1Handler;
import io.kubernetes.client.models.V1HostPathVolumeSource;
import io.kubernetes.client.models.V1Lifecycle;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1PersistentVolumeSpec;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSecurityContext;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1SecretReference;
import io.kubernetes.client.models.V1SecurityContext;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@SuppressWarnings({"SameParameterValue", "ConstantConditions", "OctalInteger", "unchecked"})
public abstract class PodHelperTestBase {
  static final String NS = "namespace";
  private static final String DOMAIN_NAME = "domain1";
  private static final String UID = "uid1";
  static final String ADMIN_SERVER = "ADMIN_SERVER";
  static final Integer ADMIN_PORT = 7001;
  private static final boolean INCLUDE_SERVER_OUT_IN_POD_LOG = true;

  private static final String CREDENTIALS_SECRET_NAME = "webLogicCredentialsSecretName";
  private static final String STORAGE_VOLUME_NAME = "weblogic-domain-storage-volume";
  private static final String LATEST_IMAGE = "image:latest";
  private static final String VERSIONED_IMAGE = "image:1.2.3";
  private static final int CONFIGURED_DELAY = 21;
  private static final int CONFIGURED_TIMEOUT = 27;
  private static final int CONFIGURED_PERIOD = 35;
  private static final String LOG_HOME = "/shared/logs";
  private static final String NODEMGR_HOME = "/u01/nodemanager";
  private static final String CONFIGMAP_VOLUME_NAME = "weblogic-domain-cm-volume";
  private static final int READ_AND_EXECUTE_MODE = 0555;
  private static final String INSTRUCTION = "{\"op\":\"%s\",\"path\":\"%s\",\"value\":\"%s\"}";

  final TerminalStep terminalStep = new TerminalStep();
  private final Domain domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  protected AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  protected List<Memento> mementos = new ArrayList<>();
  protected List<LogRecord> logRecords = new ArrayList<>();
  RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private String serverName;
  private int listenPort;
  private WlsDomainConfig domainTopology;

  PodHelperTestBase(String serverName, int listenPort) {
    this.serverName = serverName;
    this.listenPort = listenPort;
  }

  private static String getPodName(V1Pod actualBody) {
    return actualBody.getMetadata().getName();
  }

  private String getServerName() {
    return serverName;
  }

  DomainConfigurator getConfigurator() {
    return configurator;
  }

  Method getDomainSpec;

  DomainSpec getConfiguredDomainSpec()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (getDomainSpec == null) {
      getDomainSpec = DomainConfigurator.class.getDeclaredMethod("getDomainSpec");
      getDomainSpec.setAccessible(true);
    }
    return (DomainSpec) getDomainSpec.invoke(configurator);
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());
    mementos.add(InMemoryCertificates.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport.addWlsServer(ADMIN_SERVER, ADMIN_PORT);
    if (!ADMIN_SERVER.equals(serverName)) configSupport.addWlsServer(serverName, listenPort);
    configSupport.setAdminServerName(ADMIN_SERVER);

    domainTopology = configSupport.createDomainConfig();
    testSupport
        .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainTopology)
        .addToPacket(SERVER_SCAN, domainTopology.getServerConfig(serverName))
        .addDomainPresenceInfo(domainPresenceInfo);
    onAdminExpectListPersistentVolume();
  }

  abstract V1Pod createPod(Packet packet);

  private String[] getMessageKeys() {
    return new String[] {
      getCreatedMessageKey(), getExistsMessageKey(), getPatchedMessageKey(), getReplacedMessageKey()
    };
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  private DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    return new DomainPresenceInfo(domain);
  }

  WlsServerConfig getServerTopology() {
    return domainTopology.getServerConfig(getServerName());
  }

  abstract void setServerPort(int port);

  private Domain createDomain() {
    return new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(createDomainSpec());
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec()
        .withDomainUID(UID)
        .withWebLogicCredentialsSecret(new V1SecretReference().name(CREDENTIALS_SECRET_NAME))
        .withIncludeServerOutInPodLog(INCLUDE_SERVER_OUT_IN_POD_LOG)
        .withImage(LATEST_IMAGE);
  }

  void putTuningParameter(String name, String value) {
    TuningParametersStub.namedParameters.put(name, value);
  }

  CallTestSupport.CannedResponse expectCreatePod(BodyMatcher bodyMatcher) {
    return testSupport.createCannedResponse("createPod").withNamespace(NS).withBody(bodyMatcher);
  }

  BodyMatcher podWithName(String podName) {
    return body -> body instanceof V1Pod && getPodName((V1Pod) body).equals(podName);
  }

  private void defineDomainImage(String image) {
    configureDomain().withDefaultImage(image);
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
  }

  String getPodName() {
    return LegalNames.toPodName(UID, getServerName());
  }

  V1Container getCreatedPodSpecContainer() {
    return getCreatedPod().getSpec().getContainers().get(0);
  }

  List<V1Container> getCreatedPodSpecContainers() {
    return getCreatedPod().getSpec().getContainers();
  }

  List<V1Container> getCreatedPodSpecInitContainers() {
    return getCreatedPod().getSpec().getInitContainers();
  }

  @Test
  public void whenNoPod_createIt() {
    expectCreatePod(podWithName(getPodName())).returning(createTestPodModel());
    expectStepsAfterCreation();

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getCreatedMessageKey()));
  }

  @Test
  public void whenPodCreated_specHasOneContainer() {
    assertThat(getCreatedPod().getSpec().getContainers(), hasSize(1));
  }

  @Test
  public void whenPodCreated_hasSha256HashAnnotationForRecipe() {
    assertThat(getCreatedPod().getMetadata().getAnnotations(), hasKey(SHA256_ANNOTATION));
  }

  @Test
  public void whenPodCreatedWithLatestImage_useAlwaysPullPolicy() {
    defineDomainImage(LATEST_IMAGE);

    V1Container v1Container = getCreatedPodSpecContainer();

    assertThat(v1Container.getName(), equalTo(CONTAINER_NAME));
    assertThat(v1Container.getImage(), equalTo(LATEST_IMAGE));
    assertThat(v1Container.getImagePullPolicy(), equalTo(ALWAYS_IMAGEPULLPOLICY));
  }

  @Test
  public void whenPodCreatedWithVersionedImage_useIfNotPresentPolicy() {
    defineDomainImage(VERSIONED_IMAGE);

    V1Container v1Container = getCreatedPodSpecContainer();

    assertThat(v1Container.getImage(), equalTo(VERSIONED_IMAGE));
    assertThat(v1Container.getImagePullPolicy(), equalTo(IFNOTPRESENT_IMAGEPULLPOLICY));
  }

  @Test
  public void whenPodCreatedWithoutPullSecret_doNotAddToPod() {
    assertThat(getCreatedPod().getSpec().getImagePullSecrets(), empty());
  }

  @Test
  public void whenPodCreatedWithPullSecret_addToPod() {
    V1LocalObjectReference imagePullSecret = new V1LocalObjectReference().name("secret");
    configureDomain().withDefaultImagePullSecrets(imagePullSecret);

    assertThat(getCreatedPod().getSpec().getImagePullSecrets(), hasItem(imagePullSecret));
  }

  @Test
  public void whenPodCreated_withNoPVC_containerHasExpectedVolumeMounts() {
    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        containsInAnyOrder(
            writableVolumeMount(
                UID + SIT_CONFIG_MAP_VOLUME_SUFFIX, "/weblogic-operator/introspector"),
            readOnlyVolumeMount("weblogic-domain-debug-cm-volume", "/weblogic-operator/debug"),
            readOnlyVolumeMount("weblogic-domain-cm-volume", "/weblogic-operator/scripts")));
  }

  @Test
  public void whenPodCreated_lifecyclePreStopHasStopServerCommand() {
    assertThat(
        getCreatedPodSpecContainer().getLifecycle().getPreStop().getExec().getCommand(),
        contains("/weblogic-operator/scripts/stopServer.sh"));
  }

  @Test
  public void whenPodCreated_livenessProbeHasLivenessCommand() {
    assertThat(
        getCreatedPodSpecContainer().getLivenessProbe().getExec().getCommand(),
        contains("/weblogic-operator/scripts/livenessProbe.sh"));
  }

  @Test
  public void whenPodCreated_livenessProbeHasDefinedTuning() {
    assertThat(
        getCreatedPodSpecContainer().getLivenessProbe(),
        hasExpectedTuning(LIVENESS_INITIAL_DELAY, LIVENESS_TIMEOUT, LIVENESS_PERIOD));
  }

  @Test
  public void whenPodCreated_readinessProbeHasReadinessCommand() {
    V1HTTPGetAction getAction = getCreatedPodSpecContainer().getReadinessProbe().getHttpGet();
    assertThat(getAction.getPath(), equalTo("/weblogic/ready"));
    assertThat(getAction.getPort().getIntValue(), equalTo(listenPort));
  }

  @Test
  public void whenPodCreated_readinessProbeHasDefinedTuning() {
    assertThat(
        getCreatedPodSpecContainer().getReadinessProbe(),
        hasExpectedTuning(READINESS_INITIAL_DELAY, READINESS_TIMEOUT, READINESS_PERIOD));
  }

  @Test
  public void whenPodCreatedWithDomainV2Settings_livenessProbeHasConfiguredTuning() {
    configureServer()
        .withLivenessProbeSettings(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD);
    assertThat(
        getCreatedPodSpecContainer().getLivenessProbe(),
        hasExpectedTuning(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD));
  }

  @Test
  public void whenPodCreated_readinessProbeHasConfiguredTuning() {
    configureServer()
        .withReadinessProbeSettings(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD);
    assertThat(
        getCreatedPodSpecContainer().getReadinessProbe(),
        hasExpectedTuning(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD));
  }

  protected abstract void verifyPodReplaced();

  protected abstract void verifyPodNotReplacedWhen(PodMutator mutator);

  private void verifyPatchPod(PodMutator mutator, String... patchInstructions) {
    V1Pod existingPod = createPodModel();
    mutator.mutate(existingPod);
    initializeExistingPod(existingPod);

    verifyPatchPod(patchInstructions);
  }

  private void verifyPatchPod(String... patchInstructions) {
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        (pod, next) -> terminalStep);

    testSupport
        .createCannedResponse("patchPod")
        .withName(getPodName())
        .withNamespace(NS)
        .withBody(expect(patchInstructions))
        .returning(createTestPodModel());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getPatchedMessageKey()));
  }

  protected abstract ServerConfigurator configureServer(
      DomainConfigurator configurator, String serverName);

  @SuppressWarnings("unchecked")
  @Test
  public void whenPodCreated_hasPredefinedEnvVariables() {
    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(
            hasEnvVar("DOMAIN_NAME", DOMAIN_NAME),
            hasEnvVar("DOMAIN_HOME", "/u01/oracle/user_projects/domains"),
            hasEnvVar("ADMIN_NAME", ADMIN_SERVER),
            hasEnvVar("ADMIN_PORT", Integer.toString(ADMIN_PORT)),
            hasEnvVar("SERVER_NAME", getServerName()),
            hasEnvVar("ADMIN_USERNAME", null),
            hasEnvVar("ADMIN_PASSWORD", null),
            hasEnvVar("DOMAIN_UID", UID),
            hasEnvVar("NODEMGR_HOME", NODEMGR_HOME),
            hasEnvVar("SERVER_OUT_IN_POD_LOG", Boolean.toString(INCLUDE_SERVER_OUT_IN_POD_LOG)),
            hasEnvVar("LOG_HOME", null),
            hasEnvVar("SERVICE_NAME", LegalNames.toServerServiceName(UID, getServerName())),
            hasEnvVar("AS_SERVICE_NAME", LegalNames.toServerServiceName(UID, ADMIN_SERVER)),
            hasEnvVar("USER_MEM_ARGS", "-Djava.security.egd=file:/dev/./urandom")));
  }

  @Test
  public void whenPodCreated_withLogHomeSpecified_hasLogHomeEnvVariable() {
    final String MY_LOG_HOME = "/shared/mylogs";
    domainPresenceInfo.getDomain().getSpec().setLogHomeEnabled(true);
    domainPresenceInfo.getDomain().getSpec().setLogHome("/shared/mylogs");
    assertThat(getCreatedPodSpecContainer().getEnv(), allOf(hasEnvVar("LOG_HOME", MY_LOG_HOME)));
  }

  @Test
  public void whenPodCreated_withoutLogHomeSpecified_hasDefaultLogHomeEnvVariable() {
    domainPresenceInfo.getDomain().getSpec().setLogHomeEnabled(true);
    domainPresenceInfo.getDomain().getSpec().setLogHome(null);
    assertThat(
        getCreatedPodSpecContainer().getEnv(), allOf(hasEnvVar("LOG_HOME", LOG_HOME + "/" + UID)));
  }

  static Matcher<Iterable<? super V1EnvVar>> hasEnvVar(String name, String value) {
    return hasItem(new V1EnvVar().name(name).value(value));
  }

  static Matcher<Iterable<? super V1VolumeMount>> hasVolumeMount(String name, String path) {
    return hasItem(new V1VolumeMount().name(name).mountPath(path));
  }

  static Matcher<Iterable<? super V1Volume>> hasVolume(String name, String path) {
    return hasItem(new V1Volume().name(name).hostPath(new V1HostPathVolumeSource().path(path)));
  }

  static V1Container createContainer(String name, String image, String... command) {
    return new V1Container().name(name).image(image).command(Arrays.asList(command));
  }

  static Matcher<Iterable<? super V1Container>> hasContainer(
      String name, String image, String... command) {
    return hasItem(createContainer(name, image, command));
  }

  @Test
  public void whenDomainPresenceLacksClaims_adminPodSpecHasNoDomainStorageVolume() {
    assertThat(getVolumeWithName(getCreatedPod(), STORAGE_VOLUME_NAME), nullValue());
  }

  @Test
  public void createdPod_hasConfigMapVolume() {
    V1Volume credentialsVolume = getVolumeWithName(getCreatedPod(), CONFIGMAP_VOLUME_NAME);

    assertThat(credentialsVolume.getConfigMap().getName(), equalTo(DOMAIN_CONFIG_MAP_NAME));
    assertThat(credentialsVolume.getConfigMap().getDefaultMode(), equalTo(READ_AND_EXECUTE_MODE));
  }

  private V1Volume getVolumeWithName(V1Pod pod, String volumeName) {
    for (V1Volume volume : pod.getSpec().getVolumes()) {
      if (volume.getName().equals(volumeName)) {
        return volume;
      }
    }
    return null;
  }

  @Test
  public void whenPodCreated_hasExpectedLabels() {
    assertThat(
        getCreatedPod().getMetadata().getLabels(),
        allOf(
            hasEntry(
                LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION),
            hasEntry(LabelConstants.DOMAINUID_LABEL, UID),
            hasEntry(LabelConstants.DOMAINNAME_LABEL, DOMAIN_NAME),
            hasEntry(LabelConstants.SERVERNAME_LABEL, getServerName()),
            hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")));
  }

  @Test
  public void whenPodCreated_hasPrometheusAnnotations() {
    assertThat(
        getCreatedPod().getMetadata().getAnnotations(),
        allOf(
            hasEntry("prometheus.io/port", Integer.toString(listenPort)),
            hasEntry("prometheus.io/path", "/wls-exporter/metrics"),
            hasEntry("prometheus.io/scrape", "true")));
  }

  @Test
  @Ignore("Ignored: getCreatedPodSpecContainer is returing null because Pod is not yet created")
  public void whenPodCreated_containerUsesListenPort() {
    V1Container v1Container = getCreatedPodSpecContainer();

    assertThat(v1Container.getPorts(), hasSize(1));
    assertThat(v1Container.getPorts().get(0).getProtocol(), equalTo("TCP"));
    assertThat(v1Container.getPorts().get(0).getContainerPort(), equalTo(listenPort));
  }

  abstract void expectStepsAfterCreation();

  abstract String getCreatedMessageKey();

  abstract FiberTestSupport.StepFactory getStepFactory();

  V1Pod getCreatedPod() {
    PodFetcher podFetcher = new PodFetcher(getPodName());
    expectCreatePod(podFetcher).returning(createTestPodModel());
    expectStepsAfterCreation();

    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    return podFetcher.getCreatedPod();
  }

  V1PersistentVolumeList createPersistentVolumeList() {
    V1PersistentVolume pv =
        new V1PersistentVolume()
            .spec(
                new V1PersistentVolumeSpec()
                    .accessModes(Collections.singletonList("ReadWriteMany")));
    return new V1PersistentVolumeList().items(Collections.singletonList(pv));
  }

  CallTestSupport.CannedResponse expectListPersistentVolume() {
    return testSupport
        .createCannedResponse("listPersistentVolume")
        .withLabelSelectors("weblogic.domainUID=" + UID);
  }

  @Test
  public void whenPodHasUnknownCustomerLabel_ignoreIt() {
    verifyPodNotReplacedWhen(pod -> pod.getMetadata().putLabelsItem("customer.label", "value"));
  }

  @Test
  public void whenPodLacksExpectedCustomerLabel_addIt() {
    initializeExistingPod();

    configurator.withPodLabel("expected.label", "value");

    verifyPatchPod("add", "/metadata/labels/expected.label", "value");
  }

  @Test
  public void whenPodCustomerLabelHasBadValue_replaceIt() {
    configurator.withPodLabel("customer.label", "value");

    verifyPatchPod(
        pod -> pod.getMetadata().putLabelsItem("customer.label", "badvalue"),
        "replace",
        "/metadata/labels/customer.label",
        "value");
  }

  @Test
  public void whenPodLacksExpectedCustomerLabelAndRequestRequirement_replaceIt() {
    initializeExistingPod();

    configurator.withPodLabel("expected.label", "value").withRequestRequirement("widgets", "10");

    verifyPodReplaced();
  }

  void initializeExistingPod() {
    initializeExistingPod(createPodModel());
  }

  private V1Pod createPodModel() {
    return createPod(testSupport.getPacket());
  }

  @Test
  public void whenPodHasUnknownCustomerAnnotations_ignoreIt() {
    verifyPodNotReplacedWhen(pod -> pod.getMetadata().putAnnotationsItem("annotation", "value"));
  }

  @Test
  public void whenPodLacksExpectedCustomerAnnotations_addIt() {
    initializeExistingPod();

    configurator.withPodAnnotation("expected.annotation", "value");

    verifyPatchPod("add", "/metadata/annotations/expected.annotation", "value");
  }

  @Test
  public void whenPodCustomerAnnotationHasBadValue_replaceIt() {
    configurator.withPodAnnotation("customer.annotation", "value");

    verifyPatchPod(
        pod -> pod.getMetadata().putAnnotationsItem("customer.annotation", "badvalue"),
        "replace",
        "/metadata/annotations/customer.annotation",
        "value");
  }

  @Test
  public void whenConfigurationModifiesPodSecurityContext_replacePod() {
    initializeExistingPod();

    configurator.withPodSecurityContext(new V1PodSecurityContext().runAsGroup(12345L));

    verifyPodReplaced();
  }

  @Test
  public void whenConfigurationAddsNodeSelector_replacePod() {
    initializeExistingPod();

    configurator.withNodeSelector("key", "value");

    verifyPodReplaced();
  }

  @Test
  public void whenNullVsEmptyNodeSelector_dontReplaceIt() {
    verifyPodNotReplacedWhen(pod -> pod.getSpec().setNodeSelector(null));
  }

  @Test
  public void whenConfigurationModifiesContainerSecurityContext_replacePod() {
    initializeExistingPod();

    configurator.withContainerSecurityContext(new V1SecurityContext().runAsGroup(9876L));

    verifyPodReplaced();
  }

  @Test
  public void whenPodLivenessProbeSettingsChanged_replacePod() {
    initializeExistingPod();

    configurator.withDefaultLivenessProbeSettings(8, 7, 6);

    verifyPodReplaced();
  }

  @Test
  public void whenPodReadinessProbeSettingsChanged_replacePod() {
    initializeExistingPod();

    configurator.withDefaultReadinessProbeSettings(5, 4, 3);

    verifyPodReplaced();
  }

  @Test
  public void whenPodRequestRequirementChanged_replacePod() {
    initializeExistingPod();

    configurator.withRequestRequirement("resource", "5");

    verifyPodReplaced();
  }

  @Test
  public void whenPodRequestRequirementsEmptyVsNull_dontReplaceIt() {
    verifyPodNotReplacedWhen(pod -> pod.getSpec().getContainers().get(0).resources(null));
  }

  @Test
  public void whenPodLimitRequirementChanged_replacePod() {
    initializeExistingPod();

    configurator.withLimitRequirement("limit", "7");

    verifyPodReplaced();
  }

  private BodyMatcher expect(String[] patchInstructions) {
    return new PatchMatcher(patchInstructions);
  }

  private V1Container getSpecContainer(V1Pod pod) {
    return pod.getSpec().getContainers().get(0);
  }

  @Test
  public void whenExistingPodSpecHasK8sVolume_ignoreIt() {
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
  public void whenExistingPodSpecHasK8sVolumeMount_ignoreIt() {
    verifyPodNotReplacedWhen(
        (pod) ->
            getSpecContainer(pod)
                .addVolumeMountsItem(
                    new V1VolumeMount()
                        .name("dummy")
                        .mountPath(PodDefaults.K8S_SERVICE_ACCOUNT_MOUNT_PATH)));
  }

  @Test
  public void whenPodConfigurationAddsVolume_replacePod() {
    initializeExistingPod();

    configureServer().withAdditionalVolume("dummy", "/dummy");

    verifyPodReplaced();
  }

  abstract ServerConfigurator configureServer();

  @Test
  public void whenPodConfigurationAddsImagePullSecret_replacePod() {
    initializeExistingPod();

    configureDomain().withDefaultImagePullSecrets(new V1LocalObjectReference().name("secret"));

    verifyPodReplaced();
  }

  @Test
  public void whenPodConfigurationAddsVolumeMount_replacePod() {
    initializeExistingPod();

    configureServer().withAdditionalVolumeMount("dummy", "/dummy");

    verifyPodReplaced();
  }

  @Test
  public void whenPodConfigurationChangesImageName_replacePod() {
    initializeExistingPod();

    configureDomain().withDefaultImage(VERSIONED_IMAGE);

    verifyPodReplaced();
  }

  @Test
  public void whenPodConfigurationChangesImagePullPolicy_replacePod() {
    initializeExistingPod();

    configureDomain().withDefaultImagePullPolicy("NONE");

    verifyPodReplaced();
  }

  @Test
  public void whenDomainConfigurationAddsRestartVersion_replacePod() {
    initializeExistingPod();

    configureDomain().withRestartVersion("123");

    verifyPodReplaced();
  }

  @Test
  public void whenServerConfigurationAddsRestartVersion_replacePod() {
    initializeExistingPod();

    configureServer().withRestartVersion("123");

    verifyPodReplaced();
  }

  @Test
  public void whenServerListenPortChanged_replacePod() {
    initializeExistingPod();

    setServerPort(12345);

    verifyPodReplaced();
  }

  @Test
  public void whenServerAddsNap_replacePod() {
    initializeExistingPod();

    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint("nap1", "TCP", 1234, 9001));

    verifyPodReplaced();
  }

  protected void onAdminExpectListPersistentVolume() {
    // default is no-op
  }

  @Test
  public void whenNoPod_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectCreatePod(podWithName(getPodName())).failingWithStatus(401);
    expectStepsAfterCreation();

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenCompliantPodExists_recordIt() {
    initializeExistingPod();
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsFine(getExistsMessageKey()));
    ServerKubernetesObjects sko =
        domainPresenceInfo
            .getServers()
            .computeIfAbsent(getServerName(), k -> new ServerKubernetesObjects());
    assertThat(sko.getPod().get(), equalTo(createPodModel()));
  }

  void initializeExistingPod(V1Pod pod) {
    ServerKubernetesObjects sko =
        domainPresenceInfo
            .getServers()
            .computeIfAbsent(getServerName(), k -> new ServerKubernetesObjects());
    sko.getPod().set(pod);
  }

  abstract String getExistsMessageKey();

  abstract String getPatchedMessageKey();

  abstract String getReplacedMessageKey();

  abstract V1Pod createTestPodModel();

  V1EnvVar envItem(String name, String value) {
    return new V1EnvVar().name(name).value(value);
  }

  private V1Lifecycle createLifecycle() {
    return new V1Lifecycle()
        .preStop(
            new V1Handler()
                .exec(
                    new V1ExecAction().addCommandItem("/weblogic-operator/scripts/stopServer.sh")));
  }

  private V1Probe createReadinessProbe() {
    return new V1Probe()
        .exec(new V1ExecAction().addCommandItem("/weblogic-operator/scripts/readinessProbe.sh"))
        .initialDelaySeconds(READINESS_INITIAL_DELAY)
        .timeoutSeconds(READINESS_TIMEOUT)
        .periodSeconds(READINESS_PERIOD)
        .failureThreshold(1);
  }

  private V1Probe createLivenessProbe() {
    return new V1Probe()
        .exec(new V1ExecAction().addCommandItem("/weblogic-operator/scripts/livenessProbe.sh"))
        .initialDelaySeconds(LIVENESS_INITIAL_DELAY)
        .timeoutSeconds(LIVENESS_TIMEOUT)
        .periodSeconds(LIVENESS_PERIOD)
        .failureThreshold(1);
  }

  V1ObjectMeta createPodMetadata() {
    V1ObjectMeta meta =
        new V1ObjectMeta()
            .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION)
            .putLabelsItem(LabelConstants.DOMAINUID_LABEL, UID)
            .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, DOMAIN_NAME)
            .putLabelsItem(LabelConstants.DOMAINHOME_LABEL, "/u01/oracle/user_projects/domains")
            .putLabelsItem(LabelConstants.SERVERNAME_LABEL, getServerName())
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    AnnotationHelper.annotateForPrometheus(meta, listenPort);
    return meta;
  }

  V1Container createPodSpecContainer() {
    return new V1Container()
        .name(CONTAINER_NAME)
        .image(LATEST_IMAGE)
        .imagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
        .securityContext(new V1SecurityContext())
        .addPortsItem(
            new V1ContainerPort().name("default").containerPort(listenPort).protocol("TCP"))
        .lifecycle(createLifecycle())
        .volumeMounts(PodDefaults.getStandardVolumeMounts(UID))
        .command(createStartCommand())
        .addEnvItem(envItem("DOMAIN_NAME", DOMAIN_NAME))
        .addEnvItem(envItem("DOMAIN_HOME", "/u01/oracle/user_projects/domains"))
        .addEnvItem(envItem("ADMIN_NAME", ADMIN_SERVER))
        .addEnvItem(envItem("ADMIN_PORT", Integer.toString(ADMIN_PORT)))
        .addEnvItem(envItem("SERVER_NAME", getServerName()))
        .addEnvItem(envItem("ADMIN_USERNAME", null))
        .addEnvItem(envItem("ADMIN_PASSWORD", null))
        .addEnvItem(envItem("DOMAIN_UID", UID))
        .addEnvItem(envItem("NODEMGR_HOME", NODEMGR_HOME))
        .addEnvItem(
            envItem("SERVER_OUT_IN_POD_LOG", Boolean.toString(INCLUDE_SERVER_OUT_IN_POD_LOG)))
        .addEnvItem(envItem("LOG_HOME", null))
        .addEnvItem(envItem("SERVICE_NAME", LegalNames.toServerServiceName(UID, getServerName())))
        .addEnvItem(envItem("AS_SERVICE_NAME", LegalNames.toServerServiceName(UID, ADMIN_SERVER)))
        .addEnvItem(envItem("USER_MEM_ARGS", "-Djava.security.egd=file:/dev/./urandom"))
        .livenessProbe(createLivenessProbe())
        .readinessProbe(createReadinessProbe());
  }

  V1PodSpec createPodSpec() {
    return new V1PodSpec()
        .securityContext(new V1PodSecurityContext())
        .containers(Collections.singletonList(createPodSpecContainer()))
        .nodeSelector(Collections.emptyMap())
        .volumes(PodDefaults.getStandardVolumes(UID));
  }

  abstract List<String> createStartCommand();

  @Test
  public void whenDomainPresenceInfoLacksImageName_createdPodUsesDefaultImage() {
    configureDomain().withDefaultImage(null);

    assertThat(getCreatedPodSpecContainer().getImage(), equalTo(DEFAULT_IMAGE));
  }

  // todo test that changing a label or annotation does not change the hash

  interface PodMutator {
    void mutate(V1Pod pod);
  }

  static class PodFetcher implements BodyMatcher {
    private String podName;
    V1Pod createdPod;

    PodFetcher(String podName) {
      this.podName = podName;
    }

    V1Pod getCreatedPod() {
      return createdPod;
    }

    @Override
    public boolean matches(Object actualBody) {
      if (!isExpectedPod(actualBody)) {
        return false;
      } else {
        createdPod = (V1Pod) actualBody;
        return true;
      }
    }

    private boolean isExpectedPod(Object body) {
      return body instanceof V1Pod && getPodName((V1Pod) body).equals(podName);
    }
  }

  @SuppressWarnings("unused")
  static class VolumeMountMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.models.V1VolumeMount> {
    private String expectedName;
    private String expectedPath;
    private boolean readOnly;

    private VolumeMountMatcher(String expectedName, String expectedPath, boolean readOnly) {
      this.expectedName = expectedName;
      this.expectedPath = expectedPath;
      this.readOnly = readOnly;
    }

    static VolumeMountMatcher writableVolumeMount(String expectedName, String expectedPath) {
      return new PodHelperTestBase.VolumeMountMatcher(expectedName, expectedPath, false);
    }

    static VolumeMountMatcher readOnlyVolumeMount(String expectedName, String expectedPath) {
      return new PodHelperTestBase.VolumeMountMatcher(expectedName, expectedPath, true);
    }

    @Override
    protected boolean matchesSafely(V1VolumeMount item, Description mismatchDescription) {
      return expectedName.equals(item.getName())
          && expectedPath.equals(item.getMountPath())
          && readOnly == isReadOnly(item);
    }

    private Boolean isReadOnly(V1VolumeMount item) {
      return item.isReadOnly() != null && item.isReadOnly();
    }

    @Override
    public void describeTo(Description description) {
      description
          .appendText(getReadable())
          .appendText(" V1VolumeMount ")
          .appendValue(expectedName)
          .appendText(" at ")
          .appendValue(expectedPath);
    }

    private String getReadable() {
      return readOnly ? "read-only" : "writable";
    }
  }

  @SuppressWarnings("unused")
  static class ProbeMatcher
      extends org.hamcrest.TypeSafeDiagnosingMatcher<io.kubernetes.client.models.V1Probe> {
    private static final Integer EXPECTED_FAILURE_THRESHOLD = 1;
    private Integer expectedInitialDelay;
    private Integer expectedTimeout;
    private Integer expectedPeriod;

    private ProbeMatcher(int expectedInitialDelay, int expectedTimeout, int expectedPeriod) {
      this.expectedInitialDelay = expectedInitialDelay;
      this.expectedTimeout = expectedTimeout;
      this.expectedPeriod = expectedPeriod;
    }

    static ProbeMatcher hasExpectedTuning(
        int expectedInitialDelay, int expectedTimeout, int expectedPeriod) {
      return new PodHelperTestBase.ProbeMatcher(
          expectedInitialDelay, expectedTimeout, expectedPeriod);
    }

    @Override
    protected boolean matchesSafely(V1Probe item, Description mismatchDescription) {
      if (Objects.equals(expectedInitialDelay, item.getInitialDelaySeconds())
          && Objects.equals(expectedTimeout, item.getTimeoutSeconds())
          && Objects.equals(expectedPeriod, item.getPeriodSeconds())
          && Objects.equals(EXPECTED_FAILURE_THRESHOLD, item.getFailureThreshold())) return true;
      else {
        mismatchDescription
            .appendText("probe with initial delay ")
            .appendValue(item.getInitialDelaySeconds())
            .appendText(", timeout ")
            .appendValue(item.getTimeoutSeconds())
            .appendText(", period ")
            .appendValue(item.getPeriodSeconds())
            .appendText(" and failureThreshold ")
            .appendValue(item.getFailureThreshold());

        return false;
      }
    }

    @Override
    public void describeTo(Description description) {
      description
          .appendText("probe with initial delay ")
          .appendValue(expectedInitialDelay)
          .appendText(", timeout ")
          .appendValue(expectedTimeout)
          .appendText(", period ")
          .appendValue(expectedPeriod)
          .appendText(" and failureThreshold ")
          .appendValue(EXPECTED_FAILURE_THRESHOLD);
    }
  }

  protected static class PatchMatcher implements BodyMatcher {
    private Set<String> expectedInstructions = new HashSet<>();
    private int index = 0;

    PatchMatcher(String[] patchInstructions) {
      while (index < patchInstructions.length) addExpectedInstruction(patchInstructions);
    }

    private void addExpectedInstruction(String[] strings) {
      expectedInstructions.add(
          String.format(INSTRUCTION, strings[index], strings[index + 1], strings[index + 2]));
      index += 3;
    }

    @Override
    public boolean matches(Object actualBody) {
      if (!(actualBody instanceof List)) return false;
      List<?> instructions = (List<?>) actualBody;
      Set<String> actualInstructions = new HashSet<>();

      for (Object instruction : instructions)
        actualInstructions.add(new Gson().toJson((JsonElement) instruction));

      return actualInstructions.equals(expectedInstructions);
    }
  }
}

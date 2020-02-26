// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ConfigMapKeySelector;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1ExecAction;
import io.kubernetes.client.openapi.models.V1HTTPGetAction;
import io.kubernetes.client.openapi.models.V1Handler;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1Lifecycle;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectFieldSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodAffinity;
import io.kubernetes.client.openapi.models.V1PodAffinityTerm;
import io.kubernetes.client.openapi.models.V1PodAntiAffinity;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.calls.unprocessable.UnprocessableEntityBuilder;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.CONTAINER_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.helpers.AnnotationHelper.SHA256_ANNOTATION;
import static oracle.kubernetes.operator.helpers.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.helpers.Matchers.ProbeMatcher.hasExpectedTuning;
import static oracle.kubernetes.operator.helpers.Matchers.VolumeMountMatcher.readOnlyVolumeMount;
import static oracle.kubernetes.operator.helpers.Matchers.VolumeMountMatcher.writableVolumeMount;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasPvClaimVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasResourceQuantity;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolumeMount;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SIT_CONFIG_MAP_VOLUME_SUFFIX;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.LIVENESS_INITIAL_DELAY;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.LIVENESS_PERIOD;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.LIVENESS_TIMEOUT;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.READINESS_INITIAL_DELAY;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.READINESS_PERIOD;
import static oracle.kubernetes.operator.helpers.TuningParametersStub.READINESS_TIMEOUT;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings({"SameParameterValue", "ConstantConditions", "OctalInteger", "unchecked"})
public abstract class PodHelperTestBase {
  static final String NS = "namespace";
  static final String ADMIN_SERVER = "ADMIN_SERVER";
  static final Integer ADMIN_PORT = 7001;
  private static final String DOMAIN_NAME = "domain1";
  protected static final String UID = "uid1";
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

  final TerminalStep terminalStep = new TerminalStep();
  private final Domain domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  protected KubernetesTestSupport testSupport = new KubernetesTestSupport();
  protected List<Memento> mementos = new ArrayList<>();
  protected List<LogRecord> logRecords = new ArrayList<>();
  RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private Method getDomainSpec;
  private DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private String serverName;
  private int listenPort;
  private WlsDomainConfig domainTopology;
  protected final V1PodSecurityContext podSecurityContext = createPodSecurityContext(123L);
  protected final V1SecurityContext containerSecurityContext = createSecurityContext(222L);
  protected final V1Affinity affinity = createAffinity();

  PodHelperTestBase(String serverName, int listenPort) {
    this.serverName = serverName;
    this.listenPort = listenPort;
  }

  Domain getDomain() {
    return (Domain) testSupport.getResourceWithName(DOMAIN, DOMAIN_NAME);
  }

  String getPodName() {
    return LegalNames.toPodName(UID, getServerName());
  }

  static V1Container createContainer(String name, String image, String... command) {
    return new V1Container().name(name).image(image).command(Arrays.asList(command));
  }

  private String getServerName() {
    return serverName;
  }

  DomainConfigurator getConfigurator() {
    return configurator;
  }

  DomainSpec getConfiguredDomainSpec()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    if (getDomainSpec == null) {
      getDomainSpec = DomainConfigurator.class.getDeclaredMethod("getDomainSpec");
      getDomainSpec.setAccessible(true);
    }
    return (DomainSpec) getDomainSpec.invoke(configurator);
  }

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());
    mementos.add(InMemoryCertificates.install());

    WlsDomainConfigSupport configSupport = new WlsDomainConfigSupport(DOMAIN_NAME);
    configSupport.addWlsServer(ADMIN_SERVER, ADMIN_PORT);
    if (!ADMIN_SERVER.equals(serverName)) {
      configSupport.addWlsServer(serverName, listenPort);
    }
    configSupport.setAdminServerName(ADMIN_SERVER);

    testSupport.defineResources(domain);
    domainTopology = configSupport.createDomainConfig();
    testSupport
        .addToPacket(ProcessingConstants.DOMAIN_TOPOLOGY, domainTopology)
        .addToPacket(SERVER_SCAN, domainTopology.getServerConfig(serverName))
        .addDomainPresenceInfo(domainPresenceInfo);
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        new PassthroughPodAwaiterStepFactory());
  }

  abstract V1Pod createPod(Packet packet);

  private String[] getMessageKeys() {
    return new String[] {
      getCreatedMessageKey(), getExistsMessageKey(), getPatchedMessageKey(), getReplacedMessageKey()
    };
  }

  /**
   * Tear down test.
   * @throws Exception on failure
   */
  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

    testSupport.throwOnCompletionFailure();
  }

  private DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    return new DomainPresenceInfo(domain);
  }

  WlsServerConfig getServerTopology() {
    return domainTopology.getServerConfig(getServerName());
  }

  abstract void setServerPort(int port);

  private Domain createDomain() {
    return new Domain().withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME)).withSpec(createDomainSpec());
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec()
        .withDomainUid(UID)
        .withWebLogicCredentialsSecret(new V1SecretReference().name(CREDENTIALS_SECRET_NAME))
        .withIncludeServerOutInPodLog(INCLUDE_SERVER_OUT_IN_POD_LOG)
        .withImage(LATEST_IMAGE);
  }

  private void defineDomainImage(String image) {
    configureDomain().withDefaultImage(image);
  }

  private DomainConfigurator configureDomain() {
    return DomainConfiguratorFactory.forDomain(domainPresenceInfo.getDomain());
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
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(testSupport.getResources(KubernetesTestSupport.POD), notNullValue());
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
  public void whenPodCreated_withNoPvc_containerHasExpectedVolumeMounts() {
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
  public void whenPodCreatedWithAdminPortEnabled_readinessProbeHasReadinessCommand() {
    final Integer adminPort = 9002;
    domainTopology.getServerConfig(serverName).setAdminPort(adminPort);
    V1HTTPGetAction getAction = getCreatedPodSpecContainer().getReadinessProbe().getHttpGet();
    assertThat(getAction.getPath(), equalTo("/weblogic/ready"));
    assertThat(getAction.getPort().getIntValue(), equalTo(adminPort));
    assertThat(getAction.getScheme(), equalTo("HTTPS"));
  }

  @Test
  public void whenPodCreatedWithAdminPortEnabled_adminPortSecureEnvVarIsTrue() {
    final Integer adminPort = 9002;
    domainTopology.getServerConfig(serverName).setAdminPort(adminPort);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("ADMIN_PORT_SECURE", "true"));
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

  @Test 
  public void whenPodCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.failOnResource(POD, getPodName(), NS, new UnprocessableEntityBuilder()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus("FieldValueNotFound", "Test this failure"));
  }

  @Test
  public void whenPodCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.failOnResource(POD, getPodName(), NS, new UnprocessableEntityBuilder()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  public void whenPodCreationFailsDueToQuotaExceeded_reportInDomainStatus() {
    testSupport.failOnResource(POD, getPodName(), NS, createQuotaExceededException());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus("Forbidden", getQuotaExceededMessage()));
  }

  private ApiException createQuotaExceededException() {
    return new ApiException(HttpURLConnection.HTTP_FORBIDDEN, getQuotaExceededMessage());
  }

  private String getQuotaExceededMessage() {
    return "pod " + getPodName() + " is forbidden: quota exceeded";
  }

  @Test
  public void whenPodCreationFailsDueToQuotaExceeded_abortFiber() {
    testSupport.failOnResource(POD, getPodName(), NS, createQuotaExceededException());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(terminalStep.wasRun(), is(false));
  }

  protected abstract void verifyPodReplaced();

  protected abstract void verifyPodNotReplacedWhen(PodMutator mutator);

  private void misconfigurePod(PodMutator mutator) {
    V1Pod existingPod = createPodModel();
    mutator.mutate(existingPod);
    initializeExistingPod(existingPod);
  }

  private V1Pod getPatchedPod() {
    testSupport.addComponent(
        ProcessingConstants.PODWATCHER_COMPONENT_NAME,
        PodAwaiterStepFactory.class,
        new NullPodAwaiterStepFactory(terminalStep));

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getPatchedMessageKey()));

    return (V1Pod) testSupport.getResourceWithName(KubernetesTestSupport.POD, getPodName());
  }

  protected abstract ServerConfigurator configureServer(
      DomainConfigurator configurator, String serverName);

  abstract ServerConfigurator configureServer();

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
            hasEnvVar(
                "USER_MEM_ARGS",
                "-Djava.security.egd=file:/dev/./urandom")));
  }

  @Test
  public void whenPodCreated_withLogHomeSpecified_hasLogHomeEnvVariable() {
    final String myLogHome = "/shared/mylogs/";
    domainPresenceInfo.getDomain().getSpec().setLogHomeEnabled(true);
    domainPresenceInfo.getDomain().getSpec().setLogHome("/shared/mylogs/");
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("LOG_HOME", myLogHome));
  }

  @Test
  public void whenPodCreated_withoutLogHomeSpecified_hasDefaultLogHomeEnvVariable() {
    domainPresenceInfo.getDomain().getSpec().setLogHomeEnabled(true);
    domainPresenceInfo.getDomain().getSpec().setLogHome(null);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("LOG_HOME", LOG_HOME + "/" + UID));
  }

  private static final String OVERRIDE_DATA_DIR = "/u01/data";
  private static final String OVERRIDE_DATA_HOME = OVERRIDE_DATA_DIR + File.separator + UID;

  @Test
  public void whenPodCreated_withDataHomeSpecified_verifyDataHomeEnvDefined() {
    domainPresenceInfo.getDomain().getSpec().setDataHome(OVERRIDE_DATA_DIR);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar(ServerEnvVars.DATA_HOME, OVERRIDE_DATA_HOME));
  }

  private static final String EMPTY_DATA_HOME = "";

  @Test
  public void whenPodCreated_withDataHomeNotSpecified_verifyDataHomeEnvNotDefined() {
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar(ServerEnvVars.DATA_HOME, EMPTY_DATA_HOME)));
  }

  @Test
  public void whenPodCreated_withEmptyDataHomeSpecified_verifyDataHomeEnvNotDefined() {
    domainPresenceInfo.getDomain().getSpec().setDataHome(EMPTY_DATA_HOME);
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar(ServerEnvVars.DATA_HOME, EMPTY_DATA_HOME)));
  }

  private static final String NULL_DATA_HOME = null;

  @Test
  public void whenPodCreated_withNullDataHomeSpecified_verifyDataHomeEnvNotDefined() {
    domainPresenceInfo.getDomain().getSpec().setDataHome(NULL_DATA_HOME);
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar(ServerEnvVars.DATA_HOME, NULL_DATA_HOME)));
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

  abstract String getCreatedMessageKey();

  abstract FiberTestSupport.StepFactory getStepFactory();

  V1Pod getCreatedPod() {
    testSupport.runSteps(getStepFactory(), terminalStep);
    logRecords.clear();

    return (V1Pod) testSupport.getResources(KubernetesTestSupport.POD).get(0);
  }

  @Test
  public void whenPodHasUnknownCustomerLabel_ignoreIt() {
    verifyPodNotReplacedWhen(pod -> pod.getMetadata().putLabelsItem("customer.label", "value"));
  }

  @Test
  public void whenPodLacksExpectedCustomerLabel_addIt() {
    initializeExistingPod();
    configurator.withPodLabel("customer.label", "value");

    V1Pod patchedPod = getPatchedPod();

    assertThat(patchedPod.getMetadata().getLabels().get("customer.label"), equalTo("value"));
  }

  @Test
  public void whenPodLacksExpectedCustomerAnnotations_addIt() {
    initializeExistingPod();
    configurator.withPodAnnotation("customer.annotation", "value");

    V1Pod patchedPod = getPatchedPod();

    assertThat(patchedPod.getMetadata().getAnnotations().get("customer.annotation"), equalTo("value"));
  }

  @Test
  public void whenPodCustomerLabelHasBadValue_replaceIt() {
    configurator.withPodLabel("customer.label", "value");
    misconfigurePod(pod -> pod.getMetadata().putLabelsItem("customer.label", "badvalue"));

    V1Pod patchedPod = getPatchedPod();

    assertThat(patchedPod.getMetadata().getLabels().get("customer.label"), equalTo("value"));
  }

  @Test
  public void whenPodCustomerAnnotationHasBadValue_replaceIt() {
    configurator.withPodAnnotation("customer.annotation", "value");
    misconfigurePod(pod -> pod.getMetadata().putAnnotationsItem("customer.annotation", "badvalue"));

    V1Pod patchedPod = getPatchedPod();

    assertThat(patchedPod.getMetadata().getAnnotations().get("customer.annotation"), equalTo("value"));
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

  void initializeExistingPod(V1Pod pod) {
    testSupport.defineResources(pod);
    domainPresenceInfo.setServerPod(getServerName(), pod);
  }

  private V1Pod createPodModel() {
    return createPod(testSupport.getPacket());
  }

  @Test
  public void whenPodHasUnknownCustomerAnnotations_ignoreIt() {
    verifyPodNotReplacedWhen(pod -> pod.getMetadata().putAnnotationsItem("annotation", "value"));
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

  @Test
  public void whenNoPod_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(KubernetesTestSupport.POD, getPodName(), NS, 401);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenCompliantPodExists_logIt() {
    initializeExistingPod();
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsFine(getExistsMessageKey()));
    assertThat(domainPresenceInfo.getServerPod(serverName), equalTo(createPodModel()));
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
        .addEnvItem(
            envItem(
                "USER_MEM_ARGS",
                "-Djava.security.egd=file:/dev/./urandom"))
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

  static V1PodSecurityContext createPodSecurityContext(long runAsGroup) {
    return new V1PodSecurityContext().runAsGroup(runAsGroup);
  }

  static V1SecurityContext createSecurityContext(long runAsGroup) {
    return new V1SecurityContext().runAsGroup(runAsGroup);
  }

  static V1Affinity createAffinity() {
    V1PodAffinity podAffinity = new V1PodAffinity()
        .addRequiredDuringSchedulingIgnoredDuringExecutionItem(
            new V1PodAffinityTerm()
                .labelSelector(
                    new V1LabelSelector()
                        .addMatchExpressionsItem(
                            new V1LabelSelectorRequirement().key("security").operator("In").addValuesItem("S1")
                        )
                )
                .topologyKey("failure-domain.beta.kubernetes.io/zone")
        );
    V1PodAntiAffinity podAntiAffinity = new V1PodAntiAffinity()
        .addPreferredDuringSchedulingIgnoredDuringExecutionItem(
            new V1WeightedPodAffinityTerm()
                .weight(100)
                .podAffinityTerm(
                    new V1PodAffinityTerm()
                        .labelSelector(
                            new V1LabelSelector()
                                .addMatchExpressionsItem(
                                    new V1LabelSelectorRequirement().key("security").operator("In").addValuesItem("S2")
                                )
                        )
                        .topologyKey("failure-domain.beta.kubernetes.io/zon")
                )
        );
    return new V1Affinity().podAffinity(podAffinity).podAntiAffinity(podAntiAffinity);
  }

  static V1Toleration createToleration(String key, String operator, String value, String effect) {
    return new V1Toleration().key(key).operator(operator).value(value).effect(effect);
  }

  static V1EnvVar createFieldRefEnvVar(String name, String fieldPath) {
    return new V1EnvVar().name(name).valueFrom(
        new V1EnvVarSource().fieldRef(new V1ObjectFieldSelector().fieldPath(fieldPath)));
  }

  static V1EnvVar createConfigMapKeyRefEnvVar(String name, String configMapName, String key) {
    return new V1EnvVar().name(name).valueFrom(
        new V1EnvVarSource().configMapKeyRef(new V1ConfigMapKeySelector().name(configMapName).key(key)));
  }

  static V1EnvVar createSecretKeyRefEnvVar(String name, String secretName, String key) {
    return new V1EnvVar().name(name).valueFrom(
        new V1EnvVarSource().secretKeyRef(new V1SecretKeySelector().name(secretName).key(key)));
  }

  abstract List<String> createStartCommand();

  @Test
  public void whenDomainPresenceInfoLacksImageName_createdPodUsesDefaultImage() {
    configureDomain().withDefaultImage(null);

    assertThat(getCreatedPodSpecContainer().getImage(), equalTo(DEFAULT_IMAGE));
  }

  @Test
  public void whenDomainHasAdditionalVolumes_createPodWithThem() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  public void whenDomainHasAdditionalPvClaimVolume_createPodWithIt() {
    getConfigurator()
        .withAdditionalPvClaimVolume("volume1", "myPersistentVolumeClaim");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasPvClaimVolume("volume1", "myPersistentVolumeClaim")));
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
  public void whenDomainHasAffinity_createPodWithIt() {
    getConfigurator()
        .withAffinity(affinity);

    assertThat(
        getCreatedPod().getSpec().getAffinity(),
        is(affinity));
  }

  @Test
  public void whenServerHasAffinity_createPodWithIt() {
    configureServer()
        .withAffinity(affinity);

    assertThat(
        getCreatedPod().getSpec().getAffinity(),
        is(affinity));
  }

  @Test
  public void whenDomainHasNodeSelector_createPodWithIt() {
    getConfigurator()
        .withNodeSelector("os_arch", "x86_64");

    assertThat(
        getCreatedPod().getSpec().getNodeSelector(),
        hasEntry("os_arch", "x86_64"));
  }

  @Test
  public void whenServerHasNodeSelector_createPodWithIt() {
    configureServer()
        .withNodeSelector("os_arch", "x86_64");

    assertThat(
        getCreatedPod().getSpec().getNodeSelector(),
        hasEntry("os_arch", "x86_64"));
  }

  @Test
  public void whenDomainHasNodeName_createPodWithIt() {
    getConfigurator()
        .withNodeName("kube-01");

    assertThat(
        getCreatedPod().getSpec().getNodeName(),
        is("kube-01"));
  }

  @Test
  public void whenServerHasNodeName_createPodWithIt() {
    configureServer()
        .withNodeName("kube-01");

    assertThat(
        getCreatedPod().getSpec().getNodeName(),
        is("kube-01"));
  }

  @Test
  public void whenDomainHasSchedulerName_createPodWithIt() {
    getConfigurator()
        .withSchedulerName("my-scheduler");

    assertThat(
        getCreatedPod().getSpec().getSchedulerName(),
        is("my-scheduler"));
  }

  @Test
  public void whenServerHasSchedulerName_createPodWithIt() {
    configureServer()
        .withSchedulerName("my-scheduler");

    assertThat(
        getCreatedPod().getSpec().getSchedulerName(),
        is("my-scheduler"));
  }

  @Test
  public void whenDomainHasRuntimeClassName_createPodWithIt() {
    getConfigurator()
        .withRuntimeClassName("RuntimeClassName");

    assertThat(
        getCreatedPod().getSpec().getRuntimeClassName(),
        is("RuntimeClassName"));
  }

  @Test
  public void whenServerHasRuntimeClassName_createPodWithIt() {
    configureServer()
        .withRuntimeClassName("RuntimeClassName");

    assertThat(
        getCreatedPod().getSpec().getRuntimeClassName(),
        is("RuntimeClassName"));
  }

  @Test
  public void whenDomainHasPriorityClassName_createPodWithIt() {
    getConfigurator()
        .withPriorityClassName("PriorityClassName");

    assertThat(
        getCreatedPod().getSpec().getPriorityClassName(),
        is("PriorityClassName"));
  }

  @Test
  public void whenServerHasPriorityClassName_createPodWithIt() {
    configureServer()
        .withPriorityClassName("PriorityClassName");

    assertThat(
        getCreatedPod().getSpec().getPriorityClassName(),
        is("PriorityClassName"));
  }

  @Test
  public void whenDomainHasRestartPolicy_createPodWithIt() {
    getConfigurator()
        .withRestartPolicy("Always");

    assertThat(
        getCreatedPod().getSpec().getRestartPolicy(),
        is("Always"));
  }

  @Test
  public void whenServerHasRestartPolicy_createPodWithIt() {
    configureServer()
        .withRestartPolicy("Always");

    assertThat(
        getCreatedPod().getSpec().getRestartPolicy(),
        is("Always"));
  }

  @Test
  public void whenDomainHasPodSecurityContext_createPodWithIt() {
    getConfigurator()
        .withPodSecurityContext(podSecurityContext);

    assertThat(
        getCreatedPod().getSpec().getSecurityContext(),
        is(podSecurityContext));
  }

  @Test
  public void whenServerHasPodSecurityContext_createPodWithIt() {
    configureServer()
        .withPodSecurityContext(podSecurityContext);

    assertThat(
        getCreatedPod().getSpec().getSecurityContext(),
        is(podSecurityContext));
  }

  @Test
  public void whenDomainHasContainerSecurityContext_createContainersWithIt() {
    getConfigurator()
        .withContainerSecurityContext(containerSecurityContext);

    getCreatedPodSpecContainers()
        .forEach(c -> assertThat(
            c.getSecurityContext(),
            is(containerSecurityContext)));
  }

  @Test
  public void whenServerHasContainerSecurityContext_createContainersWithIt() {
    configureServer()
        .withContainerSecurityContext(containerSecurityContext);

    getCreatedPodSpecContainers()
        .forEach(c -> assertThat(
            c.getSecurityContext(),
            is(containerSecurityContext)));
  }

  @Test
  public void whenServerHasResources_createContainersWithThem() {
    configureServer()
        .withLimitRequirement("cpu", "1Gi")
        .withRequestRequirement("memory", "250m");

    List<V1Container> containers = getCreatedPodSpecContainers();

    containers.forEach(c -> assertThat(c.getResources().getLimits(), hasResourceQuantity("cpu", "1Gi")));
    containers.forEach(c -> assertThat(c.getResources().getRequests(), hasResourceQuantity("memory", "250m")));
  }

  @Test
  public void whenDomainHasResources_createContainersWithThem() {
    getConfigurator()
        .withLimitRequirement("cpu", "1Gi")
        .withRequestRequirement("memory", "250m");

    List<V1Container> containers = getCreatedPodSpecContainers();

    containers.forEach(c -> assertThat(c.getResources().getLimits(), hasResourceQuantity("cpu", "1Gi")));
    containers.forEach(c -> assertThat(c.getResources().getRequests(), hasResourceQuantity("memory", "250m")));
  }

  // todo test that changing a label or annotation does not change the hash

  interface PodMutator {
    void mutate(V1Pod pod);
  }

  protected static class NullPodAwaiterStepFactory implements PodAwaiterStepFactory {
    private final Step ne;

    NullPodAwaiterStepFactory(Step next) {
      this.ne = next;
    }

    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return ne;
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return ne;
    }
  }

  protected static class PassthroughPodAwaiterStepFactory implements PodAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return next;
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return next;
    }
  }
}

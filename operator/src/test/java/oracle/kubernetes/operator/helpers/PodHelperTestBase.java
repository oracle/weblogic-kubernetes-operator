// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
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
import io.kubernetes.client.openapi.models.V1OwnerReference;
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
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.WlsDomainConfigSupport;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainValidationBaseTest;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static com.meterware.simplestub.Stub.createStrictStub;
import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.DOMAINZIP_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.NUM_CONFIG_MAPS;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.SECRETS_MD_5;
import static oracle.kubernetes.operator.KubernetesConstants.ALWAYS_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.CONTAINER_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_DEBUG_CONFIG_MAP_SUFFIX;
import static oracle.kubernetes.operator.KubernetesConstants.IFNOTPRESENT_IMAGEPULLPOLICY;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.LabelConstants.MII_UPDATED_RESTART_REQUIRED_LABEL;
import static oracle.kubernetes.operator.LabelConstants.OPERATOR_VERSION;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_SUCCESS;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.helpers.AnnotationHelper.SHA256_ANNOTATION;
import static oracle.kubernetes.operator.helpers.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.POD;
import static oracle.kubernetes.operator.helpers.Matchers.ProbeMatcher.hasExpectedTuning;
import static oracle.kubernetes.operator.helpers.Matchers.VolumeMatcher.volume;
import static oracle.kubernetes.operator.helpers.Matchers.VolumeMountMatcher.readOnlyVolumeMount;
import static oracle.kubernetes.operator.helpers.Matchers.VolumeMountMatcher.writableVolumeMount;
import static oracle.kubernetes.operator.helpers.Matchers.hasEnvVar;
import static oracle.kubernetes.operator.helpers.Matchers.hasPvClaimVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasResourceQuantity;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolume;
import static oracle.kubernetes.operator.helpers.Matchers.hasVolumeMount;
import static oracle.kubernetes.operator.helpers.StepContextConstants.DEBUG_CM_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.INTROSPECTOR_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.RUNTIME_ENCRYPTION_SECRET_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.SCRIPTS_VOLUME;
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
public abstract class PodHelperTestBase extends DomainValidationBaseTest {
  static final String NS = "namespace";
  static final String ADMIN_SERVER = "ADMIN_SERVER";
  static final Integer ADMIN_PORT = 7001;
  static final Integer SSL_PORT = 7002;
  protected static final String DOMAIN_NAME = "domain1";
  protected static final String UID = "uid1";
  protected static final String KUBERNETES_UID = "12345";
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
  private static final String CONFIGMAP_VOLUME_NAME = "weblogic-scripts-cm-volume";
  private static final int READ_AND_EXECUTE_MODE = 0555;
  private static final String TEST_PRODUCT_VERSION = "unit-test";

  final TerminalStep terminalStep = new TerminalStep();
  private final Domain domain = createDomain();
  private final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
  protected final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  protected final List<Memento> mementos = new ArrayList<>();
  protected final List<LogRecord> logRecords = new ArrayList<>();
  final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private Method getDomainSpec;
  private final DomainConfigurator configurator = DomainConfiguratorFactory.forDomain(domain);
  private final String serverName;
  private final int listenPort;
  private WlsDomainConfig domainTopology;
  protected final V1PodSecurityContext podSecurityContext = createPodSecurityContext(123L);
  protected final V1SecurityContext containerSecurityContext = createSecurityContext(222L);
  protected final V1Affinity affinity = createAffinity();
  private Memento hashMemento;

  PodHelperTestBase(String serverName, int listenPort) {
    this.serverName = serverName;
    this.listenPort = listenPort;
  }

  Domain getDomain() {
    return testSupport.getResourceWithName(DOMAIN, DOMAIN_NAME);
  }

  String getPodName() {
    return LegalNames.toPodName(UID, getServerName());
  }

  static V1Container createContainer(String name, String image, String... command) {
    return new V1Container().name(name).image(image).command(Arrays.asList(command));
  }

  static List<V1EnvVar> getPredefinedEnvVariables(String serverName) {
    List<V1EnvVar> envVars  = new ArrayList<>();
    envVars.add(createEnvVar("DOMAIN_NAME", DOMAIN_NAME));
    envVars.add(createEnvVar("DOMAIN_HOME", "/u01/oracle/user_projects/domains"));
    envVars.add(createEnvVar("ADMIN_NAME", ADMIN_SERVER));
    envVars.add(createEnvVar("ADMIN_PORT", Integer.toString(ADMIN_PORT)));
    envVars.add(createEnvVar("SERVER_NAME", serverName));
    envVars.add(createEnvVar("DOMAIN_UID", UID));
    envVars.add(createEnvVar("NODEMGR_HOME", NODEMGR_HOME));
    envVars.add(createEnvVar("LOG_HOME", null));
    envVars.add(createEnvVar("SERVER_OUT_IN_POD_LOG", Boolean.toString(INCLUDE_SERVER_OUT_IN_POD_LOG)));
    envVars.add(createEnvVar("SERVICE_NAME", LegalNames.toServerServiceName(UID, serverName)));
    envVars.add(createEnvVar("AS_SERVICE_NAME", LegalNames.toServerServiceName(UID, ADMIN_SERVER)));
    envVars.add(createEnvVar(
            "USER_MEM_ARGS",
            "-Djava.security.egd=file:/dev/./urandom"));
    envVars.add(createEnvVar("ADMIN_USERNAME", null));
    envVars.add(createEnvVar("ADMIN_PASSWORD", null));
    return envVars;
  }

  private static V1EnvVar createEnvVar(String name, String value) {
    return new V1EnvVar().name(name).value(value);
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

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(testSupport.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(hashMemento = UnitTestHash.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(setProductVersion(TEST_PRODUCT_VERSION));
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE)
            .ignoringLoggedExceptions(ApiException.class));

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

  private Memento setProductVersion(String productVersion) throws NoSuchFieldException {
    return StaticStubSupport.install(PodStepContext.class, "productVersion", productVersion);
  }

  abstract V1Pod createPod(Packet packet);

  private String[] getMessageKeys() {
    return new String[] {
      getCreatedMessageKey(),
        getExistsMessageKey(),
        getPatchedMessageKey(),
        getReplacedMessageKey(),
        getDomainValidationFailedKey()
    };
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

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
    return new Domain()
        .withApiVersion(KubernetesConstants.DOMAIN_VERSION)
        .withKind(KubernetesConstants.DOMAIN)
        .withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME).uid(KUBERNETES_UID))
        .withSpec(createDomainSpec());
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

  final DomainConfigurator configureDomain() {
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

  // Returns the YAML for a 3.0 domain-in-image pod with only the plain port enabled.
  abstract String getReferencePlainPortPodYaml_3_0();

  // Returns the YAML for a 3.1 domain-in-image pod with only the plain port enabled.
  abstract String getReferencePlainPortPodYaml_3_1();

  // Returns the YAML for a 3.0 domain-in-image pod with the SSL port enabled.
  abstract String getReferenceSslPortPodYaml_3_0();

  // Returns the YAML for a 3.1 domain-in-image pod with the SSL port enabled.
  abstract String getReferenceSslPortPodYaml_3_1();

  // Returns the YAML for a 3.1 Mii Pod.
  abstract String getReferenceMiiPodYaml();

  @Test
  public void afterUpgradingPlainPortPodFrom30_patchIt() {
    useProductionHash();
    initializeExistingPod(loadPodModel(getReferencePlainPortPodYaml_3_0()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  public void afterUpgradingPlainPortPodFrom31_patchIt() {
    useProductionHash();
    initializeExistingPod(loadPodModel(getReferencePlainPortPodYaml_3_1()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  public void afterUpgradingSslPortPodFrom30_patchIt() {
    useProductionHash();
    getServerTopology().setSslListenPort(7002);
    initializeExistingPod(loadPodModel(getReferenceSslPortPodYaml_3_0()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  public void afterUpgradingSslPortPodFrom31_patchIt() {
    useProductionHash();
    getServerTopology().setSslListenPort(7002);
    initializeExistingPod(loadPodModel(getReferenceSslPortPodYaml_3_1()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  void useProductionHash() {
    hashMemento.revert();
  }

  @Test
  public void afterUpgradingMiiPodFrom31_patchIt() {
    useProductionHash();
    testSupport.addToPacket(SECRETS_MD_5, "originalSecret");
    testSupport.addToPacket(DOMAINZIP_HASH, "originalSecret");
    disableAutoIntrospectOnNewMiiPods();
    initializeExistingPod(loadPodModel(getReferenceMiiPodYaml()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  private V1Pod loadPodModel(String podYaml) {
    return new Yaml().loadAs(podYaml, V1Pod.class);
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
  public void whenPodCreated_withNoPvc_image_containerHasExpectedVolumeMounts() {
    configurator.withDomainHomeSourceType(DomainSourceType.Image);
    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        containsInAnyOrder(
            writableVolumeMount(
                  INTROSPECTOR_VOLUME, "/weblogic-operator/introspector"),
            readOnlyVolumeMount("weblogic-domain-debug-cm-volume", "/weblogic-operator/debug"),
            readOnlyVolumeMount("weblogic-scripts-cm-volume", "/weblogic-operator/scripts")));
  }

  @Test
  public void whenPodCreated_withNoPvc_fromModel_containerHasExpectedVolumeMounts() {
    reportInspectionWasRun();
    configurator.withDomainHomeSourceType(DomainSourceType.FromModel)
        .withRuntimeEncryptionSecret("my-runtime-encryption-secret");
    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        containsInAnyOrder(
            writableVolumeMount(
                  INTROSPECTOR_VOLUME, "/weblogic-operator/introspector"),
            readOnlyVolumeMount("weblogic-domain-debug-cm-volume", "/weblogic-operator/debug"),
            readOnlyVolumeMount("weblogic-scripts-cm-volume", "/weblogic-operator/scripts"),
            readOnlyVolumeMount(RUNTIME_ENCRYPTION_SECRET_VOLUME,
                RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH))); 
  }

  @Test
  public void whenIntrospectionCreatesMultipleConfigMaps_createCorrespondingVolumeMounts() {
    testSupport.addToPacket(NUM_CONFIG_MAPS, "3");

    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        allOf(
              hasItem(writableVolumeMount(INTROSPECTOR_VOLUME, "/weblogic-operator/introspector")),
              hasItem(writableVolumeMount(INTROSPECTOR_VOLUME + "-1", "/weblogic-operator/introspector-1")),
              hasItem(writableVolumeMount(INTROSPECTOR_VOLUME + "-2", "/weblogic-operator/introspector-2"))
              ));
  }

  public void reportInspectionWasRun() {
    testSupport.addToPacket(MAKE_RIGHT_DOMAIN_OPERATION, reportIntrospectionRun());
  }

  private MakeRightDomainOperation reportIntrospectionRun() {
    return createStub(InspectionWasRun.class);
  }

  abstract static class InspectionWasRun implements MakeRightDomainOperation {
    @Override
    public boolean wasInspectionRun() {
      return true;
    }
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
  public void whenPodCreatedWithOnRestartDistribution_dontAddDynamicUpdateEnvVar() {
    configureDomain().withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.ON_RESTART);

    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("DYNAMIC_CONFIG_OVERRIDE")));
  }

  @Test
  public void whenPodCreatedWithDynamicDistribution_addDynamicUpdateEnvVar() {
    configureDomain().withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.DYNAMIC);

    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("DYNAMIC_CONFIG_OVERRIDE"));
  }

  @Test
  public void whenDistributionStrategyModified_dontReplacePod() {
    configureDomain().withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.DYNAMIC);
    initializeExistingPod();

    configureDomain().withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.ON_RESTART);
    verifyPodNotReplaced();
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
    testSupport.failOnResource(POD, getPodName(), NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus("FieldValueNotFound",
        "testcall in namespace junit, for testName: Test this failure"));
  }

  @Test
  public void whenPodCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.failOnResource(POD, getPodName(), NS, new UnrecoverableErrorBuilderImpl()
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

    assertThat(getDomain(), hasStatus("Forbidden",
        "testcall in namespace junit, for testName: " + getQuotaExceededMessage()));
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

  // todo set property to indicate dynamic/on_restart copying
  protected abstract void verifyPodReplaced();

  protected void verifyPodPatched() {
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, not(containsFine(getExistsMessageKey())));
    assertThat(logRecords, containsInfo(getPatchedMessageKey()));
  }

  protected void verifyPodNotPatched() {
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsFine(getExistsMessageKey()));
    assertThat(logRecords, not(containsInfo(getPatchedMessageKey())));
  }

  protected void verifyPodNotReplaced() {
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, not(containsInfo(getReplacedMessageKey())));
    assertThat(logRecords, containsFine(getExistsMessageKey()));
  }

  protected abstract void verifyPodNotReplacedWhen(PodMutator mutator);

  private void misconfigurePod(PodMutator mutator) {
    V1Pod existingPod = createPodModel();
    mutator.mutate(existingPod);
    initializeExistingPod(existingPod);
  }

  private V1Pod getPatchedPod() {
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getPatchedMessageKey()));

    return testSupport.getResourceWithName(KubernetesTestSupport.POD, getPodName());
  }

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
  void whenPodCreated_hasProductVersion() throws NoSuchFieldException {
    assertThat(getCreatedPod().getMetadata().getLabels(), hasEntry(OPERATOR_VERSION, TEST_PRODUCT_VERSION));
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

  @Test
  public void whenPodCreated_withLivenessCustomScriptSpecified_hasEnvVariable() {
    final String customScript = "/u01/customLiveness.sh";
    domainPresenceInfo.getDomain().getSpec().setLivenessProbeCustomScript(customScript);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("LIVENESS_PROBE_CUSTOM_SCRIPT", customScript));
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

    assertThat(credentialsVolume.getConfigMap().getName(), equalTo(SCRIPT_CONFIG_MAP_NAME));
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
            hasEntry(LabelConstants.DOMAINUID_LABEL, UID),
            hasEntry(LabelConstants.DOMAINNAME_LABEL, DOMAIN_NAME),
            hasEntry(LabelConstants.SERVERNAME_LABEL, getServerName()),
            hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")));
  }

  @Test
  public void whenPodCreated_containerUsesListenPort() {
    final V1ContainerPort plainPort = getContainerPort("default");

    assertThat(plainPort, notNullValue());
    assertThat(plainPort.getProtocol(), equalTo("TCP"));
    assertThat(plainPort.getContainerPort(), equalTo(listenPort));
  }

  private V1ContainerPort getContainerPort(String portName) {
    return getCreatedPodSpecContainer().getPorts().stream().filter(
          p -> p.getName().equalsIgnoreCase(portName)).findFirst().orElse(null);
  }

  @Test
  public void whenPodCreatedWithSslPort_containerUsesIt() {
    domainTopology.getServerConfig(serverName).setSslListenPort(SSL_PORT);
    final V1ContainerPort sslPort = getContainerPort("default-secure");

    assertThat(sslPort, notNullValue());
    assertThat(sslPort.getProtocol(), equalTo("TCP"));
    assertThat(sslPort.getContainerPort(), equalTo(SSL_PORT));
  }

  @Test
  public void whenPodCreatedWithAdminPortEnabled__containerUsesIt() {
    final Integer adminPort = 9002;
    domainTopology.getServerConfig(serverName).setAdminPort(adminPort);
    final V1ContainerPort sslPort = getContainerPort("default-admin");

    assertThat(sslPort, notNullValue());
    assertThat(sslPort.getProtocol(), equalTo("TCP"));
    assertThat(sslPort.getContainerPort(), equalTo(adminPort));
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

  void initializeExistingPodWithIntrospectVersion(String introspectVersion) {
    initializeExistingPodWithIntrospectVersion(createPodModel(), introspectVersion);
  }

  void initializeExistingPodWithIntrospectVersion(V1Pod pod, String introspectVersion) {
    testSupport.defineResources(pod);
    pod.getMetadata().getLabels().put(LabelConstants.INTROSPECTION_STATE_LABEL, introspectVersion);
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
  public void whenServerConfigurationAddsIntrospectionVersion_patchPod() {
    initializeExistingPod();

    configurator.withIntrospectVersion("123");

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, not(containsFine(getExistsMessageKey())));
    assertThat(logRecords, containsInfo(getPatchedMessageKey()));
  }

  @Test
  public void whenServerConfigurationIntrospectionVersionTheSame_dontPatchPod() {
    initializeExistingPodWithIntrospectVersion("123");

    configurator.withIntrospectVersion("123");

    verifyPodNotPatched();
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
  public void whenMiiSecretsHashChanged_replacePod() {
    testSupport.addToPacket(SECRETS_MD_5, "originalSecret");
    initializeExistingPod();

    testSupport.addToPacket(SECRETS_MD_5, "newSecret");

    verifyPodReplaced();
  }

  @Test
  public void whenMiiDomainZipHashChanged_replacePod() {
    testSupport.addToPacket(DOMAINZIP_HASH, "originalSecret");
    initializeExistingPod();

    testSupport.addToPacket(DOMAINZIP_HASH, "newSecret");

    verifyPodReplaced();
  }

  @Test
  public void whenMiiDynamicUpdateDynamicChangesOnlyButOnlineUpdateDisabled_replacePod() {
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_SUCCESS);

    verifyPodReplaced();
  }

  @Test
  public void whenMiiDynamicUpdateDynamicChangesOnly_dontReplacePod() {
    configureDomain().withMIIOnlineUpdate();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_SUCCESS);

    verifyPodPatched();
  }

  private void initializeMiiUpdateTest(String miiDynamicUpdateResult) {
    testSupport.addToPacket(DOMAINZIP_HASH, "originalZip");
    disableAutoIntrospectOnNewMiiPods();
    initializeExistingPod();

    testSupport.addToPacket(DOMAINZIP_HASH, "newZipHash");
    testSupport.addToPacket(MII_DYNAMIC_UPDATE, miiDynamicUpdateResult);
  }

  // Mii requires an introspection when bringing up a new pod. To disable that in these tests,
  // we will pretend that the domain is not MII.
  private void disableAutoIntrospectOnNewMiiPods() {
    domain.getSpec().setDomainHomeSourceType(DomainSourceType.Image);
  }

  @Test
  public void whenMiiDynamicUpdateDynamicChangesOnly_updateDomainZipHash() {
    configureDomain().withMIIOnlineUpdate();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_SUCCESS);

    verifyPodPatched();

    assertThat(getPodLabel(LabelConstants.MODEL_IN_IMAGE_DOMAINZIP_HASH), equalTo(paddedZipHash("newZipHash")));
  }

  @Test
  public void whenMiiOnlineUpdateSettingEnabled_dontReplacePod() {
    testSupport.addToPacket(DOMAINZIP_HASH, "originalZip");
    disableAutoIntrospectOnNewMiiPods();
    initializeExistingPod();

    configureDomain().withMIIOnlineUpdate();
    verifyPodNotReplaced();
  }

  private String getPodLabel(String labelName) {
    return domainPresenceInfo.getServerPod(getServerName()).getMetadata().getLabels().get(labelName);
  }

  @Test
  public void whenMiiNonDynamicUpdateDynamicChangesCommitOnly_dontReplacePod() {
    configureDomain().withMIIOnlineUpdate();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_RESTART_REQUIRED);

    verifyPodPatched();
  }

  @Test
  public void whenMiiNonDynamicUpdateDynamicChangesCommitOnly_addRestartRequiredLabel() {
    configureDomain().withMIIOnlineUpdate();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_RESTART_REQUIRED);

    assertThat(getPatchedPod().getMetadata().getLabels(), hasEntry(MII_UPDATED_RESTART_REQUIRED_LABEL, "true"));
  }

  @Test
  public void whenMiiNonDynamicUpdateDynamicChangesCommitAndRoll_replacePod() {
    configureDomain().withMIIOnlineUpdateOnDynamicChangesUpdateAndRoll();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_RESTART_REQUIRED);

    verifyPodReplaced();
  }

  private String paddedZipHash(String hash) {
    return "md5." + hash + ".md5";
  }

  @Test
  public void whenNoPod_onFiveHundred() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(KubernetesTestSupport.POD, getPodName(), NS, 500);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    assertThat(getDomain(), hasStatus("ServerError",
            "testcall in namespace junit, for testName: failure reported in test"));
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

  abstract String getDomainValidationFailedKey();

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
            .putLabelsItem(LabelConstants.DOMAINUID_LABEL, UID)
            .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, DOMAIN_NAME)
            .putLabelsItem(LabelConstants.DOMAINHOME_LABEL, "/u01/oracle/user_projects/domains")
            .putLabelsItem(LabelConstants.SERVERNAME_LABEL, getServerName())
            .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    AnnotationHelper.annotateForPrometheus(meta, "/wls-exporter", listenPort);
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
        .volumeMounts(PodDefaults.getStandardVolumeMounts(UID, 1))
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
        .volumes(PodDefaults.getStandardVolumes(UID, 1));
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
  public void verifyStandardVolumes() {
    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasItem(volume(INTROSPECTOR_VOLUME, UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX)),
              hasItem(volume(DEBUG_CM_VOLUME, UID + DOMAIN_DEBUG_CONFIG_MAP_SUFFIX)),
              hasItem(volume(SCRIPTS_VOLUME, SCRIPT_CONFIG_MAP_NAME))));
  }

  @Test
  public void whenIntrospectionCreatesMultipleConfigMaps_createCorrespondingVolumes() {
    testSupport.addToPacket(NUM_CONFIG_MAPS, "3");

    assertThat(
          getCreatedPod().getSpec().getVolumes(),
          allOf(hasItem(volume(INTROSPECTOR_VOLUME, UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX)),
                hasItem(volume(INTROSPECTOR_VOLUME + "-1", UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX + "-1")),
                hasItem(volume(INTROSPECTOR_VOLUME + "-2", UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX + "-2"))));
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

  @Test
  public void whenPodCreated_createPodWithOwnerReference() {
    V1OwnerReference expectedReference = new V1OwnerReference()
        .apiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
        .kind(KubernetesConstants.DOMAIN)
        .name(DOMAIN_NAME)
        .uid(KUBERNETES_UID)
        .controller(true);

    assertThat(getCreatedPod().getMetadata().getOwnerReferences(), contains(expectedReference));
  }

  interface PodMutator {
    void mutate(V1Pod pod);
  }

  protected static class PassthroughPodAwaiterStepFactory implements PodAwaiterStepFactory {
    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return next;
    }

    @Override
    public Step waitForReady(String podName, Step next) {
      return next;
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return next;
    }
  }

  public static class DelayedPodAwaiterStepFactory implements PodAwaiterStepFactory {
    private final int delaySeconds;

    public DelayedPodAwaiterStepFactory(int delaySeconds) {
      this.delaySeconds = delaySeconds;
    }

    @Override
    public Step waitForReady(V1Pod pod, Step next) {
      return new DelayStep(next, delaySeconds);
    }

    @Override
    public Step waitForReady(String podName, Step next) {
      return new DelayStep(next, delaySeconds);
    }

    @Override
    public Step waitForDelete(V1Pod pod, Step next) {
      return new DelayStep(next, delaySeconds);
    }
  }

  private static class DelayStep extends Step {
    private final int delay;
    private final Step next;

    DelayStep(Step next, int delay) {
      this.delay = delay;
      this.next = next;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doDelay(next, packet, delay, TimeUnit.SECONDS);
    }
  }

}

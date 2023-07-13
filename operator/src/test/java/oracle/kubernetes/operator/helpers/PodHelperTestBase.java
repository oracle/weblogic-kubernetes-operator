// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ConfigMapKeySelector;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1ExecAction;
import io.kubernetes.client.openapi.models.V1HTTPGetAction;
import io.kubernetes.client.openapi.models.V1HostAlias;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1Lifecycle;
import io.kubernetes.client.openapi.models.V1LifecycleHandler;
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
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;
import io.kubernetes.client.util.Yaml;
import oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars;
import oracle.kubernetes.common.utils.SchemaConversionUtils;
import oracle.kubernetes.operator.DomainProcessorImpl;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.OverrideDistributionStrategy;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.unprocessable.UnrecoverableErrorBuilderImpl;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
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
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.DomainValidationTestBase;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static com.meterware.simplestub.Stub.createStub;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_TARGET_PATH;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_VOLUME_NAME_PREFIX;
import static oracle.kubernetes.common.CommonConstants.COMPATIBILITY_MODE;
import static oracle.kubernetes.common.CommonConstants.SCRIPTS_VOLUME;
import static oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars.AUXILIARY_IMAGE_COMMAND;
import static oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars.AUXILIARY_IMAGE_CONTAINER_IMAGE;
import static oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars.AUXILIARY_IMAGE_CONTAINER_NAME;
import static oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars.AUXILIARY_IMAGE_PATH;
import static oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars.AUXILIARY_IMAGE_PATHS;
import static oracle.kubernetes.common.logging.MessageKeys.KUBERNETES_EVENT_ERROR;
import static oracle.kubernetes.common.utils.LogMatcher.containsFine;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.DomainStatusMatcher.hasStatus;
import static oracle.kubernetes.operator.EventConstants.DOMAIN_FAILED_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEventWithNamespace;
import static oracle.kubernetes.operator.EventTestUtils.getEventsWithReason;
import static oracle.kubernetes.operator.EventTestUtils.getLocalizedString;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.DOMAINZIP_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.NUM_CONFIG_MAPS;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.SECRETS_MD_5;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_SIDECAR_PORT;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_IMAGE;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_DEBUG_CONFIG_MAP_SUFFIX;
import static oracle.kubernetes.operator.KubernetesConstants.EXPORTER_CONTAINER_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.KubernetesConstants.POD;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.LabelConstants.MII_UPDATED_RESTART_REQUIRED_LABEL;
import static oracle.kubernetes.operator.LabelConstants.OPERATOR_VERSION;
import static oracle.kubernetes.operator.ProcessingConstants.ENVVARS;
import static oracle.kubernetes.operator.ProcessingConstants.MAKE_RIGHT_DOMAIN_OPERATION;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_RESTART_REQUIRED;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_SUCCESS;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_SCAN;
import static oracle.kubernetes.operator.helpers.AnnotationHelper.SHA256_ANNOTATION;
import static oracle.kubernetes.operator.helpers.DomainIntrospectorJobTest.TEST_VOLUME_NAME;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_FAILED;
import static oracle.kubernetes.operator.helpers.ManagedPodHelperTest.JavaOptMatcher.hasJavaOption;
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
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONTAINER_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.INTROSPECTOR_VOLUME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH;
import static oracle.kubernetes.operator.helpers.StepContextConstants.RUNTIME_ENCRYPTION_SECRET_VOLUME;
import static oracle.kubernetes.operator.tuning.TuningParameters.KUBERNETES_PLATFORM_NAME;
import static oracle.kubernetes.operator.tuning.TuningParameters.LIVENESS_FAILURE_COUNT_THRESHOLD;
import static oracle.kubernetes.operator.tuning.TuningParameters.LIVENESS_INITIAL_DELAY_SECONDS;
import static oracle.kubernetes.operator.tuning.TuningParameters.LIVENESS_PERIOD_SECONDS;
import static oracle.kubernetes.operator.tuning.TuningParameters.LIVENESS_SUCCESS_COUNT_THRESHOLD;
import static oracle.kubernetes.operator.tuning.TuningParameters.LIVENESS_TIMEOUT_SECONDS;
import static oracle.kubernetes.operator.tuning.TuningParameters.READINESS_FAILURE_COUNT_THRESHOLD;
import static oracle.kubernetes.operator.tuning.TuningParameters.READINESS_INITIAL_DELAY_SECONDS;
import static oracle.kubernetes.operator.tuning.TuningParameters.READINESS_PERIOD_SECONDS;
import static oracle.kubernetes.operator.tuning.TuningParameters.READINESS_SUCCESS_COUNT_THRESHOLD;
import static oracle.kubernetes.operator.tuning.TuningParameters.READINESS_TIMEOUT_SECONDS;
import static oracle.kubernetes.weblogic.domain.model.AuxiliaryImage.AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME;
import static oracle.kubernetes.weblogic.domain.model.DomainFailureReason.KUBERNETES;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.hamcrest.junit.MatcherAssert.assertThat;

@SuppressWarnings({"SameParameterValue", "ConstantConditions", "OctalInteger", "unchecked"})
public abstract class PodHelperTestBase extends DomainValidationTestBase {

  public static final String EXPORTER_IMAGE = "monexp:latest";
  public static final String CUSTOM_WDT_INSTALL_SOURCE_HOME = "/myaux/weblogic-deploy";
  public static final String CUSTOM_MODEL_SOURCE_HOME = "/myaux/models";
  public static final String CUSTOM_MOUNT_PATH = "/myaux";

  static final String NS = "namespace";
  static final String ADMIN_SERVER = "ADMIN_SERVER";
  static final Integer ADMIN_PORT = 7001;
  static final Integer SSL_PORT = 7002;
  protected static final String DOMAIN_NAME = "domain1";
  protected static final String UID = "uid1";
  protected static final String KUBERNETES_UID = "12345";
  // Pod tuning
  private static final int LIVENESS_FAILURE_THRESHOLD = 1;
  private static final int LIVENESS_SUCCESS_THRESHOLD = 1;
  private static final int LIVENESS_TIMEOUT = 5;
  private static final int LIVENESS_PERIOD = 6;
  private static final int LIVENESS_INITIAL_DELAY = 4;
  private static final int READINESS_FAILURE_THRESHOLD = 1;
  private static final int READINESS_SUCCESS_THRESHOLD = 1;
  private static final int READINESS_PERIOD = 3;
  private static final int READINESS_TIMEOUT = 2;
  private static final int READINESS_INITIAL_DELAY = 1;
  private static final boolean INCLUDE_SERVER_OUT_IN_POD_LOG = true;

  private static final String CREDENTIALS_SECRET_NAME = "webLogicCredentialsSecretName";
  private static final String STORAGE_VOLUME_NAME = "weblogic-domain-storage-volume";
  private static final String LATEST_IMAGE = "image:latest";
  private static final String VERSIONED_IMAGE = "image:1.2.3";
  private static final int CONFIGURED_DELAY = 21;
  private static final int CONFIGURED_TIMEOUT = 27;
  private static final int CONFIGURED_PERIOD = 35;
  public static final int CONFIGURED_FAILURE_THRESHOLD = 1;
  public static final int CONFIGURED_SUCCESS_THRESHOLD = 2;
  private static final Integer DEFAULT_SUCCESS_THRESHOLD = null;
  private static final String LOG_HOME = "/shared/logs";
  private static final String NODEMGR_HOME = "/u01/nodemanager";
  private static final String CONFIGMAP_VOLUME_NAME = "weblogic-scripts-cm-volume";
  private static final int READ_AND_EXECUTE_MODE = 0555;
  private static final String TEST_PRODUCT_VERSION = "unit-test";
  private static final String NOOP_EXPORTER_CONFIG = "queries:\n";
  public static final String LONG_CHANNEL_NAME = "Very_Long_Channel_Name";
  public static final String TRUNCATED_PORT_NAME_PREFIX = "very-long-ch";
  public static final String CUSTOM_COMMAND_SCRIPT = "customScript.sh";
  static final String DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH = "/auxiliary";

  final TerminalStep terminalStep = new TerminalStep();
  private final DomainResource domain = createDomain();
  protected final DomainPresenceInfo domainPresenceInfo = createDomainPresenceInfo(domain);
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
  private final Map<String, Map<String, KubernetesEventObjects>> domainEventObjects = new ConcurrentHashMap<>();
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;
  private final SchemaConversionUtils conversionUtils = new SchemaConversionUtils();

  PodHelperTestBase(String serverName, int listenPort) {
    this.serverName = serverName;
    this.listenPort = listenPort;
  }

  TestUtils.ConsoleHandlerMemento getConsoleHandlerMemento() {
    return consoleHandlerMemento;
  }

  DomainResource getDomain() {
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

  static List<V1EnvVar> getAuxiliaryImageEnvVariables(String image, String sourceWDTInstallHome,
                                                      String sourceModelHome, String name) {
    List<V1EnvVar> envVars = new ArrayList<>();
    envVars.add(createEnvVar(AuxiliaryImageEnvVars.AUXILIARY_IMAGE_TARGET_PATH, AUXILIARY_IMAGE_TARGET_PATH));
    envVars.add(createEnvVar(AuxiliaryImageEnvVars.AUXILIARY_IMAGE_SOURCE_WDT_INSTALL_HOME, sourceWDTInstallHome));
    envVars.add(createEnvVar(AuxiliaryImageEnvVars.AUXILIARY_IMAGE_SOURCE_MODEL_HOME, sourceModelHome));
    envVars.add(createEnvVar(AUXILIARY_IMAGE_CONTAINER_IMAGE, image));
    envVars.add(createEnvVar(AUXILIARY_IMAGE_CONTAINER_NAME, name));
    return envVars;
  }

  static List<V1EnvVar> getLegacyAuxiliaryImageEnvVariables(String image, String name, String command) {
    List<V1EnvVar> envVars = new ArrayList<>();
    envVars.add(createEnvVar(AUXILIARY_IMAGE_PATH, DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH));
    envVars.add(createEnvVar(AuxiliaryImageEnvVars.AUXILIARY_IMAGE_TARGET_PATH, AUXILIARY_IMAGE_TARGET_PATH));
    envVars.add(createEnvVar(AuxiliaryImageEnvVars.AUXILIARY_IMAGE_COMMAND, command));
    envVars.add(createEnvVar(AUXILIARY_IMAGE_CONTAINER_IMAGE, image));
    envVars.add(createEnvVar(AUXILIARY_IMAGE_CONTAINER_NAME, COMPATIBILITY_MODE + name));
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
          consoleHandlerMemento = TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE)
            .ignoringLoggedExceptions(ApiException.class));
    mementos.add(StaticStubSupport.install(DomainProcessorImpl.class, "domainEventK8SObjects", domainEventObjects));

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

    definePodTuning();
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

  void definePodTuning() {
    defineTuningParameter(READINESS_INITIAL_DELAY_SECONDS, READINESS_INITIAL_DELAY);
    defineTuningParameter(READINESS_TIMEOUT_SECONDS, READINESS_TIMEOUT);
    defineTuningParameter(READINESS_PERIOD_SECONDS, READINESS_PERIOD);
    defineTuningParameter(READINESS_SUCCESS_COUNT_THRESHOLD, READINESS_SUCCESS_THRESHOLD);
    defineTuningParameter(READINESS_FAILURE_COUNT_THRESHOLD, READINESS_FAILURE_THRESHOLD);
    defineTuningParameter(LIVENESS_INITIAL_DELAY_SECONDS, LIVENESS_INITIAL_DELAY);
    defineTuningParameter(LIVENESS_TIMEOUT_SECONDS, LIVENESS_TIMEOUT);
    defineTuningParameter(LIVENESS_PERIOD_SECONDS, LIVENESS_PERIOD);
    defineTuningParameter(LIVENESS_SUCCESS_COUNT_THRESHOLD, LIVENESS_SUCCESS_THRESHOLD);
    defineTuningParameter(LIVENESS_FAILURE_COUNT_THRESHOLD, LIVENESS_FAILURE_THRESHOLD);
  }

  private void defineTuningParameter(String name, int value) {
    TuningParametersStub.setParameter(name, Integer.toString(value));
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  private DomainPresenceInfo createDomainPresenceInfo(DomainResource domain) {
    return new DomainPresenceInfo(domain);
  }

  WlsServerConfig getServerTopology() {
    return domainTopology.getServerConfig(getServerName());
  }

  @Test
  void whenPodCreatedWithoutExportConfiguration_hasPrometheusAnnotations() {
    assertThat(
        getCreatedPod().getMetadata().getAnnotations(),
        allOf(
            hasEntry("prometheus.io/port", Integer.toString(getServerTopology().getListenPort())),
            hasEntry("prometheus.io/path", "/wls-exporter/metrics"),
            hasEntry("prometheus.io/scrape", "true")));
  }

  @Test
  void whenPodCreatedWithAdminNap_prometheusAnnotationsSpecifyPlainTextPort() {
    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint("test", "admin", 8001, 8001));
    getServerTopology().setListenPort(7001);
    getServerTopology().setSslListenPort(7002);
    assertThat(
        getCreatedPod().getMetadata().getAnnotations(),
        allOf(
            hasEntry("prometheus.io/port", "7001"),
            hasEntry("prometheus.io/path", "/wls-exporter/metrics"),
            hasEntry("prometheus.io/scrape", "true")));
  }

  @Test
  void whenPodCreatedWithAdminNapNameExceedingMaxPortNameLength_podContainerCreatedWithTruncatedPortName() {
    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint(LONG_CHANNEL_NAME, "admin", 8001, 8001));
    assertThat(
            getContainerPorts(),
            hasItem(createContainerPort(TRUNCATED_PORT_NAME_PREFIX + "-01")));
  }

  private List<V1ContainerPort> getContainerPorts() {
    return getCreatedPod().getSpec().getContainers().stream()
            .filter(c -> c.getName().equals(WLS_CONTAINER_NAME)).findFirst().map(V1Container::getPorts).orElse(null);
  }

  private V1ContainerPort createContainerPort(String portName) {
    return new V1ContainerPort().name(portName).containerPort(8001).protocol("TCP");
  }

  @Test
  void whenPodCreatedWithMultipleNapsWithNamesExceedingMaxPortNameLength_podContainerCreatedWithTruncatedPortNames() {
    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint(LONG_CHANNEL_NAME + "1", "admin", 8001, 8001));
    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint(LONG_CHANNEL_NAME + "11", "admin", 8001, 8001));
    assertThat(
            getContainerPorts(),
            hasItems(createContainerPort(TRUNCATED_PORT_NAME_PREFIX + "-01"),
                     createContainerPort(TRUNCATED_PORT_NAME_PREFIX + "-02")));
  }

  @Test
  void whenOneNapNameExceedsMaxPortNameLengthAndOtherNapWithShortName_podContainerCreatedWithCorrectPortNames() {
    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint("shortname", "admin", 8001, 8001));
    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint(LONG_CHANNEL_NAME + "1", "admin", 8001, 8001));

    assertThat(
        getContainerPorts(),
        hasItems(createContainerPort(TRUNCATED_PORT_NAME_PREFIX + "-01"),
            createContainerPort("shortname")));
  }

  @Test
  void whenPodCreatedWithMultipleNapsSomeWithNamesExceedingMaxPortNameLength_podContainerCreatedWithMixedPortNames() {
    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint(LONG_CHANNEL_NAME, "admin", 8001, 8001));
    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint("My_Channel_Name", "admin", 8001, 8001));
    assertThat(
            getContainerPorts(),
            hasItems(createContainerPort(TRUNCATED_PORT_NAME_PREFIX + "-01"),
                     createContainerPort("my-channel-name")));
  }

  protected DomainConfigurator defineExporterConfiguration() {
    return configureDomain()
          .withMonitoringExporterConfiguration(NOOP_EXPORTER_CONFIG)
          .withMonitoringExporterImage(EXPORTER_IMAGE);
  }

  protected void defineExporterConfigurationWithResourceRequirements() {
    defineExporterConfiguration()
        .withMonitoringExporterResources(
            new V1ResourceRequirements().limits(Map.of("cpu", new Quantity("0.25"))));
  }

  protected void defineFluentdConfiguration(boolean watchIntrospectorLog) {
    configureDomain()
            .withFluentdConfiguration(watchIntrospectorLog, "fluentd-cred",
                    null, null, null);
  }

  @Test
  void whenDomainHasMonitoringExporterConfiguration_hasPrometheusAnnotations() {
    defineExporterConfiguration();

    assertThat(
        getCreatedPod().getMetadata().getAnnotations(),
        allOf(
            hasEntry("prometheus.io/port", Integer.toString(DEFAULT_EXPORTER_SIDECAR_PORT)),
            hasEntry("prometheus.io/path", "/metrics"),
            hasEntry("prometheus.io/scrape", "true")));
  }

  protected V1Container getExporterContainer() {
    return getCreatedPodSpecContainers().stream().filter(this::isMonitoringExporterContainer).findFirst().orElse(null);
  }

  protected V1Container getFluentdContainer() {
    return getCreatedPodSpecContainers().stream().filter(this::isFluentdContainer).findFirst().orElse(null);
  }

  private boolean isMonitoringExporterContainer(V1Container container) {
    return container.getName().contains(EXPORTER_CONTAINER_NAME);
  }

  private boolean isFluentdContainer(V1Container container) {
    return container.getName().contains(FLUENTD_CONTAINER_NAME);
  }

  @Test
  void monitoringExporterContainer_hasExporterName() {
    defineExporterConfiguration();

    assertThat(getExporterContainer().getName(), equalTo(EXPORTER_CONTAINER_NAME));
  }

  @Test
  void monitoringExporterContainerCommand_isNotDefined() {
    defineExporterConfiguration();

    assertThat(getExporterContainer().getCommand(), nullValue());
  }

  @Test
  void monitoringExporterContainer_hasDefaultImageName() {
    defineExporterConfiguration();

    assertThat(getExporterContainer().getImage(), equalTo(EXPORTER_IMAGE));
  }

  @Test
  void monitoringExporterContainer_hasInferredPullPolicy() {
    defineExporterConfiguration();

    assertThat(getExporterContainer().getImagePullPolicy(), equalTo("Always"));
  }

  @Test
  void monitoringExporterContainer_SpecifiedCpuLimit() {
    defineExporterConfigurationWithResourceRequirements();

    assertThat(getExporterContainer().getResources().getLimits(), hasEntry("cpu", new Quantity("0.25")));
  }

  @Test
  void whenExporterContainerCreated_hasMetricsPortsItem() {
    defineExporterConfiguration();

    V1ContainerPort metricsPort = getExporterContainerPort("metrics");
    assertThat(metricsPort, notNullValue());
    assertThat(metricsPort.getProtocol(), equalTo("TCP"));
    assertThat(metricsPort.getContainerPort(), equalTo(DEFAULT_EXPORTER_SIDECAR_PORT));
  }

  @Test
  void whenExporterContainerCreatedWithPort_hasMetricsPortsItem() {
    defineExporterConfiguration().withMonitoringExporterPort(300);

    V1ContainerPort metricsPort = getExporterContainerPort("metrics");
    assertThat(metricsPort, notNullValue());
    assertThat(metricsPort.getProtocol(), equalTo("TCP"));
    assertThat(metricsPort.getContainerPort(), equalTo(300));
  }

  @Test
  void afterUpgradeIstioMonitoringExporterPod_dontReplacePod() {
    useProductionHash();
    defineExporterConfiguration();

    initializeExistingPod(loadPodModel(getReferenceIstioMonitoringExporterTcpProtocol()));

    // Surprisingly, verifyPodPatched() is the correct assertion -- because the logic to adjust the recipe and
    // generate hashes for pre-existing pods works correctly, the existing pod will not be replaced; however,
    // it will be patched to update the weblogic.operatorVersion label and the annotation with the hash.
    verifyPodPatched();
  }

  @Test
  void afterUpgradeIstioMonitoringExporterPodWithResourceRequirements_replacePod() {
    useProductionHash();
    defineExporterConfigurationWithResourceRequirements();

    initializeExistingPod(loadPodModel(getReferenceIstioMonitoringExporterTcpProtocol()));

    verifyPodReplaced();
  }

  @Test
  void afterUpgradeLogHomeLayoutStillFlat_dontReplacePod() {
    useProductionHash();
    defineExporterConfiguration();

    configurator.withLogHomeLayout(LogHomeLayoutType.FLAT);

    initializeExistingPod(loadPodModel(getReferenceIstioMonitoringExporterTcpProtocol()));

    // Surprisingly, verifyPodPatched() is the correct assertion -- because the logic to adjust the recipe and
    // generate hashes for pre-existing pods works correctly, the existing pod will not be replaced; however,
    // it will be patched to update the weblogic.operatorVersion label and the annotation with the hash.
    verifyPodPatched();
  }

  @Test
  void afterUpgradeLogHomeLayoutByServers_replacePod() {
    useProductionHash();
    defineExporterConfigurationWithResourceRequirements();

    configurator.withLogHomeLayout(LogHomeLayoutType.BY_SERVERS);

    initializeExistingPod(loadPodModel(getReferenceIstioMonitoringExporterTcpProtocol()));

    verifyPodReplaced();
  }

  @Test
  void whenExporterContainerCreatedAndIstioEnabled_hasMetricsPortsItem() {
    defineExporterConfiguration();

    V1ContainerPort metricsPort = getExporterContainerPort("metrics");
    assertThat(metricsPort, notNullValue());
    assertThat(metricsPort.getProtocol(), equalTo("TCP"));
    assertThat(metricsPort.getContainerPort(), equalTo(DEFAULT_EXPORTER_SIDECAR_PORT));
  }

  private V1ContainerPort getExporterContainerPort(@Nonnull String name) {
    return Optional.ofNullable(getExporterContainer().getPorts()).orElse(Collections.emptyList()).stream()
          .filter(p -> name.equals(p.getName())).findFirst().orElse(null);
  }

  @Test
  void whenExporterContainerCreated_specifyOperatorDomain() {
    defineExporterConfiguration();

    assertThat(getExporterContainer(), hasJavaOption("-DDOMAIN=" + getDomain().getDomainUid()));
  }

  @Test
  void whenDomainHasFluentdInServerPod_createPodShouldHaveFluentdContainer() {
    defineFluentdConfiguration(true);
    
    assertThat(getFluentdContainer(), notNullValue());
  }

  @Test
  void whenDomainHasFluentdInServerPod_podRequiredEnvSet() {
    defineFluentdConfiguration(true);

    V1Container fluentContainer = getFluentdContainer();
    assertThat(fluentContainer, notNullValue());

    assertThat(hasContainerEnvName(fluentContainer, "FLUENTD_CONF"), equalTo(true));
    assertThat(hasContainerEnvName(fluentContainer, "FLUENT_ELASTICSEARCH_SED_DISABLE"), equalTo(true));
    assertThat(hasContainerEnvName(fluentContainer, "DOMAIN_UID"), equalTo(true));
    assertThat(hasContainerEnvName(fluentContainer, "SERVER_NAME"), equalTo(true));
    assertThat(hasContainerEnvName(fluentContainer, "LOG_PATH"), equalTo(true));
    assertThat(hasContainerEnvName(fluentContainer, "ELASTICSEARCH_HOST"), equalTo(true));
    assertThat(hasContainerEnvName(fluentContainer, "ELASTICSEARCH_PORT"), equalTo(true));
    assertThat(hasContainerEnvName(fluentContainer, "ELASTICSEARCH_USER"), equalTo(true));
    assertThat(hasContainerEnvName(fluentContainer, "ELASTICSEARCH_PASSWORD"), equalTo(true));

    assertThat(hasContainerEnvNameReferenceSecretKey(fluentContainer,
            "ELASTICSEARCH_HOST","fluentd-cred", "elasticsearchhost"),
            equalTo(true));
    assertThat(hasContainerEnvNameReferenceSecretKey(fluentContainer,
                    "ELASTICSEARCH_PORT","fluentd-cred", "elasticsearchport"),
            equalTo(true));
    assertThat(hasContainerEnvNameReferenceSecretKey(fluentContainer,
                    "ELASTICSEARCH_USER","fluentd-cred", "elasticsearchuser"),
            equalTo(true));
    assertThat(hasContainerEnvNameReferenceSecretKey(fluentContainer,
                    "ELASTICSEARCH_PASSWORD","fluentd-cred", "elasticsearchpassword"),
            equalTo(true));
  }

  private boolean hasContainerEnvName(V1Container container, String name) {

    return Optional.ofNullable(container)
            .map(V1Container::getEnv).orElse(Collections.emptyList()).stream()
            .anyMatch(e -> e.getName().equals(name));

  }

  private boolean hasContainerEnvNameReferenceSecretKey(V1Container container, String envName, String secretName,
                                                        String secretKey) {

    V1SecretKeySelector ref = Optional.ofNullable(container)
            .map(V1Container::getEnv).orElse(Collections.emptyList()).stream()
            .filter(c -> c.getName().equals(envName))
            .findFirst()
            .map(V1EnvVar::getValueFrom)
            .map(V1EnvVarSource::getSecretKeyRef)
            .orElse(null);

    return ref != null && ref.getName().equals(secretName) && ref.getKey().equals(secretKey);
  }

  V1Affinity getCreatePodAffinity() {
    return Optional.ofNullable(getCreatedPod().getSpec()).map(V1PodSpec::getAffinity).orElse(new V1Affinity());
  }

  abstract void setServerPort(int port);

  private DomainResource createDomain() {
    return new DomainResource()
        .withApiVersion(KubernetesConstants.DOMAIN_VERSION)
        .withKind(DOMAIN)
        .withMetadata(new V1ObjectMeta().namespace(NS).name(DOMAIN_NAME).uid(KUBERNETES_UID))
        .withSpec(createDomainSpec());
  }

  private DomainSpec createDomainSpec() {
    return new DomainSpec()
        .withDomainUid(UID)
        .withWebLogicCredentialsSecret(new V1LocalObjectReference().name(CREDENTIALS_SECRET_NAME))
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
  void whenNoPod_createIt() {
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(testSupport.getResources(KubernetesTestSupport.POD), notNullValue());
    assertThat(logRecords, containsInfo(getCreatedMessageKey()));
  }

  @Test
  void whenPodCreated_specHasOneContainer() {
    assertThat(getCreatedPod().getSpec().getContainers(), hasSize(1));
  }

  @Test
  void whenPodCreated_hasSha256HashAnnotationForRecipe() {
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

  // Returns the YAML for a 3.3 Mii pod with aux image.
  abstract String getReferenceMiiAuxImagePodYaml_3_3();

  // Returns the YAML for a 4.0 Mii pod with aux image.
  abstract String getReferenceMiiAuxImagePodYaml_4_0();

  // Returns the YAML for a 3.4 Mii pod with converted aux image.
  abstract String getReferenceMiiConvertedAuxImagePodYaml_3_4();

  // Returns the YAML for a 3.4.1 Mii pod with converted aux image.
  abstract String getReferenceMiiConvertedAuxImagePodYaml_3_4_1();

  abstract String getReferenceIstioMonitoringExporterTcpProtocol();

  @Test
  void afterUpgradingPlainPortPodFrom30_patchIt() {
    useProductionHash();
    initializeExistingPod(loadPodModel(getReferencePlainPortPodYaml_3_0()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  void afterUpgradingMiiDomainWith3_3_AuxImages_patchIt() {
    configureDomain().withInitContainer(createInitContainer())
        .withRequestRequirement("memory", "768Mi")
        .withRequestRequirement("cpu", "250m")
        .withAdditionalVolumeMount("compat-ai-vol-auxiliaryimagevolume1", "/auxiliary")
        .withAdditionalVolume(new V1Volume().name("compat-ai-vol-auxiliaryimagevolume1")
            .emptyDir(new V1EmptyDirVolumeSource()));

    useProductionHash();
    initializeExistingPod(loadPodModel(getReferenceMiiAuxImagePodYaml_3_3()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  private V1Container createInitContainer() {
    return new V1Container().name("compat-operator-aux-container1").image("model-in-image:WLS-AI-v1")
        .command(Collections.singletonList(AUXILIARY_IMAGE_INIT_CONTAINER_WRAPPER_SCRIPT))
        .imagePullPolicy("IfNotPresent")
        .addEnvItem(envItem(AUXILIARY_IMAGE_PATH, DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH))
        .addEnvItem(envItem(AuxiliaryImageEnvVars.AUXILIARY_IMAGE_TARGET_PATH, AUXILIARY_IMAGE_TARGET_PATH))
        .addEnvItem(envItem(AUXILIARY_IMAGE_COMMAND, AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND))
        .addEnvItem(envItem(AUXILIARY_IMAGE_CONTAINER_IMAGE, "model-in-image:WLS-AI-v1"))
        .addEnvItem(envItem(AUXILIARY_IMAGE_CONTAINER_NAME, "compat-operator-aux-container1"))
        .addVolumeMountsItem(new V1VolumeMount().mountPath(AUXILIARY_IMAGE_TARGET_PATH)
            .name("aux-image-volume-auxiliaryimagevolume1"))
        .addVolumeMountsItem(new V1VolumeMount().mountPath("/weblogic-operator/scripts")
            .name("weblogic-scripts-cm-volume"));
  }

  @Test
  void afterUpgradingMiiDomainWith3_4_ConvertedAuxImages_patchIt() {
    configureDomain().withInitContainer(createInitContainer())
        .withRequestRequirement("memory", "768Mi")
        .withRequestRequirement("cpu", "250m")
        .withAdditionalVolumeMount("compat-ai-vol-auxiliaryimagevolume1", "/auxiliary")
        .withAdditionalVolume(new V1Volume().name("compat-ai-vol-auxiliaryimagevolume1")
            .emptyDir(new V1EmptyDirVolumeSource()));

    useProductionHash();
    initializeExistingPod(loadPodModel(getReferenceMiiConvertedAuxImagePodYaml_3_4()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  void afterUpgradingMiiDomainWith3_4_1_ConvertedAuxImages_patchIt() {
    configureDomain().withInitContainer(createInitContainer())
        .withRequestRequirement("memory", "768Mi")
        .withRequestRequirement("cpu", "250m")
        .withAdditionalVolumeMount("compat-ai-vol-auxiliaryimagevolume1", "/auxiliary")
        .withAdditionalVolume(new V1Volume().name("compat-ai-vol-auxiliaryimagevolume1")
            .emptyDir(new V1EmptyDirVolumeSource()));

    useProductionHash();
    initializeExistingPod(loadPodModel(getReferenceMiiConvertedAuxImagePodYaml_3_4_1()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  void afterUpgradingMiiDomainWith4_0_AuxImages_patchIt() {
    configureDomain().withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1"));

    useProductionHash();
    initializeExistingPod(loadPodModel(getReferenceMiiAuxImagePodYaml_4_0()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  void afterUpgradingPlainPortPodFrom31_patchIt() {
    useProductionHash();
    initializeExistingPod(loadPodModel(getReferencePlainPortPodYaml_3_1()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  void afterUpgradingSslPortPodFrom30_patchIt() {
    useProductionHash();
    getServerTopology().setSslListenPort(7002);
    initializeExistingPod(loadPodModel(getReferenceSslPortPodYaml_3_0()));

    verifyPodPatched();

    V1Pod patchedPod = domainPresenceInfo.getServerPod(getServerName());
    assertThat(patchedPod.getMetadata().getLabels().get(OPERATOR_VERSION), equalTo(TEST_PRODUCT_VERSION));
    assertThat(AnnotationHelper.getHash(patchedPod), equalTo(AnnotationHelper.getHash(createPodModel())));
  }

  @Test
  void afterUpgradingSslPortPodFrom31_patchIt() {
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
  void afterUpgradingMiiPodFrom31_patchIt() {
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
    return Yaml.loadAs(podYaml, V1Pod.class);
  }

  @Test
  void whenPodCreatedWithLatestImage_useAlwaysPullPolicy() {
    defineDomainImage(LATEST_IMAGE);

    V1Container v1Container = getCreatedPodSpecContainer();

    assertThat(v1Container.getName(), equalTo(WLS_CONTAINER_NAME));
    assertThat(v1Container.getImage(), equalTo(LATEST_IMAGE));
    assertThat(v1Container.getImagePullPolicy(), equalTo("Always"));
  }

  @Test
  void whenPodCreatedWithVersionedImage_useIfNotPresentPolicy() {
    defineDomainImage(VERSIONED_IMAGE);

    V1Container v1Container = getCreatedPodSpecContainer();

    assertThat(v1Container.getImage(), equalTo(VERSIONED_IMAGE));
    assertThat(v1Container.getImagePullPolicy(), equalTo("IfNotPresent"));
  }

  @Test
  void whenPodCreatedWithoutPullSecret_doNotAddToPod() {
    assertThat(getCreatedPod().getSpec().getImagePullSecrets(), empty());
  }

  @Test
  void whenPodCreatedWithPullSecret_addToPod() {
    V1LocalObjectReference imagePullSecret = new V1LocalObjectReference().name("secret");
    configureDomain().withDefaultImagePullSecrets(imagePullSecret);

    assertThat(getCreatedPod().getSpec().getImagePullSecrets(), hasItem(imagePullSecret));
  }

  @Test
  void whenPodCreatedWithHostAliases_addToPod() {
    V1HostAlias hostAlias = new V1HostAlias().addHostnamesItem("www.test.com").ip("1.1.1.1");
    configureDomain().withHostAliases(hostAlias);

    assertThat(getCreatedPod().getSpec().getHostAliases(), hasItem(hostAlias));
  }

  @Test
  void whenPodCreated_withNoPvc_image_containerHasExpectedVolumeMounts() {
    configurator.withDomainHomeSourceType(DomainSourceType.IMAGE);
    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        containsInAnyOrder(
            writableVolumeMount(
                  INTROSPECTOR_VOLUME, "/weblogic-operator/introspector"),
            readOnlyVolumeMount("weblogic-domain-debug-cm-volume", "/weblogic-operator/debug"),
            readOnlyVolumeMount("weblogic-scripts-cm-volume", "/weblogic-operator/scripts")));
  }

  @Test
  void whenPodCreated_withNoPvc_fromModel_containerHasExpectedVolumeMounts() {
    reportInspectionWasRun();
    configurator.withDomainHomeSourceType(DomainSourceType.FROM_MODEL)
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
  void whenDomainHasAuxiliaryImageAndNoVolumeMount_createPodsWithInitContainerEmptyDirVolumeAndVolumeMounts() {
    getConfigurator()
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "IfNotPresent")));
    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasAuxiliaryImage_createPodsWithInitContainerEmptyDirVolumeAndVolumeMounts() {
    getConfigurator()
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1", "IfNotPresent")));
    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasAuxiliaryImageAndVolumeWithCustomMountPath_createPodsWithVolumeMountHavingCustomMountPath() {
    getConfigurator()
            .withAuxiliaryImageVolumeMountPath(CUSTOM_MOUNT_PATH)
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1"));

    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).mountPath(CUSTOM_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasAuxiliaryImageVolumeWithMedium_createPodsWithVolumeHavingSpecifiedMedium() {
    getConfigurator()
            .withAuxiliaryImageVolumeMedium("Memory")
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1"));

    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource().medium("Memory"))));
  }

  @Test
  void whenDomainHasAuxiliaryImageVolumeWithSizeLimit_createPodsWithVolumeHavingSpecifiedSizeLimit() {
    getConfigurator()
            .withAuxiliaryImageVolumeSizeLimit("100G")
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image:v1"));

    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME).emptyDir(
                    new V1EmptyDirVolumeSource().sizeLimit(Quantity.fromString("100G")))));
  }

  @Test
  void whenDomainHasAuxiliaryImagesWithImagePullPolicy_createPodsWithAIInitContainerHavingImagePullPolicy() {
    getConfigurator()
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
                    .imagePullPolicy("Always")));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1", "Always")));
  }

  @Test
  void whenDomainHasAuxiliaryImagesWithResourceRequirements_createPodsWithAIInitContainerHavingResourceRequirements() {
    getConfigurator()
        .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
            .imagePullPolicy("Always")))
        .withLimitRequirement("cpu", "250m")
        .withRequestRequirement("memory", "1Gi");

    assertThat(getCreatedPodSpecInitContainers(),
        allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
            "wdt-image:v1", "Always", new V1ResourceRequirements()
                .limits(Collections.singletonMap("cpu", new Quantity("250m")))
                .requests(Collections.singletonMap("memory", new Quantity("1Gi"))))));
  }

  @Test
  void whenDomainHasAIWithCustomSourceWdtInstallHome_createPodsWithAIInitContainerHavingCustomSourceWdtInstallHome() {
    getConfigurator()
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
                    .sourceWDTInstallHome(CUSTOM_WDT_INSTALL_SOURCE_HOME)));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image:v1", "IfNotPresent", CUSTOM_WDT_INSTALL_SOURCE_HOME)));
  }

  @Test
  void whenDomainHasAIWithCustomSourceModelHome_createPodsWithAIInitContainerHavingCustomSourceModelHome() {
    getConfigurator()
            .withAuxiliaryImages(Collections.singletonList(getAuxiliaryImage("wdt-image:v1")
                    .sourceWDTInstallHome(CUSTOM_WDT_INSTALL_SOURCE_HOME).sourceModelHome(CUSTOM_MODEL_SOURCE_HOME)));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "IfNotPresent", CUSTOM_WDT_INSTALL_SOURCE_HOME,
                    CUSTOM_MODEL_SOURCE_HOME)));
  }

  @Test
  void whenDomainHasMultipleAuxiliaryImages_createPodsWithAuxiliaryImageInitContainersInCorrectOrder() {
    getConfigurator()
            .withAuxiliaryImages(getAuxiliaryImages("wdt-image1:v1", "wdt-image2:v1"));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image1:v1", "IfNotPresent"),
                Matchers.hasAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                    "wdt-image2:v1", "IfNotPresent")));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(), hasSize(5));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(AUXILIARY_IMAGE_INTERNAL_VOLUME_NAME)
                    .mountPath(DEFAULT_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasLegacyAuxiliaryImage_createPodsWithInitContainerEmptyDirVolumeAndVolumeMounts() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            Collections.singletonList(auxiliaryImage))));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1",
                    "IfNotPresent",
                    AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, serverName)));
    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName()).emptyDir(
                    new V1EmptyDirVolumeSource())));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(getLegacyAuxiliaryImageVolumeName())
                    .mountPath(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Nonnull
  protected String getAuxiliaryImageVolumeName() {
    return getAuxiliaryImageVolumeName(TEST_VOLUME_NAME);
  }

  @Nonnull
  protected static String getAuxiliaryImageVolumeName(String testVolumeName) {
    return AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + testVolumeName;
  }

  @Nonnull
  protected static String getLegacyAuxiliaryImageVolumeName() {
    return COMPATIBILITY_MODE + getAuxiliaryImageVolumeName(TEST_VOLUME_NAME);
  }

  @Nonnull
  protected String getLegacyAuxiliaryImageVolumeName(String testVolumeName) {
    return COMPATIBILITY_MODE + AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + testVolumeName;
  }

  @Test
  void whenDomainHasLegacyAuxiliaryImageAndVolumeWithCustomMountPath_createPodsWithVolumeMountHavingCustomMountPath() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME, CUSTOM_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            Collections.singletonList(auxiliaryImage))));

    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(COMPATIBILITY_MODE + getAuxiliaryImageVolumeName())
                    .mountPath(CUSTOM_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasLegacyAuxiliaryImageVolumeWithMedium_createPodsWithVolumeHavingSpecifiedMedium() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH, null, "Memory");
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image1:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            Collections.singletonList(auxiliaryImage))));

    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName()).emptyDir(
                    new V1EmptyDirVolumeSource().medium("Memory"))));
  }

  @Test
  void whenDomainHasLegacyAuxiliaryImageVolumeWithSizeLimit_createPodsWithVolumeHavingSpecifiedSizeLimit() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(TEST_VOLUME_NAME,
            DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH, "100G", null);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image1:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            Collections.singletonList(auxiliaryImage))));

    assertThat(getCreatedPod().getSpec().getVolumes(),
            hasItem(new V1Volume().name(getLegacyAuxiliaryImageVolumeName()).emptyDir(
                    new V1EmptyDirVolumeSource().sizeLimit(Quantity.fromString("100G")))));
  }

  @Test
  void whenDomainHasLegacyAuxiliaryImagesWithImagePullPolicy_createPodsWithAIInitContainerHavingImagePullPolicy() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "Always");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(Collections.singletonList(auxiliaryImageVolume),
                            Collections.singletonList(auxiliaryImage))));

    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "Always",
                    AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND,
                    serverName)));
  }

  @Test
  void whenDomainHasLegacyAuxiliaryImagesWithResources_createPodsWithAIInitContainerHavingResourceRequirements() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "Always");

    convertDomainWithLegacyAuxImages(
        createLegacyDomainMap(
            createDomainSpecMapWithResources(Collections.singletonList(auxiliaryImageVolume),
                Collections.singletonList(auxiliaryImage), createResources())));

    assertThat(getCreatedPodSpecInitContainers(),
        allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
            "wdt-image:v1", "Always",
            AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND,
            new V1ResourceRequirements().limits(Collections.singletonMap("cpu", new Quantity("250m")))
                .requests(Collections.singletonMap("memory", new Quantity("1Gi"))))));
  }

  static ImmutableMap<String, Object> createLegacyDomainMap(Map<String, Object> spec) {
    return ImmutableMap.of("apiVersion", "weblogic.oracle/v8",
            "kind", "Domain",
            "metadata", createMetadata(),
            "spec", spec);
  }

  static Map<String, Object> createDomainSpecMap(List<Object> auxiliaryImageVolumes, List<Object> auxiliaryImages) {
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("domainUID", "uid1");
    spec.put("webLogicCredentialsSecret", createWebLogicCredentialsSecret());
    spec.put("includeServerOutInPodLog", Boolean.TRUE);
    spec.put("image", "image:latest");
    spec.put("auxiliaryImageVolumes", auxiliaryImageVolumes);
    spec.put("serverPod", createServerPod(auxiliaryImages));
    return spec;
  }

  static Map<String, Object> createDomainSpecMapWithResources(List<Object> auxiliaryImageVolumes,
                                                              List<Object> auxiliaryImages,
                                                              Map<String, Object> resources) {
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("domainUID", "uid1");
    spec.put("webLogicCredentialsSecret", createWebLogicCredentialsSecret());
    spec.put("includeServerOutInPodLog", Boolean.TRUE);
    spec.put("image", "image:latest");
    spec.put("auxiliaryImageVolumes", auxiliaryImageVolumes);
    spec.put("serverPod", createServerPod(auxiliaryImages, resources));
    return spec;
  }

  static Map<String, Object> createSpecWithAdminServer(List<Object> auxiliaryImageVolumes, List<Object> auxiliaryImages,
                                                       Map<String,Object> adminSpec) {
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("domainUID", "uid1");
    spec.put("webLogicCredentialsSecret", createWebLogicCredentialsSecret());
    spec.put("includeServerOutInPodLog", Boolean.TRUE);
    spec.put("image", "image:latest");
    spec.put("auxiliaryImageVolumes", auxiliaryImageVolumes);
    spec.put("serverPod", createServerPod(auxiliaryImages));
    spec.put("adminServer", adminSpec);
    return spec;
  }

  static Map<String, Object> createSpecWithClusters(List<Object> auxiliaryImageVolumes, List<Object> auxiliaryImages,
                                                       List<Object> clustersSpec) {
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("domainUID", "uid1");
    spec.put("webLogicCredentialsSecret", createWebLogicCredentialsSecret());
    spec.put("includeServerOutInPodLog", Boolean.TRUE);
    spec.put("image", "image:latest");
    spec.put("auxiliaryImageVolumes", auxiliaryImageVolumes);
    spec.put("serverPod", createServerPod(auxiliaryImages));
    spec.put("clusters", clustersSpec);
    return spec;
  }

  static Map<String, Object> createSpecWithManagedServers(List<Object> auxiliaryImageVolumes, List<Object>
          auxiliaryImages, List<Object> managedServersSpec) {
    Map<String, Object> spec = new LinkedHashMap<>();
    spec.put("domainUID", "uid1");
    spec.put("webLogicCredentialsSecret", createWebLogicCredentialsSecret());
    spec.put("includeServerOutInPodLog", Boolean.TRUE);
    spec.put("image", "image:latest");
    spec.put("auxiliaryImageVolumes", auxiliaryImageVolumes);
    spec.put("serverPod", createServerPod(auxiliaryImages));
    spec.put("managedServers", managedServersSpec);
    return spec;
  }

  static List<Object> createClusterSpecWithAuxImages(List<Object> auxiliaryImages, String clusterName) {
    List<Object> clusterSpec = new ArrayList<>();
    clusterSpec.add(ImmutableMap.of("clusterName", clusterName,
            "serverPod", createServerPod(auxiliaryImages)));
    return clusterSpec;
  }

  static List<Object> createManagedServersSpecWithAuxImages(List<Object> auxiliaryImages, String msName) {
    List<Object> managedServersSpec = new ArrayList<>();
    managedServersSpec.add(ImmutableMap.of("serverName", msName,
            "serverPod", createServerPod(auxiliaryImages)));
    return managedServersSpec;
  }

  static Map<String, Object> createServerPodWithAuxImages(List<Object> auxiliaryImages) {
    return ImmutableMap.of("serverPod", createServerPod(auxiliaryImages));
  }

  @Nonnull
  private static Map<String, Object> createServerPod(List<Object> auxiliaryImages) {
    Map<String, Object> serverPod = new LinkedHashMap<>();
    serverPod.put("auxiliaryImages", auxiliaryImages);
    return serverPod;
  }

  @Nonnull
  private static Map<String, Object> createServerPod(List<Object> auxiliaryImages, Map<String, Object> resources) {
    Map<String, Object> serverPod = new LinkedHashMap<>();
    serverPod.put("auxiliaryImages", auxiliaryImages);
    serverPod.put("resources", resources);
    return serverPod;
  }

  @Nonnull
  private static Map<String, Object> createWebLogicCredentialsSecret() {
    Map<String, Object> webLogicCredentialsSecret = new LinkedHashMap<>();
    webLogicCredentialsSecret.put("name", "webLogicCredentialsSecretName");
    return webLogicCredentialsSecret;
  }

  @Nonnull
  static Map<String, Object> createMetadata() {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("name", "domain1");
    metadata.put("namespace", "namespace");
    metadata.put("uid", "12345");
    return metadata;
  }

  static Map<String, Object> createAuxiliaryImage(String image, String imagePullPolicy) {
    return createAuxiliaryImage(image, imagePullPolicy,
            AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, TEST_VOLUME_NAME);
  }

  static Map<String, Object> createAuxiliaryImage(String image, String imagePullPolicy,
                                                  String command) {
    return createAuxiliaryImage(image, imagePullPolicy, command,
            TEST_VOLUME_NAME);
  }

  @Nonnull
  private static Map<String, Object> createAuxiliaryImage(String image, String imagePullPolicy,
                                                          String command, String volume) {
    Map<String, Object> auxiliaryImage = new LinkedHashMap<>();
    auxiliaryImage.put("image", image);
    auxiliaryImage.put("imagePullPolicy", imagePullPolicy);
    auxiliaryImage.put("volume", volume);
    auxiliaryImage.put("command", command);
    return auxiliaryImage;
  }

  static Map<String, Object> createResources() {
    Map<String, Object> resources = new LinkedHashMap<>();
    resources.put("requests", Collections.singletonMap("memory", "1Gi"));
    resources.put("limits", Collections.singletonMap("cpu", "250m"));
    return resources;
  }

  static Map<String, Object> createAuxiliaryImageVolume(String mountPath) {
    return createAuxiliaryImageVolume(TEST_VOLUME_NAME, mountPath);
  }

  @Nonnull
  static Map<String, Object> createAuxiliaryImageVolume(String name, String mountPath) {
    Map<String, Object> auxiliaryImageVolumes = new LinkedHashMap<>();
    auxiliaryImageVolumes.put("name", name);
    auxiliaryImageVolumes.put("mountPath", mountPath);
    return auxiliaryImageVolumes;
  }

  @Nonnull
  static Map<String, Object> createAuxiliaryImageVolume(String name, String mountPath, String sizeLimit,
                                                                String medium) {
    Map<String, Object> auxiliaryImageVolume = new LinkedHashMap<>();
    auxiliaryImageVolume.put("name", name);
    auxiliaryImageVolume.put("mountPath", mountPath);
    Optional.ofNullable(sizeLimit).ifPresent(s -> auxiliaryImageVolume.put("sizeLimit", s));
    Optional.ofNullable(medium).ifPresent(m -> auxiliaryImageVolume.put("medium", m));
    return auxiliaryImageVolume;
  }

  void convertDomainWithLegacyAuxImages(Map<String, Object> map) {
    try {
      testSupport.addDomainPresenceInfo(new DomainPresenceInfo(
              readDomain(conversionUtils.convertDomainSchema(Yaml.dump(map)))));
      addAuxiliaryImagePathsEnvToPacket();
    } catch (IOException ioe) {
      ioe.printStackTrace();
    }
  }

  @Test
  void whenDomainHasLegacyAuxImagesWithCustomCommand_createPodsWithAuxImageInitContainerHavingCustomCommand() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image:v1", "IfNotPresent",
            CUSTOM_COMMAND_SCRIPT);

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(Collections.singletonList(auxiliaryImageVolume),
                            Collections.singletonList(auxiliaryImage))));
    assertThat(getCreatedPodSpecInitContainers(),
            allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                    "wdt-image:v1", "IfNotPresent", CUSTOM_COMMAND_SCRIPT, serverName)));
  }

  private void addAuxiliaryImagePathsEnvToPacket() {
    testSupport.addToPacket(ENVVARS,
            Collections.singletonList(new V1EnvVar().name(AUXILIARY_IMAGE_PATHS)
                    .value(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Test
  void whenDomainHasMultipleLegacyAuxiliaryImages_createPodsWithAuxiliaryImageInitContainersInCorrectOrder() {
    Map<String, Object> auxiliaryImageVolume = createAuxiliaryImageVolume(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH);
    Map<String, Object> auxiliaryImage =
        createAuxiliaryImage("wdt-image1:v1", "IfNotPresent");
    Map<String, Object> auxiliaryImage2 =
        createAuxiliaryImage("wdt-image2:v1", "IfNotPresent");

    convertDomainWithLegacyAuxImages(
            createLegacyDomainMap(
                    createDomainSpecMap(
                            Collections.singletonList(auxiliaryImageVolume),
                            Arrays.asList(auxiliaryImage, auxiliaryImage2))));

    assertThat(getCreatedPodSpecInitContainers(),
        allOf(Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 1,
                "wdt-image1:v1", "IfNotPresent",
                AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, serverName),
            Matchers.hasLegacyAuxiliaryImageInitContainer(AUXILIARY_IMAGE_INIT_CONTAINER_NAME_PREFIX + 2,
                "wdt-image2:v1",
                "IfNotPresent",
                AUXILIARY_IMAGE_DEFAULT_INIT_CONTAINER_COMMAND, serverName)));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(), hasSize(4));
    assertThat(getCreatedPodSpecContainers().get(0).getVolumeMounts(),
            hasItem(new V1VolumeMount().name(COMPATIBILITY_MODE + AUXILIARY_IMAGE_VOLUME_NAME_PREFIX + TEST_VOLUME_NAME)
                    .mountPath(DEFAULT_LEGACY_AUXILIARY_IMAGE_MOUNT_PATH)));
  }

  @Nonnull
  List<AuxiliaryImage> getAuxiliaryImages(String... images) {
    List<AuxiliaryImage> auxiliaryImageList = new ArrayList<>();
    Arrays.stream(images).forEach(image -> auxiliaryImageList
            .add(new AuxiliaryImage().image(image)));
    return auxiliaryImageList;
  }

  @Nonnull
  public static AuxiliaryImage getAuxiliaryImage(String image) {
    return new AuxiliaryImage().image(image);
  }

  @Test
  void whenIntrospectionCreatesMultipleConfigMaps_createCorrespondingVolumeMounts() {
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
  void whenPodCreated_lifecyclePreStopHasStopServerCommand() {
    assertThat(
        getCreatedPodSpecContainer().getLifecycle().getPreStop().getExec().getCommand(),
        contains("/weblogic-operator/scripts/stopServer.sh"));
  }

  @Test
  void whenPodCreated_livenessProbeHasLivenessCommand() {
    assertThat(
        getCreatedPodSpecContainer().getLivenessProbe().getExec().getCommand(),
        contains("/weblogic-operator/scripts/livenessProbe.sh"));
  }

  @Test
  void whenPodCreated_livenessProbeHasDefinedTuning() {
    assertThat(
        getCreatedPodSpecContainer().getLivenessProbe(),
        hasExpectedTuning(LIVENESS_INITIAL_DELAY, LIVENESS_TIMEOUT, LIVENESS_PERIOD, DEFAULT_SUCCESS_THRESHOLD,
                CONFIGURED_FAILURE_THRESHOLD));
  }

  @Test
  void whenPodCreated_readinessProbeHasReadinessCommand() {
    V1HTTPGetAction getAction = getCreatedPodSpecContainer().getReadinessProbe().getHttpGet();
    assertThat(getAction.getPath(), equalTo("/weblogic/ready"));
    assertThat(getAction.getPort().getIntValue(), equalTo(listenPort));
  }

  @Test
  void whenPodCreated_readinessProbeHasDefinedTuning() {
    assertThat(
        getCreatedPodSpecContainer().getReadinessProbe(),
        hasExpectedTuning(READINESS_INITIAL_DELAY, READINESS_TIMEOUT, READINESS_PERIOD, DEFAULT_SUCCESS_THRESHOLD,
                CONFIGURED_FAILURE_THRESHOLD));
  }

  @Test
  void whenPodCreatedWithAdminPortEnabled_readinessProbeHasReadinessCommand() {
    final Integer adminPort = 9002;
    domainTopology.getServerConfig(serverName).setAdminPort(adminPort);
    V1HTTPGetAction getAction = getCreatedPodSpecContainer().getReadinessProbe().getHttpGet();
    assertThat(getAction.getPath(), equalTo("/weblogic/ready"));
    assertThat(getAction.getPort().getIntValue(), equalTo(adminPort));
    assertThat(getAction.getScheme(), equalTo("HTTPS"));
  }

  @Test
  void whenPodCreatedWithAdminPortEnabled_adminPortSecureEnvVarIsTrue() {
    final Integer adminPort = 9002;
    domainTopology.getServerConfig(serverName).setAdminPort(adminPort);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("ADMIN_PORT_SECURE", "true"));
  }

  @Test
  void whenPodCreatedWithOnRestartDistribution_dontAddDynamicUpdateEnvVar() {
    configureDomain().withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.ON_RESTART);

    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("DYNAMIC_CONFIG_OVERRIDE")));
  }

  @Test
  void whenPodCreatedWithDynamicDistribution_addDynamicUpdateEnvVar() {
    configureDomain().withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.DYNAMIC);

    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("DYNAMIC_CONFIG_OVERRIDE"));
  }

  @Test
  void whenDistributionStrategyModified_dontReplacePod() {
    configureDomain().withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.DYNAMIC);
    initializeExistingPod();

    configureDomain().withConfigOverrideDistributionStrategy(OverrideDistributionStrategy.ON_RESTART);
    verifyPodNotReplaced();
  }

  @Test
  void whenPodCreatedWithDomainV2Settings_livenessProbeHasConfiguredTuning() {
    configureServer()
        .withLivenessProbeSettings(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD)
        .withLivenessProbeThresholds(CONFIGURED_SUCCESS_THRESHOLD, CONFIGURED_FAILURE_THRESHOLD);
    assertThat(
        getCreatedPodSpecContainer().getLivenessProbe(),
        hasExpectedTuning(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD, CONFIGURED_SUCCESS_THRESHOLD,
                CONFIGURED_FAILURE_THRESHOLD));
  }

  @Test
  void whenPodCreated_readinessProbeHasConfiguredTuning() {
    configureServer()
        .withReadinessProbeSettings(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD)
        .withReadinessProbeThresholds(CONFIGURED_SUCCESS_THRESHOLD, CONFIGURED_FAILURE_THRESHOLD);
    assertThat(
        getCreatedPodSpecContainer().getReadinessProbe(),
        hasExpectedTuning(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD,
                CONFIGURED_SUCCESS_THRESHOLD, CONFIGURED_FAILURE_THRESHOLD));
  }

  @Test
  public void whenPodCreationFailsDueToUnprocessableEntityFailure_reportInDomainStatus() {
    testSupport.failOnCreate(POD, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus().withReason(KUBERNETES)
        .withMessageContaining("create", "pod", NS, "Test this failure"));
  }

  @Test
  public void whenPodCreationFailsDueToUnprocessableEntityFailure_createFailedKubernetesEvent() {
    testSupport.failOnCreate(POD, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(KUBERNETES_EVENT_ERROR)));
  }

  @Test
  void whenPodCreationFailsDueToUnprocessableEntityFailure_abortFiber() {
    testSupport.failOnCreate(POD, NS, new UnrecoverableErrorBuilderImpl()
        .withReason("FieldValueNotFound")
        .withMessage("Test this failure")
        .build());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(terminalStep.wasRun(), is(false));
  }

  @Test
  void whenPodCreationFailsDueToQuotaExceeded_reportInDomainStatus() {
    testSupport.failOnCreate(POD, NS, createQuotaExceededException());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(getDomain(), hasStatus().withReason(KUBERNETES)
          .withMessageContaining("create", "pod", NS, getQuotaExceededMessage()));
  }

  @Test
  void whenPodCreationFailsDueToQuotaExceeded_generateFailedEvent() {
    testSupport.failOnCreate(POD, NS, createQuotaExceededException());

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(
        "Expected Event " + DOMAIN_FAILED + " expected with message not found",
        getExpectedEventMessage(DOMAIN_FAILED),
        stringContainsInOrder("Domain", UID, "failed due to",
            getLocalizedString(KUBERNETES_EVENT_ERROR)));
  }

  private ApiException createQuotaExceededException() {
    return new ApiException(HttpURLConnection.HTTP_FORBIDDEN, getQuotaExceededMessage());
  }

  private String getQuotaExceededMessage() {
    return "pod " + getPodName() + " is forbidden: quota exceeded";
  }

  @Test
  void whenPodCreationFailsDueToQuotaExceeded_abortFiber() {
    testSupport.failOnCreate(POD, NS, createQuotaExceededException());

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
  void whenPodCreated_hasPredefinedEnvVariables() {
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
  void whenPodCreated_hasProductVersion() {
    assertThat(getCreatedPod().getMetadata().getLabels(), hasEntry(OPERATOR_VERSION, TEST_PRODUCT_VERSION));
  }

  @Test
  void whenPodCreated_withLogHomeSpecified_hasLogHomeEnvVariable() {
    final String myLogHome = "/shared/mylogs/";
    domainPresenceInfo.getDomain().getSpec().setLogHomeEnabled(true);
    domainPresenceInfo.getDomain().getSpec().setLogHome("/shared/mylogs/");
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("LOG_HOME", myLogHome));
  }

  @Test
  void whenPodCreated_withLogHomeLayoutFlat_hasLogHomeLayoutEnvVariableSet() {
    domainPresenceInfo.getDomain().getSpec().setLogHomeEnabled(true);
    domainPresenceInfo.getDomain().getSpec().setLogHome("/shared/mylogs/");
    domainPresenceInfo.getDomain().getSpec().setLogHomeLayout(LogHomeLayoutType.FLAT);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("LOG_HOME_LAYOUT",
        LogHomeLayoutType.FLAT.toString()));
  }

  @Test
  void whenPodCreated_withoutLogHomeLayout_hasNoLogHomeLayoutEnvVariableSet() {
    domainPresenceInfo.getDomain().getSpec().setLogHomeEnabled(true);
    domainPresenceInfo.getDomain().getSpec().setLogHome("/shared/mylogs/");
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar("LOG_HOME_LAYOUT",
        LogHomeLayoutType.FLAT.toString())));
  }

  @Test
  void whenPodCreated_withoutLogHomeSpecified_hasDefaultLogHomeEnvVariable() {
    domainPresenceInfo.getDomain().getSpec().setLogHomeEnabled(true);
    domainPresenceInfo.getDomain().getSpec().setLogHome(null);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("LOG_HOME", LOG_HOME + "/" + UID));
  }

  @Test
  void whenPodCreated_withLivenessCustomScriptSpecified_hasEnvVariable() {
    final String customScript = "/u01/customLiveness.sh";
    domainPresenceInfo.getDomain().getSpec().setLivenessProbeCustomScript(customScript);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("LIVENESS_PROBE_CUSTOM_SCRIPT", customScript));
  }

  @Test
  void whenPodCreated_withReplaceVariablesInJavaOptionsSpecified_hasEnvVariable() {
    domainPresenceInfo.getDomain().getSpec().setReplaceVariablesInJavaOptions(true);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar("REPLACE_VARIABLES_IN_JAVA_OPTIONS", "true"));
  }

  @Test
  void whenOperatorHasKubernetesPlatformConfigured_createdPodSpecContainerHasKubernetesPlatformEnvVariable() {
    TuningParametersStub.setParameter(KUBERNETES_PLATFORM_NAME, "Openshift");
    assertThat(getCreatedPodSpecContainer().getEnv(),
            hasEnvVar(ServerEnvVars.KUBERNETES_PLATFORM, "Openshift")
    );
  }

  @Test
  void whenNotConfigured_KubernetesPlatform_createdPodSpecContainerHasNoKubernetesPlatformEnvVariable() {
    assertThat(getCreatedPodSpecContainer().getEnv(),
            not(hasEnvVar(ServerEnvVars.KUBERNETES_PLATFORM, "Openshift"))
    );
  }

  private static final String OVERRIDE_DATA_DIR = "/u01/data";
  private static final String OVERRIDE_DATA_HOME = OVERRIDE_DATA_DIR + File.separator + UID;

  @Test
  void whenPodCreated_withDataHomeSpecified_verifyDataHomeEnvDefined() {
    domainPresenceInfo.getDomain().getSpec().setDataHome(OVERRIDE_DATA_DIR);
    assertThat(getCreatedPodSpecContainer().getEnv(), hasEnvVar(ServerEnvVars.DATA_HOME, OVERRIDE_DATA_HOME));
  }

  private static final String EMPTY_DATA_HOME = "";

  @Test
  void whenPodCreated_withDataHomeNotSpecified_verifyDataHomeEnvNotDefined() {
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar(ServerEnvVars.DATA_HOME, EMPTY_DATA_HOME)));
  }

  @Test
  void whenPodCreated_withEmptyDataHomeSpecified_verifyDataHomeEnvNotDefined() {
    domainPresenceInfo.getDomain().getSpec().setDataHome(EMPTY_DATA_HOME);
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar(ServerEnvVars.DATA_HOME, EMPTY_DATA_HOME)));
  }

  private static final String NULL_DATA_HOME = null;

  @Test
  void whenPodCreated_withNullDataHomeSpecified_verifyDataHomeEnvNotDefined() {
    domainPresenceInfo.getDomain().getSpec().setDataHome(NULL_DATA_HOME);
    assertThat(getCreatedPodSpecContainer().getEnv(), not(hasEnvVar(ServerEnvVars.DATA_HOME, NULL_DATA_HOME)));
  }

  @Test
  void whenDomainPresenceLacksClaims_adminPodSpecHasNoDomainStorageVolume() {
    assertThat(getVolumeWithName(getCreatedPod(), STORAGE_VOLUME_NAME), nullValue());
  }

  @Test
  void createdPod_hasConfigMapVolume() {
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
  void whenPodCreated_hasExpectedLabels() {
    assertThat(
        getCreatedPod().getMetadata().getLabels(),
        allOf(
            hasEntry(LabelConstants.DOMAINUID_LABEL, UID),
            hasEntry(LabelConstants.DOMAINNAME_LABEL, DOMAIN_NAME),
            hasEntry(LabelConstants.SERVERNAME_LABEL, getServerName()),
            hasEntry(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")));
  }

  @Test
  void whenPodCreated_containerUsesListenPort() {
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
  void whenPodCreatedWithSslPort_containerUsesIt() {
    domainTopology.getServerConfig(serverName).setSslListenPort(SSL_PORT);
    final V1ContainerPort sslPort = getContainerPort("default-secure");

    assertThat(sslPort, notNullValue());
    assertThat(sslPort.getProtocol(), equalTo("TCP"));
    assertThat(sslPort.getContainerPort(), equalTo(SSL_PORT));
  }

  @Test
  void whenPodCreatedWithAdminPortEnabled__containerUsesIt() {
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
  void whenPodHasUnknownCustomerLabel_ignoreIt() {
    verifyPodNotReplacedWhen(pod -> pod.getMetadata().putLabelsItem("customer.label", "value"));
  }

  @Test
  void whenPodLacksExpectedCustomerLabel_addIt() {
    initializeExistingPod();
    configurator.withPodLabel("customer.label", "value");

    V1Pod patchedPod = getPatchedPod();

    assertThat(patchedPod.getMetadata().getLabels().get("customer.label"), equalTo("value"));
  }

  @Test
  void whenPodLacksExpectedCustomerAnnotations_addIt() {
    initializeExistingPod();
    configurator.withPodAnnotation("customer.annotation", "value");

    V1Pod patchedPod = getPatchedPod();

    assertThat(patchedPod.getMetadata().getAnnotations().get("customer.annotation"), equalTo("value"));
  }

  @Test
  void whenPodCustomerLabelHasBadValue_replaceIt() {
    configurator.withPodLabel("customer.label", "value");
    misconfigurePod(pod -> pod.getMetadata().putLabelsItem("customer.label", "badvalue"));

    V1Pod patchedPod = getPatchedPod();

    assertThat(patchedPod.getMetadata().getLabels().get("customer.label"), equalTo("value"));
  }

  @Test
  void whenPodCustomerAnnotationHasBadValue_replaceIt() {
    configurator.withPodAnnotation("customer.annotation", "value");
    misconfigurePod(pod -> pod.getMetadata().putAnnotationsItem("customer.annotation", "badvalue"));

    V1Pod patchedPod = getPatchedPod();

    assertThat(patchedPod.getMetadata().getAnnotations().get("customer.annotation"), equalTo("value"));
  }

  @Test
  void whenPodLacksExpectedCustomerLabelAndRequestRequirement_replaceIt() {
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
  void whenPodHasUnknownCustomerAnnotations_ignoreIt() {
    verifyPodNotReplacedWhen(pod -> pod.getMetadata().putAnnotationsItem("annotation", "value"));
  }

  @Test
  void whenConfigurationModifiesPodSecurityContext_replacePod() {
    initializeExistingPod();

    configurator.withPodSecurityContext(new V1PodSecurityContext().runAsGroup(12345L));

    verifyPodReplaced();
  }

  @Test
  void whenConfigurationAddsNodeSelector_replacePod() {
    initializeExistingPod();

    configurator.withNodeSelector("key", "value");

    verifyPodReplaced();
  }

  @Test
  void whenNullVsEmptyNodeSelector_dontReplaceIt() {
    verifyPodNotReplacedWhen(pod -> pod.getSpec().setNodeSelector(null));
  }

  @Test
  void whenConfigurationModifiesContainerSecurityContext_replacePod() {
    initializeExistingPod();

    configurator.withContainerSecurityContext(new V1SecurityContext().runAsGroup(9876L));

    verifyPodReplaced();
  }

  @Test
  void whenPodLivenessProbeSettingsChanged_replacePod() {
    initializeExistingPod();

    configurator.withDefaultLivenessProbeSettings(8, 7, 6);

    verifyPodReplaced();
  }

  @Test
  void whenPodReadinessProbeSettingsChanged_replacePod() {
    initializeExistingPod();

    configurator.withDefaultReadinessProbeSettings(5, 4, 3);

    verifyPodReplaced();
  }

  @Test
  void whenPodRequestRequirementChanged_replacePod() {
    initializeExistingPod();

    configurator.withRequestRequirement("resource", "5");

    verifyPodReplaced();
  }

  @Test
  void whenPodRequestRequirementsEmptyVsNull_dontReplaceIt() {
    verifyPodNotReplacedWhen(pod -> pod.getSpec().getContainers().get(0).resources(null));
  }

  @Test
  void whenPodLimitRequirementChanged_replacePod() {
    initializeExistingPod();

    configurator.withLimitRequirement("limit", "7");

    verifyPodReplaced();
  }

  private V1Container getSpecContainer(V1Pod pod) {
    return pod.getSpec().getContainers().get(0);
  }

  @Test
  void whenExistingPodSpecHasK8sVolume_ignoreIt() {
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
  void whenExistingPodSpecHasK8sVolumeMount_ignoreIt() {
    verifyPodNotReplacedWhen(
        (pod) ->
            getSpecContainer(pod)
                .addVolumeMountsItem(
                    new V1VolumeMount()
                        .name("dummy")
                        .mountPath(PodDefaults.K8S_SERVICE_ACCOUNT_MOUNT_PATH)));
  }

  @Test
  void whenPodConfigurationAddsVolume_replacePod() {
    initializeExistingPod();

    configureServer().withAdditionalVolume("dummy", "/dummy");

    verifyPodReplaced();
  }

  @Test
  void whenPodConfigurationAddsImagePullSecret_replacePod() {
    initializeExistingPod();

    configureDomain().withDefaultImagePullSecrets(new V1LocalObjectReference().name("secret"));

    verifyPodReplaced();
  }

  @Test
  void whenPodConfigurationAddsVolumeMount_replacePod() {
    initializeExistingPod();

    configureServer().withAdditionalVolumeMount("dummy", "/dummy");

    verifyPodReplaced();
  }

  @Test
  void whenPodConfigurationChangesImageName_replacePod() {
    initializeExistingPod();

    configureDomain().withDefaultImage(VERSIONED_IMAGE);

    verifyPodReplaced();
  }

  @Test
  void whenPodConfigurationChangesImagePullPolicy_replacePod() {
    initializeExistingPod();

    configureDomain().withDefaultImagePullPolicy("Never");

    verifyPodReplaced();
  }

  @Test
  void whenDomainConfigurationAddsRestartVersion_replacePod() {
    initializeExistingPod();

    configureDomain().withRestartVersion("123");

    verifyPodReplaced();
  }

  @Test
  void whenServerConfigurationAddsRestartVersion_replacePod() {
    initializeExistingPod();

    configureServer().withRestartVersion("123");

    verifyPodReplaced();
  }

  @Test
  void whenServerConfigurationAddsIntrospectionVersion_patchPod() {
    initializeExistingPod();

    configurator.withIntrospectVersion("123");

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, not(containsFine(getExistsMessageKey())));
    assertThat(logRecords, containsInfo(getPatchedMessageKey()));
  }

  @Test
  void whenServerConfigurationIntrospectionVersionTheSame_dontPatchPod() {
    initializeExistingPodWithIntrospectVersion("123");

    configurator.withIntrospectVersion("123");

    verifyPodNotPatched();
  }

  @Test
  void whenServerListenPortChanged_replacePod() {
    initializeExistingPod();

    setServerPort(12345);

    verifyPodReplaced();
  }

  @Test
  void whenServerAddsNap_replacePod() {
    initializeExistingPod();

    getServerTopology().addNetworkAccessPoint(new NetworkAccessPoint("nap1", "TCP", 1234, 9001));

    verifyPodReplaced();
  }

  @Test
  void whenMiiSecretsHashChanged_replacePod() {
    testSupport.addToPacket(SECRETS_MD_5, "originalSecret");
    initializeExistingPod();

    testSupport.addToPacket(SECRETS_MD_5, "newSecret");

    verifyPodReplaced();
  }

  @Test
  void whenMiiDomainZipHashChanged_replacePod() {
    testSupport.addToPacket(DOMAINZIP_HASH, "originalSecret");
    initializeExistingPod();

    testSupport.addToPacket(DOMAINZIP_HASH, "newSecret");

    verifyPodReplaced();
  }

  @Test
  void whenMiiDynamicUpdateDynamicChangesOnlyButOnlineUpdateDisabled_replacePod() {
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_SUCCESS);

    verifyPodReplaced();
  }

  @Test
  void whenMiiDynamicUpdateDynamicChangesOnly_dontReplacePod() {
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
    domain.getSpec().setDomainHomeSourceType(DomainSourceType.IMAGE);
  }

  @Test
  void whenMiiDynamicUpdateDynamicChangesOnly_updateDomainZipHash() {
    configureDomain().withMIIOnlineUpdate();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_SUCCESS);

    verifyPodPatched();

    assertThat(getPodLabel(LabelConstants.MODEL_IN_IMAGE_DOMAINZIP_HASH), equalTo(paddedZipHash("newZipHash")));
  }

  @Test
  void whenMiiOnlineUpdateSettingEnabled_dontReplacePod() {
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
  void whenMiiNonDynamicUpdateDynamicChangesCommitOnly_dontReplacePod() {
    configureDomain().withMIIOnlineUpdate();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_RESTART_REQUIRED);

    verifyPodPatched();
  }

  @Test
  void whenMiiNonDynamicUpdateDynamicChangesCommitOnly_addRestartRequiredLabel() {
    configureDomain().withMIIOnlineUpdate();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_RESTART_REQUIRED);

    assertThat(getPatchedPod().getMetadata().getLabels(), hasEntry(MII_UPDATED_RESTART_REQUIRED_LABEL, "true"));
  }

  @Test
  void whenMiiNonDynamicUpdateDynamicChangesCommitAndRoll_replacePod() {
    configureDomain().withMIIOnlineUpdateOnDynamicChangesUpdateAndRoll();
    initializeMiiUpdateTest(MII_DYNAMIC_UPDATE_RESTART_REQUIRED);

    verifyPodReplaced();
  }

  private String paddedZipHash(String hash) {
    return "md5." + hash + ".md5";
  }

  @Test
  void whenNoPod_onInternalError() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(KubernetesTestSupport.POD, NS, HTTP_INTERNAL_ERROR);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    assertThat(getDomain(), hasStatus().withReason(KUBERNETES).withMessageContaining("create", "pod", NS));
  }

  @Test
  void whenNoPod_generateFailedEvent() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(KubernetesTestSupport.POD, NS, HTTP_INTERNAL_ERROR);

    FiberTestSupport.StepFactory stepFactory = getStepFactory();
    Step initialStep = stepFactory.createStepList(terminalStep);
    testSupport.runSteps(initialStep);

    assertThat(getEvents().stream().anyMatch(this::isKubernetesFailedEvent), is(true));
  }

  private boolean isKubernetesFailedEvent(CoreV1Event e) {
    return DOMAIN_FAILED_EVENT.equals(e.getReason())
        && e.getMessage().contains(getLocalizedString(KUBERNETES_EVENT_ERROR));
  }

  @Test
  void whenCompliantPodExists_logIt() {
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
            new V1LifecycleHandler()
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
        .name(WLS_CONTAINER_NAME)
        .image(LATEST_IMAGE)
        .imagePullPolicy("Always")
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

  static V1Toleration createToleration(String key, String operator, String value,
                                       String effect) {
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
  void whenDomainPresenceInfoLacksImageName_createdPodUsesDefaultImage() {
    configureDomain().withDefaultImage(null);

    assertThat(getCreatedPodSpecContainer().getImage(), equalTo(DEFAULT_IMAGE));
  }

  @Test
  void verifyStandardVolumes() {
    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasItem(volume(INTROSPECTOR_VOLUME, UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX)),
              hasItem(volume(DEBUG_CM_VOLUME, UID + DOMAIN_DEBUG_CONFIG_MAP_SUFFIX)),
              hasItem(volume(SCRIPTS_VOLUME, SCRIPT_CONFIG_MAP_NAME))));
  }

  @Test
  void whenIntrospectionCreatesMultipleConfigMaps_createCorrespondingVolumes() {
    testSupport.addToPacket(NUM_CONFIG_MAPS, "3");

    assertThat(
          getCreatedPod().getSpec().getVolumes(),
          allOf(hasItem(volume(INTROSPECTOR_VOLUME, UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX)),
                hasItem(volume(INTROSPECTOR_VOLUME + "-1", UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX + "-1")),
                hasItem(volume(INTROSPECTOR_VOLUME + "-2", UID + INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX + "-2"))));
  }

  @Test
  void whenDomainHasAdditionalVolumes_createPodWithThem() {
    getConfigurator()
        .withAdditionalVolume("volume1", "/source-path1")
        .withAdditionalVolume("volume2", "/source-path2");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasVolume("volume1", "/source-path1"), hasVolume("volume2", "/source-path2")));
  }

  @Test
  void whenDomainHasAdditionalPvClaimVolume_createPodWithIt() {
    getConfigurator()
        .withAdditionalPvClaimVolume("volume1", "myPersistentVolumeClaim");

    assertThat(
        getCreatedPod().getSpec().getVolumes(),
        allOf(hasPvClaimVolume("volume1", "myPersistentVolumeClaim")));
  }

  @Test
  void whenDomainHasAdditionalVolumeMounts_createAdminPodWithThem() {
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
  void whenDomainHasNoAffinity_createdNonClusteredPodHasDefaultDomainUidVariableAffinity() {
    assertThat(getCreatePodAffinity(), is(new AffinityHelper().domainUID(UID).getAntiAffinity()));
  }

  @Test
  void whenDomainHasAffinity_createPodWithIt() {
    getConfigurator()
        .withAffinity(affinity);

    assertThat(
        getCreatedPod().getSpec().getAffinity(),
        is(affinity));
  }

  @Test
  void whenServerHasAffinity_createPodWithIt() {
    configureServer()
        .withAffinity(affinity);

    assertThat(
        getCreatedPod().getSpec().getAffinity(),
        is(affinity));
  }

  @Test
  void whenDomainHasNodeSelector_createPodWithIt() {
    getConfigurator()
        .withNodeSelector("os_arch", "x86_64");

    assertThat(
        getCreatedPod().getSpec().getNodeSelector(),
        hasEntry("os_arch", "x86_64"));
  }

  @Test
  void whenServerHasNodeSelector_createPodWithIt() {
    configureServer()
        .withNodeSelector("os_arch", "x86_64");

    assertThat(
        getCreatedPod().getSpec().getNodeSelector(),
        hasEntry("os_arch", "x86_64"));
  }

  @Test
  void whenDomainHasNodeName_createPodWithIt() {
    getConfigurator()
        .withNodeName("kube-01");

    assertThat(
        getCreatedPod().getSpec().getNodeName(),
        is("kube-01"));
  }

  @Test
  void whenServerHasNodeName_createPodWithIt() {
    configureServer()
        .withNodeName("kube-01");

    assertThat(
        getCreatedPod().getSpec().getNodeName(),
        is("kube-01"));
  }

  @Test
  void whenDomainHasSchedulerName_createPodWithIt() {
    getConfigurator()
        .withSchedulerName("my-scheduler");

    assertThat(
        getCreatedPod().getSpec().getSchedulerName(),
        is("my-scheduler"));
  }

  @Test
  void whenServerHasSchedulerName_createPodWithIt() {
    configureServer()
        .withSchedulerName("my-scheduler");

    assertThat(
        getCreatedPod().getSpec().getSchedulerName(),
        is("my-scheduler"));
  }

  @Test
  void whenDomainHasRuntimeClassName_createPodWithIt() {
    getConfigurator()
        .withRuntimeClassName("RuntimeClassName");

    assertThat(
        getCreatedPod().getSpec().getRuntimeClassName(),
        is("RuntimeClassName"));
  }

  @Test
  void whenServerHasRuntimeClassName_createPodWithIt() {
    configureServer()
        .withRuntimeClassName("RuntimeClassName");

    assertThat(
        getCreatedPod().getSpec().getRuntimeClassName(),
        is("RuntimeClassName"));
  }

  @Test
  void whenDomainHasPriorityClassName_createPodWithIt() {
    getConfigurator()
        .withPriorityClassName("PriorityClassName");

    assertThat(
        getCreatedPod().getSpec().getPriorityClassName(),
        is("PriorityClassName"));
  }

  @Test
  void whenServerHasPriorityClassName_createPodWithIt() {
    configureServer()
        .withPriorityClassName("PriorityClassName");

    assertThat(
        getCreatedPod().getSpec().getPriorityClassName(),
        is("PriorityClassName"));
  }

  @Test
  void whenDomainHasRestartPolicy_createPodWithIt() {
    getConfigurator()
        .withRestartPolicy("Always");

    assertThat(
        getCreatedPod().getSpec().getRestartPolicy(),
        is("Always"));
  }

  @Test
  void whenServerHasRestartPolicy_createPodWithIt() {
    configureServer()
        .withRestartPolicy("Always");

    assertThat(
        getCreatedPod().getSpec().getRestartPolicy(),
        is("Always"));
  }

  @Test
  void whenDomainHasPodSecurityContext_createPodWithIt() {
    getConfigurator()
        .withPodSecurityContext(podSecurityContext);

    assertThat(
        getCreatedPod().getSpec().getSecurityContext(),
        is(podSecurityContext));
  }

  @Test
  void whenServerHasPodSecurityContext_createPodWithIt() {
    configureServer()
        .withPodSecurityContext(podSecurityContext);

    assertThat(
        getCreatedPod().getSpec().getSecurityContext(),
        is(podSecurityContext));
  }

  @Test
  void whenDomainHasContainerSecurityContext_createContainersWithIt() {
    getConfigurator()
        .withContainerSecurityContext(containerSecurityContext);

    getCreatedPodSpecContainers()
        .forEach(c -> assertThat(
            c.getSecurityContext(),
            is(containerSecurityContext)));
  }

  @Test
  void whenServerHasContainerSecurityContext_createContainersWithIt() {
    configureServer()
        .withContainerSecurityContext(containerSecurityContext);

    getCreatedPodSpecContainers()
        .forEach(c -> assertThat(
            c.getSecurityContext(),
            is(containerSecurityContext)));
  }

  @Test
  void whenServerHasResources_createContainersWithThem() {
    configureServer()
        .withLimitRequirement("cpu", "1Gi")
        .withRequestRequirement("memory", "250m");

    List<V1Container> containers = getCreatedPodSpecContainers();

    containers.forEach(c -> assertThat(c.getResources().getLimits(), hasResourceQuantity("cpu", "1Gi")));
    containers.forEach(c -> assertThat(c.getResources().getRequests(), hasResourceQuantity("memory", "250m")));
  }

  @Test
  void whenDomainHasResources_createContainersWithThem() {
    getConfigurator()
        .withLimitRequirement("cpu", "1Gi")
        .withRequestRequirement("memory", "250m");

    List<V1Container> containers = getCreatedPodSpecContainers();

    containers.forEach(c -> assertThat(c.getResources().getLimits(), hasResourceQuantity("cpu", "1Gi")));
    containers.forEach(c -> assertThat(c.getResources().getRequests(), hasResourceQuantity("memory", "250m")));
  }

  @Test
  void whenPodCreated_createPodWithOwnerReference() {
    V1OwnerReference expectedReference = new V1OwnerReference()
        .apiVersion(KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION)
        .kind(DOMAIN)
        .name(DOMAIN_NAME)
        .uid(KUBERNETES_UID)
        .controller(true);

    assertThat(getCreatedPod().getMetadata().getOwnerReferences(), contains(expectedReference));
  }

  protected void assertContainsEventWithNamespace(EventHelper.EventItem event, String ns) {
    MatcherAssert.assertThat(
        "Expected Event " + event.getReason() + " was not created",
        containsEventWithNamespace(getEvents(), event.getReason(), ns),
        is(true));
  }

  protected String getExpectedEventMessage(EventHelper.EventItem event) {
    List<CoreV1Event> events = getEventsWithReason(getEvents(), event.getReason());
    //System.out.println(events);
    return Optional.ofNullable(events)
        .filter(list -> list.size() != 0)
        .map(n -> n.get(0))
        .map(CoreV1Event::getMessage)
        .orElse("Event not found");
  }

  List<CoreV1Event> getEvents() {
    return testSupport.getResources(KubernetesTestSupport.EVENT);
  }

  interface PodMutator {
    void mutate(V1Pod pod);
  }

  public static class PassthroughPodAwaiterStepFactory implements PodAwaiterStepFactory {
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

    @Override
    public Step waitForServerShutdown(String serverName, DomainResource domain, Step next) {
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

    @Override
    public Step waitForServerShutdown(String serverName, DomainResource domain, Step next) {
      return next;
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

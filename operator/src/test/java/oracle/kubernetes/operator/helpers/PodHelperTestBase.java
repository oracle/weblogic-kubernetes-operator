// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.ProbeMatcher.hasExpectedTuning;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.VolumeMountMatcher.readOnlyVolumeMount;
import static oracle.kubernetes.operator.helpers.PodHelperTestBase.VolumeMountMatcher.writableVolumeMount;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ExecAction;
import io.kubernetes.client.models.V1Handler;
import io.kubernetes.client.models.V1HostPathVolumeSource;
import io.kubernetes.client.models.V1Lifecycle;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1PersistentVolumeSpec;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1SecretReference;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParametersImpl;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.ServerConfigurator;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import oracle.kubernetes.weblogic.domain.v2.DomainV2Configurator;
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
  static final String INCLUDE_SERVER_OUT_IN_POD_LOG = "true";

  private static final String ADMIN_SECRET_NAME = "adminSecretName";
  private static final String STORAGE_VOLUME_NAME = "weblogic-domain-storage-volume";
  private static final String LATEST_IMAGE = "image:latest";
  static final String VERSIONED_IMAGE = "image:1.2.3";
  private static final int READINESS_INITIAL_DELAY = 1;
  private static final int READINESS_TIMEOUT = 2;
  private static final int READINESS_PERIOD = 3;
  private static final int LIVENESS_INITIAL_DELAY = 4;
  private static final int LIVENESS_PERIOD = 6;
  private static final int LIVENESS_TIMEOUT = 5;
  private static final int CONFIGURED_DELAY = 21;
  private static final int CONFIGURED_TIMEOUT = 27;
  private static final int CONFIGURED_PERIOD = 35;
  private static final String LOG_HOME = "/shared/logs";
  private static final String NODEMGR_HOME = "/u01/nodemanager";
  private static final String CREDENTIALS_VOLUME_NAME = "weblogic-credentials-volume";
  private static final String CONFIGMAP_VOLUME_NAME = "weblogic-domain-cm-volume";
  private static final String SIT_CONFIG_MAP_VOLUME_SUFFIX =
      "-weblogic-domain-introspect-cm-volume";
  private static final int READ_AND_EXECUTE_MODE = 0555;
  private static final Map<String, String> NODE_SELECTOR =
      Collections.singletonMap("labelKey", "labelValue");

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

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, getMessageKeys())
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());
    mementos.add(TuningParametersStub.install());

    testSupport.addDomainPresenceInfo(domainPresenceInfo);
    onAdminExpectListPersistentVolume();
  }

  private String[] getMessageKeys() {
    return new String[] {
      getPodCreatedMessageKey(), getPodExistsMessageKey(), getPodReplacedMessageKey()
    };
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  private DomainPresenceInfo createDomainPresenceInfo(Domain domain) {
    DomainPresenceInfo domainPresenceInfo = new DomainPresenceInfo(domain);
    domainPresenceInfo.setClaims(new V1PersistentVolumeClaimList());
    return domainPresenceInfo;
  }

  private Domain createDomain() {
    return new Domain().withMetadata(new V1ObjectMeta().namespace(NS)).withSpec(createDomainSpec());
  }

  @SuppressWarnings("deprecation")
  private DomainSpec createDomainSpec() {
    return new DomainSpec()
        .withDomainName(DOMAIN_NAME)
        .withDomainUID(UID)
        .withAsName(ADMIN_SERVER)
        .withAsPort(ADMIN_PORT)
        .withAdminSecret(new V1SecretReference().name(ADMIN_SECRET_NAME))
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

  @SuppressWarnings("deprecation")
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

  @Test
  public void whenNoPod_createIt() {
    expectCreatePod(podWithName(getPodName())).returning(createPodModel());
    expectStepsAfterCreation();

    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsInfo(getPodCreatedMessageKey()));
  }

  @Test
  public void whenPodCreated_specHasOneContainer() {
    assertThat(getCreatedPod().getSpec().getContainers(), hasSize(1));
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
  public void whenPodCreated_containerHasExpectedVolumeMounts() {
    assertThat(
        getCreatedPodSpecContainer().getVolumeMounts(),
        containsInAnyOrder(
            writableVolumeMount("weblogic-domain-storage-volume", "/shared"),
            writableVolumeMount(
                UID + SIT_CONFIG_MAP_VOLUME_SUFFIX, "/weblogic-operator/introspector"),
            readOnlyVolumeMount("weblogic-credentials-volume", "/weblogic-operator/secrets"),
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
    assertThat(
        getCreatedPodSpecContainer().getReadinessProbe().getExec().getCommand(),
        contains("/weblogic-operator/scripts/readinessProbe.sh"));
  }

  @Test
  public void whenPodCreated_readinessProbeHasDefinedTuning() {
    assertThat(
        getCreatedPodSpecContainer().getReadinessProbe(),
        hasExpectedTuning(READINESS_INITIAL_DELAY, READINESS_TIMEOUT, READINESS_PERIOD));
  }

  @Test
  public void whenPodCreatedWithDomainV2Settings_livenessProbeHasConfiguredTuning() {
    configureDomainV2Server()
        .withLivenessProbeSettings(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD);
    assertThat(
        getCreatedPodSpecContainer().getLivenessProbe(),
        hasExpectedTuning(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD));
  }

  @Test
  public void whenPodCreatedWithDomainV2Settings_readinessProbeHasConfiguredTuning() {
    configureDomainV2Server()
        .withReadinessProbeSettings(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD);
    assertThat(
        getCreatedPodSpecContainer().getReadinessProbe(),
        hasExpectedTuning(CONFIGURED_DELAY, CONFIGURED_TIMEOUT, CONFIGURED_PERIOD));
  }

  private ServerConfigurator configureDomainV2Server() {
    return getServerConfigurator(new DomainV2Configurator(domain), getServerName());
  }

  protected abstract ServerConfigurator getServerConfigurator(
      DomainConfigurator configurator, String serverName);

  @SuppressWarnings("unchecked")
  @Test
  public void whenPodCreated_hasPredefinedEnvVariables() {
    assertThat(
        getCreatedPodSpecContainer().getEnv(),
        allOf(
            hasEnvVar("DOMAIN_NAME", DOMAIN_NAME),
            hasEnvVar("DOMAIN_HOME", "/shared/domains/" + UID),
            hasEnvVar("ADMIN_NAME", ADMIN_SERVER),
            hasEnvVar("ADMIN_PORT", Integer.toString(ADMIN_PORT)),
            hasEnvVar("SERVER_NAME", getServerName()),
            hasEnvVar("ADMIN_USERNAME", null),
            hasEnvVar("ADMIN_PASSWORD", null),
            hasEnvVar("DOMAIN_UID", UID),
            hasEnvVar("NODEMGR_HOME", NODEMGR_HOME),
            hasEnvVar("SERVER_OUT_IN_POD_LOG", INCLUDE_SERVER_OUT_IN_POD_LOG),
            hasEnvVar("LOG_HOME", LOG_HOME + "/" + UID),
            hasEnvVar("SERVICE_NAME", LegalNames.toServerServiceName(UID, getServerName())),
            hasEnvVar("AS_SERVICE_NAME", LegalNames.toServerServiceName(UID, ADMIN_SERVER))));
  }

  @Test
  public void whenPodCreated_withLogHomeSpecified_hasLogHomeEnvVariable() {
    final String MY_LOG_HOME = "/shared/mylogs";
    domainPresenceInfo.getDomain().getSpec().setLogHome("/shared/mylogs");
    assertThat(getCreatedPodSpecContainer().getEnv(), allOf(hasEnvVar("LOG_HOME", MY_LOG_HOME)));
  }

  @Test
  public void whenPodCreated_withoutLogHomeSpecified_hasDefaultLogHomeEnvVariable() {
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

  @Test
  public void whenDomainPresenceLacksClaims_adminPodSpecHasNoDomainStorageVolume() {
    assertThat(getVolumeWithName(getCreatedPod(), STORAGE_VOLUME_NAME), nullValue());
  }

  @Test
  public void whenDomainPresenceHasClaim_podSpecHasDomainStorageVolume() {
    domainPresenceInfo
        .getClaims()
        .addItemsItem(
            new V1PersistentVolumeClaim().metadata(new V1ObjectMeta().name("claim-name")));

    V1Volume storageVolume = getVolumeWithName(getCreatedPod(), STORAGE_VOLUME_NAME);

    assertThat(storageVolume.getPersistentVolumeClaim().getClaimName(), equalTo("claim-name"));
  }

  @Test
  public void whenDomainSpecifiesClaimName_podSpecUsesIt() {
    configurator.withPredefinedClaim("predefined");
    domainPresenceInfo
        .getClaims()
        .addItemsItem(
            new V1PersistentVolumeClaim().metadata(new V1ObjectMeta().name("claim-name")));

    V1Volume storageVolume = getVolumeWithName(getCreatedPod(), STORAGE_VOLUME_NAME);

    assertThat(storageVolume.getPersistentVolumeClaim().getClaimName(), equalTo("predefined"));
  }

  @Test
  public void createdPod_hasCredentialsVolume() {
    V1Volume credentialsVolume = getVolumeWithName(getCreatedPod(), CREDENTIALS_VOLUME_NAME);

    assertThat(credentialsVolume.getSecret().getSecretName(), equalTo(ADMIN_SECRET_NAME));
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
            hasEntry("prometheus.io/port", "" + Integer.toString(listenPort)),
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

  abstract String getPodCreatedMessageKey();

  abstract FiberTestSupport.StepFactory getStepFactory();

  V1Pod getCreatedPod() {
    PodFetcher podFetcher = new PodFetcher(getPodName());
    expectCreatePod(podFetcher).returning(createPodModel());
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
    initializeExistingPod(createPodModel());
    testSupport.runSteps(getStepFactory(), terminalStep);

    assertThat(logRecords, containsFine(getPodExistsMessageKey()));
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

  abstract String getPodExistsMessageKey();

  abstract String getPodReplacedMessageKey();

  abstract V1Pod createPodModel();

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
    return new V1ObjectMeta()
        .putLabelsItem(RESOURCE_VERSION_LABEL, VersionConstants.DEFAULT_DOMAIN_VERSION)
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, UID)
        .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, DOMAIN_NAME)
        .putLabelsItem(LabelConstants.DOMAINHOME_LABEL, "/shared/domains/" + UID)
        .putLabelsItem(LabelConstants.SERVERNAME_LABEL, getServerName())
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
  }

  V1Container createPodSpecContainer() {
    return new V1Container()
        .name(CONTAINER_NAME)
        .image(LATEST_IMAGE)
        .imagePullPolicy(ALWAYS_IMAGEPULLPOLICY)
        .addPortsItem(new V1ContainerPort().protocol("TCP").containerPort(listenPort))
        .lifecycle(createLifecycle())
        .addVolumeMountsItem(
            new V1VolumeMount().name("weblogic-domain-storage-volume").mountPath("/shared"))
        .addVolumeMountsItem(
            new V1VolumeMount()
                .name("weblogic-credentials-volume")
                .mountPath("/weblogic-operator/secrets")
                .readOnly(true))
        .addVolumeMountsItem(
            new V1VolumeMount()
                .name("weblogic-domain-cm-volume")
                .mountPath("/weblogic-operator/scripts")
                .readOnly(true))
        .command(createStartCommand())
        .addEnvItem(envItem("DOMAIN_NAME", DOMAIN_NAME))
        .addEnvItem(envItem("DOMAIN_HOME", "/shared/domains/" + UID))
        .addEnvItem(envItem("ADMIN_NAME", ADMIN_SERVER))
        .addEnvItem(envItem("ADMIN_PORT", Integer.toString(ADMIN_PORT)))
        .addEnvItem(envItem("SERVER_NAME", getServerName()))
        .addEnvItem(envItem("ADMIN_USERNAME", null))
        .addEnvItem(envItem("ADMIN_PASSWORD", null))
        .addEnvItem(envItem("DOMAIN_UID", UID))
        .addEnvItem(envItem("NODEMGR_HOME", NODEMGR_HOME))
        .addEnvItem(envItem("SERVER_OUT_IN_POD_LOG", INCLUDE_SERVER_OUT_IN_POD_LOG))
        .addEnvItem(envItem("LOG_HOME", LOG_HOME + "/" + UID))
        .addEnvItem(envItem("SERVICE_NAME", LegalNames.toServerServiceName(UID, getServerName())))
        .addEnvItem(envItem("AS_SERVICE_NAME", LegalNames.toServerServiceName(UID, ADMIN_SERVER)))
        .livenessProbe(createLivenessProbe())
        .readinessProbe(createReadinessProbe());
  }

  V1PodSpec createPodSpec() {
    V1PodSpec v1PodSpec =
        new V1PodSpec().containers(Collections.singletonList(createPodSpecContainer()));
    v1PodSpec.setNodeSelector(createNodeSelector());
    return new V1PodSpec().containers(Collections.singletonList(createPodSpecContainer()));
  }

  Map<String, String> createNodeSelector() {
    return NODE_SELECTOR;
  }

  abstract List<String> createStartCommand();

  @Test
  public void whenDomainPresenceInfoLacksImageName_createdPodUsesDefaultImage() {
    configureDomain().withDefaultImage(null);

    assertThat(getCreatedPodSpecContainer().getImage(), equalTo(DEFAULT_IMAGE));
  }

  interface PodMutator {
    void mutate(V1Pod pod);
  }

  abstract static class TuningParametersStub implements TuningParameters {
    static Map<String, String> namedParameters;

    static Memento install() throws NoSuchFieldException {
      namedParameters = new HashMap<>();
      return StaticStubSupport.install(
          TuningParametersImpl.class, "INSTANCE", createStrictStub(TuningParametersStub.class));
    }

    @Override
    public PodTuning getPodTuning() {
      return new PodTuning(
          READINESS_INITIAL_DELAY,
          READINESS_TIMEOUT,
          READINESS_PERIOD,
          LIVENESS_INITIAL_DELAY,
          LIVENESS_TIMEOUT,
          LIVENESS_PERIOD);
    }

    @Override
    public CallBuilderTuning getCallBuilderTuning() {
      return null;
    }

    @Override
    public String get(Object key) {
      return namedParameters.get(key);
    }
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
}

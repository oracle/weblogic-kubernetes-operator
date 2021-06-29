// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ExecAction;
import io.kubernetes.client.openapi.models.V1HTTPGetAction;
import io.kubernetes.client.openapi.models.V1Handler;
import io.kubernetes.client.openapi.models.V1Lifecycle;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.helpers.CompatibilityCheck.CompatibilityScope;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.CommonMount;
import oracle.kubernetes.weblogic.domain.model.CommonMountEnvVars;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars;
import oracle.kubernetes.weblogic.domain.model.MonitoringExporterSpecification;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import oracle.kubernetes.weblogic.domain.model.Shutdown;
import org.apache.commons.lang3.builder.EqualsBuilder;

import static oracle.kubernetes.operator.EventConstants.ROLL_REASON_DOMAIN_RESOURCE_CHANGED;
import static oracle.kubernetes.operator.EventConstants.ROLL_REASON_WEBLOGIC_CONFIGURATION_CHANGED;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.NUM_CONFIG_MAPS;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_SIDECAR_PORT;
import static oracle.kubernetes.operator.KubernetesConstants.EXPORTER_CONTAINER_NAME;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.LabelConstants.MII_UPDATED_RESTART_REQUIRED_LABEL;
import static oracle.kubernetes.operator.LabelConstants.MODEL_IN_IMAGE_DOMAINZIP_HASH;
import static oracle.kubernetes.operator.LabelConstants.OPERATOR_VERSION;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_ROLL_START_EVENT_GENERATED;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_SUCCESS;
import static oracle.kubernetes.operator.helpers.AnnotationHelper.SHA256_ANNOTATION;
import static oracle.kubernetes.operator.helpers.CompatibilityCheck.CompatibilityScope.DOMAIN;
import static oracle.kubernetes.operator.helpers.CompatibilityCheck.CompatibilityScope.UNKNOWN;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.DOMAIN_ROLL_STARTING;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.POD_CYCLE_STARTING;

public abstract class PodStepContext extends BasePodStepContext {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String STOP_SERVER = "/weblogic-operator/scripts/stopServer.sh";
  private static final String START_SERVER = "/weblogic-operator/scripts/startServer.sh";
  private static final String LIVENESS_PROBE = "/weblogic-operator/scripts/livenessProbe.sh";

  private static final String READINESS_PATH = "/weblogic/ready";
  private static String productVersion;
  protected final ExporterContext exporterContext;

  final WlsServerConfig scan;
  @Nonnull
  private final Packet packet;
  private final WlsDomainConfig domainTopology;
  private final Step conflictStep;
  private V1Pod podModel;
  private final String miiModelSecretsHash;
  private final String miiDomainZipHash;
  private final String domainRestartVersion;
  private boolean addRestartRequiredLabel;
  private String sha256Hash;

  PodStepContext(Step conflictStep, Packet packet) {
    super(packet.getSpi(DomainPresenceInfo.class));
    this.conflictStep = conflictStep;
    domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    miiModelSecretsHash = (String)packet.get(IntrospectorConfigMapConstants.SECRETS_MD_5);
    miiDomainZipHash = (String)packet.get(IntrospectorConfigMapConstants.DOMAINZIP_HASH);
    domainRestartVersion = (String)packet.get(IntrospectorConfigMapConstants.DOMAIN_RESTART_VERSION);
    scan = (WlsServerConfig) packet.get(ProcessingConstants.SERVER_SCAN);
    this.packet = packet;
    exporterContext = createExporterContext();
  }

  private static boolean isPatchableItem(Map.Entry<String, String> entry) {
    return isCustomerItem(entry) || PATCHABLE_OPERATOR_KEYS.contains(entry.getKey());
  }

  private static final Set<String> PATCHABLE_OPERATOR_KEYS
        = Set.of(INTROSPECTION_STATE_LABEL, OPERATOR_VERSION, MODEL_IN_IMAGE_DOMAINZIP_HASH, SHA256_ANNOTATION);

  private static boolean isCustomerItem(Map.Entry<String, String> entry) {
    return !entry.getKey().startsWith("weblogic.");
  }

  static void setProductVersion(String productVersion) {
    PodStepContext.productVersion = productVersion;
  }

  void init() {
    podModel = createPodModel();
  }

  V1Pod getPodModel() {
    return podModel;
  }

  private Step getConflictStep() {
    return new ConflictStep();
  }

  ExporterContext createExporterContext() {
    return useSidecar()
        ? new SidecarExporterContext(getMonitoringExporterSpecification()) : new WebAppExporterContext();
  }

  // Use the monitoring exporter sidecar if an exporter configuration is part of the domain.
  private boolean useSidecar() {
    return getDomain().getMonitoringExporterConfiguration() != null;
  }

  abstract Map<String, String> getPodLabels();

  abstract Map<String, String> getPodAnnotations();

  private String getNamespace() {
    return info.getNamespace();
  }

  private int getNumIntrospectorConfigMaps() {
    return Optional.ofNullable(getSpecifiedNumConfigMaps()).orElse(1);
  }

  private Integer getSpecifiedNumConfigMaps() {
    return Optional.ofNullable(packet.<String>getValue(NUM_CONFIG_MAPS)).map(this::parseOrNull).orElse(null);
  }

  private Integer parseOrNull(String count) {
    try {
      return Integer.parseInt(count);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  // ------------------------ data methods ----------------------------

  String getDomainUid() {
    return getDomain().getDomainUid();
  }

  Domain getDomain() {
    return info.getDomain();
  }

  private String getDomainName() {
    return domainTopology.getName();
  }

  private DomainSourceType getDomainHomeSourceType() {
    return getDomain().getDomainHomeSourceType();
  }

  String getPodName() {
    return LegalNames.toPodName(getDomainUid(), getServerName());
  }

  String getAsName() {
    return domainTopology.getAdminServerName();
  }

  Integer getAsPort() {
    return domainTopology
        .getServerConfig(domainTopology.getAdminServerName())
        .getLocalAdminProtocolChannelPort();
  }

  MonitoringExporterSpecification getMonitoringExporterSpecification() {
    return getDomain().getMonitoringExporterSpecification();
  }

  /**
   * Check if the server is listening on a secure port. NOTE: If the targeted server is a managed
   * server, this method is overridden to check if the managed server has a secure listen port rather
   * than the admin server. See PodHelper.ManagedPodStepContext
   *
   * @return true if server is listening on a secure port
   */
  boolean isLocalAdminProtocolChannelSecure() {
    return domainTopology
        .getServerConfig(getServerName())
        .isLocalAdminProtocolChannelSecure();
  }

  /**
   * Check if the admin server is listening on a secure port.
   *
   * @return true if admin server is listening on a secure port
   */
  private boolean isAdminServerProtocolChannelSecure() {
    return domainTopology
        .getServerConfig(domainTopology.getAdminServerName())
        .isLocalAdminProtocolChannelSecure();
  }


  Integer getLocalAdminProtocolChannelPort() {
    return domainTopology
        .getServerConfig(domainTopology.getAdminServerName())
        .getLocalAdminProtocolChannelPort();
  }

  private String getDataHome() {
    String dataHome = getDomain().getDataHome();
    return dataHome != null && !dataHome.isEmpty() ? dataHome + File.separator + getDomainUid() : null;
  }

  private String getEffectiveLogHome() {
    return getDomain().getEffectiveLogHome();
  }

  private String isIncludeServerOutInPodLog() {
    return Boolean.toString(getDomain().isIncludeServerOutInPodLog());
  }

  private String getRuntimeEncryptionSecret() {
    return getDomain().getRuntimeEncryptionSecret();
  }

  List<V1ContainerPort> getContainerPorts() {
    List<V1ContainerPort> ports = new ArrayList<>();
    getNetworkAccessPoints(scan).forEach(nap -> addContainerPort(ports, nap));

    if (!getDomain().isIstioEnabled()) { // if Istio enabled, the following were added to the NAPs by introspection.
      addContainerPort(ports, "default", getListenPort(), "TCP");
      addContainerPort(ports, "default-secure", getSslListenPort(), "TCP");
      addContainerPort(ports, "default-admin", getAdminPort(), "TCP");
    }

    return ports;
  }

  List<NetworkAccessPoint> getNetworkAccessPoints(WlsServerConfig config) {
    return Optional.ofNullable(config).map(WlsServerConfig::getNetworkAccessPoints).orElse(Collections.emptyList());
  }

  private boolean isSipProtocol(NetworkAccessPoint nap) {
    return "sip".equals(nap.getProtocol()) || "sips".equals(nap.getProtocol());
  }

  private void addContainerPort(List<V1ContainerPort> ports, NetworkAccessPoint nap) {
    String name = LegalNames.toDns1123LegalName(nap.getName());
    addContainerPort(ports, name, nap.getListenPort(), "TCP");

    if (isSipProtocol(nap)) {
      addContainerPort(ports, "udp-" + name, nap.getListenPort(), "UDP");
    }
  }

  private void addContainerPort(List<V1ContainerPort> ports, String name,
                                @Nullable Integer listenPort, String protocol) {
    if (listenPort != null) {
      ports.add(new V1ContainerPort().name(name).containerPort(listenPort).protocol(protocol));
    }
  }

  Integer getListenPort() {
    return Optional.ofNullable(scan).map(WlsServerConfig::getListenPort).orElse(null);
  }

  Integer getSslListenPort() {
    return Optional.ofNullable(scan).map(WlsServerConfig::getSslListenPort).orElse(null);
  }

  Integer getAdminPort() {
    return Optional.ofNullable(scan).map(WlsServerConfig::getAdminPort).orElse(null);
  }

  abstract Integer getOldMetricsPort();

  abstract String getServerName();

  String getClusterName() {
    return null;
  }

  // Prevent the watcher from recreating pod with old spec
  private void markBeingDeleted() {
    info.setServerPodBeingDeleted(getServerName(), Boolean.TRUE);
  }

  // ----------------------- step methods ------------------------------

  private void clearBeingDeleted() {
    info.setServerPodBeingDeleted(getServerName(), Boolean.FALSE);
  }

  private void setRecordedPod(V1Pod pod) {
    info.setServerPod(getServerName(), pod);
  }

  /**
   * Reads the specified pod and decides whether it must be created or replaced.
   *
   * @param next the next step to perform after the pod verification is complete.
   * @return a step to be scheduled.
   */
  Step verifyPod(Step next) {
    return Step.chain(
        DomainValidationSteps.createAdditionalDomainValidationSteps(podModel.getSpec()),
        new VerifyPodStep(next));
  }

  /**
   * Deletes the specified pod.
   *
   * @param pod the existing pod
   * @param next the next step to perform after the pod deletion is complete.
   * @return a step to be scheduled.
   */
  private Step deletePod(V1Pod pod, Step next) {
    return new CallBuilder()
        .deletePodAsync(getPodName(), getNamespace(), getDomainUid(), new V1DeleteOptions(), deleteResponse(pod, next));
  }

  /**
   * Creates the specified new pod and performs any additional needed processing.
   *
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  abstract Step createNewPod(Step next);

  /**
   * Creates the specified new pod and records it.
   *
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  Step createPod(Step next) {
    clearBeingDeleted();
    return createPodAsync(createResponse(next));
  }

  private Step createPodAsync(ResponseStep<V1Pod> response) {
    return new CallBuilder().createPodAsync(getNamespace(), getPodModel(), response);
  }

  /**
   * Creates the specified replacement pod and performs any additional needed processing.
   *
   * @param pod the existing pod
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  abstract Step replaceCurrentPod(V1Pod pod, Step next);

  /**
   * Creates the specified replacement pod and records it.
   *
   * @param next the next step to perform after the pod creation is complete.
   * @return a step to be scheduled.
   */
  private Step replacePod(Step next) {
    return createPodAsync(replaceResponse(next));
  }

  /**
   * Creates a Progressing step before an action step.
   *
   * @param actionStep the step to perform after the ProgressingStep.
   * @return a step to be scheduled.
   */
  abstract Step createProgressingStep(Step actionStep);

  private Step patchCurrentPod(V1Pod currentPod, Step next) {
    return createProgressingStep(patchPod(currentPod, next));
  }

  private Step patchPod(V1Pod currentPod, Step next) {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    KubernetesUtils.addPatches(
        patchBuilder, "/metadata/labels/", getLabels(currentPod), getNonHashedPodLabels());
    KubernetesUtils.addPatches(
        patchBuilder, "/metadata/annotations/", getAnnotations(currentPod), getNonHashedPodAnnotations());
    return new CallBuilder()
        .patchPodAsync(getPodName(), getNamespace(), getDomainUid(),
            new V1Patch(patchBuilder.build().toString()), patchResponse(next));
  }

  private Map<String, String> getNonHashedPodLabels() {
    Map<String,String> result = new HashMap<>(getPodLabels());
    Optional.ofNullable(miiDomainZipHash)
          .ifPresent(h -> result.put(MODEL_IN_IMAGE_DOMAINZIP_HASH, formatHashLabel(h)));

    Optional.ofNullable(getDomain().getSpec().getIntrospectVersion())
        .ifPresent(version -> result.put(INTROSPECTION_STATE_LABEL, version));
    Optional.ofNullable(productVersion)
          .ifPresent(productVersion -> result.put(LabelConstants.OPERATOR_VERSION, productVersion));

    if (addRestartRequiredLabel) {
      result.put(MII_UPDATED_RESTART_REQUIRED_LABEL, "true");
    }

    return result;
  }

  private Map<String, String> getNonHashedPodAnnotations() {
    Map<String,String> result = new HashMap<>(getPodAnnotations());
    result.put(SHA256_ANNOTATION, sha256Hash);

    return result;
  }

  private Map<String, String> getLabels(V1Pod pod) {
    return Optional.ofNullable(pod.getMetadata()).map(V1ObjectMeta::getLabels).orElseGet(Collections::emptyMap);
  }

  private Map<String, String> getAnnotations(V1Pod pod) {
    return Optional.ofNullable(pod.getMetadata()).map(V1ObjectMeta::getAnnotations).orElseGet(Collections::emptyMap);
  }

  private void logPodCreated() {
    LOGGER.info(getPodCreatedMessageKey(), getDomainUid(), getServerName());
  }

  private void logPodExists() {
    LOGGER.fine(getPodExistsMessageKey(), getDomainUid(), getServerName());
  }

  private void logPodPatched() {
    LOGGER.info(getPodPatchedMessageKey(), getDomainUid(), getServerName());
  }

  private void logPodReplaced() {
    LOGGER.info(getPodReplacedMessageKey(), getDomainUid(), getServerName());
  }

  protected Step createDomainRollStartEventIfNeeded(V1Pod pod, Step next) {
    if ("true".equals(packet.getValue(DOMAIN_ROLL_START_EVENT_GENERATED))) {
      return next;
    }

    String domainIncompatibility = getDomainIncompatibility(pod);
    if (haveReasonsToRoll(domainIncompatibility)) {
      return createDomainRollStartEvent(next, domainIncompatibility);
    }

    return next;
  }

  private String getDomainIncompatibility(V1Pod pod) {
    String domainIncompatibility = getReasonToRecycle(pod, DOMAIN);
    if (!haveReasonsToRoll(domainIncompatibility)
        && haveReasonsToRoll(getReasonToRecycle(pod, UNKNOWN))) {
      domainIncompatibility = ROLL_REASON_DOMAIN_RESOURCE_CHANGED;
    }

    if (!canUseNewDomainZip(pod)) {
      if (haveReasonsToRoll(domainIncompatibility)) {
        domainIncompatibility += ",\n" + ROLL_REASON_WEBLOGIC_CONFIGURATION_CHANGED;
      } else {
        domainIncompatibility = ROLL_REASON_WEBLOGIC_CONFIGURATION_CHANGED;
      }
    }
    return domainIncompatibility;
  }

  private Step createDomainRollStartEvent(Step next, String domainIncompatibility) {
    LOGGER.info(MessageKeys.DOMAIN_ROLL_STARTING, getDomainUid(), domainIncompatibility);
    packet.put(DOMAIN_ROLL_START_EVENT_GENERATED, "true");
    return Step.chain(
        EventHelper.createEventStep(
            new EventData(DOMAIN_ROLL_STARTING, domainIncompatibility.trim())),
        next);
  }

  private boolean haveReasonsToRoll(String domainIncompatibility) {
    return domainIncompatibility != null && domainIncompatibility.length() != 0;
  }

  abstract String getPodCreatedMessageKey();

  abstract String getPodExistsMessageKey();

  abstract String getPodPatchedMessageKey();

  abstract String getPodReplacedMessageKey();

  Step createCyclePodStep(V1Pod pod, Step next) {
    return new CyclePodStep(pod, next);
  }

  private boolean mustPatchPod(V1Pod currentPod) {
    return KubernetesUtils.isMissingValues(getLabels(currentPod), getNonHashedPodLabels())
        || KubernetesUtils.isMissingValues(getAnnotations(currentPod), getPodAnnotations());
  }

  private boolean canUseCurrentPod(V1Pod currentPod) {

    boolean useCurrent = hasCorrectPodHash(currentPod) && canUseNewDomainZip(currentPod);

    if (!useCurrent && AnnotationHelper.getDebugString(currentPod).length() > 0) {
      LOGGER.fine(
          MessageKeys.POD_DUMP,
          AnnotationHelper.getDebugString(currentPod),
          AnnotationHelper.getDebugString(getPodModel()));
    }

    return useCurrent;
  }

  private boolean hasCorrectPodHash(V1Pod currentPod) {
    if (!isLegacyPod(currentPod)) {
      return AnnotationHelper.getHash(getPodModel()).equals(AnnotationHelper.getHash(currentPod));
    } else {
      return canAdjustHashToMatch(currentPod, AnnotationHelper.getHash(currentPod));
    }
  }

  private boolean isLegacyPod(V1Pod currentPod) {
    return !hasLabel(currentPod, OPERATOR_VERSION);
  }

  private boolean canAdjustHashToMatch(V1Pod currentPod, String requiredHash) {
    return requiredHash.equals(adjustedHash(currentPod, this::addLegacyPrometheusAnnotationsFrom_3_0))
          || requiredHash.equals(adjustedHash(currentPod, this::addLegacyPrometheusAnnotationsFrom_3_1));
  }

  private boolean hasLabel(V1Pod pod, String key) {
    return pod.getMetadata().getLabels().containsKey(key);
  }

  private String adjustedHash(V1Pod currentPod, Consumer<V1Pod> prometheusAdjustment) {
    V1Pod recipe = createPodRecipe();
    prometheusAdjustment.accept(recipe);

    if (isLegacyMiiPod(currentPod)) {
      copyLabel(currentPod, recipe, MODEL_IN_IMAGE_DOMAINZIP_HASH);
    }

    return AnnotationHelper.createHash(recipe);
  }

  private void addLegacyPrometheusAnnotationsFrom_3_0(V1Pod pod) {
    AnnotationHelper.annotateForPrometheus(pod.getMetadata(), "/wls-exporter", getOldMetricsPort());
  }

  private void addLegacyPrometheusAnnotationsFrom_3_1(V1Pod pod) {
    AnnotationHelper.annotateForPrometheus(pod.getMetadata(), "/wls-exporter", getMetricsPort());
  }

  private Integer getMetricsPort() {
    return getListenPort() != null ? getListenPort() : getSslListenPort();
  }

  private boolean isLegacyMiiPod(V1Pod currentPod) {
    return hasLabel(currentPod, MODEL_IN_IMAGE_DOMAINZIP_HASH);
  }

  private void copyLabel(V1Pod fromPod, V1Pod toPod, String key) {
    setLabel(toPod, key, getLabel(fromPod, key));
  }

  private void setLabel(V1Pod currentPod, String key, String value) {
    currentPod.getMetadata().putLabelsItem(key, value);
  }

  private String getLabel(V1Pod currentPod, String key) {
    return currentPod.getMetadata().getLabels().get(key);
  }

  private V1Pod withLegacyDomainHash(V1Pod pod, String oldDomainHash) {
    pod.getMetadata().putLabelsItem(MODEL_IN_IMAGE_DOMAINZIP_HASH, oldDomainHash);
    return pod;
  }

  protected boolean canUseNewDomainZip(V1Pod currentPod) {
    String dynamicUpdateResult = packet.getValue(MII_DYNAMIC_UPDATE);

    if (miiDomainZipHash == null || isDomainZipUnchanged(currentPod)) {
      return true;
    } else if (dynamicUpdateResult == null || !getDomain().isUseOnlineUpdate()) {
      return false;
    } else if (dynamicUpdateResult.equals(MII_DYNAMIC_UPDATE_SUCCESS)) {
      return true;
    } else if (getDomain().getMiiNonDynamicChangesMethod() == MIINonDynamicChangesMethod.CommitUpdateOnly) {
      addRestartRequiredLabel = true;
      return true;
    } else {
      return false;
    }
  }

  private boolean isDomainZipUnchanged(V1Pod currentPod) {
    return formatHashLabel(miiDomainZipHash).equals(getLabel(currentPod, MODEL_IN_IMAGE_DOMAINZIP_HASH));
  }

  protected String getReasonToRecycle(V1Pod currentPod, CompatibilityScope scope) {
    PodCompatibility compatibility = new PodCompatibility(getPodModel(), currentPod);
    return compatibility.getScopedIncompatibility(scope);
  }

  private ResponseStep<V1Pod> createResponse(Step next) {
    return new CreateResponseStep(next);
  }

  private ResponseStep<Object> deleteResponse(V1Pod pod, Step next) {
    return new DeleteResponseStep(pod, next);
  }

  private ResponseStep<V1Pod> replaceResponse(Step next) {
    return new ReplacePodResponseStep(next);
  }

  private ResponseStep<V1Pod> patchResponse(Step next) {
    return new PatchPodResponseStep(next);
  }

  V1Pod createPodModel() {
    final V1Pod podRecipe = createPodRecipe();
    sha256Hash = AnnotationHelper.createHash(podRecipe);
    return withNonHashedElements(podRecipe);
  }

  @Override
  protected Map<String, String> augmentSubVars(Map<String, String> vars) {
    String clusterName = getClusterName();
    if (clusterName != null) {
      vars.put("CLUSTER_NAME", clusterName);
    }
    return vars;
  }

  V1Pod withNonHashedElements(V1Pod pod) {
    V1ObjectMeta metadata = Objects.requireNonNull(pod.getMetadata());
    // Adds labels and annotations to a pod, skipping any whose names begin with "weblogic."
    getNonHashedPodLabels().entrySet().stream()
        .filter(PodStepContext::isPatchableItem)
        .forEach(e -> metadata.putLabelsItem(e.getKey(), e.getValue()));
    getNonHashedPodAnnotations().entrySet().stream()
        .filter(PodStepContext::isPatchableItem)
        .forEach(e -> metadata.putAnnotationsItem(e.getKey(), e.getValue()));

    setTerminationGracePeriod(pod);
    getContainer(pod).map(V1Container::getEnv).ifPresent(this::updateEnv);

    updateForOwnerReference(metadata);

    // Add prometheus annotations. This will overwrite any custom annotations with same name.
    // Prometheus does not support "prometheus.io/scheme".  The scheme(http/https) can be set
    // in the Prometheus Chart values yaml under the "extraScrapeConfigs:" section.
    if (exporterContext.isEnabled()) {
      AnnotationHelper.annotateForPrometheus(metadata, exporterContext.getBasePath(), exporterContext.getPort());
    }

    return updateForDeepSubstitution(pod.getSpec(), pod);
  }

  private void setTerminationGracePeriod(V1Pod pod) {
    Objects.requireNonNull(pod.getSpec()).terminationGracePeriodSeconds(getTerminationGracePeriodSeconds());
  }

  private long getTerminationGracePeriodSeconds() {
    return getShutdownSpec().getTimeoutSeconds() + PodHelper.DEFAULT_ADDITIONAL_DELETE_TIME;
  }

  private void updateEnv(List<V1EnvVar> env) {
    updateEnvForShutdown(env);
    updateEnvForStartupMode(env);
    defineConfigOverride(env);
    updateEnvWithDomainSourceType(env);
  }

  private void updateEnvWithDomainSourceType(List<V1EnvVar> env) {
    addDefaultEnvVarIfMissing(env, IntrospectorJobEnvVars.DOMAIN_SOURCE_TYPE, getDomainHomeSourceType().toString());
  }

  private void updateEnvForShutdown(List<V1EnvVar> env) {
    if (scan != null) {
      Integer localAdminPort = scan.getLocalAdminProtocolChannelPort();
      addOrReplaceEnvVar(env, "LOCAL_ADMIN_PORT", String.valueOf(localAdminPort));
      addOrReplaceEnvVar(env, "LOCAL_ADMIN_PROTOCOL", localAdminPort.equals(scan.getListenPort()) ? "t3" : "t3s");
    }

    Shutdown shutdown = getShutdownSpec();
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_TYPE", shutdown.getShutdownType());
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_TIMEOUT", String.valueOf(shutdown.getTimeoutSeconds()));
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_IGNORE_SESSIONS", String.valueOf(shutdown.getIgnoreSessions()));
  }

  private Shutdown getShutdownSpec() {
    return Optional.ofNullable(getServerSpec()).map(ServerSpec::getShutdown).orElse(new Shutdown());
  }

  private void updateEnvForStartupMode(List<V1EnvVar> env) {
    Optional.ofNullable(getServerSpec())
          .map(ServerSpec::getDesiredState)
          .filter(this::isNotRunning)
          .ifPresent(s -> addDefaultEnvVarIfMissing(env, "STARTUP_MODE", s));
    Optional.ofNullable(getDomain().getLivenessProbeCustomScript())
          .ifPresent(s -> addDefaultEnvVarIfMissing(env, "LIVENESS_PROBE_CUSTOM_SCRIPT", s));
  }

  private boolean isNotRunning(String desiredState) {
    return !WebLogicConstants.RUNNING_STATE.equals(desiredState);
  }

  private void defineConfigOverride(List<V1EnvVar> env) {
    if (distributeOverridesDynamically()) {
      addDefaultEnvVarIfMissing(env, ServerEnvVars.DYNAMIC_CONFIG_OVERRIDE, "true");
    }
  }

  // Creates a pod model containing elements which are not patchable.
  private V1Pod createPodRecipe() {
    return new V1Pod().metadata(createMetadata()).spec(createSpec(TuningParameters.getInstance()));
  }

  protected V1ObjectMeta createMetadata() {
    final V1ObjectMeta metadata = new V1ObjectMeta().name(getPodName()).namespace(getNamespace());

    LOGGER.finest("PodStepContext.createMetaData domainRestartVersion from INIT "
        + domainRestartVersion);
    LOGGER.finest("PodStepContext.createMetaData domainRestartVersion from serverspec "
        + getServerSpec().getDomainRestartVersion());
    LOGGER.finest("PodStepContext.createMetaData domainIntrospectVersion from spec "
        + getDomain().getIntrospectVersion());

    metadata
        .putLabelsItem(LabelConstants.DOMAINUID_LABEL, getDomainUid())
        .putLabelsItem(LabelConstants.DOMAINNAME_LABEL, getDomainName())
        .putLabelsItem(LabelConstants.SERVERNAME_LABEL, getServerName())
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
        .putLabelsItem(
            LabelConstants.DOMAINRESTARTVERSION_LABEL, getServerSpec().getDomainRestartVersion())
        .putLabelsItem(
            LabelConstants.CLUSTERRESTARTVERSION_LABEL, getServerSpec().getClusterRestartVersion())
        .putLabelsItem(
            LabelConstants.SERVERRESTARTVERSION_LABEL, getServerSpec().getServerRestartVersion());

    Optional.ofNullable(miiModelSecretsHash)
          .ifPresent(hash -> addHashLabel(metadata, LabelConstants.MODEL_IN_IMAGE_MODEL_SECRETS_HASH, hash));

    return metadata;
  }

  private void addHashLabel(V1ObjectMeta metadata, String label, String hash) {
    metadata.putLabelsItem(label, formatHashLabel(hash));
  }

  private static String formatHashLabel(String hash) {
    return String.format("md5.%s.md5", hash.replace("\n", ""));
  }


  protected V1PodSpec createSpec(TuningParameters tuningParameters) {
    V1PodSpec podSpec = createPodSpec(tuningParameters)
        .readinessGates(getReadinessGates())
        .initContainers(getInitContainers(tuningParameters));

    for (V1Volume additionalVolume : getVolumes(getDomainUid())) {
      podSpec.addVolumesItem(additionalVolume);
    }
    addEmptyDirVolume(podSpec, info.getDomain().getCommonMountVolumes());
    return podSpec;
  }

  private List<V1Container> getInitContainers(TuningParameters tuningParameters) {
    List<V1Container> initContainers = new ArrayList<>();
    Optional.ofNullable(getServerSpec().getCommonMounts()).ifPresent(commonMounts ->
            getCommonMountInitContainers(commonMounts, initContainers));
    initContainers.addAll(getServerSpec().getInitContainers().stream()
            .map(c -> c.env(createEnv(c, tuningParameters))).collect(Collectors.toList()));
    return initContainers;
  }

  protected void getCommonMountInitContainers(List<CommonMount> commonMountList, List<V1Container> initContainers) {
    Optional.ofNullable(commonMountList).ifPresent(cl -> IntStream.range(0, cl.size()).forEach(idx ->
            initContainers.add(createInitContainerForCommonMount(cl.get(idx), idx))));
  }

  private List<V1EnvVar> createEnv(V1Container c, TuningParameters tuningParameters) {
    List<V1EnvVar> initContainerEnvVars = new ArrayList<>();
    Optional.ofNullable(c.getEnv()).ifPresent(initContainerEnvVars::addAll);
    getEnvironmentVariables(tuningParameters).forEach(envVar ->
            addIfMissing(initContainerEnvVars, envVar.getName(), envVar.getValue(), envVar.getValueFrom()));
    return initContainerEnvVars;
  }

  // ---------------------- model methods ------------------------------

  private List<V1PodReadinessGate> getReadinessGates() {
    List<V1PodReadinessGate> readinessGates = getServerSpec().getReadinessGates();
    return readinessGates.isEmpty() ? null : readinessGates;
  }

  private List<V1Volume> getVolumes(String domainUid) {
    List<V1Volume> volumes = PodDefaults.getStandardVolumes(domainUid, getNumIntrospectorConfigMaps());
    volumes.addAll(getServerSpec().getAdditionalVolumes());
    if (getDomainHomeSourceType() == DomainSourceType.FromModel) {
      volumes.add(createRuntimeEncryptionSecretVolume());
    }

    return volumes;
  }

  protected V1Container createPrimaryContainer(TuningParameters tuningParameters) {
    V1Container v1Container = super.createPrimaryContainer(tuningParameters)
            .ports(getContainerPorts())
            .lifecycle(createLifecycle())
            .livenessProbe(createLivenessProbe(tuningParameters.getPodTuning()));

    if (!mockWls()) {
      v1Container.readinessProbe(createReadinessProbe(tuningParameters.getPodTuning()));
    }

    for (V1VolumeMount additionalVolumeMount : getVolumeMounts()) {
      v1Container.addVolumeMountsItem(additionalVolumeMount);
    }
    Optional.ofNullable(getServerSpec().getCommonMounts()).ifPresent(commonMounts ->
            commonMounts.forEach(cm -> addVolumeMount(v1Container, cm)));
    return v1Container;
  }

  private V1VolumeMount createRuntimeEncryptionSecretVolumeMount() {
    return new V1VolumeMount().name(RUNTIME_ENCRYPTION_SECRET_VOLUME)
        .mountPath(RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH).readOnly(true);
  }

  protected String getContainerName() {
    return KubernetesConstants.CONTAINER_NAME;
  }

  protected List<String> getContainerCommand() {
    return Collections.singletonList(START_SERVER);
  }

  protected List<V1Container> getContainers() {
    List<V1Container> containers = new ArrayList<>(getServerSpec().getContainers());
    exporterContext.addContainer(containers);
    return containers;
  }

  private List<V1VolumeMount> getVolumeMounts() {
    List<V1VolumeMount> mounts = PodDefaults.getStandardVolumeMounts(getDomainUid(), getNumIntrospectorConfigMaps());
    mounts.addAll(getServerSpec().getAdditionalVolumeMounts());
    if (getDomainHomeSourceType() == DomainSourceType.FromModel) {
      mounts.add(createRuntimeEncryptionSecretVolumeMount());
    }
    return mounts;
  }

  private V1Volume createRuntimeEncryptionSecretVolume() {
    return new V1Volume()
        .name(RUNTIME_ENCRYPTION_SECRET_VOLUME)
        .secret(getRuntimeEncryptionSecretVolumeSource(getRuntimeEncryptionSecret()));
  }

  private V1SecretVolumeSource getRuntimeEncryptionSecretVolumeSource(String name) {
    return new V1SecretVolumeSource().secretName(name).defaultMode(420);
  }

  /**
   * Sets the environment variables used by operator/src/main/resources/scripts/startServer.sh
   * @param vars a list to which new variables are to be added
   */
  void addStartupEnvVars(List<V1EnvVar> vars) {
    addEnvVar(vars, ServerEnvVars.DOMAIN_NAME, getDomainName());
    addEnvVar(vars, ServerEnvVars.DOMAIN_HOME, getDomainHome());
    addEnvVar(vars, ServerEnvVars.ADMIN_NAME, getAsName());
    addEnvVar(vars, ServerEnvVars.ADMIN_PORT, getAsPort().toString());
    addEnvVarIfTrue(isLocalAdminProtocolChannelSecure(), vars, "ADMIN_PORT_SECURE");
    addEnvVarIfTrue(isAdminServerProtocolChannelSecure(), vars, ServerEnvVars.ADMIN_SERVER_PORT_SECURE);
    addEnvVar(vars, ServerEnvVars.SERVER_NAME, getServerName());
    addEnvVar(vars, ServerEnvVars.DOMAIN_UID, getDomainUid());
    addEnvVar(vars, ServerEnvVars.NODEMGR_HOME, NODEMGR_HOME);
    addEnvVar(vars, ServerEnvVars.LOG_HOME, getEffectiveLogHome());
    addEnvVar(vars, ServerEnvVars.SERVER_OUT_IN_POD_LOG, isIncludeServerOutInPodLog());
    addEnvVar(vars, ServerEnvVars.SERVICE_NAME, LegalNames.toServerServiceName(getDomainUid(), getServerName()));
    addEnvVar(vars, ServerEnvVars.AS_SERVICE_NAME, LegalNames.toServerServiceName(getDomainUid(), getAsName()));
    Optional.ofNullable(getDataHome()).ifPresent(v -> addEnvVar(vars, ServerEnvVars.DATA_HOME, v));
    Optional.ofNullable(getServerSpec().getCommonMounts()).ifPresent(cm -> addCommonMountEnv(cm, vars));
    addEnvVarIfTrue(mockWls(), vars, "MOCK_WLS");
  }

  protected void addCommonMountEnv(List<CommonMount> commonMountList, List<V1EnvVar> vars) {
    Optional.ofNullable(commonMountList).ifPresent(commonMounts -> {
      addEnvVar(vars, IntrospectorJobEnvVars.WDT_INSTALL_HOME, getWdtInstallHome());
      addEnvVar(vars, IntrospectorJobEnvVars.WDT_MODEL_HOME, getModelHome());
      Optional.ofNullable(getCommonMountPaths(commonMountList, getDomain().getCommonMountVolumes()))
              .ifPresent(c -> addEnvVar(vars, CommonMountEnvVars.COMMON_MOUNT_PATHS, c));
    });
  }

  private String getDomainHome() {
    return getDomain().getDomainHome();
  }

  private String getWdtInstallHome() {
    return getDomain().getWdtInstallHome();
  }

  private String getModelHome() {
    return getDomain().getModelHome();
  }

  private boolean distributeOverridesDynamically() {
    return getDomain().distributeOverridesDynamically();
  }

  private V1Lifecycle createLifecycle() {
    return new V1Lifecycle().preStop(handler(STOP_SERVER));
  }

  private V1Handler handler(String... commandItems) {
    return new V1Handler().exec(execAction(commandItems));
  }

  private V1ExecAction execAction(String... commandItems) {
    return new V1ExecAction().command(Arrays.asList(commandItems));
  }

  private V1Probe createReadinessProbe(TuningParameters.PodTuning tuning) {
    V1Probe readinessProbe = new V1Probe();
    readinessProbe
        .initialDelaySeconds(getReadinessProbeInitialDelaySeconds(tuning))
        .timeoutSeconds(getReadinessProbeTimeoutSeconds(tuning))
        .periodSeconds(getReadinessProbePeriodSeconds(tuning))
        .failureThreshold(FAILURE_THRESHOLD);
    try {
      boolean istioEnabled = getDomain().isIstioEnabled();
      if (istioEnabled) {
        int istioReadinessPort = getDomain().getIstioReadinessPort();
        readinessProbe =
            readinessProbe.httpGet(httpGetAction(READINESS_PATH, istioReadinessPort, false));
      } else {
        readinessProbe =
            readinessProbe.httpGet(
                httpGetAction(
                    READINESS_PATH,
                    getLocalAdminProtocolChannelPort(),
                    isLocalAdminProtocolChannelSecure()));
      }
    } catch (Exception e) {
      // do nothing
    }
    return readinessProbe;
  }

  @SuppressWarnings("SameParameterValue")
  private V1HTTPGetAction httpGetAction(String path, int port, boolean useHttps) {
    V1HTTPGetAction getAction = new V1HTTPGetAction();
    getAction.path(path).port(new IntOrString(port));
    if (useHttps) {
      getAction.scheme("HTTPS");
    }
    return getAction;
  }

  private int getReadinessProbePeriodSeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getPeriodSeconds())
        .orElse(tuning.readinessProbePeriodSeconds);
  }

  private int getReadinessProbeTimeoutSeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getTimeoutSeconds())
        .orElse(tuning.readinessProbeTimeoutSeconds);
  }

  private int getReadinessProbeInitialDelaySeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getInitialDelaySeconds())
        .orElse(tuning.readinessProbeInitialDelaySeconds);
  }

  private V1Probe createLivenessProbe(TuningParameters.PodTuning tuning) {
    return new V1Probe()
        .initialDelaySeconds(getLivenessProbeInitialDelaySeconds(tuning))
        .timeoutSeconds(getLivenessProbeTimeoutSeconds(tuning))
        .periodSeconds(getLivenessProbePeriodSeconds(tuning))
        .failureThreshold(FAILURE_THRESHOLD)
        .exec(execAction(LIVENESS_PROBE));
  }

  private int getLivenessProbeInitialDelaySeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getInitialDelaySeconds())
        .orElse(tuning.livenessProbeInitialDelaySeconds);
  }

  private int getLivenessProbeTimeoutSeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getTimeoutSeconds())
        .orElse(tuning.livenessProbeTimeoutSeconds);
  }

  private int getLivenessProbePeriodSeconds(TuningParameters.PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getPeriodSeconds())
        .orElse(tuning.livenessProbePeriodSeconds);
  }

  private boolean mockWls() {
    return Boolean.getBoolean("mockWLS");
  }

  private abstract class BaseStep extends Step {
    BaseStep() {
      this(null);
    }

    BaseStep(Step next) {
      super(next);
    }

    protected String getDetail() {
      return getServerName();
    }
  }

  private class ConflictStep extends BaseStep {

    @Override
    public NextAction apply(Packet packet) {
      return doNext(getConflictStep(), packet);
    }

    @Override
    public boolean equals(Object other) {
      if (other == this) {
        return true;
      }
      if (!(other instanceof ConflictStep)) {
        return false;
      }
      ConflictStep rhs = ((ConflictStep) other);
      return new EqualsBuilder().append(conflictStep, rhs.getConflictStep()).isEquals();
    }

    private Step getConflictStep() {
      return conflictStep;
    }
  }

  public class CyclePodStep extends BaseStep {
    private final V1Pod pod;

    CyclePodStep(V1Pod pod, Step next) {
      super(next);
      this.pod = pod;
    }

    @Override
    public NextAction apply(Packet packet) {

      markBeingDeleted();
      return doNext(createCyclePodEventStep(deletePod(pod, getNext())), packet);
    }

    private Step createCyclePodEventStep(Step next) {
      String reason = getReasonToRecycle(pod, CompatibilityScope.POD);
      LOGGER.info(
          MessageKeys.CYCLING_POD,
          Objects.requireNonNull(pod.getMetadata()).getName(),
          reason);
      return Step.chain(EventHelper.createEventStep(new EventData(POD_CYCLE_STARTING, reason).podName(getPodName())),
          next);
    }
  }

  private class VerifyPodStep extends BaseStep {

    VerifyPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      V1Pod currentPod = info.getServerPod(getServerName());
      // reset introspect failure job count - if any
      Optional.ofNullable(packet.getSpi(DomainPresenceInfo.class))
          .map(DomainPresenceInfo::getDomain)
          .map(Domain::getStatus)
          .ifPresent(DomainStatus::resetIntrospectJobFailureCount);

      if (currentPod == null) {
        return doNext(createNewPod(getNext()), packet);
      } else if (!canUseCurrentPod(currentPod)) {
        return doNext(replaceCurrentPod(currentPod, getNext()), packet);
      } else if (mustPatchPod(currentPod)) {
        return doNext(patchCurrentPod(currentPod, getNext()), packet);
      } else {
        logPodExists();
        return doNext(packet);
      }
    }
  }

  private abstract class BaseResponseStep extends ResponseStep<V1Pod> {
    BaseResponseStep(Step next) {
      super(next);
    }

    protected String getDetail() {
      return getServerName();
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Pod> callResponse) {
      if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
        return updateDomainStatus(packet, callResponse);
      } else {
        return onFailure(getConflictStep(), packet, callResponse);
      }
    }

    private NextAction updateDomainStatus(Packet packet, CallResponse<V1Pod> callResponse) {
      return doNext(DomainStatusUpdater.createFailureRelatedSteps(callResponse, null), packet);
    }
  }

  private class CreateResponseStep extends BaseResponseStep {

    CreateResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
      logPodCreated();
      if (callResponse.getResult() != null) {
        info.updateLastKnownServerStatus(getServerName(), WebLogicConstants.STARTING_STATE);
        setRecordedPod(callResponse.getResult());
      }

      boolean waitForPodReady =
          (boolean) Optional.ofNullable(packet.get(ProcessingConstants.WAIT_FOR_POD_READY)).orElse(false);

      if (waitForPodReady) {
        PodAwaiterStepFactory pw = packet.getSpi(PodAwaiterStepFactory.class);
        return doNext(pw.waitForReady(callResponse.getResult(), getNext()), packet);
      }
      return doNext(packet);
    }
  }

  private class DeleteResponseStep extends ResponseStep<Object> {
    private final V1Pod pod;

    DeleteResponseStep(V1Pod pod, Step next) {
      super(next);
      this.pod = pod;
    }

    protected String getDetail() {
      return getServerName();
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<Object> callResponses) {
      if (callResponses.getStatusCode() == CallBuilder.NOT_FOUND) {
        return onSuccess(packet, callResponses);
      }
      return super.onFailure(getConflictStep(), packet, callResponses);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<Object> callResponses) {
      PodAwaiterStepFactory pw = packet.getSpi(PodAwaiterStepFactory.class);
      return doNext(pw.waitForDelete(pod, replacePod(getNext())), packet);
    }
  }

  private class ReplacePodResponseStep extends PatchPodResponseStep {

    ReplacePodResponseStep(Step next) {
      super(next);
    }

    @Override
    void logPodChanged() {
      logPodReplaced();
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
      return doNext(
          packet.getSpi(PodAwaiterStepFactory.class).waitForReady(processResponse(callResponse), getNext()),
          packet);
    }
  }

  private class PatchPodResponseStep extends BaseResponseStep {

    PatchPodResponseStep(Step next) {
      super(next);
    }

    void logPodChanged() {
      logPodPatched();
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
      processResponse(callResponse);
      return doNext(getNext(), packet);
    }

    protected V1Pod processResponse(CallResponse<V1Pod> callResponse) {
      V1Pod newPod = callResponse.getResult();
      logPodChanged();
      if (newPod != null) {
        setRecordedPod(newPod);
      }
      return newPod;
    }
  }

  abstract class ExporterContext {
    int getWebLogicRestPort() {
      return scan.getLocalAdminProtocolChannelPort();
    }

    boolean isWebLogicSecure() {
      return !Objects.equals(getWebLogicRestPort(), getListenPort());
    }

    abstract boolean isEnabled();

    abstract int getPort();

    abstract String getBasePath();

    abstract void addContainer(List<V1Container> containers);
  }

  class WebAppExporterContext extends ExporterContext {

    @Override
    boolean isEnabled() {
      return getListenPort() != null;
    }

    @Override
    int getPort() {
      return getListenPort();
    }

    @Override
    String getBasePath() {
      return "/wls-exporter";
    }

    @Override
    void addContainer(List<V1Container> containers) {
      // do nothing
    }
  }

  class SidecarExporterContext extends ExporterContext {
    private final int metricsPort;

    public SidecarExporterContext(MonitoringExporterSpecification specification) {
      metricsPort = specification.getRestPort();
    }

    @Override
    boolean isEnabled() {
      return true;
    }

    @Override
    int getPort() {
      return metricsPort;
    }

    @Override
    String getBasePath() {
      return "";
    }

    @Override
    void addContainer(List<V1Container> containers) {
      containers.add(createMonitoringExporterContainer());
    }

    private V1Container createMonitoringExporterContainer() {
      return new V1Container()
            .name(EXPORTER_CONTAINER_NAME)
            .image(getDomain().getMonitoringExporterImage())
            .imagePullPolicy(getDomain().getMonitoringExporterImagePullPolicy())
            .addEnvItem(new V1EnvVar().name("JAVA_OPTS").value(createJavaOptions()))
            .addPortsItem(new V1ContainerPort()
                .name(getMetricsPortName()).protocol("TCP").containerPort(getPort()));
    }

    private String getMetricsPortName() {
      return getDomain().isIstioEnabled() ? "tcp-metrics" : "metrics";
    }

    private String createJavaOptions() {
      final List<String> args = new ArrayList<>();
      args.add("-DDOMAIN=" + getDomainUid());
      args.add("-DWLS_PORT=" + getWebLogicRestPort());
      if (isWebLogicSecure()) {
        args.add("-DWLS_SECURE=true");
      }
      if (metricsPort != DEFAULT_EXPORTER_SIDECAR_PORT) {
        args.add("-DEXPORTER_PORT=" + metricsPort);
      }

      return String.join(" ", args);
    }
  }
}

// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerBuilder;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ExecAction;
import io.kubernetes.client.openapi.models.V1HTTPGetAction;
import io.kubernetes.client.openapi.models.V1Lifecycle;
import io.kubernetes.client.openapi.models.V1LifecycleHandler;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodReadinessGate;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodSpecBuilder;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Yaml;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.DomainSourceType;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.LogHomeLayoutType;
import oracle.kubernetes.operator.MIINonDynamicChangesMethod;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.tuning.PodTuning;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.AuxiliaryImage;
import oracle.kubernetes.weblogic.domain.model.ClusterResource;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.IntrospectorJobEnvVars;
import oracle.kubernetes.weblogic.domain.model.MonitoringExporterSpecification;
import oracle.kubernetes.weblogic.domain.model.ServerEnvVars;
import oracle.kubernetes.weblogic.domain.model.Shutdown;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_VOLUME_NAME_OLD_PREFIX;
import static oracle.kubernetes.common.AuxiliaryImageConstants.AUXILIARY_IMAGE_VOLUME_NAME_PREFIX;
import static oracle.kubernetes.common.CommonConstants.COMPATIBILITY_MODE;
import static oracle.kubernetes.common.helpers.AuxiliaryImageEnvVars.AUXILIARY_IMAGE_MOUNT_PATH;
import static oracle.kubernetes.common.logging.MessageKeys.CYCLING_POD_EVICTED;
import static oracle.kubernetes.common.logging.MessageKeys.CYCLING_POD_SPEC_CHANGED;
import static oracle.kubernetes.operator.DomainStatusUpdater.createKubernetesFailureSteps;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.NUM_CONFIG_MAPS;
import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_SIDECAR_PORT;
import static oracle.kubernetes.operator.KubernetesConstants.EXPORTER_CONTAINER_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.LabelConstants.CLUSTER_OBSERVED_GENERATION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.DOMAIN_OBSERVED_GENERATION_LABEL;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.LabelConstants.MII_UPDATED_RESTART_REQUIRED_LABEL;
import static oracle.kubernetes.operator.LabelConstants.MODEL_IN_IMAGE_DOMAINZIP_HASH;
import static oracle.kubernetes.operator.LabelConstants.OPERATOR_VERSION;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE;
import static oracle.kubernetes.operator.ProcessingConstants.MII_DYNAMIC_UPDATE_SUCCESS;
import static oracle.kubernetes.operator.helpers.AffinityHelper.DOMAIN_UID_VARIABLE;
import static oracle.kubernetes.operator.helpers.AffinityHelper.getDefaultAntiAffinity;
import static oracle.kubernetes.operator.helpers.AnnotationHelper.SHA256_ANNOTATION;
import static oracle.kubernetes.operator.helpers.EventHelper.EventItem.POD_CYCLE_STARTING;
import static oracle.kubernetes.operator.helpers.FluentdHelper.addFluentdContainer;
import static oracle.kubernetes.operator.helpers.LegalNames.LEGAL_CONTAINER_PORT_NAME_MAX_LENGTH;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_WDT_INSTALL_HOME;
import static oracle.kubernetes.weblogic.domain.model.Model.DEFAULT_WDT_MODEL_HOME;

@SuppressWarnings("ConstantConditions")
public abstract class PodStepContext extends BasePodStepContext {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String STOP_SERVER = "/weblogic-operator/scripts/stopServer.sh";
  private static final String START_SERVER = "/weblogic-operator/scripts/startServer.sh";
  private static final String LIVENESS_PROBE = "/weblogic-operator/scripts/livenessProbe.sh";

  private static final String READINESS_PATH = "/weblogic/ready";
  private static final String WLS_EXPORTER = "/wls-exporter";

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
    this.packet = packet;
    domainTopology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    miiModelSecretsHash = (String)packet.get(IntrospectorConfigMapConstants.SECRETS_MD_5);
    miiDomainZipHash = (String)packet.get(IntrospectorConfigMapConstants.DOMAINZIP_HASH);
    domainRestartVersion = (String)packet.get(IntrospectorConfigMapConstants.DOMAIN_RESTART_VERSION);
    scan = (WlsServerConfig) packet.get(ProcessingConstants.SERVER_SCAN);
    exporterContext = createExporterContext();
  }

  private static boolean isPatchableItem(Map.Entry<String, String> entry) {
    return isCustomerItem(entry) || PATCHABLE_OPERATOR_KEYS.contains(entry.getKey());
  }

  private static final Set<String> PATCHABLE_OPERATOR_KEYS = Set.of(INTROSPECTION_STATE_LABEL, OPERATOR_VERSION,
      MODEL_IN_IMAGE_DOMAINZIP_HASH, SHA256_ANNOTATION, DOMAIN_OBSERVED_GENERATION_LABEL,
      CLUSTER_OBSERVED_GENERATION_LABEL);

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

  String getNamespace() {
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

  DomainResource getDomain() {
    return info.getDomain();
  }

  ClusterResource getCluster(String clusterName) {
    return info.getClusterResource(clusterName);
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

  private LogHomeLayoutType getLogHomeLayout() {
    return getDomain().getLogHomeLayout();
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

    addContainerPort(ports, "default", getListenPort(), "TCP");
    addContainerPort(ports, "default-secure", getSslListenPort(), "TCP");
    addContainerPort(ports, "default-admin", getAdminPort(), "TCP");

    return ports;
  }

  List<NetworkAccessPoint> getNetworkAccessPoints(WlsServerConfig config) {
    return Optional.ofNullable(config).map(WlsServerConfig::getNetworkAccessPoints).orElse(Collections.emptyList());
  }

  private boolean isSipProtocol(NetworkAccessPoint nap) {
    return "sip".equals(nap.getProtocol()) || "sips".equals(nap.getProtocol());
  }

  private void addContainerPort(List<V1ContainerPort> ports, NetworkAccessPoint nap) {
    String name = createContainerPortName(ports, LegalNames.toDns1123LegalName(nap.getName()));
    addContainerPort(ports, name, nap.getListenPort(), "TCP");

    if (isSipProtocol(nap)) {
      addContainerPort(ports, "udp-" + name, nap.getListenPort(), "UDP");
    }
  }

  private void addContainerPort(List<V1ContainerPort> ports, String name,
                                @Nullable Integer listenPort, String protocol) {
    if (listenPort != null) {
      String finalName = createContainerPortName(ports, name);
      // add if needed
      if (ports.stream().noneMatch(p -> p.getProtocol().equals(protocol) && p.getContainerPort().equals(listenPort)
          && Objects.equals(p.getName(), finalName))) {
        ports.add(new V1ContainerPort().name(name).containerPort(listenPort).protocol(protocol));
      }
    }
  }

  private String createContainerPortName(List<V1ContainerPort> ports, String name) {
    //Container port names can be a maximum of 15 characters in length
    if (name.length() > LEGAL_CONTAINER_PORT_NAME_MAX_LENGTH) {
      String portNamePrefix = getPortNamePrefix(name);
      // Find ports with the name having the same first 12 characters
      List<V1ContainerPort> containerPortsWithSamePrefix = ports.stream().filter(port ->
              portNamePrefix.equals(getPortNamePrefix(port.getName()))).collect(Collectors.toList());
      int index = containerPortsWithSamePrefix.size() + 1;
      String indexStr = String.valueOf(index);
      // zero fill to the left for single digit index (e.g. 01)
      if (index < 10) {
        indexStr = "0" + index;
      } else if (index >= 100) {
        LOGGER.severe(MessageKeys.ILLEGAL_NETWORK_CHANNEL_NAME_LENGTH, getDomainUid(), getServerName(),
                name, LEGAL_CONTAINER_PORT_NAME_MAX_LENGTH);
        return name;
      }
      name = portNamePrefix + "-" + indexStr;
    }
    return  name;
  }

  @Nonnull
  private String getPortNamePrefix(String name) {
    // Use first 12 characters of port name as prefix due to 15 character port name limit
    return name.length() > 12 ? name.substring(0, 12) : name;
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

  private Map<String, String> getNonHashedPodLabels() {
    Map<String,String> result = new HashMap<>(getPodLabels());
    Optional.ofNullable(miiDomainZipHash)
          .ifPresent(h -> result.put(MODEL_IN_IMAGE_DOMAINZIP_HASH, formatHashLabel(h)));

    Optional.ofNullable(getDomain().getSpec().getIntrospectVersion())
        .ifPresent(version -> result.put(INTROSPECTION_STATE_LABEL, version));
    Optional.ofNullable(productVersion)
          .ifPresent(pv -> result.put(LabelConstants.OPERATOR_VERSION, pv));
    Optional.ofNullable(getDomain().getMetadata()).map(V1ObjectMeta::getGeneration)
        .ifPresent(generation -> result.put(DOMAIN_OBSERVED_GENERATION_LABEL, String.valueOf(generation)));
    Optional.ofNullable(getCluster(getClusterName())).map(ClusterResource::getMetadata).map(V1ObjectMeta::getGeneration)
        .ifPresent(generation -> result.put(CLUSTER_OBSERVED_GENERATION_LABEL, String.valueOf(generation)));

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

  abstract String getPodCreatedMessageKey();

  abstract String getPodExistsMessageKey();

  abstract String getPodPatchedMessageKey();

  abstract String getPodReplacedMessageKey();

  Step cycleEvictedPodStep(V1Pod pod, Step next) {
    return new CyclePodStep(pod, next, LOGGER.formatMessage(CYCLING_POD_EVICTED));
  }

  Step createCyclePodStep(V1Pod pod, Step next) {
    return Step.chain(DomainStatusUpdater.createStartRollStep(),
        new CyclePodStep(pod, createCycleEndStep(next), LOGGER.formatMessage(CYCLING_POD_SPEC_CHANGED)));
  }

  abstract Step createCycleEndStep(Step next);

  private boolean isRecentOperator(String operatorVersion) {
    try {
      SemanticVersion version = new SemanticVersion(operatorVersion);
      return version.getMajor() > 3 || (version.getMajor() == 3 && version.getMinor() >= 2);
    } catch (Exception e) {
      return false;
    }
  }

  private void addLegacyPrometheusAnnotationsFrom31(V1Pod pod) {
    AnnotationHelper.annotateForPrometheus(pod.getMetadata(), WLS_EXPORTER, getMetricsPort());
  }

  private Integer getMetricsPort() {
    return getListenPort() != null ? getListenPort() : getSslListenPort();
  }

  private String getLabel(V1Pod currentPod, String key) {
    return currentPod.getMetadata().getLabels().get(key);
  }

  protected boolean canUseNewDomainZip(V1Pod currentPod) {
    String dynamicUpdateResult = packet.getValue(MII_DYNAMIC_UPDATE);

    if (miiDomainZipHash == null || isDomainZipUnchanged(currentPod)) {
      return true;
    } else if (dynamicUpdateResult == null || !getDomain().isUseOnlineUpdate()) {
      return false;
    } else if (dynamicUpdateResult.equals(MII_DYNAMIC_UPDATE_SUCCESS)) {
      return true;
    } else if (getDomain().getMiiNonDynamicChangesMethod() == MIINonDynamicChangesMethod.COMMIT_UPDATE_ONLY) {
      addRestartRequiredLabel = true;
      return true;
    } else {
      return false;
    }
  }

  private boolean isDomainZipUnchanged(V1Pod currentPod) {
    return formatHashLabel(miiDomainZipHash).equals(getLabel(currentPod, MODEL_IN_IMAGE_DOMAINZIP_HASH));
  }

  private ResponseStep<V1Pod> createResponse(Step next) {
    return new CreateResponseStep(next);
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
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_TYPE", shutdown.getShutdownType().toString());
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_TIMEOUT", String.valueOf(shutdown.getTimeoutSeconds()));
    addDefaultEnvVarIfMissing(env, "SHUTDOWN_IGNORE_SESSIONS", String.valueOf(shutdown.getIgnoreSessions()));
    if (!shutdown.getWaitForAllSessions().equals(Shutdown.DEFAULT_WAIT_FOR_ALL_SESSIONS)) {
      addDefaultEnvVarIfMissing(env, "SHUTDOWN_WAIT_FOR_ALL_SESSIONS",
              String.valueOf(shutdown.getWaitForAllSessions()));
    }
  }

  private Shutdown getShutdownSpec() {
    return Optional.ofNullable(getServerSpec()).map(EffectiveServerSpec::getShutdown).orElse(new Shutdown());
  }

  private void updateEnvForStartupMode(List<V1EnvVar> env) {
    Optional.ofNullable(getDomain().getLivenessProbeCustomScript())
          .ifPresent(s -> addDefaultEnvVarIfMissing(env, "LIVENESS_PROBE_CUSTOM_SCRIPT", s));
    Optional.ofNullable(getDomain().isReplaceVariablesInJavaOptions())
        .ifPresent(r -> addDefaultEnvVarIfMissing(env, "REPLACE_VARIABLES_IN_JAVA_OPTIONS", Boolean.toString(r)));
  }

  private void defineConfigOverride(List<V1EnvVar> env) {
    if (distributeOverridesDynamically()) {
      addDefaultEnvVarIfMissing(env, ServerEnvVars.DYNAMIC_CONFIG_OVERRIDE, "true");
    }
  }

  // Creates a pod model containing elements which are not patchable.
  private V1Pod createPodRecipe() {
    return new V1Pod().metadata(createMetadata()).spec(createSpec());
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

    Optional.ofNullable(miiModelSecretsHash).ifPresent(hash -> addHashLabel(metadata, hash));

    return metadata;
  }

  private void addHashLabel(V1ObjectMeta metadata, String hash) {
    metadata.putLabelsItem(LabelConstants.MODEL_IN_IMAGE_MODEL_SECRETS_HASH, formatHashLabel(hash));
  }

  private static String formatHashLabel(String hash) {
    return String.format("md5.%s.md5", hash.replace("\n", ""));
  }


  protected V1PodSpec createSpec() {
    V1PodSpec podSpec = createPodSpec()
        .readinessGates(getReadinessGates())
        .initContainers(getInitContainers());

    for (V1Volume additionalVolume : getVolumes(getDomainUid())) {
      podSpec.addVolumesItem(additionalVolume);
    }
    Optional.ofNullable(getAuxiliaryImages()).ifPresent(auxImages -> podSpec.addVolumesItem(createEmptyDirVolume()));
    podSpec.affinity(getAffinity());
    return podSpec;
  }

  protected V1Affinity getAffinity() {
    if (isDefaultAntiAffinity() && isNonClusteredServer()) {
      return defaultDomainUidVariableAntiAffinity();
    }
    return getServerSpec().getAffinity();
  }

  private boolean isNonClusteredServer() {
    return getClusterName() == null;
  }

  private boolean isDefaultAntiAffinity() {
    return getServerSpec().getAffinity().equals(getDefaultAntiAffinity());
  }

  private V1Affinity defaultDomainUidVariableAntiAffinity() {
    return new AffinityHelper().domainUID(DOMAIN_UID_VARIABLE).getAntiAffinity();
  }

  private List<V1Container> getInitContainers() {
    List<V1Container> initContainers = new ArrayList<>();
    Optional.ofNullable(getAuxiliaryImages()).ifPresent(auxiliaryImages ->
            getAuxiliaryImageInitContainers(auxiliaryImages, initContainers));
    initContainers.addAll(getServerSpec().getInitContainers().stream()
            .map(c -> c.env(createEnv(c)).resources(createResources()))
        .collect(Collectors.toList()));
    return initContainers;
  }

  protected void getAuxiliaryImageInitContainers(List<AuxiliaryImage> auxiliaryImageList,
                                                 List<V1Container> initContainers) {
    Optional.ofNullable(auxiliaryImageList).ifPresent(cl -> IntStream.range(0, cl.size()).forEach(idx ->
            initContainers.add(createInitContainerForAuxiliaryImage(cl.get(idx), idx))));
  }

  // ---------------------- model methods ------------------------------

  private List<V1PodReadinessGate> getReadinessGates() {
    List<V1PodReadinessGate> readinessGates = getServerSpec().getReadinessGates();
    return readinessGates.isEmpty() ? null : readinessGates;
  }

  private List<V1Volume> getVolumes(String domainUid) {
    List<V1Volume> volumes = PodDefaults.getStandardVolumes(domainUid, getNumIntrospectorConfigMaps());
    if (getDomainHomeSourceType() == DomainSourceType.FROM_MODEL) {
      volumes.add(createRuntimeEncryptionSecretVolume());
    }
    volumes.addAll(getServerSpec().getAdditionalVolumes());

    return volumes;
  }

  @Override
  protected V1Container createPrimaryContainer() {
    final PodTuning podTuning = TuningParameters.getInstance().getPodTuning();
    V1Container v1Container = super.createPrimaryContainer()
            .ports(getContainerPorts())
            .lifecycle(createLifecycle())
            .livenessProbe(createLivenessProbe(podTuning));

    if (!mockWls()) {
      v1Container.readinessProbe(createReadinessProbe(podTuning));
    }

    for (V1VolumeMount additionalVolumeMount : getVolumeMounts()) {
      v1Container.addVolumeMountsItem(additionalVolumeMount);
    }
    Optional.ofNullable(getAuxiliaryImages()).ifPresent(auxiliaryImages -> addVolumeMountIfMissing(v1Container));
    return v1Container;
  }

  private V1VolumeMount createRuntimeEncryptionSecretVolumeMount() {
    return new V1VolumeMount().name(RUNTIME_ENCRYPTION_SECRET_VOLUME)
        .mountPath(RUNTIME_ENCRYPTION_SECRET_MOUNT_PATH).readOnly(true);
  }

  protected String getContainerName() {
    return KubernetesConstants.WLS_CONTAINER_NAME;
  }

  protected List<String> getContainerCommand() {
    return Collections.singletonList(START_SERVER);
  }

  protected List<V1Container> getContainers() {
    List<V1Container> containers = new ArrayList<>(getServerSpec().getContainers());
    exporterContext.addContainer(containers);
    Optional.ofNullable(getDomain().getFluentdSpecification())
        .ifPresent(fluentd -> addFluentdContainer(fluentd, containers, getDomain(), false));
    return containers;
  }

  protected List<V1Volume> getFluentdVolumes() {
    List<V1Volume> volumes = new ArrayList<>();
    Optional.ofNullable(getDomain())
        .map(DomainResource::getFluentdSpecification)
        .ifPresent(c -> volumes.add(new V1Volume().name(FLUENTD_CONFIGMAP_VOLUME)
            .configMap(new V1ConfigMapVolumeSource()
                .name(getDomainUid() + FLUENTD_CONFIGMAP_NAME_SUFFIX)
                .defaultMode(420))));
    return volumes;
  }

  private List<V1VolumeMount> getVolumeMounts() {
    List<V1VolumeMount> mounts = PodDefaults.getStandardVolumeMounts(getDomainUid(), getNumIntrospectorConfigMaps());
    if (getDomainHomeSourceType() == DomainSourceType.FROM_MODEL) {
      mounts.add(createRuntimeEncryptionSecretVolumeMount());
    }
    mounts.addAll(getServerSpec().getAdditionalVolumeMounts());
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
    if (getLogHomeLayout() == LogHomeLayoutType.FLAT) {
      addEnvVar(vars, ServerEnvVars.LOG_HOME_LAYOUT, getLogHomeLayout().toString());
    }
    addEnvVar(vars, ServerEnvVars.SERVER_OUT_IN_POD_LOG, isIncludeServerOutInPodLog());
    addEnvVar(vars, ServerEnvVars.SERVICE_NAME, LegalNames.toServerServiceName(getDomainUid(), getServerName()));
    addEnvVar(vars, ServerEnvVars.AS_SERVICE_NAME, LegalNames.toServerServiceName(getDomainUid(), getAsName()));
    Optional.ofNullable(getDataHome()).ifPresent(v -> addEnvVar(vars, ServerEnvVars.DATA_HOME, v));
    String wdtInstallHome = getWdtInstallHome();
    if (wdtInstallHome != null && !wdtInstallHome.isEmpty() && !wdtInstallHome.equals(DEFAULT_WDT_INSTALL_HOME)) {
      addEnvVar(vars, IntrospectorJobEnvVars.WDT_INSTALL_HOME, wdtInstallHome);
    }
    String wdtModelHome = getModelHome();
    if (wdtModelHome != null && !wdtModelHome.isEmpty() && !wdtModelHome.equals(DEFAULT_WDT_MODEL_HOME)) {
      addEnvVar(vars, IntrospectorJobEnvVars.WDT_MODEL_HOME, wdtModelHome);
    }
    Optional.ofNullable(getAuxiliaryImages()).ifPresent(ais -> addAuxiliaryImageEnv(ais, vars));
    addEnvVarIfTrue(mockWls(), vars, "MOCK_WLS");
    Optional.ofNullable(getKubernetesPlatform()).ifPresent(v ->
            addEnvVar(vars, ServerEnvVars.KUBERNETES_PLATFORM, v));
  }

  protected void addAuxiliaryImageEnv(List<AuxiliaryImage> auxiliaryImageList, List<V1EnvVar> vars) {
    Optional.ofNullable(auxiliaryImageList).ifPresent(auxiliaryImages -> {
      if (!auxiliaryImages.isEmpty()) {
        addEnvVar(vars, AUXILIARY_IMAGE_MOUNT_PATH, getDomain().getAuxiliaryImageVolumeMountPath());
      }
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

  private List<AuxiliaryImage> getAuxiliaryImages() {
    return getDomain().getAuxiliaryImages();
  }

  private boolean distributeOverridesDynamically() {
    return getDomain().distributeOverridesDynamically();
  }

  private V1Lifecycle createLifecycle() {
    return new V1Lifecycle().preStop(handler(STOP_SERVER));
  }

  private V1LifecycleHandler handler(String... commandItems) {
    return new V1LifecycleHandler().exec(execAction(commandItems));
  }

  private V1ExecAction execAction(String... commandItems) {
    return new V1ExecAction().command(Arrays.asList(commandItems));
  }

  private V1Probe createReadinessProbe(PodTuning tuning) {
    V1Probe readinessProbe = new V1Probe();
    readinessProbe
        .initialDelaySeconds(getReadinessProbeInitialDelaySeconds(tuning))
        .timeoutSeconds(getReadinessProbeTimeoutSeconds(tuning))
        .periodSeconds(getReadinessProbePeriodSeconds(tuning))
        .failureThreshold(getReadinessProbeFailureThreshold(tuning));

    // Add the success threshold only if the value is non-default to avoid pod roll.
    if (getReadinessProbeSuccessThreshold(tuning) != DEFAULT_SUCCESS_THRESHOLD) {
      readinessProbe.successThreshold(getReadinessProbeSuccessThreshold(tuning));
    }

    try {
      readinessProbe =
          readinessProbe.httpGet(
              httpGetAction(
                  READINESS_PATH,
                  getLocalAdminProtocolChannelPort(),
                  isLocalAdminProtocolChannelSecure()));
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

  private int getReadinessProbePeriodSeconds(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getPeriodSeconds())
        .orElse(tuning.getReadinessProbePeriodSeconds());
  }

  private int getReadinessProbeTimeoutSeconds(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getTimeoutSeconds())
        .orElse(tuning.getReadinessProbeTimeoutSeconds());
  }

  private int getReadinessProbeInitialDelaySeconds(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getInitialDelaySeconds())
        .orElse(tuning.getReadinessProbeInitialDelaySeconds());
  }

  private int getReadinessProbeSuccessThreshold(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getSuccessThreshold())
            .orElse(tuning.getReadinessProbeSuccessThreshold());
  }

  private int getReadinessProbeFailureThreshold(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getReadinessProbe().getFailureThreshold())
            .orElse(tuning.getReadinessProbeFailureThreshold());
  }

  private V1Probe createLivenessProbe(PodTuning tuning) {
    V1Probe livenessProbe = new V1Probe()
        .initialDelaySeconds(getLivenessProbeInitialDelaySeconds(tuning))
        .timeoutSeconds(getLivenessProbeTimeoutSeconds(tuning))
        .periodSeconds(getLivenessProbePeriodSeconds(tuning))
        .failureThreshold(getLivenessProbeFailureThreshold(tuning));

    // Add the success threshold only if the value is non-default to avoid pod roll.
    if (getLivenessProbeSuccessThreshold(tuning) != DEFAULT_SUCCESS_THRESHOLD) {
      livenessProbe.successThreshold(getLivenessProbeSuccessThreshold(tuning));
    }
    return livenessProbe.exec(execAction(LIVENESS_PROBE));
  }

  private int getLivenessProbeInitialDelaySeconds(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getInitialDelaySeconds())
        .orElse(tuning.getLivenessProbeInitialDelaySeconds());
  }

  private int getLivenessProbeTimeoutSeconds(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getTimeoutSeconds())
        .orElse(tuning.getLivenessProbeTimeoutSeconds());
  }

  private int getLivenessProbePeriodSeconds(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getPeriodSeconds())
        .orElse(tuning.getLivenessProbePeriodSeconds());
  }

  private int getLivenessProbeSuccessThreshold(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getSuccessThreshold())
            .orElse(tuning.getLivenessProbeSuccessThreshold());
  }

  private int getLivenessProbeFailureThreshold(PodTuning tuning) {
    return Optional.ofNullable(getServerSpec().getLivenessProbe().getFailureThreshold())
            .orElse(tuning.getLivenessProbeFailureThreshold());
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

    @Override
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

    @Override
    public int hashCode() {
      HashCodeBuilder builder =
          new HashCodeBuilder()
              .appendSuper(super.hashCode())
              .append(conflictStep);

      return builder.toHashCode();
    }

    private Step getConflictStep() {
      return conflictStep;
    }
  }

  public class CyclePodStep extends BaseStep {
    private final V1Pod pod;
    private final String message;

    CyclePodStep(V1Pod pod, Step next, String message) {
      super(next);
      this.pod = pod;
      this.message = message;
    }

    private ResponseStep<Object> deleteResponse(V1Pod pod, Step next) {
      return new DeleteResponseStep(pod, next);
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
          .deletePodAsync(getPodName(), getNamespace(), getDomainUid(),
              new V1DeleteOptions(), deleteResponse(pod, next));
    }

    // Prevent the watcher from recreating pod with old spec
    private void markBeingDeleted() {
      info.setServerPodBeingDeleted(getServerName(), Boolean.TRUE);
    }

    @Override
    public NextAction apply(Packet packet) {

      markBeingDeleted();
      return doNext(createCyclePodEventStep(deletePod(pod, getNext())), packet);
    }

    private Step createCyclePodEventStep(Step next) {
      LOGGER.info(MessageKeys.CYCLING_POD, Objects.requireNonNull(pod.getMetadata()).getName());
      return Step.chain(EventHelper.createEventStep(new EventData(POD_CYCLE_STARTING, message).podName(getPodName())),
          next);
    }
  }

  private class VerifyPodStep extends BaseStep {

    VerifyPodStep(Step next) {
      super(next);
    }

    private ResponseStep<V1Pod> patchResponse(Step next) {
      return new PatchPodResponseStep(next);
    }

    private Map<String, String> getLabels(V1Pod pod) {
      return Optional.ofNullable(pod.getMetadata()).map(V1ObjectMeta::getLabels).orElseGet(Collections::emptyMap);
    }

    private Map<String, String> getAnnotations(V1Pod pod) {
      return Optional.ofNullable(pod.getMetadata()).map(V1ObjectMeta::getAnnotations).orElseGet(Collections::emptyMap);
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

    private Step patchCurrentPod(V1Pod currentPod, Step next) {
      return patchPod(currentPod, next);
    }

    private boolean mustPatchPod(V1Pod currentPod) {
      return KubernetesUtils.isMissingValues(getLabels(currentPod), getNonHashedPodLabels())
          || KubernetesUtils.isMissingValues(getAnnotations(currentPod), getPodAnnotations());
    }

    private void logPodExists() {
      LOGGER.fine(getPodExistsMessageKey(), getDomainUid(), getServerName());
    }

    private boolean hasLabel(V1Pod pod, String key) {
      return pod.getMetadata().getLabels().containsKey(key);
    }

    private boolean isLegacyPod(V1Pod currentPod) {
      return !hasLabel(currentPod, OPERATOR_VERSION);
    }

    private boolean isPodFromRecentOperator(V1Pod currentPod) {
      return Optional.ofNullable(currentPod.getMetadata()).map(V1ObjectMeta::getLabels)
          .map(l -> l.get(OPERATOR_VERSION)).map(PodStepContext.this::isRecentOperator).orElse(false);
    }

    private boolean isLegacyMiiPod(V1Pod currentPod) {
      return hasLabel(currentPod, MODEL_IN_IMAGE_DOMAINZIP_HASH);
    }

    private void setLabel(V1Pod currentPod, String key, String value) {
      currentPod.getMetadata().putLabelsItem(key, value);
    }

    @SuppressWarnings("SameParameterValue")
    private void copyLabel(V1Pod fromPod, V1Pod toPod, String key) {
      setLabel(toPod, key, getLabel(fromPod, key));
    }

    private String adjustedLegacyHash(V1Pod currentPod, Consumer<V1Pod> adjustment) {
      V1Pod recipe = createPodRecipe();
      adjustment.accept(recipe);

      if (isLegacyMiiPod(currentPod)) {
        copyLabel(currentPod, recipe, MODEL_IN_IMAGE_DOMAINZIP_HASH);
      }

      restoreAffinityContent(recipe, currentPod);

      return AnnotationHelper.createHash(recipe);
    }

    private String adjustedHash(V1Pod currentPod, List<BiConsumer<V1Pod, V1Pod>> adjustments) {
      V1Pod recipe = createPodRecipe();
      adjustments.forEach(adjustment -> adjustment.accept(recipe, currentPod));

      return AnnotationHelper.createHash(recipe);
    }

    private void addLegacyPrometheusAnnotationsFrom30(V1Pod pod) {
      AnnotationHelper.annotateForPrometheus(pod.getMetadata(), WLS_EXPORTER, getOldMetricsPort());
    }

    private boolean canAdjustLegacyHashToMatch(V1Pod currentPod, String requiredHash) {
      // Legacy pods could be created by operator version 3.0 or 3.1
      return requiredHash.equals(adjustedLegacyHash(currentPod, this::addLegacyPrometheusAnnotationsFrom30))
          || requiredHash.equals(
              adjustedLegacyHash(currentPod, PodStepContext.this::addLegacyPrometheusAnnotationsFrom31));
    }

    private void adjustVolumeMountName(List<V1VolumeMount> convertedVolumeMounts, V1VolumeMount volumeMount) {
      convertedVolumeMounts.add(volumeMount.name(volumeMount.getName().replaceAll("^" + COMPATIBILITY_MODE
          + AUXILIARY_IMAGE_VOLUME_NAME_PREFIX, AUXILIARY_IMAGE_VOLUME_NAME_OLD_PREFIX)));
    }

    private void adjustContainer(List<V1Container> convertedContainers, V1Container container, V1Pod currentPod) {
      adjustContainer(convertedContainers, container, false, currentPod);
    }

    private void adjustContainer(List<V1Container> convertedContainers, V1Container container, boolean initContainer,
                                 V1Pod currentPod) {
      String convertedName = container.getName().replaceAll("^" + COMPATIBILITY_MODE, "");
      List<V1EnvVar> newEnv = new ArrayList<>();
      Optional.ofNullable(container.getEnv())
          .ifPresent(env -> env.forEach(envVar -> newEnv.add(envVar.value(Optional.ofNullable(envVar)
          .map(V1EnvVar::getValue).map(v -> v.replaceAll("^" + COMPATIBILITY_MODE, ""))
              .orElse(null)))));

      List<V1VolumeMount> convertedVolumeMounts = new ArrayList<>();
      Optional.ofNullable(container.getVolumeMounts())
          .ifPresent(vms -> vms.forEach(i -> adjustVolumeMountName(convertedVolumeMounts, i)));
      if (initContainer && container.getName().startsWith(COMPATIBILITY_MODE)
          && !isPodFromOperatorMajorMinorVersion34RevAbove0(currentPod)) {
        container.resources(null);
      }

      convertedContainers.add(new V1ContainerBuilder(container).build().name(convertedName).env(newEnv)
          .volumeMounts(convertedVolumeMounts.isEmpty() ? null : convertedVolumeMounts));
    }

    private boolean isPodFromOperatorMajorMinorVersion34RevAbove0(V1Pod currentPod) {
      return Optional.ofNullable(currentPod.getMetadata()).map(V1ObjectMeta::getLabels)
          .map(l -> l.get(OPERATOR_VERSION)).map(PodStepContext.this::isOperatorMajorMinorVersion34RevAbove0)
          .orElse(false);
    }

    private void adjustVolumeName(List<V1Volume> convertedVolumes, V1Volume volume) {
      convertedVolumes.add(volume.name(volume.getName().replaceAll("^" + COMPATIBILITY_MODE
          + AUXILIARY_IMAGE_VOLUME_NAME_PREFIX, AUXILIARY_IMAGE_VOLUME_NAME_OLD_PREFIX)));
    }

    private void convertAuxImagesInitContainerVolumeAndMounts(V1Pod recipe, V1Pod currentPod) {
      V1PodSpec podSpec = recipe.getSpec();
      List<V1Container> convertedInitContainers = new ArrayList<>();
      Optional.ofNullable(podSpec.getInitContainers())
          .ifPresent(initContainers -> initContainers.forEach(
              i -> adjustContainer(convertedInitContainers, i, true, currentPod)));
      podSpec.initContainers(convertedInitContainers);

      List<V1Container> convertedContainers = new ArrayList<>();
      podSpec.getContainers().forEach(c -> adjustContainer(convertedContainers, c, currentPod));
      podSpec.containers(convertedContainers);

      List<V1Volume> convertedVolumes = new ArrayList<>();
      Optional.ofNullable(podSpec.getVolumes())
          .ifPresent(volumes -> volumes.forEach(i -> adjustVolumeName(convertedVolumes, i)));
      podSpec.volumes(convertedVolumes);
      recipe.spec(new V1PodSpecBuilder(podSpec).build()
          .initContainers(convertedInitContainers.isEmpty() ? null : convertedInitContainers)
          .volumes(convertedVolumes.isEmpty() ? null : convertedVolumes));
    }

    private void restoreMetricsExporterSidecarPortTcpMetrics(V1Pod recipe, V1Pod currentPod) {
      V1PodSpec podSpec = recipe.getSpec();
      podSpec.getContainers().stream().filter(c -> "monitoring-exporter".equals(c.getName()))
          .findFirst().flatMap(c -> c.getPorts().stream().filter(p -> "metrics".equals(p.getName()))
              .findFirst()).ifPresent(p -> p.setName("tcp-metrics"));
    }

    private void restoreLegacyIstioPortsConfig(V1Pod recipePod,  V1Pod currentPod) {
      V1PodSpec recipePodSpec = recipePod.getSpec();

      Optional<V1Container> weblogicContainer =
          currentPod.getSpec().getContainers().stream().filter(c -> "weblogic-server".equals(c.getName()))
          .findFirst();

      // Get the istio side car env list

      Optional<List<V1EnvVar>> envVars =
          currentPod.getSpec().getContainers().stream().filter(c -> "istio-proxy".equals(c.getName()))
              .findFirst().map(V1Container::getEnv);

      envVars.ifPresent(v1EnvVars -> weblogicContainer.map(V1Container::getReadinessProbe)
          .ifPresent(v1Probe -> resetIstioPodRecipeAfterUpgrade(recipePodSpec, weblogicContainer.get(), v1EnvVars)));
    }

    @SuppressWarnings({"java:S112", "unchecked"})
    private void resetIstioPodRecipeAfterUpgrade(V1PodSpec recipePodSpec, V1Container wlContainer,
                                                 List<V1EnvVar> envVars) {

      for (V1EnvVar envVar : envVars) {
        if ("ISTIO_KUBE_APP_PROBERS".equals(envVar.getName())) {
          try {

            Map<String, Object> readinessValue =
                new ObjectMapper().readValue(envVar.getValue(), HashMap.class);

            Map<String, Object> readyProbe = (Map<String, Object>)
                readinessValue.get("/app-health/weblogic-server/readyz");
            Map<String, Object> httpGet = (Map<String, Object>) readyProbe.get("httpGet");

            Optional<V1Container> recipeContainer = recipePodSpec.getContainers().stream()
                .filter(c -> "weblogic-server".equals(c.getName()))
                .findFirst();

            // reset the readiness port since new recipe no longer use the istio.readinessProbe from the istio sidecar
            Integer port = (Integer)httpGet.get("port");
            String scheme = (String)httpGet.get("scheme");

            recipeContainer.ifPresent(c -> c.getReadinessProbe().getHttpGet().path(READINESS_PATH)
                .port(new IntOrString(port)));

            if (scheme.equals("HTTPS")) {
              recipeContainer.ifPresent(c -> c.getReadinessProbe().getHttpGet()
                  .setScheme("HTTPS"));
            } else if (scheme.equals("HTTP")) {
              recipeContainer.ifPresent(c -> c.getReadinessProbe().getHttpGet()
                  .setScheme(null));
            }

            // copy the ports over for calculating hash
            List<V1ContainerPort>  currentContainerPorts = wlContainer.getPorts();
            if (currentContainerPorts.isEmpty()) {
              recipeContainer.ifPresent(c -> c.setPorts(new ArrayList<>()));
            } else {
              recipeContainer.ifPresent(c -> c.setPorts(currentContainerPorts));
            }
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
          break;
        }
      }
    }

    private void restoreAffinityContent(V1Pod recipe, V1Pod currentPod) {
      recipe.getSpec().affinity(currentPod.getSpec().getAffinity());
    }

    private void restoreLogHomeLayoutEnvVar(V1Pod recipe, V1Pod currentPod) {
      getContainer(recipe).map(V1Container::getEnv).ifPresent(envVars ->
          Optional.ofNullable(findEnvVar(envVars, ServerEnvVars.LOG_HOME_LAYOUT)).ifPresent(ev -> {
            if (LogHomeLayoutType.FLAT.toString().equals(ev.getValue())) {
              List<V1EnvVar> curPodEVs = getContainer(currentPod).map(V1Container::getEnv).orElse(new ArrayList<>());
              if (findEnvVar(curPodEVs, ServerEnvVars.LOG_HOME_LAYOUT) == null) {
                removeEnvVar(envVars, ServerEnvVars.LOG_HOME_LAYOUT);
              }
            }
          }));
    }

    private void restoreFluentdVolume(V1Pod recipe, V1Pod currentPod) {
      Optional.ofNullable(recipe.getSpec().getVolumes())
          .ifPresent(volumes -> volumes.stream().filter(volume -> FLUENTD_CONFIGMAP_VOLUME.equals(volume.getName()))
              .forEach(volume -> {
                Optional.ofNullable(volume.getConfigMap()).ifPresent(cms -> cms.setName(OLD_FLUENTD_CONFIGMAP_NAME));
              }));
    }

    private void restoreSecurityContext(V1Pod recipe, V1Pod currentPod) {
      if (PodSecurityHelper.getDefaultPodSecurityContext().equals(recipe.getSpec().getSecurityContext())) {
        recipe.getSpec().setSecurityContext(null);
      }
      Optional.ofNullable(recipe.getSpec().getContainers())
          .ifPresent(containers -> containers.forEach(container -> {
            if (PodSecurityHelper.getDefaultContainerSecurityContext().equals(container.getSecurityContext())) {
              container.setSecurityContext(null);
            }
          }));
      Optional.ofNullable(recipe.getSpec().getInitContainers())
          .ifPresent(initContainers -> initContainers.forEach(initContainer -> {
            if (PodSecurityHelper.getDefaultContainerSecurityContext().equals(initContainer.getSecurityContext())) {
              initContainer.setSecurityContext(null);
            }
          }));
    }

    private boolean canAdjustRecentOperatorMajorVersion3HashToMatch(V1Pod currentPod, String requiredHash) {
      // start with list of adjustment methods
      // generate stream of combinations
      // for each combination, start with pod recipe, apply all adjustments, and generate hash
      // return true if any adjusted hash matches required hash
      List<BiConsumer<V1Pod, V1Pod>> adjustments = List.of(
          this::restoreMetricsExporterSidecarPortTcpMetrics,
          this::convertAuxImagesInitContainerVolumeAndMounts,
          this::restoreLegacyIstioPortsConfig,
          this::restoreAffinityContent,
          this::restoreLogHomeLayoutEnvVar,
          this::restoreFluentdVolume,
          this::restoreSecurityContext);
      return Combinations.of(adjustments)
          .map(adjustment -> adjustedHash(currentPod, adjustment))
          .anyMatch(requiredHash::equals);
    }

    private boolean hasCorrectPodHash(V1Pod currentPod) {

      return (isLegacyPod(currentPod)
              && canAdjustLegacyHashToMatch(currentPod, AnnotationHelper.getHash(currentPod)))
          || (isPodFromRecentOperator(currentPod)
              && canAdjustRecentOperatorMajorVersion3HashToMatch(currentPod, AnnotationHelper.getHash(currentPod)))
          || AnnotationHelper.getHash(getPodModel()).equals(AnnotationHelper.getHash(currentPod));
    }

    private boolean canUseCurrentPod(V1Pod currentPod) {
      boolean useCurrent = hasCorrectPodHash(currentPod) && canUseNewDomainZip(currentPod);

      if (!useCurrent) {
        LOGGER.finer(
            MessageKeys.POD_DUMP,
            Yaml.dump(currentPod),
            Yaml.dump(getPodModel()));
      }

      return useCurrent;
    }

    @Override
    public NextAction apply(Packet packet) {
      V1Pod currentPod = info.getServerPod(getServerName());

      if (currentPod == null) {
        return doNext(createNewPod(getNext()), packet);
      } else if (!canUseCurrentPod(currentPod)) {
        return doNext(replaceCurrentPod(currentPod, getNext()), packet);
      } else if (PodHelper.shouldRestartEvictedPod(currentPod)) {
        return doNext(cycleEvictedPodStep(currentPod, getNext()), packet);
      } else if (mustPatchPod(currentPod)) {
        return doNext(patchCurrentPod(currentPod, getNext()), packet);
      } else {
        logPodExists();
        return doNext(packet);
      }
    }
  }


  private boolean isOperatorMajorMinorVersion34RevAbove0(String operatorVersion) {
    SemanticVersion version = new SemanticVersion(operatorVersion);
    return version.getMajor() == 3 && version.getMinor() == 4 && version.getRevision() > 0;
  }

  private abstract class BaseResponseStep extends ResponseStep<V1Pod> {
    BaseResponseStep(Step next) {
      super(next);
    }

    @Override
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
      return doNext(createKubernetesFailureSteps(callResponse), packet);
    }
  }

  private class CreateResponseStep extends BaseResponseStep {

    CreateResponseStep(Step next) {
      super(next);
    }

    private void logPodCreated() {
      LOGGER.info(getPodCreatedMessageKey(), getDomainUid(), getServerName());
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

    @Override
    protected String getDetail() {
      return getServerName();
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<Object> callResponses) {
      if (callResponses.getStatusCode() == HTTP_NOT_FOUND) {
        return onSuccess(packet, callResponses);
      }
      return super.onFailure(getConflictStep(), packet, callResponses);
    }

    private ResponseStep<V1Pod> replaceResponse(Step next) {
      return new ReplacePodResponseStep(next);
    }

    /**
     * Creates the specified replacement pod and records it.
     *
     * @param next the next step to perform after the pod creation is complete.
     * @return a step to be scheduled.
     */
    private Step replacePod(Step next) {
      return createPodAsync(replaceResponse(next));
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

    private void logPodReplaced() {
      LOGGER.info(getPodReplacedMessageKey(), getDomainUid(), getServerName());
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

    private void logPodPatched() {
      LOGGER.info(getPodPatchedMessageKey(), getDomainUid(), getServerName());
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
      return WLS_EXPORTER;
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
            .resources(getDomain().getMonitoringExporterResources())
            .securityContext(PodSecurityHelper.getDefaultContainerSecurityContext())
            .addEnvItem(new V1EnvVar().name("JAVA_OPTS").value(createJavaOptions()))
            .addPortsItem(new V1ContainerPort()
                .name("metrics").protocol("TCP").containerPort(getPort()));
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

  @Override
  protected List<V1EnvVar> getServerPodEnvironmentVariables() {
    return getServerSpec().getEnvironmentVariables();
  }

  @Override
  protected V1ResourceRequirements getResources() {
    return getServerSpec().getResources();
  }
}

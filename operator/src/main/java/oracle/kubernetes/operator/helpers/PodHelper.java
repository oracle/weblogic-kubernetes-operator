// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.RetryStrategy;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.MonitoringExporterSpecification;
import oracle.kubernetes.weblogic.domain.model.ServerSpec;
import oracle.kubernetes.weblogic.domain.model.Shutdown;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_SIDECAR_PORT;
import static oracle.kubernetes.operator.KubernetesConstants.EXPORTER_CONTAINER_NAME;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;

@SuppressWarnings("ConstantConditions")
public class PodHelper {
  static final long DEFAULT_ADDITIONAL_DELETE_TIME = 10;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private PodHelper() {
  }

  /**
   * Creates an admin server pod resource, based on the specified packet.
   * Expects the packet to contain a domain presence info as well as:
   *   SCAN                 the topology for the server (WlsServerConfig)
   *   DOMAIN_TOPOLOGY      the topology for the domain (WlsDomainConfig)
   *
   *
   * @param packet a packet describing the domain model and topology.
   * @return an appropriate Kubernetes resource
   */
  public static V1Pod createAdminServerPodModel(Packet packet) {
    return new AdminPodStepContext(null, packet).createPodModel();
  }

  /**
   * Creates a managed server pod resource, based on the specified packet.
   * Expects the packet to contain a domain presence info as well as:
   *   CLUSTER_NAME         (optional) the name of the cluster to which the server is assigned
   *   SCAN                 the topology for the server (WlsServerConfig)
   *   DOMAIN_TOPOLOGY      the topology for the domain (WlsDomainConfig)
   *
   *
   * @param packet a packet describing the domain model and topology.
   * @return an appropriate Kubernetes resource
   */
  public static V1Pod createManagedServerPodModel(Packet packet) {
    return new ManagedPodStepContext(null, packet).createPodModel();
  }

  /**
   * check if pod is ready.
   * @param pod pod
   * @return true, if pod is ready
   */
  public static boolean isReady(V1Pod pod) {
    boolean ready = getReadyStatus(pod);
    if (ready) {
      LOGGER.fine(MessageKeys.POD_IS_READY, pod.getMetadata().getName());
    }
    return ready;
  }

  static boolean hasReadyServer(V1Pod pod) {
    return Optional.ofNullable(pod).map(PodHelper::getReadyStatus).orElse(false);
  }

  static boolean isScheduled(@Nullable V1Pod pod) {
    return Optional.ofNullable(pod).map(V1Pod::getSpec).map(V1PodSpec::getNodeName).isPresent();
  }

  static boolean hasClusterNameOrNull(@Nullable V1Pod pod, String clusterName) {
    return getClusterName(pod) == null || getClusterName(pod).equals(clusterName);
  }

  private static String getClusterName(@Nullable V1Pod pod) {
    return Optional.ofNullable(pod)
          .map(V1Pod::getMetadata)
          .map(V1ObjectMeta::getLabels)
          .map(PodHelper::getClusterName)
          .orElse(null);
  }

  private static String getClusterName(@Nonnull Map<String,String> labels) {
    return labels.get(CLUSTERNAME_LABEL);
  }

  static boolean isNotAdminServer(@Nullable V1Pod pod, String adminServerName) {
    return Optional.ofNullable(getServerName(pod)).map(s -> !s.equals(adminServerName)).orElse(true);
  }

  private static String getServerName(@Nullable V1Pod pod) {
    return Optional.ofNullable(pod)
            .map(V1Pod::getMetadata)
            .map(V1ObjectMeta::getLabels)
            .map(PodHelper::getServerName)
            .orElse(null);
  }

  private static String getServerName(@Nonnull Map<String,String> labels) {
    return labels.get(SERVERNAME_LABEL);
  }

  /**
   * get if pod is in ready state.
   * @param pod pod
   * @return true, if pod is ready
   */
  public static boolean getReadyStatus(V1Pod pod) {
    return Optional.ofNullable(pod.getStatus())
          .filter(PodHelper::isRunning)
          .map(V1PodStatus::getConditions)
          .orElse(Collections.emptyList())
          .stream()
          .anyMatch(PodHelper::isReadyCondition);
  }

  private static boolean isRunning(@Nonnull V1PodStatus status) {
    return "Running".equals(status.getPhase());
  }

  private static boolean isReadyCondition(V1PodCondition condition) {
    return "Ready".equals(condition.getType()) && "True".equals(condition.getStatus());
  }

  /**
   * Check if pod is deleting.
   * @param pod pod
   * @return true, if pod is deleting
   */
  public static boolean isDeleting(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    if (meta != null) {
      return meta.getDeletionTimestamp() != null;
    }
    return false;
  }

  /**
   * Check if pod is in failed state.
   * @param pod pod
   * @return true, if pod is in failed state
   */
  public static boolean isFailed(V1Pod pod) {
    V1PodStatus status = pod.getStatus();
    if (status != null) {
      if ("Failed".equals(status.getPhase())) {
        LOGGER.severe(MessageKeys.POD_IS_FAILED, pod.getMetadata().getName());
        return true;
      }
    }
    return false;
  }

  /**
   * get pod domain UID.
   * @param pod pod
   * @return domain UID
   */
  public static String getPodDomainUid(V1Pod pod) {
    return KubernetesUtils.getDomainUidLabel(
        Optional.ofNullable(pod).map(V1Pod::getMetadata).orElse(null));
  }

  /**
   * get pod's server name.
   * @param pod pod
   * @return server name
   */
  public static String getPodServerName(V1Pod pod) {
    V1ObjectMeta meta = pod.getMetadata();
    Map<String, String> labels = meta.getLabels();
    if (labels != null) {
      return labels.get(LabelConstants.SERVERNAME_LABEL);
    }
    return null;
  }


  /**
   * Factory for {@link Step} that creates admin server pod.
   *
   * @param next Next processing step
   * @return Step for creating admin server pod
   */
  public static Step createAdminPodStep(Step next) {
    return new AdminPodStep(next);
  }

  static void addToPacket(Packet packet, PodAwaiterStepFactory pw) {
    packet
        .getComponents()
        .put(
            ProcessingConstants.PODWATCHER_COMPONENT_NAME,
            Component.createFor(PodAwaiterStepFactory.class, pw));
  }

  static PodAwaiterStepFactory getPodAwaiterStepFactory(Packet packet) {
    return packet.getSpi(PodAwaiterStepFactory.class);
  }

  /**
   * Factory for {@link Step} that creates managed server pod.
   *
   * @param next Next processing step
   * @return Step for creating managed server pod
   */
  public static Step createManagedPodStep(Step next) {
    return new ManagedPodStep(next);
  }

  /**
   * Factory for {@link Step} that deletes server pod.
   *
   * @param serverName the name of the server whose pod is to be deleted
   * @param next Next processing step
   * @return Step for deleting server pod
   */
  public static Step deletePodStep(String serverName, Step next) {
    return new DeletePodStep(serverName, next);
  }

  static List<V1EnvVar> createCopy(List<V1EnvVar> envVars) {
    ArrayList<V1EnvVar> copy = new ArrayList<>();
    if (envVars != null) {
      for (V1EnvVar envVar : envVars) {
        // note that a deep copy of valueFrom is not needed here as, unlike with value, the
        // new V1EnvVarFrom objects would be created by the doDeepSubstitutions() method in
        // StepContextBase class.
        copy.add(new V1EnvVar()
            .name(envVar.getName())
            .value(envVar.getValue())
            .valueFrom(envVar.getValueFrom()));
      }
    }
    return copy;
  }

  static class AdminPodStepContext extends PodStepContext {
    static final String INTERNAL_OPERATOR_CERT_ENV = "INTERNAL_OPERATOR_CERT";
    private final Packet packet;

    AdminPodStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);
      this.packet = packet;

      init();
    }

    @Override
    ServerSpec getServerSpec() {
      return getDomain().getAdminServerSpec();
    }

    @Override
    String getServerName() {
      return getAsName();
    }

    @Override
    Step createProgressingStep(Step actionStep) {
      return DomainStatusUpdater.createProgressingStartedEventStep(
          DomainStatusUpdater.ADMIN_SERVER_STARTING_PROGRESS_REASON, false, actionStep);
    }

    @Override
    Step createNewPod(Step next) {
      return createProgressingStep(createPod(next));
    }

    @Override
    Step replaceCurrentPod(V1Pod pod, Step next) {
      if (MakeRightDomainOperation.isInspectionRequired(packet)) {
        return createProgressingStep(MakeRightDomainOperation.createStepsToRerunWithIntrospection(packet));
      } else {
        return createProgressingStep(createCyclePodStep(pod, next));
      }
    }

    @Override
    String getPodCreatedMessageKey() {
      return MessageKeys.ADMIN_POD_CREATED;
    }

    @Override
    String getPodExistsMessageKey() {
      return MessageKeys.ADMIN_POD_EXISTS;
    }

    @Override
    String getPodPatchedMessageKey() {
      return MessageKeys.ADMIN_POD_PATCHED;
    }

    @Override
    String getPodReplacedMessageKey() {
      return MessageKeys.ADMIN_POD_REPLACED;
    }

    @Override
    V1Pod withNonHashedElements(V1Pod pod) {
      V1Pod v1Pod = super.withNonHashedElements(pod);
      getContainer(v1Pod).ifPresent(c -> c.addEnvItem(internalCertEnvValue()));

      return v1Pod;
    }

    private V1EnvVar internalCertEnvValue() {
      return new V1EnvVar().name(INTERNAL_OPERATOR_CERT_ENV).value(getInternalOperatorCertFile());
    }

    @Override
    protected V1PodSpec createSpec(TuningParameters tuningParameters) {
      return super.createSpec(tuningParameters).hostname(getPodName());
    }

    @Override
    List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters) {
      List<V1EnvVar> vars = createCopy(getServerSpec().getEnvironmentVariables());
      addStartupEnvVars(vars);
      return vars;
    }

    @Override
    protected Map<String, String> getPodLabels() {
      return getServerSpec().getPodLabels();
    }

    @Override
    protected Map<String, String> getPodAnnotations() {
      return getServerSpec().getPodAnnotations();
    }

    private String getInternalOperatorCertFile() {
      return Certificates.getOperatorInternalCertificateData();
    }
  }

  static class AdminPodStep extends Step {

    AdminPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      PodStepContext context = new AdminPodStepContext(this, packet);

      return doNext(context.verifyPod(getNext()), packet);
    }

  }

  static class ManagedPodStepContext extends PodStepContext {

    private final String clusterName;
    private final Packet packet;
    private final ExporterContext exporterContext;

    ManagedPodStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);
      this.packet = packet;
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);
      exporterContext = createExporterContext();

      init();
    }

    ExporterContext createExporterContext() {
      return useSidecar() ? new SidecarExporterContext() : new WebAppExporterContext();
    }

    // Use the monitoring exporter sidecar if an exporter configuration is part of the domain.
    private boolean useSidecar() {
      return getDomain().getMonitoringExporterConfiguration() != null;
    }

    @Override
    ServerSpec getServerSpec() {
      return getDomain().getServer(getServerName(), getClusterName());
    }

    @Override
    protected Map<String, String> getPodLabels() {
      return getServerSpec().getPodLabels();
    }

    @Override
    protected Map<String, String> getPodAnnotations() {
      return getServerSpec().getPodAnnotations();
    }

    @Override
    boolean isLocalAdminProtocolChannelSecure() {
      return scan.isLocalAdminProtocolChannelSecure();
    }

    @Override
    Integer getLocalAdminProtocolChannelPort() {
      return scan.getLocalAdminProtocolChannelPort();
    }

    @Override
    String getServerName() {
      return scan.getName();
    }

    @Override
    // let the pod rolling step update the pod
    Step replaceCurrentPod(V1Pod pod, Step next) {
      return deferProcessing(createCyclePodStep(pod, next));
    }

    private Step deferProcessing(Step deferredStep) {
      synchronized (packet) {
        Optional.ofNullable(getServersToRoll()).ifPresent(r -> r.put(getServerName(), createRollRequest(deferredStep)));
      }
      return null;
    }

    private Step.StepAndPacket createRollRequest(Step deferredStep) {
      return new Step.StepAndPacket(createProgressingStep(deferredStep), packet.copy());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Step.StepAndPacket> getServersToRoll() {
      return (Map<String, Step.StepAndPacket>) packet.get(SERVERS_TO_ROLL);
    }

    @Override
    Step createProgressingStep(Step actionStep) {
      return DomainStatusUpdater.createProgressingStartedEventStep(
          DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON, false, actionStep);
    }

    @Override
    Step createNewPod(Step next) {
      return createProgressingStep(createPod(next));
    }

    @Override
    String getPodCreatedMessageKey() {
      return MessageKeys.MANAGED_POD_CREATED;
    }

    @Override
    String getPodExistsMessageKey() {
      return MessageKeys.MANAGED_POD_EXISTS;
    }

    @Override
    String getPodPatchedMessageKey() {
      return MessageKeys.MANAGED_POD_PATCHED;
    }

    @Override
    protected String getPodReplacedMessageKey() {
      return MessageKeys.MANAGED_POD_REPLACED;
    }

    @Override
    protected V1ObjectMeta createMetadata() {
      V1ObjectMeta metadata = super.createMetadata();
      if (getClusterName() != null) {
        metadata.putLabelsItem(CLUSTERNAME_LABEL, getClusterName());
      }

      // Add prometheus annotations. This will overwrite any custom annotations with same name.
      // Prometheus does not support "prometheus.io/scheme".  The scheme(http/https) can be set
      // in the Prometheus Chart values yaml under the "extraScrapeConfigs:" section.
      if (exporterContext.isEnabled()) {
        AnnotationHelper.annotateForPrometheus(metadata, exporterContext.getBasePath(), exporterContext.getPort());
      }
      return metadata;
    }

    @Override
    protected String getClusterName() {
      return clusterName;
    }

    @Override
    protected List<String> getContainerCommand() {
      return new ArrayList<>(super.getContainerCommand());
    }

    @Override
    protected List<V1Container> getContainers() {
      List<V1Container> containers = new ArrayList<>(super.getContainers());
      exporterContext.addContainer(containers);
      return containers;
    }

    @Override
    @SuppressWarnings("unchecked")
    List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters) {
      List<V1EnvVar> envVars = createCopy((List<V1EnvVar>) packet.get(ProcessingConstants.ENVVARS));

      List<V1EnvVar> vars = new ArrayList<>(envVars);
      addStartupEnvVars(vars);
      return vars;
    }

    abstract class ExporterContext {
      int getWebLogicRestPort() {
        return selectPortByProtocolName().orElse(selectPortFromList());
      }

      private Optional<Integer> selectPortByProtocolName() {
        return getContainerPorts().stream()
              .filter(p -> Objects.equals(scan.getAdminProtocolChannelName(), p.getName()))
              .findFirst()
              .map(V1ContainerPort::getContainerPort);
      }

      private int selectPortFromList() {
        return Stream.of(getAdminPort(), getListenPort(), getSslListenPort())
              .filter(Objects::nonNull)
              .findFirst()
              .orElseThrow(() -> new RuntimeException("No ports defined for this server"));
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
      private static final int DEBUG_PORT = 30055;
      private final int metricsPort;

      public SidecarExporterContext() {
        metricsPort = MonitoringExporterSpecification.getRestPort(scan);
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
              .addPortsItem(new V1ContainerPort().name("metrics").protocol("TCP").containerPort(getPort()))
              .addPortsItem(new V1ContainerPort().name("debugger").protocol("TCP").containerPort(DEBUG_PORT));
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
        args.add(getDebugOption());

        return String.join(" ", args);
      }

      private String getDebugOption() {
        return "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:" + DEBUG_PORT;
      }
    }

  }

  static class ManagedPodStep extends Step {
    ManagedPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      ManagedPodStepContext context = new ManagedPodStepContext(this, packet);

      return doNext(context.verifyPod(getNext()), packet);
    }
  }

  private static class DeletePodStep extends Step {
    private final String serverName;

    DeletePodStep(String serverName, Step next) {
      super(next);
      this.serverName = serverName;
    }

    @Override
    public NextAction apply(Packet packet) {

      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      V1Pod oldPod = info.getServerPod(serverName);

      long gracePeriodSeconds = Shutdown.DEFAULT_TIMEOUT;
      String clusterName = null;
      if (oldPod != null) {
        Map<String, String> labels = oldPod.getMetadata().getLabels();
        if (labels != null) {
          clusterName = labels.get(CLUSTERNAME_LABEL);
        }

        ServerSpec serverSpec = info.getDomain().getServer(serverName, clusterName);
        if (serverSpec != null) {
          // We add a 10 second fudge factor here to account for the fact that WLST takes
          // ~6 seconds to start, so along with any other delay in connecting and issuing
          // the shutdown, the actual server instance has the full configured timeout to
          // gracefully shutdown before the container is destroyed by this timeout.
          // We will remove this fudge factor when the operator connects via REST to shutdown
          // the server instance.
          gracePeriodSeconds =
              serverSpec.getShutdown().getTimeoutSeconds() + DEFAULT_ADDITIONAL_DELETE_TIME;
        }

        String name = oldPod.getMetadata().getName();
        info.setServerPodBeingDeleted(serverName, Boolean.TRUE);
        return doNext(
            deletePod(name, info.getNamespace(), getPodDomainUid(oldPod), gracePeriodSeconds, getNext()),
            packet);
      } else {
        return doNext(packet);
      }
    }

    private Step deletePod(String name, String namespace, String domainUid, long gracePeriodSeconds, Step next) {

      Step conflictStep =
          new CallBuilder()
              .readPodAsync(
                  name,
                  namespace,
                  domainUid,
                  new DefaultResponseStep<>(next) {
                    @Override
                    public NextAction onSuccess(Packet packet, CallResponse<V1Pod> callResponse) {
                      V1Pod pod = callResponse.getResult();

                      if (pod != null && !PodHelper.isDeleting(pod)) {
                        // pod still needs to be deleted
                        return doNext(DeletePodStep.this, packet);
                      }
                      return super.onSuccess(packet, callResponse);
                    }
                  });

      V1DeleteOptions deleteOptions = new V1DeleteOptions().gracePeriodSeconds(gracePeriodSeconds);
      DeletePodRetryStrategy retryStrategy = new DeletePodRetryStrategy(next);
      return new CallBuilder().withRetryStrategy(retryStrategy)
              .deletePodAsync(name, namespace, domainUid, deleteOptions, new DefaultResponseStep<>(conflictStep, next));
    }
  }

  /* Retry strategy for delete pod which will not perform any retries */
  private static final class DeletePodRetryStrategy implements RetryStrategy {
    private final Step retryStep;

    DeletePodRetryStrategy(Step retryStep) {
      this.retryStep = retryStep;
    }

    @Override
    public NextAction doPotentialRetry(Step conflictStep, Packet packet, int statusCode) {
      NextAction na = new NextAction();
      na.invoke(retryStep, packet);
      return na;
    }

    @Override
    public void reset() {
    }
  }

}

// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarBuilder;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodStatus;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.processing.EffectiveServerSpec;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import oracle.kubernetes.weblogic.domain.model.DomainStatus;
import oracle.kubernetes.weblogic.domain.model.ServerStatus;
import oracle.kubernetes.weblogic.domain.model.Shutdown;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.KubernetesConstants.EVICTED_REASON;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.POD_SCHEDULED;
import static oracle.kubernetes.operator.KubernetesConstants.UNSCHEDULABLE_REASON;
import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.SERVERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.SERVERS_TO_ROLL;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.UNKNOWN_STATE;

@SuppressWarnings("ConstantConditions")
public class PodHelper {
  public static final long DEFAULT_ADDITIONAL_DELETE_TIME = 10;
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private PodHelper() {
  }

  /**
   * Specifies the product version used to create pods.
   * @param productVersion the version of the operator.
   */
  public static void setProductVersion(String productVersion) {
    PodStepContext.setProductVersion(productVersion);
  }

  /**
   * Creates an admin server pod resource, based on the specified packet.
   * Expects the packet to contain a domain presence info as well as:
   *   SCAN                 the topology for the server (WlsServerConfig)
   *   DOMAIN_TOPOLOGY      the topology for the domain (WlsDomainConfig)
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
    boolean ready = hasReadyStatus(pod);
    if (ready) {
      LOGGER.fine(MessageKeys.POD_IS_READY, pod.getMetadata().getName());
    }
    return ready;
  }

  /**
   * Is the pod waiting to roll.
   * @param pod pod
   * @return true if the pod is waiting to roll
   */
  public static boolean isWaitingToRoll(V1Pod pod) {
    return Optional.ofNullable(pod)
            .map(V1Pod::getMetadata)
            .map(V1ObjectMeta::getAnnotations)
            .map(labels -> "true".equalsIgnoreCase(labels.get(LabelConstants.TO_BE_ROLLED_LABEL)))
            .orElse(false);
  }

  static boolean hasReadyServer(V1Pod pod) {
    return Optional.ofNullable(pod).map(PodHelper::hasReadyStatus).orElse(false);
  }

  static boolean isScheduled(@Nullable V1Pod pod) {
    return Optional.ofNullable(pod).map(V1Pod::getSpec).map(V1PodSpec::getNodeName).isPresent();
  }

  static boolean hasClusterNameOrNull(@Nullable V1Pod pod, String clusterName) {
    String actualName = getClusterName(pod);
    return actualName == null || actualName.equals(clusterName);
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

  static String getServerName(@Nullable V1Pod pod) {
    return Optional.ofNullable(pod)
            .map(V1Pod::getMetadata)
            .map(V1ObjectMeta::getLabels)
            .map(PodHelper::getServerName)
            .orElse(null);
  }


  private static String getServerName(@Nonnull Map<String,String> labels) {
    return labels.get(SERVERNAME_LABEL);
  }

  private static V1SecurityContext getEffectiveSecurityContext(V1PodSecurityContext ctx) {
    return new V1SecurityContext()
            .runAsUser(ctx.getRunAsUser())
            .runAsGroup(ctx.getRunAsGroup())
            .runAsNonRoot(ctx.getRunAsNonRoot())
            .seccompProfile(ctx.getSeccompProfile())
            .seLinuxOptions(ctx.getSeLinuxOptions())
            .windowsOptions(ctx.getWindowsOptions());

  }

  /**
   * get if pod is in ready state.
   * @param pod pod
   * @return true, if pod is ready
   */
  public static boolean hasReadyStatus(V1Pod pod) {
    return Optional.ofNullable(pod)
          .map(V1Pod::getStatus)
          .filter(PodHelper::isRunning)
          .map(V1PodStatus::getConditions)
          .orElse(Collections.emptyList())
          .stream()
          .anyMatch(PodHelper::isReadyCondition);
  }

  /**
   * Get pod's Ready condition if the pod is in Running phase.
   * @param pod pod
   * @return V1PodCondition, if exists, otherwise null.
   */
  public static V1PodCondition getReadyCondition(V1Pod pod) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getStatus)
        .filter(PodHelper::isRunning)
        .map(V1PodStatus::getConditions)
        .orElse(Collections.emptyList())
        .stream()
        .filter(PodHelper::isReadyNotTrueCondition).findFirst().orElse(null);
  }

  /**
   * Get the server state From the domain resource.
   * @param domain domain resource.
   * @param serverName Name of the server.
   * @return server state, if exists, otherwise null.
   */
  public static String getServerState(DomainResource domain, String serverName) {
    return Optional.ofNullable(domain)
        .map(DomainResource::getStatus)
        .map(s -> getServerStatus(s, serverName))
        .map(ServerStatus::getState).orElse(null);
  }

  private static ServerStatus getServerStatus(DomainStatus domainStatus, String serverName) {
    return domainStatus.getServers().stream().filter(s -> matchingServerName(s, serverName))
        .findAny().orElse(null);
  }

  private static boolean matchingServerName(ServerStatus serverStatus, String serverName) {
    return serverStatus.getServerName().equals(serverName);
  }

  private static boolean isRunning(@Nonnull V1PodStatus status) {
    return "Running".equals(status.getPhase());
  }

  private static boolean isReadyCondition(V1PodCondition condition) {
    return "Ready".equals(condition.getType()) && "True".equals(condition.getStatus());
  }

  private static boolean isUnSchedulableTheReason(V1PodCondition condition) {
    return POD_SCHEDULED.equals(condition.getType()) && "False".equals(condition.getStatus())
            && UNSCHEDULABLE_REASON.equals(condition.getReason());
  }

  private static boolean isReadyNotTrueCondition(V1PodCondition condition) {
    return "Ready".equals(condition.getType()) && !"True".equals(condition.getStatus());
  }

  /**
   * Returns the Kubernetes name of the specified pod.
   * @param pod the pod
   */
  public static String getPodName(@Nonnull V1Pod pod) {
    return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getName).orElse("");
  }

  /**
   * Returns the Kubernetes namespace of the specified pod.
   * @param pod the pod
   */
  public static String getPodNamespace(@Nonnull V1Pod pod) {
    return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getNamespace).orElse("");
  }

  /**
   * Returns true if the specified pod is scheduled for deletion, as indicated by the presence of a deletion timestamp.
   * @param pod the pod
   */
  public static boolean isDeleting(@Nonnull V1Pod pod) {
    return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getDeletionTimestamp).isPresent();
  }

  /**
   * Returns true if the specified pod is in the failed state.
   * @param pod the pod to check
   */
  public static boolean isFailed(V1Pod pod) {
    if ("Failed".equals(getPhase(pod))) {
      LOGGER.severe(MessageKeys.POD_IS_FAILED, pod.getMetadata().getName());
      return true;
    }
    return false;
  }

  /**
   * Returns true if the specified pod is in the pending state.
   * @param pod the pod to check
   */
  public static boolean isPending(V1Pod pod) {
    return "Pending".equals(getPhase(pod));
  }

  @Nullable
  private static String getPhase(V1Pod pod) {
    return Optional.ofNullable(pod.getStatus()).map(V1PodStatus::getPhase).orElse(null);
  }

  /**
   * Check if pod is in failed state with "Evicted" as the reason.
   * @param pod pod
   * @return true, if pod is in failed state with "Evicted" as the reason.
   */
  public static boolean isEvicted(V1Pod pod) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getStatus)
        .map(PodHelper::isEvicted)
        .orElse(false);
  }

  /**
   * Check if the pod status shows that the pod is evicted.
   * @param status Pod status to be checked
   * @return True if the pod status shows that the pod is evicted, false otherwise
   */
  public static boolean isEvicted(@Nonnull V1PodStatus status) {
    return "Failed".equals(status.getPhase())
        && EVICTED_REASON.equals(status.getReason());
  }

  /**
   * Return true if pod was evicted and operator is configured to restart evicted pods.
   * @param pod pod
   * @return true, if pod was evicted and operator is configured to restart evicted pods
   *
   */
  public static boolean shouldRestartEvictedPod(V1Pod pod) {
    return isEvicted(pod) && TuningParameters.getInstance().isRestartEvictedPods();
  }

  /**
   * Return true if the pod has unschedulable condition.
   * @param pod  Kubernetes Pod
   * @return true if the pod is unschedulable
   */
  public static boolean hasUnSchedulableCondition(V1Pod pod) {
    return Optional.ofNullable(pod)
            .filter(PodHelper::isPending)
            .map(V1Pod::getStatus)
            .map(V1PodStatus::getConditions)
            .orElse(Collections.emptyList())
            .stream()
            .anyMatch(PodHelper::isUnSchedulableTheReason);
  }

  /**
   * Returns the domain UID associated with the specified pod.
   * @param pod the pod
   */
  public static String getPodDomainUid(V1Pod pod) {
    return KubernetesUtils.getDomainUidLabel(Optional.ofNullable(pod).map(V1Pod::getMetadata).orElse(null));
  }

  /**
   * Returns the pod's cluster-name.
   * The cluster-name is the value of the weblogic.clusterName label in the given pod.
   *
   * @param pod the pod
   * @return cluster name.
   */
  public static String getPodClusterName(V1Pod pod) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(labels -> labels.get(CLUSTERNAME_LABEL)).orElse(null);
  }

  /**
   * get pod's server name.
   * @param pod pod
   * @return server name
   */
  public static String getPodServerName(V1Pod pod) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(labels -> labels.get(SERVERNAME_LABEL)).orElse(null);
  }

  /**
   * get pod's label value for a label name.
   * @param pod pod
   * @param labelName label name
   * @return label value
   */
  public static String getPodLabel(V1Pod pod, String labelName) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getMetadata)
        .map(V1ObjectMeta::getLabels)
        .map(m -> m.get(labelName))
        .orElse(null);
  }

  private static boolean hasLabel(V1Pod pod, String label) {
    return Optional.ofNullable(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getLabels)
        .map(l -> l.containsKey(label)).orElse(false);
  }

  private static Step patchPodLabel(V1Pod pod, String label, Step next) {
    if (!hasLabel(pod, label)) {
      JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
      patchBuilder.add("/metadata/labels/" + label, "true");
      V1ObjectMeta meta = pod.getMetadata();
      return RequestBuilder.POD.patch(meta.getNamespace(), meta.getName(),
              V1Patch.PATCH_FORMAT_JSON_PATCH,
              new V1Patch(patchBuilder.build().toString()), patchResponse(next));
    }
    return next;
  }

  /**
   * get pod's annotation value for a annotation name.
   * @param pod pod
   * @param annotationName annotation name
   * @return annotation value
   */
  public static String getPodAnnotation(V1Pod pod, String annotationName) {
    return Optional.ofNullable(pod)
            .map(V1Pod::getMetadata)
            .map(V1ObjectMeta::getAnnotations)
            .map(m -> m.get(annotationName))
            .orElse(null);
  }

  private static boolean hasAnnotation(V1Pod pod, String annotation) {
    return Optional.ofNullable(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getAnnotations)
            .map(l -> l.containsKey(annotation)).orElse(false);
  }

  private static Step patchPodAnnotation(V1Pod pod, String annotation, Step next) {
    return patchPodAnnotation(pod, annotation, "true", next);
  }

  private static Step patchPodAnnotation(V1Pod pod, String annotation, String value, Step next) {
    if (!hasAnnotation(pod, annotation)) {
      JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
      patchBuilder.add("/metadata/annotations/" + annotation, value);
      V1ObjectMeta meta = pod.getMetadata();
      return RequestBuilder.POD.patch(meta.getNamespace(), meta.getName(),
              V1Patch.PATCH_FORMAT_JSON_PATCH,
              new V1Patch(patchBuilder.build().toString()), patchResponse(next));
    }
    return next;
  }

  private static ResponseStep<V1Pod> patchResponse(Step next) {
    return new PatchPodResponseStep(next);
  }

  private static class PatchPodResponseStep extends ResponseStep<V1Pod> {
    PatchPodResponseStep(Step next) {
      super(next);
    }

    @Override
    public Result onSuccess(Packet packet, KubernetesApiResponse<V1Pod> callResponse) {
      DomainPresenceInfo info = (DomainPresenceInfo)  packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      V1Pod pod = callResponse.getObject();
      info.setServerPod(getPodServerName(pod), pod);
      return doNext(packet);
    }

    @Override
    public Result onFailure(Packet packet, KubernetesApiResponse<V1Pod> callResponse) {
      if (callResponse.getHttpStatusCode() == HTTP_NOT_FOUND) {
        return doNext(packet);
      }
      return super.onFailure(packet, callResponse);
    }
  }

  /**
   * Annotate pod as needing to shut down.
   * @param pod Pod
   * @param next Next step
   * @return Step that will check for existing annotation and add if it is missing
   */
  public static Step annotatePodAsNeedingToShutdown(V1Pod pod, String value, Step next) {
    return patchPodAnnotation(pod, LabelConstants.TO_BE_SHUTDOWN_LABEL, value, next);
  }

  /**
   * Check if the pod is already annotated for shut down.
   * @param pod Pod
   * @return true, if the pod is already annotated.
   */
  public static boolean isPodAlreadyAnnotatedForShutdown(V1Pod pod) {
    return !Objects.isNull(getPodShutdownAnnotation(pod));
  }

  public static String getPodShutdownAnnotation(V1Pod pod) {
    return getPodAnnotation(pod, LabelConstants.TO_BE_SHUTDOWN_LABEL);
  }

  /**
   * Get the message from the pod's status.
   * @param pod pod
   * @return Message string from the pod's status
   */
  public static String getPodStatusMessage(V1Pod pod) {
    return Optional.ofNullable(pod)
        .map(V1Pod::getStatus)
        .map(V1PodStatus::getMessage)
        .orElse(null);
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

  /**
   * Factory for {@link Step} that checks that admin server pod is ready.
   *
   * @param next Next processing step
   * @return Step for checking ready status for admin server pod
   */
  public static Step createAdminReadyStep(Step next) {
    return new AdminPodReadyStep(next);
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
   * @param isMustWait if true, then step will not progress until server pod is fully gone
   * @param next Next processing step
   * @return Step for deleting server pod
   */
  public static Step deletePodStep(String serverName, boolean isMustWait, Step next) {
    return new DeletePodStep(serverName, isMustWait, next);
  }

  /**
   * Create a copy of the list of V1EnvVar environment variables.
   *
   * @param envVars list of environment variables to copy
   * @return List containing a copy of the original list.
   */
  public static List<V1EnvVar> createCopy(List<V1EnvVar> envVars) {
    ArrayList<V1EnvVar> copy = new ArrayList<>();
    if (envVars != null) {
      for (V1EnvVar envVar : envVars) {
        // note that a deep copy of valueFrom is not needed here as, unlike with value, the
        // new V1EnvVarFrom objects would be created by the doDeepSubstitutions() method in
        // StepContextBase class.
        copy.add(new V1EnvVarBuilder(envVar).build());
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
    EffectiveServerSpec getServerSpec() {
      return getDomain().getAdminServerSpec();
    }

    @Override
    V1SecurityContext getInitContainerSecurityContext() {
      if (getServerSpec().getContainerSecurityContext() != null) {
        return getServerSpec().getContainerSecurityContext();
      }
      if (getPodSecurityContext().equals(PodSecurityHelper.getDefaultPodSecurityContext())) {
        return PodSecurityHelper.getDefaultContainerSecurityContext();
      }
      return getEffectiveSecurityContext(getPodSecurityContext());
    }

    @Override
    V1PodSecurityContext getPodSecurityContext() {
      return getServerSpec().getPodSecurityContext();
    }

    @Override
    Integer getOldMetricsPort() {
      return getAsPort();
    }

    @Override
    String getServerName() {
      return getAsName();
    }

    @Override
    Step createNewPod(Step next) {
      return createPod(next);
    }

    @Override
    Step replaceCurrentPod(V1Pod pod, Step next) {
      if (MakeRightDomainOperation.isInspectionRequired(packet)) {
        return MakeRightDomainOperation.createStepsToRerunWithIntrospection(packet);
      } else {
        return createCyclePodStep(pod, next);
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
    protected V1PodSpec createSpec() {
      return super.createSpec().hostname(super.getPodName());
    }

    @Override
    List<V1EnvVar> getConfiguredEnvVars() {
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
      CoreDelegate delegate = (CoreDelegate) packet.get(ProcessingConstants.DELEGATE_COMPONENT_NAME);
      return new Certificates(delegate).getOperatorInternalCertificateData();
    }

    @Override
    Step createCycleEndStep(Step next) {
      return next;
    }
  }

  static class AdminPodStep extends Step {

    AdminPodStep(Step next) {
      super(next);
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      PodStepContext context = new AdminPodStepContext(this, packet);

      return doNext(context.verifyPod(getNext()), packet);
    }

  }

  static class AdminPodReadyStep extends Step {

    AdminPodReadyStep(Step next) {
      super(next);
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      WlsDomainConfig domainTopology =
              (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
      V1Pod adminPod = info.getServerPod(domainTopology.getAdminServerName());

      if (adminPod == null || !isPodReady(adminPod)) {
        // requeue to wait for admin pod to be ready
        return doRequeue(packet);
      }

      return doNext(packet);
    }

    protected boolean isPodReady(V1Pod result) {
      return result != null && !PodHelper.isDeleting(result) && PodHelper.isReady(result);
    }
  }

  static class ManagedPodStepContext extends PodStepContext {

    private final String clusterName;
    private final Packet packet;

    ManagedPodStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);
      this.packet = packet;
      clusterName = (String) packet.get(ProcessingConstants.CLUSTER_NAME);

      init();
    }

    @Override
    EffectiveServerSpec getServerSpec() {
      return info.getServer(getServerName(), getClusterName());
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
    Integer getOldMetricsPort() {
      return getListenPort();
    }

    @Override
    String getServerName() {
      return scan.getName();
    }

    @Override
    // let the pod rolling step update the pod
    Step replaceCurrentPod(V1Pod pod, Step next) {
      return annotatePodAsNeedingToRoll(pod, new DeferProcessing(pod, next));
    }

    private class DeferProcessing extends Step {
      private final V1Pod pod;

      DeferProcessing(V1Pod pod, Step next) {
        super(next);
        this.pod = pod;
      }

      @NotNull
      @Override
      public Result apply(Packet packet) {
        deferProcessing(createCyclePodStep(pod, getNext()));
        return doEnd(packet);
      }
    }

    @Override
    Step createCycleEndStep(Step next) {
      return new CycleEndStep(next);
    }

    private class CycleEndStep extends Step {

      public CycleEndStep(Step next) {
        super(next);
      }

      @Override
      public @Nonnull Result apply(Packet packet) {
        removeFromServersMarkedForRollMap();
        return doNext(packet);
      }

      private Map<String, Fiber.StepAndPacket> serversMarkedForRoll(Packet packet) {
        return DomainPresenceInfo.fromPacket(packet)
            .map(DomainPresenceInfo::getServersToRoll)
            .orElse(Collections.emptyMap());
      }

      private void removeFromServersMarkedForRollMap() {
        synchronized (packet) {
          Optional.ofNullable(serversMarkedForRoll(packet)).ifPresent(m -> m.remove(getServerName()));
        }
      }
    }

    private void deferProcessing(Step deferredStep) {
      synchronized (packet) {
        Optional.ofNullable(getServersToRoll()).ifPresent(r -> r.put(getServerName(), createRollRequest(deferredStep)));
      }
    }

    // Patch the pod to indicate a pending roll.
    private Step annotatePodAsNeedingToRoll(V1Pod pod, Step next) {
      return patchPodAnnotation(pod, LabelConstants.TO_BE_ROLLED_LABEL, next);
    }

    private Fiber.StepAndPacket createRollRequest(Step deferredStep) {
      return new Fiber.StepAndPacket(deferredStep, packet.copy());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Fiber.StepAndPacket> getServersToRoll() {
      return (Map<String, Fiber.StepAndPacket>) packet.get(SERVERS_TO_ROLL);
    }

    @Override
    Step createNewPod(Step next) {
      return createPod(next);
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
    V1SecurityContext getInitContainerSecurityContext() {
      if (getServerSpec().getContainerSecurityContext() != null) {
        return getServerSpec().getContainerSecurityContext();
      }
      if (getPodSecurityContext().equals(PodSecurityHelper.getDefaultPodSecurityContext())) {
        return PodSecurityHelper.getDefaultContainerSecurityContext();
      }
      return getEffectiveSecurityContext(getPodSecurityContext());
    }

    @Override
    V1PodSecurityContext getPodSecurityContext() {
      return getServerSpec().getPodSecurityContext();
    }

    @Override
    @SuppressWarnings("unchecked")
    List<V1EnvVar> getConfiguredEnvVars() {
      List<V1EnvVar> envVars = createCopy((List<V1EnvVar>) packet.get(ProcessingConstants.ENVVARS));

      List<V1EnvVar> vars = new ArrayList<>(envVars);
      addStartupEnvVars(vars);
      return vars;
    }
  }

  static class ManagedPodStep extends Step {
    ManagedPodStep(Step next) {
      super(next);
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      ManagedPodStepContext context = new ManagedPodStepContext(this, packet);

      return doNext(context.verifyPod(getNext()), packet);
    }
  }

  private static class DeletePodStep extends Step {
    private final String serverName;
    private final boolean isMustWait;

    DeletePodStep(String serverName, boolean isMustWait, Step next) {
      super(next);
      this.serverName = serverName;
      this.isMustWait = isMustWait;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      final DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      final V1Pod oldPod = info.getServerPod(serverName);

      if (oldPod == null || info.getDomain() == null) {
        return doNext(packet);
      } else {
        final String clusterName = getClusterName(oldPod);
        final String name = oldPod.getMetadata().getName();
        long gracePeriodSeconds = getGracePeriodSeconds(info, clusterName);
        if (isServerShutdown(getServerStateFromInfo(info, serverName))) {
          gracePeriodSeconds = 0;
        }

        return doNext(
            deletePod(name, info.getNamespace(), isMustWait, gracePeriodSeconds, getNext()),
            packet);
      }
    }

    @Nonnull
    private Boolean isServerShutdown(String serverState) {
      return Optional.ofNullable(serverState).map(s -> SHUTDOWN_STATE.equals(s) || UNKNOWN_STATE.equals(s))
          .orElse(false);
    }

    @Nullable
    private String getClusterName(V1Pod oldPod) {
      return Optional.ofNullable(oldPod.getMetadata().getLabels()).map(l -> l.get(CLUSTERNAME_LABEL)).orElse(null);
    }

    private long getGracePeriodSeconds(DomainPresenceInfo info, String clusterName) {
      return Optional.ofNullable(info.getServer(serverName, clusterName))
          .map(this::getConfiguredGracePeriodSeconds)
          .orElse(Shutdown.DEFAULT_TIMEOUT);
    }

    private String getServerStateFromInfo(DomainPresenceInfo info, String serverName) {
      return Optional.ofNullable(info.getLastKnownServerStatus(serverName))
          .map(LastKnownStatus::getStatus).orElse(getServerState(info.getDomain(), serverName));
    }

    // We add a 10-second fudge factor here to account for the fact that WLST takes
    // ~6 seconds to start, so along with any other delay in connecting and issuing
    // the shutdown, the actual server instance has the full configured timeout to
    // gracefully shutdown before the container is destroyed by this timeout.
    // We will remove this fudge factor when the operator connects via REST to shut down
    // the server instance.
    private long getConfiguredGracePeriodSeconds(EffectiveServerSpec effectiveServerSpec) {
      return effectiveServerSpec.getShutdown().getTimeoutSeconds() + DEFAULT_ADDITIONAL_DELETE_TIME;
    }

    private Step deletePod(String name, String namespace, boolean isMustWait, long gracePeriodSeconds, Step next) {
      Step conflictStep = RequestBuilder.POD.get(namespace, name, new DefaultResponseStep<>(next) {
        @Override
        public Result onSuccess(Packet packet, KubernetesApiResponse<V1Pod> callResponse) {
          V1Pod pod = callResponse.getObject();

          if (pod != null && !PodHelper.isDeleting(pod)) {
            // pod still needs to be deleted
            return doNext(DeletePodStep.this, packet);
          }
          return super.onSuccess(packet, callResponse);
        }
      });

      DeleteOptions deleteOptions = (DeleteOptions) new DeleteOptions().gracePeriodSeconds(gracePeriodSeconds);
      return RequestBuilder.POD.delete(namespace, name, deleteOptions,
              new DefaultResponseStep<V1Pod>(conflictStep, next) {
          public Result onSuccess(Packet packet, KubernetesApiResponse<V1Pod> callResponse) {
            DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
            if (callResponse.getHttpStatusCode() == HTTP_NOT_FOUND) {
              info.setServerPod(serverName, null);
            } else if (isMustWait) {
              info.setServerPod(serverName, callResponse.getObject());
              // requeue to wait for pod to be deleted and gone
              return doRequeue(packet);
            }
            return doNext(packet);
          }
        });
    }
  }
}

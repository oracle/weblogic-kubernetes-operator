// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.ServerSpec;

public class PodHelper {

  private PodHelper() {}

  static class AdminPodStepContext extends PodStepContext {
    private static final String INTERNAL_OPERATOR_CERT_FILE = "internalOperatorCert";
    private static final String INTERNAL_OPERATOR_CERT_ENV = "INTERNAL_OPERATOR_CERT";

    AdminPodStepContext(Step conflictStep, Packet packet) {
      super(conflictStep, packet);

      init();
    }

    @Override
    ServerSpec getServerSpec() {
      return getDomain().getAdminServerSpec();
    }

    @Override
    Integer getDefaultPort() {
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
    Step replaceCurrentPod(Step next) {
      return createCyclePodStep(next);
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
    protected V1PodSpec createSpec(TuningParameters tuningParameters) {
      return super.createSpec(tuningParameters).hostname(getPodName());
    }

    @Override
    List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters) {
      List<V1EnvVar> vars = new ArrayList<>(getServerSpec().getEnvironmentVariables());
      addEnvVar(vars, INTERNAL_OPERATOR_CERT_ENV, getInternalOperatorCertFile(tuningParameters));
      overrideContainerWeblogicEnvVars(vars);
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

    private String getInternalOperatorCertFile(TuningParameters tuningParameters) {
      return tuningParameters.get(INTERNAL_OPERATOR_CERT_FILE);
    }
  }

  /**
   * Factory for {@link Step} that creates admin server pod
   *
   * @param next Next processing step
   * @return Step for creating admin server pod
   */
  public static Step createAdminPodStep(Step next) {
    return new AdminPodStep(next);
  }

  static class AdminPodStep extends Step {

    AdminPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      PodStepContext context = new AdminPodStepContext(this, packet);

      return doNext(context.verifyPersistentVolume(context.verifyPod(getNext())), packet);
    }
  }

  static void addToPacket(Packet packet, PodAwaiterStepFactory pw) {
    packet
        .getComponents()
        .put(
            ProcessingConstants.PODWATCHER_COMPONENT_NAME,
            Component.createFor(PodAwaiterStepFactory.class, pw));
  }

  static PodAwaiterStepFactory getPodAwaiterStepFactory(Packet packet) {
    return packet.getSPI(PodAwaiterStepFactory.class);
  }

  /**
   * Factory for {@link Step} that creates managed server pod
   *
   * @param next Next processing step
   * @return Step for creating managed server pod
   */
  public static Step createManagedPodStep(Step next) {
    return new ManagedPodStep(next);
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
    Integer getDefaultPort() {
      return scan.getListenPort();
    }

    @Override
    String getServerName() {
      return scan.getName();
    }

    @Override
    // let the pod rolling step update the pod
    Step replaceCurrentPod(Step next) {
      synchronized (packet) {
        @SuppressWarnings("unchecked")
        Map<String, Step.StepAndPacket> rolling =
            (Map<String, Step.StepAndPacket>) packet.get(ProcessingConstants.SERVERS_TO_ROLL);
        if (rolling != null) {
          rolling.put(
              getServerName(),
              new Step.StepAndPacket(
                  DomainStatusUpdater.createProgressingStep(
                      DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON,
                      false,
                      createCyclePodStep(next)),
                  packet.clone()));
        }
      }
      return null;
    }

    @Override
    Step createNewPod(Step next) {
      return DomainStatusUpdater.createProgressingStep(
          DomainStatusUpdater.MANAGED_SERVERS_STARTING_PROGRESS_REASON, false, createPod(next));
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
        metadata.putLabelsItem(LabelConstants.CLUSTERNAME_LABEL, getClusterName());
      }
      return metadata;
    }

    private String getClusterName() {
      return clusterName;
    }

    @Override
    protected List<String> getContainerCommand() {
      return new ArrayList<>(super.getContainerCommand());
    }

    @Override
    @SuppressWarnings("unchecked")
    List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters) {
      List<V1EnvVar> envVars = (List<V1EnvVar>) packet.get(ProcessingConstants.ENVVARS);

      List<V1EnvVar> vars = new ArrayList<>();
      if (envVars != null) {
        vars.addAll(envVars);
      }
      overrideContainerWeblogicEnvVars(vars);
      return vars;
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

  /**
   * Factory for {@link Step} that deletes server pod
   *
   * @param sko Server Kubernetes Objects
   * @param next Next processing step
   * @return Step for deleting server pod
   */
  public static Step deletePodStep(ServerKubernetesObjects sko, Step next) {
    return new DeletePodStep(sko, next);
  }

  private static class DeletePodStep extends Step {
    private final ServerKubernetesObjects sko;

    DeletePodStep(ServerKubernetesObjects sko, Step next) {
      super(next);
      this.sko = sko;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      V1Pod oldPod = removePodFromRecord();

      if (oldPod != null) {
        String name = oldPod.getMetadata().getName();
        return doNext(deletePod(name, info.getNamespace(), getNext()), packet);
      } else {
        return doNext(packet);
      }
    }

    // Set pod to null so that watcher doesn't try to recreate pod
    private V1Pod removePodFromRecord() {
      return sko.getPod().getAndSet(null);
    }

    private Step deletePod(String name, String namespace, Step next) {
      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      return new CallBuilder()
          .deletePodAsync(name, namespace, deleteOptions, new DefaultResponseStep<>(next));
    }
  }
}

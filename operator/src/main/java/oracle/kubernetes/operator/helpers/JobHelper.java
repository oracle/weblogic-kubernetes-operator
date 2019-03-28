// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import java.util.List;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.steps.ManagedServersUpStep;
import oracle.kubernetes.operator.steps.WatchDomainIntrospectorJobReadyStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;

public class JobHelper {

  static final String START_TIME = "WlsRetriever-startTime";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private JobHelper() {}

  static String createJobName(String domainUID) {
    return LegalNames.toJobIntrospectorName(domainUID);
  }

  static class DomainIntrospectorJobStepContext extends JobStepContext {
    private final DomainPresenceInfo info;

    DomainIntrospectorJobStepContext(DomainPresenceInfo info, Packet packet) {
      super(packet);
      this.info = info;

      init();
    }

    /**
     * Creates the specified new pod and performs any additional needed processing.
     *
     * @param next the next step to perform after the pod creation is complete.
     * @return a step to be scheduled.
     */
    @Override
    Step createNewJob(Step next) {
      return createJob(next);
    }

    @Override
    String getJobCreatedMessageKey() {
      return MessageKeys.JOB_CREATED;
    }

    @Override
    String getJobName() {
      return LegalNames.toJobIntrospectorName(getDomainUID());
    }

    Domain getDomain() {
      return info.getDomain();
    }

    @Override
    protected List<V1Volume> getAdditionalVolumes() {
      return getDomain().getSpec().getAdditionalVolumes();
    }

    @Override
    protected List<V1VolumeMount> getAdditionalVolumeMounts() {
      return getDomain().getSpec().getAdditionalVolumeMounts();
    }

    @Override
    List<V1EnvVar> getConfiguredEnvVars(TuningParameters tuningParameters) {
      // Pod for introspector job would use same environment variables as for admin server
      List<V1EnvVar> vars =
          PodHelper.createCopy(getDomain().getAdminServerSpec().getEnvironmentVariables());

      addEnvVar(vars, "NAMESPACE", getNamespace());
      addEnvVar(vars, "DOMAIN_UID", getDomainUID());
      addEnvVar(vars, "DOMAIN_HOME", getDomainHome());
      addEnvVar(vars, "NODEMGR_HOME", getNodeManagerHome());
      addEnvVar(vars, "LOG_HOME", getEffectiveLogHome());
      addEnvVar(vars, "INTROSPECT_HOME", getIntrospectHome());
      addEnvVar(vars, "SERVER_OUT_IN_POD_LOG", getIncludeServerOutInPodLog());
      addEnvVar(vars, "CREDENTIALS_SECRET_NAME", getWebLogicCredentialsSecretName());

      return vars;
    }
  }

  /**
   * Factory for {@link Step} that creates WebLogic domain introspector job.
   *
   * @param next Next processing step
   * @return Step for creating job
   */
  public static Step createDomainIntrospectorJobStep(Step next) {

    return new DomainIntrospectorJobStep(next);
  }

  static class DomainIntrospectorJobStep extends Step {

    DomainIntrospectorJobStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      if (runIntrospector(packet, info)) {
        JobStepContext context = new DomainIntrospectorJobStepContext(info, packet);

        packet.putIfAbsent(START_TIME, System.currentTimeMillis());

        return doNext(
            context.createNewJob(
                readDomainIntrospectorPodLogStep(
                    ConfigMapHelper.createSitConfigMapStep(getNext()))),
            packet);
      }

      return doNext(getNext(), packet);
    }
  }

  private static boolean runIntrospector(Packet packet, DomainPresenceInfo info) {
    WlsDomainConfig topology = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    LOGGER.fine("runIntrospector topology: " + topology);
    LOGGER.fine("runningServersCount: " + runningServersCount(info));
    LOGGER.fine("creatingServers: " + creatingServers(info));
    return topology == null || isBringingUpNewDomain(info);
  }

  private static boolean isBringingUpNewDomain(DomainPresenceInfo info) {
    return runningServersCount(info) == 0 && creatingServers(info);
  }

  private static int runningServersCount(DomainPresenceInfo info) {
    return ManagedServersUpStep.getRunningServers(info).size();
  }

  /**
   * TODO: Enhance determination of when we believe we're creating WLS managed server pods.
   *
   * @param info the domain presence info
   * @return True, if creating servers
   */
  static boolean creatingServers(DomainPresenceInfo info) {
    Domain dom = info.getDomain();
    DomainSpec spec = dom.getSpec();
    List<Cluster> clusters = spec.getClusters();
    List<ManagedServer> servers = spec.getManagedServers();

    // Are we starting a cluster?
    // NOTE: clusterServerStartPolicy == null indicates default policy
    for (Cluster cluster : clusters) {
      int replicaCount = dom.getReplicaCount(cluster.getClusterName());
      String clusterServerStartPolicy = cluster.getServerStartPolicy();
      LOGGER.fine(
          "Start Policy: "
              + clusterServerStartPolicy
              + ", replicaCount: "
              + replicaCount
              + " for cluster: "
              + cluster);
      if ((clusterServerStartPolicy == null
              || !clusterServerStartPolicy.equals(ConfigurationConstants.START_NEVER))
          && replicaCount > 0) {
        return true;
      }
    }

    // If Domain level Server Start Policy = ALWAYS, IF_NEEDED or ADMIN_ONLY then we most likely
    // will start a server pod
    // NOTE: domainServerStartPolicy == null indicates default policy
    String domainServerStartPolicy = dom.getSpec().getServerStartPolicy();
    if (domainServerStartPolicy == null
        || !domainServerStartPolicy.equals(ConfigurationConstants.START_NEVER)) {
      return true;
    }

    // Are we starting any explicitly specified individual server?
    // NOTE: serverStartPolicy == null indicates default policy
    for (ManagedServer server : servers) {
      String serverStartPolicy = server.getServerStartPolicy();
      if (serverStartPolicy == null
          || !serverStartPolicy.equals(ConfigurationConstants.START_NEVER)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Factory for {@link Step} that deletes WebLogic domain introspector job.
   *
   * @param domainUID The unique identifier assigned to the Weblogic domain when it was registered
   * @param namespace Namespace
   * @param next Next processing step
   * @return Step for deleting the domain introsepctor jod
   */
  public static Step deleteDomainIntrospectorJobStep(
      String domainUID, String namespace, Step next) {
    return new DeleteIntrospectorJobStep(domainUID, namespace, next);
  }

  private static class DeleteIntrospectorJobStep extends Step {
    private String domainUID;
    private String namespace;

    DeleteIntrospectorJobStep(String domainUID, String namespace, Step next) {
      super(next);
      this.domainUID = domainUID;
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(deleteJob(getNext()), packet);
    }

    String getJobDeletedMessageKey() {
      return MessageKeys.JOB_DELETED;
    }

    void logJobDeleted(String domainUID, String namespace, String jobName) {
      LOGGER.info(getJobDeletedMessageKey(), domainUID, namespace, jobName);
    }

    private Step deleteJob(Step next) {
      String jobName = JobHelper.createJobName(this.domainUID);
      logJobDeleted(this.domainUID, namespace, jobName);
      return new CallBuilder()
          .deleteJobAsync(
              jobName,
              this.namespace,
              new V1DeleteOptions().propagationPolicy("Foreground"),
              new DefaultResponseStep<>(next));
    }
  }

  private static Step createWatchDomainIntrospectorJobReadyStep(Step next) {
    return new WatchDomainIntrospectorJobReadyStep(next);
  }

  /**
   * Factory for {@link Step} that reads WebLogic domain introspector job results from pod's log.
   *
   * @param next Next processing step
   * @return Step for reading WebLogic domain introspector pod log
   */
  private static Step readDomainIntrospectorPodLogStep(Step next) {
    return createWatchDomainIntrospectorJobReadyStep(
        readDomainIntrospectorPodStep(new ReadDomainIntrospectorPodLogStep(next)));
  }

  private static class ReadDomainIntrospectorPodLogStep extends Step {

    ReadDomainIntrospectorPodLogStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      String namespace = info.getNamespace();

      String jobPodName = (String) packet.get(ProcessingConstants.JOB_POD_NAME);

      return doNext(readDomainIntrospectorPodLog(jobPodName, namespace, getNext()), packet);
    }

    private Step readDomainIntrospectorPodLog(String jobPodName, String namespace, Step next) {
      return new CallBuilder()
          .readPodLogAsync(
              jobPodName, namespace, new ReadDomainIntrospectorPodLogResponseStep(next));
    }
  }

  private static class ReadDomainIntrospectorPodLogResponseStep extends ResponseStep<String> {
    ReadDomainIntrospectorPodLogResponseStep(Step nextStep) {
      super(nextStep);
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<String> callResponse) {
      cleanupJobArtifacts(packet);
      return super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<String> callResponse) {
      String result = callResponse.getResult();

      // Log output to Operator log
      LOGGER.fine("+++++ ReadDomainIntrospectorPodLogResponseStep: \n" + result);

      V1Job domainIntrospectorJob = (V1Job) packet.get(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);
      if (domainIntrospectorJob != null && JobWatcher.isComplete(domainIntrospectorJob)) {
        if (result != null) {
          packet.put(ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT, result);
        }

        // Delete the job once we've successfully read the result
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        java.lang.String domainUID = info.getDomain().getDomainUID();
        java.lang.String namespace = info.getNamespace();

        cleanupJobArtifacts(packet);

        return doNext(
            JobHelper.deleteDomainIntrospectorJobStep(domainUID, namespace, getNext()), packet);
      }

      return onFailure(packet, callResponse);
    }

    private void cleanupJobArtifacts(Packet packet) {
      packet.remove(ProcessingConstants.JOB_POD_NAME);
      packet.remove(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);
    }
  }

  /**
   * Factory for {@link Step} that reads WebLogic domain introspector pod.
   *
   * @param next Next processing step
   * @return Step for reading WebLogic domain introspector pod
   */
  private static Step readDomainIntrospectorPodStep(Step next) {
    return new ReadDomainIntrospectorPodStep(next);
  }

  private static class ReadDomainIntrospectorPodStep extends Step {

    ReadDomainIntrospectorPodStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      String domainUID = info.getDomain().getDomainUID();
      String namespace = info.getNamespace();

      return doNext(readDomainIntrospectorPod(domainUID, namespace, getNext()), packet);
    }

    private Step readDomainIntrospectorPod(String domainUID, String namespace, Step next) {
      return new CallBuilder()
          .withLabelSelectors(LabelConstants.JOBNAME_LABEL)
          .listPodAsync(namespace, new PodListStep(domainUID, namespace, next));
    }
  }

  private static class PodListStep extends ResponseStep<V1PodList> {
    private final String domainUID;

    PodListStep(String domainUID, String ns, Step next) {
      super(next);
      this.domainUID = domainUID;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1PodList> callResponse) {
      return super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1PodList> callResponse) {
      String jobNamePrefix = createJobName(domainUID);
      V1PodList result = callResponse.getResult();
      if (result != null) {
        for (V1Pod pod : result.getItems()) {
          if (pod.getMetadata().getName().startsWith(jobNamePrefix)) {
            LOGGER.fine("+++++ JobHelper.PodListStep pod: " + pod.toString());
            packet.put(ProcessingConstants.JOB_POD_NAME, pod.getMetadata().getName());
          }
        }
      }

      return doNext(packet);
    }
  }
}

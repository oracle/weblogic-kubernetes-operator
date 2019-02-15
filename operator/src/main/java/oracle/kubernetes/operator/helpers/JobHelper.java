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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.validation.constraints.NotNull;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
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
import oracle.kubernetes.weblogic.domain.v2.Cluster;
import oracle.kubernetes.weblogic.domain.v2.ConfigurationConstants;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainSpec;
import oracle.kubernetes.weblogic.domain.v2.ManagedServer;

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
          new ArrayList<>(getDomain().getAdminServerSpec().getEnvironmentVariables());

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
   * Factory for {@link Step} that creates WebLogic domain introspector job
   *
   * @param tuning Watch tuning parameters
   * @param next Next processing step
   * @param jws Map of JobWatcher objects, keyed by the string value of the name of a namespace
   * @param isStopping
   * @return Step for creating job
   */
  public static Step createDomainIntrospectorJobStep(
      WatchTuning tuning,
      Step next,
      @NotNull Map<String, JobWatcher> jws,
      @NotNull AtomicBoolean isStopping) {

    return new DomainIntrospectorJobStep(tuning, next, jws, isStopping);
  }

  static class DomainIntrospectorJobStep extends Step {
    private final WatchTuning tuning;
    private final Map<String, JobWatcher> jws;
    private final AtomicBoolean isStopping;

    DomainIntrospectorJobStep(
        WatchTuning tuning, Step next, Map<String, JobWatcher> jws, AtomicBoolean isStopping) {
      super(next);
      this.tuning = tuning;
      this.jws = jws;
      this.isStopping = isStopping;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      if (runIntrospector(packet, info)) {
        JobStepContext context = new DomainIntrospectorJobStepContext(info, packet);

        packet.putIfAbsent(START_TIME, Long.valueOf(System.currentTimeMillis()));

        return doNext(
            context.createNewJob(
                readDomainIntrospectorPodLogStep(
                    tuning, ConfigMapHelper.createSitConfigMapStep(getNext()), jws, isStopping)),
            packet);
      }

      return doNext(getNext(), packet);
    }
  }

  private static boolean runIntrospector(Packet packet, DomainPresenceInfo info) {
    WlsDomainConfig config = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
    LOGGER.fine("runIntrospector topology: " + config);
    LOGGER.fine("runningServersCount: " + runningServersCount(info));
    LOGGER.fine("creatingServers: " + creatingServers(info));
    if (config == null || (runningServersCount(info) == 0 && creatingServers(info))) {
      return true;
    }
    return false;
  }

  private static int runningServersCount(DomainPresenceInfo info) {
    return ManagedServersUpStep.getRunningServers(info).size();
  }

  /**
   * TODO: Enhance determination of when we believe we're creating WLS managed server pods
   *
   * @param info
   * @return
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
   * Factory for {@link Step} that deletes WebLogic domain introspector job
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

    protected void logJobDeleted(String domainUID, String namespace, String jobName) {
      LOGGER.info(getJobDeletedMessageKey(), domainUID, namespace, jobName);
    }

    private Step deleteJob(Step next) {
      String jobName = JobHelper.createJobName(this.domainUID);
      logJobDeleted(this.domainUID, namespace, jobName);
      Step step =
          new CallBuilder()
              .deleteJobAsync(
                  jobName,
                  this.namespace,
                  new V1DeleteOptions().propagationPolicy("Foreground"),
                  new DefaultResponseStep<>(next));
      return step;
    }
  }

  private static Step createWatchDomainIntrospectorJobReadyStep(
      WatchTuning tuning, Step next, Map<String, JobWatcher> jws, AtomicBoolean isStopping) {
    return new WatchDomainIntrospectorJobReadyStep(tuning, next, jws, isStopping);
  }

  /**
   * Factory for {@link Step} that reads WebLogic domain introspector job results from pod's log
   *
   * @param tuning Watch tuning parameters
   * @param next Next processing step
   * @return Step for reading WebLogic domain introspector pod log
   */
  static Step readDomainIntrospectorPodLogStep(
      WatchTuning tuning, Step next, Map<String, JobWatcher> jws, AtomicBoolean isStopping) {
    return createWatchDomainIntrospectorJobReadyStep(
        tuning,
        readDomainIntrospectorPodStep(new ReadDomainIntrospectorPodLogStep(next)),
        jws,
        isStopping);
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
      Step step =
          new CallBuilder()
              .readPodLogAsync(
                  jobPodName, namespace, new ReadDomainIntrospectorPodLogResponseStep(next));
      return step;
    }
  }

  private static class ReadDomainIntrospectorPodLogResponseStep extends ResponseStep<String> {
    public ReadDomainIntrospectorPodLogResponseStep(Step nextStep) {
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
   * Factory for {@link Step} that reads WebLogic domain introspector pod
   *
   * @param next Next processing step
   * @return Step for reading WebLogic domain introspector pod
   */
  public static Step readDomainIntrospectorPodStep(Step next) {
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

      Step step =
          new CallBuilder()
              .withLabelSelectors(LabelConstants.JOBNAME_LABEL)
              .listPodAsync(namespace, new PodListStep(domainUID, namespace, next));

      return step;
    }
  }

  private static class PodListStep extends ResponseStep<V1PodList> {
    private final String ns;
    private final String domainUID;

    PodListStep(String domainUID, String ns, Step next) {
      super(next);
      this.domainUID = domainUID;
      this.ns = ns;
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

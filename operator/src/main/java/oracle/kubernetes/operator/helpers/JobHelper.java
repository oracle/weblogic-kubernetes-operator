// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import java.util.ArrayList;
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
import oracle.kubernetes.operator.steps.WatchDomainIntrospectorJobReadyStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class JobHelper {

  static final String START_TIME = "WlsRetriever-startTime";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private JobHelper() {}

  static String createJobName(String domainUID) {
    return LegalNames.toJobIntrospectorName(domainUID);
  }

  static class DomainIntrospectorJobStepContext extends JobStepContext {

    DomainIntrospectorJobStepContext(Packet packet) {
      super(packet);

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

    @Override
    List<V1EnvVar> getEnvironmentVariables(TuningParameters tuningParameters) {
      List<V1EnvVar> envVarList = new ArrayList<V1EnvVar>();
      addEnvVar(envVarList, "NAMESPACE", getNamespace());
      addEnvVar(envVarList, "DOMAIN_UID", getDomainUID());
      addEnvVar(envVarList, "DOMAIN_HOME", getDomainHome());
      addEnvVar(envVarList, "NODEMGR_HOME", getNodeManagerHome());
      addEnvVar(envVarList, "LOG_HOME", getEffectiveLogHome());
      addEnvVar(envVarList, "INTROSPECT_HOME", getIntrospectHome());
      addEnvVar(envVarList, "SERVER_OUT_IN_POD_LOG", getIncludeServerOutInPodLog());

      return envVarList;
    }
  }

  /**
   * Factory for {@link Step} that creates WebLogic domain introspector job
   *
   * @param next Next processing step
   * @return Step for creating job
   */
  public static Step createDomainIntrospectorJobStep(Step next) {

    return new DomainIntrospectorJobStep(
        readDomainIntrospectorPodLogStep(ConfigMapHelper.createSitConfigMapStep(next)));
  }

  static class DomainIntrospectorJobStep extends Step {
    public DomainIntrospectorJobStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      JobStepContext context = new DomainIntrospectorJobStepContext(packet);

      packet.putIfAbsent(START_TIME, Long.valueOf(System.currentTimeMillis()));

      return doNext(context.createNewJob(getNext()), packet);
    }
  }

  /**
   * Factory for {@link Step} that deletes WebLogic domain introspector job
   *
   * @param next Next processing step
   * @return Step for deleting server pod
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

  private static Step createWatchDomainIntrospectorJobReadyStep(Step next) {
    return new WatchDomainIntrospectorJobReadyStep(next);
  }

  /**
   * Factory for {@link Step} that reads WebLogic domain introspector job results from pod's log
   *
   * @param next Next processing step
   * @return Step for reading WebLogic domain introspector pod log
   */
  public static Step readDomainIntrospectorPodLogStep(Step next) {
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
      Step step =
          new CallBuilder()
              .readPodLogAsync(
                  jobPodName, namespace, new ReadDomainIntrospectorPodLogResponseStep<>(next));
      return step;
    }
  }

  private static class ReadDomainIntrospectorPodLogResponseStep<String>
      extends ResponseStep<String> {
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

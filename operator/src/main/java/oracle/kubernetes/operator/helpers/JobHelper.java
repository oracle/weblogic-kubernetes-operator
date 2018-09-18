// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1EnvVar;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class JobHelper {

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
      return "Domain Introspector job " + getJobName() + " created";
    }

    @Override
    String getJobDeletedMessageKey() {
      return "Domain Introspector job " + getJobName() + " deleted";
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
      addEnvVar(envVarList, "LOG_HOME", getLogHome());
      addEnvVar(envVarList, "INTROSPECT_HOME", getIntrospectHome());

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
    return new DomainIntrospectorJobStep(next);
  }

  static class DomainIntrospectorJobStep extends Step {
    public DomainIntrospectorJobStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      JobStepContext context = new DomainIntrospectorJobStepContext(packet);

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

    private Step deleteJob(Step next) {
      // logJobDeleted();
      String jobName = JobHelper.createJobName(this.domainUID);
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
}

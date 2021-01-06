// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.openapi.models.V1Job;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class WatchDomainIntrospectorJobReadyStep extends Step {

  public WatchDomainIntrospectorJobReadyStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    V1Job domainIntrospectorJob = (V1Job) packet.get(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);

    if (hasNotCompleted(domainIntrospectorJob)) {
      JobAwaiterStepFactory jw = packet.getSpi(JobAwaiterStepFactory.class);
      return doNext(jw.waitForReady(domainIntrospectorJob, getNext()), packet);
    } else {
      return doNext(packet);
    }
  }

  private boolean hasNotCompleted(V1Job domainIntrospectorJob) {
    return domainIntrospectorJob != null && !JobWatcher.isComplete(domainIntrospectorJob);
  }
}

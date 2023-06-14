// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.openapi.models.V1Job;
import oracle.kubernetes.operator.JobAwaiterStepFactory;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.DomainStatusUpdater.createRemoveFailuresStep;

public class WatchDomainIntrospectorJobReadyStep extends Step {

  @Override
  public NextAction apply(Packet packet) {
    V1Job domainIntrospectorJob = (V1Job) packet.get(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);

    if (hasNotCompleted(domainIntrospectorJob)) {
      JobAwaiterStepFactory jw = packet.getSpi(JobAwaiterStepFactory.class);
      return doNext(jw.waitForReady(domainIntrospectorJob, createRemoveFailuresStep(getNext())), packet);
    } else {
      return doNext(createRemoveFailuresStep(getNext()), packet);
    }
  }

  private boolean hasNotCompleted(V1Job domainIntrospectorJob) {
    return domainIntrospectorJob != null && !JobWatcher.isComplete(domainIntrospectorJob);
  }
}

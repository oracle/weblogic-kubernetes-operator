// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1Job;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters.WatchTuning;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;

public class WatchDomainIntrospectorJobReadyStep extends Step {
  private final WatchTuning tuning;
  private final Map<String, JobWatcher> jws;
  private final AtomicBoolean isStopping;

  public WatchDomainIntrospectorJobReadyStep(
      WatchTuning tuning, Step next, Map<String, JobWatcher> jws, AtomicBoolean isStopping) {
    super(next);
    this.tuning = tuning;
    this.jws = jws;
    this.isStopping = isStopping;
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
    String namespace = info.getNamespace();
    String initialResourceVersion = info.getDomain().getMetadata().getResourceVersion();

    V1Job domainIntrospectorJob = (V1Job) packet.get(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);

    // No need to spawn a watcher if the job is already complete
    if (domainIntrospectorJob != null && !JobWatcher.isComplete(domainIntrospectorJob)) {
      JobWatcher jw = null;
      if (jws == null || !jws.containsKey(namespace)) {
        jw =
            JobWatcher.create(
                ThreadFactorySingleton.getInstance(),
                namespace,
                initialResourceVersion,
                tuning,
                isStopping != null ? isStopping : new AtomicBoolean(false));
        if (jws != null) jws.put(namespace, jw);
      } else {
        jw = jws.get(namespace);
      }
      NextAction retVal = doNext(jw.waitForReady(domainIntrospectorJob, getNext()), packet);

      return retVal;
    }

    return doNext(packet);
  }
}

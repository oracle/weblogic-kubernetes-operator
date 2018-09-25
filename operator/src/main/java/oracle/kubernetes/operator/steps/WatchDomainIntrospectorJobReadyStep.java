// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.util.Watch;
import java.util.concurrent.atomic.AtomicBoolean;
import oracle.kubernetes.operator.JobWatcher;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;

public class WatchDomainIntrospectorJobReadyStep extends Step {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public WatchDomainIntrospectorJobReadyStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
    String namespace = info.getNamespace();
    String initialResourceVersion = info.getDomain().getMetadata().getResourceVersion();

    V1Job domainIntrospectorJob = (V1Job) packet.get(ProcessingConstants.DOMAIN_INTROSPECTOR_JOB);

    if (domainIntrospectorJob != null) {
      JobWatcher jw =
          JobWatcher.create(
              ThreadFactorySingleton.getInstance(),
              namespace,
              initialResourceVersion,
              new AtomicBoolean(false));

      return doNext(jw.waitForReady(domainIntrospectorJob, getNext()), packet);
    }

    return doNext(packet);
  }

  private static void dispatchJobWatch(Watch.Response<V1Job> item) {
    V1Job j = item.object;
    if (j != null) {
      V1ObjectMeta metadata = j.getMetadata();
      String domainUID = metadata.getLabels().get(LabelConstants.DOMAINUID_LABEL);
      if (domainUID != null) {
        switch (item.type) {
          case "ADDED":
          case "MODIFIED":
            break;
          case "DELETED":
            LOGGER.info(
                MessageKeys.JOB_DELETED,
                domainUID,
                metadata.getNamespace(),
                j.getMetadata().getName());
            break;

          case "ERROR":
          default:
        }
      }
    }
  }
}

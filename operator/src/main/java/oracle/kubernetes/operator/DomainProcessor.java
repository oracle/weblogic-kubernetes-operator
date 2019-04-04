// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Event;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.Watch;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.weblogic.domain.model.Domain;

public interface DomainProcessor {

  public void makeRightDomainPresence(
      DomainPresenceInfo info,
      boolean explicitRecheck,
      boolean isDeleting,
      boolean isWillInterrupt);

  public void dispatchDomainWatch(Watch.Response<Domain> item);

  public void dispatchPodWatch(Watch.Response<V1Pod> item);

  public void dispatchServiceWatch(Watch.Response<V1Service> item);

  public void dispatchConfigMapWatch(Watch.Response<V1ConfigMap> item);

  public void dispatchEventWatch(Watch.Response<V1Event> item);

  public void stopNamespace(String ns);
}

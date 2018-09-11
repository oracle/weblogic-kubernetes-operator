// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.Collection;
import java.util.Iterator;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ExternalAdminChannelIteratorStep extends Step {
  private final DomainPresenceInfo info;
  private final Iterator<NetworkAccessPoint> it;

  public ExternalAdminChannelIteratorStep(
      DomainPresenceInfo info, Collection<NetworkAccessPoint> naps, Step next) {
    super(next);
    this.info = info;
    this.it = naps.iterator();
  }

  @Override
  public NextAction apply(Packet packet) {
    if (it.hasNext()) {
      packet.put(ProcessingConstants.SERVER_NAME, info.getDomain().getSpec().getAsName());
      packet.put(ProcessingConstants.NETWORK_ACCESS_POINT, it.next());
      Step step = ServiceHelper.createForExternalChannelStep(this);
      return doNext(step, packet);
    }
    return doNext(packet);
  }
}

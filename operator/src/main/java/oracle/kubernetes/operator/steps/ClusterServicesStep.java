// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.Scan;
import oracle.kubernetes.operator.helpers.ScanCache;
import oracle.kubernetes.operator.helpers.ServiceHelper;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ClusterServicesStep extends Step {
  private final DomainPresenceInfo info;

  public ClusterServicesStep(DomainPresenceInfo info, Step next) {
    super(next);
    this.info = info;
  }

  @Override
  public NextAction apply(Packet packet) {
    Collection<StepAndPacket> startDetails = new ArrayList<>();

    // Add cluster services
    Scan scan = ScanCache.INSTANCE.lookupScan(info.getNamespace(), info.getDomainUID());
    WlsDomainConfig config = scan != null ? scan.getWlsDomainConfig() : null;
    if (config != null) {
      for (Map.Entry<String, WlsClusterConfig> entry : config.getClusterConfigs().entrySet()) {
        Packet p = packet.clone();
        WlsClusterConfig clusterConfig = entry.getValue();
        p.put(ProcessingConstants.CLUSTER_NAME, clusterConfig.getClusterName());
        for (WlsServerConfig serverConfig : clusterConfig.getServerConfigs()) {
          p.put(ProcessingConstants.PORT, serverConfig.getListenPort());
          break;
        }

        startDetails.add(new StepAndPacket(ServiceHelper.createForClusterStep(null), p));
      }
    }

    if (startDetails.isEmpty()) {
      return doNext(packet);
    }
    return doForkJoin(getNext(), packet, startDetails);
  }
}

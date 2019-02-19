// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainStatus;
import oracle.kubernetes.weblogic.domain.v2.ServerStatus;

/**
 * After the {@link PodHelper} identifies servers that are presently running, but that are using an
 * out-of-date specification, it defers the processing of these servers to the RollingHelper. This
 * class will ensure that a minimum number of cluster members remain up, if possible, throughout the
 * rolling process.
 */
public class RollingHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private RollingHelper() {}

  /**
   * Creates an asynchronous step that completes the rolling. The rolling parameter is a map from
   * server name to a {@link StepAndPacket} that includes the asynchronous step and packet necessary
   * to roll that individual server. This will include first stopping (deleting) the existing Pod,
   * recreating the Pod with the updated specification, waiting for that new Pod to become Ready
   * and, finally, completing the server presence with necessary Service and Ingress objects, etc.
   *
   * @param rolling Map from server name to {@link Step} and {@link Packet} combination for rolling
   *     one server
   * @param next Next asynchronous step
   * @return Asynchronous step to complete rolling
   */
  public static Step rollServers(Map<String, StepAndPacket> rolling, Step next) {
    return new RollingStep(rolling, next);
  }

  private static class RollingStep extends Step {
    private final Map<String, StepAndPacket> rolling;

    private RollingStep(Map<String, StepAndPacket> rolling, Step next) {
      super(next);
      this.rolling = rolling;
    }

    @Override
    protected String getDetail() {
      return String.join(",", rolling.keySet());
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      Domain dom = info.getDomain();
      DomainStatus status = dom.getStatus();
      // These are presently Ready servers
      List<String> availableServers = new ArrayList<>();
      List<ServerStatus> ss = status != null ? status.getServers() : null;
      if (ss != null) {
        for (ServerStatus s : ss) {
          if (WebLogicConstants.RUNNING_STATE.equals(s.getState())) {
            availableServers.add(s.getServerName());
          }
        }
      }

      Collection<StepAndPacket> serversThatCanRestartNow = new ArrayList<>();
      Map<String, Collection<StepAndPacket>> clusteredRestarts = new HashMap<>();

      List<String> servers = new ArrayList<>();
      for (Map.Entry<String, StepAndPacket> entry : rolling.entrySet()) {
        // If this server isn't currently Ready, then it can be safely restarted now
        // regardless of the state of its cluster (if any)
        if (!availableServers.contains(entry.getKey())) {
          servers.add(entry.getKey());
          serversThatCanRestartNow.add(entry.getValue());
          continue;
        }

        // If this server isn't part of a cluster, then it can also be safely restarted now
        Packet p = entry.getValue().packet;
        String clusterName = (String) p.get(ProcessingConstants.CLUSTER_NAME);
        if (clusterName == null) {
          servers.add(entry.getKey());
          serversThatCanRestartNow.add(entry.getValue());
          continue;
        }

        // clustered server
        Collection<StepAndPacket> cr = clusteredRestarts.get(clusterName);
        if (cr == null) {
          cr = new ArrayList<>();
          clusteredRestarts.put(clusterName, cr);
        }
        cr.add(entry.getValue());
      }

      if (!servers.isEmpty()) {
        LOGGER.info(MessageKeys.CYCLING_SERVERS, dom.getDomainUID(), servers);
      }

      Collection<StepAndPacket> work = new ArrayList<>();
      if (!serversThatCanRestartNow.isEmpty()) {
        work.add(
            new StepAndPacket(
                new ServersThatCanRestartNowStep(serversThatCanRestartNow, null), packet));
      }

      if (!clusteredRestarts.isEmpty()) {
        for (Map.Entry<String, Collection<StepAndPacket>> entry : clusteredRestarts.entrySet()) {
          work.add(
              new StepAndPacket(
                  new RollSpecificClusterStep(entry.getKey(), entry.getValue(), null), packet));
        }
      }

      if (!work.isEmpty()) {
        return doForkJoin(getNext(), packet, work);
      }

      return doNext(packet);
    }
  }

  private static class ServersThatCanRestartNowStep extends Step {
    private final Collection<StepAndPacket> serversThatCanRestartNow;

    public ServersThatCanRestartNowStep(
        Collection<StepAndPacket> serversThatCanRestartNow, Step next) {
      super(next);
      this.serversThatCanRestartNow = serversThatCanRestartNow;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doForkJoin(getNext(), packet, serversThatCanRestartNow);
    }
  }

  private static class RollSpecificClusterStep extends Step {
    private final String clusterName;
    private final Iterator<StepAndPacket> it;

    public RollSpecificClusterStep(
        String clusterName, Collection<StepAndPacket> clusteredServerRestarts, Step next) {
      super(next);
      this.clusterName = clusterName;
      it = clusteredServerRestarts.iterator();
    }

    @Override
    public NextAction apply(Packet packet) {
      if (it.hasNext()) {
        DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
        WlsDomainConfig config = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);

        // Refresh as this is constantly changing
        Domain dom = info.getDomain();
        DomainStatus status = dom.getStatus();
        // These are presently Ready servers
        List<String> availableServers = new ArrayList<>();
        List<ServerStatus> ss = status.getServers();
        if (ss != null) {
          for (ServerStatus s : ss) {
            if (WebLogicConstants.RUNNING_STATE.equals(s.getState())) {
              availableServers.add(s.getServerName());
            }
          }
        }

        List<String> servers = new ArrayList<>();
        List<String> readyServers = new ArrayList<>();

        Collection<StepAndPacket> serversThatCanRestartNow = new ArrayList<>();
        // We will always restart at least one server
        StepAndPacket current = it.next();
        serversThatCanRestartNow.add(current);

        WlsServerConfig serverConfig =
            (WlsServerConfig) current.packet.get(ProcessingConstants.SERVER_SCAN);
        servers.add(serverConfig != null ? serverConfig.getName() : config.getAdminServerName());

        // See if we can restart more now
        if (it.hasNext()) {
          // we are already pending a restart of one server, so start count at -1
          int countReady = -1;
          WlsClusterConfig cluster = config != null ? config.getClusterConfig(clusterName) : null;
          if (cluster != null) {
            List<WlsServerConfig> serversConfigs = cluster.getServerConfigs();
            if (serversConfigs != null) {
              for (WlsServerConfig s : serversConfigs) {
                // figure out how many servers are currently ready
                if (availableServers.contains(s.getName())) {
                  readyServers.add(s.getName());
                  countReady++;
                }
              }
            }
          }

          // then add as many as possible next() entries leaving at least minimum cluster
          // availability
          while (countReady-- > dom.getMinAvailable(clusterName)) {
            current = it.next();
            serverConfig = (WlsServerConfig) current.packet.get(ProcessingConstants.SERVER_SCAN);
            String serverName = null;
            if (serverConfig != null) {
              serverName = serverConfig.getName();
            } else if (config != null) {
              serverName = config.getAdminServerName();
            }
            if (serverName != null) {
              servers.add(serverName);
            }
            serversThatCanRestartNow.add(current);
            if (!it.hasNext()) {
              break;
            }
          }
        }

        readyServers.removeAll(servers);
        LOGGER.info(MessageKeys.ROLLING_SERVERS, dom.getDomainUID(), servers, readyServers);

        return doNext(new ServersThatCanRestartNowStep(serversThatCanRestartNow, this), packet);
      }

      return doNext(packet);
    }
  }
}

// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1Pod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.PodAwaiterStepFactory;
import oracle.kubernetes.operator.ProcessingConstants;
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
import oracle.kubernetes.weblogic.domain.model.Domain;

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
      // These are presently Ready servers
      List<String> availableServers = getReadyServers(info);

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

  private static List<String> getReadyServers(DomainPresenceInfo info) {
    // These are presently Ready servers
    List<String> availableServers = new ArrayList<>();
    for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
      V1Pod pod = entry.getValue().getPod().get();
      if (pod != null && !PodHelper.isDeleting(pod) && PodHelper.getReadyStatus(pod)) {
        availableServers.add(entry.getKey());
      }
    }
    return availableServers;
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
    public String getDetail() {
      return clusterName;
    }

    @Override
    public NextAction apply(Packet packet) {
      synchronized (it) {
        if (it.hasNext()) {
          DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
          WlsDomainConfig config =
              (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);

          // Refresh as this is constantly changing
          Domain dom = info.getDomain();
          // These are presently Ready servers
          List<String> availableServers = getReadyServers(info);

          List<String> servers = new ArrayList<>();
          List<String> readyServers = new ArrayList<>();
          List<V1Pod> notReadyServers = new ArrayList<>();

          Collection<StepAndPacket> serversThatCanRestartNow = new ArrayList<>();

          int countReady = 0;
          WlsClusterConfig cluster = config != null ? config.getClusterConfig(clusterName) : null;
          if (cluster != null) {
            List<WlsServerConfig> serversConfigs = cluster.getServerConfigs();
            if (serversConfigs != null) {
              for (WlsServerConfig s : serversConfigs) {
                // figure out how many servers are currently ready
                String name = s.getName();
                if (availableServers.contains(name)) {
                  readyServers.add(s.getName());
                  countReady++;
                } else {
                  V1Pod pod = info.getServerPod(name);
                  if (pod != null) {
                    notReadyServers.add(pod);
                  }
                }
              }
            }
          }

          // then add as many as possible next() entries leaving at least minimum cluster
          // availability
          while (countReady-- > dom.getMinAvailable(clusterName)) {
            StepAndPacket current = it.next();
            WlsServerConfig serverConfig =
                (WlsServerConfig) current.packet.get(ProcessingConstants.SERVER_SCAN);
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

          if (serversThatCanRestartNow.isEmpty()) {
            // Not enough servers are ready to let us restart a server now
            if (!notReadyServers.isEmpty()) {
              PodAwaiterStepFactory pw = PodHelper.getPodAwaiterStepFactory(packet);
              Collection<StepAndPacket> waitForUnreadyServers = new ArrayList<>();
              for (V1Pod pod : notReadyServers) {
                waitForUnreadyServers.add(
                    new StepAndPacket(pw.waitForReady(pod, null), packet.clone()));
              }

              // Wait for at least one of the not-yet-ready servers to become ready
              return doForkAtLeastOne(this, packet, waitForUnreadyServers);
            } else {
              throw new IllegalStateException();
            }
          }

          readyServers.removeAll(servers);
          LOGGER.info(MessageKeys.ROLLING_SERVERS, dom.getDomainUID(), servers, readyServers);

          return doNext(new ServersThatCanRestartNowStep(serversThatCanRestartNow, this), packet);
        }
      }

      return doNext(packet);
    }
  }
}

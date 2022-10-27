// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.Step.StepAndPacket;
import oracle.kubernetes.utils.OperatorUtils;

import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;

/**
 * After the {@link PodHelper} identifies servers that are presently running, but that are using an
 * out-of-date specification, it defers the processing of these servers to the RollingHelper. This
 * class will ensure that a minimum number of cluster members remain up, if possible, throughout the
 * rolling process.
 */
public class RollingHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final long DELAY_IN_SECONDS = 1;

  private RollingHelper() {
  }

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

  private static boolean hasReadyServer(V1Pod pod) {
    return !PodHelper.isDeleting(pod) && PodHelper.hasReadyStatus(pod);
  }

  private abstract static class BaseStepContext {

    protected final Packet packet;

    private BaseStepContext(Packet packet) {
      this.packet = packet;
    }

    protected DomainPresenceInfo getInfo() {
      return DomainPresenceInfo.fromPacket(packet).orElseThrow();
    }

    protected List<String> getReadyServers() {
      return getInfo().getSelectedActiveServerNames(RollingHelper::hasReadyServer);
    }
  }

  private static class RollingStep extends Step {
    private final Map<String, StepAndPacket> rolling;

    private RollingStep(Map<String, StepAndPacket> rolling, Step next) {
      super(next);
      // sort the rolling map so servers would be restarted in order based on server names
      this.rolling = OperatorUtils.createSortedMap(rolling);
    }

    @Override
    protected String getDetail() {
      return String.join(",", rolling.keySet());
    }

    @Override
    public NextAction apply(Packet packet) {
      final StepContext context = new StepContext(packet);
      context.classifyRollingEntries(rolling);

      context.createWork(packet);
      if (context.hasNoWork()) {
        return doNext(packet);
      } else {
        return doForkJoin(getNext(), packet, context.getWork());
      }
    }

    private static class StepContext extends BaseStepContext {
      private final List<StepAndPacket> serverRestarts = new ArrayList<>();
      private final Map<String, Queue<StepAndPacket>> clusteredRestarts = new HashMap<>();
      private final Collection<StepAndPacket> work = new ArrayList<>();

      StepContext(Packet packet) {
        super(packet);
      }

      private void classifyRollingEntries(Map<String, StepAndPacket> rolling) {
        for (Map.Entry<String, StepAndPacket> rollingEntry : rolling.entrySet()) {
          if (canBeStartedImmediately(rollingEntry)) {
            recordForImmediateStart(rollingEntry);
          } else {
            recordForPerClusterRollingStart(rollingEntry);
          }
        }
      }

      private boolean canBeStartedImmediately(Map.Entry<String, StepAndPacket> rollingEntry) {
        return getClusterName(rollingEntry) == null || isServerNotReady(rollingEntry);
      }

      private String getClusterName(Map.Entry<String, StepAndPacket> rollingEntry) {
        return rollingEntry.getValue().packet.getValue(ProcessingConstants.CLUSTER_NAME);
      }

      private boolean isServerNotReady(Map.Entry<String, StepAndPacket> rollingEntry) {
        return !getReadyServers().contains(rollingEntry.getKey());
      }

      private void recordForImmediateStart(Map.Entry<String, StepAndPacket> rollingEntry) {
        serverRestarts.add(rollingEntry.getValue());
      }

      private void recordForPerClusterRollingStart(Map.Entry<String, StepAndPacket> rollingEntry) {
        clusteredRestarts.computeIfAbsent(getClusterName(rollingEntry), this::createQueue).add(rollingEntry.getValue());
      }

      private Queue<StepAndPacket> createQueue(String clusterName) {
        return new ConcurrentLinkedQueue<>();
      }

      private void createWork(Packet packet) {
        Optional.of(serverRestarts)
              .filter(this::hasRestarts)
              .map(this::createServersStep)
              .ifPresent(step -> addWork(packet, step));

        clusteredRestarts.entrySet().stream().map(this::createPerClusterStep).forEach(step -> addWork(packet, step));
      }

      private boolean hasRestarts(Collection<?> collection) {
        return !collection.isEmpty();
      }

      private Collection<StepAndPacket> getWork() {
        return work;
      }

      @Nonnull
      private Step createServersStep(Collection<StepAndPacket> serverRestarts) {
        return new ServersThatCanRestartNowStep(serverRestarts);
      }

      @Nonnull
      private RollSpecificClusterStep createPerClusterStep(Map.Entry<String, Queue<StepAndPacket>> entry) {
        return new RollSpecificClusterStep(entry.getKey(), entry.getValue());
      }

      private void addWork(Packet packet, Step step) {
        work.add(new StepAndPacket(step, packet));
      }

      private boolean hasNoWork() {
        return work.isEmpty();
      }
    }

  }

  private static class ServersThatCanRestartNowStep extends Step {
    private final Collection<StepAndPacket> serversThatCanRestartNow;

    public ServersThatCanRestartNowStep(Collection<StepAndPacket> serversThatCanRestartNow) {
      this.serversThatCanRestartNow = serversThatCanRestartNow;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doForkJoin(getNext(), packet, serversThatCanRestartNow);
    }
  }

  static class RollSpecificClusterStep extends Step {
    private final String clusterName;
    private final Queue<StepAndPacket> servers;
    private int loggedServersSize = -1;
    private String loggedReadyServers;

    public RollSpecificClusterStep(String clusterName, Queue<StepAndPacket> clusteredServerRestarts) {
      this.clusterName = clusterName;
      servers = clusteredServerRestarts;
    }

    @Override
    public String getDetail() {
      return clusterName;
    }

    List<String> getServerNames(@Nonnull Queue<StepAndPacket> stepAndPackets) {
      return stepAndPackets.stream().map(this::getServerName).collect(Collectors.toList());
    }

    String getServerName(StepAndPacket stepAndPacket) {
      return (String) Optional.ofNullable(stepAndPacket.getPacket())
        .map(p -> p.get(ProcessingConstants.SERVER_NAME))
        .orElse("");
    }

    @Override
    public NextAction apply(Packet packet) {
      StepContext context = new StepContext(packet, clusterName);
      List<String> readyServers = context.getReadyServers(packet.getValue(DOMAIN_TOPOLOGY));
      if (loggedServersSize != servers.size() || !Objects.equals(loggedReadyServers, readyServers.toString())) {
        LOGGER.info(MessageKeys.ROLLING_SERVERS,
            context.getDomainUid(), getServerNames(servers), readyServers);
        loggedServersSize = servers.size();
        loggedReadyServers = readyServers.toString();
      }

      int countToRestartNow = readyServers.size() - context.getMinAvailable(clusterName);
      Collection<StepAndPacket> restarts = new ArrayList<>();
      for (int i = 0; i < countToRestartNow; i++) {
        Optional.ofNullable(servers.poll()).ifPresent(restarts::add);
      }

      if (!restarts.isEmpty()) {
        return doForkJoin(this, packet, restarts);
      } else if (!servers.isEmpty()) {
        return doDelay(this, packet, DELAY_IN_SECONDS, TimeUnit.SECONDS);
      } else {
        return doNext(packet);
      }
    }

    private static class StepContext extends BaseStepContext {
      private final String clusterName;

      StepContext(Packet packet, String clusterName) {
        super(packet);
        this.clusterName = clusterName;
      }

      String getDomainUid() {
        return getInfo().getDomainUid();
      }
      
      private int getMinAvailable(String clusterName) {
        return getInfo().getMinAvailable(clusterName);
      }

      @Nonnull
      private List<String> getReadyServers(WlsDomainConfig config) {
        return Optional.ofNullable(config)
              .map(this::getClusterConfig)
              .map(WlsClusterConfig::getServerConfigs).orElse(Collections.emptyList()).stream()
              .map(WlsServerConfig::getName)
              .filter(this::isServerReady)
              .collect(Collectors.toList());
      }

      private WlsClusterConfig getClusterConfig(WlsDomainConfig config) {
        return config.getClusterConfig(clusterName);
      }

      private boolean isServerReady(String serverName) {
        return getReadyServers().contains(serverName);
      }
    }
  }
}

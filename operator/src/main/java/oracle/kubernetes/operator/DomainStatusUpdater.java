// Copyright 2018,2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.LabelConstants.CLUSTERNAME_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_TOPOLOGY;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.WebLogicConstants.RUNNING_STATE;
import static oracle.kubernetes.operator.WebLogicConstants.SHUTDOWN_STATE;
import static oracle.kubernetes.weblogic.domain.v2.DomainConditionType.Available;
import static oracle.kubernetes.weblogic.domain.v2.DomainConditionType.Failed;
import static oracle.kubernetes.weblogic.domain.v2.DomainConditionType.Progressing;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo.ServerStartupInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Fiber.CompletionCallback;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v2.Domain;
import oracle.kubernetes.weblogic.domain.v2.DomainCondition;
import oracle.kubernetes.weblogic.domain.v2.DomainStatus;
import oracle.kubernetes.weblogic.domain.v2.ServerHealth;
import oracle.kubernetes.weblogic.domain.v2.ServerStatus;

/**
 * Updates for status of Domain. This class has two modes: 1) Watching for Pod state changes by
 * listening to events from {@link PodWatcher} and 2) Factory for {@link Step}s that the main
 * processing flow can use to explicitly set the condition to Progressing or Failed.
 */
public class DomainStatusUpdater {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public static final String INSPECTING_DOMAIN_PROGRESS_REASON = "InspectingDomainPrescence";
  public static final String MANAGED_SERVERS_STARTING_PROGRESS_REASON = "ManagedServersStarting";

  public static final String SERVERS_READY_REASON = "ServersReady";
  public static final String ALL_STOPPED_AVAILABLE_REASON = "AllServersStopped";

  private static final String TRUE = "True";
  private static final String FALSE = "False";

  private DomainStatusUpdater() {}

  static class DomainConditionStepContext {
    private final DomainPresenceInfo info;

    DomainConditionStepContext(Packet packet) {
      info = packet.getSPI(DomainPresenceInfo.class);
    }

    DomainPresenceInfo getInfo() {
      return info;
    }

    DomainStatus getStatus() {
      return getDomain().getOrCreateStatus();
    }

    Domain getDomain() {
      return info.getDomain();
    }
  }

  /**
   * Asynchronous step to set Domain status to indicate WebLogic server status.
   *
   * @param timeoutSeconds Timeout in seconds
   * @param next Next step
   * @return Step
   */
  @SuppressWarnings("SameParameterValue")
  static Step createStatusStep(int timeoutSeconds, Step next) {
    return new StatusUpdateHookStep(timeoutSeconds, next);
  }

  private static class StatusUpdateHookStep extends Step {
    private final int timeoutSeconds;

    StatusUpdateHookStep(int timeoutSeconds, Step next) {
      super(next);
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);
      return doNext(
          ServerStatusReader.createDomainStatusReaderStep(
              info, timeoutSeconds, new StatusUpdateStep(getNext())),
          packet);
    }
  }

  static class StatusUpdateStep extends Step {
    StatusUpdateStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      StatusUpdateContext context = new StatusUpdateContext(packet);
      DomainStatus status = context.getStatus().clearModified();

      if (context.getDomain() != null) {
        status.setServers(new ArrayList<>(context.getServerStatuses().values()));
        status.setReplicas(context.getReplicaSetting());

        if (context.isHasFailedPod()) {
          status.removeConditionIf(c -> c.getType() == Available);
          status.removeConditionIf(c -> c.getType() == Progressing);
          status.addCondition(new DomainCondition(Failed).withStatus(TRUE).withReason("PodFailed"));
        } else {
          status.removeConditionIf(c -> c.getType() == Failed);
          if (context.allIntendedServersRunning()) {
            status.removeConditionIf(c -> c.getType() == Progressing);
            status.addCondition(
                new DomainCondition(Available).withStatus(TRUE).withReason(SERVERS_READY_REASON));
          }
        }
      }

      // This will control if we need to re-check states soon or if we can slow down checks
      packet.put(ProcessingConstants.STATUS_UNCHANGED, !status.isModified());

      if (status.isModified()) {
        LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getInfo().getDomainUID(), status);
      }
      LOGGER.exiting();

      return status.isModified()
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, StatusUpdateStep.this, getNext())
          : doNext(packet);
    }

    static class StatusUpdateContext extends DomainConditionStepContext {
      private final WlsDomainConfig config;
      private final Map<String, String> serverState;
      private final Map<String, ServerHealth> serverHealth;

      StatusUpdateContext(Packet packet) {
        super(packet);
        config = packet.getValue(DOMAIN_TOPOLOGY);
        serverState = packet.getValue(SERVER_STATE_MAP);
        serverHealth = packet.getValue(SERVER_HEALTH_MAP);
      }

      private boolean allIntendedServersRunning() {
        return getServerStartupInfos()
            .filter(this::shouldBeRunning)
            .map(ServerStartupInfo::getServerName)
            .noneMatch(this::isNotRunning);
      }

      private Stream<ServerStartupInfo> getServerStartupInfos() {
        return Optional.ofNullable(getInfo().getServerStartupInfo())
            .map(Collection::stream)
            .orElse(Stream.empty());
      }

      private boolean shouldBeRunning(ServerStartupInfo startupInfo) {
        return "RUNNING".equals(startupInfo.getDesiredState());
      }

      private boolean isNotRunning(@Nonnull String serverName) {
        return !RUNNING_STATE.equals(getRunningState(serverName));
      }

      private boolean isHasFailedPod() {
        return getServers().values().stream().anyMatch(this::isPodFailed);
      }

      private boolean isPodFailed(ServerKubernetesObjects sko) {
        return Optional.ofNullable(sko.getPod().get()).map(PodWatcher::isFailed).orElse(false);
      }

      Map<String, ServerStatus> getServerStatuses() {
        return getServerNames()
            .stream()
            .collect(Collectors.toMap(Function.identity(), this::createServerStatus));
      }

      private ServerStatus createServerStatus(String serverName) {
        return new ServerStatus()
            .withServerName(serverName)
            .withState(getRunningState(serverName))
            .withHealth(serverHealth.get(serverName))
            .withClusterName(getClusterName(serverName))
            .withNodeName(getNodeName(serverName));
      }

      private String getRunningState(String serverName) {
        return serverState.getOrDefault(serverName, SHUTDOWN_STATE);
      }

      Integer getReplicaSetting() {
        Collection<Long> values = getClusterCounts().values();
        if (values.size() == 1) {
          return values.iterator().next().intValue();
        } else {
          return null;
        }
      }

      private Map<String, Long> getClusterCounts() {
        return getServerNames()
            .stream()
            .map(this::getClusterNameFromPod)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
      }

      private String getNodeName(String serverName) {
        return getPod(serverName).map(p -> p.getSpec().getNodeName()).orElse(null);
      }

      private Optional<V1Pod> getPod(String serverName) {
        return Optional.ofNullable(getServers().get(serverName)).map(s -> s.getPod().get());
      }

      private String getClusterName(String serverName) {
        return Optional.ofNullable(config)
            .map(c -> c.getClusterName(serverName))
            .orElse(getClusterNameFromPod(serverName));
      }

      private String getClusterNameFromPod(String serverName) {
        return getPod(serverName)
            .map(p -> p.getMetadata().getLabels().get(CLUSTERNAME_LABEL))
            .orElse(null);
      }

      private Collection<String> getServerNames() {
        Set<String> result = new HashSet<>(getServers().keySet());
        if (config != null) {
          result.addAll(config.getServerConfigs().keySet());
        }
        return result;
      }

      private Map<String, ServerKubernetesObjects> getServers() {
        return getInfo().getServers();
      }
    }
  }

  /**
   * Asynchronous step to set Domain condition to Progressing.
   *
   * @param reason Progressing reason
   * @param isPreserveAvailable true, if existing Available=True condition should be preserved
   * @param next Next step
   * @return Step
   */
  public static Step createProgressingStep(String reason, boolean isPreserveAvailable, Step next) {
    return new ProgressingHookStep(reason, isPreserveAvailable, next);
  }

  private static class ProgressingHookStep extends Step {
    private final String reason;
    private final boolean isPreserveAvailable;

    private ProgressingHookStep(String reason, boolean isPreserveAvailable, Step next) {
      super(next);
      this.reason = reason;
      this.isPreserveAvailable = isPreserveAvailable;
    }

    @Override
    public NextAction apply(Packet packet) {
      Fiber f = Fiber.current().createChildFiber();
      Packet p = new Packet();
      p.getComponents().putAll(packet.getComponents());
      f.start(
          new ProgressingStep(reason, isPreserveAvailable),
          p,
          new CompletionCallback() {
            @Override
            public void onCompletion(Packet packet) {}

            @Override
            public void onThrowable(Packet packet, Throwable throwable) {
              LOGGER.severe(MessageKeys.EXCEPTION, throwable);
            }
          });

      return doNext(packet);
    }
  }

  private static class ProgressingStep extends Step {
    private final String reason;
    private final boolean isPreserveAvailable;

    private ProgressingStep(String reason, boolean isPreserveAvailable) {
      super(null);
      this.reason = reason;
      this.isPreserveAvailable = isPreserveAvailable;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      DomainConditionStepContext context = new DomainConditionStepContext(packet);
      DomainStatus status = context.getStatus().clearModified();

      status.addCondition(new DomainCondition(Progressing).withStatus(TRUE).withReason(reason));
      status.removeConditionIf(c -> c.getType() == Failed);
      if (!isPreserveAvailable) {
        status.removeConditionIf(c -> c.getType() == Available);
      }

      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomain().getDomainUID(), status);
      LOGGER.exiting();

      return status.isModified()
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, ProgressingStep.this, getNext())
          : doNext(packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition end Progressing and set Available, if needed.
   *
   * @param next Next step
   * @return Step
   */
  static Step createEndProgressingStep(Step next) {
    return new EndProgressingStep(next);
  }

  private static class EndProgressingStep extends Step {

    EndProgressingStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      DomainConditionStepContext context = new DomainConditionStepContext(packet);
      DomainStatus status = context.getStatus().clearModified();

      status.removeConditionIf(c -> c.getType() == Progressing && TRUE.equals(c.getStatus()));

      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomain().getDomainUID(), status);
      LOGGER.exiting();

      return status.isModified()
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, EndProgressingStep.this, getNext())
          : doNext(packet);
    }
  }

  /**
   * Asynchronous step to set Domain condition to Available.
   *
   * @param reason Available reason
   * @param next Next step
   * @return Step
   */
  public static Step createAvailableStep(String reason, Step next) {
    return new AvailableHookStep(reason, next);
  }

  private static class AvailableHookStep extends Step {
    private final String reason;

    private AvailableHookStep(String reason, Step next) {
      super(next);
      this.reason = reason;
    }

    @Override
    public NextAction apply(Packet packet) {
      Fiber f = Fiber.current().createChildFiber();
      Packet p = new Packet();
      p.getComponents().putAll(packet.getComponents());
      f.start(
          new AvailableStep(reason),
          p,
          new CompletionCallback() {
            @Override
            public void onCompletion(Packet packet) {}

            @Override
            public void onThrowable(Packet packet, Throwable throwable) {
              LOGGER.severe(MessageKeys.EXCEPTION, throwable);
            }
          });

      return doNext(packet);
    }
  }

  private static class AvailableStep extends Step {
    private final String reason;

    private AvailableStep(String reason) {
      super(null);
      this.reason = reason;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      DomainConditionStepContext context = new DomainConditionStepContext(packet);
      DomainStatus status = context.getStatus().clearModified();

      status.addCondition(new DomainCondition(Available).withStatus(TRUE).withReason(reason));
      status.removeConditionIf(c -> c.getType() == Failed);

      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomain().getDomainUID(), status);
      LOGGER.exiting();
      return status.isModified()
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, AvailableStep.this, getNext())
          : doNext(packet);
    }
  }

  private static NextAction doDomainUpdate(
      Domain dom, DomainPresenceInfo info, Packet packet, Step conflictStep, Step next) {
    V1ObjectMeta meta = dom.getMetadata();
    NextAction na = new NextAction();

    // *NOTE* See the note in KubernetesVersion
    // If we update the CRDHelper to include the status subresource, then this code
    // needs to be modified to use replaceDomainStatusAsync.  Then, validate if onSuccess
    // should update info.

    na.invoke(
        new CallBuilder()
            .replaceDomainAsync(
                meta.getName(),
                meta.getNamespace(),
                dom,
                new DefaultResponseStep<Domain>(next) {
                  @Override
                  public NextAction onFailure(Packet packet, CallResponse<Domain> callResponse) {
                    if (callResponse.getStatusCode() == CallBuilder.NOT_FOUND) {
                      return doNext(packet); // Just ignore update
                    }
                    return super.onFailure(
                        getRereadDomainConflictStep(info, meta, conflictStep),
                        packet,
                        callResponse);
                  }

                  @Override
                  public NextAction onSuccess(Packet packet, CallResponse<Domain> callResponse) {
                    // Update info only if using replaceDomain
                    // Skip, if we switch to using replaceDomainStatus
                    info.setDomain(callResponse.getResult());
                    return doNext(packet);
                  }
                }),
        packet);
    return na;
  }

  private static Step getRereadDomainConflictStep(
      DomainPresenceInfo info, V1ObjectMeta meta, Step next) {
    return new CallBuilder()
        .readDomainAsync(
            meta.getName(),
            meta.getNamespace(),
            new DefaultResponseStep<Domain>(next) {
              @Override
              public NextAction onSuccess(Packet packet, CallResponse<Domain> callResponse) {
                info.setDomain(callResponse.getResult());
                return doNext(packet);
              }
            });
  }

  /**
   * Asynchronous step to set Domain condition to Failed.
   *
   * @param throwable Throwable that caused failure
   * @param next Next step
   * @return Step
   */
  static Step createFailedStep(Throwable throwable, Step next) {
    return new FailedHookStep(throwable, next);
  }

  private static class FailedHookStep extends Step {
    private final Throwable throwable;

    private FailedHookStep(Throwable throwable, Step next) {
      super(next);
      this.throwable = throwable;
    }

    @Override
    public NextAction apply(Packet packet) {
      Fiber f = Fiber.current().createChildFiber();
      Packet p = new Packet();
      p.getComponents().putAll(packet.getComponents());
      f.start(
          new FailedStep(throwable),
          p,
          new CompletionCallback() {
            @Override
            public void onCompletion(Packet packet) {}

            @Override
            public void onThrowable(Packet packet, Throwable throwable) {
              LOGGER.severe(MessageKeys.EXCEPTION, throwable);
            }
          });

      return doNext(packet);
    }
  }

  private static class FailedStep extends Step {
    private final Throwable throwable;

    private FailedStep(Throwable throwable) {
      super(null);
      this.throwable = throwable;
    }

    @Override
    public NextAction apply(Packet packet) {
      LOGGER.entering();

      DomainConditionStepContext context = new DomainConditionStepContext(packet);
      final DomainStatus status = context.getStatus().clearModified();

      status.addCondition(
          new DomainCondition(Failed)
              .withStatus(TRUE)
              .withReason("Exception")
              .withMessage(throwable.getMessage()));
      if (status.hasConditionWith(c -> c.hasType(Progressing))) {
        status.addCondition(new DomainCondition(Progressing).withStatus(FALSE));
      }

      LOGGER.info(MessageKeys.DOMAIN_STATUS, context.getDomain().getDomainUID(), status);
      LOGGER.exiting();

      return status.isModified()
          ? doDomainUpdate(
              context.getDomain(), context.getInfo(), packet, FailedStep.this, getNext())
          : doNext(packet);
    }
  }
}

// Copyright (c) 2018, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.KubernetesUtils;
import oracle.kubernetes.operator.helpers.LastKnownStatus;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.ThreadLoggingContext;
import oracle.kubernetes.operator.steps.ReadHealthStep;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.utils.KubernetesExec;
import oracle.kubernetes.operator.utils.KubernetesExecFactory;
import oracle.kubernetes.operator.utils.KubernetesExecFactoryImpl;
import oracle.kubernetes.operator.work.Fiber;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.OperatorUtils;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;

import static oracle.kubernetes.operator.KubernetesConstants.WLS_CONTAINER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;
import static oracle.kubernetes.operator.logging.ThreadLoggingContext.setThreadContext;

/** Creates an asynchronous step to read the WebLogic server state from a particular pod. */
public class ServerStatusReader {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Function<Step, Step> stepFactory = ReadHealthStep::createReadHealthStep;

  @SuppressWarnings("FieldMayBeFinal") // may be replaced by unit test
  private static KubernetesExecFactory execFactory = new KubernetesExecFactoryImpl();

  private ServerStatusReader() {
  }

  static Step createDomainStatusReaderStep(
      DomainPresenceInfo info, long timeoutSeconds, Step next) {
    return new DomainStatusReaderStep(info, timeoutSeconds, next);
  }

  /**
   * Asynchronous step to set Domain status to indicate WebLogic server status.
   *
   * @param timeoutSeconds Timeout in seconds
   * @return Step
   */
  static Step createStatusStep(int timeoutSeconds) {
    return new StatusUpdateHookStep(timeoutSeconds, null);
  }

  private static class DomainStatusReaderStep extends Step {
    private final DomainPresenceInfo info;
    private final long timeoutSeconds;

    DomainStatusReaderStep(DomainPresenceInfo info, long timeoutSeconds, Step next) {
      super(next);
      this.info = info;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      packet.put(SERVER_STATE_MAP, new ConcurrentHashMap<String, String>());
      packet.put(SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());

      AtomicInteger remainingServerHealthToRead = new AtomicInteger();
      packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, remainingServerHealthToRead);

      Collection<Fiber.StepAndPacket> startDetails =
          info.getServerPods()
              .map(pod -> createStatusReaderStep(packet, pod))
              .toList();

      if (startDetails.isEmpty()) {
        return doNext(packet);
      } else {
        remainingServerHealthToRead.set(startDetails.size());
        return doForkJoin(getNext(), packet, startDetails);
      }
    }

    /**
     * Creates asynchronous step to read WebLogic server state from a particular pod.
     *
     * @param pod The pod
     * @param serverName Server name
     * @param timeoutSeconds Timeout in seconds
     * @return Created step
     */
    private static Step createServerStatusReaderStep(V1Pod pod, String serverName, long timeoutSeconds) {
      return new ServerStatusReaderStep(serverName, timeoutSeconds, new ServerHealthStep(serverName, pod, null));
    }

    private Fiber.StepAndPacket createStatusReaderStep(Packet packet, V1Pod pod) {
      return new Fiber.StepAndPacket(
          createServerStatusReaderStep(pod, PodHelper.getPodServerName(pod), timeoutSeconds),
          packet.copy());
    }
  }

  private static class ServerStatusReaderStep extends Step {
    private final String serverName;
    private final long timeoutSeconds;

    ServerStatusReaderStep(String serverName, long timeoutSeconds, Step next) {
      super(next);
      this.serverName = serverName;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    @SuppressWarnings("try")
    public @Nonnull Result apply(Packet packet) {
      @SuppressWarnings("unchecked")
      final ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(SERVER_STATE_MAP);
      final long unchangedCountToDelayStatusRecheck
          = TuningParameters.getInstance().getUnchangedCountToDelayStatusRecheck();
      final int eventualLongDelay = TuningParameters.getInstance().getEventualLongDelay();
      final DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      final LastKnownStatus lastKnownStatus = info.getLastKnownServerStatus(serverName);
      final V1Pod currentPod = info.getServerPod(serverName);

      if (lastKnownStatus != null
          && !WebLogicConstants.UNKNOWN_STATE.equals(lastKnownStatus.getStatus())
          && lastKnownStatus.getUnchangedCount() >= unchangedCountToDelayStatusRecheck
          && SystemClock.now().isBefore(lastKnownStatus.getTime().plusSeconds(eventualLongDelay))) {
        String state = lastKnownStatus.getStatus();
        serverStateMap.put(serverName, state);
        return doNext(packet);
      }

      if (PodHelper.hasReadyStatus(currentPod)) {
        // set default to UNKNOWN; will be corrected in ReadHealthStep
        serverStateMap.put(serverName, WebLogicConstants.UNKNOWN_STATE);
        return doNext(packet);
      }

      final boolean stdin = false;
      final boolean tty = false;
      Process proc = null;
      String state = null;

      try {
        try (ThreadLoggingContext stack =
                 setThreadContext().namespace(getNamespace(currentPod)).domainUid(getDomainUid(currentPod))) {

          KubernetesExec kubernetesExec = execFactory.create(currentPod, WLS_CONTAINER_NAME);
          kubernetesExec.setStdin(stdin);
          kubernetesExec.setTty(tty);
          proc = kubernetesExec.exec("/weblogic-operator/scripts/readState.sh");

          try (final Reader reader = new InputStreamReader(proc.getInputStream())) {
            state = OperatorUtils.toString(reader);
          }

          if (proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            int exitValue = proc.exitValue();
            LOGGER.fine("readState exit: " + exitValue + ", readState for " + currentPod.getMetadata().getName());
            if (exitValue == 1 || exitValue == 2) {
              state =
                  isPodBeingDeleted(info, currentPod)
                      ? WebLogicConstants.SHUTDOWN_STATE
                      : WebLogicConstants.STARTING_STATE;
            } else if (exitValue != 0) {
              state = WebLogicConstants.UNKNOWN_STATE;
            }
          }
        }
      } catch (InterruptedException ignore) {
        Thread.currentThread().interrupt();
      } catch (IOException | ApiException e) {
        try (ThreadLoggingContext stack =
                 setThreadContext().namespace(getNamespace(currentPod)).domainUid(getDomainUid(currentPod))) {
          LOGGER.warning(MessageKeys.EXCEPTION, e);
        }
      } finally {
        if (proc != null) {
          proc.destroy();
        }
      }

      try (ThreadLoggingContext stack =
               setThreadContext().namespace(getNamespace(currentPod)).domainUid(getDomainUid(currentPod))) {
        LOGGER.fine("readState: " + state + " for " + currentPod.getMetadata().getName());
        state = chooseStateOrLastKnownServerStatus(info, lastKnownStatus, state, currentPod);
        serverStateMap.put(serverName, state);
      }

      return doNext(packet);
    }

    private boolean isPodBeingDeleted(DomainPresenceInfo info, V1Pod pod) {
      return PodHelper.isDeleting(pod) || info.isServerPodBeingDeleted(PodHelper.getPodServerName(pod));
    }

    private String getNamespace(@Nonnull V1Pod pod) {
      return Optional.of(pod).map(V1Pod::getMetadata).map(V1ObjectMeta::getNamespace).orElse(null);
    }

    public String getDomainUid(V1Pod pod) {
      return KubernetesUtils.getDomainUidLabel(
          Optional.ofNullable(pod).map(V1Pod::getMetadata).orElse(null));
    }

    private String chooseStateOrLastKnownServerStatus(
        DomainPresenceInfo info, LastKnownStatus lastKnownStatus, String state, V1Pod pod) {
      if (state != null) {
        state = state.trim();
        if (!state.isEmpty()) {
          info.updateLastKnownServerStatus(serverName, state);
          return state;
        }
      }

      if (lastKnownStatus != null) {
        return lastKnownStatus.getStatus();
      }
      state =
          (PodHelper.isDeleting(pod)
              ? WebLogicConstants.SHUTTING_DOWN_STATE
              : WebLogicConstants.STARTING_STATE);
      info.updateLastKnownServerStatus(serverName, state);
      return state;
    }
  }

  private static class ServerHealthStep extends Step {
    private final String serverName;
    private final V1Pod pod;

    ServerHealthStep(String serverName, V1Pod pod, Step next) {
      super(next);
      this.serverName = serverName;
      this.pod = pod;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(SERVER_STATE_MAP);
      String state = serverStateMap.get(serverName);

      if (PodHelper.hasReadyStatus(pod)
          || WebLogicConstants.STATES_SUPPORTING_REST.contains(state)) {
        packet.put(ProcessingConstants.SERVER_NAME, serverName);
        return doNext(stepFactory.apply(getNext()), packet);
      }

      return doNext(packet);
    }
  }

  static class StatusUpdateHookStep extends Step {
    private final int timeoutSeconds;

    StatusUpdateHookStep(int timeoutSeconds, Step next) {
      super(next);
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      return doNext(getNextStep(info), packet);
    }

    private Step getNextStep(DomainPresenceInfo info) {
      return Optional.ofNullable(info)
          .map(i -> createDomainStatusReaderStep(i, timeoutSeconds,
              DomainStatusUpdater.createStatusUpdateStep(getNext())))
          .orElse(getNext());
    }
  }
}

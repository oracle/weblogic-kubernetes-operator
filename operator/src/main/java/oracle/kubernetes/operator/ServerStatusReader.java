// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.LastKnownStatus;
import oracle.kubernetes.operator.helpers.PodHelper;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.ReadHealthStep;
import oracle.kubernetes.operator.utils.KubernetesExec;
import oracle.kubernetes.operator.utils.KubernetesExecFactory;
import oracle.kubernetes.operator.utils.KubernetesExecFactoryImpl;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.ServerHealth;
import org.joda.time.DateTime;

import static oracle.kubernetes.operator.KubernetesConstants.CONTAINER_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_HEALTH_MAP;
import static oracle.kubernetes.operator.ProcessingConstants.SERVER_STATE_MAP;

/** Creates an asynchronous step to read the WebLogic server state from a particular pod. */
public class ServerStatusReader {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static KubernetesExecFactory EXEC_FACTORY = new KubernetesExecFactoryImpl();
  private static Function<Step, Step> STEP_FACTORY = ReadHealthStep::createReadHealthStep;

  private ServerStatusReader() {
  }

  static Step createDomainStatusReaderStep(
      DomainPresenceInfo info, long timeoutSeconds, Step next) {
    return new DomainStatusReaderStep(info, timeoutSeconds, next);
  }

  /**
   * Creates asynchronous step to read WebLogic server state from a particular pod.
   *
   * @param info the domain presence
   * @param pod The pod
   * @param serverName Server name
   * @param timeoutSeconds Timeout in seconds
   * @return Created step
   */
  private static Step createServerStatusReaderStep(
      DomainPresenceInfo info, V1Pod pod, String serverName, long timeoutSeconds) {
    return new ServerStatusReaderStep(
        info, pod, serverName, timeoutSeconds, new ServerHealthStep(serverName, pod, null));
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

  private static class DomainStatusReaderStep extends Step {
    private final DomainPresenceInfo info;
    private final long timeoutSeconds;

    DomainStatusReaderStep(DomainPresenceInfo info, long timeoutSeconds, Step next) {
      super(next);
      this.info = info;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      packet.put(SERVER_STATE_MAP, new ConcurrentHashMap<String, String>());
      packet.put(SERVER_HEALTH_MAP, new ConcurrentHashMap<String, ServerHealth>());

      AtomicInteger remainingServerHealthToRead = new AtomicInteger();
      packet.put(ProcessingConstants.REMAINING_SERVERS_HEALTH_TO_READ, remainingServerHealthToRead);

      Collection<StepAndPacket> startDetails =
          info.getServerPods()
              .map(pod -> createStatusReaderStep(packet, pod))
              .collect(Collectors.toList());

      if (startDetails.isEmpty()) {
        return doNext(packet);
      } else {
        remainingServerHealthToRead.set(startDetails.size());
        return doForkJoin(getNext(), packet, startDetails);
      }
    }

    private StepAndPacket createStatusReaderStep(Packet packet, V1Pod pod) {
      return new StepAndPacket(
          createServerStatusReaderStep(info, pod, PodHelper.getPodServerName(pod), timeoutSeconds),
          packet.clone());
    }
  }

  private static class ServerStatusReaderStep extends Step {
    private final DomainPresenceInfo info;
    private final V1Pod pod;
    private final String serverName;
    private final long timeoutSeconds;

    ServerStatusReaderStep(
        DomainPresenceInfo info, V1Pod pod, String serverName, long timeoutSeconds, Step next) {
      super(next);
      this.info = info;
      this.pod = pod;
      this.serverName = serverName;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(SERVER_STATE_MAP);

      TuningParameters.MainTuning main = TuningParameters.getInstance().getMainTuning();
      LastKnownStatus lastKnownStatus = info.getLastKnownServerStatus(serverName);
      if (lastKnownStatus != null
          && !WebLogicConstants.UNKNOWN_STATE.equals(lastKnownStatus.getStatus())
          && lastKnownStatus.getUnchangedCount() >= main.unchangedCountToDelayStatusRecheck) {
        if (DateTime.now()
            .isBefore(lastKnownStatus.getTime().plusSeconds((int) main.eventualLongDelay))) {
          String state = lastKnownStatus.getStatus();
          serverStateMap.put(serverName, state);
          return doNext(packet);
        }
      }

      if (PodHelper.getReadyStatus(pod)) {
        // set default to UNKNOWN; will be corrected in ReadHealthStep
        serverStateMap.put(serverName, WebLogicConstants.UNKNOWN_STATE);
        return doNext(packet);
      }

      // Even though we don't need input data for this call, the API server is
      // returning 400 Bad Request any time we set these to false.  There is likely some bug in the
      // client
      final boolean stdin = true;
      final boolean tty = true;

      return doSuspend(
          fiber -> {
            Process proc = null;
            String state = null;
            ClientPool helper = ClientPool.getInstance();
            ApiClient client = helper.take();
            try {
              KubernetesExec kubernetesExec = EXEC_FACTORY.create(client, pod, CONTAINER_NAME);
              kubernetesExec.setStdin(stdin);
              kubernetesExec.setTty(tty);
              proc = kubernetesExec.exec("/weblogic-operator/scripts/readState.sh");

              InputStream in = proc.getInputStream();
              if (proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                int exitValue = proc.exitValue();
                if (exitValue == 0) {
                  try (final Reader reader = new InputStreamReader(in, Charsets.UTF_8)) {
                    state = CharStreams.toString(reader);
                  }
                } else if (exitValue == 1 || exitValue == 2) {
                  state =
                      PodHelper.isDeleting(pod)
                          ? WebLogicConstants.SHUTDOWN_STATE
                          : WebLogicConstants.STARTING_STATE;
                } else {
                  state = WebLogicConstants.UNKNOWN_STATE;
                }
              }
            } catch (InterruptedException ignore) {
              Thread.currentThread().interrupt();
            } catch (IOException | ApiException e) {
              LOGGER.warning(MessageKeys.EXCEPTION, e);
            } finally {
              helper.recycle(client);
              if (proc != null) {
                proc.destroy();
              }
            }

            state = chooseStateOrLastKnownServerStatus(lastKnownStatus, state);
            serverStateMap.put(serverName, state);
            fiber.resume(packet);
          });
    }

    private String chooseStateOrLastKnownServerStatus(
        LastKnownStatus lastKnownStatus, String state) {
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
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(SERVER_STATE_MAP);
      String state = serverStateMap.get(serverName);

      if (PodHelper.getReadyStatus(pod)
          || WebLogicConstants.STATES_SUPPORTING_REST.contains(state)) {
        packet.put(ProcessingConstants.SERVER_NAME, serverName);
        return doNext(STEP_FACTORY.apply(getNext()), packet);
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
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      return doNext(
          createDomainStatusReaderStep(
              info, timeoutSeconds, DomainStatusUpdater.createStatusUpdateStep(getNext())),
          packet);
    }
  }
}

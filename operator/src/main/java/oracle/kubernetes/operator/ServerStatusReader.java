// Copyright 2018, 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.KubernetesConstants.CONTAINER_NAME;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Pod;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import oracle.kubernetes.operator.helpers.ClientPool;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
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
import oracle.kubernetes.weblogic.domain.v2.ServerHealth;

/** Creates an asynchronous step to read the WebLogic server state from a particular pod */
public class ServerStatusReader {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static KubernetesExecFactory EXEC_FACTORY = new KubernetesExecFactoryImpl();
  private static Function<Step, Step> STEP_FACTORY = ReadHealthStep::createReadHealthStep;

  private ServerStatusReader() {}

  static Step createDomainStatusReaderStep(
      DomainPresenceInfo info, long timeoutSeconds, Step next) {
    return new DomainStatusReaderStep(info, timeoutSeconds, next);
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
      ConcurrentMap<String, String> serverStateMap = new ConcurrentHashMap<>();
      packet.put(ProcessingConstants.SERVER_STATE_MAP, serverStateMap);

      ConcurrentMap<String, ServerHealth> serverHealthMap = new ConcurrentHashMap<>();
      packet.put(ProcessingConstants.SERVER_HEALTH_MAP, serverHealthMap);

      Collection<StepAndPacket> startDetails = new ArrayList<>();
      for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
        String serverName = entry.getKey();
        ServerKubernetesObjects sko = entry.getValue();
        if (sko != null) { // !! Impossible to have a null value in a concurrent map
          V1Pod pod = sko.getPod().get();
          if (pod != null) {
            Packet p = packet.clone();
            startDetails.add(
                new StepAndPacket(
                    createServerStatusReaderStep(sko, pod, serverName, timeoutSeconds), p));
          }
        }
      }

      if (startDetails.isEmpty()) {
        return doNext(packet);
      }
      return doForkJoin(getNext(), packet, startDetails);
    }
  }

  /**
   * Creates asynchronous step to read WebLogic server state from a particular pod
   *
   * @param sko Server objects
   * @param pod The pod
   * @param serverName Server name
   * @param timeoutSeconds Timeout in seconds
   * @return Created step
   */
  private static Step createServerStatusReaderStep(
      ServerKubernetesObjects sko, V1Pod pod, String serverName, long timeoutSeconds) {
    return new ServerStatusReaderStep(
        sko, pod, serverName, timeoutSeconds, new ServerHealthStep(serverName, null));
  }

  private static class ServerStatusReaderStep extends Step {
    private final ServerKubernetesObjects sko;
    private final V1Pod pod;
    private final String serverName;
    private final long timeoutSeconds;

    ServerStatusReaderStep(
        ServerKubernetesObjects sko, V1Pod pod, String serverName, long timeoutSeconds, Step next) {
      super(next);
      this.sko = sko;
      this.pod = pod;
      this.serverName = serverName;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(ProcessingConstants.SERVER_STATE_MAP);

      if (PodWatcher.getReadyStatus(pod)) {
        sko.getLastKnownStatus().set(WebLogicConstants.RUNNING_STATE);
        serverStateMap.put(serverName, WebLogicConstants.RUNNING_STATE);
        return doNext(packet);
      } else {
        String lastKnownState = sko.getLastKnownStatus().get();
        if (lastKnownState != null) {
          serverStateMap.put(serverName, lastKnownState);
          return doNext(packet);
        }
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
                try (final Reader reader = new InputStreamReader(in, Charsets.UTF_8)) {
                  state = CharStreams.toString(reader);
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

            serverStateMap.put(
                serverName, state != null ? state.trim() : WebLogicConstants.UNKNOWN_STATE);
            fiber.resume(packet);
          });
    }
  }

  private static class ServerHealthStep extends Step {
    private final String serverName;

    ServerHealthStep(String serverName, Step next) {
      super(next);
      this.serverName = serverName;
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverStateMap =
          (ConcurrentMap<String, String>) packet.get(ProcessingConstants.SERVER_STATE_MAP);
      String state = serverStateMap.get(serverName);

      if (WebLogicConstants.STATES_SUPPORTING_REST.contains(state)) {
        packet.put(ProcessingConstants.SERVER_NAME, serverName);
        return doNext(STEP_FACTORY.apply(getNext()), packet);
      }

      return doNext(packet);
    }
  }
}

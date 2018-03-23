// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.Exec;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ClientHelper;
import oracle.kubernetes.operator.helpers.ClientHolder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ServerKubernetesObjects;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.WlsRetriever;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;
import oracle.kubernetes.weblogic.domain.v1.ServerHealth;

/**
 * Creates an asynchronous step to read the WebLogic server state from a particular pod
 * 
 */
public class ServerStatusReader {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  private ServerStatusReader() {
  }

  public static Step createDomainStatusReaderStep(DomainPresenceInfo info, long timeoutSeconds, Step next) {
    return new DomainStatusReaderStep(info, timeoutSeconds, next);
  }
  
  private static class DomainStatusReaderStep extends Step {
    private final DomainPresenceInfo info;
    private final long timeoutSeconds;

    public DomainStatusReaderStep(DomainPresenceInfo info, long timeoutSeconds, Step next) {
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

      Domain domain = info.getDomain();
      V1ObjectMeta meta = domain.getMetadata();
      DomainSpec spec = domain.getSpec();
      
      String namespace = meta.getNamespace();
      String domainUID = spec.getDomainUID();
      
      Collection<StepAndPacket> startDetails = new ArrayList<>();
      for (Map.Entry<String, ServerKubernetesObjects> entry : info.getServers().entrySet()) {
        String serverName = entry.getKey();
        ServerKubernetesObjects sko = entry.getValue();
        if (sko != null) {
          V1Pod pod = sko.getPod().get();
          if (pod != null) {
            if (PodWatcher.isReady(pod, true)) {
              serverStateMap.put(serverName, "RUNNING");
            } else {
              Packet p = packet.clone();
              startDetails.add(new StepAndPacket(
                  createServerStatusReaderStep(namespace, domainUID, serverName, timeoutSeconds, null), p));
            }
          }
        }
      }

      if (startDetails.isEmpty()) {
        return doNext(packet);
      }
      return doForkJoin(next, packet, startDetails);
    }
  }
  
  /**
   * Creates asynchronous step to read WebLogic server state from a particular pod
   * @param namespace Namespace
   * @param domainUID Domain UID
   * @param serverName Server name
   * @param timeoutSeconds Timeout in seconds
   * @param next Next step
   * @return Created step
   */
  public static Step createServerStatusReaderStep(String namespace, String domainUID,
      String serverName, long timeoutSeconds, Step next) {
    return new ServerStatusReaderStep(namespace, domainUID, serverName, timeoutSeconds, 
        new ServerHealthStep(serverName, timeoutSeconds, next));
  }

  private static class ServerStatusReaderStep extends Step {
    private final String namespace;
    private final String domainUID;
    private final String serverName;
    private final long timeoutSeconds;

    public ServerStatusReaderStep(String namespace, String domainUID, String serverName, 
        long timeoutSeconds, Step next) {
      super(next);
      this.namespace = namespace;
      this.domainUID = domainUID;
      this.serverName = serverName;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      String podName = CallBuilder.toDNS1123LegalName(domainUID + "-" + serverName);

      // Even though we don't need input data for this call, the API server is 
      // returning 400 Bad Request any time we set these to false.  There is likely some bug in the client
      final boolean stdin = true;
      final boolean tty = true;

      return doSuspend(fiber -> {
        ClientHelper helper = ClientHelper.getInstance();
        ClientHolder holder = helper.take();
        Exec exec = new Exec(holder.getApiClient());
        Process proc = null;
        String state = null;
        try {
          proc = exec.exec(namespace, podName,
              new String[] { "/weblogic-operator/scripts/readState.sh" },
              KubernetesConstants.CONTAINER_NAME, stdin, tty);

          InputStream in = proc.getInputStream();
          if (proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            try (final Reader reader = new InputStreamReader(in, Charsets.UTF_8)) {
                state = CharStreams.toString(reader);
            }
          }
        } catch (IOException | ApiException | InterruptedException e) {
          LOGGER.warning(MessageKeys.EXCEPTION, e);
        } finally {
          helper.recycle(holder);
          if (proc != null) {
            proc.destroy();
          }
        }
        
        @SuppressWarnings("unchecked")
        ConcurrentMap<String, String> serverStateMap = (ConcurrentMap<String, String>) packet
            .get(ProcessingConstants.SERVER_STATE_MAP);
        serverStateMap.put(serverName, parseState(state));
        fiber.resume(packet);
      });
    }
  }
  
  private static String parseState(String state) {
    // Format of state is "<serverState>:<Y or N, if server started>:<Y or N, if server failed>
    String s = "UNKNOWN";
    if (state != null) {
      int ind = state.indexOf(':');
      if (ind > 0) {
        s = state.substring(0, ind);
      }
    }
    
    return s;
  }
  
  private static final Set<String> statesSupportingREST = new HashSet<>();
  static {
    statesSupportingREST.add("STANDBY");
    statesSupportingREST.add("ADMIN");
    statesSupportingREST.add("RESUMING");
    statesSupportingREST.add("RUNNING");
    statesSupportingREST.add("SUSPENDING");
    statesSupportingREST.add("FORCE_SUSPENDING");
  }
  
  private static class ServerHealthStep extends Step {
    private final String serverName;
    private final long timeoutSeconds; // FIXME
    
    public ServerHealthStep(String serverName, long timeoutSeconds, Step next) {
      super(next);
      this.serverName = serverName;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      @SuppressWarnings("unchecked")
      ConcurrentMap<String, String> serverStateMap = (ConcurrentMap<String, String>) packet
          .get(ProcessingConstants.SERVER_STATE_MAP);
      String state = serverStateMap.get(serverName);
      if (statesSupportingREST.contains(state)) {
        packet.put(ProcessingConstants.SERVER_NAME, serverName);
        return doNext(WlsRetriever.readHealthStep(next), packet);
      }
      
      return doNext(packet);
    }
    
  }
}

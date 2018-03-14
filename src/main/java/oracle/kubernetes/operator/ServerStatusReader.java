// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.google.common.io.CharStreams;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.Exec;
import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.weblogic.domain.v1.DomainSpec;

/**
 * Creates an asynchronous step to read the WebLogic server state from a particular pod
 * 
 */
public class ServerStatusReader {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  
  private ServerStatusReader() {
  }

  public static Step createDomainStatusReaderStep(DomainPresenceInfo info, long timeout, TimeUnit unit, Step next) {
    return new DomainStatusReaderStep(info, timeout, unit, next);
  }
  
  private static class DomainStatusReaderStep extends Step {
    private final DomainPresenceInfo info;
    private final long timeout;
    private final TimeUnit unit;

    public DomainStatusReaderStep(DomainPresenceInfo info, long timeout, TimeUnit unit, Step next) {
      super(next);
      this.info = info;
      this.timeout = timeout;
      this.unit = unit;
    }

    @Override
    public NextAction apply(Packet packet) {
      packet.put(ProcessingConstants.SERVER_STATE_MAP, new ConcurrentHashMap<String, String>());
      
      Domain domain = info.getDomain();
      V1ObjectMeta meta = domain.getMetadata();
      DomainSpec spec = domain.getSpec();
      
      String namespace = meta.getNamespace();
      String domainUID = spec.getDomainUID();
      String domainName = spec.getDomainName();
      
      Collection<StepAndPacket> startDetails = new ArrayList<>();
      WlsDomainConfig scan = info.getScan();
      if (scan != null) {
        for (Map.Entry<String, WlsServerConfig> entry : scan.getServerConfigs().entrySet()) {
          String serverName = entry.getKey();
          Packet p = packet.clone();
          startDetails.add(new StepAndPacket(
              createServerStatusReaderStep(namespace, domainUID, domainName, serverName, timeout, unit, null), p));
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
   * @param domainName Domain name
   * @param serverName Server name
   * @param timeout Timeout in milliseconds
   * @param next Next step
   * @return Created step
   */
  public static Step createServerStatusReaderStep(String namespace, String domainUID, String domainName,
      String serverName, long timeout, TimeUnit unit, Step next) {
    return new ServerStatusReaderStep(namespace, domainUID, domainName, serverName, timeout, unit, next);
  }

  private static class ServerStatusReaderStep extends Step {
    private final String namespace;
    private final String domainUID;
    private final String domainName;
    private final String serverName;
    private final long timeout;
    private final TimeUnit unit;

    public ServerStatusReaderStep(String namespace, String domainUID, String domainName, String serverName, 
        long timeout, TimeUnit unit, Step next) {
      super(next);
      this.namespace = namespace;
      this.domainUID = domainUID;
      this.domainName = domainName;
      this.serverName = serverName;
      this.timeout = timeout;
      this.unit = unit;
    }

    @Override
    public NextAction apply(Packet packet) {
      Exec exec = new Exec();

      String podName = CallBuilder.toDNS1123LegalName(domainUID + "-" + serverName);

      final boolean stdin = false;
      final boolean tty = false;

      return doSuspend(fiber -> {
        try {
          final Process proc = exec.exec(namespace, podName,
              new String[] { "/weblogic-operator/scripts/readState.sh", domainName, serverName },
              KubernetesConstants.CONTAINER_NAME, stdin, tty);

          if (proc.waitFor(timeout, unit)) {
            String state = null;
            try (final Reader reader = new InputStreamReader(proc.getInputStream())) {
              state = CharStreams.toString(reader);
            }

            @SuppressWarnings("unchecked")
            ConcurrentMap<String, String> serverStateMap = (ConcurrentMap<String, String>) packet
                .get(ProcessingConstants.SERVER_STATE_MAP);
            serverStateMap.put(serverName, state);
          }
        } catch (IOException | ApiException | InterruptedException e) {
          LOGGER.warning(MessageKeys.EXCEPTION, e);
        }
        
        fiber.resume(packet);
      });
    }
  }
  
  public static String parseState(String state) {
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
}

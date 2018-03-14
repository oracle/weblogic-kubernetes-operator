// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.CallBuilder.ExecParams;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
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

  public static Step createDomainStatusReaderStep(DomainPresenceInfo info, int timeoutSeconds, Step next) {
    return new DomainStatusReaderStep(info, timeoutSeconds, next);
  }
  
  private static class DomainStatusReaderStep extends Step {
    private final DomainPresenceInfo info;
    private final int timeoutSeconds;

    public DomainStatusReaderStep(DomainPresenceInfo info, int timeoutSeconds, Step next) {
      super(next);
      this.info = info;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      packet.put(ProcessingConstants.SERVER_STATE_MAP, new ConcurrentHashMap<String, String>());
      
      Domain domain = info.getDomain();
      V1ObjectMeta meta = domain.getMetadata();
      DomainSpec spec = domain.getSpec();
      
      String namespace = meta.getNamespace();
      String domainUID = spec.getDomainUID();
      
      Collection<StepAndPacket> startDetails = new ArrayList<>();
      WlsDomainConfig scan = info.getScan();
      if (scan != null) {
        for (Map.Entry<String, WlsServerConfig> entry : scan.getServerConfigs().entrySet()) {
          String serverName = entry.getKey();
          Packet p = packet.clone();
          startDetails.add(new StepAndPacket(
              createServerStatusReaderStep(namespace, domainUID, serverName, timeoutSeconds, null), p));
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
      String serverName, int timeoutSeconds, Step next) {
    return new ServerStatusReaderStep(namespace, domainUID, serverName, timeoutSeconds, next);
  }

  private static class ServerStatusReaderStep extends Step {
    private final String namespace;
    private final String domainUID;
    private final String serverName;
    private final int timeoutSeconds;

    public ServerStatusReaderStep(String namespace, String domainUID, String serverName, 
        int timeoutSeconds, Step next) {
      super(next);
      this.namespace = namespace;
      this.domainUID = domainUID;
      this.serverName = serverName;
      this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public NextAction apply(Packet packet) {
      String podName = CallBuilder.toDNS1123LegalName(domainUID + "-" + serverName);

      final ExecParams execParams = new ExecParams();
      execParams.command = "/weblogic-operator/scripts/readState.sh";
      execParams.container = KubernetesConstants.CONTAINER_NAME;
      
      return doNext(CallBuilder.create()
          .with($ -> {$.timeoutSeconds = timeoutSeconds;})
          .execPodAsync(podName, namespace, execParams, new ResponseStep<String>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          // Failures are okay
          return onSuccess(packet, null, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, String result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          @SuppressWarnings("unchecked")
          ConcurrentMap<String, String> serverStateMap = (ConcurrentMap<String, String>) packet
              .get(ProcessingConstants.SERVER_STATE_MAP);
          if (result != null) {
            serverStateMap.put(serverName, result);
          } else {
            serverStateMap.remove(serverName);
          }
          return doNext(packet);
        }
      }), packet);
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

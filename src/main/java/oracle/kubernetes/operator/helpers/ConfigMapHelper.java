// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ConfigMapHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private ConfigMapHelper() {}
  
  /**
   * Factory for {@link Step} that creates config map containing scripts
   * @param namespace Namespace
   * @param next Next processing step
   * @return Step for creating config map containing scripts
   */
  public static Step createScriptConfigMapStep(String namespace, Step next) {
    return new ScriptConfigMapStep(namespace, next);
  }

  private static class ScriptConfigMapStep extends Step {
    private final String namespace;
    
    public ScriptConfigMapStep(String namespace, Step next) {
      super(next);
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      String name = "weblogic-domain-config-map";
      V1ConfigMap cm = new V1ConfigMap();
      
      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(name);
      metadata.setNamespace(namespace);
      cm.setMetadata(metadata);

      Map<String, String> data = new HashMap<>();
      
      // HERE
      data.put("livenessProbe.sh", 
          "#!/bin/bash\n" + 
          "\n" + 
          "# Kubernetes periodically calls this liveness probe script to determine whether\n" + 
          "# the pod should be restarted. The script checks a WebLogic Server state file which\n" + 
          "# is updated by the node manager.\n" + 
          "\n" + 
          "STATEFILE=${DOMAIN_HOME}/servers/${SERVER_NAME}/data/nodemanager/${SERVER_NAME}.state\n" + 
          "\n" + 
          "if [ \\`jps -l | grep -c \" weblogic.NodeManager\"\\` -eq 0 ]; then\n" + 
          "  echo \"Error: WebLogic NodeManager process not found.\"\n" + 
          "  exit 1\n" + 
          "fi\n" + 
          "\n" + 
          "if [ -f \\${STATEFILE} ] && [ \\`grep -c \"FAILED_NOT_RESTARTABLE\" \\${STATEFILE}\\` -eq 1 ]; then\n" + 
          "  echo \"Error: WebLogic Server FAILED_NOT_RESTARTABLE.\"\n" + 
          "  exit 1\n" + 
          "fi\n" + 
          "\n" + 
          "echo \"Info: Probe check passed.\"\n" + 
          "exit 0");
      
      cm.setData(data);
      
      Step read = CallBuilder.create().readConfigMapAsync(name, namespace, new ResponseStep<V1ConfigMap>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (statusCode == CallBuilder.NOT_FOUND) {
            return onSuccess(packet, null, statusCode, responseHeaders);
          }
          return super.onFailure(packet, e, statusCode, responseHeaders);
        }

        @Override
        public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          if (result == null) {
            Step create = CallBuilder.create().createConfigMapAsync(namespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                
                LOGGER.info(MessageKeys.ADMIN_POD_CREATED, weblogicDomainUID, spec.getAsName());
                return doNext(packet);
              }
            });
            return doNext(create, packet);
          } else if (result.getData().entrySet().containsAll(data.entrySet())) {
            // existing config map has correct data
            LOGGER.fine(MessageKeys.ADMIN_POD_EXISTS, weblogicDomainUID, spec.getAsName());
            return doNext(packet);
          } else {
            // we need to update the config map
            Map<String, String> updated = result.getData();
            updated.putAll(data);
            cm.setData(updated);
            Step replace = CallBuilder.create().replaceConfigMapAsync(name, namespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                LOGGER.info(MessageKeys.ADMIN_POD_REPLACED, weblogicDomainUID, spec.getAsName());
                return doNext(packet);
              }
            });
            return doNext(replace, packet);
          }
        }
      });
      
      return doNext(read, packet);
    }
  }

}

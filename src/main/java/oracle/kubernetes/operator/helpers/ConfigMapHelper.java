// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ContainerResolver;
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

  // Make this public so that it can be unit tested
  public static class ScriptConfigMapStep extends Step {
    private final String namespace;
    
    public ScriptConfigMapStep(String namespace, Step next) {
      super(next);
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      V1ConfigMap cm = computeDomainConfigMap();
      CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      Step read = factory.create().readConfigMapAsync(cm.getMetadata().getName(), namespace, new ResponseStep<V1ConfigMap>(next) {
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
            Step create = factory.create().createConfigMapAsync(namespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(ScriptConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                
                LOGGER.info(MessageKeys.CM_CREATED, namespace);
                packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
                return doNext(packet);
              }
            });
            return doNext(create, packet);
          } else if (AnnotationHelper.checkFormatAnnotation(result.getMetadata()) && result.getData().entrySet().containsAll(cm.getData().entrySet())) {
            // existing config map has correct data
            LOGGER.fine(MessageKeys.CM_EXISTS, namespace);
            packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
            return doNext(packet);
          } else {
            // we need to update the config map
            Map<String, String> updated = result.getData();
            updated.putAll(cm.getData());
            cm.setData(updated);
            Step replace = factory.create().replaceConfigMapAsync(cm.getMetadata().getName(), namespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(ScriptConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                LOGGER.info(MessageKeys.CM_REPLACED, namespace);
                packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
                return doNext(packet);
              }
            });
            return doNext(replace, packet);
          }
        }
      });
      
      return doNext(read, packet);
    }

    // Make this protected so that it can be unit tested
    protected V1ConfigMap computeDomainConfigMap() {
      String name = KubernetesConstants.DOMAIN_CONFIG_MAP_NAME;
      V1ConfigMap cm = new V1ConfigMap();
      cm.setApiVersion("v1");
      cm.setKind("ConfigMap");

      V1ObjectMeta metadata = new V1ObjectMeta();
      metadata.setName(name);
      metadata.setNamespace(namespace);
      
      AnnotationHelper.annotateWithFormat(metadata);
      
      Map<String, String> labels = new HashMap<>();
      // This config map is a singleton that is shared by all the domains
      // We need to add a domain uid label so that it can be located as
      // related to the operator.  However, we don't have a specific domain uid
      // to set as the value.  So, just set it to an empty string.  That way,
      // someone seleting on just the weblogic.domainUID label will find it.
      labels.put(LabelConstants.DOMAINUID_LABEL, "");
      labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      metadata.setLabels(labels);

      cm.setMetadata(metadata);

      Map<String, String> data = new HashMap<>();
      
      data.put("livenessProbe.sh", 
          "#!/bin/bash\n" + 
          "# Kubernetes periodically calls this liveness probe script to determine whether\n" + 
          "# the pod should be restarted. The script checks a WebLogic Server state file which\n" + 
          "# is updated by the node manager.\n" + 
          "DN=${DOMAIN_NAME:-$1}\n" +
          "SN=${SERVER_NAME:-$2}\n" +
          "STATEFILE=/shared/domain/${DN}/servers/${SN}/data/nodemanager/${SN}.state\n" + 
          "if [ `jps -l | grep -c \" weblogic.NodeManager\"` -eq 0 ]; then\n" + 
          "  echo \"Error: WebLogic NodeManager process not found.\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "if [ -f ${STATEFILE} ] && [ `grep -c \"FAILED_NOT_RESTARTABLE\" ${STATEFILE}` -eq 1 ]; then\n" + 
          "  echo \"Error: WebLogic Server FAILED_NOT_RESTARTABLE.\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "exit 0");
      
      data.put("readinessProbe.sh", 
          "#!/bin/bash\n" + 
          "\n" + 
          "# Kubernetes periodically calls this readiness probe script to determine whether\n" + 
          "# the pod should be included in load balancing. The script checks a WebLogic Server state\n" + 
          "# file which is updated by the node manager.\n" + 
          "\n" + 
          "DN=${DOMAIN_NAME:-$1}\n" +
          "SN=${SERVER_NAME:-$2}\n" +
          "STATEFILE=/shared/domain/${DN}/servers/${SN}/data/nodemanager/${SN}.state\n" + 
          "\n" + 
          "if [ `jps -l | grep -c \" weblogic.NodeManager\"` -eq 0 ]; then\n" + 
          "  echo \"Error: WebLogic NodeManager process not found.\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "\n" + 
          "if [ ! -f ${STATEFILE} ] || [ `grep -c \"RUNNING\" ${STATEFILE}` -ne 1 ]; then\n" + 
          "  exit 1\n" + 
          "fi\n" + 
          "\n" + 
          "exit 0");

      data.put("readState.sh", 
          "#!/bin/bash\n" + 
          "\n" + 
          "# Reads the current state of a server. The script checks a WebLogic Server state\n" + 
          "# file which is updated by the node manager.\n" + 
          "\n" + 
          "DN=${DOMAIN_NAME:-$1}\n" +
          "SN=${SERVER_NAME:-$2}\n" +
          "STATEFILE=/shared/domain/${DN}/servers/${SN}/data/nodemanager/${SN}.state\n" + 
          "\n" + 
          "if [ `jps -l | grep -c \" weblogic.NodeManager\"` -eq 0 ]; then\n" + 
          "  echo \"Error: WebLogic NodeManager process not found.\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "\n" + 
          "if [ ! -f ${STATEFILE} ]; then\n" + 
          "  echo \"Error: Server state file not found.\"\n" +
          "  exit 1\n" + 
          "fi\n" + 
          "\n" + 
          "cat ${STATEFILE}\n" +
          "exit 0");

      cm.setData(data);

      return cm;
    }
  }

}

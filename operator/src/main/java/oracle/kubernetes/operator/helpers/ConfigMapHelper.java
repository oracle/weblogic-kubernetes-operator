// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.WebLogicConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.ContainerResolver;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ConfigMapHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String SCRIPT_LOCATION = "/scripts";

  private ConfigMapHelper() {}
  
  /**
   * Factory for {@link Step} that creates config map containing scripts
   * @param operatorNamespace the operator's namespace
   * @param domainNamespace the domain's namespace
   * @param next Next processing step
   * @return Step for creating config map containing scripts
   */
  public static Step createScriptConfigMapStep(String operatorNamespace, String domainNamespace, Step next) {
    return new ScriptConfigMapStep(operatorNamespace, domainNamespace, next);
  }

  // Make this public so that it can be unit tested
  public static class ScriptConfigMapStep extends Step {
    private final String operatorNamespace;
    private final String domainNamespace;
    
    public ScriptConfigMapStep(String operatorNamespace, String domainNamespace, Step next) {
      super(next);
      this.operatorNamespace = operatorNamespace;
      this.domainNamespace = domainNamespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      V1ConfigMap cm = computeDomainConfigMap();
      CallBuilderFactory factory = ContainerResolver.getInstance().getContainer().getSPI(CallBuilderFactory.class);
      Step read = factory.create().readConfigMapAsync(cm.getMetadata().getName(), domainNamespace, new ResponseStep<V1ConfigMap>(next) {
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
            Step create = factory.create().createConfigMapAsync(domainNamespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(ScriptConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                
                LOGGER.info(MessageKeys.CM_CREATED, domainNamespace);
                packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
                return doNext(packet);
              }
            });
            return doNext(create, packet);
          } else if (AnnotationHelper.checkFormatAnnotation(result.getMetadata()) && result.getData().entrySet().containsAll(cm.getData().entrySet())) {
            // existing config map has correct data
            LOGGER.fine(MessageKeys.CM_EXISTS, domainNamespace);
            packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
            return doNext(packet);
          } else {
            // we need to update the config map
            Map<String, String> updated = result.getData();
            updated.putAll(cm.getData());
            cm.setData(updated);
            Step replace = factory.create().replaceConfigMapAsync(cm.getMetadata().getName(), domainNamespace, cm, new ResponseStep<V1ConfigMap>(next) {
              @Override
              public NextAction onFailure(Packet packet, ApiException e, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                return super.onFailure(ScriptConfigMapStep.this, packet, e, statusCode, responseHeaders);
              }
              
              @Override
              public NextAction onSuccess(Packet packet, V1ConfigMap result, int statusCode,
                  Map<String, List<String>> responseHeaders) {
                LOGGER.info(MessageKeys.CM_REPLACED, domainNamespace);
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
      metadata.setNamespace(domainNamespace);
      
      AnnotationHelper.annotateWithFormat(metadata);
      
      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.OPERATORNAME_LABEL, operatorNamespace);
      labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      metadata.setLabels(labels);

      cm.setMetadata(metadata);
      cm.setData(loadScripts());

      return cm;
    }

    private synchronized Map<String, String> loadScripts() {
      URI uri = null;
      try {
        uri = getClass().getResource(SCRIPT_LOCATION).toURI();
      } catch (URISyntaxException e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
        throw new RuntimeException(e);
      }

      try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.<String, Object>emptyMap())) {
        Stream<Path> walk = Files.walk(fileSystem.getPath(SCRIPT_LOCATION), 1);
        Map<String, String> data = new HashMap<>();
        for (Iterator<Path> it = walk.iterator(); it.hasNext();) {
          Path script = it.next();
          String scriptName = script.toString();
          if (!SCRIPT_LOCATION.equals(scriptName)) {
            data.put(script.getFileName().toString(), readScript(scriptName));
          }
        }
        LOGGER.info(MessageKeys.SCRIPT_LOADED, domainNamespace);
        return data;
      } catch (IOException e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
        throw new RuntimeException(e);
      }
    }

    private String readScript(String scriptName) throws IOException {
      try (
        InputStream inputStream = getClass().getResourceAsStream(scriptName);
        ByteArrayOutputStream result = new ByteArrayOutputStream()
      ) {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
          result.write(buffer, 0, length);
        }
        return result.toString();
      }
    }
  }

}

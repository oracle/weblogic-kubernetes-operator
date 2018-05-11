// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.VersionConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

public class ConfigMapHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String SCRIPTS = "scripts";
  private static final String SCRIPT_LOCATION = "/" + SCRIPTS;

  private ConfigMapHelper() {}

  /**
   * Factory for {@link Step} that creates config map containing scripts
   *
   * @param operatorNamespace the operator's namespace
   * @param domainNamespace the domain's namespace
   * @param next Next processing step
   * @return Step for creating config map containing scripts
   */
  public static Step createScriptConfigMapStep(
      String operatorNamespace, String domainNamespace, Step next) {
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
      CallBuilderFactory factory = new CallBuilderFactory();
      Step read =
          factory
              .create()
              .readConfigMapAsync(
                  cm.getMetadata().getName(),
                  domainNamespace,
                  new ResponseStep<V1ConfigMap>(next) {
                    @Override
                    public NextAction onFailure(
                        Packet packet,
                        ApiException e,
                        int statusCode,
                        Map<String, List<String>> responseHeaders) {
                      if (statusCode == CallBuilder.NOT_FOUND) {
                        return onSuccess(packet, null, statusCode, responseHeaders);
                      }
                      return super.onFailure(packet, e, statusCode, responseHeaders);
                    }

                    @Override
                    public NextAction onSuccess(
                        Packet packet,
                        V1ConfigMap result,
                        int statusCode,
                        Map<String, List<String>> responseHeaders) {
                      if (result == null) {
                        Step create =
                            factory
                                .create()
                                .createConfigMapAsync(
                                    domainNamespace,
                                    cm,
                                    new ResponseStep<V1ConfigMap>(next) {
                                      @Override
                                      public NextAction onFailure(
                                          Packet packet,
                                          ApiException e,
                                          int statusCode,
                                          Map<String, List<String>> responseHeaders) {
                                        return super.onFailure(
                                            ScriptConfigMapStep.this,
                                            packet,
                                            e,
                                            statusCode,
                                            responseHeaders);
                                      }

                                      @Override
                                      public NextAction onSuccess(
                                          Packet packet,
                                          V1ConfigMap result,
                                          int statusCode,
                                          Map<String, List<String>> responseHeaders) {

                                        LOGGER.info(MessageKeys.CM_CREATED, domainNamespace);
                                        packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
                                        return doNext(packet);
                                      }
                                    });
                        return doNext(create, packet);
                      } else if (VersionHelper.matchesResourceVersion(
                              result.getMetadata(), VersionConstants.DOMAIN_V1)
                          && result.getData().entrySet().containsAll(cm.getData().entrySet())) {
                        // existing config map has correct data
                        LOGGER.fine(MessageKeys.CM_EXISTS, domainNamespace);
                        packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, result);
                        return doNext(packet);
                      } else {
                        // we need to update the config map
                        Map<String, String> updated = result.getData();
                        updated.putAll(cm.getData());
                        cm.setData(updated);
                        Step replace =
                            factory
                                .create()
                                .replaceConfigMapAsync(
                                    cm.getMetadata().getName(),
                                    domainNamespace,
                                    cm,
                                    new ResponseStep<V1ConfigMap>(next) {
                                      @Override
                                      public NextAction onFailure(
                                          Packet packet,
                                          ApiException e,
                                          int statusCode,
                                          Map<String, List<String>> responseHeaders) {
                                        return super.onFailure(
                                            ScriptConfigMapStep.this,
                                            packet,
                                            e,
                                            statusCode,
                                            responseHeaders);
                                      }

                                      @Override
                                      public NextAction onSuccess(
                                          Packet packet,
                                          V1ConfigMap result,
                                          int statusCode,
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

      Map<String, String> labels = new HashMap<>();
      labels.put(LabelConstants.RESOURCE_VERSION_LABEL, VersionConstants.DOMAIN_V1);
      labels.put(LabelConstants.OPERATORNAME_LABEL, operatorNamespace);
      labels.put(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
      metadata.setLabels(labels);

      cm.setMetadata(metadata);
      cm.setData(loadScripts(domainNamespace));

      return cm;
    }

    private static synchronized Map<String, String> loadScripts(String domainNamespace) {
      URI uri;
      try {
        uri = ScriptConfigMapStep.class.getResource(SCRIPT_LOCATION).toURI();
      } catch (URISyntaxException e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
        throw new RuntimeException(e);
      }

      try {
        if ("jar".equals(uri.getScheme())) {
          try (FileSystem fileSystem = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
            return walkScriptsPath(fileSystem.getPath(SCRIPTS), domainNamespace);
          }
        } else {
          return walkScriptsPath(Paths.get(uri), domainNamespace);
        }
      } catch (IOException e) {
        LOGGER.warning(MessageKeys.EXCEPTION, e);
        throw new RuntimeException(e);
      }
    }

    private static Map<String, String> walkScriptsPath(Path scriptsDir, String domainNamespace)
        throws IOException {
      try (Stream<Path> walk = Files.walk(scriptsDir, 1)) {
        Map<String, String> data =
            walk.filter(i -> !Files.isDirectory(i))
                .collect(
                    Collectors.toMap(
                        i -> i.getFileName().toString(),
                        i -> new String(read(i), StandardCharsets.UTF_8)));
        LOGGER.info(MessageKeys.SCRIPT_LOADED, domainNamespace);
        return data;
      }
    }

    private static byte[] read(Path path) {
      try {
        return Files.readAllBytes(path);
      } catch (IOException io) {
        LOGGER.warning(MessageKeys.EXCEPTION, io);
      }
      return null;
    }
  }
}

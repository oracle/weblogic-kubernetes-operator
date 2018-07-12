// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

  private static final String SCRIPT_LOCATION = "/scripts";
  private static final ConfigMapComparator COMPARATOR = new ConfigMapComparatorImpl();

  private static FileGroupReader scriptReader = new FileGroupReader(SCRIPT_LOCATION);

  private ConfigMapHelper() {}

  /**
   * Factory for {@link Step} that creates config map containing scripts
   *
   * @param operatorNamespace the operator's namespace
   * @param domainNamespace the domain's namespace
   * @return Step for creating config map containing scripts
   */
  public static Step createScriptConfigMapStep(String operatorNamespace, String domainNamespace) {
    return new ScriptConfigMapStep(operatorNamespace, domainNamespace, null);
  }

  // Make this public so that it can be unit tested
  public static class ScriptConfigMapStep extends Step {
    private final String operatorNamespace;
    private final String domainNamespace;

    ScriptConfigMapStep(String operatorNamespace, String domainNamespace, Step next) {
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
                  new ResponseStep<V1ConfigMap>(getNext()) {
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
                                    new ResponseStep<V1ConfigMap>(getNext()) {
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
                          && COMPARATOR.containsAll(result, cm)) {
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
                                    new ResponseStep<V1ConfigMap>(getNext()) {
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
      cm.setData(loadScriptsFromClasspath());

      return cm;
    }

    private synchronized Map<String, String> loadScriptsFromClasspath() {
      Map<String, String> scripts = scriptReader.loadFilesFromClasspath();
      LOGGER.info(MessageKeys.SCRIPT_LOADED, domainNamespace);
      return scripts;
    }
  }

  static FileGroupReader getScriptReader() {
    return scriptReader;
  }

  interface ConfigMapComparator {
    /** Returns true if the actual map contains all of the entries from the expected map. */
    boolean containsAll(V1ConfigMap actual, V1ConfigMap expected);
  }

  static class ConfigMapComparatorImpl implements ConfigMapComparator {
    @Override
    public boolean containsAll(V1ConfigMap actual, V1ConfigMap expected) {
      return actual.getData().entrySet().containsAll(expected.getData().entrySet());
    }
  }
}

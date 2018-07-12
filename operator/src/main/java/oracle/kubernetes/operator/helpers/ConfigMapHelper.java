// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.VersionConstants.DOMAIN_V1;

import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import java.util.Map;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
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
    return new ScriptConfigMapStep(operatorNamespace, domainNamespace);
  }

  static class ScriptConfigMapStep extends Step {
    ConfigMapContext context;

    ScriptConfigMapStep(String operatorNamespace, String domainNamespace) {
      context = new ConfigMapContext(this, operatorNamespace, domainNamespace);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyConfigMap(getNext()), packet);
    }
  }

  static class ConfigMapContext {
    private final Step conflictStep;
    private final String operatorNamespace;
    private final String domainNamespace;
    private final V1ConfigMap model;
    private final Map<String, String> classpathScripts = loadScriptsFromClasspath();

    ConfigMapContext(Step conflictStep, String operatorNamespace, String domainNamespace) {
      this.conflictStep = conflictStep;
      this.operatorNamespace = operatorNamespace;
      this.domainNamespace = domainNamespace;
      this.model = createModel(classpathScripts);
    }

    private V1ConfigMap createModel(Map<String, String> data) {
      return new V1ConfigMap()
          .apiVersion("v1")
          .kind("ConfigMap")
          .metadata(createMetadata())
          .data(data);
    }

    private V1ObjectMeta createMetadata() {
      return new V1ObjectMeta()
          .name(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
          .namespace(this.domainNamespace)
          .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, DOMAIN_V1)
          .putLabelsItem(LabelConstants.OPERATORNAME_LABEL, operatorNamespace)
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    }

    private synchronized Map<String, String> loadScriptsFromClasspath() {
      Map<String, String> scripts = scriptReader.loadFilesFromClasspath();
      LOGGER.info(MessageKeys.SCRIPT_LOADED, this.domainNamespace);
      return scripts;
    }

    Step verifyConfigMap(Step next) {
      return new CallBuilder()
          .readConfigMapAsync(
              model.getMetadata().getName(), domainNamespace, createReadResponseStep(next));
    }

    ResponseStep<V1ConfigMap> createReadResponseStep(Step next) {
      return new ReadResponseStep(next);
    }

    class ReadResponseStep extends DefaultResponseStep<V1ConfigMap> {
      ReadResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
        V1ConfigMap existingMap = callResponse.getResult();
        if (existingMap == null) {
          return doNext(createConfigMap(getNext()), packet);
        } else if (isCompatibleMap(existingMap)) {
          logConfigMapExists();
          packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, existingMap);
          return doNext(packet);
        } else {
          return doNext(updateConfigMap(getNext(), existingMap), packet);
        }
      }
    }

    Step createConfigMap(Step next) {
      return new CallBuilder()
          .createConfigMapAsync(domainNamespace, model, createCreateResponseStep(next));
    }

    ResponseStep<V1ConfigMap> createCreateResponseStep(Step next) {
      return new CreateResponseStep(next);
    }

    private class CreateResponseStep extends ResponseStep<V1ConfigMap> {
      CreateResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1ConfigMap> callResponse) {
        return super.onFailure(conflictStep, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
        LOGGER.info(MessageKeys.CM_CREATED, domainNamespace);
        packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, callResponse.getResult());
        return doNext(packet);
      }
    }

    private boolean isCompatibleMap(V1ConfigMap existingMap) {
      return VersionHelper.matchesResourceVersion(existingMap.getMetadata(), DOMAIN_V1)
          && COMPARATOR.containsAll(existingMap, this.model);
    }

    void logConfigMapExists() {
      LOGGER.fine(MessageKeys.CM_EXISTS, domainNamespace);
    }

    Step updateConfigMap(Step next, V1ConfigMap existingConfigMap) {
      return new CallBuilder()
          .replaceConfigMapAsync(
              model.getMetadata().getName(),
              domainNamespace,
              createModel(getCombinedData(existingConfigMap)),
              createReplaceResponseStep(next));
    }

    Map<String, String> getCombinedData(V1ConfigMap existingConfigMap) {
      Map<String, String> updated = existingConfigMap.getData();
      updated.putAll(this.classpathScripts);
      return updated;
    }

    ResponseStep<V1ConfigMap> createReplaceResponseStep(Step next) {
      return new ReplaceResponseStep(next);
    }

    private class ReplaceResponseStep extends ResponseStep<V1ConfigMap> {
      ReplaceResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1ConfigMap> callResponse) {
        return super.onFailure(conflictStep, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
        LOGGER.info(MessageKeys.CM_REPLACED, domainNamespace);
        packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, callResponse.getResult());
        return doNext(packet);
      }
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

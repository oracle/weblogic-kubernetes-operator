// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1DeleteOptions;
import io.kubernetes.client.models.V1ObjectMeta;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.rest.Scan;
import oracle.kubernetes.operator.rest.ScanCache;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.joda.time.DateTime;

public class ConfigMapHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String SCRIPT_LOCATION = "/scripts";
  private static final ConfigMapComparator COMPARATOR = new ConfigMapComparatorImpl();

  private static final FileGroupReader scriptReader = new FileGroupReader(SCRIPT_LOCATION);

  private ConfigMapHelper() {}

  /**
   * Factory for {@link Step} that creates config map containing scripts.
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
      context = new ScriptConfigMapContext(this, operatorNamespace, domainNamespace);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyConfigMap(getNext()), packet);
    }
  }

  static class ScriptConfigMapContext extends ConfigMapContext {
    private final Map<String, String> classpathScripts = loadScriptsFromClasspath();

    ScriptConfigMapContext(Step conflictStep, String operatorNamespace, String domainNamespace) {
      super(conflictStep, operatorNamespace, domainNamespace);

      this.model = createModel(classpathScripts);
    }

    private V1ConfigMap createModel(Map<String, String> data) {
      return new V1ConfigMap().metadata(createMetadata()).data(data);
    }

    private V1ObjectMeta createMetadata() {
      return super.createMetadata(KubernetesConstants.DOMAIN_CONFIG_MAP_NAME)
          .putLabelsItem(LabelConstants.OPERATORNAME_LABEL, operatorNamespace);
    }

    private synchronized Map<String, String> loadScriptsFromClasspath() {
      Map<String, String> scripts = scriptReader.loadFilesFromClasspath();
      LOGGER.fine(MessageKeys.SCRIPT_LOADED, this.domainNamespace);
      return scripts;
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
  }

  abstract static class ConfigMapContext {
    protected final Step conflictStep;
    protected final String operatorNamespace;
    protected final String domainNamespace;
    protected V1ConfigMap model;

    ConfigMapContext(Step conflictStep, String operatorNamespace, String domainNamespace) {
      this.conflictStep = conflictStep;
      this.operatorNamespace = operatorNamespace;
      this.domainNamespace = domainNamespace;
    }

    protected V1ObjectMeta createMetadata(String configMapName) {
      return new V1ObjectMeta()
          .name(configMapName)
          .namespace(this.domainNamespace)
          .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION)
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    }

    Step verifyConfigMap(Step next) {
      return new CallBuilder()
          .readConfigMapAsync(
              model.getMetadata().getName(), domainNamespace, createReadResponseStep(next));
    }

    abstract ResponseStep<V1ConfigMap> createReadResponseStep(Step next);

    Step createConfigMap(Step next) {
      return new CallBuilder()
          .createConfigMapAsync(domainNamespace, model, createCreateResponseStep(next));
    }

    abstract ResponseStep<V1ConfigMap> createCreateResponseStep(Step next);

    protected boolean isCompatibleMap(V1ConfigMap existingMap) {
      return VersionHelper.matchesResourceVersion(existingMap.getMetadata(), DEFAULT_DOMAIN_VERSION)
          && COMPARATOR.containsAll(existingMap, this.model);
    }

    void logConfigMapExists() {
      LOGGER.fine(MessageKeys.CM_EXISTS, domainNamespace);
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

  /**
   * Factory for {@link Step} that creates config map containing sit config.
   *
   * @param next Next step
   * @return Step for creating config map containing sit config
   */
  public static Step createSitConfigMapStep(Step next) {
    return new SitConfigMapStep(next);
  }

  static class SitConfigMapStep extends Step {

    SitConfigMapStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      String result = (String) packet.remove(ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT);
      // Parse results into separate data files
      Map<String, String> data = parseIntrospectorResult(result, info.getDomainUID());
      LOGGER.fine("================");
      LOGGER.fine(data.toString());
      LOGGER.fine("================");
      String topologyYaml = data.get("topology.yaml");
      if (topologyYaml != null) {
        LOGGER.fine("topology.yaml: " + topologyYaml);
        DomainTopology domainTopology = parseDomainTopologyYaml(topologyYaml);
        if (domainTopology == null || !domainTopology.getDomainValid()) {
          // If introspector determines Domain is invalid then log erros and terminate the fiber
          if (domainTopology != null) {
            logValidationErrors(domainTopology.getValidationErrors());
          }
          return doNext(null, packet);
        }
        WlsDomainConfig wlsDomainConfig = domainTopology.getDomain();
        ScanCache.INSTANCE.registerScan(
            info.getNamespace(), info.getDomainUID(), new Scan(wlsDomainConfig, new DateTime()));
        packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, wlsDomainConfig);
        LOGGER.info(
            MessageKeys.WLS_CONFIGURATION_READ,
            (System.currentTimeMillis() - ((Long) packet.get(JobHelper.START_TIME))),
            wlsDomainConfig);
        SitConfigMapContext context =
            new SitConfigMapContext(
                this, info.getDomainUID(), getOperatorNamespace(), info.getNamespace(), data);

        return doNext(context.verifyConfigMap(getNext()), packet);
      }

      // TODO: How do we handle no topology?
      return doNext(getNext(), packet);
    }

    private void logValidationErrors(List<String> validationErrors) {
      if (!validationErrors.isEmpty()) {
        for (String err : validationErrors) {
          LOGGER.severe(err);
        }
      }
    }

    private static String getOperatorNamespace() {
      String namespace = System.getenv("OPERATOR_NAMESPACE");
      if (namespace == null) {
        namespace = "default";
      }
      return namespace;
    }
  }

  public static class SitConfigMapContext extends ConfigMapContext {
    Map<String, String> data;
    String domainUID;
    String cmName;

    SitConfigMapContext(
        Step conflictStep,
        String domainUID,
        String operatorNamespace,
        String domainNamespace,
        Map<String, String> data) {
      super(conflictStep, operatorNamespace, domainNamespace);

      this.domainUID = domainUID;
      this.cmName = getConfigMapName(domainUID);
      this.data = data;
      this.model = createModel(data);
    }

    private V1ConfigMap createModel(Map<String, String> data) {
      return new V1ConfigMap()
          .apiVersion("v1")
          .kind("ConfigMap")
          .metadata(createMetadata())
          .data(data);
    }

    public static String getConfigMapName(String domainUID) {
      return domainUID + KubernetesConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
    }

    ResponseStep<V1ConfigMap> createReadResponseStep(Step next) {
      return new ReadResponseStep(next);
    }

    private V1ObjectMeta createMetadata() {
      return super.createMetadata(cmName).putLabelsItem(LabelConstants.DOMAINUID_LABEL, domainUID);
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
          packet.put(ProcessingConstants.SIT_CONFIG_MAP, existingMap);
          return doNext(packet);
        } else {
          return doNext(updateConfigMap(getNext(), existingMap), packet);
        }
      }
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
        packet.put(ProcessingConstants.SIT_CONFIG_MAP, callResponse.getResult());
        return doNext(packet);
      }
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
        packet.put(ProcessingConstants.SIT_CONFIG_MAP, callResponse.getResult());
        return doNext(packet);
      }
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
      updated.putAll(this.data);
      return updated;
    }
  }

  /**
   * Factory for {@link Step} that deletes introspector config map.
   *
   * @param domainUID The unique identifier assigned to the Weblogic domain when it was registered
   * @param namespace Namespace
   * @param next Next processing step
   * @return Step for deleting introspector config map
   */
  public static Step deleteDomainIntrospectorConfigMapStep(
      String domainUID, String namespace, Step next) {
    return new DeleteIntrospectorConfigMapStep(domainUID, namespace, next);
  }

  private static class DeleteIntrospectorConfigMapStep extends Step {
    private String domainUID;
    private String namespace;

    DeleteIntrospectorConfigMapStep(String domainUID, String namespace, Step next) {
      super(next);
      this.domainUID = domainUID;
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(deleteSitConfigMap(getNext()), packet);
    }

    String getConfigMapDeletedMessageKey() {
      return "Domain Introspector config map "
          + SitConfigMapContext.getConfigMapName(this.domainUID)
          + " deleted";
    }

    protected void logConfigMapDeleted() {
      LOGGER.info(getConfigMapDeletedMessageKey());
    }

    private Step deleteSitConfigMap(Step next) {
      logConfigMapDeleted();
      String configMapName = SitConfigMapContext.getConfigMapName(this.domainUID);
      Step step =
          new CallBuilder()
              .deleteConfigMapAsync(
                  configMapName,
                  this.namespace,
                  new V1DeleteOptions(),
                  new DefaultResponseStep<>(next));
      return step;
    }
  }

  public static Step readExistingSituConfigMap(String ns, String domainUID) {
    String situConfigMapName = ConfigMapHelper.SitConfigMapContext.getConfigMapName(domainUID);
    return new CallBuilder().readConfigMapAsync(situConfigMapName, ns, new ReadSituConfigMapStep());
  }

  private static class ReadSituConfigMapStep extends ResponseStep<V1ConfigMap> {

    ReadSituConfigMapStep() {}

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      return callResponse.getStatusCode() == CallBuilder.NOT_FOUND
          ? onSuccess(packet, callResponse)
          : super.onFailure(packet, callResponse);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      DomainPresenceInfo info = packet.getSPI(DomainPresenceInfo.class);

      V1ConfigMap result = callResponse.getResult();
      if (result != null) {
        Map<String, String> data = result.getData();
        String topologyYaml = data.get("topology.yaml");
        if (topologyYaml != null) {
          ConfigMapHelper.DomainTopology domainTopology =
              ConfigMapHelper.parseDomainTopologyYaml(topologyYaml);
          if (domainTopology != null) {
            WlsDomainConfig wlsDomainConfig = domainTopology.getDomain();
            ScanCache.INSTANCE.registerScan(
                info.getNamespace(),
                info.getDomainUID(),
                new Scan(wlsDomainConfig, new DateTime()));
            packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, wlsDomainConfig);
          }
        }
      }

      return doNext(packet);
    }
  }

  static Map<String, String> parseIntrospectorResult(String text, String domainUID) {
    Map<String, String> map = new HashMap<>();
    try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
      String line = reader.readLine();
      while (line != null) {
        if (line.startsWith(">>>") && !line.endsWith("EOF")) {
          // Beginning of file, extract file name
          String filename = extractFilename(line);
          readFile(reader, filename, map, domainUID);
        }
        line = reader.readLine();
      }
    } catch (IOException exc) {
      LOGGER.warning(MessageKeys.CANNOT_PARSE_INTROSPECTOR_RESULT, domainUID, exc);
    }

    return map;
  }

  static void readFile(
      BufferedReader reader, String fileName, Map<String, String> map, String domainUID) {
    StringBuilder stringBuilder = new StringBuilder();
    try {
      String line = reader.readLine();
      while (line != null) {
        if (line.startsWith(">>>") && line.endsWith("EOF")) {
          map.put(fileName, stringBuilder.toString());
          return;
        } else {
          // add line to StringBuilder
          stringBuilder.append(line);
          stringBuilder.append(System.getProperty("line.separator"));
        }
        line = reader.readLine();
      }
    } catch (IOException ioe) {
      LOGGER.warning(MessageKeys.CANNOT_PARSE_INTROSPECTOR_FILE, fileName, domainUID, ioe);
    }
  }

  static String extractFilename(String line) {
    int lastSlash = line.lastIndexOf('/');
    String fname = line.substring(lastSlash + 1, line.length());
    return fname;
  }

  public static DomainTopology parseDomainTopologyYaml(String topologyYaml) {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    try {
      DomainTopology domainTopology = mapper.readValue(topologyYaml, DomainTopology.class);

      LOGGER.fine(
          ReflectionToStringBuilder.toString(domainTopology, ToStringStyle.MULTI_LINE_STYLE));

      return domainTopology;

    } catch (Exception e) {
      LOGGER.warning(MessageKeys.CANNOT_PARSE_TOPOLOGY, e);
    }

    return null;
  }

  public static class DomainTopology {
    private boolean domainValid;
    private WlsDomainConfig domain;
    private List<String> validationErrors;

    public boolean getDomainValid() {
      // domainValid = true AND no validation errors exist
      if (domainValid && getValidationErrors().isEmpty()) {
        return true;
      }
      return false;
    }

    public void setDomainValid(boolean domainValid) {
      this.domainValid = domainValid;
    }

    public WlsDomainConfig getDomain() {
      this.domain.processDynamicClusters();
      return this.domain;
    }

    public void setDomain(WlsDomainConfig domain) {
      this.domain = domain;
    }

    public List<String> getValidationErrors() {
      if (validationErrors == null) {
        validationErrors = Collections.emptyList();
      }

      if (!domainValid && validationErrors.isEmpty()) {
        // add a log message that domain was marked invalid since we have no validation
        // errors from introspector.
        validationErrors = new ArrayList<>();
        validationErrors.add(
            "Error, domain is invalid although there are no validation errors from introspector job.");
      }

      return validationErrors;
    }

    public void setValidationErrors(List<String> validationErrors) {
      this.validationErrors = validationErrors;
    }

    public String toString() {
      if (domainValid) {
        return "domain: " + domain;
      }
      return "domainValid: " + domainValid + ", validationErrors: " + validationErrors;
    }
  }
}

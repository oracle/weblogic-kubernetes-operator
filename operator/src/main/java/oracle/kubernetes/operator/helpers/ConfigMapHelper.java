// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.json.Json;
import javax.json.JsonPatchBuilder;
import javax.json.JsonValue;
import javax.validation.constraints.NotNull;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.IntrospectorConfigMapKeys;
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
import oracle.kubernetes.weblogic.domain.model.Domain;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.joda.time.DateTime;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAINZIP_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAIN_INPUTS_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAIN_RESTART_VERSION;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.SECRETS_MD_5;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.SIT_CONFIG_FILE_PREFIX;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_VALIDATION_ERRORS;

public class ConfigMapHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String SCRIPT_LOCATION = "/scripts";
  private static final ConfigMapComparator COMPARATOR = new ConfigMapComparatorImpl();

  private static final FileGroupReader scriptReader = new FileGroupReader(SCRIPT_LOCATION);

  private ConfigMapHelper() {
  }

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

  static FileGroupReader getScriptReader() {
    return scriptReader;
  }

  static Map<String, String> parseIntrospectorResult(String text, String domainUid) {
    Map<String, String> map = new HashMap<>();
    String token = ">>>  updatedomainResult=";

    try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
      String line = reader.readLine();
      while (line != null) {
        if (line.contains(token)) {
          int index = line.indexOf(token);
          int beg = index + 1 + token.length();
          map.put("UPDATEDOMAINRESULT", line.substring(beg - 1));
        }
        if (line.startsWith(">>>") && !line.endsWith("EOF")) {
          String filename = extractFilename(line);
          readFile(reader, filename, map, domainUid);
        }
        line = reader.readLine();
      }
    } catch (IOException exc) {
      LOGGER.warning(MessageKeys.CANNOT_PARSE_INTROSPECTOR_RESULT, domainUid, exc);
    }

    return map;
  }

  static void readFile(
      BufferedReader reader, String fileName, Map<String, String> map, String domainUid) {
    StringBuilder stringBuilder = new StringBuilder();
    try {
      String line = reader.readLine();
      while (line != null) {
        if (line.startsWith(">>>") && line.endsWith("EOF")) {
          map.put(fileName, stringBuilder.toString().trim());
          return;
        } else {
          // add line to StringBuilder
          stringBuilder.append(line);
          stringBuilder.append(System.getProperty("line.separator"));
        }
        line = reader.readLine();
      }
    } catch (IOException ioe) {
      LOGGER.warning(MessageKeys.CANNOT_PARSE_INTROSPECTOR_FILE, fileName, domainUid, ioe);
    }
  }

  static String extractFilename(String line) {
    int lastSlash = line.lastIndexOf('/');
    return line.substring(lastSlash + 1);
  }

  /**
   * getModelInImageSpecHash returns the hash for the fields that should be compared for changes.
   *
   * @param imageName image name
   * @return int hash value of the fields
   */
  public static int getModelInImageSpecHash(String imageName) {
    return new HashCodeBuilder(17, 37)
        .append(imageName)
        .toHashCode();
  }

  /**
   * Returns the standard name for the generated domain config map.
   * @param domainUid the unique ID of the domain
   * @return map name
   */
  public static String getIntrospectorConfigMapName(String domainUid) {
    return domainUid + KubernetesConstants.INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX;
  }

  abstract static class ConfigMapComparator {
    boolean containsAll(V1ConfigMap actual, V1ConfigMap expected) {
      return containsAllData(getData(actual), getData(expected));
    }

    private Map<String,String> getData(V1ConfigMap map) {
      return Optional.ofNullable(map).map(V1ConfigMap::getData).orElse(Collections.emptyMap());
    }

    abstract boolean containsAllData(Map<String, String> actual, Map<String, String> expected);
  }

  static class ScriptConfigMapStep extends Step {
    final ConfigMapContext context;

    ScriptConfigMapStep(String operatorNamespace, String domainNamespace) {
      context = new ScriptConfigMapContext(this, operatorNamespace, domainNamespace);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyConfigMap(getNext()), packet);
    }
  }

  static class ScriptConfigMapContext extends ConfigMapContext {

    ScriptConfigMapContext(Step conflictStep, String operatorNamespace, String domainNamespace) {
      super(conflictStep, SCRIPT_CONFIG_MAP_NAME, domainNamespace, loadScriptsFromClasspath(domainNamespace), null);

      addLabel(LabelConstants.OPERATORNAME_LABEL, operatorNamespace);
    }

    private static synchronized Map<String, String> loadScriptsFromClasspath(String domainNamespace) {
      Map<String, String> scripts = scriptReader.loadFilesFromClasspath();
      LOGGER.fine(MessageKeys.SCRIPT_LOADED, domainNamespace);
      return scripts;
    }

    @Override
    void recordCurrentMap(Packet packet, V1ConfigMap configMap) {
      packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, configMap);
    }


  }

  abstract static class ConfigMapContext extends StepContextBase {
    private final Map<String, String> contents;
    private final Step conflictStep;
    private final String name;
    private final String namespace;
    private V1ConfigMap model;
    private final Map<String, String> labels = new HashMap<>();

    ConfigMapContext(Step conflictStep, String name, String namespace, Map<String, String> contents,
                     DomainPresenceInfo info) {
      super(info);
      this.conflictStep = conflictStep;
      this.name = name;
      this.namespace = namespace;
      this.contents = contents;

      addLabel(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    }

    /**
     * This method is invoked after all config map processing is done. Subclasses may override
     * it to record the config map in a packet.
     * @param packet the packet
     * @param configMap the final config map
     */
    void recordCurrentMap(Packet packet, V1ConfigMap configMap) {
    }

    protected String getName() {
      return name;
    }

    protected V1ConfigMap getModel() {
      if (model == null) {
        model = createModel(contents);
      }
      return model;
    }

    protected final V1ConfigMap createModel(Map<String, String> data) {
      return new V1ConfigMap().kind("ConfigMap").apiVersion("v1").metadata(createMetadata()).data(data);
    }

    private V1ObjectMeta createMetadata() {
      return updateForOwnerReference(
          new V1ObjectMeta()
          .name(name)
          .namespace(namespace)
          .labels(labels));
    }

    @SuppressWarnings("SameParameterValue")
    void addLabel(String name, String value) {
      labels.put(name, value);
      model = null;
    }

    private Map<String,String> getLabels() {
      return Collections.unmodifiableMap(labels);
    }

    /**
     * Creates the step which begins verifying or updating the config map.
     * @param next the step to run after the config map processing is done
     * @return the new step to run
     */
    Step verifyConfigMap(Step next) {
      return new CallBuilder().readConfigMapAsync(getName(), namespace, new ReadResponseStep(next));
    }

    Step createConfigMap(Step next) {
      return new CallBuilder()
          .createConfigMapAsync(namespace, getModel(), createCreateResponseStep(next));
    }

    boolean isIncompatibleMap(V1ConfigMap existingMap) {
      return !COMPARATOR.containsAll(existingMap, getModel());
    }

    V1ConfigMap withoutTransientData(V1ConfigMap originalMap) {
      if (originalMap != null && originalMap.getData() != null) {
        originalMap.setData(withoutTransientEntries(originalMap.getData()));
      }
      return originalMap;
    }

    private Map<String, String> withoutTransientEntries(Map<String, String> data) {
      data.entrySet().removeIf(this::shouldRemove);
      return data;
    }

    boolean shouldRemove(Map.Entry<String, String> entry) {
      return false;
    }

    class ReadResponseStep extends DefaultResponseStep<V1ConfigMap> {
      ReadResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
        DomainPresenceInfo.fromPacket(packet).map(DomainPresenceInfo::getDomain).map(Domain::getIntrospectVersion)
              .ifPresent(value -> addLabel(INTROSPECTION_STATE_LABEL, value));
        V1ConfigMap existingMap = withoutTransientData(callResponse.getResult());
        if (existingMap == null) {
          return doNext(createConfigMap(getNext()), packet);
        } else if (isIncompatibleMap(existingMap)) {
          return doNext(updateConfigMap(getNext(), existingMap), packet);
        } else if (mustPatchCurrentMap(existingMap)) {
          return doNext(patchCurrentMap(existingMap, getNext()), packet);
        } else {
          logConfigMapExists();
          recordCurrentMap(packet, existingMap);
          return doNext(packet);
        }
      }

      private Step createConfigMap(Step next) {
        return new CallBuilder()
            .createConfigMapAsync(namespace, getModel(), createCreateResponseStep(next));
      }

      private void logConfigMapExists() {
        LOGGER.fine(MessageKeys.CM_EXISTS, getName(), namespace);
      }

      private Step updateConfigMap(Step next, V1ConfigMap existingConfigMap) {
        return new CallBuilder().replaceConfigMapAsync(name, namespace,
                                        createModel(getCombinedData(existingConfigMap)),
                                        createReplaceResponseStep(next));
      }

      private boolean mustPatchCurrentMap(V1ConfigMap currentMap) {
        return KubernetesUtils.isMissingValues(getMapLabels(currentMap), getLabels());
      }

      private Map<String, String> getMapLabels(@NotNull V1ConfigMap map) {
        return Optional.ofNullable(map.getMetadata()).map(V1ObjectMeta::getLabels).orElseGet(Collections::emptyMap);
      }

      private Step patchCurrentMap(V1ConfigMap currentMap, Step next) {
        JsonPatchBuilder patchBuilder = Json.createPatchBuilder();

        if (labelsNotDefined(currentMap)) {
          patchBuilder.add("/metadata/labels", JsonValue.EMPTY_JSON_OBJECT);
        }
        
        KubernetesUtils.addPatches(
            patchBuilder, "/metadata/labels/", getMapLabels(currentMap), getLabels());

        return new CallBuilder()
            .patchConfigMapAsync(name, namespace,
                new V1Patch(patchBuilder.build().toString()), createPatchResponseStep(next));
      }

      private boolean labelsNotDefined(V1ConfigMap currentMap) {
        return Objects.requireNonNull(currentMap.getMetadata()).getLabels() == null;
      }
    }

    private Map<String, String> getCombinedData(V1ConfigMap existingConfigMap) {
      Map<String, String> updated = Objects.requireNonNull(existingConfigMap.getData());
      updated.putAll(contents);
      return updated;
    }

    private ResponseStep<V1ConfigMap> createCreateResponseStep(Step next) {
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
        LOGGER.info(MessageKeys.CM_CREATED, getName(), namespace);
        recordCurrentMap(packet, callResponse.getResult());
        return doNext(packet);
      }
    }

    private ResponseStep<V1ConfigMap> createReplaceResponseStep(Step next) {
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
        LOGGER.info(MessageKeys.CM_REPLACED, getName(), namespace);
        recordCurrentMap(packet, callResponse.getResult());
        return doNext(packet);
      }
    }


    private ResponseStep<V1ConfigMap> createPatchResponseStep(Step next) {
      return new PatchResponseStep(next);
    }

    private class PatchResponseStep extends ResponseStep<V1ConfigMap> {

      PatchResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
        LOGGER.info(MessageKeys.CM_PATCHED, getName(), namespace);
        return doNext(packet);
      }
    }

  }

  /** Returns true if the actual map contains all of the entries from the expected map. */
  static class ConfigMapComparatorImpl extends ConfigMapComparator {

    @Override
    boolean containsAllData(Map<String, String> actual, Map<String, String> expected) {
      return actual.entrySet().containsAll(expected.entrySet());
    }
  }

  /**
   * Factory for a step that creates or updates the generated domain config map from introspection results.
   * Reads the following packet fields:
   *   DOMAIN_INTROSPECTOR_LOG_RESULT     the introspection result
   * and updates:
   *   DOMAIN_TOPOLOGY                    the parsed topology
   *   DOMAIN_HASH                        a hash of the topology
   *   SECRETS_HASH                       a hash of the override secrets
   *   DOMAIN_RESTART_VERSION             a field from the domain to force rolling when changed
   *   DOMAIN_INPUTS_HASH                 a hash of the image used in the domain
   *
   * @param next Next step
   * @return Step for creating config map containing introspection results
   */
  public static Step createIntrospectorConfigMapStep(Step next) {
    return new IntrospectionConfigMapStep(next);
  }

  /**
   * The first in a chain of steps to create the introspector config map from introspection results.
   */
  static class IntrospectionConfigMapStep extends Step {

    IntrospectionConfigMapStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      IntrospectionLoader loader = new IntrospectionLoader(packet, this);
      if (loader.isTopologyNotValid()) {
        return doNext(reportTopologyErrorsAndStop(), packet);
      } else if (loader.getDomainConfig() == null)  {
        return doNext(loader.createIntrospectionVersionUpdateStep(), packet);
      } else {
        LOGGER.fine(MessageKeys.WLS_CONFIGURATION_READ, timeSinceJobStart(packet), loader.getDomainConfig());
        loader.updatePacket();
        return doNext(loader.createValidationStep(), packet);
      }
    }

    private long timeSinceJobStart(Packet packet) {
      return System.currentTimeMillis() - ((Long) packet.get(JobHelper.START_TIME));
    }

  }

  static class IntrospectionLoader {
    private final Packet packet;
    private final Step conflictStep;
    private final DomainPresenceInfo info;
    private Map<String, String> data;
    private WlsDomainConfig wlsDomainConfig;

    IntrospectionLoader(Packet packet, Step conflictStep) {
      this.packet = packet;
      this.info = packet.getSpi(DomainPresenceInfo.class);
      this.conflictStep = conflictStep;
      parseIntrospectorResult();
    }

    private void parseIntrospectorResult() {
      String result = (String) packet.remove(ProcessingConstants.DOMAIN_INTROSPECTOR_LOG_RESULT);
      data = ConfigMapHelper.parseIntrospectorResult(result, info.getDomainUid());

      LOGGER.fine("================");
      LOGGER.fine(data.toString());
      LOGGER.fine("================");

      wlsDomainConfig = Optional.ofNullable(data.get(IntrospectorConfigMapKeys.TOPOLOGY_YAML))
            .map(this::getDomainTopology)
            .map(DomainTopology::getDomain)
            .orElse(null);
    }

    boolean isTopologyNotValid() {
      return packet.containsKey(DOMAIN_VALIDATION_ERRORS);
    }

    private void updatePacket() {
      ScanCache.INSTANCE.registerScan(
            info.getNamespace(), info.getDomainUid(), new Scan(wlsDomainConfig, new DateTime()));
      packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, wlsDomainConfig);

      copyFileToPacketIfPresent(DOMAINZIP_HASH, DOMAINZIP_HASH);
      copyFileToPacketIfPresent(SECRETS_MD_5, SECRETS_MD_5);
      copyToPacketAndFileIfPresent(DOMAIN_RESTART_VERSION, info.getDomain().getRestartVersion());
      copyToPacketAndFileIfPresent(DOMAIN_INPUTS_HASH, getModelInImageSpecHash());
    }

    private Step createIntrospectionVersionUpdateStep() {
      return DomainValidationSteps.createValidateDomainTopologyStep(
            createIntrospectorConfigMapContext(conflictStep).patchOnly().verifyConfigMap(conflictStep.getNext()));
    }

    private Step createValidationStep() {
      return DomainValidationSteps.createValidateDomainTopologyStep(
            createIntrospectorConfigMapContext(conflictStep).verifyConfigMap(conflictStep.getNext()));
    }

    private IntrospectorConfigMapContext createIntrospectorConfigMapContext(Step conflictStep) {
      return new IntrospectorConfigMapContext(conflictStep, info.getDomain(), data, info);
    }

    private String getModelInImageSpecHash() {
      return String.valueOf(ConfigMapHelper.getModelInImageSpecHash(info.getDomain().getSpec().getImage()));
    }

    private void copyFileToPacketIfPresent(String fileName, String packetKey) {
      Optional.ofNullable(data.get(fileName)).ifPresent(value -> packet.put(packetKey, value));
    }

    private void copyToPacketAndFileIfPresent(String packetKey, String stringValue) {
      Optional.ofNullable(stringValue).ifPresent(value -> populatePacketAndFile(packetKey, value));
    }

    private void populatePacketAndFile(String packetKey, String value) {
      packet.put(packetKey, value);
      data.put(packetKey, value);
    }

    private WlsDomainConfig getDomainConfig() {
      return wlsDomainConfig;
    }

    private DomainTopology getDomainTopology(String topologyYaml) {
      LOGGER.fine("topology.yaml: " + topologyYaml);
      return DomainTopology.parseDomainTopologyYaml(topologyYaml, this::reportValidationErrors);
    }

    private void reportValidationErrors(List<String> validationErrors) {
      packet.put(ProcessingConstants.DOMAIN_VALIDATION_ERRORS, validationErrors);
      if (!validationErrors.isEmpty()) {
        for (String err : validationErrors) {
          LOGGER.severe(err);
        }
      }
    }
  }

  public static Step reportTopologyErrorsAndStop() {
    return new TopologyErrorsReportStep();
  }

  private static class TopologyErrorsReportStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      List<String> errors = getErrors(packet);
      Step step = DomainStatusUpdater.createFailedStep(DomainStatusUpdater.BAD_TOPOLOGY, perLine(errors), null);
      return doNext(step, packet);
    }

    @SuppressWarnings("unchecked")
    private List<String> getErrors(Packet packet) {
      return (List<String>) packet.get(DOMAIN_VALIDATION_ERRORS);
    }

    @NotNull
    private String perLine(List<String> errors) {
      return String.join(lineSeparator(), errors);
    }
  }

  public static class IntrospectorConfigMapContext extends ConfigMapContext {
    final String domainUid;
    private boolean patchOnly;

    IntrospectorConfigMapContext(
          Step conflictStep,
          Domain domain,
          Map<String, String> data,
          DomainPresenceInfo info) {
      super(conflictStep, getIntrospectorConfigMapName(domain.getDomainUid()), domain.getNamespace(), data, info);

      this.domainUid = domain.getDomainUid();
      addLabel(LabelConstants.DOMAINUID_LABEL, domainUid);
    }

    IntrospectorConfigMapContext patchOnly() {
      patchOnly = true;
      return this;
    }

    @Override
    Step createConfigMap(Step next) {
      return patchOnly ? null : super.createConfigMap(next);
    }

    @Override
    boolean isIncompatibleMap(V1ConfigMap existingMap) {
      return !patchOnly && super.isIncompatibleMap(existingMap);
    }

    @Override
    boolean shouldRemove(Map.Entry<String, String> entry) {
      return !patchOnly && isRemovableKey(entry.getKey());
    }

    private boolean isRemovableKey(String key) {
      return key.startsWith(SIT_CONFIG_FILE_PREFIX);
    }

  }

  /**
   * Factory for a step that deletes the generated introspector config map.
   *
   * @param domainUid The unique identifier assigned to the WebLogic domain when it was registered
   * @param namespace the domain namespace
   * @param next the next step to run after the map is deleted
   * @return the created step
   */
  public static Step deleteIntrospectorConfigMapStep(String domainUid, String namespace, Step next) {
    return new DeleteIntrospectorConfigMapStep(domainUid, namespace, next);
  }

  private static class DeleteIntrospectorConfigMapStep extends Step {
    private final String domainUid;
    private final String namespace;

    DeleteIntrospectorConfigMapStep(String domainUid, String namespace, Step next) {
      super(next);
      this.domainUid = domainUid;
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(deleteIntrospectorConfigMap(getNext()), packet);
    }

    String getConfigMapDeletedMessageKey() {
      return String.format("Introspector config map %s deleted", getIntrospectorConfigMapName(this.domainUid));
    }

    protected void logConfigMapDeleted() {
      LOGGER.fine(getConfigMapDeletedMessageKey());
    }

    private Step deleteIntrospectorConfigMap(Step next) {
      logConfigMapDeleted();
      String configMapName = getIntrospectorConfigMapName(this.domainUid);
      return new CallBuilder()
          .deleteConfigMapAsync(configMapName, namespace, new V1DeleteOptions(), new DefaultResponseStep<>(next));
    }
  }

  /**
   * Reads the introspector config map for the specified domain, populating the following packet entries:
   *   DOMAIN_TOPOLOGY                    the parsed topology
   *   DOMAIN_HASH                        a hash of the topology
   *   SECRETS_HASH                       a hash of the override secrets
   *   DOMAIN_RESTART_VERSION             a field from the domain to force rolling when changed
   *   DOMAIN_INPUTS_HASH                 a hash of the image used in the domain.
   *
   * @param ns the namespace of the domain
   * @param domainUid the unique domain ID
   * @return a step to do the processing.
   */
  public static Step readExistingIntrospectorConfigMap(String ns, String domainUid) {
    String configMapName = getIntrospectorConfigMapName(domainUid);
    return new CallBuilder().readConfigMapAsync(configMapName, ns, new ReadIntrospectorConfigMapStep());
  }

  private static class ReadIntrospectorConfigMapStep extends DefaultResponseStep<V1ConfigMap> {

    ReadIntrospectorConfigMapStep() {
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      V1ConfigMap result = callResponse.getResult();
      copyMapEntryToPacket(result, packet, SECRETS_MD_5);
      copyMapEntryToPacket(result, packet, DOMAINZIP_HASH);
      copyMapEntryToPacket(result, packet, DOMAIN_RESTART_VERSION);
      copyMapEntryToPacket(result, packet, DOMAIN_INPUTS_HASH);

      DomainTopology domainTopology =
            Optional.ofNullable(result)
                  .map(V1ConfigMap::getData)
                  .map(this::getTopologyYaml)
                  .map(DomainTopology::parseDomainTopologyYaml)
                  .orElse(null);

      if (domainTopology != null) {
        recordTopology(packet, packet.getSpi(DomainPresenceInfo.class), domainTopology);
        return doNext(DomainValidationSteps.createValidateDomainTopologyStep(getNext()), packet);
      } else {
        return doNext(packet);
      }
    }

    private String getTopologyYaml(Map<String, String> data) {
      return data.get(IntrospectorConfigMapKeys.TOPOLOGY_YAML);
    }

    private void recordTopology(Packet packet, DomainPresenceInfo info, DomainTopology domainTopology) {
      ScanCache.INSTANCE.registerScan(
          info.getNamespace(),
          info.getDomainUid(),
          new Scan(domainTopology.getDomain(), new DateTime()));

      packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, domainTopology.getDomain());
    }

    private void copyMapEntryToPacket(V1ConfigMap result, Packet packet, String mapKey) {
      Optional.ofNullable(result)
            .map(V1ConfigMap::getData)
            .map(m -> m.get(mapKey))
            .ifPresent(v -> addToPacket(packet, mapKey, v));
    }

    private void addToPacket(Packet packet, String key, String value) {
      LOGGER.finest("Read " + key + " value " + value + " from domain config map");
      packet.put(key, value);
    }
  }

  /**
   * Reads the introspector config map for the specified domain, populating the following packet entries.
   *   INTROSPECTION_STATE_LABEL          the value of the domain's 'introspectVersion' when this map was created
   *
   * @param ns the namespace of the domain
   * @param domainUid the unique domain ID
   * @return a step to do the processing.
   */
  public static Step readIntrospectionVersionStep(String ns, String domainUid) {
    String configMapName = getIntrospectorConfigMapName(domainUid);
    return new CallBuilder().readConfigMapAsync(configMapName, ns, new ReadIntrospectionVersionStep());
  }

  private static class ReadIntrospectionVersionStep extends DefaultResponseStep<V1ConfigMap> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      Optional.ofNullable(callResponse.getResult())
            .map(V1ConfigMap::getMetadata)
            .map(V1ObjectMeta::getLabels)
            .map(l -> l.get(INTROSPECTION_STATE_LABEL))
            .ifPresentOrElse(
                version -> packet.put(INTROSPECTION_STATE_LABEL, version),
                () -> packet.remove(INTROSPECTION_STATE_LABEL));

      return doNext(packet);
    }
  }

}

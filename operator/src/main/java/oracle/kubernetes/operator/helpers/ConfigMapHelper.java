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

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
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

import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAINZIP_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAIN_INPUTS_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.DOMAIN_RESTART_VERSION;
import static oracle.kubernetes.operator.IntrospectorConfigMapKeys.SECRETS_MD_5;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.VersionConstants.DEFAULT_DOMAIN_VERSION;

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
  static int getModelInImageSpecHash(String imageName) {
    return new HashCodeBuilder(17, 37)
        .append(imageName)
        .toHashCode();
  }

  /**
   * Returns the standard name for the generated domain config map.
   * @param domainUid the unique ID of the domain
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
    private final String operatorNamespace;

    ScriptConfigMapContext(Step conflictStep, String operatorNamespace, String domainNamespace) {
      super(conflictStep, SCRIPT_CONFIG_MAP_NAME, domainNamespace, loadScriptsFromClasspath(domainNamespace));

      this.operatorNamespace = operatorNamespace;
    }

    @Override
    protected V1ObjectMeta customize(V1ObjectMeta metadata) {
      return metadata.putLabelsItem(LabelConstants.OPERATORNAME_LABEL, operatorNamespace);
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

  abstract static class ConfigMapContext {
    private final Map<String, String> contents;
    private final Step conflictStep;
    private final String name;
    private final String namespace;
    private V1ConfigMap model;

    ConfigMapContext(Step conflictStep, String name, String namespace, Map<String, String> contents) {
      this.conflictStep = conflictStep;
      this.name = name;
      this.namespace = namespace;
      this.contents = contents;
    }

    /**
     * Subclasses may override this to apply customizations to the model metadata. This is typically
     * done to add labels that are specific to a particular type of config map.
     * @param metadata the common metadata to customize
     * @return the updated metadata
     */
    protected V1ObjectMeta customize(V1ObjectMeta metadata) {
      return metadata;
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
      return new V1ConfigMap().kind("ConfigMap").apiVersion("v1")
            .metadata(customize(createMetadata())).data(data);
    }

    private V1ObjectMeta createMetadata() {
      return new V1ObjectMeta()
          .name(name)
          .namespace(namespace)
          .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, DEFAULT_DOMAIN_VERSION)
          .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
    }

    /**
     * Creates the step which begins verifying or updating the config map.
     * @param next the step to run after the config map processing is done
     * @return the new step to run
     */
    Step verifyConfigMap(Step next) {
      return new CallBuilder().readConfigMapAsync(getName(), namespace, new ReadResponseStep(next));
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
          recordCurrentMap(packet, existingMap);
          return doNext(packet);
        } else {
          return doNext(updateConfigMap(getNext(), existingMap), packet);
        }
      }

      private Step createConfigMap(Step next) {
        return new CallBuilder()
            .createConfigMapAsync(namespace, getModel(), createCreateResponseStep(next));
      }

      private boolean isCompatibleMap(V1ConfigMap existingMap) {
        return VersionHelper.matchesResourceVersion(existingMap.getMetadata(), DEFAULT_DOMAIN_VERSION)
            && COMPARATOR.containsAll(existingMap, getModel());
      }

      private void logConfigMapExists() {
        LOGGER.fine(MessageKeys.CM_EXISTS, getName(), namespace);
      }

      private Step updateConfigMap(Step next, V1ConfigMap existingConfigMap) {
        return new CallBuilder().replaceConfigMapAsync(name, namespace,
                                        createModel(getCombinedData(existingConfigMap)),
                                        createReplaceResponseStep(next));
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
        LOGGER.info(MessageKeys.CM_REPLACED, SCRIPT_CONFIG_MAP_NAME, namespace);
        recordCurrentMap(packet, callResponse.getResult());
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
   * The first in a chain of steps to create the situation config map from introspection results.
   */
  static class IntrospectionConfigMapStep extends Step {

    IntrospectionConfigMapStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      IntrospectionLoader loader = new IntrospectionLoader(packet, this);
      
      if (loader.getDomainConfig() == null) {
        return doNext(null, packet);
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
            .map(IntrospectionLoader::getDomainTopology)
            .map(DomainTopology::getDomain)
            .orElse(null);
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

    private Step createValidationStep() {
      return DomainValidationSteps.createValidateDomainTopologyStep(
            createIntrospectorConfigMapContext(conflictStep).verifyConfigMap(conflictStep.getNext()));
    }

    private IntrospectorConfigMapContext createIntrospectorConfigMapContext(Step conflictStep) {
      return new IntrospectorConfigMapContext(conflictStep, info.getDomain(), data);
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

    private static DomainTopology getDomainTopology(String topologyYaml) {
      LOGGER.fine("topology.yaml: " + topologyYaml);
      return DomainTopology.parseDomainTopologyYaml(topologyYaml,
            IntrospectionLoader::logValidationErrors);
    }

    private static void logValidationErrors(List<String> validationErrors) {
      if (!validationErrors.isEmpty()) {
        for (String err : validationErrors) {
          LOGGER.severe(err);
        }
      }
    }
  }

  /**
   * Creates a step to add entries to the introspector config map. Uses Packet.getSpi to retrieve
   * the current domain presence info.
   *
   *
   * @param domain the domain associated with the map
   * @param entries a map of entries to add
   * @param next the next step to process after the config map is updated
   * @return the created step
   */
  public static Step addIntrospectorConfigMapEntriesStep(Domain domain, Map<String, String> entries, Step next) {
    return new AddIntrospectorConfigEntriesStep(domain, entries, next);
  }

  /**
   * A step which starts a chain to add entries to the domain config map.
   */
  static class AddIntrospectorConfigEntriesStep extends Step {

    private final Domain domain;
    private final Map<String, String> additionalEntries;

    public AddIntrospectorConfigEntriesStep(Domain domain, Map<String, String> additionalEntries, Step next) {
      super(next);
      this.domain = domain;
      this.additionalEntries = new HashMap<>(additionalEntries);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(createContext().verifyConfigMap(getNext()), packet);
    }

    private IntrospectorConfigMapContext createContext() {
      return new IntrospectorConfigMapContext(this, domain, additionalEntries);
    }
  }

  public static class IntrospectorConfigMapContext extends ConfigMapContext {
    final String domainUid;

    IntrospectorConfigMapContext(
          Step conflictStep,
          Domain domain,
          Map<String, String> data) {
      super(conflictStep, getIntrospectorConfigMapName(domain.getDomainUid()), domain.getNamespace(), data);

      this.domainUid = domain.getDomainUid();
    }

    @Override
    protected V1ObjectMeta customize(V1ObjectMeta metadata) {
      return metadata.putLabelsItem(LabelConstants.DOMAINUID_LABEL, domainUid);
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
      copyMapEntryToPacket(result, packet, IntrospectorConfigMapKeys.CONFIGURATION_OVERRIDES);
      
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

}

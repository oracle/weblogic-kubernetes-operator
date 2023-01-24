// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

import com.google.gson.Gson;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import jakarta.json.JsonValue;
import jakarta.validation.constraints.NotNull;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.IntrospectorConfigMapConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.http.rest.Scan;
import oracle.kubernetes.operator.http.rest.ScanCache;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClock;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.yaml.snakeyaml.Yaml;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.DOMAINZIP_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.DOMAIN_INPUTS_HASH;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.DOMAIN_RESTART_VERSION;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.NUM_CONFIG_MAPS;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.SECRETS_MD_5;
import static oracle.kubernetes.operator.IntrospectorConfigMapConstants.SIT_CONFIG_FILE_PREFIX;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_DOMAIN_SPEC_GENERATION;
import static oracle.kubernetes.operator.LabelConstants.INTROSPECTION_STATE_LABEL;
import static oracle.kubernetes.operator.ProcessingConstants.DOMAIN_VALIDATION_ERRORS;
import static oracle.kubernetes.operator.helpers.KubernetesUtils.getDomainUidLabel;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONFIGMAP_NAME_SUFFIX;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONFIG_DATA_NAME;
import static oracle.kubernetes.operator.helpers.StepContextConstants.OLD_FLUENTD_CONFIGMAP_NAME;

public class ConfigMapHelper {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private static final String SCRIPT_LOCATION = "/scripts";
  private static final String UPDATEDOMAINRESULT = "UPDATEDOMAINRESULT";
  private static final ConfigMapComparator COMPARATOR = new ConfigMapComparator();

  private static final FileGroupReader scriptReader = new FileGroupReader(SCRIPT_LOCATION);

  private ConfigMapHelper() {
  }

  /**
   * Factory for {@link Step} that creates config map containing scripts.
   *
   * @param domainNamespace the domain's namespace
   * @return Step for creating config map containing scripts
   */
  public static Step createScriptConfigMapStep(String domainNamespace, SemanticVersion productVersion) {
    return new ScriptConfigMapStep(domainNamespace, productVersion);
  }

  static Map<String, String> parseIntrospectorResult(String text, String domainUid) {
    Map<String, String> map = new HashMap<>();
    String updateResultToken = ">>>  updatedomainResult=";

    try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
      String line = reader.readLine();
      while (line != null) {
        if (line.contains(updateResultToken)) {
          int index = line.indexOf(updateResultToken);
          int beg = index + 1 + updateResultToken.length();
          map.put(UPDATEDOMAINRESULT, line.substring(beg - 1));
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
   * Returns the standard name for the introspector config map.
   * @param domainUid the unique ID of the domain
   * @return map name
   */
  public static String getIntrospectorConfigMapName(String domainUid) {
    return IntrospectorConfigMapConstants.getIntrospectorConfigMapName(domainUid, 0);
  }

  static class ConfigMapComparator {
    boolean isOutdated(SemanticVersion productVersion, V1ConfigMap actual, V1ConfigMap expected) {
      // Check product version label
      if (productVersion != null) {
        SemanticVersion currentVersion = KubernetesUtils.getProductVersionFromMetadata(actual.getMetadata());
        if (currentVersion == null || productVersion.compareTo(currentVersion) > 0) {
          return true;
        }
      }

      return !AnnotationHelper.getHash(expected).equals(AnnotationHelper.getHash(actual));
    }
  }

  static class ScriptConfigMapStep extends Step {
    final ConfigMapContext context;

    ScriptConfigMapStep(String domainNamespace, SemanticVersion productVersion) {
      context = new ScriptConfigMapContext(this, domainNamespace, productVersion);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyConfigMap(getNext()), packet);
    }
  }

  static class ScriptConfigMapContext extends ConfigMapContext {
    ScriptConfigMapContext(Step conflictStep, String domainNamespace, SemanticVersion productVersion) {
      super(conflictStep, SCRIPT_CONFIG_MAP_NAME, domainNamespace,
          loadScriptsFromClasspath(domainNamespace), null, productVersion);

      addLabel(LabelConstants.OPERATORNAME_LABEL, getOperatorNamespace());
    }

    @Override
    void recordCurrentMap(Packet packet, V1ConfigMap configMap) {
      packet.put(ProcessingConstants.SCRIPT_CONFIG_MAP, configMap);
    }
  }

  static synchronized Map<String, String> loadScriptsFromClasspath(String domainNamespace) {
    Map<String, String> scripts = scriptReader.loadFilesFromClasspath();
    LOGGER.finer(MessageKeys.SCRIPT_LOADED, domainNamespace);
    return scripts;
  }

  abstract static class ConfigMapContext extends StepContextBase {
    private final Map<String, String> contents;
    private final Step conflictStep;
    private final String name;
    private final String namespace;
    private V1ConfigMap model;
    private final Map<String, String> labels = new HashMap<>();
    protected final SemanticVersion productVersion;

    ConfigMapContext(Step conflictStep, String name, String namespace, Map<String, String> contents,
                     DomainPresenceInfo info) {
      this(conflictStep, name, namespace, contents, info, null);
    }

    ConfigMapContext(Step conflictStep, String name, String namespace, Map<String, String> contents,
                     DomainPresenceInfo info, SemanticVersion productVersion) {
      super(info);
      this.conflictStep = conflictStep;
      this.name = name;
      this.namespace = namespace;
      this.contents = contents;
      this.productVersion = productVersion;

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

    @SuppressWarnings("SameParameterValue")
    void setContentValue(String key, String value) {
      contents.put(key, value);
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
      return AnnotationHelper.withSha256Hash(
          new V1ConfigMap().kind("ConfigMap").apiVersion("v1").metadata(createMetadata()).data(data), data);
    }

    private V1ObjectMeta createMetadata() {
      V1ObjectMeta metadata = updateForOwnerReference(
          new V1ObjectMeta()
          .name(name)
          .namespace(namespace)
          .labels(labels));

      if (productVersion != null) {
        metadata.putLabelsItem(LabelConstants.OPERATOR_VERSION, productVersion.toString());
      }

      return metadata;
    }

    @SuppressWarnings("SameParameterValue")
    void addLabel(String name, String value) {
      labels.put(name, value);
      model = null;
    }

    /**
     * Creates the step which begins verifying or updating the config map.
     * @param next the step to run after the config map processing is done
     * @return the new step to run
     */
    Step verifyConfigMap(Step next) {
      return new CallBuilder().readConfigMapAsync(getName(), namespace, null, new ReadResponseStep(next));
    }

    boolean isOutdated(V1ConfigMap existingMap) {
      return COMPARATOR.isOutdated(productVersion, existingMap, getModel());
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
        DomainResource domain = DomainPresenceInfo.fromPacket(packet).map(DomainPresenceInfo::getDomain).orElse(null);
        Optional.ofNullable(domain).map(DomainResource::getIntrospectVersion)
              .ifPresent(value -> addLabel(INTROSPECTION_STATE_LABEL, value));
        Optional.ofNullable(domain).map(DomainResource::getMetadata).map(V1ObjectMeta::getGeneration)
                .ifPresent(value -> addLabel(INTROSPECTION_DOMAIN_SPEC_GENERATION, value.toString()));
        V1ConfigMap existingMap = withoutTransientData(callResponse.getResult());
        if (existingMap == null) {
          return doNext(createConfigMap(getNext()), packet);
        } else if (isOutdated(existingMap)) {
          return doNext(replaceConfigMap(getNext()), packet);
        } else if (mustPatchCurrentMap(existingMap)) {
          return doNext(patchCurrentMap(existingMap, getNext()), packet);
        } else if (mustPatchImageHashInMap(existingMap, packet)) {
          return doNext(patchImageHashInCurrentMap(existingMap, packet, getNext()), packet);
        } else {
          logConfigMapExists();
          recordCurrentMap(packet, existingMap);
          return doNext(packet);
        }
      }

      private ResponseStep<V1ConfigMap> createCreateResponseStep(Step next) {
        return new CreateResponseStep(next);
      }

      private Step createConfigMap(Step next) {
        return new CallBuilder()
            .createConfigMapAsync(namespace, getModel(), createCreateResponseStep(next));
      }

      private void logConfigMapExists() {
        LOGGER.fine(MessageKeys.CM_EXISTS, getResourceName(), namespace);
      }

      private ResponseStep<V1ConfigMap> createReplaceResponseStep(Step next) {
        return new ReplaceResponseStep(next);
      }

      private Step replaceConfigMap(Step next) {
        return new CallBuilder().replaceConfigMapAsync(name, namespace,
                                        model,
                                        createReplaceResponseStep(next));
      }

      private Map<String,String> getLabels() {
        return Collections.unmodifiableMap(labels);
      }

      private boolean mustPatchCurrentMap(V1ConfigMap currentMap) {
        return KubernetesUtils.isMissingValues(getMapLabels(currentMap), getLabels());
      }

      private boolean mustPatchImageHashInMap(V1ConfigMap currentMap, Packet packet) {
        return (currentMap.getData() != null) && Optional.ofNullable((String)packet.get(DOMAIN_INPUTS_HASH))
                .map(hash -> !hash.equals(currentMap.getData().get(DOMAIN_INPUTS_HASH))).orElse(false);
      }

      private Map<String, String> getMapLabels(@NotNull V1ConfigMap map) {
        return Optional.ofNullable(map.getMetadata()).map(V1ObjectMeta::getLabels).orElseGet(Collections::emptyMap);
      }

      private ResponseStep<V1ConfigMap> createPatchResponseStep(Step next) {
        return new PatchResponseStep(next);
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
                getDomainUidLabel(Optional.of(currentMap).map(V1ConfigMap::getMetadata).orElse(null)),
                new V1Patch(patchBuilder.build().toString()), createPatchResponseStep(next));
      }

      private Step patchImageHashInCurrentMap(V1ConfigMap currentMap, Packet packet, Step next) {
        JsonPatchBuilder patchBuilder = Json.createPatchBuilder();

        patchBuilder.add("/data/" + DOMAIN_INPUTS_HASH, (String)packet.get(DOMAIN_INPUTS_HASH));

        return new CallBuilder()
                .patchConfigMapAsync(name, namespace,
                        getDomainUidLabel(Optional.of(currentMap).map(V1ConfigMap::getMetadata).orElse(null)),
                        new V1Patch(patchBuilder.build().toString()), createPatchResponseStep(next));
      }

      private boolean labelsNotDefined(V1ConfigMap currentMap) {
        return Objects.requireNonNull(currentMap.getMetadata()).getLabels() == null;
      }
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
        LOGGER.info(MessageKeys.CM_CREATED, getResourceName(), namespace);
        recordCurrentMap(packet, callResponse.getResult());
        return doNext(packet);
      }
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
        LOGGER.info(MessageKeys.CM_REPLACED, getResourceName(), namespace);
        recordCurrentMap(packet, callResponse.getResult());
        return doNext(packet);
      }
    }


    private class PatchResponseStep extends ResponseStep<V1ConfigMap> {

      PatchResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
        LOGGER.info(MessageKeys.CM_PATCHED, getResourceName(), namespace);
        return doNext(packet);
      }
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
        loader.updateImageHashInPacket();
        return doNext(loader.createIntrospectionVersionUpdateStep(), packet);
      } else {
        loader.updatePacket();
        return doNext(loader.createValidationStep(), packet);
      }
    }
  }

  static class IntrospectionLoader {
    private static final String NON_DYNAMIC_CHANGES_FILE = "non_dynamic_changes.file";

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
      Optional.ofNullable(data.get(IntrospectorConfigMapConstants.TOPOLOGY_YAML))
              .map(t -> data.put(IntrospectorConfigMapConstants.TOPOLOGY_JSON, convertToJson(t)));

      LOGGER.fine("================");
      LOGGER.fine(data.toString());
      LOGGER.fine("================");

      wlsDomainConfig = Optional.ofNullable(data.get(IntrospectorConfigMapConstants.TOPOLOGY_YAML))
            .map(this::getDomainTopology)
            .map(DomainTopology::getDomain)
            .orElse(null);

      String updateDomainResult = data.get(UPDATEDOMAINRESULT);
      if (updateDomainResult != null) {
        LOGGER.fine("ConfigMapHelper.apply: MII Dynamic update result " + updateDomainResult);
        packet.put(ProcessingConstants.MII_DYNAMIC_UPDATE, updateDomainResult);
        if (data.containsKey(NON_DYNAMIC_CHANGES_FILE)) {
          String rollbackFileContent = data.get(NON_DYNAMIC_CHANGES_FILE);
          packet.put(ProcessingConstants.MII_DYNAMIC_UPDATE_WDTROLLBACKFILE, rollbackFileContent);
          data.remove(NON_DYNAMIC_CHANGES_FILE);
        }
        // remove this, there is no need to store it in the configmap
        data.remove(UPDATEDOMAINRESULT);
      }
    }

    public static String convertToJson(String yaml) {
      return new Gson().toJson(new Yaml().load(yaml), LinkedHashMap.class);
    }

    boolean isTopologyNotValid() {
      return packet.containsKey(DOMAIN_VALIDATION_ERRORS);
    }

    private void updatePacket() {
      ScanCache.INSTANCE.registerScan(
            info.getNamespace(), info.getDomainUid(), new Scan(wlsDomainConfig, SystemClock.now()));
      packet.put(ProcessingConstants.DOMAIN_TOPOLOGY, wlsDomainConfig);

      copyFileToPacketIfPresent(DOMAINZIP_HASH, DOMAINZIP_HASH);
      copyFileToPacketIfPresent(SECRETS_MD_5, SECRETS_MD_5);
      copyToPacketAndFileIfPresent(DOMAIN_RESTART_VERSION, info.getDomain().getRestartVersion());
      copyToPacketAndFileIfPresent(DOMAIN_INPUTS_HASH, getModelInImageSpecHash());
    }

    private void updateImageHashInPacket() {
      copyToPacketAndFileIfPresent(DOMAIN_INPUTS_HASH, getModelInImageSpecHash());
    }

    private Step createIntrospectionVersionUpdateStep() {
      return createIntrospectorConfigMapContext().patchOnly().verifyConfigMap(conflictStep.getNext());
    }

    private Step createValidationStep() {
      return Step.chain(
            new IntrospectionConfigMapStep(data, conflictStep.getNext()),
            DomainValidationSteps.createValidateDomainTopologySteps(null)
            );
    }

    private class IntrospectionConfigMapStep extends Step {
      private final Map<String, String> data;
      private final ConfigMapSplitter<IntrospectorConfigMapContext> splitter;

      IntrospectionConfigMapStep(Map<String, String> data, Step next) {
        super(next);
        this.splitter = new ConfigMapSplitter<>(IntrospectionLoader.this::createIntrospectorConfigMapContext);
        this.data = data;
      }

      @Override
      public NextAction apply(Packet packet) {
        Collection<StepAndPacket> startDetails = splitter.split(data).stream()
              .map(c -> c.createStepAndPacket(packet))
              .collect(Collectors.toList());
        packet.put(NUM_CONFIG_MAPS, Integer.toString(startDetails.size()));
        return doForkJoin(getNext(), packet, startDetails);
      }

    }

    private IntrospectorConfigMapContext createIntrospectorConfigMapContext() {
      return createIntrospectorConfigMapContext(data, 0);
    }

    private IntrospectorConfigMapContext createIntrospectorConfigMapContext(
        Map<String, String> data, int index) {
      return new IntrospectorConfigMapContext(conflictStep, info, data, index);
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
      Step step = DomainStatusUpdater.createDomainInvalidFailureSteps(perLine(errors));
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

  public static class IntrospectorConfigMapContext extends ConfigMapContext implements SplitterTarget {

    private boolean patchOnly;

    IntrospectorConfigMapContext(Step conflictStep, DomainPresenceInfo info,
                                 Map<String, String> data, int index) {
      super(conflictStep, getConfigMapName(info, index), info.getNamespace(), data, info);

      addLabel(LabelConstants.DOMAINUID_LABEL, info.getDomainUid());
    }

    private static String getConfigMapName(DomainPresenceInfo info, int index) {
      return IntrospectorConfigMapConstants.getIntrospectorConfigMapName(info.getDomainUid(), index);
    }

    @Override
    public void recordNumTargets(int numTargets) {
      setContentValue(NUM_CONFIG_MAPS, Integer.toString(numTargets));
    }

    IntrospectorConfigMapContext patchOnly() {
      patchOnly = true;
      return this;
    }

    @Override
    boolean isOutdated(V1ConfigMap existingMap) {
      return !patchOnly && super.isOutdated(existingMap);
    }

    @Override
    boolean shouldRemove(Map.Entry<String, String> entry) {
      return !patchOnly && isRemovableKey(entry.getKey());
    }

    private boolean isRemovableKey(String key) {
      return key.startsWith(SIT_CONFIG_FILE_PREFIX);
    }

    public Step.StepAndPacket createStepAndPacket(Packet packet) {
      return new Step.StepAndPacket(verifyConfigMap(null), packet.copy());
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
    return new DeleteIntrospectorConfigMapsStep(domainUid, namespace, next);
  }

  private static class DeleteIntrospectorConfigMapsStep extends Step {
    private final String domainUid;
    private final String namespace;

    private DeleteIntrospectorConfigMapsStep(String domainUid, String namespace, Step next) {
      super(next);
      this.domainUid = domainUid;
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      Step step = new CallBuilder()
            .withLabelSelectors(LabelConstants.getCreatedByOperatorSelector())
            .listConfigMapsAsync(namespace, new SelectConfigMapsToDeleteStep(domainUid, namespace, getNext()));

      return doNext(step, packet);
    }
  }

  private static class SelectConfigMapsToDeleteStep extends DefaultResponseStep<V1ConfigMapList> {
    private final String domainUid;
    private final String namespace;

    public SelectConfigMapsToDeleteStep(String domainUid, String namespace, Step next) {
      super(next);
      this.domainUid = domainUid;
      this.namespace = namespace;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMapList> callResponse) {
      final List<String> configMapNames = getIntrospectorOrFluentdConfigMapNames(callResponse.getResult());
      if (configMapNames.isEmpty()) {
        return doNext(packet);
      } else {
        Collection<StepAndPacket> startDetails = new ArrayList<>();
        for (String configMapName : configMapNames) {
          startDetails.add(new StepAndPacket(
                new DeleteIntrospectorConfigMapStep(domainUid, namespace, configMapName), packet));
        }
        return doForkJoin(getNext(), packet, startDetails);
      }
    }

    @Nonnull
    protected List<String> getIntrospectorOrFluentdConfigMapNames(V1ConfigMapList list) {
      return list.getItems().stream()
            .map(this::getName)
            .filter(this::isIntrospectorOrFluentdConfigMapName)
            .collect(Collectors.toList());
    }

    private boolean isIntrospectorOrFluentdConfigMapName(String name) {
      return name.startsWith(IntrospectorConfigMapConstants.getIntrospectorConfigMapNamePrefix(domainUid))
          || (domainUid + FLUENTD_CONFIGMAP_NAME_SUFFIX).equals(name)
          // Match old, undecorated name of config map to clean-up
          || OLD_FLUENTD_CONFIGMAP_NAME.equals(name);
    }

    @Nonnull
    private String getName(V1ConfigMap configMap) {
      return Optional.ofNullable(configMap.getMetadata())
            .map(V1ObjectMeta::getName).orElse("");
    }
  }

  private static class DeleteIntrospectorConfigMapStep extends Step {
    private final String domainUid;
    private final String namespace;
    private final String configMapName;


    DeleteIntrospectorConfigMapStep(String domainUid, String namespace, String configMapName) {
      this.domainUid = domainUid;
      this.namespace = namespace;
      this.configMapName = configMapName;
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
      return new CallBuilder()
          .deleteConfigMapAsync(configMapName, namespace, domainUid,
              new V1DeleteOptions(), new DefaultResponseStep<>(next));
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
   * @return a step to do the processing.
   */
  public static Step readExistingIntrospectorConfigMap() {
    return new ReadIntrospectorConfigMapStep(ReadIntrospectorConfigMapResponseStep::new);
  }

  static class ReadIntrospectorConfigMapStep extends Step {

    private final Function<Step, ResponseStep<V1ConfigMap>> responseStepConstructor;

    private ReadIntrospectorConfigMapStep(Function<Step, ResponseStep<V1ConfigMap>> constructor) {
      this.responseStepConstructor = constructor;
    }

    @Override
    public NextAction apply(Packet packet) {
      final DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      return doNext(createReadStep(info), packet);
    }

    private Step createReadStep(DomainPresenceInfo info) {
      final String ns = info.getNamespace();
      final String domainUid = info.getDomainUid();
      final String configMapName = getIntrospectorConfigMapName(domainUid);

      return new CallBuilder()
          .readConfigMapAsync(configMapName, ns, domainUid, responseStepConstructor.apply(getNext()));
    }
  }

  private static class ReadIntrospectorConfigMapResponseStep extends DefaultResponseStep<V1ConfigMap> {

    ReadIntrospectorConfigMapResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      V1ConfigMap result = callResponse.getResult();
      copyMapEntryToPacket(result, packet, SECRETS_MD_5);
      copyMapEntryToPacket(result, packet, DOMAINZIP_HASH);
      copyMapEntryToPacket(result, packet, DOMAIN_RESTART_VERSION);
      copyMapEntryToPacket(result, packet, DOMAIN_INPUTS_HASH);
      copyMapEntryToPacket(result, packet, NUM_CONFIG_MAPS);

      DomainTopology domainTopology =
            Optional.ofNullable(result)
                  .map(V1ConfigMap::getData)
                  .map(this::getTopologyYaml)
                  .map(DomainTopology::parseDomainTopologyYaml)
                  .orElse(null);

      if (domainTopology != null) {
        recordTopology(packet, packet.getSpi(DomainPresenceInfo.class), domainTopology);
        recordIntrospectVersionAndGeneration(result, packet);
      }
      return doNext(packet);
    }

    private void recordIntrospectVersionAndGeneration(V1ConfigMap result, Packet packet) {
      Map<String, String> labels = Optional.ofNullable(result)
              .map(V1ConfigMap::getMetadata)
              .map(V1ObjectMeta::getLabels).orElse(null);

      Optional.ofNullable(labels).map(l -> l.get(INTROSPECTION_STATE_LABEL))
              .ifPresentOrElse(
                      version -> packet.put(INTROSPECTION_STATE_LABEL, version),
                      () -> packet.remove(INTROSPECTION_STATE_LABEL));
      Optional.ofNullable(labels).map(l -> l.get(INTROSPECTION_DOMAIN_SPEC_GENERATION))
              .ifPresent(generation -> packet.put(INTROSPECTION_DOMAIN_SPEC_GENERATION, generation));
    }

    private String getTopologyYaml(Map<String, String> data) {
      return data.get(IntrospectorConfigMapConstants.TOPOLOGY_YAML);
    }

    private void recordTopology(Packet packet, DomainPresenceInfo info, DomainTopology domainTopology) {
      ScanCache.INSTANCE.registerScan(
          info.getNamespace(),
          info.getDomainUid(),
          new Scan(domainTopology.getDomain(), SystemClock.now()));

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
   * @return a step to do the processing.
   */
  public static Step readIntrospectionVersionStep() {
    return new ReadIntrospectorConfigMapStep(ReadIntrospectionVersionResponseStep::new);
  }

  /**
   * Create or replace fluentd configuration map.
   * @return next step
   */
  public static Step createOrReplaceFluentdConfigMapStep() {
    return new CreateOrReplaceFluentdConfigMapStep();
  }

  private static class CreateOrReplaceFluentdConfigMapStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      if (hasNoFluentdSpecification(packet)) {
        return doNext(packet);
      } else {
        return doNext(createNextStep(DomainPresenceInfo.fromPacket(packet).orElseThrow()), packet);
      }
    }

    private boolean hasNoFluentdSpecification(Packet packet) {
      return DomainPresenceInfo.fromPacket(packet)
          .map(DomainPresenceInfo::getDomain)
          .map(DomainResource::getFluentdSpecification)
          .isEmpty();
    }

    private Step createNextStep(DomainPresenceInfo info) {
      return new CallBuilder().readConfigMapAsync(
          info.getDomainUid() + FLUENTD_CONFIGMAP_NAME_SUFFIX,
          info.getNamespace(),
          info.getDomainUid(),
          new ReadFluentdConfigMapResponseStep(getNext()));
    }
  }

  private static class ReadIntrospectionVersionResponseStep extends DefaultResponseStep<V1ConfigMap> {

    private ReadIntrospectionVersionResponseStep(Step nextStep) {
      super(nextStep);
    }

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

  private static class CreateFluentdConfigMapResponseStep extends DefaultResponseStep<V1ConfigMap> {

    CreateFluentdConfigMapResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      LOGGER.info(MessageKeys.FLUENTD_CONFIGMAP_CREATED);
      return doNext(packet);
    }

  }

  private static class ReplaceFluentdConfigMapResponseStep extends DefaultResponseStep<V1ConfigMap> {

    ReplaceFluentdConfigMapResponseStep(Step next) {
      super(next);
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      LOGGER.info(MessageKeys.FLUENTD_CONFIGMAP_REPLACED);
      return doNext(packet);
    }

  }

  private static class ReadFluentdConfigMapResponseStep extends DefaultResponseStep<V1ConfigMap> {
    ReadFluentdConfigMapResponseStep(Step next) {
      super(next);
    }

    private static Step createFluentdConfigMap(DomainPresenceInfo info, Step next) {
      return new CallBuilder()
          .createConfigMapAsync(info.getNamespace(),
              FluentdHelper.getFluentdConfigMap(info),
              new CreateFluentdConfigMapResponseStep(next));
    }

    private static Step replaceFluentdConfigMap(DomainPresenceInfo info, Step next) {
      return new CallBuilder()
          .replaceConfigMapAsync(
              info.getDomainUid() + FLUENTD_CONFIGMAP_NAME_SUFFIX,
              info.getNamespace(),
              FluentdHelper.getFluentdConfigMap(info),
              new ReplaceFluentdConfigMapResponseStep(next));
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMap> callResponse) {
      DomainPresenceInfo info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
      String existingConfigMapData = Optional.ofNullable(callResponse.getResult())
              .map(V1ConfigMap::getData)
              .map(c -> c.get(FLUENTD_CONFIG_DATA_NAME))
              .orElse(null);

      if (existingConfigMapData == null) {
        return doNext(createFluentdConfigMap(info, getNext()), packet);
      } else if (isOutdated(info, existingConfigMapData)) {
        return doNext(replaceFluentdConfigMap(info, getNext()), packet);
      }
      return doNext(packet);
    }

    private boolean isOutdated(DomainPresenceInfo info, String existingConfigData) {
      return !existingConfigData.equals(info.getDomain().getFluentdSpecification().getFluentdConfiguration());
    }
  }

}

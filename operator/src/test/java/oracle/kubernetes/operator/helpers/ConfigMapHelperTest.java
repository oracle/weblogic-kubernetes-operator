// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.KubernetesConstants.SCRIPT_CONFIG_MAP_NAME;
import static oracle.kubernetes.operator.ProcessingConstants.SCRIPT_CONFIG_MAP;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CONFIG_MAP;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.CM_REPLACED;
import static oracle.kubernetes.utils.LogMatcher.containsFine;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ConfigMapHelperTest {
  static final String[] SCRIPT_NAMES = {
    "livenessProbe.sh",
    "readState.sh",
    "start-server.py",
    "startServer.sh",
    "stop-server.py",
    "stopServer.sh",
    "introspectDomain.sh",
    "introspectDomain.py",
    "startNodeManager.sh",
    "utils.py",
    "utils.sh",
    "wlst.sh",
    "tailLog.sh",
    "monitorLog.sh",
    "model_diff.py",
    "modelInImage.sh",
    "wdt_create_filter.py",
    "model_filters.json",
    "encryption_util.py"
  };
  private static final String DOMAIN_NS = "namespace";

  private static final String ADDITIONAL_NAME = "additional.sh";
  private static final String[] PARTIAL_SCRIPT_NAMES = {"livenessProbe.sh", ADDITIONAL_NAME};
  private static final String[] COMBINED_SCRIPT_NAMES = combine(SCRIPT_NAMES, PARTIAL_SCRIPT_NAMES);
  private final V1ConfigMap defaultConfigMap = defineDefaultConfigMap();
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final InMemoryFileSystem fileSystem = InMemoryFileSystem.createInstance();
  private final Function<URI, Path> pathFunction = fileSystem::getPath;


  @SuppressWarnings("SameParameterValue")
  private static String[] combine(String[] first, String[] second) {
    return Stream.of(first, second).flatMap(Stream::of).distinct().toArray(String[]::new);
  }

  private static Map<String, String> nameOnlyScriptMap(String... scriptNames) {
    return Stream.of(scriptNames).collect(Collectors.toMap(s -> s, s -> ""));
  }

  private V1ConfigMap defineDefaultConfigMap() {
    return defineConfigMap(SCRIPT_NAMES);
  }

  private V1ConfigMap defineConfigMap(String... scriptNames) {
    return new V1ConfigMap()
        .apiVersion("v1")
        .kind("ConfigMap")
        .metadata(createMetadata())
        .data(nameOnlyScriptMap(scriptNames));
  }

  private V1ObjectMeta createMetadata() {
    return new V1ObjectMeta()
        .name(SCRIPT_CONFIG_MAP_NAME)
        .namespace(DOMAIN_NS)
        .putLabelsItem(LabelConstants.OPERATORNAME_LABEL, getOperatorNamespace())
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true");
  }

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CM_CREATED, CM_EXISTS, CM_REPLACED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(TestComparator.install());
    mementos.add(StaticStubSupport.install(FileGroupReader.class, "uriToPath", pathFunction));

    defineInMemoryFiles(SCRIPT_NAMES);
  }

  private void defineInMemoryFiles(String... scriptNames) {
    final String scriptRoot = getClass().getResource("/scripts/").getPath();
    for (String scriptName : scriptNames) {
      fileSystem.defineFile(scriptRoot + scriptName, "");
    }
  }

  @After
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenUnableToReadConfigMap_reportFailure() {
    testSupport.failOnResource(CONFIG_MAP, SCRIPT_CONFIG_MAP_NAME, DOMAIN_NS, 401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void whenNoConfigMap_createIt() {
    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS));

    assertThat(testSupport.getResources(CONFIG_MAP), notNullValue());
    assertThat(logRecords, containsInfo(CM_CREATED));
  }

  @Test
  public void whenNoConfigMap_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CONFIG_MAP, SCRIPT_CONFIG_MAP_NAME, DOMAIN_NS, 401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptConfigMapStep));
  }

  @Test
  public void whenMatchingConfigMapExists_addToPacket() {
    testSupport.defineResources(defaultConfigMap);

    Packet packet = testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS));

    assertThat(logRecords, containsFine(CM_EXISTS));
    assertThat(packet, hasEntry(SCRIPT_CONFIG_MAP, defaultConfigMap));
  }

  @Test
  public void whenExistingConfigMapIsMissingData_replaceIt() {
    testSupport.defineResources(defineConfigMap(PARTIAL_SCRIPT_NAMES));

    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS));

    assertThat(logRecords, containsInfo(CM_REPLACED));
    assertThat(getScriptConfigKeys(), containsInAnyOrder(COMBINED_SCRIPT_NAMES));
  }

  private Collection<String> getScriptConfigKeys() {
    return Optional.ofNullable(getScriptConfigMap())
          .map(V1ConfigMap::getData)
          .map(Map::keySet)
          .orElseGet(Collections::emptySet);
  }

  private V1ConfigMap getScriptConfigMap() {
    final List<V1ConfigMap> configMaps = testSupport.getResources(CONFIG_MAP);
    return configMaps.stream().filter(this::isScriptConfigMap).findFirst().orElse(null);
  }

  private boolean isScriptConfigMap(@Nonnull V1ConfigMap map) {
    return Optional.ofNullable(map.getMetadata()).map(this::isScriptConfigMapMetaData).orElse(false);
  }

  private boolean isScriptConfigMapMetaData(V1ObjectMeta meta) {
    return SCRIPT_CONFIG_MAP_NAME.equals(meta.getName()) && DOMAIN_NS.equals(meta.getNamespace());
  }

  @Test
  public void whenExistingConfigMapHasExtraData_dontRemoveIt() {
    testSupport.defineResources(defineConfigMap(PARTIAL_SCRIPT_NAMES));

    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS));

    assertThat(logRecords, containsInfo(CM_REPLACED));
    assertThat(getScriptConfigKeys(), hasItem(ADDITIONAL_NAME));
  }

  // An implementation of the comparator that tests only the keys in the maps
  static class TestComparator extends ConfigMapHelper.ConfigMapComparator {
    static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(ConfigMapHelper.class, "COMPARATOR", new TestComparator());
    }

    @Override
    boolean containsAllData(Map<String, String> actual, Map<String, String> expected) {
      return actual.keySet().containsAll(expected.keySet());
    }
  }

}

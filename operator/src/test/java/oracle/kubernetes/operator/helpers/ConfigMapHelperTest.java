// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ConfigMapHelperTest {
  private static final String DOMAIN_NS = "namespace";

  private static final SemanticVersion PRODUCT_VERSION = new SemanticVersion(3, 0, 0);
  private static final SemanticVersion PRODUCT_VERSION_OLD = new SemanticVersion(2, 4, 0);
  private static final SemanticVersion PRODUCT_VERSION_FUTURE = new SemanticVersion(3, 1, 0);

  private final V1ConfigMap defaultConfigMap = defineConfigMap(PRODUCT_VERSION);
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();


  private V1ConfigMap defineConfigMap(SemanticVersion productVersion) {
    Map<String, String> data = ConfigMapHelper.loadScriptsFromClasspath(DOMAIN_NS);
    return AnnotationHelper.withSha256Hash(new V1ConfigMap()
        .apiVersion("v1")
        .kind("ConfigMap")
        .metadata(createMetadata(productVersion))
        .data(data), data);
  }

  private V1ObjectMeta createMetadata(SemanticVersion productVersion) {
    return new V1ObjectMeta()
        .name(SCRIPT_CONFIG_MAP_NAME)
        .namespace(DOMAIN_NS)
        .putLabelsItem(LabelConstants.OPERATORNAME_LABEL, getOperatorNamespace())
        .putLabelsItem(LabelConstants.CREATEDBYOPERATOR_LABEL, "true")
        .putLabelsItem(LabelConstants.OPERATOR_VERSION, productVersion.toString());
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CM_CREATED, CM_EXISTS, CM_REPLACED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenUnableToReadConfigMap_reportFailure() {
    testSupport.failOnResource(CONFIG_MAP, SCRIPT_CONFIG_MAP_NAME, DOMAIN_NS, 401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS, null);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void whenNoConfigMap_createIt() {
    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS, null));

    assertThat(testSupport.getResources(CONFIG_MAP), notNullValue());
    assertThat(logRecords, containsInfo(CM_CREATED));
  }

  @Test
  public void whenNoConfigMap_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CONFIG_MAP, SCRIPT_CONFIG_MAP_NAME, DOMAIN_NS, 401);

    Step scriptConfigMapStep = ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS, null);
    testSupport.runSteps(scriptConfigMapStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptConfigMapStep));
  }

  @Test
  public void whenMatchingConfigMapExists_addToPacket() {
    testSupport.defineResources(defaultConfigMap);

    Packet packet = testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS, PRODUCT_VERSION));

    assertThat(logRecords, containsFine(CM_EXISTS));
    assertThat(packet, hasEntry(SCRIPT_CONFIG_MAP, defaultConfigMap));
  }

  @Test
  public void whenExistingConfigMapHasOldVersion_replaceIt() {
    testSupport.defineResources(defineConfigMap(PRODUCT_VERSION_OLD));

    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS, PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CM_REPLACED));
  }

  @Test
  public void whenExistingConfigMapHasFutureVersion_dontReplaceIt() {
    testSupport.defineResources(defineConfigMap(PRODUCT_VERSION_FUTURE));

    testSupport.runSteps(ConfigMapHelper.createScriptConfigMapStep(DOMAIN_NS, PRODUCT_VERSION));
  }

}

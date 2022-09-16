// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.CREATE_CRD_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.CREATING_CRD;
import static oracle.kubernetes.common.logging.MessageKeys.REPLACE_CRD_FAILED;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.WebhookMainTest.getCertificates;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterCrdTest {

  private static final SemanticVersion PRODUCT_VERSION = new SemanticVersion(3, 0, 0);
  public static final String EMPTY_YAML = "{}";

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final InMemoryFileSystem fileSystem = InMemoryFileSystem.createInstance();

  private static V1CustomResourceDefinition createCrd() {
    return new CrdHelper.ClusterCrdContext().createModel(PRODUCT_VERSION, getCertificates());
  }

  @BeforeEach
  void setUp() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CREATING_CRD, REPLACE_CRD_FAILED, CREATE_CRD_FAILED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(fileSystem.install());
    mementos.add(TuningParametersStub.install());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void defaultCrd_hasExpectedNames() {
    final V1CustomResourceDefinition defaultCrd = createCrd();
    assertThat(defaultCrd.getSpec().getNames().getKind(), equalTo(KubernetesConstants.CLUSTER));
    assertThat(defaultCrd.getSpec().getNames().getSingular(), equalTo(KubernetesConstants.CLUSTER_SINGULAR));
    assertThat(defaultCrd.getSpec().getNames().getPlural(), equalTo(KubernetesConstants.CLUSTER_PLURAL));
    assertThat(defaultCrd.getSpec().getNames().getShortNames(), contains(KubernetesConstants.CLUSTER_SHORT));
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  void defaultCrd_hasExpectedMetaData() {
    assertThat(createCrd().getMetadata().getName(), equalTo(KubernetesConstants.CLUSTER_CRD_NAME));
  }

  @Test
  void whenMultipleSchemaFilesDefined_ignoreNonClusterCrds() {
    createCrdFile("/domain-crd-schemav7-1.yaml");
    createCrdFile("/cluster-crd-schemav0-1");
    createCrdFile("/cluster-crdjunk-1");
    final V1CustomResourceDefinition crd = createCrd();

    final List<String> versions = crd.getSpec().getVersions().stream()
        .map(V1CustomResourceDefinitionVersion::getName)
        .collect(Collectors.toList());
    assertThat(versions, contains("v1", "v0"));
  }

  @SuppressWarnings("ConstantConditions")
  private void createCrdFile(String fileName) {
    final URL SCHEMA_ROOT = ClusterCrdTest.class.getResource("/schema");
    fileSystem.defineFile(SCHEMA_ROOT.getPath() + fileName, EMPTY_YAML);
  }

  // todo Ryan, what if we have already generated a file for current version? Current code generates a duplicate...

  @Test
  void whenNoCurrentCrd_createIt() {
    testSupport.runSteps(CrdHelper.createClusterCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void crdDoesNotHaveConversionWebhook() {
    assertThat(createCrd().getSpec().getConversion(), nullValue());
  }
}

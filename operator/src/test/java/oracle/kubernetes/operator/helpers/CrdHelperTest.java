// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1CustomResourceConversion;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1JSONSchemaProps;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.Namespaces;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.common.logging.MessageKeys.ASYNC_NO_RETRY;
import static oracle.kubernetes.common.logging.MessageKeys.CREATE_CRD_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.CREATING_CRD;
import static oracle.kubernetes.common.logging.MessageKeys.REPLACE_CRD_FAILED;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.ProcessingConstants.WEBHOOK;
import static oracle.kubernetes.operator.WebhookMainTest.getCertificates;
import static oracle.kubernetes.operator.helpers.CrdHelperTest.TestSubject.CLUSTER;
import static oracle.kubernetes.operator.helpers.CrdHelperTest.TestSubject.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CUSTOM_RESOURCE_DEFINITION;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class CrdHelperTest extends CrdHelperTestBase {
  private static final SemanticVersion PRODUCT_VERSION_OLD = new SemanticVersion(2, 4, 0);
  private static final SemanticVersion PRODUCT_VERSION_FUTURE = new SemanticVersion(3, 1, 0);
  public static final String WEBHOOK_CERTIFICATE = "/deployment/webhook-identity/webhookCert";

  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final InMemoryFileSystem fileSystem = InMemoryFileSystem.createInstance();
  private final TerminalStep terminalStep = new TerminalStep();
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        consoleHandlerMemento = TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CREATING_CRD, REPLACE_CRD_FAILED, CREATE_CRD_FAILED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(fileSystem.install());
    mementos.add(InMemoryCertificates.install(fileSystem));
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());
  }

  @AfterEach
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  enum TestSubject {
    DOMAIN {
      @Override
      @Nonnull
      protected String getExpectedCrdName() {
        return KubernetesConstants.DOMAIN_CRD_NAME;
      }

      @Nonnull
      @Override
      String getLatestCrdVersion() {
        return KubernetesConstants.DOMAIN_VERSION;
      }

      @Override
      @Nonnull
      Step createCrdStep(SemanticVersion semanticVersion, Certificates certificates) {
        return CrdHelper.createDomainCrdStep(semanticVersion, certificates);
      }

      @Nonnull
      @Override
      CrdHelper.CrdContext createCrdContext() {
        return new CrdHelper.DomainCrdContext();
      }
    }, CLUSTER {
      @Override
      @Nonnull
      protected String getExpectedCrdName() {
        return KubernetesConstants.CLUSTER_CRD_NAME;
      }

      @Nonnull
      @Override
      String getLatestCrdVersion() {
        return KubernetesConstants.CLUSTER_VERSION;
      }

      @Override
      @Nonnull
      Step createCrdStep(SemanticVersion semanticVersion, Certificates certificates) {
        return CrdHelper.createClusterCrdStep(semanticVersion);
      }

      @Nonnull
      @Override
      CrdHelper.CrdContext createCrdContext() {
        return new CrdHelper.ClusterCrdContext();
      }
    };

    Step createCrdStep(SemanticVersion semanticVersion) {
      return createCrdStep(semanticVersion, null);
    }

    @Nonnull
    abstract Step createCrdStep(SemanticVersion semanticVersion, Certificates certificates);

    @Nonnull
    abstract CrdHelper.CrdContext createCrdContext();

    @Nonnull
    abstract String getExpectedCrdName();

    @Nonnull
    abstract String getLatestCrdVersion();

    boolean isCurrentCrd(V1CustomResourceDefinition crd) {
      return getExpectedCrdName().equals(getName(crd));
    }

    @Nonnull
    private String getName(V1CustomResourceDefinition crd) {
      return Optional.of(crd).map(V1CustomResourceDefinition::getMetadata).map(V1ObjectMeta::getName).orElse("");
    }

    V1CustomResourceDefinition createCrd(Certificates certificates) {
      return createCrdContext().createModel(CrdHelperTestBase.PRODUCT_VERSION, certificates);
    }
  }

  @Test
  void verifyDomainCrdMapPropertiesGenerated() {
    final V1CustomResourceDefinition crd = defineDomainCrd();
    assertThat(getAdditionalPropertiesMap(crd, "spec", "serverService", "labels"), hasEntry("type", "string"));
    assertThat(getAdditionalPropertiesMap(crd, "spec", "serverService", "annotations"), hasEntry("type", "string"));
    assertThat(
        getAdditionalPropertiesMap(crd, "spec", "adminServer", "adminService", "labels"),
        hasEntry("type", "string"));
    assertThat(
        getAdditionalPropertiesMap(crd, "spec", "adminServer", "adminService", "annotations"),
        hasEntry("type", "string"));
    assertThat(getAdditionalPropertiesMap(crd, "spec", "serverPod", "resources", "limits"), hasEntry("type", "string"));
  }

  @Nullable
  @SuppressWarnings({"ConstantConditions", "unchecked"})
  private <T> Map<String, T> getAdditionalPropertiesMap(V1CustomResourceDefinition crd, String... pathElements) {
    V1JSONSchemaProps schemaProps = getProperties(crd, pathElements);

    assertThat(schemaProps.getAdditionalProperties(), instanceOf(Map.class));
    return (Map<String, T>) schemaProps.getAdditionalProperties();
  }

  @SuppressWarnings("ConstantConditions")
  @Nullable
  private String getPropertiesType(V1CustomResourceDefinition crd, String... pathElements) {
    return getProperties(crd, pathElements).getType();
  }

  @SuppressWarnings("ConstantConditions")
  @Nullable
  private V1JSONSchemaProps getProperties(V1CustomResourceDefinition crd, String[] pathElements) {
    V1JSONSchemaProps schemaProps = crd.getSpec().getVersions().get(0).getSchema().getOpenAPIV3Schema();
    for (String pathElement : pathElements) {
      schemaProps = schemaProps.getProperties().get(pathElement);
    }
    return schemaProps;
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenCrdV1SupportedAndNoCrd_createIt(TestSubject testSubject) {
    testSupport.runSteps(testSubject.createCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenNotAuthorizedToReadCrd_retryOnFailureAndLogWarningMessageInOnFailureNoRetry(TestSubject testSubject) {
    consoleHandlerMemento.collectLogMessages(logRecords, ASYNC_NO_RETRY);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(CUSTOM_RESOURCE_DEFINITION, null, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = testSubject.createCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, containsWarning(ASYNC_NO_RETRY));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenNotAuthorizedToReadCrdDedicatedMode_dontLogWarningMessageInOnFailureNoRetry(TestSubject testSubject) {
    consoleHandlerMemento.collectLogMessages(logRecords, ASYNC_NO_RETRY);
    defineSelectionStrategy(Namespaces.SelectionStrategy.DEDICATED);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(CUSTOM_RESOURCE_DEFINITION, null, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = testSubject.createCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, not(containsWarning(ASYNC_NO_RETRY)));
  }

  private void defineSelectionStrategy(Namespaces.SelectionStrategy selectionStrategy) {
    TuningParametersStub.setParameter(Namespaces.SELECTION_STRATEGY_KEY, selectionStrategy.toString());
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenForbiddenToReadCrdDedicatedMode_dontLogWarningMessageInOnFailureNoRetry(TestSubject testSubject) {
    consoleHandlerMemento.collectLogMessages(logRecords, ASYNC_NO_RETRY);
    defineSelectionStrategy(Namespaces.SelectionStrategy.DEDICATED);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(CUSTOM_RESOURCE_DEFINITION, null, null, HTTP_FORBIDDEN);

    Step scriptCrdStep = testSubject.createCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, not(containsWarning(ASYNC_NO_RETRY)));
  }


  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenNotAuthorizedToReadCrdAndWarningNotEnabled_DontLogWarningInOnFailureNoRetry(TestSubject testSubject) {
    LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger().setLevel(Level.SEVERE);
    consoleHandlerMemento.collectLogMessages(logRecords, ASYNC_NO_RETRY);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(CUSTOM_RESOURCE_DEFINITION, null, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = testSubject.createCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, not(containsWarning(ASYNC_NO_RETRY)));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenNoCrd_retryOnFailureAndLogFailedMessageInOnFailureNoRetry(TestSubject testSubject) {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CUSTOM_RESOURCE_DEFINITION, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = testSubject.createCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, containsInfo(CREATE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenNoCrd_proceedToNextStep(TestSubject testSubject) {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CUSTOM_RESOURCE_DEFINITION, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = testSubject.createCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(Step.chain(scriptCrdStep, terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
    logRecords.clear();
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class, names = {"DOMAIN"})
  void whenExistingCrdHasCurrentApiVersionButOldProductVersion_replaceIt(TestSubject testSubject) {
    testSupport.defineResources(defineCrd(PRODUCT_VERSION, testSubject.getExpectedCrdName()));

    testSupport.runSteps(testSubject.createCrdStep(PRODUCT_VERSION_FUTURE));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    List<V1CustomResourceDefinition> crds = testSupport.getResources(CUSTOM_RESOURCE_DEFINITION);
    V1CustomResourceDefinition crd = crds.stream().filter(testSubject::isCurrentCrd).findFirst().orElse(null);
    assertThat(crd, notNullValue());
    assertThat(getProductVersionFromMetadata(crd.getMetadata()), equalTo(PRODUCT_VERSION_FUTURE));
  }

  private SemanticVersion getProductVersionFromMetadata(V1ObjectMeta metadata) {
    return Optional.ofNullable(metadata)
            .map(V1ObjectMeta::getLabels)
            .map(labels -> labels.get(LabelConstants.OPERATOR_VERSION))
            .map(SemanticVersion::new)
            .orElse(null);
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class, names = {"DOMAIN"})
  void whenExistingCrdHasFutureVersionWithConversionWebhook_dontReplaceIt(TestSubject testSubject) {
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_FUTURE, testSubject.getExpectedCrdName());
    existing.getSpec().addVersionsItem(
            new V1CustomResourceDefinitionVersion().served(true).name(testSubject.getLatestCrdVersion()))
            .conversion(new V1CustomResourceConversion().strategy("Webhook"));
    testSupport.defineResources(existing);

    testSupport.runSteps(testSubject.createCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, not(containsInfo(CREATING_CRD)));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class, names = {"DOMAIN"})
  void whenExistingDomainCrdHasNoneConversionStrategy_replaceIt(TestSubject testSubject) {
    final V1CustomResourceDefinition crd = testSubject.createCrd(getCertificates());
    crd
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(testSubject.getLatestCrdVersion()))
        .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(crd);

    testSupport.runSteps(testSubject.createCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenExistingClusterCrdHasNoneConversionStrategy_dontReplaceIt() {
    final V1CustomResourceDefinition crd = CLUSTER.createCrd(getCertificates());
    crd
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(CLUSTER.getLatestCrdVersion()))
        .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(crd);

    testSupport.runSteps(CLUSTER.createCrdStep(PRODUCT_VERSION));

    assertThat(crd.getSpec().getConversion().getStrategy(), is("None"));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class, names = {"DOMAIN"})
  void whenExistingCrdHasCompatibleConversionWebhook_dontReplaceIt(TestSubject testSubject) {
    final V1CustomResourceDefinition crd = testSubject.createCrd(getCertificates());
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    crd.getSpec().addVersionsItem(
        new V1CustomResourceDefinitionVersion().served(true).name(testSubject.getLatestCrdVersion()))
        .conversion(CrdHelper.CrdContext.createConversionWebhook("asdf"));
    testSupport.defineResources(crd);

    testSupport.runSteps(testSubject.createCrdStep(PRODUCT_VERSION, getCertificates()));
    assertThat(logRecords, not(containsInfo(CREATING_CRD)));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class, names = {"DOMAIN"})
  void whenExistingCrdHasIncompatibleConversionWebhook_replaceIt(TestSubject testSubject) {
    final V1CustomResourceDefinition crd = testSubject.createCrd(getCertificates());
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    crd.getSpec().addVersionsItem(
        new V1CustomResourceDefinitionVersion().served(true).name(testSubject.getLatestCrdVersion()))
        .conversion(CrdHelper.CrdContext.createConversionWebhook("xyz"));
    testSupport.defineResources(crd);

    testSupport.runSteps(testSubject.createCrdStep(PRODUCT_VERSION, getCertificates()));
    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class, names = {"DOMAIN"})
  void whenCrdStepCalledWithNullProductVersionAndIncompatibleConversionWebhook_replaceIt(TestSubject testSubject) {
    final V1CustomResourceDefinition crd = testSubject.createCrd(getCertificates());
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    crd.getSpec().addVersionsItem(
        new V1CustomResourceDefinitionVersion().served(true).name(DOMAIN.getLatestCrdVersion()))
        .conversion(CrdHelper.CrdContext.createConversionWebhook("xyz"));
    testSupport.defineResources(crd);

    testSupport.runSteps(DOMAIN.createCrdStep(null, getCertificates()));
    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenExistingDomainCrdHasFutureVersionAndIncompatibleConversionWebhook_dontReplaceIt() {
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_FUTURE, DOMAIN.getExpectedCrdName());
    existing.getSpec().addVersionsItem(
        new V1CustomResourceDefinitionVersion().served(true).name(DOMAIN.getLatestCrdVersion()))
        .conversion(CrdHelper.CrdContext.createConversionWebhook("xyz"));
    testSupport.defineResources(existing);

    testSupport.runSteps(DOMAIN.createCrdStep(PRODUCT_VERSION, getCertificates()));

    assertThat(logRecords, not(containsInfo(CREATING_CRD)));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class, names = {"DOMAIN"})
  void whenExistingDomainCrdHasOldVersionAndNoneConversionStrategy_replaceIt(TestSubject testSubject) {
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_OLD, testSubject.getExpectedCrdName());
    existing
            .getSpec()
            .addVersionsItem(
                    new V1CustomResourceDefinitionVersion()
                            .served(true)
                            .name(testSubject.getLatestCrdVersion()))
            .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(existing);

    testSupport.runSteps(testSubject.createCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenExistingClusterCrdHasOldVersionAndNoneConversionStrategy_dontReplaceIt() {
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_FUTURE, CLUSTER.getExpectedCrdName());
    existing
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(CLUSTER.getLatestCrdVersion()))
        .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(existing);

    testSupport.runSteps(CLUSTER.createCrdStep(PRODUCT_VERSION));

    assertThat(existing.getSpec().getConversion().getStrategy(), is("None"));
  }

  @SuppressWarnings("ConstantConditions")
  @Test
  void whenExistingDomainCrdHasFutureVersionButNoneConversionStrategy_updateCrdWithWebhook() {
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_FUTURE, DOMAIN.getExpectedCrdName());
    existing
            .getSpec()
            .addVersionsItem(
                    new V1CustomResourceDefinitionVersion()
                            .served(true)
                            .name(DOMAIN.getLatestCrdVersion()))
            .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(existing);

    testSupport.runSteps(DOMAIN.createCrdStep(PRODUCT_VERSION, getCertificates()));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    assertThat(existing.getSpec().getConversion().getStrategy(), is(WEBHOOK));
  }

  @Test
  void whenExistingClusterCrdHasHasFutureVersionButNoneConversionStrategy_dontReplaceIt() {
    final V1CustomResourceDefinition existing = CLUSTER.createCrd(getCertificates());
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    existing
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(DOMAIN.getLatestCrdVersion()))
        .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(existing);

    testSupport.runSteps(CLUSTER.createCrdStep(PRODUCT_VERSION, getCertificates()));

    assertThat(existing.getSpec().getConversion().getStrategy(), is("None"));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenExistingCrdHasFutureVersionButNotCurrentStorage_updateIt(TestSubject testSubject) {
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_FUTURE, testSubject.getExpectedCrdName()));

    V1CustomResourceDefinition replacement = defineCrd(PRODUCT_VERSION_FUTURE, testSubject.getExpectedCrdName());
    replacement
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(testSubject.getLatestCrdVersion()));

    testSupport.runSteps(DOMAIN.createCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @ParameterizedTest
  @EnumSource(value = TestSubject.class)
  void whenReplaceFails_scheduleRetryAndLogFailedMessageInOnFailureNoRetry(TestSubject testSubject) {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_OLD, testSubject.getExpectedCrdName()));
    testSupport.failOnReplace(CUSTOM_RESOURCE_DEFINITION, testSubject.getExpectedCrdName(), null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = testSubject.createCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  void whenReplaceFailsThrowsStreamException_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_OLD, DOMAIN.getExpectedCrdName()));
    testSupport.failOnReplaceWithStreamResetException(CUSTOM_RESOURCE_DEFINITION, DOMAIN.getExpectedCrdName(), null);

    Step scriptCrdStep = DOMAIN.createCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  void whenCrdMainCalledWithNoArguments_illegalArgumentExceptionThrown() {
    Assertions.assertThrows(IllegalArgumentException.class, CrdHelper::main);
  }

  @Test
  void whenCrdMainCalledWithJustOneArgument_illegalArgumentExceptionThrown() {
    Assertions.assertThrows(IllegalArgumentException.class, () -> CrdHelper.main("/crd.yaml"));
  }

  @Test
  void whenDomainCrdCreatedWithMainMethod_containsPreserveFieldsAnnotation() throws URISyntaxException {
    CrdHelper.main("/crd.yaml", "/cluster-crd.yaml");

    assertThat(fileSystem.getContents("/crd.yaml"), containsString("x-kubernetes-preserve-unknown-fields"));
  }

  @Test
  void whenDomainCrdCreatedWithRelativeFileName_containsPreserveFieldsAnnotation() throws URISyntaxException {
    CrdHelper.main("crd.yaml", "cluster-crd.yaml");

    assertThat(fileSystem.getContents("/crd.yaml"), containsString("x-kubernetes-preserve-unknown-fields"));
  }

  @Test
  void whenClusterCrdCreatedWithMainMethod_containsPreserveFieldsAnnotation() throws URISyntaxException {
    CrdHelper.main("/crd.yaml", "/cluster-crd.yaml");

    assertThat(fileSystem.getContents("/cluster-crd.yaml"), notNullValue());
  }

  @Test
  void whenClusterCrdCreatedWithRelativeFileName_containsPreserveFieldsAnnotation() throws URISyntaxException {
    CrdHelper.main("crd.yaml", "cluster-crd.yaml");

    assertThat(fileSystem.getContents("/cluster-crd.yaml"), notNullValue());
  }

  @Test
  void testCrdCreationExceptionWhenWritingCrd() throws NoSuchFieldException {
    fileSystem.throwExceptionOnGetPath("/crd.yaml");

    Assertions.assertThrows(CrdHelper.CrdCreationException.class, () -> CrdHelper.main("crd.yaml", "cluster.yaml"));
  }

  @Test
  void whenClusterCrdCreated_specContainsFields() {
    V1CustomResourceDefinition crd = defineClusterCrd();
    assertThat(getPropertiesType(crd, "spec", "clusterName"), equalTo("string"));
    assertThat(getPropertiesType(crd, "spec", "replicas"), equalTo("integer"));
  }

  private V1CustomResourceDefinition defineClusterCrd() {
    return new CrdHelper.ClusterCrdContext().createModel(PRODUCT_VERSION, null);
  }


  @Test
  void whenClusterCrdCreated_statusContainsExpectedFields() {
    V1CustomResourceDefinition crd = defineClusterCrd();
    assertThat(getPropertiesType(crd, "status", "minimumReplicas"), equalTo("integer"));
    assertThat(getPropertiesType(crd, "status", "maximumReplicas"), equalTo("integer"));
  }

  // todo check additional arguments: if second arg not present: error or skip cluster resource ?


  private V1CustomResourceDefinition defineDomainCrd() {
    return new CrdHelper.DomainCrdContext().createModel(PRODUCT_VERSION, getCertificates());
  }

}

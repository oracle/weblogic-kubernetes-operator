// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1CustomResourceConversion;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1JSONSchemaProps;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.common.logging.MessageKeys.ASYNC_NO_RETRY;
import static oracle.kubernetes.common.logging.MessageKeys.CREATE_CRD_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.CREATING_CRD;
import static oracle.kubernetes.common.logging.MessageKeys.REPLACE_CRD_FAILED;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsWarning;
import static oracle.kubernetes.operator.ProcessingConstants.WEBHOOK;
import static oracle.kubernetes.operator.WebhookMainTest.getCertificates;
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

class CrdHelperTest {
  private static final SemanticVersion PRODUCT_VERSION = new SemanticVersion(3, 0, 0);
  private static final SemanticVersion PRODUCT_VERSION_OLD = new SemanticVersion(2, 4, 0);
  private static final SemanticVersion PRODUCT_VERSION_FUTURE = new SemanticVersion(3, 1, 0);
  public static final String WEBHOOK_CERTIFICATE = "/deployment/webhook-identity/webhookCert";

  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final InMemoryFileSystem fileSystem = InMemoryFileSystem.createInstance();
  private final Function<URI, Path> pathFunction = fileSystem::getPath;
  private final Function<URI, Path> pathFunctionWithException = fileSystem::getPathThrowsIllegaArgumentException;
  private final Function<String, Path> getInMemoryPath = fileSystem::getPath;
  private final TerminalStep terminalStep = new TerminalStep();
  private TestUtils.ConsoleHandlerMemento consoleHandlerMemento;

  private V1CustomResourceDefinition defineDomainCrd() {
    return new CrdHelper.DomainCrdContext().createModel(PRODUCT_VERSION, getCertificates());
  }

  private V1CustomResourceDefinition defineCrd(SemanticVersion operatorVersion) {
    return new V1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion))
        .spec(createSpec());
  }

  @SuppressWarnings("SameParameterValue")
  private V1ObjectMeta createMetadata(SemanticVersion operatorVersion) {
    return new V1ObjectMeta()
        .name(KubernetesConstants.CRD_NAME)
        .putLabelsItem(LabelConstants.OPERATOR_VERSION,
            Optional.ofNullable(operatorVersion).map(SemanticVersion::toString).orElse(null));
  }

  private V1CustomResourceDefinitionSpec createSpec() {
    return new V1CustomResourceDefinitionSpec()
        .group(KubernetesConstants.DOMAIN_GROUP)
        .scope("Namespaced")
        .addVersionsItem(new V1CustomResourceDefinitionVersion()
            .served(true).name(KubernetesConstants.OLD_DOMAIN_VERSION))
        .names(
            new V1CustomResourceDefinitionNames()
                .plural(KubernetesConstants.DOMAIN_PLURAL)
                .singular(KubernetesConstants.DOMAIN_SINGULAR)
                .kind(KubernetesConstants.DOMAIN)
                .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT)));
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        consoleHandlerMemento = TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CREATING_CRD, REPLACE_CRD_FAILED, CREATE_CRD_FAILED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(FileGroupReader.class, "uriToPath", pathFunction));
    mementos.add(StaticStubSupport.install(CrdHelper.class, "uriToPath", pathFunction));
    mementos.add(StaticStubSupport.install(Certificates.class, "getPath", getInMemoryPath));
    mementos.add(TuningParametersStub.install());
    mementos.add(UnitTestHash.install());
  }

  @AfterEach
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  void verifyOperatorMapPropertiesGenerated() {
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

  @Nullable
  private String getPropertiesType(V1CustomResourceDefinition crd, String... pathElements) {
    return getProperties(crd, pathElements).getType();
  }

  @Nullable
  private V1JSONSchemaProps getProperties(V1CustomResourceDefinition crd, String[] pathElements) {
    V1JSONSchemaProps schemaProps = crd.getSpec().getVersions().get(0).getSchema().getOpenAPIV3Schema();
    for (String pathElement : pathElements) {
      schemaProps = schemaProps.getProperties().get(pathElement);
    }
    return schemaProps;
  }

  @Test
  void whenCrdV1SupportedAndNoCrd_createIt() {
    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenNotAuthorizedToReadCrd_retryOnFailureAndLogWarningMessageInOnFailureNoRetry() {
    consoleHandlerMemento.collectLogMessages(logRecords, ASYNC_NO_RETRY);
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(CUSTOM_RESOURCE_DEFINITION, null, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, containsWarning(ASYNC_NO_RETRY));
  }

  @Test
  void whenNoCrd_retryOnFailureAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CUSTOM_RESOURCE_DEFINITION, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, containsInfo(CREATE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  void whenNoCrd_proceedToNextStep() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CUSTOM_RESOURCE_DEFINITION, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(Step.chain(scriptCrdStep, terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
    logRecords.clear();
  }

  @Test
  void whenExistingCrdHasCurrentApiVersionButOldProductVersion_replaceIt() {
    testSupport.defineResources(defineCrd(PRODUCT_VERSION));

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION_FUTURE));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    List<V1CustomResourceDefinition> crds = testSupport.getResources(CUSTOM_RESOURCE_DEFINITION);
    V1CustomResourceDefinition crd = crds.stream().findFirst().orElse(null);
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

  @Test
  void whenExistingCrdHasFutureVersionWithConversionWebhook_dontReplaceIt() {
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_FUTURE);
    existing.getSpec().addVersionsItem(
            new V1CustomResourceDefinitionVersion().served(true).name(KubernetesConstants.DOMAIN_VERSION))
            .conversion(new V1CustomResourceConversion().strategy("Webhook"));
    testSupport.defineResources(existing);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, not(containsInfo(CREATING_CRD)));
  }

  @Test
  void whenExistingCrdHasNoneConversionStrategy_replaceIt() {
    final V1CustomResourceDefinition crd = defineDomainCrd();
    crd
            .getSpec()
            .addVersionsItem(
                    new V1CustomResourceDefinitionVersion()
                            .served(true)
                            .name(KubernetesConstants.DOMAIN_VERSION))
            .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(crd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenExistingCrdHasCompatibleConversionWebhook_dontReplaceIt() {
    V1CustomResourceDefinition crd = defineDomainCrd();
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    crd.getSpec().addVersionsItem(
        new V1CustomResourceDefinitionVersion().served(true).name(KubernetesConstants.DOMAIN_VERSION))
        .conversion(CrdHelper.CrdContext.createConversionWebhook("asdf"));
    testSupport.defineResources(crd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION, getCertificates()));
    assertThat(logRecords, not(containsInfo(CREATING_CRD)));
  }

  @Test
  void whenExistingCrdHasIncompatibleConversionWebhook_replaceIt() {
    V1CustomResourceDefinition crd = defineDomainCrd();
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    crd.getSpec().addVersionsItem(
        new V1CustomResourceDefinitionVersion().served(true).name(KubernetesConstants.DOMAIN_VERSION))
        .conversion(CrdHelper.CrdContext.createConversionWebhook("xyz"));
    testSupport.defineResources(crd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION, getCertificates()));
    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenCrdStepCalledWithNullProductVersionAndIncompatibleConversionWebhook_replaceIt() {
    V1CustomResourceDefinition crd = defineDomainCrd();
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    crd.getSpec().addVersionsItem(
        new V1CustomResourceDefinitionVersion().served(true).name(KubernetesConstants.DOMAIN_VERSION))
        .conversion(CrdHelper.CrdContext.createConversionWebhook("xyz"));
    testSupport.defineResources(crd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(null, getCertificates()));
    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenExistingCrdHasFutureVersionAndIncompatibleConversionWebhook_dontReplaceIt() {
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_FUTURE);
    existing.getSpec().addVersionsItem(
        new V1CustomResourceDefinitionVersion().served(true).name(KubernetesConstants.DOMAIN_VERSION))
        .conversion(CrdHelper.CrdContext.createConversionWebhook("xyz"));
    testSupport.defineResources(existing);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION, getCertificates()));

    assertThat(logRecords, not(containsInfo(CREATING_CRD)));
  }

  @Test
  void whenExistingCrdHasOldVersionAndNoneConversionStrategy_replaceIt() {
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_OLD);
    existing
            .getSpec()
            .addVersionsItem(
                    new V1CustomResourceDefinitionVersion()
                            .served(true)
                            .name(KubernetesConstants.DOMAIN_VERSION))
            .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(existing);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenExistingCrdHasFutureVersionButNoneConversionStrategy_updateCrdWithWebhook() {
    fileSystem.defineFile(WEBHOOK_CERTIFICATE, "asdf");
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_FUTURE);
    existing
            .getSpec()
            .addVersionsItem(
                    new V1CustomResourceDefinitionVersion()
                            .served(true)
                            .name(KubernetesConstants.DOMAIN_VERSION))
            .conversion(new V1CustomResourceConversion().strategy("None"));
    testSupport.defineResources(existing);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION, getCertificates()));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    assertThat(existing.getSpec().getConversion().getStrategy(), is(WEBHOOK));
  }

  @Test
  void whenExistingCrdHasFutureVersionButNotCurrentStorage_updateIt() {
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_FUTURE));

    V1CustomResourceDefinition replacement = defineCrd(PRODUCT_VERSION_FUTURE);
    replacement
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));

    testSupport.runSteps(CrdHelper.createDomainCrdStep(PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  void whenReplaceFails_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_OLD));
    testSupport.failOnReplace(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  void whenReplaceFailsThrowsStreamException_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_OLD));
    testSupport.failOnReplaceWithStreamResetException(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(PRODUCT_VERSION);
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
    StaticStubSupport.install(CrdHelper.class, "uriToPath", pathFunctionWithException);

    Assertions.assertThrows(CrdHelper.CrdCreationException.class, () -> CrdHelper.main("crd.yaml", "cluster.yaml"));
  }

  @Test
  void whenClusterCrdCreated_specContainsFields() {
    V1CustomResourceDefinition crd = defineClusterCrd();
    assertThat(getPropertiesType(crd, "spec", "clusterName"), equalTo("string"));
    assertThat(getPropertiesType(crd, "spec", "replicas"), equalTo("number"));
  }

  private V1CustomResourceDefinition defineClusterCrd() {
    return new CrdHelper.ClusterCrdContext().createModel(PRODUCT_VERSION, null);
  }


  @Test
  void whenClusterCrdCreated_statusContainsExpectedFields() {
    V1CustomResourceDefinition crd = defineClusterCrd();
    assertThat(getPropertiesType(crd, "spec", "replicas"), equalTo("number"));
    assertThat(getPropertiesType(crd, "status", "readyReplicas"), equalTo("number"));
    assertThat(getPropertiesType(crd, "status", "maximumReplicas"), equalTo("number"));
  }

  // todo check additional arguments: if second arg not present: error or skip cluster resource ?

}

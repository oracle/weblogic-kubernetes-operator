// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinitionSpec;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.TerminalStep;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.BETA_CRD;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CUSTOM_RESOURCE_DEFINITION;
import static oracle.kubernetes.operator.logging.MessageKeys.CREATE_CRD_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.CREATING_CRD;
import static oracle.kubernetes.operator.logging.MessageKeys.REPLACE_CRD_FAILED;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

public class CrdHelperTest {
  private static final KubernetesVersion KUBERNETES_VERSION_15 = new KubernetesVersion(1, 15);
  private static final KubernetesVersion KUBERNETES_VERSION_16 = new KubernetesVersion(1, 16);

  private static final SemanticVersion PRODUCT_VERSION = new SemanticVersion(3, 0, 0);
  private static final SemanticVersion PRODUCT_VERSION_OLD = new SemanticVersion(2, 4, 0);
  private static final SemanticVersion PRODUCT_VERSION_FUTURE = new SemanticVersion(3, 1, 0);
  private static final int UNPROCESSABLE_ENTITY = 422;

  private V1CustomResourceDefinition defaultCrd;
  private V1beta1CustomResourceDefinition defaultBetaCrd;
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final InMemoryFileSystem fileSystem = InMemoryFileSystem.createInstance();
  private final Function<URI, Path> pathFunction = fileSystem::getPath;
  private final TerminalStep terminalStep = new TerminalStep();

  private V1CustomResourceDefinition defineDefaultCrd() {
    return CrdHelper.CrdContext.createModel(KUBERNETES_VERSION_16, PRODUCT_VERSION);
  }

  private V1beta1CustomResourceDefinition defineDefaultBetaCrd() {
    return CrdHelper.CrdContext.createBetaModel(KUBERNETES_VERSION_15, PRODUCT_VERSION);
  }

  private V1CustomResourceDefinition defineCrd(SemanticVersion operatorVersion) {
    return new V1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion))
        .spec(createSpec());
  }

  @SuppressWarnings("SameParameterValue")
  private V1beta1CustomResourceDefinition defineBetaCrd(SemanticVersion operatorVersion) {
    return new V1beta1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1beta1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion))
        .spec(createBetaSpec());
  }

  private V1ObjectMeta createMetadata(SemanticVersion operatorVersion) {
    return new V1ObjectMeta()
        .name(KubernetesConstants.CRD_NAME)
        .putLabelsItem(LabelConstants.OPERATOR_VERISON, operatorVersion.toString());
  }

  private V1CustomResourceDefinitionSpec createSpec() {
    return new V1CustomResourceDefinitionSpec()
        .group(KubernetesConstants.DOMAIN_GROUP)
        .scope("Namespaced")
        .names(
            new V1CustomResourceDefinitionNames()
                .plural(KubernetesConstants.DOMAIN_PLURAL)
                .singular(KubernetesConstants.DOMAIN_SINGULAR)
                .kind(KubernetesConstants.DOMAIN)
                .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT)));
  }

  private V1beta1CustomResourceDefinitionSpec createBetaSpec() {
    return new V1beta1CustomResourceDefinitionSpec()
        .group(KubernetesConstants.DOMAIN_GROUP)
        .scope("Namespaced")
        .names(
            new V1beta1CustomResourceDefinitionNames()
                .plural(KubernetesConstants.DOMAIN_PLURAL)
                .singular(KubernetesConstants.DOMAIN_SINGULAR)
                .kind(KubernetesConstants.DOMAIN)
                .shortNames(Collections.singletonList(KubernetesConstants.DOMAIN_SHORT)));
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CREATING_CRD, REPLACE_CRD_FAILED, CREATE_CRD_FAILED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
    mementos.add(StaticStubSupport.install(FileGroupReader.class, "uriToPath", pathFunction));

    defaultCrd = defineDefaultCrd();
    defaultBetaCrd = defineDefaultBetaCrd();
  }

  @AfterEach
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenUnableToReadBetaCrd_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(BETA_CRD, KubernetesConstants.CRD_NAME, UNPROCESSABLE_ENTITY);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void whenCrdV1SupportedAndNoCrd_createIt() {
    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenCrdV1SupportedAndBetaCrd_upgradeIt() {
    testSupport.defineResources(defaultBetaCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    assertThat(testSupport.getResources(CUSTOM_RESOURCE_DEFINITION), hasItem(defaultCrd));
  }

  @Test
  public void whenNoBetaCrd_createIt() {
    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    assertThat(testSupport.getResources(BETA_CRD), hasItem(defaultBetaCrd));
  }

  @Test
  public void whenNoBetaCrd_retryOnFailureAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(BETA_CRD, KubernetesConstants.CRD_NAME, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, containsInfo(CREATE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenNoCrd_retryOnFailureAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16,PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, containsInfo(CREATE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenNoCrd_proceedToNextStep() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16,PRODUCT_VERSION);
    testSupport.runSteps(Step.chain(scriptCrdStep, terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
    logRecords.clear();
  }

  @Test
  public void whenMatchingCrdExists_noop() {
    testSupport.defineResources(defaultBetaCrd);

    testSupport.runSteps(
          Step.chain(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION), terminalStep));

    assertThat(terminalStep.wasRun(), is(true));
  }

  @Test
  public void whenExistingCrdHasOldVersion_replaceIt() {
    testSupport.defineResources(defineBetaCrd(PRODUCT_VERSION_OLD));

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenExistingCrdHasCurrentApiVersionButOldProductVersion_replaceIt() {
    testSupport.defineResources(defineCrd(PRODUCT_VERSION));

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION_FUTURE));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    List<V1CustomResourceDefinition> crds = testSupport.getResources(CUSTOM_RESOURCE_DEFINITION);
    V1CustomResourceDefinition crd = crds.stream().findFirst().orElse(null);
    assertNotNull(crd);
    assertThat(getProductVersionFromMetadata(crd.getMetadata()), equalTo(PRODUCT_VERSION_FUTURE));
  }

  private SemanticVersion getProductVersionFromMetadata(V1ObjectMeta metadata) {
    return Optional.ofNullable(metadata)
            .map(V1ObjectMeta::getLabels)
            .map(labels -> labels.get(LabelConstants.OPERATOR_VERISON))
            .map(SemanticVersion::new)
            .orElse(null);
  }

  @Test
  public void whenExistingCrdHasFutureVersion_dontReplaceIt() {
    V1CustomResourceDefinition existing = defineCrd(PRODUCT_VERSION_FUTURE);
    existing
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));
    testSupport.defineResources(existing);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION));
  }

  @Test
  public void whenExistingCrdHasFutureVersionButNotCurrentStorage_updateIt() {
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_FUTURE));

    V1CustomResourceDefinition replacement = defineCrd(PRODUCT_VERSION_FUTURE);
    replacement
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenReplaceFails_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_OLD));
    testSupport.failOnReplace(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenReplaceFailsThrowsStreamException_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineCrd(PRODUCT_VERSION_OLD));
    testSupport.failOnReplaceWithStreamResetException(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenBetaCrdReplaceFails_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineBetaCrd(PRODUCT_VERSION_OLD));
    testSupport.failOnReplace(BETA_CRD, KubernetesConstants.CRD_NAME, null, HTTP_UNAUTHORIZED);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenBetaCrdReplaceThrowsStreamResetException_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineBetaCrd(PRODUCT_VERSION_OLD));
    testSupport.failOnReplaceWithStreamResetException(BETA_CRD, KubernetesConstants.CRD_NAME, null);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

}

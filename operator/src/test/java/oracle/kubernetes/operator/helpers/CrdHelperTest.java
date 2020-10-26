// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
import io.kubernetes.client.openapi.models.V1JSONSchemaProps;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceValidation;
import io.kubernetes.client.openapi.models.V1beta1JSONSchemaProps;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.calls.FailureStatusSourceException;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.BETA_CRD;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CUSTOM_RESOURCE_DEFINITION;
import static oracle.kubernetes.operator.logging.MessageKeys.CREATE_CRD_FAILED;
import static oracle.kubernetes.operator.logging.MessageKeys.CREATING_CRD;
import static oracle.kubernetes.operator.logging.MessageKeys.REPLACE_CRD_FAILED;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

public class CrdHelperTest {
  private static final KubernetesVersion KUBERNETES_VERSION_15 = new KubernetesVersion(1, 15);
  private static final KubernetesVersion KUBERNETES_VERSION_16 = new KubernetesVersion(1, 16);

  private static final SemanticVersion PRODUCT_VERSION = new SemanticVersion(3, 0, 0);
  private static final SemanticVersion PRODUCT_VERSION_OLD = new SemanticVersion(2, 4, 0);
  private static final SemanticVersion PRODUCT_VERSION_FUTURE = new SemanticVersion(3, 1, 0);

  private V1CustomResourceDefinition defaultCrd;
  private V1beta1CustomResourceDefinition defaultBetaCrd;
  private final RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final InMemoryFileSystem fileSystem = InMemoryFileSystem.createInstance();
  private final Function<URI, Path> pathFunction = fileSystem::getPath;

  private V1CustomResourceDefinition defineDefaultCrd() {
    return CrdHelper.CrdContext.createModel(KUBERNETES_VERSION_16, PRODUCT_VERSION);
  }

  private V1beta1CustomResourceDefinition defineDefaultBetaCrd() {
    return CrdHelper.CrdContext.createBetaModel(KUBERNETES_VERSION_15, PRODUCT_VERSION);
  }

  private V1CustomResourceDefinition defineCrd(String version, SemanticVersion operatorVersion) {
    return new V1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion))
        .spec(createSpec(version));
  }

  @SuppressWarnings("SameParameterValue")
  private V1beta1CustomResourceDefinition defineBetaCrd(String version, SemanticVersion operatorVersion) {
    return new V1beta1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1beta1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion))
        .spec(createBetaSpec(version));
  }

  private V1ObjectMeta createMetadata(SemanticVersion operatorVersion) {
    return new V1ObjectMeta()
        .name(KubernetesConstants.CRD_NAME)
        .putLabelsItem(LabelConstants.OPERATOR_VERISON, operatorVersion.toString());
  }

  private V1CustomResourceDefinitionSpec createSpec(String version) {
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

  private V1beta1CustomResourceDefinitionSpec createBetaSpec(String version) {
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

  @Before
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

  @After
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  public void whenUnableToReadBetaCrd_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnResource(BETA_CRD, KubernetesConstants.CRD_NAME, 422);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION, null);
    testSupport.runSteps(scriptCrdStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void whenCrdV1SupportedAndNoCrd_createIt() {
    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenCrdV1SupportedAndBetaCrd_upgradeIt() {
    testSupport.defineResources(defaultBetaCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    assertThat(testSupport.getResources(CUSTOM_RESOURCE_DEFINITION), hasItem(defaultCrd));
  }

  @Test
  public void whenNoBetaCrd_createIt() {
    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
    assertThat(testSupport.getResources(BETA_CRD), hasItem(defaultBetaCrd));
  }

  @Test
  public void whenNoBetaCrd_retryOnFailureAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(BETA_CRD, KubernetesConstants.CRD_NAME, null, 401);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION, null);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, containsInfo(CREATE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenNoCrd_retryOnFailureAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.failOnCreate(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null, 401);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16,PRODUCT_VERSION,  null);
    testSupport.runSteps(scriptCrdStep);
    assertThat(logRecords, containsInfo(CREATE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenMatchingCrdExists_noop() {
    testSupport.defineResources(defaultBetaCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION, null));
  }

  @Test
  public void whenExistingCrdHasOldVersion_replaceIt() {
    testSupport.defineResources(defineBetaCrd("v1", PRODUCT_VERSION_OLD));

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenExistingCrdHasCurrentApiVersionButOldProductVersion_replaceIt() {
    testSupport.defineResources(defineCrd(KubernetesConstants.DOMAIN_VERSION, PRODUCT_VERSION));

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION_FUTURE, null));

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
  @Ignore
  public void whenExistingCrdHasFutureVersion_dontReplaceIt() {
    V1CustomResourceDefinition existing = defineCrd("v500", PRODUCT_VERSION_FUTURE);
    existing
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));
    testSupport.defineResources(existing);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION, null));
  }

  @Test
  @Ignore
  public void whenExistingCrdHasFutureVersionButNotCurrentStorage_updateIt() {
    testSupport.defineResources(defineCrd("v500", PRODUCT_VERSION_FUTURE));

    V1CustomResourceDefinition replacement = defineCrd("v500", PRODUCT_VERSION_FUTURE);
    replacement
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenReplaceFails_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineCrd("v1", PRODUCT_VERSION_OLD));
    testSupport.failOnReplace(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null, 401);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION, null);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenReplaceFailsThrowsStreamException_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineCrd("v1", PRODUCT_VERSION_OLD));
    testSupport.failOnReplaceWithStreamResetException(CUSTOM_RESOURCE_DEFINITION, KubernetesConstants.CRD_NAME, null);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, PRODUCT_VERSION, null);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenBetaCrdReplaceFails_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineBetaCrd("v1", PRODUCT_VERSION_OLD));
    testSupport.failOnReplace(BETA_CRD, KubernetesConstants.CRD_NAME, null, 401);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION, null);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenBetaCrdReplaceThrowsStreamResetException_scheduleRetryAndLogFailedMessageInOnFailureNoRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    testSupport.defineResources(defineBetaCrd("v1", PRODUCT_VERSION_OLD));
    testSupport.failOnReplaceWithStreamResetException(BETA_CRD, KubernetesConstants.CRD_NAME, null);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, PRODUCT_VERSION, null);
    testSupport.runSteps(scriptCrdStep);

    assertThat(logRecords, containsInfo(REPLACE_CRD_FAILED));
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  class V1CustomResourceDefinitionMatcher implements BodyMatcher {
    private V1CustomResourceDefinition expected;

    V1CustomResourceDefinitionMatcher(V1CustomResourceDefinition expected) {
      this.expected = expected;
    }

    @Override
    public boolean matches(Object actualBody) {
      return actualBody instanceof V1CustomResourceDefinition
          && matches((V1CustomResourceDefinition) actualBody);
    }

    private boolean matches(V1CustomResourceDefinition actualBody) {
      return hasExpectedVersion(actualBody) && hasSchemaVerification(actualBody);
    }

    private boolean hasExpectedVersion(V1CustomResourceDefinition actualBody) {
      return Objects.equals(expected.getSpec().getVersions(), actualBody.getSpec().getVersions());
    }

    private boolean hasSchemaVerification(V1CustomResourceDefinition actualBody) {
      List<V1CustomResourceDefinitionVersion> versions = actualBody.getSpec().getVersions();
      if (versions == null) {
        return expected.getSpec().getVersions() == null;
      }

      V1JSONSchemaProps openApiV3Schema = null;
      for (V1CustomResourceDefinitionVersion version : versions) {
        if (KubernetesConstants.DOMAIN_VERSION.equals(version.getName())) {
          openApiV3Schema = version.getSchema().getOpenAPIV3Schema();
          break;
        }
      }

      if (openApiV3Schema == null) {
        List<V1CustomResourceDefinitionVersion> expectedVersions = expected.getSpec().getVersions();
        if (expectedVersions != null) {
          for (V1CustomResourceDefinitionVersion version : expectedVersions) {
            if (KubernetesConstants.DOMAIN_VERSION.equals(version.getName())) {
              if (version.getSchema().getOpenAPIV3Schema() != null) {
                return false;
              }
              break;
            }
          }
        }
      }

      if (openApiV3Schema == null || openApiV3Schema.getProperties().size() != 2) {
        return false;
      }

      // check for structural schema condition 1 -- top level type value
      // https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/
      //     custom-resource-definitions/#specifying-a-structural-schema
      if (openApiV3Schema == null || !openApiV3Schema.getType().equals("object")) {
        return false;
      }

      V1JSONSchemaProps spec = openApiV3Schema.getProperties().get("spec");
      if (spec == null || spec.getProperties().isEmpty()) {
        return false;
      }

      return spec.getProperties().containsKey("serverStartState");
    }
  }

  class V1beta1CustomResourceDefinitionMatcher implements BodyMatcher {
    private V1beta1CustomResourceDefinition expected;

    V1beta1CustomResourceDefinitionMatcher(V1beta1CustomResourceDefinition expected) {
      this.expected = expected;
    }

    @Override
    public boolean matches(Object actualBody) {
      return actualBody instanceof V1beta1CustomResourceDefinition
          && matches((V1beta1CustomResourceDefinition) actualBody);
    }

    private boolean matches(V1beta1CustomResourceDefinition actualBody) {
      return hasExpectedVersion(actualBody) && hasSchemaVerification(actualBody);
    }

    private boolean hasExpectedVersion(V1beta1CustomResourceDefinition actualBody) {
      return Objects.equals(expected.getSpec().getVersion(), actualBody.getSpec().getVersion())
          && Objects.equals(expected.getSpec().getVersions(), actualBody.getSpec().getVersions());
    }

    private boolean hasSchemaVerification(V1beta1CustomResourceDefinition actualBody) {
      V1beta1CustomResourceValidation validation = actualBody.getSpec().getValidation();
      if (validation == null) {
        return expected.getSpec().getValidation() == null;
      }

      V1beta1JSONSchemaProps openApiV3Schema = validation.getOpenAPIV3Schema();
      if (openApiV3Schema == null || openApiV3Schema.getProperties().size() != 2) {
        return false;
      }

      // check for structural schema condition 1 -- top level type value
      // https://kubernetes.io/docs/tasks/access-kubernetes-api/custom-resources/
      //     custom-resource-definitions/#specifying-a-structural-schema
      if (openApiV3Schema == null || !openApiV3Schema.getType().equals("object")) {
        return false;
      }

      V1beta1JSONSchemaProps spec = openApiV3Schema.getProperties().get("spec");
      if (spec == null || spec.getProperties().isEmpty()) {
        return false;
      }

      return spec.getProperties().containsKey("serverStartState");
    }
  }
}

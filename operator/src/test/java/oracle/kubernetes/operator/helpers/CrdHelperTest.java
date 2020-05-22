// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
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
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.VersionConstants.OPERATOR_V1;
import static oracle.kubernetes.operator.logging.MessageKeys.CREATING_CRD;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CrdHelperTest {
  private static final KubernetesVersion KUBERNETES_VERSION_15 = new KubernetesVersion(1, 15);
  private static final KubernetesVersion KUBERNETES_VERSION_16 = new KubernetesVersion(1, 16);

  private final V1CustomResourceDefinition defaultCrd = defineDefaultCrd();
  private final V1beta1CustomResourceDefinition defaultBetaCrd = defineDefaultBetaCrd();
  private RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();

  private V1CustomResourceDefinition defineDefaultCrd() {
    return CrdHelper.CrdContext.createModel(KUBERNETES_VERSION_16);
  }

  private V1beta1CustomResourceDefinition defineDefaultBetaCrd() {
    return CrdHelper.CrdContext.createBetaModel(KUBERNETES_VERSION_15);
  }

  private V1CustomResourceDefinition defineCrd(String version, String operatorVersion) {
    return new V1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion))
        .spec(createSpec(version));
  }

  private V1beta1CustomResourceDefinition defineBetaCrd(String version, String operatorVersion) {
    return new V1beta1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1beta1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion))
        .spec(createBetaSpec(version));
  }

  private V1ObjectMeta createMetadata(String operatorVersion) {
    return new V1ObjectMeta()
        .name(KubernetesConstants.CRD_NAME)
        .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, operatorVersion);
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

  /**
   * Setup test.
   * @throws Exception on failure
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CREATING_CRD)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());
  }

  /**
   * Tear down test.
   * @throws Exception on failure
   */
  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) {
      memento.revert();
    }

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  public void whenUnableToReadBetaCrd_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadBetaCrd().failingWithStatus(422);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, null);
    testSupport.runSteps(scriptCrdStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
  }

  @Test
  public void whenCrdV1SupportedAndNoCrd_createIt() {
    expectReadCrd().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectReadBetaCrd().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectSuccessfulCreateCrd(defaultCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenCrdV1SupportedAndBetaCrd_upgradeIt() {
    expectReadCrd().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectReadBetaCrd().returning(defaultBetaCrd);
    expectSuccessfulReplaceCrd(defaultCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenNoBetaCrd_createIt() {
    expectReadBetaCrd().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectSuccessfulCreateBetaCrd(defaultBetaCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenNoCrd_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadBetaCrd().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateBetaCrd(defaultBetaCrd).failingWithStatus(401);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, null);
    testSupport.runSteps(scriptCrdStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenMatchingCrdExists_noop() {
    expectReadBetaCrd().returning(defaultBetaCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, null));
  }

  @Test
  public void whenExistingCrdHasOldVersion_replaceIt() {
    expectReadBetaCrd().returning(defineBetaCrd("v1", OPERATOR_V1));
    expectSuccessfulReplaceBetaCrd(defaultBetaCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_15, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  @Ignore
  public void whenExistingCrdHasFutureVersion_dontReplaceIt() {
    V1CustomResourceDefinition existing = defineCrd("v500", "operator-v500");
    existing
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));
    expectReadCrd().returning(existing);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, null));
  }

  @Test
  @Ignore
  public void whenExistingCrdHasFutureVersionButNotCurrentStorage_updateIt() {
    expectReadCrd().returning(defineCrd("v500", "operator-v500"));

    V1CustomResourceDefinition replacement = defineCrd("v500", "operator-v500");
    replacement
        .getSpec()
        .addVersionsItem(
            new V1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));
    expectSuccessfulReplaceCrd(replacement);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenReplaceFails_scheduleRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadCrd().returning(defineCrd("v1", OPERATOR_V1));
    expectReplaceCrd(defaultCrd).failingWithStatus(401);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION_16, null);
    testSupport.runSteps(scriptCrdStep);

    testSupport.verifyCompletionThrowable(FailureStatusSourceException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  private CallTestSupport.CannedResponse expectReadCrd() {
    return testSupport.createCannedResponse("readCRD").withName(KubernetesConstants.CRD_NAME);
  }

  private CallTestSupport.CannedResponse expectReadBetaCrd() {
    return testSupport.createCannedResponse("readBetaCRD").withName(KubernetesConstants.CRD_NAME);
  }

  private void expectSuccessfulCreateCrd(V1CustomResourceDefinition expectedConfig) {
    expectCreateCrd(expectedConfig).returning(expectedConfig);
  }

  private void expectSuccessfulCreateBetaCrd(V1beta1CustomResourceDefinition expectedConfig) {
    expectCreateBetaCrd(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectCreateCrd(
      V1CustomResourceDefinition expectedConfig) {
    return testSupport
        .createCannedResponse("createCRD")
        .withBody(new V1CustomResourceDefinitionMatcher(expectedConfig));
  }

  private CallTestSupport.CannedResponse expectCreateBetaCrd(
      V1beta1CustomResourceDefinition expectedConfig) {
    return testSupport
        .createCannedResponse("createBetaCRD")
        .withBody(new V1beta1CustomResourceDefinitionMatcher(expectedConfig));
  }

  private void expectSuccessfulReplaceCrd(V1CustomResourceDefinition expectedConfig) {
    expectReplaceCrd(expectedConfig).returning(expectedConfig);
  }

  private void expectSuccessfulReplaceBetaCrd(V1beta1CustomResourceDefinition expectedConfig) {
    expectReplaceBetaCrd(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectReplaceCrd(
      V1CustomResourceDefinition expectedConfig) {
    return testSupport
        .createCannedResponse("replaceCRD")
        .withName(KubernetesConstants.CRD_NAME)
        .withBody(new V1CustomResourceDefinitionMatcher(expectedConfig));
  }

  private CallTestSupport.CannedResponse expectReplaceBetaCrd(
      V1beta1CustomResourceDefinition expectedConfig) {
    return testSupport
        .createCannedResponse("replaceBetaCRD")
        .withName(KubernetesConstants.CRD_NAME)
        .withBody(new V1beta1CustomResourceDefinitionMatcher(expectedConfig));
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

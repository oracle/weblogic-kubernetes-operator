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
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionNames;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionSpec;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinitionVersion;
import io.kubernetes.client.openapi.models.V1JSONSchemaProps;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
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
  private static final KubernetesVersion KUBERNETES_VERSION = new KubernetesVersion(1, 10);

  private final V1CustomResourceDefinition defaultCrd = defineDefaultCrd();
  private RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();

  private V1CustomResourceDefinition defineDefaultCrd() {
    return CrdHelper.CrdContext.createModel(KUBERNETES_VERSION);
  }

  private V1CustomResourceDefinition defineCrd(String version, String operatorVersion) {
    return new V1CustomResourceDefinition()
        .apiVersion("apiextensions.k8s.io/v1beta1")
        .kind("CustomResourceDefinition")
        .metadata(createMetadata(operatorVersion))
        .spec(createSpec(version));
  }

  private V1ObjectMeta createMetadata(String operatorVersion) {
    return new V1ObjectMeta()
        .name(KubernetesConstants.CRD_NAME)
        .putLabelsItem(LabelConstants.RESOURCE_VERSION_LABEL, operatorVersion);
  }

  private V1CustomResourceDefinitionSpec createSpec(String version) {
    return new V1CustomResourceDefinitionSpec()
        .group(KubernetesConstants.DOMAIN_GROUP)
        // FIXME .version(version)
        .scope("Namespaced")
        .names(
            new V1CustomResourceDefinitionNames()
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
  public void whenUnableToReadCrd_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadCrd().failingWithStatus(422);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION, null);
    testSupport.runSteps(scriptCrdStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenNoCrd_createIt() {
    expectReadCrd().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectSuccessfulCreateCrd(defaultCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenNoCrd_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadCrd().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateCrd(defaultCrd).failingWithStatus(401);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION, null);
    testSupport.runSteps(scriptCrdStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  @Test
  public void whenMatchingCrdExists_noop() {
    expectReadCrd().returning(defaultCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION, null));
  }

  @Test
  public void whenExistingCrdHasOldVersion_replaceIt() {
    expectReadCrd().returning(defineCrd("v1", OPERATOR_V1));
    expectSuccessfulReplaceCrd(defaultCrd);

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION, null));

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

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION, null));
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

    testSupport.runSteps(CrdHelper.createDomainCrdStep(KUBERNETES_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenReplaceFails_scheduleRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadCrd().returning(defineCrd("v1", OPERATOR_V1));
    expectReplaceCrd(defaultCrd).failingWithStatus(401);

    Step scriptCrdStep = CrdHelper.createDomainCrdStep(KUBERNETES_VERSION, null);
    testSupport.runSteps(scriptCrdStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCrdStep));
  }

  private CallTestSupport.CannedResponse expectReadCrd() {
    return testSupport.createCannedResponse("readCRD").withName(KubernetesConstants.CRD_NAME);
  }

  private void expectSuccessfulCreateCrd(V1CustomResourceDefinition expectedConfig) {
    expectCreateCrd(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectCreateCrd(
      V1CustomResourceDefinition expectedConfig) {
    return testSupport
        .createCannedResponse("createCRD")
        .withBody(new V1CustomResourceDefinitionMatcher(expectedConfig));
  }

  private void expectSuccessfulReplaceCrd(V1CustomResourceDefinition expectedConfig) {
    expectReplaceCrd(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectReplaceCrd(
      V1CustomResourceDefinition expectedConfig) {
    return testSupport
        .createCannedResponse("replaceCRD")
        .withName(KubernetesConstants.CRD_NAME)
        .withBody(new V1CustomResourceDefinitionMatcher(expectedConfig));
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
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.LogMatcher.containsInfo;
import static oracle.kubernetes.operator.VersionConstants.OPERATOR_V1;
import static oracle.kubernetes.operator.logging.MessageKeys.CREATING_CRD;
import static org.hamcrest.Matchers.sameInstance;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionNames;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionSpec;
import io.kubernetes.client.models.V1beta1CustomResourceDefinitionVersion;
import io.kubernetes.client.models.V1beta1CustomResourceValidation;
import io.kubernetes.client.models.V1beta1JSONSchemaProps;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import oracle.kubernetes.TestUtils;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.work.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CRDHelperTest {
  private static final KubernetesVersion KUBERNETES_VERSION = new KubernetesVersion(1, 10);

  private final V1beta1CustomResourceDefinition defaultCRD = defineDefaultCRD();
  private RetryStrategyStub retryStrategy = createStrictStub(RetryStrategyStub.class);

  private AsyncCallTestSupport testSupport = new AsyncCallTestSupport();
  private List<Memento> mementos = new ArrayList<>();
  private List<LogRecord> logRecords = new ArrayList<>();

  private V1beta1CustomResourceDefinition defineDefaultCRD() {
    return CRDHelper.CRDContext.createModel(KUBERNETES_VERSION);
  }

  private V1beta1CustomResourceDefinition defineCRD(String version, String operatorVersion) {
    return new V1beta1CustomResourceDefinition()
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

  private V1beta1CustomResourceDefinitionSpec createSpec(String version) {
    return new V1beta1CustomResourceDefinitionSpec()
        .group(KubernetesConstants.DOMAIN_GROUP)
        .version(version)
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
            .collectLogMessages(logRecords, CREATING_CRD)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.installRequestStepFactory());
  }

  @After
  public void tearDown() throws Exception {
    for (Memento memento : mementos) memento.revert();

    testSupport.throwOnCompletionFailure();
    testSupport.verifyAllDefinedResponsesInvoked();
  }

  @Test
  public void whenUnableToReadCRD_reportFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadCRD().failingWithStatus(401);

    Step scriptCRDStep = CRDHelper.createDomainCRDStep(KUBERNETES_VERSION, null);
    testSupport.runSteps(scriptCRDStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
  }

  @Test
  public void whenNoCRD_createIt() {
    expectReadCRD().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectSuccessfulCreateCRD(defaultCRD);

    testSupport.runSteps(CRDHelper.createDomainCRDStep(KUBERNETES_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenNoCRD_retryOnFailure() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadCRD().failingWithStatus(HttpURLConnection.HTTP_NOT_FOUND);
    expectCreateCRD(defaultCRD).failingWithStatus(401);

    Step scriptCRDStep = CRDHelper.createDomainCRDStep(KUBERNETES_VERSION, null);
    testSupport.runSteps(scriptCRDStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCRDStep));
  }

  @Test
  public void whenMatchingCRDExists_noop() {
    expectReadCRD().returning(defaultCRD);

    testSupport.runSteps(CRDHelper.createDomainCRDStep(KUBERNETES_VERSION, null));
  }

  @Test
  public void whenExistingCRDHasOldVersion_replaceIt() {
    expectReadCRD().returning(defineCRD("v1", OPERATOR_V1));
    expectSuccessfulReplaceCRD(defaultCRD);

    testSupport.runSteps(CRDHelper.createDomainCRDStep(KUBERNETES_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenExistingCRDHasFutureVersion_dontReplaceIt() {
    V1beta1CustomResourceDefinition existing = defineCRD("v4", "operator-v4");
    existing
        .getSpec()
        .addVersionsItem(
            new V1beta1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));
    expectReadCRD().returning(existing);

    testSupport.runSteps(CRDHelper.createDomainCRDStep(KUBERNETES_VERSION, null));
  }

  @Test
  public void whenExistingCRDHasFutureVersionButNotCurrentStorage_updateIt() {
    expectReadCRD().returning(defineCRD("v4", "operator-v4"));

    V1beta1CustomResourceDefinition replacement = defineCRD("v4", "operator-v4");
    replacement
        .getSpec()
        .addVersionsItem(
            new V1beta1CustomResourceDefinitionVersion()
                .served(true)
                .name(KubernetesConstants.DOMAIN_VERSION));
    expectSuccessfulReplaceCRD(replacement);

    testSupport.runSteps(CRDHelper.createDomainCRDStep(KUBERNETES_VERSION, null));

    assertThat(logRecords, containsInfo(CREATING_CRD));
  }

  @Test
  public void whenReplaceFails_scheduleRetry() {
    testSupport.addRetryStrategy(retryStrategy);
    expectReadCRD().returning(defineCRD("v1", OPERATOR_V1));
    expectReplaceCRD(defaultCRD).failingWithStatus(401);

    Step scriptCRDStep = CRDHelper.createDomainCRDStep(KUBERNETES_VERSION, null);
    testSupport.runSteps(scriptCRDStep);

    testSupport.verifyCompletionThrowable(ApiException.class);
    assertThat(retryStrategy.getConflictStep(), sameInstance(scriptCRDStep));
  }

  private CallTestSupport.CannedResponse expectReadCRD() {
    return testSupport.createCannedResponse("readCRD").withName(KubernetesConstants.CRD_NAME);
  }

  private void expectSuccessfulCreateCRD(V1beta1CustomResourceDefinition expectedConfig) {
    expectCreateCRD(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectCreateCRD(
      V1beta1CustomResourceDefinition expectedConfig) {
    return testSupport
        .createCannedResponse("createCRD")
        .withBody(new V1beta1CustomResourceDefinitionMatcher(expectedConfig));
  }

  private void expectSuccessfulReplaceCRD(V1beta1CustomResourceDefinition expectedConfig) {
    expectReplaceCRD(expectedConfig).returning(expectedConfig);
  }

  private CallTestSupport.CannedResponse expectReplaceCRD(
      V1beta1CustomResourceDefinition expectedConfig) {
    return testSupport
        .createCannedResponse("replaceCRD")
        .withName(KubernetesConstants.CRD_NAME)
        .withBody(new V1beta1CustomResourceDefinitionMatcher(expectedConfig));
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
      if (validation == null) return expected.getSpec().getValidation() == null;

      V1beta1JSONSchemaProps openAPIV3Schema = validation.getOpenAPIV3Schema();
      if (openAPIV3Schema == null || openAPIV3Schema.getProperties().size() != 2) return false;

      V1beta1JSONSchemaProps spec = openAPIV3Schema.getProperties().get("spec");
      if (spec == null || spec.getProperties().isEmpty()) return false;

      return spec.getProperties().containsKey("serverStartState");
    }
  }
}

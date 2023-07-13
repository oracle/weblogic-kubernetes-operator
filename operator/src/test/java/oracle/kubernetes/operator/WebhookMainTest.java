// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.file.NoSuchFileException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.AdmissionregistrationV1ServiceReference;
import io.kubernetes.client.openapi.models.AdmissionregistrationV1WebhookClientConfig;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1RuleWithOperations;
import io.kubernetes.client.openapi.models.V1ValidatingWebhook;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.calls.UnrecoverableCallException;
import oracle.kubernetes.operator.helpers.CrdHelperTestBase;
import oracle.kubernetes.operator.helpers.HelmAccessStub;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.OnConflictRetryStrategyStub;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.helpers.UnitTestHash;
import oracle.kubernetes.operator.http.BaseServer;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.Container;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.TestUtils;
import org.glassfish.jersey.server.ResourceConfig;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.common.CommonConstants.SECRETS_WEBHOOK_CERT;
import static oracle.kubernetes.common.CommonConstants.SECRETS_WEBHOOK_KEY;
import static oracle.kubernetes.common.logging.MessageKeys.CRD_NOT_INSTALLED;
import static oracle.kubernetes.common.logging.MessageKeys.CREATE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.READ_VALIDATING_WEBHOOK_CONFIGURATION_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.REPLACE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED;
import static oracle.kubernetes.common.logging.MessageKeys.VALIDATING_WEBHOOK_CONFIGURATION_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.VALIDATING_WEBHOOK_CONFIGURATION_REPLACED;
import static oracle.kubernetes.common.logging.MessageKeys.WAIT_FOR_CRD_INSTALLATION;
import static oracle.kubernetes.common.logging.MessageKeys.WEBHOOK_CONFIG_NAMESPACE;
import static oracle.kubernetes.common.logging.MessageKeys.WEBHOOK_STARTED;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.operator.EventConstants.WEBHOOK_STARTUP_FAILED_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEventsWithCountOne;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER_CRD_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER_PLURAL;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_CRD_NAME;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_PLURAL;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_GATEWAY_TIMEOUT;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_INTERNAL_ERROR;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_UNAUTHORIZED;
import static oracle.kubernetes.operator.KubernetesConstants.WEBHOOK_NAMESPACE_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.WEBHOOK_POD_NAME_ENV;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.OperatorMain.GIT_BRANCH_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_BUILD_TIME_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_BUILD_VERSION_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_COMMIT_KEY;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CLUSTER;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.CUSTOM_RESOURCE_DEFINITION;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.VALIDATING_WEBHOOK_CONFIGURATION;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.helpers.WebhookHelper.CREATE;
import static oracle.kubernetes.operator.helpers.WebhookHelper.UPDATE;
import static oracle.kubernetes.operator.helpers.WebhookHelper.VALIDATING_WEBHOOK_NAME;
import static oracle.kubernetes.operator.helpers.WebhookHelper.VALIDATING_WEBHOOK_PATH;
import static oracle.kubernetes.operator.http.rest.RestConfigImpl.CONVERSION_WEBHOOK_HTTPS_PORT;
import static oracle.kubernetes.operator.tuning.TuningParameters.CRD_PRESENCE_FAILURE_RETRY_MAX_COUNT;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.WEBLOGIC_OPERATOR_WEBHOOK_SVC;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class WebhookMainTest extends CrdHelperTestBase {
  public static final VersionInfo TEST_VERSION_INFO = new VersionInfo().major("1").minor("18").gitVersion("0");
  public static final KubernetesVersion TEST_VERSION = new KubernetesVersion(TEST_VERSION_INFO);

  private static final String WEBHOOK_POD_NAME = "my-webhook-1234";
  private static final String WEBHOOK_NAMESPACE = "webhook-namespace";

  private static final String GIT_BUILD_VERSION = "3.1.0";
  private static final String GIT_BRANCH = "master";
  private static final String GIT_COMMIT = "a987654";
  private static final String GIT_BUILD_TIME = "Sep-10-2015";
  private static final String IMPL = GIT_BRANCH + "." + GIT_COMMIT;

  private static final Properties buildProperties;
  public static final String RESOURCE_VERSION = "123";
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final TestUtils.ConsoleHandlerMemento loggerControl = TestUtils.silenceOperatorLogger();
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private final WebhookMainDelegateStub delegate =
          createStrictStub(WebhookMainDelegateStub.class, testSupport);
  private final WebhookMain main = new WebhookMain(delegate);
  private static final InMemoryFileSystem inMemoryFileSystem = InMemoryFileSystem.createInstance();
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private final OnConflictRetryStrategyStub retryStrategy = createStrictStub(OnConflictRetryStrategyStub.class);

  private final String testNamespace = "ns1";
  private final byte[] testCaBundle = new byte[] { (byte)0xe0, 0x4f, (byte)0xd0,
      0x20, (byte)0xea, 0x3a, 0x69, 0x10, (byte)0xa2, (byte)0xd8, 0x08, 0x00, 0x2b,
      0x30, 0x30, (byte)0x9d };

  private V1ValidatingWebhookConfiguration testValidatingWebhookConfig;

  private V1ValidatingWebhook createValidatingWebhook() {
    return new V1ValidatingWebhook().clientConfig(createWebhookClientConfig());
  }

  private AdmissionregistrationV1WebhookClientConfig createWebhookClientConfig() {
    return new AdmissionregistrationV1WebhookClientConfig().service(createServiceReference());
  }

  private AdmissionregistrationV1ServiceReference createServiceReference() {
    return new AdmissionregistrationV1ServiceReference().namespace(getWebhookNamespace());
  }

  static {
    buildProperties = new PropertiesBuilder()
              .withProperty(GIT_BUILD_VERSION_KEY, GIT_BUILD_VERSION)
              .withProperty(GIT_BRANCH_KEY, GIT_BRANCH)
              .withProperty(GIT_COMMIT_KEY, GIT_COMMIT)
              .withProperty(GIT_BUILD_TIME_KEY, GIT_BUILD_TIME)
              .build();
  }

  private static class PropertiesBuilder {
    private final Properties properties = new Properties();

    private PropertiesBuilder withProperty(String name, String value) {
      properties.put(name, value);
      return this;
    }

    private Properties build() {
      return properties;
    }
  }

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(loggerControl.withLogLevel(Level.INFO)
        .collectLogMessages(logRecords,
            VALIDATING_WEBHOOK_CONFIGURATION_CREATED, VALIDATING_WEBHOOK_CONFIGURATION_REPLACED,
            CREATE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED, REPLACE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED,
            READ_VALIDATING_WEBHOOK_CONFIGURATION_FAILED));
    mementos.add(testSupport.install());
    mementos.add(TestStepFactory.install());
    mementos.add(HelmAccessStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(NoopWatcherStarter.install());
    mementos.add(inMemoryFileSystem.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(UnitTestHash.install());

    HelmAccessStub.defineVariable(WEBHOOK_NAMESPACE_ENV, WEBHOOK_NAMESPACE);
    HelmAccessStub.defineVariable(WEBHOOK_POD_NAME_ENV, WEBHOOK_POD_NAME);

    // this has to be done after WEBHOOK_NAMESPACE_ENV is defined in HelmAccessStub
    testValidatingWebhookConfig = new V1ValidatingWebhookConfiguration()
        .metadata(createNameOnlyMetadata())
        .addWebhooksItem(createValidatingWebhook());

    testSupport.addRetryStrategy(retryStrategy);
  }

  @AfterEach
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  void whenConversionWebhookCreated_logStartupMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, WEBHOOK_STARTED);

    WebhookMain.createMain(buildProperties);

    assertThat(logRecords,
        containsInfo(WEBHOOK_STARTED).withParams(GIT_BUILD_VERSION, IMPL, GIT_BUILD_TIME));
  }

  @Test
  void whenConversionWebhookCreated_logWebhookNamespace() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, WEBHOOK_CONFIG_NAMESPACE);

    WebhookMain.createMain(buildProperties);

    assertThat(logRecords, containsInfo(WEBHOOK_CONFIG_NAMESPACE).withParams(getWebhookNamespace()));
  }

  @Test
  void whenConversionWebhookCompleteBeginFailsWithException_failedEventIsGenerated() {
    InMemoryCertificates.defineWebhookCertificateFile("asdf");
    inMemoryFileSystem.defineFile("/deployment/webhook-identity/webhookKey", "asdf");
    loggerControl.ignoringLoggedExceptions(RuntimeException.class, NoSuchFileException.class);

    WebhookMain.createMain(buildProperties).completeBegin();

    MatcherAssert.assertThat("Found 1 WEBHOOK_START_FAILED_EVENT event with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            WEBHOOK_STARTUP_FAILED_EVENT, 1), is(true));
  }

  private void simulateMissingCRD(String resourceType) {
    testSupport.failOnResource(resourceType, null, getWebhookNamespace(), HttpURLConnection.HTTP_NOT_FOUND);
  }

  private void recheckCRD() {
    testSupport.runSteps(main.createCRDRecheckSteps());
  }

  @ParameterizedTest
  @ValueSource(strings = {DOMAIN, CLUSTER})
  void whenNoCRD_logReasonForFailure(String resourceType) {
    TuningParametersStub.setParameter(CRD_PRESENCE_FAILURE_RETRY_MAX_COUNT, "0");
    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD(resourceType);

    recheckCRD();

    assertThat(logRecords, containsSevere(CRD_NOT_INSTALLED));
  }

  @ParameterizedTest
  @ValueSource(strings = {DOMAIN, CLUSTER})
  void whenNoCRDOnFirstAttempt_severeMessageNotLoggedOnRerty(String resourceType) {
    TuningParametersStub.setParameter(CRD_PRESENCE_FAILURE_RETRY_MAX_COUNT, "1");
    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD(resourceType);

    recheckCRD();

    recheckCRD();

    assertThat(logRecords, not(containsSevere(CRD_NOT_INSTALLED)));
  }

  @Test
  void whenCRDsExist_resourceVersionsAreCached() {
    V1CustomResourceDefinition domainCrd = defineCrd(PRODUCT_VERSION, DOMAIN_CRD_NAME);
    Objects.requireNonNull(domainCrd.getMetadata()).resourceVersion(RESOURCE_VERSION);
    V1CustomResourceDefinition clusterCrd = defineCrd(PRODUCT_VERSION, CLUSTER_CRD_NAME);
    Objects.requireNonNull(clusterCrd.getMetadata()).resourceVersion(RESOURCE_VERSION);
    testSupport.defineResources(domainCrd, clusterCrd);

    recheckCRD();

    assertThat(delegate.getDomainCrdResourceVersion(), is(RESOURCE_VERSION));
    assertThat(delegate.getClusterCrdResourceVersion(), is(RESOURCE_VERSION));
  }

  @ParameterizedTest
  @ValueSource(strings = {DOMAIN, CLUSTER})
  void afterLoggedCRDMissing_dontDoItASecondTime(String resourceType) {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, WAIT_FOR_CRD_INSTALLATION);
    simulateMissingCRD(resourceType);
    recheckCRD();
    logRecords.clear();

    recheckCRD();

    assertThat(logRecords, not(containsSevere(WAIT_FOR_CRD_INSTALLATION)));
  }

  @ParameterizedTest
  @ValueSource(strings = {DOMAIN, CLUSTER})
  void afterMissingCRDcorrected_subsequentFailureLogsReasonForFailure(String resourceType) {
    TuningParametersStub.setParameter(CRD_PRESENCE_FAILURE_RETRY_MAX_COUNT, "0");
    simulateMissingCRD(resourceType);
    recheckCRD();
    testSupport.cancelFailures();
    recheckCRD();

    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD(resourceType);
    recheckCRD();

    assertThat(logRecords, containsSevere(CRD_NOT_INSTALLED));
  }

  @Test
  void whenValidatingWebhookCreated_logStartupMessage() {
    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME));
  }

  @Test
  void whenValidatingWebhookCreated_foundExpectedContents() {
    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getLabels(generatedConfiguration), hasEntry(CREATEDBYOPERATOR_LABEL, "true"));
    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getServiceName(generatedConfiguration), equalTo(WEBLOGIC_OPERATOR_WEBHOOK_SVC));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
    assertThat(getServicePort(generatedConfiguration), equalTo(CONVERSION_WEBHOOK_HTTPS_PORT));
    assertThat(getServicePath(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_PATH));
  }

  @Test
  void whenValidatingWebhookCreated_foundExpectedRuleContents() {
    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getRules(generatedConfiguration).size(), equalTo(2));
    assertThat(allRuleOperationsMatch(generatedConfiguration, Arrays.asList(CREATE, UPDATE)), equalTo(true));
    assertThat(oneRuleForDomain(generatedConfiguration), equalTo(true));
    assertThat(oneRuleForCluster(generatedConfiguration), equalTo(true));
  }


  @Test
  void afterWebhookCreated_domainAndClusterCrdsExist() {
    testSupport.runSteps(main.createStartupSteps());
    logRecords.clear();

    assertThat(getCreatedCrdNames(), containsInAnyOrder(DOMAIN_CRD_NAME, CLUSTER_CRD_NAME));
  }

  @Nonnull
  private List<String> getCreatedCrdNames() {
    return testSupport.<V1CustomResourceDefinition>getResources(CUSTOM_RESOURCE_DEFINITION).stream()
        .map(V1CustomResourceDefinition::getMetadata)
        .filter(Objects::nonNull)
        .map(V1ObjectMeta::getName)
        .collect(Collectors.toList());
  }

  @Test
  void whenValidatingWebhookReadFailed404_createIt() {
    testSupport.failOnRead(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_NOT_FOUND);

    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME));
  }

  @Test
  void whenValidatingWebhookReadFailed500_dontCreateIt() {
    testSupport.failOnRead(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_INTERNAL_ERROR);

    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), empty());
    assertThat(logRecords,
        not(containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME)));
    assertThat(logRecords,
        containsInfo(READ_VALIDATING_WEBHOOK_CONFIGURATION_FAILED).withParams(VALIDATING_WEBHOOK_NAME));
    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void whenValidatingWebhookReadFailed504_createItOnRetry() {
    testSupport.failOnRead(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_GATEWAY_TIMEOUT);

    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME));
  }

  @Test
  void whenValidatingWebhookReadFailed401_dontCreateIt() {
    testSupport.failOnRead(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_UNAUTHORIZED);

    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        containsInfo(READ_VALIDATING_WEBHOOK_CONFIGURATION_FAILED).withParams(VALIDATING_WEBHOOK_NAME));
  }

  @Test
  void whenValidatingWebhookCreatedAgainWithDifferentNamespace_replaceIt() {
    setServiceNamespace(testNamespace);
    testSupport.defineResources(testValidatingWebhookConfig);

    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
  }

  @Test
  void whenValidatingWebhookCreatedAgainWithSameNamespaceDifferentCABundle_replaceIt() {
    setServiceCaBundle(testCaBundle);
    testSupport.defineResources(testValidatingWebhookConfig);

    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getCaBundle(generatedConfiguration), not(equalTo(testCaBundle)));
    assertThat(getCreatedValidatingWebhookConfigurationCount(), equalTo(1));
  }

  @Test
  void whenValidatingWebhookCreatedAgainWithSameNamespaceSameCABundle_noLogMessagesFound() {
    testSupport.defineResources(testValidatingWebhookConfig);

    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(logRecords,
        not(containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME)));
    assertThat(logRecords,
        not(containsInfo(CREATE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED).withParams(VALIDATING_WEBHOOK_NAME)));
    assertThat(logRecords,
        not(containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_REPLACED).withParams(VALIDATING_WEBHOOK_NAME)));
    assertThat(logRecords,
        not(containsInfo(REPLACE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED).withParams(VALIDATING_WEBHOOK_NAME)));
  }

  @Test
  void whenValidatingWebhookCreatedAfterFailure504_logStartupFailedMessage() {
    testSupport.failOnCreate(VALIDATING_WEBHOOK_CONFIGURATION, null, HTTP_GATEWAY_TIMEOUT);

    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME));
    assertThat(logRecords,
        not(containsInfo(CREATE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED).withParams(VALIDATING_WEBHOOK_NAME)));
  }

  @Test
  void whenValidatingWebhookCreatedAfterFailure401_dontCreateIt() {
    testSupport.failOnCreate(VALIDATING_WEBHOOK_CONFIGURATION, null, HTTP_UNAUTHORIZED);

    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), empty());
    assertThat(logRecords,
        not(containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME)));
    assertThat(logRecords,
        containsInfo(CREATE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED).withParams(VALIDATING_WEBHOOK_NAME));
  }

  @Test
  void whenValidatingWebhookCreatedAfterFailure500_dontCreateIt() {
    testSupport.failOnCreate(VALIDATING_WEBHOOK_CONFIGURATION, null, HTTP_INTERNAL_ERROR);

    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        not(containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME)));
    assertThat(logRecords,
        containsInfo(CREATE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED).withParams(VALIDATING_WEBHOOK_NAME));
    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void whenValidatingWebhookCreatedAgainWithDifferentNamespaceAfterFailure504_replaceIt() {
    setServiceNamespace(testNamespace);
    testSupport.defineResources(testValidatingWebhookConfig);
    testSupport.failOnReplace(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_GATEWAY_TIMEOUT);

    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
  }

  @Test
  void whenValidatingWebhookCreatedAgainWithFailure401onReplace_dontReplaceItOnRetry() {
    setServiceCaBundle(testCaBundle);
    setServiceNamespace(testNamespace);
    testSupport.defineResources(testValidatingWebhookConfig);
    testSupport.failOnReplace(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_UNAUTHORIZED);

    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getCaBundle(generatedConfiguration), equalTo(testCaBundle));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(testNamespace));
    assertThat(getCreatedValidatingWebhookConfigurationCount(), equalTo(1));
  }

  @Test
  void whenValidatingWebhookCreatedAgainWithFailure404onReplace_replaceItOnRetry() {
    setServiceCaBundle(testCaBundle);
    setServiceNamespace(testNamespace);
    testSupport.defineResources(testValidatingWebhookConfig);
    testSupport.failOnReplace(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_NOT_FOUND);

    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getCaBundle(generatedConfiguration), not(equalTo(testCaBundle)));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
    assertThat(getCreatedValidatingWebhookConfigurationCount(), equalTo(1));
  }

  @Test
  void whenValidatingWebhookCreatedAgainWithFailure504onReplace_replaceItOnRetry() {
    setServiceCaBundle(testCaBundle);
    setServiceNamespace(testNamespace);
    testSupport.defineResources(testValidatingWebhookConfig);
    testSupport.failOnReplace(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_GATEWAY_TIMEOUT);

    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getCaBundle(generatedConfiguration), not(equalTo(testCaBundle)));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
    assertThat(getCreatedValidatingWebhookConfigurationCount(), equalTo(1));
  }

  @Test
  void whenValidatingWebhookCreatedAgainWithFailure500onReplace_dontReplaceItOnRetry() {
    setServiceCaBundle(testCaBundle);
    setServiceNamespace(testNamespace);
    testSupport.defineResources(testValidatingWebhookConfig);
    testSupport.failOnReplace(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, HTTP_INTERNAL_ERROR);

    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    assertThat(logRecords,
        not(containsInfo(REPLACE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED).withParams(VALIDATING_WEBHOOK_NAME)));
    testSupport.verifyCompletionThrowable(UnrecoverableCallException.class);
  }

  @Test
  void whenWebhookShutdown_deleteValidatingWebhookConfiguration() {
    setServiceCaBundle(testCaBundle);
    setServiceNamespace(testNamespace);
    testSupport.defineResources(testValidatingWebhookConfig);

    testSupport.runSteps(main.createShutdownSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(generatedConfiguration, nullValue());
  }

  @Test
  void whenWebhookShutdown_completionCallbackOccursBeforeFollowingLogic() {
    final List<String> callOrder = Collections.synchronizedList(new ArrayList<>());
    main.stopDeployment(() -> callOrder.add("completionCallback"));
    callOrder.add("afterStoppedDeployment");

    assertThat(callOrder, hasItems("completionCallback", "afterStoppedDeployment"));
  }

  @Test
  void whenShutdownMarkerIsCreated_stopWebhook() throws NoSuchFieldException {
    mementos.add(StaticStubSupport.install(
            BaseMain.class, "wrappedExecutorService", testSupport.getScheduledExecutorService()));
    inMemoryFileSystem.defineFile(delegate.getShutdownMarker(), "shutdown");
    testSupport.presetFixedDelay();

    main.waitForDeath();

    assertThat(main.getShutdownSignalAvailablePermits(), equalTo(0));
  }

  @Test
  void whenWebhookStopped_restServerShutdown() {
    WebhookMain m = WebhookMain.createMain(buildProperties);
    BaseServerStub restServer = new BaseServerStub();
    m.getRestServer().set(restServer);
    m.completeStop();
    assertThat(restServer.isStopCalled, is(true));
    assertThat(m.getRestServer().get(), nullValue());
  }

  private static class BaseServerStub extends BaseServer {
    private boolean isStopCalled = false;

    @Override
    public void start(Container container) throws UnrecoverableKeyException, CertificateException, IOException,
        NoSuchAlgorithmException, KeyStoreException, InvalidKeySpecException, KeyManagementException {
      // no-op
    }

    @Override
    public void stop() {
      isStopCalled = true;
    }

    @Override
    protected ResourceConfig createResourceConfig() {
      throw new IllegalStateException();
    }
  }

  private V1ObjectMeta createNameOnlyMetadata() {
    return new V1ObjectMeta().name(VALIDATING_WEBHOOK_NAME);
  }

  private String getName(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getMetadata(configuration)).map(V1ObjectMeta::getName).orElse("");
  }

  private Map<String, String> getLabels(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getMetadata(configuration)).map(V1ObjectMeta::getLabels).orElse(new HashMap<>());
  }

  private V1ObjectMeta getMetadata(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(configuration).map(V1ValidatingWebhookConfiguration::getMetadata).orElse(null);
  }

  private String getServiceNamespace(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getService(configuration))
        .map(AdmissionregistrationV1ServiceReference::getNamespace)
        .orElse("");
  }

  private Integer getServicePort(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getService(configuration))
        .map(AdmissionregistrationV1ServiceReference::getPort)
        .orElse(0);
  }

  private String getServicePath(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getService(configuration))
        .map(AdmissionregistrationV1ServiceReference::getPath)
        .orElse("");
  }

  @Nullable
  private V1ValidatingWebhook getFirstWebhook(V1ValidatingWebhookConfiguration configuration) {
    return (V1ValidatingWebhook) Optional.of(configuration)
        .map(V1ValidatingWebhookConfiguration::getWebhooks)
        .map(this::getFirstElement)
        .orElse(null);
  }

  private List<V1RuleWithOperations> getRules(@Nonnull V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getFirstWebhook(configuration))
        .map(V1ValidatingWebhook::getRules).orElse(Collections.emptyList());
  }

  private boolean allRuleOperationsMatch(V1ValidatingWebhookConfiguration configuration, List<String> operations) {
    return getRules(configuration).stream().allMatch(r -> areOperationsMatch(operations, r));
  }

  private boolean oneRuleForDomain(V1ValidatingWebhookConfiguration configuration) {
    return oneRuleFor(configuration, DOMAIN_PLURAL);
  }

  private boolean oneRuleForCluster(V1ValidatingWebhookConfiguration configuration) {
    return oneRuleFor(configuration, CLUSTER_PLURAL);
  }

  private boolean oneRuleFor(V1ValidatingWebhookConfiguration configuration, String resource) {
    return getMatchRules(configuration, resource).size() == 1;
  }

  @Nonnull
  private List<V1RuleWithOperations> getMatchRules(V1ValidatingWebhookConfiguration configuration, String resource) {
    return getRules(configuration).stream().filter(r -> isResourceEquals(resource, r)).collect(Collectors.toList());
  }

  private boolean areOperationsMatch(List<String> operations, V1RuleWithOperations r) {
    return Objects.equals(operations, getOperations(r));
  }

  private boolean isResourceEquals(String resource, V1RuleWithOperations r) {
    return resource.equals(getResource(r));
  }

  private List<String> getOperations(V1RuleWithOperations rule) {
    return Optional.ofNullable(rule)
        .map(V1RuleWithOperations::getOperations)
        .orElse(new ArrayList<>());
  }

  private String getResource(V1RuleWithOperations rule) {
    return (String) Optional.ofNullable(rule)
        .map(V1RuleWithOperations::getResources)
        .map(this::getFirstElement)
        .orElse(null);
  }

  private <T> Object getFirstElement(List<T> l) {
    return l.isEmpty() ? null : l.get(0);
  }

  private String getServiceName(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getService(configuration))
        .map(AdmissionregistrationV1ServiceReference::getName)
        .orElse("");
  }

  private AdmissionregistrationV1ServiceReference getService(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getClientConfig(configuration))
        .map(AdmissionregistrationV1WebhookClientConfig::getService).orElse(null);
  }

  private AdmissionregistrationV1WebhookClientConfig getClientConfig(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getFirstWebhook(configuration)).map(V1ValidatingWebhook::getClientConfig).orElse(null);
  }

  @Nullable
  private byte[] getCaBundle(V1ValidatingWebhookConfiguration configuration) {
    return Optional.ofNullable(getClientConfig(configuration))
        .map(AdmissionregistrationV1WebhookClientConfig::getCaBundle)
        .orElse(null);
  }

  @SuppressWarnings("SameParameterValue")
  private void setServiceNamespace(String ns) {
    Optional.ofNullable(getService(testValidatingWebhookConfig)).ifPresent(s -> s.namespace(ns));
  }

  private void setServiceCaBundle(byte[] caBundle) {
    Optional.ofNullable(getClientConfig(testValidatingWebhookConfig)).ifPresent(s -> s.caBundle(caBundle));
  }

  V1ValidatingWebhookConfiguration getCreatedValidatingWebhookConfiguration() {
    return (V1ValidatingWebhookConfiguration)
        getFirstElement(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION));
  }

  int getCreatedValidatingWebhookConfigurationCount() {
    return testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION).size();
  }

  public abstract static class WebhookMainDelegateStub implements WebhookMainDelegate {
    private final FiberTestSupport testSupport;
    private String domainCrdResourceVersion;
    private String clusterCrdResourceVersion;

    public WebhookMainDelegateStub(FiberTestSupport testSupport) {
      this.testSupport = testSupport;
    }

    @Override
    public void runSteps(Packet packet, Step firstStep, Runnable completionAction) {
      testSupport.withPacket(packet)
                 .withCompletionAction(completionAction)
                 .runSteps(firstStep);
    }

    @Override
    public KubernetesVersion getKubernetesVersion() {
      return TEST_VERSION;
    }

    @Override
    public SemanticVersion getProductVersion() {
      return SemanticVersion.TEST_VERSION;
    }

    @Override
    public File getDeploymentHome() {
      return new File("/deployment");
    }

    @Override
    public String getWebhookCertUri() {
      return SECRETS_WEBHOOK_CERT;
    }

    @Override
    public String getWebhookKeyUri() {
      return SECRETS_WEBHOOK_KEY;
    }

    @Override
    public String getDomainCrdResourceVersion() {
      return this.domainCrdResourceVersion;
    }

    @Override
    public void setDomainCrdResourceVersion(String resourceVersion) {
      this.domainCrdResourceVersion = resourceVersion;
    }

    @Override
    public String getClusterCrdResourceVersion() {
      return this.clusterCrdResourceVersion;
    }

    @Override
    public void setClusterCrdResourceVersion(String resourceVersion) {
      this.clusterCrdResourceVersion = resourceVersion;
    }

  }

  static class TestStepFactory implements WebhookMain.NextStepFactory {
    @SuppressWarnings("FieldCanBeLocal")
    private static TestStepFactory factory = new TestStepFactory();

    private static Memento install() throws NoSuchFieldException {
      factory = new TestStepFactory();
      return StaticStubSupport.install(WebhookMain.class, "nextStepFactory", factory);
    }

    @Override
    public Step createInitializationStep(WebhookMainDelegate delegate, Step next) {
      return next;
    }
  }

  public static Certificates getCertificates() {
    return new Certificates(new CoreDelegateImpl(buildProperties, null));
  }
}

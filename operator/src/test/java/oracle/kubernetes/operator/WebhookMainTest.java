// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.net.HttpURLConnection;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import io.kubernetes.client.openapi.models.AdmissionregistrationV1ServiceReference;
import io.kubernetes.client.openapi.models.AdmissionregistrationV1WebhookClientConfig;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1RuleWithOperations;
import io.kubernetes.client.openapi.models.V1ValidatingWebhook;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import io.kubernetes.client.openapi.models.VersionInfo;
import oracle.kubernetes.operator.builders.StubWatchFactory;
import oracle.kubernetes.operator.helpers.HelmAccessStub;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.helpers.KubernetesVersion;
import oracle.kubernetes.operator.helpers.OnConflictRetryStrategyStub;
import oracle.kubernetes.operator.helpers.SemanticVersion;
import oracle.kubernetes.operator.helpers.TuningParametersStub;
import oracle.kubernetes.operator.rest.RestConfig;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.steps.InitializeWebhookIdentityStep;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.FiberTestSupport;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.operator.work.ThreadFactorySingleton;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.junit.MatcherAssert;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.common.CommonConstants.SECRETS_WEBHOOK_CERT;
import static oracle.kubernetes.common.CommonConstants.SECRETS_WEBHOOK_KEY;
import static oracle.kubernetes.common.logging.MessageKeys.CONVERSION_WEBHOOK_STARTED;
import static oracle.kubernetes.common.logging.MessageKeys.CRD_NOT_INSTALLED;
import static oracle.kubernetes.common.logging.MessageKeys.VALIDATING_WEBHOOK_CONFIGURATION_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.WAIT_FOR_CRD_INSTALLATION;
import static oracle.kubernetes.common.logging.MessageKeys.WEBHOOK_CONFIG_NAMESPACE;
import static oracle.kubernetes.common.utils.LogMatcher.containsInfo;
import static oracle.kubernetes.common.utils.LogMatcher.containsSevere;
import static oracle.kubernetes.operator.EventConstants.CONVERSION_WEBHOOK_FAILED_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEventsWithCountOne;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.KubernetesConstants.WEBHOOK_NAMESPACE_ENV;
import static oracle.kubernetes.operator.KubernetesConstants.WEBHOOK_POD_NAME_ENV;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.OperatorMain.GIT_BRANCH_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_BUILD_TIME_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_BUILD_VERSION_KEY;
import static oracle.kubernetes.operator.OperatorMain.GIT_COMMIT_KEY;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.DOMAIN;
import static oracle.kubernetes.operator.helpers.KubernetesTestSupport.VALIDATING_WEBHOOK_CONFIGURATION;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.helpers.WebhookHelper.UPDATE;
import static oracle.kubernetes.operator.helpers.WebhookHelper.VALIDATING_WEBHOOK_NAME;
import static oracle.kubernetes.operator.helpers.WebhookHelper.VALIDATING_WEBHOOK_PATH;
import static oracle.kubernetes.operator.rest.RestConfigImpl.CONVERSION_WEBHOOK_HTTPS_PORT;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.WEBLOGIC_OPERATOR_WEBHOOK_SVC;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class WebhookMainTest extends ThreadFactoryTestBase {
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
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final TestUtils.ConsoleHandlerMemento loggerControl = TestUtils.silenceOperatorLogger();
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private final WebhookMainDelegateStub delegate =
          createStrictStub(WebhookMainDelegateStub.class, testSupport);
  private final WebhookMain main = new WebhookMain(delegate);
  private static final InMemoryFileSystem inMemoryFileSystem = InMemoryFileSystem.createInstance();
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Function<String, Path> getInMemoryPath = inMemoryFileSystem::getPath;
  private final OnConflictRetryStrategyStub retryStrategy = createStrictStub(OnConflictRetryStrategyStub.class);


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
    mementos.add(loggerControl);
    mementos.add(testSupport.install());
    mementos.add(TestStepFactory.install());
    mementos.add(HelmAccessStub.install());
    mementos.add(TuningParametersStub.install());
    mementos.add(StubWatchFactory.install());
    mementos.add(StaticStubSupport.install(ThreadFactorySingleton.class, "instance", this));
    mementos.add(NoopWatcherStarter.install());
    mementos.add(StaticStubSupport.install(InitializeWebhookIdentityStep.class, "getPath", getInMemoryPath));
    mementos.add(InMemoryCertificates.install());

    HelmAccessStub.defineVariable(WEBHOOK_NAMESPACE_ENV, WEBHOOK_NAMESPACE);
    HelmAccessStub.defineVariable(WEBHOOK_POD_NAME_ENV, WEBHOOK_POD_NAME);
  }

  @AfterEach
  public void tearDown() throws Exception {
    testSupport.throwOnCompletionFailure();

    mementos.forEach(Memento::revert);
  }

  @Test
  void whenConversionWebhookCreated_logStartupMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, CONVERSION_WEBHOOK_STARTED);

    WebhookMain.createMain(buildProperties);

    assertThat(logRecords,
               containsInfo(CONVERSION_WEBHOOK_STARTED).withParams(GIT_BUILD_VERSION, IMPL, GIT_BUILD_TIME));
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

    MatcherAssert.assertThat("Found 1 CONVERSION_FAILED_EVENT event with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            CONVERSION_WEBHOOK_FAILED_EVENT, 1), is(true));
  }

  private void simulateMissingCRD() {
    testSupport.failOnResource(DOMAIN, null, getWebhookNamespace(), HttpURLConnection.HTTP_NOT_FOUND);
  }

  private void recheckCRD() {
    testSupport.runSteps(main.createCRDRecheckSteps());
  }

  @Test
  void whenNoCRD_logReasonForFailure() {
    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD();

    recheckCRD();

    assertThat(logRecords, containsSevere(CRD_NOT_INSTALLED));
  }

  @Test
  void afterLoggedCRDMissing_dontDoItASecondTime() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, WAIT_FOR_CRD_INSTALLATION);
    simulateMissingCRD();
    recheckCRD();
    logRecords.clear();

    recheckCRD();

    assertThat(logRecords, not(containsSevere(WAIT_FOR_CRD_INSTALLATION)));
  }

  @Test
  void afterMissingCRDcorrected_subsequentFailureLogsReasonForFailure() {
    simulateMissingCRD();
    recheckCRD();
    testSupport.cancelFailures();
    recheckCRD();

    loggerControl.withLogLevel(Level.SEVERE).collectLogMessages(logRecords, CRD_NOT_INSTALLED);
    simulateMissingCRD();
    recheckCRD();

    assertThat(logRecords, containsSevere(CRD_NOT_INSTALLED));
  }

  @Test
  void whenValidatingWebhookCreated_logStartupMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, VALIDATING_WEBHOOK_CONFIGURATION_CREATED);

    WebhookMain main = new WebhookMain(delegate);
    main.startDeployment(null);

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME));
  }

  @Test
  void whenValidatingWebhookCreated_foundExpectedContents() {
    WebhookMain main = new WebhookMain(delegate);

    main.startDeployment(null);

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getLabels(generatedConfiguration), hasEntry(CREATEDBYOPERATOR_LABEL, "true"));
    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getRuleOperation(generatedConfiguration), equalTo(UPDATE));
    assertThat(getServiceName(generatedConfiguration), equalTo(WEBLOGIC_OPERATOR_WEBHOOK_SVC));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
    assertThat(getServicePort(generatedConfiguration), equalTo(CONVERSION_WEBHOOK_HTTPS_PORT));
    assertThat(getServicePath(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_PATH));
  }

  @Test
  void whenValidatingWebhookCreatedWithClientServiceDifferentNamespace_replaceIt() {
    V1ValidatingWebhookConfiguration resource
        = new V1ValidatingWebhookConfiguration().metadata(createNameOnlyMetadata(VALIDATING_WEBHOOK_NAME))
        .addWebhooksItem(new V1ValidatingWebhook().clientConfig(new AdmissionregistrationV1WebhookClientConfig()
            .service(new AdmissionregistrationV1ServiceReference().namespace("ns1"))));
    testSupport.defineResources(resource);

    WebhookMain main = new WebhookMain(delegate);

    main.startDeployment(null);

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
  }

  @Test
  void whenValidatingWebhookCreatedAfterFailure401_logStartupMessage() {
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, VALIDATING_WEBHOOK_CONFIGURATION_CREATED);

    testSupport.failOnCreate(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, 401);
    WebhookMain main = new WebhookMain(delegate);
    main.startDeployment(null);

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME));
  }

  @Test
  void whenValidatingWebhookCreatedWithClientServiceDifferentNamespaceAfterFailure401_replaceIt() {
    V1ValidatingWebhookConfiguration resource
        = new V1ValidatingWebhookConfiguration().metadata(createNameOnlyMetadata(VALIDATING_WEBHOOK_NAME))
        .addWebhooksItem(new V1ValidatingWebhook().clientConfig(new AdmissionregistrationV1WebhookClientConfig()
            .service(new AdmissionregistrationV1ServiceReference().namespace("ns1"))));
    testSupport.defineResources(resource);
    testSupport.failOnReplace(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, 401);

    WebhookMain main = new WebhookMain(delegate);

    main.startDeployment(null);

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
  }

  @Test
  void whenValidatingWebhookCreatedAfterFailure400_logStartupMessage() {
    testSupport.addRetryStrategy(retryStrategy);
    loggerControl.withLogLevel(Level.INFO).collectLogMessages(logRecords, VALIDATING_WEBHOOK_CONFIGURATION_CREATED);

    testSupport.failOnCreate(VALIDATING_WEBHOOK_CONFIGURATION, null, 400);

    WebhookMain main = new WebhookMain(delegate);
    testSupport.runSteps(main.createStartupSteps());

    assertThat(testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION), notNullValue());
    assertThat(logRecords,
        containsInfo(VALIDATING_WEBHOOK_CONFIGURATION_CREATED).withParams(VALIDATING_WEBHOOK_NAME));
  }

  @Test
  void whenValidatingWebhookCreatedWithClientServiceDifferentNamespaceAfterFailure404_replaceIt() {
    testSupport.addRetryStrategy(retryStrategy);
    V1ValidatingWebhookConfiguration resource
        = new V1ValidatingWebhookConfiguration().metadata(createNameOnlyMetadata(VALIDATING_WEBHOOK_NAME))
        .addWebhooksItem(new V1ValidatingWebhook().clientConfig(new AdmissionregistrationV1WebhookClientConfig()
            .service(new AdmissionregistrationV1ServiceReference().namespace("ns1"))));
    testSupport.defineResources(resource);
    testSupport.failOnReplace(VALIDATING_WEBHOOK_CONFIGURATION, VALIDATING_WEBHOOK_NAME, null, 404);

    WebhookMain main = new WebhookMain(delegate);
    testSupport.runSteps(main.createStartupSteps());

    logRecords.clear();
    V1ValidatingWebhookConfiguration generatedConfiguration = getCreatedValidatingWebhookConfiguration();

    assertThat(getName(generatedConfiguration), equalTo(VALIDATING_WEBHOOK_NAME));
    assertThat(getServiceNamespace(generatedConfiguration), equalTo(getWebhookNamespace()));
  }

  private V1ObjectMeta createNameOnlyMetadata(String name) {
    return new V1ObjectMeta().name(name);
  }

  @Nullable
  private String getName(V1ValidatingWebhookConfiguration configuration) {
    return configuration.getMetadata().getName();
  }

  @Nullable
  private Map<String, String> getLabels(V1ValidatingWebhookConfiguration configuration) {
    return configuration.getMetadata().getLabels();
  }

  @Nullable
  private String getServiceNamespace(V1ValidatingWebhookConfiguration configuration) {
    return Optional.of(getFirstWebhook(configuration)).map(V1ValidatingWebhook::getClientConfig)
        .map(AdmissionregistrationV1WebhookClientConfig::getService)
        .map(AdmissionregistrationV1ServiceReference::getNamespace)
        .orElse("");
  }

  @Nullable
  private Integer getServicePort(V1ValidatingWebhookConfiguration configuration) {
    return Optional.of(getFirstWebhook(configuration)).map(V1ValidatingWebhook::getClientConfig)
        .map(AdmissionregistrationV1WebhookClientConfig::getService)
        .map(AdmissionregistrationV1ServiceReference::getPort)
        .orElse(0);
  }

  @Nullable
  private String getServicePath(V1ValidatingWebhookConfiguration configuration) {
    return Optional.of(getFirstWebhook(configuration)).map(V1ValidatingWebhook::getClientConfig)
        .map(AdmissionregistrationV1WebhookClientConfig::getService)
        .map(AdmissionregistrationV1ServiceReference::getPath)
        .orElse("");
  }

  @Nullable
  private V1ValidatingWebhook getFirstWebhook(V1ValidatingWebhookConfiguration configuration) {
    return Optional.of(configuration).map(V1ValidatingWebhookConfiguration::getWebhooks).get().get(0);
  }

  @Nullable
  private V1RuleWithOperations getFirstRule(V1ValidatingWebhookConfiguration configuration) {
    return Optional.of(getFirstWebhook(configuration)).map(V1ValidatingWebhook::getRules).get().get(0);
  }

  @Nullable
  private String getRuleOperation(V1ValidatingWebhookConfiguration configuration) {
    return Optional.of(getFirstRule(configuration)).map(V1RuleWithOperations::getOperations).get().get(0);
  }

  @Nullable
  private String getServiceName(V1ValidatingWebhookConfiguration configuration) {
    return Optional.of(getFirstWebhook(configuration)).map(V1ValidatingWebhook::getClientConfig)
        .map(AdmissionregistrationV1WebhookClientConfig::getService)
        .map(AdmissionregistrationV1ServiceReference::getName)
        .orElse("");
  }

  V1ValidatingWebhookConfiguration getCreatedValidatingWebhookConfiguration() {
    return (V1ValidatingWebhookConfiguration)
        testSupport.getResources(VALIDATING_WEBHOOK_CONFIGURATION).get(0);
  }

  public abstract static class WebhookMainDelegateStub implements WebhookMainDelegate {
    private final FiberTestSupport testSupport;

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

  static class RestConfigStub implements RestConfig {
    private final Certificates certificates;

    RestConfigStub(Certificates certificates) {
      this.certificates = certificates;
    }

    @Override
    public String getHost() {
      return null;
    }

    @Override
    public int getExternalHttpsPort() {
      return 0;
    }

    @Override
    public int getInternalHttpsPort() {
      return 0;
    }

    @Override
    public int getWebhookHttpsPort() {
      return 0;
    }

    @Override
    public String getOperatorExternalCertificateData() {
      return null;
    }

    @Override
    public String getOperatorInternalCertificateData() {
      return null;
    }

    @Override
    public String getOperatorExternalCertificateFile() {
      return null;
    }

    @Override
    public String getOperatorInternalCertificateFile() {
      return null;
    }

    @Override
    public String getOperatorExternalKeyData() {
      return null;
    }

    @Override
    public String getOperatorInternalKeyData() {
      return null;
    }

    @Override
    public String getOperatorExternalKeyFile() {
      return null;
    }

    @Override
    public String getOperatorInternalKeyFile() {
      return null;
    }

    @Override
    public RestBackend getBackend(String accessToken) {
      return null;
    }

    @Override
    public String getWebhookCertificateData() {
      return certificates.getWebhookCertificateData();
    }

    @Override
    public String getWebhookCertificateFile() {
      return null;
    }

    @Override
    public String getWebhookKeyData() {
      return null;
    }

    @Override
    public String getWebhookKeyFile() {
      return certificates.getWebhookKeyFilePath();
    }

  }
}

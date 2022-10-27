// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nonnull;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import com.meterware.simplestub.Stub;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.kubernetes.operator.WebhookMain;
import oracle.kubernetes.operator.WebhookMainTest;
import oracle.kubernetes.operator.helpers.KubernetesTestSupport;
import oracle.kubernetes.operator.tuning.TuningParametersStub;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.utils.InMemoryCertificates;
import oracle.kubernetes.operator.utils.InMemoryFileSystem;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.utils.SystemClockTestSupport;
import oracle.kubernetes.utils.TestUtils;
import org.hamcrest.Matchers;
import org.hamcrest.junit.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.meterware.simplestub.Stub.createStrictStub;
import static oracle.kubernetes.operator.EventConstants.WEBHOOK_STARTUP_FAILED_EVENT;
import static oracle.kubernetes.operator.EventTestUtils.containsEventsWithCountOne;
import static oracle.kubernetes.operator.EventTestUtils.getEvents;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.DEFAULT_NAMESPACE;
import static oracle.kubernetes.operator.steps.InitializeWebhookIdentityStep.WEBHOOK_KEY;
import static oracle.kubernetes.operator.steps.InitializeWebhookIdentityStep.WEBHOOK_SECRETS;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.WEBHOOK_CERTIFICATE;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class InitializeWebhookIdentityStepTest {
  private final List<Memento> mementos = new ArrayList<>();
  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();

  private final WebhookMainTest.WebhookMainDelegateStub delegate =
      createStrictStub(WebhookMainTest.WebhookMainDelegateStub.class, testSupport);
  private final Step initializeWebhookIdentityStep = new InitializeWebhookIdentityStep(delegate,
      new WebhookMain.CheckFailureAndCreateEventStep());

  private static final InMemoryFileSystem inMemoryFileSystem = InMemoryFileSystem.createInstance();
  private static final Function<String, Path> getInMemoryPath = inMemoryFileSystem::getPath;

  @BeforeEach
  void setup() throws NoSuchFieldException {
    mementos.add(TestUtils.silenceOperatorLogger());

    mementos.add(testSupport.install());

    mementos.add(SystemClockTestSupport.installClock());
    mementos.add(TuningParametersStub.install());
    mementos.add(InMemoryCertificates.install());
    mementos.add(inMemoryFileSystem.install());
    mementos.add(SSlIdentityFactoryStub.install());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void whenNoWebhookIdentity_verifyIdentityIsInitialized() {
    Certificates certificates = new Certificates(delegate);

    testSupport.runSteps(initializeWebhookIdentityStep);

    MatcherAssert.assertThat(certificates.getWebhookCertificateData(), Matchers.notNullValue());
    MatcherAssert.assertThat(certificates.getWebhookKeyFilePath(),
        equalTo("/deployment/webhook-identity/webhookKey"));
  }

  @Test
  void whenWebhookIdentityExistsInFileSystemAndWritePermissionMissing_verifyFailedEventGenerated() {
    inMemoryFileSystem.defineFile("/deployment/secrets/webhookCert", "xyz");
    inMemoryFileSystem.defineFile("/deployment/secrets/webhookKey", "xyz");

    testSupport.runSteps(initializeWebhookIdentityStep);

    MatcherAssert.assertThat("Found 1 WEBHOOK_STARTUP_FAILED_EVENT event with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            WEBHOOK_STARTUP_FAILED_EVENT, 1), is(true));
  }

  @Test
  void whenWebhookIdentityExistsInConfigMapAndWritePermissionMissing_verifyFailedEventGenerated() {
    Map<String, byte[]> data = new HashMap<>();
    data.put(WEBHOOK_KEY, "xyz".getBytes());
    data.put(WEBHOOK_CERTIFICATE, "xyz".getBytes());
    V1Secret secret = new V1Secret().metadata(createSecretMetadata()).data(data);
    testSupport.defineResources(secret);
    testSupport.failOnResource(KubernetesTestSupport.SECRET, WEBHOOK_SECRETS, "TEST", 409);

    testSupport.runSteps(initializeWebhookIdentityStep);

    MatcherAssert.assertThat("Found 1 WEBHOOK_STARTUP_FAILED_EVENT event with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            WEBHOOK_STARTUP_FAILED_EVENT, 1), is(true));
  }

  @Test
  void whenNoWebhookIdentityAndReplaceFailsWithConflict_verifyIdentityIsInitialized() {
    Map<String, byte[]> data = new HashMap<>();
    V1Secret secret = new V1Secret().metadata(createSecretMetadata()).data(data);
    testSupport.defineResources(secret);
    testSupport.failOnReplace(KubernetesTestSupport.SECRET, WEBHOOK_SECRETS, DEFAULT_NAMESPACE, 409);

    Certificates certificates = new Certificates(delegate);

    testSupport.runSteps(initializeWebhookIdentityStep);

    MatcherAssert.assertThat(certificates.getWebhookCertificateData(), Matchers.notNullValue());
    MatcherAssert.assertThat(certificates.getWebhookKeyFilePath(),
        equalTo("/deployment/webhook-identity/webhookKey"));
  }

  @Test
  void whenWebhookIdentityExistsInConfigMapAndConflictError_verifyFailedEventGenerated() {
    Map<String, byte[]> data = new HashMap<>();
    data.put(WEBHOOK_KEY, "xyz".getBytes());
    data.put(WEBHOOK_CERTIFICATE, "xyz".getBytes());
    V1Secret secret = new V1Secret().metadata(createSecretMetadata()).data(data);
    testSupport.defineResources(secret);
    testSupport.failOnReplace(KubernetesTestSupport.SECRET, WEBHOOK_SECRETS, DEFAULT_NAMESPACE, 409);
    inMemoryFileSystem.defineFile("/deployment/secrets/webhookCert", "xyz");
    inMemoryFileSystem.defineFile("/deployment/secrets/webhookKey", "xyz");

    testSupport.runSteps(initializeWebhookIdentityStep);

    MatcherAssert.assertThat("Found 1 WEBHOOK_STARTUP_FAILED_EVENT event with expected count 1",
        containsEventsWithCountOne(getEvents(testSupport),
            WEBHOOK_STARTUP_FAILED_EVENT, 1), is(true));
  }

  @Test
  void whenNoWebhookIdentityAndReplaceFailsWith500Error_verifyIdentityIsInitialized() {
    Map<String, byte[]> data = new HashMap<>();
    V1Secret secret = new V1Secret().metadata(createSecretMetadata()).data(data);
    testSupport.defineResources(secret);
    testSupport.failOnReplace(KubernetesTestSupport.SECRET, WEBHOOK_SECRETS, DEFAULT_NAMESPACE, 500);

    Certificates certificates = new Certificates(delegate);

    testSupport.runSteps(initializeWebhookIdentityStep);

    MatcherAssert.assertThat(certificates.getWebhookCertificateData(), Matchers.notNullValue());
    MatcherAssert.assertThat(certificates.getWebhookKeyFilePath(),
        equalTo("/deployment/webhook-identity/webhookKey"));
  }

  private V1ObjectMeta createSecretMetadata() {
    return new V1ObjectMeta().name(WEBHOOK_SECRETS).namespace(DEFAULT_NAMESPACE);
  }

  static class SSlIdentityFactoryStub implements SslIdentityFactory {

    static Memento install() throws NoSuchFieldException {
      return StaticStubSupport.install(
          InitializeWebhookIdentityStep.class, "identityFactory", new SSlIdentityFactoryStub());
    }

    @Nonnull
    @Override
    public KeyPair createKeyPair() {
      return new KeyPair(Stub.createNiceStub(PublicKey.class), Stub.createNiceStub(PrivateKey.class));
    }

    @Override
    public String convertToPEM(Object object) throws IOException {
      return Integer.toString(object.hashCode());
    }

    @Override
    public X509Certificate createCertificate(String name, KeyPair keyPair) {
      return Stub.createNiceStub(X509Certificate.class);
    }
  }
}

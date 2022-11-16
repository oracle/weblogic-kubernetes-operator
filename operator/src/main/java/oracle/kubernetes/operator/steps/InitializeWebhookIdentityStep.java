// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.kubernetes.operator.WebhookMainDelegate;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.utils.PathSupport;
import oracle.kubernetes.operator.utils.SelfSignedCertUtils;
import oracle.kubernetes.operator.work.Component;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.OperatorCreationException;

import static oracle.kubernetes.common.logging.MessageKeys.WEBHOOK_IDENTITY_INITIALIZATION_FAILED;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.WEBHOOK_CERTIFICATE;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.generateCertificate;

public class InitializeWebhookIdentityStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  static final String WEBHOOK_SECRETS = "weblogic-webhook-secrets";
  private static final String SHA_256_WITH_RSA = "SHA256withRSA";
  private static final String COMMON_NAME = "weblogic-webhook";
  private static final int CERTIFICATE_VALIDITY_DAYS = 3650;
  public static final String WEBHOOK_KEY = "webhookKey";
  public static final String EXCEPTION = "Exception";
  // allow unit tests to set this
  @SuppressWarnings("FieldMayBeFinal") // allow unit tests to set this
  private static SslIdentityFactory identityFactory = new SslIdentityFactoryImpl();

  private final File webhookCertFile;
  private final File webhookKeyFile;
  private final File certFile;
  private final File keyFile;

  /**
   * Constructor for the InitializeWebhookIdentityStep.
   * @param next Next step to be executed.
   */
  public InitializeWebhookIdentityStep(WebhookMainDelegate delegate, Step next) {
    super(next);
    Certificates certificates = new Certificates(delegate);
    this.webhookCertFile = certificates.getWebhookCertificateFile();
    this.webhookKeyFile = certificates.getWebhookKeyFile();
    this.certFile = new File(delegate.getDeploymentHome(), delegate.getWebhookCertUri());
    this.keyFile = new File(delegate.getDeploymentHome(), delegate.getWebhookKeyUri());
  }

  @Override
  public NextAction apply(Packet packet) {
    try {
      if (isWebHoodSslIdentityAlreadyCreated()) {
        reuseIdentity();
        return doNext(getNext(), packet);
      } else {
        return createIdentity(packet);
      }
    } catch (IdentityInitializationException | IOException e) {
      LOGGER.warning(WEBHOOK_IDENTITY_INITIALIZATION_FAILED, e.toString());
      packet.getComponents().put(EXCEPTION, Component.createFor(Exception.class, e));
      return doNext(getNext(), packet);
    }
  }

  private boolean isWebHoodSslIdentityAlreadyCreated() {
    return isFileExists(certFile) && isFileExists(keyFile);
  }

  private static boolean isFileExists(File file) {
    return Files.isRegularFile(PathSupport.getPath(file));
  }

  private void reuseIdentity() throws IOException {
    // copy the certificate and key from the webhook's secret
    // to the locations the webhook runtime expects
    FileUtils.copyFile(certFile, webhookCertFile);
    FileUtils.copyFile(keyFile, webhookKeyFile);
  }

  private NextAction createIdentity(Packet packet) throws IdentityInitializationException {
    try {
      final KeyPair keyPair = identityFactory.createKeyPair();
      final String key = identityFactory.convertToPEM(keyPair.getPrivate());
      writeToFile(key, webhookKeyFile);
      X509Certificate cert = identityFactory.createCertificate(webhookCertFile.getName(), keyPair);
      String certString = getBase64Encoded(cert);
      writeToFile(certString, webhookCertFile);
      // put the new certificate and key in the webhook's secret so that it will be available
      // the next time the webhook is started
      return doNext(recordWebhookIdentity(new WebhookIdentity(key, certString.getBytes()), getNext()), packet);
    } catch (Exception e) {
      throw new IdentityInitializationException(e);
    }
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  private static void writeToFile(String content, File path) throws IOException {
    path.getParentFile().mkdirs();
    try (Writer wr = Files.newBufferedWriter(PathSupport.getPath(path))) {
      wr.write(content);
      wr.flush();
    }
  }

  private static String getBase64Encoded(X509Certificate cert) throws IOException {
    return Base64.getEncoder().encodeToString(identityFactory.convertToPEM(cert).getBytes());
  }

  private Step recordWebhookIdentity(WebhookIdentity webhookIdentity, Step next) {
    return new CallBuilder().readSecretAsync(WEBHOOK_SECRETS,
            getWebhookNamespace(), readSecretResponseStep(next, webhookIdentity));
  }

  private ResponseStep<V1Secret> readSecretResponseStep(Step next, WebhookIdentity webhookIdentity) {
    return new ReadSecretResponseStep(next, webhookIdentity);
  }

  private class ReadSecretResponseStep extends DefaultResponseStep<V1Secret> {
    final WebhookIdentity webhookIdentity;

    ReadSecretResponseStep(Step next, WebhookIdentity webhookIdentity) {
      super(next);
      this.webhookIdentity = webhookIdentity;
    }

    private Step createSecret(Step next, WebhookIdentity webhookIdentity) {
      return new CallBuilder()
          .createSecretAsync(getWebhookNamespace(),
              createModel(null, webhookIdentity),
              new DefaultResponseStep<>(next));
    }

    private Step replaceSecret(Step next, V1Secret secret, WebhookIdentity webhookIdentity) {
      return new CallBuilder()
          .replaceSecretAsync(WEBHOOK_SECRETS, getWebhookNamespace(), createModel(secret, webhookIdentity),
              new ReplaceSecretResponseStep(webhookIdentity, next));
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Secret> callResponse) {
      V1Secret existingSecret = callResponse.getResult();
      Map<String, byte[]> data = Optional.ofNullable(existingSecret).map(V1Secret::getData).orElse(new HashMap<>());
      if (existingSecret == null) {
        return doNext(createSecret(getNext(), webhookIdentity), packet);
      } else if (identityExists(data)) {
        try {
          reuseExistingIdentity(data);
        } catch (Exception e) {
          LOGGER.severe(WEBHOOK_IDENTITY_INITIALIZATION_FAILED, e.toString());
          packet.getComponents().put(EXCEPTION, Component.createFor(Exception.class, e));
        }
        return doNext(getNext(), packet);
      }
      return doNext(replaceSecret(getNext(), existingSecret, webhookIdentity), packet);
    }

    private boolean identityExists(Map<String, byte[]> data) {
      return data.get(WEBHOOK_KEY) != null && data.get(WEBHOOK_CERTIFICATE) != null;
    }

    private void reuseExistingIdentity(Map<String, byte[]> data) throws IOException {
      Files.write(webhookKeyFile.toPath(), data.get(WEBHOOK_KEY));
      Files.write(webhookCertFile.toPath(), data.get(WEBHOOK_CERTIFICATE));
    }
  }

  private static class SslIdentityFactoryImpl implements SslIdentityFactory {

    @Override
    @Nonnull
    public KeyPair createKeyPair() throws NoSuchAlgorithmException, InvalidKeySpecException {
      return SelfSignedCertUtils.createKeyPair();
    }

    @Override
    public String convertToPEM(Object object) throws IOException {
      StringWriter writer = new StringWriter();
      try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
        pemWriter.writeObject(object);
        pemWriter.flush();
      }
      return writer.toString();
    }

    @Override
    public X509Certificate createCertificate(String name, KeyPair keyPair)
        throws OperatorCreationException, CertificateException, CertIOException {
      return generateCertificate(name, keyPair, SHA_256_WITH_RSA, COMMON_NAME, CERTIFICATE_VALIDITY_DAYS);
    }
  }

  private class ReplaceSecretResponseStep extends DefaultResponseStep<V1Secret> {
    WebhookIdentity webhookIdentity;

    ReplaceSecretResponseStep(WebhookIdentity webhookIdentity, Step next) {
      super(next);
      this.webhookIdentity = webhookIdentity;
    }

    @Override
    public NextAction onFailure(Packet packet, CallResponse<V1Secret> callResponse) {
      if (UnrecoverableErrorBuilder.isAsyncCallConflictFailure(callResponse)) {
        return doNext(Step.chain(readSecretResponseStep(getNext(), webhookIdentity), getNext()), packet);
      } else {
        return super.onFailure(packet, callResponse);
      }
    }
  }

  protected static V1Secret createModel(V1Secret secret, WebhookIdentity webhookIdentity) {
    if (secret == null) {
      Map<String, byte[]> data = new HashMap<>();
      data.put(WEBHOOK_KEY, webhookIdentity.getWebhookKey().getBytes());
      data.put(WEBHOOK_CERTIFICATE, webhookIdentity.getWebhookCert());
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(createMetadata()).data(data);
    } else {
      Map<String, byte[]> data = Optional.ofNullable(secret.getData()).orElse(new HashMap<>());
      data.put(WEBHOOK_KEY, webhookIdentity.getWebhookKey().getBytes());
      data.put(WEBHOOK_CERTIFICATE, webhookIdentity.getWebhookCert());
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(secret.getMetadata()).data(data);
    }
  }

  private static V1ObjectMeta createMetadata() {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.webhookName", getWebhookNamespace());
    return new V1ObjectMeta().name(WEBHOOK_SECRETS).namespace(getWebhookNamespace())
            .labels(labels);
  }

  static final class WebhookIdentity {

    private final String webhookKey;
    private final byte[] webhookCert;

    public WebhookIdentity(String webhookKey, byte[] webhookCert) {
      this.webhookKey = webhookKey;
      this.webhookCert = webhookCert;
    }

    public String getWebhookKey() {
      return webhookKey;
    }

    public byte[] getWebhookCert() {
      return webhookCert;
    }
  }

  public static class IdentityInitializationException extends Exception {
    public IdentityInitializationException(Exception e) {
      super(e);
    }
  }
}

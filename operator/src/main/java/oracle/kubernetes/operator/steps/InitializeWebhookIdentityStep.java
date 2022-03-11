// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import jakarta.json.Json;
import jakarta.json.JsonPatchBuilder;
import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.logging.MessageKeys.WEBHOOK_IDENTITY_INITIALIZATION_FAILED;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.createKeyPair;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.generateCertificate;

public class InitializeWebhookIdentityStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  private static final String WEBHOOK_CM = "weblogic-webhook-cm";
  private static final String WEBHOOK_SECRETS = "weblogic-webhook-secrets";
  private static final String SHA_256_WITH_RSA = "SHA256withRSA";
  private static final String COMMON_NAME = "weblogic-webhook";
  private static final int CERTIFICATE_VALIDITY_DAYS = 3650;
  public static final String WEBHOOK_KEY = "webhookKey";

  private final File internalCertFile;
  private final File internalKeyFile;
  private final File certFile;
  private final File keyFile;

  /**
   * Constructor for the InitializeWebhookIdentityStep.
   * @param next Next step to be executed.
   */
  public InitializeWebhookIdentityStep(CoreDelegate delegate, Step next) {
    super(next);
    Certificates certificates = new Certificates(delegate);
    this.internalCertFile = certificates.getWebhookCertificateFile();
    this.internalKeyFile = certificates.getWebhookKeyFile();
    this.certFile = new File(delegate.getDeploymentHome(), "/config/webhookCert");
    this.keyFile = new File(delegate.getDeploymentHome(), "/secrets/webhookKey");

  }

  @Override
  public NextAction apply(Packet packet) {
    try {
      if (certFile.exists() && keyFile.exists()) {
        // The webhook's ssl identity has already been created.
        reuseIdentity();
        return doNext(getNext(), packet);
      } else {
        // The webhook's ssl identity hasn't been created yet.
        return createIdentity(packet);
      }
    } catch (Exception e) {
      LOGGER.warning(WEBHOOK_IDENTITY_INITIALIZATION_FAILED, e.toString());
      throw new RuntimeException(e);
    }
  }

  private void reuseIdentity() throws IOException {
    // copy the certificate and key from the webhook's config map and secret
    // to the locations the webhook runtime expects
    FileUtils.copyFile(certFile, internalCertFile);
    FileUtils.copyFile(keyFile, internalKeyFile);
  }

  private NextAction createIdentity(Packet packet) throws Exception {
    KeyPair keyPair = createKeyPair();
    String key = convertToPEM(keyPair.getPrivate());
    writeToFile(key, internalKeyFile);
    X509Certificate cert = generateCertificate(internalCertFile.getName(), keyPair, SHA_256_WITH_RSA, COMMON_NAME,
            CERTIFICATE_VALIDITY_DAYS);
    writeToFile(getBase64Encoded(cert), internalCertFile);
    // put the new certificate in the webhook's config map so that it will be available
    // the next time the webhook is started
    return doNext(recordWebhookCert(internalCertFile.getName(), cert,
            recordWebhookKey(key, getNext())), packet);
  }

  private static String convertToPEM(Object object) throws IOException {
    StringWriter writer = new StringWriter();
    try (JcaPEMWriter pemWriter = new JcaPEMWriter(writer)) {
      pemWriter.writeObject(object);
      pemWriter.flush();
    }
    return writer.toString();
  }

  private static void writeToFile(String content, File path) throws IOException {
    path.getParentFile().mkdirs();
    try (Writer wr = Files.newBufferedWriter(path.toPath())) {
      wr.write(content);
      wr.flush();
    }
  }

  private static String getBase64Encoded(X509Certificate cert) throws IOException {
    return Base64.getEncoder().encodeToString(convertToPEM(cert).getBytes());
  }

  private static Step recordWebhookCert(String certName, X509Certificate cert, Step next) throws IOException {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/data/webhookCert", getBase64Encoded(cert));
    return new CallBuilder()
            .patchConfigMapAsync(WEBHOOK_CM, getWebhookNamespace(),
                    null,
                    new V1Patch(patchBuilder.build().toString()), new DefaultResponseStep<>(next));
  }

  private static Step recordWebhookKey(String key, Step next) {
    return new CallBuilder().readSecretAsync(WEBHOOK_SECRETS,
            getWebhookNamespace(), readSecretResponseStep(next, key));
  }

  private static ResponseStep<V1Secret> readSecretResponseStep(Step next, String webhookKey) {
    return new ReadSecretResponseStep(next, webhookKey);
  }

  private static class ReadSecretResponseStep extends DefaultResponseStep<V1Secret> {
    final String webhookKey;

    ReadSecretResponseStep(Step next, String webhookKey) {
      super(next);
      this.webhookKey = webhookKey;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Secret> callResponse) {
      V1Secret existingSecret = callResponse.getResult();
      if (existingSecret == null) {
        return doNext(createSecret(getNext(), webhookKey), packet);
      } else {
        return doNext(replaceSecret(getNext(), existingSecret, webhookKey), packet);
      }
    }
  }

  private static Step createSecret(Step next, String webhookKey) {
    return new CallBuilder()
            .createSecretAsync(getWebhookNamespace(),
                    createModel(null, webhookKey), new DefaultResponseStep<>(next));
  }

  private static Step replaceSecret(Step next, V1Secret secret, String webhookKey) {
    return new CallBuilder()
            .replaceSecretAsync(WEBHOOK_SECRETS, getWebhookNamespace(), createModel(secret, webhookKey),
                    new DefaultResponseStep<>(next));
  }

  protected static final V1Secret createModel(V1Secret secret, String webhookKey) {
    if (secret == null) {
      Map<String, byte[]> data = new HashMap<>();
      data.put(WEBHOOK_KEY, webhookKey.getBytes());
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(createMetadata()).data(data);
    } else {
      Map<String, byte[]> data = Optional.ofNullable(secret.getData()).orElse(new HashMap<>());
      data.put(WEBHOOK_KEY, webhookKey.getBytes());
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(secret.getMetadata()).data(data);
    }
  }

  private static V1ObjectMeta createMetadata() {
    Map labels = new HashMap<>();
    labels.put("weblogic.webhookName", getWebhookNamespace());
    return new V1ObjectMeta().name(WEBHOOK_SECRETS).namespace(getWebhookNamespace())
            .labels(labels);
  }
}

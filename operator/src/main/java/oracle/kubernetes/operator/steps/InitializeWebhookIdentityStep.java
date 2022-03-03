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
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.CallBuilder;
import oracle.kubernetes.operator.helpers.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.logging.MessageKeys.WEBHOOK_IDENTITY_INITIALIZATION_FAILED;
import static oracle.kubernetes.operator.utils.Certificates.WEBHOOK_CERTIFICATE;
import static oracle.kubernetes.operator.utils.Certificates.WEBHOOK_CERTIFICATE_KEY;
import static oracle.kubernetes.operator.utils.Certificates.WEBHOOK_DIR;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.createKeyPair;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.generateCertificate;

public class InitializeWebhookIdentityStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  private static final String WEBHOOK_CM = "weblogic-webhook-cm";
  private static final String WEBHOOK_SECRETS = "weblogic-webhook-secrets";
  private static final String SHA_256_WITH_RSA = "SHA256withRSA";
  private static final String COMMON_NAME = "weblogic-webhook";
  private static final int CERTIFICATE_VALIDITY_DAYS = 3650;
  private static final File certFile = new File(WEBHOOK_DIR + "/config/webhookCert");
  private static final File keyFile = new File(WEBHOOK_DIR + "/secrets/webhookKey");
  public static final String INTERNAL_WEBHOOK_KEY = "internalWebhookKey";

  /**
   * Constructor for the InitializeIdentityStep.
   * @param next Next step to be executed.
   */
  public InitializeWebhookIdentityStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    try {
      if (certFile.exists() && keyFile.exists()) {
        // The webhook's internal ssl identity has already been created.
        reuseIdentity();
        return doNext(getNext(), packet);
      } else {
        // The webhook's internal ssl identity hasn't been created yet.
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
    FileUtils.copyFile(certFile, new File(WEBHOOK_CERTIFICATE));
    FileUtils.copyFile(keyFile, new File(WEBHOOK_CERTIFICATE_KEY));
  }

  private NextAction createIdentity(Packet packet) throws Exception {
    KeyPair keyPair = createKeyPair();
    String key = convertToPEM(keyPair.getPrivate());
    writeToFile(key, new File(WEBHOOK_CERTIFICATE_KEY));
    X509Certificate cert = generateCertificate(WEBHOOK_CERTIFICATE, keyPair, SHA_256_WITH_RSA, COMMON_NAME,
            CERTIFICATE_VALIDITY_DAYS);
    writeToFile(getBase64Encoded(cert), new File(WEBHOOK_CERTIFICATE));
    // put the new certificate in the webhook's config map so that it will be available
    // the next time the webhook is started
    return doNext(recordInternalWebhookCert(WEBHOOK_CERTIFICATE, cert,
            recordInternalWebhookKey(key, getNext())), packet);
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

  private static Step recordInternalWebhookCert(String certName, X509Certificate cert, Step next) throws IOException {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/data/webhookCert", getBase64Encoded(cert));
    return new CallBuilder()
            .patchConfigMapAsync(WEBHOOK_CM, getWebhookNamespace(),
                    null,
                    new V1Patch(patchBuilder.build().toString()), new DefaultResponseStep<>(next));
  }

  private static Step recordInternalWebhookKey(String key, Step next) {
    return new CallBuilder().readSecretAsync(WEBHOOK_SECRETS,
            getWebhookNamespace(), readSecretResponseStep(next, key));
  }

  private static ResponseStep<V1Secret> readSecretResponseStep(Step next, String internalWebhookKey) {
    return new ReadSecretResponseStep(next, internalWebhookKey);
  }

  private static class ReadSecretResponseStep extends DefaultResponseStep<V1Secret> {
    final String internalWebhookKey;

    ReadSecretResponseStep(Step next, String internalWebhookKey) {
      super(next);
      this.internalWebhookKey = internalWebhookKey;
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Secret> callResponse) {
      V1Secret existingSecret = callResponse.getResult();
      if (existingSecret == null) {
        return doNext(createSecret(getNext(), internalWebhookKey), packet);
      } else {
        return doNext(replaceSecret(getNext(), existingSecret, internalWebhookKey), packet);
      }
    }
  }

  private static Step createSecret(Step next, String internalWebhookKey) {
    return new CallBuilder()
            .createSecretAsync(getWebhookNamespace(),
                    createModel(null, internalWebhookKey), new DefaultResponseStep<>(next));
  }

  private static Step replaceSecret(Step next, V1Secret secret, String internalWebhookKey) {
    return new CallBuilder()
            .replaceSecretAsync(WEBHOOK_SECRETS, getWebhookNamespace(), createModel(secret, internalWebhookKey),
                    new DefaultResponseStep<>(next));
  }

  protected static final V1Secret createModel(V1Secret secret, String internalWebhookKey) {
    if (secret == null) {
      Map<String, byte[]> data = new HashMap<>();
      data.put(INTERNAL_WEBHOOK_KEY, internalWebhookKey.getBytes());
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(createMetadata()).data(data);
    } else {
      Map<String, byte[]> data = Optional.ofNullable(secret.getData()).orElse(new HashMap<>());
      data.put(INTERNAL_WEBHOOK_KEY, internalWebhookKey.getBytes());
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

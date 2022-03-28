// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
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
import oracle.kubernetes.operator.MainDelegate;
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

import static oracle.kubernetes.common.logging.MessageKeys.INTERNAL_IDENTITY_INITIALIZATION_FAILED;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.createKeyPair;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.generateCertificate;

public class InitializeInternalIdentityStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String OPERATOR_CM = "weblogic-operator-cm";
  private static final String OPERATOR_SECRETS = "weblogic-operator-secrets";
  private static final String SHA_256_WITH_RSA = "SHA256withRSA";
  private static final String COMMON_NAME = "weblogic-operator";
  private static final int CERTIFICATE_VALIDITY_DAYS = 3650;

  private final File internalCertFile;
  private final File internalKeyFile;
  private final File configInternalCertFile;
  private final File secretsInternalKeyFile;

  /**
   * Initialize step with delegate and next step.
   * @param delegate Delegate
   * @param next Next step
   */
  public InitializeInternalIdentityStep(MainDelegate delegate, Step next) {
    super(next);
    Certificates certificates = new Certificates(delegate);
    this.internalCertFile = certificates.getOperatorInternalCertificateFile();
    this.internalKeyFile = certificates.getOperatorInternalKeyFile();
    this.configInternalCertFile = new File(delegate.getDeploymentHome(), "/config/internalOperatorCert");
    this.secretsInternalKeyFile = new File(delegate.getDeploymentHome(), "/secrets/internalOperatorKey");
  }

  @Override
  public NextAction apply(Packet packet) {
    try {
      if (configInternalCertFile.exists() && secretsInternalKeyFile.exists()) {
        // The operator's internal ssl identity has already been created.
        reuseInternalIdentity();
        return doNext(getNext(), packet);
      } else {
        // The operator's internal ssl identity hasn't been created yet.
        return createInternalIdentity(packet);
      }
    } catch (Exception e) {
      LOGGER.warning(INTERNAL_IDENTITY_INITIALIZATION_FAILED, e.toString());
      throw new RuntimeException(e);
    }
  }

  private void reuseInternalIdentity() throws IOException {
    // copy the certificate and key from the operator's config map and secret
    // to the locations the operator runtime expects
    FileUtils.copyFile(configInternalCertFile, internalCertFile);
    FileUtils.copyFile(secretsInternalKeyFile, internalKeyFile);
  }

  private NextAction createInternalIdentity(Packet packet) throws Exception {
    KeyPair keyPair = createKeyPair();
    String key = convertToPEM(keyPair.getPrivate());
    writeToFile(key, internalKeyFile);
    X509Certificate cert = generateCertificate(internalCertFile.getName(), keyPair, SHA_256_WITH_RSA, COMMON_NAME,
            CERTIFICATE_VALIDITY_DAYS);
    writeToFile(getBase64Encoded(cert), internalCertFile);
    // put the new certificate in the operator's config map so that it will be available
    // the next time the operator is started
    return doNext(recordInternalOperatorCert(cert,
            recordInternalOperatorKey(key, getNext())), packet);
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

  private static Step recordInternalOperatorCert(X509Certificate cert, Step next) throws IOException {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/data/internalOperatorCert", getBase64Encoded(cert));

    return new CallBuilder()
            .patchConfigMapAsync(OPERATOR_CM, getOperatorNamespace(),
                    null,
                    new V1Patch(patchBuilder.build().toString()), new DefaultResponseStep<>(next));
  }

  private static Step recordInternalOperatorKey(String key, Step next) {
    return new CallBuilder().readSecretAsync(OPERATOR_SECRETS,
            getOperatorNamespace(), readSecretResponseStep(next, key));
  }

  private static ResponseStep<V1Secret> readSecretResponseStep(Step next, String internalOperatorKey) {
    return new ReadSecretResponseStep(next, internalOperatorKey);
  }

  private static class ReadSecretResponseStep extends DefaultResponseStep<V1Secret> {
    final String internalOperatorKey;

    ReadSecretResponseStep(Step next, String internalOperatorKey) {
      super(next);
      this.internalOperatorKey = internalOperatorKey;
    }

    private static Step createSecret(Step next, String internalOperatorKey) {
      return new CallBuilder()
          .createSecretAsync(getOperatorNamespace(),
              createModel(null, internalOperatorKey), new DefaultResponseStep<>(next));
    }

    private static Step replaceSecret(Step next, V1Secret secret, String internalOperatorKey) {
      return new CallBuilder()
          .replaceSecretAsync(OPERATOR_SECRETS, getOperatorNamespace(), createModel(secret, internalOperatorKey),
              new DefaultResponseStep<>(next));
    }

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1Secret> callResponse) {
      V1Secret existingSecret = callResponse.getResult();
      if (existingSecret == null) {
        return doNext(createSecret(getNext(), internalOperatorKey), packet);
      } else {
        return doNext(replaceSecret(getNext(), existingSecret, internalOperatorKey), packet);
      }
    }
  }

  protected static V1Secret createModel(V1Secret secret, String internalOperatorKey) {
    if (secret == null) {
      Map<String, byte[]> data = new HashMap<>();
      data.put("internalOperatorKey", internalOperatorKey.getBytes());
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(createMetadata()).data(data);
    } else {
      Map<String, byte[]> data = Optional.ofNullable(secret.getData()).orElse(new HashMap<>());
      data.put("internalOperatorKey", internalOperatorKey.getBytes());
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(secret.getMetadata()).data(data);
    }
  }

  private static V1ObjectMeta createMetadata() {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.operatorName", getOperatorNamespace());
    return new V1ObjectMeta().name(OPERATOR_SECRETS).namespace(getOperatorNamespace())
            .labels(labels);
  }
}
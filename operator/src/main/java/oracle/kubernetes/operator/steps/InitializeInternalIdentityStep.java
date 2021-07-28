// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.security.Key;
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
import oracle.kubernetes.operator.utils.SelfSignedCertGenerator;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.logging.MessageKeys.INTERNAL_IDENTITY_INITIALIZATION_FAILED;
import static oracle.kubernetes.operator.utils.Certificates.INTERNAL_CERTIFICATE;
import static oracle.kubernetes.operator.utils.Certificates.INTERNAL_CERTIFICATE_KEY;
import static oracle.kubernetes.operator.utils.SelfSignedCertGenerator.createKeyPair;
import static oracle.kubernetes.operator.utils.SelfSignedCertGenerator.writePem;
import static oracle.kubernetes.operator.utils.SelfSignedCertGenerator.writeStringToFile;

public class InitializeInternalIdentityStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  public static final String OPERATOR_CM = "weblogic-operator-cm";
  public static final String OPERATOR_SECRETS = "weblogic-operator-secrets";
  public static final String SHA_256_WITH_RSA = "SHA256withRSA";
  public static final String COMMON_NAME = "weblogic-operator";

  public InitializeInternalIdentityStep(Step next) {
    super(next);
  }

  @Override
  public NextAction apply(Packet packet) {
    try {
      KeyPair keyPair = createKeyPair();
      writePem(keyPair.getPrivate(), new File(INTERNAL_CERTIFICATE_KEY));
      X509Certificate cert = SelfSignedCertGenerator.generate(keyPair, SHA_256_WITH_RSA, COMMON_NAME, 3650);
      writeStringToFile(getBase64Encoded(cert), new File(INTERNAL_CERTIFICATE));
      return doNext(recordInternalOperatorCert(cert,
              recordInternalOperatorKey(keyPair.getPrivate(), getNext())), packet);
    } catch (Exception e) {
      LOGGER.severe(INTERNAL_IDENTITY_INITIALIZATION_FAILED, e.toString());
      return doNext(getNext(), packet);
    }
  }

  private static Step recordInternalOperatorCert(X509Certificate cert, Step next) throws IOException {
    JsonPatchBuilder patchBuilder = Json.createPatchBuilder();
    patchBuilder.add("/data/internalOperatorCert", getBase64Encoded(cert));

    return new CallBuilder()
            .patchConfigMapAsync(OPERATOR_CM, getOperatorNamespace(),
                    null,
                    new V1Patch(patchBuilder.build().toString()), new DefaultResponseStep<>(next));
  }

  private static String getBase64Encoded(X509Certificate cert) throws IOException {
    StringWriter writer = new StringWriter();
    JcaPEMWriter pemWriter = new JcaPEMWriter(writer);
    pemWriter.writeObject(cert);
    pemWriter.flush();
    return Base64.getEncoder().encodeToString(writer.toString().getBytes());
  }

  private static Step recordInternalOperatorKey(Key key, Step next) {
    return new CallBuilder().readSecretAsync(OPERATOR_SECRETS,
            getOperatorNamespace(), readSecretResponseStep(next, key));
  }

  private static ResponseStep<V1Secret> readSecretResponseStep(Step next, Key internalOperatorKey) {
    return new ReadSecretResponseStep(next, internalOperatorKey);
  }

  protected static final V1Secret createModel(V1Secret secret, Key internalOperatorKey) {
    byte[] encodedKey = Base64.getEncoder().encode(internalOperatorKey.getEncoded());
    if (secret == null) {
      Map<String, byte[]> data = new HashMap<>();
      data.put("internalOperatorKey", encodedKey);
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(createMetadata()).data(data);
    } else {
      Map data = Optional.ofNullable(secret.getData()).orElse(new HashMap<>());
      data.put("internalOperatorKey", encodedKey);
      return new V1Secret().kind("Secret").apiVersion("v1").metadata(secret.getMetadata()).data(data);
    }
  }

  private static V1ObjectMeta createMetadata() {
    Map labels = new HashMap<>();
    labels.put("weblogic.operatorName", getOperatorNamespace());
    return new V1ObjectMeta().name(OPERATOR_SECRETS).namespace(getOperatorNamespace())
            .labels(labels);
  }

  private static class ReadSecretResponseStep extends DefaultResponseStep<V1Secret> {
    final Key internalOperatorKey;

    ReadSecretResponseStep(Step next, Key internalOperatorKey) {
      super(next);
      this.internalOperatorKey = internalOperatorKey;
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

  private static Step createSecret(Step next, Key internalOperatorKey) {
    return new CallBuilder()
            .createSecretAsync(getOperatorNamespace(),
                    createModel(null, internalOperatorKey), new DefaultResponseStep<>(next));
  }

  private static Step replaceSecret(Step next, V1Secret secret, Key internalOperatorKey) {
    return new CallBuilder()
            .replaceSecretAsync(OPERATOR_SECRETS, getOperatorNamespace(), createModel(secret, internalOperatorKey),
                    new DefaultResponseStep<>(next));
  }
}
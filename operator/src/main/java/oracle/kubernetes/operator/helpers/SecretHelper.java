// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1Secret;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.LoggingFilter;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

/** A Helper Class for retrieving Kubernetes Secrets used by the WebLogic Operator. */
public class SecretHelper {
  public static final String SECRET_DATA_KEY = "secretData";
  // Admin Server Credentials Type Secret
  // has 2 fields (username and password)
  public static final String ADMIN_SERVER_CREDENTIALS_USERNAME = "username";
  public static final String ADMIN_SERVER_CREDENTIALS_PASSWORD = "password";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Constructor.
   *
   */
  public SecretHelper() {

  }

  /**
   * Factory for {@link Step} that asynchronously acquires secret data.
   *
   * @param secretType Secret type
   * @param secretName Secret name
   * @param namespace Namespace
   * @param next Next processing step
   * @return Step for acquiring secret data
   */
  public static Step getSecretData(
      SecretType secretType, String secretName, String namespace, Step next) {
    return new SecretDataStep(secretType, secretName, namespace, next);
  }

  private static Map<String, byte[]> harvestAdminSecretData(
      V1Secret secret, LoggingFilter loggingFilter) {
    Map<String, byte[]> secretData = new HashMap<>();
    byte[] usernameBytes = secret.getData().get(ADMIN_SERVER_CREDENTIALS_USERNAME);
    byte[] passwordBytes = secret.getData().get(ADMIN_SERVER_CREDENTIALS_PASSWORD);

    if (usernameBytes != null) {
      secretData.put(ADMIN_SERVER_CREDENTIALS_USERNAME, usernameBytes);
    } else {
      LOGGER.warning(
          loggingFilter, MessageKeys.SECRET_DATA_NOT_FOUND, ADMIN_SERVER_CREDENTIALS_USERNAME);
    }

    if (passwordBytes != null) {
      secretData.put(ADMIN_SERVER_CREDENTIALS_PASSWORD, passwordBytes);
    } else {
      LOGGER.warning(
          loggingFilter, MessageKeys.SECRET_DATA_NOT_FOUND, ADMIN_SERVER_CREDENTIALS_PASSWORD);
    }
    return secretData;
  }

  private static class SecretDataStep extends Step {
    private final SecretType secretType;
    private final String secretName;
    private final String namespace;

    SecretDataStep(SecretType secretType, String secretName, String namespace, Step next) {
      super(next);
      this.secretType = secretType;
      this.secretName = secretName;
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (secretType != SecretType.WebLogicCredentials) {
        throw new IllegalArgumentException("Invalid secret type");
      } else if (secretName == null) {
        throw new IllegalArgumentException("Invalid secret name");
      }

      LOGGER.fine(MessageKeys.RETRIEVING_SECRET, secretName);
      Step read =
          new CallBuilder()
              .readSecretAsync(secretName, namespace, new SecretResponseStep(packet, getNext()));

      return doNext(read, packet);
    }

    private class SecretResponseStep extends ResponseStep<V1Secret> {
      private final LoggingFilter loggingFilter;

      SecretResponseStep(Packet packet, Step next) {
        super(next);
        this.loggingFilter = packet.getValue(LoggingFilter.LOGGING_FILTER_PACKET_KEY);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1Secret> callResponse) {
        if (callResponse.getStatusCode() == CallBuilder.NOT_FOUND) {
          LOGGER.warning(loggingFilter, MessageKeys.SECRET_NOT_FOUND, secretName, namespace, secretType);
          return doNext(packet);
        }
        return super.onFailure(packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Secret> callResponse) {
        packet.put(
            SECRET_DATA_KEY, harvestAdminSecretData(callResponse.getResult(), loggingFilter));
        return doNext(packet);
      }
    }
  }
}

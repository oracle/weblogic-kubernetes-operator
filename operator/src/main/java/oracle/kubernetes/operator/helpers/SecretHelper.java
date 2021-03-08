// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;

import io.kubernetes.client.openapi.models.V1Secret;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.LoggingFilter;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.operator.helpers.SecretType.WebLogicCredentials;
import static oracle.kubernetes.operator.logging.MessageKeys.SECRET_NOT_FOUND;

/** A Helper Class for retrieving Kubernetes Secrets used by the WebLogic Operator. */
public class SecretHelper {
  // Admin Server Credentials Type Secret
  // has 2 fields (username and password)
  public static final String USERNAME_KEY = "username";
  public static final String PASSWORD_KEY = "password";
  private static final String AUTHORIZATION_HEADER_FACTORY = "AuthorizationHeaderFactory";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Factory for a Step that adds a factory to create authorization headers, using the secret associated
   * with the current domain.
   * Expects packet to contain a domain presence info.
   * Records an instance of AuthorizationHeaderFactory in the packet.
   */
  public static Step createAuthorizationHeaderFactoryStep() {
    return new AuthorizationHeaderFactoryStep();
  }

  /**
   * Returns the authorization header factory stored in the specified packet, or null if it is absent.
   * @param packet the packet to read.
   */
  public static AuthorizationHeaderFactory getAuthorizationHeaderFactory(Packet packet) {
    return (AuthorizationHeaderFactory) packet.get(AUTHORIZATION_HEADER_FACTORY);
  }


  private static class AuthorizationHeaderFactoryStep extends Step {

    private String secretName;
    private String namespace;

    @Override
    public NextAction apply(Packet packet) {
      secretName = packet.getSpi(DomainPresenceInfo.class).getDomain().getWebLogicCredentialsSecretName();
      namespace = packet.getSpi(DomainPresenceInfo.class).getNamespace();

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
          LOGGER.warning(loggingFilter, SECRET_NOT_FOUND, secretName, namespace, WebLogicCredentials);
          return doNext(packet);
        }
        return super.onFailure(packet, callResponse);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1Secret> callResponse) {
        packet.put(AUTHORIZATION_HEADER_FACTORY,
              new SecretContext(callResponse.getResult(), loggingFilter).createAuthorizationHeaderFactory());
        return doNext(packet);
      }

    }

    static class SecretContext {
      private final V1Secret secret;
      private final LoggingFilter loggingFilter;

      SecretContext(V1Secret secret, LoggingFilter loggingFilter) {
        this.secret = secret;
        this.loggingFilter = loggingFilter;
      }

      AuthorizationHeaderFactory createAuthorizationHeaderFactory() {
        return new AuthorizationHeaderFactory(getSecretItem(USERNAME_KEY), getSecretItem(PASSWORD_KEY));
      }

      private byte[] getSecretItem(String key) {
        byte[] value = Optional.of(secret).map(V1Secret::getData).map(data -> data.get(key)).orElse(null);
        if (value == null) {
          LOGGER.warning(loggingFilter, MessageKeys.SECRET_DATA_NOT_FOUND, key);
          throw new RuntimeException("Unable to retrieve secret data");
        }
        return value;
      }

    }
  }
}

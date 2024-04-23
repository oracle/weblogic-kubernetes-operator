// Copyright (c) 2017, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import oracle.kubernetes.common.logging.LoggingFilter;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.RequestBuilder;
import oracle.kubernetes.operator.calls.ResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import static oracle.kubernetes.common.logging.MessageKeys.SECRET_NOT_FOUND;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.helpers.SecretType.WEBLOGIC_CREDENTIALS;

/** A Helper Class for retrieving Kubernetes Secrets used by the WebLogic Operator. */
public class SecretHelper {

  private SecretHelper() {
    // no-op
  }

  // Admin Server Credentials Type Secret
  // has 2 fields (username and password)
  public static final String USERNAME_KEY = "username";
  public static final String PASSWORD_KEY = "password";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  /**
   * Factory for a Step that adds a factory to create authorization headers, using the secret associated
   * with the current domain.
   * Expects packet to contain a domain presence info.
   * Records an instance of AuthorizationSource in the packet.
   */
  public static Step createAuthorizationSourceStep() {
    return new AuthorizationSourceStep();
  }

  /**
   * Returns the authorization header factory stored in the specified packet, or null if it is absent.
   * @param packet the packet to read.
   */
  public static AuthorizationSource getAuthorizationSource(Packet packet) {
    return (AuthorizationSource) packet.get(ProcessingConstants.AUTHORIZATION_SOURCE);
  }

  private static class AuthorizationSourceStep extends Step {

    private String secretName;
    private String namespace;

    @Override
    public @Nonnull Result apply(Packet packet) {
      DomainPresenceInfo dpi = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
      V1Secret secret = dpi.getWebLogicCredentialsSecret();
      if (secret != null) {
        insertAuthorizationSource(packet, secret);
        return doNext(packet);
      } else {
        secretName = dpi.getDomain().getWebLogicCredentialsSecretName();
        namespace = dpi.getNamespace();

        if (secretName == null) {
          return doNext(packet);
        } else {
          LOGGER.fine(MessageKeys.RETRIEVING_SECRET, secretName);
          return doNext(RequestBuilder.SECRET.get(namespace, secretName, new SecretResponseStep(getNext())), packet);
        }
      }
    }

    private void insertAuthorizationSource(Packet packet, V1Secret secret) {
      packet.put(ProcessingConstants.AUTHORIZATION_SOURCE,
          new SecretContext((DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO),
              secret, packet.getValue(LoggingFilter.LOGGING_FILTER_PACKET_KEY))
              .createAuthorizationSource());
    }

    private class SecretResponseStep extends ResponseStep<V1Secret> {

      SecretResponseStep(Step next) {
        super(next);
      }

      @Override
      public Result onFailure(Packet packet, KubernetesApiResponse<V1Secret> callResponse) {
        if (callResponse.getHttpStatusCode() == HTTP_NOT_FOUND) {
          LoggingFilter loggingFilter = packet.getValue(LoggingFilter.LOGGING_FILTER_PACKET_KEY);
          LOGGER.warning(loggingFilter, SECRET_NOT_FOUND, secretName, namespace, WEBLOGIC_CREDENTIALS);
          return doNext(packet);
        }
        return super.onFailure(packet, callResponse);
      }

      @Override
      public Result onSuccess(Packet packet, KubernetesApiResponse<V1Secret> callResponse) {
        V1Secret secret = callResponse.getObject();
        DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
        info.setWebLogicCredentialsSecret(secret);
        insertAuthorizationSource(packet, secret);
        return doNext(packet);
      }
    }

    static class SecretContext {
      private final DomainPresenceInfo dpi;
      private final V1Secret secret;
      private final LoggingFilter loggingFilter;

      SecretContext(DomainPresenceInfo dpi, V1Secret secret, LoggingFilter loggingFilter) {
        this.dpi = dpi;
        this.secret = secret;
        this.loggingFilter = loggingFilter;
      }

      AuthorizationSource createAuthorizationSource() {
        // assign variables here so that log warnings, if needed, are generated early
        byte[] username = getSecretItem(USERNAME_KEY);
        byte[] password = getSecretItem(PASSWORD_KEY);
        return new AuthorizationSource() {
          @Override
          public byte[] getUserName() {
            return username;
          }

          @Override
          public byte[] getPassword() {
            return password;
          }

          @Override
          public void onFailure() {
            dpi.setWebLogicCredentialsSecret(null);
          }
        };
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

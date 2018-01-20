// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.JSON;
import io.kubernetes.client.models.V1Secret;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;

import org.joda.time.DateTime;

import java.lang.reflect.Type;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Helper Class for retrieving Kubernetes Secrets used by the WebLogic Operator
 */
public class SecretHelper {
  public static final String SECRET_DATA_KEY = "secretData";

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final ClientHolder client;
  private final String namespace;

  public enum SecretType {
    AdminServerCredentials
  }

  // Admin Server Credentials Type Secret
  // has 2 fields (username and password)
  public static final String ADMIN_SERVER_CREDENTIALS_USERNAME = "username";
  public static final String ADMIN_SERVER_CREDENTIALS_PASSWORD = "password";

  /**
   * Constructor.
   *
   * @param client Client object to access Kubernetes APIs.
   * @param namespace Scope for object names and authorization.
   */
  public SecretHelper(ClientHolder client, String namespace) {

    this.client = client;
    this.namespace = namespace;
  }

  /**
   * Get Data for Specified Secret
   *
   * @param secretType the secret to retrieve
   * @param secretName the name of the secret.
   * @return a Map containing the secret data fields and values
   **/
  public Map<String, byte[]> getSecretData(SecretType secretType, String secretName) {

    LOGGER.entering();

    try {
      if (secretType != SecretType.AdminServerCredentials) {
        throw new IllegalArgumentException("Invalid secret type");
      } else if (secretName == null) {
        throw new IllegalArgumentException("Invalid secret name");
      }

      LOGGER.info(MessageKeys.RETRIEVING_SECRET, secretName);

      V1Secret secret = client.callBuilder().readSecret(secretName, namespace);
      if (secret == null || secret.getData() == null) {
        LOGGER.info(MessageKeys.SECRET_NOT_FOUND, secretName);
        LOGGER.exiting(null);
        return null;
      }

      return harvestAdminSecretData(secret);
    } catch (Throwable e) {
      LOGGER.warning(MessageKeys.EXCEPTION, e);
      return null;
    } finally {
      LOGGER.exiting();
    }
  }
  
  /**
   * Factory for {@link Step} that asynchronously acquires secret data
   * @param secretType Secret type
   * @param secretName Secret name
   * @param namespace Namespace
   * @param next Next processing step
   * @return Step for acquiring secret data
   */
  public static Step getSecretData(SecretType secretType, String secretName, String namespace, Step next) {
    return new SecretDataStep(secretType, secretName, namespace, next);
  }
  
  private static class SecretDataStep extends Step {
    private final SecretType secretType;
    private final String secretName;
    private final String namespace;

    public SecretDataStep(SecretType secretType, String secretName, String namespace, Step next) {
      super(next);
      this.secretType = secretType;
      this.secretName = secretName;
      this.namespace = namespace;
    }

    @Override
    public NextAction apply(Packet packet) {
      if (secretType != SecretType.AdminServerCredentials) {
        throw new IllegalArgumentException("Invalid secret type");
      } else if (secretName == null) {
        throw new IllegalArgumentException("Invalid secret name");
      }

      LOGGER.info(MessageKeys.RETRIEVING_SECRET, secretName);
      Step read = CallBuilder.create().readSecretAsync(secretName, namespace, new ResponseStep<V1Secret>(next) {
        @Override
        public NextAction onFailure(Packet packet, ApiException e, int statusCode,
            Map<String, List<String>> responseHeaders) {
          NextAction nextAction = super.onFailure(packet, e, statusCode, responseHeaders);
          if (nextAction.getNext() == null) {
            // no further retries
            LOGGER.info(MessageKeys.SECRET_NOT_FOUND, secretName);
          }
          return nextAction;
        }

        @Override
        public NextAction onSuccess(Packet packet, V1Secret result, int statusCode,
            Map<String, List<String>> responseHeaders) {
          packet.put(SECRET_DATA_KEY, harvestAdminSecretData(result));
          return doNext(packet);
        }
      });
      
      return doNext(read, packet);
    }
  }
  
  private static Map<String, byte[]> harvestAdminSecretData(V1Secret secret) {
    Map<String, byte[]> secretData = new HashMap<>();
    byte[] usernameBytes = secret.getData().get(ADMIN_SERVER_CREDENTIALS_USERNAME);
    byte[] passwordBytes = secret.getData().get(ADMIN_SERVER_CREDENTIALS_PASSWORD);

    if (usernameBytes != null) {
      secretData.put(ADMIN_SERVER_CREDENTIALS_USERNAME, usernameBytes);
    } else {
      LOGGER.info(MessageKeys.SECRET_DATA_NOT_FOUND, ADMIN_SERVER_CREDENTIALS_USERNAME);
    }

    if (passwordBytes != null) {
      secretData.put(ADMIN_SERVER_CREDENTIALS_PASSWORD, passwordBytes);
    } else {
      LOGGER.info(MessageKeys.SECRET_DATA_NOT_FOUND, ADMIN_SERVER_CREDENTIALS_PASSWORD);
    }
    return secretData;
  }

  // Due to issue with kubernetes-client/java (com.google.gson.JsonSyntaxException when deserialize V1Secret)
  // Issue #131
  // Add a custom Gson to the client so secrets can be decoded.

  /**
   * Add custom Gson to client
   * @param apiClient API client
   */
  public static void addCustomGsonToClient(ApiClient apiClient) {

    LOGGER.entering();

    JSON.DateTypeAdapter dateTypeAdapter = new JSON.DateTypeAdapter();
    JSON.SqlDateTypeAdapter sqlDateTypeAdapter = new JSON.SqlDateTypeAdapter();
    JSON.DateTimeTypeAdapter dateTimeTypeAdapter = new JSON.DateTimeTypeAdapter();

    Gson customGson =
        (new GsonBuilder()).registerTypeAdapter(
            Date.class, dateTypeAdapter).registerTypeAdapter(
            java.sql.Date.class, sqlDateTypeAdapter).registerTypeAdapter(
            DateTime.class, dateTimeTypeAdapter).registerTypeAdapter(
            byte[].class, new ByteArrayBase64StringTypeAdapter()).create();

    apiClient.getJSON().setGson(customGson);

    LOGGER.exiting();
  }

  private static class ByteArrayBase64StringTypeAdapter implements JsonSerializer<byte[]>, JsonDeserializer<byte[]> {

    public byte[] deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
      return Base64.getUrlDecoder().decode(json.getAsString());
    }

    public JsonElement serialize(byte[] src, Type typeOfSrc, JsonSerializationContext context) {
      return new JsonPrimitive(Base64.getUrlEncoder().encodeToString(src));
    }
  }

}

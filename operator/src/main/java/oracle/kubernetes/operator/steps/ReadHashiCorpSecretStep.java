// Copyright (c) 2015, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import javax.annotation.Nonnull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kubernetes.client.extended.controller.reconciler.Result;
import io.kubernetes.client.openapi.models.V1Secret;
import oracle.kubernetes.common.logging.LoggingFilter;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.helpers.AuthorizationSource;
import oracle.kubernetes.operator.helpers.DomainPresenceInfo;
import oracle.kubernetes.operator.http.client.HttpResponseStep;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.steps.HttpRequestProcessing.HTTP_TIMEOUT_SECONDS;
import static oracle.kubernetes.operator.steps.HttpRequestProcessing.createRequestStep;

public class ReadHashiCorpSecretStep extends Step {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public ReadHashiCorpSecretStep(Step next) {
    super(next);
  }

  /**
   * Creates asynchronous {@link Step} to read health from a server instance.
   *
   * @param next Next processing step
   * @return asynchronous step
   */
  public static Step createReadHashiCorpSecretStep(Step next) {
    return new ReadHashiCorpSecretStep(next);
  }

  private static HttpRequest.Builder createHashiCorpRequestBuilder(String url, long timeout) {
    final URI uri = URI.create(url);
    return HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(timeout))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("X-Requested-By", "WebLogic Operator");
  }

  private static HttpRequest.Builder createHashiCorpReadRequestBuilder(String url, String clientToken, long timeout) {
    final URI uri = URI.create(url);
    return HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(timeout))
            .header("Accept", "application/json")
            .header("X-Vault-Token", clientToken)
            .header("Content-Type", "application/json")
            .header("X-Requested-By", "WebLogic Operator");
  }

  private static void logHashiCorpFailure(Packet packet) {
    LOGGER.info(
            (LoggingFilter) packet.get(LoggingFilter.LOGGING_FILTER_PACKET_KEY),
            MessageKeys.WLS_HEALTH_READ_FAILED,
            packet.get(ProcessingConstants.SERVER_NAME));
  }

  @Override
  public @Nonnull Result apply(Packet packet) {
    DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
    String credentialSecretName = info.getDomain().getWebLogicCredentialsSecretName();
    String secretPath = info.getDomain().getHashicorpSecretPath();
    String url = info.getDomain().getHashicorpBaseUrl();
    String role = info.getDomain().getHashicorpK8sRole();

    if (credentialSecretName == null) {
      return doNext(packet);
    } else {
      return doNext(
              Step.chain(
                      new LoginHashicorpWithHttpStep(url, role),
                      new ReadHashicorpWithHttpStep(credentialSecretName, url, secretPath, getNext())),
              packet);
    }
  }


  static final class ReadHashicorpWithHttpStep extends Step {
    @Nonnull
    private final String credentialSecretName;
    private final String secretPath;
    private final String url;

    ReadHashicorpWithHttpStep(@NotNull String credentialSecretName, String url, String secretPath, Step next) {
      super(next);
      this.credentialSecretName = credentialSecretName;
      this.secretPath = secretPath;
      this.url = url;
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      String clientToken = (String) packet.get("HASHICORP_TOKEN");
      return doNext(createRequestStep(createReadCredentialRequest(url, clientToken, secretPath,
                      credentialSecretName),
              new StoreSecretResultStep(getNext())), packet);
    }

    private HttpRequest createReadCredentialRequest(String url, String clientToken, String secretPath,
                                                    String secretName) {
      LOGGER.finer("Create REST request to service URL: " + url);
      return createHashiCorpReadRequestBuilder(
              url + secretPath + secretName,
              clientToken,
              HTTP_TIMEOUT_SECONDS)
              .build();
    }


  }

  static final class LoginHashicorpWithHttpStep extends Step {
    private final String url;
    private final String role;

    LoginHashicorpWithHttpStep(String url, String role) {
      this.url = url;
      this.role = role;
    }

    private HttpRequest createLoginRequest() {
      LOGGER.finer("Create Login request to Hashicorp vault: ");
      String loginPayload = getLoginPayload(role);

      return createHashiCorpRequestBuilder(url + "/v1/auth/kubernetes/login",
              HTTP_TIMEOUT_SECONDS)
              .POST(HttpRequest.BodyPublishers.ofString(loginPayload))
              .build();
    }

    @Override
    public @Nonnull Result apply(Packet packet) {
      return doNext(createRequestStep(createLoginRequest(),
              new LoginHashicorpResultStep(getNext())), packet);
    }

    private String getLoginPayload(String roleName) {

      String jwt;
      try {
        jwt = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/token")));
      } catch (Exception e) {
        LOGGER.warning("Failed to read service account token: " + e.getMessage());
        jwt = ""; // fallback to empty string if read fails
      }
      return "{'jwt': '" + jwt + "', 'role': '" + roleName + "'}";
    }


  }

  static final class LoginHashicorpResultStep extends HttpResponseStep {

    LoginHashicorpResultStep(Step next) {
      super(next);
    }

    @Override
    public Result onSuccess(Packet packet, HttpResponse<String> response) {
      try {
        String body = response.body();

        JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
        if (jsonObject.has("auth") && jsonObject.getAsJsonObject("auth").has("client_token")) {
          String clientToken = jsonObject.getAsJsonObject("auth").get("client_token").getAsString();
          packet.put("HASHICORP_TOKEN", clientToken);
        } else {
          LOGGER.warning("Response does not contain 'client_token' in 'auth' object.");
        }

        return doNext(packet);
      } catch (Exception e) {
        LOGGER.warning("Failed to process the response: " + e.getMessage());
        return doNext(packet);
      }
    }

    @Override
    public Result onFailure(Packet packet, HttpResponse<String> response) {
      return doNext(packet);
    }

  }

  static final class StoreSecretResultStep extends HttpResponseStep {

    StoreSecretResultStep(Step next) {
      super(next);
    }

    @Override
    public Result onSuccess(Packet packet, HttpResponse<String> response) {
      final String USERNAME_KEY = "username";
      final String PASSWORD_KEY = "password";

      try {
        String body = response.body();
        JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
        if (jsonObject.has("data") && jsonObject.getAsJsonObject("data").has("data")) {
          JsonObject secretData = jsonObject.getAsJsonObject("data").getAsJsonObject("data");
          packet.put("HASHICORP_SECRET_DATA", secretData.toString());

          V1Secret secret = new V1Secret();
          secret.putStringDataItem(USERNAME_KEY, secretData.get(USERNAME_KEY).getAsString());
          secret.putStringDataItem(PASSWORD_KEY, secretData.get(PASSWORD_KEY).getAsString());
          DomainPresenceInfo info = (DomainPresenceInfo) packet.get(ProcessingConstants.DOMAIN_PRESENCE_INFO);
          info.setWebLogicCredentialsSecret(secret);
          AuthorizationSource authorizationSource = new AuthorizationSource() {

            @Override
            public byte[] getUserName() {
              return secretData.get(USERNAME_KEY).getAsString().getBytes();
            }

            @Override
            public byte[] getPassword() {
              return secretData.get(PASSWORD_KEY).getAsString().getBytes();
            }

            @Override
            public void onFailure() {
              info.setWebLogicCredentialsSecret(null);
            }
          };

          packet.put(ProcessingConstants.AUTHORIZATION_SOURCE, authorizationSource);

        } else {
          LOGGER.warning("Response does not contain expected 'data.data' structure.");
        }
        return doNext(packet);
      } catch (Throwable t) {
        LOGGER.warning("Failed to process the response: " + t.getMessage());
        return doNext(packet);
      }
    }

    @Override
    public Result onFailure(Packet packet, HttpResponse<String> response) {
      return doNext(packet);
    }

  }

}

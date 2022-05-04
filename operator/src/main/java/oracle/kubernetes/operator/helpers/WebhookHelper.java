// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.AdmissionregistrationV1ServiceReference;
import io.kubernetes.client.openapi.models.AdmissionregistrationV1WebhookClientConfig;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1RuleWithOperations;
import io.kubernetes.client.openapi.models.V1ValidatingWebhook;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.apache.commons.codec.binary.Base64;

import static oracle.kubernetes.common.logging.MessageKeys.VALIDATING_WEBHOOK_CONFIGURATION_CREATED;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.rest.RestConfigImpl.CONVERSION_WEBHOOK_HTTPS_PORT;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.WEBLOGIC_OPERATOR_WEBHOOK_SVC;

public class WebhookHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  public static final String VALIDATING_WEBHOOK_NAME = "weblogic.validating.webhook.v9";
  public static final String VALIDATING_WEBHOOK_PATH = "/admission";
  public static final String APP_GROUP = "weblogic.oracle";
  public static final String API_VERSION = "v9";
  public static final String ADMISSION_REVIEW_VERSION = "v1";
  public static final String DOMAIN_RESOURCES = "domains";
  public static final String OPERATION = "UPDATE";
  public static final String SIDE_EFFECT_NONE = "None";
  public static final String SCOPE = "Namespaced";


  private WebhookHelper() {
  }

  /**
   * Factory for {@link Step} that verifies and creates pod disruption budget if needed.
   *
   * @param certificates certificates for the webhook
   * @return Step for creating a validating webhook configuration
   */
  public static Step createValidatingWebhookConfigurationStep(
      Certificates certificates) {
    return new CreateValidatingWebhookConfigurationStep(certificates);
  }

  static class CreateValidatingWebhookConfigurationStep extends Step {
    private final ValidatingWebhookConfigurationContext context;

    CreateValidatingWebhookConfigurationStep(Certificates certificates) {
      super();
      context = createContext(certificates);
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(context.verifyValidatingWebhookConfiguration(getNext()), packet);
    }

    protected ValidatingWebhookConfigurationContext createContext(Certificates certificates) {
      return new ValidatingWebhookConfigurationContext(this, certificates);
    }
  }
  
  static class ValidatingWebhookConfigurationContext {
    private final Step conflictStep;
    private final V1ValidatingWebhookConfiguration model;

    ValidatingWebhookConfigurationContext(Step conflictStep, Certificates certificates) {
      this.conflictStep = conflictStep;
      this.model = createModel(certificates);
    }

    V1ValidatingWebhookConfiguration createModel(Certificates certificates) {
      Map<String, String> labels = new HashMap<>();
      labels.put(CREATEDBYOPERATOR_LABEL, "true");
      return AnnotationHelper.withSha256Hash(new V1ValidatingWebhookConfiguration()
          .metadata(new V1ObjectMeta().name(VALIDATING_WEBHOOK_NAME).labels(labels))
          .addWebhooksItem(new V1ValidatingWebhook().name(VALIDATING_WEBHOOK_NAME)
              .admissionReviewVersions(Collections.singletonList(ADMISSION_REVIEW_VERSION))
              .sideEffects(SIDE_EFFECT_NONE)
              .addRulesItem(new V1RuleWithOperations()
                  .addApiGroupsItem(APP_GROUP)
                  .apiVersions(Collections.singletonList(API_VERSION))
                  .operations(Collections.singletonList(OPERATION))
                  .resources(Collections.singletonList(DOMAIN_RESOURCES))
                  .scope(SCOPE))
              .clientConfig(new AdmissionregistrationV1WebhookClientConfig()
                  .service(new AdmissionregistrationV1ServiceReference()
                      .namespace(getWebhookNamespace())
                      .name(WEBLOGIC_OPERATOR_WEBHOOK_SVC)
                      .port(CONVERSION_WEBHOOK_HTTPS_PORT)
                      .path(VALIDATING_WEBHOOK_PATH))
                  .caBundle(Optional.ofNullable(certificates).map(Certificates::getWebhookCertificateData)
                      .map(Base64::decodeBase64).orElse(null)))));
    }

    Step verifyValidatingWebhookConfiguration(Step next) {
      return new CallBuilder().readValidatingWebhookConfigurationAsync(
          model.getMetadata().getName(), createReadResponseStep(next));
    }

    ResponseStep<V1ValidatingWebhookConfiguration> createReadResponseStep(Step next) {
      return new ReadResponseStep(next);
    }

    Step createValidatingWebhookConfiguration(Step next) {
      return new CallBuilder().createValidatingWebhookConfigurationAsync(
          model, createCreateResponseStep(next));
    }

    ResponseStep<V1ValidatingWebhookConfiguration> createCreateResponseStep(Step next) {
      return new CreateResponseStep(next);
    }

    class ReadResponseStep extends DefaultResponseStep<V1ValidatingWebhookConfiguration> {
      ReadResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        V1ValidatingWebhookConfiguration existingCrd = callResponse.getResult();
        if (existingCrd == null) {
          return doNext(createValidatingWebhookConfiguration(getNext()), packet);
        } else {
          // TODO handle existing non-matching configuration
          return doNext(packet);
        }
      }

      @Override
      protected NextAction onFailureNoRetry(
          Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        return isNotAuthorizedOrForbidden(callResponse)
            ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }

    private class CreateResponseStep extends ResponseStep<V1ValidatingWebhookConfiguration> {
      CreateResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(
          Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        return super.onFailure(conflictStep, packet, callResponse);
      }

      @Override
      public NextAction onSuccess(
          Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        LOGGER.info(VALIDATING_WEBHOOK_CONFIGURATION_CREATED, callResponse.getResult().getMetadata().getName());
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(
          Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        LOGGER.info(MessageKeys.CREATE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED,
            VALIDATING_WEBHOOK_NAME, callResponse.getE().getResponseBody());
        return isNotAuthorizedOrForbidden(callResponse)
            ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }
  }

  /**
   * Convert the domain schema to desired API version.
   * @param oldObject object that needs to be validated against.
   * @param object object that needs to be validated.
   * @return true if valid, otherwise false
   */
  public static boolean validate(Object oldObject, Object object) {
    LOGGER.fine("validate domain " + object + " against " + oldObject);
    LOGGER.info("Validating webhook is invoked and returns true");
    return true;
  }
}

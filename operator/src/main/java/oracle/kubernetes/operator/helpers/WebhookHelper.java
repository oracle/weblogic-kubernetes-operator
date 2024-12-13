// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.kubernetes.client.openapi.models.AdmissionregistrationV1ServiceReference;
import io.kubernetes.client.openapi.models.AdmissionregistrationV1WebhookClientConfig;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1RuleWithOperations;
import io.kubernetes.client.openapi.models.V1ValidatingWebhook;
import io.kubernetes.client.openapi.models.V1ValidatingWebhookConfiguration;
import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.calls.UnrecoverableErrorBuilder;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import org.apache.commons.codec.binary.Base64;

import static oracle.kubernetes.common.logging.MessageKeys.VALIDATING_WEBHOOK_CONFIGURATION_CREATED;
import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER_PLURAL;
import static oracle.kubernetes.operator.KubernetesConstants.CLUSTER_VERSION;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_GROUP;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_PLURAL;
import static oracle.kubernetes.operator.KubernetesConstants.DOMAIN_VERSION;
import static oracle.kubernetes.operator.KubernetesConstants.HTTP_NOT_FOUND;
import static oracle.kubernetes.operator.LabelConstants.CREATEDBYOPERATOR_LABEL;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;
import static oracle.kubernetes.operator.http.rest.RestConfigImpl.CONVERSION_WEBHOOK_HTTPS_PORT;
import static oracle.kubernetes.operator.utils.SelfSignedCertUtils.WEBLOGIC_OPERATOR_WEBHOOK_SVC;

public class WebhookHelper {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  public static final String VALIDATING_WEBHOOK_NAME = "weblogic.validating.webhook";
  public static final String VALIDATING_WEBHOOK_PATH = "/admission";
  public static final String APP_GROUP = DOMAIN_GROUP;
  public static final String DOMAIN_RESOURCES = DOMAIN_PLURAL;
  public static final String CLUSTER_RESOURCES = CLUSTER_PLURAL;
  public static final String ADMISSION_REVIEW_VERSION = "v1";
  public static final String CREATE = "CREATE";
  public static final String UPDATE = "UPDATE";
  public static final String SIDE_EFFECT_NONE = "None";
  public static final String SCOPE = "Namespaced";

  private WebhookHelper() {
  }

  /**
   * Factory for {@link Step} that verifies and creates validating webhook configuration if needed.
   *
   * @param certificates certificates for the webhook
   * @return Step for creating a validating webhook configuration
   */
  public static Step createValidatingWebhookConfigurationStep(
      Certificates certificates) {
    return new CreateValidatingWebhookConfigurationStep(certificates);
  }

  static class CreateValidatingWebhookConfigurationStep extends Step {
    private final Certificates certificates;

    CreateValidatingWebhookConfigurationStep(Certificates certificates) {
      super();
      this.certificates = certificates;
    }

    @Override
    public NextAction apply(Packet packet) {
      return doNext(createContext().verifyValidatingWebhookConfiguration(getNext()), packet);
    }

    protected ValidatingWebhookConfigurationContext createContext() {
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

    private V1ValidatingWebhookConfiguration createModel(Certificates certificates) {
      Map<String, String> labels = new HashMap<>();
      labels.put(CREATEDBYOPERATOR_LABEL, "true");
      return AnnotationHelper.withSha256Hash(createValidatingWebhookConfigurationModel(certificates, labels));
    }

    private V1ValidatingWebhookConfiguration createValidatingWebhookConfigurationModel(
        Certificates certificates, Map<String, String> labels) {
      return new V1ValidatingWebhookConfiguration()
          .metadata(createMetadata(labels))
          .addWebhooksItem(createWebhooksItem(certificates));
    }

    private V1ValidatingWebhook createWebhooksItem(Certificates certificates) {
      return new V1ValidatingWebhook().name(VALIDATING_WEBHOOK_NAME)
          .admissionReviewVersions(Collections.singletonList(ADMISSION_REVIEW_VERSION))
          .sideEffects(SIDE_EFFECT_NONE)
          .clientConfig(createClientConfig(certificates))
          .addRulesItem(createRuleForDomain())
          .addRulesItem(createRuleForCluster());
    }

    private AdmissionregistrationV1WebhookClientConfig createClientConfig(Certificates certificates) {
      return new AdmissionregistrationV1WebhookClientConfig()
          .service(createServiceReference())
          .caBundle(getCaBundle(certificates));
    }

    private AdmissionregistrationV1ServiceReference createServiceReference() {
      return new AdmissionregistrationV1ServiceReference()
          .namespace(getWebhookNamespace())
          .name(WEBLOGIC_OPERATOR_WEBHOOK_SVC)
          .port(CONVERSION_WEBHOOK_HTTPS_PORT)
          .path(VALIDATING_WEBHOOK_PATH);
    }

    private V1RuleWithOperations createRule() {
      return new V1RuleWithOperations()
          .addApiGroupsItem(APP_GROUP)
          .operations(Arrays.asList(CREATE, UPDATE))
          .scope(SCOPE);
    }

    private V1RuleWithOperations createRuleForDomain() {
      return createRule().apiVersions(createList(DOMAIN_VERSION)).resources(createList(DOMAIN_RESOURCES));
    }

    @Nonnull
    private List<String> createList(String item) {
      return Collections.singletonList(item);
    }

    private V1RuleWithOperations createRuleForCluster() {
      return createRule().apiVersions(createList(CLUSTER_VERSION))
          .resources(Arrays.asList(CLUSTER_RESOURCES, "*/scale"));
    }

    private V1ObjectMeta createMetadata(Map<String, String> labels) {
      return new V1ObjectMeta().name(VALIDATING_WEBHOOK_NAME).labels(labels);
    }

    @Nullable
    private byte[] getCaBundle(@Nonnull Certificates certificates) {
      return Optional.of(certificates).map(Certificates::getWebhookCertificateData)
          .map(Base64::decodeBase64).orElse(null);
    }

    private Step verifyValidatingWebhookConfiguration(Step next) {
      return new CallBuilder().readValidatingWebhookConfigurationAsync(
          getName(model), createReadResponseStep(next));
    }

    @Nullable
    private String getName(V1ValidatingWebhookConfiguration validatingWebhookConfiguration) {
      return Optional.ofNullable(validatingWebhookConfiguration)
          .map(V1ValidatingWebhookConfiguration::getMetadata)
          .map(V1ObjectMeta::getName)
          .orElse(null);
    }

    private Step getConflictStep() {
      return conflictStep;
    }

    private ResponseStep<V1ValidatingWebhookConfiguration> createReadResponseStep(Step next) {
      return new ReadResponseStep(next);
    }

    
    private class ReadResponseStep extends WebhookConfigResponseStep {
      ReadResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        clearExistingKubernetesNetworkException(packet);
        V1ValidatingWebhookConfiguration existingWebhookConfig = callResponse.getResult();
        if (existingWebhookConfig == null) {
          return doNext(createValidatingWebhookConfiguration(getNext()), packet);
        } else if (shouldUpdate(existingWebhookConfig)) {
          return doNext(replaceValidatingWebhookConfiguration(getNext(), existingWebhookConfig), packet);
        } else {
          return doNext(packet);
        }
      }

      private boolean shouldUpdate(V1ValidatingWebhookConfiguration existingWebhookConfig) {
        return !getServiceNamespace(existingWebhookConfig).equals(getServiceNamespace(model))
            || !Arrays.equals(getClientConfigCaBundle(existingWebhookConfig), getClientConfigCaBundle(model));
      }

      private Object getServiceNamespace(V1ValidatingWebhookConfiguration webhookConfig) {
        return Optional.ofNullable(getFirstWebhook(webhookConfig)).map(V1ValidatingWebhook::getClientConfig)
            .map(AdmissionregistrationV1WebhookClientConfig::getService)
            .map(AdmissionregistrationV1ServiceReference::getNamespace).orElse("");
      }

      private byte[] getClientConfigCaBundle(V1ValidatingWebhookConfiguration webhookConfig) {
        return Optional.ofNullable(getFirstWebhook(webhookConfig)).map(V1ValidatingWebhook::getClientConfig)
            .map(AdmissionregistrationV1WebhookClientConfig::getCaBundle)
            .orElse(null);
      }

      private V1ValidatingWebhook getFirstWebhook(V1ValidatingWebhookConfiguration webhookConfig) {
        return Optional.of(webhookConfig)
            .map(V1ValidatingWebhookConfiguration::getWebhooks)
            .map(this::getFirstWebhook)
            .orElse(null);
      }

      private V1ValidatingWebhook getFirstWebhook(List<V1ValidatingWebhook> l) {
        return l.isEmpty() ? null : l.get(0);
      }

      private Step createValidatingWebhookConfiguration(Step next) {
        return new CallBuilder().createValidatingWebhookConfigurationAsync(
            model, createCreateResponseStep(next));
      }

      private ResponseStep<V1ValidatingWebhookConfiguration> createCreateResponseStep(Step next) {
        return new CreateResponseStep(next);
      }

      private Step replaceValidatingWebhookConfiguration(Step next, V1ValidatingWebhookConfiguration existing) {
        return new CallBuilder().replaceValidatingWebhookConfigurationAsync(
            VALIDATING_WEBHOOK_NAME, updateModel(existing), createReplaceResponseStep(next));
      }

      private V1ValidatingWebhookConfiguration updateModel(V1ValidatingWebhookConfiguration existing) {
        model.setMetadata(existing.getMetadata());
        return model;
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        return callResponse.getStatusCode() == HTTP_NOT_FOUND
            ? onSuccess(packet, callResponse)
            : super.onFailure(packet, callResponse);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet,
                                            CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        LOGGER.info(MessageKeys.READ_VALIDATING_WEBHOOK_CONFIGURATION_FAILED,
            VALIDATING_WEBHOOK_NAME, callResponse.getE().getResponseBody());
        return isNotAuthorizedOrForbidden(callResponse)
                ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }

    private class CreateResponseStep extends WebhookConfigResponseStep {
      CreateResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        clearExistingKubernetesNetworkException(packet);
        LOGGER.info(VALIDATING_WEBHOOK_CONFIGURATION_CREATED, getName(callResponse.getResult()));
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet,
                                            CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        LOGGER.info(MessageKeys.CREATE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED,
            VALIDATING_WEBHOOK_NAME, callResponse.getE().getResponseBody());
        return isNotAuthorizedOrForbidden(callResponse)
                ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }

    ResponseStep<V1ValidatingWebhookConfiguration> createReplaceResponseStep(Step next) {
      return new ReplaceResponseStep(next);
    }

    private class ReplaceResponseStep extends WebhookConfigResponseStep {
      ReplaceResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        if (UnrecoverableErrorBuilder.isAsyncCallNotFoundFailure(callResponse)) {
          return super.onFailure(getConflictStep(), packet, callResponse);
        } else {
          return super.onFailure(packet, callResponse);
        }
      }

      @Override
      public NextAction onSuccess(Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        clearExistingKubernetesNetworkException(packet);
        LOGGER.info(MessageKeys.VALIDATING_WEBHOOK_CONFIGURATION_REPLACED, getName(callResponse.getResult()));
        return doNext(packet);
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet,
                                            CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        LOGGER.info(MessageKeys.REPLACE_VALIDATING_WEBHOOK_CONFIGURATION_FAILED,
            VALIDATING_WEBHOOK_NAME, callResponse.getE().getResponseBody());
        return isNotAuthorizedOrForbidden(callResponse)
                ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }

    private class WebhookConfigResponseStep extends ResponseStep<V1ValidatingWebhookConfiguration> {
      WebhookConfigResponseStep(Step next) {
        super(next);
      }

      @Override
      public NextAction onFailure(Packet packet, CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        if (UnrecoverableErrorBuilder.isAsyncCallUnrecoverableFailure(callResponse)) {
          return onFailureNoRetry(packet, callResponse);
        } else {
          return super.onFailure(getConflictStep(), packet, callResponse);
        }
      }

      @Override
      protected NextAction onFailureNoRetry(Packet packet,
                                            CallResponse<V1ValidatingWebhookConfiguration> callResponse) {
        return isNotAuthorizedOrForbidden(callResponse)
            ? doNext(packet) : super.onFailureNoRetry(packet, callResponse);
      }
    }
  }

  public static Step deleteValidatingWebhookConfigurationStep() {
    return new DeleteValidatingWebhookConfigurationStep();
  }

  private static class DeleteValidatingWebhookConfigurationStep extends Step {
    @Override
    public NextAction apply(Packet packet) {
      return doNext(createActionStep(), packet);
    }

    private Step createActionStep() {
      V1DeleteOptions deleteOptions = new V1DeleteOptions();
      return new CallBuilder()
          .deleteValidatingWebhookConfigurationAsync(VALIDATING_WEBHOOK_NAME, deleteOptions,
              new DefaultResponseStep<>(getNext()));
    }
  }
}

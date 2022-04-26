// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.KubernetesResourceLookup;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.common.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;
import static oracle.kubernetes.operator.DomainFailureReason.DOMAIN_INVALID;
import static oracle.kubernetes.operator.DomainFailureReason.TOPOLOGY_MISMATCH;
import static oracle.kubernetes.operator.DomainStatusUpdater.createRemoveSelectedFailuresStep;

public class DomainValidationSteps {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String SECRETS = "secrets";
  private static final String CONFIGMAPS = "configmaps";

  private DomainValidationSteps() {
  }

  /**
   * Returns a chain of steps to validate the domain in the current packet.
   * @param namespace the namespace for the domain
   */
  public static Step createDomainValidationSteps(String namespace) {
    return Step.chain(
          createListSecretsStep(namespace),
          createListConfigMapsStep(namespace),
          new DomainValidationStep());
  }

  static Step createAdditionalDomainValidationSteps(V1PodSpec podSpec) {
    return new DomainAdditionalValidationStep(podSpec);
  }

  public static Step createAfterIntrospectValidationSteps() {
    return new DomainAfterIntrospectValidationStep();
  }

  private static Step createListSecretsStep(String domainNamespace) {
    return new CallBuilder().listSecretsAsync(domainNamespace, new ListSecretsResponseStep());
  }

  static Step createValidateDomainTopologyStep(Step next) {
    return new ValidateDomainTopologyStep(next);
  }

  static class ListSecretsResponseStep extends DefaultResponseStep<V1SecretList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1SecretList> callResponse) {
      List<V1Secret> list = getSecrets(packet);
      list.addAll(callResponse.getResult().getItems());
      packet.put(SECRETS, list);

      return doContinueListOrNext(callResponse, packet);
    }

    static List<V1Secret> getSecrets(Packet packet) {
      return Optional.ofNullable(packet.<List<V1Secret>>getValue(SECRETS)).orElse(new ArrayList<>());
    }
  }

  private static Step createListConfigMapsStep(String domainNamespace) {
    return new CallBuilder().listConfigMapsAsync(domainNamespace, new ListConfigMapsResponseStep());
  }

  static class ListConfigMapsResponseStep extends DefaultResponseStep<V1ConfigMapList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMapList> callResponse) {
      List<V1ConfigMap> list = getConfigMaps(packet);
      list.addAll(callResponse.getResult().getItems());
      packet.put(CONFIGMAPS, list);

      return doContinueListOrNext(callResponse, packet);
    }

    static List<V1ConfigMap> getConfigMaps(Packet packet) {
      return Optional.ofNullable(packet.<List<V1ConfigMap>>getValue(CONFIGMAPS)).orElse(new ArrayList<>());
    }
  }

  static class DomainValidationStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      Domain domain = info.getDomain();
      List<String> validationFailures = domain.getValidationFailures(new KubernetesResourceLookupImpl(packet));

      if (validationFailures.isEmpty()) {
        return doNext(createRemoveSelectedFailuresStep(getNext(), DOMAIN_INVALID), packet)
              .withDebugComment(packet, this::domainValidated);
      } else {
        LOGGER.severe(DOMAIN_VALIDATION_FAILED, domain.getDomainUid(), perLine(validationFailures));
        return doNext(DomainStatusUpdater.createDomainInvalidFailureSteps(perLine(validationFailures)), packet);
      }

    }

    private String perLine(List<String> validationFailures) {
      return String.join(lineSeparator(), validationFailures);
    }

    private String domainValidated(Packet packet) {
      return "Validated " + DomainPresenceInfo.fromPacket(packet).orElse(null);
    }
    
  }

  static class DomainAdditionalValidationStep extends Step {
    final V1PodSpec podSpec;

    DomainAdditionalValidationStep(V1PodSpec podSpec) {
      this.podSpec = podSpec;
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      Domain domain = info.getDomain();
      List<String> validationFailures = domain.getAdditionalValidationFailures(podSpec);

      if (validationFailures.isEmpty()) {
        return doNext(packet);
      }

      LOGGER.severe(DOMAIN_VALIDATION_FAILED, domain.getDomainUid(), perLine(validationFailures));
      Step step = DomainStatusUpdater.createDomainInvalidFailureSteps(perLine(validationFailures));
      return doNext(step, packet);
    }

    private String perLine(List<String> validationFailures) {
      return String.join(lineSeparator(), validationFailures);
    }

  }

  static class ValidateDomainTopologyStep extends Step {

    ValidateDomainTopologyStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      List<String> failures = new WlsConfigValidator(packet).loggingTo(LOGGER).getFailures();
      if (failures.isEmpty()) {
        return doNext(createRemoveSelectedFailuresStep(getNext(), TOPOLOGY_MISMATCH), packet);
      } else {
        return doNext(createTopologyMismatchFailureSteps(getNext(), failures), packet);
      }
    }

    private Step createTopologyMismatchFailureSteps(Step next, List<String> failures) {
      final String failureString = String.join(lineSeparator(), failures);
      return DomainStatusUpdater.createTopologyMismatchFailureSteps(failureString, next);
    }
  }

  static class KubernetesResourceLookupImpl implements KubernetesResourceLookup {

    private final Packet packet;

    KubernetesResourceLookupImpl(Packet packet) {
      this.packet = packet;
    }

    @Override
    public boolean isSecretExists(String name, String namespace) {
      return getSecrets(packet).stream().anyMatch(s -> isSpecifiedSecret(s, name, namespace));
    }

    boolean isSpecifiedSecret(V1Secret secret, String name, String namespace) {
      return hasMatchingMetadata(secret.getMetadata(), name, namespace);
    }

    @SuppressWarnings("unchecked")
    private List<V1Secret> getSecrets(Packet packet) {
      return (List<V1Secret>) packet.get(SECRETS);
    }

    @Override
    public boolean isConfigMapExists(String name, String namespace) {
      return getConfigMaps(packet).stream().anyMatch(s -> isSpecifiedConfigMap(s, name, namespace));
    }

    boolean isSpecifiedConfigMap(V1ConfigMap configmap, String name, String namespace) {
      return hasMatchingMetadata(configmap.getMetadata(), name, namespace);
    }

    @SuppressWarnings("unchecked")
    private List<V1ConfigMap> getConfigMaps(Packet packet) {
      return (List<V1ConfigMap>) packet.get(CONFIGMAPS);
    }

    private boolean hasMatchingMetadata(V1ObjectMeta metadata, String name, String namespace) {
      return metadata != null
            && Objects.equals(name, metadata.getName())
            && Objects.equals(namespace, metadata.getNamespace());
    }
  }

  private static class DomainAfterIntrospectValidationStep extends Step {

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      List<String> validationFailures = new WlsConfigValidator(packet).getFailures();

      if (validationFailures.isEmpty()) {
        return doNext(packet);
      }

      LOGGER.severe(DOMAIN_VALIDATION_FAILED, info.getDomainUid(), perLine(validationFailures));
      Step step = DomainStatusUpdater.createDomainInvalidFailureSteps(perLine(validationFailures));
      return doNext(step, packet);
    }

    private String perLine(List<String> validationFailures) {
      return String.join(lineSeparator(), validationFailures);
    }

  }
}

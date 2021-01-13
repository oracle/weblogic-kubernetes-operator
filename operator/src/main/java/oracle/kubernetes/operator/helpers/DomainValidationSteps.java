// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

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
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import oracle.kubernetes.weblogic.domain.model.KubernetesResourceLookup;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.operator.DomainStatusUpdater.BAD_DOMAIN;
import static oracle.kubernetes.operator.helpers.EventHelper.createEventStep;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;

public class DomainValidationSteps {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String SECRETS = "secrets";
  private static final String CONFIGMAPS = "configmaps";

  public static Step createDomainValidationSteps(String namespace, Step next) {
    return Step.chain(createListSecretsStep(namespace), createListConfigMapsStep(namespace),
              new DomainValidationStep(next));
  }

  public static Step createAdditionalDomainValidationSteps(V1PodSpec podSpec) {
    return new DomainAdditionalValidationStep(podSpec);
  }

  public static Step createAfterIntrospectValidationSteps() {
    return new DomainAfterIntrospectValidationStep();
  }

  private static Step createListSecretsStep(String domainNamespace) {
    return new CallBuilder().listSecretsAsync(domainNamespace, new ListSecretsResponseStep());
  }

  public static Step createValidateDomainTopologyStep(Step next) {
    return new ValidateDomainTopologyStep(next);
  }

  static class ListSecretsResponseStep extends DefaultResponseStep<V1SecretList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1SecretList> callResponse) {
      packet.put(SECRETS, callResponse.getResult().getItems());

      return doContinueListOrNext(callResponse, packet);
    }
  }

  private static Step createListConfigMapsStep(String domainNamespace) {
    return new CallBuilder().listConfigMapsAsync(domainNamespace, new ListConfigMapsResponseStep());
  }

  static class ListConfigMapsResponseStep extends DefaultResponseStep<V1ConfigMapList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMapList> callResponse) {
      packet.put(CONFIGMAPS, callResponse.getResult().getItems());

      return doContinueListOrNext(callResponse, packet);
    }
  }

  static class DomainValidationStep extends Step {

    DomainValidationStep(Step next) {
      super(next);
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      Domain domain = info.getDomain();
      List<String> validationFailures = domain.getValidationFailures(new KubernetesResourceLookupImpl(packet));

      if (validationFailures.isEmpty()) {
        return doNext(packet);
      }

      LOGGER.severe(DOMAIN_VALIDATION_FAILED, domain.getDomainUid(), perLine(validationFailures));
      Step step = DomainStatusUpdater.createFailureRelatedSteps(BAD_DOMAIN, perLine(validationFailures), null);
      return doNext(step, packet);
    }

    private String perLine(List<String> validationFailures) {
      return String.join(lineSeparator(), validationFailures);
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
      Step step = DomainStatusUpdater.createFailureRelatedSteps(BAD_DOMAIN, perLine(validationFailures), null);
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


    private void logAndAddValidationWarning(DomainPresenceInfo info, String msgId, Object... messageParams) {
      LOGGER.warning(msgId, messageParams);
      info.addValidationWarning(LOGGER.formatMessage(msgId, messageParams));
    }

    private void validate(DomainPresenceInfo info, WlsDomainConfig wlsDomainConfig) {
      DomainSpec domainSpec = info.getDomain().getSpec();

      info.clearValidationWarnings();

      // log warnings for each cluster that is specified in domain resource but not configured
      // in the WebLogic domain
      domainSpec.getClusters().forEach(
          c -> warnIfClusterDoesNotExist(wlsDomainConfig, c.getClusterName(), info));

      // log warnings for each managed server that is specified in domain resource but not configured
      // in the WebLogic domain
      domainSpec.getManagedServers().forEach(
          s -> warnIfServerDoesNotExist(wlsDomainConfig, s.getServerName(), info));
    }

    private void warnIfClusterDoesNotExist(WlsDomainConfig domainConfig,
        String clusterName, DomainPresenceInfo info) {
      if (!domainConfig.containsCluster(clusterName)) {
        logAndAddValidationWarning(info, MessageKeys.NO_CLUSTER_IN_DOMAIN, clusterName);
      }
    }

    private void warnIfServerDoesNotExist(WlsDomainConfig domainConfig,
        String serverName, DomainPresenceInfo info) {
      if (!domainConfig.containsServer(serverName)) {
        logAndAddValidationWarning(info, MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN, serverName);
      }
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      boolean isExplicitRecheck = MakeRightDomainOperation.isExplicitRecheck(packet);
      WlsDomainConfig wlsDomainConfig = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
      validate(info, wlsDomainConfig);

      return doNext(getNextStep(info.getValidationWarningsAsString(), isExplicitRecheck, getNext()), packet);
    }

    private Step getNextStep(String message, boolean skipCreateEvent, Step next) {
      return skipCreateEvent
          ? next
          : Optional.ofNullable((message))
              .map(m -> createEventStep(new EventData(EventItem.DOMAIN_VALIDATION_ERROR, m), next))
              .orElse(next);
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
      Domain domain = info.getDomain();
      List<String> validationFailures = domain.getAfterIntrospectValidationFailures(packet);

      if (validationFailures.isEmpty()) {
        return doNext(packet);
      }

      LOGGER.severe(DOMAIN_VALIDATION_FAILED, domain.getDomainUid(), perLine(validationFailures));
      Step step = DomainStatusUpdater.createFailureRelatedSteps(BAD_DOMAIN, perLine(validationFailures), null);
      return doNext(step, packet);
    }

    private String perLine(List<String> validationFailures) {
      return String.join(lineSeparator(), validationFailures);
    }

  }
}

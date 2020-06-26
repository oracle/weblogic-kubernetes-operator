// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import oracle.kubernetes.operator.DomainStatusUpdater;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.work.NextAction;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.model.Cluster;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.KubernetesResourceLookup;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;

import static java.lang.System.lineSeparator;
import static oracle.kubernetes.operator.DomainStatusUpdater.BAD_DOMAIN;
import static oracle.kubernetes.operator.logging.MessageKeys.DOMAIN_VALIDATION_FAILED;

public class DomainValidationSteps {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static final String SECRETS = "secrets";
  private static final String CONFIGMAPS = "configmaps";

  public static Step createDomainValidationSteps(String namespace, Step next) {
    return Step.chain(createListSecretsStep(namespace), createListConfigMapsStep(namespace),
              new DomainValidationStep(next));
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
      return doNext(packet);
    }
  }

  private static Step createListConfigMapsStep(String domainNamespace) {
    return new CallBuilder().listConfigMapsAsync(domainNamespace, new ListConfigMapsResponseStep());
  }

  static class ListConfigMapsResponseStep extends DefaultResponseStep<V1ConfigMapList> {

    @Override
    public NextAction onSuccess(Packet packet, CallResponse<V1ConfigMapList> callResponse) {
      packet.put(CONFIGMAPS, callResponse.getResult().getItems());
      return doNext(packet);
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
      Step step = DomainStatusUpdater.createFailedStep(BAD_DOMAIN, perLine(validationFailures), null);
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


    private void logAndAddWarning(List<String> validationWarnings, String messageKey, Object... params) {
      LOGGER.warning(messageKey, params);
      validationWarnings.add(LOGGER.formatMessage(messageKey, params));
    }

    private void validate(DomainPresenceInfo info, WlsDomainConfig wlsDomainConfig) {
      List<String> validationWarnings = new ArrayList<>();

      Domain domain = info.getDomain();

      // log warnings for clusters that are specified in domain resource but not configured
      // in the WebLogic domain
      for (Cluster cluster : domain.getSpec().getClusters()) {
        if (!wlsDomainConfig.containsCluster(cluster.getClusterName())) {
          logAndAddWarning(validationWarnings, MessageKeys.NO_CLUSTER_IN_DOMAIN, cluster.getClusterName());
        }
      }
      // log warnings for managed servers that are specified in domain resource but not configured
      // in the WebLogic domain
      for (ManagedServer server : domain.getSpec().getManagedServers()) {
        if (!wlsDomainConfig.containsServer(server.getServerName())) {
          logAndAddWarning(validationWarnings, MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN, server.getServerName());
        }
      }
      info.clearValidationWarnings();
      for (String warning: validationWarnings) {
        info.addValidationWarning(warning);
      }
    }

    @Override
    public NextAction apply(Packet packet) {
      DomainPresenceInfo info = packet.getSpi(DomainPresenceInfo.class);
      WlsDomainConfig wlsDomainConfig = (WlsDomainConfig) packet.get(ProcessingConstants.DOMAIN_TOPOLOGY);
      validate(info, wlsDomainConfig);

      return doNext(packet);
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
}

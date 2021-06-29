// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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
import oracle.kubernetes.operator.MakeRightDomainOperation;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.calls.AsyncRequestStep;
import oracle.kubernetes.operator.calls.CallResponse;
import oracle.kubernetes.operator.helpers.EventHelper.EventData;
import oracle.kubernetes.operator.helpers.EventHelper.EventItem;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;
import oracle.kubernetes.operator.steps.DefaultResponseStep;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDynamicServersConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
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
      return Optional.ofNullable(getSecretsIfContinue(packet)).orElse(new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private static List<V1Secret> getSecretsIfContinue(Packet packet) {
      return packet.get(AsyncRequestStep.CONTINUE) != null ? (List<V1Secret>) packet.get(SECRETS) : null;
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
      return Optional.ofNullable(getConfigMapsIfContinue(packet)).orElse(new ArrayList<>());
    }

    @SuppressWarnings("unchecked")
    private static List<V1ConfigMap> getConfigMapsIfContinue(Packet packet) {
      return packet.get(AsyncRequestStep.CONTINUE) != null ? (List<V1ConfigMap>) packet.get(CONFIGMAPS) : null;
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

      // log warning if monitoring exporter port is specified and it conflicts with a server port
      Optional.ofNullable(domainSpec.getMonitoringExporterPort()).ifPresent(port -> {
        wlsDomainConfig.getServerConfigs().values()
            .forEach(server -> warnIfMonitoringExporterPortConflicts(port, server, info));
        wlsDomainConfig.getClusterConfigs().values()
            .forEach(cluster -> Optional.ofNullable(cluster.getDynamicServersConfig())
                .map(WlsDynamicServersConfig::getServerTemplate)
                .ifPresent(template -> warnIfMonitoringExporterPortConflicts(port, cluster, template, info)));
      });
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

    private void warnIfMonitoringExporterPortConflicts(
        Integer port, WlsServerConfig serverConfig, DomainPresenceInfo info) {
      warnIfMonitoringExporterPortConflicts(port, null, serverConfig, info);
    }

    private void warnIfMonitoringExporterPortConflicts(
        Integer port, WlsClusterConfig cluster, WlsServerConfig serverConfig, DomainPresenceInfo info) {

      if (port.equals(serverConfig.getListenPort())) {
        logAndAddValidationWarningExporter(port, cluster, serverConfig, serverConfig.getListenPort(), info);
      }
      if (port.equals(serverConfig.getSslListenPort())) {
        logAndAddValidationWarningExporter(port, cluster, serverConfig, serverConfig.getSslListenPort(), info);
      }
      if (port.equals(serverConfig.getAdminPort())) {
        logAndAddValidationWarningExporter(port, cluster, serverConfig, serverConfig.getAdminPort(), info);
      }
      Optional.ofNullable(serverConfig.getNetworkAccessPoints()).ifPresent(naps -> naps.forEach(nap -> {
        if (port.equals(nap.getListenPort())) {
          logAndAddValidationWarningExporter(port, cluster, serverConfig, nap.getListenPort(), info);
        }
      }));
    }

    private void logAndAddValidationWarningExporter(
        Integer port, WlsClusterConfig cluster, WlsServerConfig serverConfig,
        Integer conflictPort, DomainPresenceInfo info) {
      if (cluster != null) {
        logAndAddValidationWarning(info, MessageKeys.MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER,
            // Note: Using Integer.toString because default logger behavior formats with commas, e.g. "7,001"
            Integer.toString(port), cluster.getClusterName(), Integer.toString(conflictPort));
      } else {
        logAndAddValidationWarning(info, MessageKeys.MONITORING_EXPORTER_CONFLICT_SERVER,
            Integer.toString(port), serverConfig.getName(), Integer.toString(conflictPort));
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

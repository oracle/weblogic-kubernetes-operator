// Copyright (c) 2022, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;

import oracle.kubernetes.operator.DomainFailureMessages;
import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.tuning.TuningParameters;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDynamicServersConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.weblogic.domain.model.ManagedServer;
import oracle.kubernetes.weblogic.domain.model.MonitoringExporterSpecification;
import org.apache.commons.collections4.ListUtils;

import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_CLUSTER_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_SERVER_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER;
import static oracle.kubernetes.common.logging.MessageKeys.MONITORING_EXPORTER_CONFLICT_SERVER;
import static oracle.kubernetes.common.logging.MessageKeys.NO_AVAILABLE_PORT_TO_USE_FOR_REST;
import static oracle.kubernetes.common.logging.MessageKeys.NO_CLUSTER_IN_DOMAIN;
import static oracle.kubernetes.common.logging.MessageKeys.NO_MANAGED_SERVER_IN_DOMAIN;
import static oracle.kubernetes.common.logging.MessageKeys.TOO_MANY_REPLICAS_FAILURE;
import static oracle.kubernetes.operator.helpers.LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH;

public class WlsConfigValidator {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public static final String CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM = "clusterSizePaddingValidationEnabled";

  @FunctionalInterface
  interface NameGenerator {
    String toLegalName(String domainUid, String rawName);
  }

  private final String domainUid;
  private final List<String> topologyFailures = new ArrayList<>();
  private final DomainPresenceInfo info;
  private final WlsDomainConfig domainConfig;
  private LoggingFacade loggingFacade;

  private final GeneratedNameCheck clusterServiceNameCheck
        = new GeneratedNameCheck(LegalNames::toClusterServiceName, ILLEGAL_CLUSTER_SERVICE_NAME_LENGTH);
  private final GeneratedNameCheck externalServiceNameCheck
        = new GeneratedNameCheck(LegalNames::toExternalServiceName, ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH);
  private final GeneratedNameCheck serverServiceNameCheck
        = new GeneratedNameCheck(LegalNames::toServerServiceName, ILLEGAL_SERVER_SERVICE_NAME_LENGTH);

  private class GeneratedNameCheck {
    private final NameGenerator nameGenerator;
    private final String messageKey;
    private final int padding;

    GeneratedNameCheck(NameGenerator nameGenerator, String messageKey) {
      this(nameGenerator, messageKey, 0);
    }

    private GeneratedNameCheck(NameGenerator nameGenerator, String messageKey, int padding) {
      this.nameGenerator = nameGenerator;
      this.messageKey = messageKey;
      this.padding = padding;
    }

    private GeneratedNameCheck withPadding(int padding) {
      return new GeneratedNameCheck(nameGenerator, messageKey, padding);
    }

    private void check(String rawName) {
      final String legalName = nameGenerator.toLegalName(domainUid, rawName);

      if (legalName.length() > getLimit()) {
        reportFailure(messageKey, domainUid, rawName, getLimit() - numFormatChars());
      }
    }

    private int numFormatChars() {
      return nameGenerator.toLegalName("", "").length();
    }

    private int getLimit() {
      return LEGAL_DNS_LABEL_NAME_MAX_LENGTH - padding;
    }
  }

  WlsConfigValidator(Packet packet) {
    this.info = DomainPresenceInfo.fromPacket(packet).orElseThrow();
    this.domainConfig = packet.getValue(ProcessingConstants.DOMAIN_TOPOLOGY);
    this.domainUid = info.getDomainUid();
  }

  WlsConfigValidator loggingTo(LoggingFacade loggingFacade) {
    this.loggingFacade = loggingFacade;
    return this;
  }

  /**
   * Returns the failures from attempting to validate the topology.
   *
   * @return a list of strings that describe the failures.
   */
  List<String> getTopologyFailures() {
    if (domainConfig != null) {
      verifyDomainResourceReferences();
      verifyGeneratedResourceNames();
      verifyServerPorts();
      reportIfExporterPortConflicts();
    }
    return topologyFailures;
  }

  // Ensure that all clusters and servers configured by the domain resource actually exist in the topology.
  private void verifyDomainResourceReferences() {
    getManagedServers().stream()
          .map(ManagedServer::getServerName).filter(this::isUnknownServer).forEach(this::reportUnknownServer);

    info.getReferencedClusters().stream()
            .map(cluster -> Map.entry(cluster.getSpec().getClusterName(),
                    Objects.requireNonNull(cluster.getMetadata().getName())))
            .filter(entry -> isUnknownCluster(entry.getKey()))
            .forEach(entry -> reportUnknownCluster(entry.getKey(), entry.getValue()));

  }

  private List<ManagedServer> getManagedServers() {
    return info.getDomain().getSpec().getManagedServers();
  }

  private boolean isUnknownServer(String serverName) {
    return !domainConfig.containsServer(serverName);
  }

  private void reportUnknownServer(String serverName) {
    reportFailure(NO_MANAGED_SERVER_IN_DOMAIN, serverName);
  }

  private void reportFailure(String messageKey, Object... params) {
    topologyFailures.add(LOGGER.formatMessage(messageKey, params));
    if (loggingFacade != null) {
      loggingFacade.warning(messageKey, params);
    }
  }

  private boolean isUnknownCluster(String clusterName) {
    return !domainConfig.containsCluster(clusterName);
  }

  private void reportUnknownCluster(String wlsClusterName, String refClusterName) {
    reportFailure(NO_CLUSTER_IN_DOMAIN, wlsClusterName, refClusterName);
  }

  // Ensure that generated service names are valid.
  private void verifyGeneratedResourceNames() {
    serverServiceNameCheck.check(domainConfig.getAdminServerName());
    if (isExternalServiceConfigured()) {
      externalServiceNameCheck.check(domainConfig.getAdminServerName());
    }

    checkGeneratedStandaloneServerServiceNames();
    domainConfig.getClusterConfigs().values().forEach(this::checkGeneratedServerServiceNames);
    checkGeneratedClusterServiceNames();
  }

  private void checkGeneratedStandaloneServerServiceNames() {
    domainConfig.getServerConfigs().values().stream()
          .map(WlsServerConfig::getName)
          .forEach(serverServiceNameCheck::check);
  }

  private void checkGeneratedServerServiceNames(WlsClusterConfig wlsClusterConfig) {
    final GeneratedNameCheck check = serverServiceNameCheck.withPadding(getClusterSizePadding(wlsClusterConfig));
    wlsClusterConfig.getServerConfigs().stream().map(WlsServerConfig::getName).forEach(check::check);
  }

  // See documentation for tuning parameter: clusterSizePaddingValidationEnabled
  private int getClusterSizePadding(WlsClusterConfig wlsClusterConfig) {
    final int clusterSize = wlsClusterConfig.getServerConfigs().size();
    if (!isClusterSizePaddingValidationEnabled() || clusterSize <= 0 || clusterSize >= 100) {
      return 0;
    } else {
      return clusterSize >= 10 ? 1 : 2;
    }
  }

  private boolean isClusterSizePaddingValidationEnabled() {
    return "true".equalsIgnoreCase(getClusterSizePaddingValidationEnabledParameter());
  }

  private String getClusterSizePaddingValidationEnabledParameter() {
    return Optional.ofNullable(TuningParameters.getInstance())
          .map(t -> t.get(CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM))
          .orElse("true");
  }

  private void checkGeneratedClusterServiceNames() {
    domainConfig.getClusterConfigs().values().stream()
          .map(WlsClusterConfig::getName)
          .forEach(clusterServiceNameCheck::check);
  }

  private boolean isExternalServiceConfigured() {
    return info.getDomain().isExternalServiceConfigured();
  }

  private void verifyServerPorts() {
    domainConfig.getServerConfigs().values().forEach(this::checkServerPorts);
    domainConfig.getClusterConfigs().values().forEach(this::verifyClusteredServerPorts);
  }

  private void verifyClusteredServerPorts(WlsClusterConfig wlsClusterConfig) {
    wlsClusterConfig.getServerConfigs().forEach(this::checkServerPorts);
  }

  private void checkServerPorts(WlsServerConfig wlsServerConfig) {
    if (noAvailableAdminPort(wlsServerConfig)) {
      reportFailure(NO_AVAILABLE_PORT_TO_USE_FOR_REST, domainUid, wlsServerConfig.getName());
    }
  }

  private boolean noAvailableAdminPort(WlsServerConfig wlsServerConfig) {
    return wlsServerConfig.getAdminProtocolChannelName() == null;
  }

  private void reportIfExporterPortConflicts() {
    Optional.ofNullable(info.getDomain().getMonitoringExporterSpecification())
          .map(MonitoringExporterSpecification::getRestPort)
          .ifPresent(this::reportIfExporterPortConflicts);
  }

  private void reportIfExporterPortConflicts(int exporterPort) {
    domainConfig.getServerConfigs().values().forEach(server -> reportIfExporterPortConflicts(exporterPort, server));
    domainConfig.getClusterConfigs().values().forEach(cluster -> reportIfExporterPortConflicts(exporterPort, cluster));
  }

  private void reportIfExporterPortConflicts(int exporterPort, WlsServerConfig server) {
    if (isPortInUse(exporterPort, server)) {
      reportFailure(MONITORING_EXPORTER_CONFLICT_SERVER, exporterPort, server.getName());
    }
  }

  private void reportIfExporterPortConflicts(int exporterPort, WlsClusterConfig cluster) {
    cluster.getServers().forEach(server -> reportIfExporterPortConflicts(exporterPort, server));
    if (isPortInUseByDynamicServer(exporterPort, cluster)) {
      reportFailure(MONITORING_EXPORTER_CONFLICT_DYNAMIC_CLUSTER, exporterPort, cluster.getClusterName());
    }
  }

  @Nonnull
  private Boolean isPortInUseByDynamicServer(int exporterPort, WlsClusterConfig cluster) {
    return Optional.ofNullable(cluster.getDynamicServersConfig())
          .map(WlsDynamicServersConfig::getServerTemplate)
          .map(config -> isPortInUse(exporterPort, config)).orElse(false);
  }

  private boolean isPortInUse(int port, WlsServerConfig serverConfig) {
    return getDefinedPorts(serverConfig).stream().anyMatch(p -> p == port);
  }

  private List<Integer> getDefinedPorts(WlsServerConfig serverConfig) {
    return ListUtils.union(getStandardPorts(serverConfig), getNapPorts(serverConfig));
  }

  private List<Integer> getStandardPorts(WlsServerConfig serverConfig) {
    return getNonNullPorts(serverConfig.getListenPort(), serverConfig.getSslListenPort(), serverConfig.getAdminPort());
  }

  private List<Integer> getNonNullPorts(Integer... definedPorts) {
    return Arrays.stream(definedPorts).filter(Objects::nonNull).toList();
  }

  private List<Integer> getNapPorts(WlsServerConfig serverConfig) {
    return Optional.ofNullable(serverConfig.getNetworkAccessPoints()).orElse(List.of()).stream()
          .map(NetworkAccessPoint::getListenPort)
          .filter(Objects::nonNull)
          .toList();
  }

  List<String> getReplicaTooHighFailures() {
    final List<String> failures = new ArrayList<>();

    for (String clusterName : getTopologyClusterNames()) {
      new ClusterReplicaCheck(failures, clusterName).validateCluster();
    }
    return failures;
  }

  class ClusterReplicaCheck {
    private final List<String> failures;
    private final String clusterName;
    private final int replicaCount;
    private final int clusterSize;

    ClusterReplicaCheck(List<String> failures, String clusterName) {
      this.failures = failures;
      this.clusterName = clusterName;
      this.replicaCount = info.getReplicaCount(clusterName);
      this.clusterSize = domainConfig.getClusterConfig(clusterName).getClusterSize();
    }

    private void validateCluster() {
      if (replicaCount > clusterSize) {
        failures.add(DomainFailureMessages.createReplicaFailureMessage(clusterName, replicaCount, clusterSize));
        if (loggingFacade != null) {
          loggingFacade.warning(TOO_MANY_REPLICAS_FAILURE, replicaCount, clusterName, clusterSize);
        }
      }
    }

  }

  @Nonnull
  private String[] getTopologyClusterNames() {
    return Optional.ofNullable(domainConfig).map(WlsDomainConfig::getClusterNames).orElse(new String[0]);
  }
}

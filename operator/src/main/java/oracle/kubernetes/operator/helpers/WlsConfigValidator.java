// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import oracle.kubernetes.operator.ProcessingConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.wlsconfig.WlsClusterConfig;
import oracle.kubernetes.operator.wlsconfig.WlsDomainConfig;
import oracle.kubernetes.operator.wlsconfig.WlsServerConfig;
import oracle.kubernetes.operator.work.Packet;

import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_CLUSTER_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_EXTERNAL_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.ILLEGAL_SERVER_SERVICE_NAME_LENGTH;
import static oracle.kubernetes.common.logging.MessageKeys.NO_AVAILABLE_PORT_TO_USE_FOR_REST;
import static oracle.kubernetes.operator.helpers.LegalNames.LEGAL_DNS_LABEL_NAME_MAX_LENGTH;

public class WlsConfigValidator {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  public static final String CLUSTER_SIZE_PADDING_VALIDATION_ENABLED_PARAM = "clusterSizePaddingValidationEnabled";

  @FunctionalInterface
  interface NameGenerator {
    String toLegalName(String domainUid, String rawName);
  }

  private final String domainUid;
  private final List<String> failures = new ArrayList<>();
  private final DomainPresenceInfo info;
  private final WlsDomainConfig domainConfig;

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
        failures.add(LOGGER.formatMessage(messageKey, domainUid, rawName, legalName, getLimit()));
      }
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

  /**
   * Returns the failures from attempting to validate the topology.
   *
   * @return a list of strings that describe the failures.
   */
  List<String> getFailures() {
    verifyGeneratedResourceNames();
    verifyServerPorts();
    return failures;
  }

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
      failures.add(LOGGER.formatMessage(NO_AVAILABLE_PORT_TO_USE_FOR_REST, domainUid, wlsServerConfig.getName()));
    }
  }

  private boolean noAvailableAdminPort(WlsServerConfig wlsServerConfig) {
    return wlsServerConfig.getAdminProtocolChannelName() == null;
  }
}

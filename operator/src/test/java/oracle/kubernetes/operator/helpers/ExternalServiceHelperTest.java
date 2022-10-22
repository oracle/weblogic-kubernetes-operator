// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;

import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.wlsconfig.NetworkAccessPoint;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServiceConfigurator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static oracle.kubernetes.common.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_REPLACED;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ExternalServiceHelperTest extends ServiceHelperTest {

  private static final int NODE_PORT = 2300;
  private static final int LISTEN_PORT = 2100;

  public ExternalServiceHelperTest() {
    super(new ExternalServiceTestFacade());
  }

  static class ExternalServiceTestFacade extends TestFacade {
    ExternalServiceTestFacade() {
      getExpectedNodePorts().put("default", getTestNodePort());
      getExpectedNodePorts().put(LegalNames.toDns1123LegalName(getNap1()), getNap1NodePort());
      getExpectedNodePorts().put(LegalNames.toDns1123LegalName(getNap2()), getNapPort2());
    }

    @Override
    OperatorServiceType getType() {
      return OperatorServiceType.EXTERNAL;
    }

    @Override
    public String getServiceCreateLogMessage() {
      return EXTERNAL_CHANNEL_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return EXTERNAL_CHANNEL_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return EXTERNAL_CHANNEL_SERVICE_REPLACED;
    }

    @Override
    public String getServerName() {
      return getAdminServerName();
    }

    @Override
    public String getServiceName() {
      return LegalNames.toExternalServiceName(UID, getServerName());
    }

    @Override
    public Step createSteps(Step next) {
      return ServiceHelper.createForExternalServiceStep(next);
    }

    @Override
    public V1Service createServiceModel(Packet packet) {
      return ServiceHelper.createExternalServiceModel(packet);
    }

    @Override
    public V1Service getRecordedService(DomainPresenceInfo info) {
      return info.getExternalService(getServerName());
    }

    @Override
    public void recordService(DomainPresenceInfo info, V1Service service) {
      info.setExternalService(getServerName(), service);
    }

    @Override
    public Integer getExpectedListenPort() {
      return getAdminPort();
    }

    @Override
    public String getExpectedServiceType() {
      return "NodePort";
    }

    @Override
    ServiceConfigurator configureService(DomainPresenceInfo info, DomainConfigurator configurator) {
      return configurator.configureAdminServer().configureAdminService();
    }

    @Override
    String getExpectedSelectorValue() {
      return getServerName();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"http", "https", "tcp", "tls"})
  void whenOldStyledIstioUserDefinedPortAdded_createExternalPort(String protocol) {
    final String configuredChannelName = "istio";
    final String channelName = protocol + "=" + configuredChannelName;
    configureDomain()
        .configureAdminServer().configureAdminService().withChannel(channelName, NODE_PORT);
    getServerConfig().addNetworkAccessPoint(
        new NetworkAccessPoint(channelName, "t3", LISTEN_PORT, 0));

    final V1ServicePort createdPort = getServerPortWithName(channelName);
    assertThat(createdPort, notNullValue());
    assertThat(createdPort.getPort(), equalTo(LISTEN_PORT));
    assertThat(createdPort.getNodePort(), equalTo(NODE_PORT));
  }

  V1ServicePort getServerPortWithName(String name) {
    return getCreatedServicePorts().stream()
          .filter(p -> name.equals(p.getName()))
          .findFirst().orElse(null);
  }

  @Nonnull
  private List<V1ServicePort> getCreatedServicePorts() {
    return Optional.ofNullable(createService())
          .map(V1Service::getSpec)
          .map(V1ServiceSpec::getPorts)
          .orElse(Collections.emptyList());
  }

}


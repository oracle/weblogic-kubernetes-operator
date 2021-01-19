// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServiceConfigurator;

import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.EXTERNAL_CHANNEL_SERVICE_REPLACED;

public class ExternalServiceHelperTest extends ServiceHelperTest {

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
    public ServiceType getExpectedServiceType() {
      return ServiceType.NodePort;
    }

    @Override
    ServiceConfigurator configureService(DomainConfigurator configurator) {
      return configurator.configureAdminServer().configureAdminService();
    }

    @Override
    String getExpectedSelectorValue() {
      return getServerName();
    }
  }

}

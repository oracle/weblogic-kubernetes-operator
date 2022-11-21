// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.V1Service;
import oracle.kubernetes.operator.LabelConstants;
import oracle.kubernetes.operator.work.Packet;
import oracle.kubernetes.operator.work.Step;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.ServiceConfigurator;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_SERVICE_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_SERVICE_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.CLUSTER_SERVICE_REPLACED;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class ClusterServiceHelperTest extends ServiceHelperTest {

  public ClusterServiceHelperTest() {
    super(new ClusterServiceTestFacade());
  }

  static class ClusterServiceTestFacade extends TestFacade {
    ClusterServiceTestFacade() {
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName(getNap3()), getNapPort3());
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName(getNapSipClear()), getNapPortSipClear());
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName(getNapSipSecure()), getNapPortSipSecure());
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName("udp-" + getNapSipClear()), getNapPortSipClear());
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName("udp-" + getNapSipSecure()), getNapPortSipSecure());
    }

    @Override
    OperatorServiceType getType() {
      return OperatorServiceType.CLUSTER;
    }

    @Override
    public String getServiceCreateLogMessage() {
      return CLUSTER_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return CLUSTER_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return CLUSTER_SERVICE_REPLACED;
    }

    @Override
    public String getServerName() {
      return getManagedServerName();
    }

    @Override
    public String getServiceName() {
      return LegalNames.toClusterServiceName(UID, getTestCluster());
    }

    @Override
    public Step createSteps(Step next) {
      return ServiceHelper.createForClusterStep(next);
    }

    @Override
    public V1Service createServiceModel(Packet packet) {
      return ServiceHelper.createClusterServiceModel(packet);
    }

    @Override
    public V1Service getRecordedService(DomainPresenceInfo info) {
      return info.getClusterService(getTestCluster());
    }

    @Override
    public void recordService(DomainPresenceInfo info, V1Service service) {
      info.setClusterService(getTestCluster(), service);
    }

    @Override
    public Integer getExpectedListenPort() {
      return getTestPort();
    }

    @Override
    public Integer getExpectedAdminPort() {
      return getAdminPort();
    }

    @Override
    public ServiceConfigurator configureService(DomainPresenceInfo info, DomainConfigurator configurator) {
      return configurator.configureCluster(info, getTestCluster());
    }

    @Override
    String getExpectedSelectorKey() {
      return LabelConstants.CLUSTERNAME_LABEL;
    }

    @Override
    String getExpectedSelectorValue() {
      return getTestCluster();
    }
  }

  @Test
  void whenCreated_modelHasSessionAffinity() {
    V1Service model = createService();

    assertThat(
        model.getSpec().getSessionAffinity(),
        is("ClientIP"));
  }
}

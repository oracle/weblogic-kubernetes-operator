// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.openapi.models.V1Service;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.KubernetesConstants.DEFAULT_EXPORTER_SIDECAR_PORT;
import static oracle.kubernetes.operator.helpers.ServiceHelperTest.PortMatcher.containsPort;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_CREATED;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_EXISTS;
import static oracle.kubernetes.operator.logging.MessageKeys.MANAGED_SERVICE_REPLACED;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class ManagerServerServiceHelperTest extends ServiceHelperTest {

  public ManagerServerServiceHelperTest() {
    super(new ManagedServerTestFacade());
  }

  static class ManagedServerTestFacade extends ServiceHelperTest.ServerTestFacade {
    ManagedServerTestFacade() {
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName(getNap3()), getNapPort3());
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName(getNapSipClear()), getNapPortSipClear());
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName(getNapSipSecure()), getNapPortSipSecure());
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName("udp-" + getNapSipClear()), getNapPortSipClear());
      getExpectedNapPorts().put(LegalNames.toDns1123LegalName("udp-" + getNapSipSecure()), getNapPortSipSecure());
    }

    @Override
    public String getServiceCreateLogMessage() {
      return MANAGED_SERVICE_CREATED;
    }

    @Override
    public String getServiceExistsLogMessage() {
      return MANAGED_SERVICE_EXISTS;
    }

    @Override
    public String getServiceReplacedLogMessage() {
      return MANAGED_SERVICE_REPLACED;
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
    public String getServerName() {
      return getManagedServerName();
    }
  }

  @Test
  void whenDomainHasMonitoringExporterConfiguration_serviceHasExporterPort() {
    configureDomain().withMonitoringExporterConfiguration("queries:\n");

    V1Service service = createService();

    assertThat(service, containsPort("metrics", DEFAULT_EXPORTER_SIDECAR_PORT));
  }

  @Test
  void whenDomainHasMonitoringExporterConfigurationWithPort_serviceHasExporterPort() {
    configureDomain().withMonitoringExporterConfiguration("queries:\n").withMonitoringExporterPort(300);

    V1Service service = createService();

    assertThat(service, containsPort("metrics", 300));
  }

  @Test
  void whenDomainHasMonitoringExporterConfigurationAndIstio_serviceHasExporterPort() {
    configureDomain().withMonitoringExporterConfiguration("queries:\n").withIstio();

    V1Service service = createService();

    assertThat(service, containsPort("tcp-metrics", DEFAULT_EXPORTER_SIDECAR_PORT));
  }
}

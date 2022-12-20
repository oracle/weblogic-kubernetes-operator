// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import oracle.kubernetes.operator.DomainProcessorTestSetup;
import oracle.kubernetes.utils.TestUtils;
import oracle.kubernetes.weblogic.domain.DomainConfigurator;
import oracle.kubernetes.weblogic.domain.DomainConfiguratorFactory;
import oracle.kubernetes.weblogic.domain.model.DomainResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.common.logging.MessageKeys.CM_CREATED;
import static oracle.kubernetes.common.logging.MessageKeys.CM_EXISTS;
import static oracle.kubernetes.common.logging.MessageKeys.CM_REPLACED;
import static oracle.kubernetes.operator.helpers.StepContextConstants.FLUENTD_CONFIG_DATA_NAME;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class FluentdHelperTest {
  private static final String DOMAIN_NS = "namespace";

  private final KubernetesTestSupport testSupport = new KubernetesTestSupport();
  private final List<Memento> mementos = new ArrayList<>();
  private final List<LogRecord> logRecords = new ArrayList<>();
  private final DomainResource newDomain = DomainProcessorTestSetup.createTestDomain(2L);
  private final DomainConfigurator domainConfigurator = configureDomain(newDomain);


  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger()
            .collectLogMessages(logRecords, CM_CREATED, CM_EXISTS, CM_REPLACED)
            .withLogLevel(Level.FINE));
    mementos.add(testSupport.install());
  }

  @AfterEach
  public void tearDown() throws Exception {
    mementos.forEach(Memento::revert);

    testSupport.throwOnCompletionFailure();
  }

  @Test
  void whenUserSpecifyOwnFluentdConfig() {
    String data = "<match>me</match>";
    domainConfigurator
        .withFluentdConfiguration(true, "fluentd-cred",
            data, null, null);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    V1ConfigMap configMap = FluentdHelper.getFluentdConfigMap(info);

    assertThat(configMap.getData().get(FLUENTD_CONFIG_DATA_NAME), equalTo(data));
  }

  @Test
  void whenUserNotSpecifyOwnFluentdConfig() {
    domainConfigurator
        .withFluentdConfiguration(true, "fluentd-cred",
            null, null, null);
    DomainPresenceInfo info = new DomainPresenceInfo(newDomain);
    V1ConfigMap configMap = FluentdHelper.getFluentdConfigMap(info);

    assertThat(configMap.getData().get(FLUENTD_CONFIG_DATA_NAME), notNullValue());
    assertThat(configMap.getData().get(FLUENTD_CONFIG_DATA_NAME), startsWith("   <match fluent.**>"));
  }

  private DomainConfigurator configureDomain(DomainResource domain) {
    return DomainConfiguratorFactory.forDomain(domain);
  }

}

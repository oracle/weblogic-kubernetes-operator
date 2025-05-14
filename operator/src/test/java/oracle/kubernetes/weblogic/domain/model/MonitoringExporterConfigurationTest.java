// Copyright (c) 2021, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.weblogic.domain.model;

import java.util.ArrayList;
import java.util.List;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.jayway.jsonpath.matchers.JsonPathMatchers.hasJsonPath;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.junit.MatcherAssert.assertThat;

class MonitoringExporterConfigurationTest {

  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  void setUp() {
    mementos.add(TestUtils.silenceJsonPathLogger());
  }

  @AfterEach
  void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  void deserializeFromJson() {
    final MonitoringExporterConfiguration configuration = MonitoringExporterConfiguration.createFromYaml(CONFIG);

    final String jsonString = configuration.asJsonString();
    assertThat(jsonString, hasJsonPath("$.metricsNameSnakeCase", equalTo(true)));
    assertThat(jsonString, hasJsonPath("$.queries[0].applicationRuntimes.key", equalTo("name")));
    assertThat(jsonString, hasJsonPath("$.queries[0].applicationRuntimes.componentRuntimes.type", equalTo("WebAppComponentRuntime")));
    assertThat(configuration.matchesYaml(CONFIG), is(true));
  }

  private static final String CONFIG = """
          ---
          metricsNameSnakeCase: true
          queries:
          - applicationRuntimes:
              key: name
              componentRuntimes:
                type: WebAppComponentRuntime
                prefix: webapp_config_
                key: name
                values: [deploymentState, type, contextRoot, sourceInfo, openSessionsHighCount]
                stringValues:
                  state: [ok,failed,overloaded,critical,warn]
                servlets:
                  prefix: weblogic_servlet_
                  key: servletName
                  values: [invocationTotalCount, executionTimeTotal]
          """;

  @Test
  void matchVisuallyDifferentYaml() {
    final MonitoringExporterConfiguration configuration = MonitoringExporterConfiguration.createFromYaml(VERSION_1);

    assertThat(configuration.matchesYaml(VERSION_2), is(true));
  }

  private static final String VERSION_1 = """
          ---
          domainQualifier:  true
          queries:
          - group1:
            key: name
            keyName: groupName
            values: [left, middle, right]""";

  private static final String VERSION_2 = """
          ---
          host: localhost
          domainQualifier:  true
          queries:
          - group1:
            keyName: groupName
            key: name
            values: [left, middle, right]""";

}
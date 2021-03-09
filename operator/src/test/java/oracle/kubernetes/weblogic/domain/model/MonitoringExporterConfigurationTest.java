// Copyright (c) 2021, Oracle and/or its affiliates.
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

    assertThat(configuration.asJsonString(), hasJsonPath("$.metricsNameSnakeCase", equalTo(true)));
    assertThat(configuration.asJsonString(), hasJsonPath("$.queries[0].applicationRuntimes.key", equalTo("name")));
    assertThat(configuration.asJsonString(),
          hasJsonPath("$.queries[0].applicationRuntimes.componentRuntimes.type", equalTo("WebAppComponentRuntime")));
  }

  private static final String CONFIG = "---\n"
        + "metricsNameSnakeCase: true\n"
        + "queries:\n"
        + "- applicationRuntimes:\n"
        + "    key: name\n"
        + "    componentRuntimes:\n"
        + "      type: WebAppComponentRuntime\n"
        + "      prefix: webapp_config_\n"
        + "      key: name\n"
        + "      values: [deploymentState, type, contextRoot, sourceInfo, openSessionsHighCount]\n"
        + "      servlets:\n"
        + "        prefix: weblogic_servlet_\n"
        + "        key: servletName\n"
        + "        values: [invocationTotalCount, executionTimeTotal]\n";

  @Test
  void matchVisuallyDifferentYaml() {
    final MonitoringExporterConfiguration configuration = MonitoringExporterConfiguration.createFromYaml(VERSION_1);

    assertThat(configuration.matchesYaml(VERSION_2), is(true));
  }

  private static final String VERSION_1 = "---\n"
        + "domainQualifier:  true\n"
        + "queries:\n"
        + "- group1:\n"
        + "  key: name\n"
        + "  keyName: groupName\n"
        + "  values: [left, middle, right]";

  private static final String VERSION_2 = "---\n"
        + "host: localhost\n"
        + "domainQualifier:  true\n"
        + "queries:\n"
        + "- group1:\n"
        + "  keyName: groupName\n"
        + "  key: name\n"
        + "  values: [left, middle, right]";

}
// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

@SuppressWarnings("SameParameterValue")
class OperatorChartIT extends OperatorChartItBase {

  private static final InstallArgs NO_VALUES_INSTALL_ARGS = newInstallArgs(Collections.emptyMap());
  public static final String JVM_OPTIONS_DEFAULT = "-XshowSettings:vm -XX:MaxRAMPercentage=70";

  @Test
  void whenChartsGenerated_haveOneRoleBinding() throws Exception {
    ProcessedChart chart = getChart(NO_VALUES_INSTALL_ARGS);

    assertThat(chart.getDocuments("RoleBinding"), hasSize(1));
  }

  @Test
  void byDefault_operatorDeploymentContainerHasJavaLoggingLevel() throws Exception {
    assertThat(getEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "JAVA_LOGGING_LEVEL"), equalTo("INFO"));
  }

  @Test
  void byDefault_operatorDeploymentContainerHasJvmOptionsWithMaxRamPercentage() throws Exception {
    assertThat(getEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "JVM_OPTIONS"), equalTo(JVM_OPTIONS_DEFAULT));
  }

  @Test
  void whenChartChangesJvmOptions_changeDeploymentValue() throws Exception {
    final InstallArgs installArgs = newInstallArgs(Map.of("jvmOptions", "-override"));
    assertThat(getEnvironmentVariable(installArgs, "JVM_OPTIONS"), equalTo("-override"));
  }

  private String getEnvironmentVariable(InstallArgs installArgs, String name) throws Exception {
    final Map<String, Object> container = getOperatorDeploymentContainer(installArgs);
    final List<String> values = JsonPath.parse(container).read("$.env[?(@.name=='" + name + "')].value");
    return values.stream().findFirst().orElse(null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getOperatorDeploymentContainer(InstallArgs installArgs) throws Exception {
    final Map<String, Object> operatorDeployment = getOperatorDeployment(installArgs);

    final List<Object> c = JsonPath.parse(operatorDeployment)
        .read("$.spec.template.spec.containers[?(@.name=='weblogic-operator')]");
    return (Map<String, Object>) c.get(0);
  }

  private Map<String,Object> getOperatorDeployment(InstallArgs installArgs) throws Exception {
    return getChart(installArgs).getDocuments("Deployment").stream()
        .filter(this::nameIsWebLogicOperator)
        .findFirst()
        .orElse(Collections.emptyMap());
  }

  @SuppressWarnings("unchecked")
  private boolean nameIsWebLogicOperator(Map<String,Object> document) {
    Map<String,Object> metadata = (Map<String,Object>) document.get("metadata");
    return "weblogic-operator".equals(metadata.get("name"));
  }
}

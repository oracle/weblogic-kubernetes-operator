// Copyright (c) 2018, 2026, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helm;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

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
  void byDefault_operatorDeploymentContainerHasJavaLoggingSettings() throws Exception {
    assertThat(getEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "JAVA_LOGGING_LEVEL"), equalTo("INFO"));
    assertThat(getEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "JAVA_LOGGING_MAXSIZE"), equalTo("20000000"));
    assertThat(getEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "JAVA_LOGGING_COUNT"), equalTo("10"));
    assertThat(getEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "OPERATOR_LOGDIR"), equalTo("/logs"));
  }

  @Test
  void byDefault_webhookDeploymentContainerHasJavaLoggingSettings() throws Exception {
    assertThat(getWebhookEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "JAVA_LOGGING_LEVEL"), equalTo("INFO"));
    assertThat(getWebhookEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "JAVA_LOGGING_MAXSIZE"), equalTo("20000000"));
    assertThat(getWebhookEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "JAVA_LOGGING_COUNT"), equalTo("10"));
    assertThat(getWebhookEnvironmentVariable(NO_VALUES_INSTALL_ARGS, "OPERATOR_LOGDIR"), equalTo("/logs"));
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

  @Test
  void whenLocalDeveloperModeEnabled_operatorAndWebhookConfigMapsHaveValue() throws Exception {
    final InstallArgs installArgs =
        newInstallArgs(Map.of("domainOnPV", Map.of("localDeveloperMode", true)));

    assertThat(getConfigMapData(installArgs, "weblogic-operator-cm"),
        hasEntry("domainOnPVLocalDeveloperMode", "true"));
    assertThat(getConfigMapData(installArgs, "weblogic-webhook-cm"),
        hasEntry("domainOnPVLocalDeveloperMode", "true"));
  }

  @Test
  void byDefault_operatorAndWebhookDeploymentContainersMountWritableWorkDirectories() throws Exception {
    assertWritableEmptyDirMounted(getOperatorDeployment(NO_VALUES_INSTALL_ARGS),
        getOperatorDeploymentContainer(NO_VALUES_INSTALL_ARGS), "deployment-volume", "/deployment");
    assertWritableEmptyDirMounted(getWebhookDeployment(NO_VALUES_INSTALL_ARGS),
        getWebhookDeploymentContainer(NO_VALUES_INSTALL_ARGS), "deployment-volume", "/deployment");
    assertWritableEmptyDirMounted(getOperatorDeployment(NO_VALUES_INSTALL_ARGS),
        getOperatorDeploymentContainer(NO_VALUES_INSTALL_ARGS), "tmp-volume", "/tmp");
    assertWritableEmptyDirMounted(getWebhookDeployment(NO_VALUES_INSTALL_ARGS),
        getWebhookDeploymentContainer(NO_VALUES_INSTALL_ARGS), "tmp-volume", "/tmp");
  }

  @Test
  void whenListStrategyConfigured_webhookConfigMapHasDomainNamespaces() throws Exception {
    final InstallArgs installArgs =
        newInstallArgs(Map.of(
            "domainNamespaceSelectionStrategy", "List",
            "domainNamespaces", List.of("domain-ns-b", "domain-ns-a")));

    assertThat(getConfigMapData(installArgs, "weblogic-webhook-cm"),
        hasEntry("domainNamespaces", "domain-ns-a,domain-ns-b"));
  }

  @Test
  void whenLabelSelectorStrategyConfigured_webhookConfigMapHasDomainNamespaceLabelSelector() throws Exception {
    final InstallArgs installArgs =
        newInstallArgs(Map.of(
            "domainNamespaceSelectionStrategy", "LabelSelector",
            "domainNamespaceLabelSelector", "environment=dev"));

    assertThat(getConfigMapData(installArgs, "weblogic-webhook-cm"),
        hasEntry("domainNamespaceLabelSelector", "environment=dev"));
  }

  @Test
  void whenRegExpStrategyConfigured_webhookConfigMapHasDomainNamespaceRegExp() throws Exception {
    final InstallArgs installArgs =
        newInstallArgs(Map.of(
            "domainNamespaceSelectionStrategy", "RegExp",
            "domainNamespaceRegExp", "^domain-ns-[0-9]+$"));

    assertThat(getConfigMapData(installArgs, "weblogic-webhook-cm"),
        hasEntry("domainNamespaceRegExp", "^domain-ns-[0-9]+$"));
  }

  @Test
  void whenDedicatedStrategyConfigured_webhookConfigMapHasNoDomainNamespaceSelectionData() throws Exception {
    final InstallArgs installArgs =
        newInstallArgs(Map.of(
            "domainNamespaceSelectionStrategy", "Dedicated",
            "domainNamespaces", List.of("domain-ns-a"),
            "domainNamespaceLabelSelector", "environment=dev",
            "domainNamespaceRegExp", "^domain-ns-[0-9]+$"));

    assertThat(getConfigMapData(installArgs, "weblogic-webhook-cm"), not(hasKey("domainNamespaceSelectionStrategy")));
    assertThat(getConfigMapData(installArgs, "weblogic-webhook-cm"), not(hasKey("domainNamespaces")));
    assertThat(getConfigMapData(installArgs, "weblogic-webhook-cm"), not(hasKey("domainNamespaceLabelSelector")));
    assertThat(getConfigMapData(installArgs, "weblogic-webhook-cm"), not(hasKey("domainNamespaceRegExp")));
  }

  @Test
  void whenDedicatedStrategyConfigured_webhookDeploymentHasDedicatedMode() throws Exception {
    final InstallArgs installArgs =
        newInstallArgs(Map.of("domainNamespaceSelectionStrategy", "Dedicated"));

    assertThat(getWebhookEnvironmentVariable(installArgs, "WEBHOOK_DEDICATED_MODE"), equalTo("true"));
  }

  private String getEnvironmentVariable(InstallArgs installArgs, String name) throws Exception {
    final Map<String, Object> container = getOperatorDeploymentContainer(installArgs);
    final List<String> values = JsonPath.parse(container).read("$.env[?(@.name=='" + name + "')].value");
    return values.stream().findFirst().orElse(null);
  }

  private String getWebhookEnvironmentVariable(InstallArgs installArgs, String name) throws Exception {
    final Map<String, Object> container = getWebhookDeploymentContainer(installArgs);
    final List<String> values = JsonPath.parse(container).read("$.env[?(@.name=='" + name + "')].value");
    return values.stream().findFirst().orElse(null);
  }

  private void assertWritableEmptyDirMounted(Map<String,Object> deployment, Map<String, Object> container,
                                            String volumeName, String mountPath) {
    assertThat(getVolumeMountPath(container, volumeName), equalTo(mountPath));
    assertThat(getEmptyDirVolumes(deployment, volumeName), hasSize(1));
  }

  private String getVolumeMountPath(Map<String, Object> container, String volumeName) {
    final List<String> values = JsonPath.parse(container)
        .read("$.volumeMounts[?(@.name=='" + volumeName + "')].mountPath");
    return values.stream().findFirst().orElse(null);
  }

  private List<Object> getEmptyDirVolumes(Map<String,Object> deployment, String volumeName) {
    return JsonPath.parse(deployment).read("$.spec.template.spec.volumes[?(@.name=='" + volumeName + "')].emptyDir");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getConfigMapData(InstallArgs installArgs, String name) throws Exception {
    return (Map<String, Object>) getChart(installArgs).getDocuments("ConfigMap").stream()
        .filter(configMap -> name.equals(JsonPath.read(configMap, "$.metadata.name")))
        .findFirst()
        .map(configMap -> configMap.get("data"))
        .orElse(Collections.emptyMap());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getOperatorDeploymentContainer(InstallArgs installArgs) throws Exception {
    final Map<String, Object> operatorDeployment = getOperatorDeployment(installArgs);

    final List<Object> c = JsonPath.parse(operatorDeployment)
        .read("$.spec.template.spec.containers[?(@.name=='weblogic-operator')]");
    return (Map<String, Object>) c.get(0);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getWebhookDeploymentContainer(InstallArgs installArgs) throws Exception {
    final Map<String, Object> webhookDeployment = getWebhookDeployment(installArgs);

    final List<Object> c = JsonPath.parse(webhookDeployment)
        .read("$.spec.template.spec.containers[?(@.name=='weblogic-operator-webhook')]");
    return (Map<String, Object>) c.get(0);
  }

  private Map<String,Object> getOperatorDeployment(InstallArgs installArgs) throws Exception {
    return getDeployment(installArgs, "weblogic-operator");
  }

  private Map<String,Object> getWebhookDeployment(InstallArgs installArgs) throws Exception {
    return getDeployment(installArgs, "weblogic-operator-webhook");
  }

  private Map<String,Object> getDeployment(InstallArgs installArgs, String name) throws Exception {
    return getChart(installArgs).getDocuments("Deployment").stream()
        .filter(document -> nameIs(document, name))
        .findFirst()
        .orElse(Collections.emptyMap());
  }

  @SuppressWarnings("unchecked")
  private boolean nameIs(Map<String,Object> document, String name) {
    Map<String,Object> metadata = (Map<String,Object>) document.get("metadata");
    return name.equals(metadata.get("name"));
  }
}

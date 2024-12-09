// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import java.util.List;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1Capabilities;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentStrategy;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1ExecAction;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1Lifecycle;
import io.kubernetes.client.openapi.models.V1LifecycleHandler;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1PolicyRule;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1SeccompProfile;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.utils.GeneratedOperatorObjects;
import oracle.kubernetes.operator.utils.KubernetesArtifactUtils;
import oracle.kubernetes.operator.utils.OperatorValues;
import oracle.kubernetes.operator.utils.OperatorYamlFactory;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static oracle.kubernetes.operator.LabelConstants.APP_LABEL;
import static oracle.kubernetes.operator.LabelConstants.OPERATORNAME_LABEL;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterRole;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterRoleBinding;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newClusterRoleRef;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newConfigMap;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newConfigMapVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newContainer;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newDeployment;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newDeploymentSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newEnvVar;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newEnvVarSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newExecAction;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newNamespace;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newObjectFieldSelector;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newObjectMeta;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPodSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPodTemplateSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newPolicyRule;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newProbe;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newRole;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newRoleBinding;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newRoleRef;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newSecret;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newSecretVolumeSource;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newService;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServiceAccount;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServicePort;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newServiceSpec;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newSubject;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newVolume;
import static oracle.kubernetes.operator.utils.KubernetesArtifactUtils.newVolumeMount;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

/**
 * Base class for testing that the all artifacts in the yaml files that create-weblogic-operator.sh
 * generates.
 */
abstract class CreateOperatorGeneratedFilesTestBase {

  private static OperatorValues inputs;
  private static GeneratedOperatorObjects generatedFiles;

  protected static OperatorValues getInputs() {
    return inputs;
  }

  private static GeneratedOperatorObjects getGeneratedFiles() {
    return generatedFiles;
  }

  static void setup(OperatorYamlFactory factory, OperatorValues val) throws Exception {
    inputs = val;
    generatedFiles = factory.generate(val);
  }

  @Test
  void generatesCorrect_operatorConfigMap() {
    assertThat(
        getActualWeblogicOperatorConfigMap(), equalTo(getExpectedWeblogicOperatorConfigMap()));
  }

  private V1ConfigMap getActualWeblogicOperatorConfigMap() {
    return getGeneratedFiles().getOperatorConfigMap();
  }

  private V1ConfigMap getExpectedWeblogicOperatorConfigMap() {
    V1ConfigMap v1ConfigMap =
        newConfigMap()
            .metadata(
                newObjectMeta()
                    .name("weblogic-operator-cm")
                    .namespace(getInputs().getNamespace())
                    .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
            .putDataItem("helmChartVersion", "4.3.0-RELEASE-MARKER")
            .putDataItem("serviceaccount", getInputs().getServiceAccount())
            .putDataItem("domainNamespaceSelectionStrategy", getInputs().getDomainNamespaceSelectionStrategy())
            .putDataItem("domainNamespaces", getInputs().getDomainNamespaces())
            .putDataItem("introspectorJobNameSuffix", "-introspector")
            .putDataItem("externalServiceNameSuffix", "-ext")
            .putDataItem("clusterSizePaddingValidationEnabled", "true");

    if (StringUtils.isNotEmpty(getInputs().getDomainNamespaceLabelSelector())) {
      v1ConfigMap.putDataItem("domainNamespaceLabelSelector", getInputs().getDomainNamespaceLabelSelector());
    }
    if (StringUtils.isNotEmpty(getInputs().getDomainNamespaceRegExp())) {
      v1ConfigMap.putDataItem("domainNamespaceRegExp", getInputs().getDomainNamespaceRegExp());
    }

    if (expectExternalCredentials()) {
      v1ConfigMap.putDataItem(
          "externalOperatorCert",
          Base64.encodeBase64String(getExpectedExternalWeblogicOperatorCert().getBytes()));
    }
    return v1ConfigMap;
  }

  protected abstract String getExpectedExternalWeblogicOperatorCert();

  @Test
  void generatesCorrect_operatorSecrets() {
    if (expectExternalCredentials()) {
      assertThat(
          new String(getActualWeblogicOperatorSecrets().getData().get("externalOperatorKey")),
          equalTo(new String(getExpectedWeblogicOperatorSecrets().getData().get("externalOperatorKey"))));
    } else {
      assertThat(getActualWeblogicOperatorSecrets().getData(), not(hasKey("externalOperatorKey")));
    }
  }

  private V1Secret getActualWeblogicOperatorSecrets() {
    return getGeneratedFiles().getOperatorSecrets();
  }

  private V1Secret getExpectedWeblogicOperatorSecrets() {
    V1Secret v1Secret =
        newSecret()
            .metadata(
                newObjectMeta()
                    .name("weblogic-operator-secrets")
                    .namespace(getInputs().getNamespace())
                    .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
            .type("Opaque");
    if (expectExternalCredentials()) {
      v1Secret.putDataItem(
          "externalOperatorKey", getExpectedExternalWeblogicOperatorKey().getBytes());
    }
    return v1Secret;
  }

  private boolean expectExternalCredentials() {
    return isExternalRestPortEnabled();
  }

  private boolean isExternalRestPortEnabled() {
    return Boolean.parseBoolean(getInputs().getExternalRestEnabled());
  }

  protected abstract String getExpectedExternalWeblogicOperatorKey();

  @Test
  void generatesCorrect_operatorDeployment() {
    assertThat(
        getActualWeblogicOperatorDeployment(),
        equalTo(getExpectedWeblogicOperatorDeployment()));
  }

  private V1Deployment getActualWeblogicOperatorDeployment() {
    return getGeneratedFiles().getOperatorDeployment();
  }

  protected V1Deployment getExpectedWeblogicOperatorDeployment() {
    return newDeployment()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(
            newDeploymentSpec()
                .selector(new V1LabelSelector()
                    .putMatchLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
                .replicas(1)
                .strategy(new V1DeploymentStrategy()
                    .type("Recreate"))
                .template(
                    newPodTemplateSpec()
                        .metadata(
                            newObjectMeta()
                                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace())
                                .putLabelsItem(APP_LABEL, "weblogic-operator")
                                .putAnnotationsItem("prometheus.io/port", "8083")
                                .putAnnotationsItem("prometheus.io/scrape", "true"))
                        .spec(
                            newPodSpec()
                                .serviceAccountName(getInputs().getServiceAccount())
                                .securityContext(new V1PodSecurityContext().seccompProfile(
                                    new V1SeccompProfile().type("RuntimeDefault")))
                                .addContainersItem(
                                    newContainer()
                                        .name("weblogic-operator")
                                        .image(getInputs().getWeblogicOperatorImage())
                                        .imagePullPolicy(
                                            getInputs().getWeblogicOperatorImagePullPolicy())
                                        .addCommandItem("/deployment/operator.sh")
                                        .lifecycle(
                                            new V1Lifecycle().preStop(new V1LifecycleHandler().exec(
                                                new V1ExecAction().addCommandItem("/deployment/stop.sh"))))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("OPERATOR_NAMESPACE")
                                                .valueFrom(
                                                    newEnvVarSource()
                                                        .fieldRef(
                                                            newObjectFieldSelector()
                                                                .fieldPath("metadata.namespace"))))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("OPERATOR_POD_NAME")
                                                .valueFrom(new V1EnvVarSource()
                                                    .fieldRef(newObjectFieldSelector()
                                                        .fieldPath("metadata.name"))))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("OPERATOR_POD_UID")
                                                .valueFrom(new V1EnvVarSource()
                                                    .fieldRef(newObjectFieldSelector()
                                                        .fieldPath("metadata.uid"))))
                                        .addEnvItem(
                                            newEnvVar().name("OPERATOR_VERBOSE").value("false"))
                                        .addEnvItem(
                                            newEnvVar().name("ENABLE_REST_ENDPOINT").value("true"))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("JAVA_LOGGING_LEVEL")
                                                .value(getInputs().getJavaLoggingLevel()))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("JAVA_LOGGING_MAXSIZE")
                                                .value("20000000"))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("JAVA_LOGGING_COUNT")
                                                .value("10"))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("JVM_OPTIONS")
                                                .value("-XX:MaxRAMPercentage=70"))
                                        .resources(
                                            new V1ResourceRequirements()
                                                .putRequestsItem("cpu", Quantity.fromString("250m"))
                                                .putRequestsItem(
                                                    "memory", Quantity.fromString("512Mi")))
                                        .securityContext(
                                            new V1SecurityContext().runAsUser(1000L)
                                                .runAsNonRoot(true)
                                                .privileged(false).allowPrivilegeEscalation(false)
                                                .capabilities(new V1Capabilities().addDropItem("ALL")))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-operator-cm-volume")
                                                .mountPath("/deployment/config"))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-operator-debug-cm-volume")
                                                .mountPath("/deployment/debug-config"))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-operator-secrets-volume")
                                                .mountPath("/deployment/secrets")
                                                .readOnly(true)))
                                .addVolumesItem(
                                    newVolume()
                                        .name("weblogic-operator-cm-volume")
                                        .configMap(
                                            newConfigMapVolumeSource()
                                                .name("weblogic-operator-cm")))
                                .addVolumesItem(
                                    newVolume()
                                        .name("weblogic-operator-debug-cm-volume")
                                        .configMap(
                                            newConfigMapVolumeSource()
                                                .optional(Boolean.TRUE)
                                                .name("weblogic-operator-debug-cm")))
                                .addVolumesItem(
                                    newVolume()
                                        .name("weblogic-operator-secrets-volume")
                                        .secret(
                                            newSecretVolumeSource()
                                                .secretName("weblogic-operator-secrets"))))));
  }

  void expectProbes(V1Container container) {
    container
        .livenessProbe(createProbe(40, 10, 5, "/probes/livenessProbe.sh"))
        .readinessProbe(createProbe(2, 10, null, "/probes/readinessProbe.sh"));
  }

  private V1Probe createProbe(Integer initialDelaySeconds, Integer periodSeconds,
                              Integer failureThreshold, String shellScript) {
    return newProbe()
        .initialDelaySeconds(initialDelaySeconds)
        .periodSeconds(periodSeconds)
        .failureThreshold(failureThreshold)
        .exec(newExecAction().addCommandItem(shellScript));
  }

  @Test
  void generatesCorrect_externalWeblogicOperatorService() {
    V1Service expected = getExpectedExternalWeblogicOperatorService();
    if (expected != null) {
      assertThat(getGeneratedFiles().getExternalOperatorService(), equalTo(expected));
    }
  }

  protected abstract V1Service getExpectedExternalWeblogicOperatorService();

  V1Service getExpectedExternalWeblogicOperatorService(
      boolean debuggingEnabled, boolean externalRestEnabled) {
    if (!debuggingEnabled && !externalRestEnabled) {
      return null;
    }
    V1ServiceSpec spec =
        newServiceSpec().type("NodePort").putSelectorItem(APP_LABEL, "weblogic-operator");
    if (externalRestEnabled) {
      spec.addPortsItem(newServicePort()
              .name("rest")
              .port(8081)
              .appProtocol("https")
              .nodePort(Integer.parseInt(getInputs().getExternalRestHttpsPort())));
    }
    if (debuggingEnabled) {
      spec.addPortsItem(
          newServicePort()
              .name("debug")
              .port(Integer.parseInt(getInputs().getInternalDebugHttpPort()))
              .appProtocol("http")
              .nodePort(Integer.parseInt(getInputs().getExternalDebugHttpPort())));
    }
    return newService()
        .metadata(
            newObjectMeta()
                .name("external-weblogic-operator-svc")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(spec);
  }

  @Test
  void generatesCorrect_internalWeblogicOperatorService() {
    assertThat(
        getActualInternalWeblogicOperatorService(),
        equalTo(getExpectedInternalWeblogicOperatorService()));
  }

  private V1Service getActualInternalWeblogicOperatorService() {
    return getGeneratedFiles().getInternalOperatorService();
  }

  private V1Service getExpectedInternalWeblogicOperatorService() {
    return newService()
        .metadata(
            newObjectMeta()
                .name("internal-weblogic-operator-svc")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(
            newServiceSpec()
                .type("ClusterIP")
                .putSelectorItem(APP_LABEL, "weblogic-operator")
                .addPortsItem(newServicePort().name("rest").appProtocol("https").port(8082))
                .addPortsItem(newServicePort().name("metrics").appProtocol("http").port(8083)));
  }

  @Test
  protected void generatesCorrect_weblogicOperatorNamespace() {
    assertThat(
        getActualWeblogicOperatorNamespace(), equalTo(getExpectedWeblogicOperatorNamespace()));
  }

  private V1Namespace getActualWeblogicOperatorNamespace() {
    return getGeneratedFiles().getOperatorNamespace();
  }

  private V1Namespace getExpectedWeblogicOperatorNamespace() {
    return newNamespace()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace())
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()));
  }

  @Test
  protected void generatesCorrect_weblogicOperatorServiceAccount() {
    assertThat(
        getActualWeblogicOperatorServiceAccount(),
        equalTo(getExpectedWeblogicOperatorServiceAccount()));
  }

  private V1ServiceAccount getActualWeblogicOperatorServiceAccount() {
    return getGeneratedFiles().getOperatorServiceAccount();
  }

  private V1ServiceAccount getExpectedWeblogicOperatorServiceAccount() {
    return newServiceAccount()
        .metadata(
            newObjectMeta()
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()));
  }

  @Test
  void generatesCorrect_weblogicOperatorClusterRole() {
    assertThat(
        getActualWeblogicOperatorClusterRole(),
        equalTo(getExpectedWeblogicOperatorClusterRole()));
  }

  private V1ClusterRole getActualWeblogicOperatorClusterRole() {
    return getGeneratedFiles().getWeblogicOperatorClusterRole();
  }

  private V1ClusterRole getExpectedWeblogicOperatorClusterRole() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-general")
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(singletonList("namespaces"))
                .verbs(asList("get", "list", "watch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("apiextensions.k8s.io")
                .addResourcesItem("customresourcedefinitions")
                .verbs(
                    asList(
                        "get",
                        "list",
                        "watch",
                        "create",
                        "update",
                        "patch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(singletonList("persistentvolumes"))
                .verbs(asList("get", "list", "create")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("weblogic.oracle")
                .addResourcesItem("domains")
                .addResourcesItem("clusters")
                .addResourcesItem("domains/status")
                .addResourcesItem("clusters/status")
                .verbs(asList("get", "create", "list", "watch", "update", "patch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("authentication.k8s.io")
                .addResourcesItem("tokenreviews")
                .verbs(singletonList("create")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("authorization.k8s.io")
                .resources(singletonList("selfsubjectrulesreviews"))
                .verbs(singletonList("create")))
        .addRulesItem(newPolicyRuleForValidatingWebhookConfiguration());
  }

  @Test
  void generatesCorrect_weblogicOperatorClusterRoleNonResource() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorClusterRoleNonResource(),
        equalTo(getExpectedWeblogicOperatorClusterRoleNonResource()));
  }

  private V1ClusterRole getExpectedWeblogicOperatorClusterRoleNonResource() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-nonresource")
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(newPolicyRule().addNonResourceURLsItem("/version/*").addVerbsItem("get"));
  }

  @Test
  void generatesCorrect_operatorRoleBinding() {
    assertThat(
        getGeneratedFiles().getOperatorRoleBinding(),
        equalTo(
            newClusterRoleBinding()
                .metadata(
                    newObjectMeta()
                        .name(
                            getInputs().getNamespace()
                                + "-weblogic-operator-clusterrolebinding-general")
                        .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
                .addSubjectsItem(
                    newSubject()
                        .kind("ServiceAccount")
                        .name(getInputs().getServiceAccount())
                        .namespace(getInputs().getNamespace())
                        .apiGroup(""))
                .roleRef(
                    newClusterRoleRef()
                        .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-general")
                        .apiGroup(KubernetesArtifactUtils.API_GROUP_RBAC))));
  }

  @Test
  void generatesCorrect_operatorRoleBindingNonResource() {
    assertThat(
        getActualOperatorRoleBindingNonResource(),
        equalTo(getExpectedOperatorRoleBindingNonResource()));
  }

  private V1ClusterRoleBinding getActualOperatorRoleBindingNonResource() {
    return getGeneratedFiles().getOperatorRoleBindingNonResource();
  }

  private V1ClusterRoleBinding getExpectedOperatorRoleBindingNonResource() {
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(
                    getInputs().getNamespace()
                        + "-weblogic-operator-clusterrolebinding-nonresource")
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(
            newClusterRoleRef()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-nonresource")
                .apiGroup(KubernetesArtifactUtils.API_GROUP_RBAC));
  }

  @Test
  void generatesCorrect_weblogicOperatorNamespaceRole() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorNamespaceRole(),
        equalTo(getExpectedWeblogicOperatorNamespaceRole()));
  }

  private V1ClusterRole getExpectedWeblogicOperatorNamespaceRole() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-namespace")
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(
                    asList(
                        "services",
                        "configmaps",
                        "pods",
                        "events"))
                .verbs(
                    asList(
                        "get",
                        "list",
                        "watch",
                        "create",
                        "update",
                        "patch",
                        "delete",
                        "deletecollection")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(singletonList("secrets"))
                .verbs(asList("get", "list", "watch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(singletonList("persistentvolumeclaims"))
                .verbs(asList("get", "list", "create")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(singletonList("pods/log"))
                .verbs(asList("get", "list")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(singletonList("pods/exec"))
                .verbs(asList("get", "create")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("batch")
                .resources(singletonList("jobs"))
                .verbs(
                    asList(
                        "get",
                        "list",
                        "watch",
                        "create",
                        "update",
                        "patch",
                        "delete",
                        "deletecollection")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("policy")
                .resources(singletonList("poddisruptionbudgets"))
                .verbs(
                    asList(
                        "get",
                        "list",
                        "watch",
                        "create",
                        "update",
                        "patch",
                        "delete",
                        "deletecollection")));
  }

  @Test
  void generatesCorrect_domainNamespaces_weblogicOperatorRoleBindings() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorClusterRoleBinding(),
        equalTo(getExpectedWeblogicOperatorClusterRoleBinding()));
  }

  private V1ClusterRoleBinding getExpectedWeblogicOperatorClusterRoleBinding() {
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrolebinding-namespace")
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(
            newClusterRoleRef()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-namespace")
                .apiGroup(KubernetesArtifactUtils.API_GROUP_RBAC));
  }

  private V1RoleBinding getExpectedWeblogicOperatorRoleBinding() {
    return newRoleBinding()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-rolebinding")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(
            newRoleRef()
                .name("weblogic-operator-role")
                .apiGroup(KubernetesArtifactUtils.API_GROUP_RBAC));
  }

  @Test
  void generatesCorrect_weblogicOperatorRole() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorRole(),
        equalTo(getExpectedWeblogicOperatorRole()));
  }

  private V1Role getExpectedWeblogicOperatorRole() {
    return newRole()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-role")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(asList("events", "secrets", "configmaps"))
                .verbs(
                    asList(
                        "get",
                        "list",
                        "watch",
                        "create",
                        "update",
                        "patch",
                        "delete",
                        "deletecollection")))
        .addRulesItem(
            newPolicyRuleForValidatingWebhookConfiguration());
  }

  private V1PolicyRule newPolicyRuleForValidatingWebhookConfiguration() {
    return newPolicyRule()
        .addApiGroupsItem("admissionregistration.k8s.io")
        .resources(List.of("validatingwebhookconfigurations"))
        .verbs(
            asList(
                "get",
                "create",
                "update",
                "patch",
                "delete"));
  }

  @Test
  void generatesCorrect_weblogicOperatorRoleBinding() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorRoleBinding(),
        equalTo(getExpectedWeblogicOperatorRoleBinding()));
  }

  @SuppressWarnings("unused")
  protected V1Service getExpectedExternalOperatorService(
      boolean debuggingEnabled, boolean externalRestEnabled) {
    V1ServiceSpec spec =
        newServiceSpec().type("NodePort").putSelectorItem(APP_LABEL, "weblogic-operator");
    if (externalRestEnabled) {
      spec.addPortsItem(
          newServicePort()
              .name("rest")
              .port(8081)
              .nodePort(Integer.parseInt(inputs.getExternalRestHttpsPort())));
    }
    if (debuggingEnabled) {
      spec.addPortsItem(
          newServicePort()
              .name("debug")
              .port(Integer.parseInt(inputs.getInternalDebugHttpPort()))
              .nodePort(Integer.parseInt(inputs.getExternalDebugHttpPort())));
    }
    return newService()
        .metadata(
            newObjectMeta()
                .name("external-weblogic-operator-svc")
                .namespace(inputs.getNamespace())
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(spec);
  }

  protected void expectRemoteDebug(V1Container operatorContainer, String debugSuspend) {
    operatorContainer.addEnvItem(
        newEnvVar().name("REMOTE_DEBUG_PORT").value(getInputs().getInternalDebugHttpPort()));
    operatorContainer.addEnvItem(newEnvVar().name("DEBUG_SUSPEND").value(debugSuspend));
  }
}

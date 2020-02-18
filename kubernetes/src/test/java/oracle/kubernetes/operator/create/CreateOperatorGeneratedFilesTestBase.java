// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1Probe;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Role;
import io.kubernetes.client.openapi.models.V1RoleBinding;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.kubernetes.operator.utils.GeneratedOperatorObjects;
import oracle.kubernetes.operator.utils.KubernetesArtifactUtils;
import oracle.kubernetes.operator.utils.OperatorValues;
import oracle.kubernetes.operator.utils.OperatorYamlFactory;
import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static oracle.kubernetes.operator.LabelConstants.APP_LABEL;
import static oracle.kubernetes.operator.LabelConstants.OPERATORNAME_LABEL;
import static oracle.kubernetes.operator.LabelConstants.RESOURCE_VERSION_LABEL;
import static oracle.kubernetes.operator.VersionConstants.OPERATOR_V2;
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
import static oracle.kubernetes.operator.utils.YamlUtils.yamlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

/**
 * Base class for testing that the all artifacts in the yaml files that create-weblogic-operator.sh
 * generates
 */
public abstract class CreateOperatorGeneratedFilesTestBase {

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
  public void generatesCorrect_operatorConfigMap() {
    assertThat(
        getActualWeblogicOperatorConfigMap(), yamlEqualTo(getExpectedWeblogicOperatorConfigMap()));
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
                    .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                    .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
            .putDataItem("dedicated", getInputs().getDedicated())
            .putDataItem("serviceaccount", getInputs().getServiceAccount())
            .putDataItem("targetNamespaces", getInputs().getTargetNamespaces());
    if (expectExternalCredentials()) {
      v1ConfigMap.putDataItem(
          "externalOperatorCert",
          Base64.encodeBase64String(getExpectedExternalWeblogicOperatorCert().getBytes()));
    }
    return v1ConfigMap;
  }

  protected abstract String getExpectedExternalWeblogicOperatorCert();

  @Test
  public void generatesCorrect_operatorSecrets() {
    assertThat(
        getActualWeblogicOperatorSecrets(), yamlEqualTo(getExpectedWeblogicOperatorSecrets()));
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
                    .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
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
  public void generatesCorrect_operatorDeployment() {
    assertThat(
        getActualWeblogicOperatorDeployment(),
        yamlEqualTo(getExpectedWeblogicOperatorDeployment()));
  }

  private ExtensionsV1beta1Deployment getActualWeblogicOperatorDeployment() {
    return getGeneratedFiles().getOperatorDeployment();
  }

  protected ExtensionsV1beta1Deployment getExpectedWeblogicOperatorDeployment() {
    return newDeployment()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(
            newDeploymentSpec()
                .selector(new V1LabelSelector()
                    .putMatchLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                    .putMatchLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
                .replicas(1)
                .template(
                    newPodTemplateSpec()
                        .metadata(
                            newObjectMeta()
                                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace())
                                .putLabelsItem(APP_LABEL, "weblogic-operator"))
                        .spec(
                            newPodSpec()
                                .serviceAccountName(getInputs().getServiceAccount())
                                .addContainersItem(
                                    newContainer()
                                        .name("weblogic-operator")
                                        .image(getInputs().getWeblogicOperatorImage())
                                        .imagePullPolicy(
                                            getInputs().getWeblogicOperatorImagePullPolicy())
                                        .addCommandItem("bash")
                                        .addArgsItem("/operator/operator.sh")
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("OPERATOR_NAMESPACE")
                                                .valueFrom(
                                                    newEnvVarSource()
                                                        .fieldRef(
                                                            newObjectFieldSelector()
                                                                .fieldPath("metadata.namespace"))))
                                        .addEnvItem(
                                            newEnvVar().name("OPERATOR_VERBOSE").value("false"))
                                        .addEnvItem(
                                            newEnvVar()
                                                .name("JAVA_LOGGING_LEVEL")
                                                .value(getInputs().getJavaLoggingLevel()))
                                        .addEnvItem(
                                            newEnvVar().name("ISTIO_ENABLED").value("false"))
                                        .resources(
                                            new V1ResourceRequirements()
                                                .putRequestsItem("cpu", Quantity.fromString("100m"))
                                                .putRequestsItem(
                                                    "memory", Quantity.fromString("512Mi")))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-operator-cm-volume")
                                                .mountPath("/operator/config"))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-operator-debug-cm-volume")
                                                .mountPath("/operator/debug-config"))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-operator-secrets-volume")
                                                .mountPath("/operator/secrets")
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
        .livenessProbe(createProbe(20, 5, "/operator/livenessProbe.sh"))
        .readinessProbe(createProbe(2, 10, "/operator/readinessProbe.sh"));
  }

  private V1Probe createProbe(int initialDelaySeconds, int periodSeconds, String shellScript) {
    return newProbe()
        .initialDelaySeconds(initialDelaySeconds)
        .periodSeconds(periodSeconds)
        .exec(newExecAction().addCommandItem("bash").addCommandItem(shellScript));
  }

  @Test
  public void generatesCorrect_externalWeblogicOperatorService() {
    V1Service expected = getExpectedExternalWeblogicOperatorService();
    if (expected != null) {
      assertThat(getGeneratedFiles().getExternalOperatorService(), yamlEqualTo(expected));
    } else {
      try {
        getGeneratedFiles().getExternalOperatorService();
        fail("Should not have found an external operator service yaml");
      } catch (AssertionError ignored) {
        // no-op
      }
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
      spec.addPortsItem(
          newServicePort()
              .name("rest")
              .port(8081)
              .nodePort(Integer.parseInt(getInputs().getExternalRestHttpsPort())));
    }
    if (debuggingEnabled) {
      spec.addPortsItem(
          newServicePort()
              .name("debug")
              .port(Integer.parseInt(getInputs().getInternalDebugHttpPort()))
              .nodePort(Integer.parseInt(getInputs().getExternalDebugHttpPort())));
    }
    return newService()
        .metadata(
            newObjectMeta()
                .name("external-weblogic-operator-svc")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(spec);
  }

  @Test
  public void generatesCorrect_internalWeblogicOperatorService() {
    assertThat(
        getActualInternalWeblogicOperatorService(),
        yamlEqualTo(getExpectedInternalWeblogicOperatorService()));
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
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(
            newServiceSpec()
                .type("ClusterIP")
                .putSelectorItem(APP_LABEL, "weblogic-operator")
                .addPortsItem(newServicePort().name("rest").port(8082)));
  }

  @Test
  public void generatesCorrect_weblogicOperatorNamespace() {
    assertThat(
        getActualWeblogicOperatorNamespace(), yamlEqualTo(getExpectedWeblogicOperatorNamespace()));
  }

  private V1Namespace getActualWeblogicOperatorNamespace() {
    return getGeneratedFiles().getOperatorNamespace();
  }

  private V1Namespace getExpectedWeblogicOperatorNamespace() {
    return newNamespace()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()));
  }

  @Test
  public void generatesCorrect_weblogicOperatorServiceAccount() {
    assertThat(
        getActualWeblogicOperatorServiceAccount(),
        yamlEqualTo(getExpectedWeblogicOperatorServiceAccount()));
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
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()));
  }

  @Test
  public void generatesCorrect_weblogicOperatorClusterRole() {
    assertThat(
        getActualWeblogicOperatorClusterRole(),
        yamlEqualTo(getExpectedWeblogicOperatorClusterRole()));
  }

  private V1ClusterRole getActualWeblogicOperatorClusterRole() {
    return getGeneratedFiles().getWeblogicOperatorClusterRole();
  }

  private V1ClusterRole getExpectedWeblogicOperatorClusterRole() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-general")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
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
                .addApiGroupsItem("weblogic.oracle")
                .addResourcesItem("domains")
                .addResourcesItem("domains/status")
                .verbs(asList("get", "list", "watch", "update", "patch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("authentication.k8s.io")
                .addResourcesItem("tokenreviews")
                .verbs(singletonList("create")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("authorization.k8s.io")
                .resources(singletonList("selfsubjectrulesreviews"))
                .verbs(singletonList("create")));
  }

  @Test
  public void generatesCorrect_weblogicOperatorClusterRoleNonResource() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorClusterRoleNonResource(),
        yamlEqualTo(getExpectedWeblogicOperatorClusterRoleNonResource()));
  }

  private V1ClusterRole getExpectedWeblogicOperatorClusterRoleNonResource() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-nonresource")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(newPolicyRule().addNonResourceURLsItem("/version/*").addVerbsItem("get"));
  }

  @Test
  public void generatesCorrect_operatorRoleBinding() {
    assertThat(
        getGeneratedFiles().getOperatorRoleBinding(),
        yamlEqualTo(
            newClusterRoleBinding()
                .metadata(
                    newObjectMeta()
                        .name(
                            getInputs().getNamespace()
                                + "-weblogic-operator-clusterrolebinding-general")
                        .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
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
  public void generatesCorrect_operatorRoleBindingNonResource() {
    assertThat(
        getActualOperatorRoleBindingNonResource(),
        yamlEqualTo(getExpectedOperatorRoleBindingNonResource()));
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
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
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
  public void generatesCorrect_operatorRoleBindingDiscovery() {
    assertThat(
        getGeneratedFiles().getOperatorRoleBindingDiscovery(),
        yamlEqualTo(getExpectedOperatorRoleBindingDiscovery()));
  }

  private V1ClusterRoleBinding getExpectedOperatorRoleBindingDiscovery() {
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(
                    getInputs().getNamespace() + "-weblogic-operator-clusterrolebinding-discovery")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(
            newClusterRoleRef()
                .name("system:discovery")
                .apiGroup(KubernetesArtifactUtils.API_GROUP_RBAC));
  }

  @Test
  public void generatesCorrect_operatorRoleBindingAuthDelegator() {
    assertThat(
        getGeneratedFiles().getOperatorRoleBindingAuthDelegator(),
        yamlEqualTo(getExpectedOperatorRoleBindingAuthDelegator()));
  }

  private V1ClusterRoleBinding getExpectedOperatorRoleBindingAuthDelegator() {
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(
                    getInputs().getNamespace()
                        + "-weblogic-operator-clusterrolebinding-auth-delegator")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(
            newClusterRoleRef()
                .name("system:auth-delegator")
                .apiGroup(KubernetesArtifactUtils.API_GROUP_RBAC));
  }

  @Test
  public void generatesCorrect_weblogicOperatorNamespaceRole() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorNamespaceRole(),
        yamlEqualTo(getExpectedWeblogicOperatorNamespaceRole()));
  }

  private V1ClusterRole getExpectedWeblogicOperatorNamespaceRole() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-weblogic-operator-clusterrole-namespace")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
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
                .resources(singletonList("pods/log"))
                .verbs(asList("get", "list")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(singletonList("pods/exec"))
                .verbs(singletonList("create")))
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
                        "deletecollection")));
  }

  @Test
  public void generatesCorrect_targetNamespaces_weblogicOperatorRoleBindings() {
    for (String targetNamespace : getInputs().getTargetNamespaces().split(",")) {
      String namespace = targetNamespace.trim();
      assertThat(
          getGeneratedFiles().getWeblogicOperatorRoleBinding(namespace),
          yamlEqualTo(getExpectedWeblogicOperatorRoleBinding(namespace)));
    }
  }

  private V1RoleBinding getExpectedWeblogicOperatorRoleBinding(String namespace) {
    return newRoleBinding()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-rolebinding-namespace")
                .namespace(namespace)
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
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
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
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
  public void generatesCorrect_weblogicOperatorRole() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorRole(),
        yamlEqualTo(getExpectedWeblogicOperatorRole()));
  }

  private V1Role getExpectedWeblogicOperatorRole() {
    return newRole()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-role")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(asList("secrets", "configmaps", "events"))
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
  public void generatesCorrect_weblogicOperatorRoleBinding() {
    assertThat(
        getGeneratedFiles().getWeblogicOperatorRoleBinding(),
        yamlEqualTo(getExpectedWeblogicOperatorRoleBinding()));
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
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V2)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(spec);
  }

  protected void expectRemoteDebug(V1Container operatorContainer, String debugSuspend) {
    operatorContainer.addEnvItem(
        newEnvVar().name("REMOTE_DEBUG_PORT").value(getInputs().getInternalDebugHttpPort()));
    operatorContainer.addEnvItem(newEnvVar().name("DEBUG_SUSPEND").value(debugSuspend));
  }
}

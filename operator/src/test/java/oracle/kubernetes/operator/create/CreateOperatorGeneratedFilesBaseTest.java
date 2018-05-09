// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import static java.util.Arrays.asList;
import static oracle.kubernetes.operator.LabelConstants.*;
import static oracle.kubernetes.operator.VersionConstants.*;
import static oracle.kubernetes.operator.create.CreateOperatorInputs.readInputsYamlFile;
import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.yamlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.kubernetes.client.models.*;
import org.apache.commons.codec.binary.Base64;
import org.junit.AfterClass;
import org.junit.Test;

/**
 * Base class for testing that the all artifacts in the yaml files that create-weblogic-operator.sh
 * generates
 */
public abstract class CreateOperatorGeneratedFilesBaseTest {

  private static CreateOperatorInputs inputs;
  private static GeneratedOperatorYamlFiles generatedFiles;

  protected static CreateOperatorInputs getInputs() {
    return inputs;
  }

  protected static GeneratedOperatorYamlFiles getGeneratedFiles() {
    return generatedFiles;
  }

  protected ParsedWeblogicOperatorSecurityYaml getWeblogicOperatorSecurityYaml() {
    return getGeneratedFiles().getWeblogicOperatorSecurityYaml();
  }

  protected ParsedWeblogicOperatorYaml getWeblogicOperatorYaml() {
    return getGeneratedFiles().getWeblogicOperatorYaml();
  }

  protected static void setup(CreateOperatorInputs val) throws Exception {
    inputs = val;
    generatedFiles = GeneratedOperatorYamlFiles.generateOperatorYamlFiles(getInputs());
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatedCorrect_weblogicOperatorInputsYaml() throws Exception {
    assertThat(
        readInputsYamlFile(
            generatedFiles.getOperatorFiles().getCreateWeblogicOperatorInputsYamlPath()),
        yamlEqualTo(readInputsYamlFile(generatedFiles.getInputsYamlPath())));
  }

  @Test
  public void weblogicOperatorYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
        getWeblogicOperatorYaml().getObjectCount(),
        is(getWeblogicOperatorYaml().getExpectedObjectCount()));
  }

  @Test
  public void weblogicOperatorSecurityYaml_hasCorrectNumberOfObjects() throws Exception {
    assertThat(
        getWeblogicOperatorSecurityYaml().getObjectCount(),
        is(getWeblogicOperatorSecurityYaml().getExpectedObjectCount()));
  }

  @Test
  public void generatesCorrect_operatorConfigMap() throws Exception {
    assertThat(
        getActualWeblogicOperatorConfigMap(), yamlEqualTo(getExpectedWeblogicOperatorConfigMap()));
  }

  protected V1ConfigMap getActualWeblogicOperatorConfigMap() {
    return getWeblogicOperatorYaml().getOperatorConfigMap();
  }

  protected V1ConfigMap getExpectedWeblogicOperatorConfigMap() {
    return newConfigMap()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-cm")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .putDataItem("serviceaccount", getInputs().getServiceAccount())
        .putDataItem("targetNamespaces", getInputs().getTargetNamespaces())
        .putDataItem(
            "externalOperatorCert",
            Base64.encodeBase64String(getExpectedExternalWeblogicOperatorCert().getBytes()))
        .putDataItem(
            "internalOperatorCert",
            Base64.encodeBase64String(getInputs().internalOperatorSelfSignedCertPem().getBytes()));
  }

  protected abstract String getExpectedExternalWeblogicOperatorCert();

  @Test
  public void generatesCorrect_operatorSecrets() throws Exception {
    assertThat(
        getActualWeblogicOperatorSecrets(), yamlEqualTo(getExpectedWeblogicOperatorSecrets()));
  }

  protected V1Secret getActualWeblogicOperatorSecrets() {
    return getWeblogicOperatorYaml().getOperatorSecrets();
  }

  protected V1Secret getExpectedWeblogicOperatorSecrets() {
    return newSecret()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-secrets")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .type("Opaque")
        .putDataItem("externalOperatorKey", getExpectedExternalWeblogicOperatorKey().getBytes())
        .putDataItem(
            "internalOperatorKey", getInputs().internalOperatorSelfSignedKeyPem().getBytes());
  }

  protected abstract String getExpectedExternalWeblogicOperatorKey();

  @Test
  public void generatesCorrect_operatorDeployment() throws Exception {
    assertThat(
        getActualWeblogicOperatorDeployment(),
        yamlEqualTo(getExpectedWeblogicOperatorDeployment()));
  }

  protected ExtensionsV1beta1Deployment getActualWeblogicOperatorDeployment() {
    return getWeblogicOperatorYaml().getOperatorDeployment();
  }

  protected ExtensionsV1beta1Deployment getExpectedWeblogicOperatorDeployment() {
    return newDeployment()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(
            newDeploymentSpec()
                .replicas(1)
                .template(
                    newPodTemplateSpec()
                        .metadata(
                            newObjectMeta()
                                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
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
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-operator-cm-volume")
                                                .mountPath("/operator/config"))
                                        .addVolumeMountsItem(
                                            newVolumeMount()
                                                .name("weblogic-operator-secrets-volume")
                                                .mountPath("/operator/secrets")
                                                .readOnly(true))
                                        .livenessProbe(
                                            newProbe()
                                                .initialDelaySeconds(120)
                                                .periodSeconds(5)
                                                .exec(
                                                    newExecAction()
                                                        .addCommandItem("bash")
                                                        .addCommandItem(
                                                            "/operator/livenessProbe.sh"))))
                                .addVolumesItem(
                                    newVolume()
                                        .name("weblogic-operator-cm-volume")
                                        .configMap(
                                            newConfigMapVolumeSource()
                                                .name("weblogic-operator-cm")))
                                .addVolumesItem(
                                    newVolume()
                                        .name("weblogic-operator-secrets-volume")
                                        .secret(
                                            newSecretVolumeSource()
                                                .secretName("weblogic-operator-secrets"))))));
  }

  @Test
  public void generatesCorrect_externalWeblogicOperatorService() throws Exception {
    V1Service actual = getActualExternalWeblogicOperatorService();
    V1Service expected = getExpectedExternalWeblogicOperatorService();
    if (expected == null) {
      assertThat(actual, nullValue());
    } else {
      assertThat(actual, yamlEqualTo(expected));
    }
  }

  protected V1Service getActualExternalWeblogicOperatorService() {
    return getWeblogicOperatorYaml().getExternalOperatorService();
  }

  protected abstract V1Service getExpectedExternalWeblogicOperatorService();

  protected V1Service getExpectedExternalWeblogicOperatorService(
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
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(spec);
  }

  @Test
  public void generatesCorrect_internalWeblogicOperatorService() throws Exception {
    assertThat(
        getActualInternalWeblogicOperatorService(),
        yamlEqualTo(getExpectedInternalWeblogicOperatorService()));
  }

  protected V1Service getActualInternalWeblogicOperatorService() {
    return getWeblogicOperatorYaml().getInternalOperatorService();
  }

  protected V1Service getExpectedInternalWeblogicOperatorService() {
    return newService()
        .metadata(
            newObjectMeta()
                .name("internal-weblogic-operator-svc")
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(
            newServiceSpec()
                .type("ClusterIP")
                .putSelectorItem(APP_LABEL, "weblogic-operator")
                .addPortsItem(newServicePort().name("rest").port(8082)));
  }

  @Test
  public void generatesCorrect_weblogicOperatorNamespace() throws Exception {
    assertThat(
        getActualWeblogicOperatorNamespace(), yamlEqualTo(getExpectedWeblogicOperatorNamespace()));
  }

  protected V1Namespace getActualWeblogicOperatorNamespace() {
    return getWeblogicOperatorSecurityYaml().getOperatorNamespace();
  }

  protected V1Namespace getExpectedWeblogicOperatorNamespace() {
    return newNamespace()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()));
  }

  @Test
  public void generatesCorrect_weblogicOperatorServiceAccount() throws Exception {
    assertThat(
        getActualWeblogicOperatorServiceAccount(),
        yamlEqualTo(getExpectedWeblogicOperatorServiceAccount()));
  }

  protected V1ServiceAccount getActualWeblogicOperatorServiceAccount() {
    return getWeblogicOperatorSecurityYaml().getOperatorServiceAccount();
  }

  protected V1ServiceAccount getExpectedWeblogicOperatorServiceAccount() {
    return newServiceAccount()
        .metadata(
            newObjectMeta()
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()));
  }

  @Test
  public void generatesCorrect_weblogicOperatorClusterRole() throws Exception {
    assertThat(
        getActualWeblogicOperatorClusterRole(),
        yamlEqualTo(getExpectedWeblogicOperatorClusterRole()));
  }

  protected V1beta1ClusterRole getActualWeblogicOperatorClusterRole() {
    return getWeblogicOperatorSecurityYaml().getWeblogicOperatorClusterRole();
  }

  protected V1beta1ClusterRole getExpectedWeblogicOperatorClusterRole() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-cluster-role")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(asList("namespaces"))
                .verbs(asList("get", "list", "watch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(asList("persistentvolumes"))
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
                .addApiGroupsItem("apiextensions.k8s.io")
                .addResourcesItem("customresourcedefinitions")
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
                .addApiGroupsItem("weblogic.oracle")
                .addResourcesItem("domains")
                .verbs(asList("get", "list", "watch", "update", "patch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("weblogic.oracle")
                .addResourcesItem("domains/status")
                .addVerbsItem("update"))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("extensions")
                .addResourcesItem("ingresses")
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
                .addApiGroupsItem("authentication.k8s.io")
                .addResourcesItem("tokenreviews")
                .verbs(asList("create")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("authorization.k8s.io")
                .resources(
                    asList(
                        "selfsubjectaccessreviews",
                        "localsubjectaccessreviews",
                        "subjectaccessreviews",
                        "selfsubjectrulesreviews"))
                .verbs(asList("create")));
  }

  @Test
  public void generatesCorrect_weblogicOperatorClusterRoleNonResource() throws Exception {
    assertThat(
        getActualWeblogicOperatorClusterRoleNonResource(),
        yamlEqualTo(getExpectedWeblogicOperatorClusterRoleNonResource()));
  }

  protected V1beta1ClusterRole getActualWeblogicOperatorClusterRoleNonResource() {
    return getWeblogicOperatorSecurityYaml().getWeblogicOperatorClusterRoleNonResource();
  }

  protected V1beta1ClusterRole getExpectedWeblogicOperatorClusterRoleNonResource() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-cluster-role-nonresource")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(newPolicyRule().addNonResourceURLsItem("/version/*").addVerbsItem("get"));
  }

  @Test
  public void generatesCorrect_operatorRoleBinding() throws Exception {
    assertThat(
        getWeblogicOperatorSecurityYaml().getOperatorRoleBinding(),
        yamlEqualTo(
            newClusterRoleBinding()
                .metadata(
                    newObjectMeta()
                        .name(getInputs().getNamespace() + "-operator-rolebinding")
                        .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                        .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
                .addSubjectsItem(
                    newSubject()
                        .kind("ServiceAccount")
                        .name(getInputs().getServiceAccount())
                        .namespace(getInputs().getNamespace())
                        .apiGroup(""))
                .roleRef(
                    newRoleRef()
                        .name("weblogic-operator-cluster-role")
                        .apiGroup("rbac.authorization.k8s.io"))));
  }

  @Test
  public void generatesCorrect_operatorRoleBindingNonResource() throws Exception {
    assertThat(
        getActualOperatorRoleBindingNonResource(),
        yamlEqualTo(getExpectedOperatorRoleBindingNonResource()));
  }

  protected V1beta1ClusterRoleBinding getActualOperatorRoleBindingNonResource() {
    return getWeblogicOperatorSecurityYaml().getOperatorRoleBindingNonResource();
  }

  protected V1beta1ClusterRoleBinding getExpectedOperatorRoleBindingNonResource() {
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-operator-rolebinding-nonresource")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(
            newRoleRef()
                .name("weblogic-operator-cluster-role-nonresource")
                .apiGroup("rbac.authorization.k8s.io"));
  }

  @Test
  public void generatesCorrect_operatorRoleBindingDiscovery() throws Exception {
    assertThat(
        getActualOperatorRoleBindingDiscovery(),
        yamlEqualTo(getExpectedOperatorRoleBindingDiscovery()));
  }

  protected V1beta1ClusterRoleBinding getActualOperatorRoleBindingDiscovery() {
    return getWeblogicOperatorSecurityYaml().getOperatorRoleBindingDiscovery();
  }

  protected V1beta1ClusterRoleBinding getExpectedOperatorRoleBindingDiscovery() {
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-operator-rolebinding-discovery")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(newRoleRef().name("system:discovery").apiGroup("rbac.authorization.k8s.io"));
  }

  @Test
  public void generatesCorrect_operatorRoleBindingAuthDelegator() throws Exception {
    assertThat(
        getActualOperatorRoleBindingAuthDelegator(),
        yamlEqualTo(getExpectedOperatorRoleBindingAuthDelegator()));
  }

  protected V1beta1ClusterRoleBinding getActualOperatorRoleBindingAuthDelegator() {
    return getWeblogicOperatorSecurityYaml().getOperatorRoleBindingAuthDelegator();
  }

  protected V1beta1ClusterRoleBinding getExpectedOperatorRoleBindingAuthDelegator() {
    return newClusterRoleBinding()
        .metadata(
            newObjectMeta()
                .name(getInputs().getNamespace() + "-operator-rolebinding-auth-delegator")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(newRoleRef().name("system:auth-delegator").apiGroup("rbac.authorization.k8s.io"));
  }

  @Test
  public void generatesCorrect_weblogicOperatorNamespaceRole() throws Exception {
    assertThat(
        getActualWeblogicOperatorNamespaceRole(),
        yamlEqualTo(getExpectedWeblogicOperatorNamespaceRole()));
  }

  protected V1beta1ClusterRole getActualWeblogicOperatorNamespaceRole() {
    return getWeblogicOperatorSecurityYaml().getWeblogicOperatorNamespaceRole();
  }

  protected V1beta1ClusterRole getExpectedWeblogicOperatorNamespaceRole() {
    return newClusterRole()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-namespace-role")
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(asList("secrets"))
                .verbs(asList("get", "list", "watch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("storage.k8s.io")
                .addResourcesItem("storageclasses")
                .verbs(asList("get", "list", "watch")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(
                    asList(
                        "services",
                        "configmaps",
                        "pods",
                        "podtemplates",
                        "events",
                        "persistentvolumeclaims"))
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
                .resources(asList("pods/logs"))
                .verbs(asList("get", "list")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("")
                .resources(asList("pods/exec"))
                .verbs(asList("create")))
        .addRulesItem(
            newPolicyRule()
                .addApiGroupsItem("batch")
                .resources(asList("jobs", "cronjobs"))
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
                .addApiGroupsItem("settings.k8s.io")
                .addResourcesItem("podpresets")
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
                .addApiGroupsItem("extensions")
                .resources(asList("podsecuritypolicies", "networkpolicies"))
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
  public void generatesCorrect_targetNamespaces_weblogicOperatorRoleBindings() throws Exception {
    for (String targetNamespace : getInputs().getTargetNamespaces().split(",")) {
      String namespace = targetNamespace.trim();
      assertThat(
          getActualWeblogicOperatorRoleBinding(namespace),
          yamlEqualTo(getExpectedWeblogicOperatorRoleBinding(namespace)));
    }
  }

  protected V1beta1RoleBinding getActualWeblogicOperatorRoleBinding(String namespace) {
    return getWeblogicOperatorSecurityYaml().getWeblogicOperatorRoleBinding(namespace);
  }

  protected V1beta1RoleBinding getExpectedWeblogicOperatorRoleBinding(String namespace) {
    return newRoleBinding()
        .metadata(
            newObjectMeta()
                .name("weblogic-operator-rolebinding")
                .namespace(namespace)
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .addSubjectsItem(
            newSubject()
                .kind("ServiceAccount")
                .name(getInputs().getServiceAccount())
                .namespace(getInputs().getNamespace())
                .apiGroup(""))
        .roleRef(newRoleRef().name("weblogic-operator-namespace-role").apiGroup(""));
  }

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
                .putLabelsItem(RESOURCE_VERSION_LABEL, OPERATOR_V1)
                .putLabelsItem(OPERATORNAME_LABEL, getInputs().getNamespace()))
        .spec(spec);
  }
}

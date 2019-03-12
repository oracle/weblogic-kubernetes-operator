// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import com.appscode.voyager.client.models.V1beta1HTTPIngressBackend;
import com.appscode.voyager.client.models.V1beta1HTTPIngressPath;
import com.appscode.voyager.client.models.V1beta1HTTPIngressRuleValue;
import com.appscode.voyager.client.models.V1beta1Ingress;
import com.appscode.voyager.client.models.V1beta1IngressRule;
import com.appscode.voyager.client.models.V1beta1IngressSpec;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.models.ApiregistrationV1beta1ServiceReference;
import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.ExtensionsV1beta1DeploymentSpec;
import io.kubernetes.client.models.V1ClusterRole;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1ContainerPort;
import io.kubernetes.client.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1EnvVarSource;
import io.kubernetes.client.models.V1ExecAction;
import io.kubernetes.client.models.V1HTTPGetAction;
import io.kubernetes.client.models.V1Handler;
import io.kubernetes.client.models.V1HostPathVolumeSource;
import io.kubernetes.client.models.V1Job;
import io.kubernetes.client.models.V1JobSpec;
import io.kubernetes.client.models.V1LabelSelector;
import io.kubernetes.client.models.V1Lifecycle;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1NFSVolumeSource;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectFieldSelector;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PersistentVolume;
import io.kubernetes.client.models.V1PersistentVolumeClaim;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.models.V1PersistentVolumeSpec;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1PolicyRule;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1ResourceRequirements;
import io.kubernetes.client.models.V1Role;
import io.kubernetes.client.models.V1RoleBinding;
import io.kubernetes.client.models.V1RoleRef;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1SecretReference;
import io.kubernetes.client.models.V1SecretVolumeSource;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1Subject;
import io.kubernetes.client.models.V1TCPSocketAction;
import io.kubernetes.client.models.V1Toleration;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;
import io.kubernetes.client.models.V1beta1APIService;
import io.kubernetes.client.models.V1beta1APIServiceSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import oracle.kubernetes.operator.KubernetesConstants;
import oracle.kubernetes.weblogic.domain.model.Domain;
import oracle.kubernetes.weblogic.domain.model.DomainSpec;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

/** Utilities to help construct and manage kubernetes artifacts */
public class KubernetesArtifactUtils {

  public static final String API_GROUP_RBAC = "rbac.authorization.k8s.io";

  public static final String API_VERSION_APPS_V1BETA1 = "apps/v1beta1";
  public static final String API_VERSION_BATCH_V1 = "batch/v1";
  public static final String API_VERSION_EXTENSIONS_V1BETA1 = "extensions/v1beta1";
  public static final String API_VERSION_REGISTRATION_V1BETA1 = "apiregistration.k8s.io/v1beta1";
  public static final String API_VERSION_RBAC_V1 = API_GROUP_RBAC + "/v1";
  public static final String API_VERSION_RBAC_V1BETA1 = API_GROUP_RBAC + "/v1beta1";
  public static final String API_VERSION_WEBLOGIC_ORACLE =
      KubernetesConstants.DOMAIN_GROUP + "/" + KubernetesConstants.DOMAIN_VERSION;
  public static final String API_VERSION_V1 = "v1";
  public static final String API_VERSION_VOYAGER_V1BETA1 = "voyager.appscode.com/v1beta1";

  public static final String KIND_API_SERVICE = "APIService";
  public static final String KIND_CONFIG_MAP = "ConfigMap";
  public static final String KIND_CLUSTER_ROLE = "ClusterRole";
  public static final String KIND_CLUSTER_ROLE_BINDING = "ClusterRoleBinding";
  public static final String KIND_DOMAIN = "Domain";
  public static final String KIND_JOB = "Job";
  public static final String KIND_DEPLOYMENT = "Deployment";
  public static final String KIND_INGRESS = "Ingress";
  public static final String KIND_NAMESPACE = "Namespace";
  public static final String KIND_PERSISTENT_VOLUME = "PersistentVolume";
  public static final String KIND_PERSISTENT_VOLUME_CLAIM = "PersistentVolumeClaim";
  public static final String KIND_ROLE = "Role";
  public static final String KIND_ROLE_BINDING = "RoleBinding";
  public static final String KIND_SECRET = "Secret";
  public static final String KIND_SERVICE = "Service";
  public static final String KIND_SERVICE_ACCOUNT = "ServiceAccount";

  public static V1Namespace newNamespace() {
    return (new V1Namespace()).apiVersion(API_VERSION_V1).kind(KIND_NAMESPACE);
  }

  public static V1ServiceAccount newServiceAccount() {
    return (new V1ServiceAccount()).apiVersion(API_VERSION_V1).kind(KIND_SERVICE_ACCOUNT);
  }

  public static Domain newDomain() {
    return (new Domain()).withApiVersion(API_VERSION_WEBLOGIC_ORACLE).withKind(KIND_DOMAIN);
  }

  public static ExtensionsV1beta1Deployment newDeployment() {
    return (new ExtensionsV1beta1Deployment())
        .apiVersion(API_VERSION_APPS_V1BETA1)
        .kind(KIND_DEPLOYMENT);
  }

  public static V1Job newJob() {
    return (new V1Job()).apiVersion(API_VERSION_BATCH_V1).kind(KIND_JOB);
  }

  public static V1Service newService() {
    return (new V1Service()).apiVersion(API_VERSION_V1).kind(KIND_SERVICE);
  }

  public static V1PersistentVolume newPersistentVolume() {
    return (new V1PersistentVolume()).apiVersion(API_VERSION_V1).kind(KIND_PERSISTENT_VOLUME);
  }

  public static V1PersistentVolumeClaim newPersistentVolumeClaim() {
    return (new V1PersistentVolumeClaim())
        .apiVersion(API_VERSION_V1)
        .kind(KIND_PERSISTENT_VOLUME_CLAIM);
  }

  public static V1PersistentVolumeClaimList newPersistentVolumeClaimList() {
    return new V1PersistentVolumeClaimList();
  }

  public static V1ServicePort newServicePort() {
    return new V1ServicePort();
  }

  public static V1ConfigMap newConfigMap() {
    return (new V1ConfigMap()).apiVersion(API_VERSION_V1).kind(KIND_CONFIG_MAP);
  }

  public static V1Secret newSecret() {
    return (new V1Secret()).apiVersion(API_VERSION_V1).kind(KIND_SECRET);
  }

  public static V1beta1APIService newAPIService() {
    return (new V1beta1APIService())
        .apiVersion(API_VERSION_REGISTRATION_V1BETA1)
        .kind(KIND_API_SERVICE);
  }

  public static V1beta1APIServiceSpec newAPIServiceSpec() {
    return new V1beta1APIServiceSpec();
  }

  public static ApiregistrationV1beta1ServiceReference newServiceReference() {
    return new ApiregistrationV1beta1ServiceReference();
  }

  public static V1beta1Ingress newIngress() {
    return (new V1beta1Ingress()).apiVersion(API_VERSION_VOYAGER_V1BETA1).kind(KIND_INGRESS);
  }

  public static V1beta1HTTPIngressBackend newHTTPIngressBackend() {
    return new V1beta1HTTPIngressBackend();
  }

  public static V1beta1HTTPIngressPath newHTTPIngressPath() {
    return new V1beta1HTTPIngressPath();
  }

  public static V1beta1HTTPIngressRuleValue newHTTPIngressRuleValue() {
    return new V1beta1HTTPIngressRuleValue();
  }

  public static V1beta1IngressRule newIngressRule() {
    return new V1beta1IngressRule();
  }

  public static V1beta1IngressSpec newIngressSpec() {
    return new V1beta1IngressSpec();
  }

  public static V1Role newRole() {
    return (new V1Role()).apiVersion(API_VERSION_RBAC_V1).kind(KIND_ROLE);
  }

  public static V1ClusterRole newClusterRole() {
    return (new V1ClusterRole()).apiVersion(API_VERSION_RBAC_V1).kind(KIND_CLUSTER_ROLE);
  }

  public static V1ClusterRoleBinding newClusterRoleBinding() {
    return (new V1ClusterRoleBinding())
        .apiVersion(API_VERSION_RBAC_V1)
        .kind(KIND_CLUSTER_ROLE_BINDING);
  }

  public static V1RoleBinding newRoleBinding() {
    return (new V1RoleBinding()).apiVersion(API_VERSION_RBAC_V1).kind(KIND_ROLE_BINDING);
  }

  public static V1RoleRef newRoleRef() {
    return (new V1RoleRef()).apiGroup(API_GROUP_RBAC).kind(KIND_ROLE);
  }

  public static V1RoleRef newClusterRoleRef() {
    return (new V1RoleRef()).apiGroup(API_GROUP_RBAC).kind(KIND_CLUSTER_ROLE);
  }

  public static V1Subject newSubject() {
    return new V1Subject();
  }

  public static V1ObjectMeta newObjectMeta() {
    return new V1ObjectMeta();
  }

  public static ExtensionsV1beta1DeploymentSpec newDeploymentSpec() {
    return new ExtensionsV1beta1DeploymentSpec();
  }

  public static V1JobSpec newJobSpec() {
    return new V1JobSpec();
  }

  public static V1PodTemplateSpec newPodTemplateSpec() {
    return new V1PodTemplateSpec();
  }

  public static V1Pod newPod() {
    return new V1Pod();
  }

  public static V1PodSpec newPodSpec() {
    return new V1PodSpec();
  }

  public static V1Container newContainer() {
    return new V1Container();
  }

  public static V1EnvVar newEnvVar() {
    return new V1EnvVar();
  }

  public static FluentArrayList<V1EnvVar> newEnvVarList() {
    return newFluentArrayList(V1EnvVar.class);
  }

  public static V1EnvVarSource newEnvVarSource() {
    return new V1EnvVarSource();
  }

  public static V1ObjectFieldSelector newObjectFieldSelector() {
    return new V1ObjectFieldSelector();
  }

  public static V1VolumeMount newVolumeMount() {
    return new V1VolumeMount();
  }

  public static V1ContainerPort newContainerPort() {
    return new V1ContainerPort();
  }

  public static V1Probe newProbe() {
    return new V1Probe();
  }

  public static V1ExecAction newExecAction() {
    return new V1ExecAction();
  }

  public static V1Volume newVolume() {
    return new V1Volume();
  }

  public static V1ConfigMapVolumeSource newConfigMapVolumeSource() {
    return new V1ConfigMapVolumeSource();
  }

  public static V1SecretVolumeSource newSecretVolumeSource() {
    return new V1SecretVolumeSource();
  }

  public static V1PersistentVolumeClaimVolumeSource newPersistentVolumeClaimVolumeSource() {
    return new V1PersistentVolumeClaimVolumeSource();
  }

  public static V1EmptyDirVolumeSource newEmptyDirVolumeSource() {
    return new V1EmptyDirVolumeSource();
  }

  public static V1LocalObjectReference newLocalObjectReference() {
    return new V1LocalObjectReference();
  }

  public static V1PolicyRule newPolicyRule() {
    return new V1PolicyRule();
  }

  public static V1ResourceRequirements newResourceRequirements() {
    return new V1ResourceRequirements();
  }

  public static V1HostPathVolumeSource newHostPathVolumeSource() {
    return new V1HostPathVolumeSource();
  }

  public static V1NFSVolumeSource newNFSVolumeSource() {
    return new V1NFSVolumeSource();
  }

  public static IntOrString newIntOrString(String val) {
    return new IntOrString(val);
  }

  public static IntOrString newIntOrString(int val) {
    return new IntOrString(val);
  }

  public static Quantity newQuantity(String val) {
    return Quantity.fromString(val);
  }

  public static V1SecretReference newSecretReference() {
    return new V1SecretReference();
  }

  public static DomainSpec newDomainSpec() {
    return new DomainSpec();
  }

  public static V1ServiceSpec newServiceSpec() {
    return new V1ServiceSpec();
  }

  public static V1PersistentVolumeSpec newPersistentVolumeSpec() {
    return new V1PersistentVolumeSpec();
  }

  public static V1PersistentVolumeClaimSpec newPersistentVolumeClaimSpec() {
    return new V1PersistentVolumeClaimSpec();
  }

  public static V1LabelSelector newLabelSelector() {
    return new V1LabelSelector();
  }

  public static V1HTTPGetAction newHTTPGetAction() {
    return new V1HTTPGetAction();
  }

  public static V1TCPSocketAction newTCPSocketAction() {
    return new V1TCPSocketAction();
  }

  public static V1Toleration newToleration() {
    return new V1Toleration();
  }

  public static V1Handler newHandler() {
    return new V1Handler();
  }

  public static V1Lifecycle newLifecycle() {
    return new V1Lifecycle();
  }

  public static FluentArrayList<String> newStringList() {
    return newFluentArrayList(String.class);
  }

  public static FluentArrayList<V1LocalObjectReference> newLocalObjectReferenceList() {
    return newFluentArrayList(V1LocalObjectReference.class);
  }

  public static <E> FluentArrayList<E> newFluentArrayList(Class<E> type) {
    return new FluentArrayList<E>();
  }

  // The weblogic k8s artificats are missing fluent methods for adding items.
  // Add fluent classes to help with this.
  @SuppressWarnings("serial")
  public static class FluentArrayList<E> extends ArrayList<E> {
    public FluentArrayList<E> addElement(E e) {
      add(e);
      return this;
    }
  }

  // Some of the k8s artifacts, especially config maps, contain scripts and
  // configuration files whose values we don't want to hard code into the tests.
  // However, some parts of these values can be expansions of text from the
  // inputs files.
  // For these cases, the general testing pattern is to:
  // 1) extract the values from the actual k8s artifacts
  // 2) empty the values in the actual k8s artifacts
  // 3) create a desired k8s artifact, with empty values
  // 4) use yamlEqualTo to compare the desired k8s artifact with the actual k8s
  //    artifact whose values have been extracted and emptied
  //    (i.e. make sure the rest of the fields of the actual k8s artifact are as expected
  // 5) if (4) passes, THEN make sure that the extracted value contains the
  //    expected expanded text (i.e. just test part of the text, not all of it)
  // This method helps with (1) & (2)
  public static String getThenEmptyConfigMapDataValue(V1ConfigMap configMap, String key) {
    if (configMap != null) {
      Map<String, String> data = configMap.getData();
      if (data != null && data.containsKey(key)) {
        String val = data.get(key);
        data.put(key, "");
        return val;
      }
    }
    return null;
  }

  public static RegexpsMatcher containsRegexps(String... regExps) {
    return new RegexpsMatcher(regExps);
  }

  public static String toContainsRegExp(String regexp) {
    return ".*" + regexp + ".*";
  }

  private static class RegexpsMatcher extends TypeSafeDiagnosingMatcher<String> {
    public static final String MULTI_LINE_REGEXP_PREFIX = "(?s)";
    private String[] regExps;

    private RegexpsMatcher(String[] regExps) {
      this.regExps = regExps;
    }

    @Override
    protected boolean matchesSafely(String have, Description description) {
      List<String> missingRegexps = getMissingRegexps(have, regExps);
      if (missingRegexps.isEmpty()) {
        return true;
      }
      description.appendValueList(
          "\n  actual was\n'" + have + "'\n  is missing [", ", ", "]", missingRegexps);
      return false;
    }

    private List<String> getMissingRegexps(String have, String... regExpsWant) {
      List<String> missing = new ArrayList<>();
      for (String regExp : regExpsWant) {
        if (!have.matches(MULTI_LINE_REGEXP_PREFIX + toContainsRegExp(regExp))) {
          missing.add(regExp);
        }
      }
      return missing;
    }

    @Override
    public void describeTo(Description description) {
      description.appendValueList("\n  containing [", ",", "]", regExps);
    }
  }
}

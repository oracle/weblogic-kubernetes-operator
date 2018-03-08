// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.ExtensionsV1beta1DeploymentSpec;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1PolicyRule;
import io.kubernetes.client.models.V1beta1RoleBinding;
import io.kubernetes.client.models.V1beta1RoleRef;
import io.kubernetes.client.models.V1beta1Subject;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.models.V1EnvVar;
import io.kubernetes.client.models.V1EnvVarSource;
import io.kubernetes.client.models.V1ExecAction;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectFieldSelector;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1PodSpec;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1Probe;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1SecretVolumeSource;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1ServicePort;
import io.kubernetes.client.models.V1ServiceSpec;
import io.kubernetes.client.models.V1Volume;
import io.kubernetes.client.models.V1VolumeMount;

/**
 * Utilities to help construct and manage kubernetes artifacts
 */
public class KubernetesArtifactUtils {

  public static final String API_VERSION_RBAC_V1BETA1 = "rbac.authorization.k8s.io/v1beta1";
  public static final String API_VERSION_APPS_V1BEtA1 = "apps/v1beta1";
  public static final String API_VERSION_V1 = "v1";

  public static final String KIND_CONFIG_MAP = "ConfigMap";
  public static final String KIND_CLUSTER_ROLE = "ClusterRole";
  public static final String KIND_CLUSTER_ROLE_BINDING = "ClusterRoleBinding";
  public static final String KIND_DEPLOYMENT = "Deployment";
  public static final String KIND_NAMESPACE = "Namespace";
  public static final String KIND_ROLE_BINDING = "RoleBinding";
  public static final String KIND_SECRET = "Secret";
  public static final String KIND_SERVICE = "Service";
  public static final String KIND_SERVICE_ACCOUNT = "ServiceAccount";

  public static V1Namespace newNamespace(String name) {
    return
      (new V1Namespace())
        .apiVersion(API_VERSION_V1)
        .kind(KIND_NAMESPACE)
        .metadata(newObjectMeta().name(name));
  }

  public static V1ServiceAccount newServiceAccount(String name, String namespace) {
    return
      (new V1ServiceAccount())
        .apiVersion(API_VERSION_V1)
        .kind(KIND_SERVICE_ACCOUNT)
        .metadata(newObjectMeta().name(name).namespace(namespace));
  }

  public static ExtensionsV1beta1Deployment newDeployment(String name, String namespace) {
    return
      (new ExtensionsV1beta1Deployment())
        .apiVersion(API_VERSION_APPS_V1BEtA1)
        .kind(KIND_DEPLOYMENT)
        .metadata(newObjectMeta().name(name).namespace(namespace));
  }

  public static V1Service newService(String name, String namespace) {
    return
      (new V1Service())
        .apiVersion(API_VERSION_V1)
        .kind(KIND_SERVICE)
        .metadata(newObjectMeta().name(name).namespace(namespace))
        .spec(new V1ServiceSpec());
  }

  public static V1ServicePort newServicePort(String name) {
    return (new V1ServicePort()).name(name);
  }

  public static V1ConfigMap newConfigMap(String name, String namespace) {
    return
      (new V1ConfigMap())
        .apiVersion(API_VERSION_V1)
        .kind(KIND_CONFIG_MAP)
        .metadata(newObjectMeta().name(name).namespace(namespace));
  }

  public static V1Secret newSecret(String name, String namespace) {
    return
      (new V1Secret())
        .apiVersion(API_VERSION_V1)
        .kind(KIND_SECRET)
        .metadata(newObjectMeta().name(name).namespace(namespace));
  }

  public static V1beta1ClusterRole newClusterRole(String name) {
    return
      (new V1beta1ClusterRole())
        .apiVersion(API_VERSION_RBAC_V1BETA1)
        .kind(KIND_CLUSTER_ROLE)
        .metadata(newObjectMeta().name(name));
  }

  public static V1beta1ClusterRoleBinding newClusterRoleBinding(String name) {
    return
      (new V1beta1ClusterRoleBinding())
        .apiVersion(API_VERSION_RBAC_V1BETA1)
        .kind(KIND_CLUSTER_ROLE_BINDING)
        .metadata(newObjectMeta().name(name));
  }

  public static V1beta1ClusterRoleBinding newRoleBinding(String name, String namespace) {
    return
      (new V1beta1ClusterRoleBinding())
        .apiVersion(API_VERSION_RBAC_V1BETA1)
        .kind(KIND_ROLE_BINDING)
        .metadata(newObjectMeta().name(name).namespace(namespace));
  }

  public static V1beta1RoleRef newRoleRef(String name, String apiGroup) {
    return
      (new V1beta1RoleRef())
        .kind(KIND_CLUSTER_ROLE)
        .name(name)
        .apiGroup(apiGroup);
  }

  public static V1beta1Subject newSubject(String kind, String name, String namespace, String apiGroup) {
    return
      (new V1beta1Subject())
        .kind(kind)
        .name(name)
        .namespace(namespace)
        .apiGroup(apiGroup);
  }

  public static V1ObjectMeta newObjectMeta() {
    return new V1ObjectMeta();
  }

  public static ExtensionsV1beta1DeploymentSpec newDeploymentSpec() {
    return new ExtensionsV1beta1DeploymentSpec();
  }

  public static V1PodTemplateSpec newPodTemplateSpec() {
    return new V1PodTemplateSpec();
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

  public static V1EnvVarSource newEnvVarSource() {
    return new V1EnvVarSource();
  }

  public static V1ObjectFieldSelector newObjectFieldSelector() {
    return new V1ObjectFieldSelector();
  }

  public static V1VolumeMount newVolumeMount() {
    return new V1VolumeMount();
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

  public static V1EmptyDirVolumeSource newEmptyDirVolumeSource() {
    return new V1EmptyDirVolumeSource();
  }

  public static V1LocalObjectReference newLocalObjectReference() {
    return new V1LocalObjectReference();
  }

  public static V1beta1PolicyRule newPolicyRule() {
    return new V1beta1PolicyRule();
  }
}

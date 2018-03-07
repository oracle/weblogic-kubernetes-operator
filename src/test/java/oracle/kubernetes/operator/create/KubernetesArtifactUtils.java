// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;
import io.kubernetes.client.models.V1beta1RoleRef;
import io.kubernetes.client.models.V1beta1Subject;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1ServiceAccount;

/**
 * Utilities to help construct and manage kubernetes artifacts
 */
public class KubernetesArtifactUtils {

  public static final String API_VERSION_RBAC_V1BETA1 = "rbac.authorization.k8s.io/v1beta1";
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
        .metadata(newMetadata(name));
  }

  public static V1ServiceAccount newServiceAccount(String name, String namespace) {
    return
      (new V1ServiceAccount())
        .apiVersion(API_VERSION_V1)
        .kind(KIND_SERVICE_ACCOUNT)
        .metadata(newMetadata(name).namespace(namespace));
  }

  public static V1beta1ClusterRole newClusterRole(String name) {
    return
      (new V1beta1ClusterRole())
        .apiVersion(API_VERSION_RBAC_V1BETA1)
        .kind(KIND_CLUSTER_ROLE)
        .metadata(newMetadata(name));
  }

  public static V1beta1ClusterRoleBinding newClusterRoleBinding(String name) {
    return
      (new V1beta1ClusterRoleBinding())
        .apiVersion(API_VERSION_RBAC_V1BETA1)
        .kind(KIND_CLUSTER_ROLE_BINDING)
        .metadata(newMetadata(name));
  }

  public static V1beta1ClusterRoleBinding newRoleBinding(String name, String namespace) {
    return
      (new V1beta1ClusterRoleBinding())
        .apiVersion(API_VERSION_RBAC_V1BETA1)
        .kind(KIND_ROLE_BINDING)
        .metadata(newMetadata(name).namespace(namespace));
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

  public static V1ObjectMeta newMetadata(String name) {
    return (new V1ObjectMeta()).name(name);
  }
}

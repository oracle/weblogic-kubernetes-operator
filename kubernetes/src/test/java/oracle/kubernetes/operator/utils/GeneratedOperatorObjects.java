// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ClusterRole;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1Role;
import io.kubernetes.client.models.V1RoleBinding;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;

/**
 * Generates the operator yaml files for a set of valid operator input params. Creates and managed
 * the user projects directory that the files are stored in. Parses the generated yaml files into
 * typed java objects.
 */
public class GeneratedOperatorObjects {

  private ParsedWeblogicOperatorYaml operatorYaml;
  private ParsedWeblogicOperatorSecurityYaml securityYaml;

  public GeneratedOperatorObjects(
      ParsedWeblogicOperatorYaml operatorYaml, ParsedWeblogicOperatorSecurityYaml securityYaml) {
    this.operatorYaml = operatorYaml;
    this.securityYaml = securityYaml;
  }

  public V1ClusterRole getWeblogicOperatorClusterRole() {
    return securityYaml.getWeblogicOperatorClusterRole();
  }

  public int getExpectedObjectCount() {
    return operatorYaml.getExpectedObjectCount() + securityYaml.getExpectedObjectCount();
  }

  public int getObjectCount() {
    return operatorYaml.getObjectCount() + securityYaml.getObjectCount();
  }

  public V1Namespace getOperatorNamespace() {
    return securityYaml.getOperatorNamespace();
  }

  public ExtensionsV1beta1Deployment getOperatorDeployment() {
    return operatorYaml.getOperatorDeployment();
  }

  public V1Secret getOperatorSecrets() {
    return operatorYaml.getOperatorSecrets();
  }

  public V1ConfigMap getOperatorConfigMap() {
    return operatorYaml.getOperatorConfigMap();
  }

  public V1Service getExternalOperatorService() {
    return operatorYaml.getExternalOperatorService();
  }

  public V1Service getInternalOperatorService() {
    return operatorYaml.getInternalOperatorService();
  }

  public V1ServiceAccount getOperatorServiceAccount() {
    return securityYaml.getOperatorServiceAccount();
  }

  public V1ClusterRole getWeblogicOperatorClusterRoleNonResource() {
    return securityYaml.getWeblogicOperatorClusterRoleNonResource();
  }

  public V1ClusterRoleBinding getOperatorRoleBinding() {
    return securityYaml.getOperatorRoleBinding();
  }

  public V1ClusterRoleBinding getOperatorRoleBindingNonResource() {
    return securityYaml.getOperatorRoleBindingNonResource();
  }

  public V1ClusterRoleBinding getOperatorRoleBindingDiscovery() {
    return securityYaml.getOperatorRoleBindingDiscovery();
  }

  public V1ClusterRoleBinding getOperatorRoleBindingAuthDelegator() {
    return securityYaml.getOperatorRoleBindingAuthDelegator();
  }

  public V1ClusterRole getWeblogicOperatorNamespaceRole() {
    return securityYaml.getWeblogicOperatorNamespaceRole();
  }

  public V1RoleBinding getWeblogicOperatorRoleBinding(String namespace) {
    return securityYaml.getWeblogicOperatorRoleBinding(namespace);
  }

  public V1Role getWeblogicOperatorRole() {
    return securityYaml.getWeblogicOperatorRole();
  }

  public V1RoleBinding getWeblogicOperatorRoleBinding() {
    return securityYaml.getWeblogicOperatorRoleBinding();
  }
}

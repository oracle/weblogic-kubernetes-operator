// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;

import java.nio.file.Path;

/**
 * Parses a generated weblogic-operator-security.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicOperatorSecurityYaml extends ParsedKubernetesYaml {

  private CreateOperatorInputs inputs;

  public ParsedWeblogicOperatorSecurityYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1Namespace getOperatorNamespace() {
    return getNamespaces().find(inputs.getNamespace());
  }

  public V1ServiceAccount getOperatorServiceAccount() {
    return getServiceAccounts().find(inputs.getServiceAccount());
  }

  public V1beta1ClusterRole getWeblogicOperatorClusterRole() {
    return getClusterRoles().find("weblogic-operator-cluster-role");
  }

  public V1beta1ClusterRole getWeblogicOperatorClusterRoleNonResource() {
    return getClusterRoles().find("weblogic-operator-cluster-role-nonresource");
  }

  public V1beta1ClusterRoleBinding getOperatorRoleBinding() {
    return getClusterRoleBindings().find(inputs.getNamespace() + "-operator-rolebinding");
  }

  public V1beta1ClusterRoleBinding getOperatorRoleBindingNonResource() {
    return getClusterRoleBindings().find(inputs.getNamespace() + "-operator-rolebinding-nonresource");
  }

  public V1beta1ClusterRoleBinding getOperatorRoleBindingDiscovery() {
    return getClusterRoleBindings().find(inputs.getNamespace() + "-operator-rolebinding-discovery");
  }

  public V1beta1ClusterRoleBinding getOperatorRoleBindingAuthDelegator() {
    return getClusterRoleBindings().find(inputs.getNamespace() + "-operator-rolebinding-auth-delegator");
  }

  public V1beta1ClusterRole getWeblogicOperatorNamespaceRole() {
    return getClusterRoles().find("weblogic-operator-namespace-role");
  }

  public V1beta1RoleBinding getWeblogicOperatorRoleBinding(String namespace) {
    return getRoleBindings().find("weblogic-operator-rolebinding", namespace);
  }

  public int getExpectedObjectCount() {
    int rtn = 9;
    // add one role binding for each namespace
    for (@SuppressWarnings("unused") String targetNamespace : inputs.getTargetNamespaces().split(",")) {
      rtn++;
    }
    return rtn;
  }
}


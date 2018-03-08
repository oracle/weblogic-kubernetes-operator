// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;

import java.nio.file.Path;

/**
 * Parses a generated weblogic-operator-security.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicOperatorSecurityYaml {

  private CreateOperatorInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedWeblogicOperatorSecurityYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public V1Namespace getOperatorNamespace() {
    return parsedYaml.getNamespaces().find(inputs.getNamespace());
  }

  public V1ServiceAccount getOperatorServiceAccount() {
    return parsedYaml.getServiceAccounts().find(inputs.getServiceAccount());
  }

  public V1beta1ClusterRole getWeblogicOperatorClusterRole() {
    return parsedYaml.getClusterRoles().find("weblogic-operator-cluster-role");
  }
  public V1beta1ClusterRole getWeblogicOperatorClusterRoleNonResource() {
    return parsedYaml.getClusterRoles().find("weblogic-operator-cluster-role-nonresource");
  }

  public V1beta1ClusterRoleBinding getOperatorRoleBinding() {
    return parsedYaml.getClusterRoleBindings().find(inputs.getNamespace() + "-operator-rolebinding");
  }

  public V1beta1ClusterRoleBinding getOperatorRoleBindingNonResource() {
    return parsedYaml.getClusterRoleBindings().find(inputs.getNamespace() + "-operator-rolebinding-nonresource");
  }

  public V1beta1ClusterRoleBinding getOperatorRoleBindingDiscovery() {
    return parsedYaml.getClusterRoleBindings().find(inputs.getNamespace() + "-operator-rolebinding-discovery");
  }

  public V1beta1ClusterRoleBinding getOperatorRoleBindingAuthDelegator() {
    return parsedYaml.getClusterRoleBindings().find(inputs.getNamespace() + "-operator-rolebinding-auth-delegator");
  }

  public V1beta1ClusterRole getWeblogicOperatorNamespaceRole() {
    return parsedYaml.getClusterRoles().find("weblogic-operator-namespace-role");
  }

  public V1beta1RoleBinding getWeblogicOperatorRoleBinding(String namespace) {
    return parsedYaml.getRoleBindings().find("weblogic-operator-rolebinding", namespace);
  }
}


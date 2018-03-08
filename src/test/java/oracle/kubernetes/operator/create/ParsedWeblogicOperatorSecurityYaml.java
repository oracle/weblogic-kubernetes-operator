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

  private V1Namespace operatorNamespace;
  private V1ServiceAccount operatorServiceAccount;
  private V1beta1ClusterRole weblogicOperatorClusterRole;
  private V1beta1ClusterRole weblogicOperatorClusterRoleNonResource;
  private V1beta1ClusterRoleBinding operatorRoleBinding;
  private V1beta1ClusterRoleBinding operatorRoleBindingNonResource;
  private V1beta1ClusterRoleBinding operatorRoleBindingDiscovery;
  private V1beta1ClusterRoleBinding operatorRoleBindingAuthDelegator;
  private V1beta1ClusterRole weblogicOperatorNamespaceRole;
  private V1beta1RoleBinding weblogicOperatorRoleBinding;

  public V1Namespace getOperatorNamespace() { return operatorNamespace; }
  public V1ServiceAccount getOperatorServiceAccount() { return operatorServiceAccount; }
  public V1beta1ClusterRole getWeblogicOperatorClusterRole() { return weblogicOperatorClusterRole; }
  public V1beta1ClusterRole getWeblogicOperatorClusterRoleNonResource() { return weblogicOperatorClusterRoleNonResource; }
  public V1beta1ClusterRoleBinding getOperatorRoleBinding() { return operatorRoleBinding; }
  public V1beta1ClusterRoleBinding getOperatorRoleBindingNonResource() { return operatorRoleBindingNonResource; }
  public V1beta1ClusterRoleBinding getOperatorRoleBindingDiscovery() { return operatorRoleBindingDiscovery; }
  public V1beta1ClusterRoleBinding getOperatorRoleBindingAuthDelegator() { return operatorRoleBindingAuthDelegator; }
  public V1beta1ClusterRole getWeblogicOperatorNamespaceRole() { return weblogicOperatorNamespaceRole; }
  public V1beta1RoleBinding getWeblogicOperatorRoleBinding() { return weblogicOperatorRoleBinding; }

  public ParsedWeblogicOperatorSecurityYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    ParsedKubernetesYaml parsedYaml = new ParsedKubernetesYaml(yamlPath);
    operatorNamespace = parsedYaml.getNamespace(inputs.getNamespace());
    operatorServiceAccount = parsedYaml.getServiceAccount(inputs.getServiceAccount());
    weblogicOperatorClusterRole = parsedYaml.getClusterRole("weblogic-operator-cluster-role");
    weblogicOperatorClusterRoleNonResource = parsedYaml.getClusterRole("weblogic-operator-cluster-role-nonresource");
    operatorRoleBinding = parsedYaml.getClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding");
    operatorRoleBindingNonResource = parsedYaml.getClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-nonresource");
    operatorRoleBindingDiscovery = parsedYaml.getClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-discovery");
    operatorRoleBindingAuthDelegator = parsedYaml.getClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-auth-delegator");
    weblogicOperatorNamespaceRole = parsedYaml.getClusterRole("weblogic-operator-namespace-role");
    weblogicOperatorRoleBinding = parsedYaml.getRoleBinding("weblogic-operator-rolebinding");
  }
}


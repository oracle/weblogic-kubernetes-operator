// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1ServiceAccount;

/**
 * Parses a generated weblogic-operator-security.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicOperatorSecurityYaml {

  public V1Namespace operatorNamespace;
  public V1ServiceAccount operatorServiceAccount;
  public V1beta1ClusterRole weblogicOperatorClusterRole;
  public V1beta1ClusterRole weblogicOperatorClusterRoleNonResource;
  public V1beta1ClusterRoleBinding operatorRoleBinding;
  public V1beta1ClusterRoleBinding operatorRoleBindingNonResource;
  public V1beta1ClusterRoleBinding operatorRoleBindingDiscovery;
  public V1beta1ClusterRoleBinding operatorRoleBindingAuthDelegator;
  public V1beta1ClusterRole weblogicOperatorNamespaceRole;
  public V1beta1RoleBinding weblogicOperatorRoleBinding;

  public ParsedWeblogicOperatorSecurityYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    ParsedKubernetesYaml parsed = new ParsedKubernetesYaml(yamlPath);
    operatorNamespace = parsed.getNamespace(inputs.getNamespace());
    operatorServiceAccount = parsed.getServiceAccount(inputs.getServiceAccount());
    weblogicOperatorClusterRole = parsed.getClusterRole("weblogic-operator-cluster-role");
    weblogicOperatorClusterRoleNonResource = parsed.getClusterRole("weblogic-operator-cluster-role-nonresource");
    operatorRoleBinding = parsed.getClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding");
    operatorRoleBindingNonResource = parsed.getClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-nonresource");
    operatorRoleBindingDiscovery = parsed.getClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-discovery");
    operatorRoleBindingAuthDelegator = parsed.getClusterRoleBinding(inputs.getNamespace() + "-operator-rolebinding-auth-delegator");
    weblogicOperatorNamespaceRole = parsed.getClusterRole("weblogic-operator-namespace-role");
    weblogicOperatorRoleBinding = parsed.getRoleBinding("weblogic-operator-rolebinding");
  }
}


// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1ClusterRole;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import io.kubernetes.client.models.V1Namespace;
import io.kubernetes.client.models.V1Role;
import io.kubernetes.client.models.V1RoleBinding;
import io.kubernetes.client.models.V1ServiceAccount;
import java.nio.file.Path;

/** Parses a generated weblogic-operator-security.yaml file into a set of typed k8s java objects */
public class ParsedWeblogicOperatorSecurityYaml extends ParsedKubernetesYaml {

  private OperatorValues inputs;

  public ParsedWeblogicOperatorSecurityYaml(Path yamlPath, OperatorValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedWeblogicOperatorSecurityYaml(YamlReader factory, OperatorValues inputs)
      throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1Namespace getOperatorNamespace() {
    return getNamespaces().find(inputs.getNamespace());
  }

  public V1ServiceAccount getOperatorServiceAccount() {
    return getServiceAccounts().find(inputs.getServiceAccount());
  }

  public V1ClusterRole getWeblogicOperatorClusterRole() {
    return getClusterRoles().find(inputs.getNamespace() + "-weblogic-operator-clusterrole-general");
  }

  public V1ClusterRole getWeblogicOperatorClusterRoleNonResource() {
    return getClusterRoles()
        .find(inputs.getNamespace() + "-weblogic-operator-clusterrole-nonresource");
  }

  public V1ClusterRoleBinding getOperatorRoleBinding() {
    return getClusterRoleBindings()
        .find(inputs.getNamespace() + "-weblogic-operator-clusterrolebinding-general");
  }

  public V1ClusterRoleBinding getOperatorRoleBindingNonResource() {
    return getClusterRoleBindings()
        .find(inputs.getNamespace() + "-weblogic-operator-clusterrolebinding-nonresource");
  }

  public V1ClusterRoleBinding getOperatorRoleBindingDiscovery() {
    return getClusterRoleBindings()
        .find(inputs.getNamespace() + "-weblogic-operator-clusterrolebinding-discovery");
  }

  public V1ClusterRoleBinding getOperatorRoleBindingAuthDelegator() {
    return getClusterRoleBindings()
        .find(inputs.getNamespace() + "-weblogic-operator-clusterrolebinding-auth-delegator");
  }

  public V1ClusterRole getWeblogicOperatorNamespaceRole() {
    return getClusterRoles()
        .find(inputs.getNamespace() + "-weblogic-operator-clusterrole-namespace");
  }

  public V1RoleBinding getWeblogicOperatorRoleBinding(String namespace) {
    return getRoleBindings().find("weblogic-operator-rolebinding-namespace", namespace);
  }

  public V1Role getWeblogicOperatorRole() {
    return getRoles().find("weblogic-operator-role");
  }

  public V1RoleBinding getWeblogicOperatorRoleBinding() {
    return getRoleBindings().find("weblogic-operator-rolebinding");
  }

  public int getExpectedObjectCount() {
    int rtn = 9;
    // add one role binding for each namespace
    for (@SuppressWarnings("unused")
    String targetNamespace : inputs.getTargetNamespaces().split(",")) {
      rtn++;
    }
    return rtn;
  }
}

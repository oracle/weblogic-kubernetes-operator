// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1ServiceAccount;
import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import io.kubernetes.client.models.V1beta1RoleBinding;
import java.nio.file.Path;

/** Parses a generated voyager-operator-security.yaml file into a set of typed k8s java objects */
public class ParsedVoyagerOperatorSecurityYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedVoyagerOperatorSecurityYaml(Path yamlPath, CreateDomainInputs inputs)
      throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1ServiceAccount getVoyagerServiceAccount() {
    return getServiceAccounts().find(getVoyagerOperatorName());
  }

  public V1beta1ClusterRole getVoyagerClusterRole() {
    return getClusterRoles().find(getVoyagerOperatorName());
  }

  public V1beta1ClusterRoleBinding getVoyagerClusterRoleBinding() {
    return getClusterRoleBindings().find(getVoyagerOperatorName());
  }

  public V1beta1RoleBinding getVoyagerAuthenticationReaderRoleBinding() {
    return getRoleBindings().find("voyager-apiserver-extension-server-authentication-reader");
  }

  public V1beta1ClusterRoleBinding getVoyagerAuthDelegatorClusterRoleBinding() {
    return getClusterRoleBindings().find("voyager-apiserver-auth-delegator");
  }

  public V1beta1ClusterRole getVoyagerAppsCodeEditClusterRole() {
    return getClusterRoles().find("appscode:voyager:edit");
  }

  public V1beta1ClusterRole getVoyagerAppsCodeViewClusterRole() {
    return getClusterRoles().find("appscode:voyager:view");
  }

  public int getExpectedObjectCount() {
    return 7;
  }

  private String getVoyagerOperatorName() {
    return "voyager-operator";
  }
}

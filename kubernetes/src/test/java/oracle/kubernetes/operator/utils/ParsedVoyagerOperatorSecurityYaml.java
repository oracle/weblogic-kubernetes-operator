// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1ClusterRole;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import io.kubernetes.client.models.V1RoleBinding;
import io.kubernetes.client.models.V1ServiceAccount;
import java.nio.file.Path;

/** Parses a generated voyager-operator-security.yaml file into a set of typed k8s java objects */
public class ParsedVoyagerOperatorSecurityYaml extends ParsedKubernetesYaml {

  public ParsedVoyagerOperatorSecurityYaml(Path yamlPath) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
  }

  public ParsedVoyagerOperatorSecurityYaml(YamlReader factory) throws Exception {
    super(factory);
  }

  public V1ServiceAccount getVoyagerServiceAccount() {
    return getServiceAccounts().find(getVoyagerOperatorName());
  }

  public V1ClusterRole getVoyagerClusterRole() {
    return getClusterRoles().find(getVoyagerOperatorName());
  }

  public V1ClusterRoleBinding getVoyagerClusterRoleBinding() {
    return getClusterRoleBindings().find(getVoyagerOperatorName());
  }

  public V1RoleBinding getVoyagerAuthenticationReaderRoleBinding() {
    return getRoleBindings().find("voyager-apiserver-extension-server-authentication-reader");
  }

  public V1ClusterRoleBinding getVoyagerAuthDelegatorClusterRoleBinding() {
    return getClusterRoleBindings().find("voyager-apiserver-auth-delegator");
  }

  public V1ClusterRole getVoyagerAppsCodeEditClusterRole() {
    return getClusterRoles().find("appscode:voyager:edit");
  }

  public V1ClusterRole getVoyagerAppsCodeViewClusterRole() {
    return getClusterRoles().find("appscode:voyager:view");
  }

  public int getExpectedObjectCount() {
    return 7;
  }

  private String getVoyagerOperatorName() {
    return "voyager-operator";
  }
}

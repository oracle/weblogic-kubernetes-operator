// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import java.nio.file.Path;

/**
 * Parses a generated weblogic-domain-traefik-security-inputs.getClusterName().toLowerCase().yaml
 * file into a set of typed k8s java objects
 */
public class ParsedTraefikSecurityYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedTraefikSecurityYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1beta1ClusterRole getTraefikClusterRole() {
    return getClusterRoles().find(getTraefikScope());
  }

  public V1beta1ClusterRoleBinding getTraefikDashboardClusterRoleBinding() {
    return getClusterRoleBindings().find(getTraefikScope());
  }

  public int getExpectedObjectCount() {
    return 2;
  }

  private String getTraefikScope() {
    return getClusterScope() + "-traefik";
  }

  private String getClusterScope() {
    return inputs.getDomainUID() + "-" + inputs.getClusterName().toLowerCase();
  }
}

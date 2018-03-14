// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;

/**
 * Parses a generated traefik-security.yaml file into a set of typed k8s java objects
 */
public class ParsedTraefikSecurityYaml {

  private CreateDomainInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedTraefikSecurityYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public V1beta1ClusterRole getTraefikClusterRole() {
    return parsedYaml.getClusterRoles().find(getTraefikScope());
  }

  public V1beta1ClusterRoleBinding getTraefikDashboardClusterRoleBinding() {
    return parsedYaml.getClusterRoleBindings().find(getTraefikScope());
  }

  private String getTraefikScope() {
    return getClusterScope() + "-traefik";
  }

  private String getClusterScope() {
    return inputs.getDomainUid() + "-" + inputs.getClusterName().toLowerCase();
  }
}

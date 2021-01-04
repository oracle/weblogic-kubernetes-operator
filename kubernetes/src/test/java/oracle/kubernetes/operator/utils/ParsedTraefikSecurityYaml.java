// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import oracle.kubernetes.operator.helpers.LegalNames;

/**
 * Parses a generated
 * weblogic-domain-traefik-security-inputs.LegalNames.toDns1123LegalName(getClusterName()).yaml file
 * into a set of typed k8s java objects
 */
public class ParsedTraefikSecurityYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedTraefikSecurityYaml(Path yamlPath, DomainValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedTraefikSecurityYaml(YamlReader factory, DomainValues inputs) throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1ClusterRole getTraefikClusterRole() {
    return getClusterRoles().find(getTraefikScope());
  }

  public V1ClusterRoleBinding getTraefikDashboardClusterRoleBinding() {
    return getClusterRoleBindings().find(getTraefikScope());
  }

  public int getExpectedObjectCount() {
    return 2;
  }

  private String getTraefikScope() {
    return getClusterScope() + "-traefik";
  }

  private String getClusterScope() {
    return inputs.getDomainUid() + "-" + LegalNames.toDns1123LegalName(inputs.getClusterName());
  }
}

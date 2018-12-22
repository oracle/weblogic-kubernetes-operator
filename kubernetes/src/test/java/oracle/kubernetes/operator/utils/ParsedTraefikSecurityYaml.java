// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1ClusterRole;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import java.nio.file.Path;
import oracle.kubernetes.operator.helpers.LegalNames;

/**
 * Parses a generated
 * weblogic-domain-traefik-security-inputs.LegalNames.toDNS1123LegalName(getClusterName()).yaml file
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
    return inputs.getDomainUID() + "-" + LegalNames.toDNS1123LegalName(inputs.getClusterName());
  }
}

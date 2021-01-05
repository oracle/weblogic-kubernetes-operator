// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.V1ClusterRole;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;

/**
 * Parses a generated weblogic-domain-apache-security.yaml file into a set of typed k8s java objects
 */
public class ParsedApacheSecurityYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedApacheSecurityYaml(Path yamlPath, DomainValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedApacheSecurityYaml(YamlReader factory, DomainValues inputs) throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1ClusterRole getApacheClusterRole() {
    return getClusterRoles().find(getApacheName());
  }

  public V1ClusterRoleBinding getApacheClusterRoleBinding() {
    return getClusterRoleBindings().find(getApacheName());
  }

  public int getExpectedObjectCount() {
    return 2;
  }

  private String getApacheName() {
    return inputs.getDomainUid() + "-apache-webtier";
  }
}

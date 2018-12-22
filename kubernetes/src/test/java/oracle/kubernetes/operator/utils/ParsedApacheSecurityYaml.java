// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1ClusterRole;
import io.kubernetes.client.models.V1ClusterRoleBinding;
import java.nio.file.Path;

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
    return inputs.getDomainUID() + "-apache-webtier";
  }
}

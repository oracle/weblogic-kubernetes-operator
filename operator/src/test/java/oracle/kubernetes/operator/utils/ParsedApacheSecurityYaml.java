// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.V1beta1ClusterRole;
import io.kubernetes.client.models.V1beta1ClusterRoleBinding;
import java.nio.file.Path;

/**
 * Parses a generated weblogic-domain-apache-security.yaml file into a set of typed k8s java objects
 */
public class ParsedApacheSecurityYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedApacheSecurityYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1beta1ClusterRole getApacheClusterRole() {
    return getClusterRoles().find(getApacheName());
  }

  public V1beta1ClusterRoleBinding getApacheClusterRoleBinding() {
    return getClusterRoleBindings().find(getApacheName());
  }

  public int getExpectedObjectCount() {
    return 2;
  }

  private String getApacheName() {
    return inputs.getDomainUID() + "-apache-webtier";
  }
}

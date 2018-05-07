// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import java.nio.file.Path;

/**
 * Parses a generated weblogic-domain-traefik-inputs.getClusterName().toLowerCase().yaml file into a
 * set of typed k8s java objects
 */
public class ParsedTraefikYaml extends ParsedKubernetesYaml {

  private CreateDomainInputs inputs;

  public ParsedTraefikYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    super(yamlPath);
    this.inputs = inputs;
  }

  public V1ServiceAccount getTraefikServiceAccount() {
    return getServiceAccounts().find(getTraefikScope());
  }

  public ExtensionsV1beta1Deployment getTraefikDeployment() {
    return getDeployments().find(getTraefikScope());
  }

  public V1ConfigMap getTraefikConfigMap() {
    return getConfigMaps().find(getTraefikScope() + "-cm");
  }

  public V1Service getTraefikService() {
    return getServices().find(getTraefikScope());
  }

  public V1Service getTraefikDashboardService() {
    return getServices().find(getClusterScope() + "-traefik-dashboard");
  }

  public int getExpectedObjectCount() {
    return 5;
  }

  private String getTraefikScope() {
    return getClusterScope() + "-traefik";
  }

  private String getClusterScope() {
    return inputs.getDomainUID() + "-" + inputs.getClusterName().toLowerCase();
  }
}

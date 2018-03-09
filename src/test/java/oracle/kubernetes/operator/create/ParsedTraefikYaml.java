// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;

/**
 * Parses a generated traefik.yaml file into a set of typed k8s java objects
 */
public class ParsedTraefikYaml {

  private CreateDomainInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedTraefikYaml(Path yamlPath, CreateDomainInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public V1ServiceAccount getTraefikServiceAccount() {
    return parsedYaml.getServiceAccounts().find(getTraefikScope());
  }

  public ExtensionsV1beta1Deployment getTraefikDeployment() {
    return parsedYaml.getDeployments().find(getTraefikScope());
  }

  public V1ConfigMap getTraefikConfig() {
    return parsedYaml.getConfigMaps().find(getTraefikScope());
  }

  public V1Service getTraefikService() {
    return parsedYaml.getServices().find(getTraefikScope());
  }

  public V1Service getTraefikDashboardService() {
    return parsedYaml.getServices().find(getClusterScope() + "-traefik-dashboard");
  }

  private String getTraefikScope() {
    return getClusterScope() + "-traefik";
  }

  private String getClusterScope() {
    return inputs.getDomainUid() + "-" + inputs.getClusterName().toLowerCase();
  }
}

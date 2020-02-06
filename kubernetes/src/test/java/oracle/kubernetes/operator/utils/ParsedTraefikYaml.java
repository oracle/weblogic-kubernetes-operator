// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.kubernetes.operator.helpers.LegalNames;

/**
 * Parses a generated
 * weblogic-domain-traefik-inputs.LegalNames.toDns1123LegalName(getClusterName()).yaml file into a
 * set of typed k8s java objects
 */
public class ParsedTraefikYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedTraefikYaml(Path yamlPath, DomainValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedTraefikYaml(YamlReader factory, DomainValues inputs) throws Exception {
    super(factory);
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
    return inputs.getDomainUid() + "-" + LegalNames.toDns1123LegalName(inputs.getClusterName());
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import java.nio.file.Path;
import oracle.kubernetes.operator.helpers.LegalNames;

/**
 * Parses a generated
 * weblogic-domain-traefik-inputs.LegalNames.toDNS1123LegalName(getClusterName()).yaml file into a
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
    return inputs.getDomainUID() + "-" + LegalNames.toDNS1123LegalName(inputs.getClusterName());
  }
}

// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1beta1APIService;

/** Parses a generated voyager-operator.yaml file into a set of typed k8s java objects */
public class ParsedVoyagerOperatorYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedVoyagerOperatorYaml(Path yamlPath, DomainValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedVoyagerOperatorYaml(YamlReader factory, DomainValues inputs) throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1Deployment getVoyagerOperatorDeployment() {
    return getDeployments().find(getVoyagerOperatorName());
  }

  public V1Secret getVoyagerOperatorSecret() {
    return getSecrets().find(getVoyagerName() + "-apiserver-cert");
  }

  public V1Service getVoyagerOperatorService() {
    return getServices().find(getVoyagerOperatorName());
  }

  public V1beta1APIService getVoyagerOperatorApiService() {
    return getApiServices().find("v1beta1.admission.voyager.appscode.com");
  }

  public int getExpectedObjectCount() {
    return 4;
  }

  protected String getVoyagerName() {
    return "voyager";
  }

  private String getVoyagerOperatorName() {
    return getVoyagerName() + "-operator";
  }
}

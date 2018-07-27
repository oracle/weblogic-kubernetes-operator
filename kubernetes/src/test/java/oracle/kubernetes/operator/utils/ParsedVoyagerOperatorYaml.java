// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1beta1APIService;
import java.nio.file.Path;

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

  public ExtensionsV1beta1Deployment getVoyagerOperatorDeployment() {
    return getDeployments().find(getVoyagerOperatorName());
  }

  public V1Secret getVoyagerOperatorSecret() {
    return getSecrets().find(getVoyagerName() + "-apiserver-cert");
  }

  public V1Service getVoyagerOperatorService() {
    return getServices().find(getVoyagerOperatorName());
  }

  public V1beta1APIService getVoyagerOperatorAPIService() {
    return getAPIServices().find("v1beta1.admission.voyager.appscode.com");
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

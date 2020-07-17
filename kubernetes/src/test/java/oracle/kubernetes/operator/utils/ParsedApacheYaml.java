// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;

/** Parses a generated weblogic-domain-apache.yaml file into a set of typed k8s java objects */
public class ParsedApacheYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedApacheYaml(Path yamlPath, DomainValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedApacheYaml(YamlReader factory, DomainValues inputs) throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1ServiceAccount getApacheServiceAccount() {
    return getServiceAccounts().find(getApacheName());
  }

  public V1Deployment getApacheDeployment() {
    return getDeployments().find(getApacheName());
  }

  public V1Service getApacheService() {
    return getServices().find(getApacheName());
  }

  public int getExpectedObjectCount() {
    return 3;
  }

  private String getApacheName() {
    return inputs.getDomainUid() + "-apache-webtier";
  }
}

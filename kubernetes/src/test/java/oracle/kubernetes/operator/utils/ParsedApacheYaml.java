// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServiceAccount;
import java.nio.file.Path;

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

  public ExtensionsV1beta1Deployment getApacheDeployment() {
    return getDeployments().find(getApacheName());
  }

  public V1Service getApacheService() {
    return getServices().find(getApacheName());
  }

  public int getExpectedObjectCount() {
    return 3;
  }

  private String getApacheName() {
    return inputs.getDomainUID() + "-apache-webtier";
  }
}

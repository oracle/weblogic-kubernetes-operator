// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.NetworkingV1beta1Ingress;
import io.kubernetes.client.openapi.models.V1Service;

/**
 * Parses a generated weblogic-domain-voyager-ingress.yaml file into a set of typed k8s java objects
 */
public class ParsedVoyagerIngressYaml extends ParsedKubernetesYaml {

  private DomainValues inputs;

  public ParsedVoyagerIngressYaml(Path yamlPath, DomainValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

  public ParsedVoyagerIngressYaml(YamlReader factory, DomainValues inputs) throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public NetworkingV1beta1Ingress getVoyagerIngress() {
    return getIngresses().find(getVoyagerIngressName());
  }

  public V1Service getVoyagerIngressService() {
    return getServices().find(getVoyagerIngressName() + "-stats");
  }

  public int getExpectedObjectCount() {
    return 2;
  }

  private String getVoyagerIngressName() {
    return inputs.getDomainUid() + "-voyager";
  }
}

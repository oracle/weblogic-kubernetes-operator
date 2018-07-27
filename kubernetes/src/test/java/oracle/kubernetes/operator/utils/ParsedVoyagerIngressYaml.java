// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import com.appscode.voyager.client.models.V1beta1Ingress;
import io.kubernetes.client.models.V1Service;
import java.nio.file.Path;

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

  public V1beta1Ingress getVoyagerIngress() {
    return getIngresses().find(getVoyagerIngressName());
  }

  public V1Service getVoyagerIngressService() {
    return getServices().find(getVoyagerIngressName() + "-stats");
  }

  public int getExpectedObjectCount() {
    return 2;
  }

  private String getVoyagerIngressName() {
    return inputs.getDomainUID() + "-voyager";
  }
}

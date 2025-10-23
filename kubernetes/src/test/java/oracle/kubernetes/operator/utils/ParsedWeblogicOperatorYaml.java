// Copyright (c) 2018, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Secret;

/** Parses a generated weblogic-operator.yaml file into a set of typed k8s java objects. */
public class ParsedWeblogicOperatorYaml extends ParsedKubernetesYaml {

  private final OperatorValues inputs;

  public ParsedWeblogicOperatorYaml(YamlReader factory, OperatorValues inputs) throws Exception {
    super(factory);
    this.inputs = inputs;
  }

  public V1ConfigMap getOperatorConfigMap() {
    return getConfigMaps().find("weblogic-operator-cm");
  }

  public V1Secret getOperatorSecrets() {
    return getSecrets().find("weblogic-operator-secrets");
  }

  public V1Deployment getOperatorDeployment() {
    return getDeployments().find("weblogic-operator");
  }

  /**
   * get expected object count.
   * @return object count
   */
  public int getExpectedObjectCount() {
    int rtn = 6;
    if (inputs.getRemoteDebugNodePortEnabled().equals("true")) {
      // the external operator service is enabled if the remote debug port is enabled
      rtn++;
    }
    return rtn;
  }
}

// Copyright (c) 2018, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;

/** Parses a generated weblogic-operator.yaml file into a set of typed k8s java objects */
public class ParsedWeblogicOperatorYaml extends ParsedKubernetesYaml {

  private static String OPERATOR_RELEASE = "weblogic-operator";

  private OperatorValues inputs;

  ParsedWeblogicOperatorYaml(Path yamlPath, OperatorValues inputs) throws Exception {
    super(new ScriptGeneratedYamlReader(yamlPath));
    this.inputs = inputs;
  }

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

  public V1Service getExternalOperatorService() {
    return getServices().find("external-weblogic-operator-svc");
  }

  public V1Service getInternalOperatorService() {
    return getServices().find("internal-weblogic-operator-svc");
  }

  /**
   * get expected object count.
   * @return object count
   */
  public int getExpectedObjectCount() {
    int rtn = 6;
    if (inputs.getRemoteDebugNodePortEnabled().equals("true")
        || !(inputs.getExternalRestEnabled().equals("true"))) {
      // the external operator service is enabled if the remote debug port is enabled or external
      // rest is enabled
      rtn++;
    }
    return rtn;
  }
}

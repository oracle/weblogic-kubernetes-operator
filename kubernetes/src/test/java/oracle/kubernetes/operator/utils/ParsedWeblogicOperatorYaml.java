// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static oracle.kubernetes.operator.utils.OperatorValues.EXTERNAL_REST_OPTION_NONE;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;
import java.nio.file.Path;

/** Parses a generated weblogic-operator.yaml file into a set of typed k8s java objects */
public class ParsedWeblogicOperatorYaml extends ParsedKubernetesYaml {

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

  public ExtensionsV1beta1Deployment getOperatorDeployment() {
    return getDeployments().find("weblogic-operator");
  }

  public V1Service getExternalOperatorService() {
    return getServices().find("external-weblogic-operator-svc");
  }

  public V1Service getInternalOperatorService() {
    return getServices().find("internal-weblogic-operator-svc");
  }

  public int getExpectedObjectCount() {
    int rtn = 4;
    if (inputs.getRemoteDebugNodePortEnabled().equals("true")
        || !(inputs.getExternalRestOption().equals(EXTERNAL_REST_OPTION_NONE))) {
      // the external operator service is enabled if the remote debug port is enabled or external
      // rest is enabled
      rtn++;
    }
    return rtn;
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;

/**
 * Parses a generated weblogic-operator.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicOperatorYaml {

  private CreateOperatorInputs inputs;
  private ParsedKubernetesYaml parsedYaml;

  public ParsedWeblogicOperatorYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    this.inputs = inputs;
    parsedYaml = new ParsedKubernetesYaml(yamlPath);
  }

  public V1ConfigMap getOperatorConfigMap() {
    return parsedYaml.getConfigMaps().find("operator-config-map");
  }

  public V1Secret getOperatorSecrets() {
    return parsedYaml.getSecrets().find("operator-secrets");
  }

  public ExtensionsV1beta1Deployment getOperatorDeployment() {
    return parsedYaml.getDeployments().find("weblogic-operator");
  }

  public V1Service getExternalOperatorService() {
    return parsedYaml.getServices().find("external-weblogic-operator-service");
  }

  public V1Service getInternalOperatorService() {
    return parsedYaml.getServices().find("internal-weblogic-operator-service");
  }
}

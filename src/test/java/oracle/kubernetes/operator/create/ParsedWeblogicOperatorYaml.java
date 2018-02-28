// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1beta1RoleBinding;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;

/**
 * Parses a generated weblogic-operator.yaml file into a set of typed k8s java objects
 */
public class ParsedWeblogicOperatorYaml {

  public V1ConfigMap operatorConfigMap;
  public V1Secret operatorSecrets;
  public ExtensionsV1beta1Deployment operatorDeployment;
  public V1Service externalOperatorService;
  public V1Service internalOperatorService;

  public ParsedWeblogicOperatorYaml(Path yamlPath, CreateOperatorInputs inputs) throws Exception {
    ParsedKubernetesYaml parsed = new ParsedKubernetesYaml(yamlPath);
    int count = 0;

    operatorConfigMap = parsed.getConfigMap("operator-config-map");
    assertThat(operatorConfigMap, notNullValue());
    count++;

    operatorSecrets = parsed.getSecret("operator-secrets");
    assertThat(operatorSecrets, notNullValue());
    count++;

    operatorDeployment = parsed.getDeployment("weblogic-operator");
    assertThat(operatorDeployment, notNullValue());
    count++;

    internalOperatorService = parsed.getService("internal-weblogic-operator-service");
    assertThat(internalOperatorService, notNullValue());
    count++;

    if (!("none".equals(inputs.externalRestOption))) {
      externalOperatorService = parsed.getService("external-weblogic-operator-service");
      assertThat(externalOperatorService, notNullValue());
      count++;
    }

    assertThat(count, is(parsed.getInstanceCount()));
  }
}


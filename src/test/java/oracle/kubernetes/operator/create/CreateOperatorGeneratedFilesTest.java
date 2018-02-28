// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

import static oracle.kubernetes.operator.create.ExecResultMatcher.succeedsAndPrints;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests that the yaml files that create-weblogic-operator.sh generates,
 * based on the inputs the customer can specify, are correct.
 */
public class CreateOperatorGeneratedFilesTest extends CreateOperatorTest {

  // TODO - write lots of test cases that vary the inputs
  // and make sure that corresponding yaml files are as expected

  @Test
  public void generatedYamlFilesForDefaultInputsFile_areParsableIntoK8sObjects() throws Exception {
    CreateOperatorInputs inputs = readDefaultInputsFile();
    assertThat(execCreateOperator(inputs), succeedsAndPrints());
    // Some trial balloon yaml value tests - probably need to rework as custom matchers
    {
      ParsedWeblogicOperatorYaml parsed = parseGeneratedWeblogicOperatorYaml(inputs);

      assertThat(parsed.operatorDeployment, notNullValue());
      assertThat(parsed.operatorDeployment.getMetadata(), notNullValue());
      assertThat(parsed.operatorDeployment.getMetadata().getName(), equalTo("weblogic-operator"));
      assertThat(parsed.operatorDeployment.getMetadata().getNamespace(), equalTo(inputs.namespace));

      assertThat(parsed.operatorConfigMap, notNullValue());
      assertThat(parsed.operatorConfigMap.getMetadata(), notNullValue());
      assertThat(parsed.operatorConfigMap.getMetadata().getName(), equalTo("operator-config-map"));
      assertThat(parsed.operatorConfigMap.getMetadata().getNamespace(), equalTo(inputs.namespace));

      assertThat(parsed.operatorConfigMap.getData(), notNullValue());
      assertThat(parsed.operatorConfigMap.getData().keySet(),
        containsInAnyOrder("serviceaccount", "targetNamespaces", "externalOperatorCert", "internalOperatorCert"));
      assertThat(parsed.operatorConfigMap.getData().get("serviceaccount"), equalTo(inputs.serviceAccount));
      assertThat(parsed.operatorConfigMap.getData().get("targetNamespaces"), equalTo(inputs.targetNamespaces));
      assertThat(parsed.operatorConfigMap.getData().get("externalOperatorCert"), isEmptyString());
      assertThat(parsed.operatorConfigMap.getData().get("internalOperatorCert"), not(isEmptyOrNullString()));
    }
    {
      ParsedWeblogicOperatorSecurityYaml parsed = parseGeneratedWeblogicOperatorSecurityYaml(inputs);
    }
  }

  private ParsedWeblogicOperatorYaml parseGeneratedWeblogicOperatorYaml(CreateOperatorInputs inputs) throws Exception {
    return new ParsedWeblogicOperatorYaml(weblogicOperatorYamlPath(inputs), inputs);
  }

  private ParsedWeblogicOperatorSecurityYaml parseGeneratedWeblogicOperatorSecurityYaml(CreateOperatorInputs inputs) throws Exception {
    return new ParsedWeblogicOperatorSecurityYaml(weblogicOperatorSecurityYamlPath(inputs), inputs);
  }
}

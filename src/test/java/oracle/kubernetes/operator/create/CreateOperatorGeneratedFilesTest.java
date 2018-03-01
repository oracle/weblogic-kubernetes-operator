// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1beta1RoleBinding;
import io.kubernetes.client.models.V1ConfigMap;
import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Secret;
import io.kubernetes.client.models.V1Service;

import org.apache.commons.codec.binary.Base64;

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

  // TBD - should the tests look like:
  //  a) create a set of inputs, verify all yamls generated to match
  //  b) set one input, verify that one generated yaml matches
  //  c) other?

  @Test
  public void generatedWeblogicOperator_matchesInputsNamespace() throws Exception {
    // force it to create the external operator service so we can check its metadata name
    CreateOperatorInputs inputs = readDefaultInputsFile();
    inputs.namespace = "custom-operator-namespace";
    inputs.externalRestOption = "custom-cert";
    inputs.externalOperatorCert = "custom-certificate-pem";
    inputs.externalOperatorKey = Base64.encodeBase64String("custom-private-key-pem".getBytes());
/*
    inputs.externalRestOption = "self-signed-cert";
    inputs.externalSans = "DNS:localhost";
*/

    ParsedWeblogicOperatorYaml parsed = generateWeblogicOperatorYaml(inputs);

    {
      V1ConfigMap obj = parsed.operatorConfigMap;
      assertThat(obj, notNullValue());
      assertThat_metadataNamespace_matchesInputsNamespace(obj.getMetadata(), inputs);
    }
    {
      ExtensionsV1beta1Deployment obj = parsed.operatorDeployment;
      assertThat(obj, notNullValue());
      assertThat_metadataNamespace_matchesInputsNamespace(obj.getMetadata(), inputs);
    }
    {
      V1Secret obj = parsed.operatorSecrets;
      assertThat(obj, notNullValue());
      assertThat_metadataNamespace_matchesInputsNamespace(obj.getMetadata(), inputs);
    }
    {
      V1Service obj = parsed.externalOperatorService;
      assertThat(obj, notNullValue());
      assertThat_metadataNamespace_matchesInputsNamespace(obj.getMetadata(), inputs);
    }
    {
      V1Service obj = parsed.internalOperatorService;
      assertThat(obj, notNullValue());
      assertThat_metadataNamespace_matchesInputsNamespace(obj.getMetadata(), inputs);
    }
  }

  // TBD - rewrite as a matcher?
  private void assertThat_metadataNamespace_matchesInputsNamespace(V1ObjectMeta metadata, CreateOperatorInputs inputs) {
    assertThat(metadata, notNullValue());
    assertThat(metadata.getNamespace(), equalTo(inputs.namespace));
  }

  private ParsedWeblogicOperatorYaml generateWeblogicOperatorYaml(CreateOperatorInputs inputs) throws Exception {
    assertThat(execCreateOperator(inputs), succeedsAndPrints("Completed"));
    return new ParsedWeblogicOperatorYaml(weblogicOperatorYamlPath(inputs), inputs);
  }

  private ParsedWeblogicOperatorSecurityYaml generateWeblogicOperatorSecurityYaml(CreateOperatorInputs inputs) throws Exception {
    assertThat(execCreateOperator(inputs), succeedsAndPrints());
    return new ParsedWeblogicOperatorSecurityYaml(weblogicOperatorSecurityYamlPath(inputs), inputs);
  }
}

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the image pull secret related input parameters are
 * correct when an image pull secret is configured.
 */
public class CreateOperatorGeneratedFilesHaveImagePullSecretTest {

  private static CreateOperatorInputs inputs;
  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs =
      CreateOperatorInputs.newInputs()
        .imagePullSecretName("test-operator-image-pull-secret-name");
    generatedFiles = GeneratedOperatorYamlFiles.generateOperatorYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_weblogicOperatorYaml_operatorDeployment() throws Exception {
    ExtensionsV1beta1Deployment want =
      weblogicOperatorYaml().getBaseExpectedOperatorDeployment();
    want.getSpec().getTemplate().getSpec().addImagePullSecretsItem(newLocalObjectReference()
      .name(inputs.getImagePullSecretName()));
    assertThat(
      weblogicOperatorYaml().getOperatorDeployment(),
      yamlEqualTo(want));
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}


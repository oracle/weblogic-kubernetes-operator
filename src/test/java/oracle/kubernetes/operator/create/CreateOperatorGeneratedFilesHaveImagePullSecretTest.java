// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the image pull secret related input parameters are
 * correct when an image pull secret is configured.
 */
public class CreateOperatorGeneratedFilesHaveImagePullSecretTest {

  private static final String TEST_OPERATOR_IMAGE_PULL_SECRET_NAME = "test-operator-image-pull-secret-name";

  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    CreateOperatorInputs inputs =
      CreateOperatorInputs.newInputs().imagePullSecretName(TEST_OPERATOR_IMAGE_PULL_SECRET_NAME);
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
      .name(TEST_OPERATOR_IMAGE_PULL_SECRET_NAME));
    assertThat(
      weblogicOperatorYaml().getOperatorDeployment(),
      equalTo(want)
    );
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}


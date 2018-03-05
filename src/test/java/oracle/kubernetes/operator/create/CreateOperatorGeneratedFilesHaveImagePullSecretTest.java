// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.Test;

import java.util.List;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.ExtensionsV1beta1DeploymentSpec;
import io.kubernetes.client.models.V1LocalObjectReference;
import io.kubernetes.client.models.V1PodTemplateSpec;
import io.kubernetes.client.models.V1PodSpec;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the image pull secret related input parameters are
 * correct when an image pull secret is configured.
 */
public class CreateOperatorGeneratedFilesHaveImagePullSecretTest extends CreateOperatorGeneratedFilesTest {

  private static final String TEST_OPERATOR_IMAGE_PULL_SECRET_NAME = "test-operator-image-pull-secret-name";

  @Override
  protected CreateOperatorInputs createInputs() throws Exception {
    return super.createInputs().imagePullSecretName(TEST_OPERATOR_IMAGE_PULL_SECRET_NAME);
  }

  @Test
  public void generatesCorrectDeploymentImagePullSecrets() throws Exception {
    /* Expected yaml:
      kind: Deployment
      spec:
        template:
          spec:
            imagePullSecrets:
              name: test-operator-image-pull-secret-name
     */
    ExtensionsV1beta1Deployment dep = weblogicOperatorYaml.operatorDeployment;
    assertThat(dep, notNullValue());
    ExtensionsV1beta1DeploymentSpec spec = dep.getSpec();
    assertThat(spec, notNullValue());
    V1PodTemplateSpec template = spec.getTemplate();
    assertThat(template, notNullValue());
    V1PodSpec podSpec = template.getSpec();
    assertThat(podSpec.getImagePullSecrets(), notNullValue());
    List<V1LocalObjectReference> secrets = podSpec.getImagePullSecrets();
    assertThat(secrets, notNullValue());
    assertThat(secrets.size(), is(1));
    assertThat(secrets.get(0).getName(), equalTo(TEST_OPERATOR_IMAGE_PULL_SECRET_NAME));
  }
}


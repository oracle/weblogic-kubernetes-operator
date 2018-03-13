// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.V1Job;
import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static oracle.kubernetes.operator.create.YamlUtils.*;
import oracle.kubernetes.weblogic.domain.v1.Domain;
import static org.hamcrest.MatcherAssert.assertThat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-domain-operator.sh
 * creates are correct when the admin node port is enabled, the t3 channel is enabled,
 * there is no image pull secret, and production mode is enabled
 */
public class CreateDomainGeneratedFilesOptionalFeaturesEnabledTest {

  private static CreateDomainInputs inputs;
  private static GeneratedDomainYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs =
      CreateDomainInputs.newInputs()
        .exposeAdminNodePort("true")
        .exposeAdminT3Channel("true")
        .imagePullSecretName("test-weblogic-image-pull-secret-name")
        .productionModeEnabled("true");
    generatedFiles = GeneratedDomainYamlFiles.generateDomainYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_createWeblogicDomainYaml_createWeblogicDomainJob() throws Exception {
    V1Job want = generatedFiles.getCreateWeblogicDomainJobYaml().getExpectedBaseCreateWeblogicDomainJob();
    want.getSpec().getTemplate().getSpec().addImagePullSecretsItem(newLocalObjectReference()
      .name(inputs.getImagePullSecretName()));
    assertThat(
      generatedFiles.getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainJob(),
      yamlEqualTo(want));
  }

  @Test
  public void generatesCorrect_domainCustomResourceYaml_domain() throws Exception {
    Domain want = generatedFiles.getDomainCustomResourceYaml().getBaseExpectedDomain();
    want.getSpec().withExportT3Channels(newStringList().addElement("T3Channel"));
    // there is only one server startup item in the base domain config - set its node port:
    want.getSpec().getServerStartup().get(0).withNodePort(Integer.parseInt(inputs.getAdminNodePort()));
    assertThat(
      generatedFiles.getDomainCustomResourceYaml().getDomain(),
      yamlEqualTo(want));
  }
}

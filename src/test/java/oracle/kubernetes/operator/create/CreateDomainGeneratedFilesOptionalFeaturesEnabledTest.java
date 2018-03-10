// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the all artifacts in the yaml files that create-domain-operator.sh
 * creates are correct when the admin node port is disabled and t3 channel is disable,
 * and there is no image pull secret.
 */
public class CreateDomainGeneratedFilesOptionalFeaturesEnabledTest {

  private static CreateDomainInputs inputs;
  private static GeneratedDomainYamlFiles generatedFiles;

  private static final String TEST_WEBLOGIC_IMAGE_PULL_SECRET_NAME = "test-weblogic-image-pull-secret-name";

  @BeforeClass
  public static void setup() throws Exception {
    inputs =
      CreateDomainInputs.newInputs()
        .exposeAdminNodePort("true")
        .exposeAdminT3Channel("true")
        .imagePullSecretName(TEST_WEBLOGIC_IMAGE_PULL_SECRET_NAME);
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
//System.out.println("MOREAUT_DEBUG create domain job =\n" + YamlUtils.newYaml().dump(generatedFiles.getCreateWeblogicDomainJobYaml().getCreateWeblogicDomainJob()));
    // TBD
  }

  @Test
  public void generatesCorrect_domainCustomResourceYaml_domain() throws Exception {
//System.out.println("MOREAUT_DEBUG domain custom resource =\n" + YamlUtils.newYaml().dump(generatedFiles.getDomainCustomResourceYaml().getDomain()));
    // TBD
  }
}

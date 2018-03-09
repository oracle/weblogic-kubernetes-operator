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
public class CreateDomainGeneratedFilesAdminNodePortDisabledT3ChannelDisabledTest {

  private static CreateDomainInputs inputs;
  private static GeneratedDomainYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateDomainInputs.newInputs(); // defaults to admin node port off, t3 channel off
    generatedFiles = GeneratedDomainYamlFiles.generateDomainYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrect_domainCustomResourceYaml_domain() throws Exception {
//System.out.println("MOREAUT_DEBUG domain=\n" + YamlUtils.newYaml().dump(generatedFiles.getDomainCustomResourceYaml().getDomain()));
    // TBD
  }

  // TBD - other tests for the other generated yaml file's k8s artifacts ...
}

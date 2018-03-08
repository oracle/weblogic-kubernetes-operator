// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import io.kubernetes.client.models.ExtensionsV1beta1Deployment;
import io.kubernetes.client.models.V1Container;

import static oracle.kubernetes.operator.create.KubernetesArtifactUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests that the artifacts in the yaml files that create-weblogic-operator.sh
 * creates that are affected by the elk-related input parameters are correct when
 * elk is enabled.
 */
public class CreateOperatorGeneratedFilesELKEnabledTest {

  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    CreateOperatorInputs inputs = CreateOperatorInputs.newInputs().elkIntegrationEnabled("true");
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
    V1Container operatorContainer = want.getSpec().getTemplate().getSpec().getContainers().get(0);
    operatorContainer.addVolumeMountsItem(newVolumeMount()
      .name("log-dir")
      .mountPath("/logs")
      .readOnly(false));
    want.getSpec().getTemplate().getSpec().addContainersItem(newContainer()
      .name("logstash")
      .image("logstash:5")
      .addArgsItem("-f")
      .addArgsItem("/logs/logstash.conf")
      .addEnvItem(newEnvVar()
        .name("ELASTICSEARCH_HOST")
        .value("elasticsearch.default.svc.cluster.local"))
      .addEnvItem(newEnvVar()
        .name("ELASTICSEARCH_PORT")
        .value("9200"))
      .addVolumeMountsItem(newVolumeMount()
        .name("log-dir")
        .mountPath("/logs")));
    want.getSpec().getTemplate().getSpec().addVolumesItem(newVolume()
      .name("log-dir")
      .emptyDir(newEmptyDirVolumeSource()
        .medium("Memory")));
    assertThat(
      weblogicOperatorYaml().getOperatorDeployment(),
      equalTo(want)
    );
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}

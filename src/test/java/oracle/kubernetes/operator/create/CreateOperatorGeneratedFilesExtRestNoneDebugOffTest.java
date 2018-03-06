// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.models.V1ServicePort;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Tests that the all artifacts in the yaml files that create-weblogic-operator.sh
 * creates are correct when external rest is none, the remote debug port is disabled,
 * elk is disabled and there is no image pull secret.
 */
public class CreateOperatorGeneratedFilesExtRestNoneDebugOffTest {

  private static CreateOperatorInputs inputs;
  private static GeneratedOperatorYamlFiles generatedFiles;

  @BeforeClass
  public static void setup() throws Exception {
    inputs = CreateOperatorInputs.newInputs();
    generatedFiles = GeneratedOperatorYamlFiles.generateOperatorYamlFiles(inputs);
  }

  @AfterClass
  public static void tearDown() throws Exception {
    if (generatedFiles != null) {
      generatedFiles.remove();
    }
  }

  @Test
  public void generatesCorrectOperatorConfigMap() throws Exception {
    weblogicOperatorYaml().assertThatOperatorConfigMapIsCorrect(inputs, ""); // no external operator cert
  }

  @Test
  public void generatesCorrectOperatorSecrets() throws Exception {
    weblogicOperatorYaml().assertThatOperatorSecretsAreCorrect(inputs, ""); // no external operator key
  }

  @Test
  public void generatesCorrectInternalOperatorService() throws Exception {
    /* Expected yaml:
      apiVersion: v1
      kind: Service
      metadata:
        name: internal-weblogic-operator-service
        namespace: inputs.getNamespace()
      spec:
        type: ClusterIP
        selector:
          app: weblogic-operator
        ports:
          - port: 8082
            name: rest-https
    */
    V1Service service = weblogicOperatorYaml().getInternalOperatorService();
    List<V1ServicePort> ports =
      weblogicOperatorYaml().assertThatServiceExistsThenReturnPorts(
        service,
        "internal-weblogic-operator-service",
        inputs.getNamespace(),
        "ClusterIP"
      );
    assertThat(ports.size(), is(1));
    V1ServicePort port = ports.get(0);
    assertThat(port, notNullValue());
    assertThat(port.getName(), equalTo("rest-https"));
    assertThat(port.getPort(), is(8082));
  }

  @Test
  public void generatesCorrectExternalOperatorService() throws Exception {
    weblogicOperatorYaml().assertThatExternalOperatorServiceIsCorrect(inputs, false, false);
  }

  private ParsedWeblogicOperatorYaml weblogicOperatorYaml() {
    return generatedFiles.getWeblogicOperatorYaml();
  }
}

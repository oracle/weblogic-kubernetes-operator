// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
package oracle.kubernetes.operator.create;

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
    // TBD
    /* Expected yaml:
      spec:
        template:
          spec:
            containers:
            - name: weblogic-operator
              - mountPath: /logs
                name: log-dir
                readOnly: false
            - name: logstash
              image: logstash:5
              args: ["-f", "/logs/logstash.conf"]
              volumeMounts:
              - mountPath: /logs
                name: log-dir
              env:
              - name: ELASTICSEARCH_HOST
                value: "elasticsearch.default.svc.cluster.local"
              - name: ELASTICSEARCH_PORT
                value: "9200"
            volumes:
            - name: log-dir
              emptyDir:
                medium: Memory
      */
  }
}

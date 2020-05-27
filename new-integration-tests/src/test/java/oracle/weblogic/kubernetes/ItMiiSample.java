// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.MII_SAMPLES_SCRIPT;
import static oracle.weblogic.kubernetes.TestConstants.MII_SAMPLES_WORK_DIR;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test model in image sample")
@IntegrationTest
public class ItMiiSample implements LoggedTest {

  @Test
  @DisplayName("Test MII Sample")
  public void testMiiSample(@Namespaces(2) List<String> namespaces) {

    logger.info("Getting unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String operatorNamespace = namespaces.get(0);

    logger.info("Getting unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    String domainNamespace = namespaces.get(1);

    String cmd = MII_SAMPLES_SCRIPT + " -initial";
    Map envMap = new HashMap<String, String>();
    envMap.put("DOMAIN_NAMESPACE", domainNamespace);
    envMap.put("WORKDIR", MII_SAMPLES_WORK_DIR);

    boolean success = Command.withParams(new CommandParams()
                    .command(cmd)
                    .env(envMap)
                    .redirect(true)).executeAndVerify("Woo hoo! Finished without errors");
    assertTrue(success, "Samples script did not execute successfully");
  }
}

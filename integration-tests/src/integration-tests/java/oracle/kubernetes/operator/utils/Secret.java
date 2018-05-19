// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.logging.Logger;

public class Secret {
  private String secretName;
  private String namespace;
  private String username;
  private String password;
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public Secret(String namespace, String secretName, String username, String password) {
    this.namespace = namespace;
    this.secretName = secretName;
    this.username = username;
    this.password = password;

    String command = "kubectl -n " + namespace + " delete secret " + secretName;
    logger.info("Running " + command);
    TestUtils.executeCommand("kubectl -n " + namespace + " delete secret " + secretName);
    command =
        "kubectl -n "
            + this.namespace
            + ""
            + "	create secret generic "
            + this.secretName
            + " --from-literal=username="
            + this.username
            + " --from-literal=password="
            + this.password;
    logger.info("Running " + command);
    String cmdResult = TestUtils.executeCommand(command);
    if (!cmdResult.contains("created")) {
      throw new IllegalArgumentException("Couldn't create secret \n" + cmdResult);
    }
  }
}

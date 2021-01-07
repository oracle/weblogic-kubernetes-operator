// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;

/**
 * Assertions for Docker usages.
 */
public class Docker {

  /**
   * Check if the Docker image containing the search string exists.
   * @param searchString search string
   * @return true on success
   */
  public static boolean doesImageExist(String searchString) {
    CommandParams cmdParams = Command.defaultCommandParams()
        .command(String.format("docker images | grep %s", searchString))
        .saveResults(true)
        .redirect(false);

    return Command.withParams(cmdParams)
        .executeAndVerify(searchString);
  }
}

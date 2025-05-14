// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;

import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;

/**
 * Assertions for image usages.
 */
public class Image {

  /**
   * Check if the image containing the search string exists.
   * @param searchString search string
   * @return true on success
   */
  public static boolean doesImageExist(String searchString) {
    CommandParams cmdParams = Command.defaultCommandParams()
        .command(String.format("%s images | grep %s", WLSIMG_BUILDER, searchString))
        .saveResults(true)
        .redirect(false);

    return Command.withParams(cmdParams)
        .executeAndVerify(searchString);
  }
}

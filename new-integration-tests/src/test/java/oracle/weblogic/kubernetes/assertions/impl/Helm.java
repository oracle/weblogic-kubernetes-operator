// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;

/**
 * Assertions for Helm usages.
 */
public class Helm {

  /**
   * Check Helm release status is deployed.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @return true on success
   */
  public static boolean isReleaseDeployed(String releaseName, String namespace) {
    CommandParams cmdParams = Command.defaultCommandParams()
        .command(String.format("helm list -n %s --filter %s", namespace, releaseName))
        .saveResults(true)
        .redirect(false);

    if (Command.withParams(cmdParams)
        .execute()) {
      return cmdParams.stdout().toLowerCase().contains("deployed");
    }
    return false;
  }

}

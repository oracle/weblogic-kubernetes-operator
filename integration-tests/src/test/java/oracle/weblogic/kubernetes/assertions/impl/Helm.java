// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
    return checkHelmReleaseStatus(releaseName, namespace, "deployed");
  }

  /**
   * Check Helm release status is failed.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @return true on success
   */
  public static boolean isReleaseFailed(String releaseName, String namespace) {
    return checkHelmReleaseStatus(releaseName, namespace, "failed");
  }

  /**
   * Check Helm release status against expected.
   * @param releaseName release name which unique in a namespace
   * @param namespace namespace name
   * @param status expected value
   * @return true on success
   */
  public static boolean checkHelmReleaseStatus(String releaseName, String namespace, String status) {
    CommandParams cmdParams = Command.defaultCommandParams()
        .command(String.format("helm list -n %s --filter %s", namespace, releaseName))
        .saveResults(true)
        .redirect(false);

    if (Command.withParams(cmdParams)
        .execute()) {
      return cmdParams.stdout().toLowerCase().contains(status);
    }
    return false;
  }

  /**
   * Check Helm release revision against expected.
   * @param releaseName release name which is unique in a namespace
   * @param namespace namespace name
   * @param revision expected value
   * @return true on success
   */
  public static boolean checkHelmReleaseRevision(String releaseName, String namespace, String revision) {
    CommandParams cmdParams = Command.defaultCommandParams()
        .command(String.format("helm status %s -n %s", releaseName, namespace))
        .saveResults(true)
        .redirect(false);

    if (Command.withParams(cmdParams)
        .execute()) {
      return cmdParams.stdout().toLowerCase().contains("revision: " + revision);
    }
    return false;
  }

}

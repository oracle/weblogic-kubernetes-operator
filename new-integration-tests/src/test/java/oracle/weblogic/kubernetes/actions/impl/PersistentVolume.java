// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvNotExists;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class PersistentVolume {

  private static final ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Create a Kubernetes Persistent Volume.
   *
   * @param persistentVolume V1PersistentVolume object containing persistent volume
   *     configuration data
   * @return true if successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean create(V1PersistentVolume persistentVolume) throws ApiException {
    return Kubernetes.createPv(persistentVolume);
  }

  /**
   * Delete the Kubernetes Persistent Volume.
   *
   * @param name name of the Persistent Volume
   * @return true if successful
   */
  public static boolean delete(String name) {
    Kubernetes.deletePv(name);
    // check the persistent volume and persistent volume claim not exist
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for persistent volume {0} to be deleted "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                name,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> pvNotExists(name, null),
            String.format("pvNotExists failed with ApiException when checking pv %s", name)));
    return true;
  }
}

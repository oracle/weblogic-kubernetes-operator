// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Date;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.calls.KubernetesApiAuthenticationHealth;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.work.Cancellable;

/**
 * This task maintains the "liveness" indicator so that Kubernetes knows the Operator is still
 * alive.
 */
public class DeploymentLiveness implements Runnable {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final Collection<Cancellable> futures;
  private final File livenessFile;
  private final File authenticationFailureFile;

  /**
   * Creates the liveness updater.
   * @param futures critical operator tasks whose termination must fail liveness
   * @param delegate supplies the deployment home
   */
  public DeploymentLiveness(Collection<Cancellable> futures, CoreDelegate delegate) {
    this.futures = futures;
    livenessFile = new File(delegate.getDeploymentHome(), ".alive");
    authenticationFailureFile = new File(delegate.getDeploymentHome(), ".api-authentication-failure");
  }

  @Override
  public void run() {
    if (KubernetesApiAuthenticationHealth.shouldFailLiveness()) {
      createAuthenticationFailureFile();
      return;
    }
    deleteAuthenticationFailureFile();

    try {
      if (livenessFile.createNewFile()) {
        LOGGER.fine("Liveness file created");
      }
    } catch (IOException ioe) {
      LOGGER.warning(MessageKeys.COULD_NOT_CREATE_LIVENESS_FILE);
    }
    if (futures.stream().filter(Cancellable::isDoneOrCancelled).findAny().isEmpty()
          && livenessFile.setLastModified(new Date().getTime())) {
      LOGGER.fine("Liveness file last modified time set");
    }
  }

  private void createAuthenticationFailureFile() {
    if (authenticationFailureFile.exists()) {
      return;
    }
    try {
      Files.writeString(authenticationFailureFile.toPath(),
          KubernetesApiAuthenticationHealth.DIAGNOSTIC_MARKER
              + ": operator Kubernetes API authentication failed continuously; intentionally failing liveness.");
    } catch (IOException ioe) {
      LOGGER.warning(MessageKeys.EXCEPTION, ioe);
    }
  }

  private void deleteAuthenticationFailureFile() {
    try {
      Files.deleteIfExists(authenticationFailureFile.toPath());
    } catch (IOException ioe) {
      LOGGER.warning(MessageKeys.EXCEPTION, ioe);
    }
  }
}

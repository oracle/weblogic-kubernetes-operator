// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import oracle.kubernetes.common.logging.MessageKeys;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/**
 * This task maintains the "liveness" indicator so that Kubernetes knows the Operator is still
 * alive.
 */
public class DeploymentLiveness implements Runnable {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final File livenessFile;

  public DeploymentLiveness(CoreDelegate delegate) {
    livenessFile = new File(delegate.getProbesHome(), ".alive");
  }

  @Override
  public void run() {
    try {
      if (livenessFile.createNewFile()) {
        LOGGER.fine("Liveness file created");
      }
    } catch (IOException ioe) {
      LOGGER.warning(MessageKeys.COULD_NOT_CREATE_LIVENESS_FILE);
    }
    if (livenessFile.setLastModified(new Date().getTime())) {
      LOGGER.fine("Liveness file last modified time set");
    }
  }
}

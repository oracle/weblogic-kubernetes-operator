// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

/** This task creates the "readiness" indicator so that Kubernetes knows the Operator is ready. */
public class DeploymentReady {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final File readinessFile;

  public DeploymentReady(CoreDelegate delegate) {
    readinessFile = new File(delegate.getProbesHome(), ".ready");
  }

  /**
   * Create the Operator readiness indicator.
   * @throws IOException if the readiness file does not exist
   */
  public void create() throws IOException {
    if (readinessFile.createNewFile()) {
      LOGGER.fine("Readiness file created");
    }
  }
}

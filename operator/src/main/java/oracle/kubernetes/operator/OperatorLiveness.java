// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/**
 * This thread maintains the "liveness" indicator so that Kubernetes knows the Operator is still
 * alive.
 */
public class OperatorLiveness extends Thread {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final File livenessFile = new File("/operator/.alive");

  @Override
  public void run() {
    // every five seconds we need to update the last modified time on the liveness file

    while (true) {

      if (!livenessFile.exists()) {
        try {
          livenessFile.createNewFile();
        } catch (IOException ioe) {
          LOGGER.info(MessageKeys.COULD_NOT_CREATE_LIVENESS_FILE);
        }
      }
      livenessFile.setLastModified(new Date().getTime());

      try {
        Thread.sleep(5000);
      } catch (InterruptedException ignore) {
      }
    }
  }
}

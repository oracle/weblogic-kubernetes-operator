// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.logging.MessageKeys;

/**
 * This task maintains the "liveness" indicator so that Kubernetes knows the Webhook is still
 * alive.
 */
public class WebhookLiveness implements Runnable {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private final File livenessFile = new File("/webhook/.alive");

  @Override
  public void run() {
    if (!livenessFile.exists()) {
      try {
        livenessFile.createNewFile();
      } catch (IOException ioe) {
        LOGGER.warning(MessageKeys.COULD_NOT_CREATE_WEBHOOK_LIVENESS_FILE);
      }
    }
    livenessFile.setLastModified(new Date().getTime());
  }
}

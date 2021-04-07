// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SSL utility class for tests.
 */
public class SslUtils {

  /**
   * Generate SSL KeyStore in JKS format.
   */
  public static void generateJksStores() {
    LoggingFacade logger = getLogger();
    Path jksInstallPath =
        Paths.get(RESOURCE_DIR, "bash-scripts", "generate-selfsign-jks.sh");
    String installScript = jksInstallPath.toString();
    String command =
        String.format("%s %s", installScript, RESULTS_ROOT);
    logger.info("JKS Store creation command {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute());
    
    // Copy the scripts to RESULTS_ROOT
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(RESOURCE_DIR, "bash-scripts", "generate-selfsign-jks.sh"),
        Paths.get(RESULTS_ROOT, "generate-selfsign-jks.sh"), 
        StandardCopyOption.REPLACE_EXISTING),
        "Copy generate-selfsign-jks.sh to RESULTS_ROOT failed");
  }

}

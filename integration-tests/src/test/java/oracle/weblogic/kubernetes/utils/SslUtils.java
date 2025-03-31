// Copyright (c) 2021, 2025, Oracle and/or its affiliates.
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
   * @param uniquePath keystore directory
   */
  public static void generateJksStores(String uniquePath) {
    generateJksStores(uniquePath, "generate-selfsign-jks.sh");
  }

  /**
   * Generate SSL KeyStore in JKS format.
   */
  public static void generateJksStores() {
    generateJksStores(RESULTS_ROOT);
  }

  /**
   * Generate SSL KeyStore in JKS format.
   * @param uniquePath keystore directory
   * @param script shell scriptName
   */
  public static void generateJksStores(String uniquePath, String script) {
    LoggingFacade logger = getLogger();
    Path jksInstallPath =
        Paths.get(RESOURCE_DIR, "bash-scripts", script);
    String installScript = jksInstallPath.toString();
    String command =
        String.format("%s %s", installScript, uniquePath);
    logger.info("JKS Store creation command {0}", command);
    assertTrue(() -> Command.withParams(
            defaultCommandParams()
                .command(command)
                .redirect(false))
        .execute());

    // Copy the scripts to RESULTS_ROOT
    assertDoesNotThrow(() -> Files.copy(
            Paths.get(RESOURCE_DIR, "bash-scripts", script),
            Paths.get(uniquePath, script),
            StandardCopyOption.REPLACE_EXISTING),
        "Copy " + script + " to RESULTS_ROOT failed");
  }

}

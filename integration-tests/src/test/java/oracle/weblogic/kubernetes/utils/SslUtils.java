// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
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

  /**
   * Generate container to create KeyStore directory in specified pv.
   * @param pvName  pv name
   * @param keyStorePath keystore path
   * @param mountPath pv mount path
   */
  public static synchronized V1Container createKeyStoreDirContainer(String pvName,
                                                                    String mountPath,
                                                                    String keyStorePath) {
    String argCommand = "mkdir -p " + mountPath + "/" + keyStorePath;

    V1Container container = new V1Container()
        .name("create-keystore-dir") // change the ownership of the pv to opc:opc
        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
        .imagePullPolicy(IMAGE_PULL_POLICY)
        .addCommandItem("/bin/sh")
        .addArgsItem(argCommand)
        .volumeMounts(Arrays.asList(
            new V1VolumeMount()
                .name(pvName)
                .mountPath(mountPath)))
        .securityContext(new V1SecurityContext()
            .runAsGroup(0L)
            .runAsUser(0L));
    return container;
  }
}

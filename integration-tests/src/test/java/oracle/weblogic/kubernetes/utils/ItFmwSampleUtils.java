// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.addSccToDBSvcAccount;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility class for FMW domain sample to start DB service and RCU schema.
 */
public class ItFmwSampleUtils {

  private static final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private static final Path tempSamplePath = Paths.get(WORK_DIR, "fmw-diisample-testing");

  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  /**
   * Start Oracle DB instance, create rcu pod and load database schema in the specified namespace.
   *
   * @param dbImage image name of database
   * @param dbNamespace namespace where DB and RCU schema are going to start
   * @param dbPort NodePort of DB
   * @throws ApiException if any error occurs when setting up RCU database
   */

  public static void setupDBBySample(String dbImage, String dbNamespace, int dbPort,
       Path tempSamplePath) {
    LoggingFacade logger = getLogger();
    setupSample(tempSamplePath);
    // create pull secrets when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(dbNamespace);

    logger.info("Start Oracle DB with dbImage: {0}, dbPort: {1}, dbNamespace: {2}",
        dbImage, dbPort, dbNamespace);
    if (OKD) {
      addSccToDBSvcAccount("default", dbNamespace);
    }
    startOracleDB(dbImage, dbPort, dbNamespace, tempSamplePath);
  }

  /**
   * Start Oracle DB pod and service in the specified namespace.
   *
   * @param dbBaseImageName full image name for DB deployment
   * @param dbPort NodePort of DB
   * @param dbNamespace namespace where DB instance is going to start
   */
  public static synchronized void startOracleDB(String dbBaseImageName, int dbPort, String dbNamespace,
       Path tempSamplePath) {
    LoggingFacade logger = getLogger();
    Path dbSamplePathBase = Paths.get(tempSamplePath.toString(), "/scripts/create-oracle-db-service/");
    String script = Paths.get(dbSamplePathBase.toString(),  "start-db-service.sh").toString();
    logger.info("Script for startOracleDB: {0}", script);

    String command = script
        + " -i " + dbBaseImageName
        + " -p " + dbPort
        + " -s " + BASE_IMAGES_REPO_SECRET
        + " -n " + dbNamespace;
    logger.info("Command for startOracleDb: {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .saveResults(true)
            .redirect(true))
        .execute(), "Failed to execute command: " + command);

    // sleep for a while to make sure the DB pod is created
    assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(10));
  }

  /**
   copy samples directory to a temporary location.
   @param tempSamplePath path of the temporary sample location
   */
  public static void setupSample(Path tempSamplePath) {
    LoggingFacade logger = getLogger();
    assertDoesNotThrow(() -> {
      logger.info("Deleting and recreating {0}", tempSamplePath);
      deleteDirectory(tempSamplePath.toFile());
      Files.createDirectories(tempSamplePath);

      logger.info("Copying {0} to {1}", samplePath, tempSamplePath);
      copyDirectory(samplePath.toFile(), tempSamplePath.toFile());
    });

    String command = "chmod -R 755 " + tempSamplePath;
    logger.info("The command to be executed: " + command);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to chmod tempSamplePath");
  }

  /**
   * Create a RCU schema in the namespace.
   *
   * @param fmwBaseImageName the FMW image name
   * @param rcuPrefix prefix of RCU schema
   * @param dbUrl URL of DB
   * @param dbNamespace namespace of DB where RCU is
   */
  public static void createRcuSchema(String fmwBaseImageName, String rcuPrefix,
       String dbUrl, String dbNamespace, Path tempSamplePath) {
    LoggingFacade logger = getLogger();
    Path rcuSamplePathBase = Paths.get(tempSamplePath.toString(), "/scripts/create-rcu-schema");
    String script = Paths.get(rcuSamplePathBase.toString(), "create-rcu-schema.sh").toString();
    String outputPath = Paths.get(rcuSamplePathBase.toString(), "rcuoutput").toString();
    logger.info("Script for createRcuSchema: {0}", script);
    String command = script
        + " -i " + fmwBaseImageName
        + " -p " + BASE_IMAGES_REPO_SECRET
        + " -s " + rcuPrefix
        + " -d " + dbUrl
        + " -n " + dbNamespace
        + " -o " + outputPath;
    logger.info("Command for createRcuSchema: {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .saveResults(true)
            .redirect(true))
        .execute(), "Failed to execute command: " + command);
  }

}

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
import static oracle.weblogic.kubernetes.TestConstants.NFS_SERVER;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
//import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
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

  /**
   * add security context constraints to the service account of db namespace for sample.
   * @param serviceAccount - service account to add to scc
   * @param namespace - namespace to which the service account belongs
   */
  private static void addSccToDBSvcAccount(String serviceAccount, String namespace) {
    LoggingFacade logger = getLogger();
    logger.info("Adding security context constraints to serviceAccount: {1}, namespace: {2}",
        serviceAccount, namespace);
    assertTrue(new Command()
        .withParams(new CommandParams()
            .command("oc adm policy add-scc-to-user anyuid -z " + serviceAccount + " -n " + namespace))
        .execute(), "oc expose service failed");
  }

  /** Create a persistent volume and persistent volume claim for the FMW sample.
   * @param domainUid domain UID
   * @param domainNamespace name of the namespace in which to create the persistent volume claim
   * @Param tempSamplePath  path of the temporary sample location
   */
  public static void createPvPvc(String domainUid, String domainNamespace,
       Path tempSamplePath, String className) {
    LoggingFacade logger = getLogger();
    String pvName = domainUid + "-weblogic-sample-pv";
    String pvcName = domainUid + "-weblogic-sample-pvc";

    Path pvpvcBase = Paths.get(tempSamplePath.toString(),
        "scripts/create-weblogic-domain-pv-pvc");

    // create pv and pvc
    assertDoesNotThrow(() -> {
      if (!OKD) {
        // when tests are running in local box the PV directories need to exist
        Path pvHostPathBase;
        pvHostPathBase = Files.createDirectories(Paths.get(PV_ROOT, className));
        Path pvHostPath;
        pvHostPath = Files.createDirectories(Paths.get(PV_ROOT, className, pvName));

        logger.info("Creating PV directory host path {0}", pvHostPath);
        deleteDirectory(pvHostPath.toFile());
        Files.createDirectories(pvHostPath);
        String command1 = "chmod -R 777 " + pvHostPathBase;
        logger.info("Command1 to be executed: " + command1);
        assertTrue(new Command()
            .withParams(new CommandParams()
                .command(command1))
            .execute(), "Failed to chmod " + PV_ROOT);

        // set the pvHostPath in create-pv-pvc-inputs.yaml
        replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
            "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + pvHostPath);
      } else {
        replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
            "weblogicDomainStorageType: HOST_PATH", "weblogicDomainStorageType: NFS");
        replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
            "#weblogicDomainStorageNFSServer: nfsServer", "weblogicDomainStorageNFSServer: "
                + NFS_SERVER);
        replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
            "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + PV_ROOT);
        replaceStringInFile(Paths.get(pvpvcBase.toString(), "pv-template.yaml").toString(),
            "%DOMAIN_UID%%SEPARATOR%%BASE_NAME%-storage-class",
            "%DOMAIN_UID%%SEPARATOR%%BASE_NAME%-okd-nfsmnt");

      }

      //set namespace in create-pv-pvc-inputs.yaml
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
            "namespace: default", "namespace: " + domainNamespace);
      // set the pv storage policy to Recycle in create-pv-pvc-inputs.yaml
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "weblogicDomainStorageReclaimPolicy: Retain", "weblogicDomainStorageReclaimPolicy: Recycle");
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "domainUID:", "domainUID: " + domainUid);
    });

    // generate the create-pv-pvc-inputs.yaml
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
        + Paths.get(pvpvcBase.toString(), "create-pv-pvc.sh").toString()
        + " -i " + Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString()
        + " -o "
        + Paths.get(pvpvcBase.toString()));

    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create create-pv-pvc-inputs.yaml");

    //create pv and pvc
    params = new CommandParams().defaults();
    params.command("kubectl create -f " + Paths.get(pvpvcBase.toString(),
        "pv-pvcs/" + domainUid + "-weblogic-sample-pv.yaml").toString());
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create pv");

    testUntil(
        assertDoesNotThrow(
            () -> pvExists(pvName, null),
            String.format("pvExists failed with ApiException for pv %s", pvName)),
        logger,
        "pv {0} to be ready",
        pvName);

    params = new CommandParams().defaults();
    params.command("kubectl create -f " + Paths.get(pvpvcBase.toString(),
        "pv-pvcs/" + domainUid + "-weblogic-sample-pvc.yaml").toString());
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create pvc");

    testUntil(
        assertDoesNotThrow(
            () -> pvcExists(pvcName, domainNamespace),
            String.format("pvcExists failed with ApiException for pvc %s", pvcName)),
        logger,
        "pv {0} to be ready in namespace {1}",
        pvcName,
        domainNamespace);
  }


}

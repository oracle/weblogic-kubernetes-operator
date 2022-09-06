// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.FSS_DIR;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.NFS_SERVER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.assertions.impl.PersistentVolume.doesPVExist;
import static oracle.weblogic.kubernetes.assertions.impl.PersistentVolume.pvNotExist;
import static oracle.weblogic.kubernetes.assertions.impl.PersistentVolumeClaim.doesPVCExist;
import static oracle.weblogic.kubernetes.assertions.impl.PersistentVolumeClaim.pvcNotExist;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to FMW domain samples.
 */
@DisplayName("Verify the JRF domain on pv sample using wlst and wdt")
@IntegrationTest
@Tag("samples")
@Tag("oke-sequential")
@Tag("olcne")
@Tag("kind-parallel")
@Tag("toolkits-srg")
public class ItFmwSample {

  private static String dbNamespace = null;
  private static String domainNamespace = null;

  private static final Path samplePath = get(ITTESTS_DIR, "../kubernetes/samples");
  private static final Path dbSamplePath = get(WORK_DIR, "fmw-sample-testing", "db");

  private static final String ORACLEDBURLPREFIX = "oracle-db.";
  private static final String ORACLEDBSUFFIX = ".svc.cluster.local:1521/devpdb.k8s";
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static String dbUrl = null;

  private static LoggingFacade logger = null;

  private static final String[] params = { "wdt:fmwsamplepv", "wlst:fmwsamplepv2"};

  // generates the stream of objects used by parametrized test.
  private static Stream<String> paramProvider() {
    return Arrays.stream(params);
  }

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Pull FMW image and Oracle DB image if needed.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    String opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for JRF domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace = namespaces.get(2);

    int dbPort = getNextFreePort();
    logger.info("Start DB and for namespace: {0}, "
            + "dbImage: {2},  fmwImage: {3}, dbPort: {4} ", dbNamespace,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC, dbPort);
    assertDoesNotThrow(() -> setupDBBySample(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        dbNamespace, dbPort),
        String.format("Failed to create DB in the namespace %s with dbPort %d ",
            dbNamespace, dbPort));

    for (String param: params) {
      String rcuSchemaPrefix = param.split(":")[1];

      logger.info("Create RCU schema with fmwImage: {0}, rcuSchemaPrefix: {1}, dbUrl: {2}, "
          + " dbNamespace: {3}:", FMWINFRA_IMAGE_TO_USE_IN_SPEC, rcuSchemaPrefix, dbUrl, dbNamespace);
      assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, rcuSchemaPrefix, dbUrl, dbNamespace),
          String.format("Failed to create RCU schema for prefix %s in the namespace %s with dbUrl %s",
              rcuSchemaPrefix, dbNamespace, dbUrl));
    }



    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Test JRF domain on pv samples using domains created by wlst and wdt. Verify Pod is ready and
   * service exists for both admin server and managed servers. Verify EM console is accessible.
   *
   * @param model domain name and script type to create domain. Acceptable values of format String:wlst|wdt
   */
  @ParameterizedTest
  @MethodSource("paramProvider")
  @DisplayName("Test FMW domain on PV sample")
  void testFmwDomainInPv(String model) {

    String domainUid = model.split(":")[1];
    String script = model.split(":")[0];
    Path testSamplePath = get(WORK_DIR, "fmw-sample-testing", domainNamespace, "fwmdomainInPV", domainUid, script);

    setupSample(testSamplePath);

    // create persistent volume and persistent volume claims used by the samples
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    createPvPvc(domainUid, testSamplePath, pvName, pvcName);

    //create WebLogic secrets for the domain
    createSecretWithUsernamePassword(domainUid + "-weblogic-credentials", domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    // create RCU credential secret
    createRcuSecretWithUsernamePassword(domainUid + "-rcu-credentials", domainNamespace,
        RCUSCHEMAUSERNAME, RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD);

    Path sampleBase = get(testSamplePath.toString(),
        "scripts/create-fmw-infrastructure-domain/domain-home-on-pv");

    //update create-domain-inputs.yaml with the values from this test
    updateDomainInputsFile(domainUid, sampleBase, pvcName);

    // change image name
    assertDoesNotThrow(() -> {
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "createDomainFilesDir: wlst", "createDomainFilesDir: "
                     + script);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "image: container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4",
              "image: " + FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    });

    // run create-domain.sh to create domain.yaml file, run kubectl to create the domain and verify
    //verify EM console is accessible
    createDomainAndVerify(domainUid, sampleBase);

  }

  private void createDomainAndVerify(String domainName, Path sampleBase) {

    // run create-domain.sh to create domain.yaml file
    logger.info("Run create-domain.sh to create domain.yaml file");
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
            + get(sampleBase.toString(), "create-domain.sh").toString()
            + " -i " + get(sampleBase.toString(), "create-domain-inputs.yaml").toString()
            + " -o "
            + get(sampleBase.toString()));

    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain.yaml");

    // run kubectl to create the domain
    logger.info("Run kubectl to create the domain");
    params = new CommandParams().defaults();
    params.command("kubectl apply -f "
            + get(sampleBase.toString(), "weblogic-domains/" + domainName + "/domain.yaml").toString());

    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainName, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainName,
        domainNamespace);

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainName + "-" + adminServerName;

    final String managedServerNameBase = "managed-server";
    String managedServerPodNamePrefix = domainName + "-" + managedServerNameBase;
    int replicaCount = 2;

    // verify the admin server service and pod is created
    checkPodReadyAndServiceExists(adminServerPodName, domainName, domainNamespace);

    // verify managed server services created and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainName, domainNamespace);
    }

    checkAccessToEMconsole(adminServerPodName);
  }

  private void createPvPvc(String domainUid, Path testSamplePath, String pvName, String pvcName) {

    // delete pvc first if exists
    if (assertDoesNotThrow(() -> doesPVCExist(pvcName, domainNamespace))) {
      deletePersistentVolumeClaim(pvcName, domainNamespace);
    }
    testUntil(
        assertDoesNotThrow(() -> pvcNotExist(pvcName, domainNamespace),
            String.format("pvcNotExist failed for pvc %s in namespace %s", pvcName, domainNamespace)),
          logger, "pvc {0} to be deleted in namespace {1}", pvcName, domainNamespace);

    // delete pv first if exists
    if (assertDoesNotThrow(() -> doesPVExist(pvName, null))) {
      deletePersistentVolume(pvName);
    }
    testUntil(
        assertDoesNotThrow(() -> pvNotExist(pvName, null),
            String.format("pvNotExists failed for pv %s", pvName)), logger, "pv {0} to be deleted", pvName);

    Path pvpvcBase = get(testSamplePath.toString(), "scripts/create-weblogic-domain-pv-pvc");

    // create pv and pvc
    assertDoesNotThrow(() -> {
      // when tests are running in local box the PV directories need to exist
      if (!OKE_CLUSTER) {
        Path pvHostPathBase;
        pvHostPathBase = createDirectories(get(PV_ROOT, this.getClass().getSimpleName()));
        Path pvHostPath;
        pvHostPath = createDirectories(get(PV_ROOT, this.getClass().getSimpleName(), pvName));

        logger.info("Creating PV directory host path {0}", pvHostPath);
        deleteDirectory(pvHostPath.toFile());
        createDirectories(pvHostPath);
        String command1 = "chmod -R 777 " + pvHostPathBase;
        logger.info("Command1 to be executed: " + command1);
        assertTrue(Command
                .withParams(new CommandParams()
                        .command(command1))
                .execute(), "Failed to chmod " + PV_ROOT);


        // set the pvHostPath in create-pv-pvc-inputs.yaml
        replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
                "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + pvHostPath);
      } else {
        // set the pvHostPath in create-pv-pvc-inputs.yaml
        replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
                "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + FSS_DIR);
        replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
                "weblogicDomainStorageType: HOST_PATH", "weblogicDomainStorageType: NFS");
        replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
                "#weblogicDomainStorageNFSServer: nfsServer", "weblogicDomainStorageNFSServer: " + NFS_SERVER);
        replaceStringInFile(get(pvpvcBase.toString(), "pvc-template.yaml").toString(),
                "storageClassName: %DOMAIN_UID%%SEPARATOR%%BASE_NAME%-storage-class", "storageClassName: oci-fss");
        replaceStringInFile(get(pvpvcBase.toString(), "pv-template.yaml").toString(),
                "storageClassName: %DOMAIN_UID%%SEPARATOR%%BASE_NAME%-storage-class", "storageClassName: oci-fss");
      }
      // set the namespace in create-pv-pvc-inputs.yaml
      replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "namespace: default", "namespace: " + domainNamespace);
      replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "domainUID:", "domainUID: " + domainUid);
      replaceStringInFile(get(pvpvcBase.toString(), "pvc-template.yaml").toString(),
              "%DOMAIN_UID%%SEPARATOR%%BASE_NAME%-pvc", pvcName);
      replaceStringInFile(get(pvpvcBase.toString(), "pv-template.yaml").toString(),
              "%DOMAIN_UID%%SEPARATOR%%BASE_NAME%-pv", pvName);
    });

    // generate the create-pv-pvc-inputs.yaml
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
        + get(pvpvcBase.toString(), "create-pv-pvc.sh").toString()
        + " -i " + get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString()
        + " -o "
        + get(pvpvcBase.toString()));

    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create create-pv-pvc-inputs.yaml");

    //create pv and pvc
    params = new CommandParams().defaults();
    params.command("kubectl create -f " + get(pvpvcBase.toString(),
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
    params.command("kubectl create -f " + get(pvpvcBase.toString(),
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

  // copy samples directory to a temporary location
  private static void setupSample(Path testSamplePath) {
    assertDoesNotThrow(() -> {
      logger.info("Deleting and recreating {0}", testSamplePath);
      deleteDirectory(testSamplePath.toFile());
      createDirectories(testSamplePath);

      logger.info("Copying {0} to {1}", samplePath, testSamplePath);
      copyDirectory(samplePath.toFile(), testSamplePath.toFile());
    });

    String command = "chmod -R 755 " + testSamplePath;
    logger.info("The command to be executed: " + command);
    assertTrue(Command
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to chmod testSamplePath");
  }

  private void updateDomainInputsFile(String domainUid, Path sampleBase, String pvcName) {
    // in general the node port range has to be between 30,100 to 32,767
    // to avoid port conflict because of the delay in using it, the port here
    // starts with 30172
    final int t3ChannelPort = getNextFreePort();
    final int adminNodePort = getNextFreePort();

    // change namespace from default to custom, domain name, and t3PublicAddress
    assertDoesNotThrow(() -> {
      if (OKE_CLUSTER && sampleBase.toString().contains("domain-home-on-pv")) {
        String chownCommand1 = "chown 1000:0 %DOMAIN_ROOT_DIR%"
                + "/. && find %DOMAIN_ROOT_DIR%"
                + "/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0  chown -R 1000:0";
        replaceStringInFile(get(sampleBase.toString(), "create-domain-job-template.yaml").toString(),
                "chown -R 1000:0 %DOMAIN_ROOT_DIR%", chownCommand1);
      }

      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "namespace: default", "namespace: " + domainNamespace);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "domain1", domainUid);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#t3PublicAddress:", "t3PublicAddress: " + K8S_NODEPORT_HOST);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "t3ChannelPort: 30012", "t3ChannelPort: " + t3ChannelPort);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "exposeAdminT3Channel: false", "exposeAdminT3Channel: true");
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "exposeAdminNodePort: false", "exposeAdminNodePort: true");
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "adminNodePort: 30701", "adminNodePort: " + adminNodePort);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#imagePullSecretName:", "imagePullSecretName: " + BASE_IMAGES_REPO_SECRET_NAME);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "rcuDatabaseURL: database:1521/service", "rcuDatabaseURL: " + dbUrl);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "domainHome: /shared/domains", "domainHome: /shared/"
                      + domainNamespace + "/" + domainUid + "/domains");
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "logHome: /shared/logs", "logHome: /shared/"
                      + domainNamespace + "/" + domainUid + "/logs");
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "persistentVolumeClaimName: " + domainUid + "-weblogic-sample-pvc",
              "persistentVolumeClaimName: " + pvcName);
    });
  }

  /**
   * Start Oracle DB instance, create rcu pod and load database schema in the specified namespace.
   *
   * @param dbImage image name of database
   * @param fmwImage image name of FMW
]   * @param dbNamespace namespace where DB and RCU schema are going to start
   * @param dbPort NodePort of DB
   * @throws Exception if any error occurs when setting up RCU database
   */

  private static void setupDBBySample(String dbImage, String fmwImage,
       String dbNamespace, int dbPort) throws ApiException {
    LoggingFacade logger = getLogger();

    setupSample(dbSamplePath);
    // create pull secrets when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(dbNamespace);

    logger.info("Start Oracle DB with dbImage: {0}, dbPort: {1}, dbNamespace: {2}",
        dbImage, dbPort, dbNamespace);
    startOracleDB(dbImage, dbPort, dbNamespace);
  }

  /**
   * Start Oracle DB pod and service in the specified namespace.
   *
   * @param dbBaseImageName full image name for DB deployment
   * @param dbPort NodePort of DB
   * @param dbNamespace namespace where DB instance is going to start
   */
  private static synchronized void startOracleDB(String dbBaseImageName, int dbPort, String dbNamespace) {

    Path dbSamplePathBase = get(dbSamplePath.toString(), "/scripts/create-oracle-db-service/");
    String script = get(dbSamplePathBase.toString(),  "start-db-service.sh").toString();
    logger.info("Script for startOracleDB: {0}", script);

    String command = script
        + " -i " + dbBaseImageName
        + " -p " + dbPort
        + " -s " + BASE_IMAGES_REPO_SECRET_NAME
        + " -n " + dbNamespace;
    logger.info("Command for startOracleDb: {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .saveResults(true)
            .redirect(true))
        .execute(), "Failed to execute command: " + command);

    // sleep for a while to make sure the DB pod is created
    try {
      Thread.sleep(10 * 1000);
    } catch (InterruptedException ie) {
        // ignore
    }
  }

  /**
   * Create a RCU schema in the namespace.
   *
   * @param fmwBaseImageName the FMW image name
   * @param rcuPrefix prefix of RCU schema
   * @param dbUrl URL of DB
   * @param dbNamespace namespace of DB where RCU is
   */
  private static void createRcuSchema(String fmwBaseImageName, String rcuPrefix, String dbUrl,
      String dbNamespace) {

    LoggingFacade logger = getLogger();

    Path rcuSamplePathBase = get(dbSamplePath.toString(), "/scripts/create-rcu-schema");
    String script = get(rcuSamplePathBase.toString(), "create-rcu-schema.sh").toString();
    String outputPath = get(rcuSamplePathBase.toString(), "rcuoutput").toString();
    logger.info("Script for createRcuSchema: {0}", script);
    String command = script
        + " -i " + fmwBaseImageName
        + " -p " + BASE_IMAGES_REPO_SECRET_NAME
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

  private void checkAccessToEMconsole(String adminServerPodName) {
    //check access to the em console: http://hostname:port/em
    int nodePort = getServiceNodePort(
           domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, nodePort,
          "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    String curlCmd1 = "curl -s -L --show-error --noproxy '*' "
        + " http://" + K8S_NODEPORT_HOST + ":" + nodePort
        + "/em --write-out %{http_code} -o /dev/null";
    logger.info("Executing default nodeport curl command {0}", curlCmd1);
    assertTrue(callWebAppAndWaitTillReady(curlCmd1, 5), "Calling web app failed");
    logger.info("EM console is accessible thru default service");

  }

}

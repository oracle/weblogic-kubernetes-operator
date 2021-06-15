// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createRcuSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to FMW domain samples.
 */
@DisplayName("Verify the JRF domain on pv sample using wlst and wdt")
@IntegrationTest
public class ItFmwSample {

  private static String dbNamespace = null;
  private static String domainNamespace = null;

  private static final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private static final Path tempSamplePath = Paths.get(WORK_DIR, "fmw-sample-testing");

  private static final String ORACLEDBURLPREFIX = "oracle-db.";
  private static final String ORACLEDBSUFFIX = ".svc.cluster.local:1521/devpdb.k8s";
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static String dbUrl = null;

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await();

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
    createSecretForBaseImages(domainNamespace);

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
  public void testFmwDomainInPv(String model) {

    String domainUid = model.split(":")[1];
    String script = model.split(":")[0];

    setupSample();

    // create persistent volume and persistent volume claims used by the samples
    createPvPvc(domainUid);

    //create WebLogic secrets for the domain
    createSecretWithUsernamePassword(domainUid + "-weblogic-credentials", domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    // create RCU credential secret
    createRcuSecretWithUsernamePassword(domainUid + "-rcu-credentials", domainNamespace,
        RCUSCHEMAUSERNAME, RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD);

    Path sampleBase = Paths.get(tempSamplePath.toString(),
        "scripts/create-fmw-infrastructure-domain/domain-home-on-pv");

    //update create-domain-inputs.yaml with the values from this test
    updateDomainInputsFile(domainUid, sampleBase);

    // change image name
    assertDoesNotThrow(() -> {
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "createDomainFilesDir: wlst", "createDomainFilesDir: " + script);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
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
            + Paths.get(sampleBase.toString(), "create-domain.sh").toString()
            + " -i " + Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString()
            + " -o "
            + Paths.get(sampleBase.toString()));

    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain.yaml");

    // run kubectl to create the domain
    logger.info("Run kubectl to create the domain");
    params = new CommandParams().defaults();
    params.command("kubectl apply -f "
            + Paths.get(sampleBase.toString(), "weblogic-domains/" + domainName + "/domain.yaml").toString());

    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                                    + "(elapsed time {2}ms, remaining time {3}ms)",
                            domainName,
                            domainNamespace,
                            condition.getElapsedTimeInMS(),
                            condition.getRemainingTimeInMS()))
            .until(domainExists(domainName, DOMAIN_VERSION, domainNamespace));

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

  private void createPvPvc(String domainUid) {

    String pvName = domainUid + "-weblogic-sample-pv";
    String pvcName = domainUid + "-weblogic-sample-pvc";

    Path pvpvcBase = Paths.get(tempSamplePath.toString(),
        "scripts/create-weblogic-domain-pv-pvc");

    // create pv and pvc
    assertDoesNotThrow(() -> {
      // when tests are running in local box the PV directories need to exist
      Path pvHostPathBase;
      pvHostPathBase = Files.createDirectories(Paths.get(PV_ROOT, this.getClass().getSimpleName()));
      Path pvHostPath;
      pvHostPath = Files.createDirectories(Paths.get(PV_ROOT, this.getClass().getSimpleName(), pvName));

      logger.info("Creating PV directory host path {0}", pvHostPath);
      deleteDirectory(pvHostPath.toFile());
      Files.createDirectories(pvHostPath);
      String command1  = "chmod -R 777 " + pvHostPathBase;
      logger.info("Command1 to be executed: " + command1);
      assertTrue(new Command()
          .withParams(new CommandParams()
            .command(command1))
          .execute(), "Failed to chmod " + PV_ROOT);

      // set the pvHostPath in create-pv-pvc-inputs.yaml
      replaceStringInFile(Paths.get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
          "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + pvHostPath);
      // set the namespace in create-pv-pvc-inputs.yaml
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

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pv {0} to be ready, "
                + "(elapsed time {1}ms, remaining time {2}ms)",
                pvName,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> pvExists(pvName, null),
            String.format("pvExists failed with ApiException for pv %s",
                pvName)));

    params = new CommandParams().defaults();
    params.command("kubectl create -f " + Paths.get(pvpvcBase.toString(),
        "pv-pvcs/" + domainUid + "-weblogic-sample-pvc.yaml").toString());
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create pvc");

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pv {0} to be ready in namespace {1} "
                + "(elapsed time {2}ms, remaining time {3}ms)",
                pvcName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> pvcExists(pvcName, domainNamespace),
            String.format("pvcExists failed with ApiException for pvc %s",
                pvcName)));

  }

  // copy samples directory to a temporary location
  private static void setupSample() {
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

  private void updateDomainInputsFile(String domainUid, Path sampleBase) {
    // in general the node port range has to be between 30,100 to 32,767
    // to avoid port conflict because of the delay in using it, the port here
    // starts with 30172
    final int t3ChannelPort = getNextFreePort();
    final int adminNodePort = getNextFreePort();

    // change namespace from default to custom, domain name, and t3PublicAddress
    assertDoesNotThrow(() -> {
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "namespace: default", "namespace: " + domainNamespace);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "domain1", domainUid);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#t3PublicAddress:", "t3PublicAddress: " + K8S_NODEPORT_HOST);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "t3ChannelPort: 30012", "t3ChannelPort: " + t3ChannelPort);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "exposeAdminT3Channel: false", "exposeAdminT3Channel: true");
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "exposeAdminNodePort: false", "exposeAdminNodePort: true");
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "adminNodePort: 30701", "adminNodePort: " + adminNodePort);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#imagePullSecretName:", "imagePullSecretName: " + BASE_IMAGES_REPO_SECRET);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "rcuDatabaseURL: database:1521/service", "rcuDatabaseURL: " + dbUrl);
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

    setupSample();
    // create pull secrets when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(dbNamespace);

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

  private void checkAccessToEMconsole(String adminServerPodName) {
    //check access to the em console: http://hostname:port/em
    int nodePort = getServiceNodePort(
           domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertTrue(nodePort != -1,
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

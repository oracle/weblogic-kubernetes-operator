// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
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

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.ORACLE_DB_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ORACLE_RCU_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.tagAndPushToKind;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DbUtils.createDbSecretWithPassword;
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
 * Tests related to FMW domain-in-image samples.
 */
@DisplayName("Verify the JRF domain-in-image sample using wlst and wdt")
@IntegrationTest
@Tag("samples")
@Tag("kind-parallel")
public class ItFmwDiiSample {

  private static String dbNamespace = null;
  private static String domainNamespace = null;

  private static final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private static final Path tempSamplePath = Paths.get(WORK_DIR, "fmw-diisample-testing");

  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static String dbUrl = null;

  private static LoggingFacade logger = null;

  private static final String[] params = { "wdt:fmwdiiwdt", "wlst:fmwdiiwlst"};

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
    //dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

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
    assertDoesNotThrow(() -> setupDBBySample(DB_IMAGE_TO_USE_IN_SPEC, dbNamespace, dbPort),
        String.format("Failed to create DB in the namespace %s with dbPort %d ",
            dbNamespace, dbPort));

    dbUrl = K8S_NODEPORT_HOST + ":" + dbPort + "/devpdb.k8s";

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
   * Test JRF domain-in-image samples using domains created by wlst and wdt.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   *
   * @param model domain name and script type to create domain. Acceptable values of format String:wlst|wdt
   */
  @ParameterizedTest
  @MethodSource("paramProvider")
  @DisplayName("Test FMW domain in image sample")
  void testFmwDomainInImageSample(String model) {
    String domainUid = model.split(":")[1];
    String script = model.split(":")[0]; // wlst | wdt way of creating domain

    // copy sample a temporary directory
    setupSample();

    // create WebLogic secrets for the domain
    createSecretWithUsernamePassword(domainUid + "-weblogic-credentials", domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create RCU credential secret for use by domain
    createRcuSecretWithUsernamePassword(domainUid + "-rcu-credentials", domainNamespace,
        RCUSCHEMAUSERNAME, RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD);

    Path sampleBase = Paths.get(tempSamplePath.toString(),
        "scripts/create-fmw-infrastructure-domain/domain-home-in-image");

    // update create-domain-inputs.yaml with the values from this test
    updateDomainInputsFile(domainUid, sampleBase, script);

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
            + " -u " + ADMIN_USERNAME_DEFAULT
            + " -p " + ADMIN_PASSWORD_DEFAULT
            + " -q " + RCUSYSPASSWORD
            + " -b host"
            + " -o "
            + Paths.get(sampleBase.toString()));

    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain.yaml");

    //If the tests are running in kind cluster, push the image to kind registry
    if (KIND_REPO != null) {
      String taggedImage = FMWINFRA_IMAGE_TO_USE_IN_SPEC.replaceAll("localhost", "domain-home-in-image");
      String newImage = KIND_REPO + "domain-in-image:" + domainName;
      testUntil(
          tagAndPushToKind(taggedImage, newImage),
          logger,
          "tagAndPushToKind for image {0} to be successful",
          newImage);
      assertDoesNotThrow(() -> replaceStringInFile(
          Paths.get(sampleBase.toString(), "weblogic-domains/" + domainName + "/domain.yaml").toString(),
          taggedImage, newImage));
      assertDoesNotThrow(() -> logger.info(Files.readString(
          Paths.get(sampleBase.toString(), "weblogic-domains/" + domainName + "/domain.yaml"))));
    }

    // run kubectl to create the domain
    logger.info("Run kubectl to create the domain");
    params = new CommandParams().defaults();
    params.command("kubectl apply -f "
            + Paths.get(sampleBase.toString(), "weblogic-domains/" + domainName + "/domain.yaml").toString());

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
    assertTrue(Command
        .withParams(new CommandParams()
            .command(command))
        .execute(), "Failed to chmod tempSamplePath");
  }

  private void updateDomainInputsFile(String domainUid, Path sampleBase, String script) {
    final int t3ChannelPort = getNextFreePort();
    final int adminNodePort = getNextFreePort();

    // change namespace from default to custom, domain name, and t3PublicAddress
    assertDoesNotThrow(() -> {
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "mode: wdt", "mode: " + script);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "domainHomeImageBase: container-registry.oracle.com/middleware/fmw-infrastructure:12.2.1.4",
          "domainHomeImageBase: " + FMWINFRA_IMAGE_TO_USE_IN_SPEC);
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
          "#imagePullSecretName:", "imagePullSecretName: " + BASE_IMAGES_REPO_SECRET_NAME);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "rcuDatabaseURL: database:1521/service", "rcuDatabaseURL: " + dbUrl);
      replaceStringInFile(Paths.get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
          "initialManagedServerReplicas: 1", "initialManagedServerReplicas: 2");
    });
  }

  /**
   * Start Oracle DB instance, create rcu pod and load database schema in the specified namespace.
   *
   * @param dbImage image name of database
   * @param dbNamespace namespace where DB and RCU schema are going to start
   * @param dbPort NodePort of DB
   * @throws ApiException if any error occurs when setting up RCU database
   */

  private static void setupDBBySample(String dbImage, String dbNamespace, int dbPort) throws ApiException {

    setupSample();
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

    Path dbSamplePathBase = Paths.get(tempSamplePath.toString(), "/scripts/create-oracle-db-service/");
    String script = Paths.get(dbSamplePathBase.toString(),  "start-db-service.sh").toString();
    logger.info("Script for startOracleDB: {0}", script);

    deleteSecret(ORACLE_DB_SECRET_NAME, dbNamespace); // does nothing if missing
    createDbSecretWithPassword(ORACLE_DB_SECRET_NAME, dbNamespace, RCUSYSPASSWORD); // throws an assertion if fails

    String command = script
        + " -a " + ORACLE_DB_SECRET_NAME
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
    assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(10));
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

    // create RCU credential secret for use by rcu initialization
    // all schemas share the same schema password
    deleteSecret(ORACLE_RCU_SECRET_NAME, dbNamespace); // does nothing if missing
    createRcuSecretWithUsernamePassword(ORACLE_RCU_SECRET_NAME, dbNamespace,
        "", RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD); // throws if fails

    Path rcuSamplePathBase = Paths.get(tempSamplePath.toString(), "/scripts/create-rcu-schema");
    String script = Paths.get(rcuSamplePathBase.toString(), "create-rcu-schema.sh").toString();
    String outputPath = Paths.get(rcuSamplePathBase.toString(), "rcuoutput").toString();
    String imagePullPolicy = "IfNotPresent";
    logger.info("Script for createRcuSchema: {0}", script);
    String command = script
        + " -c " + ORACLE_RCU_SECRET_NAME
        + " -i " + fmwBaseImageName
        + " -p " + BASE_IMAGES_REPO_SECRET_NAME
        + " -s " + rcuPrefix
        + " -d " + dbUrl
        + " -n " + dbNamespace
        + " -o " + outputPath
        + " -u " + imagePullPolicy;
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

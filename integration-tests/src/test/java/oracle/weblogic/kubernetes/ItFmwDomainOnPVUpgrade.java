// Copyright (c) 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.CreateIfNotExists;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainOnPV;
import oracle.weblogic.domain.DomainOnPVType;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Opss;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_TENANCY;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePull;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.actions.impl.Domain.shutdown;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.FmwUtils.getConfiguration;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to create a FMW domain in persistent volume and upgrade it to 14.1.2.0.
 */
@DisplayName("Test to create a FMW domain in persistent volume and upgrade to 14.1.2.0")
@IntegrationTest
@Tag("kind-sequential")
@Tag("oke-sequential")
@Tag("okd-fmw-cert")
class ItFmwDomainOnPVUpgrade {

  private static String domainNamespace = null;
  private static String dbNamespace = null;
  
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String storageClassName = "fmw-domain-storage-class";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;
  private static String DOMAINHOMEPREFIX = null;
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;

  private final String fmwModelFilePrefix = "model-fmwdomain-upgrade";  
  
  private static final String imageTag12214 = "12.2.1.4";
  private static final String image12214 = FMWINFRA_IMAGE_NAME + ":" + imageTag12214;
  private static final String imageTag1412 = "14.1.2.0-jdk17-ol8";
  private static final String image1412 = FMWINFRA_IMAGE_NAME + ":" + imageTag1412;
  
  private static ConditionFactory withVeryLongRetryPolicy
      = with().pollDelay(0, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(30, MINUTES).await();

  /**
   * Assigns unique namespaces for DB, operator and domain.
   * Start DB service.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique dbNamespace
    logger.info("Assign a unique namespace for DB");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    final int dbListenerPort = getNextFreePort();
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";

    // get a new unique opNamespace
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    String opNamespace = namespaces.get(1);

    // get a new unique domainNamespace
    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace = namespaces.get(2);

    DOMAINHOMEPREFIX = "/shared/" + domainNamespace + "/domains/";

    if (OKD) {
      logger.info("Start DB in namespace: {0}, dbListenerPort: {1}, dbUrl: {2}, dbImage: {3}",
          dbNamespace, dbListenerPort, dbUrl, DB_IMAGE_TO_USE_IN_SPEC);
      assertDoesNotThrow(() -> startOracleDB(DB_IMAGE_TO_USE_IN_SPEC, getNextFreePort(), dbNamespace, dbListenerPort),
          String.format("Failed to start Oracle DB in the namespace %s with dbUrl %s, dbListenerPost %s",
              dbNamespace, dbUrl, dbListenerPort));
    } else {
      String dbName = "fmwupgradedomain-" + "oracle-db";
      logger.info("Create Oracle DB in namespace: {0} ", dbNamespace);
      createBaseRepoSecret(dbNamespace);
      dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, RCUSYSPASSWORD, dbNamespace));

    }
    // install operator and verify its running in ready state
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, ELASTICSEARCH_HOST, false, true, null,
        null, false, "INFO", "DomainOnPvSimplification=true", false, domainNamespace);

    // create pull secrets for domainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    if (KIND_REPO != null) {
      Collection<String> images = new ArrayList<>();
      images.add(image12214);
      images.add(image1412);

      for (String image : images) {
        testUntil(
            withVeryLongRetryPolicy,
            pullImageFromBaseRepoAndPushToKind(image),
            logger,
            "pullImageFromBaseRepoAndPushToKind for image {0} to be successful",
            image);
      }
    }
  }
  
  /**
   * Create a basic FMW domain on PV with server start mode prod
   * Verify Pod is ready and service exists for both admin server and managed servers. 
   * Run the upgrade assistant to upgade the JRF domain 
   * verify the domain starts and is upgraded to 14.1.2.0.0
   */
  @Test
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @DisplayName("Create a FMW domain and upgrade to 14.1.2.0 in prod server start mode")
  void testUpgradeProductionDomain() {
    String domainUid = "jrfonpv-prod";
    String domainHome = DOMAINHOMEPREFIX + domainUid;
    String startMode = "prod";
    String pvcName = getUniqueName(domainUid + "-pvc-");
    String rcuSchemaPrefix = "jrfprod1";
    String fmwModelFile = Paths.get(RESOURCE_DIR, "jrfdomainupgrade", "jrf-production-upgrade.yaml").toString();
    createDomain(domainUid, startMode, rcuSchemaPrefix, fmwModelFile, pvcName);
    shutdown(domainUid, domainNamespace);
    launchPvHelperPod(domainNamespace, pvcName);
    copyResponseFile(domainNamespace, dbUrl, rcuSchemaPrefix, domainHome);
    runUpgradeAssistant(domainNamespace);
    runUpgradeDomain(domainNamespace, domainHome);
    deletePvhelperPod(domainNamespace);
    patchDomain(domainUid, domainNamespace);
    verifyDomainReady(domainUid, domainNamespace);
    shutdown(domainUid, domainNamespace);
  }
  
  /**
   * Create a basic FMW domain on PV with server start mode dev
   * Verify Pod is ready and service exists for both admin server and managed servers. 
   * Run the upgrade assistant to upgade the JRF domain 
   * verify the domain starts and is upgraded to 14.1.2.0.0
   */
  @Test
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @DisplayName("Create a FMW domain and upgrade to 14.1.2.0 in dev server start mode")
  void testUpgradeDevDomain() {
    String domainUid = "jrfonpv-dev";
    String domainHome = DOMAINHOMEPREFIX + domainUid;
    String startMode = "dev";
    String pvcName = getUniqueName(domainUid + "-pvc-");
    String rcuSchemaPrefix = "jrfdev1";
    String fmwModelFile = Paths.get(RESOURCE_DIR, "jrfdomainupgrade", "jrf-production-upgrade.yaml").toString();
    createDomain(domainUid, startMode, rcuSchemaPrefix, fmwModelFile, pvcName);
    shutdown(domainUid, domainNamespace);
    launchPvHelperPod(domainNamespace, pvcName);
    copyResponseFile(domainNamespace, dbUrl, rcuSchemaPrefix, domainHome);
    runUpgradeAssistant(domainNamespace);
    runUpgradeDomain(domainNamespace, domainHome);
    deletePvhelperPod(domainNamespace);
    patchDomain(domainUid, domainNamespace);
    verifyDomainReady(domainUid, domainNamespace);
    shutdown(domainUid, domainNamespace);
  }  

  /**
   * Create a basic FMW domain on PV with server start mode secure
   * Verify Pod is ready and service exists for both admin server and managed servers. 
   * Run the upgrade assistant to upgade the JRF domain 
   * verify the domain starts and is upgraded to 14.1.2.0.0
   */
  @Test
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @DisplayName("Create a FMW domain and upgrade to 14.1.2.0 in secure server start mode")
  void testUpgradeSecureDomain() {
    String domainUid = "jrfonpv-secure";
    String domainHome = DOMAINHOMEPREFIX + domainUid;
    String startMode = "secure";
    String pvcName = getUniqueName(domainUid + "-pvc-");
    String rcuSchemaPrefix = "jrfsecure1";
    String fmwModelFile = Paths.get(RESOURCE_DIR, "jrfdomainupgrade", "jrf-secure-upgrade.yaml").toString();
    createDomain(domainUid, startMode, rcuSchemaPrefix, fmwModelFile, pvcName);
    shutdown(domainUid, domainNamespace);
    launchPvHelperPod(domainNamespace, pvcName);
    copyResponseFile(domainNamespace, dbUrl, rcuSchemaPrefix, domainHome);
    runUpgradeAssistant(domainNamespace);
    runUpgradeDomain(domainNamespace, domainHome);
    deletePvhelperPod(domainNamespace);
    patchDomain(domainUid, domainNamespace);
    verifyDomainReady(domainUid, domainNamespace);
    shutdown(domainUid, domainNamespace);
  }  
  
  private void createDomain(String domainName, String startMode, String rcuSchemaprefix, 
      String fmwModelFile, String pvcName) {
    
    final String pvName = getUniqueName(domainName + "-pv-");
    final String wlSecretName = domainName + "-weblogic-credentials";
    // create FMW domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainName, startMode, rcuSchemaprefix);

    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "dci-jrfonpv";
    String dciTag = getDateAndTimeStamp();
    // create image with model and wdt installation files
    WitParams witParams
        = new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(dciTag)
            .modelFiles(Collections.singletonList(fmwModelFile))
            .modelVariableFiles(Collections.singletonList(fmwModelPropFile.getAbsolutePath()));
    createAndPushAuxiliaryImage(domainCreationImageName, dciTag, witParams);

    DomainCreationImage domainCreationImage
        = new DomainCreationImage().image(domainCreationImageName + ":" + dciTag);

    // create opss wallet password secret
    String opsswalletpassSecretName = domainName + "-opss-wallet-password-secret";
    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    // create a domain resource
    logger.info("Creating domain custom resource");
    Map<String, Quantity> pvCapacity = new HashMap<>();
    pvCapacity.put("storage", new Quantity("2Gi"));

    Map<String, Quantity> pvcRequest = new HashMap<>();
    pvcRequest.put("storage", new Quantity("2Gi"));
    Configuration configuration = null;
    if (OKE_CLUSTER) {
      configuration = getConfiguration(pvcName, pvcRequest, "oci-fss");
    } else {
      configuration = getConfiguration(pvName, pvcName, pvCapacity, pvcRequest, storageClassName,
          this.getClass().getSimpleName());
    }
    configuration.getInitializeDomainOnPV().domain(new DomainOnPV()
        .createMode(CreateIfNotExists.DOMAIN_AND_RCU)
        .domainCreationImages(Collections.singletonList(domainCreationImage))
        .domainType(DomainOnPVType.JRF)
        .opss(new Opss()
            .walletPasswordSecret(opsswalletpassSecretName)));
    DomainResource domain = createDomainResourceOnPv(
        domainName,
        domainNamespace,
        wlSecretName,
        clusterName,
        pvName,
        pvcName,
        new String[]{BASE_IMAGES_REPO_SECRET_NAME},
        DOMAINHOMEPREFIX,
        replicaCount,
        configuration,
        image12214);

    // Set the inter-pod anti-affinity for the domain custom resource
    setPodAntiAffinity(domain);

    // create a domain custom resource and verify domain is created
    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready    
    verifyDomainReady(domainName, domainNamespace);
  }

  private File createWdtPropertyFile(String domainName, String startMode, String rcuSchemaPrefix) {

    // create property file used with domain model file
    Properties p = new Properties();
    p.setProperty("rcuDb", dbUrl);
    p.setProperty("rcuSchemaPrefix", rcuSchemaPrefix);
    p.setProperty("rcuSchemaPassword", RCUSCHEMAPASSWORD);
    p.setProperty("rcuSysPassword", RCUSYSPASSWORD);
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainName);
    p.setProperty("startMode", startMode);

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(()
        -> File.createTempFile(fmwModelFilePrefix, ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create FMW model properties file");

    // create the property file
    assertDoesNotThrow(()
        -> p.store(new FileOutputStream(domainPropertiesFile), "FMW properties file"),
        "Failed to write FMW properties file");

    return domainPropertiesFile;
  }

  private Callable<Boolean> tagImageAndPushIfNeeded(String originalImage, String taggedImage) {
    return (() -> {
      boolean result = true;
      imagePull(originalImage);
      result = result && imageTag(originalImage, taggedImage);
      imageRepoLoginAndPushImageToRegistry(taggedImage);
      return result;
    });
  }

  private void launchPvHelperPod(String namespace, String pvcName) {
    String podName = "pvhelper";

    String script = ITTESTS_DIR + "/../kubernetes/samples/scripts/domain-lifecycle/pv-pvc-helper.sh";
    String command = "/bin/bash " + script + " -n " + namespace + " -p " + BASE_IMAGES_REPO_SECRET_NAME
        + " -c " + pvcName + " -m /shared -i " + image1412;
    ExecResult result = null;
    try {
      result = exec(new String(command), true);
      getLogger().info("The command returned exit value: " + result.exitValue()
          + " command output: " + result.stderr() + "\n" + result.stdout());
      assertTrue((result.exitValue() == 0), "command returned non zero value");
    } catch (Exception e) {
      getLogger().info("Got exception, command failed with errors " + e.getMessage());
    }
    checkPodReady(podName, null, namespace);
  }

  private void copyResponseFile(String namespace, String dbUrl, String rcuSchemaPrefix, String domainHome) {
    Path jrfResponseTemplateFile = Paths.get(RESOURCE_DIR, "jrfdomainupgrade", "jrfresponse.txt");

    File jrfResponseFile = assertDoesNotThrow(()
        -> File.createTempFile("jrfresponse", ".txt", new File(RESULTS_TEMPFILE)),
        "Failed to create JRF upgrade assistant response file");
    assertDoesNotThrow(() -> Files.copy(jrfResponseTemplateFile, jrfResponseFile.toPath(), REPLACE_EXISTING),
        "Failed to copy " + jrfResponseTemplateFile.toString());
    // replace domainHome, dbUrl and rcuSchemaPrefix in JRF response text file
    assertDoesNotThrow(() -> replaceStringInFile(
        jrfResponseFile.toString(), "DOMAIN_HOME", domainHome),
        "Could not modify the DOMAIN_HOME in response file");
    assertDoesNotThrow(() -> replaceStringInFile(
        jrfResponseFile.toString(), "DB_CONNECTION_STRING", dbUrl),
        "Could not modify the DB_CONNECTION_STRING in response file");
    assertDoesNotThrow(() -> replaceStringInFile(
        jrfResponseFile.toString(), "RCU_SCHEMA_PREFIX", rcuSchemaPrefix),
        "Could not modify the RCU_SCHEMA_PREFIX in response file");
    assertDoesNotThrow(() -> copyFileToPod(namespace,
        "pvhelper", "",
        jrfResponseFile.toPath(),
        Paths.get("/tmp/jrfresponse.txt")),
        "Copying file to pod failed");
  }

  private void runUpgradeAssistant(String namespace) {
    String command = "/u01/oracle/oracle_common/upgrade/bin/ua -response /tmp/jrfresponse.txt -logDir /tmp";
    String success = "Oracle Metadata Services schema upgrade finished with status: succeeded";
    try {
      ExecResult result = execCommand(namespace, "pvhelper", null, true, "/bin/sh", "-c", command);
      boolean done = result.stderr().contains(success) || result.stdout().contains(success);
      assertTrue(done, "upgrade assistant didn't succeed");
    } catch (ApiException | IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
  }

  private void runUpgradeDomain(String namespace, String domainHome) {
    try {
      List<String> stringsToWrite = Arrays.asList(
          "readDomainForUpgrade(\"" + domainHome + "\")",
          "updateDomain()",
          "closeDomain()");
      File wlstScript = assertDoesNotThrow(()
          -> File.createTempFile("upgradeDomain", ".py", new File(RESULTS_TEMPFILE)),
          "Failed to create JRF upgrade domain python script file");
      Files.write(wlstScript.toPath(), stringsToWrite, StandardOpenOption.CREATE);
      logger.info("Strings written to the file successfully!");
      assertDoesNotThrow(() -> copyFileToPod(namespace,
          "pvhelper", "",
          wlstScript.toPath(),
          Paths.get("/tmp/" + wlstScript.getName())),
          "Copying file to pod failed");
      String command = "/u01/oracle/oracle_common/common/bin/wlst.sh /tmp/" + wlstScript.getName();

      ExecResult result = execCommand(namespace, "pvhelper", null, true, "/bin/sh", "-c", command);
      assertTrue((result.exitValue() == 0), "command returned non zero value");
    } catch (ApiException | IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
  }

  private void deletePvhelperPod(String namespace) {
    assertDoesNotThrow(() -> deletePod("pvhelper", namespace));
    testUntil(
        podDoesNotExist("pvhelper", null, namespace),
        logger,
        "{0} to be deleted in namespace {1}",
        "pvhelper",
        namespace);
  }

  private void patchDomain(String domainUid, String namespace) {
    DomainResource domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, namespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, namespace));
    assertNotNull(domain, "Got null domain resource");
    logger.info("image before update: " + domain.getSpec().getImage());

    String patchStr = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": \"" + image1412 + "\"},"
        + "{\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"IfNeeded\"}"
        + "]";
    logger.info("PatchStr for imageUpdate: {0}", patchStr);

    assertTrue(patchDomainResource(domainUid, namespace, new StringBuffer(patchStr)),
        "patchDomainCustomResource(imageUpdate) failed");

    domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, namespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, namespace));
    assertNotNull(domain, "Got null domain resource after patching");
    logger.info("image after update: " + domain.getSpec().getImage());
  }

  private void verifyDomainReady(String domainUid, String namespace) {
    String adminServerPodName = domainUid + "-admin-server";
    // verify the admin server service and pod created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, namespace);

    String cluster1ManagedServerPodNamePrefix = domainUid + "-managed-server";
    // verify managed server services and pods are created
    for (int i = 1; i <= 2; i++) {
      logger.info("Checking managed server service and pod {0} is created in namespace {1}",
          cluster1ManagedServerPodNamePrefix + i, namespace);
      checkPodReadyAndServiceExists(cluster1ManagedServerPodNamePrefix + i, domainUid, namespace);
    }
  }
  
  private static DomainResource createDomainResourceOnPv(String domainUid,
                                                  String domNamespace,
                                                  String adminSecretName,
                                                  String clusterName,
                                                  String pvName,
                                                  String pvcName,
                                                  String[] repoSecretName,
                                                  String domainInHomePrefix,
                                                  int replicaCount,
                                                  Configuration configuration,
                                                  String imageToUse) {

    // create secrets
    List<V1LocalObjectReference> secrets = new ArrayList<>();
    for (String secret : repoSecretName) {
      secrets.add(new V1LocalObjectReference().name(secret));
    }
    
    // create a domain custom resource configuration object
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(domainInHomePrefix + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(imageToUse)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domNamespace + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom"))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .configuration(configuration));
    domain.spec().setImagePullSecrets(secrets);

    // create cluster resource for the domain
    String clusterResName  = domainUid + "-" + clusterName;
    if (!Cluster.doesClusterExist(clusterResName, CLUSTER_VERSION, domNamespace)) {
      ClusterResource cluster = createClusterResource(clusterResName,
          clusterName, domNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    return domain;
  }
  
  private static Callable<Boolean> pullImageFromBaseRepoAndPushToKind(String image) {
    return (() -> {
      String kindRepoImage = KIND_REPO + image.substring(BASE_IMAGES_REPO.length() + BASE_IMAGES_TENANCY.length() + 2);
      return imagePull(image) && imageTag(image, kindRepoImage) && imagePush(kindRepoImage);
    });
  }
  
}

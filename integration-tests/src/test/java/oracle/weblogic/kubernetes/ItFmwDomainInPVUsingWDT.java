// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.HTTPS_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.HTTP_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.NO_PROXY;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Image.getImageEnvVar;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FmwUtils.createDomainResourceOnPv;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to creat a FMW domain in persistent volume using WDT.
 */
@DisplayName("Test to creat a FMW domain in persistent volume using WDT")
@IntegrationTest
@Tag("oke-sequential")
@Tag("kind-sequential")
@Tag("okd-fmw-cert")
class ItFmwDomainInPVUsingWDT {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String dbNamespace = null;
  private static String oracle_home = null;
  private static String java_home = null;

  private static final String RCUSCHEMAPREFIX = "fmwdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;
  private static String DOMAINHOMEPREFIX = null;
  private static final String domainUid = "fmw-domainonpv-wdt";
  private static final String clusterName = "cluster-1";
  private static final String adminServerName = "admin-server";
  private static final String managedServerNameBase = "managed-server";
  private static final String adminServerPodName = domainUid + "-" + adminServerName;
  private static final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  private static final int managedServerPort = 8001;
  private final String wlSecretName = domainUid + "-weblogic-credentials";
  private final String rcuSecretName = domainUid + "-rcu-credentials";
  private static final int replicaCount = 1;

  private final String wdtCreateDomainScript = "setup_wdt.sh";
  private final String fmwModelFilePrefix = "model-fmwdomain-onpv-wdt";
  private final String fmwModelFile = fmwModelFilePrefix + ".yaml";

  /**
   * Assigns unique namespaces for DB, operator and domain.
   * Start DB service and create RCU schema.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique dbNamespace
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    final int dbListenerPort = getNextFreePort();
    ORACLEDBSUFFIX = ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    // get a new unique opNamespace
    logger.info("Assign a unique namespace for operator1");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    // get a new unique domainNamespace
    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace = namespaces.get(2);

    DOMAINHOMEPREFIX = "/shared/" + domainNamespace + "/domains/";
    // start DB and create RCU schema
    logger.info("Start DB and create RCU schema for namespace: {0}, dbListenerPort: {1}, RCU prefix: {2}, "
         + "dbUrl: {3}, dbImage: {4},  fmwImage: {5} ", dbNamespace, dbListenerPort, RCUSCHEMAPREFIX, dbUrl,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        RCUSCHEMAPREFIX, dbNamespace, getNextFreePort(), dbUrl, dbListenerPort),
        String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
        + "dbUrl %s, dbListenerPost %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl, dbListenerPort));

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create pull secrets for domainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
  }

  /**
   * Create a basic FMW domain on PV using WDT.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   */
  @Test
  @DisplayName("Create a FMW domainon on PV using WDT")
  void testFmwDomainOnPVUsingWdt() {
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();

    // create FMW domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create RCU credential secret
    createRcuSecretWithUsernamePassword(rcuSecretName, domainNamespace,
        RCUSCHEMAUSERNAME, RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD);

    // create persistent volume and persistent volume claim for domain
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(t3ChannelPort);

    // use shell script to download WDT and run the WDT createDomain script to create FMW domain
    Path wdtScript = Paths.get(RESOURCE_DIR, "bash-scripts", wdtCreateDomainScript);
    // WDT model file containing WebLogic domain configuration
    Path wdtModelFile = Paths.get(MODEL_DIR, fmwModelFile);

    // create configmap and domain in persistent volume
    createDomainJobOnPv(wdtScript, wdtModelFile, fmwModelPropFile.toPath(), pvName, pvcName);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    DomainResource domain = createDomainResourceOnPv(domainUid,
                                             domainNamespace,
                                             wlSecretName,
                                             clusterName,
                                             pvName,
                                             pvcName,
                                             DOMAINHOMEPREFIX,
                                             replicaCount,
                                             t3ChannelPort);

    // Set the inter-pod anti-affinity for the domain custom resource
    setPodAntiAffinity(domain);

    // create a domain custom resource and verify domain is created
    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready and EM console is accessible
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");
  }

  private void createDomainJobOnPv(Path domainCreationScriptFile,
                                   Path modelFile,
                                   Path domainPropertiesFile,
                                   String pvName,
                                   String pvcName) {

    logger.info("Preparing to run create domain job using WDT");
    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(domainCreationScriptFile);
    domainScriptFiles.add(domainPropertiesFile);
    domainScriptFiles.add(modelFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles,
            domainNamespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/weblogic/" + domainCreationScriptFile.getFileName())
        .addEnvItem(new V1EnvVar()
            .name("WDT_VERSION")
            .value(WDT_VERSION))
        .addEnvItem(new V1EnvVar()
            .name("WDT_MODEL_FILE")
            .value("/u01/weblogic/" + modelFile.getFileName()))
        .addEnvItem(new V1EnvVar()
            .name("WDT_VAR_FILE")
            .value("/u01/weblogic/" + domainPropertiesFile.getFileName()))
        .addEnvItem(new V1EnvVar()
            .name("WDT_DIR")
            .value("/u01/shared/wdt"))
        .addEnvItem(new V1EnvVar()
            .name("DOMAIN_HOME_DIR")
            .value(DOMAINHOMEPREFIX + domainUid))
        .addEnvItem(new V1EnvVar()
            .name("DOMAIN_TYPE")
            .value("JRF"));

    if (HTTP_PROXY != null) {
      jobCreationContainer.addEnvItem(new V1EnvVar().name("http_proxy").value(HTTP_PROXY));
    }
    if (HTTPS_PROXY != null) {
      jobCreationContainer.addEnvItem(new V1EnvVar().name("https_proxy").value(HTTPS_PROXY));
    }
    if (NO_PROXY != null) {
      jobCreationContainer.addEnvItem(new V1EnvVar().name("no_proxy").value(NO_PROXY));
    }

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(FMWINFRA_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        domainNamespace, jobCreationContainer);
  }

  private File createWdtPropertyFile(int t3ChannelPort) {
    //get ENV variable from the image
    assertNotNull(getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "ORACLE_HOME"),
        "envVar ORACLE_HOME from image is null");
    oracle_home = getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "ORACLE_HOME");
    logger.info("ORACLE_HOME in image {0} is: {1}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, oracle_home);
    assertNotNull(getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "JAVA_HOME"),
        "envVar JAVA_HOME from image is null");
    java_home = getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "JAVA_HOME");
    logger.info("JAVA_HOME in image {0} is: {1}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, java_home);

    // create wlst property file object
    Properties p = new Properties();
    p.setProperty("oracleHome", oracle_home); //default $ORACLE_HOME
    p.setProperty("javaHome", java_home); //default $JAVA_HOME
    p.setProperty("rcuDb", dbUrl);
    p.setProperty("rcuSchemaPrefix", RCUSCHEMAPREFIX);
    p.setProperty("rcuSchemaPassword", RCUSCHEMAPASSWORD);
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainUid);
    p.setProperty("adminServerName", adminServerName);
    p.setProperty("productionModeEnabled", "true");
    p.setProperty("clusterName", clusterName);
    p.setProperty("managedServerNameBase", managedServerNameBase);
    p.setProperty("t3ChannelPort", Integer.toString(t3ChannelPort));
    p.setProperty("t3PublicAddress", K8S_NODEPORT_HOST);
    p.setProperty("managedServerPort", Integer.toString(managedServerPort));

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
        File.createTempFile(fmwModelFilePrefix, "properties"),
        "Failed to create FMW model properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "FMW properties file"),
        "Failed to write FMW properties file");

    return domainPropertiesFile;
  }
}


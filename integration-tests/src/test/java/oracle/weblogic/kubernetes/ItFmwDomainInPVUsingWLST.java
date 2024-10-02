// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Image.getImageEnvVar;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests to create JRF domain in persistent volume using WLST.
 */
@DisplayName("Verify the WebLogic server pods can run with domain created in persistent volume")
@Tag("kind-sequential")
@Tag("okd-fmw-cert")
@IntegrationTest
@Tag("olcne-sequential")
@Tag("oke-weekly-sequential")
class ItFmwDomainInPVUsingWLST {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String jrfDomainNamespace = null;
  private static String oracle_home = null;
  private static String java_home = null;

  private static final String RCUSCHEMAPREFIX = "jrfdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private static final String domainUid = "jrfdomain-inpv-wlst";
  private static String dbName = domainUid + "my-oracle-db";
  private final String wlSecretName = domainUid + "-weblogic-credentials";
  private final String rcuSecretName = domainUid + "-rcu-credentials";

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
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

    final int dbListenerPort = getNextFreePort();
    ORACLEDBSUFFIX = ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;


    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for JRF domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    jrfDomainNamespace = namespaces.get(2);

    if (OKD) {
      logger.info("Start DB and create RCU schema for namespace: {0}, dbListenerPort: {1}, RCU prefix: {2}, "
          + "dbUrl: {3}, dbImage: {4},  fmwImage: {5} ", dbNamespace, dbListenerPort, RCUSCHEMAPREFIX, dbUrl,
          DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
      assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
          RCUSCHEMAPREFIX, dbNamespace, getNextFreePort(), dbUrl, dbListenerPort),
          String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
          + "dbUrl %s, dbListenerPost %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl, dbListenerPort));

    } else {
      logger.info("Create Oracle DB in namespace: {0} ", dbNamespace);
      createBaseRepoSecret(dbNamespace);
      dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, RCUSYSPASSWORD, dbNamespace));

      logger.info("Create RCU schema with fmwImage: {0}, rcuSchemaPrefix: {1}, dbUrl: {2}, "
          + " dbNamespace: {3}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX, dbUrl, dbNamespace);
      assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX,
          dbUrl, dbNamespace));
    }
    
    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, jrfDomainNamespace);

    logger.info("For ItFmwDomainInPV using DB image: {0}, FMW image {1}",
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);

  }

  /**
   * Create a JRF domain using WLST in a persistent volume.
   * Create a domain custom resource with domainHomeSourceType as PersistentVolume.
   * Verify domain pods runs in ready state and services are created.
   */
  @Test
  @DisplayName("Create JRF domain in PV using WLST script")
  void testFmwDomainInPvUsingWlst() {
    final String clusterName = "cluster-jrfdomain-inpv";
    final String adminServerName = "wlst-admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String managedServerNameBase = "wlst-ms-";
    final int managedServerPort = 8001;
    final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    final int replicaCount = 1;
    final int t3ChannelPort = getNextFreePort();

    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");

    // create pull secrets for jrfDomainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(jrfDomainNamespace);


    // create JRF domain credential secret
    createSecretWithUsernamePassword(wlSecretName, jrfDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create RCU credential secret
    createRcuSecretWithUsernamePassword(rcuSecretName, jrfDomainNamespace,
        RCUSCHEMAUSERNAME, RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD);

    // create persistent volume and persistent volume claim for domain
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, jrfDomainNamespace);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create domain properties file");

    //get ENV variable from the image
    assertNotNull(getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "ORACLE_HOME"),
        "envVar ORACLE_HOME from image is null");
    oracle_home = getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "ORACLE_HOME");
    logger.info("ORACLE_HOME in image {0} is: {1}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, oracle_home);
    assertNotNull(getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "JAVA_HOME"),
        "envVar JAVA_HOME from image is null");
    java_home = getImageEnvVar(FMWINFRA_IMAGE_TO_USE_IN_SPEC, "JAVA_HOME");
    logger.info("JAVA_HOME in image {0} is: {1}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, java_home);
    
    String uniquePath = "/shared/" + jrfDomainNamespace + "/domains/";

    Properties p = new Properties();
    p.setProperty("oracleHome", oracle_home); //default $ORACLE_HOME
    p.setProperty("javaHome", java_home); //default $JAVA_HOME
    p.setProperty("domainParentDir", uniquePath);
    p.setProperty("domainName", domainUid);
    p.setProperty("domainUser", ADMIN_USERNAME_DEFAULT);
    p.setProperty("domainPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("rcuDb", dbUrl);
    p.setProperty("rcuSchemaPrefix", RCUSCHEMAPREFIX);
    p.setProperty("rcuSchemaPassword", RCUSCHEMAPASSWORD);
    p.setProperty("adminListenPort", "7001");
    p.setProperty("adminName", adminServerName);
    p.setProperty("managedNameBase", managedServerNameBase);
    p.setProperty("managedServerPort",Integer.toString(managedServerPort));
    p.setProperty("prodMode", "true");
    p.setProperty("managedCount", "4");
    p.setProperty("clusterName", clusterName);
    p.setProperty("t3ChannelPublicAddress", K8S_NODEPORT_HOST);
    p.setProperty("t3ChannelPort", Integer.toString(t3ChannelPort));
    p.setProperty("exposeAdminT3Channel", "true");

    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "jrf wlst properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "jrf-wlst-create-domain-onpv.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, jrfDomainNamespace);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(jrfDomainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(uniquePath + domainUid) // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(FMWINFRA_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME)))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome(uniquePath + "logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort)))));

    setPodAntiAffinity(domain);

    // verify the domain custom resource is created
    createDomainAndVerify(domain, jrfDomainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, jrfDomainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, jrfDomainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, jrfDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, jrfDomainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, jrfDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, jrfDomainNamespace);
    }

  }

  /**
   * Create a WebLogic domain on a persistent volume by doing the following.
   * Create a configmap containing WLST script and property file.
   * Create a Kubernetes job to create domain on persistent volume.
   *
   * @param wlstScriptFile python script file to create domain
   * @param domainPropertiesFile properties file containing domain configuration
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   */
  private void createDomainOnPVUsingWlst(Path wlstScriptFile, Path domainPropertiesFile,
      String pvName, String pvcName, String namespace) {

    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScriptFile);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles,
            namespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName()); //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(FMWINFRA_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        jrfDomainNamespace, jobCreationContainer);

  }

}


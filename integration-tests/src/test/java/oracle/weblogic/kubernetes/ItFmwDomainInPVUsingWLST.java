// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonTestUtils;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Docker.getImageEnvVar;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.DbUtils.setupDBandRCUschema;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests to create JRF domain in persistent volume using WLST.
 */
@DisplayName("Verify the WebLogic server pods can run with domain created in persistent volume")
@IntegrationTest
public class ItFmwDomainInPVUsingWLST {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String jrfDomainNamespace = null;
  private static String oracle_home = null;
  private static String java_home = null;

  private static final String RCUSCHEMAPREFIX = "jrfdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static final String ORACLEDBSUFFIX = ".svc.cluster.local:1521/devpdb.k8s";
  private static final String RCUSYSUSERNAME = "sys";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAUSERNAME = "myrcuuser";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;

  private final String domainUid = "jrfdomain-inpv";
  private final String wlSecretName = domainUid + "-weblogic-credentials";
  private final String rcuSecretName = domainUid + "-rcu-credentials";

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();

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
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ORACLEDBSUFFIX;

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    opNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for JRF domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    jrfDomainNamespace = namespaces.get(2);

    logger.info("Start DB and create RCU schema for namespace: {0}, RCU prefix: {1}, "
        + "dbUrl: {2}, dbImage: {3},  fmwImage: {4} ", dbNamespace, RCUSCHEMAPREFIX, dbUrl,
        DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDBandRCUschema(DB_IMAGE_TO_USE_IN_SPEC, FMWINFRA_IMAGE_TO_USE_IN_SPEC,
        RCUSCHEMAPREFIX, dbNamespace, 0, dbUrl),
        String.format("Failed to create RCU schema for prefix %s in the namespace %s with "
        + "dbUrl %s", RCUSCHEMAPREFIX, dbNamespace, dbUrl));

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
  public void testFmwDomainInPvUsingWlst() {
    final String clusterName = "cluster-jrfdomain-inpv";
    final String adminServerName = "wlst-admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String managedServerNameBase = "wlst-ms-";
    final int managedServerPort = 8001;
    final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    final int replicaCount = 2;
    final int t3ChannelPort = getNextFreePort();

    final String pvName = domainUid + "-pv";
    final String pvcName = domainUid + "-pvc";

    // create pull secrets for jrfDomainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(jrfDomainNamespace);


    // create JRF domain credential secret
    createSecretWithUsernamePassword(wlSecretName, jrfDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create RCU credential secret
    CommonTestUtils.createRcuSecretWithUsernamePassword(rcuSecretName, jrfDomainNamespace,
        RCUSCHEMAUSERNAME, RCUSCHEMAPASSWORD, RCUSYSUSERNAME, RCUSYSPASSWORD);

    // create persistent volume and persistent volume claim for domain
    CommonTestUtils.createPV(pvName, domainUid, this.getClass().getSimpleName());
    CommonTestUtils.createPVC(pvName, pvcName, domainUid, jrfDomainNamespace);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", "properties"),
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

    Properties p = new Properties();
    p.setProperty("oracleHome", oracle_home); //default $ORACLE_HOME
    p.setProperty("javaHome", java_home); //default $JAVA_HOME
    p.setProperty("domainParentDir", "/shared/domains/");
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
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(jrfDomainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)  // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(FMWINFRA_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET)))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(jrfDomainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
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
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")
                ));

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
        () -> CommonTestUtils.createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles,
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
    CommonTestUtils.createDomainJob(FMWINFRA_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        jrfDomainNamespace, jobCreationContainer);

  }

}


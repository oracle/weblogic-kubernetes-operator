// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.V1Patch;
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
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Situational Configuration overrides for system resources.
 */
@DisplayName("Verify the JMS and WLDF system resources are overridden with values from override files")
@IntegrationTest
public class ItSystemResOverrides {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  final String domainUid = "mysitconfigdomain";
  final String clusterName = "mycluster";
  final String adminServerName = "admin-server";
  final String adminServerPodName = domainUid + "-" + adminServerName;
  final String managedServerNameBase = "ms-";
  final int managedServerPort = 8001;
  int t3ChannelPort;
  final String pvName = domainUid + "-pv"; // name of the persistent volume
  final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim
  final String wlSecretName = "weblogic-credentials";
  final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  int replicaCount = 2;

  static Path sitconfigAppPath;
  String overridecm = "configoverride-cm";
  LinkedHashMap<String, OffsetDateTime> podTimestamps;

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();
  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pulls WebLogic image if running tests in Kind cluster.
   * Installs operator.
   * Creates and starts a WebLogic domain with a dynamic cluster containing 2 managed server instances.
   * Creates JMS and WLDF system resources.
   * Deploys sitconfig application to cluster and admin targets.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for domain namspace");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    //create and start WebLogic domain
    createDomain();

    // build the sitconfig application
    Path distDir = buildApplication(Paths.get(APP_DIR, "sitconfig"),
        null, null, "dist", domainNamespace);
    sitconfigAppPath = Paths.get(distDir.toString(), "sitconfig.war");
    assertTrue(sitconfigAppPath.toFile().exists(), "Application archive is not available");

    //deploy application to view server configuration
    deployApplication(clusterName + "," + adminServerName);

  }

  /**
   * Test JMS and WLDF system resources configurations are overridden dynamically when domain resource
   * is updated with overridesConfigMap property. After the override verifies the system resources properties
   * are overridden as per the values set.
   * 1. Creates a config override map containing JMS and WLDF override files
   * 2. Patches the domain with new configmap override and introspect version
   * 3. Verifies the introspector is run and resource config is updated
   */
  @Test
  @DisplayName("Test JMS and WLDF system resources override")
  public void testJmsWldfSystemResourceOverride() {

    //store the pod creation timestamps
    storePodCreationTimestamps();

    List<Path> overrideFiles = new ArrayList<>();
    overrideFiles.add(
        Paths.get(RESOURCE_DIR, "configfiles/configoverridesset2/jms-ClusterJmsSystemResource.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset2/diagnostics-WLDF-MODULE-0.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset2/version.txt"));

    //create config override map
    createConfigMapFromFiles(overridecm, overrideFiles, domainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

    logger.info("patch the domain resource with overridesConfigMap and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overridesConfigMap\", \"value\": \"" + overridecm + "\"},"
        + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    verifyIntrospectorRuns();
    verifyPodsStateNotChanged();

    //wait until config is updated upto 5 minutes
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for jms server configuration to be updated"
                + "(elapsed time {0} ms, remaining time {1} ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(configUpdated());

    verifyJMSResourceOverride();
    verifyWLDFResourceOverride();

  }

  private Callable<Boolean> configUpdated() {
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName),
            "default"),
        "Getting admin server node port failed");

    //verify server attribute MaxMessageSize
    String appURI = "/sitconfig/SitconfigServlet";
    String url = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort + appURI;

    return (()
        -> {
      HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(url, true));
      return (response.statusCode() == 200) && response.body().contains("ExpirationPolicy:Discard");
    });
  }

  private void verifyJMSResourceOverride() {
    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String uri = "http://" + K8S_NODEPORT_HOST + ":" + port + "/sitconfig/SitconfigServlet";

    HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(uri, true));
    assertEquals(200, response.statusCode(), "Status code not equals to 200");
    assertTrue(response.body().contains("ExpirationPolicy:Discard"), "Didn't get ExpirationPolicy:Discard");
    assertTrue(response.body().contains("RedeliveryLimit:20"), "Didn't get RedeliveryLimit:20");
    assertTrue(response.body().contains("Notes:mysitconfigdomain"), "Didn't get Correct Notes description");
  }

  private void verifyWLDFResourceOverride() {
    int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String uri = "http://" + K8S_NODEPORT_HOST + ":" + port + "/sitconfig/SitconfigServlet";

    HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(uri, true));
    assertEquals(200, response.statusCode(), "Status code not equals to 200");
    assertTrue(response.body().contains("MONITORS:PASSED"), "Didn't get MONITORS:PASSED");
    assertTrue(response.body().contains("HARVESTORS:PASSED"), "Didn't get HARVESTORS:PASSED");
    assertTrue(response.body().contains("HARVESTOR MATCHED:weblogic.management.runtime.JDBCServiceRuntimeMBean"),
        "Didn't get HARVESTOR MATCHED:weblogic.management.runtime.JDBCServiceRuntimeMBean");
    assertTrue(response.body().contains("HARVESTOR MATCHED:weblogic.management.runtime.ServerRuntimeMBean"),
        "Didn't get HARVESTOR MATCHED:weblogic.management.runtime.ServerRuntimeMBean");
  }


  //store pod creation timestamps for podstate check
  private void storePodCreationTimestamps() {
    // get the pod creation time stamps
    podTimestamps = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    podTimestamps.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      podTimestamps.put(managedServerPodNamePrefix + i,
          getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }
  }

  //check if the pods are restarted by comparing the pod creationtimestamp.
  private void verifyPodsStateNotChanged() {
    logger.info("Verifying the WebLogic server pod states are not changed");
    for (Map.Entry<String, OffsetDateTime> entry : podTimestamps.entrySet()) {
      String podName = entry.getKey();
      OffsetDateTime creationTimestamp = entry.getValue();
      assertTrue(podStateNotChanged(podName, domainUid, domainNamespace,
          creationTimestamp), "Pod is restarted");
    }
  }

  //verify the introspector pod is created and run
  private void verifyIntrospectorRuns() {
    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);
  }

  //create a standard WebLogic domain.
  private void createDomain() {

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    t3ChannelPort = getNextFreePort();

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(()
        -> File.createTempFile("domain", ".properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", adminServerName);
    p.setProperty("managed_server_port", Integer.toString(managedServerPort));
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "2");
    p.setProperty("managed_server_name_base", managedServerNameBase);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(()
        -> p.store(new FileOutputStream(domainPropertiesFile), "domain properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "sit-config-create-domain.py");

    // create configmap and domain on persistent volume using the WLST script and property file
    createDomainOnPVUsingWlst(wlstScript, domainPropertiesFile.toPath(),
        pvName, pvcName, domainNamespace);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .configuration(new Configuration()
                .overrideDistributionStrategy("DYNAMIC"))
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid) // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET)))  // this secret is used only for non-kind cluster
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.debug.DebugSituationalConfig=true "
                        + "-Dweblogic.debug.DebugSituationalConfigDumpXml=true "
                        + "-Dweblogic.kernel.debug=true "
                        + "-Dweblogic.debug.DebugMessaging=true "
                        + "-Dweblogic.debug.DebugConnection=true "
                        + "-Dweblogic.ResolveDNSName=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addEnvItem(new V1EnvVar()
                    .name("CUSTOM_ENV")
                    .value("##~`!^${ls}"))
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
                .serverStartState("RUNNING")));
    setPodAntiAffinity(domain);
    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
  }

  //deploy application sitconfig.war to domain
  private void deployApplication(String targets) {
    logger.info("Getting port for default channel");
    int defaultChannelPort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server default port failed");
    logger.info("default channel port: {0}", defaultChannelPort);
    assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

    //deploy application
    logger.info("Deploying webapp {0} to domain", sitconfigAppPath);
    deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, targets, sitconfigAppPath,
        domainNamespace);
  }

  /**
   * Create a WebLogic domain on a persistent volume by doing the following. Create a configmap containing WLST script
   * and property file. Create a Kubernetes job to create domain on persistent volume.
   *
   * @param wlstScriptFile python script to create domain
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
        () -> createConfigMapForDomainCreation(
            domainScriptConfigMapName, domainScriptFiles, namespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh
        // script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName());
    //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        namespace, jobCreationContainer);
  }

}

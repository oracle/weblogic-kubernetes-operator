// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.K8sEvents;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLS_UPDATE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to introspectVersion attribute.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the introspectVersion runs the introspector")
@IntegrationTest
public class ItKubernetesEvents {

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private static final String domainUid = "myintrodomain";

  private static String nginxNamespace = null;
  private static int nodeportshttp;
  private static HelmParams nginxHelmParams = null;
  private static String imageUpdate = KIND_REPO != null ? KIND_REPO
      + (WEBLOGIC_IMAGE_NAME + ":" + WLS_UPDATE_IMAGE_TAG).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
      : WEBLOGIC_IMAGE_NAME + ":" + WLS_UPDATE_IMAGE_TAG;
  private final String wlSecretName = "weblogic-credentials";

  private Map<String, DateTime> podsWithTimeStamps = null;

  // create standard, reusable retry/backoff policy
  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(10, MINUTES).await();

  private static Path clusterViewAppPath;
  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for Introspect Version WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);
    logger.info("Assign a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace is null");
    nginxNamespace = namespaces.get(2);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);
    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

  }

  /**
   * Test domain status gets updated when introspectVersion attribute is added under domain.spec.
   * Test Creates a domain in persistent volume using WLST.
   * Updates the cluster configuration; cluster size using online WLST.
   * Patches the domain custom resource with introSpectVersion.
   * Verifies the introspector runs and the cluster maximum replica is updated
   * under domain status.
   * Verifies that the new pod comes up and sample application deployment works.
   */
  @Order(1)
  @Test
  @DisplayName("Test introSpectVersion starting a introspector and updating domain status")
  public void testDomainDefaultEvents() {

    final String clusterName = "mycluster";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;

    final String managedServerNameBase = "ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    final int managedServerPort = 8001;

    int replicaCount = 2;

    // in general the node port range has to be between 30,000 to 32,767
    // to avoid port conflict because of the delay in using it, the port here
    // starts with 30100
    final int t3ChannelPort = getNextFreePort(30172, 32767);

    final String pvName = domainUid + "-pv"; // name of the persistent volume
    final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", "properties"),
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
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "domain properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");

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
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)  // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET)))  // this secret is used only in non-kind cluster
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
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.kernel.debug=true "
                        + "-Dweblogic.debug.DebugMessaging=true "
                        + "-Dweblogic.debug.DebugConnection=true "
                        + "-Dweblogic.debug.DebugUnicastMessaging=true "
                        + "-Dweblogic.debug.DebugClusterHeartbeats=true "
                        + "-Dweblogic.debug.DebugJNDI=true "
                        + "-Dweblogic.debug.DebugJNDIResolution=true "
                        + "-Dweblogic.debug.DebugCluster=true "
                        + "-Dweblogic.ResolveDNSName=true "
                        + "-Dweblogic.MaxMessageSize=20000000"))
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
                        .nodePort(0))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));
    setPodAntiAffinity(domain);
    DateTime timestamp = new DateTime(System.currentTimeMillis());
    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // verify the DomainCreated event is generated
    K8sEvents.checkDomainEvent(opNamespace, domainNamespace, domainUid, K8sEvents.DOMAIN_CREATED, "Normal", timestamp);

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

    // verify the DomainProcessing Starting/Completed event is generated
    K8sEvents.checkDomainEvent(opNamespace, domainNamespace, domainUid,
        K8sEvents.DOMAIN_PROCESSING_STARTING, "Normal", timestamp);
    K8sEvents.checkDomainEvent(opNamespace, domainNamespace, domainUid,
        K8sEvents.DOMAIN_PROCESSING_COMPLETED, "Normal", timestamp);

    // get the pod creation time stamps
    LinkedHashMap<String, DateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    DateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }

    // patch the domain to increase the replicas of the cluster and add introspectVersion field
    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));
    String patchStr =
        "["
            + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
            + "]";

    logger.info("Updating replicas in cluster {0} using patch string: {1}", clusterName, patchStr);
    V1Patch patch = new V1Patch(patchStr);
    timestamp = new DateTime(System.currentTimeMillis());
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the DomainCreated event is generated
    K8sEvents.checkDomainEvent(opNamespace, domainNamespace, domainUid, K8sEvents.DOMAIN_CREATED, "Normal", timestamp);
    // verify the DomainProcessing Starting/Completed event is generated
    K8sEvents.checkDomainEvent(opNamespace, domainNamespace, domainUid,
        K8sEvents.DOMAIN_PROCESSING_STARTING, "Normal", timestamp);

    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    // verify existing managed server services are not affected
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }

    // verify existing managed server pods are not affected
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    //verify domain processing completed
    K8sEvents.checkDomainEvent(opNamespace, domainNamespace, domainUid,
        K8sEvents.DOMAIN_PROCESSING_COMPLETED, "Normal", timestamp);

    // get the original domain resource before update
    Domain domaincr = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domaincr, "Got null domain resource");
    assertNotNull(domaincr.getSpec(), domainUid + " /spec is null");

    //print out the original image name
    String imageName = domaincr.getSpec().getImage();
    logger.info("Currently the image name used for the domain is: {0}", imageName);

    //change image name to imageUpdate
    patchStr = "[{\"op\": \"replace\", \"path\": \"/spec/image\", \"value\": \"nonexistingimage:v1\"}]";
    logger.info("PatchStr for imageUpdate: {0}", patchStr.toString());

    patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "patchDomainCustomResource(imageUpdate) failed");

    domaincr = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domaincr, "Got null domain resource after patching");
    assertNotNull(domaincr.getSpec(), domainUid + " /spec is null");

    //print out image name in the new patched domain
    logger.info("In the new patched domain image name is: {0}", domaincr.getSpec().getImage());

    try {
      Thread.sleep(1000 * 120);
    } catch (InterruptedException ex) {
      Logger.getLogger(ItKubernetesEvents.class.getName()).log(Level.SEVERE, null, ex);
    }

    timestamp = new DateTime(System.currentTimeMillis());
    TestActions.deleteDomainCustomResource(domainUid, domainNamespace);
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);
    checkPodDoesNotExist(managedServerPodNamePrefix + 1, domainUid, domainNamespace);
    checkPodDoesNotExist(managedServerPodNamePrefix + 2, domainUid, domainNamespace);

    //verify domain deleted event
    K8sEvents.checkDomainEvent(opNamespace, domainNamespace, domainUid,
        K8sEvents.DOMAIN_DELETED, "Normal", timestamp);

  }

  /**
   * Create a WebLogic domain on a persistent volume by doing the following.
   * Create a configmap containing WLST script and property file.
   * Create a Kubernetes job to create domain on persistent volume.
   *
   * @param wlstScriptFile       python script to create domain
   * @param domainPropertiesFile properties file containing domain configuration
   * @param pvName               name of the persistent volume to create domain in
   * @param pvcName              name of the persistent volume claim
   * @param namespace            name of the domain namespace in which the job is created
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
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName()); //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        namespace, jobCreationContainer);

  }


}
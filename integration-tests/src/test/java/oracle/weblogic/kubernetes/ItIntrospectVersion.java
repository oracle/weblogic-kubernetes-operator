// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.CommonTestUtils;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getCurrentIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.Pod.getPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.verifyPodsNotRolled;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyServerCommunication;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingRest;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_CYCLE_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.getOpGeneratedEvent;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScript;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to introspectVersion attribute.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the introspectVersion runs the introspector")
@IntegrationTest
class ItIntrospectVersion {

  private static String opNamespace = null;
  private static String introDomainNamespace = null;

  private static final String domainUid = "myintrodomain";

  private final String wlSecretName = "weblogic-credentials";

  private static String adminSvcExtHost = null;
  private static String clusterRouteHost = null;

  private Map<String, OffsetDateTime> podsWithTimeStamps = null;

  private static final String INTROSPECT_DOMAIN_SCRIPT = "introspectDomain.sh";
  private static final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private static final Path tempSamplePath = Paths.get(WORK_DIR, "intros-sample-testing");
  private static final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");

  private static Path clusterViewAppPath;
  private static LoggingFacade logger = null;
  private final int managedServerPort = 7100;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for Introspect Version WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    introDomainNamespace = namespaces.get(1);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, introDomainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(introDomainNamespace);

    // build the clusterview application
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", introDomainNamespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

    setupSample();
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
  void testDomainIntrospectVersionNotRolling() {

    final String clusterName = "mycluster";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String clusterServiceName = domainUid + "-cluster-" + clusterName;

    final String managedServerNameBase = "managed-server";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    int replicaCount = 2;

    final int t3ChannelPort = getNextFreePort();

    final String pvName = domainUid + "-pv"; // name of the persistent volume
    final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, introDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, introDomainNamespace);

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
        pvName, pvcName, introDomainNamespace);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(introDomainNamespace))
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
                .namespace(introDomainNamespace))
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
    // verify the domain custom resource is created
    createDomainAndVerify(domain, introDomainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, introDomainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, introDomainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), introDomainNamespace);
    logger.info("admin svc host = {0}", adminSvcExtHost);

    // deploy application and verify all servers functions normally
    logger.info("Getting port for default channel");
    int defaultChannelPort = assertDoesNotThrow(()
        -> getServicePort(introDomainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server default port failed");
    logger.info("default channel port: {0}", defaultChannelPort);
    assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

    int serviceNodePort = assertDoesNotThrow(() ->
            getServiceNodePort(introDomainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");
    logger.info("Admin Server default node port : {0}", serviceNodePort);
    assertNotEquals(-1, serviceNodePort, "admin server default node port is not valid");

    //deploy clusterview application
    logger.info("Deploying clusterview app {0} to cluster {1}",
        clusterViewAppPath, clusterName);
    //ExecResult result = null;
    String targets = "{identity:[clusters,'mycluster']},{identity:[servers,'admin-server']}";

    String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);
    logger.info("hostAndPort = {0} ", hostAndPort);

    testUntil(
        () -> {
          ExecResult result = assertDoesNotThrow(() -> deployUsingRest(hostAndPort,
              ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
              targets, clusterViewAppPath, null, "clusterview"));
          return result.stdout().equals("202");
        },
        logger,
        "Deploying the application using Rest");

    List<String> managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(managedServerNameBase + i);
    }

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // get the pod creation time stamps
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(introDomainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(introDomainNamespace, managedServerPodNamePrefix + i));
    }

    logger.info("change the cluster size to 3 and verify the introspector runs and updates the domain status");
    // create a temporary WebLogic WLST property file
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
        "Creating WLST properties file failed");
    Properties p1 = new Properties();
    p1.setProperty("admin_host", adminServerPodName);
    p1.setProperty("admin_port", Integer.toString(defaultChannelPort));
    p1.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p1.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p1.setProperty("cluster_name", clusterName);
    p1.setProperty("max_cluster_size", Integer.toString(3));
    p1.setProperty("test_name", "change_server_count");
    assertDoesNotThrow(() -> p1.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");

    // change the server count of the cluster by running online WLST
    Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
    executeWLSTScript(configScript, wlstPropertiesFile.toPath(), introDomainNamespace);

    // patch the domain to increase the replicas of the cluster and add introspectVersion field
    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, introDomainNamespace));
    String patchStr =
        "["
            + "{\"op\": \"replace\", \"path\": \"/spec/clusters/0/replicas\", \"value\": 3},"
            + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
            + "]";

    logger.info("Updating replicas in cluster {0} using patch string: {1}", clusterName, patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, introDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, introDomainNamespace);

    //verify the maximum cluster size is updated to expected value
    testUntil(
        () -> {
          Domain res = getDomainCustomResource(domainUid, introDomainNamespace);
          return (res.getStatus().getClusters().get(0).getMaximumReplicas() == 3);
        },
        logger,
        "Domain.status.clusters.{0}.maximumReplicas to be {1}",
        clusterName,
        3);

    // verify the 3rd server pod comes up
    checkServiceExists(managedServerPodNamePrefix + 3, introDomainNamespace);
    checkPodReady(managedServerPodNamePrefix + 3, domainUid, introDomainNamespace);

    // verify existing managed server services are not affected
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify existing managed server pods are not affected
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    // verify existing pods are not restarted
    podStateNotChanged(adminServerPodName, domainUid, introDomainNamespace, adminPodCreationTime);
    for (int i = 1; i <= replicaCount; i++) {
      podStateNotChanged(managedServerPodNamePrefix + i,
          domainUid, introDomainNamespace, pods.get(i));
    }

    //create ingress controller - OKD uses services exposed as routes
    if (!OKD) {
      Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
      clusterNameMsPortMap.put(clusterName, managedServerPort);
      logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, introDomainNamespace);
      createIngressForDomainAndVerify(domainUid, introDomainNamespace, clusterNameMsPortMap);
    } else {
      clusterRouteHost = createRouteForOKD(clusterServiceName, introDomainNamespace);
    }

    managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount + 1; i++) {
      managedServerNames.add(managedServerNameBase + i);
    }

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    String curlRequest = null;

    if (OKD) {
      logger.info("cluster svc host = {0}", clusterRouteHost);
      logger.info("Accessing the clusterview app through cluster route");
      curlRequest = String.format("curl --silent --show-error --noproxy '*' "
            + "http://%s/clusterview/ClusterViewServlet"
            + "\"?user=" + ADMIN_USERNAME_DEFAULT
            + "&password=" + ADMIN_PASSWORD_DEFAULT + "\"", clusterRouteHost);
    }

    // verify each managed server can see other member in the cluster
    for (String managedServerName : managedServerNames) {
      verifyConnectionBetweenClusterMembers(managedServerName, managedServerNames);
    }

    // verify when a domain resource has spec.introspectVersion configured,
    // all WebLogic server pods will have a label "weblogic.introspectVersion"
    // set to the value of spec.introspectVersion.
    verifyIntrospectVersionLabelInPod(replicaCount);
  }

  private void verifyConnectionBetweenClusterMembers(String serverName, List<String> managedServerNames) {
    String podName = domainUid + "-" + serverName;
    final String command = String.format(
        "kubectl exec -n " + introDomainNamespace + "  " + podName + " -- curl http://"
            + ADMIN_USERNAME_DEFAULT
            + ":"
            + ADMIN_PASSWORD_DEFAULT
            + "@" + podName + ":%s/clusterview/ClusterViewServlet"
            + "\"?user=" + ADMIN_USERNAME_DEFAULT
            + "&password=" + ADMIN_PASSWORD_DEFAULT + "\"",managedServerPort);
    verifyServerCommunication(command, serverName, managedServerNames);
  }

  /**
   * Test server pods are rolling restarted and updated when domain is patched
   * with introSpectVersion when non dynamic changes are made.
   * Updates the admin server listen port using online WLST.
   * Patches the domain custom resource with introSpectVersion.
   * Verifies the introspector runs and pods are restated in a rolling fashion.
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   * Verifies the new admin port of the admin server in services.
   * Verifies accessing sample application in admin server works.
   */
  @Order(2)
  @Test
  @DisplayName("Test introspectVersion rolling server pods when admin server port is changed")
  void testDomainIntrospectVersionRolling() {

    final String clusterName = "mycluster";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;

    final String managedServerNameBase = "managed-server";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    final int replicaCount = 3;
    final int newAdminPort = 7005;

    checkServiceExists(adminServerPodName, introDomainNamespace);
    checkPodReady(adminServerPodName, domainUid, introDomainNamespace);
    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    // get the pod creation time stamps
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(introDomainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(introDomainNamespace, managedServerPodNamePrefix + i));
    }

    //change admin port from 7001 to 7005
    String restUrl = "/management/weblogic/latest/edit/servers/" + adminServerName;

    String curlCmd = "curl -v -m 60"
        + " -u " + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT
        + " -H X-Requested-By:MyClient "
        + " -H Accept:application/json "
        + " -H Content-Type:application/json -d \"{listenPort: " + newAdminPort + "}\" "
        + " -X POST http://" + adminServerPodName + ":7001" + restUrl;
    logger.info("Command to set HTTP request and get HTTP response {0} ", curlCmd);

    try {
      execCommand(introDomainNamespace, adminServerPodName, null, true, "/bin/sh", "-c", curlCmd);
    } catch (Exception ex) {
      logger.severe(ex.getMessage());
    }

    //needed for event verification
    OffsetDateTime timestamp = now();

    patchDomainResourceWithNewIntrospectVersion(domainUid, introDomainNamespace);

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);

    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, introDomainNamespace);

    //verify the pods are restarted
    verifyRollingRestartOccurred(pods, 1, introDomainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, introDomainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, introDomainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    //verify the introspectVersion change causes the domain roll events to be logged
    logger.info("verify domain roll starting/pod cycle starting/domain roll completed events are logged");
    checkEvent(opNamespace, introDomainNamespace, domainUid, DOMAIN_ROLL_STARTING,
        "Normal", timestamp, withStandardRetryPolicy);
    checkEvent(opNamespace, introDomainNamespace, domainUid, POD_CYCLE_STARTING,
        "Normal", timestamp, withStandardRetryPolicy);

    CoreV1Event event = getOpGeneratedEvent(introDomainNamespace, DOMAIN_ROLL_STARTING, "Normal", timestamp);
    logger.info(Yaml.dump(event));
    logger.info("verify the event message contains the domain resource changed message");
    assertTrue(event.getMessage().contains("resource changed"));

    event = getOpGeneratedEvent(introDomainNamespace, POD_CYCLE_STARTING, "Normal", timestamp);
    logger.info(Yaml.dump(event));
    logger.info("verify the event message contains the property changed in domain resource");
    assertTrue(event.getMessage().contains("ADMIN_PORT"));

    checkEvent(opNamespace, introDomainNamespace, domainUid, DOMAIN_ROLL_COMPLETED,
        "Normal", timestamp, withStandardRetryPolicy);

    // verify the admin port is changed to newAdminPort
    assertEquals(newAdminPort, assertDoesNotThrow(()
        -> getServicePort(introDomainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server port failed"),
        "Updated admin server port is not equal to expected value");

    List<String> managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(managedServerNameBase + i);
    }

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    
    // verify each managed server can see other member in the cluster
    for (String managedServerName : managedServerNames) {
      verifyConnectionBetweenClusterMembers(managedServerName, managedServerNames);
    }

    // verify when a domain/cluster is rolling restarted without changing the spec.introspectVersion,
    // all server pods' weblogic.introspectVersion label stay unchanged after the pods are restarted.
    verifyIntrospectVersionLabelInPod(replicaCount);
  }

  /**
   * Test changes the WebLogic credentials and verifies the servers can startup and function with changed credentials.
   * a. Creates new WebLogic credentials using WLST.
   * b. Creates new Kubernetes secret for WebLogic credentials.
   * c. Patch the Domain Resource with new credentials, restartVerion and introspectVersion.
   * d. Verifies the servers in the domain are restarted .
   * e. Make a REST api call to access management console using new password.
   * f. Make a REST api call to access management console using old password.
   */
  @Order(3)
  @Test
  @DisplayName("Test change WebLogic admin credentials for domain running in persistent volume")
  void testCredentialChange() {

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;

    final String managedServerNameBase = "managed-server";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    int replicaCount = 3;

    logger.info("Getting port for default channel");
    int adminServerPort
        = getServicePort(introDomainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServerPort, "Couldn't get valid port for default channel");

    // get the pod creation time stamps
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(introDomainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(introDomainNamespace, managedServerPodNamePrefix + i));
    }

    // create a temporary WebLogic WLST property file
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
        "Creating WLST properties file failed");
    Properties p = new Properties();
    p.setProperty("admin_host", adminServerPodName);
    p.setProperty("admin_port", Integer.toString(adminServerPort));
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("new_admin_user", ADMIN_USERNAME_PATCH);
    p.setProperty("new_admin_password", ADMIN_PASSWORD_PATCH);
    p.setProperty("test_name", "replace_admin_user");
    assertDoesNotThrow(() -> p.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");

    // change the admin server port to a different value to force pod restart
    logger.info("Creating a new WebLogic user/password {0}/{1} in default security realm",
        ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH);
    Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
    executeWLSTScript(configScript, wlstPropertiesFile.toPath(), introDomainNamespace);

    // create a new secret for admin credentials
    logger.info("Create a new secret that contains new WebLogic admin credentials");
    String newWlSecretName = "weblogic-credentials-new";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        newWlSecretName,
        introDomainNamespace,
        ADMIN_USERNAME_PATCH,
        ADMIN_PASSWORD_PATCH),
        String.format("createSecret failed for %s", newWlSecretName));

    // delete the old secret
    logger.info("Deleting the old secret");
    deleteSecret(wlSecretName, introDomainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, introDomainNamespace));
    String oldVersion = assertDoesNotThrow(()
        -> getDomainCustomResource(domainUid, introDomainNamespace).getSpec().getRestartVersion());
    int newVersion = oldVersion == null ? 1 : Integer.valueOf(oldVersion) + 1;

    logger.info("patch the domain resource with new WebLogic secret, restartVersion and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/webLogicCredentialsSecret/name\", "
        + "\"value\": \"" + newWlSecretName + "\"},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", "
        + "\"value\": \"" + introspectVersion + "\"},"
        + "{\"op\": \"add\", \"path\": \"/spec/restartVersion\", "
        + "\"value\": \"" + newVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, introDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);

    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, introDomainNamespace);

    //verify the pods are restarted
    verifyRollingRestartOccurred(pods, 1, introDomainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, introDomainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, introDomainNamespace);

    // verify new cluster managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify new cluster managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        introDomainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");
    assertNotEquals(-1, serviceNodePort, "Couldn't get valid node port for default channel");

    // Make a REST API call to access management console
    // So that the test will also work with WebLogic slim image
    final boolean VALID = true;
    final boolean INVALID = false;
    logger.info("Check that after patching current credentials are not valid and new credentials are");
    verifyCredentials(adminSvcExtHost, adminServerPodName, introDomainNamespace,
         ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, INVALID);
    verifyCredentials(adminSvcExtHost, adminServerPodName, introDomainNamespace,
         ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH, VALID);

    List<String> managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(managedServerNameBase + i);
    }

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH);

    // verify when the spec.introspectVersion is changed,
    // all running server pods' weblogic.introspectVersion label is updated to the new value.
    verifyIntrospectVersionLabelInPod(replicaCount);
  }

  /**
   * Test brings up a new cluster and verifies it can successfully start by doing the following.
   * a. Creates new WebLogic static cluster using WLST.
   * b. Patch the Domain Resource with cluster
   * c. Update the introspectVersion version
   * d. Verifies the servers in the new WebLogic cluster comes up without affecting any of the running servers on
   * pre-existing WebLogic cluster.
   */
  @Order(4)
  @Test
  @DisplayName("Test new cluster creation on demand using WLST and introspection")
  void testCreateNewCluster() {

    final String clusterName = "cl2";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;

    final String managedServerNameBase = "cl2-ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    final int replicaCount = 2;

    logger.info("Getting port for default channel");
    int adminServerPort
        = getServicePort(introDomainNamespace, getExternalServicePodName(adminServerPodName), "default");

    // create a temporary WebLogic WLST property file
    File wlstPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("wlst", "properties"),
        "Creating WLST properties file failed");
    Properties p = new Properties();
    p.setProperty("admin_host", adminServerPodName);
    p.setProperty("admin_port", Integer.toString(adminServerPort));
    p.setProperty("admin_username", ADMIN_USERNAME_PATCH);
    p.setProperty("admin_password", ADMIN_PASSWORD_PATCH);
    p.setProperty("test_name", "create_cluster");
    p.setProperty("cluster_name", clusterName);
    p.setProperty("server_prefix", managedServerNameBase);
    p.setProperty("server_count", "3");
    assertDoesNotThrow(() -> p.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
        "Failed to write the WLST properties to file");

    // changet the admin server port to a different value to force pod restart
    Path configScript = Paths.get(RESOURCE_DIR, "python-scripts", "introspect_version_script.py");
    executeWLSTScript(configScript, wlstPropertiesFile.toPath(), introDomainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, introDomainNamespace));

    logger.info("patch the domain resource with new cluster and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": "
        + "    {\"clusterName\" : \"" + clusterName + "\", \"replicas\": 2, \"serverStartState\": \"RUNNING\"}"
        + "},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, introDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);

    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, introDomainNamespace);

    // verify new cluster managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, introDomainNamespace);
    }

    // verify new cluster managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    List<String> managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(managedServerNameBase + i);
    }

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH);

  }

  /**
   * Modify the domain scope property
   * From: "image: container-registry.oracle.com/middleware/weblogic:ImageTagBeingUsed" to
   * To: "image: container-registry.oracle.com/middleware/weblogic:DateAndTimeStamp"
   * e.g, From ""image: container-registry.oracle.com/middleware/weblogic:12.2.1.4"
   * To: "image:container-registry.oracle.com/middleware/weblogic:2021-07-08-162571383699"
   * Verify all the pods are restarted and back to ready state
   * Verify the admin server is accessible and cluster members are healthy
   */
  @Order(5)
  @Test
  @DisplayName("Verify server pods are restarted by updating image name")
  void testUpdateImageName() {

    final String domainNamespace = introDomainNamespace;
    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String managedServerNameBase = "cl2-ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    final int replicaCount = 2;

    List<String> managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(managedServerNameBase + i);
    }

    // get the original domain resource before update
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), domain1 + " /spec is null");

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodName, managedServerPodNamePrefix,
        replicaCount);

    //print out the original image name
    String imageName = domain1.getSpec().getImage();
    logger.info("Currently the image name used for the domain is: {0}", imageName);

    //change image name to imageUpdate
    String imageTag = CommonTestUtils.getDateAndTimeStamp();
    String imageUpdate = KIND_REPO != null ? KIND_REPO
        + (WEBLOGIC_IMAGE_NAME + ":" + imageTag).substring(TestConstants.BASE_IMAGES_REPO.length() + 1)
        : WEBLOGIC_IMAGE_NAME + ":" + imageTag;
    dockerTag(imageName, imageUpdate);
    dockerLoginAndPushImageToRegistry(imageUpdate);

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/image\",")
        .append("\"value\": \"")
        .append(imageUpdate)
        .append("\"}]");
    logger.info("PatchStr for imageUpdate: {0}", patchStr.toString());

    assertTrue(patchDomainResource(domainUid, domainNamespace, patchStr),
        "patchDomainCustomResource(imageUpdate) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + " /spec is null");

    //print out image name in the new patched domain
    logger.info("In the new patched domain image name is: {0}", domain1.getSpec().getImage());

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH);

  }

  /**
   * Test that when a domain resource has spec.introspectVersion configured,
   * after a cluster is scaled up, new server pods have the label "weblogic.introspectVersion" set as well.
   */
  @Test
  @Order(6)
  @DisplayName("Scale up cluster-1 in domain1Namespace and verify label weblogic.introspectVersion set")
  void testDedicatedModeSameNamespaceScale() {
    final String adminServerName = "admin-server";
    final String managedServerNameBase = "managed-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    // scale up the domain by increasing replica count
    int replicaCount = 3;
    boolean scalingSuccess = assertDoesNotThrow(() ->
        scaleCluster(domainUid, introDomainNamespace, "cluster-1", replicaCount),
        String.format("Scaling the cluster cluster-1 of domain %s in namespace %s failed",
        domainUid, introDomainNamespace));
    assertTrue(scalingSuccess,
        String.format("Cluster scaling failed for domain %s in namespace %s", domainUid, introDomainNamespace));

    // check new server is started and existing servers are running
    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, introDomainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, introDomainNamespace);

    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    // verify when a domain resource has spec.introspectVersion configured,
    // after a cluster is scaled up, new server pods have the label "weblogic.introspectVersion" set as well.
    verifyIntrospectVersionLabelInPod(replicaCount);
  }

  /**
   * Update the introspectVersion of the domain resource using lifecycle script.
   * Refer to kubernetes/samples/scripts/domain-lifecycle/introspectDomain.sh
   * The usecase update the introspectVersion by passing differnt value to -i 
   * option (non-numeic, non-numeric with space, no value) and make sure that 
   * the introspectVersion is updated accrodingly in both domain sepc level 
   * and server pod level. 
   * It also verifies the intospector job is started/stoped and none of the 
   * server pod is rolled since there is no change to resource configuration.
   */
  @Test
  @Order(7)
  @DisplayName("Test to use sample scripts to explicitly initiate introspection")
  void testIntrospectDomainScript() {
    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String managedServerNameBase = "managed-server";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    final int replicaCount = 3;

    // verify admin server pods are ready
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, introDomainNamespace);
    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, introDomainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, introDomainNamespace);
    }

    // get the pod creation time stamps
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(introDomainNamespace, adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      pods.put(managedServerPodNamePrefix + i,
          getPodCreationTime(introDomainNamespace, managedServerPodNamePrefix + i));
    }

    // get introspectVersion before running introspectDomain.sh
    String ivBefore =
        assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, introDomainNamespace));
    logger.info("introspectVersion before running the script {0}",ivBefore);

    // use introspectDomain.sh to initiate introspection
    logger.info("Initiate introspection with non numeric string (vX.Y)");
    String introspectVersion = "vX.Y";
    String extraParam = " -i " + introspectVersion;
    assertDoesNotThrow(() -> executeLifecycleScript(INTROSPECT_DOMAIN_SCRIPT, extraParam),
        String.format("Failed to run %s", INTROSPECT_DOMAIN_SCRIPT));

    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, introDomainNamespace);

    // get introspectVersion after running introspectDomain.sh
    String ivAfter = assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, introDomainNamespace));
    logger.info("introspectVersion after running the script {0}",ivAfter);

    // verify that introspectVersion is changed 
    assertTrue(ivAfter.equals(introspectVersion),
        "introspectVersion must change to  "  + introspectVersion);

    assertFalse(ivAfter.equals(ivBefore),
        "introspectVersion should change from " + ivBefore + " to " + ivAfter);

    // verify when a domain resource has spec.introspectVersion configured,
    // after a introspectVersion is modified, new server pods have the label
    // "weblogic.introspectVersion" set as well.
    testUntil(
        () -> {
          assertDoesNotThrow(() -> verifyIntrospectVersionLabelInPod(replicaCount));
          return true;
        },
        logger,
        " Running verifyIntrospectVersionLabelInPod");


    // use introspectDomain.sh to initiate introspection
    logger.info("Initiate introspection with non numeric string with space");
    introspectVersion = "My Version";
    String extraParam2 = " -i " + "\"" + introspectVersion + "\"";
    assertDoesNotThrow(() -> executeLifecycleScript(INTROSPECT_DOMAIN_SCRIPT, extraParam2),
        String.format("Failed to run %s", INTROSPECT_DOMAIN_SCRIPT));

    //verify the introspector pod is created and runs
    introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);

    // get introspectVersion after running introspectDomain.sh
    ivAfter = assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, introDomainNamespace));
    logger.info("introspectVersion after running the script {0}",ivAfter);

    // verify that introspectVersion is changed 
    assertTrue(ivAfter.equals(introspectVersion),
        "introspectVersion must change to  "  + introspectVersion);

    // use introspectDomain.sh to initiate introspection
    // Since the current version is non-numeric the updated version is 
    // updated to 1 
    logger.info("Initiate introspection with no explicit version(1)");
    assertDoesNotThrow(() -> executeLifecycleScript(INTROSPECT_DOMAIN_SCRIPT, ""),
        String.format("Failed to run %s", INTROSPECT_DOMAIN_SCRIPT));

    //verify the introspector pod is created and runs
    introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);

    // get introspectVersion after running introspectDomain.sh
    ivAfter = assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, introDomainNamespace));
    logger.info("introspectVersion after running the script {0}",ivAfter);

    // verify that introspectVersion is changed 
    assertTrue(ivAfter.equals("1"), "introspectVersion must change to 1");

    // use introspectDomain.sh to initiate introspection
    // Since the current version is 1, the updated version must be set to 2 
    logger.info("Initiate introspection with no explicit version (2)");
    assertDoesNotThrow(() -> executeLifecycleScript(INTROSPECT_DOMAIN_SCRIPT, ""),
        String.format("Failed to run %s", INTROSPECT_DOMAIN_SCRIPT));

    //verify the introspector pod is created and runs
    introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);

    // get introspectVersion after running introspectDomain.sh
    ivAfter = assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, introDomainNamespace));
    logger.info("introspectVersion after running the script {0}",ivAfter);

    // verify that introspectVersion is changed 
    assertTrue(ivAfter.equals("2"), "introspectVersion must change to 2");

    // use introspectDomain.sh to initiate introspection
    // with an explicit introspection with -i parameter
    logger.info("Initiate introspection with explicit numeric version");
    String extraParam3 = " -i  101";
    assertDoesNotThrow(() -> executeLifecycleScript(INTROSPECT_DOMAIN_SCRIPT, extraParam3),
        String.format("Failed to run %s", INTROSPECT_DOMAIN_SCRIPT));

    //verify the introspector pod is created and runs
    introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, introDomainNamespace);

    // get introspectVersion after running introspectDomain.sh
    ivAfter = assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, introDomainNamespace));
    logger.info("introspectVersion after running the script {0}",ivAfter);

    // verify that introspectVersion is changed 
    assertTrue(ivAfter.equals("101"), "introspectVersion must change to 101");

    //verify the pods are not restarted in any introspectVersion update
    verifyPodsNotRolled(introDomainNamespace, pods);
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

  private static void verifyMemberHealth(String adminServerPodName, List<String> managedServerNames,
                                         String user, String password) {

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(introDomainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);
    logger.info("hostAndPort = {0} ", hostAndPort);

    logger.info("Checking the health of servers in cluster");
    String url = "http://" + hostAndPort
        + "/clusterview/ClusterViewServlet?user=" + user + "&password=" + password;

    testUntil(
        () -> {
          HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(url, true));
          if (response.statusCode() != 200) {
            logger.info("Response code is not 200 retrying...");
            return false;
          }
          boolean health = true;
          for (String managedServer : managedServerNames) {
            health = health && response.body().contains(managedServer + ":HEALTH_OK");
            if (health) {
              logger.info(managedServer + " is healthy");
            } else {
              logger.info(managedServer + " health is not OK or server not found");
            }
          }
          return health;
        },
        logger,
        "Verifying the health of all cluster members");
  }

  private void verifyIntrospectVersionLabelInPod(int replicaCount) {
    final String adminServerName = "admin-server";
    final String managedServerNameBase = "managed-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;

    String introspectVersion =
        assertDoesNotThrow(() -> getCurrentIntrospectVersion(domainUid, introDomainNamespace));

    // verify admin server pods
    logger.info("Verify weblogic.introspectVersion in admin server pod {0}", adminServerPodName);
    verifyIntrospectVersionLabelValue(adminServerPodName, introspectVersion);

    // verify managed server pods
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Verify weblogic.introspectVersion in managed server pod {0}",
          managedServerPodNamePrefix + i);
      verifyIntrospectVersionLabelValue(managedServerPodNamePrefix + i, introspectVersion);
    }
  }

  private void verifyIntrospectVersionLabelValue(String podName, String introspectVersion) {
    final String wlsIntroVersion = "weblogic.introspectVersion";
    V1Pod myPod = assertDoesNotThrow(() ->
        getPod(introDomainNamespace, "", podName),
        "Get pod " + podName);

    Map<String, String> myLabels = myPod.getMetadata().getLabels();

    for (Map.Entry<String, String> entry : myLabels.entrySet()) {
      if (entry.getKey().equals(wlsIntroVersion)) {
        logger.info("Get Spec Key:value = {0}:{1}", entry.getKey(), entry.getValue());
        logger.info("Verifying weblogic.introspectVersion is set to {0}", introspectVersion);

        assertTrue(entry.getValue().equals(introspectVersion),
            "Failed to set " + wlsIntroVersion + " to " + introspectVersion);
      }
    }
  }

  // copy samples directory to a temporary location
  private static void setupSample() {
    assertDoesNotThrow(() -> {
      logger.info("Deleting and recreating {0}", tempSamplePath);
      Files.createDirectories(tempSamplePath);
      deleteDirectory(tempSamplePath.toFile());
      Files.createDirectories(tempSamplePath);
      logger.info("Copying {0} to {1}", samplePath, tempSamplePath);
      copyDirectory(samplePath.toFile(), tempSamplePath.toFile());
    });
  }

  // Function to execute domain lifecyle scripts
  private String executeLifecycleScript(String script, String extraParams) {
    String commonParameters = " -d " + domainUid + " -n " + introDomainNamespace + extraParams;
    CommandParams params = new CommandParams().defaults();

    params.command("sh "
        + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
        + commonParameters);

    ExecResult execResult = Command.withParams(params).executeAndReturnResult();
    assertEquals(0, execResult.exitValue(),
        String.format("Failed to execute script  %s ", script));

    return execResult.toString();
  }
}

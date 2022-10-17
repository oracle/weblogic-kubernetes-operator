// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getContainerRestartCount;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.copyFileToPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isNginxReady;
import static oracle.weblogic.kubernetes.assertions.impl.Domain.doesDomainExist;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.CommonLBTestUtils.checkIngressReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_CHANGED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_STARTED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_TERMINATED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkPodEventLoggedOnce;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class creates model-in-image WebLogic domain.
 * Verify the basic lifecycle operations of the WebLogic server pods by
 * triggering rolling.
 * Also verify the sample application can be accessed via NGINX ingress controller.
 */
@DisplayName("Verify rolling restart behavior in a multi-cluster MII domain and "
        + "the sample application can be accessed via NGINX ingress controller")
@IntegrationTest
class ItMiiRollingRestartLivenessProbe {

  // domain constants
  private static final int NUMBER_OF_CLUSTERS_MIIDOMAIN = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final String clusterName = "cluster-1";
  private static final int MANAGED_SERVER_PORT = 8001;
  private static final int ADMIN_SERVER_PORT = 7001;
  private static final int replicaCount = 2;
  private static final String SAMPLE_APP_CONTEXT_ROOT = "sample-war";
  private static final String WLDF_OPENSESSION_APP = "opensessionapp";
  private static final String miiImageName = "mdlb-mii-image";
  private static final String wdtModelFileForMiiDomain = "model-multiclusterdomain-sampleapp-wls.yaml";
  private static final String miiDomainUid = "mdlb-miidomain";

  private static String opNamespace = null;
  private static NginxParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static int nodeportshttps = 0;
  private static LoggingFacade logger = null;
  private static String miiDomainNamespace = null;
  private static String miiImage = null;
  private static String nginxNamespace = null;

  /**
   * Install operator and NGINX.
   * Create a model in image type of domain.
   * Create ingress for the domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    nginxNamespace = namespaces.get(1);

    // get unique namespaces for three different type of domains
    logger.info("Getting unique namespaces for three different type of domains");
    assertNotNull(namespaces.get(2));
    miiDomainNamespace = namespaces.get(2);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // create mii image
    miiImage = createAndPushMiiImage();

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, miiDomainNamespace);

    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    createRouteForOKD("external-weblogic-operator-svc", opNamespace);

    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    if (!OKD) {
      // install and verify NGINX
      nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
      String nginxServiceName = nginxHelmParams.getHelmParams().getReleaseName() + "-ingress-nginx-controller";
      logger.info("NGINX service name: {0}", nginxServiceName);
      nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
      logger.info("NGINX http node port: {0}", nodeportshttp);
      nodeportshttps = getServiceNodePort(nginxNamespace, nginxServiceName, "https");
      logger.info("NGINX https node port: {0}", nodeportshttps);
    }
  }

  /**
   * Verify liveness probe by killing managed server process 3 times to kick pod container auto-restart.
   */
  @Test
  @DisplayName("Test liveness probe of pod")
  void testLivenessProbe() {
    Domain domain = createOrStartDomainBasedOnDomainType();

    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();
    int numClusters = domain.getSpec().getClusters().size();

    String serverNamePrefix;
    if (numClusters > 1) {
      serverNamePrefix = domainUid + "-" + clusterName + "-" + MANAGED_SERVER_NAME_BASE;
    } else {
      serverNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    }

    // create file to kill server process
    File killServerScript = assertDoesNotThrow(() -> createScriptToKillServer(),
        "Failed to create script to kill server");
    logger.info("File/script created to kill server {0}", killServerScript);

    String server1Name = serverNamePrefix + "1";
    checkPodReady(server1Name, domainUid, domainNamespace);

    // copy script to pod
    String destLocation = "/u01/killserver.sh";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace, server1Name, "weblogic-server",
            killServerScript.toPath(), Paths.get(destLocation)),
        String.format("Failed to copy file %s to pod %s in namespace %s",
            killServerScript, server1Name, domainNamespace));
    logger.info("File copied to Pod {0} in namespace {1}", server1Name, domainNamespace);

    // get the restart count of the container in pod before liveness probe restarts
    final int beforeRestartCount =
        assertDoesNotThrow(() -> getContainerRestartCount(domainNamespace, null, server1Name, null),
            String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
                server1Name, domainNamespace));
    logger.info("Restart count before liveness probe {0}", beforeRestartCount);

    // change file permissions
    ExecResult execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, server1Name, null,
            true, "/bin/sh", "-c", "chmod +x " + destLocation),
        String.format("Failed to change permissions for file %s in pod %s", destLocation, server1Name));
    assertTrue(execResult.exitValue() == 0,
        String.format("Failed to change file %s permissions, stderr %s stdout %s", destLocation,
            execResult.stderr(), execResult.stdout()));
    logger.info("File permissions changed inside pod");

    /* First, kill the managed server process in the container three times to cause the node manager to
     * mark the server 'failed not restartable'. This in turn is detected by the liveness probe, which
     * initiates a container restart.
     */
    for (int i = 0; i < 3; i++) {
      execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, server1Name, null,
              true, "/bin/sh", "-c", destLocation + " " + server1Name),
          String.format("Failed to execute script %s in pod %s namespace %s", destLocation,
              server1Name, domainNamespace));
      logger.info("Command executed to kill server inside pod, exit value {0}, stdout {1}, stderr {2}",
          execResult.exitValue(), execResult.stdout(), execResult.stderr());

      try {
        Thread.sleep(2 * 1000);
      } catch (InterruptedException ie) {
        // ignore
      }
    }

    // check pod is ready
    checkPodReady(server1Name, domainUid, domainNamespace);

    // get the restart count of the container in pod after liveness probe restarts
    int afterRestartCount = assertDoesNotThrow(() ->
            getContainerRestartCount(domainNamespace, null, server1Name, null),
        String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
            server1Name, domainNamespace));
    assertTrue(afterRestartCount - beforeRestartCount == 1,
        String.format("Liveness probe did not start the container in pod %s in namespace %s",
            server1Name, domainNamespace));

    // check the sample app is accessible from admin server pod
    for (int i = 1; i <= replicaCount; i++) {
      testUntil(
          checkSampleAppReady(domainUid, domainNamespace, serverNamePrefix + i),
          logger,
          "sample app is accessible from server {0} in namespace {1}",
          serverNamePrefix + i,
          domainNamespace);
    }

    // check the NGINX pod is ready.
    testUntil(isNginxReady(nginxNamespace),
        logger,
        "Nginx is ready in namespace {0}",
        nginxNamespace);

    // check the ingress is ready
    String ingressHost = domainUid + "." + domainNamespace + "." + clusterName + ".test";
    logger.info("Checking for the ingress is ready");
    checkIngressReady(true, ingressHost, false, nodeportshttp, nodeportshttps, "");

    //access application in managed servers through NGINX load balancer
    logger.info("Accessing the sample app through NGINX load balancer");
    String curlCmd = generateCurlCmd(domainUid, domainNamespace, clusterName, SAMPLE_APP_CONTEXT_ROOT);
    List<String> managedServers = listManagedServersBeforeScale(numClusters, clusterName, replicaCount);
    testUntil(withLongRetryPolicy, () -> callWebAppAndCheckForServerNameInResponse(curlCmd, managedServers, 20),
        logger,
        "NGINX can access the test web app from all managed servers {0} in the domain",
        managedServers);

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
  }

  /**
   * Test rolling restart for a multi-clusters domain.
   * Make sure pods are restarted only once.
   * Verify all pods are terminated and restarted only once
   * Rolling restart triggered by changing:
   * imagePullPolicy: IfNotPresent --> imagePullPolicy: Never
   * Verify domain changed event is logged.
   */
  @Test
  @DisplayName("Verify server pods are restarted only once by changing the imagePullPolicy in multi-cluster domain")
  void testMiiMultiClustersRollingRestart() {

    // get the original domain resource before update
    Domain domain1 = createOrStartDomainBasedOnDomainType();
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

    OffsetDateTime timestamp = now();

    //change imagePullPolicy: IfNotPresent --> imagePullPolicy: Never
    StringBuffer patchStr = new StringBuffer("[{")
            .append("\"op\": \"replace\",")
            .append(" \"path\": \"/spec/imagePullPolicy\",")
            .append("\"value\": \"")
            .append("Never")
            .append("\"}]");

    boolean cmPatched = patchDomainResource(miiDomainUid, miiDomainNamespace, patchStr);
    assertTrue(cmPatched, "patchDomainCustomResource(imagePullPolicy) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(miiDomainUid, miiDomainNamespace),
            String.format("getDomainCustomResource failed with ApiException when tried to get domain %s "
                    + "in namespace %s", miiDomainUid, miiDomainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

    //verify domain changed event is logged
    testUntil(
        checkDomainEvent(opNamespace, miiDomainNamespace, miiDomainUid, DOMAIN_CHANGED, "Normal", timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        DOMAIN_CHANGED,
        miiDomainNamespace);

    // wait for longer time for DomainCompleted event
    testUntil(
        withLongRetryPolicy,
        checkDomainEvent(opNamespace, miiDomainNamespace, miiDomainUid, DOMAIN_PROCESSING_COMPLETED,
            "Normal", timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        DOMAIN_PROCESSING_COMPLETED,
        miiDomainNamespace);

    // Verify that pod termination and started events are logged only once for each managed server in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            miiDomainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        logger.info("Checking that managed server pod {0} is terminated and restarted once in namespace {1}",
            managedServerPodName, miiDomainNamespace);
        String stopEventMsg = "Stopping container weblogic-server";
        testUntil(
            withLongRetryPolicy,
            checkPodEventLoggedOnce(miiDomainNamespace, managedServerPodName, POD_TERMINATED, stopEventMsg, timestamp),
            logger,
            "event {0} to be logged for pod {1} in namespace {2}",
            POD_TERMINATED,
            managedServerPodName,
            miiDomainNamespace);

        String startedEventMsg = "Started container weblogic-server";
        testUntil(
            withLongRetryPolicy,
            checkPodEventLoggedOnce(miiDomainNamespace, managedServerPodName, POD_STARTED, startedEventMsg, timestamp),
            logger,
            "event {0} to be logged for pod {1} in namespace {2}",
            POD_STARTED,
            managedServerPodName,
            miiDomainNamespace);
      }
    }

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(miiDomainNamespace, miiDomainUid, replicaCount);
  }

  /**
   * Create model in image domain with multiple clusters.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  private static Domain createMiiDomainWithMultiClusters(String domainNamespace) {

    // admin/managed server name here should match with WDT model yaml file
    String adminServerPodName = miiDomainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    // construct the cluster list used for domain custom resource
    List<Cluster> clusterList = new ArrayList<>();
    for (int i = NUMBER_OF_CLUSTERS_MIIDOMAIN; i >= 1; i--) {
      clusterList.add(new Cluster()
          .clusterName(CLUSTER_NAME_PREFIX + i)
          .replicas(replicaCount)
          .serverStartState("RUNNING"));
    }

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(miiDomainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(miiDomainUid)
            .domainHome("/u01/domains/" + miiDomainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true "
                        + "-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminChannelPortForwardingEnabled(true)
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default-secure")
                        .nodePort(getNextFreePort()))
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .clusters(clusterList)
            .configuration(new Configuration()
                .introspectorJobActiveDeadlineSeconds(300L)
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));
    setPodAntiAffinity(domain);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        miiDomainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, miiDomainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            miiDomainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check managed server pod is ready and service exists in the namespace
        logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, miiDomainUid, domainNamespace);
      }
    }

    return domain;
  }

  /**
   * Generate the curl command to access the sample app from the ingress controller.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace the namespace in which the domain exists
   * @param clusterName WebLogic cluster name which is the backend of the ingress
   * @param appContextRoot the context root of the application
   * @return curl command string
   */
  private static String generateCurlCmd(String domainUid, String domainNamespace, String clusterName,
                                        String appContextRoot) {
    return String.format("curl -v --show-error --noproxy '*' -H 'host: %s' http://%s:%s/%s/index.jsp",
        domainUid + "." + domainNamespace + "." + clusterName + ".test",
        K8S_NODEPORT_HOST, nodeportshttp, appContextRoot);
  }

  /**
   * Generate a server list which contains all managed servers in the cluster before scale.
   *
   * @param numClusters         number of clusters in the domain
   * @param clusterName         the name of the WebLogic cluster
   * @param replicasBeforeScale the replicas of WebLogic cluster before scale
   * @return list of managed servers in the cluster before scale
   */
  private static List<String> listManagedServersBeforeScale(int numClusters, String clusterName,
                                                            int replicasBeforeScale) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      if (numClusters <= 1) {
        managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
      } else {
        managedServerNames.add(clusterName + "-" + MANAGED_SERVER_NAME_BASE + i);
      }
    }

    return managedServerNames;
  }

  /**
   * Assert the specified domain and domain spec, metadata and clusters not null.
   * @param domain oracle.weblogic.domain.Domain object
   */
  private static void assertDomainNotNull(Domain domain) {
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), domain + " spec is null");
    assertNotNull(domain.getMetadata(), domain + " metadata is null");
    assertNotNull(domain.getSpec().getClusters(), domain.getSpec() + " getClusters() is null");
  }

  /**
   * Create a script to kill server.
   * @return a File object
   * @throws IOException if can not create a file
   */
  private File createScriptToKillServer() throws IOException {
    File killServerScript = File.createTempFile("killserver", ".sh");
    //deletes the file when VM terminates
    killServerScript.deleteOnExit();
    try (FileWriter fw = new FileWriter(killServerScript)) {
      fw.write("#!/bin/bash\n");
      fw.write("jps\n");
      fw.write("jps | grep Server\n");
      fw.write("jps | grep Server | awk '{print $1}'\n");
      fw.write("kill -9 `jps | grep Server | awk '{print $1}'`");
    }
    killServerScript.setExecutable(true, false);
    return killServerScript;
  }

  /**
   * Create mii image and push it to the registry.
   *
   * @return mii image created
   */
  private static String createAndPushMiiImage() {
    // create image with model files
    logger.info("Creating image with model file {0} and verify", wdtModelFileForMiiDomain);
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);
    miiImage =
        createMiiImageAndVerify(miiImageName, Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForMiiDomain),
            appSrcDirList, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    return miiImage;
  }

  private static Domain createOrStartDomainBasedOnDomainType() {
    Domain domain;

    if (!doesDomainExist(miiDomainUid, DOMAIN_VERSION, miiDomainNamespace)) {

      // create mii domain
      domain = createMiiDomainWithMultiClusters(miiDomainNamespace);

      // create route for OKD or ingress for the domain
      createRouteForOKDOrIngressForDomain(domain);
    } else {
      domain = assertDoesNotThrow(() -> getDomainCustomResource(miiDomainUid, miiDomainNamespace));
      startDomainAndVerify(miiDomainNamespace, miiDomainUid, replicaCount, domain.getSpec().getClusters().size());
    }

    assertDomainNotNull(domain);

    return domain;
  }

  private static void createRouteForOKDOrIngressForDomain(Domain domain) {

    assertDomainNotNull(domain);
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();

    //create route for external admin service
    createRouteForOKD(domainUid + "-admin-server-ext", domainNamespace);

    // create ingress using host based routing
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    int numClusters = domain.getSpec().getClusters().size();
    for (int i = 1; i <= numClusters; i++) {
      clusterNameMsPortMap.put(CLUSTER_NAME_PREFIX + i, MANAGED_SERVER_PORT);
    }

    if (OKD) {
      for (int i = 1; i <= numClusters; i++) {
        createRouteForOKD(domainUid + "-cluster-cluster-" + i, domainNamespace);
      }
    } else {
      logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
      createIngressForDomainAndVerify(domainUid, domainNamespace, nodeportshttp, clusterNameMsPortMap,
          true, nginxHelmParams.getIngressClassName(), true, ADMIN_SERVER_PORT);
    }
  }

  /**
   * Start domain and verify all the server pods were started.
   *
   * @param domainNamespace the namespace where the domain exists
   * @param domainUid the uid of the domain to shutdown
   * @param replicaCount replica count of the domain cluster
   * @param numClusters number of clusters in the domain
   */
  private static void startDomainAndVerify(String domainNamespace,
                                           String domainUid,
                                           int replicaCount,
                                           int numClusters) {
    // start domain
    getLogger().info("Starting domain {0} in namespace {1}", domainUid, domainNamespace);
    startDomain(domainUid, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    getLogger().info("Checking that admin service/pod {0} exists in namespace {1}",
        domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainNamespace);
    checkPodReadyAndServiceExists(domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainUid, domainNamespace);

    String managedServerPodName;
    if (numClusters > 1) {
      for (int i = 1; i <= numClusters; i++) {
        for (int j = 1; j <= replicaCount; j++) {
          managedServerPodName = domainUid + "-cluster-" + i + "-" + MANAGED_SERVER_NAME_BASE + j;
          // check that ms service/pod exists in the domain namespace
          getLogger().info("Checking that clustered ms service/pod {0} exists in namespace {1}",
              managedServerPodName, domainNamespace);
          checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
        }
      }
    } else {
      for (int i = 1; i <= replicaCount; i++) {
        managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;

        // check that ms service/pod exists in the domain namespace
        getLogger().info("Checking that clustered ms service/pod {0} exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }
  }

  private Callable<Boolean> checkSampleAppReady(String domainUid, String domainNamespace, String serverName) {
    return () -> {
      String curlCmd = String.format("curl http://%s:%s/sample-war/index.jsp", serverName, MANAGED_SERVER_PORT);
      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      ExecResult execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, adminServerPodName, null,
          true, "/bin/sh", "-c", curlCmd),
          String.format("Failed to execute curl command %s from pod %s in namespace %s", curlCmd,
              adminServerPodName, domainNamespace));
      return execResult.stdout().contains(serverName.substring(domainUid.length() + 1));
    };
  }
}

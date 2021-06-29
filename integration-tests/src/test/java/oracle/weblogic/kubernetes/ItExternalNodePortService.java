// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOSTNAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileFromPod;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * The use case verifies external RMI client access to WebLogic cluster.
 * The external RMI client access resources (JMS/EJB) using the NodePort 
 * service instead of LoadBalancer tunneling using the approach as described 
 * in following  WebLogic Kubernetes operator faq page
 * https://oracle.github.io/weblogic-kubernetes-operator/faq/external-clients/
 * In a WebLogic domain, configure a custom channel for the T3 protocol that
 * enables HTTP tunneling, and specifies an external address and port that
 * correspond to the address and port remote clients will use to access the
 * WebLogic cluster resources. Configure a WebLogic dynamic cluster domain using
 * Model In Image. Add a cluster targeted JMS distributed destination.
 * Configure a NodePort Sevice that redirects HTTP traffic to custom channel. 
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test external RMI access through NodePort tunneling")
@IntegrationTest
class ItExternalNodePortService {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static int replicaCount = 2;
  private static int nextFreePort = -1;
  private static String clusterName = "cluster-1";
  private final String adminServerPodName = domainUid + "-admin-server";
  private final String managedServerPrefix = domainUid + "-managed-server";
  private static final String TUNNELING_MODEL_FILE = "nodeport.tunneling.model.yaml";
  private static final String domainUid = "mii-nodeport-tunneling";
  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * Create domain resource.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    logger.info("K8S_NODEPORT_HOSTNAME {0} K8S_NODEPORT_HOST {1}", K8S_NODEPORT_HOSTNAME, K8S_NODEPORT_HOST);
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a new unique opNamespace
    logger.info("Assigning unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Assigning unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
            "weblogicenc", "weblogicenc");

    // Prepare the config map sparse model file from the template by replacing
    // Public Address of the custom channel with K8S_NODEPORT_HOST
    nextFreePort = getNextFreePort();
    Map<String, String> configTemplateMap  = new HashMap();
    configTemplateMap.put("INGRESS_HOST", K8S_NODEPORT_HOST);
    configTemplateMap.put("FREE_PORT", String.valueOf(nextFreePort));

    Path srcFile = Paths.get(RESOURCE_DIR,
        "wdt-models", "nodeport.tunneling.model.template.yaml");
    Path targetFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcFile.toString(),
        TUNNELING_MODEL_FILE, configTemplateMap));
    logger.info("Generated tunneling ConfigMap model file {0}", targetFile);

    String configMapName = "jms-nodeport-tunneling-configmap";
    List<Path> configMapFiles = new ArrayList<>();
    configMapFiles.add(Paths.get(RESULTS_ROOT, TUNNELING_MODEL_FILE));

    createConfigMapFromFiles(configMapName, configMapFiles, domainNamespace);

    // create the domain CR with a pre-defined configmap
    createDomainResource(domainUid, domainNamespace, adminSecretName,
        OCIR_SECRET_NAME, encryptionSecretName, replicaCount, configMapName);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, DOMAIN_VERSION, domainNamespace));
  }

  /**
   * Verify all server pods are running.
   * Verify all k8s services for all servers are created.
   */
  @BeforeEach
  public void beforeEach() {
    logger.info("Check admin service and pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server services and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server services and pods are created in namespace {0}",
          domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * The external JMS client sends 300 messages to a Uniform Distributed
   * Queue using Nodeport service http url which maps to custom channel on
   * cluster member server on WebLogic cluster. The test also make sure that
   * each member destination gets an equal number of messages.
   * The test is skipped for slim images, beacuse wlthint3client.jar is not 
   * available to download to build the external rmi JMS Client. 
   */
  @Test
  @DisplayName("Verify RMI access to WLS through NodePort Service")
  public void testExternalRmiAccessThruNodePortService() {

    assumeFalse(WEBLOGIC_SLIM, "Skipping RMI Tunnelling Test for slim image");
    // Build the standalone JMS Client to send and receive messages
    buildClient();
    buildClientOnPod();

    // Prepare the Nodeport service yaml file from the template file by 
    // replacing domain namespace, domain UID, cluster name and host name 
    Map<String, String> templateMap  = new HashMap();
    templateMap.put("DOMAIN_NS", domainNamespace);
    templateMap.put("DOMAIN_UID", domainUid);
    templateMap.put("CLUSTER", clusterName);
    templateMap.put("INGRESS_HOST", K8S_NODEPORT_HOST);
    templateMap.put("FREE_PORT", String.valueOf(nextFreePort));

    Path srcTunnelingFile = Paths.get(RESOURCE_DIR,
        "tunneling", "nodeport.tunneling.template.yaml");
    Path targetTunnelingFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcTunnelingFile.toString(),
        "nodeport.tunneling.yaml", templateMap));
    logger.info("Generated NodePort Tunneling file {0}", targetTunnelingFile);

    StringBuffer deployNodePort = new StringBuffer("kubectl apply -f ");
    deployNodePort.append(Paths.get(RESULTS_ROOT, "nodeport.tunneling.yaml"));
    // Deploy the NodePort Service
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(deployNodePort), true));

    // Unlike Voyager There is no such service to check for tunneling
    logger.info("kubectl apply returned {0}", result.toString());
    String serviceName = domainUid + "-cluster-" + clusterName + "-ext";
    String portName = "clustert3channel";
    checkServiceExists(serviceName, domainNamespace);
    int httpTunnelingPort =
        getServiceNodePort(domainNamespace, serviceName, portName);
    assertTrue(httpTunnelingPort != -1,
        "Could not get the Http TunnelingPort service node port");
    logger.info("HttpTunnelingPort for NodePort Service {0}", httpTunnelingPort);

    // Make sure the JMS Connection LoadBalancing and message LoadBalancing
    // works from RMI client outside of k8s cluster 
    runExtClient(httpTunnelingPort, 2, false);
    logger.info("External RMI tunneling works for NodePortService");
  }

  // Run the RMI client outside the K8s Cluster
  private void runExtClient(int httpTunnelingPort, int serverCount, boolean checkConnection) {
    // Generate java command to execute client with classpath
    StringBuffer httpUrl = new StringBuffer("http://");
    httpUrl.append(K8S_NODEPORT_HOST + ":" + httpTunnelingPort);
    StringBuffer javaCmd = new StringBuffer("java -cp ");
    javaCmd.append(Paths.get(RESULTS_ROOT, "wlthint3client.jar"));
    javaCmd.append(":");
    javaCmd.append(Paths.get(RESULTS_ROOT));
    javaCmd.append(" JmsTestClient ");
    javaCmd.append(httpUrl);
    javaCmd.append(" ");
    javaCmd.append(String.valueOf(serverCount));
    javaCmd.append(" ");
    javaCmd.append(String.valueOf(checkConnection));
    logger.info("java command to be run {0}", javaCmd.toString());

    // Note it takes a couples of iterations before the client success
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Wait for Http JMS Client to access WLS "
                    + "(elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(runJmsClient(new String(javaCmd)));
  }

  // Download the wlthint3client.jar from Adminserver pod to local filesystem.
  // Use wlthint3client.jar in classpath to build and run the standalone
  // JMS client that sends messages to a Uniform Distributed Queue using
  // load balancer http(s) url which maps to custom channel on cluster member
  // server on WebLogic cluster.
  private void buildClient() {

    assertDoesNotThrow(() -> copyFileFromPod(domainNamespace,
             adminServerPodName, "weblogic-server",
             "/u01/oracle/wlserver/server/lib/wlthint3client.jar",
             Paths.get(RESULTS_ROOT, "wlthint3client.jar")));
    StringBuffer javacCmd = new StringBuffer("javac -cp ");
    javacCmd.append(Paths.get(RESULTS_ROOT, "wlthint3client.jar "));
    javacCmd.append(Paths.get(RESOURCE_DIR, "tunneling", "JmsTestClient.java"));
    javacCmd.append(Paths.get(" -d "));
    javacCmd.append(Paths.get(RESULTS_ROOT));
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertTrue(result.exitValue() == 0, "Client compilation fails");
  }

  // Build JMS Client inside the Admin Server Pod
  private void buildClientOnPod() {
    String destLocation = "/u01/oracle/JmsTestClient.java";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace,
             adminServerPodName, "weblogic-server",
             Paths.get(RESOURCE_DIR, "tunneling", "JmsTestClient.java"),
             Paths.get(destLocation)));

    String jarLocation = "/u01/oracle/wlserver/server/lib/wlthint3client.jar";
    StringBuffer javacCmd = new StringBuffer("kubectl exec -n ");
    javacCmd.append(domainNamespace);
    javacCmd.append(" -it ");
    javacCmd.append(adminServerPodName);
    javacCmd.append(" -- /bin/bash -c \"");
    javacCmd.append("javac -cp ");
    javacCmd.append(jarLocation);
    javacCmd.append(" JmsTestClient.java ");
    javacCmd.append(" \"");
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertTrue(result.exitValue() == 0, "Client compilation fails");
  }

  // Run external standalone JMS Client using wlthint3client.jar in classpath.
  // The client sends 300 messsage to a Uniform Distributed Queue.
  // Make sure that each destination get excatly 150 messages each.
  private static Callable<Boolean> runJmsClient(String javaCmd) {
    return (()  -> {
      ExecResult result = assertDoesNotThrow(() -> exec(new String(javaCmd), true));
      logger.info("java returned {0}", result.toString());
      logger.info("java returned EXIT value {0}", result.exitValue());
      return ((result.exitValue() == 0));
    });
  }

  @AfterAll
  public void tearDownAll() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false"))) {
      StringBuffer removeNodePort = new StringBuffer("kubectl delete -f ");
      removeNodePort.append(Paths.get(RESULTS_ROOT, "cluster.nodeport.svc.yaml"));
      assertDoesNotThrow(() -> exec(new String(removeNodePort), true));
    }
  }

  private static void createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName,
      int replicaCount, String configmapName) {
    // create the domain CR
    Domain domain = new Domain()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new V1ObjectMeta()
                    .name(domainUid)
                    .namespace(domNamespace))
            .spec(new DomainSpec()
                    .domainUid(domainUid)
                    .domainHomeSourceType("FromModel")
                    .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
                    .addImagePullSecretsItem(new V1LocalObjectReference()
                            .name(repoSecretName))
                    .webLogicCredentialsSecret(new V1SecretReference()
                            .name(adminSecretName)
                            .namespace(domNamespace))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("IF_NEEDED")
                    .serverPod(new ServerPod()
                            .addEnvItem(new V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                            .addEnvItem(new V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new AdminServer()
                            .serverStartState("RUNNING")
                            .adminService(new AdminService()
                                    .addChannelsItem(new Channel()
                                            .channelName("default")
                                            .nodePort(0))))
                    .addClustersItem(new Cluster()
                            .clusterName("cluster-1")
                            .replicas(replicaCount)
                            .serverStartState("RUNNING"))
                    .configuration(new Configuration()
                            .model(new Model()
                                    .domainType("WLS")
                                    .configMap(configmapName)
                                    .runtimeEncryptionSecret(encryptionSecretName))
                        .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
            domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
            String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
                    domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
                    + "for %s in namespace %s", domainUid, domNamespace));
  }

}

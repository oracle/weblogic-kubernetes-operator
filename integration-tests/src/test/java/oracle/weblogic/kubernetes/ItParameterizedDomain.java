// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
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
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.io.File.createTempFile;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readString;
import static java.nio.file.Paths.get;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_IMAGE_DOMAINHOME_BASE_DIR;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLDF_CLUSTER_ROLE_BINDING_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLDF_CLUSTER_ROLE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteClusterRole;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteClusterRoleBinding;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.getContainerRestartCount;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.copyFileToPod;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterRoleBindingExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.clusterRoleExists;
import static oracle.weblogic.kubernetes.utils.CommonPatchTestUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.FileUtils.doesFileExistInPod;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_CHANGED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_PROCESSING_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_STARTED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_TERMINATED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkPodEventLoggedOnce;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Verify scaling up and down the clusters in the domain with different domain types.
 * Also verify the sample application can be accessed via NGINX ingress controller.
 * Also verify the rolling restart behavior in a multi-cluster MII domain.
 */
@DisplayName("Verify scaling the clusters in the domain with different domain types, "
        + "rolling restart behavior in a multi-cluster MII domain and "
        + "the sample application can be accessed via NGINX ingress controller")
@IntegrationTest
class ItParameterizedDomain {

  // domain constants
  private static final int NUMBER_OF_CLUSTERS_MIIDOMAIN = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final String clusterName = "cluster-1";
  private static final int MANAGED_SERVER_PORT = 8001;
  private static final int ADMIN_SERVER_PORT = 7001;
  private static final int replicaCount = 2;
  private static final String SAMPLE_APP_CONTEXT_ROOT = "sample-war";
  private static final String WLDF_OPENSESSION_APP = "opensessionapp";
  private static final String WLDF_OPENSESSION_APP_CONTEXT_ROOT = "opensession";
  private static final String wlSecretName = "weblogic-credentials";
  private static final String DATA_HOME_OVERRIDE = "/u01/oracle/mydata";

  private static String opNamespace = null;
  private static String opServiceAccount = null;
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;
  private static int externalRestHttpsPort = 0;
  private static List<Domain> domains = new ArrayList<>();
  private static LoggingFacade logger = null;
  private static Domain miiDomain = null;
  private static Domain domainInImage = null;
  private static Domain domainOnPV = null;
  private static int t3ChannelPort = 0;
  private static String miiDomainNamespace = null;
  private static final String miiDomainUid = "miidomain";

  private String curlCmd = null;


  /**
   * Install operator and NGINX.
   * Create three different type of domains: model in image, domain in PV and domain in image.
   * Create ingress for each domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(5) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    String nginxNamespace = namespaces.get(1);

    // get unique namespaces for three different type of domains
    logger.info("Getting unique namespaces for three different type of domains");
    assertNotNull(namespaces.get(2));
    miiDomainNamespace = namespaces.get(2);
    assertNotNull(namespaces.get(3));
    String domainOnPVNamespace = namespaces.get(3);
    assertNotNull(namespaces.get(4));
    String domainInImageNamespace = namespaces.get(4);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    installAndVerifyOperator(opNamespace, opServiceAccount, true, 0,
        miiDomainNamespace, domainOnPVNamespace, domainInImageNamespace);

    externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, 0, 0);
    String nginxServiceName = nginxHelmParams.getReleaseName() + "-ingress-nginx-controller";
    logger.info("NGINX service name: {0}", nginxServiceName);
    nodeportshttp = getServiceNodePort(nginxNamespace, nginxServiceName, "http");
    logger.info("NGINX http node port: {0}", nodeportshttp);

    // create model in image domain with multiple clusters
    miiDomain = createMiiDomainWithMultiClusters(miiDomainUid, miiDomainNamespace);
    // create domain in image
    domainInImage = createAndVerifyDomainInImageUsingWdt(domainInImageNamespace);
    // create domain in pv
    domainOnPV = createDomainOnPvUsingWdt(domainOnPVNamespace);

    domains.add(miiDomain);
    domains.add(domainInImage);
    domains.add(domainOnPV);

    // create ingress for each domain
    for (Domain domain: domains) {
      assertDomainNotNull(domain);

      String domainUid = domain.getSpec().getDomainUid();
      String domainNamespace = domain.getMetadata().getNamespace();

      // create ingress using host based routing
      Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
      int numClusters = domain.getSpec().getClusters().size();
      for (int i = 1; i <= numClusters; i++) {
        clusterNameMsPortMap.put(CLUSTER_NAME_PREFIX + i, MANAGED_SERVER_PORT);
      }
      logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
      createIngressForDomainAndVerify(domainUid, domainNamespace, nodeportshttp, clusterNameMsPortMap, true,
          true, ADMIN_SERVER_PORT);
    }
  }

  /**
   * Scale the cluster by patching domain resource for three different type of domains.
   *
   * @param domain oracle.weblogic.domain.Domain object
   */
  @ParameterizedTest
  @DisplayName("scale cluster by patching domain resource with three different type of domains")
  @MethodSource("domainProvider")
  public void testParamsScaleClustersByPatchingDomainResource(Domain domain) {
    assertDomainNotNull(domain);

    // Verify scale cluster of the domain by patching domain resource
    logger.info("testScaleClustersByPatchingDomainResource with domain {0}", domain.getMetadata().getName());
    testScaleClustersByPatchingDomainResource(domain);
  }

  /**
   * Scale cluster using REST API for three different type of domains.
   *
   * @param domain oracle.weblogic.domain.Domain object
   */
  @ParameterizedTest
  @DisplayName("scale cluster using REST API for three different type of domains")
  @MethodSource("domainProvider")
  public void testParamsScaleClustersWithRestApi(Domain domain) {
    assertDomainNotNull(domain);

    // Verify scale cluster of the domain using REST API
    logger.info("testScaleClustersWithRestApi with domain {0}", domain.getMetadata().getName());
    testScaleClustersWithRestApi(domain);
  }

  /**
   * Scale cluster using WLDF policy for three different type of domains.
   *
   * @param domain oracle.weblogic.domain.Domain object
   */
  @ParameterizedTest
  @DisplayName("scale cluster using WLDF policy for three different type of domains")
  @MethodSource("domainProvider")
  public void testParamsScaleClustersWithWLDF(Domain domain) {
    assertDomainNotNull(domain);

    // Verify scale cluster of the domain with WLDF policy
    logger.info("testScaleClustersWithWLDF with domain {0}", domain.getMetadata().getName());
    testScaleClustersWithWLDF(domain);
  }

  /**
   * Verify admin console login using admin node port.
   *
   * @param domain oracle.weblogic.domain.Domain object
   */
  @ParameterizedTest
  @DisplayName("Test admin console login using admin node port")
  @MethodSource("domainProvider")
  public void testAdminConsoleLoginUsingAdminNodePort(Domain domain) {
    assumeFalse(WEBLOGIC_SLIM, "Skipping the Console Test for slim image");
    assertDomainNotNull(domain);
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    logger.info("Validating WebLogic admin server access by login to console");
    boolean loginSuccessful = assertDoesNotThrow(() ->
        adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        "Access to admin server node port failed");
    assertTrue(loginSuccessful, "Console login validation failed");
  }

  /**
   * Verify admin console login using ingress controller.
   *
   * @param domain oracle.weblogic.domain.Domain object
   */
  @ParameterizedTest
  @DisplayName("Test admin console login using ingress controller")
  @MethodSource("domainProvider")
  public void testAdminConsoleLoginUsingIngressController(Domain domain) {
    assumeFalse(WEBLOGIC_SLIM, "Skipping the Console Test for slim image");
    assertDomainNotNull(domain);
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();

    String curlCmd = "curl --silent --show-error --noproxy '*' -H 'host: "
        + domainUid + "." + domainNamespace + ".adminserver.test"
        + "' http://" + K8S_NODEPORT_HOST + ":" + nodeportshttp
        + "/console/login/LoginForm.jsp --write-out %{http_code} -o /dev/null";

    logger.info("Executing curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
    logger.info("WebLogic console on domain1 is accessible");
  }

  /**
   * Verify liveness probe by killing managed server process 3 times to kick pod container auto-restart.
   */
  @Test
  @DisplayName("Test liveness probe of pod")
  public void testLivenessProbe() {
    Domain domain = miiDomain;
    assertDomainNotNull(domain);
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();
    int numClusters = domain.getSpec().getClusters().size();
    String serverName;
    if (numClusters > 1) {
      serverName = domainUid + "-" + clusterName + "-" + MANAGED_SERVER_NAME_BASE + "1";
    } else {
      serverName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + "1";
    }

    // create file to kill server process
    File killServerScript = assertDoesNotThrow(() -> createScriptToKillServer(),
        "Failed to create script to kill server");
    logger.info("File/script created to kill server {0}", killServerScript);

    checkPodReady(serverName, domainUid, domainNamespace);

    // copy script to pod
    String destLocation = "/u01/killserver.sh";
    assertDoesNotThrow(() -> copyFileToPod(domainNamespace, serverName, "weblogic-server",
        killServerScript.toPath(), Paths.get(destLocation)),
        String.format("Failed to copy file %s to pod %s in namespace %s",
            killServerScript, serverName, domainNamespace));
    logger.info("File copied to Pod {0} in namespace {1}", serverName, domainNamespace);

    // get the restart count of the container in pod before liveness probe restarts
    final int beforeRestartCount =
        assertDoesNotThrow(() -> getContainerRestartCount(domainNamespace, null, serverName, null),
            String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
                serverName, domainNamespace));
    logger.info("Restart count before liveness probe {0}", beforeRestartCount);

    // change file permissions
    ExecResult execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, serverName, null,
        true, "/bin/sh", "-c", "chmod +x " + destLocation),
        String.format("Failed to change permissions for file %s in pod %s", destLocation, serverName));
    assertTrue(execResult.exitValue() == 0,
        String.format("Failed to change file %s permissions, stderr %s stdout %s", destLocation,
            execResult.stderr(), execResult.stdout()));
    logger.info("File permissions changed inside pod");

    /* First, kill the managed server process in the container three times to cause the node manager to
     * mark the server 'failed not restartable'. This in turn is detected by the liveness probe, which
     * initiates a container restart.
     */
    for (int i = 0; i < 3; i++) {
      execResult = assertDoesNotThrow(() -> execCommand(domainNamespace, serverName, null,
          true, "/bin/sh", "-c", destLocation + " " + serverName),
          String.format("Failed to execute script %s in pod %s namespace %s", destLocation,
              serverName, domainNamespace));
      logger.info("Command executed to kill server inside pod, exit value {0}, stdout {1}, stderr {2}",
          execResult.exitValue(), execResult.stdout(), execResult.stderr());

      try {
        Thread.sleep(2 * 1000);
      } catch (InterruptedException ie) {
        // ignore
      }
    }

    // check pod is ready
    checkPodReady(serverName, domainUid, domainNamespace);

    // get the restart count of the container in pod after liveness probe restarts
    int afterRestartCount = assertDoesNotThrow(() ->
            getContainerRestartCount(domainNamespace, null, serverName, null),
        String.format("Failed to get the restart count of the container from pod {0} in namespace {1}",
            serverName, domainNamespace));
    assertTrue(afterRestartCount - beforeRestartCount == 1,
        String.format("Liveness probe did not start the container in pod %s in namespace %s",
            serverName, domainNamespace));

    //access application in managed servers through NGINX load balancer
    logger.info("Accessing the sample app through NGINX load balancer");
    String curlCmd = generateCurlCmd(domainUid, domainNamespace, clusterName, SAMPLE_APP_CONTEXT_ROOT);
    List<String> managedServers = listManagedServersBeforeScale(numClusters, clusterName, replicaCount);
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServers, 20))
        .as("Verify NGINX can access the test web app from all managed servers in the domain")
        .withFailMessage("NGINX can not access the test web app from one or more of the managed servers")
        .isTrue();
  }

  /**
   * Verify dataHome override in a domain with domain in image type.
   * In this domain, set dataHome to /u01/oracle/mydata in domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should be overridden by dataHome
   * File store and JMS server are targeted to the WebLogic cluster cluster-1
   * see resource/wdt-models/wdt-singlecluster-multiapps-usingprop-wls.yaml
   */
  @Test
  @DisplayName("Test dataHome override in a domain with domain in image type")
  public void testDataHomeOverrideDomainInImage() {

    assertDomainNotNull(domainInImage);
    String domainUid = domainInImage.getSpec().getDomainUid();
    String domainNamespace = domainInImage.getMetadata().getNamespace();

    // check in admin server pod, there is no data file for JMS server created
    String dataFileToCheck = DATA_HOME_OVERRIDE + "/" + domainUid + "/FILESTORE-0000000.DAT";
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    assertFalse(assertDoesNotThrow(
        () -> doesFileExistInPod(domainNamespace, adminServerPodName, dataFileToCheck),
        String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
            dataFileToCheck, adminServerPodName, domainNamespace)),
        String.format("%s exists in pod %s in namespace %s, expects not exist",
            dataFileToCheck, adminServerPodName, domainNamespace));

    // check in admin server pod, the default admin server data file moved to DATA_HOME_OVERRIDE
    String defaultAdminDataFile = DATA_HOME_OVERRIDE + "/" + domainUid + "/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(domainNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, the custom data file for JMS and default managed server datafile are created
    // in DATA_HOME_OVERRIDE
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      String customDataFile = DATA_HOME_OVERRIDE + "/" + domainUid + "/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(domainNamespace, managedServerPodName, customDataFile);

      String defaultMSDataFile = DATA_HOME_OVERRIDE + "/" + domainUid + "/_WLS_MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(domainNamespace, managedServerPodName, defaultMSDataFile);
    }
  }

  /**
   * Verify dataHome override in a domain with model in image type.
   * In this domain, dataHome is not specified in the domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should not be overridden
   * by dataHome
   * File store and JMS server are targeted to the WebLogic admin server
   * see resource/wdt-models/model-multiclusterdomain-sampleapp-wls.yaml
   */
  @Test
  @DisplayName("Test dataHome override in a domain with model in image type")
  public void testDataHomeOverrideMiiDomain() {

    assertDomainNotNull(miiDomain);
    String domainUid = miiDomain.getSpec().getDomainUid();
    String domainNamespace = miiDomain.getMetadata().getNamespace();

    // check in admin server pod, there is a data file for JMS server created in /u01/oracle/customFileStore
    String dataFileToCheck = "/u01/oracle/customFileStore/FILESTORE-0000000.DAT";
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    waitForFileExistsInPod(domainNamespace, adminServerPodName, dataFileToCheck);

    // check in admin server pod, the default admin server data file is in default data store
    String defaultAdminDataFile =
        "/u01/domains/" + domainUid + "/servers/admin-server/data/store/default/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(domainNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, there is no custom data file for JMS is created
    for (int i = 1; i <= replicaCount; i++) {
      for (int j = 1; j <= NUMBER_OF_CLUSTERS_MIIDOMAIN; j++) {
        String managedServerPodName = domainUid + "-cluster-" + j + "-" + MANAGED_SERVER_NAME_BASE + i;
        String customDataFile = "/u01/oracle/customFileStore/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
        assertFalse(assertDoesNotThrow(() ->
                doesFileExistInPod(domainNamespace, managedServerPodName, customDataFile),
            String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
                customDataFile, managedServerPodName, domainNamespace)),
            String.format("found file %s in pod %s in namespace %s, expect not exist",
                customDataFile, managedServerPodName, domainNamespace));

        String defaultMSDataFile = "/u01/domains/" + domainUid + "/servers/cluster-" + j + "-managed-server" + i
            + "/data/store/default/_WLS_CLUSTER-" + j + "-MANAGED-SERVER" + i + "000000.DAT";
        waitForFileExistsInPod(domainNamespace, managedServerPodName, defaultMSDataFile);
      }
    }
  }

  /**
   * Verify dataHome override in a domain with domain on PV type.
   * In this domain, dataHome is set to empty string in the domain custom resource
   * The domain contains JMS and File Store configuration
   * File store directory is set to /u01/oracle/customFileStore in the model file which should not be overridden
   * by dataHome
   * File store and JMS server are targeted to the WebLogic admin server
   * see resource/wdt-models/domain-onpv-wdt-model.yaml
   */
  @Test
  @DisplayName("Test dataHome override in a domain with domain on PV type")
  public void testDataHomeOverrideDomainOnPV() {

    assertDomainNotNull(domainOnPV);
    String domainUid = domainOnPV.getSpec().getDomainUid();
    String domainNamespace = domainOnPV.getMetadata().getNamespace();

    // check in admin server pod, there is a data file for JMS server created in /u01/oracle/customFileStore
    String dataFileToCheck = "/u01/oracle/customFileStore/FILESTORE-0000000.DAT";
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    waitForFileExistsInPod(domainNamespace, adminServerPodName, dataFileToCheck);

    // check in admin server pod, the default admin server data file is in default data store
    String defaultAdminDataFile =
        "/u01/shared/domains/" + domainUid + "/servers/admin-server/data/store/default/_WLS_ADMIN-SERVER000000.DAT";
    waitForFileExistsInPod(domainNamespace, adminServerPodName, defaultAdminDataFile);

    // check in managed server pod, there is no custom data file for JMS is created
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      String customDataFile = "/u01/oracle/customFileStore/FILESTORE-0@MANAGED-SERVER" + i + "000000.DAT";
      assertFalse(assertDoesNotThrow(() ->
              doesFileExistInPod(domainNamespace, managedServerPodName, customDataFile),
          String.format("exception thrown when checking file %s exists in pod %s in namespace %s",
              customDataFile, managedServerPodName, domainNamespace)),
          String.format("found file %s in pod %s in namespace %s, expect not exist",
              customDataFile, managedServerPodName, domainNamespace));

      String defaultMSDataFile = "/u01/shared/domains/" + domainUid + "/servers/managed-server" + i
          + "/data/store/default/_WLS_MANAGED-SERVER" + i + "000000.DAT";
      waitForFileExistsInPod(domainNamespace, managedServerPodName, defaultMSDataFile);
    }
  }

  /**
   * Test rolling restart for a multi-clusters domain and make sure pods are restarted only once.
   * Verify all pods are terminated and restarted only once.
   * Rolling restart triggered by changing: imagePullPolicy: IfNotPresent --> imagePullPolicy: Never.
   */
  @Test
  @DisplayName("Verify server pods are restarted only once by changing the imagePullPolicy in multi-cluster domain")
  public void testRollingRestartBehaviorInMultiClusterMiiDomainByChangingImagePullPolicy() {
    DateTime timestamp = new DateTime(Instant.now().getEpochSecond() * 1000L);

    // get the original domain resource before update
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(miiDomainUid, miiDomainNamespace),
            String.format("getDomainCustomResource failed with ApiException when tried to get domain %s "
                    + "in namespace %s", miiDomainUid, miiDomainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

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

    ConditionFactory withStandardRetryPolicy
            = with().pollDelay(2, SECONDS)
            .and().with().pollInterval(10, SECONDS)
            .atMost(10, MINUTES).await();

    //verify domain changed event is logged
    withStandardRetryPolicy
            .conditionEvaluationListener(
                    condition -> logger.info("Waiting for domain event {0} to be logged "
                                    + "(elapsed time {1}ms, remaining time {2}ms)",
                            DOMAIN_CHANGED,
                            condition.getElapsedTimeInMS(),
                            condition.getRemainingTimeInMS()))
            .until(checkDomainEvent(opNamespace, miiDomainNamespace, miiDomainUid,
                    DOMAIN_CHANGED, "Normal", timestamp));

    // verify the DomainProcessing Starting/Completed event is generated
    withStandardRetryPolicy
            .conditionEvaluationListener(
                    condition -> logger.info("Waiting for domain event {0} to be logged "
                                    + "(elapsed time {1}ms, remaining time {2}ms)",
                            DOMAIN_PROCESSING_STARTING,
                            condition.getElapsedTimeInMS(),
                            condition.getRemainingTimeInMS()))
            .until(checkDomainEvent(opNamespace, miiDomainNamespace, miiDomainUid,
                    DOMAIN_PROCESSING_STARTING, "Normal", timestamp));

    withStandardRetryPolicy
            .conditionEvaluationListener(
                    condition -> logger.info("Waiting for domain event {0} to be logged "
                                    + "(elapsed time {1}ms, remaining time {2}ms)",
                            DOMAIN_PROCESSING_COMPLETED,
                            condition.getElapsedTimeInMS(),
                            condition.getRemainingTimeInMS()))
            .until(checkDomainEvent(opNamespace, miiDomainNamespace, miiDomainUid,
                    DOMAIN_PROCESSING_COMPLETED, "Normal", timestamp));

    // Verify that pod termination and started events are logged only once for each managed server in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
                miiDomainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        logger.info("Checking that managed server pod {0} is terminated and restarted once in namespace {1}",
                managedServerPodName, miiDomainNamespace);
        withStandardRetryPolicy
                .conditionEvaluationListener(
                        condition -> logger.info("Waiting for event {0} to be logged for pod {1} "
                                        + "(elapsed time {2}ms, remaining time {3}ms)",
                                POD_TERMINATED,
                                managedServerPodName,
                                condition.getElapsedTimeInMS(),
                                condition.getRemainingTimeInMS()))
                .until(checkPodEventLoggedOnce(miiDomainNamespace,
                        managedServerPodName, POD_TERMINATED, timestamp));
        withStandardRetryPolicy
                .conditionEvaluationListener(
                        condition -> logger.info("Waiting for event {0} to be logged for pod {1} "
                                        + "(elapsed time {2}ms, remaining time {3}ms)",
                                POD_STARTED,
                                managedServerPodName,
                                condition.getElapsedTimeInMS(),
                                condition.getRemainingTimeInMS()))
                .until(checkPodEventLoggedOnce(miiDomainNamespace,
                        managedServerPodName, POD_STARTED, timestamp));
      }
    }
  }

  /**
   * Generate a steam of Domain objects used in parameterized tests.
   * @return stream of oracle.weblogic.domain.Domain objects
   */
  private static Stream<Domain> domainProvider() {
    return domains.stream();
  }

  /**
   * Verify scale each cluster of the domain by patching domain resource.
   * @param domain oracle.weblogic.domain.Domain object
   */
  private void testScaleClustersByPatchingDomainResource(Domain domain) {
    assertDomainNotNull(domain);

    // get the domain properties
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();
    int numClusters = domain.getSpec().getClusters().size();

    for (int i = 1; i <= numClusters; i++) {
      String clusterName = CLUSTER_NAME_PREFIX + i;
      String managedServerPodNamePrefix = generateMsPodNamePrefix(numClusters, domainUid, clusterName);

      int numberOfServers;
      // scale cluster-1 to 1 server and cluster-2 to 3 servers
      if (i == 1) {
        numberOfServers = 1;
      } else {
        numberOfServers = 3;
      }

      logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
          clusterName, domainUid, domainNamespace, numberOfServers);
      curlCmd = generateCurlCmd(domainUid, domainNamespace, clusterName, SAMPLE_APP_CONTEXT_ROOT);
      List<String> managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, replicaCount);
      scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
          replicaCount, numberOfServers, curlCmd, managedServersBeforeScale);

      // then scale cluster back to 2 servers
      logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
          clusterName, domainUid, domainNamespace, numberOfServers, replicaCount);
      managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, numberOfServers);
      scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
          numberOfServers, replicaCount, curlCmd, managedServersBeforeScale);
    }
  }

  /**
   * Verify scale each cluster of the domain by calling REST API.
   * @param domain oracle.weblogic.domain.Domain object
   */
  private void testScaleClustersWithRestApi(Domain domain) {
    assertDomainNotNull(domain);

    // get domain properties
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();
    int numClusters = domain.getSpec().getClusters().size();
    String managedServerPodNamePrefix = generateMsPodNamePrefix(numClusters, domainUid, clusterName);
    int numberOfServers = 3;

    logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace, replicaCount, numberOfServers);
    curlCmd = generateCurlCmd(domainUid, domainNamespace, clusterName, SAMPLE_APP_CONTEXT_ROOT);
    List<String> managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, replicaCount);
    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        replicaCount, numberOfServers, true, externalRestHttpsPort, opNamespace, opServiceAccount,
        false, "", "", 0, "", "", curlCmd, managedServersBeforeScale);

    // then scale cluster back to 2 servers
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace, numberOfServers, replicaCount);
    managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, numberOfServers);
    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        numberOfServers, replicaCount, true, externalRestHttpsPort, opNamespace, opServiceAccount,
        false, "", "", 0, "", "", curlCmd, managedServersBeforeScale);
  }

  /**
   * Verify scale each cluster in the domain using WLDF policy.
   * @param domain oracle.weblogic.domain.Domain object
   */
  private void testScaleClustersWithWLDF(Domain domain) {
    assertDomainNotNull(domain);

    // get domain properties
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();
    String domainHome = domain.getSpec().getDomainHome();
    int numClusters = domain.getSpec().getClusters().size();
    String managedServerPodNamePrefix = generateMsPodNamePrefix(numClusters, domainUid, clusterName);

    curlCmd = generateCurlCmd(domainUid, domainNamespace, clusterName, SAMPLE_APP_CONTEXT_ROOT);

    // scale up the cluster by 1 server
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace, replicaCount, replicaCount + 1);
    List<String> managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, replicaCount);
    String curlCmdForWLDFScript =
        generateCurlCmd(domainUid, domainNamespace, clusterName, WLDF_OPENSESSION_APP_CONTEXT_ROOT);

    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        replicaCount, replicaCount + 1, false, 0, opNamespace, opServiceAccount,
        true, domainHome, "scaleUp", 1,
        WLDF_OPENSESSION_APP, curlCmdForWLDFScript, curlCmd, managedServersBeforeScale);

    // scale down the cluster by 1 server
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace, replicaCount + 1, replicaCount);
    managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, replicaCount + 1);

    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        replicaCount + 1, replicaCount, false, 0, opNamespace, opServiceAccount,
        true, domainHome, "scaleDown", 1,
        WLDF_OPENSESSION_APP, curlCmdForWLDFScript, curlCmd, managedServersBeforeScale);
  }

  /**
   * Uninstall NGINX release.
   * Delete cluster role and cluster role binding used for WLDF.
   */
  @AfterAll
  public void tearDownAll() {

    // uninstall NGINX release
    if (nginxHelmParams != null) {
      assertThat(uninstallNginx(nginxHelmParams))
          .as("Test uninstallNginx returns true")
          .withFailMessage("uninstallNginx() did not return true")
          .isTrue();
    }

    for (Domain domain : domains) {
      assertDomainNotNull(domain);

      String domainNamespace = domain.getMetadata().getNamespace();

      // delete cluster role binding created for WLDF policy
      if (assertDoesNotThrow(
          () -> clusterRoleBindingExists(domainNamespace + "-" + WLDF_CLUSTER_ROLE_BINDING_NAME))) {
        assertTrue(deleteClusterRoleBinding(domainNamespace + "-" + WLDF_CLUSTER_ROLE_BINDING_NAME));
      }
    }

    // delete cluster role created for WLDF policy
    if (assertDoesNotThrow(() -> clusterRoleExists(WLDF_CLUSTER_ROLE_NAME))) {
      assertThat(assertDoesNotThrow(() -> deleteClusterRole(WLDF_CLUSTER_ROLE_NAME),
          "deleteClusterRole failed with ApiException"))
          .as("Test delete cluster role returns true")
          .withFailMessage("deleteClusterRole() did not return true")
          .isTrue();
    }
  }

  /**
   * Create model in image domain with multiple clusters.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  private static Domain createMiiDomainWithMultiClusters(String domainUid, String domainNamespace) {

    final String miiImageName = "mii-image";
    final String wdtModelFileForMiiDomain = "model-multiclusterdomain-sampleapp-wls.yaml";

    // admin/managed server name here should match with WDT model yaml file
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // create image with model files
    logger.info("Creating image with model file {0} and verify", wdtModelFileForMiiDomain);
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);
    String miiImage =
        createMiiImageAndVerify(miiImageName, Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForMiiDomain),
            appSrcDirList, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

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
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/u01/domains/" + domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(OCIR_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .clusters(clusterList)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));
    setPodAntiAffinity(domain);
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check managed server pod is ready and service exists in the namespace
        logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }

    return domain;
  }

  /**
   * Create a domain in PV using WDT.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  private static Domain createDomainOnPvUsingWdt(String domainNamespace) {
    final String domainUid = "domainonpv" + "-" + domainNamespace.substring(3);
    final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

    t3ChannelPort = getNextFreePort(31111, 32767);  // the port range has to be between 30,000 to 32,767

    final String pvName = domainUid + "-pv"; // name of the persistent volume
    final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    Path pvHostPath =
        get(PV_ROOT, ItParameterizedDomain.class.getSimpleName(), pvcName);

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("5Gi"))
            .persistentVolumeReclaimPolicy("Recycle"))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
            .build()
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeName(pvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("5Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvcName)
            .withNamespace(domainNamespace)
            .build()
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));

    String labelSelector = String.format("weblogic.domainUid in (%s)", domainUid);
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector, domainNamespace,
        domainUid + "-weblogic-domain-storage-class", pvHostPath);

    // create a temporary WebLogic domain property file as a input for WDT model file
    File domainPropertiesFile = assertDoesNotThrow(() -> createTempFile("domainonpv", "properties"),
        "Failed to create domain properties file");

    Properties p = new Properties();
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainUid);
    p.setProperty("adminServerName", ADMIN_SERVER_NAME_BASE);
    p.setProperty("productionModeEnabled", "true");
    p.setProperty("clusterName", clusterName);
    p.setProperty("configuredManagedServerCount", "4");
    p.setProperty("managedServerNameBase", MANAGED_SERVER_NAME_BASE);
    p.setProperty("t3ChannelPort", Integer.toString(t3ChannelPort));
    p.setProperty("t3PublicAddress", K8S_NODEPORT_HOST);
    p.setProperty("managedServerPort", "8001");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
        "Failed to write domain properties file");

    // shell script to download WDT and run the WDT createDomain script
    Path wdtScript = get(RESOURCE_DIR, "bash-scripts", "wdt-create-domain-onpv.sh");
    // WDT model file containing WebLogic domain configuration
    Path wdtModelFile = get(RESOURCE_DIR, "wdt-models", "domain-onpv-wdt-model.yaml");

    // create configmap and domain in persistent volume using WDT
    runCreateDomainOnPVJobUsingWdt(wdtScript, wdtModelFile, domainPropertiesFile.toPath(),
        domainUid, pvName, pvcName, domainNamespace);

    // create the domain custom resource
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/u01/shared/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET)))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/u01/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
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
                    .mountPath("/u01/shared")
                    .name(pvName)))
            .adminServer(new AdminServer()
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
    createDomainAndVerify(domain, domainNamespace);

    // verify admin server pod is ready and service exists
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify managed server pods are ready and services exist
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready and service existing in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    // build application sample-app and opensessionapp
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);

    for (String appName : appSrcDirList) {
      assertTrue(buildAppArchive(defaultAppParams()
              .srcDirList(Collections.singletonList(appName))
              .appName(appName)),
          String.format("Failed to create app archive for %s", appName));

      logger.info("Getting port for default channel");
      int defaultChannelPort = assertDoesNotThrow(()
          -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
          "Getting admin server default port failed");
      logger.info("default channel port: {0}", defaultChannelPort);
      assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

      //deploy application
      Path archivePath = get(ARCHIVE_DIR, "wlsdeploy", "applications", appName + ".ear");
      logger.info("Deploying webapp {0} to domain {1}", archivePath, domainUid);
      deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelPort),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, clusterName + "," + ADMIN_SERVER_NAME_BASE, archivePath,
          domainNamespace);
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
   * Create a WebLogic domain in a persistent volume by doing the following.
   * Create a configmap containing WDT model file, property file and shell script to download and run WDT.
   * Create a Kubernetes job to create domain on persistent volume.
   *
   * @param domainCreationScriptFile path of the shell script to download and run WDT
   * @param modelFile path of the WDT model file
   * @param domainPropertiesFile property file holding properties referenced in WDT model file
   * @param domainUid unique id of the WebLogic domain
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   */
  private static void runCreateDomainOnPVJobUsingWdt(Path domainCreationScriptFile,
                                                     Path modelFile,
                                                     Path domainPropertiesFile,
                                                     String domainUid,
                                                     String pvName,
                                                     String pvcName,
                                                     String namespace) {
    logger.info("Preparing to run create domain job using WDT");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(domainCreationScriptFile);
    domainScriptFiles.add(domainPropertiesFile);
    domainScriptFiles.add(modelFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles, namespace),
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
            .value("/u01/shared/domains/" + domainUid));

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(pvName, pvcName, domainScriptConfigMapName, namespace, jobCreationContainer);
  }

  /**
   * Create ConfigMap containing domain creation scripts.
   *
   * @param configMapName name of the ConfigMap to create
   * @param files files to add in ConfigMap
   * @param namespace name of the namespace in which to create ConfigMap
   * @throws IOException when reading the domain script files fail
   */
  private static void createConfigMapForDomainCreation(String configMapName, List<Path> files, String namespace)
      throws IOException {
    logger.info("Creating ConfigMap {0}", configMapName);

    Path domainScriptsDir = createDirectories(
        get(TestConstants.LOGS_DIR, ItParameterizedDomain.class.getSimpleName(), namespace));

    // add domain creation scripts and properties files to the configmap
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      logger.info("Adding file {0} in ConfigMap", file);
      data.put(file.getFileName().toString(), readString(file));
      logger.info("Making a copy of file {0} to {1} for diagnostic purposes", file,
          domainScriptsDir.resolve(file.getFileName()));
      copy(file, domainScriptsDir.resolve(file.getFileName()));
    }
    V1ObjectMeta meta = new V1ObjectMeta()
        .name(configMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Failed to create ConfigMap %s with files %s", configMapName, files));
    assertTrue(cmCreated, String.format("Failed while creating ConfigMap %s", configMapName));
  }

  /**
   * Create a job to create a domain in persistent volume.
   *
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param domainScriptCM ConfigMap holding domain creation script files
   * @param namespace name of the domain namespace in which the job is created
   * @param jobContainer V1Container with job commands to create domain
   */
  private static void createDomainJob(String pvName,
                                      String pvcName,
                                      String domainScriptCM,
                                      String namespace,
                                      V1Container jobContainer) {
    logger.info("Running Kubernetes job to create domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job-" + pvName) // name of the create domain job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .addInitContainersItem(createfixPVCOwnerContainer(pvName, "/u01/shared"))
                    .addContainersItem(jobContainer  // container containing WLST or WDT details
                        .name("create-weblogic-domain-onpv-container")
                        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                        .imagePullPolicy("IfNotPresent")
                        .addPortsItem(new V1ContainerPort()
                            .containerPort(7001))
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                                .mountPath("/u01/weblogic"), // availble under /u01/weblogic inside pod
                            new V1VolumeMount()
                                .name(pvName) // location to write domain
                                .mountPath("/u01/shared")))) // mounted under /u01/shared inside pod
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName)),
                        new V1Volume()
                            .name("create-weblogic-domain-job-cm-volume")
                            .configMap(
                                new V1ConfigMapVolumeSource()
                                    .name(domainScriptCM)))) //config map containing domain scripts
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET))))));  // this secret is used only for non-kind cluster

    String jobName = createJobAndWaitUntilComplete(jobBody, namespace);

    // check job status and fail test if the job failed to create domain
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to create domain", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty()) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          logger.severe(podLog);
          fail("Domain create job failed");
        }
      }
    }
  }

  /**
   * Create a WebLogic domain in image using WDT.
   *
   * @param domainNamespace namespace in which the domain to be created
   * @return oracle.weblogic.domain.Domain object
   */
  private static Domain createAndVerifyDomainInImageUsingWdt(String domainNamespace) {

    String domainUid = "domaininimage";
    String wdtModelFileForDomainInImage = "wdt-singlecluster-multiapps-usingprop-wls.yaml";

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create image with model files
    logger.info("Creating image with model file and verify");
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);

    String domainInImageWithWDTImage = createImageAndVerify("domaininimage-wdtimage",
        Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForDomainInImage), appSrcDirList,
        Collections.singletonList(MODEL_DIR + "/" + WDT_BASIC_MODEL_PROPERTIES_FILE),
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false,
        domainUid, false);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domainInImageWithWDTImage);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create the domain custom resource
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(WDT_IMAGE_DOMAINHOME_BASE_DIR + "/" + domainUid)
            .dataHome("/u01/oracle/mydata")
            .domainHomeSourceType("Image")
            .image(domainInImageWithWDTImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(OCIR_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .resources(new V1ResourceRequirements()
                    .limits(new HashMap<>())
                    .requests(new HashMap<>())))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod ready and service exists in the domain namespace
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Check for admin server pod {0} ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready and service exists in the domain namespace
    String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready and services exist in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    return domain;
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
   * Generate the managed server pod name prefix.
   *
   * @param numClusters number of clusters in the domain
   * @param domainUid   uid of the domain
   * @param clusterName the cluster name of the domain
   * @return prefix of managed server pod name
   */
  private String generateMsPodNamePrefix(int numClusters, String domainUid, String clusterName) {
    String managedServerPodNamePrefix;
    if (numClusters <= 1) {
      managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    } else {
      managedServerPodNamePrefix = domainUid + "-" + clusterName + "-" + MANAGED_SERVER_NAME_BASE;
    }

    return managedServerPodNamePrefix;
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
      fw.write("kill -9 `jps | grep Server | awk '{print $1}'`");
    }
    killServerScript.setExecutable(true, false);
    return killServerScript;
  }

  /**
   * Check whether a file exists in a pod in the given namespace.
   *
   * @param namespace the Kubernetes namespace that the pod is in
   * @param podName the name of the Kubernetes pod in which the command is expected to run
   * @param fileName the filename to check
   * @return true if the file exists, otherwise return false
   */
  private Callable<Boolean> fileExistsInPod(String namespace, String podName, String fileName) {
    return () -> {
      return doesFileExistInPod(namespace, podName, fileName);
    };
  }

  /**
   * Wait for file existing in the pod in the given namespace up to 1 minute.
   * @param namespace the Kubernetes namespace that the pod is in
   * @param podName the name of the Kubernetes pod in which the command is expected to run
   * @param fileName the filename to check
   */
  private void waitForFileExistsInPod(String namespace, String podName, String fileName) {

    ConditionFactory withStandardRetryPolicy =
        with().pollDelay(1, SECONDS)
            .and().with().pollInterval(5, SECONDS)
            .atMost(1, MINUTES).await();

    logger.info("Wait for file {0} existing in pod {1} in namespace {2}", fileName, podName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for file {0} existing in pod {1} in namespace {2} "
                    + "(elapsed time {3}ms, remaining time {4}ms)",
                fileName,
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> fileExistsInPod(namespace, podName, fileName),
            "fileExistsInPod failed with IOException, ApiException or InterruptedException"));
  }
}

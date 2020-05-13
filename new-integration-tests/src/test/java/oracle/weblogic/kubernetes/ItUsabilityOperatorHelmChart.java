// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.domain.Cluster;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_SERVICE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.CommonUtils.checkPodCreated;
import static oracle.weblogic.kubernetes.utils.CommonUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.CommonUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonUtils.checkServiceCreated;
import static oracle.weblogic.kubernetes.utils.CommonUtils.checkServiceDeleted;
import static oracle.weblogic.kubernetes.utils.CommonUtils.createMiiDomain;
import static oracle.weblogic.kubernetes.utils.CommonUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Simple JUnit test file used for testing operator usability.
 * Use Helm chart to install operator(s)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test operator usability using Helm chart installation")
@IntegrationTest
class ItUsabilityOperatorHelmChart implements LoggedTest {

  // mii constants
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String MII_IMAGE_NAME = "mii-image";
  private static final String APP_NAME = "sample-app";

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp = 0;

  // domain constants
  private final String domainUid = "domain1";
  private final String clusterName = "cluster-1";
  private final int managedServerPort = 8001;
  private final int replicaCount = 2;
  private final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

  /**
   * Get namespaces for operator, domain1 and NGINX.
   * Install and verify NGINX.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {

    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // get a unique NGINX namespace
    logger.info("Getting a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    String nginxNamespace = namespaces.get(2);

    // get a free node port for NGINX
    nodeportshttp = getNextFreePort(30305, 30405);
    int nodeportshttps = getNextFreePort(30443, 30543);

    // install and verify NGINX
    logger.info("Installing and verifying NGINX");
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps);
  }

  /**
   * Create operator and verify it is deployed successfully.
   * Create domain1 and verify all server pods in domain1 were created and ready.
   * Verify NGINX can access the sample app from all managed servers in domain1
   * Delete operator.
   * Verify the states of all server pods in domain1 were not changed.
   * Verify NGINX can access the sample app from all managed servers in domain1.
   * Test fails if the state of any pod in domain1 was changed or NGINX can not access the sample app from
   * all managed servers in the absence of operator.
   */
  @Test
  @Order(1)
  @DisplayName("Create operator and domain, then delete operator and verify the domain is still running")
  @Slow
  @MustNotRunInParallel
  public void testDeleteOperatorButNotDomain() {
    // install and verify operator
    logger.info("Installing and verifying operator");
    HelmParams opHelmParams = installAndVerifyOperator(opNamespace, domainNamespace);

    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    createAndVerifyMiiDomain();

    // get the admin server pod original creation timestamp
    logger.info("Getting admin server pod original creation timestamp");
    String adminPodOriginalTimestamp =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                adminServerPodName, domainNamespace));

    // get the managed server pod original creation timestamp
    logger.info("Getting managed server pods original creation timestamp");
    List<String> managedServerPodOriginalTimestampList = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      final String managedServerPodName = managedServerPrefix + i;
      managedServerPodOriginalTimestampList.add(
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace)));
    }

    // create ingress for the domain
    logger.info("Creating an ingress for the domain");
    createIngressForDomain();

    // verify the sample apps for the domain
    logger.info("Checking that the sample app can be accessed from all managed servers through NGINX");
    verifySampleAppAccessThroughNginx();

    // delete operator
    logger.info("Uninstalling operator");
    uninstallOperator(opHelmParams);

    // verify the operator pod was deleted
    logger.info("Checking that operator pod was deleted");
    checkPodDeleted("weblogic-operator-", null, opNamespace);

    // verify the operator service was deleted
    logger.info("Checking that operator service was deleted");
    checkServiceDeleted(OPERATOR_SERVICE_NAME, opNamespace);

    // check that the state of admin server pod in the domain was not changed
    logger.info("Checking that the admin server pod state was not changed after the operator was deleted");
    assertThat(podStateNotChanged(adminServerPodName, domainUid, domainNamespace, adminPodOriginalTimestamp))
        .as("Test state of pod {0} was not changed in namespace {1}", adminServerPodName, domainNamespace)
        .withFailMessage("State of pod {0} was changed in namespace {1}",
            adminServerPodName, domainNamespace)
        .isTrue();

    // check that the states of managed server pods in the domain were not changed
    logger.info("Checking that the managed server pod state was not changed after the operator was deleted");
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      assertThat(podStateNotChanged(managedServerPodName, domainUid, domainNamespace,
              managedServerPodOriginalTimestampList.get(i - 1)))
          .as("Test state of pod {0} was not changed in namespace {1}", managedServerPodName, domainNamespace)
          .withFailMessage("State of pod {0} was changed in namespace {1}",
              managedServerPodName, domainNamespace)
          .isTrue();
    }

    // verify the sample app in the domain is still accessible from all managed servers through NGINX
    logger.info("Checking that the sample app can be accessed from all managed servers through NGINX "
        + "after the operator was deleted");
    verifySampleAppAccessThroughNginx();
  }

  /**
   * TODO: remove this after Sankar's PR is merged
   * The cleanup framework does not uninstall NGINX release. Do it here for now.
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
  }

  /**
   * Create a model in image domain and verify the domain pods are ready.
   */
  private void createAndVerifyMiiDomain() {

    // create image with model files
    logger.info("Creating a docker image with model file and verify");
    String miiImage = createImageAndVerify();

    // construct a list of oracle.weblogic.domain.Cluster objects to be used in the domain custom resource
    List<Cluster> clusters = new ArrayList<>();
    clusters.add(new Cluster()
        .clusterName(clusterName)
        .replicas(replicaCount)
        .serverStartState("RUNNING"));

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createMiiDomain(miiImage, domainUid, domainNamespace, clusters, WLS_DOMAIN_TYPE);

    // check that admin server pod was created in the domain
    logger.info("Checking that admin server pod {0} was created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodCreated(adminServerPodName, domainUid, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check that admin service was created in the domain
    logger.info("Checking that admin service {0} was created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceCreated(adminServerPodName, domainNamespace);

    // check for managed server pods existence in the domain
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod was created
      logger.info("Checking that managed server pod {0} was created in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodCreated(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server service was created
      logger.info("Checking that managed server service {0} was created in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceCreated(managedServerPodName, domainNamespace);
    }
  }

  /**
   * Create an ingress for the domain.
   */
  private void createIngressForDomain() {

    // create an ingress in domain namespace
    String ingressName = domainUid + "-nginx";
    assertThat(assertDoesNotThrow(() ->
        createIngress(ingressName, domainNamespace, domainUid, clusterName, managedServerPort,
            domainUid + ".test")))
            .as("Test ingress {0} creation succeeds", ingressName)
            .withFailMessage("Ingress creation failed for cluster {0} of domain {1} in namespace {2}",
                clusterName, domainUid, domainNamespace)
            .isTrue();

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as("Test ingress {0} was found in namespace {1}", ingressName, domainNamespace)
        .withFailMessage("Ingress {0} was not found in namespace {1}", ingressName, domainNamespace)
        .contains(ingressName);

    logger.info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);
  }

  /**
   * Verify the sample app can be accessed from all managed servers in the domain through NGINX.
   */
  private void verifySampleAppAccessThroughNginx() {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String curlCmd =
        String.format("curl --silent --noproxy '*' -H 'host: %s' http://%s:%s/sample-war/index.jsp",
            domainUid + ".test", K8S_NODEPORT_HOST, nodeportshttp);
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 50))
        .as("Verify NGINX can access the sample app from all managed servers in the domain")
        .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
        .isTrue();
  }

  /**
   * Create a Docker image for model in image domain.
   *
   * @return image name with tag
   */
  private String createImageAndVerify() {

    // create unique image name with timestamp
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    final String imageTag = dateFormat.format(date) + "-" + System.currentTimeMillis();
    // Add repository name in image name for Jenkins runs
    final String imageName = REPO_NAME + MII_IMAGE_NAME;
    final String image = imageName + ":" + imageTag;

    // build the model file list
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + WDT_MODEL_FILE);

    // build an application archive using what is in resources/apps/APP_NAME
    assertTrue(buildAppArchive(defaultAppParams()
        .srcDir(APP_NAME)), String.format("Failed to create app archive for %s", APP_NAME));

    // build the archive list
    String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, APP_NAME);
    final List<String> archiveList = Collections.singletonList(zipFile);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    // For k8s 1.16 support and as of May 6, 2020, we presently need a different JDK for these
    // tests and for image tool. This is expected to no longer be necessary once JDK 11.0.8 or
    // the next JDK 14 versions are released.
    String witJavaHome = System.getenv("WIT_JAVA_HOME");
    if (witJavaHome != null) {
      env.put("JAVA_HOME", witJavaHome);
    }

    // build an image using WebLogic Image Tool
    logger.info("Create image {0} using model directory {1}", image, MODEL_DIR);
    boolean result = createMiiImage(
        defaultWitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtVersion(WDT_VERSION)
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create the image %s using WebLogic Image Tool", image));

    // Check image exists using docker images | grep image tag.
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s does not exist", image));

    return image;
  }
}

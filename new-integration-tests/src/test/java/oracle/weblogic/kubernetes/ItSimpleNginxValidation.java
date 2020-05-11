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
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createMiiImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getIngressList;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonUtils.checkPodCreated;
import static oracle.weblogic.kubernetes.utils.CommonUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonUtils.checkServiceCreated;
import static oracle.weblogic.kubernetes.utils.CommonUtils.createMiiDomain;
import static oracle.weblogic.kubernetes.utils.CommonUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the sample application can be accessed via the ingress controller.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the sample application can be accessed via the ingress controller")
@IntegrationTest
class ItSimpleNginxValidation implements LoggedTest {

  // mii constants
  private static final String WDT_MODEL_FILE = "model1-wls.yaml";
  private static final String MII_IMAGE_NAME = "mii-image";
  private static final String APP_NAME = "sample-app";

  private static String domainNamespace = null;
  private static ConditionFactory withStandardRetryPolicy = null;
  private static HelmParams nginxHelmParams = null;
  private static int nodeportshttp;

  private String domainUid = "domain1";
  private final String clusterName = "cluster-1";
  private final int managedServerPort = 8001;
  private final int replicaCount = 2;

  /**
   * Install operator and NGINX.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    // create standard, reusable retry/backoff policy
    withStandardRetryPolicy = with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(5, MINUTES).await();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // get a unique NGINX namespace
    logger.info("Get a unique namespace for NGINX");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    String nginxNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // get a free node port for NGINX
    nodeportshttp = getNextFreePort(30305, 30405);
    int nodeportshttps = getNextFreePort(30443, 30543);

    // install and verify NGINX
    nginxHelmParams = installAndVerifyNginx(nginxNamespace, nodeportshttp, nodeportshttps);
  }

  @Test
  @Order(1)
  @DisplayName("Create model in image domain")
  @Slow
  @MustNotRunInParallel
  public void testCreateMiiDomain() {
    // admin/managed server name here should match with model yaml in WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // create image with model files
    String miiImage = createImageAndVerify();

    // construct a list of oracle.weblogic.domain.Cluster objects to be used in the domain custom resource
    List<Cluster> clusters = new ArrayList<>();
    clusters.add(new Cluster()
        .clusterName(clusterName)
        .replicas(replicaCount)
        .serverStartState("RUNNING"));

    // create and verify the domain
    createMiiDomain(miiImage, domainUid, domainNamespace, clusters, "WLS");

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

    // check admin server pod exist
    logger.info("Check for admin server pod {0} existence in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodCreated(adminServerPodName, domainUid, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceCreated(adminServerPodName, domainNamespace);

    // check managed server pods exists for each cluster in the domain
    for (int i = 0; i < clusters.size(); i++) {
      // get the replicas for each cluster
      int replicaCount = clusters.get(i).getReplicas();
      String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

      for (int j = 1; j <= replicaCount; j++) {
        // check for the managed server pod is created
        logger.info("Check for managed server pod {0} existence in namespace {1}",
            managedServerPrefix + i, domainNamespace);
        checkPodCreated(managedServerPrefix + j, domainUid, domainNamespace);

        // wait for the managed server pod to be ready
        logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
            managedServerPrefix + i, domainNamespace);
        checkPodReady(managedServerPrefix + j, domainUid, domainNamespace);

        // check the managed server service is created
        logger.info("Check managed server service {0} is created in namespace {1}",
            managedServerPrefix + i, domainNamespace);
        checkServiceCreated(managedServerPrefix + j, domainNamespace);
      }
    }
  }

  @Test
  @Order(2)
  @DisplayName("Create an ingress for a WebLogic domain in the specified domain namespace")
  public void testCreateIngress() {

    // create an ingress in domain namespace
    assertThat(assertDoesNotThrow(() -> createIngress(domainNamespace, domainUid, clusterName, managedServerPort)))
            .as("createIngress succeeds")
            .withFailMessage(String.format("failed to create an ingress for domain %s in namespace %s",
                domainUid, domainNamespace))
            .isTrue();

    // check the ingress is created
    String ingressName = domainUid + "-nginx";
    assertThat(assertDoesNotThrow(() -> getIngressList(domainNamespace)))
        .as(String.format("found the ingress %s in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("can not find ingress %s in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    logger.info("ingress is created in namespace {0}", domainNamespace);
  }

  @Test
  @Order(3)
  @DisplayName("Verify the application can be accessed through the ingress controller")
  public void testSampleAppThroughIngressController() {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // check that NGINX can access the sample apps from all managed servers in the domain
    String curlCmd = String.format("curl --silent --noproxy '*' -H 'host: %s' http://%s:%s/sample-war/index.jsp",
        domainUid + ".test", K8S_NODEPORT_HOST, nodeportshttp);
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 50))
        .as("NGINX can access the sample app from all managed servers in the domain")
        .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
        .isTrue();
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
   * Create a Docker image for model in image.
   *
   * @return image name with tag
   */
  private String createImageAndVerify() {

    // create unique image name with date
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

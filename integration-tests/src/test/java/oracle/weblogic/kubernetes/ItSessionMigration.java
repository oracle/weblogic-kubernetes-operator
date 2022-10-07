// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateNewModelFileWithUpdatedDomainUid;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.getOrigModelFile;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.getServerAndSessionInfoAndVerify;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.shutdownServerAndVerify;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verify that when the primary server is down, another server takes on its
 * clients to become the new primary server and HTTP session state is migrated
 * to the new primary server.
 *
 * Also verify that an annotation containing a slash in the name propagates
 * to the server pod
 */
@DisplayName("Test the HTTP session replication features of WebLogic")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
class ItSessionMigration {

  // constants for creating domain image using model in image
  private static final String SESSMIGR_IMAGE_NAME = "sessmigr-mii-image";

  // constants for web service
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static final String SESSMIGR_APP_WAR_NAME = "sessmigr-war";
  private static final int SESSION_STATE = 4;
  private static Map<String, String> httpAttrMap;

  // constants for operator and WebLogic domain
  private static String domainUid = "sessmigr-domain-1";
  private static String clusterName = "cluster-1";
  private static String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static String finalPrimaryServerName = null;
  // Since the ServerTemplate section of the model file model.sessmigr.yaml
  // does not explicitly specify ListenPort, the introspector/wdt generated
  // default ListenPort for each dynamic server is set to 7100
  private static int managedServerPort = 7100;
  private static int replicaCount = 2;
  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static LoggingFacade logger = null;

  private static String annotationKey = "custDomainHome";
  private static String annotationValue = "/u01/oracle";
  private static String annotationKey2 = "custHostName";
  private static String annotationValue2 = "https://hub.docker.com/a-0/b.c-d.asp";
  private static String annotationKey3 = "custImageName";
  private static String annotationValue3 = "nginx:1.14.2-1_1";

  /**
   * Install operator, create a custom image using model in image with model files
   * and create a one cluster domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create and verify WebLogic domain image using model in image with model files
    String imageName = createAndVerifyDomainImage();

    // create and verify one cluster domain
    logger.info("Create domain and verify that it's running");
    createAndVerifyDomain(imageName);

    // map to save HTTP response data
    httpAttrMap = new HashMap<String, String>();
    httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
    httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
    httpAttrMap.put("primary", "(.*)primary>(.*)</primary(.*)");
    httpAttrMap.put("secondary", "(.*)secondary>(.*)</secondary(.*)");
    httpAttrMap.put("count", "(.*)countattribute>(.*)</countattribute(.*)");
  }

  @AfterAll
  void tearDown() {
  }

  /**
   * The test sends a HTTP request to set http session state(count number), get the primary and secondary server name,
   * session create time and session state and from the util method and save HTTP session info,
   * then stop the primary server by changing serverStartPolicy to Never and patching domain.
   * Send another HTTP request to get http session state (count number), primary server and
   * session create time. Verify that a new primary server is selected and HTTP session state is migrated.
   */
  @Test
  @DisplayName("Stop the primary server, verify that a new primary server is picked and HTTP session state is migrated")
  void testSessionMigration() {
    final String primaryServerAttr = "primary";
    final String secondaryServerAttr = "secondary";
    final String sessionCreateTimeAttr = "sessioncreatetime";
    final String countAttr = "count";
    final String webServiceSetUrl = SESSMIGR_APP_WAR_NAME + "/?setCounter=" + SESSION_STATE;
    final String webServiceGetUrl = SESSMIGR_APP_WAR_NAME + "/?getCounter";
    final String clusterAddress = domainUid + "-cluster-" + clusterName;
    String serverName = managedServerPrefix + "1";

    // send a HTTP request to set http session state(count number) and save HTTP session info
    // before shutting down the primary server
    Map<String, String> httpDataInfo = getServerAndSessionInfoAndVerify(domainNamespace, adminServerPodName,
        serverName, clusterAddress, managedServerPort, webServiceSetUrl, " -c ");

    // get server and session info from web service deployed on the cluster
    String origPrimaryServerName = httpDataInfo.get(primaryServerAttr);
    String origSecondaryServerName = httpDataInfo.get(secondaryServerAttr);
    String origSessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    logger.info("Got the primary server {0}, the secondary server {1} "
            + "and session create time {2} before shutting down the primary server",
        origPrimaryServerName, origSecondaryServerName, origSessionCreateTime);

    // stop the primary server by changing ServerStartPolicy to Never and patching domain
    logger.info("Shut down the primary server {0}", origPrimaryServerName);
    shutdownServerAndVerify(domainUid, domainNamespace, origPrimaryServerName);

    // send a HTTP request to get server and session info after shutting down the primary server
    serverName = domainUid + "-" + origSecondaryServerName;
    httpDataInfo = getServerAndSessionInfoAndVerify(domainNamespace, adminServerPodName,
        serverName, clusterAddress, managedServerPort, webServiceGetUrl, " -b ");
    // get server and session info from web service deployed on the cluster
    String primaryServerName = httpDataInfo.get(primaryServerAttr);
    String sessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    String countStr = httpDataInfo.get(countAttr);
    int count = Optional.ofNullable(countStr).map(Integer::valueOf).orElse(0);
    logger.info("After patching the domain, the primary server changes to {0} "
            + ", session create time {1} and session state {2}",
        primaryServerName, sessionCreateTime, countStr);

    // verify that a new primary server is picked and HTTP session state is migrated
    assertAll("Check that WebLogic server and session vars is not null or empty",
        () -> assertNotEquals(origPrimaryServerName, primaryServerName,
            "After the primary server stopped, another server should become the new primary server"),
        () -> assertEquals(origSessionCreateTime, sessionCreateTime,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server"),
        () -> assertEquals(SESSION_STATE, count,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server")
    );

    finalPrimaryServerName = primaryServerName;

    logger.info("Done testSessionMigration \nThe new primary server is {0}, it was {1}. "
            + "\nThe session state was set to {2}, it is migrated to the new primary server.",
        primaryServerName, origPrimaryServerName, SESSION_STATE);
  }

  /**
   * Test that when updating a domain resource yaml, serverPod with annotations/labels
   * where the key contains slash propagated to the server pod.
   */
  @Test
  @DisplayName("Test that an annotation containing a slash in the name propagates to the server pod")
  void testPodAnnotationWithSlash() {
    String managedServerPodName = domainUid + "-" + "managed-server1";
    V1Pod managedServerPod = null;

    try {
      managedServerPod = Kubernetes.getPod(domainNamespace, null, managedServerPodName);
    } catch (Exception ex) {
      ex.printStackTrace();
      fail("Failed to process HTTP request " + ex.getMessage());
    }

    //check that managed server pod is up and all applicable variable values are initialized.
    assertNotNull(managedServerPod,
        "The managed server pod does not exist in namespace " + domainNamespace);
    V1ObjectMeta managedServerMetadata = managedServerPod.getMetadata();
    String myAnnotationValue = managedServerMetadata.getAnnotations().get(annotationKey);
    String myAnnotationValue2 = managedServerMetadata.getAnnotations().get(annotationKey2);
    String myAnnotationValue3 = managedServerMetadata.getAnnotations().get(annotationKey3);

    logger.info("Verify Value for annotation key:value is {0}:{1}",
        annotationKey, myAnnotationValue);
    assertEquals(annotationValue, myAnnotationValue,
        String.format("Failed to propagate annotation %s to the server pod", annotationValue));
    logger.info("Verify Value for annotation key:value is {0}:{1}",
        annotationKey2, myAnnotationValue2);
    assertEquals(annotationValue2, myAnnotationValue2,
        String.format("Failed to propagate annotation %s to the server pod", annotationValue2));
    logger.info("Verify Value for annotation key:value is {0}:{1}",
        annotationKey3, myAnnotationValue3);
    assertEquals(annotationValue3, myAnnotationValue3,
        String.format("Failed to propagate annotation %s to the server pod", annotationValue3));
  }

  private static String createAndVerifyDomainImage() {
    // create image with model files
    logger.info("Create image with model file and verify");

    // Generate the model.sessmigr.yaml file at RESULTS_ROOT
    String destSessionMigrYamlFile =
        generateNewModelFileWithUpdatedDomainUid(domainUid, "ItSessionMigration", getOrigModelFile());

    List<String> appList = new ArrayList<>();
    appList.add(SESSMIGR_APP_NAME);

    // build the model file list
    final List<String> modelList = Collections.singletonList(destSessionMigrYamlFile);

    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage = createMiiImageAndVerify(SESSMIGR_IMAGE_NAME, modelList, appList);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Create registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    return miiImage;
  }

  private static void createAndVerifyDomain(String miiImage) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
        String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc"),
        String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainCrAndVerify(adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, miiImage);

    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check that admin server pod is ready and admin service exists in the domain namespace
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod is ready and the service exists in the domain namespace
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private static void createDomainCrAndVerify(String adminSecretName,
                                              String repoSecretName,
                                              String encryptionSecretName,
                                              String miiImage) {

    Map<String, String> annotationKeyValues = new HashMap<>();
    annotationKeyValues.put(annotationKey, annotationValue);
    annotationKeyValues.put(annotationKey2, annotationValue2);
    annotationKeyValues.put(annotationKey3, annotationValue3);

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .replicas(replicaCount)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .annotations(annotationKeyValues)
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    // create cluster resource
    domain = createClusterResourceAndAddReferenceToDomain(
        domainUid + "-" + clusterName, clusterName, domainNamespace, domain, replicaCount);

    setPodAntiAffinity(domain);
    // create domain using model in image
    logger.info("Create model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }
}

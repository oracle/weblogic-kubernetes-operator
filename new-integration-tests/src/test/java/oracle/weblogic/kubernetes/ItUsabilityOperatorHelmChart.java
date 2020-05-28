// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.joda.time.DateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_SERVICE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallNginx;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressForDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyNginx;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Simple JUnit test file used for testing operator usability.
 * Use Helm chart to install operator(s)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test operator usability using Helm chart installation")
@IntegrationTest
class ItUsabilityOperatorHelmChart implements LoggedTest {

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

  // ingress host list
  private List<String> ingressHostList;

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
   * Create a custom domain resource and verify all server pods in the domain were created and ready.
   * Verify NGINX can access the sample app from all managed servers in the domain.
   * Delete operator.
   * Verify the states of all server pods in the domain were not changed.
   * Verify NGINX can access the sample app from all managed servers in the domain after the operator was deleted.
   * Test fails if the state of any pod in the domain was changed or NGINX can not access the sample app from
   * all managed servers in the absence of operator.
   */
  @Test
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
    DateTime adminPodOriginalTimestamp =
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
            String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                adminServerPodName, domainNamespace));

    // get the managed server pods original creation timestamps
    logger.info("Getting managed server pods original creation timestamps");
    List<DateTime> managedServerPodOriginalTimestampList = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      final String managedServerPodName = managedServerPrefix + i;
      managedServerPodOriginalTimestampList.add(
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace)));
    }

    // create ingress for the domain
    logger.info("Creating ingress for domain {0} in namespace {1}", domainUid, domainNamespace);
    Map<String, Integer> clusterNameMsPortMap = new HashMap<>();
    clusterNameMsPortMap.put(clusterName, managedServerPort);
    ingressHostList =
        createIngressForDomainAndVerify(domainUid, domainNamespace, clusterNameMsPortMap);

    // verify the sample apps for the domain
    logger.info("Checking that the sample app can be accessed from all managed servers through NGINX");
    verifySampleAppAccessThroughNginx();

    // delete operator
    logger.info("Uninstalling operator");
    uninstallOperator(opHelmParams);

    // verify the operator pod does not exist in the operator namespace
    logger.info("Checking that operator pod does not exist in operator namespace");
    checkPodDoesNotExist("weblogic-operator-", null, opNamespace);

    // verify the operator service does not exist in the operator namespace
    logger.info("Checking that operator service does not exist in operator namespace");
    checkServiceDoesNotExist(OPERATOR_SERVICE_NAME, opNamespace);

    // check that the state of admin server pod in the domain was not changed
    // wait some time here to ensure the pod state is not changed
    try {
      Thread.sleep(15000);
    } catch (InterruptedException e) {
      // ignore
    }

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

    // get the pre-built image created by IntegrationTestWatcher
    String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(miiImage);

    // create docker registry secret to pull the image from registry
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createDockerRegistrySecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic", "welcome1");

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    // construct a list of oracle.weblogic.domain.Cluster objects to be used in the domain custom resource
    List<Cluster> clusters = new ArrayList<>();
    clusters.add(new Cluster()
        .clusterName(clusterName)
        .replicas(replicaCount)
        .serverStartState("RUNNING"));

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(REPO_SECRET_NAME))
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
                .serverStartState("RUNNING"))
            .clusters(clusters)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check that admin service exists in the domain namespace
    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod exists
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodExists(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);
    }
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
        String.format("curl --silent --show-error --noproxy '*' -H 'host: %s' http://%s:%s/sample-war/index.jsp",
            ingressHostList.get(0), K8S_NODEPORT_HOST, nodeportshttp);
    assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, managedServerNames, 50))
        .as("Verify NGINX can access the sample app from all managed servers in the domain")
        .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
        .isTrue();
  }
}

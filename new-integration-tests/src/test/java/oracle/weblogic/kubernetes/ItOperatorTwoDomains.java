// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLS_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPull;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerTag;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.restart;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdown;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodRestarted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainOnPVWithWlst;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOCRRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVandPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test operator manages multiple domains.
 */
@DisplayName("Verify operator manages multiple domains")
@IntegrationTest
public class ItOperatorTwoDomains implements LoggedTest {

  private static final int numberOfDomains = 2;
  private static final int numberOfOperators = 2;

  private static List<String> opNamespaces = new ArrayList<>();
  private static List<String> domainNamespaces = new ArrayList<>();
  private static List<String> domainUidList = new ArrayList<>();

  // domain constants
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminUser = "weblogic";
  private final String adminPassword = "welcome1";

  private String image = null;
  private boolean isUseSecret = false;
  private int replicasAfterScale;
  private String domain1AdminServerPodName = null;
  private String domain2AdminServerPodName = null;
  private String domain1AdminPodOriginalTimestamp = null;
  private String domain2AdminPodOriginalTimestamp = null;
  private List<String> domain1ManagedServerPodOriginalTimestampList = new ArrayList<>();
  private List<String> domain2ManagedServerPodOriginalTimestampList = new ArrayList<>();

  /**
   * Install operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {

    // get a unique operator namespace
    logger.info("Get a unique namespace for operator1");
    for (int i = 0; i < numberOfOperators; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      opNamespaces.add(namespaces.get(i));
    }

    // get a unique domain namespace
    logger.info("Get a unique namespace for WebLogic domain");
    for (int i = numberOfOperators; i < numberOfOperators + numberOfDomains; i++) {
      assertNotNull(namespaces.get(i), "Namespace list is null");
      domainNamespaces.add(namespaces.get(i));
    }

    // install and verify operator
    for (int i = 0; i < numberOfOperators; i++) {
      installAndVerifyOperator(opNamespaces.get(i), domainNamespaces.get(i));
    }

    for (int i = 1; i <= numberOfDomains; i++) {
      domainUidList.add("domain" + i);
    }
  }

  /**
   * Test covers the following use case.
   * Create two domains on PV using wlst
   * domain1 managed by operator1
   * domain2 managed by operator2
   * scale cluster in domain 1 from 2 to 3 servers and verify no impact on domain 2, it continues to run
   * restart domain1 and verify domain2 continues to run
   * shutdown the domains using SERVER_START_POLICY or delete the domain
   * @throws IOException when creating PV path fails
   */
  @Test
  @DisplayName("Create domain on PV using WLST script")
  public void testTwoDomainsManagedByTwoOperators() throws IOException {

    image = WLS_BASE_IMAGE_NAME + ":" + WLS_BASE_IMAGE_TAG;

    if (!KIND_REPO.isEmpty()) {
      // We can't figure out why the kind clusters can't pull images from OCR using the image pull secret. There
      // is some evidence it may be a containerd bug. Therefore, we are going to "give up" and workaround the issue.
      // The workaround will be to:
      //   1. docker login
      //   2. docker pull
      //   3. docker tag with the KIND_REPO value
      //   4. docker push this new image name
      //   5. use this image name to create the domain resource
      assertTrue(dockerLogin(OCR_REGISTRY, OCR_USERNAME, OCR_PASSWORD), "docker login failed");
      assertTrue(dockerPull(image), String.format("docker pull failed for image %s", image));

      String kindRepoImage = KIND_REPO + image.substring(TestConstants.OCR_REGISTRY.length() + 1);
      assertTrue(dockerTag(image, kindRepoImage),
          String.format("docker tag failed for images %s, %s", image, kindRepoImage));
      assertTrue(dockerPush(kindRepoImage), String.format("docker push failed for image %s", kindRepoImage));
      image = kindRepoImage;
    } else {
      // create pull secrets for WebLogic image
      for (int i = 0; i < numberOfDomains; i++) {
        createOCRRepoSecret(domainNamespaces.get(i));
      }
      isUseSecret = true;
    }

    // create two domains on pv using wlst
    createTwoDomainsOnPVUsingWlstAndVerify();

    // get the domain1 and domain2 pods original creation timestamp
    getBothDomainsPodsOriginalCreationTimestamp();

    // scale cluster in domain 1 from 2 to 3 servers and verify no impact on domain 2, it continues to run
    replicasAfterScale = 3;
    scaleDomain1AndVerifyNoImpactOnDomain2();

    // restart domain1 and verify domain2 continues to run
    restartDomain1AndVerifyNoImpactOnDomain2();

    // shutdown both domains and verify the pods are shutdown
    shutdownBothDomainsAndVerify();
  }

  /**
   * Create two domains on PV using Wlst.
   * @throws IOException when creating PV path fails
   */
  private void createTwoDomainsOnPVUsingWlstAndVerify() throws IOException {

    String wlSecretName = "weblogic-credentials";
    for (int i = 0; i < numberOfDomains; i++) {

      // initiate domainUid, pvName, pvcName
      String domainUid = domainUidList.get(i);
      String pvName = domainUid + "-pv";
      String pvcName = domainUid + "-pvc";

      // create WebLogic credentials secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespaces.get(i), adminUser, adminPassword);

      // create persistent volume and persistent volume claims
      createPVandPVC(pvName, pvcName, domainUid, domainNamespaces.get(i), this.getClass().getSimpleName());

      // create the domain on persistent volume
      createDomainOnPVWithWlst(image, isUseSecret, pvName, pvcName, domainUid, domainNamespaces.get(i),
          clusterName, adminUser, adminPassword, this.getClass().getSimpleName());

      // create the domain custom resource configuration object
      logger.info("Creating domain custom resource");
      Domain domain = new Domain()
          .apiVersion(DOMAIN_API_VERSION)
          .kind("Domain")
          .metadata(new V1ObjectMeta() //metadata
              .name(domainUid)
              .namespace(domainNamespaces.get(i)))
          .spec(new DomainSpec() //spec
              .domainUid(domainUid)
              .domainHome("/shared/domains/" + domainUid)
              .domainHomeSourceType("PersistentVolume")
              .image(image)
              .imagePullSecrets(isUseSecret ? Arrays.asList(
                  new V1LocalObjectReference()
                      .name(OCR_SECRET_NAME))
                  : null)
              .webLogicCredentialsSecret(new V1SecretReference()
                  .name(wlSecretName)
                  .namespace(domainNamespaces.get(i)))
              .includeServerOutInPodLog(true)
              .logHomeEnabled(Boolean.TRUE)
              .logHome("/shared/logs/" + domainUid)
              .dataHome("")
              .serverStartPolicy("IF_NEEDED")
              .serverPod(new ServerPod() //serverpod
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
                          .nodePort(0))))
              .addClustersItem(new Cluster() //cluster
                  .clusterName(clusterName)
                  .replicas(replicaCount)
                  .serverStartState("RUNNING")));

      logger.info("Creating domain custom resource {0} in namespace {1}", domainUid, domainNamespaces.get(i));
      createDomainAndVerify(domain, domainNamespaces.get(i));

      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      // check admin server pod is ready and service exists in domain namespace
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespaces.get(i));

      // check for managed server pods existence
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespaces.get(i));
      }

      logger.info("Getting node port");
      final int k = i;
      int serviceNodePort = assertDoesNotThrow(() ->
              getServiceNodePort(domainNamespaces.get(k), adminServerPodName + "-external", "default"),
          "Getting admin server node port failed");

      logger.info("Validating WebLogic admin server access by login to console");
      assertTrue(assertDoesNotThrow(() -> adminNodePortAccessible(serviceNodePort, adminUser, adminPassword),
          "Access to admin server node port failed"), "Console login validation failed");
    }
  }

  /**
   * Scale domain1 and verify there is no impact on domain2.
   */
  private void scaleDomain1AndVerifyNoImpactOnDomain2() {

    // scale domain1
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domainUidList.get(0), domainNamespaces.get(0), replicasAfterScale);
    scaleAndVerifyCluster(clusterName, domainUidList.get(0), domainNamespaces.get(0),
        domainUidList.get(0) + "-" + MANAGED_SERVER_NAME_BASE, replicaCount, replicasAfterScale,
        null, null);

    // get the third managed server pod original creation timestamp to the list
    domain1ManagedServerPodOriginalTimestampList.add(
        getPodOriginalCreationTimestamp(domainUidList.get(0) + "-" + MANAGED_SERVER_NAME_BASE
                + replicasAfterScale, domainNamespaces.get(0)));

    // verify scaling domain1 has no impact on domain2
    logger.info("Checking that domain2 was not changed after domain1 was scaled up");
    verifyDomain2NotChanged();
  }

  private void restartDomain1AndVerifyNoImpactOnDomain2() {

    // shutdown domain1
    assertTrue(shutdown(domainUidList.get(0), domainNamespaces.get(0)),
        String.format("restart domain %s in namespace %s failed", domainUidList.get(0), domainNamespaces.get(0)));

    // verify all the server pods in domain1 were shutdown
    checkPodDoesNotExist(domain1AdminServerPodName, domainUidList.get(0), domainNamespaces.get(0));

    for (int i = 1; i <= replicasAfterScale; i++) {
      String domain1ManagedServerPodName = domainUidList.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(domain1ManagedServerPodName, domainUidList.get(0), domainNamespaces.get(0));
    }

    // restart domain1
    assertTrue(restart(domainUidList.get(0), domainNamespaces.get(0)),
        String.format("restart domain %s in namespace %s failed", domainUidList.get(0), domainNamespaces.get(0)));

    // verify domain1 is restarted
    // check admin server pod in domain1
    checkPodReadyAndServiceExists(domain1AdminServerPodName, domainUidList.get(0), domainNamespaces.get(0));
    checkPodRestarted(domain1AdminServerPodName, domainUidList.get(0), domainNamespaces.get(0),
        domain1AdminPodOriginalTimestamp);

    // check managed server pods in domain1
    for (int i = 1; i <= replicasAfterScale; i++) {
      String domain1ManagedServerPodName = domainUidList.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodReadyAndServiceExists(domain1ManagedServerPodName, domainUidList.get(0), domainNamespaces.get(0));
      checkPodRestarted(domain1ManagedServerPodName, domainUidList.get(0), domainNamespaces.get(0),
          domain1ManagedServerPodOriginalTimestampList.get(i - 1));
    }

    // verify domain 2 was not changed after domain1 was restarted
    verifyDomain2NotChanged();
  }

  private void verifyDomain2NotChanged() {
    logger.info("Checking that domain2 admin server pod state was not changed");
    assertThat(podStateNotChanged(domain2AdminServerPodName, domainUidList.get(1), domainNamespaces.get(1),
        domain2AdminPodOriginalTimestamp))
        .as("Test state of pod {0} was not changed in namespace {1}",
            domain2AdminServerPodName, domainNamespaces.get(1))
        .withFailMessage("State of pod {0} was changed in namespace {1}",
            domain2AdminServerPodName, domainNamespaces.get(1))
        .isTrue();

    logger.info("Checking that domain2 managed server pod state was not changed");
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUidList.get(1) + "-" + MANAGED_SERVER_NAME_BASE + i;
      assertThat(podStateNotChanged(managedServerPodName, domainUidList.get(1), domainNamespaces.get(1),
          domain2ManagedServerPodOriginalTimestampList.get(i - 1)))
          .as("Test state of pod {0} was not changed in namespace {1}",
              managedServerPodName, domainNamespaces.get(1))
          .withFailMessage("State of pod {0} was changed in namespace {1}",
              managedServerPodName, domainNamespaces.get(1))
          .isTrue();
    }
  }

  private void getBothDomainsPodsOriginalCreationTimestamp() {
    // get the domain1 pods original creation timestamp
    domain1AdminServerPodName = domainUidList.get(0) + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Getting admin server pod original creation timestamp");
    domain1AdminPodOriginalTimestamp =
        getPodOriginalCreationTimestamp(domain1AdminServerPodName, domainNamespaces.get(0));

    // get the managed server pods original creation timestamps
    logger.info("Getting managed server pods original creation timestamps");
    for (int i = 1; i <= replicaCount; i++) {
      final String managedServerPodName = domainUidList.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i;
      domain1ManagedServerPodOriginalTimestampList.add(
          getPodOriginalCreationTimestamp(managedServerPodName, domainNamespaces.get(0)));
    }

    // get the domain2 admin server pod original creation timestamp
    logger.info("Getting admin server pod original creation timestamp");
    domain2AdminServerPodName = domainUidList.get(1) + "-" + ADMIN_SERVER_NAME_BASE;
    domain2AdminPodOriginalTimestamp =
        getPodOriginalCreationTimestamp(domain2AdminServerPodName, domainNamespaces.get(1));

    // get the domain2 managed server pods original creation timestamps
    logger.info("Getting managed server pods original creation timestamps");
    for (int i = 1; i <= replicaCount; i++) {
      final String managedServerPodName = domainUidList.get(1) + "-" + MANAGED_SERVER_NAME_BASE + i;
      domain2ManagedServerPodOriginalTimestampList.add(
          getPodOriginalCreationTimestamp(managedServerPodName, domainNamespaces.get(1)));
    }
  }

  private void shutdownBothDomainsAndVerify() {

    // shutdown both domains
    for (int i = 0; i < numberOfDomains; i++) {
      shutdown(domainUidList.get(i), domainNamespaces.get(i));
    }

    // verify all the pods were shutdown
    for (int i = 0; i < numberOfDomains; i++) {
      // check admin server pod was shutdown
      checkPodDoesNotExist(domainUidList.get(i) + "-" + ADMIN_SERVER_NAME_BASE,
          domainUidList.get(i), domainNamespaces.get(i));

      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUidList.get(i) + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodDoesNotExist(managedServerPodName, domainUidList.get(i), domainNamespaces.get(i));
      }
    }

    // check the scaled up managed servers in domain1 was shutdown
    for (int i = replicaCount + 1; i <= replicasAfterScale; i++) {
      String managedServerPodName = domainUidList.get(0) + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(managedServerPodName, domainUidList.get(0), domainNamespaces.get(0));
    }

  }

  private String getPodOriginalCreationTimestamp(String podName, String namespace) {
    return assertDoesNotThrow(() -> getPodCreationTimestamp(namespace, "", podName),
        String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
            podName, namespace));
  }

  private void checkPodReadyAndServiceExists(String podName, String domainUid, String namespace) {
    logger.info("Checking that pod {0} exists in namespace {1}", podName, namespace);
    checkPodExists(podName, domainUid, namespace);

    logger.info("Waiting for pod {0} to be ready in namespace {1}", podName, namespace);
    checkPodReady(podName, domainUid, namespace);

    logger.info("Check service {0} exists in namespace {1}", podName, namespace);
    checkServiceExists(podName, namespace);

  }
}

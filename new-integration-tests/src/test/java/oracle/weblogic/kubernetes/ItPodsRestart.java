// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDockerRegistrySecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test pods are restarted after some properties in server pods are changed.
 */
@DisplayName("Test pods are restarted after some properties in server pods are changed")
@IntegrationTest
class ItPodsRestart implements LoggedTest {

  private static String domainNamespace = null;

  // domain constants
  private static final String domainUid = "domain1";
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;
  private static final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

  /**
   * Get namespaces for operator and WebLogic domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {

    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create a basic model in image domain
    createAndVerifyMiiDomain();
  }

  /**
   * Add/Modify server pod resources by patching the domain custom resource.
   * Verify all pods are restarted and back to ready state.
   * The resources tested: resources: limits: cpu: "1", resources: requests: cpu: "0.5"
   * Test fails if any server pod is not restarted and back to ready state or the compute resources in the patched
   * domain custom resource do not match the values we planned to add or modify.
   */
  @Test
  @DisplayName("Verify server pods are restarted by changing the resources")
  public void testServerPodsRestartByChangingResource() {

    // get the original domain resource before update
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));

    assertNotNull(domain1, domain1 + " is null");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + "/spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getResources(), domain1 + "/spec/serverPod/resources is null");

    // get the current server pod compute resource limit
    Map<String, Quantity> limits = domain1.getSpec().getServerPod().getResources().getLimits();
    assertNotNull(limits, domain1 + "/spec/serverPod/resources/limits is null");

    // print out current server pod compute resource limits
    logger.info("Current value for server pod compute resource limits:");
    limits.forEach((key, value) -> logger.info(key + ": " + value.toString()));

    // get the current server pod compute resource requests
    Map<String, Quantity> requests = domain1.getSpec().getServerPod().getResources().getRequests();
    assertNotNull(requests, domain1 + "/spec/serverPod/resources/requests is null");

    // print out current server pod compute resource requests
    logger.info("Current value for server pod compute resource requests:");
    requests.forEach((key, value) -> logger.info(key + ": " + value.toString()));

    // create the map with server pods and their original creation timestamps
    Map<String, DateTime> podsWithTimeStamps = new LinkedHashMap<>();
    podsWithTimeStamps.put(adminServerPodName,
        assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", adminServerPodName),
        String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
            adminServerPodName, domainNamespace)));

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      podsWithTimeStamps.put(managedServerPodName,
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace)));
    }

    // add the new server pod compute resources limits: cpu: 1, requests: cpu: 0.5
    BigDecimal cpuLimit = new BigDecimal(1);
    BigDecimal cpuRequest = new BigDecimal(0.5);

    // verify if cpu limit was set then the new value should be different than the original value
    if (limits.get("cpu") != null) {
      assertNotEquals(limits.get("cpu").getNumber().compareTo(cpuLimit), 0,
          String.format("server pod compute resources cpu limit is already set to %s, set cpu limit to "
              + "a different value", cpuLimit));
    }

    // verify if cpu request was set then the new value should be different than the original value
    if (requests.get("cpu") != null) {
      assertNotEquals(requests.get("cpu").getNumber().compareTo(cpuRequest), 0,
          String.format("server pod compute resources cpu request is already set to %s, set cpu request to "
              + "a different value", cpuRequest));
    }

    // add/modify the server pod resources by patching the domain custom resource
    assertTrue(addServerPodResources(cpuLimit, cpuRequest),
        String.format("Failed to add server pod compute resources for domain %s in namespace %s",
            domainUid, domainNamespace));

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(
        () -> verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        "More than one pod was restarted at same time"),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    // get the patched domain custom resource
    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));

    assertNotNull(domain1, domain1 + " is null");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + "/spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getResources(), domain1 + "/spec/serverPod/resources is null");

    // get new server pod compute resources limits
    limits = domain1.getSpec().getServerPod().getResources().getLimits();
    assertNotNull(limits, domain1 + "/spec/serverPod/resources/limits is null");

    // print out server pod compute resource limits
    logger.info("New value for server pod compute resource limits:");
    limits.forEach((key, value) -> logger.info(key + ": " + value.getNumber().toString()));

    // verify the server pod resources limits got updated
    logger.info("Checking that the server pod resources cpu limit was updated correctly");
    assertNotNull(limits.get("cpu"), domain1 + "/spec/serverPod/resources/limits/cpu is null");
    assertEquals(limits.get("cpu").getNumber().compareTo(cpuLimit), 0,
        String.format("server pod compute resource limits were not updated correctly, set cpu limit to %s, got %s",
            cpuLimit, limits.get("cpu").getNumber()));

    // get new server pod compute resources requests
    requests = domain1.getSpec().getServerPod().getResources().getRequests();
    assertNotNull(requests, domain1 + "/spec/serverPod/resources/requests is null");

    // print out server pod compute resource requests
    logger.info("New value for server pod compute resource requests:");
    requests.forEach((key, value) -> logger.info(key + ": " + value.getNumber()));

    // verify the server pod resources requests got updated
    logger.info("Checking that the server pod resources cpu request is updated correctly");
    assertNotNull(requests.get("cpu"), domain1 + "/spec/serverPod/resources/requests/cpu is null");
    assertEquals(requests.get("cpu").getNumber().compareTo(cpuRequest), 0,
        String.format("server pod compute resources requests was not updated correctly, set cpu request to %s, got %s",
            cpuRequest, requests.get("cpu").getNumber()));
  }

  /**
   * Create a model in image domain and verify the server pods are ready.
   */
  private static void createAndVerifyMiiDomain() {

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
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .resources(new V1ResourceRequirements()
                    .limits(new HashMap<>())
                    .requests(new HashMap<>())))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING"))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
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

      // check that the managed server pod exists in the domain namespace
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
   * Add server pod compute resources.
   *
   * @param cpuLimit cpu limit to be added to domain spec serverPod resources limits
   * @param cpuRequest cpu request to be added to domain spec serverPod resources requests
   * @return true if patching domain custom resource is successful, false otherwise
   */
  private boolean addServerPodResources(BigDecimal cpuLimit, BigDecimal cpuRequest) {
    // construct the patch string for adding server pod resources
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/serverPod/resources/limits/cpu\", ")
        .append("\"value\": \"")
        .append(cpuLimit)
        .append("\"}, {")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/serverPod/resources/requests/cpu\", ")
        .append("\"value\": \"")
        .append(cpuRequest)
        .append("\"}]");

    logger.info("Adding server pod compute resources for domain {0} in namespace {1} using patch string: {2}",
        domainUid, domainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }
}

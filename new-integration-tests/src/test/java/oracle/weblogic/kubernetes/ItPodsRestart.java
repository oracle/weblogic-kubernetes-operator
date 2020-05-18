// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodRestarted;
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
 * Test pods were restarted by some properties change.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test operator usability using Helm chart installation")
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
   * Get namespaces for operator, domain1.
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
   * Verify all pods were restarted and back to be ready.
   * The resources tested: resources: limits: cpu: "1", resources: requests: cpu: "0.5"
   * Test fails if any server pod was not restarted and back to be ready or the compute resources in the patched
   * domain custom resource do not match the values we planed to add or modify.
   */
  @Test
  @DisplayName("Verify server pods were restarted by changing the resources")
  public void testServerPodsRestartByChangingResource() {

    // get the original domain resource before update
    Domain domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));

    assertNotNull(domain1, "domain1 is null");
    assertNotNull(domain1.getSpec(), "domain1 spec is null");
    assertNotNull(domain1.getSpec().getServerPod(), "domain1 spec serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getResources(), "domain1 spec serverPod resources is null");

    // get the current server pod compute resource limit
    Map<String, Quantity> limits = domain1.getSpec().getServerPod().getResources().getLimits();
    assertNotNull(limits, "domain1 spec serverPod resources limits is null");

    // print out current server pod compute resource limits
    logger.info("Current value for server pod compute resource limits:");
    limits.forEach((key, value) -> logger.info(key + ":   " + value.toString()));

    // get the current server pod compute resource request
    Map<String, Quantity> requests = domain1.getSpec().getServerPod().getResources().getRequests();
    assertNotNull(requests, "domain1 spec serverPod resources requests is null");

    // print out current server pod compute resource requests
    logger.info("Current value for server pod compute resource requests:");
    requests.forEach((key, value) -> logger.info(key + ":   " + value.toString()));

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

    // add the new server pod compute resources limits: cpu: 1, requests: cpu: 0.5
    String cpuLimit = "1";
    String cpuRequest = "0.5";

    // verify if cpu limit was set then the new value should be different than the original value
    if (limits.get("cpu") != null) {
      assertNotEquals(limits.get("cpu").getNumber().toString(), cpuLimit,
          String.format("server pod compute resources cpu limit is already set to %s, set cpu limit to "
              + "a different value", cpuLimit));
    }

    // verify if cpu request was set then the new value should be different than the original value
    if (requests.get("cpu") != null) {
      assertNotEquals(requests.get("cpu").getNumber().toString(), cpuRequest,
          String.format("server pod compute resources cpu request is already set to %s, set cpu request to "
              + "a different value", cpuRequest));
    }

    // add/modify the server pod resources by patching the domain custom resource
    assertTrue(addServerPodResources(cpuLimit, cpuRequest), "Failed to add server pod compute resources");

    // verify the admin server pod was restarted
    checkPodRestarted(adminServerPodName, domainUid, domainNamespace, adminPodOriginalTimestamp);

    // check that the admin server pod is back to be ready
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify the managed server pod was restarted and back to be ready
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      // check the managed server pod was restarted
      checkPodRestarted(managedServerPodName, domainUid, domainNamespace,
          managedServerPodOriginalTimestampList.get(i - 1));
      // check the managed server pod is back to be ready
      checkPodReady(managedServerPodName, domainUid, domainNamespace);
    }

    // get the patched domain custom resource
    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));

    assertNotNull(domain1, "domain1 is null");
    assertNotNull(domain1.getSpec(), "domain1 spec is null");
    assertNotNull(domain1.getSpec().getServerPod(), "domain1 spec serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getResources(), "domain1 spec serverPod resources is null");

    // get the new server pod compute resources limit
    limits = domain1.getSpec().getServerPod().getResources().getLimits();
    assertNotNull(limits, "server pod resources limits are null");

    // print out server pod compute resource limits
    logger.info("New value for server pod compute resource limits:");
    limits.forEach((key, value) -> logger.info(key + ": " + value.getNumber().toString()));

    // verify the server pod resources limits got updated
    logger.info("Checking that the server pod resources cpu limit was updated correctly");
    assertNotNull(limits.get("cpu"), "server pod resources cpu limit is null");
    assertEquals(limits.get("cpu").getNumber().toString(), cpuLimit,
        String.format("server pod compute resource limits was not updated correctly, set cpu limit to %s, got %s",
            cpuLimit, limits.get("cpu").getNumber().toString()));

    // get the new server pod compute resources request
    requests = domain1.getSpec().getServerPod().getResources().getRequests();
    assertNotNull(requests, "server pod resources requests are null");

    // print out server pod compute resource requests
    logger.info("New value for server pod compute resource requests:");
    requests.forEach((key, value) -> logger.info(key + ": " + value.getNumber().toString()));

    // verify the server pod resources requests got updated
    logger.info("Checking that the server pod resources cpu request was updated correctly");
    assertNotNull(requests.get("cpu"), "server pod resources cpu request is null");
    assertEquals(requests.get("cpu").getNumber().toString(), cpuRequest,
        String.format("server pod compute resources requests was not updated correctly, set cpu request to %s, got %s",
            cpuRequest, requests.get("cpu").getNumber().toString()));
  }

  /**
   * Create a model in image domain and verify the domain pods are ready.
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
   * @return true if patch domain custom resource is successful, false otherwise
   */
  private boolean addServerPodResources(String cpuLimit, String cpuRequest) {
    // construct the patch string for scaling the cluster in the domain
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

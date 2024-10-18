// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.addServerPodResources;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Use Operator log to test WLS server pods were evicted due to Pod ephemeral local
 * storage usage exceeds the total limit of containers 25M and replaced.
 * 1. Install and start Operators
 * 2. Create and start the WebLogic domain with configuration of resource limit "ephemeral-storage=25M"
 * 3. Verify that WLS server pods were evicted due to Pod ephemeral local
 *    storage usage exceeds the total limit of containers 25M and replaced.
 */
@DisplayName("Test WLS server pods were evicted due to Pod ephemeral storage usage exceeds the total limit")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("oke-parallel")
@Tag("gate")
class ItEvictedPodsCycling {

  // constants for Domain
  private static String domainUid = "domain1";
  private static String adminServerPodName = String.format("%s-%s", domainUid, ADMIN_SERVER_NAME_BASE);
  private static String managedServerPodPrefix = String.format("%s-%s", domainUid, MANAGED_SERVER_NAME_BASE);
  private static String clusterName = "cluster-1";
  private static int replicaCount = 2;

  private static String opNamespace = null;
  private static String domainNamespace = null;

  private static Map<String, String> resourceRequest = new HashMap<>();
  private static Map<String, String> resourceLimit = new HashMap<>();
  private static final String ephemeralStorage = "25M";

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * Config resource limit and set ephemeral-storage=25M
   * Create domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism.
   */
  @BeforeAll
  public static void init(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assigning a unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify Operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create a domain resource
    createAndVerifyDomain();
  }

  /**
   * Set domain resources limits to 25M and then use Operator log to verify
   * that WLS server pods were evicted due to Pod ephemeral local
   * storage usage exceeds the total limit of containers 25M and replaced. 
   */
  @Test
  @DisplayName("Use Operator log to verify that WLS server pods were evicted and replaced")
  void testEvictedPodReplaced() throws ApiException {
    resourceLimit.put("ephemeral-storage", ephemeralStorage);
    resourceRequest.put("cpu", "250m");
    resourceRequest.put("memory", "768Mi");

    // patch domain with ephemeral-storage = 25M
    String reason = "Pod ephemeral local storage usage exceeds the total limit of containers";
    addServerPodResources(domainUid, domainNamespace, resourceLimit, resourceRequest);
    // verify that admin server pod evicted event is logged
    testUntil(withLongRetryPolicy,
        checkEvictionEvent(adminServerPodName, "Evicted", reason, "Warning"),
        logger,
        "domain event {0} to be logged in namespace {1}",
        reason,
        domainNamespace);
    resourceLimit.replace("ephemeral-storage", "250M");
    addServerPodResources(domainUid, domainNamespace, resourceLimit, resourceRequest);

    // verify that evicted pods are replaced and started
    checkServerPodsAndServiceReady();
  }

  private static DomainResource createAndVerifyDomain() {
    LoggingFacade logger = getLogger();
    // this secret is used only for non-kind cluster
    logger.info("Create the repo secret {0} to pull the image", TEST_IMAGES_REPO_SECRET_NAME);
    assertDoesNotThrow(() -> createTestRepoSecret(domainNamespace),
        String.format("createSecret failed for %s", TEST_IMAGES_REPO_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create the domain custom resource
    logger.info("Create domain resource {0} object in namespace {1} and verify that it is created",
        domainUid, domainNamespace);
    DomainResource domain = createDomainResource(
        domainUid,
        domainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName
    );

    domain.spec()
        .serverPod()
            .resources(new V1ResourceRequirements()
                .requests(new HashMap<>())
                .limits(new HashMap<>()));

    domain.spec().replicas(replicaCount);
    createDomainAndVerify(domain, domainNamespace);

    checkServerPodsAndServiceReady();

    return domain;
  }

  private static void checkServerPodsAndServiceReady() {
    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPodPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodPrefix + i, domainUid, domainNamespace);
    }
  }
  
  private Callable<Boolean> checkEvictionEvent(String adminServerpodName,
      String reason, String message, String type) throws ApiException {
    return (() -> {
      boolean gotEvent = false;
      List<CoreV1Event> events = Kubernetes.listNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        if (event.getType() != null && event.getType().equals(type)
            && event.getInvolvedObject().getName() != null
            && event.getInvolvedObject().getName().equals(adminServerpodName)
            && event.getReason() != null
            && event.getReason().equals(reason)
            && event.getMessage() != null
            && event.getMessage().contains(message)) {
          logger.info(Yaml.dump(event));
          gotEvent = true;
        }
      }
      return gotEvent;
    });
  }
}

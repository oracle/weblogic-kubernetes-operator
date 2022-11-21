// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.DomainUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_ROLLING_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_CYCLE_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkEvent;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test pods are restarted after the following properties in server pods are changed.
 * Change: The env property tested: "-Dweblogic.StdoutDebugEnabled=false" --> "-Dweblogic.StdoutDebugEnabled=true
 * Change: imagePullPolicy: IfNotPresent --> imagePullPolicy: Always(If non kind, otherwise Never).
 * Change: podSecurityContext: runAsUser:0 --> runAsUser: 1000
 * Add resources: limits: cpu: "1", resources: requests: cpu: "0.5".
 *
 */
@DisplayName("Test pods are restarted after some properties in server pods are changed")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-srg")
class ItPodsRestart {

  private static String miiImage;

  private static String opNamespace;
  private static String domainNamespace = null;

  // domain constants
  private static final String domainUid = "domain1";
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 1;
  private static final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static LoggingFacade logger = null;
  private Map<String, OffsetDateTime> podsWithTimeStamps = null;

  /**
   * Get namespaces for operator and WebLogic domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

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
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   */
  @Test
  @DisplayName("Verify server pods are restarted by changing the resources")
  void testServerPodsRestartByChangingResource() {

    // get the original domain resource before update
    DomainResource domain1 = DomainUtils.getAndValidateInitialDomain(domainNamespace, domainUid);
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

    podsWithTimeStamps = getPodsWithTimeStamps();
    // add the new server pod compute resources limits: cpu: 1, requests: cpu: 0.5
    BigDecimal cpuLimit = new BigDecimal(1);
    BigDecimal cpuRequest = new BigDecimal(0.5);

    // verify if cpu limit was set then the new value should be different than the original value
    if (limits.get("cpu") != null) {
      assertNotEquals(0, limits.get("cpu").getNumber().compareTo(cpuLimit),
          String.format("server pod compute resources cpu limit is already set to %s, set cpu limit to "
              + "a different value", cpuLimit));
    }

    // verify if cpu request was set then the new value should be different than the original value
    if (requests.get("cpu") != null) {
      assertNotEquals(0, requests.get("cpu").getNumber().compareTo(cpuRequest),
          String.format("server pod compute resources cpu request is already set to %s, set cpu request to "
              + "a different value", cpuRequest));
    }

    //get current timestamp before domain rolling restart to verify domain roll events
    OffsetDateTime timestamp = now();

    // add/modify the server pod resources by patching the domain custom resource
    assertTrue(addServerPodResources(cpuLimit, cpuRequest),
        String.format("Failed to add server pod compute resources for domain %s in namespace %s",
            domainUid, domainNamespace));

    // get the patched domain custom resource
    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));

    assertNotNull(domain1, "Got null domain resource after patching");
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
    assertEquals(0, limits.get("cpu").getNumber().compareTo(cpuLimit),
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
    assertEquals(0, requests.get("cpu").getNumber().compareTo(cpuRequest),
        String.format("server pod compute resources requests was not updated correctly, set cpu request to %s, got %s",
            cpuRequest, requests.get("cpu").getNumber()));

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    //verify the resource change causes the domain restart and domain roll events to be logged
    logger.info("verify domain roll starting/pod cycle starting/domain roll completed events are logged");
    verifyDomainRollAndPodCycleEvents(timestamp);

  }

  private void verifyDomainRollAndPodCycleEvents(OffsetDateTime timestamp) {
    checkEvent(opNamespace, domainNamespace, domainUid, DOMAIN_ROLL_STARTING,
        "Normal", timestamp, withStandardRetryPolicy);
    checkEvent(opNamespace, domainNamespace, domainUid, POD_CYCLE_STARTING,
        "Normal", timestamp, withStandardRetryPolicy);
    logger.info("verify domain roll completed event is logged");
    checkEvent(opNamespace, domainNamespace, domainUid, DOMAIN_ROLL_COMPLETED,
        "Normal", timestamp, withStandardRetryPolicy);
    // verify that Rolling condition is removed
    testUntil(
        () -> verifyDomainStatusConditionTypeDoesNotExist(
            domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_ROLLING_TYPE),
        logger,
        "Verifying domain {0} in namespace {1} no longer has a Rolling status condition",
        domainUid,
        domainNamespace);
  }

  /**
   * Modify the domain scope property on the domain resource.
   * Verify all pods are restarted and back to ready state.
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   * The resource tested: includeServerOutInPodLog: true --> includeServerOutInPodLog: false.
   */
  @Test
  @DisplayName("Verify server pods are restarted by changing IncludeServerOutInPodLog")
  void testServerPodsRestartByChangingIncludeServerOutInPodLog() {
    // get the original domain resource before update
    DomainResource domain1 = DomainUtils.getAndValidateInitialDomain(domainNamespace, domainUid);

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps();

    //print out the original IncludeServerOutInPodLog
    Boolean includeServerOutInPodLog = domain1.getSpec().getIncludeServerOutInPodLog();
    logger.info("Original IncludeServerOutInPodLog is: {0}", includeServerOutInPodLog);

    //change includeServerOutInPodLog: true --> includeServerOutInPodLog: false
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/includeServerOutInPodLog\",")
        .append("\"value\": ")
        .append(false)
        .append("}]");
    logger.info("PatchStr for includeServerOutInPodLog: {0}", patchStr.toString());

    //get current timestamp before domain rolling restart to verify domain roll events
    OffsetDateTime timestamp = now();

    boolean cmPatched = patchDomainResource(domainUid, domainNamespace, patchStr);
    assertTrue(cmPatched, "patchDomainCustomResource(IncludeServerOutInPodLog) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

    includeServerOutInPodLog = domain1.getSpec().getIncludeServerOutInPodLog();
    logger.info("In the new patched domain IncludeServerOutInPodLog is: {0}",
        includeServerOutInPodLog);
    assertFalse(includeServerOutInPodLog, "IncludeServerOutInPodLog was not updated");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    //verify the includeServerOutInPodLog change causes the domain restart and domain roll events to be logged
    logger.info("verify domain roll starting/pod cycle starting/domain roll completed events are logged");
    verifyDomainRollAndPodCycleEvents(timestamp);
  }

  /**
   * Modify domain scope serverPod env property on the domain resource.
   * Verify all pods are restarted and back to ready state.
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   * The env property tested: "-Dweblogic.StdoutDebugEnabled=false" --> "-Dweblogic.StdoutDebugEnabled=true".
   */
  @Test
  @DisplayName("Verify server pods are restarted by changing serverPod env property")
  void testServerPodsRestartByChangingEnvProperty() {
    // get the original domain resource before update
    DomainResource domain1 = DomainUtils.getAndValidateInitialDomain(domainNamespace, domainUid);
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getEnv(), domain1 + "/spec/serverPod/env is null");

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps();

    //get current timestamp before domain rolling restart to verify domain roll events
    OffsetDateTime timestamp = now();

    //print out the original env
    List<V1EnvVar> envList = domain1.getSpec().getServerPod().getEnv();
    envList.forEach(env -> {
      logger.info("The name is: {0}, value is: {1}", env.getName(), env.getValue());
      if (env.getName().equalsIgnoreCase("JAVA_OPTIONS")
          && env.getValue().equalsIgnoreCase("-Dweblogic.StdoutDebugEnabled=false")) {
        logger.info("Change JAVA_OPTIONS to -Dweblogic.StdoutDebugEnabled=true");
        StringBuffer patchStr = null;
        patchStr = new StringBuffer("[{");
        patchStr.append("\"op\": \"replace\",")
            .append(" \"path\": \"/spec/serverPod/env/0/value\",")
            .append("\"value\": \"")
            .append("-Dweblogic.StdoutDebugEnabled=true")
            .append("\"}]");
        logger.info("PatchStr for JAVA_OPTIONS {0}", patchStr.toString());

        boolean cmPatched = patchDomainResource(domainUid, domainNamespace, patchStr);
        assertTrue(cmPatched, "patchDomainCustomResource(StdoutDebugEnabled=true) failed");
      }
    }
    );

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getEnv(), domain1 + "/spec/serverPod/env is null");

    //verify the env in the new patched domain
    envList = domain1.getSpec().getServerPod().getEnv();
    String envValue = envList.get(0).getValue();
    logger.info("In the new patched domain envValue is: {0}", envValue);
    assertTrue(envValue.equalsIgnoreCase("-Dweblogic.StdoutDebugEnabled=true"), "JAVA_OPTIONS was not updated"
        + " in the new patched domain");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    logger.info("verify domain roll starting/pod cycle starting events are logged");
    verifyDomainRollAndPodCycleEvents(timestamp);

  }

  /**
   * Add domain scope serverPod podSecurityContext on the domain resource.
   * Verify all pods are restarted and back to ready state.
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   * The tested resource: podSecurityContext: runAsUser: 1000.
   */
  @Test
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @DisplayName("Verify server pods are restarted by adding serverPod podSecurityContext")
  void testServerPodsRestartByChaningPodSecurityContext() {
    // get the original domain resource before update
    DomainResource domain1 = DomainUtils.getAndValidateInitialDomain(domainNamespace, domainUid);
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getPodSecurityContext(), domain1
        + "/spec/serverPod/podSecurityContext is null");

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps();

    //get current timestamp before domain rolling restart to verify domain roll events
    OffsetDateTime timestamp = now();

    //print out the original podSecurityContext
    logger.info("In the domain1 podSecurityContext is: " + domain1.getSpec().getServerPod().getPodSecurityContext());
    logger.info("In the original domain1 runAsUser is: {0}: ",
          domain1.getSpec().getServerPod().getPodSecurityContext().getRunAsUser());

    Long runAsUser = 1000L;
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/serverPod/podSecurityContext/runAsUser\",")
        .append("\"value\": ")
        .append(runAsUser)
        .append("}]");
    logger.info("PatchStr for podSecurityContext {0}", patchStr.toString());

    boolean cmPatched = patchDomainResource(domainUid, domainNamespace, patchStr);
    assertTrue(cmPatched, "patchDomainCustomResource(podSecurityContext) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getServerPod().getPodSecurityContext(), domain1
        + "/spec/serverPod/podSecurityContext is null");
    Long runAsUserNew = domain1.getSpec().getServerPod().getPodSecurityContext().getRunAsUser();
    assertNotNull(runAsUserNew, domain1 + "/spec/serverPod/podSecurityContext/runAsUser is null");

    //verify the runAsUser in the new patched domain
    logger.info("In the new patched domain runAsUser is: {0}", runAsUserNew);
    assertEquals(0, runAsUserNew.compareTo(runAsUser),
        String.format("podSecurityContext runAsUser was not updated correctly, set runAsUser to %s, got %s",
            runAsUser, runAsUserNew));

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    logger.info("verify domain roll starting/pod cycle starting events are logged");
    verifyDomainRollAndPodCycleEvents(timestamp);

  }

  /**
   * Modify the domain scope property on the domain resource.
   * Verify all pods are restarted and back to ready state.
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   * The resources tested: imagePullPolicy: IfNotPresent --> imagePullPolicy: Always(If non kind, otherwise Never).
   */
  @Test
  @DisplayName("Verify server pods are restarted by changing imagePullPolicy")
  void testServerPodsRestartByChangingImagePullPolicy() {
    String pullPolicy = KIND_REPO != null ? "Never" : "Always";
    // get the original domain resource before update
    DomainResource domain1 = DomainUtils.getAndValidateInitialDomain(domainNamespace, domainUid);

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps();

    //print out the original imagePullPolicy
    String imagePullPolicy = domain1.getSpec().getImagePullPolicy();
    logger.info("Original domain imagePullPolicy is: {0}", imagePullPolicy);

    //change imagePullPolicy: IfNotPresent --> imagePullPolicy: Always(If non kind, otherwise Never)
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/imagePullPolicy\",")
        .append("\"value\": \"")
        .append(pullPolicy)
        .append("\"}]");
    logger.info("PatchStr for imagePullPolicy: {0}", patchStr.toString());

    //get current timestamp before domain rolling restart to verify domain roll events
    OffsetDateTime timestamp = now();

    boolean cmPatched = patchDomainResource(domainUid, domainNamespace, patchStr);
    assertTrue(cmPatched, "patchDomainCustomResource(imagePullPolicy) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

    //print out imagePullPolicy in the new patched domain
    imagePullPolicy = domain1.getSpec().getImagePullPolicy();
    logger.info("In the new patched domain imagePullPolicy is: {0}", imagePullPolicy);
    assertTrue(imagePullPolicy.equalsIgnoreCase(pullPolicy),
        "imagePullPolicy was not updated in the new patched domain");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    logger.info("verify domain roll starting/pod cycle starting events are logged");
    verifyDomainRollAndPodCycleEvents(timestamp);

  }

  /**
   * Modify the domain scope restartVersion on the domain resource.
   * Verify all pods are restarted and back to ready state.
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   */
  @Test
  @DisplayName("Restart pods using restartVersion flag")
  @Tag("gate")
  @Tag("crio")
  void testRestartVersion() {
    // get the original domain resource before update
    DomainUtils.getAndValidateInitialDomain(domainNamespace, domainUid);

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps();

    String oldVersion = assertDoesNotThrow(()
        -> getDomainCustomResource(domainUid, domainNamespace).getSpec().getRestartVersion());
    int newVersion = oldVersion == null ? 1 : Integer.valueOf(oldVersion) + 1;

    OffsetDateTime timestamp = now();

    logger.info("patch the domain resource with new WebLogic secret, restartVersion and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/restartVersion\", "
        + "\"value\": \"" + newVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    logger.info("verify domain roll starting/pod cycle starting events are logged");
    verifyDomainRollAndPodCycleEvents(timestamp);

  }

  /**
   * Modify the image on the domain resource.
   * Verify all pods are restarted and back to ready state.
   * Verifies that the domain roll starting/pod cycle starting events are logged.
   */
  @Test
  @DisplayName("Check restart of pods after image change")
  @Tag("gate")
  @Tag("crio")
  void testRestartWithImageChange() {

    String tag = getDateAndTimeStamp();
    String newImage = MII_BASIC_IMAGE_NAME + ":" + tag;
    imageTag(miiImage, newImage);
    imageRepoLoginAndPushImageToRegistry(newImage);

    // get the original domain resource before update
    DomainUtils.getAndValidateInitialDomain(domainNamespace, domainUid);

    // get the map with server pods and their original creation timestamps
    podsWithTimeStamps = getPodsWithTimeStamps();

    OffsetDateTime timestamp = now();

    logger.info("patch the domain resource with new image");
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/image\", "
        + "\"value\": \"" + newImage + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));

    logger.info("verify domain roll starting/pod cycle starting events are logged");
    verifyDomainRollAndPodCycleEvents(timestamp);

  }

  @SuppressWarnings("unchecked")
  private <K,V> Map<K,V> getPodsWithTimeStamps() {
    // create the map with server pods and their original creation timestamps
    podsWithTimeStamps = new LinkedHashMap<>();
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
    return (Map<K,V>) podsWithTimeStamps;
  }


  /**
   * Create a model in image domain and verify the server pods are ready.
   */
  private static void createAndVerifyMiiDomain() {

    // get the pre-built image created by IntegrationTestWatcher
    miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    ServerPod srvrPod = new ServerPod()
        .addEnvItem(new V1EnvVar()
            .name("JAVA_OPTIONS")
            .value("-Dweblogic.StdoutDebugEnabled=false"))
        .addEnvItem(new V1EnvVar()
            .name("USER_MEM_ARGS")
            .value("-Djava.security.egd=file:/dev/./urandom "))
        .resources(new V1ResourceRequirements()
            .limits(new HashMap<>())
            .requests(new HashMap<>()));

    if (!OKD) {
      V1PodSecurityContext podSecCtxt = new V1PodSecurityContext()
                 .runAsUser(0L);
      srvrPod.podSecurityContext(podSecCtxt);
    }

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
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(srvrPod)
            .configuration(new Configuration()
                .introspectorJobActiveDeadlineSeconds(300L)
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));
    setPodAntiAffinity(domain);
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod exists in the domain namespace
    logger.info("Checking that admin server pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodExists(adminServerPodName, domainUid, domainNamespace);

    logger.info("Checking that admin service {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check that admin server pod is ready
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;

      // check that the managed server pod exists in the domain namespace
      logger.info("Checking that managed server pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodExists(managedServerPodName, domainUid, domainNamespace);

      // check that the managed server service exists in the domain namespace
      logger.info("Checking that managed server service {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkServiceExists(managedServerPodName, domainNamespace);

      // check that the managed server pod is ready
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReady(managedServerPodName, domainUid, domainNamespace);
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

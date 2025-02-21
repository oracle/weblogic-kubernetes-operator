// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
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
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainCondition;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.DomainStatus;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.io.File.createTempFile;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readString;
import static java.nio.file.Paths.get;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.HTTPS_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.HTTP_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.NO_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_IMAGE_DOMAINHOME_BASE_DIR;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusMessageContainsExpectedMsg;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusReasonMatches;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusServerStatusHasExpectedPodStatus;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.assertions.impl.Cluster.doesClusterExist;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.JobUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DomainUtils {

  /**
   * Create a domain in the specified namespace and wait up to five minutes until the domain exists.
   *
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param domVersion custom resource's version
   */
  public static void createDomainAndVerify(DomainResource domain,
                                           String domainNamespace,
                                           String... domVersion) {
    String domainVersion = (domVersion.length == 0) ? DOMAIN_VERSION : domVersion[0];

    LoggingFacade logger = getLogger();
    // create the domain CR
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), "domain spec is null");
    String domainUid = domain.getSpec().getDomainUid();

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain, domainVersion),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, domainVersion, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);
  }

  /**
   * Create a domain in the specified namespace, wait up to five minutes until the domain exists and
   * verify the servers are running.
   *
   * @param domainUid domain
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName admin server pod name
   * @param managedServerPodNamePrefix managed server pod prefix
   * @param replicaCount replica count
   */
  public static void createDomainAndVerify(String domainUid, DomainResource domain,
                                           String domainNamespace, String adminServerPodName,
                                           String managedServerPodNamePrefix, int replicaCount) {
    createDomainAndVerify(domainUid, domain, domainNamespace, adminServerPodName, managedServerPodNamePrefix,
        replicaCount, true);
  }

  /**
   * Create a domain in the specified namespace, wait up to five minutes until the domain exists and
   * verify the servers are running.
   *
   * @param domainUid domain
   * @param domain the oracle.weblogic.domain.DomainResource object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName admin server pod name
   * @param managedServerPodNamePrefix managed server pod prefix
   * @param replicaCount replica count
   * @param verifyServerPods whether to verify server pods of the domain
   */
  public static void createDomainAndVerify(String domainUid, DomainResource domain,
                                           String domainNamespace, String adminServerPodName,
                                           String managedServerPodNamePrefix, int replicaCount,
                                           boolean verifyServerPods) {
    LoggingFacade logger = getLogger();

    // create domain and verify
    createDomainAndVerify(domain, domainNamespace);

    if (verifyServerPods) {
      // check that admin service/pod exists in the domain namespace
      logger.info("Checking that admin service/pod {0} exists in namespace {1}",
          adminServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

      for (int i = 1; i <= replicaCount; i++) {
        String managedServerPodName = managedServerPodNamePrefix + i;

        // check that ms service/pod exists in the domain namespace
        logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }
  }

  /**
   * Check the status reason of the domainUid matches the given reason.
   *
   * @param domainUid  domain uid
   * @param namespace the namespace in which the domainUid exists
   * @param statusReason the expected status reason of the domainUid
   */
  public static void checkDomainStatusReasonMatches(String domainUid, String namespace, String statusReason) {
    LoggingFacade logger = getLogger();
    testUntil(assertDoesNotThrow(() -> domainStatusReasonMatches(domainUid, namespace, statusReason)),
        logger,
        "the status reason of the domain {0} in namespace {1} matches {2}",
        domainUid,
        namespace,
        statusReason);
  }

  /**
   * Check the status message of the domainUid contains the expected msg.
   *
   * @param domainUid  domain uid
   * @param namespace the namespace in which the domainUid exists
   * @param statusMsg the expected status msg of the domainUid
   */
  public static void checkDomainStatusMessageContainsExpectedMsg(String domainUid,
                                                                 String namespace,
                                                                 String statusMsg) {
    LoggingFacade logger = getLogger();
    testUntil(withLongRetryPolicy,
        assertDoesNotThrow(() -> domainStatusMessageContainsExpectedMsg(domainUid, namespace, statusMsg)),
        logger,
        "the status msg of the domain {0} in namespace {1} contains {2}",
        domainUid,
        namespace,
        statusMsg);
  }

  /**
   * Check the domain status condition has expected status value.
   * @param domainUid Uid of the domain
   * @param namespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param expectedStatus the expected value of the status, either True or False
   */
  public static void checkDomainStatusConditionTypeHasExpectedStatus(String domainUid,
                                                                     String namespace,
                                                                     String conditionType,
                                                                     String expectedStatus) {
    checkDomainStatusConditionTypeHasExpectedStatus(domainUid, namespace,
        conditionType, expectedStatus, DOMAIN_VERSION);
  }


  /**
   * Check the domain status condition has expected status value.
   * @param domainUid Uid of the domain
   * @param namespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param expectedStatus the expected value of the status, either True or False
   * @param domainVersion version of domain
   */
  public static void checkDomainStatusConditionTypeHasExpectedStatus(String domainUid,
                                                                     String namespace,
                                                                     String conditionType,
                                                                     String expectedStatus,
                                                                     String domainVersion) {
    testUntil(
        withLongRetryPolicy,
        domainStatusConditionTypeHasExpectedStatus(domainUid, namespace, conditionType, expectedStatus, domainVersion),
        getLogger(),
        "domain status condition type {0} has expected status {1}",
        conditionType,
        expectedStatus);
  }

  /**
   * Check the domain status condition has expected status value.
   * @param retryPolicy retry policy
   * @param domainUid Uid of the domain
   * @param namespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param expectedStatus the expected value of the status, either True or False
   * @param domainVersion version of domain
   */
  public static void checkDomainStatusConditionTypeHasExpectedStatus(ConditionFactory retryPolicy,
                                                                     String domainUid,
                                                                     String namespace,
                                                                     String conditionType,
                                                                     String expectedStatus,
                                                                     String domainVersion) {
    testUntil(
        retryPolicy,
        domainStatusConditionTypeHasExpectedStatus(domainUid, namespace, conditionType, expectedStatus, domainVersion),
        getLogger(),
        "domain status condition type {0} has expected status {1}",
        conditionType,
        expectedStatus);
  }

  /**
   * Check the domain status condition type exists.
   * @param domainUid uid of the domain
   * @param namespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   */
  public static void checkDomainStatusConditionTypeExists(String domainUid,
                                                          String namespace,
                                                          String conditionType) {
    checkDomainStatusConditionTypeExists(domainUid, namespace, conditionType, DOMAIN_VERSION);
  }

  /**
   * Check the domain status condition type exists.
   * @param domainUid uid of the domain
   * @param namespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param domainVersion   version of domain
   */
  public static void checkDomainStatusConditionTypeExists(String domainUid,
                                                          String namespace,
                                                          String conditionType,
                                                          String domainVersion) {
    testUntil(
        domainStatusConditionTypeExists(domainUid, namespace, conditionType, domainVersion),
        getLogger(),
        "waiting for domain status condition type {0} exists",
        conditionType
    );
  }

  /**
   * Check the domain status condition type exists.
   * @param domainUid uid of the domain
   * @param namespace namespace of the domain
   * @param serverName name of the server
   * @param podPhase phase of the server pod
   * @param podReady status of the pod Ready condition
   */
  public static void checkServerStatusPodPhaseAndPodReady(String domainUid,
                                                          String namespace,
                                                          String serverName,
                                                          String podPhase,
                                                          String podReady) {
    domainStatusServerStatusHasExpectedPodStatus(domainUid, namespace, serverName, podPhase, podReady);
  }

  /**
   * Check the domain status condition type does not exist.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @return true if the condition type does not exist, false otherwise
   */
  public static boolean verifyDomainStatusConditionTypeDoesNotExist(String domainUid,
                                                                    String domainNamespace,
                                                                    String conditionType) {
    return verifyDomainStatusConditionTypeDoesNotExist(domainUid, domainNamespace,
        conditionType, DOMAIN_VERSION);
  }

  /**
   * Check the domain status condition type does not exist.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param domainVersion version of domain
   * @return true if the condition type does not exist, false otherwise
   */
  public static boolean verifyDomainStatusConditionTypeDoesNotExist(String domainUid,
                                                                    String domainNamespace,
                                                                    String conditionType,
                                                                    String domainVersion) {
    DomainResource domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace,
              domainVersion));

    if (domain != null && domain.getStatus() != null) {
      List<DomainCondition> domainConditionList = domain.getStatus().getConditions();
      for (DomainCondition domainCondition : domainConditionList) {
        if (domainCondition.getType().equalsIgnoreCase(conditionType)) {
          return false;
        }
      }
    } else {
      if (domain == null) {
        getLogger().info("domain is null");
      } else {
        getLogger().info("domain status is null");
      }
    }
    return true;
  }

  /**
   * Delete a domain in the specified namespace.
   * @param domainNS the namespace in which the domain exists
   * @param domainUid domain uid
   */
  public static void deleteDomainResource(String domainNS, String domainUid) {
    //clean up domain resources in namespace and set namespace to label , managed by operator
    getLogger().info("deleting domain custom resource {0} in namespace {1}", domainUid, domainNS);
    assertTrue(deleteDomainCustomResource(domainUid, domainNS));

    // wait until domain was deleted
    testUntil(
        withLongRetryPolicy,
        domainDoesNotExist(domainUid, DOMAIN_VERSION, domainNS),
        getLogger(),
        "domain {0} to be deleted in namespace {1}",
        domainUid,
        domainNS);
  }

  /**
   * Remove cluster reference from domain resource.
   *
   * @param clusterName name of the cluster to be removed from domain resource
   * @param domainUid name of the domain resource
   * @param namespace namespace in which domain resource exists
   * @throws ApiException throws when cluster interaction fails
   */
  public static void removeClusterInDomainResource(String clusterName, String domainUid, String namespace)
      throws ApiException {
    LoggingFacade logger = getLogger();
    logger.info("Removing the cluster {0} from domain resource {1}", clusterName, domainUid);
    DomainResource domainCustomResource = getDomainCustomResource(domainUid, namespace);
    Optional<V1LocalObjectReference> cluster = domainCustomResource.getSpec()
        .getClusters().stream().filter(o -> o.getName() != null && o.getName().equals(clusterName)).findAny();
    int clusterIndex = -1;
    if (cluster.isPresent()) {
      clusterIndex = domainCustomResource.getSpec().getClusters().indexOf(cluster.get());
      logger.info("Cluster index is {0}", clusterIndex);
    } else {
      logger.warning("Cluster {0} not found in domain resource {1} in namespace {2}", 
          clusterName, domainUid, namespace);
    }
    if (clusterIndex != -1) {
      String patchStr = "[{\"op\": \"remove\",\"path\": \"/spec/clusters/" + clusterIndex + "\"}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      Callable<Boolean> clusterNotFound = () -> getDomainCustomResource(domainUid, namespace).getSpec()
          .getClusters().stream().noneMatch(c -> c.getName() != null && c.getName().equals(clusterName));
      testUntil(clusterNotFound, logger, "cluster {0} to be removed from domain resource in namespace {1}",
          clusterName, namespace);
    } else {
      logger.warning("Cluster {0} not found in domain resource {1} in namespace {2}", 
          clusterName, domainUid, namespace);
    }
  }
  
  /**
   * Patch a domain with auxiliary image and verify pods are rolling restarted.
   * @param oldImageName old auxiliary image name
   * @param newImageName new auxiliary image name
   * @param domainUid uid of the domain
   * @param domainNamespace domain namespace
   * @param replicaCount replica count to verify
   */
  public static void patchDomainWithAuxiliaryImageAndVerify(String oldImageName, String newImageName,
                                                            String domainUid, String domainNamespace,
                                                            int replicaCount) {
    patchDomainWithAuxiliaryImageAndVerify(oldImageName, newImageName, domainUid,
        domainNamespace, true, replicaCount);
  }

  /**
   * Patch a domain with auxiliary image and verify pods are rolling restarted.
   *  @param oldImageName         old auxiliary image name
   * @param newImageName         new auxiliary image name
   * @param domainUid            uid of the domain
   * @param domainNamespace      domain namespace
   * @param verifyRollingRestart verify if the pods are rolling restarted
   * @param replicaCount replica count to verify
   */
  public static void patchDomainWithAuxiliaryImageAndVerify(String oldImageName, String newImageName,
                                                            String domainUid, String domainNamespace,
                                                            boolean verifyRollingRestart, int replicaCount) {

    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";
    Map<String, OffsetDateTime> podsWithTimeStamps = null;
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource ");
    assertNotNull(domain1.getSpec().getConfiguration().getModel().getAuxiliaryImages(),
        domain1 + "/spec/serverPod/auxiliaryImages is null");
    List<AuxiliaryImage> auxiliaryImageList = domain1.getSpec().getConfiguration().getModel().getAuxiliaryImages();
    assertFalse(auxiliaryImageList.isEmpty(), "AuxiliaryImage list is empty");

    String searchString;
    int index;

    AuxiliaryImage ai = auxiliaryImageList.stream()
        .filter(auxiliaryImage -> oldImageName.equals(auxiliaryImage.getImage()))
        .findAny()
        .orElse(null);
    assertNotNull(ai, "Can't find auxiliary image with Image name " + oldImageName
        + "can't patch domain " + domainUid);

    index = auxiliaryImageList.indexOf(ai);
    searchString = "\"/spec/configuration/model/auxiliaryImages/" + index + "/image\"";
    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": " + searchString + ",")
        .append(" \"value\":  \"" + newImageName + "\"")
        .append(" }]");
    getLogger().info("Auxiliary Image patch string: " + patchStr);

    //get current timestamp before domain rolling restart to verify domain roll events
    if (verifyRollingRestart) {
      podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace, adminServerPodName,
          managedServerPrefix, replicaCount);
    }
    V1Patch patch = new V1Patch((patchStr).toString());

    boolean aiPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(Auxiliary Image)  failed ");
    assertTrue(aiPatched, "patchDomainCustomResource(auxiliary image) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + " /spec is null");
    assertNotNull(domain1.getSpec().getServerPod(), domain1 + " /spec/serverPod is null");
    assertNotNull(domain1.getSpec().getConfiguration().getModel().getAuxiliaryImages(),
        domain1 + "/spec/serverPod/auxiliaryImages is null");

    //verify the new auxiliary image in the new patched domain
    auxiliaryImageList = domain1.getSpec().getConfiguration().getModel().getAuxiliaryImages();

    String auxiliaryImage = auxiliaryImageList.get(index).getImage();
    getLogger().info("In the new patched domain, imageValue is: {0}", auxiliaryImage);
    assertTrue(auxiliaryImage.equalsIgnoreCase(newImageName), "auxiliary image was not updated"
        + " in the new patched domain");

    // verify the server pods are rolling restarted and back to ready state
    getLogger().info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);

    if (verifyRollingRestart) {
      assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
          String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));
    }
  }

  /**
   * Patch a running domain with introspectVersion.
   * If the introspectVersion doesn't exist it will add the value as 2,
   * otherwise the value is updated by 1.
   *
   * @param domainUid UID of the domain to patch with introspectVersion
   * @param namespace namespace in which the domain resource exists
   * @return introspectVersion new introspectVersion of the domain resource
   */
  public static String patchDomainResourceWithNewIntrospectVersion(String domainUid, String namespace) {
    LoggingFacade logger = getLogger();
    StringBuffer patchStr;
    DomainResource res = assertDoesNotThrow(
        () -> getDomainCustomResource(domainUid, namespace),
            String.format("Failed to get the introspectVersion of %s in namespace %s", domainUid, namespace));
    int introspectVersion = 2;
    // construct the patch string
    if (res.getSpec().getIntrospectVersion() == null) {
      patchStr = new StringBuffer("[{")
          .append("\"op\": \"add\", ")
          .append("\"path\": \"/spec/introspectVersion\", ")
          .append("\"value\": \"")
          .append(introspectVersion)
          .append("\"}]");
    } else {
      introspectVersion = Integer.parseInt(res.getSpec().getIntrospectVersion()) + 1;
      patchStr = new StringBuffer("[{")
          .append("\"op\": \"replace\", ")
          .append("\"path\": \"/spec/introspectVersion\", ")
          .append("\"value\": \"")
          .append(introspectVersion)
          .append("\"}]");
    }

    logger.info("Patch String \n{0}", patchStr);
    logger.info("Adding/updating introspectVersion in domain {0} in namespace {1} using patch string: {2}",
        domainUid, namespace, patchStr.toString());

    // patch the domain
    V1Patch patch = new V1Patch(new String(patchStr));
    boolean ivPatched = patchDomainCustomResource(domainUid, namespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
    assertTrue(ivPatched, "patchDomainCustomResource(introspectVersion) failed");

    return String.valueOf(introspectVersion);
  }

  /**
   * Create a domain in PV using WDT.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  public static DomainResource createDomainOnPvUsingWdt(String domainUid,
                                                        String domainNamespace,
                                                        String wlSecretName,
                                                        String clusterName,
                                                        int replicaCount,
                                                        String testClassName) {

    int t3ChannelPort = getNextFreePort();

    final String pvName = getUniqueName(domainUid + "-pv-"); // name of the persistent volume
    final String pvcName = getUniqueName(domainUid + "-pvc-"); // name of the persistent volume claim

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing

    createPV(pvName, domainUid, testClassName);
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    String labelSelector = String.format("weblogic.domainUid in (%s)", domainUid);
    LoggingFacade logger = getLogger();
    // check the persistent volume and persistent volume claim exist
    testUntil(
            assertDoesNotThrow(() -> pvExists(pvName, labelSelector),
                    String.format("pvExists failed with ApiException when checking pv %s", pvName)),
            logger,
            "persistent volume {0} exists",
            pvName);

    testUntil(
            assertDoesNotThrow(() -> pvcExists(pvcName, domainNamespace),
                    String.format("pvcExists failed with ApiException when checking pvc %s in namespace %s",
                            pvcName, domainNamespace)),
            logger,
            "persistent volume claim {0} exists in namespace {1}",
            pvcName,
            domainNamespace);


    // create a temporary WebLogic domain property file as a input for WDT model file
    File domainPropertiesFile =
        assertDoesNotThrow(() -> createTempFile("domainonpv" + domainUid, ".properties", new File(RESULTS_TEMPFILE)),
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
    p.setProperty("adminServerSslPort", "7002");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
        "Failed to write domain properties file");

    // shell script to download WDT and run the WDT createDomain script
    Path wdtScript = get(RESOURCE_DIR, "bash-scripts", "setup_wdt.sh");
    // WDT model file containing WebLogic domain configuration
    Path wdtModelFile = get(RESOURCE_DIR, "wdt-models", "domain-onpv-wdt-model.yaml");

    // create configmap and domain in persistent volume using WDT
    runCreateDomainOnPVJobUsingWdt(wdtScript, wdtModelFile, domainPropertiesFile.toPath(),
        domainUid, pvName, pvcName, domainNamespace, testClassName);

    DomainResource domain = createDomainResourceForDomainOnPV(domainUid, domainNamespace, wlSecretName, pvName, pvcName,
        clusterName, replicaCount);

    // Verify the domain custom resource is created.
    // Also verify the admin server pod and managed server pods are up and running.
    createDomainAndVerify(domainUid, domain, domainNamespace, domainUid + "-" + ADMIN_SERVER_NAME_BASE,
        domainUid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount);

    return domain;
  }

  /**
   * Create a domain in PV using WDT.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace namespace in which the domain will be created
   * @param wlSecretName WLS secret name
   * @param clusterName WLS domain cluster name
   * @param replicaCount domain replica count
   * @param testClassName the test class name calling this method
   * @param wdtModelFile WDT model file to create the domain
   * @param verifyServerPods whether to verify the server pods
   * @return oracle.weblogic.domain.Domain objects
   */
  public static DomainResource createDomainOnPvUsingWdt(String domainUid,
                                                        String domainNamespace,
                                                        String wlSecretName,
                                                        String clusterName,
                                                        int replicaCount,
                                                        String testClassName,
                                                        String wdtModelFile,
                                                        boolean verifyServerPods) {
    return createDomainOnPvUsingWdt(domainUid, domainNamespace, wlSecretName, clusterName, clusterName,
        replicaCount, testClassName, wdtModelFile, verifyServerPods);
  }

  /**
   * Create a domain in PV using WDT.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace namespace in which the domain will be created
   * @param wlSecretName WLS secret name
   * @param clusterResName cluster resource name
   * @param clusterName WLS domain cluster name
   * @param replicaCount domain replica count
   * @param testClassName the test class name calling this method
   * @param wdtModelFile WDT model file to create the domain
   * @param verifyServerPods whether to verify the server pods
   * @return oracle.weblogic.domain.Domain objects
   */
  public static DomainResource createDomainOnPvUsingWdt(String domainUid,
                                                        String domainNamespace,
                                                        String wlSecretName,
                                                        String clusterResName,
                                                        String clusterName,
                                                        int replicaCount,
                                                        String testClassName,
                                                        String wdtModelFile,
                                                        boolean verifyServerPods) {

    int t3ChannelPort = getNextFreePort();

    final String pvName = getUniqueName(domainUid + "-pv-"); // name of the persistent volume
    final String pvcName = getUniqueName(domainUid + "-pvc-"); // name of the persistent volume claim

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing

    createPV(pvName, domainUid, testClassName);
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    String labelSelector = String.format("weblogic.domainUid in (%s)", domainUid);
    LoggingFacade logger = getLogger();
    // check the persistent volume and persistent volume claim exist
    testUntil(
        assertDoesNotThrow(() -> pvExists(pvName, labelSelector),
            String.format("pvExists failed with ApiException when checking pv %s", pvName)),
        logger,
        "persistent volume {0} exists",
        pvName);

    testUntil(
        assertDoesNotThrow(() -> pvcExists(pvcName, domainNamespace),
            String.format("pvcExists failed with ApiException when checking pvc %s in namespace %s",
                pvcName, domainNamespace)),
        logger,
        "persistent volume claim {0} exists in namespace {1}",
        pvcName,
        domainNamespace);


    // create a temporary WebLogic domain property file as a input for WDT model file
    File domainPropertiesFile =
        assertDoesNotThrow(() -> createTempFile("domainonpv" + domainUid, ".properties", new File(RESULTS_TEMPFILE)),
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
    p.setProperty("adminServerSslPort", "7002");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
        "Failed to write domain properties file");

    // shell script to download WDT and run the WDT createDomain script
    Path wdtScript = get(RESOURCE_DIR, "bash-scripts", "setup_wdt.sh");

    // create configmap and domain in persistent volume using WDT
    runCreateDomainOnPVJobUsingWdt(wdtScript, get(RESOURCE_DIR, "wdt-models", wdtModelFile),
        domainPropertiesFile.toPath(),
        domainUid, pvName, pvcName, domainNamespace, testClassName);

    DomainResource domain = createDomainResourceForDomainOnPV(domainUid, domainNamespace, wlSecretName, pvName, pvcName,
        clusterResName, clusterName, replicaCount);

    // Verify the domain custom resource is created.
    // Also verify the admin server pod and managed server pods are up and running.
    if (verifyServerPods) {
      createDomainAndVerify(domainUid, domain, domainNamespace, domainUid + "-" + ADMIN_SERVER_NAME_BASE,
          domainUid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount);
    } else {
      createDomainAndVerify(domain, domainNamespace);
    }
    return domain;
  }

  /**
   * Create domain with domain-on-pv type and verify the domain is created.
   * Also verify the admin server pod and managed server pods are up and running.
   * @param domainUid - domain uid
   * @param domainNamespace - domain namespace
   * @param wlSecretName - wls administrator secret name
   * @param pvName - PV name
   * @param pvcName - PVC name
   * @param clusterName - cluster name
   * @param replicaCount - repica count of the clsuter
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createDomainResourceForDomainOnPV(String domainUid,
                                                                 String domainNamespace,
                                                                 String wlSecretName,
                                                                 String pvName,
                                                                 String pvcName,
                                                                 String clusterName,
                                                                 int replicaCount) {
    return createDomainResourceForDomainOnPV(domainUid, domainNamespace, wlSecretName, pvName, pvcName,
        clusterName, clusterName, replicaCount);
  }

  /**
   * Create domain with domain-on-pv type and verify the domain is created.
   * Also verify the admin server pod and managed server pods are up and running.
   * @param domainUid - domain uid
   * @param domainNamespace - domain namespace
   * @param wlSecretName - wls administrator secret name
   * @param pvName - PV name
   * @param pvcName - PVC name
   * @param clusterResName - cluster resource name
   * @param clusterName - cluster name
   * @param replicaCount - repica count of the clsuter
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createDomainResourceForDomainOnPV(String domainUid,
                                                                 String domainNamespace,
                                                                 String wlSecretName,
                                                                 String pvName,
                                                                 String pvcName,
                                                                 String clusterResName,
                                                                 String clusterName,
                                                                 int replicaCount) {
    String uniquePath = "/u01/shared/" + domainNamespace + "/domains/" + domainUid;

    // create the domain custom resource
    getLogger().info("Creating domain custom resource");
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(uniquePath)
            .replicas(replicaCount)
            .domainHomeSourceType("PersistentVolume")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .imagePullSecrets(Collections.singletonList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME)))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome(uniquePath + "/logs")
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
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
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort())))));

    // create cluster resource for the domain
    if (!Cluster.doesClusterExist(clusterResName, CLUSTER_VERSION, domainNamespace)) {
      ClusterResource cluster = createClusterResource(clusterResName,
          clusterName, domainNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    setPodAntiAffinity(domain);

    return domain;
  }

  /**
   *  Utility to create domain resource on pv with configuration.
   * @param domainUid domain uid
   * @param domNamespace  domain namespace
   * @param adminSecretName wls admin secret name
   * @param clusterName cluster name
   * @param pvName PV name
   * @param pvcName PVC name
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param domainInHomePrefix domain in home prefix
   * @param replicaCount repica count of the clsuter
   * @param t3ChannelPort t3 chanel
   * @param configuration domain configuratioin object
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createDomainResourceOnPv(String domainUid,
                                                  String domNamespace,
                                                  String adminSecretName,
                                                  String clusterName,
                                                  String pvName,
                                                  String pvcName,
                                                  String[] repoSecretName,
                                                  String domainInHomePrefix,
                                                  int replicaCount,
                                                  int t3ChannelPort,
                                                  Configuration configuration) {
    return createDomainResourceOnPv(domainUid,
        domNamespace,
        adminSecretName,
        clusterName,
        pvName,
        pvcName,
        repoSecretName,
        domainInHomePrefix,
        replicaCount,
        t3ChannelPort,
        configuration,
        FMWINFRA_IMAGE_TO_USE_IN_SPEC);
  }
  
  /**
   *  Utility to create domain resource on pv with configuration.
   * @param domainUid domain uid
   * @param domNamespace  domain namespace
   * @param adminSecretName wls admin secret name
   * @param clusterName cluster name
   * @param pvName PV name
   * @param pvcName PVC name
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param domainInHomePrefix domain in home prefix
   * @param replicaCount repica count of the clsuter
   * @param t3ChannelPort t3 chanel
   * @param configuration domain configuratioin object
   * @param imageToUse base image to use
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createDomainResourceOnPv(String domainUid,
                                                  String domNamespace,
                                                  String adminSecretName,
                                                  String clusterName,
                                                  String pvName,
                                                  String pvcName,
                                                  String[] repoSecretName,
                                                  String domainInHomePrefix,
                                                  int replicaCount,
                                                  int t3ChannelPort,
                                                  Configuration configuration,
                                                  String imageToUse) {

    // create secrets
    List<V1LocalObjectReference> secrets = new ArrayList<>();
    for (String secret : repoSecretName) {
      secrets.add(new V1LocalObjectReference().name(secret));
    }
    
    // create a domain custom resource configuration object
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(domainInHomePrefix + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(imageToUse)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domNamespace + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom"))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .configuration(configuration));
    domain.spec().setImagePullSecrets(secrets);

    // create cluster resource for the domain
    String clusterResName  = domainUid + "-" + clusterName;
    if (!Cluster.doesClusterExist(clusterResName, CLUSTER_VERSION, domNamespace)) {
      ClusterResource cluster = createClusterResource(clusterResName,
          clusterName, domNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    return domain;
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
   * @param testClassName the test class name which calls this method
   */
  private static void runCreateDomainOnPVJobUsingWdt(Path domainCreationScriptFile,
                                                     Path modelFile,
                                                     Path domainPropertiesFile,
                                                     String domainUid,
                                                     String pvName,
                                                     String pvcName,
                                                     String namespace,
                                                     String testClassName) {
    getLogger().info("Preparing to run create domain job using WDT");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(domainCreationScriptFile);
    domainScriptFiles.add(domainPropertiesFile);
    domainScriptFiles.add(modelFile);
    
    String uniquePath = "/u01/shared/" + namespace + "/domains/" + domainUid;

    getLogger().info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm-" + testClassName.toLowerCase();
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles, namespace, testClassName),
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
            .value(uniquePath));

    if (HTTP_PROXY != null) {
      jobCreationContainer.addEnvItem(new V1EnvVar().name("http_proxy").value(HTTP_PROXY));
    }
    if (HTTPS_PROXY != null) {
      jobCreationContainer.addEnvItem(new V1EnvVar().name("https_proxy").value(HTTPS_PROXY));
    }
    if (NO_PROXY != null) {
      jobCreationContainer.addEnvItem(new V1EnvVar().name("no_proxy").value(HTTPS_PROXY));
    }

    getLogger().info("Running a Kubernetes job to create the domain");
    createDomainJob(pvName, pvcName, domainScriptConfigMapName, namespace, jobCreationContainer);
  }

  /**
   * Create ConfigMap containing domain creation scripts.
   *
   * @param configMapName name of the ConfigMap to create
   * @param files files to add in ConfigMap
   * @param namespace name of the namespace in which to create ConfigMap
   * @param testClassName the test class name to call this method
   * @throws IOException when reading the domain script files fail
   */
  private static void createConfigMapForDomainCreation(String configMapName,
                                                       List<Path> files,
                                                       String namespace,
                                                       String testClassName)
      throws IOException {
    getLogger().info("Creating ConfigMap {0}", configMapName);

    Path domainScriptsDir = createDirectories(
        get(TestConstants.LOGS_DIR, testClassName, namespace));

    // add domain creation scripts and properties files to the configmap
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      getLogger().info("Adding file {0} in ConfigMap", file);
      data.put(file.getFileName().toString(), readString(file));
      getLogger().info("Making a copy of file {0} to {1} for diagnostic purposes", file,
          domainScriptsDir.resolve(file.getFileName()));
      copy(file, domainScriptsDir.resolve(file.getFileName()), REPLACE_EXISTING);
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
    getLogger().info("Running Kubernetes job to create domain");
    V1PodSpec podSpec = new V1PodSpec()
        .restartPolicy("Never")
        .addContainersItem(jobContainer  // container containing WLST or WDT details
               .name("create-weblogic-domain-onpv-container")
                        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                        .imagePullPolicy(IMAGE_PULL_POLICY)
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
                            .name(BASE_IMAGES_REPO_SECRET_NAME)));  // this secret is used only for non-kind cluster
    if (!OKD) {
      podSpec.initContainers(Arrays.asList(createfixPVCOwnerContainer(pvName, "/u01/shared")));
    }

    V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec();
    podTemplateSpec.spec(podSpec);
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job-" + pvName) // name of the create domain job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(podTemplateSpec));

    String jobName = createJobAndWaitUntilComplete(jobBody, namespace);

    // check job status and fail test if the job failed to create domain
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null && job.getStatus() != null && job.getStatus().getConditions() != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equals(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        getLogger().severe("Job {0} failed to create domain", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty() && pods.get(0) != null && pods.get(0).getMetadata() != null
            && pods.get(0).getMetadata().getName() != null) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          getLogger().severe(podLog);
          fail("Domain create job failed");
        }
      }
    }
  }

  /**
   * Create a WebLogic domain in image using WDT.
   *
   * @param domainUid domain uid
   * @param domainNamespace namespace in which the domain to be created
   * @param wdtModelFileForDomainInImage WDT model file used to create domain image
   * @param appSrcDirList list of the app src in WDT model file
   * @param wlSecretName wls admin secret name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createAndVerifyDomainInImageUsingWdt(String domainUid,
                                                                    String domainNamespace,
                                                                    String wdtModelFileForDomainInImage,
                                                                    List<String> appSrcDirList,
                                                                    String wlSecretName,
                                                                    String clusterName,
                                                                    int replicaCount) {

    // create secret for admin credentials
    getLogger().info("Create secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create image with model files
    getLogger().info("Creating image with model file and verify");
    String domainInImageWithWDTImage = createImageAndVerify("domaininimage-wdtimage",
        Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForDomainInImage), appSrcDirList,
        Collections.singletonList(MODEL_DIR + "/" + WDT_BASIC_MODEL_PROPERTIES_FILE),
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false,
        domainUid, false);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(domainInImageWithWDTImage);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    return createDomainInImageAndVerify(domainUid, domainNamespace, domainInImageWithWDTImage, wlSecretName,
        clusterName, replicaCount);
  }

  /**
   * Create a WebLogic domain in image using WDT.
   *
   * @param domainUid domain uid
   * @param domainNamespace namespace in which the domain to be created
   * @param wdtModelFileForDomainInImage WDT model file used to create domain image
   * @param appSrcDirList list of the app src in WDT model file
   * @param propertyFiles list of property files
   * @param wlSecretName wls admin secret name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.DomainResource object
   */
  public static DomainResource createDomainInImageUsingWdt(String domainUid,
                                                           String domainNamespace,
                                                           String wdtModelFileForDomainInImage,
                                                           List<String> appSrcDirList,
                                                           List<String> propertyFiles,
                                                           String wlSecretName,
                                                           String clusterName,
                                                           int replicaCount) {
    return createDomainInImageUsingWdt(domainUid, domainNamespace, wdtModelFileForDomainInImage, appSrcDirList,
        propertyFiles, wlSecretName, clusterName, clusterName, replicaCount);
  }

  /**
   * Create a WebLogic domain in image using WDT.
   *
   * @param domainUid domain uid
   * @param domainNamespace namespace in which the domain to be created
   * @param wdtModelFileForDomainInImage WDT model file used to create domain image
   * @param appSrcDirList list of the app src in WDT model file
   * @param propertyFiles list of property files
   * @param wlSecretName wls admin secret name
   * @param clusterResName cluster resource name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.DomainResource object
   */
  public static DomainResource createDomainInImageUsingWdt(String domainUid,
                                                           String domainNamespace,
                                                           String wdtModelFileForDomainInImage,
                                                           List<String> appSrcDirList,
                                                           List<String> propertyFiles,
                                                           String wlSecretName,
                                                           String clusterResName,
                                                           String clusterName,
                                                           int replicaCount) {

    // create secret for admin credentials
    getLogger().info("Create secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create image with model files
    getLogger().info("Creating image with model file and verify");
    String domainInImageWithWDTImage = createImageAndVerify("domaininimage-wdtimage",
        Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForDomainInImage), appSrcDirList,
        propertyFiles,
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false,
        domainUid, false);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(domainInImageWithWDTImage);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create the domain custom resource
    DomainResource domain = createDomainResourceForDomainInImage(domainUid, domainNamespace, domainInImageWithWDTImage,
        wlSecretName, clusterResName, clusterName, replicaCount);

    // create domain and verify
    createDomainAndVerify(domain, domainNamespace);

    return domain;
  }

  /**
   * Create domain resource with domain-in-image type.
   *
   * @param domainUid domain uid
   * @param domainNamespace domain namespace
   * @param imageName image name used to create domain-in-image domain
   * @param wlSecretName wls admin secret name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createDomainResourceForDomainInImage(String domainUid,
                                                                    String domainNamespace,
                                                                    String imageName,
                                                                    String wlSecretName,
                                                                    String clusterName,
                                                                    int replicaCount,
                                                                    Long... failureRetryLimitMinutesArgs) {
    return createDomainResourceForDomainInImage(domainUid, domainNamespace, imageName, wlSecretName,
        clusterName, clusterName, replicaCount, failureRetryLimitMinutesArgs);
  }

  /**
   * Create domain resource with domain-in-image type.
   *
   * @param domainUid domain uid
   * @param domainNamespace domain namespace
   * @param imageName image name used to create domain-in-image domain
   * @param wlSecretName wls admin secret name
   * @param clusterResName cluster resource name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createDomainResourceForDomainInImage(String domainUid,
                                                                    String domainNamespace,
                                                                    String imageName,
                                                                    String wlSecretName,
                                                                    String clusterResName,
                                                                    String clusterName,
                                                                    int replicaCount,
                                                                    Long... failureRetryLimitMinutesArgs) {
    Long failureRetryLimitMinutes =
        (failureRetryLimitMinutesArgs.length == 0) ? FAILURE_RETRY_LIMIT_MINUTES : failureRetryLimitMinutesArgs[0];

    // create the domain custom resource
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(WDT_IMAGE_DOMAINHOME_BASE_DIR + "/" + domainUid)
            .dataHome("/u01/mydata")
            .domainHomeSourceType("Image")
            .replicas(replicaCount)
            .image(imageName)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(failureRetryLimitMinutes)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
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
                    .domainType(WLS_DOMAIN_TYPE))
                .introspectorJobActiveDeadlineSeconds(3000L)));

    // create cluster resource for the domain
    if (!Cluster.doesClusterExist(clusterResName, CLUSTER_VERSION, domainNamespace)) {
      ClusterResource cluster = createClusterResource(clusterResName,
          clusterName, domainNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    setPodAntiAffinity(domain);

    return domain;
  }

  /**
   * Create domain with domain-in-image type and verify the domain is created.
   * Also verify the admin server pod and managed server pods are up and running.
   * @param domainUid domain uid
   * @param domainNamespace domain namespace
   * @param imageName image name used to create domain-in-image domain
   * @param wlSecretName wls admin secret name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createDomainInImageAndVerify(String domainUid,
                                                            String domainNamespace,
                                                            String imageName,
                                                            String wlSecretName,
                                                            String clusterName,
                                                            int replicaCount) {
    // create the domain custom resource
    DomainResource domain = createDomainResourceForDomainInImage(domainUid, domainNamespace, imageName, wlSecretName,
        clusterName, replicaCount);

    createDomainAndVerify(domainUid, domain, domainNamespace, domainUid + "-" + ADMIN_SERVER_NAME_BASE,
        domainUid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount);

    return domain;
  }


  /**
   * Create model-in-image type domain resource with configMap.
   *
   * @param domainUid unique id of the WebLogic domain
   * @param domainNamespace domain namespace
   * @param clusterName names of cluster
   * @param miiImage name of the image including its tag
   * @param wlSecretName wls admin secret name
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param replicaCount replica count of the cluster
   * @param configmapName name of the configMap containing WebLogic Deploy Tooling model
   * @param introspectorDeadline seconds of introspector job active deadline
   * @param failureRetryLimitMinutesArgs the time in minutes before the operator will stop retrying Severe failures
   * @return oracle.weblogic.domain.Domain object
   */
  public static  DomainResource createMiiDomainResourceWithConfigMap(String domainUid,
                                                                     String domainNamespace,
                                                                     String clusterName,
                                                                     String wlSecretName,
                                                                     String repoSecretName,
                                                                     String encryptionSecretName,
                                                                     int replicaCount,
                                                                     String miiImage,
                                                                     String configmapName,
                                                                     Long introspectorDeadline,
                                                                     Long... failureRetryLimitMinutesArgs) {
    Long failureRetryLimitMinutes =
        (failureRetryLimitMinutesArgs.length == 0) ? FAILURE_RETRY_LIMIT_MINUTES : failureRetryLimitMinutesArgs[0];

    LoggingFacade logger = getLogger();
    String clusterResName = domainUid + "-" + clusterName;
    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("testkey", "testvalue");

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
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(failureRetryLimitMinutes)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverService(new ServerService()
                    .annotations(keyValueMap)
                    .labels(keyValueMap))
            .adminService(new AdminService()
                .addChannelsItem(new Channel()
                    .channelName("default")
                    .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
            .introspectorJobActiveDeadlineSeconds(introspectorDeadline != null ? introspectorDeadline : 3000L)));

    // create cluster resource for the domain
    if (!Cluster.doesClusterExist(clusterName, CLUSTER_VERSION, domainNamespace)) {
      ClusterResource cluster = createClusterResource(clusterResName, clusterName, domainNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
    logger.info("Creating cluster resource {0} in namespace {1}", clusterResName, domainNamespace);

    setPodAntiAffinity(domain);

    return domain;
  }

  /**
   * Create a domain-in-image type domain resource with configMap.
   *
   * @param domainUid unique id of the WebLogic domain
   * @param domainNamespace domain namespace
   * @param imageName image name used to create domain-in-image domain
   * @param wlSecretName wls admin secret name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @param configmapName name of the configMap
   * @param failureRetryLimitMinutesArgs the time in minutes before the operator will stop retrying Severe failures
   * @return oracle.weblogic.domain.Domain object
   */
  public static DomainResource createDomainResourceForDomainInImageWithConfigMap(String domainUid,
                                                                                 String domainNamespace,
                                                                                 String imageName,
                                                                                 String wlSecretName,
                                                                                 String clusterName,
                                                                                 int replicaCount,
                                                                                 String configmapName,
                                                                                 Long... failureRetryLimitMinutesArgs) {
    Long failureRetryLimitMinutes =
        (failureRetryLimitMinutesArgs.length == 0) ? FAILURE_RETRY_LIMIT_MINUTES : failureRetryLimitMinutesArgs[0];

    // create the domain custom resource
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(WDT_IMAGE_DOMAINHOME_BASE_DIR + "/" + domainUid)
            .dataHome("/u01/mydata")
            .domainHomeSourceType("Image")
            .replicas(replicaCount)
            .image(imageName)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(failureRetryLimitMinutes)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
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
                      .domainType(WLS_DOMAIN_TYPE)
                      .configMap(configmapName))
            .introspectorJobActiveDeadlineSeconds(3000L)));

    // create cluster resource for the domain
    if (!doesClusterExist(clusterName, CLUSTER_VERSION, domainNamespace)) {
      ClusterResource cluster = createClusterResource(clusterName, clusterName, domainNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterName));
    setPodAntiAffinity(domain);

    return domain;
  }

  /**
   * Shutdown domain and verify all the server pods were shutdown.
   *
   * @param domainNamespace the namespace where the domain exists
   * @param domainUid the uid of the domain to shutdown
   * @param replicaCount replica count of the domain cluster
   */
  public static void shutdownDomainAndVerify(String domainNamespace, String domainUid, int replicaCount) {
    // shutdown domain
    getLogger().info("Shutting down domain {0} in namespace {1}", domainUid, domainNamespace);
    shutdownDomain(domainUid, domainNamespace);

    // verify all the pods were shutdown
    getLogger().info("Verifying all server pods were shutdown for the domain");
    // check admin server pod was shutdown
    checkPodDoesNotExist(domainUid + "-" + ADMIN_SERVER_NAME_BASE,
        domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }
  }

  /**
   * Obtains the specified domain, validates that it has a spec and no rolling condition.
   * @param domainNamespace the namespace
   * @param domainUid the UID
   */
  @Nonnull
  public static DomainResource getAndValidateInitialDomain(String domainNamespace, String domainUid) {
    DomainResource domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid, domainNamespace));

    assertNotNull(domain, "Got null domain resource");
    assertNotNull(domain.getSpec(), domain + "/spec is null");
    assertFalse(domainHasRollingCondition(domain), "Found rolling condition at start of test");
    return domain;
  }

  /**
   * Obtains the specified domain, validates that it has a spec and no rolling condition.
   * @param domainNamespace the domain namespace
   * @param domainUid the domain UID
   * @param regex check string
   * @return true if regex found, false otherwise.
   */
  @Nonnull
  public static boolean findStringInDomainStatusMessage(String domainNamespace,
                                                        String domainUid,
                                                        String regex,
                                                        String... multupleMessage) {
    // get the domain status message
    StringBuffer getDomainInfoCmd = new StringBuffer(KUBERNETES_CLI + " get domain/");
    getDomainInfoCmd
        .append(domainUid)
        .append(" -n ")
        .append(domainNamespace);

    if (multupleMessage.length == 0) {
      // get single field of domain message
      getDomainInfoCmd.append(" -o jsonpath='{.status.message}' --ignore-not-found");
    } else {
      // use [,] to get side by side multiple fields of the domain status message
      getDomainInfoCmd.append(" -o jsonpath=\"{.status.conditions[*]['status', 'message']}\" --ignore-not-found");
    }

    getLogger().info("Command to get domain status message: " + getDomainInfoCmd);

    CommandParams params = new CommandParams().defaults();
    params.command(getDomainInfoCmd.toString());
    ExecResult execResult = Command.withParams(params).executeAndReturnResult();
    getLogger().info("Search: {0} in Domain status message: {1}", regex, execResult.stdout());

    // match regex in domain info
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(execResult.stdout());

    return matcher.find();
  }

  private static boolean domainHasRollingCondition(DomainResource domain) {
    return Optional.ofNullable(domain.getStatus())
          .map(DomainStatus::conditions).orElse(Collections.emptyList()).stream()
          .map(DomainCondition::getType).anyMatch("Rolling"::equals);
  }
}

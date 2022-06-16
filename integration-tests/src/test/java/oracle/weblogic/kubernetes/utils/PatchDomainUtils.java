// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Map;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainResourceCredentialsSecretPatched;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podRestartVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common utility class for domain patching tests.
 */
public class PatchDomainUtils {

  /**
   * Patch the domain resource with a new WebLogic admin credentials secret.
   *
   * @param domainResourceName name of the domain resource
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param secretName name of the new WebLogic admin credentials secret
   * @return restartVersion new restartVersion of the domain resource
   */
  public static String patchDomainResourceWithNewAdminSecret(
      String domainResourceName,
      String namespace,
      String secretName
  ) {
    LoggingFacade logger = getLogger();
    String patch = String.format(
        "[\n  {\"op\": \"replace\", \"path\": \"/spec/%s\", \"value\": \"%s\"}\n]\n",
        "webLogicCredentialsSecret/name", secretName);
    logger.info("Patch the domain resource {0} in namespace {1} with: {2}\n",
        domainResourceName, namespace, patch);

    assertTrue(patchDomainCustomResource(
        domainResourceName,
        namespace,
        new V1Patch(patch),
        V1Patch.PATCH_FORMAT_JSON_PATCH),
        String.format("Failed to patch the domain resource %s in namespace %s with %s: %s",
            domainResourceName, namespace, "/spec/webLogicCredentialsSecret/name", secretName));

    String oldVersion = assertDoesNotThrow(
        () -> getDomainCustomResource(domainResourceName, namespace).getSpec().getRestartVersion(),
        String.format("Failed to get the restartVersion of %s in namespace %s", domainResourceName, namespace));
    int newVersion = oldVersion == null ? 1 : Integer.valueOf(oldVersion) + 1;
    logger.info("Update domain resource {0} in namespace {1} restartVersion from {2} to {3}",
        domainResourceName, namespace, oldVersion, newVersion);
    patch =
        String.format("[\n  {\"op\": \"replace\", \"path\": \"/spec/restartVersion\", \"value\": \"%s\"}\n]\n",
            newVersion);

    logger.info("Patch the domain resource {0} in namespace {1} with: {2}\n",
        domainResourceName, namespace, patch);

    assertTrue(patchDomainCustomResource(
        domainResourceName,
        namespace,
        new V1Patch(patch),
        V1Patch.PATCH_FORMAT_JSON_PATCH),
        String.format("Failed to patch the domain resource %s in namespace %s with startVersion: %s",
            domainResourceName, namespace, newVersion));

    String updatedVersion = assertDoesNotThrow(
        () -> getDomainCustomResource(domainResourceName, namespace).getSpec().getRestartVersion(),
        String.format("Failed to get the restartVersion of %s in namespace %s", domainResourceName, namespace));
    logger.info("Current restartVersion is {0}", updatedVersion);
    assertEquals(String.valueOf(newVersion), updatedVersion,
        String.format("Failed to update the restartVersion of domain %s from %s to %s",
            domainResourceName,
            oldVersion,
            newVersion));
    return String.valueOf(newVersion);
  }

  /**
   * Patch domain resource with a new WebLogic domain credentials secret and a new restartVersion,
   * and verify if the domain spec has been correctly updated.
   *
   * @param domainUid name of the domain resource
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param secretName name of the secret that is used to patch the domain resource
   * @return restartVersion of the domain resource
   */
  public static String patchDomainWithNewSecretAndVerify(
      final String domainUid,
      final String namespace,
      final String secretName
  ) {
    LoggingFacade logger = getLogger();
    logger.info(
        "Patch domain resource {0} in namespace {1} to use the new secret {2}",
        domainUid, namespace, secretName);

    String restartVersion = patchDomainResourceWithNewAdminSecret(domainUid, namespace, secretName);

    logger.info(
        "Check that domain resource {0} in namespace {1} has been patched with new secret {2}",
        domainUid, namespace, secretName);
    checkDomainCredentialsSecretPatched(domainUid, namespace, secretName);

    // check and wait for the admin server pod to be patched with the new secret
    logger.info(
        "Check that admin server pod for domain resource {0} in namespace {1} has been patched with {2}: {3}",
        domainUid, namespace, "/spec/webLogicCredentialsSecret/name", secretName);

    return restartVersion;
  }

  /**
   * Check that domain resource has been updated with the new WebLogic domain credentials secret.
   *
   * @param domainUid name of the domain resource
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param newValue new secret name for the WebLogic domain credentials secret
   */
  private static void checkDomainCredentialsSecretPatched(
      String domainUid,
      String namespace,
      String newValue
  ) {
    LoggingFacade logger = getLogger();
    // check if domain resource has been patched with the new secret
    testUntil(
        assertDoesNotThrow(() -> domainResourceCredentialsSecretPatched(domainUid, namespace, newValue),
          String.format(
            "Domain %s in namespace %s is not patched with admin credentials secret %s",
            domainUid, namespace, newValue)),
        logger,
        "domain {0} to be patched in namespace {1}",
        domainUid,
        namespace);
  }

  /**
   * Check if the given server pod's domain restart version has been updated.
   *
   * @param podName name of the server pod
   * @param domainUid the domain that the server pod belongs to
   * @param namespace the Kubernetes namespace that the pod belongs to
   * @param restartVersion the expected value of the restart version
   */
  public static void checkPodRestartVersionUpdated(
      String podName,
      String domainUid,
      String namespace,
      String restartVersion) {
    LoggingFacade logger = getLogger();
    logger.info("Check that weblogic.domainRestartVersion of pod {0} has been updated", podName);
    boolean restartVersionUpdated = assertDoesNotThrow(
        () -> podRestartVersionUpdated(podName, domainUid, namespace, restartVersion),
        String.format("Failed to get weblogic.domainRestartVersion label of pod %s in namespace %s",
            podName, namespace));
    assertTrue(restartVersionUpdated,
        String.format("Label weblogic.domainRestartVersion of pod %s in namespace %s has not been updated",
            podName, namespace));
  }

  /**
   * Patch the domain with the given string.
   *
   * @param domainUid unique domain identifier
   * @param domainNamespace the Kubernetes namespace where the domain is
   * @param patchStr the string for patching
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainResource(String domainUid, String domainNamespace, StringBuffer patchStr) {

    LoggingFacade logger = getLogger();
    logger.info("Modify domain resource for domain {0} in namespace {1} using patch string: {2}",
        domainUid, domainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));
    return patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Patch the domain with the given string.
   *
   * @param domainUid unique domain identifier
   * @param domainNamespace the Kubernetes namespace where the domain is
   * @param patchPath the string for patching
   * @param policy the ServerStartPolicy
   * @return true if successful, false otherwise
   */
  public static boolean patchServerStartPolicy(
      String domainUid, String domainNamespace,
      String patchPath, String policy) {
    LoggingFacade logger = getLogger();
    logger.info("Updating the for domain {0} in namespace {1} using patch string: {2}",
        domainUid, domainNamespace, patchPath.toString());
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"")
        .append(patchPath)
        .append("\",")
        .append(" \"value\":  \"")
        .append(policy)
        .append("\"")
        .append(" }]");
    V1Patch patch = new V1Patch(new String(patchStr));
    return patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  /**
   * Patch the domain with server start policy.
   *
   * @param patchPath JSON path of the patch
   * @param policy server start policy
   * @param domainNamespace namespace where domain exists
   * @param domainUid unique id of domain
   */
  public static void patchDomainResourceServerStartPolicy(String patchPath, String policy, String domainNamespace,
                                            String domainUid) {
    final LoggingFacade logger = getLogger();
    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"")
        .append(patchPath)
        .append("\",")
        .append(" \"value\":  \"")
        .append(policy)
        .append("\"")
        .append(" }]");

    logger.info("The domain resource patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(new String(patchStr));
    boolean crdPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(managedShutdown) failed");
    assertTrue(crdPatched, "patchDomainCustomResource failed");
  }


  /**
   * Patch replicas at spec level.
   *
   * @param domainUid unique domain identifier
   * @param domainNamespace the Kubernetes namespace where the domain is
   * @param replicaCount the replica count to patch with
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainResourceWithNewReplicaCountAtSpecLevel(
      String domainUid, String domainNamespace, int replicaCount) {
    LoggingFacade logger = getLogger();
    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/replicas\",")
        .append(" \"value\": ")
        .append(replicaCount)
        .append(" }]");
    logger.info("Replicas patch string: {0}", patchStr);

    return patchDomainResource(domainUid, domainNamespace, patchStr);
  }

  /**
   * Add server pod compute resources.
   *
   * @param domainUid unique domain identifier
   * @param domainNamespace the Kubernetes namespace where the domain is
   * @param resourceLimits resource limit to be added to domain spec serverPod resources limits
   * @param resourceRequest resource request to be added to domain spec serverPod resources requests
   * @return true if patching domain custom resource is successful, false otherwise
   */
  public static boolean addServerPodResources(String domainUid,
                                              String domainNamespace,
                                              Map<String, String> resourceLimits,
                                              Map<String, String> resourceRequest) {
    LoggingFacade logger = getLogger();
    // construct the patch string for adding server pod resources
    StringBuffer patchStr = new StringBuffer("[");
    for (String key : resourceLimits.keySet()) {
      patchStr.append("{\"op\": \"add\", ")
          .append("\"path\": \"/spec/serverPod/resources/limits/")
          .append(key)
          .append("\", ")
          .append("\"value\": \"")
          .append(resourceLimits.get(key))
          .append("\"},");
    }

    for (String key : resourceRequest.keySet()) {
      patchStr.append("{\"op\": \"add\", ")
          .append("\"path\": \"/spec/serverPod/resources/requests/")
          .append(key)
          .append("\", ")
          .append("\"value\": \"")
          .append(resourceRequest.get(key))
          .append("\"},");
    }

    // remove last comma
    patchStr = patchStr.deleteCharAt(patchStr.lastIndexOf(","));
    patchStr.append("]");

    logger.info("Adding server pod compute resources for domain {0} in namespace {1} using patch string: {2}",
        domainUid, domainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return Domain.patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }
}

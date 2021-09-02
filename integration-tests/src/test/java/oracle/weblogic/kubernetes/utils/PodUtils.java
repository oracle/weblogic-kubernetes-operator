// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodAffinityTerm;
import io.kubernetes.client.openapi.models.V1PodAntiAffinity;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podInitializing;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PodUtils {
  /**
   * Execute command inside a pod and assert the execution.
   *
   * @param pod V1Pod object
   * @param containerName name of the container inside the pod
   * @param redirectToStdout if true redirect to stdout and stderr
   * @param command the command to execute inside the pod
   */
  public static void execInPod(V1Pod pod, String containerName, boolean redirectToStdout, String command) {
    LoggingFacade logger = getLogger();
    ExecResult exec = null;
    try {
      logger.info("Executing command {0}", command);
      exec = Exec.exec(pod, containerName, redirectToStdout, "/bin/sh", "-c", command);
      // checking for exitValue 0 for success fails sometimes as k8s exec api returns non-zero
      // exit value even on success, so checking for exitValue non-zero and stderr not empty for failure,
      // otherwise its success
      assertFalse(exec.exitValue() != 0 && exec.stderr() != null && !exec.stderr().isEmpty(),
          String.format("Command %s failed with exit value %s, stderr %s, stdout %s",
              command, exec.exitValue(), exec.stderr(), exec.stdout()));
    } catch (IOException | ApiException | InterruptedException ex) {
      logger.warning(ex.getMessage());
    }
  }

  /**
   * Check pod exists in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodExists(String podName, String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podExists(podName, domainUid, domainNamespace),
            String.format("podExists failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace)));
  }

  /**
   * Check pod is ready.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodReady(String podName, String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podReady(podName, domainUid, domainNamespace),
            String.format("podReady failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace)));
  }

  /**
   * Checks that pod is initializing.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodInitializing(String podName, String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be initializing in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podInitializing(podName, domainUid, domainNamespace),
            String.format("podReady failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace)));
  }

  /**
   * Check pod is restarted by comparing the pod's creation timestamp with the last timestamp.
   *
   * @param domainUid the label the pod is decorated with
   * @param podName pod name to check
   * @param domNamespace the Kubernetes namespace in which the domain exists
   * @param lastCreationTime the previous creation time
   */
  public static void checkPodRestarted(
      String domainUid,
      String domNamespace,
      String podName,
      OffsetDateTime lastCreationTime
  ) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be restarted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> isPodRestarted(podName, domNamespace, lastCreationTime),
            String.format(
                "pod %s has not been restarted in namespace %s", podName, domNamespace)));
  }

  /**
   * Check pod is restarted by comparing the pod's creation timestamp with the last timestamp.
   *
   * @param podName pod name to check
   * @param domNamespace the Kubernetes namespace in which the domain exists
   * @param lastCreationTime the previous creation time
   */
  public static Callable<Boolean> checkIsPodRestarted(String domNamespace,
                                                      String podName,
                                                      OffsetDateTime lastCreationTime) {
    return isPodRestarted(podName, domNamespace, lastCreationTime);
  }


  /**
   * Check pod does not exist in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which to check whether the pod exists
   */
  public static void checkPodDoesNotExist(String podName, String domainUid, String namespace) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, namespace),
            String.format("podDoesNotExist failed with ApiException for pod %s in namespace %s",
                podName, namespace)));
  }

  /**
   * Get the PodCreationTimestamp of a pod in a namespace.
   *
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param podName name of the pod
   * @return PodCreationTimestamp of the pod
   */
  public static OffsetDateTime getPodCreationTime(String namespace, String podName) {
    LoggingFacade logger = getLogger();
    OffsetDateTime podCreationTime =
        assertDoesNotThrow(() -> getPodCreationTimestamp(namespace, "", podName),
            String.format("Couldn't get PodCreationTimestamp for pod %s", podName));
    assertNotNull(podCreationTime, "Got null PodCreationTimestamp");
    logger.info("PodCreationTimestamp for pod {0} in namespace {1} is {2}",
        podName,
        namespace,
        podCreationTime);
    return podCreationTime;
  }

  /**
   * Get the creationTimestamp for the domain admin server pod and managed server pods.
   *
   * @param domainNamespace namespace where the domain is
   * @param adminServerPodName the pod name of the admin server
   * @param managedServerPrefix prefix of the managed server pod name
   * @param replicaCount replica count of the managed servers
   * @return map of domain admin server pod and managed server pods with their corresponding creationTimestamps
   */
  public static Map getPodsWithTimeStamps(String domainNamespace, String adminServerPodName,
                                          String managedServerPrefix, int replicaCount) {

    // create the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = new LinkedHashMap<>();
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
    return podsWithTimeStamps;
  }

  /**
   * Set the inter-pod anti-affinity  for the domain custom resource
   * so that server instances spread over the available Nodes.
   *
   * @param domain custom resource object
   */
  public static synchronized void setPodAntiAffinity(Domain domain) {
    domain.getSpec()
        .getClusters()
        .stream()
        .forEach(
            cluster -> {
              ServerPod serverPod = cluster.getServerPod();
              if (serverPod == null) {
                serverPod = new ServerPod();
              }
              cluster
                  .serverPod(serverPod
                      .affinity(new V1Affinity().podAntiAffinity(
                          new V1PodAntiAffinity()
                              .addPreferredDuringSchedulingIgnoredDuringExecutionItem(
                                  new V1WeightedPodAffinityTerm()
                                      .weight(100)
                                      .podAffinityTerm(new V1PodAffinityTerm()
                                          .topologyKey("kubernetes.io/hostname")
                                          .labelSelector(new V1LabelSelector()
                                              .addMatchExpressionsItem(new V1LabelSelectorRequirement()
                                                  .key("weblogic.clusterName")
                                                  .operator("In")

                                                  .addValuesItem("$(CLUSTER_NAME)")))
                                      )))));

            }
        );

  }

  /**
   * Check if the pods are deleted.
   * @param podName pod name
   * @param domainUid unique id of the domain
   * @param domNamespace namespace where domain exists
   */
  public static void checkPodDeleted(String podName, String domainUid, String domNamespace) {
    final LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                podName,
                domNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domNamespace),
            String.format("podDoesNotExist failed with ApiException for %s in namespace in %s",
                podName, domNamespace)));
  }

  public static String getExternalServicePodName(String adminServerPodName) {
    return getExternalServicePodName(adminServerPodName, TestConstants.DEFAULT_EXTERNAL_SERVICE_NAME_SUFFIX);
  }

  public static String getExternalServicePodName(String adminServerPodName, String suffix) {
    return adminServerPodName + suffix;
  }

  /**
   * Get the introspector pod name.
   * @param domainUid domain uid of the domain
   * @param domainNamespace domain namespace in which introspector runs
   * @return the introspector pod name
   * @throws ApiException if Kubernetes API calls fail
   */
  public static String getIntrospectorPodName(String domainUid, String domainNamespace) throws ApiException {
    checkPodExists(getIntrospectJobName(domainUid), domainUid, domainNamespace);

    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);

    V1Pod introspectorPod = getPod(domainNamespace, labelSelector, getIntrospectJobName(domainUid));

    if (introspectorPod != null && introspectorPod.getMetadata() != null) {
      return introspectorPod.getMetadata().getName();
    } else {
      return "";
    }
  }

}

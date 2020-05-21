// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.models.V1Pod;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class Pod {

  /**
   * Verify pods are restarted in a rolling fashion with not more than maxUnavailable pods are restarted concurrently.
   * @param pods map of pod names with its creation time stamps
   * @param maxUnavailable number of pods can concurrently restart at the same time
   * @param namespace name of the namespace in which the pod restart status to be checked
   * @return true if pods are restarted in a rolling fashion
   */
  public static boolean verifyRollingRestartOccurred(Map<String, String> pods, int maxUnavailable, String namespace) {

    // check the pods list is not empty
    if (pods.isEmpty()) {
      logger.severe("The pods list is empty");
      return false;
    }

    // reusable condition factory
    ConditionFactory retry
        = with().pollInterval(5, SECONDS).atMost(5, MINUTES).await();

    for (Map.Entry<String, String> entry : pods.entrySet()) {
      // check pods are replaced
      retry
          .conditionEvaluationListener(condition -> logger.info("Waiting for pod {0} to be "
          + "restarted in namespace {1} "
          + "(elapsed time {2}ms, remaining time {3}ms)",
          entry.getKey(),
          namespace,
          condition.getElapsedTimeInMS(),
          condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> podRestarted(entry.getKey(), pods, maxUnavailable, namespace),
              String.format("pod %s didn't restart in namespace %s", entry.getKey(), namespace)));

      // check pods are in ready status
      retry
          .conditionEvaluationListener(condition -> logger.info("Waiting for pod {0} to be "
          + "ready in namespace {1} "
          + "(elapsed time {2}ms, remaining time {3}ms)",
          entry.getKey(),
          namespace,
          condition.getElapsedTimeInMS(),
          condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> podReady(namespace, null, entry.getKey()),
              String.format("pod %s didn't become ready in namespace %s", entry.getKey(), namespace)));
    }

    return true;
  }

  /**
   * Return true if the given pod podName is restarted. This method will throw a exception if more than maxUnavailable
   * pods are restarted at the same time.
   *
   * @param pods map of pod names with its creation time stamps
   * @param podName name of pod to check for restart status
   * @param maxUnavailable number of pods that can concurrently restart at the same time
   * @param namespace name of the namespace in which the pod restart status to be checked
   * @return true if given pod is restarted
   * @throws Exception when more than maxUnavailable pods are restarting concurrently or cluster query fails
   */
  private static Callable<Boolean> podRestarted(String podName, Map<String, String> pods, int maxUnavailable,
      String namespace) throws Exception {
    return () -> {
      int terminatingPods = 0;
      boolean podRestartStatus = false;
      for (Map.Entry<String, String> entry : pods.entrySet()) {
        V1Pod pod = Kubernetes.getPod(namespace, null, entry.getKey());
        DateTime deletionTimeStamp = Optional.ofNullable(pod)
            .map(metadata -> metadata.getMetadata())
            .map(timeStamp -> timeStamp.getDeletionTimestamp()).orElse(null);
        if (deletionTimeStamp != null) {
          logger.info("Pod {0} is getting replaced", entry.getKey());
          if (++terminatingPods > maxUnavailable) {
            logger.severe("more than maxUnavailable {0} pod(s) are restarting", maxUnavailable);
            throw new Exception("more than maxUnavailable pods are restarting");
          }
        }
        if (podName.equals(entry.getKey())) {
          DateTimeFormatter dtf = DateTimeFormat.forPattern("HHmmss");
          if (pod != null && pod.getMetadata().getCreationTimestamp() != null) {
            String newCreationTimeStamp = dtf.print(pod.getMetadata().getCreationTimestamp());
            logger.info("Comparing creation timestamps old: {0} new {1}",
                entry.getValue(), newCreationTimeStamp);
            if (Long.parseLong(newCreationTimeStamp) > Long.parseLong(entry.getValue())) {
              logger.info("Pod {0} is restarted", entry.getKey());
              podRestartStatus = true;
            }
          }
        }
      }
      return podRestartStatus;
    };
  }

  /**
   * Check a given pod is in ready status.
   *
   * @param namespace name of the namespace in which to check the pod status
   * @param domainUid UID of the WebLogic domain
   * @param podName name of the pod
   * @return true if pod is ready otherwise false
   */
  public static Callable<Boolean> podReady(String namespace, String domainUid, String podName) {
    return () -> {
      return Kubernetes.isPodReady(namespace, domainUid, podName);
    };
  }

  /**
   * Check a pod is in Terminating state.
   *
   * @param podName name of the pod for which to check for Terminating status
   * @param domainUid WebLogic domain uid in which the pod exists
   * @param namespace in which the pod is running
   * @return true if the pod is terminating otherwise false
   */
  public static Callable<Boolean> podTerminating(String podName, String domainUid, String namespace) {
    return () -> {
      return Kubernetes.isPodTerminating(namespace, domainUid, podName);
    };
  }

  /**
   * Check a named pod does not exist in the given namespace.
   *
   * @param podName name of the pod to check for
   * @param domainUid Uid of WebLogic domain
   * @param namespace namespace in which to check for the pod
   * @return true if the pod does not exist in the namespace otherwise false
   */
  public static Callable<Boolean> podDoesNotExist(String podName, String domainUid, String namespace) {
    return () -> {
      return !Kubernetes.doesPodExist(namespace, domainUid, podName);
    };
  }

  /**
   * Check if a pod exists in any state in the given namespace.
   *
   * @param podName name of the pod to check for
   * @param domainUid UID of WebLogic domain in which the pod exists
   * @param namespace in which the pod exists
   * @return true if the pod exists in the namespace otherwise false
   */
  public static Callable<Boolean> podExists(String podName, String domainUid, String namespace) {
    return () -> {
      return Kubernetes.doesPodExist(namespace, domainUid, podName);
    };
  }

}
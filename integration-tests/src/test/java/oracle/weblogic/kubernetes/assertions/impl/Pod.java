// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.models.V1Pod;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;

public class Pod {

  /**
   * Verify pods are restarted in a rolling fashion with not more than maxUnavailable pods are restarted concurrently.
   * @param pods map of pod names with its creation time stamps
   * @param maxUnavailable number of pods can concurrently restart at the same time
   * @param namespace name of the namespace in which the pod restart status to be checked
   * @return true if pods are restarted in a rolling fashion
   */
  public static boolean verifyRollingRestartOccurred(Map<String, OffsetDateTime> pods,
                                                     int maxUnavailable, String namespace) {

    // check the pods list is not empty
    if (pods.isEmpty()) {
      getLogger().severe("The pods list is empty");
      return false;
    }

    // reusable condition factory
    ConditionFactory retry
        = with().pollInterval(5, SECONDS).atMost(5, MINUTES).await();

    for (Map.Entry<String, OffsetDateTime> entry : pods.entrySet()) {
      // check pods are replaced
      retry
          .conditionEvaluationListener(condition -> getLogger().info("Waiting for pod {0} to be "
          + "restarted in namespace {1} "
          + "(elapsed time {2}ms, remaining time {3}ms)",
          entry.getKey(),
          namespace,
          condition.getElapsedTimeInMS(),
          condition.getRemainingTimeInMS()))
          .until(podRestarted(entry.getKey(), pods, maxUnavailable, namespace));

      // check pods are in ready status
      retry
          .conditionEvaluationListener(condition -> getLogger().info("Waiting for pod {0} to be "
          + "ready in namespace {1} "
          + "(elapsed time {2}ms, remaining time {3}ms)",
          entry.getKey(),
          namespace,
          condition.getElapsedTimeInMS(),
          condition.getRemainingTimeInMS()))
          .until(podReady(namespace, null, entry.getKey()));
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
   */
  private static Callable<Boolean> podRestarted(String podName, Map<String, OffsetDateTime> pods, int maxUnavailable,
      String namespace) {
    return () -> {
      int terminatingPods = 0;
      boolean podRestartStatus = false;
      for (Map.Entry<String, OffsetDateTime> entry : pods.entrySet()) {
        V1Pod pod = Kubernetes.getPod(namespace, null, entry.getKey());
        OffsetDateTime deletionTimeStamp = Optional.ofNullable(pod)
            .map(metadata -> metadata.getMetadata())
            .map(timeStamp -> timeStamp.getDeletionTimestamp()).orElse(null);
        if (deletionTimeStamp != null) {
          getLogger().info("Pod {0} is getting replaced", entry.getKey());
          if (++terminatingPods > maxUnavailable) {
            getLogger().severe("more than maxUnavailable {0} pod(s) are restarting", maxUnavailable);
            throw new Exception("more than maxUnavailable pods are restarting");
          }
        }
        if (podName.equals(entry.getKey())) {
          if (pod != null && pod.getMetadata().getCreationTimestamp() != null) {
            OffsetDateTime newCreationTimeStamp = pod.getMetadata().getCreationTimestamp();
            getLogger().info("Comparing creation timestamps old: {0} new {1}",
                entry.getValue(), newCreationTimeStamp);
            if (newCreationTimeStamp.isAfter(entry.getValue())) {
              getLogger().info("Pod {0} is restarted", entry.getKey());
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
   * Check a given pod is in initializing status.
   *
   * @param namespace name of the namespace in which to check the pod status
   * @param domainUid UID of the WebLogic domain
   * @param podName name of the pod
   * @return true if pod is initializing otherwise false
   */
  public static Callable<Boolean> podInitializing(String namespace, String domainUid, String podName) {
    return () -> {
      return Kubernetes.isPodInitializing(namespace, domainUid, podName);
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

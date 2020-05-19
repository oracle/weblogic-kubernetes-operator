// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.ArrayList;
import java.util.concurrent.Callable;

import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class Pod {

  /**
   * Check only one pod is restarted at a time in the same order as in the pods list.
   * This assertion method needs to be called right after the domain is patched to ensure
   * it doesn't miss any of the pods restart.
   * @param pods names of the pods in a list
   * @param namespace name of the namespace in which to check for pods rolling restart
   * @return true if pods are restarted in rolling fashion
   */
  public static boolean verifyRollingRestartOccurred(ArrayList<String> pods, String namespace) {

    // check the pods list is not empty
    if (pods.isEmpty()) {
      logger.severe("The pods list is empty");
      return false;
    }

    // reusable condition factory
    ConditionFactory retry
        = with().pollInterval(5, SECONDS).atMost(5, MINUTES).await();

    // check pods are terminated and started.
    for (var pod : pods) {
      retry
          .conditionEvaluationListener(condition -> logger.info("Waiting for pod {0} to be "
          + "terminating in namespace {1} "
          + "(elapsed time {2}ms, remaining time {3}ms)",
          pod,
          namespace,
          condition.getElapsedTimeInMS(),
          condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> onlyGivenPodTerminating(pods, pod, namespace),
              String.format("pod %s didn't terminate in namespace %s", pod, namespace)));

      retry
          .conditionEvaluationListener(condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
          + "(elapsed time {2}ms, remaining time {3}ms)",
          pod,
          namespace,
          condition.getElapsedTimeInMS(),
          condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> podReady(namespace, null, pod),
              String.format("pod %s is not ready in namespace %s", pod, namespace)));
    }

    return true;
  }

  /**
   * Return true if the given pod is the only one terminating from the list of pods.
   *
   * @param pods names of the pods in a list
   * @param podName name of pod to check for termination status
   * @param namespace name of the namespace in which the pod terminating status to be checked
   * @return true if given pod is the only pod terminating
   * @throws Exception when more than one pod is terminating or cluster query fails
   */
  public static Callable<Boolean> onlyGivenPodTerminating(ArrayList<String> pods, String podName, String namespace)
      throws Exception {
    return () -> {
      int terminatingPods = 0;
      boolean givenPodTerminating = false;
      for (var pod : pods) {
        if (Kubernetes.isPodTerminating(namespace, null, pod)) {
          terminatingPods++;
          if (pod.equals(podName)) {
            givenPodTerminating = true;
          }
        }
      }
      if (terminatingPods > 1) {
        logger.severe("more than one pod is terminating");
        throw new Exception("more than one pod is terminating ");
      }
      return givenPodTerminating;
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
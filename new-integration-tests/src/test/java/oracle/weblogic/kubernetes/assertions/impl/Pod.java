// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class Pod {

  /**
   * Check if the given pods are restarted in the same order as in the pods list.
   * @param pods list of names of the pods
   * @param namespace name of the namespace in which to check for pod rolling restart
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

    for (var podName : pods) {
      retry
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for pod {0} to be terminating in namespace {1} "
                  + "(elapsed time {2}ms, remaining time {3}ms)",
                  podName,
                  namespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> onlyGivenPodTerminating(pods, podName, namespace),
              String.format(
                  "pod %s didn't terminate in namespace %s", podName, namespace)));

      retry
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                  + "(elapsed time {2}ms, remaining time {3}ms)",
                  podName,
                  namespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> podReady(namespace, null, podName),
              String.format(
                  "pod %s is not ready in namespace %s", podName, namespace)));
    }

    return true;
  }

  /**
   * Check the pods in the given namespace are restarted in a rolling fashion.
   * Waits until all pods are restarted, for upto 10 minutes.
   *
   * @param domainUid UID of the WebLogic domain
   * @param namespace name of the namespace in which to check for the pods restart sequence
   * @return true if pods in the namespace are restarted in a rolling fashion otherwise false
   * @throws ApiException when Kubernetes cluster query fails
   * @throws InterruptedException when pod status check threads are interrupted
   * @throws ExecutionException when pod status checks times out
   * @throws TimeoutException when waiting for the threads times out
   */
  public static boolean isARollingRestart(String domainUid, String namespace)
      throws ApiException, InterruptedException, ExecutionException, TimeoutException {

    // reusable condition factory
    ConditionFactory retry
        = with().pollInterval(5, SECONDS).atMost(5, MINUTES).await();

    // query cluster and get pods from the namespace
    String labelSelectors = "weblogic.serverName";
    V1PodList listPods = Kubernetes.listPods(namespace, labelSelectors);
    ArrayList<String> podNames = new ArrayList<>();

    //return if no pods are found
    if (listPods.getItems().isEmpty()) {
      logger.severe("No pods found in namespace {0}", namespace);
      return false;
    } else {
      logger.info("WebLogic pods found in namespace {0}", namespace);
      for (V1Pod item : listPods.getItems()) {
        logger.info(item.getMetadata().getName());
        podNames.add(item.getMetadata().getName());
      }
    }

    // check the pods termination status in concurrent thread
    ExecutorService executorService = Executors.newFixedThreadPool(podNames.size());
    ArrayList<Future<Boolean>> threads = new ArrayList<Future<Boolean>>();
    for (var podName : podNames) {
      // check for pod termination status and return true if pod is terminating
      threads.add(executorService.submit(() -> {
        retry
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for pod {0} in namespace {1} to terminate"
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                    podName,
                    namespace,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
            .until(onlyGivenPodTerminating(null, domainUid, namespace));
        return true;
      }));
      // wait for the callable to finish running and check if all pods were terminating
      for (var future : threads) {
        if (!future.get(10, MINUTES)) {
          return false;
        }
      }
    }
    executorService.shutdownNow();

    // wait for pods to become ready
    for (var podName : podNames) {
      logger.info("Wait for pod {0} to be ready in namespace {1}", podName, namespace);
      retry
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                  + "(elapsed time {2}ms, remaining time {3}ms)",
                  podName,
                  namespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> podReady(namespace, domainUid, podName),
              String.format(
                  "pod %s is not ready in namespace %s", podName, namespace)));
    }

    return true;
  }

  /**
   * Return true if the given pod is the only one terminating.
   *
   * @param podName name of pod to check for termination status
   * @param domainUid UID of the WebLogic domain
   * @param namespace name of the namespace in which the pod is running
   * @return true if given pod is terminating otherwise false
   * @throws Exception when more than one pod is terminating or cluster query fails
   */
  private static Callable<Boolean> onlyGivenPodTerminating(ArrayList<String> pods, String podName, String namespace)
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
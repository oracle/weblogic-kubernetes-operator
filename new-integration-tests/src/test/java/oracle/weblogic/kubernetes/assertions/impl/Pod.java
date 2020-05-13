// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

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

  private static ConditionFactory withStandardRetryPolicy
      = withStandardRetryPolicy = with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();

  /**
   * Check the pods in the given namespace are restarted in rolling fashion. Waits until all pods are restarted for upto
   * 7 minutes.
   *
   * @param domainUid UID of the WebLogic domain
   * @param namespace in which to check for the pods restart sequence
   * @return true if pods in the namespace restarted in a rolling fashion otherwise false
   * @throws ApiException when Kubernetes cluster query fails
   * @throws InterruptedException when pod status check threads are interrupted
   * @throws ExecutionException when pod status checks times out
   * @throws TimeoutException when waiting for the threads times out
   */
  public static boolean isARollingRestart(String domainUid, String namespace)
      throws ApiException, InterruptedException, ExecutionException, TimeoutException {

    withStandardRetryPolicy = with().pollInterval(5, SECONDS)
        .atMost(7, MINUTES).await();

    // query cluster and get pods from the namespace
    String labelSelectors = String.format("weblogic.domainUID=%s", domainUid);
    V1PodList listPods = Kubernetes.listPods(namespace, labelSelectors);
    int numOfPods = listPods.getItems().size();

    //return if no pods are found
    if (numOfPods == 0) {
      logger.severe("No pods found in namespace {0}", namespace);
      return false;
    }

    // check the pods termination status in a thread
    ExecutorService executorService = Executors.newFixedThreadPool(numOfPods);
    Future[] submit = new Future[numOfPods];
    for (int i = 0; i < numOfPods; i++) {
      V1Pod pod = listPods.getItems().get(i);
      // check for pod termination status and return true if pod is terminating
      submit[i] = executorService.submit(() -> {
        withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for pod {0} in namespace {0} to terminate"
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                    pod.getMetadata().getName(),
                    namespace,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
            .until(isOnePodTerminating(pod.getMetadata().getName(), domainUid, namespace));
        return true;
      });
      // wait for the callable to finish running and check if all pods were terminating
      for (Future future : submit) {
        if (!(Boolean) future.get(8, MINUTES)) {
          return false;
        }
      }
    }
    executorService.shutdownNow();

    // check pods are ready
    for (var pod : Kubernetes.listPods(namespace, labelSelectors).getItems()) {
      String podName = pod.getMetadata().getName();
      logger.info("Wait for pod {0} to be ready in namespace {1}", podName, namespace);
      withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for pod {0} to be ready in namespace {1} "
                  + "(elapsed time {2}ms, remaining time {3}ms)",
                  podName,
                  namespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> podReady(podName, domainUid, namespace),
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
   * @throws Exception when more than one pod is running or cluster query fails
   */
  private static Callable<Boolean> isOnePodTerminating(String podName, String domainUid, String namespace)
      throws Exception {
    return () -> {
      String labelSelectors = String.format("weblogic.domainUID=%s", domainUid);
      V1PodList listPods = Kubernetes.listPods(namespace, labelSelectors);
      if (listPods.getItems().size() == 0) {
        logger.severe("No pods found in namespace {0}", namespace);
        return false;
      }
      int terminatingPods = 0;
      boolean podTerminating = false;
      for (V1Pod pod : listPods.getItems()) {
        if (Kubernetes.isPodTerminating(namespace, domainUid, pod.getMetadata().getName())) {
          logger.info("currently terminating pod {0}", pod.getMetadata().getName());
          terminatingPods++;
          if (pod.getMetadata().getName().equals(podName)) {
            podTerminating = true;
          }
        }
      }
      if (terminatingPods > 1) {
        logger.severe("more than one pod is terminating");
        throw new Exception("more than one pod is terminating ");
      }
      return podTerminating;
    };
  }

  /**
   * Check a given is in ready status.
   *
   * @param namespace name of the namespace in which to check the pod status
   * @param domainUid UID of the WebLogic domain
   * @param podName name of the pod
   * @return true if pod is Ready otherwise false
   */
  public static Callable<Boolean> podReady(String namespace, String domainUid, String podName) {
    return () -> {
      return Kubernetes.isPodReady(namespace, domainUid, podName);
    };
  }

  /**
   * Check if a pod given by the podName is in Terminating state.
   *
   * @param podName name of the pod to check for Terminating status
   * @param domainUid WebLogic domain uid in which the pod belongs
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
   * @throws ApiException when cluster query fails
   */
  public static Callable<Boolean> podDoesNotExist(String podName, String domainUid, String namespace)
      throws ApiException {
    return () -> {
      return !Kubernetes.doesPodExist(namespace, domainUid, podName);
    };
  }

  /**
   * Check if a Kubernetes pod exists in any state in the given namespace.
   *
   * @param podName name of the pod to check for
   * @param domainUid UID of WebLogic domain in which the pod exists
   * @param namespace in which the pod exists
   * @return true if the pod exists in the namespace otherwise false
   */
  public static Callable<Boolean> podExists(String podName, String domainUid, String namespace) throws ApiException {
    return () -> {
      return Kubernetes.doesPodExist(namespace, domainUid, podName);
    };
  }

  /**
   * Check if a pod completed running.
   *
   * @param namespace name of the namespace in which the pod exists
   * @param podName name of the pod to check for its completion status
   * @return true if completed false otherwise
   */
  public static Callable<Boolean> podCompleted(String namespace, String podName) {
    return () -> {
      return Kubernetes.podCompleted(namespace, podName);
    };
  }

}

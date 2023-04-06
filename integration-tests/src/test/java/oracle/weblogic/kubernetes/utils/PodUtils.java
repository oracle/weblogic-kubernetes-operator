// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LabelSelectorRequirement;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodAffinityTerm;
import io.kubernetes.client.openapi.models.V1PodAntiAffinity;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1WeightedPodAffinityTerm;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.impl.Pod.isPodEvictedStatusLoggedInOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podInitialized;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntilNoException;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
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
    testUntil(
        assertDoesNotThrow(() -> podExists(podName, domainUid, domainNamespace),
          String.format("podExists failed with ApiException for pod %s in namespace %s",
            podName, domainNamespace)),
        logger,
        "pod {0} to be created in namespace {1}",
        podName,
        domainNamespace);
  }

  /**
   * Check pod exists in the specified namespace.
   *
   * @param conditionFactory Configuration for Awaitility condition factory
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodExists(ConditionFactory conditionFactory, String podName,
      String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    testUntil(conditionFactory,
        assertDoesNotThrow(() -> podExists(podName, domainUid, domainNamespace),
            String.format("podExists failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace)),
        logger,
        "pod {0} to be created in namespace {1}",
        podName,
        domainNamespace);
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

    testUntil(
            withLongRetryPolicy,
            assertDoesNotThrow(() -> podReady(podName, domainUid, domainNamespace),
                    String.format("podReady failed with ApiException for pod %s in namespace %s",
                            podName, domainNamespace)),
            logger,
            "pod {0} to be ready in namespace {1}",
            podName,
            domainNamespace);
  }

  /**
   * Check pod is ready.
   *
   * @param conditionFactory Configuration for Awaitility condition factory
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodReady(ConditionFactory conditionFactory, String podName,
                                   String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    testUntil(conditionFactory,
        assertDoesNotThrow(() -> podReady(podName, domainUid, domainNamespace),
            String.format("podReady failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace)),
        logger,
        "pod {0} to be ready in namespace {1}",
        podName,
        domainNamespace);
  }

  /**
   * Checks that pod is initialized.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param domainNamespace the domain namespace in which the domain exists
   */
  public static void checkPodInitialized(String podName, String domainUid, String domainNamespace) {
    LoggingFacade logger = getLogger();
    testUntil(
        assertDoesNotThrow(() -> podInitialized(podName, domainUid, domainNamespace),
          String.format("podInitialized failed with ApiException for pod %s in namespace %s",
            podName, domainNamespace)),
        logger,
        "pod {0} to be initialized in namespace {1}",
        podName,
        domainNamespace);
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
    testUntil(
        assertDoesNotThrow(() -> isPodRestarted(podName, domNamespace, lastCreationTime),
          String.format(
            "pod %s has not been restarted in namespace %s", podName, domNamespace)),
        logger,
        "pod {0} to be restarted in namespace {1}",
        podName,
        domNamespace);
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
    testUntil(
        assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, namespace),
          String.format("podDoesNotExist failed with ApiException for pod %s in namespace %s",
            podName, namespace)),
        logger,
        "pod {0} to be deleted in namespace {1}",
        podName,
        namespace);
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
  public static Map<String, OffsetDateTime> getPodsWithTimeStamps(String domainNamespace, String adminServerPodName,
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
  public static synchronized void setPodAntiAffinity(DomainResource domain) {
    domain.getSpec()
        .getClusters()
        .stream()
        .forEach(
            clusterRef -> {
              ClusterResource clusterResource =
                  assertDoesNotThrow(() -> Kubernetes.getClusterCustomResource(clusterRef.getName(),
                  domain.getMetadata().getNamespace(), CLUSTER_VERSION),
                  "Could not find the cluster resource by name " + clusterRef.getName());
              ServerPod serverPod = clusterResource.getSpec().getServerPod();
              if (clusterResource.getSpec().getServerPod() == null) {
                serverPod = new ServerPod();
              }
              clusterResource.getSpec()
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
    testUntil(withLongRetryPolicy,
        assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domNamespace),
          String.format("podDoesNotExist failed with ApiException for %s in namespace in %s",
            podName, domNamespace)),
        logger,
        "pod {0} to be deleted in namespace {1}",
        podName,
        domNamespace);
  }

  /**
   * Check if the pods are deleted.
   * @param podName pod name
   * @param domainUid unique id of the domain
   * @param domNamespace namespace where domain exists
   * @return true if pod is deleted
   */
  public static boolean isPodDeleted(String podName, String domainUid, String domNamespace) {
    final LoggingFacade logger = getLogger();
    return testUntilNoException(withLongRetryPolicy,
        assertDoesNotThrow(() -> podDoesNotExist(podName, domainUid, domNamespace),
            String.format("podDoesNotExist failed with ApiException for %s in namespace in %s",
                podName, domNamespace)),
        logger,
        "pod {0} to be deleted in namespace {1}",
        podName,
        domNamespace);
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


  /**
   * Verify introspector log contains the error message.
   * @param domainUid domain uid of the domain
   * @param namespace domain namespace in which introspector runs
   * @param expectedErrorMsg error message
   */
  public static void verifyIntrospectorPodLogContainsExpectedErrorMsg(String domainUid,
                                                                      String namespace,
                                                                      String expectedErrorMsg) {
    verifyIntrospectorPodLogContainsExpectedErrorMsg(domainUid, namespace, expectedErrorMsg, null);
  }

  /**
   * Verify introspector log contains the error message.
   * @param domainUid domain uid of the domain
   * @param namespace domain namespace in which introspector runs
   * @param expectedErrorMsg error message
   * @param follow whether to follow the log stream of the Pod
   */
  public static void verifyIntrospectorPodLogContainsExpectedErrorMsg(String domainUid,
                                                                      String namespace,
                                                                      String expectedErrorMsg,
                                                                      Boolean follow) {
    final LoggingFacade logger = getLogger();
    // wait and check whether the introspector log contains the expected error message
    logger.info("verifying that the introspector log contains the expected error message");
    testUntil(
        () -> introspectorPodLogContainsExpectedErrorMsg(domainUid, namespace, expectedErrorMsg, follow),
        logger,
        "Checking for the log of introspector pod contains the expected error msg {0}",
        expectedErrorMsg);
  }

  private static boolean introspectorPodLogContainsExpectedErrorMsg(String domainUid,
                                                                    String namespace,
                                                                    String errormsg,
                                                                    Boolean follow) {
    String introspectPodName;
    V1Pod introspectorPod;
    final LoggingFacade logger = getLogger();

    String introspectJobName = getIntrospectJobName(domainUid);
    String labelSelector = String.format("weblogic.domainUID in (%s)", domainUid);

    try {
      introspectorPod = getPod(namespace, labelSelector, introspectJobName);
    } catch (ApiException apiEx) {
      logger.severe("got ApiException while getting pod: {0}", apiEx);
      return false;
    }

    if (introspectorPod != null && introspectorPod.getMetadata() != null) {
      introspectPodName = introspectorPod.getMetadata().getName();
      logger.info("found introspectore pod {0} in namespace {1}", introspectPodName, namespace);
    } else {
      return false;
    }

    String introspectorLog;
    try {
      introspectorLog = Kubernetes.getPodLog(introspectPodName, namespace, null, null, null, follow);
      logger.info("introspector log: {0}", introspectorLog);
    } catch (ApiException apiEx) {
      logger.severe("got ApiException while getting pod log: {0}", apiEx);
      return false;
    }

    return introspectorLog.contains(errormsg);
  }

  /**
   * Check if pod Evicted status is logged in Operator log.
   *
   * @param opNamespace in which the pod is running
   * @param regex the regular expression to which this string is to be matched
   * @return true if pod Evicted status is logged in Operator log, otherwise false
   */
  public static Callable<Boolean> checkPodEvictedStatusInOperatorLogs(String opNamespace, String regex) {
    return () -> {
      String operatorPodName =
          assertDoesNotThrow(() -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace),
          "Can't get operator's pod name");

      String operatorLog =
          assertDoesNotThrow(() -> getPodLog(operatorPodName, opNamespace, OPERATOR_RELEASE_NAME),
          "Can't get operator log");

      return isPodEvictedStatusLoggedInOperator(operatorLog, regex);
    };
  }

  /**
   * Check if the pod log contains the certain text.
   * @param matchStr text to be searched in the log
   * @param podName the name of the pod
   * @param namespace namespace where pod exists
   * @return true if the text exists in the log otherwise false
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean checkPodLogContains(String matchStr, String podName, String namespace)
      throws ApiException {

    return Kubernetes.getPodLog(podName,namespace,null).contains(matchStr);

  }


  /**
   * Check if the pod log contains the certain text.
   * @param regex check string
   * @param podName the name of the pod
   * @param namespace the Operator namespace
   * @return true if regex found, false otherwise.
   */
  public static boolean checkPodLogContainsRegex(String regex, String podName, String namespace) {
    // get the Pod logs
    String operatorPodLog = assertDoesNotThrow(() -> getPodLog(podName, namespace));

    // match regex in pod log
    getLogger().info("Search: {0} in pod {1} log", regex, podName);
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(operatorPodLog);

    return matcher.find();
  }

  /**
   * Search specified regex in an uncompleted pod log.
   * @param regex check string
   * @param domainUid domain uid of the domain
   * @param namespace the Operator namespace
   * @return true if regex found, false otherwise.
   */
  public static boolean checkInUncompletedIntroPodLogContainsRegex(String regex, String domainUid, String namespace) {
    // get introspector pod name
    String introspectorPodName = assertDoesNotThrow(()
        -> getIntrospectorPodName(domainUid, namespace), "Getting introspector pod name failed");
    getLogger().info("introspector pod name is: {0}", introspectorPodName);

    // get the introspector log message
    String introspectorLog = assertDoesNotThrow(()
        -> getPodLog(introspectorPodName, namespace, domainUid + "-introspector", false, 300, true));

    // match regex in domain info
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(introspectorLog);

    return matcher.find();
  }

  /**
   * Get pod name with given prefix.
   * @param namespace namespace where pod exists
   * @return pod name
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodName(String namespace, String podPrefix) throws ApiException {
    String podName = null;
    V1PodList pods = null;
    pods = Kubernetes.listPods(namespace, null);
    if (pods.getItems().size() != 0) {
      for (V1Pod pod : pods.getItems()) {
        if (pod != null && pod.getMetadata().getName().startsWith(podPrefix)) {
          podName = pod.getMetadata().getName();
          break;
        }
      }
    }
    return podName;
  }
}

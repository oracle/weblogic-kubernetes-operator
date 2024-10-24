// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1HTTPIngressPath;
import io.kubernetes.client.openapi.models.V1HTTPIngressRuleValue;
import io.kubernetes.client.openapi.models.V1IngressBackend;
import io.kubernetes.client.openapi.models.V1IngressRule;
import io.kubernetes.client.openapi.models.V1IngressServiceBackend;
import io.kubernetes.client.openapi.models.V1IngressTLS;
import io.kubernetes.client.openapi.models.V1ServiceBackendPort;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.DomainCondition;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionEvaluationListener;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.awaitility.core.EvaluatedCondition;
import org.awaitility.core.TimeoutEvent;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.CRIO;
import static oracle.weblogic.kubernetes.TestConstants.HTTPS_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.HTTP_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.INGRESS_CLASS_FILE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.NODE_IP;
import static oracle.weblogic.kubernetes.TestConstants.NO_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.REMOTECONSOLE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.REMOTECONSOLE_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.SNAKE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.SNAKE_DOWNLOADED_FILENAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_DOWNLOAD_URL_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLE_DOWNLOAD_FILENAME_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WLE_DOWNLOAD_URL_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listIngresses;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApiInOpPod;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithWLDF;
import static oracle.weblogic.kubernetes.actions.impl.UniqueName.random;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.getClusterCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.credentialsNotValid;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.credentialsValid;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppUsingHostHeader;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToImageContainer;
import static oracle.weblogic.kubernetes.utils.FileUtils.isFileExistAndNotEmpty;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createDiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.getLbExternalIp;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodEvictedStatusInOperatorLogs;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.WLSTUtils.executeWLSTScriptInImageContainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common utility class for tests.
 */
public class CommonTestUtils {

  /**
   * Create retry policy with parameters.
   * @param pollDelaySeconds poll delay in seconds
   * @param pollInterval poll interval in seconds
   * @param seconds max wait seconds
   * @return conditionFactory object of retry policy
   */
  public static ConditionFactory createRetryPolicy(int pollDelaySeconds,
                                                   int pollInterval,
                                                   long seconds) {
    return with().pollDelay(pollDelaySeconds, SECONDS)
        .and().with().pollInterval(pollInterval, SECONDS)
        .atMost(seconds, SECONDS).await();
  }

  /**
   * Create retry policy with parameters.
   *
   * @param minutes max wait in minutes
   * @return conditionFactory object of retry policy
   */
  public static ConditionFactory createStandardRetryPolicyWithAtMost(long minutes) {
    return with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(minutes, MINUTES).await();
  }

  public static ConditionFactory withStandardRetryPolicy = createStandardRetryPolicyWithAtMost(5);
  public static ConditionFactory withLongRetryPolicy = createStandardRetryPolicyWithAtMost(15);

  private static int adminListenPort = 7001;

  /**
   * Create a condition factory with custom values for pollDelay, pollInterval and atMost time.
   *
   * @param polldelay starting delay before checking for the condition in seconds
   * @param pollInterval interval time between checking for the condition in seconds
   * @param atMostMinutes how long should it wait for the condition becomes true in minutes
   * @return ConditionFactory custom condition factory
   */
  public static ConditionFactory createCustomConditionFactory(int polldelay, int pollInterval, int atMostMinutes) {
    return with().pollDelay(polldelay, SECONDS)
        .and().with().pollInterval(pollInterval, SECONDS)
        .atMost(atMostMinutes, MINUTES).await();
  }

  /**
   * Test assertion using standard retry policy over time until it passes or the timeout expires.
   * @param conditionEvaluator Condition evaluator
   * @param logger Logger
   * @param msg Message for logging
   * @param params Parameter to message for logging
   */
  public static void testUntil(Callable<Boolean> conditionEvaluator,
                               LoggingFacade logger, String msg, Object... params) {
    testUntil(withStandardRetryPolicy, conditionEvaluator, logger, msg, params);
  }

  /**
   * Test assertion over time until it passes or the timeout expires.
   * @param conditionFactory Configuration for Awaitility condition factory
   * @param conditionEvaluator Condition evaluator
   * @param logger Logger
   * @param msg Message for logging
   * @param params Parameter to message for logging
   */
  public static void testUntil(ConditionFactory conditionFactory, Callable<Boolean> conditionEvaluator,
                               LoggingFacade logger, String msg, Object... params) {
    try {
      conditionFactory
          .conditionEvaluationListener(createConditionEvaluationListener(logger, msg, params))
          .until(conditionEvaluator);
    } catch (ConditionTimeoutException timeout) {
      throw new TimeoutException(MessageFormat.format("Timed out waiting for: " + msg, params), timeout);
    }
  }

  /**
   * Test assertion over time until it passes or the timeout expires.
   * @param conditionFactory Configuration for Awaitility condition factory
   * @param conditionEvaluator Condition evaluator
   * @param logger Logger
   * @param msg Message for logging
   * @param params Parameter to message for logging
   * @return false if timeout, true for success
   */
  public static boolean testUntilNoException(ConditionFactory conditionFactory, Callable<Boolean> conditionEvaluator,
                               LoggingFacade logger, String msg, Object... params) {
    try {
      conditionFactory
          .conditionEvaluationListener(createConditionEvaluationListener(logger, msg, params))
          .until(conditionEvaluator);
      return true;
    } catch (ConditionTimeoutException timeout) {
      return false;
    }
  }

  private static <T> ConditionEvaluationListener<T> createConditionEvaluationListener(
      LoggingFacade logger, String msg, Object... params) {
    return new ConditionEvaluationListener<>() {
      @Override
      public void conditionEvaluated(EvaluatedCondition<T> condition) {
        int paramsSize = params != null ? params.length : 0;
        String preamble;
        String timeInfo;
        if (condition.isSatisfied()) {
          preamble = "Completed: ";
          timeInfo = " (elapsed time {" + paramsSize + "} ms)";
        } else {
          preamble = "Waiting for: ";
          timeInfo = " (elapsed time {" + paramsSize + "} ms, remaining time {" + (paramsSize + 1) + "} ms)";
        }
        logger.info(preamble + msg + timeInfo,
            Stream.concat(
                Optional.ofNullable(params).map(Arrays::asList).orElse(Collections.emptyList()).stream(),
                Stream.of(condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS())).toArray());
      }

      @Override
      public void onTimeout(TimeoutEvent timeoutEvent) {
        int paramsSize = params != null ? params.length : 0;
        logger.info("Timed out waiting for: " + msg + " (elapsed time {" + paramsSize + "} ms)",
            Stream.concat(
                Optional.ofNullable(params).map(Arrays::asList).orElse(Collections.emptyList()).stream(),
                Stream.of(timeoutEvent.getElapsedTimeInMS())).toArray());
      }
    };
  }

  public static ConditionFactory withQuickRetryPolicy = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(3, SECONDS)
      .atMost(120, SECONDS).await();

  /**
   * Check pod is ready and service exists in the specified namespace.
   *
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which the pod exists
   */
  public static void checkPodReadyAndServiceExists(String podName, String domainUid, String namespace) {
    LoggingFacade logger = getLogger();

    logger.info("Check service {0} exists in namespace {1}", podName, namespace);
    checkServiceExists(podName, namespace);

    logger.info("Waiting for pod {0} to be ready in namespace {1}", podName, namespace);
    checkPodReady(podName, domainUid, namespace);
  }

  /**
   * Check pod is ready and service exists in the specified namespace.
   *
   * @param conditionFactory Configuration for Awaitility condition factory
   * @param podName pod name to check
   * @param domainUid the label the pod is decorated with
   * @param namespace the namespace in which the pod exists
   */
  public static void checkPodReadyAndServiceExists(ConditionFactory conditionFactory,
                                                   String podName, String domainUid, String namespace) {
    LoggingFacade logger = getLogger();

    logger.info("Check service {0} exists in namespace {1}", podName, namespace);
    checkServiceExists(conditionFactory, podName, namespace);

    logger.info("Waiting for pod {0} to be ready in namespace {1}", podName, namespace);
    checkPodReady(conditionFactory, podName, domainUid, namespace);
  }

  /**
   * Check service exists in the specified namespace.
   *
   * @param serviceName service name to check
   * @param namespace the namespace in which to check for the service
   */
  public static void checkServiceExists(String serviceName, String namespace) {
    LoggingFacade logger = getLogger();

    testUntil(
        withLongRetryPolicy,
        assertDoesNotThrow(() -> serviceExists(serviceName, null, namespace),
          String.format("serviceExists failed with ApiException for service %s in namespace %s",
            serviceName, namespace)),
        logger,
        "service {0} to exist in namespace {1}",
        serviceName,
        namespace);
  }

  /**
   * Check service exists in the specified namespace.
   *
   * @param conditionFactory Configuration for Awaitility condition factory
   * @param serviceName service name to check
   * @param namespace the namespace in which to check for the service
   */
  public static void checkServiceExists(ConditionFactory conditionFactory,String serviceName, String namespace) {
    LoggingFacade logger = getLogger();
    testUntil(conditionFactory,
        assertDoesNotThrow(() -> serviceExists(serviceName, null, namespace),
            String.format("serviceExists failed with ApiException for service %s in namespace %s",
                serviceName, namespace)),
        logger,
        "service {0} to exist in namespace {1}",
        serviceName,
        namespace);
  }

  /**
   * add security context constraints to the service account of db namespace.
   * @param serviceAccount - service account to add to scc
   * @param namespace - namespace to which the service account belongs
   */
  public static void addSccToDBSvcAccount(String serviceAccount, String namespace) {
    assertTrue(Command
        .withParams(new CommandParams()
            .command("oc adm policy add-scc-to-user privileged -z " + serviceAccount + " -n " + namespace))
        .execute(), "oc expose service failed");
  }

  /**
   * Check service does not exist in the specified namespace.
   *
   * @param serviceName service name to check
   * @param namespace the namespace in which to check the service does not exist
   */
  public static void checkServiceDoesNotExist(String serviceName, String namespace) {
    LoggingFacade logger = getLogger();
    testUntil(
        assertDoesNotThrow(() -> serviceDoesNotExist(serviceName, null, namespace),
          String.format("serviceDoesNotExist failed with ApiException for service %s in namespace %s",
            serviceName, namespace)),
        logger,
        "service {0} to be deleted in namespace {1}",
        serviceName,
        namespace);
  }

  /**
   * Check whether the cluster's replica count matches with input parameter value.
   *
   * @param clusterName Name of cluster to check
   * @param namespace cluster's namespace
   * @param replicaCount replica count value to match
   * @return true, if the cluster replica count is matched
   */
  public static boolean checkClusterReplicaCountMatches(String clusterName,
                                                        String namespace, int replicaCount) throws ApiException {
    ClusterResource clusterResource = getClusterCustomResource(clusterName, namespace, CLUSTER_VERSION);
    return clusterResource != null
        && clusterResource.getSpec().replicas() == replicaCount;
  }

  /** Scale the WebLogic cluster to specified number of servers.
   *  Verify the sample app can be accessed through NGINX if curlCmd is not null.
   *
   * @param clusterName the WebLogic cluster name in the domain to be scaled
   * @param domainUid the domain to which the cluster belongs
   * @param domainNamespace the namespace in which the domain exists
   * @param manageServerPodNamePrefix managed server pod name prefix
   * @param replicasBeforeScale the replicas of the WebLogic cluster before the scale
   * @param replicasAfterScale the replicas of the WebLogic cluster after the scale
   * @param curlCmd the curl command to verify ingress controller can access the sample apps from all managed servers
   *                in the cluster, if curlCmd is null, the method will not verify the accessibility of the sample app
   *                through ingress controller
   * @param expectedServerNames list of managed servers in the cluster before scale, if curlCmd is null,
   *                            set expectedServerNames to null too
   */
  public static void scaleAndVerifyCluster(String clusterName,
                                           String domainUid,
                                           String domainNamespace,
                                           String manageServerPodNamePrefix,
                                           int replicasBeforeScale,
                                           int replicasAfterScale,
                                           String curlCmd,
                                           List<String> expectedServerNames) {

    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, manageServerPodNamePrefix, replicasBeforeScale,
        replicasAfterScale, false, 0, "", "",
        false, "", "", 0, "", "", curlCmd, expectedServerNames);
  }

  /**
   * Scale the WebLogic cluster to specified number of servers.
   * Verify the sample app can be accessed through NGINX if curlCmd is not null.
   *
   * @param clusterName the WebLogic cluster name in the domain to be scaled
   * @param domainUid the domain to which the cluster belongs
   * @param domainNamespace the namespace in which the domain exists
   * @param manageServerPodNamePrefix managed server pod name prefix
   * @param replicasBeforeScale the replicas of the WebLogic cluster before the scale
   * @param replicasAfterScale the replicas of the WebLogic cluster after the scale
   * @param withRestApi whether to use REST API to scale the cluster
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opNamespace the namespace of WebLogic operator
   * @param opServiceAccount the service account for operator
   * @param withWLDF whether to use WLDF to scale cluster
   * @param domainHomeLocation the domain home location of the domain
   * @param scalingAction scaling action, accepted value: scaleUp or scaleDown
   * @param scalingSize the number of servers to scale up or scale down
   * @param myWebAppName the web app name deployed to the domain
   * @param curlCmdForWLDFApp the curl command to call the web app used in the WLDF script
   * @param curlCmd the curl command to verify ingress controller can access the sample apps from all managed servers
   *                in the cluster, if curlCmd is null, the method will not verify the accessibility of the sample app
   *                through ingress controller
   * @param expectedServerNames list of managed servers in the cluster before scale, if curlCmd is null,
   *                            set expectedServerNames to null too
   */
  public static void scaleAndVerifyCluster(String clusterName,
                                           String domainUid,
                                           String domainNamespace,
                                           String manageServerPodNamePrefix,
                                           int replicasBeforeScale,
                                           int replicasAfterScale,
                                           boolean withRestApi,
                                           int externalRestHttpsPort,
                                           String opNamespace,
                                           String opServiceAccount,
                                           boolean withWLDF,
                                           String domainHomeLocation,
                                           String scalingAction,
                                           int scalingSize,
                                           String myWebAppName,
                                           String curlCmdForWLDFApp,
                                           String curlCmd,
                                           List<String> expectedServerNames,
                                           String... args) {
    LoggingFacade logger = getLogger();
    // get the original managed server pod creation timestamp before scale
    List<OffsetDateTime> listOfPodCreationTimestamp = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      String managedServerPodName = manageServerPodNamePrefix + i;
      OffsetDateTime originalCreationTimestamp =
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
              String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
                  managedServerPodName, domainNamespace));
      listOfPodCreationTimestamp.add(originalCreationTimestamp);
    }

    // scale the cluster in the domain
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers",
        clusterName, domainUid, domainNamespace, replicasAfterScale);
    if (withRestApi) {
      if (OKE_CLUSTER && args != null && args.length > 0) {
        String operatorPodName = args[0];
        int opExtPort = 8081;
        assertThat(assertDoesNotThrow(() -> scaleClusterWithRestApiInOpPod(domainUid, clusterName,
            replicasAfterScale, operatorPodName, opExtPort, opNamespace, opServiceAccount)))
            .as(String.format("Verify scaling cluster %s of domain %s in namespace %s with REST API succeeds",
                clusterName, domainUid, domainNamespace))
            .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s with REST API failed",
                clusterName, domainUid, domainNamespace))
            .isTrue();
      } else {
        assertThat(assertDoesNotThrow(() -> scaleClusterWithRestApi(domainUid, clusterName,
            replicasAfterScale, externalRestHttpsPort, opNamespace, opServiceAccount)))
            .as(String.format("Verify scaling cluster %s of domain %s in namespace %s with REST API succeeds",
                clusterName, domainUid, domainNamespace))
            .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s with REST API failed",
                clusterName, domainUid, domainNamespace))
            .isTrue();
      }
    } else if (withWLDF) {
      // scale the cluster using WLDF policy
      assertThat(assertDoesNotThrow(() -> scaleClusterWithWLDF(clusterName, domainUid, domainNamespace,
          domainHomeLocation, scalingAction, scalingSize, opNamespace, opServiceAccount, myWebAppName,
          curlCmdForWLDFApp)))
          .as(String.format("Verify scaling cluster %s of domain %s in namespace %s with WLDF policy succeeds",
              clusterName, domainUid, domainNamespace))
          .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s with WLDF policy failed",
              clusterName, domainUid, domainNamespace))
          .isTrue();
    } else {
      assertThat(assertDoesNotThrow(() -> scaleCluster(clusterName, domainNamespace, replicasAfterScale)))
          .as(String.format("Verify scaling cluster %s of domain %s in namespace %s succeeds",
              clusterName, domainUid, domainNamespace))
          .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s failed",
              clusterName, domainUid, domainNamespace))
          .isTrue();
    }
    verifyClusterAfterScaling(domainUid, domainNamespace, manageServerPodNamePrefix,
        replicasBeforeScale, replicasAfterScale, curlCmd, expectedServerNames, listOfPodCreationTimestamp);
  }
  
  /**
   * Verify the number of servers are as expected after Scale.
   * Verify the sample app can be accessed through NGINX if curlCmd is not null.
   *
   * @param domainUid the domain to which the cluster belongs
   * @param domainNamespace the namespace in which the domain exists
   * @param manageServerPodNamePrefix managed server pod name prefix
   * @param replicasBeforeScale the replicas of the WebLogic cluster before the scale
   * @param replicasAfterScale the replicas of the WebLogic cluster after the scale
   * @param listOfPodCreationTimestamp list of pod creation timestamps
   * @param curlCmd the curl command to verify ingress controller can access the sample apps from all managed servers
   *                in the cluster, if curlCmd is null, the method will not verify the accessibility of the sample app
   *                through ingress controller
   * @param expectedServerNames list of managed servers in the cluster before scale, if curlCmd is null,
   *                            set expectedServerNames to null too
   */
  public static void verifyClusterAfterScaling(
                                           String domainUid,
                                           String domainNamespace,
                                           String manageServerPodNamePrefix,
                                           int replicasBeforeScale,
                                           int replicasAfterScale,
                                           String curlCmd,
                                           List<String> expectedServerNames,
                                           List<OffsetDateTime> listOfPodCreationTimestamp) {
    LoggingFacade logger = getLogger();
    if (replicasBeforeScale <= replicasAfterScale) {

      // scale up
      // check that the original managed server pod state is not changed during scaling the cluster
      for (int i = 1; i <= replicasBeforeScale; i++) {
        String manageServerPodName = manageServerPodNamePrefix + i;

        // check the original managed server pod state is not changed
        logger.info("Checking that the state of manged server pod {0} is not changed in namespace {1}",
            manageServerPodName, domainNamespace);
        podStateNotChanged(manageServerPodName, domainUid, domainNamespace, listOfPodCreationTimestamp.get(i - 1));
      }

      if (curlCmd != null && expectedServerNames != null) {
        // check that NGINX can access the sample apps from the original managed servers in the domain
        logger.info("Checking that NGINX can access the sample app from the original managed servers in the domain "
            + "while the domain is scaling up.");
        logger.info("expected server name list which should be in the sample app response: {0} before scale",
            expectedServerNames);

        assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
            .as("Verify NGINX can access the sample app from the original managed servers in the domain")
            .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
            .isTrue();
      }

      // check that new managed server pods were created and wait for them to be ready
      for (int i = replicasBeforeScale + 1; i <= replicasAfterScale; i++) {
        String manageServerPodName = manageServerPodNamePrefix + i;

        // check new managed server pod exists in the namespace
        logger.info("Checking that the new managed server pod {0} exists in namespace {1}",
            manageServerPodName, domainNamespace);
        checkPodExists(manageServerPodName, domainUid, domainNamespace);

        // check new managed server pod is ready
        logger.info("Checking that the new managed server pod {0} is ready in namespace {1}",
            manageServerPodName, domainNamespace);
        checkPodReady(manageServerPodName, domainUid, domainNamespace);

        // check new managed server service exists in the namespace
        logger.info("Checking that the new managed server service {0} exists in namespace {1}",
            manageServerPodName, domainNamespace);
        checkServiceExists(manageServerPodName, domainNamespace);

        if (expectedServerNames != null) {
          // add the new managed server to the list
          expectedServerNames.add(manageServerPodName.substring(domainUid.length() + 1));
        }
      }

      if (curlCmd != null && expectedServerNames != null) {
        // check that NGINX can access the sample apps from new and original managed servers
        logger.info("Checking that NGINX can access the sample app from the new and original managed servers "
            + "in the domain after the cluster is scaled up. Expected server names: {0}", expectedServerNames);
        assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
            .as("Verify NGINX can access the sample app from all managed servers in the domain")
            .withFailMessage("NGINX can not access the sample app from one or more of the managed servers")
            .isTrue();
      }
    } else {
      // scale down
      // wait and check the pods are deleted
      for (int i = replicasBeforeScale; i > replicasAfterScale; i--) {
        String managedServerPodName = manageServerPodNamePrefix + i;
        logger.info("Checking that managed server pod {0} was deleted from namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
        if (expectedServerNames != null) {
          expectedServerNames.remove(managedServerPodName.substring(domainUid.length() + 1));
        }
      }

      if (curlCmd != null && expectedServerNames != null) {
        // check that NGINX can access the app from the remaining managed servers in the domain
        logger.info("Checking that NGINX can access the sample app from the remaining managed servers in the domain "
            + "after the cluster is scaled down. Expected server name: {0}", expectedServerNames);
        assertThat(callWebAppAndCheckForServerNameInResponse(curlCmd, expectedServerNames, 50))
            .as("Verify NGINX can access the sample app from the remaining managed server in the domain")
            .withFailMessage("NGINX can not access the sample app from the remaining managed server")
            .isTrue();
      }
    }
  }
  

  /**
   * Check that the given credentials are valid to access the WebLogic domain.
   *
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @param expectValid true if the check expects a successful result
   */
  public static void verifyCredentials(
      String podName,
      String namespace,
      String username,
      String password,
      boolean expectValid) {

    verifyCredentials(adminListenPort, podName, namespace, username, password, expectValid);
  }

  /**
   * Check that the given credentials are valid to access the WebLogic domain.
   *
   * @param port listen port of admin server
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @param expectValid true if the check expects a successful result
   */
  public static void verifyCredentials(
      int port,
      String podName,
      String namespace,
      String username,
      String password,
      boolean expectValid,
      String... args) {
    LoggingFacade logger = getLogger();
    String msg = expectValid ? "valid" : "invalid";
    logger.info("Check if the given WebLogic admin credentials are {0}", msg);

    testUntil(
        withQuickRetryPolicy,
        assertDoesNotThrow(
          expectValid ? () -> credentialsValid(port, podName, namespace, username, password, args)
              : () -> credentialsNotValid(port, podName, namespace, username, password, args),
          String.format(
            "Failed to validate credentials %s/%s on pod %s in namespace %s",
            username, password, podName, namespace)),
        logger,
        "Checking that credentials {0}/{1} are {2}",
        username,
        password,
        msg);
  }

  /**
   * Check the system resource configuration using REST API.
   * @param nodePort admin node port
   * @param resourcesType type of the resource
   * @param resourcesName name of the resource
   * @param expectedStatusCode expected status code
   * @return true if the REST API results matches expected status code
   */
  public static boolean checkSystemResourceConfiguration(int nodePort, String resourcesType,
                                                   String resourcesName, String expectedStatusCode) {
    return checkSystemResourceConfiguration(null, nodePort, resourcesType, resourcesName, expectedStatusCode);
  }

  /**
   * Check the system resource configuration using REST API.
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param nodePort admin node port
   * @param resourcesType type of the resource
   * @param resourcesName name of the resource
   * @param expectedStatusCode expected status code
   * @return true if the REST API results matches expected status code
   */
  public static boolean checkSystemResourceConfiguration(String adminSvcExtHost, int nodePort, String resourcesType,
                                                   String resourcesName, String expectedStatusCode) {
    final LoggingFacade logger = getLogger();

    String hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);

    StringBuffer curlString = new StringBuffer("status=$(curl -g --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + hostAndPort)
        .append("/management/weblogic/latest/domainConfig")
        .append("/")
        .append(resourcesType)
        .append("/")
        .append(resourcesName)
        .append("/")
        .append(" --silent --show-error ")
        .append(" -o /dev/null ")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return Command
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedStatusCode);
  }

  /**
   * Check the system resource configuration using REST API.
   * @param adminServerPodName admin pod name
   * @param namespace admin pod namespace
   * @param resourcesType type of the resource
   * @param resourcesName name of the resource
   * @param expectedStatusCode expected status code
   * @return true if results matches expected status code
   */
  public static boolean checkSystemResourceConfiguration(String adminServerPodName, String namespace,
                                                         String resourcesType,
                                                         String resourcesName, String expectedStatusCode) {
    final LoggingFacade logger = getLogger();
    String protocol = "http";
    String port = "7001";

    StringBuffer curlString = new StringBuffer(KUBERNETES_CLI + " exec -n " + namespace + " " + adminServerPodName)
        .append(" -- /bin/bash -c \"")
        .append("curl -g -k --user ")
        .append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" " + protocol + "://")
        .append(adminServerPodName + ":" + port)
        .append("/management/weblogic/latest/domainConfig")
        .append("/")
        .append(resourcesType)
        .append("/")
        .append(resourcesName)
        .append("/")
        .append(" --silent --show-error ")
        .append(" -o /dev/null ")
        .append(" -w %{http_code}")
        .append(" && echo ${status}")
        .append(" \"");
    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return Command
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedStatusCode);
  }

  /**
   * verify the system resource configuration using REST API.
   *
   * @param adminRouteHost     only required for OKD env. null otherwise
   * @param nodePort           admin node port
   * @param resourcesType      type of the resource
   * @param resourcesName      name of the resource
   * @param expectedStatusCode expected status code
   * @param hostHeader         ingress host name to pass as header, only for kind cluster
   */
  public static void verifySystemResourceConfiguration(String adminRouteHost, int nodePort, String resourcesType,
                                      String resourcesName, String expectedStatusCode, String hostHeader) {
    final LoggingFacade logger = getLogger();

    String hostAndPort = getHostAndPort(adminRouteHost, nodePort);
    
    // use traefik LB for kind cluster with ingress host header in url
    String headers = "";
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
      headers = " -H 'host: " + hostHeader + "' ";
    }
    StringBuffer curlString = new StringBuffer("status=$(curl --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" ")
        .append(headers)
        .append(" http://" + hostAndPort)
        .append("/management/weblogic/latest/domainConfig")
        .append("/")
        .append(resourcesType)
        .append("/")
        .append(resourcesName)
        .append("/")
        .append(" -g --silent --show-error ")
        .append(" -o /dev/null ")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkSystemResource: curl command {0}", new String(curlString));

    verifyCommandResultContainsMsg(new String(curlString), expectedStatusCode);
  }

  /**
   * Check the system resource configuration using REST API.
   * @param nodePort admin node port
   * @param resourcesPath path of the resource
   * @param expectedValue expected value returned in the REST call
   * @return true if the REST API results matches expected status code
   */
  public static boolean checkSystemResourceConfig(int nodePort, String resourcesPath, String expectedValue) {
    return checkSystemResourceConfig(null, nodePort, resourcesPath, expectedValue);
  }

  /**
   * Check the system resource configuration using REST API.
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param nodePort admin node port
   * @param resourcesPath path of the resource
   * @param expectedValue expected value returned in the REST call
   * @return true if the REST API results matches expected status code
   */
  public static boolean checkSystemResourceConfig(String adminSvcExtHost, int nodePort,
                                       String resourcesPath, String expectedValue) {
    final LoggingFacade logger = getLogger();

    String hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);

    StringBuffer curlString = new StringBuffer("curl -g --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + hostAndPort)
        .append("/management/weblogic/latest/domainConfig")
        .append("/")
        .append(resourcesPath)
        .append("/");

    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return Command
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedValue);
  }

  /**
   * Check the system resource configuration using REST API.
   * @param adminServerPodName admin server pod name
   * @param namespace admin server pod namespace
   * @param resourcesPath path of the resource
   * @param expectedValue expected value returned in the REST call
   * @return true if the REST API results matches expected status code
   */
  public static boolean checkSystemResourceConfigViaAdminPod(String adminServerPodName, String namespace,
                                                  String resourcesPath, String expectedValue) {
    final LoggingFacade logger = getLogger();

    StringBuffer curlString = new StringBuffer(KUBERNETES_CLI + " exec -n "
        + namespace + " " + adminServerPodName)
        .append(" -- /bin/bash -c \"")
        .append("curl -g --user ")
        .append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + adminServerPodName + ":" + adminListenPort)
        .append("/management/weblogic/latest/domainConfig")
        .append("/")
        .append(resourcesPath)
        .append("/")
        .append(" \"");

    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return Command
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedValue);
  }

  /**
   * Check the system resource configuration using REST API.
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param nodePort admin node port
   * @param expectedValue expected value returned in the REST call
   * @return true if the REST API results matches expected status code
   */
  public static boolean checkSystemResourceDomainConfig(String adminSvcExtHost, int nodePort,
                                                        String expectedValue) {
    final LoggingFacade logger = getLogger();

    String hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);

    StringBuffer curlString = new StringBuffer("curl -g --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + hostAndPort)
        .append("/management/weblogic/latest/domainConfig/");

    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return Command
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedValue);
  }

  /**
   * Check the system resource runtime using REST API.
   * @param adminServerPodName admin server pod name
   * @param namespace admin pod namespace
   * @param resourcesUrl url of the resource
   * @param expectedValue expected value returned in the REST call
   * @return true if the REST API results matches expected value
   */
  public static boolean checkSystemResourceRuntime(String adminServerPodName, String namespace,
                                            String resourcesUrl, String expectedValue) {
    final LoggingFacade logger = getLogger();

    StringBuffer curlString = new StringBuffer(KUBERNETES_CLI + " exec -n "
        + namespace + " " + adminServerPodName)
        .append(" -- /bin/bash -c \"")
        .append("curl -g --user ")
        .append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + adminServerPodName + ":" + adminListenPort)
        .append("/management/weblogic/latest/domainRuntime")
        .append("/")
        .append(resourcesUrl)
        .append("/")
        .append(" \"");

    logger.info("checkSystemResource: curl command {0} expectedValue {1}", new String(curlString), expectedValue);
    return Command
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedValue);
  }


  /**
   * Compile java class inside the pod.
   * @param podName name of the pod
   * @param namespace name of namespace
   * @param destLocation location of java class
   */
  public static void runJavacInsidePod(String podName, String namespace, String destLocation) {
    final LoggingFacade logger = getLogger();

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javacCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ");
    javacCmd.append(namespace);
    javacCmd.append(" -it ");
    javacCmd.append(" -c weblogic-server ");
    javacCmd.append(podName);
    javacCmd.append(" -- /bin/bash -c \"");
    javacCmd.append("javac -cp ");
    javacCmd.append(jarLocation);
    javacCmd.append(" ");
    javacCmd.append(destLocation);
    javacCmd.append(" \"");
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertEquals(0, result.exitValue(), "Client compilation fails");
  }

  /**
   * Compile java class inside the pod.
   * @param podName name of the pod
   * @param namespace name of namespace
   * @param destLocation location of java class
   * @param extraclasspath location of java class
   */
  public static void runJavacInsidePod(String podName, String namespace, String destLocation, String extraclasspath) {
    final LoggingFacade logger = getLogger();

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javacCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ");
    javacCmd.append(namespace);
    javacCmd.append(" -it ");
    javacCmd.append(" -c weblogic-server ");
    javacCmd.append(podName);
    javacCmd.append(" -- /bin/bash -c \"");
    javacCmd.append("javac -cp ");
    javacCmd.append(jarLocation);
    javacCmd.append(":");
    javacCmd.append(extraclasspath);
    javacCmd.append(" ");
    javacCmd.append(destLocation);
    javacCmd.append(" \"");
    logger.info("javac command {0}", javacCmd.toString());
    ExecResult result = assertDoesNotThrow(
        () -> exec(new String(javacCmd), true));
    logger.info("javac returned {0}", result.toString());
    logger.info("javac returned EXIT value {0}", result.exitValue());
    assertEquals(0, result.exitValue(), "Client compilation fails");
  }

  /**
   * Run java client inside the pod using weblogic.jar.
   *
   * @param podName    name of the pod
   * @param namespace  name of the namespace
   * @param javaClientLocation location(path) of java class
   * @param javaClientClass java class name
   * @param args       arguments to the java command
   * @return true if the client ran successfully
   */
  public static Callable<Boolean> runClientInsidePod(String podName, String namespace, String javaClientLocation,
                                                     String javaClientClass, String... args) {
    final LoggingFacade logger = getLogger();

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javapCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ");
    javapCmd.append(namespace);
    javapCmd.append(" -it ");
    javapCmd.append(" -c weblogic-server ");
    javapCmd.append(podName);
    javapCmd.append(" -- /bin/bash -c \"");
    javapCmd.append("java -cp ");
    javapCmd.append(jarLocation);
    javapCmd.append(":");
    javapCmd.append(javaClientLocation);
    javapCmd.append(" ");
    javapCmd.append(javaClientClass);
    javapCmd.append(" ");
    for (String arg:args) {
      javapCmd.append(arg).append(" ");
    }
    javapCmd.append(" \"");
    logger.info("java command to be run {0}", javapCmd.toString());

    return (() -> {
      ExecResult result = assertDoesNotThrow(() -> exec(javapCmd.toString(), true));
      logger.info("java returned {0}", result.toString());
      logger.info("java returned EXIT value {0}", result.exitValue());
      return ((result.exitValue() == 0));
    });
  }

  /**
   * Run java client inside the pod using weblogic.jar.
   *
   * @param podName    name of the pod
   * @param namespace  name of the namespace
   * @param javaClientLocation location(path) of java class
   * @param javaClientClass java class name
   * @param expectedResult expected result
   * @param args       arguments to the java command
   * @return true if the client ran successfully
   */
  public static Callable<Boolean> runClientInsidePodVerifyResult(String podName,
                                                                 String namespace,
                                                                 String javaClientLocation,
                                                                 String javaClientClass,
                                                                 String expectedResult,
                                                                 String... args) {
    final LoggingFacade logger = getLogger();

    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer javapCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ");
    javapCmd.append(namespace);
    javapCmd.append(" -it ");
    javapCmd.append(" -c weblogic-server ");
    javapCmd.append(podName);
    javapCmd.append(" -- /bin/bash -c \"");
    javapCmd.append("java -cp ");
    javapCmd.append(jarLocation);
    javapCmd.append(":");
    javapCmd.append(javaClientLocation);
    javapCmd.append(" ");
    javapCmd.append(javaClientClass);
    javapCmd.append(" ");
    for (String arg:args) {
      javapCmd.append(arg).append(" ");
    }
    javapCmd.append(" \"");
    logger.info("java command to be run {0}", javapCmd.toString());

    return (() -> {
      ExecResult result = assertDoesNotThrow(() -> exec(javapCmd.toString(), true));
      logger.info("java returned {0}", result.toString());
      logger.info("java returned EXIT value {0}", result.exitValue());
      return ((result.exitValue() == 0 && result.stdout().contains(expectedResult)));
    });
  }

  /**
   * Adds proxy extra arguments for image builder command.
   **/
  public static String getImageBuilderExtraArgs() {
    StringBuffer extraArgs = new StringBuffer();

    String httpsproxy = HTTPS_PROXY;
    String httpproxy = HTTP_PROXY;
    String noproxy = NO_PROXY;
    LoggingFacade logger = getLogger();
    logger.info(" httpsproxy : " + httpsproxy);
    String proxyHost;
    StringBuffer mvnArgs = new StringBuffer();
    if (httpsproxy != null) {
      logger.info(" httpsproxy : " + httpsproxy);
      proxyHost = httpsproxy.substring(httpsproxy.lastIndexOf("www"), httpsproxy.lastIndexOf(":"));
      logger.info(" proxyHost: " + proxyHost);
      mvnArgs.append(String.format(" -Dhttps.proxyHost=%s -Dhttps.proxyPort=80 ",
          proxyHost));
      extraArgs.append(String.format(" --build-arg https_proxy=%s", httpsproxy));
    }
    if (httpproxy != null) {
      logger.info(" httpproxy : " + httpproxy);
      proxyHost = httpproxy.substring(httpproxy.lastIndexOf("www"), httpproxy.lastIndexOf(":"));
      logger.info(" proxyHost: " + proxyHost);
      mvnArgs.append(String.format(" -Dhttp.proxyHost=%s -Dhttp.proxyPort=80 ",
          proxyHost));
      extraArgs.append(String.format(" --build-arg http_proxy=%s", httpproxy));
    }
    if (noproxy != null) {
      logger.info(" noproxy : " + noproxy);
      extraArgs.append(String.format(" --build-arg no_proxy=%s",noproxy));
    }
    if (mvnArgs.length() > 0) {
      extraArgs.append(" --build-arg MAVEN_OPTS=\" " + mvnArgs.toString() + "\"");
    }
    return extraArgs.toString();
  }

  /**
   * Call the curl command and check the managed servers connect to each other.
   *
   * @param curlRequest curl command to call the clusterview app
   * @param managedServerNames managed server names part of the cluster
   */
  public static void verifyServerCommunication(String curlRequest, List<String> managedServerNames) {
    LoggingFacade logger = getLogger();

    HashMap<String, Boolean> managedServers = new HashMap<>();
    managedServerNames.forEach(managedServerName -> managedServers.put(managedServerName, false));

    //verify each server in the cluster can connect to other
    testUntil(
        () -> {
          for (int i = 0; i < managedServerNames.size(); i++) {
            logger.info(curlRequest);
            // check the response contains managed server name
            ExecResult result = null;
            try {
              logger.info("Sending request {0}", curlRequest);
              result = ExecCommand.exec(curlRequest, true);
            } catch (IOException | InterruptedException ex) {
              logger.severe(ex.getMessage());
            }

            String response = result != null ? result.stdout().trim() : "result is null";
            logger.info(response);
            for (var managedServer : managedServers.entrySet()) {
              boolean connectToOthers = true;
              logger.info("Looking for Server:" + managedServer.getKey());
              if (response.contains("ServerName:" + managedServer.getKey())) {
                for (String managedServerName : managedServerNames) {
                  logger.info("Looking for Success:" + managedServerName);
                  connectToOthers = connectToOthers && response.contains("Success:" + managedServerName);
                }
                if (connectToOthers) {
                  logger.info("Server:" + managedServer.getKey() + " can see all cluster members");
                  managedServers.put(managedServer.getKey(), true);
                }
              }
            }
          }
          managedServers.forEach((key, value) -> {
            if (value) {
              logger.info("The server {0} can see other cluster members", key);
            } else {
              logger.info("The server {0} unable to see other cluster members ", key);
            }
          });
          return !managedServers.containsValue(false);
        },
        logger,
        "Waiting until each managed server can see other cluster members");
  }

  /**
   * Call the curl command and check the managed server in the cluster can connect to each other.
   *
   * @param curlRequest curl command to call the clusterview app
   * @param managedServerNames managed server names part of the cluster
   * @param manServerName managed server to check
   */
  public static void verifyServerCommunication(String curlRequest, String manServerName,
                                               List<String> managedServerNames) {
    LoggingFacade logger = getLogger();

    HashMap<String, Boolean> managedServers = new HashMap<>();
    managedServerNames.forEach(managedServerName -> managedServers.put(managedServerName, false));

    //verify each server in the cluster can connect to other
    testUntil(
        () -> {
          for (int i = 0; i < managedServerNames.size(); i++) {
            logger.info(curlRequest);
            // check the response contains managed server name
            ExecResult result = null;
            try {
              result = ExecCommand.exec(curlRequest, true);
            } catch (IOException | InterruptedException ex) {
              logger.severe(ex.getMessage());
            }
            String response = result.stdout().trim();
            logger.info(response);
            for (var managedServer : managedServers.entrySet()) {
              boolean connectToOthers = true;
              logger.info("Looking for Server:" + manServerName);
              if (response.contains("ServerName:" + manServerName)) {
                for (String managedServerName : managedServerNames) {
                  logger.info("Looking for Success:" + managedServerName);
                  connectToOthers = connectToOthers && response.contains("Success:" + managedServerName);
                }
                if (connectToOthers) {
                  logger.info("Server:" + manServerName + " can see all cluster members");
                  managedServers.put(managedServer.getKey(), true);
                }
              }
            }
          }
          managedServers.forEach((key, value) -> {
            if (value) {
              logger.info("The server {0} can see other cluster members", key);
            } else {
              logger.info("The server {0} unable to see other cluster members ", key);
            }
          });
          return !managedServers.containsValue(false);
        },
        logger,
        "Waiting until each managed server can see other cluster members");
  }

  public static final int START_PORT = 32000;
  public static final int END_PORT = 32767;

  /**
   * Get the next free port between port and END_PORT.
   * @param startingPort starting port number
   * @param endingPort ending port number
   *
   * @return the next free port number, if there is no free port below END_PORT return -1.
   */
  public static synchronized int getNextFreePort(int startingPort, int endingPort) {
    LoggingFacade logger = getLogger();
    int freePort;
    Random random = new Random();

    while (startingPort <= endingPort) {
      freePort = startingPort + random.nextInt(endingPort - startingPort);
      try {
        isLocalPortFree(freePort, K8S_NODEPORT_HOST);
        if (OKE_CLUSTER) {
          isLocalPortFree(freePort, NODE_IP);
        }
      } catch (IOException ex) {
        return freePort;
      }
    }
    logger.warning("Could not get free port below " + END_PORT);
    return -1;
  }

  /**
   * Get the next free port between port and END_PORT.
   *
   * @return the next free port number, if there is no free port below END_PORT return -1.
   */
  public static synchronized int getNextFreePort() {
    return getNextFreePort(START_PORT, END_PORT);
  }

  /**
   * Check if the given port is free. Tries to connect to the given port, if it succeeds it means that
   * the given port is already in use by an another process.
   *
   * @param port port to check
   * @param host host to check
   * @throws java.io.IOException when the port is not used by any socket
   */
  private static void isLocalPortFree(int port, String host) throws IOException {
    try (Socket socket = new Socket(host, port)) {
      getLogger().info("Port {0} is already in use for host {1}", port, socket.getInetAddress());
    }
  }

  /**
   * Get current date and timestamp in format yyyy-MM-dd-currentimemillis.
   * @return string with date and timestamp
   */
  public static String getDateAndTimeStamp() {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    Date date = new Date();
    return dateFormat.format(date) + "-" + System.currentTimeMillis();
  }

  /**
   * Evaluates the route host name for OKD env, and host:serviceport for other env's.
   *
   * @param hostName - in OKD it is host name when svc is exposed as a route, null otherwise
   * @param servicePort - port of the service to access
   * @return host and port for all env, route hostname for OKD
   */
  public static String getHostAndPort(String hostName, int servicePort) {
    LoggingFacade logger = getLogger();

    try {
      String host;
      String hostAndPort;
      if (OCNE || CRIO) {
        hostAndPort = K8S_NODEPORT_HOST + ":" + servicePort;
      } else {
        if (TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
          host = K8S_NODEPORT_HOST;
        } else {
          if (servicePort >= 30500 && servicePort <= 30600) {
            servicePort -= 29000;
          }
          host = InetAddress.getLocalHost().getHostAddress();
        }
        host = formatIPv6Host(host);
        hostAndPort = ((OKD) ? hostName : host + ":" + servicePort);
      }
      logger.info("hostAndPort = {0} ", hostAndPort);
      if (OKE_CLUSTER_PRIVATEIP) {
        hostAndPort = hostName;
      }
      return hostAndPort;
    } catch (UnknownHostException e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Format hostname for IPV6 address.
   *
   * @param hostname name of the host
   * @return formatted for ipv6
   */
  public static String formatIPv6Host(String hostname) {
    return hostname.contains(":") ? hostname.contains("[") ? hostname : "[" + hostname + "]" : hostname;
  }

  /**
   * Get external IP address of a service.
   *
   * @param serviceName - service name
   * @param nameSpace - nameSpace of service
   * @return external IP address of the given service on OKE
   */
  public static String getServiceExtIPAddrtOke(String serviceName, String nameSpace) {
    LoggingFacade logger = getLogger();
    String serviceExtIPAddr = null;

    if (OKE_CLUSTER) {
      testUntil(
          withLongRetryPolicy,
          isServiceExtIPAddrtOkeReady(serviceName, nameSpace),
          logger,
          "Waiting until external IP address of the service available");

      serviceExtIPAddr =
        assertDoesNotThrow(() -> getLbExternalIp(serviceName, nameSpace),
          "Can't find external IP address of the service " + serviceName);

      logger.info("External IP address of the service is {0} ", serviceExtIPAddr);
    }

    String serviceExtIPAddress = serviceExtIPAddr;
    if (serviceExtIPAddr != null && serviceExtIPAddr.contains(":")) {
      serviceExtIPAddress = "[" + serviceExtIPAddr + "]";
    }

    return serviceExtIPAddress;
  }

  /**
   * Check if external IP address of a service is ready.
   *
   * @param nameSpace - nameSpace of service
   * @param serviceName - service name
   * @return external IP address of the given service on OKE
   */
  public static Callable<Boolean> isServiceExtIPAddrtOkeReady(String serviceName, String nameSpace) {
    LoggingFacade logger = getLogger();
    // Regex for IP address that contains digit from 0 to 255.
    String ipAddressNumber = "(\\d{1,2}|(0|1)\\d{2}|2[0-4]\\d|25[0-5])";

    // Regex for a digit from 0 to 255 and followed by a dot, repeat 4 times to validate an IP address.
    String regex = ipAddressNumber + "\\." + ipAddressNumber + "\\." + ipAddressNumber + "\\." + ipAddressNumber;
    Pattern p = Pattern.compile(regex);

    return () -> {
      String serviceExtIPAddr =
          assertDoesNotThrow(() -> getLbExternalIp(serviceName, nameSpace),
              "Can't find external IP address of the service " + serviceName);

      if (serviceExtIPAddr == null) {
        return false;
      }

      logger.info("External IP address of the service returns {0} ", serviceExtIPAddr);
      Matcher m = p.matcher(serviceExtIPAddr);
      logger.info("Found external IP address of the service: {0} ", m.matches());

      return m.matches();
    };
  }

  /**
   * Verify the command result contains expected message.
   *
   * @param command the command to execute
   * @param expectedMsg the expected message in the command output
   */
  public static void verifyCommandResultContainsMsg(String command, String expectedMsg) {
    testUntil(
        () -> {
          ExecResult result;
          try {
            result = exec(command, true);
            getLogger().info("The command returned exit value: " + result.exitValue()
                + " command output: " + result.stderr() + "\n" + result.stdout());

            if (result == null || result.exitValue() != 0 || result.stdout() == null) {
              return false;
            }

            return result.stdout().contains(expectedMsg);
          } catch (Exception e) {
            getLogger().info("Got exception, command failed with errors " + e.getMessage());
            return false;
          }
        },
        getLogger(),
        "Waiting until command result contains expected message \"{0}\"",
        expectedMsg);
  }

  /**
   * Verify if the WebLogic image is patched with psu.
   * @return true if the WEBLOGIC_IMAGE_TAG contains the string psu
   */
  public static boolean isWebLogicPsuPatchApplied() {
    return  WEBLOGIC_IMAGE_TAG.contains("psu") ? true : false;
  }

  /**
   * Verify domain status conditions contains the given condition type and message.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType condition type
   * @param conditionMsg  messsage in condition
   * @return true if the condition matches
   */
  public static boolean verifyDomainStatusCondition(String domainUid,
                                              String domainNamespace,
                                              String conditionType,
                                              String conditionMsg) {
    withLongRetryPolicy
        .conditionEvaluationListener(
            condition -> getLogger().info("Waiting for domain status condition message contains the expected msg "
                    + "\"{0}\", (elapsed time {1}ms, remaining time {2}ms)",
                conditionMsg,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(() -> {
          DomainResource domain = getDomainCustomResource(domainUid, domainNamespace);
          if ((domain != null) && (domain.getStatus() != null)) {
            for (DomainCondition domainCondition : domain.getStatus().getConditions()) {
              getLogger().info("Condition Type =" + domainCondition.getType()
                  + " Condition Msg =" + domainCondition.getMessage());
              if ((domainCondition.getType() != null && domainCondition.getType().equalsIgnoreCase(conditionType))
                  && (domainCondition.getMessage() != null && domainCondition.getMessage().contains(conditionMsg))) {
                return true;
              }
            }
          }
          return false;
        });
    return false;
  }

  /**
   * Start a port-forward process with a given set of attributes.
   * @param hostName host information to used against address param
   * @param domainNamespace domain namespace
   * @param domainUid domain uid
   * @param port the remote port
   * @return generated local forward port
   */
  public static String startPortForwardProcess(String hostName,
                                       String domainNamespace,
                                       String domainUid,
                                       int port) {
    LoggingFacade logger = getLogger();
    // Create a unique stdout file for kubectl port-forward command
    String pfFileName = RESULTS_ROOT + "/pf-" + domainNamespace
                    + "-" + port + ".out";

    logger.info("Start port forward process");
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // Let kubectl choose and allocate a local port number that is not in use
    StringBuffer cmd = new StringBuffer(KUBERNETES_CLI + " port-forward --address ")
        .append(hostName)
        .append(" pod/")
        .append(adminServerPodName)
        .append(" -n ")
        .append(domainNamespace)
        .append(" :")
        .append(String.valueOf(port))
        .append(" > ")
        .append(pfFileName)
        .append(" 2>&1 &");
    logger.info("Command to forward port {0} ", cmd.toString());
    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(cmd.toString(), true),
        String.format("Failed to forward port by running command %s", cmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to forward a local port to admin port. Error is %s ", result.stderr()));
    assertNotNull(getForwardedPort(pfFileName),
          "port-forward command fails to assign a local port");
    return getForwardedPort(pfFileName);
  }

  /**
   * Start a port-forward process with a given set of attributes.
   * @param hostName host information to used against address param
   * @param namespace  namespace
   * @param port the remote port
   * @param podName name of the pod
   * @return generated local forward port
   */
  public static String startPortForwardProcess(String hostName,
                                               String namespace,
                                               int port,
                                               String podName) {
    LoggingFacade logger = getLogger();
    // Create a unique stdout file for kubectl port-forward command
    String pfFileName = RESULTS_ROOT + "/pf-" + namespace
        + "-" + port + ".out";
    testUntil(
        assertDoesNotThrow(() -> isPodReady(namespace, null, podName),
            "podIsReady failed with ApiException"),
        logger,
        "pod {0} to be running in namespace {1}", podName,
        namespace);

    logger.info("Start port forward process");

    // Let kubectl choose and allocate a local port number that is not in use
    StringBuffer cmd = new StringBuffer(KUBERNETES_CLI + " port-forward --address ")
        .append(hostName)
        .append(" pod/")
        .append(podName)
        .append(" -n ")
        .append(namespace)
        .append(" :")
        .append(String.valueOf(port))
        .append(" > ")
        .append(pfFileName)
        .append(" 2>&1 &");
    logger.info("Command to forward port {0} ", cmd.toString());
    ExecResult result = assertDoesNotThrow(() -> ExecCommand.exec(cmd.toString(), true),
        String.format("Failed to forward port by running command %s", cmd));
    assertEquals(0, result.exitValue(),
        String.format("Failed to forward a local port to admin port. Error is %s ", result.stderr()));
    assertNotNull(getForwardedPort(pfFileName),
        "port-forward command fails to assign a local port");
    return getForwardedPort(pfFileName);
  }

  /**
   * Stop port-forward process(es) started through startPortForwardProcess.
   * @param domainNamespace namespace where port-forward procees were started
   */
  public static void stopPortForwardProcess(String domainNamespace) {
    LoggingFacade logger = getLogger();
    logger.info("Stop port forward process");
    final StringBuffer getPids = new StringBuffer("ps -ef | ")
        .append("grep '" + KUBERNETES_CLI + "* port-forward ' | grep ")
        .append(domainNamespace)
        .append(" | awk ")
        .append(" '{print $2}'");
    logger.info("Command to get pids for port-forward processes {0}", getPids.toString());
    ExecResult result = assertDoesNotThrow(() -> exec(getPids.toString(), true));
    if (result.exitValue() == 0) {
      String[] pids = result.stdout().split(System.lineSeparator());

      for (String pid : pids) {
        logger.info("Command to kill port forward process: {0}", "kill -9 " + pid);
        result = assertDoesNotThrow(() -> exec("kill -9 " + pid, true));
        logger.info("stopPortForwardProcess command returned {0}", result.toString());
      }
    }
  }

  private static String getForwardedPort(String portForwardFileName) {
    //wait until forwarded port number is written to the file upto 5 minutes
    LoggingFacade logger = getLogger();
    assertDoesNotThrow(() ->
        testUntil(
            isFileExistAndNotEmpty(portForwardFileName),
            logger,
            "forwarded port number is written to the file " + portForwardFileName));

    String forwardedPortNo = null;
    String portFile = assertDoesNotThrow(() -> Files.readAllLines(Paths.get(portForwardFileName)).get(0));
    logger.info("Port forward info:\n {0}", portFile);

    String regex = ".*Forwarding.*:(\\d+).*";
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(portFile);
    if (matcher.find()) {
      forwardedPortNo = matcher.group(1);
    }
    return forwardedPortNo;
  }

  /**
   * Start a port-forward process and tests using forwarded port.
   *
   * @param domainUid domain uid
   * @param domainNamespace domain namespace
   * @param istioIngressPort istio ingress port
   * @return generated local forward port
   */
  public static int testPortForwarding(String domainUid,
                                       String domainNamespace,
                                       int istioIngressPort,
                                       String... hosts) {
    LoggingFacade logger = getLogger();

    String hostAndPort = getHostAndPort(null, istioIngressPort);

    // verify ready app is accessible before port forwarding using ingress port
    String readyAppUrl = "http://" + hostAndPort + "/weblogic/ready";

    boolean checlReadyApp = checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checlReadyApp, "Failed to access ready app");
    logger.info("ready app is accessible");

    // forwarding admin port to a local port
    String localhost = "localhost";
    String forwardedPort = startPortForwardProcess(localhost, domainNamespace, domainUid, adminListenPort);
    assertNotNull(forwardedPort, "port-forward command fails to assign local port");
    logger.info("Forwarded local port is {0}", forwardedPort);

    // verify ready app is accessible after port forwarding using the forwarded port
    readyAppUrl = "http://" + localhost + ":" + forwardedPort + "/weblogic/ready";
    checlReadyApp = checkAppUsingHostHeader(readyAppUrl, domainNamespace + ".org");
    assertTrue(checlReadyApp, "Failed to access ready app thru port-forwarded port");
    logger.info("ready app is accessible thru port forwarding");

    // test accessing WLS vis WLST using the forwarded port.
    ExecResult result = accesseWLSViaWLSTUsingForwardedPort(domainUid, domainNamespace, forwardedPort);
    assertNotNull(result, "Connecting to WebLogic failed");
    logger.info("Connecting to Weblogic via WLST using forwarded port {0} returned {1}", result.toString());
    assertTrue(result.stdout().contains("Successfully connected to Admin Server"),
        "Failed to connect to WebLogic via WLST using forwarded port");

    // stop port forwarding process
    stopPortForwardProcess(domainNamespace);

    return Integer.parseInt(forwardedPort);
  }

  /**
   * Connect to WLS running on local machine vis WLST using the forwarded port.
   * e.g. forwarded port is 32001, in the image container, WLST script runs command
   * connect('admin_username','admin_password','t3://localhost:32001').
   *
   * @param domainUid domain uid
   * @param domainNamespace domain namespace
   * @param forwardedPort forwarded local port number to access WebLogic
   * @return ExecResult output of executing WLST script
   */
  public static ExecResult accesseWLSViaWLSTUsingForwardedPort(String domainUid,
                                                              String domainNamespace,
                                                              String forwardedPort) {
    LoggingFacade logger = getLogger();
    final String containerName = "wlsDockerContainer";
    final String wlstScriptFileName = "connect.py";
    final String wlstScriptFilePath = RESOURCE_DIR + "/python-scripts/" + wlstScriptFileName;
    final String wlstScriptDestPath = "/tmp/" + wlstScriptFileName;
    final String wlstPropDestPath = "/tmp/connect.prop";
    final String diiImageName = "wls-docker-container-image";
    final String diiModelFileName = "dii-docker-container.yaml";
    final String diiModelPropFileName = "dii-docker-container.properties";
    ExecResult result = null;

    try {
      // create a dii images to create a WebLogic container
      String diiDomainImage = createDiiImageAndVerify(domainUid, domainNamespace,
          diiImageName, diiModelFileName, diiModelPropFileName, null);
      logger.info("Created dii image: {0}", diiDomainImage);

      // create a WLS container using the dii images created above
      result = createAndStartWlsImageContainerAndVerify(domainUid, containerName, diiDomainImage);
      if (result.exitValue() == 0) {
        logger.info("Create WLS container succeeded: {0}", result.stdout());
      } else {
        logger.info("Create WLS container failed: {0}", result.stderr());
      }

      // create WLST property file
      File wlstPropertiesFile =
          assertDoesNotThrow(() -> File.createTempFile("wlst", ".properties", new File(RESULTS_TEMPFILE)),
          "Creating WLST properties file failed");

      String localhost = "localhost";
      Properties p1 = new Properties();
      p1.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
      p1.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
      p1.setProperty("admin_host", localhost);
      p1.setProperty("admin_port", forwardedPort);
      assertDoesNotThrow(() -> p1.store(new FileOutputStream(wlstPropertiesFile), "wlst properties file"),
          "Failed to write the WLST properties to file");
      logger.info("WLST property file is: {0} ", wlstPropertiesFile.getAbsolutePath());

      // cp WLST script and prop files to the container
      copyFileToImageContainer(containerName, wlstScriptFilePath, wlstScriptDestPath);
      copyFileToImageContainer(containerName, wlstPropertiesFile.getAbsolutePath(), wlstPropDestPath);

      Path filePath = Path.of(wlstPropertiesFile.getAbsolutePath());
      String content = Files.readString(filePath, StandardCharsets.US_ASCII);
      logger.info("Content of WLST property file: {0} ", content);

      // accessing WLS vis WLST using the forwarded port
      result = executeWLSTScriptInImageContainer(containerName, wlstScriptDestPath, wlstPropDestPath);
    } catch (Exception ex) {
      logger.info("Failed to access WLS vis WLST using the forwarded port!");
      ex.printStackTrace();
    } finally {
      stopWlsImageContainer(containerName);
      removeWlsImageContainer(containerName);
    }

    return result;
  }

  /**
   * Create a WebLogic container.
   *
   * @param domainUid domain uid
   * @param containerName container name to create
   * @param imageName image name with tag
   * @return ExecResult output of creating container
   */
  public static ExecResult createAndStartWlsImageContainerAndVerify(String domainUid,
                                                                     String containerName,
                                                                     String imageName) {
    final LoggingFacade logger = getLogger();
    ExecResult result = null;

    // create a WebLogic container
    String createContainerCmd = new StringBuffer(WLSIMG_BUILDER + " run -d -p "
        + adminListenPort + ":" + adminListenPort + " --name=")
        .append(containerName)
        .append(" --network=host ")
        //.append(" --add-host=host.docker.internal:host-gateway ")
        .append(imageName)
        .append(" /u01/oracle/user_projects/domains/")
        .append(domainUid)
        .append("/startWebLogic.sh").toString();
    logger.info("Command to create a WLS container: {0}", createContainerCmd);

    try {
      result = exec(createContainerCmd, true);
      logger.info("Result for WLS container creation is {0}", result);
    } catch (Exception ex) {
      logger.info("createContainerCmd: caught unexpected exception {0}", ex);
    }
    assertNotNull(result, "command returns null");
    if (result.exitValue() == 0) {
      // check if the container started
      logger.info("Wait for container {0} starting", containerName);
      testUntil(
          withStandardRetryPolicy,
          isImageContainerReady(containerName),
          logger,
          "{0} is started",
          containerName);
    } else {
      logger.info("Failed to exec the command {0}. Error is {1} ", createContainerCmd, result.stderr());
    }
    return result;
  }

  /**
   * Check if a WebLogic container is ready.
   *
   * @param containerName container name to check
   * @return true if a WebLogic container is ready, otherwise false
   */
  public static Callable<Boolean> isImageContainerReady(String containerName) {
    return () -> checkImageContainerReady(containerName);
  }

  /**
   * Check if a WebLogic container is in RUNNING mode.
   *
   * @param containerName container name to check
   * @return true if a WebLogic container is in RUNNING mode, otherwise false
   */
  public static boolean checkImageContainerReady(String containerName) {
    final LoggingFacade logger = getLogger();
    ExecResult result = null;

    // check is a WebLogic container RUNNING mode
    String checkContainerCmd = new StringBuffer(WLSIMG_BUILDER + " logs ").append(containerName).toString();
    logger.info("Command to check if WLS container: {0}", checkContainerCmd);

    try {
      result = exec(checkContainerCmd, true);
    } catch (Exception ex) {
      logger.info("Check container status: caught unexpected exception {0}", ex);
      ex.printStackTrace();
    }

    return result != null && result.stdout() != null && result.stdout().contains("The server started in RUNNING mode");
  }

  /**
   * Stop a WebLogic container.
   *
   * @param containerName container name to stop
   * @return ExecResult output of creating container
   */
  public static ExecResult stopWlsImageContainer(String containerName) {
    final LoggingFacade logger = getLogger();
    ExecResult result = null;

    // create a WebLogic container
    String stopContainerCmd = new StringBuffer(WLSIMG_BUILDER + " stop ").append(containerName).toString();
    logger.info("Command to stop a WLS container: {0}", stopContainerCmd);

    try {
      result = exec(stopContainerCmd, true);
    } catch (Exception ex) {
      logger.info("Stop container: caught unexpected exception {0}", ex);
    }

    return result;
  }

  /**
   * Delete a WebLogic container.
   *
   * @param containerName container name to delete
   * @return ExecResult output of creating container
   */
  public static ExecResult removeWlsImageContainer(String containerName) {
    final LoggingFacade logger = getLogger();
    ExecResult result = null;

    // create a WebLogic container
    String stopContainerCmd = new StringBuffer(WLSIMG_BUILDER + " rm ").append(containerName).toString();
    logger.info("Command to stop a WLS container: {0}", stopContainerCmd);

    try {
      result = exec(stopContainerCmd, true);
    } catch (Exception ex) {
      logger.info("Stop container: caught unexpected exception {0}", ex);
    }

    return result;
  }

  /**
   * Generate the model.sessmigr.yaml for a given test class.
   *
   * @param domainUid unique domain identifier
   * @param className test class name
   * @param origModelFile location of original model yaml file
   *
   * @return path of generated yaml file for a session migration test
   */
  public static String generateNewModelFileWithUpdatedDomainUid(String domainUid,
                                                                String className,
                                                                String origModelFile) {
    final String srcModelYamlFile =  MODEL_DIR + "/" + origModelFile;
    final String destModelYamlFile = RESULTS_ROOT + "/" + className + "/" + origModelFile;
    Path srcModelYamlPath = Paths.get(srcModelYamlFile);
    Path destModelYamlPath = Paths.get(destModelYamlFile);

    // create dest dir
    assertDoesNotThrow(() -> Files.createDirectories(
        Paths.get(RESULTS_ROOT + "/" + className)),
        String.format("Could not create directory under %s", RESULTS_ROOT + "/" + className + ""));

    // copy model.sessmigr.yamlto results dir
    assertDoesNotThrow(() -> Files.copy(srcModelYamlPath, destModelYamlPath, REPLACE_EXISTING),
        "Failed to copy " + srcModelYamlFile + " to " + destModelYamlFile);

    // DOMAIN_NAME in model.sessmigr.yaml
    assertDoesNotThrow(() -> replaceStringInFile(
        destModelYamlFile.toString(), "DOMAIN_NAME", domainUid),
        "Could not modify DOMAIN_NAME in " + destModelYamlFile);

    return destModelYamlFile;
  }

  /**
   * Create testwebapp.war.
   *
   * @param domainNamespace domain namespace
   *
   * @return location of testwebapp.war
   */
  public static String createTestWebAppWarFile(String domainNamespace) {
    LoggingFacade logger = getLogger();

    // create testwebapp.war
    String sourceTestWebAppWarLoc = APP_DIR + "/testwebapp";
    String destTestWebAppWarLoc = RESULTS_ROOT + "/" + domainNamespace;
    String createWarCmd = new StringBuffer("sh ")
        .append(APP_DIR)
        .append("/../bash-scripts/build-war-app.sh")
        .append(" -s ")
        .append(sourceTestWebAppWarLoc)
        .append(" -d ")
        .append(destTestWebAppWarLoc).toString();
    logger.info("command to build testwebapp.war {0}", createWarCmd);

    ExecResult execResult = assertDoesNotThrow(() -> exec(createWarCmd, true));
    assertEquals(0, execResult.exitValue(), "Could not create testwebapp.war");

    return destTestWebAppWarLoc + "/testwebapp.war";
  }

  /**
   * Verify Configured System Resource.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   * @param resourceType resource type
   * @param resourceName resource name
   * @param expectedValue expected value
   *
   */
  public static void verifyConfiguredSystemResource(String domainNamespace, String adminServerPodName,
                                               String adminSvcExtHost, String resourceType,
                                               String resourceName,
                                               String expectedValue) {

    LoggingFacade logger = getLogger();
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    testUntil(
        () -> checkSystemResourceConfiguration(adminServerPodName, domainNamespace, resourceType,
            resourceName, expectedValue),
        logger,
        "Checking for adminServerPodName: {0} in domainNamespace: {1} if resourceName: {2} exists",
        adminServerPodName,
        domainNamespace,
        resourceName);
    logger.info("Found the " + resourceType + " configuration");
  }

  /**
   * Check Configured System Resource by Resource Path.
   *
   * @param domainNamespace domain namespace
   * @param adminServerPodName  admin server pod name
   * @param adminSvcExtHost admin server external host
   */
  public static void verifyConfiguredSystemResouceByPath(String domainNamespace, String adminServerPodName,
                                                String adminSvcExtHost, String resourcePath, String expectedValue) {
    LoggingFacade logger = getLogger();
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, adminServiceNodePort, "admin server default node port is not valid");

    testUntil(
        () -> checkSystemResourceConfigViaAdminPod(adminServerPodName, domainNamespace,
            resourcePath,
            expectedValue),
        logger,
        "Checking for adminSvcPod: {0} in namespace: {1} if resourceName: {2} has the right value",
        adminServerPodName,
        domainNamespace,
        resourcePath);
    logger.info("Found the " + resourcePath + " configuration");
  }

  /**
   * Returns the java system property value, converting an empty string to null.
   *
   * @param propertyName the Java system property name
   * @return the actual property value, or null
   */
  public static String getNonEmptySystemProperty(String propertyName) {
    String propertyValue = System.getProperty(propertyName);
    if (propertyValue != null && propertyValue.isEmpty()) {
      propertyValue = null;
    }
    return propertyValue;
  }

  /**
   * Returns the java system property value or the default.
   * Any an empty string in the actual value is treated as null so that the default is returned.
   *
   * @param propertyName the Java system property name
   * @param defaultValue the value to return is the property
   * @return the actual property value or the default value
   */
  public static String getNonEmptySystemProperty(String propertyName, String defaultValue) {
    String propertyValue = System.getProperty(propertyName);
    if (propertyValue == null || propertyValue.isEmpty()) {
      propertyValue = defaultValue;
    }
    return propertyValue;
  }

  /**
   * Get the named property from system environment or Java system property.
   * If the property is defined in the Environment, that value will take precedence over
   * Java properties.
   *
   * @param name the name of the environment variable, or Java property
   * @param defaultValue if no environment variable is defined, nor system property, return this value
   * @return the value defined in the env or system property
   */
  public static String getEnvironmentProperty(String name, String defaultValue) {
    String envValue = System.getenv(name);
    if (envValue == null || envValue.isEmpty()) {
      return getNonEmptySystemProperty(name, defaultValue);
    } else {
      return envValue;
    }
  }

  /**
   * Returns the Kind Repo value with a trailing slash.
   *
   * @param propertyName the name used to retrieve the Kind Repo value
   * @return the Kind Repo value with a trailing slash
   */
  public static String getKindRepoValue(String propertyName) {
    String propertyValue = getNonEmptySystemProperty(propertyName);
    if (propertyValue != null && !propertyValue.endsWith("/")) {
      propertyValue += "/";
    }
    return propertyValue;
  }

  /**
   * Given a repo and tenancy name, determine the prefix length.  For example,
   * cloud.io/foobar/test-images/myimage will treat cloud.io/foobar/ as
   * the prefix so the length is 19.
   *
   * @param baseRepo    base repo name
   * @param baseTenancy base tenancy name
   * @return prefix length to strip when converting to internal repository name
   */
  public static int getBaseImagesPrefixLength(String baseRepo, String baseTenancy) {
    int result = 0;

    if (baseRepo != null && baseRepo.length() > 0) {
      // +1 for the trailing slash
      result += baseRepo.length() + 1;

      if (!baseRepo.equalsIgnoreCase("container-registry.oracle.com")) {
        if (baseTenancy != null && baseTenancy.length() > 0) {
          // +1 for the trailing slash
          result += baseTenancy.length() + 1;
        }
      }
    }
    return result;
  }

  /**
   * Returns the image name.
   *
   * @param kindRepo      the kind repo value
   * @param imageName     the image name
   * @param imageTag      the image tag
   * @param prefixLength  the prefix length of the image name
   * @return the image name and tag
   */
  public static String getKindRepoImageForSpec(String kindRepo, String imageName, String imageTag, int prefixLength) {
    String result = imageName + ":" + imageTag;
    if (kindRepo != null && kindRepo.length() > 0) {
      String imageNoPrefix = result.substring(prefixLength);
      if (kindRepo.endsWith("/")) {
        kindRepo = kindRepo.substring(0, kindRepo.length() - 1);
      }
      result = kindRepo + "/" + imageNoPrefix;
    }
    return result;
  }

  /**
   * Another helper method to deal with the complexities of initializing test constants.
   *
   * @param repo    the domain repo
   * @param tenancy the test tenancy
   * @return the domain prefix
   */
  public static String getDomainImagePrefix(String repo, String tenancy) {
    if (repo != null && repo.length() > 0) {
      if (repo.endsWith("/")) {
        repo = repo.substring(0, repo.length() - 1);
      }

      if (repo.endsWith(".com") || tenancy == null || tenancy.isEmpty()) {
        repo += "/";
      } else {
        repo += "/" + tenancy + "/";
      }
    }
    return repo;
  }

  /**
   * Get a unique name.
   * @param prefix prefix of the name
   * @param suffix suffix of the name
   * @return the full name
   */
  public static String getUniqueName(String prefix, String... suffix) {
    char[] name = new char[6];
    for (int i = 0; i < name.length; i++) {
      name[i] = (char) (random.nextInt(25) + (int) 'a');
    }
    String cmName = prefix + new String(name);
    for (String s : suffix) {
      cmName += s;
    }
    getLogger().info("Creating unique name {0}", cmName);
    return cmName;
  }

  /**
   * check the pod evicted status exists inn Operator log.
   *
   * @param opNamespace in which the Operator pod is running
   * @param podName name of the pod to check
   * @param ephemeralStorage ephemeral storage number
   */
  public static void checkPodEvictedStatus(String opNamespace, String podName, String ephemeralStorage) {
    final LoggingFacade logger = getLogger();

    logger.info("check pod {0} evicted status", podName);
    String regex = new StringBuffer()
        .append(".*Pod\\s")
        .append(podName)
        .append("\\s*was\\s*evicted\\s*due\\s*to\\s*Pod\\s*ephemeral\\s*local\\s*storage")
        .append("\\s*usage\\s*exceeds\\s*the\\s*total\\s*limit\\s*of\\s*containers\\s*")
        .append(ephemeralStorage).toString();

    logger.info("Wait for regex {0} for pod {1} existing in Operator log", regex, podName);
    testUntil(
        withStandardRetryPolicy,
        checkPodEvictedStatusInOperatorLogs(opNamespace, regex),
        logger,
        "{0} is evicted and regex {1} found in Operator log",
        podName,
        regex);
  }

  /**
   * If we use actual URL of WDT or WIT return it. If we use the "latest" release figure out the
   * actual version number and construct the complete URL
   * @return the actual download URL
   * @throws RuntimeException if the operation failed for any reason
   */
  public static String getActualLocationIfNeeded(
      String location,
      String type
  ) throws RuntimeException {
    String actualLocation = location;
    if (needToGetActualLocation(location, type)) {
      actualLocation = location + "/download/" + getInstallerFileName(type);
    } else if (!needToGetActualLocation(location, type) && type.equalsIgnoreCase(REMOTECONSOLE)) {
      actualLocation = location + getInstallerFileName(type);
    }
    getLogger().info("The actual download location for {0} is {1}", type, actualLocation);
    return actualLocation;
  }

  private static boolean needToGetActualLocation(
      String location,
      String type) {
    switch (type) {
      case WDT:
        return WDT_DOWNLOAD_URL_DEFAULT.equals(location);
      case WIT:
        return WIT_DOWNLOAD_URL_DEFAULT.equals(location);
      case WLE:
        return WLE_DOWNLOAD_URL_DEFAULT.equals(location);
      default:
        return false;
    }
  }

  /**
   * Get the installer download filename.
   * @return the download filename
   */
  public static String getInstallerFileName(
      String type) {
    switch (type) {
      case WDT:
        return WDT_DOWNLOAD_FILENAME_DEFAULT;
      case WIT:
        return WIT_DOWNLOAD_FILENAME_DEFAULT;
      case WLE:
        return WLE_DOWNLOAD_FILENAME_DEFAULT;
      case SNAKE:
        return SNAKE_DOWNLOADED_FILENAME;
      case REMOTECONSOLE:
        return REMOTECONSOLE_DOWNLOAD_FILENAME_DEFAULT;
      default:
        return "";
    }
  }

  /**
   * Get the image repo from an image name.
   * @param imageName the image name
   * @return image repo
   */
  public static String getImageRepoFromImageName(String imageName) {
    String imageRepo = null;
    if (imageName != null && imageName.contains("/")) {
      getLogger().info("Getting image repo from imageName {0}", imageName);
      int indexOfSlash = imageName.indexOf("/");
      imageRepo = imageName.substring(0, indexOfSlash);
    } else {
      getLogger().info("Can not get the image repo from imageName {0}", imageName);
    }
    return imageRepo;
  }
  
  /**
   * Backup failsafe-reports directory to a temporary location.
   *
   * @param uniqueDir directory to save reports
   * @return absolute path of the reports directory
   */
  public static String backupReports(String uniqueDir) {
    String srcContents = ITTESTS_DIR + "/target/failsafe-reports/*";
    String dstDir = WORK_DIR + "/" + uniqueDir;
    CommandParams params = new CommandParams().defaults();
    Command.withParams(params.command("ls -lrt " + srcContents)).execute();
    Command.withParams(params.command("mkdir -p " + dstDir)).execute();
    Command.withParams(params.command("cp " + srcContents + " " + dstDir)).execute();
    return dstDir;
  }

  /**
   * Restore reports from backup.
   *
   * @param backupDir directory containing the reports
   */
  public static void restoreReports(String backupDir) {
    String dstDir = ITTESTS_DIR + "/target/failsafe-reports";
    CommandParams params = new CommandParams().defaults();
    Command.withParams(params.command("mkdir -p " + dstDir)).execute();
    Command.withParams(params.command("cp " + backupDir + "/* " + dstDir)).execute();
  }

  /**
   * Exec a command inside WebLogic server pod.
   *
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param serverPodName Name of the WebLogic server pod to which the command should be sent to
   * @param serverPort server port number
   * @param resourcePath Path of the system resource to be used in the REST API call
   * @return Exec result
   */
  public static ExecResult exeAppInServerPod(String domainNamespace,
                                             String serverPodName,
                                             int serverPort,
                                             String resourcePath) {
    LoggingFacade logger = getLogger();
    String commandToRun = KUBERNETES_CLI + " exec -n "
        + domainNamespace + "  " + serverPodName + " -- curl --user "
        + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT
        + " http://" + serverPodName + ":" + serverPort + resourcePath;
    logger.info("curl command to run in admin pod {0} is: {1}", serverPodName, commandToRun);

    ExecResult result = null;
    try {
      result = ExecCommand.exec(commandToRun, true);
      logger.info("result is: {0}", result.toString());
    } catch (IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
    return result;
  }

  /**
   * Run a command inside WebLogic server pod and check the result.
   *
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param serverPodName Name of the WebLogic server pod to which the command should be sent to
   * @param serverPort server port number
   * @param resourcePath Path of the system resource to be used in the REST API call
   * @param expectedStatusCode the expected response to verify
   * @return true if command exe reply contains the expected response
   */
  public static boolean checkAppIsRunningInServerPod(String domainNamespace,
                                                     String serverPodName,
                                                     int serverPort,
                                                     String resourcePath,
                                                     String expectedStatusCode) {
    ExecResult result = exeAppInServerPod(domainNamespace, serverPodName, serverPort, resourcePath);

    return (result.exitValue() == 0 && result.stdout().contains(expectedStatusCode));
  }

  /**
   * Check if a deployed App is ready.
   *
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param serverPodName Name of the WebLogic server pod to which the command should be sent to
   * @param serverPort server port number
   * @param resourcePath Path of the system resource to be used in the REST API call
   * @param expectedStatusCode the expected response to verify
   * @return true if command exe reply contains the expected response
   */
  public static Callable<Boolean> isAppInServerPodReady(String domainNamespace,
                                                        String serverPodName,
                                                        int serverPort,
                                                        String resourcePath,
                                                        String expectedStatusCode) {
    return () -> checkAppIsRunningInServerPod(domainNamespace,
        serverPodName, serverPort, resourcePath, expectedStatusCode);
  }
  
  /**
   * Create ingress resource for a single service.
   *
   * @param domainNamespace namespace in which the service exists
   * @param domainUid domain resource name
   * @param serviceName name of the service for which to create ingress routing
   * @param port container port of the service
   * @return hostheader host header
   */
  public static String createIngressHostRouting(String domainNamespace, String domainUid,
      String serviceName, int port) {
    return createIngressHostRouting(domainNamespace, domainUid, serviceName, port, null, null, false);
  }

  /**
   * Create ingress resource for a single service.
   *
   * @param domainNamespace namespace in which the service exists
   * @param domainUid domain resource name
   * @param serviceName name of the service for which to create ingress routing
   * @param port container port of the service
   * @param annoations ingress annotations
   * @param tlsList list of tls secrets
   * @return hostheader host header
   */
  public static String createIngressHostRouting(String domainNamespace, String domainUid,
      String serviceName, int port, Map<String, String> annoations, List<V1IngressTLS> tlsList, boolean isSecureMode) {
    // create an ingress in domain namespace
    // set the ingress rule host
    String ingressHost = domainNamespace + "." + domainUid + "." + serviceName;

    // create ingress rules for two domains
    List<V1IngressRule> ingressRules = new ArrayList<>();

    V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
        .path(null)
        .pathType("ImplementationSpecific")
        .backend(new V1IngressBackend()
            .service(new V1IngressServiceBackend()
                .name(domainUid + "-" + serviceName)
                .port(new V1ServiceBackendPort().number(port)))
        );

    V1IngressRule ingressRule = new V1IngressRule()
        .host(ingressHost)
        .http(new V1HTTPIngressRuleValue()
            .paths(Collections.singletonList(httpIngressPath)));
    ingressRules.add(ingressRule);

    String ingressName = domainNamespace + "-" + domainUid + "-" + serviceName + "-" + port;
    assertDoesNotThrow(() -> createIngress(ingressName, domainNamespace, annoations,
        Files.readString(INGRESS_CLASS_FILE_NAME), ingressRules, tlsList));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);

    String curlCmd;
    if (OCNE) {
      curlCmd = assertDoesNotThrow(() -> "curl -g -k --silent --show-error --noproxy '*' -H 'host: "
          + ingressHost + "' " + (isSecureMode ? "https" : "http") + "://"
          + K8S_NODEPORT_HOST + ":" + TRAEFIK_INGRESS_HTTP_NODEPORT
          + "/weblogic/ready --write-out %{http_code} -o /dev/null");
    } else {
      curlCmd = assertDoesNotThrow(() -> "curl -g -k --silent --show-error --noproxy '*' -H 'host: "
          + ingressHost + "' " + (isSecureMode ? "https" : "http") + "://"
          + formatIPv6Host(InetAddress.getLocalHost().getHostAddress()) + ":" + TRAEFIK_INGRESS_HTTP_HOSTPORT
          + "/weblogic/ready --write-out %{http_code} -o /dev/null");
    }
    getLogger().info("Executing curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));

    getLogger().info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);
    return ingressHost;
  }
  
  /**
   * Create ingress resource for a single service.
   *
   * @param domainNamespace namespace in which the service exists
   * @param domainUid domain resource name
   * @param path path prefix
   * @param serviceName name of the service for which to create ingress routing
   * @param port container port of the service
   * @param ingressClassName ingress class name
   */ 
  public static void createIngressPathRouting(String domainNamespace, String domainUid, String path,
      String serviceName, int port, String ingressClassName) {
    // create an ingress in domain namespace
    V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
        .path(path)
        .pathType("Prefix")
        .backend(new V1IngressBackend()
            .service(new V1IngressServiceBackend()
                .name(domainUid + "-" + serviceName)
                .port(new V1ServiceBackendPort().number(port)))
        );

    // create ingress rule
    List<V1IngressRule> ingressRules = new ArrayList<>();
    V1IngressRule ingressRule = new V1IngressRule()
        .http(new V1HTTPIngressRuleValue()
            .paths(Collections.singletonList(httpIngressPath)));
    ingressRules.add(ingressRule);

    String ingressName = domainNamespace + "-" + domainUid + "-" + serviceName;
    assertDoesNotThrow(() -> createIngress(ingressName, domainNamespace, null,
        ingressClassName, ingressRules, null));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(domainNamespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, domainNamespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, domainNamespace))
        .contains(ingressName);
    getLogger().info("ingress {0} was created in namespace {1}", ingressName, domainNamespace);
  }

  /**
   * Create ingress resource for a single service.
   *
   * @param namespace namespace in which the service exists
   * @param path path prefix
   * @param serviceName name of the service for which to create ingress routing
   * @param port container port of the service
   * @param ingressClassName ingress class name
   * @param host ingress host name
   */
  public static void createIngressPathRouting(String namespace, String path,
                                              String serviceName, int port, String ingressClassName,
                                              String host) {
    // create an ingress in domain namespace
    V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
        .path(path)
        .pathType("Prefix")
        .backend(new V1IngressBackend()
            .service(new V1IngressServiceBackend()
                .name(serviceName)
                .port(new V1ServiceBackendPort().number(port)))
        );

    // create ingress rule
    List<V1IngressRule> ingressRules = new ArrayList<>();
    V1IngressRule ingressRule = new V1IngressRule()
        .host(host)
        .http(new V1HTTPIngressRuleValue()
            .paths(Collections.singletonList(httpIngressPath)));
    ingressRules.add(ingressRule);

    String ingressName = namespace + "-" + serviceName;
    assertDoesNotThrow(() -> createIngress(ingressName, namespace, null,
        ingressClassName, ingressRules, null));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(namespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, namespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, namespace))
        .contains(ingressName);
    getLogger().info("ingress {0} was created in namespace {1}", ingressName, namespace);
  }

  /**
   * Create ingress resource for a single service.
   *
   * @param namespace namespace in which the service exists
   * @param path path prefix
   * @param serviceName name of the service for which to create ingress routing
   * @param port container port of the service
   * @param ingressClassName ingress class name
   */
  public static void createIngressPathRouting(String namespace, String path,
                                                     String serviceName, int port, String ingressClassName) {
    // create an ingress in domain namespace
    V1HTTPIngressPath httpIngressPath = new V1HTTPIngressPath()
        .path(path)
        .pathType("Prefix")
        .backend(new V1IngressBackend()
            .service(new V1IngressServiceBackend()
                .name(serviceName)
                .port(new V1ServiceBackendPort().number(port)))
        );

    // create ingress rule
    List<V1IngressRule> ingressRules = new ArrayList<>();
    V1IngressRule ingressRule = new V1IngressRule()
        .http(new V1HTTPIngressRuleValue()
            .paths(Collections.singletonList(httpIngressPath)));
    ingressRules.add(ingressRule);

    String ingressName = namespace + "-" + serviceName;
    assertDoesNotThrow(() -> createIngress(ingressName, namespace, null,
        ingressClassName, ingressRules, null));

    // check the ingress was found in the domain namespace
    assertThat(assertDoesNotThrow(() -> listIngresses(namespace)))
        .as(String.format("Test ingress %s was found in namespace %s", ingressName, namespace))
        .withFailMessage(String.format("Ingress %s was not found in namespace %s", ingressName, namespace))
        .contains(ingressName);
    getLogger().info("ingress {0} was created in namespace {1}", ingressName, namespace);
  }
  
}

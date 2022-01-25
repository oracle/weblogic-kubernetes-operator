// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;


import java.io.IOException;
import java.net.Socket;
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
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainCondition;
import oracle.weblogic.kubernetes.actions.TestActions;
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
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithRestApi;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleClusterWithWLDF;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.credentialsNotValid;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.credentialsValid;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndCheckForServerNameInResponse;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.isFileExistAndNotEmpty;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common utility class for tests.
 */
public class CommonTestUtils {

  private static ConditionFactory createStandardRetryPolicyWithAtMost(long minutes) {
    return with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(minutes, MINUTES).await();
  }

  public static ConditionFactory withStandardRetryPolicy = createStandardRetryPolicyWithAtMost(5);
  public static ConditionFactory withLongRetryPolicy = createStandardRetryPolicyWithAtMost(15);

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

  private static <T> ConditionEvaluationListener<T> createConditionEvaluationListener(
      LoggingFacade logger, String msg, Object... params) {
    return new ConditionEvaluationListener<T>() {
      @Override
      public void conditionEvaluated(EvaluatedCondition condition) {
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
    assertTrue(new Command()
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
   * @param domainName Name of domain to which cluster belongs
   * @param namespace cluster's namespace
   * @param replicaCount replica count value to match
   * @return true, if the cluster replica count is matched
   */
  public static boolean checkClusterReplicaCountMatches(String clusterName, String domainName,
                                                        String namespace, Integer replicaCount) throws ApiException {
    Cluster cluster = TestActions.getDomainCustomResource(domainName, namespace).getSpec().getClusters()
            .stream().filter(c -> c.clusterName().equals(clusterName)).findAny().orElse(null);
    return Optional.ofNullable(cluster).get().replicas() == replicaCount;
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
                                           List<String> expectedServerNames) {
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
      assertThat(assertDoesNotThrow(() -> scaleClusterWithRestApi(domainUid, clusterName,
          replicasAfterScale, externalRestHttpsPort, opNamespace, opServiceAccount)))
          .as(String.format("Verify scaling cluster %s of domain %s in namespace %s with REST API succeeds",
              clusterName, domainUid, domainNamespace))
          .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s with REST API failed",
              clusterName, domainUid, domainNamespace))
          .isTrue();
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
      assertThat(assertDoesNotThrow(() -> scaleCluster(domainUid, domainNamespace, clusterName, replicasAfterScale)))
          .as(String.format("Verify scaling cluster %s of domain %s in namespace %s succeeds",
              clusterName, domainUid, domainNamespace))
          .withFailMessage(String.format("Scaling cluster %s of domain %s in namespace %s failed",
              clusterName, domainUid, domainNamespace))
          .isTrue();
    }

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

    verifyCredentials(null, podName, namespace, username, password, expectValid);
  }

  /**
   * Check that the given credentials are valid to access the WebLogic domain.
   *
   * @param host this is only for OKD - ingress host to access the service
   * @param podName name of the admin server pod
   * @param namespace name of the namespace that the pod is running in
   * @param username WebLogic admin username
   * @param password WebLogic admin password
   * @param expectValid true if the check expects a successful result
   */
  public static void verifyCredentials(
      String host,
      String podName,
      String namespace,
      String username,
      String password,
      boolean expectValid,
      String... args) {
    LoggingFacade logger = getLogger();
    String msg = expectValid ? "valid" : "invalid";
    logger.info("Check if the given WebLogic admin credentials are {0}", msg);
    String finalHost = host != null ? host : K8S_NODEPORT_HOST;
    logger.info("finalHost = {0}", finalHost);
    testUntil(
        withQuickRetryPolicy,
        assertDoesNotThrow(
          expectValid ? () -> credentialsValid(finalHost, podName, namespace, username, password, args)
              : () -> credentialsNotValid(finalHost, podName, namespace, username, password, args),
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

    String hostAndPort = (OKD) ? adminSvcExtHost : K8S_NODEPORT_HOST + ":" + nodePort;
    logger.info("hostAndPort = {0} ", hostAndPort);

    StringBuffer curlString = new StringBuffer("status=$(curl --user ");
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
    return new Command()
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedStatusCode);
  }

  /**
   * verify the system resource configuration using REST API.
   * @param adminRouteHost only required for OKD env. null otherwise
   * @param nodePort admin node port
   * @param resourcesType type of the resource
   * @param resourcesName name of the resource
   * @param expectedStatusCode expected status code
   */
  public static void verifySystemResourceConfiguration(String adminRouteHost, int nodePort, String resourcesType,
                                                       String resourcesName, String expectedStatusCode) {
    final LoggingFacade logger = getLogger();
    StringBuffer curlString = new StringBuffer("status=$(curl --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + getHostAndPort(adminRouteHost, nodePort))
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

    String hostAndPort = (OKD) ? adminSvcExtHost : K8S_NODEPORT_HOST + ":" + nodePort;
    logger.info("hostAndPort = {0} ", hostAndPort);

    StringBuffer curlString = new StringBuffer("curl --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + hostAndPort)
        .append("/management/weblogic/latest/domainConfig")
        .append("/")
        .append(resourcesPath)
        .append("/");

    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return new Command()
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedValue);
  }

  /**
   * Check the system resource runtime using REST API.
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param nodePort admin node port
   * @param resourcesUrl url of the resource
   * @param expectedValue expected value returned in the REST call
   * @return true if the REST API results matches expected value
   */
  public static boolean checkSystemResourceRuntime(String adminSvcExtHost, int nodePort, 
                                            String resourcesUrl, String expectedValue) {
    final LoggingFacade logger = getLogger();

    String hostAndPort = (OKD) ? adminSvcExtHost : K8S_NODEPORT_HOST + ":" + nodePort;
    logger.info("hostAndPort = {0} ", hostAndPort);

    StringBuffer curlString = new StringBuffer("curl --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + hostAndPort)
        .append("/management/weblogic/latest/domainRuntime")
        .append("/")
        .append(resourcesUrl)
        .append("/");

    logger.info("checkSystemResource: curl command {0} expectedValue {1}", new String(curlString), expectedValue);
    return new Command()
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
    StringBuffer javacCmd = new StringBuffer("kubectl exec -n ");
    javacCmd.append(namespace);
    javacCmd.append(" -it ");
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
    assertTrue(result.exitValue() == 0, "Client compilation fails");
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
    StringBuffer javapCmd = new StringBuffer("kubectl exec -n ");
    javapCmd.append(namespace);
    javapCmd.append(" -it ");
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
   * Adds proxy extra arguments for docker command.
   **/
  public static String getDockerExtraArgs() {
    StringBuffer extraArgs = new StringBuffer("");

    String httpsproxy = Optional.ofNullable(System.getenv("HTTPS_PROXY")).orElse(System.getenv("https_proxy"));
    String httpproxy = Optional.ofNullable(System.getenv("HTTP_PROXY")).orElse(System.getenv("http_proxy"));
    String noproxy = Optional.ofNullable(System.getenv("NO_PROXY")).orElse(System.getenv("no_proxy"));
    LoggingFacade logger = getLogger();
    logger.info(" httpsproxy : " + httpsproxy);
    String proxyHost = "";
    StringBuffer mvnArgs = new StringBuffer("");
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
    if (!mvnArgs.equals("")) {
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
              result = ExecCommand.exec(curlRequest, true);
            } catch (IOException | InterruptedException ex) {
              logger.severe(ex.getMessage());
            }
            String response = result.stdout().trim();
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

  /**
   * Get the next free port between from and to.
   *
   * @param from range starting point
   * @param to range ending point
   * @return the next free port number, if there is no free port between the range, return the ending point
   */
  public static synchronized int getNextFreePort(int from, int to) {
    LoggingFacade logger = getLogger();
    int port;
    for (port = from; port < to; port++) {
      if (isLocalPortFree(port)) {
        logger.info("next free port is: {0}", port);
        return port;
      }
    }
    logger.info("Can not find free port between {0} and {1}", from, to);
    return port;
  }

  private static int port = 30000;
  private static final int END_PORT = 32767;

  /**
   * Get the next free port between port and END_PORT.
   *
   * @return the next free port number, if there is no free port below END_PORT return -1.
   */
  public static synchronized int getNextFreePort() {
    LoggingFacade logger = getLogger();
    int freePort = 0;
    while (port <= END_PORT) {
      freePort = port++;
      if (isLocalPortFree(freePort)) {
        logger.info("next free port is: {0}", freePort);
        return freePort;
      }
    }
    logger.warning("Could not get free port below " + END_PORT);
    return -1;
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
   * Check if the given port number is free.
   *
   * @param port port number to check
   * @return true if the port is free, false otherwise
   */
  private static boolean isLocalPortFree(int port) {
    LoggingFacade logger = getLogger();
    Socket socket = null;
    try {
      socket = new Socket(K8S_NODEPORT_HOST, port);
      return false;
    } catch (IOException ignored) {
      return true;
    } finally {
      if (socket != null) {
        try {
          socket.close();
        } catch (IOException ex) {
          logger.severe("can not close Socket {0}", ex.getMessage());
        }
      }
    }
  }

  /**
   * Evaluates the route host name for OKD env, and host:serviceport for othe env's.
   *
   * @param hostName - in OKD it is host name when svc is exposed as a route, null otherwise
   * @param servicePort - port of the service to access
   * @return host and port for all env, route hostname for OKD
   */
  public static String getHostAndPort(String hostName, int servicePort) {
    LoggingFacade logger = getLogger();
    String hostAndPort = ((OKD) ? hostName : K8S_NODEPORT_HOST + ":" + servicePort);
    logger.info("hostAndPort = {0} ", hostAndPort);
    return hostAndPort;
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
          Domain domain = getDomainCustomResource(domainUid, domainNamespace);
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
    StringBuffer cmd = new StringBuffer("kubectl port-forward --address ")
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
   * Stop port-forward process(es) started through startPortForwardProcess.
   * @param domainNamespace namespace where port-forward procees were started 
   */
  public static void stopPortForwardProcess(String domainNamespace) {
    LoggingFacade logger = getLogger();
    logger.info("Stop port forward process");
    final StringBuffer getPids = new StringBuffer("ps -ef | ")
        .append("grep 'kubectl* port-forward ' | grep ")
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
    String portFile = assertDoesNotThrow(() -> Files.readAllLines(Paths.get(portForwardFileName)).get(0));
    logger.info("Port forward info:\n {0}", portFile);
    String forwardedPortNo = null;
    String regex = ".*Forwarding.*:(\\d+).*";
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
    Matcher matcher = pattern.matcher(portFile);
    if (matcher.find()) {
      forwardedPortNo = matcher.group(1);
    }
    return forwardedPortNo;
  }

  /**
   * Generate the model.sessmigr.yaml for a given test class
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
}

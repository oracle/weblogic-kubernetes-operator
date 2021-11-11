// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.JAVA_LOGGING_LEVEL_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.startOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.stopOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorRestServiceRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.serviceAccountIsCreated;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createExternalRestIdentitySecret;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OperatorUtils {

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param domainPresenceFailureRetryMaxCount the number of introspector job retries for a Domain
   * @param domainPresenceFailureRetrySeconds the interval in seconds between these retries
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        int domainPresenceFailureRetryMaxCount,
                                                        int domainPresenceFailureRetrySeconds,
                                                        String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opNamespace + "-sa", false, 0, opHelmParams, false, null, null,
        false, domainPresenceFailureRetryMaxCount, domainPresenceFailureRetrySeconds, domainNamespace);

  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opHelmParams the Helm parameters to install operator
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace, HelmParams opHelmParams,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withRestAPI,
                                                        int externalRestHttpsPort,
                                                        String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, domainNamespace);

  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withRestAPI,
                                                        int externalRestHttpsPort,
                                                        boolean elkIntegrationEnabled,
                                                        String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled, domainNamespace);

  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, false, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        boolean elkIntegrationEnabled,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled,
        null, null, false, -1, -1, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespaceSelectionStrategy value to tell the operator
   *                                         how to select the set of namespaces that it will manage
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        String domainNamespaceSelectionStrategy,
                                                        boolean elkIntegrationEnabled,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled,
        domainNamespaceSelectionStrategy, null, false, -1, -1, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List, value to tell the operator
   *                                  how to select the set of namespaces that it will manage
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param domainPresenceFailureRetryMaxCount the number of introspector job retries for a Domain
   * @param domainPresenceFailureRetrySeconds the interval in seconds between these retries
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        boolean elkIntegrationEnabled,
                                                        String domainNamespaceSelectionStrategy,
                                                        String domainNamespaceSelector,
                                                        boolean enableClusterRoleBinding,
                                                        int domainPresenceFailureRetryMaxCount,
                                                        int domainPresenceFailureRetrySeconds,
                                                        String... domainNamespace) {
    LoggingFacade logger = getLogger();

    // Create a service account for the unique opNamespace
    logger.info("Creating service account");
    testUntil(
        serviceAccountIsCreated(new V1ServiceAccount()
            .metadata(new V1ObjectMeta()
                .namespace(opNamespace)
                .name(opServiceAccount))),
        logger,
        "creating operator service account");

    logger.info("Created service account: {0}", opServiceAccount);


    // get operator image name
    String operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "operator image name can not be empty");
    logger.info("operator image name {0}", operatorImage);

    // Create Docker registry secret in the operator namespace to pull the image from repository
    // this secret is used only for non-kind cluster
    logger.info("Creating Docker registry secret in namespace {0}", opNamespace);
    createOcirRepoSecret(opNamespace);

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", OCIR_SECRET_NAME);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(Arrays.asList(domainNamespace))
        .serviceAccount(opServiceAccount);

    if (domainNamespaceSelectionStrategy != null) {
      opParams.domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy);
    }

    // use default image in chart when repoUrl is set, otherwise use latest/current branch operator image
    if (opHelmParams.getRepoUrl() == null) {
      opParams.image(operatorImage);
    }

    // enable ELK Stack
    if (elkIntegrationEnabled) {
      opParams
          .elkIntegrationEnabled(elkIntegrationEnabled);
      opParams
          .elasticSearchHost(ELASTICSEARCH_HOST);
      opParams
          .elasticSearchPort(ELASTICSEARCH_HTTP_PORT);
      opParams
          .javaLoggingLevel(JAVA_LOGGING_LEVEL_VALUE);
      opParams
          .logStashImage(LOGSTASH_IMAGE);
    }

    if (withRestAPI) {
      // create externalRestIdentitySecret
      assertTrue(createExternalRestIdentitySecret(opNamespace, DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME),
          "failed to create external REST identity secret");
      opParams
          .externalRestEnabled(true)
          .externalRestHttpsPort(externalRestHttpsPort)
          .externalRestIdentitySecret(DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME);
    }
    // operator chart values to override
    if (enableClusterRoleBinding) {
      opParams.enableClusterRoleBinding(enableClusterRoleBinding);
    }
    if (domainNamespaceSelectionStrategy != null) {
      opParams.domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy);
      if (domainNamespaceSelectionStrategy.equalsIgnoreCase("LabelSelector")) {
        opParams.domainNamespaceLabelSelector(domainNamespaceSelector);
      } else if (domainNamespaceSelectionStrategy.equalsIgnoreCase("RegExp")) {
        opParams.domainNamespaceRegExp(domainNamespaceSelector);
      }
    }

    // domainPresenceFailureRetryMaxCount and domainPresenceFailureRetrySeconds
    if (domainPresenceFailureRetryMaxCount >= 0) {
      opParams.domainPresenceFailureRetryMaxCount(domainPresenceFailureRetryMaxCount);
    }
    if (domainPresenceFailureRetrySeconds > 0) {
      opParams.domainPresenceFailureRetrySeconds(domainPresenceFailureRetrySeconds);
    }

    // If running on OKD cluster, we need to specify the target
    if (OKD) {
      opParams.kubernetesPlatform("OpenShift");
    }

    // install operator
    logger.info("Installing operator in namespace {0}", opNamespace);
    assertTrue(installOperator(opParams),
        String.format("Failed to install operator in namespace %s", opNamespace));
    logger.info("Operator installed in namespace {0}", opNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);
    assertTrue(isHelmReleaseDeployed(OPERATOR_RELEASE_NAME, opNamespace),
        String.format("Operator release %s is not in deployed status in namespace %s",
            OPERATOR_RELEASE_NAME, opNamespace));
    logger.info("Operator release {0} status is deployed in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);

    // wait for the operator to be ready
    logger.info("Wait for the operator pod is ready in namespace {0}", opNamespace);
    testUntil(
        assertDoesNotThrow(() -> operatorIsReady(opNamespace),
          "operatorIsReady failed with ApiException"),
        logger,
        "operator to be running in namespace {0}",
        opNamespace);

    if (withRestAPI) {
      logger.info("Wait for the operator external service in namespace {0}", opNamespace);
      testUntil(
          assertDoesNotThrow(() -> operatorRestServiceRunning(opNamespace),
            "operator external service is not running"),
          logger,
          "operator external service in namespace {0}",
          opNamespace);
      createRouteForOKD("external-weblogic-operator-svc", opNamespace);
      setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);
    }
    return opParams;
  }

  /**
   * Install WebLogic operator and wait up to two minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opReleaseName the operator release name
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   *                        (only in case of List selector)
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opReleaseName, String opNamespace,
                                                        String domainNamespaceSelectionStrategy,
                                                        String domainNamespaceSelector,
                                                        boolean enableClusterRoleBinding,
                                                        String... domainNamespace) {

    HelmParams opHelmParams = new HelmParams().releaseName(opReleaseName)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opReleaseName + "-sa",
        true, 0, opHelmParams, false,
        domainNamespaceSelectionStrategy, domainNamespaceSelector, enableClusterRoleBinding,
        -1, -1, domainNamespace);
  }

  /**
   * Upgrade WebLogic operator to manage the given domain namespaces.
   *
   * @param opNamespace the operator namespace in which the operator will be upgraded
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return true if successful
   */
  public static boolean upgradeAndVerifyOperator(String opNamespace,
                                                 String... domainNamespace) {
    // Helm upgrade parameters
    HelmParams opHelmParams = new HelmParams()
        .releaseName(OPERATOR_RELEASE_NAME)
        .namespace(opNamespace)
        .chartDir(OPERATOR_CHART_DIR);

    // operator chart values
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .domainNamespaces(Arrays.asList(domainNamespace));

    return upgradeAndVerifyOperator(opNamespace, opParams);
  }

  /**
   * Upgrade WebLogic operator with the helm values provided.
   *
   * @param opNamespace the operator namespace in which the operator will be upgraded
   * @param opParams operator parameters to use in the upgrade
   * @return true if successful
   */
  public static boolean upgradeAndVerifyOperator(String opNamespace, OperatorParams opParams) {

    LoggingFacade logger = getLogger();

    // upgrade operator
    logger.info("Upgrading operator in namespace {0}", opNamespace);
    if (!upgradeOperator(opParams)) {
      logger.info("Failed to upgrade operator in namespace {0}", opNamespace);
      return false;
    }
    logger.info("Operator upgraded in namespace {0}", opNamespace);

    // list Helm releases matching operator release name in operator namespace
    logger.info("Checking operator release {0} status in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);
    if (!isHelmReleaseDeployed(OPERATOR_RELEASE_NAME, opNamespace)) {
      logger.info("Operator release {0} is not in deployed status in namespace {1}",
          OPERATOR_RELEASE_NAME, opNamespace);
      return false;
    }
    logger.info("Operator release {0} status is deployed in namespace {1}",
        OPERATOR_RELEASE_NAME, opNamespace);

    return true;
  }

  /**
   * Restart Operator by changing replica to 0 in operator deployment to stop Operator
   * and changing replica back to 1 to start Operator.
   * @param opNamespace namespace where Operator exists
   */
  public static void restartOperator(String opNamespace) {
    LoggingFacade logger = getLogger();
    // get operator pod name
    String operatorPodName = assertDoesNotThrow(
        () -> getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace));
    assertNotNull(operatorPodName, "Operator pod name returned is null");
    logger.info("Operator pod name {0}", operatorPodName);

    // stop operator by changing replica to 0 in operator deployment
    assertTrue(stopOperator(opNamespace), "Couldn't stop the Operator");

    // check operator pod is not running
    checkPodDoesNotExist(operatorPodName, null, opNamespace);

    // start operator by changing replica to 1 in operator deployment
    assertTrue(startOperator(opNamespace), "Couldn't start the Operator");

    // check operator is running
    logger.info("Check Operator pod is running in namespace {0}", opNamespace);
    testUntil(
        operatorIsReady(opNamespace),
        logger,
        "operator to be running in namespace {0}",
        opNamespace);

    logger.info("Operator pod is restarted in namespace {0}", opNamespace);

  }
}

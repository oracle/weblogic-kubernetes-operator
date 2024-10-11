// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.ServiceAccount;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.ARM;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.JAVA_LOGGING_LEVEL_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPod;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.startOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.stopOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorRestServiceRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorWebhookIsReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
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
        0, opHelmParams, false, null,
        null, false, domainNamespace);
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
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withExternalRestAPI, externalRestHttpsPort, opHelmParams, domainNamespace);

  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param elkIntegrationEnabled boolean value indicating elk enabled or not
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        boolean elkIntegrationEnabled,
                                                        String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);

    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withExternalRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withExternalRestAPI, externalRestHttpsPort, opHelmParams, false, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param loggingLevel operator logging level
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        String loggingLevel,
                                                        HelmParams opHelmParams,
                                                        String... domainNamespace) {

    return installAndVerifyOperator(opNamespace,
            opServiceAccount,
            withExternalRestAPI,
            externalRestHttpsPort,
            opHelmParams,
            false,
            null,
            null,
            false,
            loggingLevel,
            domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        boolean elkIntegrationEnabled,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withExternalRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled,
        null, null, false, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
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
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        String domainNamespaceSelectionStrategy,
                                                        boolean elkIntegrationEnabled,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount,
        withExternalRestAPI, externalRestHttpsPort, opHelmParams, elkIntegrationEnabled,
        domainNamespaceSelectionStrategy, null, false, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param elkIntegrationEnabled boolean value indicating elk enabled or not
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        String elasticSearchHost,
                                                        boolean elkIntegrationEnabled,
                                                        boolean createLogStashConfigMap,
                                                        String... domainNamespace) {
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);

    return installAndVerifyOperator(opNamespace, opServiceAccount, withExternalRestAPI,
        externalRestHttpsPort, opHelmParams, elasticSearchHost,
        elkIntegrationEnabled, createLogStashConfigMap,
        null,
        null,
        false,
        "INFO",
        false,
        domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List, value to tell the operator
   *                                  how to select the set of namespaces that it will manage
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        boolean elkIntegrationEnabled,
                                                        String domainNamespaceSelectionStrategy,
                                                        String domainNamespaceSelector,
                                                        boolean enableClusterRoleBinding,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace,
        opServiceAccount,
        withExternalRestAPI,
        externalRestHttpsPort,
        opHelmParams,
        elkIntegrationEnabled,
        domainNamespaceSelectionStrategy,
        domainNamespaceSelector,
        enableClusterRoleBinding,
        "INFO",
        domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List, value to tell the operator
   *                                  how to select the set of namespaces that it will manage
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param loggingLevel logging level of operator
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        boolean elkIntegrationEnabled,
                                                        String domainNamespaceSelectionStrategy,
                                                        String domainNamespaceSelector,
                                                        boolean enableClusterRoleBinding,
                                                        String loggingLevel,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace,
        opServiceAccount,
        withExternalRestAPI,
        externalRestHttpsPort,
        opHelmParams,
        ELASTICSEARCH_HOST,
        elkIntegrationEnabled,
        true,
        domainNamespaceSelectionStrategy,
        domainNamespaceSelector,
        enableClusterRoleBinding,
        loggingLevel,
        null,
        false,
        true,
        domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elasticSearchHost Elasticsearchhost
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param createLogStashConfigMap boolean indicating creating logstash
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List, value to tell the operator
   *                                  how to select the set of namespaces that it will manage
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param loggingLevel logging level of operator
   * @param webhookOnly boolean indicating install webHookOnly operator
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        String elasticSearchHost,
                                                        boolean elkIntegrationEnabled,
                                                        boolean createLogStashConfigMap,
                                                        String domainNamespaceSelectionStrategy,
                                                        String domainNamespaceSelector,
                                                        boolean enableClusterRoleBinding,
                                                        String loggingLevel,
                                                        boolean webhookOnly,
                                                        String... domainNamespace) {
    return installAndVerifyOperator(opNamespace, opServiceAccount, withExternalRestAPI, externalRestHttpsPort,
        opHelmParams, elasticSearchHost, elkIntegrationEnabled, createLogStashConfigMap,
        domainNamespaceSelectionStrategy, domainNamespaceSelector, enableClusterRoleBinding, loggingLevel, null,
        webhookOnly, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elasticSearchHost Elasticsearchhost 
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param createLogStashConfigMap boolean indicating creating logstash
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List, value to tell the operator
   *                                  how to select the set of namespaces that it will manage
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param loggingLevel logging level of operator
   * @param featureGates new feature gates string
   * @param webhookOnly boolean indicating install webHookOnly operator 
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        String elasticSearchHost,
                                                        boolean elkIntegrationEnabled,
                                                        boolean createLogStashConfigMap,
                                                        String domainNamespaceSelectionStrategy,
                                                        String domainNamespaceSelector,
                                                        boolean enableClusterRoleBinding,
                                                        String loggingLevel,
                                                        String featureGates,
                                                        boolean webhookOnly,
                                                        String... domainNamespace) {

    return installAndVerifyOperator(opNamespace, opServiceAccount, withExternalRestAPI, externalRestHttpsPort,
     opHelmParams, elasticSearchHost, elkIntegrationEnabled, createLogStashConfigMap, domainNamespaceSelectionStrategy,
     domainNamespaceSelector, enableClusterRoleBinding, loggingLevel, featureGates, webhookOnly, false,
    domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elasticSearchHost Elasticsearchhost
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param createLogStashConfigMap boolean indicating creating logstash
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List, value to tell the operator
   *                                  how to select the set of namespaces that it will manage
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param loggingLevel logging level of operator
   * @param featureGates new feature gates string
   * @param webhookOnly boolean indicating install webHookOnly operator
   * @param operatorOnly boolean indicating install only Operator, webhook is not installed
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        String elasticSearchHost,
                                                        boolean elkIntegrationEnabled,
                                                        boolean createLogStashConfigMap,
                                                        String domainNamespaceSelectionStrategy,
                                                        String domainNamespaceSelector,
                                                        boolean enableClusterRoleBinding,
                                                        String loggingLevel,
                                                        String featureGates,
                                                        boolean webhookOnly,
                                                        boolean operatorOnly,
                                                        String... domainNamespace) {


    String operatorImage;
    LoggingFacade logger = getLogger();

    // Create a service account for the unique opNamespace
    if (!ServiceAccount.serviceAccountExists(opServiceAccount, opNamespace)) {
      logger.info("Creating service account");
      assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
          .metadata(new V1ObjectMeta()
              .namespace(opNamespace)
              .name(opServiceAccount))));
      logger.info("Created service account: {0}", opServiceAccount);
    }

    operatorImage = getOperatorImageName();
    if (ARM) {
      operatorImage = OPERATOR_RELEASE_IMAGE;
    }

    assertFalse(operatorImage.isEmpty(), "operator image name can not be empty");
    logger.info("operator image name {0}", operatorImage);

    // Create registry secret in the operator namespace to pull the image from repository
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", opNamespace);
    createTestRepoSecret(opNamespace);

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", TEST_IMAGES_REPO_SECRET_NAME);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(Arrays.asList(domainNamespace))
        .javaLoggingLevel(loggingLevel)
        .serviceAccount(opServiceAccount);
    
    if (webhookOnly) {
      opParams.webHookOnly(webhookOnly);
    }
    if (operatorOnly) {
      opParams.operatorOnly(operatorOnly);
    }

    if (domainNamespaceSelectionStrategy != null) {
      opParams.domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy);
    } else if (domainNamespace.length > 0) {
      opParams.domainNamespaceSelectionStrategy("List");
    }

    // use default image in chart when repoUrl is set, otherwise use latest/current branch operator image
    if (opHelmParams.getRepoUrl() == null)  {
      opParams.image(operatorImage);
    }

    // enable ELK Stack
    if (elkIntegrationEnabled) {
      if (!createLogStashConfigMap) {
        opParams.createLogStashConfigMap(createLogStashConfigMap);
      }
      logger.info("Choosen LOGSTASH_IMAGE {0}", LOGSTASH_IMAGE);
      opParams.elkIntegrationEnabled(elkIntegrationEnabled);
      opParams.elasticSearchHost(elasticSearchHost);
      opParams.elasticSearchPort(ELASTICSEARCH_HTTP_PORT);
      opParams.javaLoggingLevel(JAVA_LOGGING_LEVEL_VALUE);
      opParams.logStashImage(LOGSTASH_IMAGE);
    }

    if (withExternalRestAPI) {
      // create externalRestIdentitySecret
      assertTrue(createExternalRestIdentitySecret(opNamespace, DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME),
          "failed to create external REST identity secret");
      opParams
          .restEnabled(true)
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

    // If running on OKD cluster, we need to specify the target
    if (OKD) {
      opParams.kubernetesPlatform("OpenShift");
    }

    if (featureGates != null) {
      opParams.featureGates(featureGates);
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
    if (webhookOnly) {
      logger.info("Wait for the operator webhook pod is ready in namespace {0}", opNamespace);
      testUntil(
          withLongRetryPolicy,
          assertDoesNotThrow(() -> operatorWebhookIsReady(opNamespace),
              "operatorWebhookIsReady failed with ApiException"),
          logger,
          "operator webhook to be running in namespace {0}",
          opNamespace);
    } else {
      logger.info("Wait for the operator pod is ready in namespace {0}", opNamespace);
      testUntil(
          withLongRetryPolicy,
          assertDoesNotThrow(() -> operatorIsReady(opNamespace),
              "operatorIsReady failed with ApiException"),
          logger,
          "operator to be running in namespace {0}",
          opNamespace);
    }

    if (withExternalRestAPI) {
      logger.info("Wait for the operator external service in namespace {0}", opNamespace);
      testUntil(
          withLongRetryPolicy,
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
        domainNamespaceSelectionStrategy, domainNamespaceSelector, enableClusterRoleBinding, domainNamespace);
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
   * @param withExternalRestAPI whether to use REST API
   * @param externalRestHttpsPort the node port allocated for the external operator REST HTTPS interface
   * @param opHelmParams the Helm parameters to install operator
   * @param elkIntegrationEnabled true to enable ELK Stack, false otherwise
   * @param domainNamespaceSelectionStrategy SelectLabel, RegExp or List, value to tell the operator
   *                                  how to select the set of namespaces that it will manage
   * @param domainNamespaceSelector the label or expression value to manage namespaces
   * @param enableClusterRoleBinding operator cluster role binding
   * @param loggingLevel logging level of operator
   * @param domainPresenceFailureRetryMaxCount the number of introspector job retries for a Domain
   * @param domainPresenceFailureRetrySeconds the interval in seconds between these retries
   * @param openshiftIstioInjection openshift istio enabled
   * @param domainNamespace the list of the domain namespaces which will be managed by the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(String opNamespace,
                                                        String opServiceAccount,
                                                        boolean withExternalRestAPI,
                                                        int externalRestHttpsPort,
                                                        HelmParams opHelmParams,
                                                        boolean elkIntegrationEnabled,
                                                        String domainNamespaceSelectionStrategy,
                                                        String domainNamespaceSelector,
                                                        boolean enableClusterRoleBinding,
                                                        String loggingLevel,
                                                        int domainPresenceFailureRetryMaxCount,
                                                        int domainPresenceFailureRetrySeconds,
                                                        boolean openshiftIstioInjection,
                                                        String... domainNamespace) {
    LoggingFacade logger = getLogger();

    // Create a service account for the unique opNamespace
    if (!ServiceAccount.serviceAccountExists(opServiceAccount, opNamespace)) {
      logger.info("Creating service account");
      assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
          .metadata(new V1ObjectMeta()
              .namespace(opNamespace)
              .name(opServiceAccount))));
      logger.info("Created service account: {0}", opServiceAccount);
    }

    // get operator image name
    String operatorImage = getOperatorImageName();
    assertFalse(operatorImage.isEmpty(), "operator image name can not be empty");
    logger.info("operator image name {0}", operatorImage);

    // Create Docker registry secret in the operator namespace to pull the image from repository
    // this secret is used only for non-kind cluster
    logger.info("Creating image repo secret in namespace {0}", opNamespace);
    createTestRepoSecret(opNamespace);

    // map with secret
    Map<String, Object> secretNameMap = new HashMap<>();
    secretNameMap.put("name", TEST_IMAGES_REPO_SECRET_NAME);

    // operator chart values to override
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .imagePullSecrets(secretNameMap)
        .domainNamespaces(Arrays.asList(domainNamespace))
        .javaLoggingLevel(loggingLevel)
        .serviceAccount(opServiceAccount);

    if (domainNamespaceSelectionStrategy != null) {
      opParams.domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy);
    } else if (domainNamespace.length > 0) {
      opParams.domainNamespaceSelectionStrategy("List");
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

    if (withExternalRestAPI) {
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
    
    if (openshiftIstioInjection) {
      opParams.openShiftIstioInjection(openshiftIstioInjection);
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
    String labelSelector = String.format("weblogic.operatorName in (%s)", opNamespace);
    assertDoesNotThrow(() -> {
      V1Pod pod = getPod(opNamespace, labelSelector, "weblogic-operator-");
      logger.info(getPodLog(pod.getMetadata().getName(), opNamespace));
    });
    String cmdToExecute = String.format(
        KUBERNETES_CLI
            + " describe pods " + "  -n " + opNamespace);
    Command
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
    cmdToExecute = String.format(
        KUBERNETES_CLI
            + " get events  " +   "  -n " + opNamespace);
    Command
        .withParams(new CommandParams()
            .command(cmdToExecute))
        .execute();
    // wait for the operator to be ready
    logger.info("Wait for the operator pod is ready in namespace {0}", opNamespace);
    CommonTestUtils.withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for operator to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                opNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> operatorIsReady(opNamespace),
            "operatorIsReady failed with ApiException"));

    if (withExternalRestAPI) {
      logger.info("Wait for the operator external service in namespace {0}", opNamespace);
      CommonTestUtils.withStandardRetryPolicy
          .conditionEvaluationListener(
              condition -> logger.info("Waiting for operator external service in namespace {0} "
                      + "(elapsed time {1}ms, remaining time {2}ms)",
                  opNamespace,
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS()))
          .until(assertDoesNotThrow(() -> operatorRestServiceRunning(opNamespace),
              "operator external service is not running"));
    }
    return opParams;
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

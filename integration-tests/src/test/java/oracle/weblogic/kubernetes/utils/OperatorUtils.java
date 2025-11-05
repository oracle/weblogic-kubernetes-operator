// Copyright (c) 2021, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorWebhookIsReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OperatorUtils {

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param config the configuration for installing the operator
   * @return the operator Helm installation parameters
   */
  public static OperatorParams installAndVerifyOperator(OperatorInstallConfig config) {
    String opNamespace = config.getOpNamespace();
    String opServiceAccount = config.getOpServiceAccount();
    HelmParams opHelmParams = config.getOpHelmParams();
    String elasticSearchHost = config.getElasticSearchHost();
    if (config.isElkIntegrationEnabled() && elasticSearchHost == null) {
      elasticSearchHost = ELASTICSEARCH_HOST;
    }
    boolean elkIntegrationEnabled = config.isElkIntegrationEnabled();
    boolean createLogStashConfigMap = config.isCreateLogStashConfigMap();
    String domainNamespaceSelectionStrategy = config.getDomainNamespaceSelectionStrategy();
    String domainNamespaceSelector = config.getDomainNamespaceSelector();
    boolean enableClusterRoleBinding = config.isEnableClusterRoleBinding();
    String loggingLevel = config.getLoggingLevel();
    String featureGates = config.getFeatureGates();
    boolean webhookOnly = config.isWebhookOnly();
    List<String> domainNamespaces = config.getDomainNamespaces();
    int domainPresenceFailureRetryMaxCount = config.getDomainPresenceFailureRetryMaxCount();
    int domainPresenceFailureRetrySeconds = config.getDomainPresenceFailureRetrySeconds();
    boolean openshiftIstioInjection = config.isOpenshiftIstioInjection();
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
        .domainNamespaces(domainNamespaces)
        .javaLoggingLevel(loggingLevel)
        .serviceAccount(opServiceAccount);
    if (System.getProperty("OPERATOR_LOG_LEVEL") != null && !System.getProperty("OPERATOR_LOG_LEVEL").isBlank()) {
      opParams.javaLoggingLevel(System.getProperty("OPERATOR_LOG_LEVEL").trim());
    }
    
    if (webhookOnly) {
      opParams.webHookOnly(webhookOnly);
    }

    if (domainNamespaceSelectionStrategy != null) {
      opParams.domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy);
    } else if (domainNamespaces.size() > 0) {
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
      logger.info("Chosen LOGSTASH_IMAGE {0}", LOGSTASH_IMAGE);
      opParams.elkIntegrationEnabled(elkIntegrationEnabled);
      opParams.elasticSearchHost(elasticSearchHost);
      opParams.elasticSearchPort(ELASTICSEARCH_HTTP_PORT);
      opParams.javaLoggingLevel(JAVA_LOGGING_LEVEL_VALUE);
      opParams.logStashImage(LOGSTASH_IMAGE);
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

    if (openshiftIstioInjection) {
      opParams.openShiftIstioInjection(openshiftIstioInjection);
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

    // Additional logging if retry params are set (from the second overload)
    if (domainPresenceFailureRetryMaxCount >= 0 || domainPresenceFailureRetrySeconds > 0 || openshiftIstioInjection) {
      String labelSelector = String.format("weblogic.operatorName in (%s)", opNamespace);
      assertDoesNotThrow(() -> {
        V1Pod pod = getPod(opNamespace, labelSelector, "weblogic-operator-");
        logger.info(getPodLog(pod.getMetadata().getName(), opNamespace));
      });
      String cmdToExecute = String.format("%s describe pods -n %s", KUBERNETES_CLI, opNamespace);
      Command
          .withParams(new CommandParams()
              .command(cmdToExecute))
          .execute();
      cmdToExecute = String.format("%s get events -n %s", KUBERNETES_CLI, opNamespace);
      Command
          .withParams(new CommandParams()
              .command(cmdToExecute))
          .execute();
    }

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
      // Use standard retry if retry params set, else long
      if (domainPresenceFailureRetryMaxCount >= 0) {
        CommonTestUtils.withStandardRetryPolicy
            .conditionEvaluationListener(
                condition -> logger.info("Waiting for operator to be running in namespace {0} "
                        + "(elapsed time {1}ms, remaining time {2}ms)",
                    opNamespace,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
            .until(assertDoesNotThrow(() -> operatorIsReady(opNamespace),
                "operatorIsReady failed with ApiException"));
      } else {
        testUntil(
            withLongRetryPolicy,
            assertDoesNotThrow(() -> operatorIsReady(opNamespace),
                "operatorIsReady failed with ApiException"),
            logger,
            "operator to be running in namespace {0}",
            opNamespace);
      }
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
    return installAndVerifyOperator(OperatorInstallConfig.builder()
        .opNamespace(opNamespace)
        .opServiceAccount(opReleaseName + "-sa")
        .opHelmParams(opHelmParams)
        .domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy)
        .domainNamespaceSelector(domainNamespaceSelector)
        .enableClusterRoleBinding(enableClusterRoleBinding)
        .domainNamespaces(domainNamespace)
        .build());
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   *
   * @param opNamespace the operator namespace in which the operator will be installed
   * @param opServiceAccount the service account name for operator
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
    return installAndVerifyOperator(OperatorInstallConfig.builder()
        .opNamespace(opNamespace)
        .opServiceAccount(opServiceAccount)
        .opHelmParams(opHelmParams)
        .elkIntegrationEnabled(elkIntegrationEnabled)
        .domainNamespaceSelectionStrategy(domainNamespaceSelectionStrategy)
        .domainNamespaceSelector(domainNamespaceSelector)
        .enableClusterRoleBinding(enableClusterRoleBinding)
        .loggingLevel(loggingLevel)
        .domainPresenceFailureRetryMaxCount(domainPresenceFailureRetryMaxCount)
        .domainPresenceFailureRetrySeconds(domainPresenceFailureRetrySeconds)
        .openshiftIstioInjection(openshiftIstioInjection)
        .domainNamespaces(domainNamespace)
        .build());
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

  /**
   * Configuration class for Operator installation with Builder pattern.
   */
  public static class OperatorInstallConfig {
    private final String opNamespace;
    private final String opServiceAccount;
    private final HelmParams opHelmParams;
    private final String elasticSearchHost;
    private final boolean elkIntegrationEnabled;
    private final boolean createLogStashConfigMap;
    private final String domainNamespaceSelectionStrategy;
    private final String domainNamespaceSelector;
    private final boolean enableClusterRoleBinding;
    private final String loggingLevel;
    private final String featureGates;
    private final boolean webhookOnly;
    private final List<String> domainNamespaces;
    private final int domainPresenceFailureRetryMaxCount;
    private final int domainPresenceFailureRetrySeconds;
    private final boolean openshiftIstioInjection;

    private OperatorInstallConfig(Builder builder) {
      this.opNamespace = builder.opNamespace;
      this.opServiceAccount = builder.opServiceAccount;
      this.opHelmParams = builder.opHelmParams;
      this.elasticSearchHost = builder.elasticSearchHost;
      this.elkIntegrationEnabled = builder.elkIntegrationEnabled;
      this.createLogStashConfigMap = builder.createLogStashConfigMap;
      this.domainNamespaceSelectionStrategy = builder.domainNamespaceSelectionStrategy;
      this.domainNamespaceSelector = builder.domainNamespaceSelector;
      this.enableClusterRoleBinding = builder.enableClusterRoleBinding;
      this.loggingLevel = builder.loggingLevel;
      this.featureGates = builder.featureGates;
      this.webhookOnly = builder.webhookOnly;
      this.domainNamespaces = builder.domainNamespaces != null
          ? new ArrayList<>(builder.domainNamespaces) : new ArrayList<>();
      this.domainPresenceFailureRetryMaxCount = builder.domainPresenceFailureRetryMaxCount;
      this.domainPresenceFailureRetrySeconds = builder.domainPresenceFailureRetrySeconds;
      this.openshiftIstioInjection = builder.openshiftIstioInjection;
    }

    // Getters
    public String getOpNamespace() {
      return opNamespace;
    }

    public String getOpServiceAccount() {
      return opServiceAccount;
    }

    public HelmParams getOpHelmParams() {
      return opHelmParams;
    }

    public String getElasticSearchHost() {
      return elasticSearchHost;
    }

    public boolean isElkIntegrationEnabled() {
      return elkIntegrationEnabled;
    }

    public boolean isCreateLogStashConfigMap() {
      return createLogStashConfigMap;
    }

    public String getDomainNamespaceSelectionStrategy() {
      return domainNamespaceSelectionStrategy;
    }

    public String getDomainNamespaceSelector() {
      return domainNamespaceSelector;
    }

    public boolean isEnableClusterRoleBinding() {
      return enableClusterRoleBinding;
    }

    public String getLoggingLevel() {
      return loggingLevel;
    }

    public String getFeatureGates() {
      return featureGates;
    }

    public boolean isWebhookOnly() {
      return webhookOnly;
    }

    public List<String> getDomainNamespaces() {
      return domainNamespaces;
    }

    public int getDomainPresenceFailureRetryMaxCount() {
      return domainPresenceFailureRetryMaxCount;
    }

    public int getDomainPresenceFailureRetrySeconds() {
      return domainPresenceFailureRetrySeconds;
    }

    public boolean isOpenshiftIstioInjection() {
      return openshiftIstioInjection;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private String opNamespace;
      private String opServiceAccount;
      private HelmParams opHelmParams;
      private String elasticSearchHost;
      private boolean elkIntegrationEnabled = false;
      private boolean createLogStashConfigMap = true;
      private String domainNamespaceSelectionStrategy;
      private String domainNamespaceSelector;
      private boolean enableClusterRoleBinding = false;
      private String loggingLevel = "INFO";
      private String featureGates;
      private boolean webhookOnly = false;
      private List<String> domainNamespaces = new ArrayList<>();
      private int domainPresenceFailureRetryMaxCount = -1;
      private int domainPresenceFailureRetrySeconds = 0;
      private boolean openshiftIstioInjection = false;

      public Builder opNamespace(String opNamespace) {
        this.opNamespace = opNamespace;
        return this;
      }

      public Builder opServiceAccount(String opServiceAccount) {
        this.opServiceAccount = opServiceAccount;
        return this;
      }

      public Builder opHelmParams(HelmParams opHelmParams) {
        this.opHelmParams = opHelmParams;
        return this;
      }

      public Builder elasticSearchHost(String elasticSearchHost) {
        this.elasticSearchHost = elasticSearchHost;
        return this;
      }

      public Builder elkIntegrationEnabled(boolean elkIntegrationEnabled) {
        this.elkIntegrationEnabled = elkIntegrationEnabled;
        return this;
      }

      public Builder createLogStashConfigMap(boolean createLogStashConfigMap) {
        this.createLogStashConfigMap = createLogStashConfigMap;
        return this;
      }

      public Builder domainNamespaceSelectionStrategy(String domainNamespaceSelectionStrategy) {
        this.domainNamespaceSelectionStrategy = domainNamespaceSelectionStrategy;
        return this;
      }

      public Builder domainNamespaceSelector(String domainNamespaceSelector) {
        this.domainNamespaceSelector = domainNamespaceSelector;
        return this;
      }

      public Builder enableClusterRoleBinding(boolean enableClusterRoleBinding) {
        this.enableClusterRoleBinding = enableClusterRoleBinding;
        return this;
      }

      public Builder loggingLevel(String loggingLevel) {
        this.loggingLevel = loggingLevel;
        return this;
      }

      public Builder featureGates(String featureGates) {
        this.featureGates = featureGates;
        return this;
      }

      public Builder webhookOnly(boolean webhookOnly) {
        this.webhookOnly = webhookOnly;
        return this;
      }

      public Builder domainNamespaces(String... domainNamespaces) {
        this.domainNamespaces = Arrays.asList(domainNamespaces);
        return this;
      }

      public Builder domainPresenceFailureRetryMaxCount(int domainPresenceFailureRetryMaxCount) {
        this.domainPresenceFailureRetryMaxCount = domainPresenceFailureRetryMaxCount;
        return this;
      }

      public Builder domainPresenceFailureRetrySeconds(int domainPresenceFailureRetrySeconds) {
        this.domainPresenceFailureRetrySeconds = domainPresenceFailureRetrySeconds;
        return this;
      }

      public Builder openshiftIstioInjection(boolean openshiftIstioInjection) {
        this.openshiftIstioInjection = openshiftIstioInjection;
        return this;
      }

      /**
       * Builds operator install configuration.
       * @return Operator install configuration
       */
      public OperatorInstallConfig build() {
        if (opNamespace == null) {
          throw new IllegalStateException("Required fields (opNamespace) must be set");
        }
        if (opServiceAccount == null) {
          opServiceAccount = opNamespace + "-sa";
        }
        if (opHelmParams == null) {
          opHelmParams = new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
              .namespace(opNamespace)
              .chartDir(OPERATOR_CHART_DIR);

        }
        return new OperatorInstallConfig(this);
      }
    }
  }
}

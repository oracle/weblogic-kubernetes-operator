// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.Model;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DEFAULT_EXTERNAL_REST_IDENTITY_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.JAVA_LOGGING_LEVEL_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.LARGE_DOMAIN_TESTING_PROPS_FILE;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorRestServiceRunning;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorWebhookIsReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createExternalRestIdentitySecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Create given number of domains, clusters and servers.
 * Use properties file at resources/domain/largedomaintesting.props
 * to configure number of domains, clusters, servers and resource requests
 * for server pod and operator.
 * If testing for large domain(s), make sure the kubernetes cluster has
 * sufficient resources.
 * To run the test: mvn -Dit.test=ItLargeMiiDomainsClusters
 * -pl integration-tests -P integration-tests verify 2>&1  | tee test.out
 */
@DisplayName("Test to create large number of domains, clusters and servers.")
@IntegrationTest
class ItLargeMiiDomainsClusters {
  private static String opNamespace = null;
  private static List<String> domainNamespaces;
  private static final String baseDomainUid = "domain";
  private static final String baseClusterName = "cluster-";
  private static String adminServerPrefix = "-" + ADMIN_SERVER_NAME_BASE;
  private static int numOfDomains;
  private static int numOfClusters;
  private static int numOfServersToStart;
  private static int maxServersInCluster;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  private static Properties largeDomainProps = new Properties();

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(25) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Assign unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // load props file and assign values
    assertDoesNotThrow(
        () -> loadLargeDomainProps(), "Failed to load large domain props file");
    numOfDomains = Integer.valueOf(largeDomainProps.getProperty("NUMBER_OF_DOMAINS", "2"));
    numOfClusters = Integer.valueOf(largeDomainProps.getProperty("NUMBER_OF_CLUSTERS", "2"));
    // if NUMBER_OF_SERVERSTOSTART is not in props, uses MAXIMUM_SERVERS_IN_CLUSTER
    numOfServersToStart = Integer.valueOf(largeDomainProps.getProperty("NUMBER_OF_SERVERSTOSTART",
        largeDomainProps.getProperty("MAXIMUM_SERVERS_IN_CLUSTER", "2")));
    maxServersInCluster = Integer.valueOf(largeDomainProps.getProperty("MAXIMUM_SERVERS_IN_CLUSTER", "2"));

    logger.info("Assign unique namespaces for Domains");
    domainNamespaces = namespaces.subList(1, numOfDomains + 1);

    // install and verify operator
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, ELASTICSEARCH_HOST, false, true, null,
        null, false, "INFO", null, false, domainNamespaces.stream().toArray(String[]::new));

  }

  /**
   * Create given number of domains with clusters and access the console.
   */
  @Test
  @DisplayName("Create n number of domains/clusters")
  void testCreateNDomainsNClusters() {
    logger.info("Creating {0} domains with {1} clusters in each domain",
        numOfDomains, numOfClusters);

    // create given number of domains and clusters
    for (int i = 0; i < numOfDomains; i++) {
      String domainUid = baseDomainUid + (i + 1);
      String configMapName = domainUid + "-configmap";
      List<String> clusterNameList = new ArrayList<>();
      for (int j = 1; j <= numOfClusters; j++) {
        clusterNameList.add(baseClusterName + j);
      }

      // create config map with model for all clusters
      createClusterModelConfigMap(domainUid, configMapName, domainNamespaces.get(i));

      // create secrets
      createSecrets(domainNamespaces.get(i));

      // create cluster resources and domain resource
      DomainResource domain = createDomainResource(domainUid, domainNamespaces.get(i),
          MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
          adminSecretName, new String[]{TEST_IMAGES_REPO_SECRET_NAME},
          encryptionSecretName, numOfServersToStart, clusterNameList);

      // set config map
      domain.getSpec().configuration(new Configuration()
          .model(new Model()
              .configMap(configMapName)
              .runtimeEncryptionSecret(encryptionSecretName)));

      // set resource request and limit
      Map<String, Quantity> resourceRequest = new HashMap<>();
      if (largeDomainProps.containsKey("SERVER_POD_CPU_REQUEST")) {
        resourceRequest.put("cpu",
            new Quantity(largeDomainProps.getProperty("SERVER_POD_CPU_REQUEST")));
      }
      if (largeDomainProps.containsKey("SERVER_POD_MEM_REQUEST")) {
        resourceRequest.put("memory",
            new Quantity(largeDomainProps.getProperty("SERVER_POD_MEM_REQUEST")));
      }

      Map<String, Quantity> resourceLimit = new HashMap<>();
      if (largeDomainProps.containsKey("SERVER_POD_CPU_LIMIT")) {
        resourceLimit.put("cpu",
            new Quantity(largeDomainProps.getProperty("SERVER_POD_CPU_LIMIT")));
      }
      if (largeDomainProps.containsKey("SERVER_POD_MEM_LIMIT")) {
        resourceLimit.put("memory",
            new Quantity(largeDomainProps.getProperty("SERVER_POD_MEM_LIMIT")));
      }

      domain.getSpec().getServerPod().resources(new V1ResourceRequirements()
          .requests(resourceRequest)
          .limits(resourceLimit));

      logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
          domainUid, domainNamespaces.get(i),
          MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
      createDomainAndVerify(domain, domainNamespaces.get(i));

      String adminServerPodName = domainUid + adminServerPrefix;
      // check admin server pod is ready
      logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
          adminServerPodName, domainNamespaces.get(i));
      checkPodReady(adminServerPodName, domainUid, domainNamespaces.get(i));

      // check managed server pods are ready in all clusters in the domain
      for (int j = 1; j <= numOfClusters; j++) {
        String managedServerPrefix = "c" + j + "-managed-server";
        // check managed server pods are ready
        for (int k = 1; k <= numOfServersToStart; k++) {
          logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
              managedServerPrefix + k, domainNamespaces.get(i));
          checkPodReady(managedServerPrefix + k, domainUid, domainNamespaces.get(i));
        }
      }

      // access console
      int nodePort = getServiceNodePort(
          domainNamespaces.get(i), getExternalServicePodName(adminServerPodName), "default");
      assertNotEquals(-1, nodePort,
          "Could not get the default external service node port");
      logger.info("Found the default service nodePort {0}", nodePort);
      String hostAndPort = getHostAndPort(null, nodePort);

      if (!WEBLOGIC_SLIM) {
        // String curlCmd = "curl -s --show-error --noproxy '*' "
        String curlCmd = "curl -s --show-error "
            + " http://" + hostAndPort
            + "/console/login/LoginForm.jsp --write-out %{http_code} -o /dev/null";
        logger.info("Executing default nodeport curl command {0}", curlCmd);
        assertTrue(callWebAppAndWaitTillReady(curlCmd, 5));
        logger.info("WebLogic console is accessible thru default service");
      }
    }
  }

  private static void createClusterModelConfigMap(
      String domainid, String cfgMapName, String domainNamespace) {
    String yamlString = "topology:\n"
        + "  Cluster:\n";
    String clusterYamlString = "";
    String serverTemplateYamlString = "";
    for (int i = 1; i <= numOfClusters; i++) {
      clusterYamlString = clusterYamlString
          + "    'cluster-" + i + "':\n"
          + "       DynamicServers: \n"
          + "         ServerTemplate: 'cluster-" + i + "-template' \n"
          + "         ServerNamePrefix: 'c" + i + "-managed-server' \n"
          + "         DynamicClusterSize: " + maxServersInCluster + " \n"
          + "         MaxDynamicClusterSize: " + maxServersInCluster + " \n"
          + "         CalculatedListenPorts: false \n";
      serverTemplateYamlString = serverTemplateYamlString
          + "    'cluster-" + i + "-template':\n"
          + "       Cluster: 'cluster-" + i + "' \n"
          + "       ListenPort : 8001 \n";
    }
    yamlString = yamlString + clusterYamlString
        + "  ServerTemplate:\n"
        + serverTemplateYamlString;
    logger.info("Yamlstring " + yamlString);
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainid);
    Map<String, String> data = new HashMap<>();
    data.put("model.cluster.yaml", yamlString);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .labels(labels)
            .name(cfgMapName)
            .namespace(domainNamespace));
    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", cfgMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed %s", cfgMapName));
  }

  private void createSecrets(String domainNamespace) {
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");
  }

  /**
   * Install WebLogic operator and wait up to five minutes until the operator pod is ready.
   * Set resource requests, limits and jvm options.
   */
  private static OperatorParams installAndVerifyOperator(String opNamespace,
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
    String operatorImage;
    LoggingFacade logger = getLogger();

    // Create a service account for the unique opNamespace
    logger.info("Creating service account");
    assertDoesNotThrow(() -> createServiceAccount(new V1ServiceAccount()
        .metadata(new V1ObjectMeta()
            .namespace(opNamespace)
            .name(opServiceAccount))));
    logger.info("Created service account: {0}", opServiceAccount);

    operatorImage = getOperatorImageName();

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
    // default cpuRequests is 250m and memoryRequests is 512Mi
    OperatorParams opParams = new OperatorParams()
        .helmParams(opHelmParams)
        .imagePullSecrets(secretNameMap)
        .imagePullPolicy("Always")
        .domainNamespaces(Arrays.asList(domainNamespace))
        .javaLoggingLevel(loggingLevel)
        .serviceAccount(opServiceAccount);
    if (largeDomainProps.containsKey("OPERATOR_CPU_REQUEST")) {
      opParams.cpuRequests(largeDomainProps.getProperty("OPERATOR_CPU_REQUEST"));
    }
    if (largeDomainProps.containsKey("OPERATOR_MEM_REQUEST")) {
      opParams.memoryRequests(largeDomainProps.getProperty("OPERATOR_MEM_REQUEST"));
    }
    if (largeDomainProps.containsKey("OPERATOR_CPU_LIMIT")) {
      opParams.cpuLimits(largeDomainProps.getProperty("OPERATOR_CPU_LIMIT"));
    }
    if (largeDomainProps.containsKey("OPERATOR_MEM_LIMIT")) {
      opParams.memoryLimits(largeDomainProps.getProperty("OPERATOR_MEM_LIMIT"));
    }
    // below jvmOptions are to record JFR dump, the file will be inside operator pod
    if (largeDomainProps.containsKey("OPERATOR_JVM_OPTIONS")) {
      logger.info("Operator JVM Options " + largeDomainProps.getProperty("OPERATOR_JVM_OPTIONS"));
      opParams.jvmOptions(largeDomainProps.getProperty("OPERATOR_JVM_OPTIONS"));
    }

    if (webhookOnly) {
      opParams.webHookOnly(webhookOnly);
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

  private static void loadLargeDomainProps() throws IOException {
    largeDomainProps.load(new FileInputStream(RESOURCE_DIR
        + "/domain/" + LARGE_DOMAIN_TESTING_PROPS_FILE));
  }
}

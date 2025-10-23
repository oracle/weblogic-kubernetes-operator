// Copyright (c) 2023, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.lang.System.currentTimeMillis;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_PREFIX_LENGTH;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_STATUS_CONDITION_ROLLING_TYPE;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HTTP_PORT;
import static oracle.weblogic.kubernetes.TestConstants.JAVA_LOGGING_LEVEL_VALUE;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.LARGE_DOMAIN_TESTING_PROPS_FILE;
import static oracle.weblogic.kubernetes.TestConstants.LOGSTASH_IMAGE;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorImageName;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainResourceWithNewRestartVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isHelmReleaseDeployed;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorWebhookIsReady;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createPushAuxiliaryImageWithDomainConfig;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createRetryPolicy;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getKindRepoImageForSpec;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNonEmptySystemProperty;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.verifyDomainStatusConditionTypeDoesNotExist;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_COMPLETED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.DOMAIN_ROLL_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.POD_CYCLE_STARTING;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEvent;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResource;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Create given number of domains, clusters and servers.
 * Use properties file provided by system property largedomainpropsfile or
 * default to resources/domain/largedomaintesting.props
 * to configure number of domains, clusters, servers and resource requests
 * for server pod and operator.
 * If testing for large domain(s), make sure the kubernetes cluster has
 * sufficient resources.
 * To run the test: mvn -Dit.test=ItLargeMiiDomainsClusters
 * -pl integration-tests -P integration-tests verify 2>&1  | tee test.out
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
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
  private static int maxClusterUnavailable;
  private static String baseImageName;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  private static Properties largeDomainProps = new Properties();
  private static final String miiAuxiliaryImageTag = "image" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImageTag;

  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  static void initAll(@Namespaces(25) List<String> namespaces) {
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
    maxClusterUnavailable = Integer.valueOf(largeDomainProps.getProperty("MAX_CLUSTER_UNAVAILABLE", "1"));
    baseImageName = BASE_IMAGES_PREFIX + largeDomainProps.getProperty("BASE_IMAGE_NAME", WEBLOGIC_IMAGE_NAME_DEFAULT);

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
  @Order(1)
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
      createClusterModelConfigMap(
          domainUid, configMapName, domainNamespaces.get(i), maxServersInCluster);

      // create secrets
      createSecrets(domainNamespaces.get(i));

      // build app
      AppParams appParams = defaultAppParams()
          .appArchiveDir(ARCHIVE_DIR + this.getClass().getSimpleName());
      assertTrue(buildAppArchive(appParams
              .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
              .appName(MII_BASIC_APP_NAME)),
          String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

      // image with model files for domain config, app and wdt install files
      List<String> archiveList
          = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

      List<String> modelList = new ArrayList<>();
      modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
      createPushAuxiliaryImageWithDomainConfig(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImageTag, archiveList, modelList);

      final String auxiliaryImagePath = "/auxiliary";

      // create domain custom resource using auxiliary images
      logger.info("Creating domain custom resource with domainUid {0} and auxiliary image {1}",
          domainUid, miiAuxiliaryImage);
      String weblogicImageToUseInSpec = getKindRepoImageForSpec(KIND_REPO, baseImageName,
          largeDomainProps.containsKey("BASE_IMAGE_TAG") ? (String)largeDomainProps.get("BASE_IMAGE_TAG") :
          WEBLOGIC_IMAGE_TAG, BASE_IMAGES_REPO_PREFIX_LENGTH);
      logger.info("weblogicImageToUseInSpec " + weblogicImageToUseInSpec);
      DomainResource domain = createDomainResourceWithAuxiliaryImage(domainUid, domainNamespaces.get(i),
          weblogicImageToUseInSpec, adminSecretName, createSecretsForImageRepos(domainNamespaces.get(i)),
          encryptionSecretName, auxiliaryImagePath,
          miiAuxiliaryImage);

      // set config map
      domain.getSpec().getConfiguration().getModel().configMap(configMapName);
      domain.getSpec().getConfiguration().getModel().runtimeEncryptionSecret(encryptionSecretName);

      for (int j = 1; j <= numOfClusters; j++) {
        String clusterName = baseClusterName + j;
        domain = createClusterResourceAndAddReferenceToDomain(
            domainUid + "-" + clusterName, clusterName, domainNamespaces.get(i),
            domain, numOfServersToStart);

      }

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
      domain.getSpec().maxClusterUnavailable(maxClusterUnavailable);

      logger.info("Creating Domain Resource {0} in namespace {1} using image {2}",
          domainUid, domainNamespaces.get(i),
          MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
      createDomainAndVerify(domain, domainNamespaces.get(i));

      String adminServerPodName = domainUid + adminServerPrefix;
      // check admin server pod is ready
      logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
          adminServerPodName, domainNamespaces.get(i));
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespaces.get(i));

      // check managed server pods are ready in all clusters in the domain
      for (int j = 1; j <= numOfClusters; j++) {
        String managedServerPrefix = "c" + j + "-managed-server";
        // check managed server pods are ready
        for (int k = 1; k <= numOfServersToStart; k++) {
          logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
              managedServerPrefix + k, domainNamespaces.get(i));
          checkPodReadyAndServiceExists(domainUid + "-" + managedServerPrefix + k, domainUid, domainNamespaces.get(i));
        }
      }

      // access console
      int nodePort = getServiceNodePort(
          domainNamespaces.get(i), getExternalServicePodName(adminServerPodName), "default");
      assertNotEquals(-1, nodePort,
          "Could not get the default external service node port");
      logger.info("Found the default service nodePort {0}", nodePort);
      String hostAndPort = getHostAndPort(null, nodePort);

      // make sure K8S_NODEPORT_HOST is exported to make the below call work
      String curlCmd = "curl -s --show-error "
          + " http://" + hostAndPort
          + "/weblogic/ready --write-out %{http_code} -o /dev/null";
      logger.info("Executing default nodeport curl command {0}", curlCmd);
      assertTrue(callWebAppAndWaitTillReady(curlCmd, 5));
      logger.info("ready app is accessible thru default service");

    }
  }

  /**
   * Patch the domain with the different base image name.
   * Verify all the pods are restarted and back to ready state.
   */
  @Test
  @Order(2)
  @DisplayName("Test to update Base Weblogic Image Name")
  void testBaseImageMinorUpdateRollingRestart() {
    String domainUid1 = baseDomainUid + "1";
    // get the original domain resource before update
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid1, domainNamespaces.get(0)),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            baseDomainUid + "1", domainNamespaces.get(0)));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");

    Map<String, OffsetDateTime> podsWithTimeStamps = new LinkedHashMap<>();
    for (int j = 1; j <= numOfClusters; j++) {
      String managedServerPrefix = "c" + j + "-managed-server";
      // get the map with server pods and their original creation timestamps
      podsWithTimeStamps.putAll(getPodsWithTimeStamps(domainNamespaces.get(0),
          adminServerPrefix, managedServerPrefix, numOfServersToStart));
    }

    //print out the original image name
    String imageName = domain1.getSpec().getImage();
    logger.info("Currently the image name used for the domain is: {0}", imageName);

    //change image name to imageUpdate
    String imageUpdate = getKindRepoImageForSpec(KIND_REPO, baseImageName,
        largeDomainProps.containsKey("UPGRADE_IMAGE_TAG") ? (String)largeDomainProps.get("UPGRADE_IMAGE_TAG") :
            WEBLOGIC_IMAGE_TAG, BASE_IMAGES_REPO_PREFIX_LENGTH);

    StringBuffer patchStr;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/image\",")
        .append("\"value\": \"")
        .append(imageUpdate)
        .append("\"}]");
    logger.info("PatchStr for imageUpdate: {0}", patchStr.toString());

    assertTrue(patchDomainResource(domainUid1, domainNamespaces.get(0), patchStr),
        "patchDomainCustomResource(imageUpdate) failed");

    domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid1, domainNamespaces.get(0)),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid1, domainNamespaces.get(0)));
    assertNotNull(domain1, "Got null domain resource after patching");
    assertNotNull(domain1.getSpec(), domain1 + " /spec is null");

    //print out image name in the new patched domain
    logger.info("In the new patched domain image name is: {0}", domain1.getSpec().getImage());
    long timeBeforeRollingRestart = currentTimeMillis();
    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid1, domainNamespaces.get(0));
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps,
            maxClusterUnavailable, domainNamespaces.get(0)),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid1, domainNamespaces.get(0)));
    logger.info("Time took for rolling restart of domain {0}",
        (currentTimeMillis() - timeBeforeRollingRestart) / 1000 + " seconds");
    String adminServerPodName = domainUid1 + adminServerPrefix;
    checkPodReadyAndServiceExists(adminServerPodName, domainUid1, domainNamespaces.get(0));

  }

  /**
   * Test server pods are rolling restarted and updated when domain is patched
   * with new restartVersion when non dynamic changes are made.
   * Modify the dynamic cluster max size by patching the domain CRD.
   * Update domainRestartVersion to trigger a rolling restart of server pods.
   * Make sure the domain is rolled successfully by checking the events.
   * Make sure the cluster can be scaled beyond the initial maximum size.
   * Keep NUMBER_OF_SERVERSTOSTART less than MAXIMUM_SERVERS_IN_CLUSTER in the
   * properties for this test to test cluster scaling before and after changing the
   * max cluster size. Increase MAX_CLUSTER_UNAVAILABLE for large domains to reduce
   * domain restart/roll time.
   */
  @Test
  @Order(3)
  @DisplayName("Test modification to Dynamic cluster size parameters")
  void testNonDynamicUpdateRollingRestart() {
    String domainUid1 = baseDomainUid + "1";
    String clusterName = domainUid1 + "-" + "cluster-1";
    String managedServerPrefix = "c1-managed-server";
    int newMaxServersInCluster = maxServersInCluster + 5;

    // Scale the cluster to max size
    logger.info("[Before Patching] updating the replica count to {0}",
        maxServersInCluster);
    boolean p1Success = scaleCluster(clusterName,
        domainNamespaces.get(0), maxServersInCluster);
    assertTrue(p1Success,
        String.format("replica patching to %s failed for domain %s in namespace %s",
            maxServersInCluster, domainUid1, domainNamespaces.get(0)));


    // check newly added managed server pods are ready
    for (int k = numOfServersToStart; k <= maxServersInCluster; k++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + k, domainNamespaces.get(0));
      checkPodReadyAndServiceExists(domainUid1 + "-"
          + managedServerPrefix + k, domainUid1, domainNamespaces.get(0));
    }

    long timeBeforeRollingRestart = currentTimeMillis();
    OffsetDateTime timestamp = now();
    // create config map with model for dynamic cluster size
    String configMapName = "dynamic-cluster-size-cm";
    createClusterModelConfigMap(
        domainUid1, configMapName, domainNamespaces.get(0),
        newMaxServersInCluster);

    StringBuffer patchStr = null;
    patchStr = new StringBuffer("[{");
    patchStr.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/configuration/model/configMap\",")
        .append(" \"value\":  \"" + configMapName + "\"")
        .append(" }]");
    logger.log(Level.INFO, "Configmap patch string: {0}", patchStr);

    V1Patch patch = new V1Patch(new String(patchStr));
    boolean cmPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainUid1, domainNamespaces.get(0),
                patch, "application/json-patch+json"),
        "patchDomainCustomResource(configMap)  failed ");
    assertTrue(cmPatched, "patchDomainCustomResource(configMap) failed");

    String newRestartVersion = patchDomainResourceWithNewRestartVersion(domainUid1, domainNamespaces.get(0));
    logger.log(Level.INFO, "New restart version : {0}", newRestartVersion);


    // verify the rolling started and completed events on domain
    // checking every pod has rolled is time consuming on large domain,
    // hence checking events
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid1, domainNamespaces.get(0));
    verifyDomainRollAndPodCycleEvents(timestamp,
        domainNamespaces.get(0), opNamespace, domainUid1);
    logger.info("Time took for rolling restart of domain {0}",
        (currentTimeMillis() - timeBeforeRollingRestart) / 1000
            + " seconds");

    // Scale the cluster to new max size
    logger.info("[After Patching] updating the replica count to {0}",
        newMaxServersInCluster);
    p1Success = scaleCluster(clusterName,
        domainNamespaces.get(0), newMaxServersInCluster);
    assertTrue(p1Success,
        String.format("replica patching to %s failed for domain %s in namespace %s",
            newMaxServersInCluster, domainUid1, domainNamespaces.get(0)));

    // check newly added managed server pods are ready
    for (int k = maxServersInCluster; k <= newMaxServersInCluster; k++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + k, domainNamespaces.get(0));
      checkPodReadyAndServiceExists(domainUid1 + "-" + managedServerPrefix + k, domainUid1,
          domainNamespaces.get(0));
    }
  }

  private static void createClusterModelConfigMap(
      String domainid, String cfgMapName, String domainNamespace, int maxClusterSize) {
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
          + "         DynamicClusterSize: " + maxClusterSize + " \n"
          + "         MaxDynamicClusterSize: " + maxClusterSize + " \n"
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
    Map<String, String> modelData = new HashMap<>();
    modelData.put("model.cluster.yaml", yamlString);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(modelData)
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

    return opParams;
  }

  private static void loadLargeDomainProps() throws IOException {
    String largeDomainPropsFile = getNonEmptySystemProperty("largedomainpropsfile",
        RESOURCE_DIR + "/domain/" + LARGE_DOMAIN_TESTING_PROPS_FILE);
    largeDomainProps.load(new FileInputStream(largeDomainPropsFile));
  }

  private void verifyDomainRollAndPodCycleEvents(OffsetDateTime timestamp, String domainNamespace,
                                                 String opNamespace, String domainUid) {
    logger.info("verify domain roll starting/pod cycle starting events are logged");
    checkEvent(opNamespace, domainNamespace, domainUid, DOMAIN_ROLL_STARTING, "Normal", timestamp);
    checkEvent(opNamespace, domainNamespace, domainUid, POD_CYCLE_STARTING, "Normal", timestamp);
    checkEvent(opNamespace, domainNamespace, domainUid, DOMAIN_ROLL_COMPLETED, "Normal", timestamp);
    // verify that Rolling condition is removed
    testUntil(
        () -> verifyDomainStatusConditionTypeDoesNotExist(
            domainUid, domainNamespace, DOMAIN_STATUS_CONDITION_ROLLING_TYPE),
        logger,
        "Verifying domain {0} in namespace {1} no longer has a Rolling status condition",
        domainUid,
        domainNamespace);
  }

  private static void checkEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String reason, String type, OffsetDateTime timestamp) {
    ConditionFactory withLongRetryPolicy = createRetryPolicy(2, 10, 60 * 60);
    testUntil(withLongRetryPolicy,
        checkDomainEvent(opNamespace, domainNamespace, domainUid, reason, type, timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        reason,
        domainNamespace);
  }

}

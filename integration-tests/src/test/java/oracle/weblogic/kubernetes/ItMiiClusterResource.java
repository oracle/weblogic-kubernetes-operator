// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.dockerPush;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.deleteClusterCustomResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create model in image domain with Cluster Resources
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to a create model in image domain with Cluster Resourcees")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-srg")
class ItMiiClusterResource {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private String domainUid = "domain1";
  private String domainUid2 = "domain2";
  private String miiImage = null;
  private static LoggingFacade logger = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Create a (mii) WebLogic domain with domain level replica set to zero.
   * Do not not associate any Cluster Resource with domain even if two 
   * WebLogic clusters (cluster-1 and cluster-2) are configuted in config.xml
   * Create two kubernates cluster resources CR1 and CR2 
   * corresponding to WebLogic clusters cluster-1 and cluster-2.
   * Start the domain and make sure no managed servers are started from either 
   * WebLogic Cluster. 
   * Patch the domain resource to add cluster resource CR1
   * Make sure only managed servers from cluster-1 comes up
   * Patch the domain resource to replace the resource  CR1 with CR2
   * Make sure managed servers from cluster-1 goes down and managed servers 
   * from cluster-2 comes up.
   * Delete the cluster Resource (CR2)
   * Make sure managed servers from cluster-2 goes down 
   */
  @Test
  @Order(1)
  @DisplayName("Verify dynamic add/remove of cluster resource on domain")
  void testManageClusterResource() {

    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid +  "-c1-managed-server";
    final String managedServerPrefix2 = domainUid + "-c2-managed-server";
    final int replicaCount = 2;

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
            "weblogicenc", "weblogicenc");

    // create cluster object(s)
    String clusterRefName = "cluster-1";
    String clusterRefName2 = "cluster-2";
    String clusterName = "cluster-1";
    String clusterName2 = "cluster-2";

    ClusterResource cluster = createClusterResource(
        clusterRefName, clusterName, domainNamespace, replicaCount);
    logger.info("Creating cluster {0} in namespace {1}",clusterRefName, domainNamespace);
    createClusterAndVerify(cluster);
    ClusterResource cluster2 = createClusterResource(
        clusterRefName2, clusterName2, domainNamespace, replicaCount);
    logger.info("Creating cluster {0} in namespace {1}",clusterRefName2, domainNamespace);
    createClusterAndVerify(cluster2);

    String configMapName = "cluster-configmap";
    String yamlString = "topology:\n"
        + "  Cluster:\n"
        + "    'cluster-1':\n"
        + "       DynamicServers: \n"
        + "         ServerTemplate: 'cluster-1-template' \n"
        + "         ServerNamePrefix: 'c1-managed-server' \n"
        + "         DynamicClusterSize: 3 \n"
        + "         MaxDynamicClusterSize: 3 \n"
        + "         CalculatedListenPorts: false \n"
        + "    'cluster-2':\n"
        + "       DynamicServers: \n"
        + "         ServerTemplate: 'cluster-2-template' \n"
        + "         ServerNamePrefix: 'c2-managed-server' \n"
        + "         DynamicClusterSize: 4 \n"
        + "         MaxDynamicClusterSize: 4 \n"
        + "         CalculatedListenPorts: false \n"
        + "  ServerTemplate:\n"
        + "    'cluster-1-template':\n"
        + "       Cluster: 'cluster-1' \n"
        + "       ListenPort : 8001 \n"
        + "    'cluster-2-template':\n"
        + "       Cluster: 'cluster-2' \n"
        + "       ListenPort : 9001 \n";

    createModelConfigMap(configMapName, yamlString, domainUid);

    // create the domain object
    DomainResource domain = createDomainResource(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);

    // create model in image domain
    logger.info("Creating mii domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, 
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);

    // Do not set cluster references
    // check only admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are not started
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
    }

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));
    logger.info("patch the domain resource with new cluster and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\",\"path\": \"/spec/clusters/-\", \"value\": {\"name\" : \"" + clusterRefName + "\"}"
        + "},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    //verify the introspector pod is created and runs
    String introspectPodNameBase = getIntrospectJobName(domainUid);

    checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    // verify managed server services and pods are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    String introspectVersion2 = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));
    logger.info("patch the domain resource with new cluster and introspectVersion2");
    String patchStr2 = "["
        + "{\"op\": \"replace\",\"path\": \"/spec/clusters/0/name\", \"value\":"
        + " \"" + clusterRefName2 + "\""  
        + "},"
        + "{\"op\": \"replace\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion2 + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr2);
    V1Patch patch2 = new V1Patch(patchStr2);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch2, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");
    //verify the introspector pod is created and runs
    String introspectPodNameBase2 = getIntrospectJobName(domainUid);

    checkPodExists(introspectPodNameBase2, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);

    // check managed server pods from cluster-1 are shutdown
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefix + i, domainUid, domainNamespace);
    }

    // verify managed server pods from cluster-2 are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix2 + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix2 + i, domainUid, domainNamespace);
    }

    kubectlScaleCluster(clusterRefName2,domainNamespace,3);
    checkPodReadyAndServiceExists(managedServerPrefix2 + 3, domainUid, domainNamespace);
    logger.info("Cluster is scaled up to replica count 3");
   
    deleteClusterCustomResourceAndVerify(clusterRefName2,domainNamespace);
    // check managed server pods from cluster-2 are shutdown
    for (int i = 1; i <= replicaCount + 1; i++) {
      checkPodDoesNotExist(managedServerPrefix2 + i,domainUid,domainNamespace);
    }

  }

  private void pushImageIfNeeded(String image) {
    // push the image to a registry to make the test work in multi node cluster
    logger.info("docker login to registry {0}", TEST_IMAGES_REPO);
    assertTrue(dockerLogin(TEST_IMAGES_REPO, TEST_IMAGES_REPO_USERNAME, 
                TEST_IMAGES_REPO_PASSWORD), "docker login failed");
    // push image
    if (!DOMAIN_IMAGES_REPO.isEmpty()) {
      logger.info("docker push image {0} to registry", image);
      assertTrue(dockerPush(image), String.format("docker push failed for image %s", image));
    }
  }

  private String createImageAndVerify(
      String imageName,
      String imageTag,
      List<String> modelList,
      List<String> archiveList
  ) {
    String image = String.format("%s:%s", imageName, imageTag);

    // Set additional environment variables for WIT
    checkDirectory(WIT_BUILD_DIR);
    Map<String, String> env = new HashMap<>();
    env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

    if (WIT_JAVA_HOME != null) {
      env.put("JAVA_HOME", WIT_JAVA_HOME);
    }

    String witTarget = ((OKD) ? "OpenShift" : "Default");

    // build an image using WebLogic Image Tool
    logger.info("Create image {0} using model list {1} and archive list {2}",
        image, modelList, archiveList);
    boolean result = createImage(
        defaultWitParams()
            .modelImageName(imageName)
            .modelImageTag(imageTag)
            .modelFiles(modelList)
            .modelArchiveFiles(archiveList)
            .wdtModelOnly(true)
            .wdtVersion(WDT_VERSION)
            .target(witTarget)
            .target(witTarget)
            .env(env)
            .redirect(true));

    assertTrue(result, String.format("Failed to create image %s using WebLogic Image Tool", image));

    /* Check image exists using docker images | grep image tag.
     * Tag name is unique as it contains date and timestamp.
     * This is a workaround for the issue on Jenkins machine
     * as docker images imagename:imagetag is not working and
     * the test fails even though the image exists.
     */
    assertTrue(doesImageExist(imageTag),
        String.format("Image %s doesn't exist", image));

    return image;
  }

  // Create a domain resource 
  private DomainResource createDomainResource(String domainUid,
          String domNamespace, String adminSecretName,
          String repoSecretName, String encryptionSecretName,
          String miiImage, String configmapName) {

    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("testkey", "testvalue");

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .replicas(0)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminChannelPortForwardingEnabled(false)
                .serverService(new ServerService()
                    .annotations(keyValueMap)
                    .labels(keyValueMap))
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .configMap(configmapName)
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    return domain;
  }

  // create a ConfigMap with a model that add a cluster 
  private static void createModelConfigMap(String configMapName, String model, String domainUid) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    Map<String, String> data = new HashMap<>();
    data.put("model.cluster.yaml", model);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .labels(labels)
            .name(configMapName)
            .namespace(domainNamespace));
    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed %s", configMapName));
  }

  private static void kubectlScaleCluster(String clusterRef, String namespace, int replica) {
    getLogger().info("Scaling cluster resource {0} in namespace {1} using kubectl scale command", 
          clusterRef, namespace);
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl scale  clusters/" + clusterRef 
        + " --replicas=" + replica + " -n " + namespace);
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed scale the cluster");
  }

}

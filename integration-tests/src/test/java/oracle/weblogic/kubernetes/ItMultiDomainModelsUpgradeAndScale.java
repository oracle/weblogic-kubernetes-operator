// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.DomainUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesDomainExist;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createAndVerifyDomainInImageUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.installOldOperator;
import static oracle.weblogic.kubernetes.utils.UpgradeUtils.upgradeOperatorToCurrent;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class creates WebLogic domains with three models. domain-on-pv ( using WDT ) domain-in-image ( using WDT )
 * model-in-image.
 * Start all three domain with a single operator
 * Upgrade the Operator to latest version
 * Make sure all the domain can be re-managed after the Operator upgrade
 * Verify the basic lifecycle operations of the WebLogic server pods by scaling the domain after upgrade to 
 * current version.
 */
@DisplayName("Verify scaling the clusters in the domain with different domain types after operator upgrade")
@IntegrationTest
@Tag("kind-upgrade")
class ItMultiDomainModelsUpgradeAndScale {

  // domain constants
  private static final int NUMBER_OF_CLUSTERS_MIIDOMAIN = 2;
  private static final String CLUSTER_NAME_PREFIX = "cluster-";
  private static final int replicaCount = 1;
  private static final String SAMPLE_APP_CONTEXT_ROOT = "sample-war";
  private static final String WLDF_OPENSESSION_APP = "opensessionapp";
  private static final String wlSecretName = "weblogic-credentials";
  private static final String miiImageName = "mdup-mii-image";
  private static final String wdtModelFileForMiiDomain = "model-multiclusterdomain-sampleapp-wls.yaml";
  private static final String miiDomainUid = "mdup-miidomain";
  private static final String dimDomainUid = "mdup-domaininimage";
  private static final String domainOnPVUid = "mdup-domainonpv";
  private static final String wdtModelFileForDomainInImage = "wdt-singlecluster-multiapps-usingprop-wls.yaml";

  private static String opNamespace = null;  
  private static String miiDomainNamespace = null;
  private static String domainInImageNamespace = null;
  private static String domainOnPVNamespace = null;
  private static String miiImage = null;
  private static String encryptionSecretName = "encryptionsecret";
  
  private static LoggingFacade logger = null;
  private static Map<String, String> domains;

  /**
   * Create MII image.
   */
  @BeforeAll
  public static void initAll() {
    logger = getLogger();
    domains = new HashMap<>();
    // create mii image
    miiImage = createAndPushMiiImage();
  }

  /**
   * For each test:
   * Assigns unique namespaces for operator and domain.
   * @param namespaces injected by JUnit
   */
  @BeforeEach
  public void beforeEach(@Namespaces(4) List<String> namespaces) {    
    // get a unique operator namespace
    logger.info("Get a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // get unique namespaces for three different type of domains
    logger.info("Getting unique namespaces for three different type of domains");
    assertNotNull(namespaces.get(1));
    miiDomainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2));
    domainOnPVNamespace = namespaces.get(2);
    assertNotNull(namespaces.get(3));
    domainInImageNamespace = namespaces.get(3);
  }
  
  /**
   * Create 3 different types of domains with operator 4.0.8 (domain-on-pv, domain-in-image and model-in-image).
   *
   */
  @Test
  @DisplayName("create three different type of domains in Operator 4.0.8 and upgrade")
  void testInstallAndUpgradeOperatorFrom408() {
    // install and verify operator
    installOldOperator("4.0.8", opNamespace,
        miiDomainNamespace, domainOnPVNamespace, domainInImageNamespace);
    String[] domainTypes = {"modelInImage", "domainInImage", "domainOnPV"};
    for (String domainType : domainTypes) {
      DomainResource domain = createOrStartDomainBasedOnDomainType(domainType);
      domains.put(domain.getMetadata().getName(), domain.getMetadata().getNamespace());
    }
    upgradeOperatorToCurrent(opNamespace);
    scaleClustersByPatchingClusterResource();
  }

  /**
   * Create 3 different types of domains with operator 4.1.2 (domain-on-pv, domain-in-image and model-in-image).
   *
   */
  @Test
  @DisplayName("create three different type of domains in Operator 4.1.2 and upgrade")
  void testInstallAndUpgradeOperatorFrom412() {
    // install and verify operator
    installOldOperator("4.1.2", opNamespace,
        miiDomainNamespace, domainOnPVNamespace, domainInImageNamespace);
    String[] domainTypes = {"modelInImage", "domainInImage", "domainOnPV"};
    for (String domainType : domainTypes) {
      DomainResource domain = createOrStartDomainBasedOnDomainType(domainType);
      domains.put(domain.getMetadata().getName(), domain.getMetadata().getNamespace());
    }
    upgradeOperatorToCurrent(opNamespace);
    scaleClustersByPatchingClusterResource();
  }

  /**
   * Scale the cluster by patching domain resource for three different type of domains i.e. domain-on-pv,
   * domain-in-image and model-in-image
   *
   */
  private void scaleClustersByPatchingClusterResource() {
    for (Map.Entry<String, String> entry : domains.entrySet()) {
      String domainUid = entry.getKey();
      String domainNamespace = entry.getValue();
      DomainResource domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace));

      // get the domain properties
      int numClusters = domain.getSpec().getClusters().size();
      for (int i = 1; i <= numClusters; i++) {
        String clusterName = domain.getSpec().getClusters().get(i - 1).getName();
        String managedServerPodNamePrefix = generateMsPodNamePrefix(numClusters, domainUid, clusterName);
        int numberOfServers;
        // scale cluster-1 to 2 server and cluster-2 to 3 servers
        if (i == 1) {
          numberOfServers = 2;
        } else {
          numberOfServers = 3;
        }
        logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
            clusterName, domainUid, domainNamespace, numberOfServers);
        List<String> managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, replicaCount);
        scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
            replicaCount, numberOfServers, null, managedServersBeforeScale);

        // then scale cluster back to 1 servers
        logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
            clusterName, domainUid, domainNamespace, numberOfServers, replicaCount);
        managedServersBeforeScale = listManagedServersBeforeScale(numClusters, clusterName, numberOfServers);
        scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
            numberOfServers, replicaCount, null, managedServersBeforeScale);
      }

      // shutdown domain and verify the domain is shutdown
      shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
    }
  }

  /**
   * Create model in image domain with multiple clusters.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  private static DomainResource createMiiDomainWithMultiClusters(String domainNamespace) {

    // admin/managed server name here should match with WDT model yaml file
    String adminServerPodName = miiDomainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    createTestRepoSecret(domainNamespace);

    String adminSecretName = "weblogic-credentials";
    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Creating encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(miiDomainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(miiDomainUid)
            .domainHome("/u01/" + domainNamespace + "/domains/" + miiDomainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true "
                        + "-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .adminChannelPortForwardingEnabled(true)
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default-secure")
                        .nodePort(getNextFreePort()))
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .introspectorJobActiveDeadlineSeconds(3000L)
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    // create cluster resource in mii domain
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      String clusterResName = CLUSTER_NAME_PREFIX + i;
      domain = createClusterResourceAndAddReferenceToDomain(clusterResName,
          CLUSTER_NAME_PREFIX + i, domainNamespace, domain, replicaCount);
    }
    setPodAntiAffinity(domain);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        miiDomainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, miiDomainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= NUMBER_OF_CLUSTERS_MIIDOMAIN; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName
            = miiDomainUid + "-" + CLUSTER_NAME_PREFIX + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check managed server pod is ready and service exists in the namespace
        logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, miiDomainUid, domainNamespace);
      }
    }

    return domain;
  }

  /**
   * Create a domain in PV using WDT.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  private static DomainResource createDomainOnPvUsingWdt(String domainUid, String domainNamespace) {

    final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    String clusterName = "dopcluster-1";
    DomainResource domain = DomainUtils.createDomainOnPvUsingWdt(domainUid, domainNamespace, wlSecretName,
        clusterName,
        replicaCount, ItMultiDomainModelsUpgradeAndScale.class.getSimpleName());

    // build application sample-app and opensessionapp
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);

    for (String appName : appSrcDirList) {
      AppParams appParams = defaultAppParams()
          .appArchiveDir(ARCHIVE_DIR + ItMultiDomainModelsUpgradeAndScale.class.getSimpleName());
      assertTrue(buildAppArchive(appParams
          .srcDirList(Collections.singletonList(appName))
          .appName(appName)),
          String.format("Failed to create app archive for %s", appName));

      logger.info("Getting port for default channel");
      int defaultChannelPort = assertDoesNotThrow(()
          -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
          "Getting admin server default port failed");
      logger.info("default channel port: {0}", defaultChannelPort);
      assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

      //deploy application
      Path archivePath = get(appParams.appArchiveDir(), "wlsdeploy", "applications", appName + ".ear");
      logger.info("Deploying webapp {0} to domain {1}", archivePath, domainUid);
      deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelPort),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, clusterName + "," + ADMIN_SERVER_NAME_BASE, archivePath,
          domainNamespace);
    }

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int j = 1; j <= replicaCount; j++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;

      // check managed server pod is ready and service exists in the namespace
      logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

    return domain;
  }

  /**
   * Generate a server list which contains all managed servers in the cluster before scale.
   *
   * @param numClusters number of clusters in the domain
   * @param clusterName the name of the WebLogic cluster
   * @param replicasBeforeScale the replicas of WebLogic cluster before scale
   * @return list of managed servers in the cluster before scale
   */
  private static List<String> listManagedServersBeforeScale(int numClusters, String clusterName,
      int replicasBeforeScale) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      if (numClusters <= 1) {
        managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
      } else {
        managedServerNames.add(clusterName + "-" + MANAGED_SERVER_NAME_BASE + i);
      }
    }

    return managedServerNames;
  }

  /**
   * Assert the specified domain and domain spec, metadata and clusters not null.
   *
   * @param domain oracle.weblogic.domain.Domain object
   */
  private static void assertDomainNotNull(DomainResource domain) {
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), domain + " spec is null");
    assertNotNull(domain.getMetadata(), domain + " metadata is null");
    assertNotNull(domain.getSpec().getClusters(), domain.getSpec() + " getClusters() is null");
  }

  /**
   * Generate the managed server pod name prefix.
   *
   * @param numClusters number of clusters in the domain
   * @param domainUid uid of the domain
   * @param clusterName the cluster name of the domain
   * @return prefix of managed server pod name
   */
  private String generateMsPodNamePrefix(int numClusters, String domainUid, String clusterName) {
    String managedServerPodNamePrefix;
    if (numClusters <= 1) {
      managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
    } else {
      managedServerPodNamePrefix = domainUid + "-" + clusterName + "-" + MANAGED_SERVER_NAME_BASE;
    }

    return managedServerPodNamePrefix;
  }

  /**
   * Create mii image and push it to the registry.
   *
   * @return mii image created
   */
  private static String createAndPushMiiImage() {
    // create image with model files
    logger.info("Creating image with model file {0} and verify", wdtModelFileForMiiDomain);
    List<String> appSrcDirList = new ArrayList<>();
    appSrcDirList.add(MII_BASIC_APP_NAME);
    appSrcDirList.add(WLDF_OPENSESSION_APP);
    miiImage
        = createMiiImageAndVerify(miiImageName, Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForMiiDomain),
            appSrcDirList, WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    return miiImage;
  }

  private static DomainResource createOrStartDomainBasedOnDomainType(String domainType) {
    DomainResource domain = null;

    if (domainType.equalsIgnoreCase("modelInImage")) {
      if (!doesDomainExist(miiDomainUid, DOMAIN_VERSION, miiDomainNamespace)) {

        // create mii domain
        domain = createMiiDomainWithMultiClusters(miiDomainNamespace);
      } else {
        domain = assertDoesNotThrow(() -> getDomainCustomResource(miiDomainUid, miiDomainNamespace));
        startDomainAndVerify(miiDomainNamespace, miiDomainUid, replicaCount, domain.getSpec().getClusters().size());
      }
    } else if (domainType.equalsIgnoreCase("domainInImage")) {
      if (!doesDomainExist(dimDomainUid, DOMAIN_VERSION, domainInImageNamespace)) {

        // create domain in image domain
        List<String> appSrcDirList = new ArrayList<>();
        appSrcDirList.add(MII_BASIC_APP_NAME);
        appSrcDirList.add(WLDF_OPENSESSION_APP);
        String clusterName = "dimcluster-1";
        domain = createAndVerifyDomainInImageUsingWdt(dimDomainUid, domainInImageNamespace,
            wdtModelFileForDomainInImage, appSrcDirList, wlSecretName, clusterName, replicaCount);
      } else {
        domain = assertDoesNotThrow(() -> getDomainCustomResource(dimDomainUid, domainInImageNamespace));
        startDomainAndVerify(domainInImageNamespace, dimDomainUid, replicaCount, domain.getSpec().getClusters().size());
      }
    } else if (domainType.equalsIgnoreCase("domainOnPV")) {
      if (!doesDomainExist(domainOnPVUid, DOMAIN_VERSION, domainOnPVNamespace)) {

        // create domain-on-pv domain
        domain = createDomainOnPvUsingWdt(domainOnPVUid, domainOnPVNamespace);
      } else {
        domain = assertDoesNotThrow(() -> getDomainCustomResource(domainOnPVUid, domainOnPVNamespace));
        startDomainAndVerify(domainOnPVNamespace, domainOnPVUid, replicaCount, domain.getSpec().getClusters().size());
      }
    }

    assertDomainNotNull(domain);

    return domain;
  }
  
  /**
   * Start domain and verify all the server pods were started.
   *
   * @param domainNamespace the namespace where the domain exists
   * @param domainUid the uid of the domain to shutdown
   * @param replicaCount replica count of the domain cluster
   * @param numClusters number of clusters in the domain
   */
  private static void startDomainAndVerify(String domainNamespace,
      String domainUid,
      int replicaCount,
      int numClusters) {
    // start domain
    getLogger().info("Starting domain {0} in namespace {1}", domainUid, domainNamespace);
    startDomain(domainUid, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    getLogger().info("Checking that admin service/pod {0} exists in namespace {1}",
        domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainNamespace);
    checkPodReadyAndServiceExists(domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainUid, domainNamespace);

    String managedServerPodName;
    if (numClusters > 1) {
      for (int i = 1; i <= numClusters; i++) {
        for (int j = 1; j <= replicaCount; j++) {
          managedServerPodName = domainUid + "-cluster-" + i + "-" + MANAGED_SERVER_NAME_BASE + j;
          // check that ms service/pod exists in the domain namespace
          getLogger().info("Checking that clustered ms service/pod {0} exists in namespace {1}",
              managedServerPodName, domainNamespace);
          checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
        }
      }
    } else {
      for (int i = 1; i <= replicaCount; i++) {
        managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;

        // check that ms service/pod exists in the domain namespace
        getLogger().info("Checking that clustered ms service/pod {0} exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }
  }

}

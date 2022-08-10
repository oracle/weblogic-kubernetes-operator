// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.write;
import static java.nio.file.Paths.get;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FSS_DIR;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.NFS_SERVER;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_INGRESS_IMAGE_DIGEST;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_BUILD_IMAGES_IF_EXISTS;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TEST_NGINX_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.assertions.impl.PersistentVolume.doesPVExist;
import static oracle.weblogic.kubernetes.assertions.impl.PersistentVolume.pvNotExist;
import static oracle.weblogic.kubernetes.assertions.impl.PersistentVolumeClaim.doesPVCExist;
import static oracle.weblogic.kubernetes.assertions.impl.PersistentVolumeClaim.pvcNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkClusterReplicaCountMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.FileUtils.generateFileFromTemplate;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.copyFile;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test the sample script(s) to create/manage domain(s).
 * (a) using domain-on-pv and domain-in-image model with wlst and wdt option
 * (b) verify the server/cluster/domain lifecycle scripts 
 * (c) verify the setupLodbalancer.sh script to setup Traefik and Nginx
 */

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the domain on pv, domain in image samples using wlst and wdt and domain lifecycle scripts")
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("oke-sequential")
@IntegrationTest
class ItWlsSamples {

  public static final String SERVER_LIFECYCLE = "Server";
  public static final String CLUSTER_LIFECYCLE = "Cluster";
  public static final String DOMAIN = "DOMAIN";
  public static final String STOP_SERVER_SCRIPT = "stopServer.sh";
  public static final String START_SERVER_SCRIPT = "startServer.sh";
  public static final String STOP_CLUSTER_SCRIPT = "stopCluster.sh";
  public static final String START_CLUSTER_SCRIPT = "startCluster.sh";
  public static final String STOP_DOMAIN_SCRIPT = "stopDomain.sh";
  public static final String START_DOMAIN_SCRIPT = "startDomain.sh";

  private static String traefikNamespace = null;
  private static String nginxNamespace = null;
  private static String domainNamespace = null;
  private static final String domain1Name = "domain1";
  private static final String diiImageNameBase = "domain-home-in-image";
  private static final String diiImageTag =
      SKIP_BUILD_IMAGES_IF_EXISTS ? WEBLOGIC_IMAGE_TAG : getDateAndTimeStamp();
  private final int replicaCount = 2;
  private final String clusterName = "cluster-1";
  private final String managedServerNameBase = "managed-server";
  private final String managedServerPodNamePrefix = domain1Name + "-" + managedServerNameBase;

  private final Path samplePath = get(ITTESTS_DIR, "../kubernetes/samples");
  private final Path domainLifecycleSamplePath = get(samplePath + "/scripts/domain-lifecycle");
  private static String UPDATE_MODEL_FILE = "model-samples-update-domain.yaml";
  private static String UPDATE_MODEL_PROPERTIES = "model-samples-update-domain.properties";

  private static final String[] params = {"wlst:domain1", "wdt:domain2"};

  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains and installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    String opNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for Traefik controller");
    assertNotNull(namespaces.get(2), "Namespace is null");
    traefikNamespace = namespaces.get(2);

    logger.info("Assign a unique namespace for Nginx controller");
    assertNotNull(namespaces.get(3), "Namespace is null");
    nginxNamespace = namespaces.get(3);
    createTestRepoSecret(nginxNamespace);

    // create pull secrets for WebLogic image when running in non Kind cluster
    createBaseRepoSecret(domainNamespace);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Test the sample script to create domain.
   * using domain-in-image model with wlst and wdt option
   *
   * @param model domain name and script type to create domain. Acceptable values of format String:wlst|wdt
  */
  @Order(1)
  @ParameterizedTest
  @MethodSource("paramProvider")
  @DisplayName("Test samples using domain in image")
  @Tag("samples-gate")
  void testSampleDomainInImage(String model) {
    String domainName = model.split(":")[1];
    String script = model.split(":")[0];

    String imageName = DOMAIN_IMAGES_REPO + diiImageNameBase + "-" + script + ":" + diiImageTag;
    Path testSamplePath = get(WORK_DIR, "wls-sample-testing", "domainInImage", domainName, script);
    //copy the samples directory to a temporary location
    setupSample(testSamplePath);
    createSecretWithUsernamePassword(domainName + "-weblogic-credentials", domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    Path sampleBase = get(testSamplePath.toString(), "scripts/create-weblogic-domain/domain-home-in-image");

    // update create-domain-inputs.yaml with the values from this test
    updateDomainInputsFile(domainName, sampleBase,null);

    // update domainHomeImageBase with right values in create-domain-inputs.yaml
    assertDoesNotThrow(() -> {
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "domainHomeImageBase: container-registry.oracle.com/middleware/weblogic:" + WEBLOGIC_IMAGE_TAG,
              "domainHomeImageBase: " + WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#image:",
              "image: " + imageName);
    });

    if (script.equals("wlst")) {
      assertDoesNotThrow(() -> {
        replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
            "mode: wdt",
            "mode: wlst");
      });
    }

    // build the command to run create-domain.sh
    String additonalOptions = " -u "
            + ADMIN_USERNAME_DEFAULT
            + " -p "
            + ADMIN_PASSWORD_DEFAULT;
    String[] additonalStr = {additonalOptions, imageName};

    // run create-domain.sh to create domain.yaml file, run kubectl to create the domain and verify
    createDomainAndVerify(domainName, sampleBase, additonalStr);

    //delete the domain resource
    deleteDomainResourceAndVerify(domainName, sampleBase);
  }

  /**
   * Test domain in pv samples using domains created by wlst and wdt.
   * In domain on pv using wdt and wlst usecases, 
   * Also run the update domain script from the samples, to add a cluster 
   * to the domain.
   *
   * @param model domain name and script type to create domain. Acceptable values of format String:wlst|wdt
   */
  @Order(2)
  @ParameterizedTest
  @MethodSource("paramProvider")
  @DisplayName("Test samples using domain in pv")
  @Tag("samples-gate")
  void testSampleDomainInPv(String model) {

    String domainUid = model.split(":")[1];
    String script = model.split(":")[0];
    Path testSamplePath = get(WORK_DIR, "wls-sample-testing", domainNamespace, "domainInPV", domainUid, script);
    //copy the samples directory to a temporary location
    setupSample(testSamplePath);
    String secretName = domainUid + "-weblogic-credentials";
    if (!secretExists(secretName, domainNamespace)) {
      createSecretWithUsernamePassword(
          secretName,
          domainNamespace,
          ADMIN_USERNAME_DEFAULT,
          ADMIN_PASSWORD_DEFAULT);
    }
    //create PV and PVC used by the domain
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    createPvPvc(domainUid, testSamplePath, pvName, pvcName);
    // WebLogic secrets for the domain has been created by previous test
    // No need to create it again

    Path sampleBase = get(testSamplePath.toString(), "scripts/create-weblogic-domain/domain-home-on-pv");

    // update create-domain-inputs.yaml with the values from this test
    updateDomainInputsFile(domainUid, sampleBase, pvcName);

    // change namespace from default to custom, set wlst or wdt, domain name, and t3PublicAddress
    assertDoesNotThrow(() -> {
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "createDomainFilesDir: wlst", "createDomainFilesDir: "
                      +  script);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "image: container-registry.oracle.com/middleware/weblogic:" + WEBLOGIC_IMAGE_TAG,
              "image: " + WEBLOGIC_IMAGE_TO_USE_IN_SPEC);
    });

    // run create-domain.sh to create domain.yaml file, run kubectl to create the domain and verify
    createDomainAndVerify(domainUid, sampleBase);

    // update the domain to add a new cluster
    copyModelFileForUpdateDomain(sampleBase);
    updateDomainAndVerify(domainUid, sampleBase, script);
  }

  /**
   * Test scripts for stopping and starting a managed server.
   */
  @Order(3)
  @Test
  @DisplayName("Test server lifecycle samples scripts")
  @Tag("samples-gate")
  void testServerLifecycleScripts() {

    // Verify that stopServer script execution shuts down server pod and replica count is decremented
    String serverName = managedServerNameBase + "1";
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    checkPodDoesNotExist(managedServerPodNamePrefix + "1", domain1Name, domainNamespace);
    assertDoesNotThrow(() -> {
      checkClusterReplicaCountMatches(clusterName, domain1Name, domainNamespace, 1);
    });

    // Verify that startServer script execution starts server pod and replica count is incremented
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName);
    checkPodExists(managedServerPodNamePrefix + "1", domain1Name, domainNamespace);
    assertDoesNotThrow(() -> {
      checkClusterReplicaCountMatches(clusterName, domain1Name, domainNamespace, 2);
    });
  }

  /**
   * Test scripts for stopping and starting a managed server while keeping replica count constant.
   */
  @Order(4)
  @Test
  @DisplayName("Test server lifecycle samples scripts with constant replica count")
  @Tag("samples-gate")
  void testServerLifecycleScriptsWithConstantReplicaCount() {
    String serverName = managedServerNameBase + "1";
    String keepReplicaCountConstantParameter = "-k";
    // Verify that replica count is not changed when using "-k" parameter and a replacement server is started
    executeLifecycleScript(STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicaCountConstantParameter);
    checkPodDoesNotExist(managedServerPodNamePrefix + "1", domain1Name, domainNamespace);
    checkPodExists(managedServerPodNamePrefix + "3", domain1Name, domainNamespace);
    assertDoesNotThrow(() -> {
      checkClusterReplicaCountMatches(clusterName, domain1Name, domainNamespace, 2);
    });

    // Verify that replica count is not changed when using "-k" parameter and replacement server is shutdown
    executeLifecycleScript(START_SERVER_SCRIPT, SERVER_LIFECYCLE, serverName, keepReplicaCountConstantParameter);
    checkPodExists(managedServerPodNamePrefix + "1", domain1Name, domainNamespace);
    checkPodDoesNotExist(managedServerPodNamePrefix + "3", domain1Name, domainNamespace);
    assertDoesNotThrow(() -> {
      checkClusterReplicaCountMatches(clusterName, domain1Name, domainNamespace, 2);
    });
  }

  /**
   * Test scripts for stopping and starting a cluster.
   */
  @Order(5)
  @Test
  @DisplayName("Test cluster lifecycle scripts")
  @Tag("samples-gate")
  void testClusterLifecycleScripts() {

    // Verify all clustered server pods are shut down after stopCluster script execution
    executeLifecycleScript(STOP_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, clusterName);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domain1Name, domainNamespace);
    }

    // Verify all clustered server pods are started after startCluster script execution
    executeLifecycleScript(START_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, clusterName);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodExists(managedServerPodNamePrefix + i, domain1Name, domainNamespace);
    }
  }

  /**
   * Test scripts for stopping and starting a domain.
   */
  @Order(6)
  @Test
  @DisplayName("Test domain lifecycle scripts")
  @Tag("samples-gate")
  void testDomainLifecycleScripts() {
    // Verify all WebLogic server instance pods are shut down after stopDomain script execution
    executeLifecycleScript(STOP_DOMAIN_SCRIPT, DOMAIN, null);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domain1Name, domainNamespace);
    }
    String adminServerName = "admin-server";
    String adminServerPodName = domain1Name + "-" + adminServerName;
    checkPodDoesNotExist(adminServerPodName, domain1Name, domainNamespace);

    // Verify all WebLogic server instance pods are started after startDomain script execution
    executeLifecycleScript(START_DOMAIN_SCRIPT, DOMAIN, null);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodExists(managedServerPodNamePrefix + i, domain1Name, domainNamespace);
    }
    checkPodExists(adminServerPodName, domain1Name, domainNamespace);
  }

  /**
   * Verify setupLoadBalancer scripts for managing Traefik LoadBalancer.
   */
  @Test
  @DisplayName("Manage Traefik Ingress Controller with setupLoadBalancer")
  @Tag("samples-gate")
  void testTraefikIngressController() {
    Path testSamplePath = get(WORK_DIR, "wls-sample-testing", "traefik");
    setupSample(testSamplePath);
    Path scriptBase = get(testSamplePath.toString(), "charts/util");
    setupLoadBalancer(scriptBase, "traefik", " -c -n " + traefikNamespace);
    setupLoadBalancer(scriptBase, "traefik", " -d -n " + traefikNamespace);
  }

  /**
   * Verify setupLoadBalancer scripts for managing Nginx LoadBalancer.
   * Use the Nginx Controller image on TEST REPOSITOTY instead of k8s.gcr.io
   */
  @Test
  @DisplayName("Manage Nginx Ingress Controller with setupLoadBalancer")
  void testNginxIngressController() {
    Path testSamplePath = get(WORK_DIR, "wls-sample-testing", "nginx");
    setupSample(testSamplePath);
    Map<String, String> templateMap  = new HashMap<>();
    templateMap.put("TEST_IMAGES_REPO", TEST_IMAGES_REPO);
    templateMap.put("TEST_NGINX_IMAGE_NAME ", TEST_NGINX_IMAGE_NAME);
    templateMap.put("NGINX_INGRESS_IMAGE_DIGEST",NGINX_INGRESS_IMAGE_DIGEST);
    templateMap.put("TEST_IMAGES_REPO_SECRET_NAME", TEST_IMAGES_REPO_SECRET_NAME);
    Path srcPropFile = Paths.get(RESOURCE_DIR, "nginx.template.properties");
    Path targetPropFile = assertDoesNotThrow(
        () -> generateFileFromTemplate(srcPropFile.toString(), "nginx.properties", templateMap));
    logger.info("Generated nginx.properties file path is {0}", targetPropFile);
    Path scriptBase = get(testSamplePath.toString(), "charts/util");
    setupLoadBalancer(scriptBase, "nginx", " -c -n " + nginxNamespace 
         + " -p " + targetPropFile.toString());
    setupLoadBalancer(scriptBase, "nginx", " -d -s -n " + nginxNamespace);
  }

  // Function to execute domain lifecyle scripts
  private void executeLifecycleScript(String script, String scriptType, String entityName) {
    executeLifecycleScript(script, scriptType, entityName, "");
  }

  // Function to execute domain lifecyle scripts
  private void executeLifecycleScript(String script, String scriptType, String entityName, String extraParams) {
    CommandParams params;
    boolean result;
    String commonParameters;
    // This method assumes that the domain lifecycle is run on on domain1 only. The update-domain.sh is only
    // for domain-on-pv with wdt use case. So, setting the domain name and namespace using the extraParams args
    if (scriptType.equals("INTROSPECT_DOMAIN")) {
      commonParameters = extraParams;
    } else {
      commonParameters = " -d " + domain1Name + " -n " + domainNamespace;
    }
    params = new CommandParams().defaults();
    if (scriptType.equals(SERVER_LIFECYCLE)) {
      params.command("sh "
              + get(domainLifecycleSamplePath.toString(), "/" + script).toString()
              + commonParameters + " -s " + entityName + " " + extraParams);
    } else if (scriptType.equals(CLUSTER_LIFECYCLE)) {
      params.command("sh "
              + get(domainLifecycleSamplePath.toString(), "/" + script).toString()
              + commonParameters + " -c " + entityName);
    } else {
      params.command("sh "
              + get(domainLifecycleSamplePath.toString(), "/" + script).toString()
              + commonParameters);
    }
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to execute script " + script);
  }

  // generates the stream of objects used by parametrized test.
  private static Stream<String> paramProvider() {
    return Arrays.stream(params);
  }

  // copy samples directory to a temporary location
  private void setupSample(Path testSamplePath) {
    assertDoesNotThrow(() -> {
      // copy ITTESTS_DIR + "../kubernates/samples" to WORK_DIR + "/wls-sample-testing"
      logger.info("Deleting and recreating {0}", testSamplePath);
      createDirectories(testSamplePath);
      deleteDirectory(testSamplePath.toFile());
      createDirectories(testSamplePath);

      logger.info("Copying {0} to {1}", samplePath, testSamplePath);
      copyDirectory(samplePath.toFile(), testSamplePath.toFile());
    });
  }

  private void copyModelFileForUpdateDomain(Path sampleBase) {
    assertDoesNotThrow(() -> {
      copyFile(get(MODEL_DIR, UPDATE_MODEL_FILE).toFile(),
               get(sampleBase.toString(), UPDATE_MODEL_FILE).toFile());
      // create a properties file that is needed with this model file
      List<String> lines = Arrays.asList("clusterName2=cluster-2", "managedServerNameBaseC2=c2-managed-server");
      write(get(sampleBase.toString(), UPDATE_MODEL_PROPERTIES), lines, StandardCharsets.UTF_8,
          StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    });
  }

  // create persistent volume and persistent volume claims used by the samples
  private void createPvPvc(String domainName, Path testSamplePath, String pvName, String pvcName) {

    // delete pvc first if exists
    if (assertDoesNotThrow(() -> doesPVCExist(pvcName, domainNamespace))) {
      deletePersistentVolumeClaim(pvcName, domainNamespace);
    }
    testUntil(
        assertDoesNotThrow(() -> pvcNotExist(pvcName, domainNamespace),
            String.format("pvcNotExists failed for pvc %s in namespace %s", pvcName, domainNamespace)),
          logger, "pvc {0} to be deleted in namespace {1}", pvcName, domainNamespace);

    // delete pv first if exists
    if (assertDoesNotThrow(() -> doesPVExist(pvName, null))) {
      deletePersistentVolume(pvName);
    }
    testUntil(
        assertDoesNotThrow(() -> pvNotExist(pvName, null),
            String.format("pvNotExists failed for pv %s", pvName)), logger, "pv {0} to be deleted", pvName);

    Path pvpvcBase = get(testSamplePath.toString(), "scripts/create-weblogic-domain-pv-pvc");

    if (!OKE_CLUSTER) {
      // create pv and pvc
      assertDoesNotThrow(() -> {
        // when tests are running in local box the PV directories need to exist
        Path pvHostPath;
        pvHostPath = createDirectories(get(PV_ROOT, this.getClass().getSimpleName(), pvName));

        logger.info("Creating PV directory host path {0}", pvHostPath);
        deleteDirectory(pvHostPath.toFile());
        createDirectories(pvHostPath);

        // set the pvHostPath in create-pv-pvc-inputs.yaml
        replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
                "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + pvHostPath);
      });

    } else {
      assertDoesNotThrow(() -> {
        // set the pvHostPath in create-pv-pvc-inputs.yaml
        replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
                "#weblogicDomainStoragePath: /scratch/k8s_dir", "weblogicDomainStoragePath: " + FSS_DIR);
        replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
                "weblogicDomainStorageType: HOST_PATH", "weblogicDomainStorageType: NFS");
        replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
                "#weblogicDomainStorageNFSServer: nfsServer", "weblogicDomainStorageNFSServer: " + NFS_SERVER);
        replaceStringInFile(get(pvpvcBase.toString(), "pvc-template.yaml").toString(),
                "storageClassName: %DOMAIN_UID%%SEPARATOR%%BASE_NAME%-storage-class", "storageClassName: oci-fss");
        replaceStringInFile(get(pvpvcBase.toString(), "pv-template.yaml").toString(),
                "storageClassName: %DOMAIN_UID%%SEPARATOR%%BASE_NAME%-storage-class", "storageClassName: oci-fss");

      });
    }
    assertDoesNotThrow(() -> {
      // set the namespace in create-pv-pvc-inputs.yaml
      replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
              "namespace: default", "namespace: " + domainNamespace);
      // set the pv storage policy to Recycle in create-pv-pvc-inputs.yaml
      replaceStringInFile(get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString(),
              "domainUID:", "domainUID: " + domainName);
      replaceStringInFile(get(pvpvcBase.toString(), "pvc-template.yaml").toString(),
              "%DOMAIN_UID%%SEPARATOR%%BASE_NAME%-pvc", pvcName);
      replaceStringInFile(get(pvpvcBase.toString(), "pv-template.yaml").toString(),
              "%DOMAIN_UID%%SEPARATOR%%BASE_NAME%-pv", pvName);
    });

    // generate the create-pv-pvc-inputs.yaml
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
        + get(pvpvcBase.toString(), "create-pv-pvc.sh").toString()
        + " -i " + get(pvpvcBase.toString(), "create-pv-pvc-inputs.yaml").toString()
        + " -o "
        + get(pvpvcBase.toString()));

    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create create-pv-pvc-inputs.yaml");

    //create pv and pvc
    params = new CommandParams().defaults();
    params.command("kubectl create -f " + get(pvpvcBase.toString(),
        "pv-pvcs/" + domainName + "-weblogic-sample-pv.yaml").toString());
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create pv");

    testUntil(
        assertDoesNotThrow(() -> pvExists(pvName, null),
          String.format("pvExists failed with ApiException for pv %s", pvName)),
        logger,
        "pv {0} to be ready",
        pvName);

    params = new CommandParams().defaults();
    params.command("kubectl create -f " + get(pvpvcBase.toString(),
        "pv-pvcs/" + domainName + "-weblogic-sample-pvc.yaml").toString());
    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create pvc");

    testUntil(
        assertDoesNotThrow(() -> pvcExists(pvcName, domainNamespace),
          String.format("pvcExists failed with ApiException for pvc %s", pvcName)),
        logger,
        "pv {0} to be ready in namespace {1}",
        pvcName,
            domainNamespace);
  }

  private void updateDomainInputsFile(String domainUid, Path sampleBase, String pvcName) {
    // change namespace from default to custom, domain name, and t3PublicAddress
    assertDoesNotThrow(() -> {
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "namespace: default", "namespace: " + domainNamespace);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "domain1", domainUid);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#t3PublicAddress:", "t3PublicAddress: " + K8S_NODEPORT_HOST);
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "#imagePullSecretName:", "imagePullSecretName: " + BASE_IMAGES_REPO_SECRET_NAME);

      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "domainHome: /shared/domains", "domainHome: /shared/"
                      + domainNamespace + "/domains");
      replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
              "logHome: /shared/domains", "logHome: /shared/"
                      + domainNamespace + "/logs");
      if (pvcName != null) {
        replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
                "persistentVolumeClaimName: " + domainUid + "-weblogic-sample-pvc",
                "persistentVolumeClaimName: " + pvcName);
      }

      if (KIND_REPO == null) {
        replaceStringInFile(get(sampleBase.toString(), "create-domain-inputs.yaml").toString(),
            "imagePullPolicy: IfNotPresent", "imagePullPolicy: Always");
      }
      if (OKE_CLUSTER && sampleBase.toString().contains("domain-home-on-pv")) {
        String chownCommand1 = "chown 1000:0 %DOMAIN_ROOT_DIR%"
                + "/. && find %DOMAIN_ROOT_DIR%"
                + "/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0  chown -R 1000:0";

        String chownCommand2 = "chown 1000:1000 %DOMAIN_ROOT_DIR%"
                + "/. && find %DOMAIN_ROOT_DIR%"
                + "/. -maxdepth 1 ! -name '.snapshot' ! -name '.' -print0 | xargs -r -0  chown -R 1000:1000";
        replaceStringInFile(get(sampleBase.toString(), "create-domain-job-template.yaml").toString(),
                "chown -R 1000:0 %DOMAIN_ROOT_DIR%", chownCommand1);
        replaceStringInFile(get(sampleBase.toString(), "update-domain-job-template.yaml").toString(),
                "chown -R 1000:1000 %DOMAIN_ROOT_DIR%", chownCommand2);
      }
    });
  }

  private void createDomainAndVerify(String domainName,
                                     Path sampleBase, String... additonalStr) {
    String additionalOptions = (additonalStr.length == 0) ? "" : additonalStr[0];
    String imageName = (additonalStr.length == 2) ? additonalStr[1] : "";

    // run create-domain.sh to create domain.yaml file
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
            + get(sampleBase.toString(), "create-domain.sh").toString()
            + " -i " + get(sampleBase.toString(), "create-domain-inputs.yaml").toString()
            + " -o "
            + get(sampleBase.toString())
            + additionalOptions);

    logger.info("Run create-domain.sh to create domain.yaml file");
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain.yaml");

    if (sampleBase.toString().contains("domain-home-in-image")) {
      // docker login and push image to docker registry if necessary
      logger.info("Push the image {0} to Docker repo", imageName);
      dockerLoginAndPushImageToRegistry(imageName);

      // create docker registry secret to pull the image from registry
      // this secret is used only for non-kind cluster
      logger.info("Create docker registry secret in namespace {0}", domainNamespace);
      createTestRepoSecret(domainNamespace);
    }

    // wait until domain.yaml file exits
    String domainYamlFileString = get(sampleBase.toString(), "weblogic-domains/"
        + domainName + "/domain.yaml").toString();
    File domainYamlFile = new File(domainYamlFileString);
    testUntil(() -> domainYamlFile.exists(),
        logger,
        "domain yaml file {0} exists",
        domainYamlFileString);

    // run kubectl to create the domain
    logger.info("Run kubectl to create the domain");
    params = new CommandParams().defaults();
    params.command("kubectl apply -f " + domainYamlFileString);

    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainName, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainName,
        domainNamespace);

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainName + "-" + adminServerName;

    final String managedServerNameBase = "managed-server";
    String managedServerPodNamePrefix = domainName + "-" + managedServerNameBase;
    int replicaCount = 2;

    // verify the admin server service and pod is created
    checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(10, MINUTES).await(), adminServerPodName, domainName, domainNamespace);

    // verify managed server services created and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await(), managedServerPodNamePrefix + i, domainName, domainNamespace);
    }
  }

  private void updateDomainAndVerify(String domainName,
                                     Path sampleBase,
                                     String script) {
    //First copy the update model file to wdt dir and rename it wdt-model_dynamic.yaml
    assertDoesNotThrow(() -> {
      copyFile(get(sampleBase.toString(), UPDATE_MODEL_FILE).toFile(),
          get(sampleBase.toString(), "wdt/wdt_model_dynamic.yaml").toFile());
    });
    // run update-domain.sh to create domain.yaml file
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
        + get(sampleBase.toString(), "update-domain.sh").toString()
        + " -i " + get(sampleBase.toString(), "create-domain-inputs.yaml").toString()
        + "," + get(sampleBase.toString(), UPDATE_MODEL_PROPERTIES).toString()
        + " -o "
        + get(sampleBase.toString()));

    logger.info("Run update-domain.sh to create domain.yaml file");
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain.yaml");

    // wait until domain.yaml file exits
    String domainYamlFileString = get(sampleBase.toString(), "weblogic-domains/"
        + domainName + "/domain.yaml").toString();
    File domainYamlFile = new File(domainYamlFileString);
    testUntil(() -> domainYamlFile.exists(),
        logger,
        "domain yaml file {0} exists",
        domainYamlFileString);

    // For the domain created by WLST, we have to apply domain.yaml created by update-domain.sh
    // before initiating introspection of the domain to start the second cluster that was just added
    // otherwise the newly added Cluster 'cluster-2' is not added to the domain1.
    if (script.equals("wlst")) {
      // run kubectl to update the domain
      logger.info("Run kubectl to create the domain");
      params = new CommandParams().defaults();
      params.command("kubectl apply -f " + domainYamlFileString);
      result = Command.withParams(params).execute();
      assertTrue(result, "Failed to create domain custom resource");
    }

    // Have to initiate introspection of the domain to start the second cluster that was just added
    // Call introspectDomain.sh
    String extraParams = " -d " + domainName + " -n " + domainNamespace;
    executeLifecycleScript("introspectDomain.sh", "INTROSPECT_DOMAIN", "", extraParams);

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainName + "-" + adminServerName;

    final String managedServerNameBase = "c2-managed-server";
    String managedServerPodNamePrefix = domainName + "-" + managedServerNameBase;
    int replicaCount = 2;

    // verify the admin server service and pod is created
    checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .atMost(10, MINUTES).await(), adminServerPodName, domainName, domainNamespace);

    // verify managed server services created and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(10, MINUTES).await(), managedServerPodNamePrefix + i, domainName, domainNamespace);
    }
  }

  private void deleteDomainResourceAndVerify(String domainName, Path sampleBase) {
    //delete the domain resource
    CommandParams params = new CommandParams().defaults();
    params.command("kubectl delete -f "
            + get(sampleBase.toString(), "weblogic-domains/"
            + domainName + "/domain.yaml").toString());
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to delete domain custom resource");

    testUntil(
        domainDoesNotExist(domainName, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be deleted in namespace {1}",
        domainName,
        domainNamespace);
  }

  private void setupLoadBalancer(Path sampleBase, String ingressType, String additionalOptions) {
    // run setupLoadBalancer.sh to install/uninstall ingress controller
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
           + get(sampleBase.toString(), "setupLoadBalancer.sh").toString()
           + " -t " + ingressType
           + additionalOptions);
    logger.info("Run setupLoadBalancer.sh to manage {0} ingress controller", ingressType);
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to manage ingress controller");
  }
}

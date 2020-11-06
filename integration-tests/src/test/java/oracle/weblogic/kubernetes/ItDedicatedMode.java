// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteServiceAccount;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPV;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.TestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The current class verifies various use cases related to domainNamespaceSelectionStrategy.
 * For more detail regarding the feature, please refer to
 * https://github.com/oracle/weblogic-kubernetes-operator/blob/develop/docs-source/content/
 * userguide/managing-operators/using-the-operator/using-helm.md#overall-operator-information.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test Operator and WebLogic domain with Dedicated set to true")
@IntegrationTest
class ItDedicatedMode {
  // namespace constants
  private static String opNamespace = null;
  private static String domain1Namespace = null;
  private static String domain2Namespace = null;

  private static final String CRD_V16 = "domain-crd.yaml";
  private static final String CRD_V15 = "domain-v1beta1-crd.yaml";

  // domain constants
  private final String domainUid = "dedicated-domain-1";
  private final String clusterName = "cluster-1";
  private final int replicaCount = 2;
  private final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private final String managedServerPodPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

  // operator constants
  private static HelmParams opHelmParams;
  private static String opServiceAccount;
  private static final String domainNamespaceSelectionStrategy = "Dedicated";

  private static LoggingFacade logger = null;

  /**
   * Get namespaces for operator and domain2. Create CRD based on the k8s version.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism.
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    // in the dedicated mode, the operator only manages domains in the operator's own namespace
    domain1Namespace = opNamespace;

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domain2Namespace = namespaces.get(1);

    // Variables for Operator
    opServiceAccount = opNamespace + "-sa";
    opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);

    // get k8s version
    CommandParams k8sVersionCommand = new CommandParams();
    new Command()
        .withParams(k8sVersionCommand
            .saveResults(true)
            .command("kubectl version"))
        .execute();

    String k8sVersion = k8sVersionCommand.stdout();
    boolean k8sV15 = k8sVersion.contains("v1.15");
    String installedK8sVersion = (k8sV15) ? CRD_V15 : CRD_V16;

    // delete existing CRD
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    // install CRD
    String createCrdCommand = "kubectl create -f " + ITTESTS_DIR + "/../kubernetes/crd/" + installedK8sVersion;
    logger.info("Creating CRD with command {0}", createCrdCommand);
    new Command()
        .withParams(new CommandParams().command(createCrdCommand))
        .execute();
  }

  @AfterAll
  public void tearDownAll() {
  }

  /**
   * When installing the Operator via helm install,
   * set the Operator Helm Chart parameter domainNamespaceSelectionStrategy to Dedicated and
   * set domainNamespaces to something that is different from the operator's namespace.
   * Make sure that the domain which is not in the operator's target namespaces do not come up.
   *   Install an Operator with a namespace and set domainNamespaces in a different namespace
   *     from the Operator's namespace, also set domainNamespaceSelectionStrategy to Dedicated
   *     for the Operator Helm Chart.
   *   Verify the Operator is up and running.
   *   Create WebLogic Domain in a namespace that is different from the Operator's namespace.
   *   Verify that the domain does not come up.
   */
  @Test
  @Order(1)
  @DisplayName("Set domainNamespaceSelectionStrategy to Dedicated for the Operator Helm Chart and "
      + "verify that a domain not deployed in operator's namespace doesn't come up")
  public void testDedicatedModeDiffNamespace() {
    // install and verify operator
    logger.info("Installing and verifying operator");
    installAndVerifyOperator(opNamespace, opNamespace + "-sa",
        true, 0, opHelmParams, domainNamespaceSelectionStrategy,
        false, domain2Namespace);

    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    createDomain(domain2Namespace);
    verifyDomainNotRunning(domain2Namespace);
  }

  /**
   * When installing the Operator via helm install,
   * set domainNamespaceSelectionStrategy to Dedicated for the Operator Helm Chart.
   * Make sure that the domains in the operator's target namespaces comes up.
   *   Operator is installed in the test case testDedicatedModeDiffNamespace.
   *   Create a WebLogic Domain with the same namespace as Operator's namespace.
   *   Verify that the WebLogic domain whose namespace is same as Operator's namespace comes up.
   */
  @Test
  @Order(2)
  @DisplayName("Set domainNamespaceSelectionStrategy to Dedicated for the Operator Helm Chart and "
      + "verify that the domain deployed in the operator's namespace comes up")
  public void testDedicatedModeSameNamespace() {
    // create and verify the domain
    logger.info("Creating and verifying model in image domain");
    createDomain(domain1Namespace);
    verifyDomainRunning(domain1Namespace);
  }

  /**
   * Test that when domainNamespaceSelectionStrategy is set to Dedicated for the Operator Helm Chart,
   * scaling up cluster-1 in domain1Namespace succeeds.
   */
  @Test
  @Order(3)
  @DisplayName("Scale up cluster-1 in domain1Namespace and verify it succeeds")
  public void testDedicatedModeSameNamespaceScale() {
    // scale the cluster and check domain can be managed from the operator
    int externalRestHttpsPort = getServiceNodePort(opNamespace, "external-weblogic-operator-svc");
    logger.info("externalRestHttpsPort {0}", externalRestHttpsPort);

    logger.info("scaling the cluster from {0} servers to {1} servers", replicaCount, replicaCount + 1);
    scaleAndVerifyCluster(clusterName, domainUid, domain1Namespace,
        managedServerPodPrefix, replicaCount, replicaCount + 1,
        true, externalRestHttpsPort, opNamespace, opNamespace + "-sa",
        false, "", "", 0, "",
        "", null, null);
  }

  /**
   * Test when domainNamespaceSelectionStrategy is set to Dedicated for the Operator Helm Chart and
   * the CRD with a lower than expected version is present, Operator fails with error
   * if it has no permission to overwrite the CRD.
   */
  @Test
  @Order(4)
  @Disabled("Disable the test because the Operator has permission to overwrite the CRD")
  @DisplayName("Create a CRD with a lower than expected version and verify that Operator fails with error")
  public void testDedicatedModeNlowerVersionCrd() {
    // delete existing CRD
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    // install a lower version of CRD, v2.6.0
    new Command()
        .withParams(new CommandParams()
            .command("kubectl create -f " + ITTESTS_DIR + "/../kubernetes/crd/domain-v1beta1-crdv7-260.yaml"))
        .execute();

    try {
      // install latest version of operator and verify
      logger.info("Installing and verifying operator");
      installAndVerifyOperator(opNamespace, opNamespace + "-sa",
          false, 0, opHelmParams, domainNamespaceSelectionStrategy,
          false, domain2Namespace);

      // we expect installAndVerifyOperator fails with a lower than expected version of CRD
      fail("Installing the Operator should fail with a lower than expected version of CRD");
    } catch (Exception ex) {
      logger.info("Installing the Operator with a lower than expected version of CRD failed as expected");
    } finally {
      // restore the test env
      uninstallOperatorAndVerify();
    }
  }

  /**
   * Test when domainNamespaceSelectionStrategy is set to Dedicated for the Operator Helm Chart and
   * the CRD is not present or is deleted, Operator fails with error if it has no permission to create the CRD.
   */
  @Test
  @Order(5)
  @Disabled("Disable the test because the Operator has permission to create the CRD")
  @DisplayName("Delete the CRD and verify that Operator fails with error")
  public void testDedicatedModeNoCrd() {
    // delete existing CRD
    logger.info("Delete existing CRD");
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete crd domains.weblogic.oracle --ignore-not-found"))
        .execute();

    try {
      // install and verify operator
      logger.info("Installing and verifying operator");
      installAndVerifyOperator(opNamespace, opNamespace + "-sa",
          false, 0, opHelmParams, domainNamespaceSelectionStrategy,
          false, domain2Namespace);

      // we expect installAndVerifyOperator fails when the CRD misses
      fail("Installing the Operator should fail when the CRD misses");
    } catch (Exception ex) {
      logger.info("Installing the Operator failed as expected when the CRD misses");
    } finally {
      // restore the test env
      uninstallOperatorAndVerify();
    }
  }

  /**
   * Test t3 channel access by deploying a app using WLST.
   * Test Creates a domain in persistent volume using WLST.
   * Verifies that the pods comes up and sample application deployment works.
   */
  @Order(6)
  @Test
  @DisplayName("Test admin server t3 channel access by deploying a application")
  public void testAdminServerT3Channel() {

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domain1Namespace);

    // build the clusterview application
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", domain1Namespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    Path clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

    final String domainUid = "t3channeldomain";
    final String clusterName = "mycluster";

    final String adminServerName = "admin-server";
    final String adminServerPodName = domainUid + "-" + adminServerName;

    final String managedServerNameBase = "ms-";
    String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
    final int managedServerPort = 8001;

    int replicaCount = 2;

    // in general the node port range has to be between 30,000 to 32,767
    // to avoid port conflict because of the delay in using it, the port here
    // starts with 30100
    final int t3ChannelPort = getNextFreePort(30172, 32767);

    final String pvName = domainUid + "-pv"; // name of the persistent volume
    final String pvcName = domainUid + "-pvc"; // name of the persistent volume claim

    // create WebLogic domain credential secret
    String wlSecretName = "t3weblogic-credentials";
    createSecretWithUsernamePassword(wlSecretName, domain1Namespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domain1Namespace);

    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile("domain", "properties"),
        "Failed to create domain properties file");
    Properties p = new Properties();
    p.setProperty("domain_path", "/shared/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", adminServerName);
    p.setProperty("managed_server_port", Integer.toString(managedServerPort));
    p.setProperty("admin_server_port", "7001");
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "2");
    p.setProperty("managed_server_name_base", managedServerNameBase);
    p.setProperty("domain_logs", "/shared/logs");
    p.setProperty("production_mode_enabled", "true");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "domain properties file"),
        "Failed to write domain properties file");

    // WLST script for creating domain
    Path wlstScript = Paths.get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");

    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScript);
    domainScriptFiles.add(domainPropertiesFile.toPath());

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(
            domainScriptConfigMapName, domainScriptFiles, domain1Namespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScript.getFileName()) //wlst.sh script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getName()); //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        domain1Namespace, jobCreationContainer);

    // create a domain custom resource configuration object
    logger.info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domain1Namespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/shared/domains/" + domainUid)  // point to domain home in pv
            .domainHomeSourceType("PersistentVolume") // set the domain home source type as pv
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET)))  // this secret is used only in non-kind cluster
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domain1Namespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));

    // verify the domain custom resource is created
    createDomainAndVerify(domain, domain1Namespace);

    // verify the admin server service and pod created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domain1Namespace);

    // verify managed server services and pods created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service and pod {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domain1Namespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domain1Namespace);
    }

    // deploy application and verify all servers functions normally
    logger.info("Getting node port for default channel");
    int defaultChannelNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domain1Namespace, getExternalServicePodName(adminServerPodName), "t3Channel"),
        "Getting admin server default port failed");
    logger.info("techannel channel node port: {0}", defaultChannelNodePort);
    assertNotEquals(-1, defaultChannelNodePort, "admin server t3ChannelNodePort is not valid");

    //deploy clusterview application
    logger.info("Deploying clusterview app {0} to cluster {1}",
        clusterViewAppPath, clusterName);
    deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelNodePort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, adminServerName + "," + clusterName, clusterViewAppPath,
        domain1Namespace);

    List<String> managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(managedServerNameBase + i);
    }

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
  }

  private void createDomain(String domainNamespace) {
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        "weblogic", "welcome1"),
          String.format("create secret for admin credentials failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc"),
          String.format("create encryption secret failed for %s", encryptionSecretName));

    // create domain and verify
    logger.info("Create model in image domain {0} in namespace {1} using docker image {2}",
        domainNamespace, domainUid, domainNamespace);
    createDomainCrAndVerify(domainNamespace, OCIR_SECRET_NAME, adminSecretName, encryptionSecretName);
  }

  private void createDomainCrAndVerify(String domainNamespace,
                                       String repoSecretName,
                                       String adminSecretName,
                                       String encryptionSecretName) {
    // get the pre-built image created by IntegrationTestWatcher
    String miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // construct a list of oracle.weblogic.domain.Cluster objects to be used in the domain custom resource
    List<Cluster> clusters = new ArrayList<>();
    clusters.add(new Cluster()
        .clusterName(clusterName)
        .replicas(replicaCount)
        .serverStartState("RUNNING"));

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING"))
            .clusters(clusters)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName))));

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);
  }

  private void verifyDomainRunning(String domainNamespace) {
    // check that admin server pod is ready and the service exists in the domain namespace
    logger.info("Checking that admin server pod {0} is ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;

      // check that the managed server pod is ready and the service exists in the domain namespace
      logger.info("Checking that managed server pod {0} is ready in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private void verifyDomainNotRunning(String domainNamespace) {
    // check that admin server pod doesn't exists in the domain namespace
    logger.info("Checking that admin server pod {0} doesn't exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);

    // check for managed server pods existence in the domain namespace
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodPrefix + i;

      // check that managed server pod doesn't exists in the domain namespace
      logger.info("Checking that managed server pod {0} doesn't exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }
  }

  private static void verifyMemberHealth(String adminServerPodName, List<String> managedServerNames,
      String user, String password) {

    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(()
        -> getServiceNodePort(domain1Namespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    logger.info("Checking the health of servers in cluster");
    String url = "http://" + K8S_NODEPORT_HOST + ":" + serviceNodePort
        + "/clusterview/ClusterViewServlet?user=" + user + "&password=" + password;

    // create standard, reusable retry/backoff policy
    ConditionFactory withStandardRetryPolicy
        = with().pollDelay(2, SECONDS)
            .and().with().pollInterval(10, SECONDS)
            .atMost(10, MINUTES).await();

    withStandardRetryPolicy.conditionEvaluationListener(
        condition -> logger.info("Verifying the health of all cluster members"
            + "(elapsed time {0} ms, remaining time {1} ms)",
            condition.getElapsedTimeInMS(),
            condition.getRemainingTimeInMS()))
        .until((Callable<Boolean>) () -> {
          HttpResponse<String> response = assertDoesNotThrow(() -> OracleHttpClient.get(url, true));
          assertEquals(200, response.statusCode(), "Status code not equals to 200");
          boolean health = true;
          for (String managedServer : managedServerNames) {
            health = health && response.body().contains(managedServer + ":HEALTH_OK");
            if (health) {
              logger.info(managedServer + " is healthy");
            } else {
              logger.info(managedServer + " health is not OK or server not found");
            }
          }
          return health;
        });
  }

  private void uninstallOperatorAndVerify() {
    // uninstall operator
    assertTrue(uninstallOperator(opHelmParams),
        String.format("Uninstall operator failed in namespace %s", opNamespace));

    // delete service account
    assertTrue(deleteServiceAccount(opServiceAccount,opNamespace),
        String.format("Delete service acct %s failed in namespace %s", opServiceAccount, opNamespace));

    // delete secret/ocir-secret
    new Command()
        .withParams(new CommandParams()
            .command("kubectl delete secret/ocir-secret -n " + opNamespace + " --ignore-not-found"))
        .execute();
  }
}

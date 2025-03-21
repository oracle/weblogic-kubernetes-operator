// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.CreateIfNotExists;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainOnPV;
import oracle.weblogic.domain.DomainOnPVType;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.getNextIntrospectVersion;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.actions.TestActions.startDomain;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podStateNotChanged;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminServerRESTAccess;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.BuildApplication.buildApplication;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.exeAppInServerPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.isAppInServerPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapForDomainCreation;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FmwUtils.getConfiguration;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.createDomainJob;
import static oracle.weblogic.kubernetes.utils.JobUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to Situational Configuration overrides for system resources.
 */
@DisplayName("Verify the JMS and WLDF system resources are overridden with values from override files")
@IntegrationTest
@Tag("kind-parallel")
@Tag("okd-wls-mrg")
@Tag("oke-parallel")
class ItSystemResOverrides {

  private static String opNamespace = null;
  private static String domainNamespace = null;
  final String domainUid = "mysitconfigdomain";
  final String clusterName = "mycluster";
  final String adminServerName = "admin-server";
  final String adminServerPodName = domainUid + "-" + adminServerName;
  final String managedServerNameBase = "ms-";
  private static int managedServerPort = 8001;
  int t3ChannelPort;
  private static int adminPort = 7001;
  
  static {
    if (WEBLOGIC_IMAGE_TAG_DEFAULT.startsWith("14")) {
      adminPort = 7001;
    } else {
      adminPort = 7002;
    }
  }
  
  private static String hostHeader;  
  final String pvName = getUniqueName(domainUid + "-pv-");
  final String pvcName = getUniqueName(domainUid + "-pvc-");
  final String wlSecretName = "weblogic-credentials";
  final String managedServerPodNamePrefix = domainUid + "-" + managedServerNameBase;
  int replicaCount = 2;
  String adminSvcExtHost = null;

  static Path sitconfigAppPath;
  String overridecm = "configoverride-cm";
  LinkedHashMap<String, OffsetDateTime> podTimestamps;
  
  private static Path encryptModelScript;
  private static final String passPhrase = "encryptPA55word";
  private static final String encryptionSecret = "model-encryption-secret";

  private static LoggingFacade logger = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pulls WebLogic image if running tests in Kind cluster.
   * Installs operator.
   * Creates and starts a WebLogic domain with a dynamic cluster containing 2 managed server instances.
   * Creates JMS and WLDF system resources.
   * Deploys sitconfig application to cluster and admin targets.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public void initAll(@Namespaces(2) List<String> namespaces) throws IOException {
    logger = getLogger();

    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for domain namspace");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace = namespaces.get(1);
    
    logger.info("installing WebLogic Deploy Tool");
    downloadAndInstallWDT();

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    //create and start WebLogic domain
    createDomain();
    restartDomain();

    // build the sitconfig application
    Path distDir = buildApplication(Paths.get(APP_DIR, "sitconfig"),
        null, null, "dist", domainNamespace);
    sitconfigAppPath = Paths.get(distDir.toString(), "sitconfig.war");
    assertTrue(sitconfigAppPath.toFile().exists(), "Application archive is not available");

    //deploy application to view server configuration
    deployApplication(clusterName + "," + adminServerName);
  }

  /**
   * Test JMS and WLDF system resources configurations are overridden dynamically when domain resource
   * is updated with overridesConfigMap property. After the override verifies the system resources properties
   * are overridden as per the values set.
   * 1. Creates a config override map containing JMS and WLDF override files
   * 2. Patches the domain with new configmap override and introspect version
   * 3. Verifies the introspector is run and resource config is updated
   */
  @Test
  @DisplayName("Test JMS and WLDF system resources override")
  void testJmsWldfSystemResourceOverride() {

    //store the pod creation timestamps
    storePodCreationTimestamps();

    List<Path> overrideFiles = new ArrayList<>();
    overrideFiles.add(
        Paths.get(RESOURCE_DIR, "configfiles/configoverridesset2/jms-ClusterJmsSystemResource.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset2/diagnostics-WLDF-MODULE-0.xml"));
    overrideFiles.add(Paths.get(RESOURCE_DIR, "configfiles/configoverridesset2/version.txt"));

    //create config override map
    createConfigMapFromFiles(overridecm, overrideFiles, domainNamespace);

    String introspectVersion = assertDoesNotThrow(() -> getNextIntrospectVersion(domainUid, domainNamespace));

    logger.info("patch the domain resource with overridesConfigMap and introspectVersion");
    String patchStr
        = "["
        + "{\"op\": \"add\", \"path\": \"/spec/configuration/overridesConfigMap\", \"value\": \"" + overridecm + "\"},"
        + "{\"op\": \"add\", \"path\": \"/spec/introspectVersion\", \"value\": \"" + introspectVersion + "\"}"
        + "]";
    logger.info("Updating domain configuration using patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
        "Failed to patch domain");

    verifyIntrospectorRuns();
    verifyPodsStateNotChanged();

    //wait until config is updated upto 5 minutes
    if (OKE_CLUSTER) {
      testUntil(
          isAppInServerPodReady(domainNamespace,
              managedServerPodNamePrefix + 1, 8001, "/sitconfig/SitconfigServlet", "PASSED"),
              logger, "Check Deployed App {0} in server {1}",
              "/sitconfig/SitconfigServlet",
              managedServerPodNamePrefix + 1);
    } else {
      testUntil(
          configUpdated(),
          logger,
          "jms server configuration to be updated");
    }
    assertDoesNotThrow(() -> verifyJMSResourceOverride());
    assertDoesNotThrow(() -> verifyWLDFResourceOverride());
  }

  private Callable<Boolean> configUpdated() {
    return (()
        -> {
      logger.info("Getting node port for default channel");
      int serviceNodePort = assertDoesNotThrow(()
          -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName),
              "default"),
          "Getting admin server node port failed");

      //verify server attribute MaxMessageSize
      String appURI = "/sitconfig/SitconfigServlet";

      if (adminSvcExtHost == null) {
        adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
      }
      logger.info("admin svc host = {0}", adminSvcExtHost);
      String hostAndPort = getHostAndPort(adminSvcExtHost, serviceNodePort);
      Map<String, String> headers = null;
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
        headers = new HashMap<>();
        headers.put("host", hostHeader);
      }
      String url = "http://" + hostAndPort + appURI;
      HttpResponse<String> response = OracleHttpClient.get(url, headers, true);
      return (response.statusCode() == 200) && response.body().contains("ExpirationPolicy:Discard");
    });
  }

  private void verifyJMSResourceOverride() throws IOException, InterruptedException {
    String resourcePath = "/sitconfig/SitconfigServlet";

    if (OKE_CLUSTER) {
      ExecResult result = exeAppInServerPod(domainNamespace, managedServerPodNamePrefix + 1,
          8001, resourcePath);
      assertTrue(result.stdout().contains("ExpirationPolicy:Discard"), "Didn't get ExpirationPolicy:Discard");
      assertTrue(result.stdout().contains("RedeliveryLimit:20"), "Didn't get RedeliveryLimit:20");
      assertTrue(result.stdout().contains("Notes:mysitconfigdomain"), "Didn't get Correct Notes description");
    } else {
      int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
      if (adminSvcExtHost == null) {
        adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
      }
      logger.info("admin svc host = {0}", adminSvcExtHost);
      String hostAndPort = getHostAndPort(adminSvcExtHost, port);
      Map<String, String> headers = null;
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
        headers = new HashMap<>();
        headers.put("host", hostHeader);
      }
      String uri = "http://" + hostAndPort + resourcePath;
      HttpResponse<String> response = OracleHttpClient.get(uri, headers, true);
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
      assertTrue(response.body().contains("ExpirationPolicy:Discard"), "Didn't get ExpirationPolicy:Discard");
      assertTrue(response.body().contains("RedeliveryLimit:20"), "Didn't get RedeliveryLimit:20");
      assertTrue(response.body().contains("Notes:mysitconfigdomain"), "Didn't get Correct Notes description");
    }
  }

  private void verifyWLDFResourceOverride() throws IOException, InterruptedException {
    String resourcePath = "/sitconfig/SitconfigServlet";

    if (OKE_CLUSTER) {
      ExecResult result = exeAppInServerPod(domainNamespace, managedServerPodNamePrefix + 1,
          8001, resourcePath);
      assertTrue(result.stdout().contains("MONITORS:PASSED"), "Didn't get MONITORS:PASSED");
      assertTrue(result.stdout().contains("HARVESTORS:PASSED"), "Didn't get HARVESTORS:PASSED");
      assertTrue(result.stdout().contains("HARVESTOR MATCHED:weblogic.management.runtime.JDBCServiceRuntimeMBean"),
          "Didn't get HARVESTOR MATCHED:weblogic.management.runtime.JDBCServiceRuntimeMBean");
      assertTrue(result.stdout().contains("HARVESTOR MATCHED:weblogic.management.runtime.ServerRuntimeMBean"),
          "Didn't get HARVESTOR MATCHED:weblogic.management.runtime.ServerRuntimeMBean");
    } else {
      int port = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName),
          "default");
      if (adminSvcExtHost == null) {
        adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
      }
      logger.info("admin svc host = {0}", adminSvcExtHost);
      String hostAndPort = getHostAndPort(adminSvcExtHost, port);
      Map<String, String> headers = null;
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
        headers = new HashMap<>();
        headers.put("host", hostHeader);
      }
      String uri = "http://" + hostAndPort + resourcePath;
      HttpResponse<String> response = OracleHttpClient.get(uri, headers, true);
      assertEquals(200, response.statusCode(), "Status code not equals to 200");
      assertTrue(response.body().contains("MONITORS:PASSED"), "Didn't get MONITORS:PASSED");
      assertTrue(response.body().contains("HARVESTORS:PASSED"), "Didn't get HARVESTORS:PASSED");
      assertTrue(response.body().contains("HARVESTOR MATCHED:weblogic.management.runtime.JDBCServiceRuntimeMBean"),
          "Didn't get HARVESTOR MATCHED:weblogic.management.runtime.JDBCServiceRuntimeMBean");
      assertTrue(response.body().contains("HARVESTOR MATCHED:weblogic.management.runtime.ServerRuntimeMBean"),
          "Didn't get HARVESTOR MATCHED:weblogic.management.runtime.ServerRuntimeMBean");
    }
  }

  //store pod creation timestamps for podstate check
  private void storePodCreationTimestamps() {
    // get the pod creation time stamps
    podTimestamps = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace, adminServerPodName);
    podTimestamps.put(adminServerPodName, adminPodCreationTime);
    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      podTimestamps.put(managedServerPodNamePrefix + i,
          getPodCreationTime(domainNamespace, managedServerPodNamePrefix + i));
    }
  }

  //check if the pods are restarted by comparing the pod creationtimestamp.
  private void verifyPodsStateNotChanged() {
    logger.info("Verifying the WebLogic server pod states are not changed");
    for (Map.Entry<String, OffsetDateTime> entry : podTimestamps.entrySet()) {
      String podName = entry.getKey();
      OffsetDateTime creationTimestamp = entry.getValue();
      assertTrue(podStateNotChanged(podName, domainUid, domainNamespace,
          creationTimestamp), "Pod is restarted");
    }
  }

  //verify the introspector pod is created and run
  private void verifyIntrospectorRuns() {
    //verify the introspector pod is created and runs
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectPodNameBase = getIntrospectJobName(domainUid);
    checkPodExists(introspectPodNameBase, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectPodNameBase, domainUid, domainNamespace);
  }

  //create a standard WebLogic domain.
  private void createDomain() throws IOException {
    String uniqueDomainHome = "/shared/" + domainNamespace + "/domains/";

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    final String wlsModelFilePrefix = "sitconfig-dci-model";
    final String wlsModelFile = wlsModelFilePrefix + ".yaml";
    t3ChannelPort = getNextFreePort();
    logger.info("Create WDT property file");
    File wlsModelPropFile = createWdtPropertyFile(wlsModelFilePrefix,
        K8S_NODEPORT_HOST, t3ChannelPort);
    logger.info("Create WDT passphrase file"); 
    File passphraseFile = createPassphraseFile(passPhrase);    
    logger.info("Run encruptModel.sh script to encrypt clear text password in property file");    
    encryptModel(encryptModelScript,
        Path.of(MODEL_DIR, wlsModelFile),
        wlsModelPropFile.toPath(), passphraseFile.toPath());
    createSecretWithUsernamePassword(wlSecretName, opNamespace, clusterName, passPhrase);
    createEncryptionSecret(encryptionSecret, domainNamespace);

    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "wls-domain-on-pv-image";
    String domainCreationImagetag = getDateAndTimeStamp();
    // create image with model and wdt installation files
    WitParams witParams
        = new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(domainCreationImagetag)
            .modelFiles(Collections.singletonList(MODEL_DIR + "/" + wlsModelFile))
            .modelVariableFiles(Collections.singletonList(wlsModelPropFile.getAbsolutePath()));
    createAndPushAuxiliaryImage(domainCreationImageName, domainCreationImagetag, witParams);

    DomainCreationImage domainCreationImage
        = new DomainCreationImage().image(domainCreationImageName + ":" + domainCreationImagetag);

    // create a domain resource
    logger.info("Creating domain custom resource");
    Map<String, Quantity> pvCapacity = new HashMap<>();
    pvCapacity.put("storage", new Quantity("2Gi"));

    Map<String, Quantity> pvcRequest = new HashMap<>();
    pvcRequest.put("storage", new Quantity("2Gi"));
    Configuration configuration = null;
    final String storageClassName = "weblogic-domain-storage-class";
    if (OKE_CLUSTER) {
      configuration = getConfiguration(pvcName, pvcRequest, "oci-fss");
    } else {
      configuration = getConfiguration(pvName, pvcName, pvCapacity, pvcRequest, storageClassName,
          ItSystemResOverrides.class.getSimpleName());
    }
    configuration.getInitializeDomainOnPV()
        .modelEncryptionPassphraseSecret(encryptionSecret)
        .domain(new DomainOnPV()
            .createMode(CreateIfNotExists.DOMAIN)
            .domainCreationImages(Collections.singletonList(domainCreationImage))
            .domainType(DomainOnPVType.WLS));
    configuration.overrideDistributionStrategy("Dynamic");

    // create secrets
    List<V1LocalObjectReference> secrets = new ArrayList<>();
    for (String secret : new String[]{wlSecretName, BASE_IMAGES_REPO_SECRET_NAME}) {
      secrets.add(new V1LocalObjectReference().name(secret));
    }

    // create a domain custom resource configuration object
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(uniqueDomainHome + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domainNamespace + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.debug.DebugSituationalConfig=true "
                        + "-Dweblogic.debug.DebugSituationalConfigDumpXml=true "
                        + "-Dweblogic.kernel.debug=true "
                        + "-Dweblogic.debug.DebugMessaging=true "
                        + "-Dweblogic.security.SSL.ignoreHostnameVerification=true "
                        + "-Dweblogic.debug.DebugConnection=true "
                        + "-Dweblogic.ResolveDNSName=true"))
                .addEnvItem(new V1EnvVar()
                    .name("CUSTOM_ENV")
                    .value("##~`!^${ls}"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom"))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort))))
            .configuration(configuration));
    domain.spec().setImagePullSecrets(secrets);

    // create cluster resource for the domain    
    if (!Cluster.doesClusterExist(clusterName, CLUSTER_VERSION, domainNamespace)) {
      ClusterResource cluster = createClusterResource(clusterName,
          clusterName, domainNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterName));

    setPodAntiAffinity(domain);

    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // verify the admin server service created
    checkServiceExists(adminServerPodName, domainNamespace);

    // verify admin server pod is ready
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkServiceExists(managedServerPodNamePrefix + i, domainNamespace);
    }

    // verify managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Waiting for managed server pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReady(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    //create router for admin service on OKD
    if (adminSvcExtHost == null) {
      adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    }
    logger.info("admin svc host = {0}", adminSvcExtHost);
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(domainNamespace, domainUid, adminServerName, adminPort);
      assertDoesNotThrow(() -> verifyAdminServerRESTAccess("localhost", 
          TRAEFIK_INGRESS_HTTP_HOSTPORT, false, hostHeader));
    }     
  }

  //deploy application sitconfig.war to domain
  private void deployApplication(String targets) {
    logger.info("Getting port for default channel");
    int defaultChannelPort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server default port failed");
    logger.info("default channel port: {0}", defaultChannelPort);
    assertNotEquals(-1, defaultChannelPort, "admin server defaultChannelPort is not valid");

    //deploy application
    logger.info("Deploying webapp {0} to domain", sitconfigAppPath);
    deployUsingWlst(adminServerPodName, Integer.toString(defaultChannelPort),
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, targets, sitconfigAppPath,
        domainNamespace);
  }

  /**
   * Create a WebLogic domain on a persistent volume by doing the following. Create a configmap containing WLST script
   * and property file. Create a Kubernetes job to create domain on persistent volume.
   *
   * @param wlstScriptFile python script to create domain
   * @param domainPropertiesFile properties file containing domain configuration
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   */
  private void createDomainOnPVUsingWlst(Path wlstScriptFile, Path domainPropertiesFile,
      String pvName, String pvcName, String namespace) {
    logger.info("Preparing to run create domain job using WLST");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(wlstScriptFile);
    domainScriptFiles.add(domainPropertiesFile);

    logger.info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm";

    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(
            domainScriptConfigMapName, domainScriptFiles, namespace, this.getClass().getSimpleName()),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem("/u01/weblogic/" + wlstScriptFile.getFileName()) //wlst.sh
        // script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem("/u01/weblogic/" + domainPropertiesFile.getFileName());
    //domain property file

    logger.info("Running a Kubernetes job to create the domain");
    createDomainJob(WEBLOGIC_IMAGE_TO_USE_IN_SPEC, pvName, pvcName, domainScriptConfigMapName,
        namespace, jobCreationContainer);
  }

  //restart pods by manipulating the serverStartPolicy to Never and IfNeeded
  private void restartDomain() {
    logger.info("Restarting domain {0}", domainNamespace);
    shutdownDomain(domainUid, domainNamespace);

    logger.info("Checking for admin server pod shutdown");
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);
    logger.info("Checking managed server pods were shutdown");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }

    startDomain(domainUid, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // verify managed server services and pods are created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPodNamePrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodNamePrefix + i, domainUid, domainNamespace);
    }
  }
  
  public static File createWdtPropertyFile(String wlsModelFilePrefix, String nodePortHost, int t3Port) {
    // create property file used with domain model file
    Properties p = new Properties();
    p.setProperty("WebLogicAdminUserName", ADMIN_USERNAME_DEFAULT);
    p.setProperty("WebLogicAdminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("K8S_NODEPORT_HOST", nodePortHost);
    p.setProperty("T3_CHANNEL_PORT", Integer.toString(t3Port));

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
            File.createTempFile(wlsModelFilePrefix, ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create WLS model properties file");

    // create the property file
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WLS properties file"),
        "Failed to write WLS properties file");

    return domainPropertiesFile;
  }
  
  private static void downloadAndInstallWDT() throws IOException {
    String wdtUrl = WDT_DOWNLOAD_URL + "/download/weblogic-deploy.zip";
    Path destLocation = Path.of(DOWNLOAD_DIR, "wdt", "weblogic-deploy.zip");
    encryptModelScript = Path.of(DOWNLOAD_DIR, "wdt", "weblogic-deploy", "bin", "encryptModel.sh");
    if (!Files.exists(destLocation) && !Files.exists(encryptModelScript)) {
      logger.info("Downloading WDT to {0}", destLocation);
      Files.createDirectories(destLocation.getParent());
      OracleHttpClient.downloadFile(wdtUrl, destLocation.toString(), null, null, 3);
      String cmd = "cd " + destLocation.getParent() + ";unzip " + destLocation;
      assertTrue(Command.withParams(new CommandParams().command(cmd)).execute(), "unzip command failed");
    }
    assertTrue(Files.exists(encryptModelScript), "could not find createDomain.sh script");
  }
  
  private static File createPassphraseFile(String passPhrase) throws IOException {
    // create pass phrase file used with domain model file
    File passphraseFile = assertDoesNotThrow(()
        -> File.createTempFile("passphrase", ".txt", new File(RESULTS_TEMPFILE)),
        "Failed to create WLS model encrypt passphrase file");
    Files.write(passphraseFile.toPath(), passPhrase.getBytes(StandardCharsets.UTF_8));
    logger.info("passphrase file contents {0}", Files.readString(passphraseFile.toPath()));
    return passphraseFile;
  }
  
  private static void encryptModel(Path encryptModelScript, Path modelFile,
      Path propertyFile, Path passphraseFile) throws IOException {
    Path mwHome = Path.of(RESULTS_ROOT, "mwhome");
    logger.info("Encrypting property file containing the secrets {0}", propertyFile.toString());
    List<String> command = List.of(
        encryptModelScript.toString(),
        "-oracle_home", mwHome.toString(),
        "-model_file", modelFile.toString(),
        "-variable_file", propertyFile.toString(),
        "-passphrase_file", passphraseFile.toString()
    );
    logger.info("running {0}", command);
    assertTrue(Command.withParams(new CommandParams()
        .command(command.stream().collect(Collectors.joining(" ")))).execute(),
        "encryptModel.sh command failed");
    logger.info("Encrypted passphrase file contents {0}", Files.readString(propertyFile));
  }
  
  public static void createEncryptionSecret(String secretName, String namespace) {
    Map<String, String> secretMap = new HashMap<>();
    secretMap.put("passphrase", passPhrase);

    if (!secretExists(secretName, namespace)) {
      boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
          .metadata(new V1ObjectMeta()
              .name(secretName)
              .namespace(namespace))
          .stringData(secretMap)), "Create secret failed with ApiException");

      assertTrue(secretCreated, String.format("create secret failed for %s", secretName));
    }
  }
}

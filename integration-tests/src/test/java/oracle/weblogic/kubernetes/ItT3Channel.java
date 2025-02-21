// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
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
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.BuildApplication;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.OracleHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminServerRESTAccess;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.FileUtils.createWdtPropertyFile;
import static oracle.weblogic.kubernetes.utils.FmwUtils.getConfiguration;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class to test the t3 channel accessibility and using it for deployment.
 */
@DisplayName("Test T3 channel deployment")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("oke-weekly-sequential")
@Tag("kind-sequential")
@Tag("gate")    
class ItT3Channel {
  // namespace constants
  private static String opNamespace = null;
  private static String domainNamespace = null;

  // domain constants
  private final String domainUid = "t3channel-domain";
  private final String clusterName = "mycluster";
  private final int replicaCount = 2;
  private final String adminServerName = ADMIN_SERVER_NAME_BASE;
  private final String adminServerPodName = domainUid + "-" + adminServerName;
  private final String managedServerPodPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static String hostHeader;

  private static LoggingFacade logger = null;

  /**
   * Get namespaces for operator and domain.
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

    // get a new unique domainNamespace
    logger.info("Assigning a unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, domainNamespace);
  }

  /**
   * Test t3 channel access by deploying a app using WLST.
   * Test Creates a domain in persistent volume using WLST.
   * Verifies that the pods comes up and sample application deployment works.
   * Verify the default service port is set to 7100 since ListenPort is not
   * explicitly set on WLST offline script to create the domain configuration.
   */
  @Test
  @DisplayName("Test admin server t3 channel access by deploying a application")
  void testAdminServerT3Channel() {
    String uniqueDomainHome = "/shared/" + domainNamespace + "/domains/";
    
    // build the clusterview application
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"), null, null,
        "dist", domainNamespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    Path clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

    final int t3ChannelPort = getNextFreePort();

    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
    
    // create WebLogic domain credential secret
    String wlSecretName = "t3weblogic-credentials";
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    
    final String wlsModelFilePrefix = "model-dci-introspect";
    final String wlsModelFile = wlsModelFilePrefix + ".yaml";
    File wlsModelPropFile = createWdtPropertyFile(wlsModelFilePrefix,
        K8S_NODEPORT_HOST, t3ChannelPort);

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
          ItT3Channel.class.getSimpleName());
    }
    configuration.getInitializeDomainOnPV().domain(new DomainOnPV()
        .createMode(CreateIfNotExists.DOMAIN)
        .domainCreationImages(Collections.singletonList(domainCreationImage))
        .domainType(DomainOnPVType.WLS));

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
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
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
    
    // verify the domain custom resource is created
    createDomainAndVerify(domain, domainNamespace);

    // verify the admin server service and pod created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services and pods created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Checking managed server service and pod {0} is created in namespace {1}",
          managedServerPodPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodPrefix + i, domainUid, domainNamespace);
    }

    // deploy application and verify all servers functions normally
    //deploy clusterview application
    if (OKE_CLUSTER) {
      int adminPort = 7001;
      assertDoesNotThrow(() -> deployUsingWlst(adminServerPodName,
          String.valueOf(adminPort),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
          clusterName + "," + adminServerName,
          clusterViewAppPath,
          domainNamespace), "Deploying the application");
    } else {
      logger.info("Deploying clusterview app {0} to cluster {1}",
          clusterViewAppPath, clusterName);
      deployUsingWlst(adminServerPodName, Integer.toString(t3ChannelPort),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, adminServerName + "," + clusterName,
          clusterViewAppPath, domainNamespace);
    }

    List<String> managedServerNames = new ArrayList<String>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }
    
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(domainNamespace, domainUid, adminServerName, t3ChannelPort);
      assertDoesNotThrow(() -> verifyAdminServerRESTAccess("localhost", 
          TRAEFIK_INGRESS_HTTP_HOSTPORT, false, hostHeader));
    }        

    //verify admin server accessibility and the health of cluster members
    verifyMemberHealth(adminServerPodName, managedServerNames, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // Since the ListenPort is not set explicitly on ServerTemplate
    // in wlst offline script wlst-create-domain-onpv.py, the default
    // ListenPort on each manged server is set to 7100. This can be confirmed
    // by intorospecting the corresponding cluster service port (default)
    int servicePort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace,
              domainUid + "-cluster-" + clusterName, "default"),
              "Getting Cluster Service default port failed");
    int managedServerPort;
    if (WEBLOGIC_IMAGE_TAG.contains("12")) {
      managedServerPort = 7100;
    } else {
      managedServerPort = 7001;
    }
    assertEquals(managedServerPort, servicePort, "Default Service Port is not set to " + managedServerPort);
    int napPort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace,
              domainUid + "-cluster-" + clusterName, "ms-nap"),
              "Getting Cluster Service nap port failed");
    assertEquals(7110, napPort, "Nap Service Port is not set to 7110");

    servicePort = assertDoesNotThrow(()
        -> getServicePort(domainNamespace,
              domainUid + "-managed-server1", "default"),
              "Getting Managed Server Service default port failed");
    assertEquals(managedServerPort, servicePort, "Default Managed Service Port is not " + managedServerPort);

  }

  private static void verifyMemberHealth(String adminServerPodName, List<String> managedServerNames,
                                         String user, String code) {
    logger.info("Checking the health of servers in cluster");
    testUntil(() -> {
      if (OKE_CLUSTER) {
        // In internal OKE env, verifyMemberHealth in admin server pod
        int adminPort = 7001;
        final String command = KUBERNETES_CLI + " exec -n "
            + domainNamespace + "  " + adminServerPodName + " -- curl http://"
            + adminServerPodName + ":"
            + adminPort + "/clusterview/ClusterViewServlet"
            + "\"?user=" + user
            + "&password=" + code + "\"";

        ExecResult result = null;
        try {
          result = ExecCommand.exec(command, true);
        } catch (IOException | InterruptedException ex) {
          logger.severe(ex.getMessage());
        }

        String response = result.stdout().trim();
        logger.info(response);
        boolean health = true;
        for (String managedServer : managedServerNames) {
          health = health && response.contains(managedServer + ":HEALTH_OK");
          if (health) {
            logger.info(managedServer + " is healthy");
          } else {
            logger.info(managedServer + " health is not OK or server not found");
          }
        }
        return health;
      } else {
        logger.info("Getting node port for default channel");
        int serviceNodePort = assertDoesNotThrow(()
            -> getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
                "Getting admin server node port failed");
        String host = K8S_NODEPORT_HOST;
        String hostAndPort = host + ":" + serviceNodePort;
        Map<String, String> headers = null;
        if (TestConstants.KIND_CLUSTER
            && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
          hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
          headers = new HashMap<>();
          headers.put("host", hostHeader);
        }
        String url = "http://" + hostAndPort
            + "/clusterview/ClusterViewServlet?user=" + user + "&password=" + code;
        HttpResponse<String> response;
        response = OracleHttpClient.get(url, headers, true);

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
      }
    },
        logger,
        "Verifying the health of all cluster members");
  }
}

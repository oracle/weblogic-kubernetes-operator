// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic.kubernetes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Yaml;
import oracle.verrazzano.weblogic.ApplicationConfiguration;
import oracle.verrazzano.weblogic.ApplicationConfigurationSpec;
import oracle.verrazzano.weblogic.Component;
import oracle.verrazzano.weblogic.ComponentSpec;
import oracle.verrazzano.weblogic.Components;
import oracle.verrazzano.weblogic.Workload;
import oracle.verrazzano.weblogic.WorkloadSpec;
import oracle.verrazzano.weblogic.kubernetes.annotations.VzIntegrationTest;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MII_APP_RESPONSE_V1;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.appAccessibleInPod;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTargetPortForRoute;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test to create model in image domain and verify the domain started successfully

@DisplayName("Test to a create model in image domain and start the domain")
@VzIntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
@Tag("okd-wls-srg")
class ItVzMiiDomain {
  
  private static String domainNamespace = null;
  private String domainUid = "domain1";
  private static LoggingFacade logger = null;
  

  /**
   * Label domain namespace.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(1) List<String> namespaces) {
    logger = getLogger();
    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    domainNamespace = namespaces.get(0);
    Map<String, String> labels1 = new java.util.HashMap<>();
    labels1.put("verrazzano-managed", "true");
    //setLabelToNamespace(domainNamespace, labels1);
  }

  /**
   * Create a WebLogic domain VerrazzanoWebLogicWorkload component in verrazzano.
   */
  @Test
  @DisplayName("Create model in image domain and verify external admin services in verrazzano")
  void testCreateMiiDomain() {

    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-admin-server";
    final String managedServerPrefix = domainUid + "-managed-server";
    final int replicaCount = 2;
    final String hostName = "localhost";
    final int adminServerPort = 7001;
    final int adminServerSecurePort = 7008;

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

    String configMapName = "default-secure-configmap";
    String yamlString = "topology:\n"
        + "  Server:\n"
        + "    'admin-server':\n"
        + "       SSL: \n"
        + "         Enabled: true \n"
        + "         ListenPort: '" + adminServerSecurePort + "' \n";
    createModelConfigMap(configMapName, yamlString, domainUid);

    // create cluster object
    String clusterName = "cluster-1";

    // create the domain object
    DomainResource domain = createDomainResourceWithConfigMap(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG, configMapName);

    domain = createClusterResourceAndAddReferenceToDomain(
        domainUid + "-" + clusterName, clusterName, domainNamespace, domain, replicaCount);

    Component component = new Component()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("Component")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new ComponentSpec()
            .workLoad(new Workload()
                .apiVersion("oam.verrazzano.io/v1alpha1")
                .kind("VerrazzanoWebLogicWorkload")
                .spec(new WorkloadSpec()
                    .template(domain))));
    
    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("version", "v1.0.0");    
    keyValueMap.put("description", "My vz wls application");
    
    ApplicationConfiguration application = new ApplicationConfiguration()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("ApplicationConfiguration")
        .metadata(new V1ObjectMeta()
            .name("myvzdomain")
            .annotations(keyValueMap))
        .spec(new ApplicationConfigurationSpec()
            .components(Arrays.asList(new Components()
                    .componentName(domainUid))));
    
    logger.info(Yaml.dump(component));
    logger.info(Yaml.dump(application));
    
    try {
      TimeUnit.MINUTES.sleep(10);
    } catch (InterruptedException ex) {
      ;
    }
    
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG);
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
    // Need to expose the admin server external service to access the console in OKD cluster only
    // We will create one route for sslport and another for default port
    String adminSvcSslPortExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName),
                    domainNamespace, "domain1-admin-server-sslport-ext");
    setTlsTerminationForRoute("domain1-admin-server-sslport-ext", domainNamespace);
    String adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);

    // check and wait for the application to be accessible in all server pods
    for (int i = 1; i <= replicaCount; i++) {
      checkAppRunning(
          domainNamespace,
          managedServerPrefix + i,
          "8001",
          "sample-war/index.jsp",
          MII_APP_RESPONSE_V1 + i);
    }

    logger.info("All the servers in Domain {0} are running and application is available", domainUid);

    int sslNodePort = getServiceNodePort(
         domainNamespace, getExternalServicePodName(adminServerPodName), "default-secure");
    // In OKD cluster, we need to set the target port of the route to be the ssl port
    // By default, when a service is exposed as a route, the endpoint is set to the default port.
    int sslPort = getServicePort(
         domainNamespace, getExternalServicePodName(adminServerPodName), "default-secure");
    setTargetPortForRoute("domain1-admin-server-sslport-ext", domainNamespace, sslPort);
    assertNotEquals(-1, sslNodePort,
          "Could not get the default-secure external service node port");
    logger.info("Found the administration service nodePort {0}", sslNodePort);
    String hostAndPort = getHostAndPort(adminSvcSslPortExtHost, sslNodePort);
    if (!WEBLOGIC_SLIM) {
      String curlCmd = "curl -sk --show-error --noproxy '*' "
          + " https://" + hostAndPort
          + "/console/login/LoginForm.jsp --write-out %{http_code} -o /dev/null";
      logger.info("Executing default-admin nodeport curl command {0}", curlCmd);
      assertTrue(callWebAppAndWaitTillReady(curlCmd, 10));
      logger.info("WebLogic console is accessible thru default-secure service");
    } else {
      logger.info("Skipping WebLogic console in WebLogic slim image");
    }

    int nodePort = getServiceNodePort(
           domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, nodePort,
          "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);

    if (!WEBLOGIC_SLIM) {
      String curlCmd2 = "curl -s --show-error --noproxy '*' "
          + " http://" + hostAndPort
          + "/console/login/LoginForm.jsp --write-out %{http_code} -o /dev/null";
      logger.info("Executing default nodeport curl command {0}", curlCmd2);
      assertTrue(callWebAppAndWaitTillReady(curlCmd2, 5));
      logger.info("WebLogic console is accessible thru default service");
    } else {
      logger.info("Checking Rest API management console in WebLogic slim image");
      verifyCredentials(adminSvcExtHost, adminServerPodName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, true);
    }
  }

  // Create a domain resource with a custom ConfigMap
  private DomainResource createDomainResourceWithConfigMap(String domainUid,
                                                           String domNamespace, String adminSecretName,
                                                           String repoSecretName, String encryptionSecretName,
                                                           String miiImage, String configmapName) {

    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("testkey", "testvalue");

    // create the domain CR
    DomainResource domain = new DomainResource()
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
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
                        .channelName("default-secure")
                        .nodePort(getNextFreePort()))
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);
    return domain;
  }

  private void checkAppRunning(
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {

    // check if the application is accessible inside of a server pod using standard retry policy
    checkAppIsRunning(withStandardRetryPolicy, namespace, podName, internalPort, appPath, expectedStr);
  }

  private void checkAppIsRunning(
      ConditionFactory conditionFactory,
      String namespace,
      String podName,
      String internalPort,
      String appPath,
      String expectedStr
  ) {

    // check if the application is accessible inside of a server pod
    testUntil(conditionFactory,
        () -> appAccessibleInPod(namespace, podName, internalPort, appPath, expectedStr),
        logger,
        "application {0} is running on pod {1} in namespace {2}",
        appPath,
        podName,
        namespace);
  }

  // create a ConfigMap with a model that enable SSL on the Administration server
  private static void createModelConfigMap(String configMapName, String model, String domainUid) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    Map<String, String> data = new HashMap<>();
    data.put("model.ssl.yaml", model);

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

  private static void setLabelToNamespace(String domainNS, Map<String, String> labels) {
    //add label to domain namespace
    V1Namespace namespaceObject1 = assertDoesNotThrow(() -> Kubernetes.getNamespace(domainNS));
    assertNotNull(namespaceObject1, "Can't find namespace with name " + domainNS);
    namespaceObject1.getMetadata().setLabels(labels);
    assertDoesNotThrow(() -> Kubernetes.replaceNamespace(namespaceObject1));
  }  
}

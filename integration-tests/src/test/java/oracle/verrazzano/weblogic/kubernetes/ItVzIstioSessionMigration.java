// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Yaml;
import oracle.verrazzano.weblogic.ApplicationConfiguration;
import oracle.verrazzano.weblogic.ApplicationConfigurationSpec;
import oracle.verrazzano.weblogic.Component;
import oracle.verrazzano.weblogic.ComponentSpec;
import oracle.verrazzano.weblogic.Components;
import oracle.verrazzano.weblogic.Destination;
import oracle.verrazzano.weblogic.IngressRule;
import oracle.verrazzano.weblogic.IngressTrait;
import oracle.verrazzano.weblogic.IngressTraitSpec;
import oracle.verrazzano.weblogic.IngressTraits;
import oracle.verrazzano.weblogic.Path;
import oracle.verrazzano.weblogic.Workload;
import oracle.verrazzano.weblogic.WorkloadSpec;
import oracle.verrazzano.weblogic.kubernetes.annotations.VzIntegrationTest;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createApplication;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createComponent;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.generateNewModelFileWithUpdatedDomainUid;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createIstioDomainResource;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.getOrigModelFile;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.getServerAndSessionInfoAndVerify;
import static oracle.weblogic.kubernetes.utils.SessionMigrationUtil.shutdownServerAndVerify;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.getIstioHost;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.getLoadbalancerAddress;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.setLabelToNamespace;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.verifyVzApplicationAccess;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test WLS Session Migration when istio is enabled")
@VzIntegrationTest
@Tag("v8o")
class ItVzIstioSessionMigration {

  private static String domainNamespace = null;

  // constants for creating domain image using model in image
  private static final String SESSMIGR_IMAGE_NAME = "istio-sessmigr-mii-image";

  // constants for web service
  private static final String SESSMIGR_APP_NAME = "sessmigr-app";
  private static final String SESSMIGR_APP_WAR_NAME = "sessmigr-war";
  private static final int SESSION_STATE = 4;
  private static Map<String, String> httpAttrMap;

  // constants for operator and WebLogic domain
  private static String domainUid = "istio-sessmigr-domain";
  private static String clusterName = "cluster-1";
  private static String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static int managedServerPort = 7100;
  private static String finalPrimaryServerName = null;
  private static String configMapName = "istio-configmap";
  private static int replicaCount = 2;
  private static DomainResource domain;
  

  private static LoggingFacade logger = null;

  private static Map<String, Quantity> resourceRequest = new HashMap<>();
  private static Map<String, Quantity> resourceLimit = new HashMap<>();

  /**
   * Build custom image using model in image with model files
   * and create a verrazzano application with a dynamic cluster.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(1) List<String> namespaces) {
    logger = getLogger();

    logger.info("Assign unique namespace for Domain");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    domainNamespace = namespaces.get(0);
    assertDoesNotThrow(() -> setLabelToNamespace(Arrays.asList(domainNamespace)));

    // Generate the model.sessmigr.yaml file at RESULTS_ROOT
    String destSessionMigrYamlFile =
        generateNewModelFileWithUpdatedDomainUid(domainUid, "ItVzIstioSessionMigration", getOrigModelFile());

    List<String> appList = new ArrayList<>();
    appList.add(SESSMIGR_APP_NAME);

    // build the model file list
    final List<String> modelList = Collections.singletonList(destSessionMigrYamlFile);

    // create image with model files
    logger.info("Create image with model file and verify");
    String miiImage = createMiiImageAndVerify(SESSMIGR_IMAGE_NAME, modelList, appList);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    // set resource request and limit
    resourceRequest.put("cpu", new Quantity("250m"));
    resourceRequest.put("memory", new Quantity("768Mi"));
    resourceLimit.put("cpu", new Quantity("2"));
    resourceLimit.put("memory", new Quantity("2Gi"));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));
    
    domain = createDomainCrAndVerify(adminSecretName, encryptionSecretName, miiImage);
    createVzApplication();    

    // map to save HTTP response data
    httpAttrMap = new HashMap<String, String>();
    httpAttrMap.put("sessioncreatetime", "(.*)sessioncreatetime>(.*)</sessioncreatetime(.*)");
    httpAttrMap.put("sessionid", "(.*)sessionid>(.*)</sessionid(.*)");
    httpAttrMap.put("primary", "(.*)primary>(.*)</primary(.*)");
    httpAttrMap.put("secondary", "(.*)secondary>(.*)</secondary(.*)");
    httpAttrMap.put("count", "(.*)countattribute>(.*)</countattribute(.*)");
  }

  /**
   * In an istio enabled Environment, test sends a HTTP request to set http session state(count number),
   * get the primary and secondary server name, session create time and session state and from the util method
   * and save HTTP session info, then stop the primary server by changing ServerStartPolicy to Never and
   * patching domain. Send another HTTP request to get http session state (count number), primary server
   * and session create time. Verify that a new primary server is selected and HTTP session state is migrated.
   */
  @Test
  @DisplayName("Verify session migration in an istio enabled environment")
  void testSessionMigrationIstioEnabled() {
    final String primaryServerAttr = "primary";
    final String secondaryServerAttr = "secondary";
    final String sessionCreateTimeAttr = "sessioncreatetime";
    final String countAttr = "count";
    final String webServiceSetUrl = SESSMIGR_APP_WAR_NAME + "/?setCounter=" + SESSION_STATE;
    final String webServiceGetUrl = SESSMIGR_APP_WAR_NAME + "/?getCounter";
    final String clusterAddress = domainUid + "-cluster-" + clusterName;
    String serverName = managedServerPrefix + "1";

    // send a HTTP request to set http session state(count number) and save HTTP session info
    // before shutting down the primary server
    Map<String, String> httpDataInfo = getServerAndSessionInfoAndVerify(domainNamespace,
        adminServerPodName, serverName, clusterAddress, managedServerPort, webServiceSetUrl, " -c ");

    // get server and session info from web service deployed on the cluster
    String origPrimaryServerName = httpDataInfo.get(primaryServerAttr);
    String origSecondaryServerName = httpDataInfo.get(secondaryServerAttr);
    String origSessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    logger.info("Got the primary server {0}, the secondary server {1} "
        + "and session create time {2} before shutting down the primary server.",
        origPrimaryServerName, origSecondaryServerName, origSessionCreateTime);

    // stop the primary server by changing ServerStartPolicy to Never and patching domain
    logger.info("Shut down the primary server {0}", origPrimaryServerName);
    shutdownServerAndVerify(domainUid, domainNamespace, origPrimaryServerName);

    // send a HTTP request to get server and session info after shutting down the primary server
    serverName = domainUid + "-" + origSecondaryServerName;
    httpDataInfo = getServerAndSessionInfoAndVerify(domainNamespace, adminServerPodName,
        serverName, clusterAddress, managedServerPort, webServiceGetUrl, " -b ");

    // get server and session info from web service deployed on the cluster
    String primaryServerName = httpDataInfo.get(primaryServerAttr);
    String sessionCreateTime = httpDataInfo.get(sessionCreateTimeAttr);
    String countStr = httpDataInfo.get(countAttr);
    int count;
    if (countStr.equalsIgnoreCase("null")) {
      count = managedServerPort;
    } else {
      count = Optional.ofNullable(countStr).map(Integer::valueOf).orElse(managedServerPort);
    }
    logger.info("After patching the domain, the primary server changes to {0} "
        + ", session create time {1} and session state {2}",
        primaryServerName, sessionCreateTime, countStr);

    // verify that a new primary server is picked and HTTP session state is migrated
    assertAll("Check that WebLogic server and session vars is not null or empty",
        () -> assertNotEquals(origPrimaryServerName, primaryServerName,
            "After the primary server stopped, another server should become the new primary server"),
        () -> assertEquals(origSessionCreateTime, sessionCreateTime,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server"),
        () -> assertEquals(SESSION_STATE, count,
            "After the primary server stopped, HTTP session state should be migrated to the new primary server")
    );

    finalPrimaryServerName = primaryServerName;

    logger.info("Done testSessionMigration \nThe new primary server is {0}, it was {1}. "
        + "\nThe session state was set to {2}, it is migrated to the new primary server.",
        primaryServerName, origPrimaryServerName, SESSION_STATE);
  }
  
  private static void createVzApplication() {

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
            .name("myvzsessiondomain")
            .namespace(domainNamespace)
            .annotations(keyValueMap))
        .spec(new ApplicationConfigurationSpec()
            .components(Arrays.asList(new Components()
                .componentName(domainUid)
                .traits(Arrays.asList(new IngressTraits()
                    .trait(new IngressTrait()
                        .apiVersion("oam.verrazzano.io/v1alpha1")
                        .kind("IngressTrait")
                        .metadata(new V1ObjectMeta()
                            .name("mydomain-ingress")
                            .namespace(domainNamespace))
                        .spec(new IngressTraitSpec()
                            .ingressRules(Arrays.asList(
                                new IngressRule()
                                    .paths(Arrays.asList(new Path()
                                        .path("/console")
                                        .pathType("Prefix")))
                                    .destination(new Destination()
                                        .host(adminServerPodName)
                                        .port(7001)),
                                new IngressRule()
                                    .paths(Arrays.asList(new Path()
                                        .path("/sessmigr-app")
                                        .pathType("Prefix")))
                                    .destination(new Destination()
                                        .host(domainUid + "-cluster-" + clusterName)
                                        .port(managedServerPort)))))))))));

    logger.info(Yaml.dump(component));
    logger.info(Yaml.dump(application));

    logger.info("Deploying components");
    assertDoesNotThrow(() -> createComponent(component));
    logger.info("Deploying application");
    assertDoesNotThrow(() -> createApplication(application));

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

    // get istio gateway host and loadbalancer address
    String host = getIstioHost(domainNamespace);
    String address = getLoadbalancerAddress();

    // verify WebLogic console page is accessible through istio/loadbalancer
    String message = "Oracle WebLogic Server Administration Console";
    String consoleUrl = "https://" + host + "/console/login/LoginForm.jsp --resolve " + host + ":443:" + address;
    assertTrue(verifyVzApplicationAccess(consoleUrl, message), "Failed to get WebLogic administration console");
  }

  private static DomainResource createDomainCrAndVerify(String adminSecretName,
      String encryptionSecretName,
      String miiImage) {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create WDT config map without any files
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Collections.emptyList());

    // create the domain object
    DomainResource domain = createIstioDomainResource(domainUid,
        domainNamespace,
        adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        encryptionSecretName,
        replicaCount,
        miiImage,
        configMapName,
        clusterName);
    return domain;
  }
  
}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ManagedServer;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.Shutdown;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is to verify shutdown rules when shutdown properties are defined at different levels
 * (domain, cluster, adminServer and managedServer level).
 */
@DisplayName("Verify shutdown rules when shutdown properties are defined at different levels")
@IntegrationTest
class ItPodsShutdownOption {

  private static String domainNamespace = null;

  // domain constants
  private static String domainUid = "domain1";
  private static String clusterName = "cluster-1";
  private static int replicaCount = 2;
  private static String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

  private static String indManagedServerName1 = "ms-1";
  private static String indManagedServerPodName1 = domainUid + "-" + indManagedServerName1;
  private static String indManagedServerName2 = "ms-2";
  private static String indManagedServerPodName2 = domainUid + "-" + indManagedServerName2;

  private static LoggingFacade logger = null;

  private static String miiImage;
  private static String adminSecretName;
  private static String encryptionSecretName;
  private static String cmName = "configuredcluster";

  /**
   * 1. Get namespaces for operator and WebLogic domain.
   * 2. Push MII image to registry.
   * 3. Create WebLogic credential secret and encryption secret.
   * 4. Create configmap for independent managed server additions.
   *
   * @param namespaces list of namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // get the pre-built image created by IntegrationTestWatcher
    miiImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;

    // create docker registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating docker registry secret in namespace {0}", domainNamespace);
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace, "weblogic", "welcome1");

    // create encryption secret
    logger.info("Creating encryption secret");
    encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace, "weblogicenc", "weblogicenc");

    String yamlString = "topology:\n"
        + "  Server:\n"
        + "    'ms-1':\n"
        + "      ListenPort: '10001'\n"
        + "    'ms-2':\n"
        + "      ListenPort: '9001'\n";

    createModelConfigMap(cmName, yamlString);

  }

  /**
   * Delete the domain created by each test for the next test to start over.
   */
  @AfterEach
  public void afterEach() {
    logger.info("Deleting the domain resource");
    TestActions.deleteDomainCustomResource(domainUid, domainNamespace);
    checkPodDoesNotExist(adminServerPodName, domainUid, domainNamespace);
    checkPodDoesNotExist(managedServerPodNamePrefix + 1, domainUid, domainNamespace);
    checkPodDoesNotExist(managedServerPodNamePrefix + 2, domainUid, domainNamespace);
    checkPodDoesNotExist(indManagedServerPodName1, domainUid, domainNamespace);
    checkPodDoesNotExist(indManagedServerPodName2, domainUid, domainNamespace);
  }

  /**
   * Add shutdown options for servers at all levels: domain, admin server, cluster and managed server levels.
   * Verify individual specific level options takes precedence.
   *
   *<p>Domain level shutdown option which is applicable for all servers in the domain
   *      shutdownType - Forced , timeoutSeconds - 30 secs, ignoreSessions - true
   * Admin server level shutdown option applicable only to the admin server
   *      shutdownType - Forced , timeoutSeconds - 40 secs, ignoreSessions - true
   * Cluster level shutdown option applicable only to the clustered instances
   *      shutdownType - Graceful , timeoutSeconds - 60 secs, ignoreSessions - false
   * Managed server server level shutdown option applicable only to the independent managed servers
   *      shutdownType - Forced , timeoutSeconds - 45 secs, ignoreSessions - true
   *
   *<p>Since the shutdown options are provided at all levels the domain level shutdown options has no effect on the
   * admin server, cluster, or managed server options. All of those entities use their own shutdown options.
   *
   *<p>When server pods starts up the server.out log will show the options applied to the servers. The test verifies
   * the logs and determine the outcome of the test.
   *
   *<p>This use case shows how to add shutdown options at domain and how to override them at server level.
   */
  @Test
  @DisplayName("Verify shutdown rules when shutdown properties are defined at different levels ")
  public void testShutdownPropsAllLevels() {


    // create Shutdown objects for each server and cluster
    Shutdown[] shutDownObjects = new Shutdown[5];
    Shutdown dom = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(30L);
    Shutdown admin = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(40L);
    Shutdown cluster = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(60L);
    Shutdown ms1 = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(120L);
    Shutdown ms2 = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(45L);
    shutDownObjects[0] = dom;
    shutDownObjects[1] = admin;
    shutDownObjects[2] = cluster;
    shutDownObjects[3] = ms1;
    shutDownObjects[4] = ms2;
    // create domain custom resource and verify all the pods came up
    Domain domain = buildDomainResource(shutDownObjects);
    createVerifyDomain(domain);

    // get pod logs each server which contains server.out file logs and verify values set above are present in the log
    verifyServerLog(domainNamespace, adminServerPodName,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Forced", "SHUTDOWN_TIMEOUT=40"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60"});
    verifyServerLog(domainNamespace, indManagedServerPodName1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=120"});
    verifyServerLog(domainNamespace, indManagedServerPodName2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Forced", "SHUTDOWN_TIMEOUT=45"});
  }

  /**
   * Add shutdown options for servers at all levels: domain, admin server, cluster and managed server levels
   * and override all those options with a domain level ENV variables. Verify the domain level options takes precedence.
   *
   *<p>Domain level shutdown option which is applicable for all servers in the domain
   *      shutdownType - Forced , timeoutSeconds - 30 secs, ignoreSessions - true
   * Admin server level shutdown option applicable only to the admin server
   *      shutdownType - Forced , timeoutSeconds - 40 secs, ignoreSessions - true
   * Cluster level shutdown option applicable only to the clustered instances
   *      shutdownType - Graceful , timeoutSeconds - 60 secs, ignoreSessions - false
   * Managed server server level shutdown option applicable only to the independent managed servers
   *      shutdownType - Forced , timeoutSeconds - 45 secs, ignoreSessions - true
   *
   *<p>After creating the above options override the shutdownType for all servers using a ENV level value - Forced
   * Now shutdownType for all servers are overridden with Forced as the shutdown option but rest of the properties
   * are applied as set in the individual server levels.
   *
   *<p>When server pods starts up the server.out log will show the shutdown options applied to the servers.
   * The test verifies the logs and determine the outcome of the test.
   *
   *<p>This use case shows how to add shutdown options at domain and how to override them using ENV variable.
   */
  @Test
  @DisplayName("Verify shutdown rules when shutdown properties are defined at different levels ")
  public void testShutdownPropsEnvOverride() {


    // create Shutdown objects for each server and cluster
    Shutdown[] shutDownObjects = new Shutdown[5];
    Shutdown dom = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(30L);
    Shutdown admin = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(40L);
    Shutdown cluster = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(60L);
    Shutdown ms1 = new Shutdown().ignoreSessions(Boolean.FALSE).shutdownType("Graceful").timeoutSeconds(120L);
    Shutdown ms2 = new Shutdown().ignoreSessions(Boolean.TRUE).shutdownType("Forced").timeoutSeconds(45L);
    shutDownObjects[0] = dom;
    shutDownObjects[1] = admin;
    shutDownObjects[2] = cluster;
    shutDownObjects[3] = ms1;
    shutDownObjects[4] = ms2;
    // create domain custom resource and verify all the pods came up
    Domain domain = buildDomainResource(shutDownObjects);
    domain.spec().serverPod()
        .addEnvItem(new V1EnvVar()
            .name("SHUTDOWN_TYPE")
            .value("Graceful"));
    createVerifyDomain(domain);

    // get pod logs each server which contains server.out file logs and verify values set above are present in the log
    // except shutdowntype rest of the values should match with abobe shutdown object values
    verifyServerLog(domainNamespace, adminServerPodName,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=40"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60"});
    verifyServerLog(domainNamespace, managedServerPodNamePrefix + 2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=60"});
    verifyServerLog(domainNamespace, indManagedServerPodName1,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=false", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=120"});
    verifyServerLog(domainNamespace, indManagedServerPodName2,
        new String[]{"SHUTDOWN_IGNORE_SESSIONS=true", "SHUTDOWN_TYPE=Graceful", "SHUTDOWN_TIMEOUT=45"});
  }


  // create custom domain resource with different shutdownobject values for adminserver/cluster/independent ms
  private Domain buildDomainResource(Shutdown[] shutDownObject) {
    logger.info("Creating domain custom resource");
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
                .name(OCIR_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .shutdown(shutDownObject[0])
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .serverPod(new ServerPod()
                    .shutdown(shutDownObject[1])))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")
                )
            .configuration(new Configuration()
                .model(new Model()
                    .configMap(cmName)
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionSecretName)))
            .addManagedServersItem(new ManagedServer()
                .serverStartState("RUNNING")
                .serverStartPolicy("ALWAYS")
                .serverName(indManagedServerName1)
                .serverPod(new ServerPod()
                    .shutdown(shutDownObject[3])))
            .addManagedServersItem(new ManagedServer()
                .serverStartState("RUNNING")
                .serverStartPolicy("ALWAYS")
                .serverName(indManagedServerName2)
                .serverPod(new ServerPod()
                    .shutdown(shutDownObject[4]))));
    setPodAntiAffinity(domain);
    domain.getSpec().getClusters().stream().forEach(cluster ->
        cluster
            .getServerPod()
            .shutdown(shutDownObject[2]));
    return domain;
  }

  // create domain resource and verify all the server pods are ready
  private void createVerifyDomain(Domain domain) {
    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using docker image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    logger.info("Checking that admin service/pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodNamePrefix + i;

      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

    // check for independent managed server pods existence in the domain namespace
    for (String podName : new String[]{indManagedServerPodName1, indManagedServerPodName2}) {
      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that independent ms service/pod {0} exists in namespace {1}",
          podName, domainNamespace);
      checkPodReadyAndServiceExists(podName, domainUid, domainNamespace);
    }

  }


  // get pod log which includes the server.out logs and verify the messages contain the set shutdown properties
  private void verifyServerLog(String namespace, String podName, String[] envVars) {
    String podLog = assertDoesNotThrow(() -> TestActions.getPodLog(podName, namespace));
    for (String envVar : envVars) {
      logger.info("Checking Pod {0} for server startup property {1}", podName, envVar);
      assertTrue(podLog.contains(envVar), "Server log doesn't contain the " + envVar);
      logger.info("Pod {0} contains the property {1} in server startup env", podName, envVar);
    }
  }

  // Crate a ConfigMap with a model to add a 2 independent managed servers
  private static void createModelConfigMap(String configMapName, String model) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    Map<String, String> data = new HashMap<>();
    data.put("independent-ms.yaml", model);

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

}


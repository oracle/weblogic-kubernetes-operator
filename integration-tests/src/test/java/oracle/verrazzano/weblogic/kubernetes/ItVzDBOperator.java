// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.verrazzano.weblogic.kubernetes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
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
import oracle.verrazzano.weblogic.Workload;
import oracle.verrazzano.weblogic.WorkloadSpec;
import oracle.verrazzano.weblogic.kubernetes.annotations.VzIntegrationTest;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.FmwUtils;
import oracle.weblogic.kubernetes.utils.VerrazzanoUtils;
import org.junit.jupiter.api.AfterAll;
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
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ENCRYPION_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.actions.TestActions.patchClusterCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createApplication;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.createComponent;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDatabaseSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createJobToChangePermissionsOnPvHostPath;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runClientInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.runJavacInsidePod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.startPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.stopPortForwardProcess;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.deleteOracleDB;
import static oracle.weblogic.kubernetes.utils.DbUtils.installDBOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.uninstallDBOperator;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFileToPod;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createMiiImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainResourceServerStartPolicy;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.setLabelToNamespace;
import static oracle.weblogic.kubernetes.utils.VerrazzanoUtils.verifyVzApplicationAccess;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests to create FMW model in image domain and WebLogic domain using Oracle
 * database created using Oracle Database Operator.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Test to create FMW model in image domain and WebLogic domain using Oracle "
    + "database created using Oracle Database Operator")
@VzIntegrationTest
@Tag("v8o")
class ItVzDBOperator {

  private static String dbNamespace = null;
  private static String opNamespace = null;
  private static String fmwDomainNamespace = null;
  private static String wlsDomainNamespace = null;
  private static String fmwMiiImage = null;

  private static final String RCUSCHEMAPREFIX = "FMWDOMAINMII";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String modelFile = "model-singleclusterdomain-sampleapp-jrf.yaml";

  private static String dbUrl = null;
  private static String dbName = "my-oracle-sidb";
  private static LoggingFacade logger = null;

  private String fmwDomainUid = "fmwdomain-mii-db";
  private String fmwAdminServerPodName = fmwDomainUid + "-admin-server";
  private String fmwManagedServerPrefix = fmwDomainUid + "-managed-server";
  private int replicaCount = 2;
  private String clusterName = "cluster-1";
  private String fmwAminSecretName = fmwDomainUid + "-weblogic-credentials";
  private String fmwEncryptionSecretName = fmwDomainUid + "-encryptionsecret";
  private String rcuaccessSecretName = fmwDomainUid + "-rcu-access";
  private String opsswalletpassSecretName = fmwDomainUid + "-opss-wallet-password-secret";
  private String opsswalletfileSecretName = fmwDomainUid + "opss-wallet-file-secret";
  private String adminSvcExtHost = null;

  private static final String wlsDomainUid = "mii-jms-recovery-db";
  private static final String pvName = getUniqueName(wlsDomainUid + "-pv-");
  private static final String pvcName = getUniqueName(wlsDomainUid + "-pvc-");
  private static final String wlsAdminServerPodName = wlsDomainUid + "-admin-server";
  private static final String wlsManagedServerPrefix = wlsDomainUid + "-managed-server";
  private static String cpUrl;
  private static String adminSvcExtRouteHost = null;

  private final Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
  private final Path domainLifecycleSamplePath = Paths.get(samplePath + "/scripts/domain-lifecycle");
  private final String fmwClusterResName = fmwDomainUid + "-" + clusterName;
  private final String wlsClusterResName = wlsDomainUid + "-" + clusterName;
  
  static final String forwardHostName = "localhost";
  final int adminServerPort = 7001;
  static String forwardedPortNo;

  /**
   * Start DB service and create RCU schema.
   * Assigns unique namespaces for operator and domains.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster. Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) throws ApiException {

    logger = getLogger();
    logger.info("Assign a unique namespace for DB and RCU");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);

    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    fmwDomainNamespace = namespaces.get(1);

    logger.info("Assign a unique namespace for WLS domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    wlsDomainNamespace = namespaces.get(2);
    
    // create PV, PVC for logs/data
    createPV(pvName, wlsDomainUid, ItVzDBOperator.class.getSimpleName());
    createPVC(pvName, pvcName, wlsDomainUid, wlsDomainNamespace);
    // create job to change permissions on PV hostPath
    createJobToChangePermissionsOnPvHostPath(pvName, pvcName, wlsDomainNamespace);    
    
    setLabelToNamespace(Arrays.asList(wlsDomainNamespace, fmwDomainNamespace));    

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(fmwDomainNamespace);
    createBaseRepoSecret(wlsDomainNamespace);

    //install Oracle Database Operator
    assertDoesNotThrow(() -> installDBOperator(dbNamespace), "Failed to install database operator");

    logger.info("Create Oracle DB in namespace: {0} ", dbNamespace);
    dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, RCUSYSPASSWORD, dbNamespace));

    logger.info("Create RCU schema with fmwImage: {0}, rcuSchemaPrefix: {1}, dbUrl: {2}, "
        + " dbNamespace: {3}", FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX, dbUrl, dbNamespace);
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX,
        dbUrl, dbNamespace));
  }

  /**
   * Create a basic FMW model in image domain using the database created by DB Operator. Verify Pod is ready and service
   * exists for both admin server and managed servers. Verify EM console is accessible.
   */
  @Test
  @Order(1)
  @DisplayName("Create FMW Domain model in image")
  void  testFmwModelInImageWithDbOperator() {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(fmwDomainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(fmwAminSecretName,
        fmwDomainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", fmwAminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(fmwEncryptionSecretName,
        fmwDomainNamespace,
        ENCRYPION_USERNAME_DEFAULT,
        ENCRYPION_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", fmwEncryptionSecretName));

    // create RCU access secret
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuaccessSecretName, RCUSCHEMAPREFIX, dbUrl, RCUSCHEMAPASSWORD);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuaccessSecretName,
        fmwDomainNamespace,
        RCUSCHEMAPREFIX,
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuaccessSecretName));

    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        fmwDomainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    logger.info("Create an image with jrf model file");
    final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);
    fmwMiiImage = createMiiImageAndVerify(
        "jrf-mii-image",
        modelList,
        Collections.singletonList(MII_BASIC_APP_NAME),
        FMWINFRA_IMAGE_NAME,
        FMWINFRA_IMAGE_TAG,
        "JRF",
        false);

    // push the image to a registry to make it accessible in multi-node cluster
    imageRepoLoginAndPushImageToRegistry(fmwMiiImage);

    // create the domain object
    DomainResource domain = FmwUtils.createDomainResource(fmwDomainUid,
        fmwDomainNamespace,
        fmwAminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        fmwEncryptionSecretName,
        rcuaccessSecretName,
        opsswalletpassSecretName,
        fmwMiiImage);
    
    // create cluster object
    ClusterResource cluster = createClusterResource(fmwClusterResName,
        clusterName, fmwDomainNamespace, replicaCount);
    logger.info("Creating cluster resource {0} in namespace {1}", fmwClusterResName, fmwDomainNamespace);
    createClusterAndVerify(cluster);
    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(fmwClusterResName));
    
    Component component = new Component()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("Component")
        .metadata(new V1ObjectMeta()
            .name(fmwDomainUid)
            .namespace(fmwDomainNamespace))
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
            .name("myvzdbdomain")
            .namespace(fmwDomainNamespace)
            .annotations(keyValueMap))
        .spec(new ApplicationConfigurationSpec()
            .components(Arrays.asList(
                new Components()
                    .componentName(fmwDomainUid)
                    .traits(Arrays.asList(new IngressTraits()
                        .trait(new IngressTrait()
                            .apiVersion("oam.verrazzano.io/v1alpha1")
                            .kind("IngressTrait")
                            .metadata(new V1ObjectMeta()
                                .name("mydbdomain-ingress")
                                .namespace(fmwDomainNamespace))
                            .spec(new IngressTraitSpec()
                                .ingressRules(Arrays.asList(
                                    new IngressRule()
                                        .paths(Arrays.asList(new oracle.verrazzano.weblogic.Path()
                                            .path("/console")
                                            .pathType("Prefix")))
                                        .destination(new Destination()
                                            .host(fmwAdminServerPodName)
                                            .port(7001)),
                                    new IngressRule()
                                        .paths(Arrays.asList(new oracle.verrazzano.weblogic.Path()
                                            .path("/em")
                                            .pathType("Prefix")))
                                        .destination(new Destination()
                                            .host(fmwAdminServerPodName)
                                            .port(7001)))))))))));

    logger.info("Deploying components");
    assertDoesNotThrow(() -> createComponent(component));
    logger.info("Deploying application");
    assertDoesNotThrow(() -> createApplication(application));    

    verifyDomainReady(fmwDomainNamespace, fmwDomainUid, replicaCount);
    forwardedPortNo = startPortForwardProcess(forwardHostName, fmwDomainNamespace, fmwDomainUid, adminServerPort);
    verifyEMconsoleAccess("/em", "200");
    //Reuse the same RCU schema to restart JRF domain
    testReuseRCUschemaToRestartDomain();
    
    stopPortForwardProcess(fmwDomainNamespace);

  }

  /**
   * Create WebLogic domain using model in image and Oracle database used for JMS and JTA migration and service logs.
   */
  @Test
  @Order(2)
  void testWlsModelInImageWithDbOperator() {

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(wlsDomainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, wlsDomainNamespace),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, ENCRYPION_USERNAME_DEFAULT,
        ENCRYPION_PASSWORD_DEFAULT, wlsDomainNamespace),
        String.format("createSecret failed for %s", encryptionSecretName));

    logger.info("Create database secret");
    final String dbSecretName = wlsDomainUid + "-db-secret";

    cpUrl = "jdbc:oracle:thin:@//" + dbUrl;
    logger.info("ConnectionPool URL = {0}", cpUrl);
    assertDoesNotThrow(() -> createDatabaseSecret(dbSecretName,
        "sys as sysdba", "Oradoc_db1", cpUrl, wlsDomainNamespace),
        String.format("createSecret failed for %s", dbSecretName));
    
    String configMapName = "jdbc-jms-recovery-configmap";
    String configmapcomponentname = "comp-" + configMapName;
    VerrazzanoUtils.createVzConfigmapComponent(configmapcomponentname, configMapName, wlsDomainNamespace,
        wlsDomainUid, Arrays.asList(MODEL_DIR + "/jms.recovery.yaml"));

    // create the domain CR with a pre-defined configmap
    DomainResource domainCR = createDomainResource(wlsDomainUid, wlsDomainNamespace,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminSecretName, TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName, replicaCount,
        configMapName, dbSecretName, false, true);
    domainCR.spec().configuration().model().setConfigMap(configMapName);

    // create cluster object
    ClusterResource cluster = createClusterResource(wlsClusterResName, clusterName, wlsDomainNamespace, replicaCount);
    logger.info("Creating cluster resource {0} in namespace {1}", wlsClusterResName, wlsDomainNamespace);
    createClusterAndVerify(cluster);
    // set cluster references
    domainCR.getSpec().withCluster(new V1LocalObjectReference().name(wlsClusterResName));

    Component component = new Component()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("Component")
        .metadata(new V1ObjectMeta()
            .name(wlsDomainUid)
            .namespace(wlsDomainNamespace))
        .spec(new ComponentSpec()
            .workLoad(new Workload()
                .apiVersion("oam.verrazzano.io/v1alpha1")
                .kind("VerrazzanoWebLogicWorkload")
                .spec(new WorkloadSpec()
                    .template(domainCR))));

    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("version", "v1.0.0");
    keyValueMap.put("description", "My vz wls db application");

    ApplicationConfiguration application = new ApplicationConfiguration()
        .apiVersion("core.oam.dev/v1alpha2")
        .kind("ApplicationConfiguration")
        .metadata(new V1ObjectMeta()
            .name("myvzwlsdbdomain")
            .namespace(wlsDomainNamespace)
            .annotations(keyValueMap))
        .spec(new ApplicationConfigurationSpec()
            .components(Arrays.asList(
                new Components()
                    .componentName(wlsDomainUid)
                    .traits(Arrays.asList(new IngressTraits()
                        .trait(new IngressTrait()
                            .apiVersion("oam.verrazzano.io/v1alpha1")
                            .kind("IngressTrait")
                            .metadata(new V1ObjectMeta()
                                .name("mywlsdbdomain-ingress")
                                .namespace(wlsDomainNamespace))
                            .spec(new IngressTraitSpec()
                                .ingressRules(Arrays.asList(
                                    new IngressRule()
                                        .paths(Arrays.asList(
                                            new oracle.verrazzano.weblogic.Path()
                                                .path("/management")
                                                .pathType("Prefix"),
                                            new oracle.verrazzano.weblogic.Path()
                                                .path("/console")
                                                .pathType("Prefix"),
                                            new oracle.verrazzano.weblogic.Path()
                                                .path("/em")
                                                .pathType("Prefix")))
                                        .destination(new Destination()
                                            .host(wlsAdminServerPodName)
                                            .port(7001)))))))),
                new Components()
                    .componentName(configmapcomponentname))));

    logger.info("Deploying components");
    assertDoesNotThrow(() -> createComponent(component));
    logger.info("Deploying application");
    assertDoesNotThrow(() -> createApplication(application));

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", wlsDomainNamespace);
    testUntil(domainExists(wlsDomainUid, DOMAIN_VERSION, wlsDomainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        wlsDomainUid,
        wlsDomainNamespace);

    checkPodReadyAndServiceExists(wlsAdminServerPodName, wlsDomainUid, wlsDomainNamespace);
    // create the required leasing table 'ACTIVE' before we start the cluster
    createLeasingTable(wlsAdminServerPodName, wlsDomainNamespace, dbUrl);    

    String managedServerPrefix = wlsDomainUid + "-managed-server";
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerName = managedServerPrefix + i;
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerName, wlsDomainNamespace);
      checkPodReadyAndServiceExists(managedServerName, wlsDomainUid, wlsDomainNamespace);
    }
    
    forwardedPortNo = startPortForwardProcess(forwardHostName, wlsDomainNamespace, wlsDomainUid, adminServerPort);

    // verify WebLogic console page is accessible through istio/loadbalancer
    String message = "Oracle WebLogic Server Administration Console";
    String consoleUrl = "http://" + forwardHostName + ":" + forwardedPortNo + "/console/login/LoginForm.jsp";
    assertTrue(verifyVzApplicationAccess(consoleUrl, message), "Failed to get WebLogic administration console");

    //Verify JMS/JTA Service migration with File(JDBC) Store
    testMiiJmsJtaServiceMigration();
    
    stopPortForwardProcess(wlsDomainNamespace);
  }

  /**
   * Save the OPSS key wallet from a running JRF domain's introspector configmap to a file. Restore the OPSS key wallet
   * file to a Kubernetes secret. Shutdown the domain. Using the same RCU schema to restart the same JRF domain with
   * restored OPSS key wallet file secret. Verify Pod is ready and service exists for both admin server and managed
   * servers. Verify EM console is accessible.
   */
  private void testReuseRCUschemaToRestartDomain() {
    saveAndRestoreOpssWalletfileSecret(fmwDomainNamespace, fmwDomainUid, opsswalletfileSecretName);
    shutdownDomain();
    patchDomainWithWalletFileSecret(opsswalletfileSecretName);
    startupDomain();
    verifyDomainReady(fmwDomainNamespace, fmwDomainUid, replicaCount);
  }

  /**
   * Verify JMS/JTA Service is migrated to an available active server.
   */
  private void testMiiJmsJtaServiceMigration() {
    
    // build the standalone JMS Client on Admin pod
    String destLocation = "/u01/JmsSendReceiveClient.java";
    assertDoesNotThrow(() -> copyFileToPod(wlsDomainNamespace,
        wlsAdminServerPodName, "weblogic-server",
        Paths.get(RESOURCE_DIR, "jms", "JmsSendReceiveClient.java"),
        Paths.get(destLocation)));
    runJavacInsidePod(wlsAdminServerPodName, wlsDomainNamespace, destLocation);

    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server2"),
        "ClusterJmsServer@managed-server2 is not on managed-server2 before migration");

    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server2"),
        "JdbcJmsServer@managed-server2 is not on managed-server2 before migration");

    assertTrue(checkJtaRecoveryServiceRuntime("managed-server2",
        "managed-server2", "true"),
        "JTARecoveryService@managed-server2 is not on managed-server2 before migration");

    // Send persistent messages to both FileStore and JDBCStore based
    // JMS Destination (Queue)
    runJmsClientOnAdminPod("send",
        "ClusterJmsServer@managed-server2@jms.testUniformQueue");
    runJmsClientOnAdminPod("send",
        "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");

    // Scale down the cluster to repilca count of 1, this will shutdown
    // the managed server managed-server2 in the cluster to trigger
    // JMS/JTA Service Migration.
    boolean psuccess = scaleCluster(wlsClusterResName, wlsDomainNamespace, 1);
    assertTrue(psuccess,
        String.format("Cluster replica patching failed for domain %s in namespace %s",
            wlsDomainUid, wlsDomainNamespace));
    checkPodDoesNotExist(wlsManagedServerPrefix + "2", wlsDomainUid, wlsDomainNamespace);

    // Make sure the ClusterJmsServer@managed-server2 and
    // JdbcJmsServer@managed-server2 are migrated to managed-server1
    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server1"),
        "ClusterJmsServer@managed-server2 is NOT migrated to managed-server1");
    logger.info("ClusterJmsServer@managed-server2 is migrated to managed-server1");

    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server1"),
        "JdbcJmsServer@managed-server2 is NOT migrated to managed-server1");
    logger.info("JdbcJmsServer@managed-server2 is migrated to managed-server1");

    assertTrue(checkStoreRuntime("ClusterFileStore@managed-server2",
        "managed-server1"),
        "ClusterFileStore@managed-server2 is NOT migrated to managed-server1");
    logger.info("ClusterFileStore@managed-server2 is migrated to managed-server1");

    assertTrue(checkStoreRuntime("ClusterJdbcStore@managed-server2",
        "managed-server1"),
        "JdbcStore@managed-server2 is NOT migrated to managed-server1");
    logger.info("JdbcStore@managed-server2 is migrated to managed-server1");

    assertTrue(checkJtaRecoveryServiceRuntime("managed-server1",
        "managed-server2", "true"), "JTA RecoveryService@managed-server2 is not migrated to managed-server1");
    logger.info("JTA RecoveryService@managed-server2 is migrated to managed-server1");

    runJmsClientOnAdminPod("receive",
        "ClusterJmsServer@managed-server2@jms.testUniformQueue");
    runJmsClientOnAdminPod("receive",
        "JdbcJmsServer@managed-server2@jms.jdbcUniformQueue");

    // Restart the managed server(2) to make sure the JTA Recovery Service is
    // migrated back to original hosting server
    startManagedServer();
    assertTrue(checkJtaRecoveryServiceRuntime("managed-server2",
        "managed-server2", "true"),
        "JTARecoveryService@managed-server2 is not on managed-server2 after restart");
    logger.info("JTA RecoveryService@managed-server2 is migrated back to managed-server1");
    assertTrue(checkJtaRecoveryServiceRuntime("managed-server1",
        "managed-server2", "false"),
        "JTARecoveryService@managed-server2 is not deactivated on managed-server1 after restart");
    logger.info("JTA RecoveryService@managed-server2 is deactivated on managed-server1 after restart");

    assertTrue(checkStoreRuntime("ClusterFileStore@managed-server2",
        "managed-server2"),
        "FileStore@managed-server2 is NOT migrated back to managed-server2");
    logger.info("FileStore@managed-server2 is migrated back to managed-server2");
    assertTrue(checkStoreRuntime("ClusterJdbcStore@managed-server2",
        "managed-server2"),
        "JdbcStore@managed-server2 is NOT migrated back to managed-server2");
    logger.info("JdbcStore@managed-server2 is migrated back to managed-server2");

    assertTrue(checkJmsServerRuntime("ClusterJmsServer@managed-server2",
        "managed-server2"),
        "ClusterJmsServer@managed-server2 is NOT migrated back to to managed-server2");
    logger.info("ClusterJmsServer@managed-server2 is migrated back to managed-server2");
    assertTrue(checkJmsServerRuntime("JdbcJmsServer@managed-server2",
        "managed-server2"),
        "JdbcJmsServer@managed-server2 is NOT migrated back to managed-server2");
    logger.info("JdbcJmsServer@managed-server2 is migrated back to managed-server2");
  }

  /**
   * Uninstall DB operator and delete DB instance.
   * The cleanup framework does not uninstall DB operator, delete DB instance and storageclass.
   * Deletes Oracle database instance, operator and storageclass.
   */
  @AfterAll
  public void tearDownAll() throws ApiException {
    if (!SKIP_CLEANUP) {
      deleteOracleDB(dbNamespace, dbName);
      uninstallDBOperator(dbNamespace);
      Kubernetes.deletePv(pvName);
    }
  }

  // Restart the managed-server
  private void startManagedServer() {
    String patchStr
        = "["
        + "{\"op\": \"replace\", \"path\": \"/spec/replicas\", \"value\": 2}"
        + "]";
    V1Patch patch = new V1Patch(patchStr);
    logger.info("Patching cluster resource using patch string {0} ", patchStr);

    assertTrue(patchClusterCustomResource(wlsClusterResName, wlsDomainNamespace,
        patch, V1Patch.PATCH_FORMAT_JSON_PATCH), "Failed to patch cluster");
    checkPodReadyAndServiceExists(wlsManagedServerPrefix + "2", wlsDomainUid, wlsDomainNamespace);
  }

  // Run standalone JMS Client to send/receive message from
  // Distributed Destination Member
  private void runJmsClientOnAdminPod(String action, String queue) {
    testUntil(
        runClientInsidePod(wlsAdminServerPodName, wlsDomainNamespace,
            "/u01", "JmsSendReceiveClient",
            "t3://" + wlsDomainUid + "-cluster-cluster-1:8001", action, queue, "100"),
        logger,
        "Wait for JMS Client to send/recv msg");
  }

  /*
   * Verify the JMS Server Runtime through REST API.
   * Get the specific JMSServer Runtime on specified managed server.
   * @param jmsServer name of the JMSServerRuntime to look for
   * @param managedServer name of the managed server to look for JMSServerRuntime
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkJmsServerRuntime(String jmsServer, String managedServer) {

    ExecResult result = null;
    StringBuffer curlString = new StringBuffer("status=$(curl -sk --user "
        + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT + " ");
    curlString.append("http://" + forwardHostName + ":" + forwardedPortNo)
        .append("/management/weblogic/latest/domainRuntime/serverRuntimes/")
        .append(managedServer)
        .append("/JMSRuntime/JMSServers/")
        .append(jmsServer)        
        .append(" --silent --show-error ")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkJmsServerRuntime: curl command {0}", new String(curlString));
    testUntil(
        assertDoesNotThrow(() -> () -> exec(curlString.toString(), true).stdout().contains("200")),
        logger,
        "JMS Server Service to migrate");
    return true;
  }

  /*
   * Verify the Persistent Store Runtimes through REST API.
   * Get the specific Persistent Store Runtime on specified managed server.
   * @param storeName name of the PersistentStore Runtime to look for
   * @param managedServer name of the managed server to look for StoreRuntime
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkStoreRuntime(String storeName, String managedServer) {
   
    ExecResult result = null;
    StringBuffer curlString = new StringBuffer("status=$(curl -sk --user "
        + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT + " ");
    curlString.append("http://" + forwardHostName + ":" + forwardedPortNo)
        .append("/management/weblogic/latest/domainRuntime/serverRuntimes/")
        .append(managedServer)
        .append("/persistentStoreRuntimes/")
        .append(storeName)        
        .append(" --silent --show-error ")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkStoreRuntime: curl command {0}", new String(curlString));
    testUntil(
        assertDoesNotThrow(() -> () -> exec(curlString.toString(), true).stdout().contains("200")),
        logger,
        "PersistentStoreRuntimes Service to migrate");
    return true;
  }

  /*
   * Verify the JTA Recovery Service Runtime through REST API.
   * Get the JTA Recovery Service Runtime for a server on a
   * specified managed server.
   * @param managedServer name of the server to look for RecoveyServerRuntime
   * @param recoveryService name of RecoveyServerRuntime (managed server)
   * @param active is the recovery active (true or false )
   * @returns true if MBean is found otherwise false
   **/
  private boolean checkJtaRecoveryServiceRuntime(String managedServer, String recoveryService, String active) {
    
    ExecResult result = null;
    StringBuffer curlString = new StringBuffer("curl -sk --user "
        + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT + " ");
    curlString.append("\"http://" + forwardHostName + ":" + forwardedPortNo)
        .append("/management/weblogic/latest/domainRuntime/serverRuntimes/")
        .append(managedServer)
        .append("/JTARuntime/recoveryRuntimeMBeans/")
        .append(recoveryService)
        .append("?fields=active&links=none\"")
        .append(" --show-error ");
    logger.info("checkJtaRecoveryServiceRuntime: curl command {0}", new String(curlString));
    testUntil(
        assertDoesNotThrow(() -> () -> exec(curlString.toString(), true)
        .stdout().contains("{\"active\": " + active + "}")),
        logger,
        "JTA Recovery Service to migrate");
    return true;
  }

  /**
   * Create leasing Table (ACTIVE) on an Oracle DB Instance. Uses the WebLogic utility utils.Schema to add the table So
   * the command MUST be run inside a Weblogic Server pod.
   *
   * @param wlPodName the pod name
   * @param namespace the namespace in which WebLogic pod exists
   * @param dbUrl Oracle database url
   */
  public static void createLeasingTable(String wlPodName, String namespace, String dbUrl) {
    Path ddlFile = Paths.get(WORK_DIR + "/leasing.ddl");
    String ddlString = "DROP TABLE ACTIVE;\n"
        + "CREATE TABLE ACTIVE (\n"
        + "  SERVER VARCHAR2(255) NOT NULL,\n"
        + "  INSTANCE VARCHAR2(255) NOT NULL,\n"
        + "  DOMAINNAME VARCHAR2(255) NOT NULL,\n"
        + "  CLUSTERNAME VARCHAR2(255) NOT NULL,\n"
        + "  TIMEOUT DATE,\n"
        + "  PRIMARY KEY (SERVER, DOMAINNAME, CLUSTERNAME)\n"
        + ");\n";

    assertDoesNotThrow(() -> Files.write(ddlFile, ddlString.getBytes()));
    String destLocation = "/u01/leasing.ddl";
    assertDoesNotThrow(() -> copyFileToPod(namespace,
        wlPodName, "weblogic-server",
        Paths.get(WORK_DIR, "leasing.ddl"),
        Paths.get(destLocation)));
    
    String cpUrl = "jdbc:oracle:thin:@//" + dbUrl;
    String jarLocation = "/u01/oracle/wlserver/server/lib/weblogic.jar";
    StringBuffer ecmd = new StringBuffer("java -cp ");
    ecmd.append(jarLocation);
    ecmd.append(" utils.Schema ");
    ecmd.append(cpUrl);
    ecmd.append(" oracle.jdbc.OracleDriver");
    ecmd.append(" -verbose ");
    ecmd.append(" -u \"sys as sysdba\"");
    ecmd.append(" -p Oradoc_db1");
    ecmd.append(" /u01/leasing.ddl");
    ExecResult execResult = assertDoesNotThrow(() -> execCommand(namespace, wlPodName,
        "weblogic-server", true, "/bin/sh", "-c", ecmd.toString()));
    assertEquals(0, execResult.exitValue(), "Could not create the Leasing Table");
  }

  /**
   * Save the OPSS key wallet from a running JRF domain's introspector configmap to a file.
   *
   * @param namespace namespace where JRF domain exists
   * @param domainUid unique domain Uid
   * @param walletfileSecretName name of wallet file secret
   */
  private void saveAndRestoreOpssWalletfileSecret(String namespace, String domainUid,
      String walletfileSecretName) {
    Path saveAndRestoreOpssPath
        = Paths.get(RESOURCE_DIR, "bash-scripts", "opss-wallet.sh");
    String script = saveAndRestoreOpssPath.toString();
    logger.info("Script for saveAndRestoreOpss is {0)", script);

    //save opss wallet file
    String command1 = script + " -d " + domainUid + " -n " + namespace + " -s";
    logger.info("Save wallet file command: {0}", command1);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command1)
            .saveResults(true)
            .redirect(true))
        .execute());

    //restore opss wallet password secret
    String command2 = script + " -d " + domainUid + " -n " + namespace + " -r" + " -ws " + walletfileSecretName;
    logger.info("Restore wallet file command: {0}", command2);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command2)
            .saveResults(true)
            .redirect(true))
        .execute());

  }

  /**
   * Shutdown the domain by setting serverStartPolicy as "Never".
   */
  private void shutdownDomain() {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "Never", fmwDomainNamespace, fmwDomainUid);
    logger.info("Domain is patched to stop entire WebLogic domain");

    // make sure all the server pods are removed after patch
    checkPodDeleted(fmwAdminServerPodName, fmwDomainUid, fmwDomainNamespace);
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDeleted(fmwManagedServerPrefix + i, fmwDomainUid, fmwDomainNamespace);
    }

    logger.info("Domain shutdown success");

  }

  /**
   * Startup the domain by setting serverStartPolicy as "IfNeeded".
   */
  private void startupDomain() {
    patchDomainResourceServerStartPolicy("/spec/serverStartPolicy", "IfNeeded", fmwDomainNamespace, fmwDomainUid);
    logger.info("Domain is patched to start all servers in the domain");
  }

  /**
   * Patch the domain with opss wallet file secret.
   *
   * @param opssWalletFileSecretName the name of opps wallet file secret
   * @return true if patching succeeds, false otherwise
   */
  private boolean patchDomainWithWalletFileSecret(String opssWalletFileSecretName) {
    // construct the patch string for adding server pod resources
    StringBuffer patchStr = new StringBuffer("[{")
        .append("\"op\": \"add\", ")
        .append("\"path\": \"/spec/configuration/opss/walletFileSecret\", ")
        .append("\"value\": \"")
        .append(opssWalletFileSecretName)
        .append("\"}]");

    logger.info("Adding opssWalletPasswordSecretName for domain {0} in namespace {1} using patch string: {2}",
        fmwDomainUid, fmwDomainNamespace, patchStr.toString());

    V1Patch patch = new V1Patch(new String(patchStr));

    return patchDomainCustomResource(fmwDomainUid, fmwDomainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH);
  }

  private static boolean verifyEMconsoleAccess(String uri, String expectedStatusCode) {

    StringBuffer curlString = new StringBuffer("status=$(curl -sk --user ");
    curlString.append(ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT)
        .append(" http://" + forwardHostName + ":" + forwardedPortNo)
        .append(uri)
        .append(" --silent --show-error ")
        .append(" -o /dev/null ")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("check em console: curl command {0}", new String(curlString));
    return Command
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedStatusCode);
  }
  
  private static DomainResource createDomainResource(
          String domainResourceName,
          String domNamespace,
          String imageName,
          String adminSecretName,
          String repoSecretName,
          String encryptionSecretName,
          int replicaCount,
          String configMapName,
          String dbSecretName,
          boolean onlineUpdateEnabled,
          boolean setDataHome) {
    LoggingFacade logger = getLogger();

    List<String> securityList = new ArrayList<>();
    if (dbSecretName != null) {
      securityList.add(dbSecretName);
    }
    
    String uniquePath = "/shared/" + domNamespace + "/" + domainResourceName;
    DomainSpec domainSpec = new DomainSpec()
        .domainUid(domainResourceName)
        .domainHomeSourceType("FromModel")
        .image(imageName)
        .imagePullPolicy(IMAGE_PULL_POLICY)
        .addImagePullSecretsItem(new V1LocalObjectReference()
            .name(repoSecretName))
        .webLogicCredentialsSecret(new V1LocalObjectReference()
            .name(adminSecretName))
        .includeServerOutInPodLog(true)
        .logHomeEnabled(Boolean.TRUE)
        .replicas(replicaCount)
        .serverStartPolicy("IfNeeded")
        .serverPod(new ServerPod()
            .addEnvItem(new V1EnvVar()
                .name("JAVA_OPTIONS")
                .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
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
        .adminServer(new AdminServer()
            .adminService(new AdminService()
                .addChannelsItem(new Channel()
                    .channelName("default")
                    .nodePort(0))))
        .configuration(new Configuration()
            .secrets(securityList)
            .model(new Model()
                .domainType("WLS")
                .configMap(configMapName)
                .runtimeEncryptionSecret(encryptionSecretName)
                .onlineUpdate(new OnlineUpdate()
                    .enabled(onlineUpdateEnabled)))
            .introspectorJobActiveDeadlineSeconds(300L));

    if (setDataHome) {
      domainSpec.dataHome(uniquePath + "/data");
    }
    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainResourceName)
            .namespace(domNamespace))
        .spec(domainSpec);

    setPodAntiAffinity(domain);
    return domain;
  }
  
}

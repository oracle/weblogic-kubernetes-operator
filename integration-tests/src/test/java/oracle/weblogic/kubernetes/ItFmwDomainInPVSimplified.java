// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
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
import oracle.weblogic.domain.InitializeDomainOnPV;
import oracle.weblogic.domain.Opss;
import oracle.weblogic.domain.PersistentVolume;
import oracle.weblogic.domain.PersistentVolumeClaim;
import oracle.weblogic.domain.PersistentVolumeClaimSpec;
import oracle.weblogic.domain.PersistentVolumeSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.assertions.impl.Cluster;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.CLUSTER_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.deleteClusterCustomResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.addSccToDBSvcAccount;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVHostPathDir;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test to create a FMW domain in persistent volume with new simplified feature.
 */
@DisplayName("Test to create a FMW domain in persistent volume with new simplified feature")
@IntegrationTest
@Tag("kind-parallel")
class ItFmwDomainInPVSimplified {

  private static String domainNamespace = null;
  private static String dbNamespace = null;

  private static final String RCUSCHEMAPREFIX = "fmwdomainpv";
  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String storageClassName = "fmw-domain-storage-class";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;
  private static String DOMAINHOMEPREFIX = null;
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;

  private final String fmwModelFilePrefix = "model-fmwdomain-onpv-simplified";
  private final String wlsModelFilePrefix = "model-wlsdomain-onpv-simplified";

  /**
   * Assigns unique namespaces for DB, operator and domain.
   * Start DB service.
   * Pull FMW image and Oracle DB image if running tests in Kind cluster.
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique dbNamespace
    logger.info("Assign a unique namespace for DB");
    assertNotNull(namespaces.get(0), "Namespace is null");
    dbNamespace = namespaces.get(0);
    final int dbListenerPort = getNextFreePort();
    dbUrl = ORACLEDBURLPREFIX + dbNamespace + ".svc.cluster.local:" + dbListenerPort + "/devpdb.k8s";

    // get a new unique opNamespace
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(1), "Namespace is null");
    String opNamespace = namespaces.get(1);

    // get a new unique domainNamespace
    logger.info("Assign a unique namespace for FMW domain");
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace = namespaces.get(2);

    DOMAINHOMEPREFIX = "/shared/" + domainNamespace + "/domains/";

    // start DB
    logger.info("Start DB in namespace: {0}, dbListenerPort: {1}, dbUrl: {2}, dbImage: {3}",
        dbNamespace, dbListenerPort, dbUrl, DB_IMAGE_TO_USE_IN_SPEC);
    assertDoesNotThrow(() -> setupDB(DB_IMAGE_TO_USE_IN_SPEC, dbNamespace, getNextFreePort(), dbListenerPort),
        String.format("Failed to setup DB in the namespace %s with dbUrl %s, dbListenerPost %s",
            dbNamespace, dbUrl, dbListenerPort));

    // install operator and verify its running in ready state
    HelmParams opHelmParams =
        new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(opNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    installAndVerifyOperator(opNamespace, opNamespace + "-sa", false,
        0, opHelmParams, ELASTICSEARCH_HOST, false, true, null,
        null, false, "INFO", "DomainOnPvSimplification=true", false, domainNamespace);

    // create pull secrets for domainNamespace when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
  }

  /**
   * Create a basic FMW domain on PV using simplified feature.
   * Operator will create PV/PVC/RCU/Domain.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisplayName("Create a FMW domain on PV using simplified feature, Operator creates PV/PVC/RCU/Domain")
  void testOperatorCreatesPvPvcRcuDomain() {
    String domainUid = "jrfonpv-simplified";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String fmwModelFile = fmwModelFilePrefix + ".yaml";

    // create FMW domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile("jrfonpv-simplified1", RCUSCHEMAPREFIX + "1");

    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_REPO + "jrf-domain-on-pv-image";
    // create image with model and wdt installation files
    WitParams witParams =
        new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(MII_BASIC_IMAGE_TAG)
            .modelFiles(Collections.singletonList(MODEL_DIR + "/" + fmwModelFile))
            .modelVariableFiles(Collections.singletonList(fmwModelPropFile.getAbsolutePath()));
    createAndPushAuxiliaryImage(domainCreationImageName, MII_BASIC_IMAGE_TAG, witParams);

    DomainCreationImage domainCreationImage =
        new DomainCreationImage().image(domainCreationImageName + ":" + MII_BASIC_IMAGE_TAG);

    // create opss wallet password secret
    String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    // create a domain resource
    logger.info("Creating domain custom resource");
    Map<String, Quantity> pvCapacity = new HashMap<>();
    pvCapacity.put("storage", new Quantity("2Gi"));

    Map<String, Quantity> pvcRequest = new HashMap<>();
    pvcRequest.put("storage", new Quantity("2Gi"));

    Configuration configuration = new Configuration()
        .initializeDomainOnPV(new InitializeDomainOnPV()
            .persistentVolume(new PersistentVolume()
                .metadata(new V1ObjectMeta()
                    .name(pvName))
                .spec(new PersistentVolumeSpec()
                    .storageClassName(storageClassName)
                    .capacity(pvCapacity)
                    .persistentVolumeReclaimPolicy("Retain")
                    .hostPath(new V1HostPathVolumeSource()
                        .path(getHostPath(pvName, this.getClass().getSimpleName())))))
            .persistentVolumeClaim(new PersistentVolumeClaim()
                .metadata(new V1ObjectMeta()
                    .name(pvcName))
                .spec(new PersistentVolumeClaimSpec()
                    .storageClassName(storageClassName)
                    .resources(new V1ResourceRequirements()
                        .requests(pvcRequest))))
            .domain(new DomainOnPV()
                .createMode(CreateIfNotExists.DOMAIN_AND_RCU)
                .domainCreationImages(Collections.singletonList(domainCreationImage))
                .domainType(DomainOnPVType.JRF)
                .opss(new Opss()
                    .walletPasswordSecret(opsswalletpassSecretName))));

    DomainResource domain = createDomainResourceOnPv(
        domainUid,
        domainNamespace,
        wlSecretName,
        clusterName,
        pvName,
        pvcName,
        DOMAINHOMEPREFIX,
        replicaCount,
        t3ChannelPort,
        configuration);

    // Set the inter-pod anti-affinity for the domain custom resource
    setPodAntiAffinity(domain);

    // create a domain custom resource and verify domain is created
    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid);
    // delete the cluster
    deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName,  domainNamespace);
  }

  /**
   * Create a basic FMW domain on PV using simplified feature.
   * User creates PV/PVC, operator creates RCU and domain
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisplayName("Create a FMW domainon on PV. User creates PV/PVC and operator creates RCU and domain")
  void testUserCreatesPvPvcOperatorCreatesRcuDomain() {
    String domainUid = "jrfonpv-simplified2";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String fmwModelFile = fmwModelFilePrefix + ".yaml";

    // create FMW domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "2");

    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_REPO + "jrf-domain-on-pv-image2";
    // create image with model and wdt installation files
    WitParams witParams =
        new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(MII_BASIC_IMAGE_TAG)
            .modelFiles(Collections.singletonList(MODEL_DIR + "/" + fmwModelFile))
            .modelVariableFiles(Collections.singletonList(fmwModelPropFile.getAbsolutePath()));
    createAndPushAuxiliaryImage(domainCreationImageName, MII_BASIC_IMAGE_TAG, witParams);

    DomainCreationImage domainCreationImage =
        new DomainCreationImage().image(domainCreationImageName + ":" + MII_BASIC_IMAGE_TAG);

    // create opss wallet password secret
    String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    // create a domain resource
    logger.info("Creating domain custom resource");
    Configuration configuration =
        new Configuration()
            .initializeDomainOnPV(new InitializeDomainOnPV()
                .domain(new DomainOnPV()
                    .createMode(CreateIfNotExists.DOMAIN_AND_RCU)
                    .domainCreationImages(Collections.singletonList(domainCreationImage))
                    .domainType(DomainOnPVType.JRF)
                    .opss(new Opss()
                        .walletPasswordSecret(opsswalletpassSecretName))));

    DomainResource domain = createDomainResourceOnPv(
        domainUid,
        domainNamespace,
        wlSecretName,
        clusterName,
        pvName,
        pvcName,
        DOMAINHOMEPREFIX,
        replicaCount,
        t3ChannelPort,
        configuration);

    // Set the inter-pod anti-affinity for the domain custom resource
    setPodAntiAffinity(domain);

    // create a domain custom resource and verify domain is created
    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid);
    // delete the cluster
    deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName,  domainNamespace);
  }

  /**
   * Create a basic FMW domain on PV using simplified feature.
   * User creates PV/PVC and RCU schema, Operator creates domain
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisplayName("Create a FMW domainon on PV. User creates PV/PVC/RCU and operator creates domain")
  void testUserCreatesPvPvcRcuOperatorCreatesDomain() {
    String domainUid = "jrfonpv-simplified3";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String fmwModelFile = fmwModelFilePrefix + ".yaml";

    // create FMW domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    createPV(pvName, domainUid, this.getClass().getSimpleName());
    createPVC(pvName, pvcName, domainUid, domainNamespace);

    // create RCU schema
    assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "3", dbUrl,
        dbNamespace), "create rcu schema failed");

    // create RCU access secret
    String rcuAccessSecretName = domainUid + "-rcu-credentials";
    logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
        rcuAccessSecretName, RCUSCHEMAPREFIX + "3", RCUSCHEMAPASSWORD, dbUrl);
    assertDoesNotThrow(() -> createRcuAccessSecret(
        rcuAccessSecretName,
        domainNamespace,
        RCUSCHEMAPREFIX + "3",
        RCUSCHEMAPASSWORD,
        dbUrl),
        String.format("createSecret failed for %s", rcuAccessSecretName));

    // create a model property file
    File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "3");

    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_REPO + "jrf-domain-on-pv-image2";
    // create image with model and wdt installation files
    WitParams witParams =
        new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(MII_BASIC_IMAGE_TAG)
            .modelFiles(Collections.singletonList(MODEL_DIR + "/" + fmwModelFile))
            .modelVariableFiles(Collections.singletonList(fmwModelPropFile.getAbsolutePath()));
    createAndPushAuxiliaryImage(domainCreationImageName, MII_BASIC_IMAGE_TAG, witParams);

    DomainCreationImage domainCreationImage =
        new DomainCreationImage().image(domainCreationImageName + ":" + MII_BASIC_IMAGE_TAG);

    // create opss wallet password secret
    String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
    logger.info("Create OPSS wallet password secret");
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    // create a domain resource
    logger.info("Creating domain custom resource");
    Configuration configuration =
        new Configuration()
            .addSecretsItem(rcuAccessSecretName)
            .initializeDomainOnPV(new InitializeDomainOnPV()
                .domain(new DomainOnPV()
                    .createMode(CreateIfNotExists.DOMAIN)
                    .domainCreationImages(Collections.singletonList(domainCreationImage))
                    .domainType(DomainOnPVType.JRF)
                    .opss(new Opss()
                        .walletPasswordSecret(opsswalletpassSecretName))));

    DomainResource domain = createDomainResourceOnPv(
        domainUid,
        domainNamespace,
        wlSecretName,
        clusterName,
        pvName,
        pvcName,
        DOMAINHOMEPREFIX,
        replicaCount,
        t3ChannelPort,
        configuration);

    // Set the inter-pod anti-affinity for the domain custom resource
    setPodAntiAffinity(domain);

    // create a domain custom resource and verify domain is created
    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid);
    // delete the cluster
    deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName,  domainNamespace);
  }

  /**
   * Create a basic WLS domain on PV using simplified feature.
   * Operator will create PV/PVC and WLS Domain.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisplayName("Create a WLS domain on PV using simplified feature, Operator creates PV/PVC and WLS Domain")
  void testOperatorCreatesPvPvcWlsDomain() {
    String domainUid = "wlsonpv-simplified";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String wlsModelFile = wlsModelFilePrefix + ".yaml";

    // create FMW domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create a model property file
    File wlsModelPropFile = createWdtPropertyFile("wlsonpv-simplified1", RCUSCHEMAPREFIX + "1");

    // create domainCreationImage
    String domainCreationImageName = DOMAIN_IMAGES_REPO + "wls-domain-on-pv-image";
    // create image with model and wdt installation files
    WitParams witParams =
        new WitParams()
            .modelImageName(domainCreationImageName)
            .modelImageTag(MII_BASIC_IMAGE_TAG)
            .modelFiles(Collections.singletonList(MODEL_DIR + "/" + wlsModelFile))
            .modelVariableFiles(Collections.singletonList(wlsModelPropFile.getAbsolutePath()));
    createAndPushAuxiliaryImage(domainCreationImageName, MII_BASIC_IMAGE_TAG, witParams);

    DomainCreationImage domainCreationImage =
        new DomainCreationImage().image(domainCreationImageName + ":" + MII_BASIC_IMAGE_TAG);

    // create opss wallet password secret
    logger.info("Create OPSS wallet password secret");
    String opsswalletpassSecretName = domainUid + "-opss-wallet-password-secret";
    assertDoesNotThrow(() -> createOpsswalletpasswordSecret(
        opsswalletpassSecretName,
        domainNamespace,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", opsswalletpassSecretName));

    // create a domain resource
    logger.info("Creating domain custom resource");
    Map<String, Quantity> pvCapacity = new HashMap<>();
    pvCapacity.put("storage", new Quantity("2Gi"));

    Map<String, Quantity> pvcRequest = new HashMap<>();
    pvcRequest.put("storage", new Quantity("2Gi"));

    Configuration configuration = new Configuration()
        .initializeDomainOnPV(new InitializeDomainOnPV()
            .persistentVolume(new PersistentVolume()
                .metadata(new V1ObjectMeta()
                    .name(pvName))
                .spec(new PersistentVolumeSpec()
                    .storageClassName(storageClassName)
                    .capacity(pvCapacity)
                    .persistentVolumeReclaimPolicy("Retain")
                    .hostPath(new V1HostPathVolumeSource()
                        .path(getHostPath(pvName, this.getClass().getSimpleName())))))
            .persistentVolumeClaim(new PersistentVolumeClaim()
                .metadata(new V1ObjectMeta()
                    .name(pvcName))
                .spec(new PersistentVolumeClaimSpec()
                    .storageClassName(storageClassName)
                    .resources(new V1ResourceRequirements()
                        .requests(pvcRequest))))
            .domain(new DomainOnPV()
                .createMode(CreateIfNotExists.DOMAIN)
                .domainCreationImages(Collections.singletonList(domainCreationImage))
                .domainType(DomainOnPVType.WLS)));

    DomainResource domain = createDomainResourceOnPv(
        domainUid,
        domainNamespace,
        wlSecretName,
        clusterName,
        pvName,
        pvcName,
        DOMAINHOMEPREFIX,
        replicaCount,
        t3ChannelPort,
        configuration);

    // Set the inter-pod anti-affinity for the domain custom resource
    setPodAntiAffinity(domain);

    // create a domain custom resource and verify domain is created
    createDomainAndVerify(domain, domainNamespace);

    // verify that all servers are ready
    verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");

    // delete the domain
    deleteDomainResource(domainNamespace, domainUid);
    // delete the cluster
    deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName,  domainNamespace);
  }

  private File createWdtPropertyFile(String domainName, String rcuSchemaPrefix) {

    // create property file used with domain model file
    Properties p = new Properties();
    p.setProperty("rcuDb", dbUrl);
    p.setProperty("rcuSchemaPrefix", rcuSchemaPrefix);
    p.setProperty("rcuSchemaPassword", RCUSCHEMAPASSWORD);
    p.setProperty("rcuSysPassword", RCUSYSPASSWORD);
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainName);

    // create a model property file
    File domainPropertiesFile = assertDoesNotThrow(() ->
        File.createTempFile(fmwModelFilePrefix, ".properties"),
        "Failed to create FMW model properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "FMW properties file"),
        "Failed to write FMW properties file");

    return domainPropertiesFile;
  }

  private DomainResource createDomainResourceOnPv(String domainUid,
                                                  String domNamespace,
                                                  String adminSecretName,
                                                  String clusterName,
                                                  String pvName,
                                                  String pvcName,
                                                  String domainInHomePrefix,
                                                  int replicaCount,
                                                  int t3ChannelPort,
                                                  Configuration configuration) {

    // create a domain custom resource configuration object
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(domainInHomePrefix + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(FMWINFRA_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .imagePullSecrets(Collections.singletonList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME)))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domNamespace + "/logs/" + domainUid)
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

    // create cluster resource for the domain
    String clusterResName  = domainUid + "-" + clusterName;
    if (!Cluster.doesClusterExist(clusterResName, CLUSTER_VERSION, domainNamespace)) {
      ClusterResource cluster = createClusterResource(clusterResName,
          clusterName, domainNamespace, replicaCount);
      createClusterAndVerify(cluster);
    }
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    return domain;
  }

  /**
   * Start Oracle DB instance, create rcu pod and load database schema in the specified namespace.
   *
   * @param dbImage image name of database
   * @param dbNamespace namespace where DB and RCU schema are going to start
   * @param dbPort NodePort of DB
   * @param dbListenerPort TCP listener port of DB
   * @throws ApiException if any error occurs when setting up database
   */
  private static synchronized void setupDB(String dbImage, String dbNamespace, int dbPort, int dbListenerPort)
      throws ApiException {
    LoggingFacade logger = getLogger();
    // create pull secrets when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(dbNamespace);

    if (OKD) {
      addSccToDBSvcAccount("default", dbNamespace);
    }

    logger.info("Start Oracle DB with dbImage: {0}, dbPort: {1}, dbNamespace: {2}, dbListenerPort:{3}",
        dbImage, dbPort, dbNamespace, dbListenerPort);
    startOracleDB(dbImage, dbPort, dbNamespace, dbListenerPort);
  }

  // get the host path for multiple environment
  private String getHostPath(String pvName, String className) {
    Path hostPVPath = createPVHostPathDir(pvName, className);
    return hostPVPath.toString();
  }

}


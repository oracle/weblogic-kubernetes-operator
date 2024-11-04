// Copyright (c) 2023, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.File;
import java.io.FileOutputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.CreateIfNotExists;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainOnPV;
import oracle.weblogic.domain.DomainOnPVType;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.InitializeDomainOnPV;
import oracle.weblogic.domain.Opss;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.ELASTICSEARCH_HOST;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_TEMPFILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolume;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePersistentVolumeClaim;
import static oracle.weblogic.kubernetes.actions.TestActions.deletePod;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePull;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.actions.impl.Domain.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.pvcExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.deleteClusterCustomResourceAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DbUtils.createOracleDBUsingOperator;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuAccessSecret;
import static oracle.weblogic.kubernetes.utils.DbUtils.createRcuSchema;
import static oracle.weblogic.kubernetes.utils.DbUtils.startOracleDB;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainResourceOnPv;
import static oracle.weblogic.kubernetes.utils.DomainUtils.deleteDomainResource;
import static oracle.weblogic.kubernetes.utils.FmwUtils.getConfiguration;
import static oracle.weblogic.kubernetes.utils.FmwUtils.verifyDomainReady;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.changePermissionOnPv;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPV;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVC;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodsWithTimeStamps;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createOpsswalletpasswordSecret;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to create a FMW domain in persistent volume with new simplified feature.
 */
@DisplayName("Test to create a FMW domain in persistent volume with new simplified feature")
@IntegrationTest
@Tag("kind-sequential")
@Tag("oke-weekly-sequential")
@Tag("okd-fmw-cert")
@Tag("olcne-sequential")
class ItFmwDomainOnPV {

  private static String domainNamespace = null;
  private static String dbNamespace = null;

  private static final String ORACLEDBURLPREFIX = "oracledb.";
  private static String ORACLEDBSUFFIX = null;
  private static final String RCUSCHEMAPREFIX = "fmwdomainpv";
  private static final String RCUSYSPASSWORD = "Oradoc_db1";
  private static final String RCUSCHEMAPASSWORD = "Oradoc_db1";
  private static final String storageClassName = "fmw-domain-storage-class";

  private static String dbUrl = null;
  private static LoggingFacade logger = null;
  private static String DOMAINHOMEPREFIX = null;
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;

  private final String fmwModelFilePrefix = "model-fmwdomain-onpv-simplified";

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

    if (OKD) {
      logger.info("Start DB in namespace: {0}, dbListenerPort: {1}, dbUrl: {2}, dbImage: {3}",
          dbNamespace, dbListenerPort, dbUrl, DB_IMAGE_TO_USE_IN_SPEC);
      assertDoesNotThrow(() -> startOracleDB(DB_IMAGE_TO_USE_IN_SPEC, getNextFreePort(), dbNamespace, dbListenerPort),
          String.format("Failed to start Oracle DB in the namespace %s with dbUrl %s, dbListenerPost %s",
              dbNamespace, dbUrl, dbListenerPort));
    } else {
      String dbName = "fmwdomainonpv1" + "my-oracle-db";
      logger.info("Create Oracle DB in namespace: {0} ", dbNamespace);
      createBaseRepoSecret(dbNamespace);
      dbUrl = assertDoesNotThrow(() -> createOracleDBUsingOperator(dbName, RCUSYSPASSWORD, dbNamespace));

    }

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
   * Create a basic FMW domain on PV.
   * Operator will create PV/PVC/RCU/Domain.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @DisplayName("Create a FMW domain on PV using simplified feature, Operator creates PV/PVC/RCU/Domain")
  void testOperatorCreatesPvPvcRcuDomain() {
    String domainUid = "jrfonpv-simplified";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String fmwModelFile = fmwModelFilePrefix + ".yaml";
    try {
      // create FMW domain credential secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace,
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

      // create a model property file
      File fmwModelPropFile = createWdtPropertyFile("jrfonpv-simplified1", RCUSCHEMAPREFIX + "1");

      // create domainCreationImage
      String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "jrf-domain-on-pv-image";
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
      Configuration configuration = null;
      if (OKE_CLUSTER) {
        configuration = getConfiguration(pvcName, pvcRequest, "oci-fss");
      } else {
        configuration = getConfiguration(pvName, pvcName, pvCapacity, pvcRequest, storageClassName,
            this.getClass().getSimpleName());
      }
      configuration.getInitializeDomainOnPV().domain(new DomainOnPV()
          .createMode(CreateIfNotExists.DOMAIN_AND_RCU)
          .domainCreationImages(Collections.singletonList(domainCreationImage))
          .domainType(DomainOnPVType.JRF)
          .opss(new Opss()
              .walletPasswordSecret(opsswalletpassSecretName)));
      DomainResource domain = createDomainResourceOnPv(
          domainUid,
          domainNamespace,
          wlSecretName,
          clusterName,
          pvName,
          pvcName,
          new String[]{BASE_IMAGES_REPO_SECRET_NAME},
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

    } finally {
      // delete the domain
      deleteDomainResource(domainNamespace, domainUid);
      // delete the cluster
      deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName, domainNamespace);
      deletePersistentVolumeClaim(pvcName, domainNamespace);
      deletePersistentVolume(pvName);
    }
  }

  /**
   * Create a basic FMW domain on PV.
   * User creates PV/PVC, operator creates RCU and domain.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Update the base image in the domain spec, verify the domain is rolling-restarted.
   */
  @Test
  @DisplayName("Create a FMW domain on PV. User creates PV/PVC and operator creates RCU and domain")
  void testUserCreatesPvPvcOperatorCreatesRcuDomain() {
    String domainUid = "jrfonpv-simplified2";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String fmwModelFile = fmwModelFilePrefix + ".yaml";
    try {
      // create FMW domain credential secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace,
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

      // create persistent volume and persistent volume claim for domain
      createPV(pvName, domainUid, this.getClass().getSimpleName());
      createPVC(pvName, pvcName, domainUid, domainNamespace);
      String mountPath = "/shared";
      changePermissionOnPv(domainNamespace, pvName, pvcName, mountPath);

      // create a model property file
      File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "2");

      // create domainCreationImage
      String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "jrf-domain-on-pv-image1";
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
          new String[]{BASE_IMAGES_REPO_SECRET_NAME},
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

      verifyRollingRestartWithImageChg(domainUid, domainNamespace);
    } finally {
      // delete the domain
      deleteDomainResource(domainNamespace, domainUid);
      // delete the cluster
      deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName, domainNamespace);
      deletePersistentVolumeClaim(pvcName, domainNamespace);
      deletePersistentVolume(pvName);
    }
  }

  /**
   * Create a basic FMW domain on PV.
   * User creates PV/PVC and RCU schema, Operator creates domain
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisplayName("Create a FMW domain on PV. User creates PV/PVC/RCU and operator creates domain")
  void testUserCreatesPvPvcRcuOperatorCreatesDomain() {
    String domainUid = "jrfonpv-simplified3";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String fmwModelFile = fmwModelFilePrefix + ".yaml";
    try {
      // create FMW domain credential secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace,
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

      // create persistent volume and persistent volume claim for domain
      createPV(pvName, domainUid, this.getClass().getSimpleName());
      createPVC(pvName, pvcName, domainUid, domainNamespace);
      String mountPath = "/shared";
      changePermissionOnPv(domainNamespace, pvName, pvcName, mountPath);
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
      String domainCreationImageName = DOMAIN_IMAGES_PREFIX + "jrf-domain-on-pv-image2";
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
          new String[]{BASE_IMAGES_REPO_SECRET_NAME},
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
    } finally {
      // delete the domain
      deleteDomainResource(domainNamespace, domainUid);
      // delete the cluster
      deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName, domainNamespace);
      //delete the rcu pod
      assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
          "Got exception while deleting server " + "rcu");
      checkPodDoesNotExist("rcu", null, dbNamespace);
      deletePersistentVolumeClaim(pvcName, domainNamespace);
      deletePersistentVolume(pvName);
    }
  }

  /**
   * Create a basic FMW domain on PV.
   * User creates RCU schema, Operator creates PV/PVC and JRF domain
   * The user creates multiple domain initialization images
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @Test
  @DisabledIfEnvironmentVariable(named = "OKD", matches = "true")
  @DisplayName("Create a FMW domain on PV. User creates RCU and operator creates PV/PVC and domain, "
                + "User creates multiple domain initialization images")
  void testUserCreatesRcuOperatorCreatesPvPvcDomainMultipleImages() {
    String domainUid = "jrfonpv-simplified4";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String fmwModelFile = fmwModelFilePrefix + ".yaml";
    try {
      // create FMW domain credential secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace,
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

      // create RCU schema
      assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "4", dbUrl,
          dbNamespace), "create rcu schema failed");

      // create RCU access secret
      String rcuAccessSecretName = domainUid + "-rcu-credentials";
      logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
          rcuAccessSecretName, RCUSCHEMAPREFIX + "4", RCUSCHEMAPASSWORD, dbUrl);
      assertDoesNotThrow(() -> createRcuAccessSecret(
              rcuAccessSecretName,
              domainNamespace,
              RCUSCHEMAPREFIX + "4",
              RCUSCHEMAPASSWORD,
              dbUrl),
          String.format("createSecret failed for %s", rcuAccessSecretName));

      // create a model property file
      File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "4");

      // create domainCreationImage
      String domainCreationImageName1 = DOMAIN_IMAGES_PREFIX + "jrf-domain-on-pv-image3";
      // create image with model and wdt installation files
      WitParams witParams =
          new WitParams()
              .modelImageName(domainCreationImageName1)
              .modelImageTag(MII_BASIC_IMAGE_TAG)
              .modelFiles(Collections.singletonList(MODEL_DIR + "/" + fmwModelFile))
              .modelVariableFiles(Collections.singletonList(fmwModelPropFile.getAbsolutePath()));
      createAndPushAuxiliaryImage(domainCreationImageName1, MII_BASIC_IMAGE_TAG, witParams);

      DomainCreationImage domainCreationImage1 =
          new DomainCreationImage().image(domainCreationImageName1 + ":" + MII_BASIC_IMAGE_TAG);

      // create second image
      String domainCreationImageName2 = DOMAIN_IMAGES_PREFIX + "jrf-domain-on-pv-image4";
      // create image with model and wdt installation files
      witParams =
          new WitParams()
              .modelImageName(domainCreationImageName2)
              .modelImageTag(MII_BASIC_IMAGE_TAG)
              .modelFiles(Collections.singletonList(MODEL_DIR + "/model.jms2.yaml"))
              .modelVariableFiles(Collections.singletonList(fmwModelPropFile.getAbsolutePath()));
      createAndPushAuxiliaryImage(domainCreationImageName2, MII_BASIC_IMAGE_TAG, witParams);

      DomainCreationImage domainCreationImage2 =
          new DomainCreationImage()
              .image(domainCreationImageName2 + ":" + MII_BASIC_IMAGE_TAG)
              .sourceWDTInstallHome("None");

      List<DomainCreationImage> domainCreationImages = new ArrayList<>();
      domainCreationImages.add(domainCreationImage1);
      domainCreationImages.add(domainCreationImage2);

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
      Configuration configuration = null;
      if (OKE_CLUSTER) {
        configuration = getConfiguration(pvcName, pvcRequest, "oci-fss");
      } else {
        configuration = getConfiguration(pvName, pvcName, pvCapacity, pvcRequest,
            storageClassName, this.getClass().getSimpleName());
      }
      configuration.addSecretsItem(rcuAccessSecretName)
          .getInitializeDomainOnPV()
          .domain(new DomainOnPV()
              .createMode(CreateIfNotExists.DOMAIN)
              .domainCreationImages(domainCreationImages)
              .domainType(DomainOnPVType.JRF)
              .opss(new Opss()
                  .walletPasswordSecret(opsswalletpassSecretName)));

      DomainResource domain = createDomainResourceOnPv(
          domainUid,
          domainNamespace,
          wlSecretName,
          clusterName,
          pvName,
          pvcName,
          new String[]{BASE_IMAGES_REPO_SECRET_NAME},
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
    } finally {
      // delete the domain
      deleteDomainResource(domainNamespace, domainUid);
      // delete the cluster
      deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName, domainNamespace);
      //delete the rcu pod
      assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
          "Got exception while deleting server " + "rcu");
      checkPodDoesNotExist("rcu", null, dbNamespace);
      deletePersistentVolumeClaim(pvcName, domainNamespace);
      deletePersistentVolume(pvName);
    }
  }

  /**
   * Create a basic FMW domain on PV.
   * User creates PV and RCU schema, Operator creates PVC and JRF domain
   * Verfiy PVC is created.
   * Verify Pod is ready and service exists for both admin server and managed servers.
   */
  @DisabledIfEnvironmentVariable(named = "OKE_CLUSTER", matches = "true")
  @Test
  @DisplayName("Create a FMW domain on PV. User creates PV and RCU and operator creates PVC and domain")
  void testUserCreatesPvRcuOperatorCreatesPvcDomain() {
    String domainUid = "jrfonpv-simplified5";
    final String pvName = getUniqueName(domainUid + "-pv-");
    final String pvcName = getUniqueName(domainUid + "-pvc-");
    final int t3ChannelPort = getNextFreePort();
    final String wlSecretName = domainUid + "-weblogic-credentials";
    final String fmwModelFile = fmwModelFilePrefix + ".yaml";
    try {
      // create FMW domain credential secret
      createSecretWithUsernamePassword(wlSecretName, domainNamespace,
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

      // create persistent volume for domain
      createPV(pvName, domainUid, this.getClass().getSimpleName());

      // create RCU schema
      assertDoesNotThrow(() -> createRcuSchema(FMWINFRA_IMAGE_TO_USE_IN_SPEC, RCUSCHEMAPREFIX + "5", dbUrl,
          dbNamespace), "create rcu schema failed");

      // create RCU access secret
      String rcuAccessSecretName = domainUid + "-rcu-credentials";
      logger.info("Creating RCU access secret: {0}, with prefix: {1}, dbUrl: {2}, schemapassword: {3})",
          rcuAccessSecretName, RCUSCHEMAPREFIX + "5", RCUSCHEMAPASSWORD, dbUrl);
      assertDoesNotThrow(() -> createRcuAccessSecret(
              rcuAccessSecretName,
              domainNamespace,
              RCUSCHEMAPREFIX + "5",
              RCUSCHEMAPASSWORD,
              dbUrl),
          String.format("createSecret failed for %s", rcuAccessSecretName));

      // create a model property file
      File fmwModelPropFile = createWdtPropertyFile(domainUid, RCUSCHEMAPREFIX + "5");

      // create domainCreationImage
      String domainCreationImageName1 = DOMAIN_IMAGES_PREFIX + "jrf-domain-on-pv-image5";
      // create image with model and wdt installation files
      WitParams witParams =
          new WitParams()
              .modelImageName(domainCreationImageName1)
              .modelImageTag(MII_BASIC_IMAGE_TAG)
              .modelFiles(Collections.singletonList(MODEL_DIR + "/" + fmwModelFile))
              .modelVariableFiles(Collections.singletonList(fmwModelPropFile.getAbsolutePath()));
      createAndPushAuxiliaryImage(domainCreationImageName1, MII_BASIC_IMAGE_TAG, witParams);

      DomainCreationImage domainCreationImage1 =
          new DomainCreationImage().image(domainCreationImageName1 + ":" + MII_BASIC_IMAGE_TAG);

      List<DomainCreationImage> domainCreationImages = new ArrayList<>();
      domainCreationImages.add(domainCreationImage1);

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
      Map<String, Quantity> pvcRequest = new HashMap<>();
      pvcRequest.put("storage", new Quantity("2Gi"));
      Configuration configuration = null;
      if (OKE_CLUSTER) {
        configuration = getConfiguration(pvcName,pvcRequest, "oci-fss");
      } else if (OKD) {
        configuration = getConfiguration(pvcName,pvcRequest, "okd-nfsmnt");
      } else {
        configuration = getConfiguration(pvcName, pvcRequest,"weblogic-domain-storage-class");

      }
      configuration.addSecretsItem(rcuAccessSecretName)
          .getInitializeDomainOnPV()
          .domain(new DomainOnPV()
              .createMode(CreateIfNotExists.DOMAIN)
              .domainCreationImages(domainCreationImages)
              .domainType(DomainOnPVType.JRF)
              .opss(new Opss()
                  .walletPasswordSecret(opsswalletpassSecretName)));

      DomainResource domain = createDomainResourceOnPv(
          domainUid,
          domainNamespace,
          wlSecretName,
          clusterName,
          pvName,
          pvcName,
          new String[]{BASE_IMAGES_REPO_SECRET_NAME},
          DOMAINHOMEPREFIX,
          replicaCount,
          t3ChannelPort,
          configuration);

      // Set the inter-pod anti-affinity for the domain custom resource
      setPodAntiAffinity(domain);

      // create a domain custom resource and verify domain is created
      createDomainAndVerify(domain, domainNamespace);

      // verify PVC is created
      testUntil(
          assertDoesNotThrow(() -> pvcExists(pvcName, domainNamespace),
              String.format("pvcExists failed with ApiException when checking pvc %s in namespace %s",
                  pvcName, domainNamespace)),
          logger,
          "persistent volume claim {0} exists in namespace {1}",
          pvcName,
          domainNamespace);

      // verify that all servers are ready
      verifyDomainReady(domainNamespace, domainUid, replicaCount, "nosuffix");
    } finally {
      // delete the domain
      deleteDomainResource(domainNamespace, domainUid);
      // delete the cluster
      deleteClusterCustomResourceAndVerify(domainUid + "-" + clusterName, domainNamespace);
      //delete the rcu pod
      assertDoesNotThrow(() -> deletePod("rcu", dbNamespace),
          "Got exception while deleting server " + "rcu");
      checkPodDoesNotExist("rcu", null, dbNamespace);
      deletePersistentVolumeClaim(pvcName, domainNamespace);
      deletePersistentVolume(pvName);
    }
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
        File.createTempFile(fmwModelFilePrefix, ".properties", new File(RESULTS_TEMPFILE)),
        "Failed to create FMW model properties file");

    // create the property file
    assertDoesNotThrow(() ->
        p.store(new FileOutputStream(domainPropertiesFile), "FMW properties file"),
        "Failed to write FMW properties file");

    return domainPropertiesFile;
  }

  private Callable<Boolean> tagImageAndPushIfNeeded(String originalImage, String taggedImage) {
    return (() -> {
      boolean result = true;
      imagePull(originalImage);
      result = result && imageTag(originalImage, taggedImage);
      imageRepoLoginAndPushImageToRegistry(taggedImage);
      return result;
    });
  }

  private void verifyRollingRestartWithImageChg(String domainUid, String domainNamespace) {
    // get the map with server pods and their original creation timestamps
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPodNamePrefix = domainUid + "-managed-server";
    Map<String, OffsetDateTime> podsWithTimeStamps = getPodsWithTimeStamps(domainNamespace,
        adminServerPodName, managedServerPodNamePrefix, replicaCount);

    // update the domain with new base image
    int index = FMWINFRA_IMAGE_TO_USE_IN_SPEC.lastIndexOf(":");
    String newImage;
    if (OCNE) {
      newImage = BASE_IMAGES_PREFIX + "fmw-infrastructure1:newtag";
    } else {
      newImage = FMWINFRA_IMAGE_TO_USE_IN_SPEC.substring(0, index) + ":newtag";
    }
    testUntil(
        tagImageAndPushIfNeeded(FMWINFRA_IMAGE_TO_USE_IN_SPEC, newImage),
          logger,
          "tagImageAndPushIfNeeded for image {0} to be successful",
          newImage);

    logger.info("patch the domain resource with new image {0}", newImage);
    String patchStr
          = "["
          + "{\"op\": \"replace\", \"path\": \"/spec/image\", "
          + "\"value\": \"" + newImage + "\"}"
          + "]";
    logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
    V1Patch patch = new V1Patch(patchStr);
    assertTrue(patchDomainCustomResource(domainUid, domainNamespace, patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");

    // verify the server pods are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));
  }
}

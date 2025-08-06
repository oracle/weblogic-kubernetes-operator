// Copyright (c) 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.CommonMiiTestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.LOCALE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createAndPushAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createPushAuxiliaryImageWithDomainConfig;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyConfiguredSystemResource;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test to create model in image domain using auxiliary image. "
    + "Multiple domains are created in the same namespace in this class "
    + "using multiple releases of WebLogic Server.")
@IntegrationTest
@Tag("kind-parallel")
class ItWlsMultiReleaseDomains {
  
  private static String domainNamespace = null;
  private static LoggingFacade logger = null;
  private static String domainUid1 = "domain1";
  private static final String miiAuxiliaryImage1Tag = "image1" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage1Tag;
  private static final String miiAuxiliaryImage2Tag = "image2" + MII_BASIC_IMAGE_TAG;
  private static final String miiAuxiliaryImage2 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage2Tag;
  private static String adminServerPodNameDomain1;
  private static String managedServerPrefixDomain1;
  private static final int replicaCount = 2;
  private static String adminSvcExtHostDomain1 = null;
  private static String adminSecretName = "weblogic-credentials";
  private static String encryptionSecretName = "encryptionsecret";
  private static String opNamespace = null;
  private static AppParams appParams = defaultAppParams()
      .appArchiveDir(ARCHIVE_DIR + ItMiiAuxiliaryImage.class.getSimpleName());

  /**
   * Install Operator. 
   * Create a domain using multiple auxiliary images. 
   * One auxiliary image containing the domain
   * configuration and another auxiliary image with JMS system resource.
   * Verify the domain is running and JMS resource is added.
   *
   * @param namespaces list of namespaces. 
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);
    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
        "weblogicenc", "weblogicenc");

    // build app
    assertTrue(buildAppArchive(appParams
        .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
        .appName(MII_BASIC_APP_NAME)),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

    // image1 with model files for domain config, ds, app and wdt install files
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    modelList.add(MODEL_DIR + "/multi-model-one-ds.20.yaml");
    createPushAuxiliaryImageWithDomainConfig(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage1Tag, archiveList, modelList);

    // image2 with model files for jms config
    modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/model.jms2.yaml");
    WitParams witParams
        = new WitParams()
            .modelImageName(MII_AUXILIARY_IMAGE_NAME)
            .modelImageTag(miiAuxiliaryImage2Tag)
            .wdtModelOnly(true)
            .modelFiles(modelList)
            .wdtVersion("NONE");
    createAndPushAuxiliaryImage(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage2Tag, witParams);    
  }

  /**
   * Create domains using multiple releases of WebLogic.
   * Verify all the pods are started and in ready state.
   * Verify configured JMS and JDBC resources.
   */
  @ParameterizedTest
  @ValueSource(strings = {"12.2.1.4-ol8", "14.1.2.0-generic-jdk17-ol8", "15.1.1.0.0-jdk17"})
  @DisplayName("Test to create domains with different WLS releases")
  void testCreateDomainWithMultipleWLSReleases(String wlsRelease) {
    final String auxiliaryImagePath = "/auxiliary";
    String clusterName = "cluster-1";
    domainUid1 = "domain1";// + wlsRelease.substring(0, 8).replace(".", "");
    adminServerPodNameDomain1 = domainUid1 + "-admin-server";
    managedServerPrefixDomain1 = domainUid1 + "-managed-server";

    // create domain custom resource using 2 auxiliary images
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1} {2}",
        domainUid1, miiAuxiliaryImage1, miiAuxiliaryImage2);
    String wlsImage = LOCALE_IMAGE_NAME + ":" + wlsRelease;
    logger.info(wlsImage);
    DomainResource domainCR = CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage(domainUid1, domainNamespace,
        wlsImage, adminSecretName, createSecretsForImageRepos(domainNamespace),
        encryptionSecretName, auxiliaryImagePath,
        miiAuxiliaryImage1,
        miiAuxiliaryImage2);

    domainCR = createClusterResourceAndAddReferenceToDomain(
        domainUid1 + "-" + clusterName, clusterName, domainNamespace, domainCR, replicaCount);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} {2} in namespace {3}",
        domainUid1, miiAuxiliaryImage1, miiAuxiliaryImage2, domainNamespace);
    createDomainAndVerify(domainUid1, domainCR, domainNamespace,
        adminServerPodNameDomain1, managedServerPrefixDomain1, replicaCount);

    //create router for admin service on OKD
    if (adminSvcExtHostDomain1 == null) {
      adminSvcExtHostDomain1 = createRouteForOKD(getExternalServicePodName(adminServerPodNameDomain1), domainNamespace);
      logger.info("admin svc host = {0}", adminSvcExtHostDomain1);
    }

    // check configuration for JMS
    checkConfiguredJMSresouce(domainNamespace, adminServerPodNameDomain1, adminSvcExtHostDomain1);

    // get the original domain resource before update
    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid1, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
            domainUid1, domainNamespace));
    assertNotNull(domain1, "Got null domain resource");
    assertNotNull(domain1.getSpec(), domain1 + "/spec is null");
    /*
    shutdownDomain(domainUid1, domainNamespace);
    logger.info("Checking for admin server pod shutdown");
    checkPodDoesNotExist(adminServerPodNameDomain1, domainUid1, domainNamespace);
    logger.info("Checking managed server pods were shutdown");
    for (int i = 1; i <= replicaCount; i++) {
      checkPodDoesNotExist(managedServerPrefixDomain1 + i, domainUid1, domainNamespace);
    }
    deleteDomainCustomResource(domainUid1, domainNamespace);
    */
  }

  /**
   * Cleanup images.
   */
  public void tearDownAll() {
    // delete images
    deleteImage(miiAuxiliaryImage1);
    deleteImage(miiAuxiliaryImage2);
  }

  private static void checkConfiguredJMSresouce(String domainNamespace, String adminServerPodName,
      String adminSvcExtHost) {
    verifyConfiguredSystemResource(domainNamespace, adminServerPodName, adminSvcExtHost,
        "JMSSystemResources", "TestClusterJmsModule2", "200");
  }

}

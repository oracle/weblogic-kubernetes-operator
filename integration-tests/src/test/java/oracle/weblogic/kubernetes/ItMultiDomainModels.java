// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_AUXILIARY_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.scaleCluster;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isPodRestarted;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminServerRESTAccess;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminServerRESTAccessInAdminPod;
import static oracle.weblogic.kubernetes.utils.AuxiliaryImageUtils.createPushAuxiliaryImageWithDomainConfig;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResourceAndAddReferenceToDomain;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainResourceWithAuxiliaryImage;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createAndVerifyDomainInImageUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainOnPvUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretsForImageRepos;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The test class creates WebLogic domains with four models.
 * domain-on-pv ( using WDT )
 * domain-in-image ( using WDT )
 * model-in-image
 * domain-with-auxiliary-image
 * Verify the basic lifecycle operations of the WebLogic server pods by scaling the domain.
 * Also verify admin console login using admin node port.
 */
@DisplayName("Verify the basic lifecycle operations of the WebLogic server pods by scaling the clusters in the domain"
    + " with different domain types and verify admin console login using admin node port.")
@IntegrationTest
@Tag("olcne-mrg")
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-srg")
@Tag("oke-arm")
@Tag("oke-parallelnew")
@Tag("gate")
class ItMultiDomainModels {

  // domain constants
  private static final String adminServerName = "admin-server";
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 1;
  private static final String wlSecretName = "weblogic-credentials";
  private static final String miiDomainUid = "miidomain";
  private static final String dimDomainUid = "domaininimage";
  private static final String dpvDomainUid = "domainonpv";
  private static final String wdtModelFileForDomainInImage = "wdt-singlecluster-sampleapp-usingprop-wls.yaml";
  private static final String encryptionSecretName = "encryptionsecret";

  private static LoggingFacade logger = null;
  private static String miiDomainNamespace = null;
  private static String domainOnPVNamespace = null;
  private static String domainInImageNamespace = null;
  private static String auxiliaryImageDomainNamespace = null;
  private static String hostHeader;  

  /**
   * Install operator.
   * Create four different type of domains: model in image, domain on PV and domain in image and
   * domain with auxiliary image.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(5) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get unique namespaces for four different type of domains
    logger.info("Getting unique namespaces for four different type of domains");
    assertNotNull(namespaces.get(1));
    miiDomainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2));
    domainOnPVNamespace = namespaces.get(2);
    assertNotNull(namespaces.get(3));
    domainInImageNamespace = namespaces.get(3);
    assertNotNull(namespaces.get(4));
    auxiliaryImageDomainNamespace = namespaces.get(4);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator
    installAndVerifyOperator(opNamespace, opServiceAccount, false, 0,
        miiDomainNamespace, domainOnPVNamespace, domainInImageNamespace, auxiliaryImageDomainNamespace);
  }

  /**
   * Scale the cluster by patching domain resource for four different type of domains i.e. domain-on-pv,
   * domain-in-image, model-in-image and domain-with-auxiliary-image Also verify admin server access using REST
   * interface.
   *
   * @param domainType domain type, possible value: modelInImage, domainInImage, domainOnPV, auxiliaryImageDomain
   */
  @ParameterizedTest
  @DisplayName("scale cluster by patching domain resource with four different type of domains and "
      + "verify admin server is accessible via REST interface.")
  @ValueSource(strings = {"modelInImage", "domainInImage", "domainOnPV", "auxiliaryImageDomain"})
  void testScaleClustersAndAdminRESTAccess(String domainType) {
    DomainResource domain = createDomainBasedOnDomainType(domainType);

    // get the domain properties
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();

    String dynamicServerPodName = domainUid + "-managed-server1";
    OffsetDateTime dynTs = getPodCreationTime(domainNamespace, dynamicServerPodName);
    final String managedServerPrefix = domainUid + "-managed-server";
    int numberOfServers = 3;
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domainUid, domainNamespace, numberOfServers);
    assertDoesNotThrow(() -> scaleCluster(clusterName,domainNamespace,
        numberOfServers), "Could not scale up the cluster");
    // check managed server pods are ready
    for (int i = 1; i <= numberOfServers; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    Callable<Boolean> isDynRestarted =
        assertDoesNotThrow(() -> isPodRestarted(dynamicServerPodName, domainNamespace, dynTs));
    assertFalse(assertDoesNotThrow(isDynRestarted::call),
        "Dynamic managed server pod must not be restated");

    // then scale cluster back to 1 server
    logger.info("Scaling back cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace,numberOfServers,replicaCount);
    assertDoesNotThrow(() -> scaleCluster(clusterName, domainNamespace,
        replicaCount), "Could not scale down the cluster");

    for (int i = numberOfServers; i > replicaCount; i--) {
      logger.info("Wait for managed server pod {0} to be deleted in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodDeleted(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Validating WebLogic admin server access by REST");
    if (!TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      hostHeader = createIngressHostRouting(domainNamespace, domainUid, adminServerName, 7001);
      assertDoesNotThrow(()
          -> verifyAdminServerRESTAccess("localhost", TRAEFIK_INGRESS_HTTP_HOSTPORT, false, hostHeader));
    } else {
      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      try {
        verifyAdminServerRESTAccessInAdminPod(adminServerPodName, "7001",
            domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
      } catch (IOException ex) {
        logger.severe(ex.getMessage());
      }
    }
    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
  }

  /**
   * Assert the specified domain and domain spec, metadata and clusters not null.
   * @param domain oracle.weblogic.domain.Domain object
   */
  private void assertDomainNotNull(DomainResource domain) {
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), domain + " spec is null");
    assertNotNull(domain.getMetadata(), domain + " metadata is null");
    assertNotNull(domain.getSpec().getClusters(), domain.getSpec() + " getClusters() is null");
  }

  private DomainResource createDomainBasedOnDomainType(String domainType) {
    DomainResource domain;

    if (domainType.equalsIgnoreCase("modelInImage")) {
      domain = createMiiDomainAndVerify(miiDomainNamespace, miiDomainUid,
          MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
          miiDomainUid + "-" + ADMIN_SERVER_NAME_BASE,
          miiDomainUid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount);
    } else if (domainType.equalsIgnoreCase("domainInImage")) {
      List<String> appSrcDirList = new ArrayList<>();
      appSrcDirList.add(MII_BASIC_APP_NAME);
      domain = createAndVerifyDomainInImageUsingWdt(dimDomainUid, domainInImageNamespace,
          wdtModelFileForDomainInImage, appSrcDirList, wlSecretName, clusterName, replicaCount);
    } else if (domainType.equalsIgnoreCase("domainOnPV")) {
      domain = createDomainOnPvUsingWdt(dpvDomainUid, domainOnPVNamespace, wlSecretName,
          clusterName, replicaCount, ItMultiDomainModels.class.getSimpleName());
    } else {
      domain = createDomainUsingAuxiliaryImage();
    }

    assertDomainNotNull(domain);
    return domain;
  }

  private DomainResource createDomainUsingAuxiliaryImage() {
    String domainUid = "auxiliaryimagedomain";
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, auxiliaryImageDomainNamespace,
        ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    createSecretWithUsernamePassword(encryptionSecretName, auxiliaryImageDomainNamespace,
        "weblogicenc", "weblogicenc");

    // build app
    AppParams appParams = defaultAppParams()
        .srcDirList(Collections.singletonList(MII_BASIC_APP_NAME))
        .appArchiveDir(ARCHIVE_DIR + this.getClass().getSimpleName())
        .appName(MII_BASIC_APP_NAME);

    assertTrue(buildAppArchive(appParams),
        String.format("Failed to create app archive for %s", MII_BASIC_APP_NAME));

    // image1 with model files for domain config, ds, app and wdt install files
    List<String> archiveList = Collections.singletonList(appParams.appArchiveDir() + "/" + MII_BASIC_APP_NAME + ".zip");

    List<String> modelList = new ArrayList<>();
    modelList.add(MODEL_DIR + "/" + MII_BASIC_WDT_MODEL_FILE);
    String miiAuxiliaryImage1Tag = "mdmauxi-image" + MII_BASIC_IMAGE_TAG;
    createPushAuxiliaryImageWithDomainConfig(MII_AUXILIARY_IMAGE_NAME, miiAuxiliaryImage1Tag, archiveList, modelList);

    // admin/managed server name here should match with model yaml
    final String auxiliaryImagePath = "/auxiliary";
    String miiAuxiliaryImage1 = MII_AUXILIARY_IMAGE_NAME + ":" + miiAuxiliaryImage1Tag;
    // create domain custom resource using a auxiliary image
    logger.info("Creating domain custom resource with domainUid {0} and auxiliary images {1}",
        domainUid, miiAuxiliaryImage1);

    DomainResource domainCR =
        createDomainResourceWithAuxiliaryImage(domainUid, auxiliaryImageDomainNamespace,
        WEBLOGIC_IMAGE_TO_USE_IN_SPEC, wlSecretName, createSecretsForImageRepos(auxiliaryImageDomainNamespace),
        encryptionSecretName, auxiliaryImagePath, miiAuxiliaryImage1);

    domainCR = createClusterResourceAndAddReferenceToDomain(
        clusterName, clusterName, auxiliaryImageDomainNamespace, domainCR, replicaCount);

    // create domain and verify its running
    logger.info("Creating domain {0} with auxiliary images {1} in namespace {2}",
        domainUid, miiAuxiliaryImage1, auxiliaryImageDomainNamespace);
    createDomainAndVerify(domainUid, domainCR, auxiliaryImageDomainNamespace,
        adminServerPodName, managedServerPrefix, replicaCount);

    return domainCR;
  }

}
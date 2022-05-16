// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.List;

import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.annotations.DisabledOnSlimImage;
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
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.adminNodePortAccessible;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.scaleAndVerifyCluster;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createAndVerifyDomainInImageUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainOnPvUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The test class creates WebLogic domains with three models.
 * domain-on-pv ( using WDT )
 * domain-in-image ( using WDT )
 * model-in-image
 * Verify the basic lifecycle operations of the WebLogic server pods by scaling the domain.
 * Also verify admin console login using admin node port.
 */
@DisplayName("Verify the basic lifecycle operations of the WebLogic server pods by scaling the clusters in the domain"
    + " with different domain types and verify admin console login using admin node port.")
@IntegrationTest
class ItMultiDomainModels {

  // domain constants
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 2;
  private static final String wlSecretName = "weblogic-credentials";
  private static final String miiDomainUid = "miidomain";
  private static final String dimDomainUid = "domaininimage";
  private static final String dpvDomainUid = "domainonpv";
  private static final String wdtModelFileForDomainInImage = "wdt-singlecluster-sampleapp-usingprop-wls.yaml";

  private static LoggingFacade logger = null;
  private static String miiDomainNamespace = null;
  private static String domainOnPVNamespace = null;
  private static String domainInImageNamespace = null;

  /**
   * Install operator.
   * Create three different type of domains: model in image, domain on PV and domain in image.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(4) List<String> namespaces) {
    logger = getLogger();

    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get unique namespaces for three different type of domains
    logger.info("Getting unique namespaces for three different type of domains");
    assertNotNull(namespaces.get(1));
    miiDomainNamespace = namespaces.get(1);
    assertNotNull(namespaces.get(2));
    domainOnPVNamespace = namespaces.get(2);
    assertNotNull(namespaces.get(3));
    domainInImageNamespace = namespaces.get(3);

    // set the service account name for the operator
    String opServiceAccount = opNamespace + "-sa";

    // install and verify operator
    installAndVerifyOperator(opNamespace, opServiceAccount, false, 0,
        miiDomainNamespace, domainOnPVNamespace, domainInImageNamespace);
  }

  /**
   * Scale the cluster by patching domain resource for three different
   * type of domains i.e. domain-on-pv, domain-in-image and model-in-image
   * Also verify admin console login using admin node port.
   * @param domainType domain type, possible value: modelInImage, domainInImage, domainOnPV
   */
  @ParameterizedTest
  @DisplayName("scale cluster by patching domain resource with three different type of domains and "
      + "verify admin console login using admin node port.")
  @ValueSource(strings = {"modelInImage", "domainInImage", "domainOnPV"})
  @Tag("gate")
  @DisabledOnSlimImage
  void testScaleClustersAndAdminConsoleLogin(String domainType) {
    Domain domain = createDomainBasedOnDomainType(domainType);

    // get the domain properties
    String domainUid = domain.getSpec().getDomainUid();
    String domainNamespace = domain.getMetadata().getNamespace();

    String managedServerPodNamePrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;

    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
    logger.info("Getting node port for default channel");
    int serviceNodePort = assertDoesNotThrow(() -> getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default"),
        "Getting admin server node port failed");

    // In OKD cluster, we need to get the routeHost for the external admin service
    String routeHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);


    int numberOfServers = 3;
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} to {3} servers.",
        clusterName, domainUid, domainNamespace, numberOfServers);
    List<String> managedServersBeforeScale = listManagedServersBeforeScale(replicaCount);
    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        replicaCount, numberOfServers, null, managedServersBeforeScale);

    // then scale cluster back to 2 servers
    logger.info("Scaling cluster {0} of domain {1} in namespace {2} from {3} servers to {4} servers.",
        clusterName, domainUid, domainNamespace, numberOfServers, replicaCount);
    managedServersBeforeScale = listManagedServersBeforeScale(numberOfServers);
    scaleAndVerifyCluster(clusterName, domainUid, domainNamespace, managedServerPodNamePrefix,
        numberOfServers, replicaCount, null, managedServersBeforeScale);
    
    logger.info("Validating WebLogic admin server access by login to console");
    testUntil(
        assertDoesNotThrow(() -> {
          return adminNodePortAccessible(serviceNodePort, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, routeHost);
        }, "Access to admin server node port failed"),
        logger,
        "Console login validation");

    // shutdown domain and verify the domain is shutdown
    shutdownDomainAndVerify(domainNamespace, domainUid, replicaCount);
  }

  /**
   * Generate a server list which contains all managed servers in the cluster before scale.
   *
   * @param replicasBeforeScale the replicas of WebLogic cluster before scale
   * @return list of managed servers in the cluster before scale
   */
  private static List<String> listManagedServersBeforeScale(int replicasBeforeScale) {

    List<String> managedServerNames = new ArrayList<>();
    for (int i = 1; i <= replicasBeforeScale; i++) {
      managedServerNames.add(MANAGED_SERVER_NAME_BASE + i);
    }

    return managedServerNames;
  }

  /**
   * Assert the specified domain and domain spec, metadata and clusters not null.
   * @param domain oracle.weblogic.domain.Domain object
   */
  private static void assertDomainNotNull(Domain domain) {
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), domain + " spec is null");
    assertNotNull(domain.getMetadata(), domain + " metadata is null");
    assertNotNull(domain.getSpec().getClusters(), domain.getSpec() + " getClusters() is null");
  }

  private static Domain createDomainBasedOnDomainType(String domainType) {
    Domain domain = null;

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
    } else {
      domain = createDomainOnPvUsingWdt(dpvDomainUid, domainOnPVNamespace, wlSecretName,
          clusterName, replicaCount, ItMultiDomainModels.class.getSimpleName());
    }

    assertDomainNotNull(domain);
    return domain;
  }
}

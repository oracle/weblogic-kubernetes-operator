// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNonEmptySystemProperty;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createAndVerifyDomainInImageUsingWdt;
import static oracle.weblogic.kubernetes.utils.DomainUtils.shutdownDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests related to large capacity MII domain and multiple clusters.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Verify the Operator can handle multiple MII domains and clusters at the same time.")
@IntegrationTest
@Tag("okdenv")
class ItLargeCapacityDomainsClustersDII {

  private static String opNamespace = null;
  private static int numOfDomains = Integer.valueOf(getNonEmptySystemProperty("NUMBER_OF_DOMAINS", "10"));
  private static int numOfClusters = Integer.valueOf(getNonEmptySystemProperty("NUMBER_OF_CLUSTERS", "10"));
  private static final String baseDomainUid = "domain";
  private static List<String> domainNamespaces;

  private static final String adminServerName = ADMIN_SERVER_NAME_BASE;
  private static int clusterReplicaCount = 2;
  private static String clusterName = "cluster-1";
  private static LoggingFacade logger;

  static String adminSecretName = "weblogic-credentials";
  //static String encryptionSecretName = "encryptionsecret";
  private static final String wdtModelFileForDomainInImage = "wdt-singlecluster-sampleapp-usingprop-wls.yaml";

  /**
   * Assigns unique namespaces for operator and domains. Installs operator. Creates a MII WebLogic domain.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(50) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespaces for WebLogic domains");
    domainNamespaces = namespaces.subList(1, numOfDomains + 1);

    // install operator and verify its running in ready state
    installAndVerifyOperator(opNamespace, namespaces.subList(1, 50).toArray(new String[0]));
  }

  /**
   * Test brings up new MII domains and verifies it can successfully start by doing the following.
   *
   * a. Creates domain resource and deploys in Kubernetes cluster. 
   * b. Verifies the servers in the new WebLogic domain comes up.
   */
  @Order(1)
  @Test
  @DisplayName("Test MII domains creation")
  void testCreateDomains() {
    String domainUid;
    for (int i = 0; i < numOfDomains; i++) {
      domainUid = baseDomainUid + (i + 1);
      List<String> appSrcDirList = new ArrayList<>();
      appSrcDirList.add(MII_BASIC_APP_NAME);
      createAndVerifyDomainInImageUsingWdt(domainUid, domainNamespaces.get(i),
          wdtModelFileForDomainInImage, appSrcDirList, adminSecretName, clusterName, clusterReplicaCount);
    }
  }

  /**
   * Test restart all existing clusters.
   *
   * a. Shutdowns all domains using serverStartPolicy NEVER. 
   * b. Patch the Domain Resource with cluster serverStartPolicy IF_NEEDED. 
   * c. Verifies the servers in the domain cluster comes up.
   */
  @Order(2)
  @Test
  @DisplayName("Test DII domain shutdown and startup")
  void testRestartDomains() {
    String domainUid;
    for (int i = 0; i < numOfDomains; i++) {
      domainUid = baseDomainUid + (i + 1);
      shutdownDomainAndVerify(domainNamespaces.get(i), domainUid, clusterReplicaCount);
      logger.info("patch the domain resource with serverStartPolicy to IF_NEEDED");
      String patchStr
          = "[{"
          + "\"op\": \"replace\", \"path\": \"/spec/serverStartPolicy\", \"value\": \"IF_NEEDED\""
          + "}]";
      logger.info("Updating domain configuration using patch string: {0}\n", patchStr);
      V1Patch patch = new V1Patch(patchStr);
      assertTrue(patchDomainCustomResource(domainUid, domainNamespaces.get(i), patch, V1Patch.PATCH_FORMAT_JSON_PATCH),
          "Failed to patch domain");
      // check that admin service/pod exists in the domain namespace
      logger.info("Checking that admin service/pod {0} exists in namespace {1}",
          domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainNamespaces.get(i));
      checkPodReadyAndServiceExists(domainUid + "-" + ADMIN_SERVER_NAME_BASE, domainUid, domainNamespaces.get(i));

      for (int j = 1; j <= clusterReplicaCount; j++) {
        String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;
        // check that ms service/pod exists in the domain namespace
        logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
            managedServerPodName, domainNamespaces.get(i));
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespaces.get(i));
      }
    }

  }
}

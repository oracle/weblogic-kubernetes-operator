// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.impl.Service.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createMiiDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyWlsRemoteConsole;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.shutdownWlsRemoteConsole;
import static oracle.weblogic.kubernetes.utils.TestUtils.callWebAppAndWaitTillReturnedCode;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Test WebLogic remote console connecting to mii domain")
@IntegrationTest
class ItRemoteConsole {

  private static String domainNamespace = null;

  // domain constants
  private static final String domainUid = "domain1";
  private static final String clusterName = "cluster-1";
  private static final int replicaCount = 1;
  private static final String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
  private static final String managedServerPrefix = domainUid + "-" + MANAGED_SERVER_NAME_BASE;
  private static LoggingFacade logger = null;

  /**
   * Get namespaces for operator and WebLogic domain.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(2) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    final String opNamespace = namespaces.get(0);

    // get a unique domain namespace
    logger.info("Getting a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // create a basic model in image domain
    createMiiDomainAndVerify(
        domainNamespace,
        domainUid,
        MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG,
        adminServerPodName,
        managedServerPrefix,
        replicaCount);

  }

  /**
   * Verify WLS Remote Console installation is successful.
   * Verify k8s WebLogic domain is accessible through remote console.
   */
  @Test
  @DisplayName("Verify Connecting to Mii domain through WLS Remote Console is successful")
  public void testWlsRemoteConsoleConnection() {

    assertTrue(installAndVerifyWlsRemoteConsole(), "Remote Console installation failed");

    int nodePort = getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertTrue(nodePort != -1,
        "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);
    logger.info("The K8S_NODEPORT_HOST is {0}", K8S_NODEPORT_HOST);
    String curlCmd = "curl -v --user weblogic:welcome1 -H Content-Type:application/json -d "
        + "\"{ \\" + "\"domainUrl\\" + "\"" + ": " + "\\" + "\"" + "http://"
        + K8S_NODEPORT_HOST + ":" + nodePort + "\\" + "\" }" + "\""
        + " http://localhost:8012/api/connection  --write-out %{http_code} -o /dev/null";
    logger.info("Executing default nodeport curl command {0}", curlCmd);
    assertTrue(callWebAppAndWaitTillReturnedCode(curlCmd, "201", 10), "Calling web app failed");
    logger.info("WebLogic domain is accessible through remote console");

  }

  /**
   * Shutdown WLS Remote Console.
   */
  @AfterAll
  public void tearDownAll() {
    if (System.getenv("SKIP_CLEANUP") == null
        || (System.getenv("SKIP_CLEANUP") != null
        && System.getenv("SKIP_CLEANUP").equalsIgnoreCase("false")))  {
      assertTrue(shutdownWlsRemoteConsole(), "Remote Console shutdown failed");
    }
  }

}

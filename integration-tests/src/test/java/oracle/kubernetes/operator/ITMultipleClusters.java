// Copyright 2018, 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.K8sTestUtils;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Multiple clusters in a domain tests
 *
 * <p>More than 1 cluster is created in a domain , like configured and dynamic
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITMultipleClusters extends BaseTest {

  private static Operator operator1, operator2;

  private static Operator operatorForBackwardCompatibility;
  private static Operator operatorForRESTCertChain;

  private static final String TWO_CONFIGURED_CLUSTER_SCRIPT =
      "create-domain-two-configured-cluster.py";
  private static final String TWO_MIXED_CLUSTER_SCRIPT = "create-domain-two-mixed-cluster.py";

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    initialize(APP_PROPS_FILE);
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    logger.info("Run once, release cluster lease");
    tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
    logger.info("SUCCESS");
  }

  /**
   * Create 2 configured clusters in a domain each having 2 managed servers. Verify the managed
   * servers are running and verify the basic use cases.
   *
   * @throws Exception
   */
  @Test
  public void testCreateDomainTwoConfiguredCluster() throws Exception {
    String DOMAINUID = "twoconfiguredclusterdomain";
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String template =
        BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/common/domain-template.yaml";
    logger.info("Creating Operator & waiting for the script to complete execution");
    // create operator1
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
      domainMap.put("domainUID", DOMAINUID);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/"
              + TWO_CONFIGURED_CLUSTER_SCRIPT);

      String add =
          "  - clusterName: %CLUSTER_NAME%-2\n"
              + "    serverStartState: \"RUNNING\"\n"
              + "    replicas: %INITIAL_MANAGED_SERVER_REPLICAS%\n";
      logger.info("Making a backup of the domain template file:" + template);
      if (!Files.exists(Paths.get(template + ".org"))) {
        Files.copy(Paths.get(template), Paths.get(template + ".org"));
      }
      Files.write(Paths.get(template), add.getBytes(), StandardOpenOption.APPEND);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      K8sTestUtils testUtil = new K8sTestUtils();
      String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", DOMAINUID);
      String namespace = domain.getDomainNS();
      String pods[] = {
        DOMAINUID + "-" + domain.getAdminServerName(),
        DOMAINUID + "-managed-server",
        DOMAINUID + "-managed-server1",
        DOMAINUID + "-managed-server2",
        DOMAINUID + "-new-managed-server1",
        DOMAINUID + "-new-managed-server2",
      };
      for (String pod : pods) {
        assertTrue(
            pod + " Pod not running", testUtil.isPodRunning(namespace, domain1LabelSelector, pod));
      }
      testBasicUseCases(domain);
      if (!SMOKETEST) domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
        Files.copy(
            Paths.get(template + ".org"), Paths.get(template), StandardCopyOption.REPLACE_EXISTING);
        Files.delete(Paths.get(template + ".org"));
      }
      domain.shutdownUsingServerStartPolicy();
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Create 2 clusters(configured and dynamic) in a domain each having 2 managed servers. Verify the
   * managed servers are running and verify the basic use cases.
   *
   * @throws Exception
   */
  @Test
  public void testCreateDomainTwoMixedCluster() throws Exception {
    String DOMAINUID = "twomixedclusterdomain";
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String template =
        BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/common/domain-template.yaml";
    logger.info("Creating Operator & waiting for the script to complete execution");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
      domainMap.put("domainUID", DOMAINUID);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/" + TWO_MIXED_CLUSTER_SCRIPT);

      String add =
          "  - clusterName: %CLUSTER_NAME%-2\n"
              + "    serverStartState: \"RUNNING\"\n"
              + "    replicas: %INITIAL_MANAGED_SERVER_REPLICAS%\n";
      logger.info("Making a backup of the domain template file:" + template);
      if (!Files.exists(Paths.get(template + ".org"))) {
        Files.copy(Paths.get(template), Paths.get(template + ".org"));
      }
      Files.write(Paths.get(template), add.getBytes(), StandardOpenOption.APPEND);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      K8sTestUtils testUtil = new K8sTestUtils();
      String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", DOMAINUID);
      String namespace = domain.getDomainNS();
      String pods[] = {
        DOMAINUID + "-" + domain.getAdminServerName(),
        DOMAINUID + "-managed-server",
        DOMAINUID + "-managed-server1",
        DOMAINUID + "-managed-server2",
        DOMAINUID + "-new-managed-server1",
        DOMAINUID + "-new-managed-server2",
      };
      for (String pod : pods) {
        assertTrue(
            pod + " Pod not running", testUtil.isPodRunning(namespace, domain1LabelSelector, pod));
      }
      testBasicUseCases(domain);
      if (!SMOKETEST) domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
        Files.copy(
            Paths.get(template + ".org"), Paths.get(template), StandardCopyOption.REPLACE_EXISTING);
        Files.delete(Paths.get(template + ".org"));
      }
      domain.shutdownUsingServerStartPolicy();
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  private void testBasicUseCases(Domain domain) throws Exception {
    testAdminT3Channel(domain);
    testAdminServerExternalService(domain);
  }
}

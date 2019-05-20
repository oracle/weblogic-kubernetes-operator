// Copyright 2018, 2019 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.logging.Level;
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

  private static Operator operator1;
  private static final String TWO_CONFIGURED_CLUSTER_SCRIPT =
      "create-domain-two-configured-cluster.py";
  private static final String TWO_MIXED_CLUSTER_SCRIPT = "create-domain-two-mixed-cluster.py";
  private static String template;
  private static final String DOMAINUID = "twoconfigclustdomain";

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
    template =
        BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/common/domain-template.yaml";
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

    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

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
      domainMap.put("clusterType", "CONFIGURED");
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/"
              + TWO_CONFIGURED_CLUSTER_SCRIPT);
      if ((System.getenv("LB_TYPE") != null && System.getenv("LB_TYPE").equalsIgnoreCase("VOYAGER"))
          || (domainMap.containsKey("loadBalancer")
              && ((String) domainMap.get("loadBalancer")).equalsIgnoreCase("VOYAGER"))) {
        domainMap.put("voyagerWebPort", new Integer("30366"));
      }
      addCluster2ToDomainTemplate(domainMap);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String pods[] = {
        DOMAINUID + "-" + domain.getAdminServerName(),
        DOMAINUID + "-managed-server",
        DOMAINUID + "-managed-server1",
        DOMAINUID + "-managed-server2",
        DOMAINUID + "-new-managed-server1",
        DOMAINUID + "-new-managed-server2",
      };
      verifyServersStatus(domain, pods);

      testBasicUseCases(domain);
      if (!SMOKETEST) {
        domain.testWlsLivenessProbe();
      }
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
        domain.destroy();
      }
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
      if ((System.getenv("LB_TYPE") != null && System.getenv("LB_TYPE").equalsIgnoreCase("VOYAGER"))
          || (domainMap.containsKey("loadBalancer")
              && ((String) domainMap.get("loadBalancer")).equalsIgnoreCase("VOYAGER"))) {
        domainMap.put("voyagerWebPort", new Integer("30377"));
      }
      addCluster2ToDomainTemplate(domainMap);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String pods[] = {
        DOMAINUID + "-" + domain.getAdminServerName(),
        DOMAINUID + "-managed-server",
        DOMAINUID + "-managed-server1",
        DOMAINUID + "-managed-server2",
        DOMAINUID + "-new-managed-server1",
        DOMAINUID + "-new-managed-server2",
      };
      verifyServersStatus(domain, pods);

      testBasicUseCases(domain);
      if (!SMOKETEST) {
        domain.testWlsLivenessProbe();
      }
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
        domain.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Create 2 dynamic clusters in a domain using WDT in inimage each having 2 managed servers.
   * Verify the managed servers are running and verify the basic use cases.
   *
   * @throws Exception
   */
  @Test
  public void testCreateDomainTwoClusterWDTInImage() throws Exception {
    String DOMAINUID = "twoclusterdomainwdt";
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    logger.info("Creating Operator & waiting for the script to complete execution");
    if (operator1 == null) {
      operator1 = TestUtils.createOperator(OPERATOR1_YAML);
    }
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = TestUtils.loadYaml(DOMAININIMAGE_WDT_YAML);
      domainMap.put("domainUID", DOMAINUID);
      if ((System.getenv("LB_TYPE") != null && System.getenv("LB_TYPE").equalsIgnoreCase("VOYAGER"))
          || (domainMap.containsKey("loadBalancer")
              && ((String) domainMap.get("loadBalancer")).equalsIgnoreCase("VOYAGER"))) {
        domainMap.put("voyagerWebPort", new Integer("30377"));
      }
      domainMap.put(
          "customWdtTemplate",
          BaseTest.getProjectRoot()
              + "/integration-tests/src/test/resources/multipleclusters/wdtmultipledynclusters.yml");
      addCluster2ToDomainTemplate(domainMap);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String pods[] = {
        DOMAINUID + "-" + domain.getAdminServerName(),
        DOMAINUID + "-managed-server1",
        DOMAINUID + "-managed-server2",
        DOMAINUID + "-managed-server-21",
        DOMAINUID + "-managed-server-22",
      };
      verifyServersStatus(domain, pods);
      testBasicUseCases(domain);
      if (!SMOKETEST) {
        domain.testWlsLivenessProbe();
      }
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && !SMOKETEST && (JENKINS || testCompletedSuccessfully)) {
        domain.destroy();
      }
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Append a second cluster to the domain template
   *
   * @throws IOException when append fails
   */
  private void addCluster2ToDomainTemplate(Map<String, Object> domainMap) throws IOException {
    String add =
        "  - clusterName: %CLUSTER_NAME%-2\n"
            + "    serverStartState: \"RUNNING\"\n"
            + "    replicas: %INITIAL_MANAGED_SERVER_REPLICAS%\n";
    String customDomainTemplate =
        System.getProperty("java.io.tmpdir") + "/customDomainTemplate.yaml";
    Files.copy(
        Paths.get(template), Paths.get(customDomainTemplate), StandardCopyOption.REPLACE_EXISTING);
    Files.write(Paths.get(customDomainTemplate), add.getBytes(), StandardOpenOption.APPEND);
    domainMap.put("customDomainTemplate", customDomainTemplate);
  }

  /**
   * Verifies all of the servers in the cluster are in Running status
   *
   * @param Domain
   * @param String array pod names to check the status for
   */
  private void verifyServersStatus(Domain domain, String[] pods) {
    K8sTestUtils testUtil = new K8sTestUtils();
    String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", DOMAINUID);
    String namespace = domain.getDomainNS();
    for (String pod : pods) {
      assertTrue(
          pod + " Pod not running", testUtil.isPodRunning(namespace, domain1LabelSelector, pod));
    }
  }
}

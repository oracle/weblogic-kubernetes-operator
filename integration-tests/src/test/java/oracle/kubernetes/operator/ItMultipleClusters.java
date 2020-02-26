// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.K8sTestUtils;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.Alphanumeric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Multiple clusters in a domain tests.
 *
 * <p>More than 1 cluster is created in a domain , like configured and dynamic
 */
@TestMethodOrder(Alphanumeric.class)
public class ItMultipleClusters extends BaseTest {

  private static final String TWO_CONFIGURED_CLUSTER_SCRIPT =
      "create-domain-two-configured-cluster.py";
  private static final String TWO_MIXED_CLUSTER_SCRIPT = "create-domain-two-mixed-cluster.py";
  private static final String DOMAINUID = "twoconfigclustdomain";
  private static Operator operator1;
  private static String customDomainTemplate;
  private static String testClassName;
  private static String domainNS1;
  private static StringBuffer namespaceList;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeAll
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      namespaceList = new StringBuffer();
      testClassName = new Object() {
      }.getClass().getEnclosingClass().getSimpleName();
      initialize(APP_PROPS_FILE, testClassName);
    }
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    if (FULLTEST) {
      createResultAndPvDirs(testClassName);
      // create operator1
      if (operator1 == null) {
        String template =
            BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/common/domain-template.yaml";
        String add =
            "  - clusterName: %CLUSTER_NAME%-2\n"
                + "    serverStartState: \"RUNNING\"\n"
                + "    replicas: %INITIAL_MANAGED_SERVER_REPLICAS%\n";
        customDomainTemplate = getResultDir() + "/customDomainTemplate.yaml";
        Files.copy(
            Paths.get(template),
            Paths.get(customDomainTemplate),
            StandardCopyOption.REPLACE_EXISTING);
        Files.write(Paths.get(customDomainTemplate), add.getBytes(), StandardOpenOption.APPEND);

        Map<String, Object> operatorMap =
            createOperatorMap(getNewSuffixCount(), true, testClassName);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator1);
        domainNS1 = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS1);
      }

    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      tearDown(new Object() {
      }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

      LoggerHelper.getLocal().info("SUCCESS");
    }
  }

  /**
   * Create 2 configured clusters in a domain each having 2 managed servers. Verify the managed
   * servers are running and verify the basic use cases.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainTwoConfiguredCluster() throws Exception {
    Assumptions.assumeTrue(FULLTEST);

    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
      domainMap.put("domainUID", DOMAINUID);
      domainMap.put("clusterType", "CONFIGURED");
      domainMap.put("customDomainTemplate", customDomainTemplate);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/"
              + TWO_CONFIGURED_CLUSTER_SCRIPT);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String[] pods = {
          DOMAINUID + "-" + domain.getAdminServerName(),
          DOMAINUID + "-managed-server",
          DOMAINUID + "-managed-server1",
          DOMAINUID + "-managed-server2",
          DOMAINUID + "-new-managed-server1",
          DOMAINUID + "-new-managed-server2",
      };
      verifyServersStatus(domain, pods);
      testBasicUseCases(domain, false);
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create 2 clusters(configured and dynamic) in a domain each having 2 managed servers. Verify the
   * managed servers are running and verify the basic use cases.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainTwoMixedCluster() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String domainuid = "twomixedclusterdomain";
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
      domainMap.put("domainUID", domainuid);
      domainMap.put("customDomainTemplate", customDomainTemplate);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/" + TWO_MIXED_CLUSTER_SCRIPT);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String[] pods = {
          domainuid + "-" + domain.getAdminServerName(),
          domainuid + "-managed-server",
          domainuid + "-managed-server1",
          domainuid + "-managed-server2",
          domainuid + "-new-managed-server1",
          domainuid + "-new-managed-server2",
      };
      verifyServersStatus(domain, pods);

      testBasicUseCases(domain, false);
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create 2 dynamic clusters in a domain using WDT in inimage each having 2 managed servers.
   * Verify the managed servers are running and verify the basic use cases.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateDomainTwoClusterWdtInImage() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String domainuid = "twoclusterdomainwdt";
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Domain domain = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domainMap =
          createDomainInImageMap(getNewSuffixCount(), true, testClassName);
      domainMap.put("domainUID", domainuid);
      domainMap.put("customDomainTemplate", customDomainTemplate);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "customWdtTemplate",
          BaseTest.getProjectRoot()
              + "/integration-tests/src/test/resources/multipleclusters/wdtmultipledynclusters.yml");
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String[] pods = {
          domainuid + "-" + domain.getAdminServerName(),
          domainuid + "-managed-server1",
          domainuid + "-managed-server2",
          domainuid + "-managed-server-21",
          domainuid + "-managed-server-22",
      };
      verifyServersStatus(domain, pods);
      testBasicUseCases(domain, false);
      domain.testWlsLivenessProbe();
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
      if (domain != null) {
        domain.deleteImage();
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Verifies all of the servers in the cluster are in Running status.
   *
   * @param domain Domain
   * @param pods   array pod names to check the status for
   */
  private void verifyServersStatus(Domain domain, String[] pods) {
    K8sTestUtils testUtil = new K8sTestUtils();
    String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", DOMAINUID);
    String namespace = domain.getDomainNs();
    for (String pod : pods) {
      assertTrue(
          testUtil.isPodRunning(namespace, domain1LabelSelector, pod), pod + " Pod not running");
    }
  }
}

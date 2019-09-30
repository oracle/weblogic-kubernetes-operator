// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
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
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.K8sTestUtils;
import oracle.kubernetes.operator.utils.LoggerHelper;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ItManagedCoherence extends BaseTest {

  private static final String COHERENCE_CLUSTER_SCRIPT = "create-domain-coherence-cluster.py";
  private static final String COHERENCE_CLUSTER_IN_IMAGE_SCRIPT =
      "create-domain-in-image-coherence-cluster.py";
  private static final String DOMAINUID = "cmdomonpv";
  private static final String DOMAINUID1 = "cmdominimage";
  private static final String testAppName = "coherenceapp";
  private static final String appToDeploy = "CoherenceApp";
  private static final String scriptName = "buildDeployCoherenceAppInPod.sh";
  private static String customDomainTemplate;
  private static Operator operator1;
  Domain domain = null;
  private static String testClassName;
  private static String domainNS1;
  static boolean testCompletedSuccessfully = false;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      testClassName = new Object() {}.getClass().getEnclosingClass().getSimpleName();
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE, testClassName);
      String template =
          BaseTest.getProjectRoot() + "/kubernetes/samples/scripts/common/domain-template.yaml";
      String add =
          "  - clusterName: dataCluster\n"
              + "    serverStartState: \"RUNNING\"\n"
              + "    replicas: %INITIAL_MANAGED_SERVER_REPLICAS%\n";
      customDomainTemplate = BaseTest.getResultDir() + "/customDomainTemplate.yaml";

      Files.copy(
          Paths.get(template),
          Paths.get(customDomainTemplate),
          StandardCopyOption.REPLACE_EXISTING);
      Files.write(Paths.get(customDomainTemplate), add.getBytes(), StandardOpenOption.APPEND);
    }
    
    // create operator1
    if (operator1 == null) {
      Map<String, Object> operatorMap = TestUtils.createOperatorMap(
                      getNewNumber(), true, testClassName);
      operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
      Assert.assertNotNull(operator1);
      domainNS1 = ((ArrayList<String>)operatorMap.get("domainNamespaces")).get(0);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      if (operator1 != null && (JENKINS || testCompletedSuccessfully)) {
        operator1.destroy();
      }
    }
  }

  /**
   * Verifies all of the servers in the cluster are in Running status.
   *
   * @param domain Domain
   * @param pods array pod names to check the status for
   * @param domainUid Domain UID
   */
  private static void verifyServersStatus(Domain domain, String[] pods, String domainUid) {
    K8sTestUtils testUtil = new K8sTestUtils();
    String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    String namespace = domain.getDomainNs();
    for (String pod : pods) {
      assertTrue(
          pod + " Pod not running", testUtil.isPodRunning(namespace, domain1LabelSelector, pod));
    }
  }

  /**
   * Create operator and verify its deployed successfully. Create domain with 2 Managed coherence
   * clusters verify domain is started. Deploy an application to the cluster with no storage enabled
   * and the GAR file to the cluster with storage enabled. Verify that data can be added and stored
   * in the cache and can also be retrieved from cache.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateCoherenceDomainOnPvUsingWlst() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();

    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, 
                "Creating coeherence domain on pv using wlst and testing the cache");

    testCompletedSuccessfully = false;
    domain = null;
    try {
      Map<String, Object> domainMap = TestUtils.createDomainMap(getNewNumber(), testClassName);
      domainMap.put("clusterName", "appCluster");
      domainMap.put("domainUID", DOMAINUID);
      domainMap.put("customDomainTemplate", customDomainTemplate);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/" + COHERENCE_CLUSTER_SCRIPT);
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
      verifyServersStatus(domain, pods, DOMAINUID);
  
      // Build WAR in the admin pod and deploy it from the admin pod to a weblogic target
      TestUtils.buildDeployCoherenceAppInPod(
          domain,
          testAppName,
          scriptName,
          BaseTest.getUsername(),
          BaseTest.getPassword(),
          appToDeploy,
          "dataCluster");
  
      coherenceCacheTest();
      testCompletedSuccessfully = true;
    } finally {
     if (domain != null && (JENKINS || testCompletedSuccessfully)) {
       TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
     }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Create operator and verify its deployed successfully. Create domain with 2 Managed coherence
   * clusters verify domain is started. Deploy an application to the cluster with no storage enabled
   * and the GAR file to the cluster with storage enabled. Verify that data can be added and stored
   * in the cache and can also be retrieved from cache.
   *
   * @throws Exception exception
   */
  @Test
  public void testCreateCoherenceDomainInImageUsingWlst() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();

    logTestBegin(testMethodName);
    LoggerHelper.getLocal().log(Level.INFO, 
            "Creating coeherence domain in image using wlst and testing the cache");

    testCompletedSuccessfully = false;
    domain = null;
    try {
      Map<String, Object> domainMap = 
          TestUtils.createDomainInImageMap(getNewNumber(), false, testClassName);
      domainMap.put("clusterName", "appCluster");
      domainMap.put("domainUID", DOMAINUID1);
      domainMap.put("customDomainTemplate", customDomainTemplate);
      domainMap.put("namespace", domainNS1);
      domainMap.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/" + COHERENCE_CLUSTER_IN_IMAGE_SCRIPT);
      domain = TestUtils.createDomain(domainMap);
      domain.verifyDomainCreated();
      String[] pods = {
        DOMAINUID1 + "-" + domain.getAdminServerName(),
        DOMAINUID1 + "-managed-server",
        DOMAINUID1 + "-managed-server1",
        DOMAINUID1 + "-managed-server2",
        DOMAINUID1 + "-new-managed-server1",
        DOMAINUID1 + "-new-managed-server2",
      };
      verifyServersStatus(domain, pods, DOMAINUID1);
  
      // Build WAR in the admin pod and deploy it from the admin pod to a weblogic target
      TestUtils.buildDeployCoherenceAppInPod(
          domain,
          testAppName,
          scriptName,
          BaseTest.getUsername(),
          BaseTest.getPassword(),
          appToDeploy,
          "dataCluster");
  
      coherenceCacheTest();
  
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && (JENKINS || testCompletedSuccessfully)) {
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private void coherenceCacheTest() throws Exception {

    String[] firstNameList = {"Frodo", "Samwise", "Bilbo", "peregrin", "Meriadoc", "Gandalf"};
    String[] secondNameList = {"Baggins", "Gamgee", "Baggins", "Took", "Brandybuck", "TheGrey"};
    ExecResult result;

    for (int i = 0; i < firstNameList.length; i++) {
      result = addDataToCache(firstNameList[i], secondNameList[i]);
      LoggerHelper.getLocal().log(Level.INFO, "addDataToCache returned" + result.stdout());
    }
    // check if cache size is 6
    result = getCacheSize();
    LoggerHelper.getLocal().log(Level.INFO, "number of records in cache = " + result.stdout());
    if (!(result.stdout().equals("6"))) {
      LoggerHelper.getLocal().log(Level.INFO, "number of records in cache = " + result.stdout());
      assertFalse("Expected 6 records", "6".equals(result.stdout()));
    }
    // get the data from cache
    result = getCacheContents();
    LoggerHelper.getLocal().log(Level.INFO, 
        "Cache contains the following entries \n" + result.stdout());

    // Now clear the cache
    result = clearCache();
    LoggerHelper.getLocal().log(Level.INFO, 
        "Cache is cleared and should be empty" + result.stdout());
  }

  private ExecResult addDataToCache(String firstName, String secondName) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Add initial data to cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent ");
    curlCmd
        .append("-d 'action=add&first=")
        .append(firstName)
        .append("&second=")
        .append(secondName)
        .append("' ")
        .append("-X POST -H 'host: ")
        .append(domain.getDomainUid())
        .append(".org' ")
        .append("http://")
        .append(domain.getHostNameForCurl())
        .append(":")
        .append(domain.getLoadBalancerWebPort())
        .append("/")
        .append(appToDeploy)
        .append("/")
        .append(appToDeploy);
    LoggerHelper.getLocal().log(Level.INFO, "curlCmd is " + curlCmd.toString());
    return TestUtils.exec(curlCmd.toString());
  }

  private ExecResult getCacheSize() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "get the number of records in cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent ");
    curlCmd
        .append("-d 'action=size' ")
        .append("-H 'host: ")
        .append(domain.getDomainUid())
        .append(".org' ")
        .append("http://")
        .append(domain.getHostNameForCurl())
        .append(":")
        .append(domain.getLoadBalancerWebPort())
        .append("/")
        .append(appToDeploy)
        .append("/")
        .append(appToDeploy);
    return TestUtils.exec(curlCmd.toString());
  }

  private ExecResult getCacheContents() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "get the records from cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent ");
    curlCmd
        .append("-d 'action=get' ")
        .append("-H 'host: ")
        .append(domain.getDomainUid())
        .append(".org' ")
        .append("http://")
        .append(domain.getHostNameForCurl())
        .append(":")
        .append(domain.getLoadBalancerWebPort())
        .append("/")
        .append(appToDeploy)
        .append("/")
        .append(appToDeploy);
    return TestUtils.exec(curlCmd.toString());
  }

  private ExecResult clearCache() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "clear the cache");

    StringBuffer curlCmd = new StringBuffer("curl --silent ");
    curlCmd
        .append("-d 'action=clear' ")
        .append("-H 'host: ")
        .append(domain.getDomainUid())
        .append(".org' ")
        .append("http://")
        .append(domain.getHostNameForCurl())
        .append(":")
        .append(domain.getLoadBalancerWebPort())
        .append("/")
        .append(appToDeploy)
        .append("/")
        .append(appToDeploy);
    return TestUtils.exec(curlCmd.toString());
  }
}

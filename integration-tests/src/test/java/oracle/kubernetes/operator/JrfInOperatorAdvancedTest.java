// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.DbUtils;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.JrfDomain;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/** Test Cases to cover bugs while testing Operator for JRF domains. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class JrfInOperatorAdvancedTest extends BaseTest {

  // property file used to customize operator properties for operator inputs yaml
  private static final String JRF_OPERATOR_FILE_1 = "jrfoperator1.yaml";
  // file used to customize domain properties for domain, PV and LB inputs yaml
  private static final String JRF_DOMAIN_ON_PV_WLST_FILE = "jrfdomainonpvwlst.yaml";
  // property file for oracle db information
  private static final String DB_PROP_FILE = "oracledb.properties";
  private static Operator operator1;
  private static String rcuPodName;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. It also creates Oracle DB pod which used for
   * RCU.
   *
   * @throws Exception - if an error occurs when load property file or create DB pod
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);
  
      // create DB used for jrf domain
      DbUtils.createOracleDB(DB_PROP_FILE);
  
      // run RCU first
      DbUtils.deleteNamespace(DbUtils.DEFAULT_RCU_NAMESPACE);
      DbUtils.createNamespace(DbUtils.DEFAULT_RCU_NAMESPACE);
      rcuPodName = DbUtils.createRcuPod(DbUtils.DEFAULT_RCU_NAMESPACE);
  
      // TODO: reconsider the logic to check the db readiness
      // The jrfdomain can not find the db pod even the db pod shows ready, sleep more time
      logger.info("waiting for the db to be visible to rcu script ...");
      Thread.sleep(20000);
    }
  }

  /**
   * This method will run once after all test methods are finished. It Releases k8s cluster lease,
   * archives result, pv directories.
   *
   * @throws Exception - if any error occurs
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");
  
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
  
      logger.info("SUCCESS");
    }
  }

  /**
   * test the Rolling restart behavior in the jrf domain cluster level currently during the rolling
   * restart, all managed servers may be in the not ready state. Bugs 29678557, 29720185, the test
   * will fail before the bugs are fixed
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testJrfDomainClusterRestartVersion() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain1 = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domain1Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain1Map.put("domainUID", "jrfrestart");
      domain1Map.put("adminNodePort", 30705);
      domain1Map.put("t3ChannelPort", 30025);
      domain1Map.put("voyagerWebPort", 30309);
      domain1Map.put("rcuSchemaPrefix", "jrfrestart");
      domain1Map.put("initialManagedServerReplicas", 4);

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain1Map);

      // create domain
      logger.info("Creating Domain & verifying the domain creation");
      domain1 = new JrfDomain(domain1Map);
      domain1.verifyDomainCreated();

      String originalYaml =
          getUserProjectsDir() + "/weblogic-domains/" + domain1.getDomainUid() + "/domain.yaml";

      // Rolling restart the cluster by setting restartVersion at the cluster level
      // Modify the original domain yaml to include restartVersion in cluster level
      DomainCrd crd = new DomainCrd(originalYaml);
      Map<String, Object> clusterRestartVersion = new HashMap();
      clusterRestartVersion.put("restartVersion", "clusterV1");
      clusterRestartVersion.put("maxUnavailable", new Integer(2));
      crd.addObjectNodeToCluster(domain1.getClusterName(), clusterRestartVersion);
      String modYaml = crd.getYamlTree();
      logger.info(modYaml);

      // Write the modified yaml to a new file
      String restartTmpDir = getResultDir() + "/restarttemp";
      Files.createDirectories(Paths.get(restartTmpDir));
      Path path = Paths.get(restartTmpDir, "restart.cluster.yaml");
      logger.log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain crd
      logger.log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      logger.info(exec.stdout());

      int expectedMsPodsCount =
          (Integer) domain1.getDomainMap().get("initialManagedServerReplicas");
      // TODO: this verification will fail due to bug 29678557
      logger.info("Verifying the number of not ready MS pods can not exceed maxUnavailable value");
      verifyMsPodsNotReadyCountNotExceedMaxUnAvailable(domain1, expectedMsPodsCount, 2);

      // TODO: this verification will fail due to bug 29720185
      logger.info("Verifying the number of MS pods");
      if (getMsPodsCount(domain1) != expectedMsPodsCount) {
        throw new Exception(
            "The number of MS pods is not right, expect: "
                + expectedMsPodsCount
                + ", got: "
                + getMsPodsCount(domain1));
      }

      testCompletedSuccessfully = true;

    } finally {
      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        domain1.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * In create domain input yaml file, set exposeAdminNodePort to false and exposeAdminT3Channel to
   * true, make sure the managed server pods are created. Bug 29684570, the test will fail before
   * the bug is fixed
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testJrfDomainMsPodCreated() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain1 = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domain1Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain1Map.put("domainUID", "jrfmspod");
      domain1Map.put("adminNodePort", 30706);
      domain1Map.put("t3ChannelPort", 30026);
      domain1Map.put("voyagerWebPort", 30310);
      domain1Map.put("rcuSchemaPrefix", "jrfmspod");
      domain1Map.put("initialManagedServerReplicas", 4);
      domain1Map.put("exposeAdminNodePort", false);

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain1Map);

      // create domain
      logger.info("Creating Domain & verifying the domain creation");
      domain1 = new JrfDomain(domain1Map);
      domain1.verifyDomainCreated();

      testCompletedSuccessfully = true;

    } finally {
      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        domain1.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * In create domain input yaml file, set createDomainScriptsMountPath to non-default value, make
   * sure the create domain sample script works. Bug 29683926, the test will fail before the bug is
   * fixed
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testJrfDomainCreateDomainScriptsMountPath() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain1 = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domain1Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain1Map.put("domainUID", "jrfcdsmp");
      domain1Map.put("adminNodePort", 30707);
      domain1Map.put("t3ChannelPort", 30027);
      domain1Map.put("voyagerWebPort", 30311);
      domain1Map.put("rcuSchemaPrefix", "jrfcdsmp");
      // set the createDomainScriptsMountPath to non-default value
      domain1Map.put("createDomainScriptsMountPath", "/u01/weblogic1");

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain1Map);

      // create domain
      logger.info("Creating Domain & verifying the domain creation");
      domain1 = new JrfDomain(domain1Map);
      domain1.verifyDomainCreated();

      testCompletedSuccessfully = true;

    } finally {
      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        domain1.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * In createFMWDomain.py file, set administration_port_enabled to true. Make sure the admin server
   * pod is running and ready. All the managed server pods are created. Bug 29657663, the test will
   * fail before the bug is fixed
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testJrfDomainAdminPortEnabled() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain1 = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domain1Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain1Map.put("domainUID", "jrfape");
      domain1Map.put("adminNodePort", 30708);
      domain1Map.put("t3ChannelPort", 30028);
      domain1Map.put("voyagerWebPort", 30312);
      domain1Map.put("rcuSchemaPrefix", "jrfape");
      domain1Map.put(
          "createDomainPyScript",
          "integration-tests/src/test/resources/domain-home-on-pv/create-jrfdomain-admin-port-enabled.py");
      // Use -Dweblogic.ssl.AcceptKSSDemoCertsEnabled=true so that managed servers can connect
      // to admin server using SSL without running into host name verifcation check error
      // in default JRF domain that uses KSS demo identity and trust
      // https://docs.oracle.com/middleware/12213/wls/SECMG/kss.htm#SECMG673tm#ADMRF202
      domain1Map.put(
          "javaOptions",
          "-Dweblogic.StdoutDebugEnabled=false -Dweblogic.ssl.AcceptKSSDemoCertsEnabled=true");

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain1Map);

      // create domain
      logger.info("Creating Domain & verifying the domain creation");
      domain1 = new JrfDomain(domain1Map, true);
      domain1.verifyDomainCreated();

      testCompletedSuccessfully = true;

    } finally {
      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        domain1.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * In create domain input file, set exposeAdminT3Channel to true. Make sure the admin t3 channel
   * is exposed. Bug 29591809, the test will fail before the bug is fixed
   *
   * @throws Exception - if any error occurs
   */
  @Test
  public void testJrfDomainAdminT3Channel() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethod = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethod);

    if (operator1 == null) {
      operator1 = TestUtils.createOperator(JRF_OPERATOR_FILE_1);
    }

    JrfDomain domain1 = null;
    boolean testCompletedSuccessfully = false;
    try {
      Map<String, Object> domain1Map = TestUtils.loadYaml(JRF_DOMAIN_ON_PV_WLST_FILE);
      domain1Map.put("domainUID", "jrft3");
      domain1Map.put("adminNodePort", 30709);
      domain1Map.put("t3ChannelPort", 30029);
      domain1Map.put("voyagerWebPort", 30313);
      domain1Map.put("rcuSchemaPrefix", "jrft3");

      // run RCU script to load db schema
      DbUtils.runRcu(rcuPodName, domain1Map);

      // create domain
      logger.info("Creating Domain & verifying the domain creation");
      domain1 = new JrfDomain(domain1Map);
      domain1.verifyDomainCreated();

      // verify the Admin T3Channel is exposed
      testAdminT3Channel(domain1);

      testCompletedSuccessfully = true;

    } finally {
      if (domain1 != null && (JENKINS || testCompletedSuccessfully)) {
        domain1.destroy();
      }
    }
    logger.info("SUCCESS - " + testMethod);
  }

  /**
   * verify that during the rolling restart, the number of managed server pods which are not ready
   * can not exceed the maxUnAvailable value.
   *
   * @param domain - jrfdomain
   * @param expectedMsPodsCount - total number of managed server pods expected
   * @param maxUnavailable - maxUnAvailable value of the managed server pods
   * @throws Exception - if any error occurs
   */
  private void verifyMsPodsNotReadyCountNotExceedMaxUnAvailable(
      JrfDomain domain, int expectedMsPodsCount, int maxUnavailable) throws Exception {
    int i = 0;
    // first wait for the ms pods to be in terminating state
    while (i < getMaxIterationsPod() && getMsPodsNotReadyCount(domain) < 1) {
      Thread.sleep(2000);
      i++;
    }
    if (getMsPodsNotReadyCount(domain) == 0) {
      throw new Exception("hit timeout while waiting for the first MS pod to be restarted");
    }

    // check the not ready MS pod count should not exceed maxUnavailable value
    i = 0;
    int msPodRunningAndReadyCount = getMsPodsRunningAndReadyCount(domain);
    while (i < getMaxIterationsPod() * 8 && msPodRunningAndReadyCount != expectedMsPodsCount) {
      int msPodsNotReadyCount = getMsPodsNotReadyCount(domain);
      logger.info(
          "Iter ["
              + i
              + "/"
              + getMaxIterationsPod() * 8
              + "]: MS Pod Not Ready Count: "
              + msPodsNotReadyCount
              + "; MS Pod Running and Ready Count: "
              + msPodRunningAndReadyCount);
      if (msPodsNotReadyCount > maxUnavailable) {
        throw new Exception("number of not ready managed server pods exceeds " + maxUnavailable);
      }
      Thread.sleep(2000);
      i++;
      msPodRunningAndReadyCount = getMsPodsRunningAndReadyCount(domain);
    }
  }

  /**
   * get the number of managed server pods which are not ready.
   *
   * @param domain - jrfdomain
   * @return - returns the number of managed server pods which are not ready
   * @throws Exception - if any error occurs
   */
  private int getMsPodsNotReadyCount(JrfDomain domain) throws Exception {

    String domainuid = (String) domain.getDomainMap().get("domainUID");
    String managedServerNameBase = (String) domain.getDomainMap().get("managedServerNameBase");
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pods -n ")
        .append(domain.getDomainNs())
        .append(" | grep ")
        .append(domainuid + "-" + managedServerNameBase)
        .append(" | grep 0/1 | wc -l");
    ExecResult result = TestUtils.exec(cmd.toString());

    return Integer.parseInt(result.stdout());
  }

  /**
   * get the number of managed server pods which are running and ready.
   *
   * @param domain - jrfdomain
   * @return - returns the number of managed server pods which are running and ready
   * @throws Exception - if any error occurs
   */
  private int getMsPodsRunningAndReadyCount(JrfDomain domain) throws Exception {

    String domainuid = (String) domain.getDomainMap().get("domainUID");
    String managedServerNameBase = (String) domain.getDomainMap().get("managedServerNameBase");
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pods -n ")
        .append(domain.getDomainNs())
        .append(" | grep ")
        .append(domainuid + "-" + managedServerNameBase)
        .append(" | grep 1/1 | grep Running | wc -l");
    ExecResult result = TestUtils.exec(cmd.toString());

    return Integer.parseInt(result.stdout());
  }

  /**
   * get the number of managed server pods created.
   *
   * @param domain - jrfdomain
   * @return - returns the number of managed server pods created
   * @throws Exception - if any error occurs
   */
  private int getMsPodsCount(JrfDomain domain) throws Exception {
    String domainuid = (String) domain.getDomainMap().get("domainUID");
    String managedServerNameBase = (String) domain.getDomainMap().get("managedServerNameBase");
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pods -n ")
        .append(domain.getDomainNs())
        .append(" | grep ")
        .append(domainuid + "-" + managedServerNameBase)
        .append(" | wc -l");
    ExecResult result = TestUtils.exec(cmd.toString());

    return Integer.parseInt(result.stdout());
  }
}

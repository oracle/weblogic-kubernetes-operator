// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator(s) and multiple domains which are managed by the
 * Operator(s).
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITServerDiscovery extends BaseTest {

  // property file used to customize operator properties for operator inputs yaml
  private static String operatorYmlFile = "operator1.yaml";

  // file used to customize domain properties for domain, PV and LB inputs yaml
  private static String domainonpvwlstFile = "domainonpvwlst.yaml";

  // property file used to configure constants for integration tests
  private static String appPropsFile = "OperatorIT.properties";
  private static String domainYaml;
  private static String testYamlFileName = "custom-domaint.yaml";
  private static String testDomainYamlFile;

  private static Operator operator;
  private static Domain domain;

  private static boolean QUICKTEST;
  private static boolean SMOKETEST;
  private static boolean JENKINS;
  private static boolean INGRESSPERDOMAIN = true;

  // Set QUICKTEST env var to true to run a small subset of tests.
  // Set SMOKETEST env var to true to run an even smaller subset
  // of tests, plus leave domain1 up and running when the test completes.
  // set INGRESSPERDOMAIN to false to create LB's ingress by kubectl yaml file
  static {
    QUICKTEST =
        System.getenv("QUICKTEST") != null && System.getenv("QUICKTEST").equalsIgnoreCase("true");
    SMOKETEST =
        System.getenv("SMOKETEST") != null && System.getenv("SMOKETEST").equalsIgnoreCase("true");
    if (SMOKETEST) QUICKTEST = true;
    if (System.getenv("JENKINS") != null) {
      JENKINS = new Boolean(System.getenv("JENKINS")).booleanValue();
    }
    if (System.getenv("INGRESSPERDOMAIN") != null) {
      INGRESSPERDOMAIN = new Boolean(System.getenv("INGRESSPERDOMAIN")).booleanValue();
    }
  }

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes.
   *
   * <p>It also create operator and verify its deployed successfully. Create domain and verify
   * domain is created.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      // initialize test properties and create the directories
      initialize(appPropsFile);

      // create operator1
      if (operator == null) {
        logger.info("Creating Operator & waiting for the script to complete execution");
        operator = TestUtils.createOperator(operatorYmlFile);
      }

      // create domain
      if (domain == null) {
        logger.info("Creating WLS Domain & waiting for the script to complete execution");
        domain = TestUtils.createDomain(domainonpvwlstFile);
        domain.verifyDomainCreated();
      }

      domainYaml =
          BaseTest.getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";

      // create test domain yaml file
      testDomainYamlFile = BaseTest.getResultDir() + "/" + testYamlFileName;

      Path sourceFile = Paths.get(domainYaml);
      Path targetFile = Paths.get(testDomainYamlFile);

      Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

      String content = new String(Files.readAllBytes(targetFile), StandardCharsets.UTF_8);
      content = content.replaceAll("replicas: 2", "replicas: 3");
      Files.write(targetFile, content.getBytes(StandardCharsets.UTF_8));
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories
   *
   * @throws Exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      tearDown();

      logger.info("SUCCESS");
    }
  }

  /**
   * Verify that a running Operator connects to a pre-configed and newly started MS
   *
   * @throws Exception
   */
  @Test
  public void testOPConnToNewMS() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    String cmd = "kubectl apply -f " + testDomainYamlFile;
    logger.info("Start a new managed server using command:\n" + cmd);
    ExecResult result = ExecCommand.exec(cmd);

    logger.info("Check the newly created managed server is running");
    int replicas = 3;
    varifyPodReady(replicas);

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Verify that after bounce Operator, it reconnects to a pre-configed and newly started MS
   *
   * @throws Exception
   */
  @Test
  public void testOPReconnToNewMS() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Stop the Operator");
    stopOperator();

    String cmd = "kubectl apply -f " + testDomainYamlFile;
    logger.info("Start a new managed server, managed-server3 using command:\n" + cmd);
    ExecResult result = ExecCommand.exec(cmd);

    logger.info("Start the Operator");
    startOperator();

    logger.info("Check the newly created managed-server3 is running");
    int replicas = 3;
    varifyPodReady(replicas);

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Verify that after bounce Operator, it discovers running servers and connect to them in place.
   * It's able to scale down the cluster
   *
   * @throws Exception
   */
  @Test
  public void testOPReconnToRunningMSAndScaleDown() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Restart the Operator");
    stopOperator();
    startOperator();

    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    logger.info("Check managed-server1 is still running");
    int replicas = 1;
    varifyPodReady(replicas);

    // restore the test env
    int msIncrNum = 1;
    scaleUpAndVarify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Verify that after bounce Operator, it discovers running servers and connect to them in place.
   * It's able to scale up the cluster
   *
   * @throws Exception
   */
  @Test
  public void testOPReconnToRunningMSAndScaleUp() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Restart the Operator");
    stopOperator();
    startOperator();

    int msIncrNum = 1;
    scaleUpAndVarify(msIncrNum);

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Verify that after bounce Operator, it discovers running servers and connect to them in place.
   * It's able to scale up the cluster when domain has App deployed
   *
   * @throws Exception
   */
  @Test
  public void testOPReconnToRunningMSWApp() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    domain.deployWebAppViaREST(
        BaseTest.TESTWEBAPP,
        BaseTest.getProjectRoot() + "/src/integration-tests/apps/testwebapp.war",
        BaseTest.getUsername(),
        BaseTest.getPassword());

    domain.verifyWebAppLoadBalancing(BaseTest.TESTWEBAPP);

    logger.info("Restart the Operator");
    stopOperator();
    startOperator();

    int msIncrNum = 1;
    scaleUpAndVarify(msIncrNum);

    // Give each ms in the cluster to respond
    int replicaCnt = getReplicaCnt();
    for (int i = 0; i < replicaCnt + 2; i++) {
      domain.verifyWebAppLoadBalancing(BaseTest.TESTWEBAPP);
    }

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Verify that after bounce Operator and admin server, the Operator discovers running ms and
   * connect to them in place. It's able to scale down the cluster
   *
   * @throws Exception
   */
  @Test
  public void testOPAdminReconnToDomain() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();
    String adminServerName = (String) domainMap.get("adminServerName");
    String adminServerPodName = domainUid + "-" + adminServerName;

    logger.info("Stop the Operator");
    stopOperator();

    String cmd = "kubectl delete po/" + adminServerPodName + " -n " + domainNS;
    logger.info("Stop admin server <" + adminServerPodName + "> using command:\n" + cmd);
    ExecResult result = ExecCommand.exec(cmd);

    logger.info("Checking if admin pod <" + adminServerPodName + "> is deleted");
    TestUtils.checkPodDeleted(adminServerPodName, domainNS);

    logger.info("Start the Operator and admin server");
    startOperator();

    logger.info("Checking admin server <" + adminServerPodName + "> is running");
    TestUtils.checkPodReady(adminServerPodName, domainNS);

    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    // restore the test env
    int msIncrNum = 1;
    scaleUpAndVarify(msIncrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Verify that after bounce the Operator, it discovers stopped ms and re-start them.
   *
   * @throws Exception
   */
  @Test
  public void testOPMSReconnToDomain() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    String domainUid = domain.getDomainUid();

    logger.info("Stop the Operator");
    stopOperator();

    // Delte all managed servers in the cluster
    int replicaCnt = getReplicaCnt();
    for (int i = 1; i <= replicaCnt; i++) {
      String msPodName = domainUid + "-" + managedServerNameBase + i;
      String cmd = "kubectl delete po/" + msPodName + " -n " + domainNS;
      logger.info("Stop managed server <" + msPodName + "> using command:\n" + cmd);
      ExecResult result = ExecCommand.exec(cmd);

      logger.info("Checking if ms pod <" + msPodName + "> is deleted");
      TestUtils.checkPodDeleted(msPodName, domainNS);
    }

    logger.info("Start the Operator");
    startOperator();

    for (int i = 1; i <= replicaCnt; i++) {
      String msPodName = domainUid + "-" + managedServerNameBase + i;
      logger.info("Checking if ms pod <" + msPodName + "> is running");
      TestUtils.checkPodReady(msPodName, domainNS);
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Verify that when a ms dead, the Operator re-starts it.
   *
   * @throws Exception
   */
  @Test
  public void testOPRestartDeadMS() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    String domainUid = domain.getDomainUid();

    // Delte a managed servers in the cluster
    String msPodName = domainUid + "-" + managedServerNameBase + "1";
    String cmd = "kubectl delete po/" + msPodName + " -n " + domainNS;
    logger.info("Stop managed server <" + msPodName + "> using command:\n" + cmd);
    ExecResult result = ExecCommand.exec(cmd);

    logger.info("Wait 10 seconds for ms to be restarted");
    Thread.sleep(10);

    logger.info("Verify that <" + msPodName + "> is restarted by Operator");
    TestUtils.checkPodReady(msPodName, domainNS);

    logger.info("SUCCESS - " + testMethodName);
  }

  private void scaleDownAndVarify(int decrNum) throws Exception {
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = domainMap.get("namespace").toString();
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    String clusterName = domainMap.get("clusterName").toString();

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    int replicas = replicaCnt - decrNum;
    String podName = domainUid + "-" + managedServerNameBase + replicaCnt;

    logger.info("Scale domain " + domainUid + " down to " + replicas + " managed servers");
    operator.scale(domainUid, clusterName, replicas);

    logger.info("Checking if managed pod <" + podName + "> is deleted");
    TestUtils.checkPodDeleted(podName, domainNS);

    replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != replicas) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled down size "
              + replicaCnt
              + "/"
              + replicas);
    }
  }

  private void scaleUpAndVarify(int incrNum) throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = domainMap.get("namespace").toString();
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    String clusterName = domainMap.get("clusterName").toString();

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    int replicas = replicaCnt + incrNum;
    String podName = domainUid + "-" + managedServerNameBase + replicas;

    logger.info("Scale domain " + domainUid + " up to " + replicas + " managed servers");
    operator.scale(domainUid, clusterName, replicas);

    varifyPodReady(replicas);
  }

  /**
   * Verify that after bounce Operator, it reconnects to a pre-configed and newly started MS
   *
   * @throws Exception
   */
  private void varifyPodReady(int replicas) throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = domainMap.get("namespace").toString();
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    String clusterName = domainMap.get("clusterName").toString();

    String podName = domainUid + "-" + managedServerNameBase + replicas;

    logger.info("Checking if managed pod <" + podName + "> is created");
    TestUtils.checkPodCreated(podName, domainNS);

    logger.info("Checking if managed server <" + podName + "> is running");
    TestUtils.checkPodReady(podName, domainNS);

    logger.info("Checking if managed service <" + podName + "> is created");
    TestUtils.checkServiceCreated(podName, domainNS);

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != replicas) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled up size "
              + replicaCnt
              + "/"
              + replicas);
    }
  }

  private void stopOperator() throws Exception {
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    String operNS = operator.getOperatorNamespace();
    String cmd = "kubectl scale --replicas=0 deployment/weblogic-operator" + " -n " + operNS;
    logger.info("Undeploy Operator using command:\n" + cmd);

    ExecResult result = ExecCommand.exec(cmd);

    logger.info("stdout : \n" + result.stdout());

    logger.info("Checking if operator pod is deleted");
    TestUtils.checkPodDeleted("", operNS);
  }

  private void startOperator() throws Exception {
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    String operNS = operator.getOperatorNamespace();
    String cmd = "kubectl scale --replicas=1 deployment/weblogic-operator" + " -n " + operNS;
    logger.info("Deploy Operator using command:\n" + cmd);

    ExecResult result = ExecCommand.exec(cmd);

    logger.info("Checking if operator pod is running");
    operator.verifyPodCreated();
    operator.verifyOperatorReady();
  }

  private int getReplicaCnt() throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = domainMap.get("namespace").toString();
    String clusterName = domainMap.get("clusterName").toString();

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);

    return replicaCnt;
  }
}

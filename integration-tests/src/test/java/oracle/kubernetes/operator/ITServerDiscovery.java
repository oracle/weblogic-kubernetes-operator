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
 * <p>This test is used for creating Operator(s) and domain(s) which are managed by the Operator(s).
 * And to test WLS server discovery feature.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITServerDiscovery extends BaseTest {
  private static String testDomainYamlFile;

  private static Operator operator;
  private static Domain domain;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. It also creates Operator, domain and a test
   * domain yaml file.
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (!QUICKTEST) {
      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);

      // Create operator1
      if (operator == null) {
        logger.info("Creating Operator & waiting for the script to complete execution");
        operator = TestUtils.createOperator(OPERATOR1_YAML);
      }

      // create domain
      if (domain == null) {
        logger.info("Creating WLS Domain & waiting for the script to complete execution");
        domain = TestUtils.createDomain(DOMAINONPV_WLST_YAML);
        domain.verifyDomainCreated();
      }

      final String testYamlFileName = "custom-domaint.yaml";

      String domainYaml =
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
   * Releases k8s cluster lease, archives result, pv directories.
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
   * Stop Operator. Start a managed server by applying a modified domain.yaml. Restart Operator and
   * verify that it connects to this newly started managed server.
   *
   * @throws Exception
   */
  @Test
  public void testOPConnToNewMS() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Stop the Operator");
    operator.stopUsingReplicas();

    String cmd = "kubectl apply -f " + testDomainYamlFile;
    logger.info("Start a new managed server, managed-server3 using command:\n" + cmd);
    ExecResult result = ExecCommand.exec(cmd);

    logger.info("Start the Operator");
    operator.startUsingReplicas();

    logger.info("Check the newly created managed-server3 is running");
    int replicas = 3;
    varifyPodReady(replicas);

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Stop and restart Operator and verify that it discovers running servers and a newly started
   * server by scaling up cluster. Verify that the cluster scale up is noy impacted.
   *
   * @throws Exception
   */
  @Test
  public void testOPReconnToRunningMSAndScaleUp() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Restart the Operator");
    operator.restartUsingReplicas();

    int msIncrNum = 1;
    scaleUpAndVarify(msIncrNum);

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Stop Operator and admin server. Restart Operator. Verify that it restarts admin server and
   * discovers running servers. Verify that the cluster scale up is noy impacted.
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
    operator.stopUsingReplicas();

    String cmd = "kubectl delete po/" + adminServerPodName + " -n " + domainNS;
    logger.info("Stop admin server <" + adminServerPodName + "> using command:\n" + cmd);
    ExecResult result = ExecCommand.exec(cmd);

    logger.info("Checking if admin pod <" + adminServerPodName + "> is deleted");
    TestUtils.checkPodDeleted(adminServerPodName, domainNS);

    logger.info("Start the Operator and admin server");
    operator.startUsingReplicas();

    logger.info("Checking admin server <" + adminServerPodName + "> is running");
    TestUtils.checkPodReady(adminServerPodName, domainNS);

    int msIncrNum = 1;
    scaleUpAndVarify(msIncrNum);

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndVarify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Stop Operator. Kill all managed servers. Restart Operator. Verify that it restarts all managed
   * servers.
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
    operator.stopUsingReplicas();

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
    operator.startUsingReplicas();

    for (int i = 1; i <= replicaCnt; i++) {
      String msPodName = domainUid + "-" + managedServerNameBase + i;
      logger.info("Checking if ms pod <" + msPodName + "> is running");
      TestUtils.checkPodReady(msPodName, domainNS);
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Stop Operator. Kill one managed server. Restart Operator. Verify that it restarts the killed
   * managed server.
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

  private int getReplicaCnt() throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = domainMap.get("namespace").toString();
    String clusterName = domainMap.get("clusterName").toString();

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);

    return replicaCnt;
  }
}

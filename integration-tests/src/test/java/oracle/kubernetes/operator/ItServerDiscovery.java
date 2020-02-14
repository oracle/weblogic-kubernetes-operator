// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import oracle.kubernetes.operator.utils.Domain;
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
public class ItServerDiscovery extends BaseTest {
  private static String testDomainYamlFile;

  private static Operator operator;
  private static Domain domain;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. It also creates Operator, domain and a test
   * domain yaml file.
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
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
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      logger.info("++++++++++++++++++++++++++++++++++");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");
  
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
  
      logger.info("SUCCESS");
    }    
  }

  /**
   * Stop operator and apply the modified domain.yaml with replicas count increased. Restart
   * operator and make sure the cluster is scaled up accordingly.
   *
   * @throws Exception exception
   */
  @Test
  public void testOpConnToNewMS() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info("Stop the Operator");
    operator.stopUsingReplicas();

    String cmd = "kubectl apply -f " + testDomainYamlFile;
    logger.info("Start a new managed server, managed-server3 using command:\n" + cmd);
    TestUtils.exec(cmd);

    logger.info("Restart the Operator");
    operator.startUsingReplicas();

    logger.info("Check if the newly created managed-server3 is running");
    int replicas = 3;
    verifyPodReady(replicas);

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndverify(msDecrNum);

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Stop Operator. Kill admin server and all managed servers in domain cluster. Restart Operator.
   * Verify that it restarts admin server and all managed servers in domain cluster.
   *
   * @throws Exception exception
   */
  @Test
  public void testOpReconnToDomain() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();
    String adminServerName = (String) domainMap.get("adminServerName");
    String adminServerPodName = domainUid + "-" + adminServerName;
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    final String clusterName = domainMap.get("clusterName").toString();

    logger.info("Stop the Operator");
    operator.stopUsingReplicas();

    // Stop admin server
    String cmd = "kubectl delete po/" + adminServerPodName + " -n " + domainNS;
    logger.info("Stop admin server <" + adminServerPodName + "> using command:\n" + cmd);
    TestUtils.exec(cmd);

    logger.info("Check if admin pod <" + adminServerPodName + "> is deleted");
    TestUtils.checkPodDeleted(adminServerPodName, domainNS);

    // Stop all managed servers in the cluster
    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);

    for (int i = 1; i <= replicaCnt; i++) {
      String msPodName = domainUid + "-" + managedServerNameBase + i;
      cmd = "kubectl delete po/" + msPodName + " -n " + domainNS;
      logger.info("Stop managed server <" + msPodName + "> using command:\n" + cmd);
      TestUtils.exec(cmd);

      logger.info("Checking if ms pod <" + msPodName + "> is deleted");
      TestUtils.checkPodDeleted(msPodName, domainNS);
    }

    logger.info("Restart the Operator");
    operator.startUsingReplicas();

    logger.info("Check if admin pod <" + adminServerPodName + "> is running");
    TestUtils.checkPodReady(adminServerPodName, domainNS);

    for (int i = 1; i <= replicaCnt; i++) {
      String msPodName = domainUid + "-" + managedServerNameBase + i;
      logger.info("Checking if ms pod <" + msPodName + "> is running");
      TestUtils.checkPodReady(msPodName, domainNS);
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  private void scaleDownAndverify(int decrNum) throws Exception {

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

  private void verifyPodReady(int replicas) throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = domainMap.get("namespace").toString();
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    final String clusterName = domainMap.get("clusterName").toString();

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
}

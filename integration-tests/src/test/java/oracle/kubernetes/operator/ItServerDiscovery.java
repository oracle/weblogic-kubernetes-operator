// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
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

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator(s) and domain(s) which are managed by the Operator(s).
 * And to test WLS server discovery feature.
 */
@TestMethodOrder(Alphanumeric.class)
public class ItServerDiscovery extends BaseTest {
  private static String testDomainYamlFile;

  private static Operator operator;
  private static Domain domain;
  private static String domainNS;
  private static String testClassName;
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
      String testClassNameShort = "itdiscovery";
      // create operator1
      if (operator == null) {
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassNameShort);
        operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator);
        domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS);
      }
      // create domain
      if (domain == null) {
        LoggerHelper.getLocal().log(Level.INFO, "Creating WLS Domain & waiting for the script to complete execution");
        Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassNameShort);
        domainMap.put("namespace", domainNS);
        domain = TestUtils.createDomain(domainMap);
        domain.verifyDomainCreated();
      }

      final String testYamlFileName = "custom-domaint.yaml";

      String domainYaml =
          getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";

      // create test domain yaml file
      testDomainYamlFile = getResultDir() + "/" + testYamlFileName;

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
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      tearDown(new Object() {
      }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

      LoggerHelper.getLocal().log(Level.INFO, "SUCCESS");
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO, "Stop the Operator");
    operator.stopUsingReplicas();

    String cmd = "kubectl apply -f " + testDomainYamlFile;
    LoggerHelper.getLocal().log(Level.INFO, "Start a new managed server, managed-server3 using command:\n" + cmd);
    TestUtils.exec(cmd);

    LoggerHelper.getLocal().log(Level.INFO, "Restart the Operator");
    operator.startUsingReplicas();

    LoggerHelper.getLocal().log(Level.INFO, "Check if the newly created managed-server3 is running");
    int replicas = 3;
    verifyPodReady(replicas);

    // restore the test env
    int msDecrNum = 1;
    scaleDownAndverify(msDecrNum);

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Stop Operator. Kill admin server and all managed servers in domain cluster. Restart Operator.
   * Verify that it restarts admin server and all managed servers in domain cluster.
   *
   * @throws Exception exception
   */
  @Test
  public void testOpReconnToDomain() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = domainMap.get("namespace").toString();
    String domainUid = domain.getDomainUid();
    String adminServerName = (String) domainMap.get("adminServerName");
    String adminServerPodName = domainUid + "-" + adminServerName;
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    final String clusterName = domainMap.get("clusterName").toString();

    LoggerHelper.getLocal().log(Level.INFO, "Stop the Operator");
    operator.stopUsingReplicas();

    // Stop admin server
    String cmd = "kubectl delete po/" + adminServerPodName + " -n " + domainNS;
    LoggerHelper.getLocal().log(Level.INFO, "Stop admin server <" + adminServerPodName + "> using command:\n" + cmd);
    TestUtils.exec(cmd);

    LoggerHelper.getLocal().log(Level.INFO, "Check if admin pod <" + adminServerPodName + "> is deleted");
    TestUtils.checkPodDeleted(adminServerPodName, domainNS);

    // Stop all managed servers in the cluster
    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);

    for (int i = 1; i <= replicaCnt; i++) {
      String msPodName = domainUid + "-" + managedServerNameBase + i;
      cmd = "kubectl delete po/" + msPodName + " -n " + domainNS;
      LoggerHelper.getLocal().log(Level.INFO, "Stop managed server <" + msPodName + "> using command:\n" + cmd);
      TestUtils.exec(cmd);

      LoggerHelper.getLocal().log(Level.INFO, "Checking if ms pod <" + msPodName + "> is deleted");
      TestUtils.checkPodDeleted(msPodName, domainNS);
    }

    LoggerHelper.getLocal().log(Level.INFO, "Restart the Operator");
    operator.startUsingReplicas();

    LoggerHelper.getLocal().log(Level.INFO, "Check if admin pod <" + adminServerPodName + "> is running");
    TestUtils.checkPodReady(adminServerPodName, domainNS);

    for (int i = 1; i <= replicaCnt; i++) {
      String msPodName = domainUid + "-" + managedServerNameBase + i;
      LoggerHelper.getLocal().log(Level.INFO, "Checking if ms pod <" + msPodName + "> is running");
      TestUtils.checkPodReady(msPodName, domainNS);
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
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

    LoggerHelper.getLocal().log(Level.INFO, "Scale domain " + domainUid + " down to " + replicas + " managed servers");
    operator.scale(domainUid, clusterName, replicas);

    LoggerHelper.getLocal().log(Level.INFO, "Checking if managed pod <" + podName + "> is deleted");
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

    LoggerHelper.getLocal().log(Level.INFO, "Checking if managed pod <" + podName + "> is created");
    TestUtils.checkPodCreated(podName, domainNS);

    LoggerHelper.getLocal().log(Level.INFO, "Checking if managed server <" + podName + "> is running");
    TestUtils.checkPodReady(podName, domainNS);

    LoggerHelper.getLocal().log(Level.INFO, "Checking if managed service <" + podName + "> is created");
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

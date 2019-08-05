// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/** Integration tests for testing the init container for WebLogic server pods. */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItInitContainers extends BaseTest {

  private static Domain domain = null;
  private static Operator operator;
  private static String domainUid = "domaininitcont";
  private static String initContainerTmpDir = "";
  private static String originalYaml;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. Create Operator1 and domainOnPVUsingWLST
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    logger.info("staticPrepare------Begin");
    // initialize test properties and create the directories
    if (!QUICKTEST) {
      initialize(APP_PROPS_FILE);

      logger.info("Checking if operator and domain are running, if not creating");
      if (operator == null) {
        operator = TestUtils.createOperator(OPERATOR1_YAML);
      }
      initContainerTmpDir = BaseTest.getResultDir() + "/initconttemp";
      Files.createDirectories(Paths.get(initContainerTmpDir));

      domain = createInitContdomain();
      originalYaml =
          BaseTest.getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";
      Assert.assertNotNull(domain);
    }
    logger.info("staticPrepare------End");
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (!QUICKTEST) {
      logger.info("staticUnPrepare------Begin");
      if (domain != null) {
        destroyInitContdomain();
      }
      if (operator != null) {
        operator.destroy();
      }
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());
      logger.info("staticUnPrepare------End");
    }
  }

  /**
   * creates the init container domain on PV.
   *
   * @return created domain Domain
   * @throws Exception when domain creation fails
   */
  private static Domain createInitContdomain() throws Exception {
    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
    domainMap.put("domainUID", domainUid);
    domainUid = (String) domainMap.get("domainUID");
    logger.info("Creating and verifying the domain creation with domainUid: " + domainUid);
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }

  /**
   * destroys the running domain.
   *
   * @throws Exception when domain destruction fails
   */
  private static void destroyInitContdomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }

  /**
   * Add initContainers at domain spec level and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testDomainInitContainer() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String[] pods = {domainUid + "-" + domain.getAdminServerName(), domainUid + "-managed-server1"};

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);
    testInitContainer(modYaml);
    for (String pod : pods) {
      logger.info("Verifying if the pods are recreated with initialization");
      verifyPodInitialized(pod);
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     weblogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testAdminServerInitContainer() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String adminPodName = domainUid + "-" + domain.getAdminServerName();

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("adminServer", null, null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);
    testInitContainer(modYaml);
    logger.info("Verifying if the admin server pod is recreated with initialization");
    verifyPodInitialized(adminPodName);
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     weblogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testClusterInitContainer() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String adminPodName = domainUid + "-" + domain.getAdminServerName();
    final String ms2PodName = domainUid + "-managed-server2";

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("clusters", "cluster-1", null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);
    testInitContainer(modYaml);
    TestUtils.checkPodReady(adminPodName, domain.getDomainNs());
    logger.info("Verifying if the managed server pods are recreated with initialization");
    verifyPodInitialized(ms2PodName);
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testMsInitContainer() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String adminPodName = domainUid + "-" + domain.getAdminServerName();
    final String ms1PodName = domainUid + "-managed-server1";

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("managedServers", "cluster-1", "managed-server1", "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);
    testInitContainer(modYaml);
    TestUtils.checkPodReady(adminPodName, domain.getDomainNs());
    logger.info("Verifying if the managed server pod is recreated with initialization");
    verifyPodInitialized(ms1PodName);
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers which fails to run to completion and verify the weblogic server pods are not
   * started as result of it.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     weblogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testDomainInitContainerNegative() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String adminPodName = domainUid + "-" + domain.getAdminServerName();

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    modYaml = modYaml.replaceAll("sleep", "foo");
    logger.info(modYaml);
    testInitContainer(modYaml);
    String cmd = "kubectl get pod " + adminPodName + " -n " + domain.getDomainNs();
    TestUtils.checkCmdInLoop(cmd, "Init:CrashLoopBackOff", adminPodName);
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers at domain and admin server level and verify init container runs at both
   * level when the names are different.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testInitContainerDiffLevelDiffName() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String[] pods = {domainUid + "-" + domain.getAdminServerName(), domainUid + "-managed-server2"};

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox1", "sleep");
    crd.addInitContNode("adminServer", null, null, "busybox2", "sleep");
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);
    testInitContainer(modYaml);
    String cmd = "kubectl get pod " + pods[0] + " -n " + domain.getDomainNs();

    TestUtils.checkCmdInLoop(cmd, "Init:0/2", pods[0]);
    TestUtils.checkCmdInLoop(cmd, "Init:1/2", pods[0]);
    TestUtils.checkPodReady(pods[0], domain.getDomainNs());

    logger.info("Verifying if the pods are recreated with initialization");
    verifyPodInitialized(pods[1]);
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers at domain and admin server level and verify init container is not run at
   * both level when the names are same.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testInitContainerDiffLevelSameName() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String[] pods = {domainUid + "-" + domain.getAdminServerName(), domainUid + "-managed-server2"};

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox", "foo");
    crd.addInitContNode("adminServer", null, null, "busybox", "sleep");
    crd.addInitContNode("clusters", "cluster-1", null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);
    testInitContainer(modYaml);
    for (String pod : pods) {
      logger.info("Verifying if the pods are recreated with initialization");
      verifyPodInitialized(pod);
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add multiple initContainers at domain level and verify all of the init containers are run.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testInitContainerMultiple() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String[] pods = {domainUid + "-" + domain.getAdminServerName(), domainUid + "-managed-server1"};

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox1", "sleep");
    crd.addInitContNode("spec", null, null, "busybox2", "sleep");
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);
    testInitContainer(modYaml);
    String cmd;
    for (String pod : pods) {
      cmd = "kubectl get pod " + pod + " -n " + domain.getDomainNs();
      TestUtils.checkCmdInLoop(cmd, "Init:0/2", pod);
      TestUtils.checkCmdInLoop(cmd, "Init:1/2", pod);
      TestUtils.checkPodReady(pod, domain.getDomainNs());
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *     WebLogic server pod doesn't go through initialization and ready state
   */
  private void testInitContainer(String modYaml) throws Exception {
    // Write the modified yaml to a new file
    Path path = Paths.get(initContainerTmpDir, "domain.yaml");
    logger.log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, modYaml.getBytes(charset));

    destroyInitContdomain();

    // Apply the new yaml to update the domain
    logger.log(Level.INFO, "kubectl apply -f {0}", path.toString());
    ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
    logger.info(exec.stdout());
  }

  /**
   * Utility method to check if a pod goes through initialization and Ready.
   *
   * @param podName - String name of the pod to check the status for
   * @throws Exception when pod doesn't go through the initialization and ready state
   */
  private void verifyPodInitialized(String podName) throws Exception {
    TestUtils.checkPodInitializing(podName, domain.getDomainNs());
    TestUtils.checkPodReady(podName, domain.getDomainNs());
  }
}

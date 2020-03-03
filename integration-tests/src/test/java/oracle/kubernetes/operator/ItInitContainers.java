// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecResult;
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
 * Integration tests for testing the init container for WebLogic server pods.
 */
@TestMethodOrder(Alphanumeric.class)
public class ItInitContainers extends BaseTest {

  private static Domain domain = null;
  private static Operator operator;
  private static String domainUid = "domaininitcont";
  private static String initContainerTmpDir = "";
  private static String originalYaml;
  private static String testClassName;
  private static String domainNS;
  private static StringBuffer namespaceList;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties.
   *
   * @throws Exception exception if initialization of properties fails
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

      LoggerHelper.getLocal().log(Level.INFO, "Checking if operator and domain are running, if not creating");
      if (operator == null) {
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassName);
        operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator);
        domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS);
      }
      initContainerTmpDir = getResultDir() + "/initconttemp";
      Files.createDirectories(Paths.get(initContainerTmpDir));

      if (domain == null) {
        domain = createInitContdomain();
        originalYaml =
            getUserProjectsDir()
                + "/weblogic-domains/"
                + domain.getDomainUid()
                + "/domain.yaml";
        Assertions.assertNotNull(domain);
      }
      LoggerHelper.getLocal().log(Level.INFO, "staticPrepare------End");
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
      LoggerHelper.getLocal().log(Level.INFO, "staticUnPrepare------Begin");
      if (domain != null) {
        destroyInitContdomain();
      }
      if (operator != null) {
        operator.destroy();
      }
      tearDown(new Object() {
      }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());
      LoggerHelper.getLocal().log(Level.INFO, "staticUnPrepare------End");
    }
  }

  /**
   * creates the init container domain on PV.
   *
   * @return created domain Domain
   * @throws Exception when domain creation fails
   */
  private Domain createInitContdomain() throws Exception {
    Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
    domainMap.put("namespace", domainNS);
    domainMap.put("domainUID", domainUid);
    LoggerHelper.getLocal().log(Level.INFO, "Creating and verifying the domain creation with domainUid: " + domainUid);
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
   *                   WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testDomainInitContainer() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String[] pods = {domainUid + "-" + domain.getAdminServerName(), domainUid + "-managed-server1"};

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    testInitContainer(modYaml);
    for (String pod : pods) {
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the pods are recreated with initialization");
      verifyPodInitialized(pod);
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *                   weblogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testAdminServerInitContainer() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String adminPodName = domainUid + "-" + domain.getAdminServerName();

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("adminServer", null, null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    testInitContainer(modYaml);
    LoggerHelper.getLocal().log(Level.INFO, "Verifying if the admin server pod is recreated with initialization");
    verifyPodInitialized(adminPodName);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *                   weblogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testClusterInitContainer() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String adminPodName = domainUid + "-" + domain.getAdminServerName();
    final String ms2PodName = domainUid + "-managed-server2";

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("clusters", "cluster-1", null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    testInitContainer(modYaml);
    TestUtils.checkPodReady(adminPodName, domain.getDomainNs());
    LoggerHelper.getLocal().log(Level.INFO, "Verifying if the managed server pods are recreated with initialization");
    verifyPodInitialized(ms2PodName);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *                   WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testMsInitContainer() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String adminPodName = domainUid + "-" + domain.getAdminServerName();
    final String ms1PodName = domainUid + "-managed-server1";

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("managedServers", "cluster-1", "managed-server1", "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    testInitContainer(modYaml);
    TestUtils.checkPodReady(adminPodName, domain.getDomainNs());
    LoggerHelper.getLocal().log(Level.INFO, "Verifying if the managed server pod is recreated with initialization");
    verifyPodInitialized(ms1PodName);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers which fails to run to completion and verify the weblogic server pods are not
   * started as result of it.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *                   weblogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testDomainInitContainerNegative() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String adminPodName = domainUid + "-" + domain.getAdminServerName();

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    modYaml = modYaml.replaceAll("sleep", "foo");
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    testInitContainer(modYaml);
    String cmd = "kubectl get pod " + adminPodName + " -n " + domain.getDomainNs();
    TestUtils.checkCmdInLoop(cmd, "Init:CrashLoopBackOff", adminPodName);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers at domain and admin server level and verify init container runs at both
   * level when the names are different.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *                   WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testInitContainerDiffLevelDiffName() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String[] pods = {domainUid + "-" + domain.getAdminServerName(), domainUid + "-managed-server2"};

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox1", "sleep");
    crd.addInitContNode("adminServer", null, null, "busybox2", "sleep");
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    testInitContainer(modYaml);
    String cmd = "kubectl get pod " + pods[0] + " -n " + domain.getDomainNs();

    TestUtils.checkCmdInLoop(cmd, "Init:0/2", pods[0]);
    TestUtils.checkCmdInLoop(cmd, "Init:1/2", pods[0]);
    TestUtils.checkPodReady(pods[0], domain.getDomainNs());

    LoggerHelper.getLocal().log(Level.INFO, "Verifying if the pods are recreated with initialization");
    verifyPodInitialized(pods[1]);
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers at domain and admin server level and verify init container is not run at
   * both level when the names are same.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *                   WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testInitContainerDiffLevelSameName() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String[] pods = {domainUid + "-" + domain.getAdminServerName(), domainUid + "-managed-server2"};

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox", "foo");
    crd.addInitContNode("adminServer", null, null, "busybox", "sleep");
    crd.addInitContNode("clusters", "cluster-1", null, "busybox", "sleep");
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    testInitContainer(modYaml);
    for (String pod : pods) {
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the pods are recreated with initialization");
      verifyPodInitialized(pod);
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add multiple initContainers at domain level and verify all of the init containers are run.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *                   WebLogic server pod doesn't go through initialization and ready state
   */
  @Test
  public void testInitContainerMultiple() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    final String[] pods = {domainUid + "-" + domain.getAdminServerName(), domainUid + "-managed-server1"};

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null, "busybox1", "sleep");
    crd.addInitContNode("spec", null, null, "busybox2", "sleep");
    String modYaml = crd.getYamlTree();
    LoggerHelper.getLocal().log(Level.INFO, modYaml);
    testInitContainer(modYaml);
    String cmd;
    for (String pod : pods) {
      cmd = "kubectl get pod " + pod + " -n " + domain.getDomainNs();
      TestUtils.checkCmdInLoop(cmd, "Init:0/2", pod);
      TestUtils.checkCmdInLoop(cmd, "Init:1/2", pod);
      TestUtils.checkPodReady(pod, domain.getDomainNs());
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add initContainers to adminServer and verify the admin server pod goes through Init state
   * before starting the admin server pod.
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the initContainers or
   *                   WebLogic server pod doesn't go through initialization and ready state
   */
  private void testInitContainer(String modYaml) throws Exception {
    // Write the modified yaml to a new file
    Path path = Paths.get(initContainerTmpDir, "domain.yaml");
    LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, modYaml.getBytes(charset));

    destroyInitContdomain();

    // Apply the new yaml to update the domain
    LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
    ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
    LoggerHelper.getLocal().log(Level.INFO, exec.stdout());
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

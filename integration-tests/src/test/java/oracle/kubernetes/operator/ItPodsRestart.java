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

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.K8sTestUtils;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing pods being restarted by some properties change.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItPodsRestart extends BaseTest {

  private static Domain domain = null;
  private static Operator operator1;
  private static String domainUid = "";
  private static String restartTmpDir = "";
  private static String originalYaml;

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. Create Operator1 and domainOnPVUsingWLST
   * with admin server and 1 managed server if they are not running
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    if (QUICKTEST) {
      initialize(APP_PROPS_FILE);
      setMaxIterationsPod(40);

      logger.info("Checking if operator1 and domain are running, if not creating");
      if (operator1 == null) {
        operator1 = TestUtils.createOperator(OPERATOR1_YAML);
      }
      restartTmpDir = BaseTest.getResultDir() + "/restarttemp";
      Files.createDirectories(Paths.get(restartTmpDir));

      domain = createPodsRestartdomain();
      originalYaml =
          BaseTest.getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";
      Assert.assertNotNull(domain);
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (QUICKTEST) {
      logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      destroyPodsRestartdomain();
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

      logger.info("SUCCESS");
    }
  }

  private static Domain createPodsRestartdomain() throws Exception {

    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
    domainMap.put("domainUID", "domainpodsrestart");
    domainMap.put("initialManagedServerReplicas", new Integer("1"));

    domainUid = (String) domainMap.get("domainUID");
    logger.info("Creating and verifying the domain creation with domainUid: " + domainUid);

    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();

    return domain;
  }

  private static void destroyPodsRestartdomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }

  /**
   * Modify the domain scope env property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is: env:
   * "-Dweblogic.StdoutDebugEnabled=false"--> "-Dweblogic.StdoutDebugEnabled=true"
   *
   * @throws Exception exception
   */
  @Test
  public void testServerPodsRestartByChangingEnvProperty() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  env property: StdoutDebugEnabled=false to StdoutDebugEnabled=true");
    domain.verifyDomainServerPodRestart(
        "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is:
   * logHomeEnabled: true --> logHomeEnabled: false
   *
   * @throws Exception exception
   */
  @Test
  public void testServerPodsRestartByChangingLogHomeEnabled() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  logHomeEnabled: true -->  logHomeEnabled: false");
    domain.verifyDomainServerPodRestart("logHomeEnabled: true", "logHomeEnabled: false");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is:
   * imagePullPolicy: IfNotPresent --> imagePullPolicy: Never
   *
   * @throws Exception exception
   */
  @Test
  public void testServerPodsRestartByChangingImagePullPolicy() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " imagePullPolicy: IfNotPresent -->  imagePullPolicy: Never ");
    domain.verifyDomainServerPodRestart(
        "imagePullPolicy: \"IfNotPresent\"", "imagePullPolicy: \"Never\" ");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is:
   * includeServerOutInPodLog: true --> includeServerOutInPodLog: false
   *
   * @throws Exception exception
   */
  @Test
  public void testServerPodsRestartByChangingIncludeServerOutInPodLog() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  includeServerOutInPodLog: true -->  includeServerOutInPodLog: false");
    domain.verifyDomainServerPodRestart(
        "includeServerOutInPodLog: true", "includeServerOutInPodLog: false");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started .The property tested is: image:
   * "container-registry.oracle.com/middleware/weblogic:12.2.1.3" --> image:
   * "container-registry.oracle.com/middleware/weblogic:12.2.1.3-dev"
   *
   * @throws Exception exception
   */
  @Test
  public void testServerPodsRestartByChangingZImage() throws Exception {
    Assume.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    
    TestUtils.exec("docker images", true);
    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
         + domain.getDomainUid()
         + "  Image property: "
         + getWeblogicImageName()
         + ":"
         + getWeblogicImageTag()
         + " to "
         + getWeblogicImageName()
         + ":"
         + getWeblogicImageDevTag());

    String newImage = getWeblogicImageName() + ":" + getWeblogicImageDevTag();
    TestUtils.exec("docker pull " + newImage, true);
    // apply new domain yaml and verify pod restart
    domain.verifyDomainServerPodRestart(
        "\"" + getWeblogicImageName() + ":" + getWeblogicImageTag() + "\"",
        "\"" + newImage + "\"");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify/Add the containerSecurityContext section at ServerPod Level using kubectl apply -f
   * cont.security.context.domain.yaml. Verify all the pods re-started. The property tested is:
   * serverPod: containerSecurityContext: runAsUser: 1000 fsGroup: 1000.
   *
   * @throws Exception - assertion fails due to unmatched value or errors occurred if tested servers
   *     are not restarted or after restart the server yaml file doesn't include the new added
   *     property
   */
  @Test
  public void testServerPodsRestartByChangingContSecurityContext() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // firstly ensure that original domain.yaml doesn't include the property-to-be-added
    String domainFileName =
        BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
    boolean result =
        (new String(Files.readAllBytes(Paths.get(domainFileName)))).contains("fsGroup: 1000");
    Assert.assertFalse(result);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "cont.security.context.domain.yaml";
    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " change container securityContext:\n"
            + " runAsUser: 1000\n"
            + " fsGroup: 1000 ");
    domain.verifyDomainServerPodRestart(domainYaml);
    domain.findServerPropertyChange("securityContext", "admin-server");
    domain.findServerPropertyChange("securityContext", "managed-server1");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify/Add the podSecurityContext section at ServerPod level using kubectl apply -f
   * pod.security.context.domain.yaml. Verify all the pods re-started. The property tested is:
   * podSecurityContext: runAsUser: 1000 fsGroup: 2000.
   *
   * @throws Exception - assertion fails due to unmatched value or errors occurred if tested servers
   *     are not restarted or after restart the server yaml file doesn't include the new added
   *     property
   */
  @Test
  public void testServerPodsRestartByChangingPodSecurityContext() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // firstly ensure that original domain.yaml doesn't include the property-to-be-added
    String domainFileName =
        BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
    boolean result =
        (new String(Files.readAllBytes(Paths.get(domainFileName)))).contains("fsGroup: 2000");
    Assert.assertFalse(result);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "pod.security.context.domain.yaml";

    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " change securityContext:\n"
            + "   runAsUser: 1000\n"
            + "   fsGroup: 2000 ");
    domain.verifyDomainServerPodRestart(domainYaml);
    domain.findServerPropertyChange("fsGroup: 2000", "admin-server");
    domain.findServerPropertyChange("fsGroup: 2000", "managed-server1");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify/Add resources at ServerPod level using kubectl apply -f domain.yaml. Verify all pods
   * re-started. The property tested is: resources: limits: cpu: "1" requests: cpu: "0.5" args: -
   * -cpus - "2".
   *
   * @throws Exception - assertion fails due to unmatched value or errors occurred if tested servers
   *     are not restarted or after restart the server yaml file doesn't include the new added
   *     property
   */
  @Test
  public void testServerPodsRestartByChangingResource() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // firstly ensure that original domain.yaml doesn't include the property-to-be-addeded
    String domainFileName =
        BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
    boolean result =
        (new String(Files.readAllBytes(Paths.get(domainFileName)))).contains("cpu: 500m");
    Assert.assertFalse(result);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "resource.domain.yaml";

    logger.info(
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " change resource:\n"
            + "   cpu: 500m");
    domain.verifyDomainServerPodRestart(domainYaml);
    domain.findServerPropertyChange("cpu: 500m", "admin-server");
    domain.findServerPropertyChange("cpu: 500m", "managed-server1");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Add restartVersion:v1.1 at adminServer level and verify the admin pod is Terminated and
   * recreated
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *     restartVersion:v1.1
   */
  @Test
  public void testAdminServerRestartVersion() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-" + domain.getAdminServerName();

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCrd crd = new DomainCrd(originalYaml);
      Map<String, String> admin = new HashMap();
      admin.put("restartVersion", "v1.1");
      crd.addObjectNodeToAdminServer(admin);
      String modYaml = crd.getYamlTree();
      logger.info(modYaml);

      // Write the modified yaml to a new file
      Path path = Paths.get(restartTmpDir, "restart.admin.yaml");
      logger.log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain
      logger.log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      logger.info(exec.stdout());

      logger.info("Verifying if the admin server pod is recreated");
      domain.verifyAdminServerRestarted();
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      logger.info("Verifying if the admin server pod is recreated");
      domain.verifyAdminServerRestarted();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add restartVersion:v1.1 at cluster level and verify the managed servers pods are Terminated and
   * recreated
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *     restartVersion:v1.1
   */
  @Test
  public void testClusterRestartVersion() throws Exception {
    Assume.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-managed-server1";

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCrd crd = new DomainCrd(originalYaml);
      Map<String, Object> cluster = new HashMap();
      cluster.put("restartVersion", "v1.1");
      crd.addObjectNodeToCluster("cluster-1", cluster);
      String modYaml = crd.getYamlTree();
      logger.info(modYaml);

      // Write the modified yaml to a new file
      Path path = Paths.get(restartTmpDir, "restart.cluster.yaml");
      logger.log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain crd
      logger.log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      logger.info(exec.stdout());
      logger.info("Verifying if the cluster is restarted");
      domain.verifyManagedServersRestarted();
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      logger.info("Verifying if the cluster is restarted");
      domain.verifyManagedServersRestarted();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add restartVersion:v1.1 at managed server level and verify the managed server pod are
   * Terminated and recreated
   *
   * <p>Currently failing and tracked by bug in BugDB - 29489387
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *     restartVersion:v1.1
   */
  @Test
  public void testMsRestartVersion() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-managed-server1";

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      final DomainCrd crd = new DomainCrd(originalYaml);
      Map<String, String> ms = new HashMap();
      ms.put("restartVersion", "v1.1");
      ms.put("serverStartPolicy", "IF_NEEDED");
      ms.put("serverStartState", "RUNNING");
      crd.addObjectNodeToMS("managed-server1", ms);
      String modYaml = crd.getYamlTree();
      logger.info(modYaml);

      // Write the modified yaml to a new file
      Path path = Paths.get(restartTmpDir, "restart.managed.yaml");
      logger.log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain crd
      logger.log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      logger.info(exec.stdout());
      logger.info("Verifying if the managed server is restarted");
      domain.verifyManagedServersRestarted();
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      logger.info("Verifying if the managed server is restarted");
      domain.verifyManagedServersRestarted();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add restartVersion:v1.1 at doamin level and verify all of the server pods are Terminated and
   * recreated
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *     restartVersion:v1.1
   */
  @Test
  public void testDomainRestartVersion() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String adminPod = domainUid + "-" + domain.getAdminServerName();
    String msPod = domainUid + "-managed-server1";

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCrd crd = new DomainCrd(originalYaml);
      Map<String, String> domain = new HashMap();
      domain.put("restartVersion", "v1.1");
      crd.addObjectNodeToDomain(domain);
      String modYaml = crd.getYamlTree();
      logger.info(modYaml);

      // Write the modified yaml to a new file
      Path path = Paths.get(restartTmpDir, "restart.domain.yaml");
      logger.log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain crd
      logger.log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      logger.info(exec.stdout());
      logger.info("Verifying if the domain is restarted");
      this.domain.verifyAdminServerRestarted();
      this.domain.verifyManagedServersRestarted();
    } finally {
      logger.log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      logger.info("Verifying if the domain is restarted");
      this.domain.verifyAdminServerRestarted();
      this.domain.verifyManagedServersRestarted();
    }
    logger.log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Utility method to check if a pod is in Terminating or Running status.
   *
   * @param podName - String name of the pod to check the status for
   * @param podStatusExpected - String the expected status of Terminating || RUnning
   * @throws InterruptedException when thread is interrupted
   */
  private void verifyPodStatus(String podName, String podStatusExpected)
      throws InterruptedException {
    K8sTestUtils testUtil = new K8sTestUtils();
    String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    String namespace = domain.getDomainNs();
    boolean gotExpected = false;
    for (int i = 0; i < BaseTest.getMaxIterationsPod(); i++) {
      if (podStatusExpected.equals("Terminating")) {
        if (testUtil.isPodTerminating(namespace, domain1LabelSelector, podName)) {
          gotExpected = true;
          break;
        }
      } else if (podStatusExpected.equals("Running")) {
        if (testUtil.isPodRunning(namespace, domain1LabelSelector, podName)) {
          gotExpected = true;
          break;
        }
      }

      Thread.sleep(BaseTest.getWaitTimePod() * 1000);
    }
    Assert.assertTrue("Didn't get the expected pod status", gotExpected);
  }
}

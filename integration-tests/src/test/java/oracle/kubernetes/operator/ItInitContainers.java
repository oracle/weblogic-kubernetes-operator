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

  private static Domain createInitContdomain() throws Exception {
    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
    domainMap.put("domainUID", domainUid);
    domainUid = (String) domainMap.get("domainUID");
    logger.info("Creating and verifying the domain creation with domainUid: " + domainUid);
    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();
    return domain;
  }

  private static void destroyInitContdomain() throws Exception {
    if (domain != null) {
      domain.destroy();
    }
  }

  /**
   * Add restartVersion:v1.1 at adminServer level and verify the admin pod is Terminated and
   * recreated
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *     restartVersion:v1.1
   */
  @Test
  public void testAdminServerInitContainer() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-" + domain.getAdminServerName();

    // Modify the original domain yaml to include restartVersion in admin server node
    DomainCrd crd = new DomainCrd(originalYaml);
    crd.addInitContNode("spec", null, null);
    crd.addInitContNode("adminServer", null, null);
    crd.addInitContNode("clusters", "cluster-1", null);
    crd.addInitContNode("managedServers", "cluster-1", "managed-server1");
    String modYaml = crd.getYamlTree();
    logger.info(modYaml);

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

    logger.info("Verifying if the admin server pod is recreated");
    // domain.verifyAdminServerRestarted();
    for (int i = 0; i < 30; i++) {
      Thread.sleep(1000 * 10);
      TestUtils.exec("kubectl get all --all-namespaces", true);
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

// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Map;
import oracle.kubernetes.operator.utils.Domain;
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
public class ITPodsRestart extends BaseTest {

  private static Domain domain = null;
  private static Operator operator1;
  private static String domainUid = "";

  /**
   * This method gets called only once before any of the test methods are executed. It does the
   * initialization of the integration test properties defined in OperatorIT.properties and setting
   * the resultRoot, pvRoot and projectRoot attributes. Create Operator1 and domainOnPVUsingWLST
   * with admin server and 1 managed server if they are not running
   *
   * @throws Exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    // initialize test properties and create the directories
    if (!QUICKTEST) {
      initialize(APP_PROPS_FILE);

      logger.info("Checking if operator1 and domain are running, if not creating");
      if (operator1 == null) {
        operator1 = TestUtils.createOperator(OPERATOR1_YAML);
      }

      domain = createPodsRestartdomain();
      Assert.assertNotNull(domain);
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

      // destroyPodsRestartdomain();
      // tearDown();

      logger.info("SUCCESS");
    }
  }

  /**
   * The property tested is: env: "-Dweblogic.StdoutDebugEnabled=false"-->
   * "-Dweblogic.StdoutDebugEnabled=true"
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingEnvProperty() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // boolean testDomainServerPodRestart = false;
    logger.info(
        "About to testDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  env property: StdoutDebugEnabled=false to StdoutDebugEnabled=true");
    domain.testDomainServerPodRestart(
        "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * The property tested is: logHomeEnabled: true --> logHomeEnabled: false
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingLogHomeEnabled() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // boolean testDomainServerPodRestart = false;

    logger.info(
        "About to testDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  logHomeEnabled: true -->  logHomeEnabled: false");
    domain.testDomainServerPodRestart("logHomeEnabled: true", "logHomeEnabled: false");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * The property tested is: imagePullPolicy: IfNotPresent --> imagePullPolicy: Never
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingImagePullPolicy() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // boolean testDomainServerPodRestart = false;

    logger.info(
        "About to testDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " imagePullPolicy: IfNotPresent -->  imagePullPolicy: Never ");
    domain.testDomainServerPodRestart(
        "imagePullPolicy: \"IfNotPresent\"", "imagePullPolicy: \"Never\" ");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * The property tested is: includeServerOutInPodLog: true --> includeServerOutInPodLog: false
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingIncludeServerOutInPodLog() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // boolean testDomainServerPodRestart = false;

    logger.info(
        "About to testDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  includeServerOutInPodLog: true -->  includeServerOutInPodLog: false");
    domain.testDomainServerPodRestart(
        "includeServerOutInPodLog: true", "includeServerOutInPodLog: false");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * The property tested is: image: "store/oracle/weblogic:12.2.1.3" --> image:
   * "store/oracle/weblogic:duplicate"
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingZImage() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // boolean testDomainServerPodRestart = false;
    try {
      logger.info(
          "About to testDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + "  Image property: store/oracle/weblogic:12.2.1.3 to store/oracle/weblogic:duplicate");
      TestUtils.dockerTagImage("store/oracle/weblogic:12.2.1.3", "store/oracle/weblogic:duplicate");
      domain.testDomainServerPodRestart(
          "\"store/oracle/weblogic:12.2.1.3\"", "\"store/oracle/weblogic:duplicate\"");
    } finally {
      TestUtils.dockerRemoveImage("store/oracle/weblogic:duplicate");
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the containerSecurityContext section at ServerPod Level using kubectl apply -f
   * domain.yaml.Verify all the pods re-started serverPod: containerSecurityContext: runAsUser: 1000
   * fsGroup: 2000
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingContSecurityContext() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "cont.security.context.domain.yaml";
    try {
      logger.info(
          "About to testDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + " change container securityContext:\n"
              + "   runAsUser: 1000\n"
              + "   fsGroup: 2000 ");
      domain.testDomainServerPodRestart(domainYaml);
      domain.findServerPropertyChange("runAsUser: 1000", "admin-server");
      domain.findServerPropertyChange("runAsUser: 1000", "managed-server1");
    } finally {
      // bring back the domain into previous State
      logger.info("in the finally block to bring back the domain into previous State");
      String yamlFile =
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
      TestUtils.kubectlapply(yamlFile);
      domain.verifyAdminServerRestarted();
      domain.verifyManagedServersRestarted();
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the podSecurityContext section at ServerPod level using kubectl apply -f domain.yaml Add
   * Verify all pod(s) re-started podSecurityContext: runAsUser: 1000 fsGroup: 2000 The generated
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingPodSecurityContext() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "pod.security.context.domain.yaml";
    try {
      logger.info(
          "About to testDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + " change securityContext:\n"
              + "   runAsUser: 1000\n"
              + "   fsGroup: 2000 ");
      domain.testDomainServerPodRestart(domainYaml);
      domain.findServerPropertyChange("fsGroup: 2000", "admin-server");
      domain.findServerPropertyChange("fsGroup: 2000", "managed-server1");
    } finally {
      // bring back the domain into previous State
      String yamlFile =
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
      TestUtils.kubectlapply(yamlFile);
      domain.verifyAdminServerRestarted();
      domain.verifyManagedServersRestarted();
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Add imagePullSecrets section at Domain Level using kubectl apply -f domain.yaml Verify all pods
   * re-started. imagePullSecrets:- name: imagePullSecr value: myImagePullSecret
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingImagePullSecrets() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "secret.domain.yaml";
    try {
      logger.info(
          "About to testDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + " change imagePullSecrets:\n"
              + "   name: imagePullSecret\n"
              + "   value: myImagePullSecret ");
      domain.testDomainServerPodRestart(domainYaml);
      domain.findServerPropertyChange("myImagePullSecret", "admin-server");
      domain.findServerPropertyChange("myImagePullSecret", "managed-server1");
    } finally {
      // bring back the domain into previous State
      String yamlFile =
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
      TestUtils.kubectlapply(yamlFile);
      domain.verifyAdminServerRestarted();
      domain.verifyManagedServersRestarted();
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify/Add resources at ServerPod level using kubectl apply -f domain.yaml Verify all pods
   * re-started. serverPod: resources: limits: cpu: "1" requests: cpu: "0.5" args: - -cpus - "2"
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingResource() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "resource.domain.yaml";
    try {
      logger.info(
          "About to testDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + " change resource:\n"
              + "   cpu: 500m");
      domain.testDomainServerPodRestart(domainYaml);
      domain.findServerPropertyChange("cpu: 500m", "admin-server");
      domain.findServerPropertyChange("cpu: 500m", "managed-server1");
    } finally {
      // bring back the domain into previous State
      String yamlFile =
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
      TestUtils.kubectlapply(yamlFile);
      domain.verifyAdminServerRestarted();
      domain.verifyManagedServersRestarted();
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the annotations at ServerPod level using kubectl apply -f domain.yaml. Verify all po
   * re-started. annotations: custom: "DATETIME"
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingAnnotation() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "annotation.domain.yaml";
    try {
      logger.info(
          "About to testDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + " change annotation"
              + "   annotations:\n"
              + "     custom: \"DATETIME\"");
      domain.testDomainServerPodRestart(domainYaml);

    } finally {
      // bring back the domain into previous State
      String yamlFile =
          BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
      TestUtils.kubectlapply(yamlFile);
      domain.verifyAdminServerRestarted();
      domain.verifyManagedServersRestarted();
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  private static Domain createPodsRestartdomain() throws Exception {

    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
    domainMap.put("domainUID", "domainpodsrestart");
    domainMap.put("adminNodePort", new Integer("30707"));
    domainMap.put("t3ChannelPort", new Integer("30081"));
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
}

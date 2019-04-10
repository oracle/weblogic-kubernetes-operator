// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.
package oracle.kubernetes.operator;

import static oracle.kubernetes.operator.BaseTest.logger;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCRD;
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

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for testing pods being restarted by some properties change.
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ITPodsRestart extends BaseTest {

  private static Domain domain = null;
  private static Operator operator1;
  private static String KUBE_EXEC_CMD;
  private static String restartTmpDir = "";

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
      restartTmpDir = BaseTest.getResultDir() + "/restarttemp";
      Files.createDirectories(Paths.get(restartTmpDir));

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

      destroyPodsRestartdomain();
      tearDown();

      logger.info("SUCCESS");
    }
  }

  /**
   * Modify the domain scope env property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is: env:
   * "-Dweblogic.StdoutDebugEnabled=false"--> "-Dweblogic.StdoutDebugEnabled=true"
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingEnvProperty() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to testDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  env property: StdoutDebugEnabled=false to StdoutDebugEnabled=true");
    domain.testDomainServerPodRestart(
        "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is:
   * logHomeEnabled: true --> logHomeEnabled: false
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingLogHomeEnabled() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to testDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  logHomeEnabled: true -->  logHomeEnabled: false");
    domain.testDomainServerPodRestart("logHomeEnabled: true", "logHomeEnabled: false");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is:
   * imagePullPolicy: IfNotPresent --> imagePullPolicy: Never
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingImagePullPolicy() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to testDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " imagePullPolicy: IfNotPresent -->  imagePullPolicy: Never ");
    domain.testDomainServerPodRestart(
        "imagePullPolicy: \"IfNotPresent\"", "imagePullPolicy: \"Never\" ");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started. The property tested is:
   * includeServerOutInPodLog: true --> includeServerOutInPodLog: false
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingIncludeServerOutInPodLog() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    logger.info(
        "About to testDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  includeServerOutInPodLog: true -->  includeServerOutInPodLog: false");
    domain.testDomainServerPodRestart(
        "includeServerOutInPodLog: true", "includeServerOutInPodLog: false");

    logger.info("SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started .The property tested is: image:
   * "store/oracle/weblogic:12.2.1.3" --> image: "store/oracle/weblogic:duplicate"
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingZImage() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    try {
      logger.info(
          "About to testDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + "  Image property: store/oracle/weblogic:12.2.1.3 to store/oracle/weblogic:duplicate");

      TestUtils.exec("docker tag store/oracle/weblogic:12.2.1.3 store/oracle/weblogic:duplicate");
      domain.testDomainServerPodRestart(
          "\"store/oracle/weblogic:12.2.1.3\"", "\"store/oracle/weblogic:duplicate\"");
    } finally {
      TestUtils.exec("docker rmi -f store/oracle/weblogic:duplicate");
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  @Test
  public void testAdminServerRestartVersions() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String originalYaml =
        BaseTest.getUserProjectsDir()
            + "/weblogic-domains/"
            + domain.getDomainUid()
            + "/domain.yaml";
    try {
      DomainCRD crd = new DomainCRD();
      String yaml =
          crd.addRestartVersionToAdminServer(
              TestUtils.exec(
                      "kubectl get Domain "
                          + domain.getDomainUid()
                          + " -n "
                          + domain.getDomainNS()
                          + " --output json")
                  .stdout(),
              "v1.1");
      Path path = Paths.get(restartTmpDir, "restart.admin.yaml");
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, yaml.getBytes(charset));
      logger.info(TestUtils.exec("kubectl apply -f " + path.toString()).stdout());
      domain.verifyAdminServerRestarted();
    } finally {
      TestUtils.exec("kubectl apply -f " + originalYaml);
      domain.verifyAdminServerRestarted();
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  // @Test
  public void testClusterRestartVersions() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    ExecResult result;
    String originalYaml =
        BaseTest.getUserProjectsDir()
            + "/weblogic-domains/"
            + domain.getDomainUid()
            + "/domain.yaml";
    try {
      result =
          TestUtils.exec(
              "kubectl get Domain "
                  + domain.getDomainUid()
                  + " -n "
                  + domain.getDomainNS()
                  + " --output json");
      DomainCRD parser = new DomainCRD();
      String yaml =
          parser.addRestartVersionToCluster(
              result.stdout(), domain.getDomainMap().get("clusterName").toString(), "v1.1");
      Path path = Paths.get(restartTmpDir, "restart.cluster.yaml");
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, yaml.getBytes(charset));
      result = TestUtils.exec("kubectl apply -f " + path.toString());
      // TODO - verify that the pod is restarting
    } finally {
      result = TestUtils.exec("kubectl apply -f " + originalYaml);
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  // @Test
  public void testMSRestartVersions() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    ExecResult result;
    String originalYaml =
        BaseTest.getUserProjectsDir()
            + "/weblogic-domains/"
            + domain.getDomainUid()
            + "/domain.yaml";
    try {
      result =
          TestUtils.exec(
              "kubectl get Domain "
                  + domain.getDomainUid()
                  + " -n "
                  + domain.getDomainNS()
                  + " --output json");
      DomainCRD parser = new DomainCRD();
      String yaml = parser.addRestartVersionToMS(result.stdout(), "managed-server1", "v1.1");
      Path path = Paths.get(restartTmpDir, "restart.ms.yaml");
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, yaml.getBytes(charset));
      result = TestUtils.exec("kubectl apply -f " + path.toString());
      // TODO - verify that the pod is restarting
    } finally {
      result = TestUtils.exec("kubectl apply -f " + originalYaml);
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  // @Test
  public void testDomainRestartVersions() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    ExecResult result;
    String originalYaml =
        BaseTest.getUserProjectsDir()
            + "/weblogic-domains/"
            + domain.getDomainUid()
            + "/domain.yaml";
    try {
      result =
          TestUtils.exec(
              "kubectl get Domain "
                  + domain.getDomainUid()
                  + " -n "
                  + domain.getDomainNS()
                  + " --output json");
      DomainCRD parser = new DomainCRD();
      String yaml = parser.addRestartVersionToDomain(result.stdout(), "v1.1");
      Path path = Paths.get(restartTmpDir, "restart.ms.yaml");
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, yaml.getBytes(charset));
      result = TestUtils.exec("kubectl apply -f " + path.toString());
      // TODO - verify that the pod is restarting
    } finally {
      result = TestUtils.exec("kubectl apply -f " + originalYaml);
    }
    logger.info("SUCCESS - " + testMethodName);
  }

  private static Domain createPodsRestartdomain() throws Exception {

    Map<String, Object> domainMap = TestUtils.loadYaml(DOMAINONPV_WLST_YAML);
    domainMap.put("domainUID", "domainpodsrestart");
    domainMap.put("initialManagedServerReplicas", new Integer("1"));

    logger.info("Creating Domain domain& verifing the domain creation");
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

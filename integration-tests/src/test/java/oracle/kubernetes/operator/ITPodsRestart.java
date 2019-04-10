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
  private static String domainUid = "";
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
      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

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
  // @Test
  public void testServerPodsRestartByChangingEnvProperty() throws Exception {
    Assume.assumeFalse(QUICKTEST);
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
   * @throws Exception
   */
  // @Test
  public void testServerPodsRestartByChangingLogHomeEnabled() throws Exception {
    Assume.assumeFalse(QUICKTEST);
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
   * @throws Exception
   */
  // @Test
  public void testServerPodsRestartByChangingImagePullPolicy() throws Exception {
    Assume.assumeFalse(QUICKTEST);
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
   * @throws Exception
   */
  // @Test
  public void testServerPodsRestartByChangingIncludeServerOutInPodLog() throws Exception {
    Assume.assumeFalse(QUICKTEST);
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
   * "store/oracle/weblogic:12.2.1.3" --> image: "store/oracle/weblogic:duplicate"
   *
   * @throws Exception
   */
  // @Test
  public void testServerPodsRestartByChangingZImage() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    try {
      logger.info(
          "About to verifyDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + "  Image property: store/oracle/weblogic:12.2.1.3 to store/oracle/weblogic:duplicate");

      TestUtils.exec("docker tag store/oracle/weblogic:12.2.1.3 store/oracle/weblogic:duplicate");
      domain.verifyDomainServerPodRestart(
          "\"store/oracle/weblogic:12.2.1.3\"", "\"store/oracle/weblogic:duplicate\"");
    } finally {
      TestUtils.exec("docker rmi -f store/oracle/weblogic:duplicate");
    }

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
  // @Test
  public void testServerPodsRestartByChangingContSecurityContext() throws Exception {
    Assume.assumeFalse(QUICKTEST);
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
  // @Test
  public void testServerPodsRestartByChangingPodSecurityContext() throws Exception {
    Assume.assumeFalse(QUICKTEST);
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
  // @Test
  public void testServerPodsRestartByChangingResource() throws Exception {
    Assume.assumeFalse(QUICKTEST);
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
      logger.log(
          Level.INFO,
          "Running kubectl get Domain {0} -n {1} --output json",
          new Object[] {domain.getDomainUid(), domain.getDomainNS()});
      String jsonCrd =
          TestUtils.exec(
                  "kubectl get Domain "
                      + domain.getDomainUid()
                      + " -n "
                      + domain.getDomainNS()
                      + " --output json")
              .stdout();
      logger.info(jsonCrd);
      logger.info("Modifying the Domain CRD..");
      DomainCRD crd = new DomainCRD();
      String yaml = crd.addRestartVersionToAdminServer(jsonCrd, "v1.1");
      logger.info(yaml);
      Path path = Paths.get(restartTmpDir, "restart.admin.yaml");
      logger.log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, yaml.getBytes(charset));
      logger.log(Level.INFO, "Running kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      logger.info(exec.stdout());
      logger.info("Verifying if the admin server is restarted");
      domain.verifyAdminServerRestarted();
    } finally {
      logger.log(Level.INFO, "Running kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      logger.info("Verifying if the admin server is restarted");
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

// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

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
import oracle.kubernetes.operator.utils.DomainCRD;
import oracle.kubernetes.operator.utils.ExecCommand;
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
public class ITPodsRestart extends BaseTest {

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
      originalYaml =
          BaseTest.getUserProjectsDir()
              + "/weblogic-domains/"
              + domain.getDomainUid()
              + "/domain.yaml";
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
   * "container-registry.oracle.com/middleware/weblogic:12.2.1.3-190111" --> image:
   * "container-registry.oracle.com/middleware/weblogic:duplicate"
   *
   * @throws Exception
   */
  @Test
  public void testServerPodsRestartByChangingZImage() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    try {
      ExecAndPrintLog("docker images");
      String images1[] = {
        "9de773965c6b",
        "5c523ecea17f",
        "634ab59891b8",
        "c9ea22528100",
        "77979f22eea8",
        "5261b5bb1682",
        "fa63a9fa105a",
        "b52f5dd28662",
        "52c5109f9ad9",
        "e6205477ca28",
        "9b54850cad70",
        "a3c1f631d78b",
        "321290079459",
        "0abdfd581194",
        "46641d6abd6a",
        "4d0e7bd02c03",
        "4d0e7bd02c03",
        "aaea54bce1ea",
        "1b2806e9b91b",
        "26310d8c7e81",
        "b8d808b2fc8c",
        "a570b3a13826",
        "e81b76899dd9",
        "060dff5892a6",
        "1f1b15e1640a",
        "83d42e23d7b5",
        "ca9b2c67199a",
        "1997b422fe38",
        "f8356b77b86a",
        "6cbdf81e788b",
        "d2659213623e",
        "373153704253",
        "584151651c94",
        "d1b3d8091750",
        "1af5e0f6d09f",
        "1eb1067c8bc1",
        "d4b8f09c756c",
        "20853267c6cd",
        "a9436d968bd6",
        "40f75e58f1b8",
        "18794492338f",
        "9cec71b9f912",
        "a75eca5a040a",
        "f4cd13108db7",
        "a25cb4eeaacd",
        "1ccec66cf648",
        "03aae768cc41",
        "d1e87f62f9a1",
        "180b1ae20d0b",
        "e3098cafb0d6",
        "74bc3f982470",
        "d3efd9b5d292",
        "ddf1f76315de",
        "1b5a237afc1e",
        "bef9fd7ffdb0",
        "963a3f8a3b1d",
        "a3b2f2fa2c91",
        "c8f1bcbd4825",
        "97c40ae16c56",
        "44abca094a5b",
        "fafe74bcdf98",
        "944d31a10b23",
        "f1febf596a81",
        "efc87baca08f",
        "48072f5521f8",
        "ac47af0772dd",
        "84321a1dbd53",
        "1802f7158e13",
        "307fe1f9cc96",
        "7e23b521d1be",
        "d815f1273f3a",
        "e33cfeb1defc",
        "4f45aa601eef",
        "6053dbc2d144",
        "7c4672911c1c",
        "0224d6384efc",
        "ec52e4bf2e5a",
        "e927f202c152",
        "4c40c13af322",
        "e9a1a5eff9c4",
        "d58f0d158db5",
        "d58f0d158db5"
      };
      String images[] = {
        System.getenv("REPO_REGISTRY") + "/weblogick8s/weblogic-kubernetes-operator",
        System.getenv("REPO_REGISTRY") + "/weblogick8s/domain-home-in-image",
        "domain-home-in-image",
        "domain-home-in-image-wdt",
        System.getenv("REPO_REGISTRY") + "/weblogick8s/wdt/wls",
        "oracle/weblogic-kubernetes-operator",
        "store/oracle/weblogic",
        System.getenv("REPO_REGISTRY") + "/weblogick8s/store/oracle/weblogic"
      };
      for (String image : images1) {
        ExecAndPrintLog("docker rmi " + image);
      }
      ExecAndPrintLog("docker images");
      logger.info(
          "About to verifyDomainServerPodRestart for Domain: "
              + domain.getDomainUid()
              + "  Image property: "
              + getWeblogicImageName()
              + ":"
              + getWeblogicImageTag()
              + " to "
              + System.getenv("REPO_REGISTRY")
              + "/weblogick8s/middleware/weblogic:duplicate");

      if (BaseTest.SHARED_CLUSTER) {
        String newImage =
            System.getenv("REPO_REGISTRY") + "/weblogick8s/middleware/weblogic:duplicate";

        String tag =
            "docker tag " + getWeblogicImageName() + ":" + getWeblogicImageTag() + " " + newImage;
        // tag image with repo name
        ExecAndPrintLog(tag);
        ExecAndPrintLog("docker images");

        ExecAndPrintLog(
            "docker login "
                + System.getenv("REPO_REGISTRY")
                + " -u "
                + System.getenv("REPO_USERNAME")
                + " -p "
                + System.getenv("REPO_PASSWORD"));
        ExecAndPrintLog("docker push " + newImage);

        // login and push image to ocir
        // TestUtils.loginAndPushImageToOCIR(newImage);

        // create ocir registry secret in the same ns as domain which is used while pulling the
        // image
        //        TestUtils.createDockerRegistrySecret(
        //            "docker-store",
        //            BaseTest.getWeblogicImageServer(),
        //            System.getenv("OCR_USERNAME"),
        //            System.getenv("OCR_PASSWORD"),
        //            "none@oracle.com ",
        //            domain.getDomainNS());

        ExecAndPrintLog(
            "kubectl create secret docker-registry docker-store "
                + "--docker-server="
                + System.getenv("REPO_REGISTRY")
                + " --docker-username="
                + System.getenv("REPO_USERNAME")
                + " --docker-password="
                + System.getenv("REPO_PASSWORD")
                + " -n "
                + domain.getDomainNS()
                + " --dry-run -o yaml");
        String command =
            "kubectl create secret docker-registry docker-store "
                + "--docker-server="
                + System.getenv("REPO_REGISTRY")
                + " --docker-username="
                + System.getenv("REPO_USERNAME")
                + " --docker-password="
                + System.getenv("REPO_PASSWORD")
                + " -n "
                + domain.getDomainNS()
                + " --dry-run -o yaml | kubectl apply -f - ";
        ExecAndPrintLog(command);

        // apply new domain yaml and verify pod restart
        domain.verifyDomainServerPodRestart(
            "\"" + getWeblogicImageName() + ":" + getWeblogicImageTag() + "\"",
            "\"" + newImage + "\"");
      } else {
        TestUtils.exec(
            "docker tag "
                + getWeblogicImageName()
                + ":"
                + getWeblogicImageTag()
                + " "
                + getWeblogicImageName()
                + ":duplicate");
        domain.verifyDomainServerPodRestart(
            "\"" + getWeblogicImageName() + ":" + getWeblogicImageTag() + "\"",
            "\"" + getWeblogicImageName() + ":duplicate" + "\"");
      }
    } finally {
      if (!BaseTest.SHARED_CLUSTER) {
        TestUtils.exec("docker rmi -f " + getWeblogicImageName() + ":duplicate");
      }
    }

    logger.info("SUCCESS - " + testMethodName);
  }

  private void ExecAndPrintLog(String command) throws Exception {
    ExecResult result = ExecCommand.exec(command);
    logger.info(
        "Command "
            + command
            + "\nreturn value: "
            + result.exitValue()
            + "\nstderr = "
            + result.stderr()
            + "\nstdout = "
            + result.stdout());
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

  /**
   * Add restartVersion:v1.1 at adminServer level and verify the admin pod is Terminated and
   * recreated
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *     restartVersion:v1.1
   */
  // @Test
  public void testAdminServerRestartVersion() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-" + domain.getAdminServerName();

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCRD crd = new DomainCRD(originalYaml);
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
  // @Test
  public void testClusterRestartVersion() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-managed-server1";

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCRD crd = new DomainCRD(originalYaml);
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
  // // @Test
  public void testMSRestartVersion() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-managed-server1";

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCRD crd = new DomainCRD(originalYaml);
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
  // @Test
  public void testDomainRestartVersion() throws Exception {
    Assume.assumeFalse(QUICKTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String adminPod = domainUid + "-" + domain.getAdminServerName();
    String msPod = domainUid + "-managed-server1";

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCRD crd = new DomainCRD(originalYaml);
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
   * Utility method to check if a pod is in Terminating or Running status
   *
   * @param podName - String name of the pod to check the status for
   * @param podStatusExpected - String the expected status of Terminating || RUnning
   * @throws InterruptedException when thread is interrupted
   */
  private void verifyPodStatus(String podName, String podStatusExpected)
      throws InterruptedException {
    K8sTestUtils testUtil = new K8sTestUtils();
    String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    String namespace = domain.getDomainNS();
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

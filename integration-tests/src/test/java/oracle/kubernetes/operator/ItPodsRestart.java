// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.DomainCrd;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.K8sTestUtils;
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
 * <p>This test is used for testing pods being restarted by some properties change.
 */
@TestMethodOrder(Alphanumeric.class)
public class ItPodsRestart extends BaseTest {
  private static Domain domain = null;
  private static Operator operator1;
  private static String domainUid = "";
  private static String restartTmpDir = "";
  private static String originalYaml;
  private static String domainNS;
  private static boolean testCompletedSuccessfully;
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
    namespaceList = new StringBuffer();
    testClassName = new Object() {
    }.getClass().getEnclosingClass().getSimpleName();
    initialize(APP_PROPS_FILE, testClassName);
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    // initialize test properties and create the directories
    if (QUICKTEST) {
      createResultAndPvDirs(testClassName);
      // setMaxIterationsPod(80);

      LoggerHelper.getLocal().log(Level.INFO, "Checking if operator1 and domain are running, if not creating");
      if (operator1 == null) {
        Map<String, Object> operatorMap = createOperatorMap(getNewSuffixCount(), true, testClassName);
        operator1 = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        Assertions.assertNotNull(operator1);
        domainNS = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList.append((String)operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS);
      }
      restartTmpDir = getResultDir() + "/restarttemp";
      Files.createDirectories(Paths.get(restartTmpDir));

      if (domain == null) {
        domain = createPodsRestartdomain();
        originalYaml =
            getUserProjectsDir()
                + "/weblogic-domains/"
                + domain.getDomainUid()
                + "/domain.yaml";
        Assertions.assertNotNull(domain);
      }
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories.
   *
   * @throws Exception exception
   */
  @AfterAll
  public static void staticUnPrepare() throws Exception {
    tearDown(new Object() {
    }.getClass().getEnclosingClass().getSimpleName(), namespaceList.toString());

    LoggerHelper.getLocal().info("SUCCESS");
  }

  private Domain createPodsRestartdomain() throws Exception {

    Map<String, Object> domainMap = createDomainMap(getNewSuffixCount(), testClassName);
    // domainMap.put("domainUID", "domainpodsrestart");
    domainMap.put("initialManagedServerReplicas", new Integer("1"));
    domainMap.put("namespace", domainNS);
    domainUid = (String) domainMap.get("domainUID");
    LoggerHelper.getLocal().log(Level.INFO,
        "Creating and verifying the domain creation with domainUid: " + domainUid);

    domain = TestUtils.createDomain(domainMap);
    domain.verifyDomainCreated();

    return domain;
  }

  private static void destroyPodsRestartdomain() throws Exception {
    if (domain != null) {
      TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
      TestUtils.verifyAfterDeletion(domain);
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO,
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  env property: StdoutDebugEnabled=false to StdoutDebugEnabled=true");
    domain.verifyDomainServerPodRestart(
        "\"-Dweblogic.StdoutDebugEnabled=false\"", "\"-Dweblogic.StdoutDebugEnabled=true\"");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO,
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  logHomeEnabled: true -->  logHomeEnabled: false");
    domain.verifyDomainServerPodRestart("logHomeEnabled: true", "logHomeEnabled: false");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO,
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " imagePullPolicy: IfNotPresent -->  imagePullPolicy: Never ");
    domain.verifyDomainServerPodRestart(
        "imagePullPolicy: \"IfNotPresent\"", "imagePullPolicy: \"Never\" ");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    LoggerHelper.getLocal().log(Level.INFO,
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + "  includeServerOutInPodLog: true -->  includeServerOutInPodLog: false");
    domain.verifyDomainServerPodRestart(
        "includeServerOutInPodLog: true", "includeServerOutInPodLog: false");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Modify the domain scope property on the domain resource using kubectl apply -f domain.yaml
   * Verify that all the server pods in the domain got re-started .The property tested is: image:
   * "container-registry.oracle.com/middleware/weblogic:12.2.1.3" --> image:
   * "container-registry.oracle.com/middleware/weblogic:12.2.1.3-dev"
   *
   * @throws Exception exception
   */
  //@Test
  public void testServerPodsRestartByChangingZImage() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    testCompletedSuccessfully = false;

    TestUtils.exec("docker images", true);
    LoggerHelper.getLocal().log(Level.INFO,
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
    //TestUtils.exec("docker pull " + newImage, true);
    callDockerPull(newImage);
    // apply new domain yaml and verify pod restart
    domain.verifyDomainServerPodRestart(
        "\"" + getWeblogicImageName() + ":" + getWeblogicImageTag() + "\"",
        "\"" + newImage + "\"");
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Modify/Add the containerSecurityContext section at ServerPod Level using kubectl apply -f
   * cont.security.context.domain.yaml. Verify all the pods re-started. The property tested is:
   * serverPod: containerSecurityContext: runAsUser: 1000.
   *
   * @throws Exception - assertion fails due to unmatched value or errors occurred if tested servers
   *                   are not restarted or after restart the server yaml file doesn't include the new added
   *                   property
   */
  @Test
  public void testServerPodsRestartByChangingContSecurityContext() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // firstly ensure that original domain.yaml doesn't include the property-to-be-added
    String domainFileName =
        getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
    boolean result =
        (new String(Files.readAllBytes(Paths.get(domainFileName)))).contains("runAsUser: 1000");
    Assertions.assertFalse(result);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "cont.security.context.domain.yaml";
    LoggerHelper.getLocal().log(Level.INFO,
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " change container securityContext:\n"
            + " runAsUser: 1000 ");
    domain.verifyDomainServerPodRestart(domainYaml);
    domain.findServerPropertyChange("securityContext", "admin-server");
    domain.findServerPropertyChange("securityContext", "managed-server1");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Modify/Add the podSecurityContext section at ServerPod level using kubectl apply -f
   * pod.security.context.domain.yaml. Verify all the pods re-started. The property tested is:
   * podSecurityContext: runAsUser: 1000 fsGroup: 2000.
   *
   * @throws Exception - assertion fails due to unmatched value or errors occurred if tested servers
   *     are not restarted or after restart the server yaml file doesn't include 
   *     the new added property
   */
  @Test
  public void testServerPodsRestartByChangingPodSecurityContext() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // firstly ensure that original domain.yaml doesn't include the property-to-be-added
    String domainFileName =
        getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
    boolean result =
        (new String(Files.readAllBytes(Paths.get(domainFileName)))).contains("fsGroup: 2000");
    Assertions.assertFalse(result);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "pod.security.context.domain.yaml";

    LoggerHelper.getLocal().log(Level.INFO,
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " change securityContext:\n"
            + "   runAsUser: 1000\n"
            + "   fsGroup: 2000 ");
    domain.verifyDomainServerPodRestart(domainYaml);
    domain.findServerPropertyChange("fsGroup: 2000", "admin-server");
    domain.findServerPropertyChange("fsGroup: 2000", "managed-server1");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Modify/Add resources at ServerPod level using kubectl apply -f domain.yaml. Verify all pods
   * re-started. The property tested is: resources: limits: cpu: "1" requests: cpu: "0.5" args: -
   * -cpus - "2".
   *
   * @throws Exception - assertion fails due to unmatched value or errors occurred if tested servers
   *                   are not restarted or after restart the server yaml file doesn't include the new added
   *                   property
   */
  @Test
  public void testServerPodsRestartByChangingResource() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    // firstly ensure that original domain.yaml doesn't include the property-to-be-addeded
    String domainFileName =
        getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/domain.yaml";
    boolean result =
        (new String(Files.readAllBytes(Paths.get(domainFileName)))).contains("cpu: 500m");
    Assertions.assertFalse(result);

    // domainYaml: the yaml file name with changed property under resources dir
    String domainYaml = "resource.domain.yaml";

    LoggerHelper.getLocal().log(Level.INFO,
        "About to verifyDomainServerPodRestart for Domain: "
            + domain.getDomainUid()
            + " change resource:\n"
            + "   cpu: 500m");
    domain.verifyDomainServerPodRestart(domainYaml);
    domain.findServerPropertyChange("cpu: 500m", "admin-server");
    domain.findServerPropertyChange("cpu: 500m", "managed-server1");

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  /**
   * Add restartVersion:v1.1 at adminServer level and verify the admin pod is Terminated and
   * recreated
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *                   restartVersion:v1.1
   */
  @Test
  public void testAdminServerRestartVersion() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    String podName = domainUid + "-" + domain.getAdminServerName();

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCrd crd = new DomainCrd(originalYaml);
      Map<String, String> admin = new HashMap();
      admin.put("restartVersion", "v1.1");
      crd.addObjectNodeToAdminServer(admin);
      String modYaml = crd.getYamlTree();
      LoggerHelper.getLocal().log(Level.INFO, modYaml);

      // Write the modified yaml to a new file
      Path path = Paths.get(restartTmpDir, "restart.admin.yaml");
      LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain
      LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      LoggerHelper.getLocal().log(Level.INFO, exec.stdout());

      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the admin server pod is recreated");
      domain.verifyAdminServerRestarted();
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the admin server pod is recreated");
      domain.verifyAdminServerRestarted();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add restartVersion:v1.1 at cluster level and verify the managed servers pods are Terminated and
   * recreated
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *                   restartVersion:v1.1
   */
  @Test
  public void testClusterRestartVersion() throws Exception {
    Assumptions.assumeTrue(QUICKTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);
    testCompletedSuccessfully = false;
    String podName = domainUid + "-managed-server1";

    try {
      // Modify the original domain yaml to include restartVersion in admin server node
      DomainCrd crd = new DomainCrd(originalYaml);
      Map<String, Object> cluster = new HashMap();
      cluster.put("restartVersion", "v1.1");
      crd.addObjectNodeToCluster("cluster-1", cluster);
      String modYaml = crd.getYamlTree();
      LoggerHelper.getLocal().log(Level.INFO, modYaml);

      // Write the modified yaml to a new file
      Path path = Paths.get(restartTmpDir, "restart.cluster.yaml");
      LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain crd
      LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      LoggerHelper.getLocal().log(Level.INFO, exec.stdout());
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the cluster is restarted");
      domain.verifyManagedServersRestarted();
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the cluster is restarted");
      domain.verifyManagedServersRestarted();
    }
    testCompletedSuccessfully = true;
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add restartVersion:v1.1 at managed server level and verify the managed server pod are
   * Terminated and recreated
   *
   * <p>Currently failing and tracked by bug in BugDB - 29489387
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *                   restartVersion:v1.1
   */
  @Test
  public void testMsRestartVersion() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
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
      LoggerHelper.getLocal().log(Level.INFO, modYaml);

      // Write the modified yaml to a new file
      Path path = Paths.get(restartTmpDir, "restart.managed.yaml");
      LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain crd
      LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      LoggerHelper.getLocal().log(Level.INFO, exec.stdout());
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the managed server is restarted");
      domain.verifyManagedServersRestarted();
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the managed server is restarted");
      domain.verifyManagedServersRestarted();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Add restartVersion:v1.1 at doamin level and verify all of the server pods are Terminated and
   * recreated
   *
   * @throws Exception when domain.yaml cannot be read or modified to include the
   *                   restartVersion:v1.1
   */
  @Test
  public void testDomainRestartVersion() throws Exception {
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {
    }.getClass().getEnclosingMethod().getName();
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
      LoggerHelper.getLocal().log(Level.INFO, modYaml);

      // Write the modified yaml to a new file
      Path path = Paths.get(restartTmpDir, "restart.domain.yaml");
      LoggerHelper.getLocal().log(Level.INFO, "Path of the modified domain.yaml :{0}", path.toString());
      Charset charset = StandardCharsets.UTF_8;
      Files.write(path, modYaml.getBytes(charset));

      // Apply the new yaml to update the domain crd
      LoggerHelper.getLocal().log(Level.INFO, "kubectl apply -f {0}", path.toString());
      ExecResult exec = TestUtils.exec("kubectl apply -f " + path.toString());
      LoggerHelper.getLocal().log(Level.INFO, exec.stdout());
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the domain is restarted");
      this.domain.verifyAdminServerRestarted();
      this.domain.verifyManagedServersRestarted();
    } finally {
      LoggerHelper.getLocal().log(
          Level.INFO, "Reverting back the domain to old crd\n kubectl apply -f {0}", originalYaml);
      TestUtils.exec("kubectl apply -f " + originalYaml);
      LoggerHelper.getLocal().log(Level.INFO, "Verifying if the domain is restarted");
      this.domain.verifyAdminServerRestarted();
      this.domain.verifyManagedServersRestarted();
    }
    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - {0}", testMethodName);
  }

  /**
   * Utility method to check if a pod is in Terminating or Running status.
   *
   * @param podName           - String name of the pod to check the status for
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
    Assertions.assertTrue(gotExpected, "Didn't get the expected pod status");
  }
  
  private void callDockerPull(String imageName) throws Exception {
    int maxIterations = 20;
    int waitTime = 10;
    for (int i = 0; i < maxIterations; i++) {
      //ExecResult result = TestUtils.exec("docker pull " + imageName, true);
      ExecResult result = ExecCommand.exec("docker pull " + imageName);
      if (result.exitValue() != 0) {
        LoggerHelper.getLocal().log(Level.INFO,
            "callDockerPull did not return 0 exitValue got "
                + result.exitValue()
                + ", iteration "
                + i
                + " of "
                + maxIterations);
        if (i == (maxIterations - 1)) {
          throw new RuntimeException(
              "FAILURE: callDockerPull did not return 0 exitValue, got " 
                  + "\nstderr = "
                  + result.stderr()
                  + "\nstdout = "
                  + result.stdout());
        }
        try {
          Thread.sleep(waitTime * 1000);
        } catch (InterruptedException ignore) {
          // no-op
        }
      } else {
        LoggerHelper.getLocal().log(Level.INFO,
            "callDockerPull returned 0 exitValue, iteration " + i);
        break;
      }
    }
  }

}

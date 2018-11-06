// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;

/**
 * Base class which contains common methods to create/shutdown operator and domain. IT tests can
 * extend this class.
 */
public class BaseTest {
  public static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");
  public static final String TESTWEBAPP = "testwebapp";

  private static String resultRoot = "";
  private static String pvRoot = "";
  private static String resultDir = "";
  private static String userProjectsDir = "";
  private static String projectRoot = "";
  private static String username = "weblogic";
  private static String password = "welcome1";
  private static int maxIterationsPod = 50;
  private static int waitTimePod = 5;
  private static String leaseId = "";
  private static String branchName = "";

  private static Properties appProps;

  public static void initialize(String appPropsFile) throws Exception {

    // load app props defined
    appProps = TestUtils.loadProps(appPropsFile);

    // check app props
    String baseDir = appProps.getProperty("baseDir");
    if (baseDir == null) {
      throw new IllegalArgumentException("FAILURE: baseDir is not set");
    }
    username = appProps.getProperty("username", username);
    password = appProps.getProperty("password", password);
    maxIterationsPod =
        new Integer(appProps.getProperty("maxIterationsPod", "" + maxIterationsPod)).intValue();
    waitTimePod = new Integer(appProps.getProperty("waitTimePod", "" + waitTimePod)).intValue();
    if (System.getenv("RESULT_ROOT") != null) {
      resultRoot = System.getenv("RESULT_ROOT");
    } else {
      resultRoot = baseDir + "/" + System.getProperty("user.name") + "/wl_k8s_test_results";
    }
    if (System.getenv("PV_ROOT") != null) {
      pvRoot = System.getenv("PV_ROOT");
    } else {
      pvRoot = resultRoot;
    }
    if (System.getenv("LEASE_ID") != null) {
      leaseId = System.getenv("LEASE_ID");
    }
    resultDir = resultRoot + "/acceptance_test_tmp";
    userProjectsDir = resultDir + "/user-projects";
    projectRoot = System.getProperty("user.dir") + "/..";

    // BRANCH_NAME var is used in Jenkins job
    if (System.getenv("BRANCH_NAME") != null) {
      branchName = System.getenv("BRANCH_NAME");
    } else {
      branchName = TestUtils.getGitBranchName();
    }

    // for manual/local run, do cleanup
    if (System.getenv("WERCKER") == null && System.getenv("JENKINS") == null) {

      // delete k8s artifacts created if any, delete PV directories
      ExecResult clnResult = cleanup();
      /* if (clnResult.exitValue() != 0) {
        throw new RuntimeException(
            "FAILED: Command to call cleanup script failed " + clnResult.stderr());
      } */
      logger.info(
          "Command to call cleanup script returned "
              + clnResult.stdout()
              + "\n"
              + clnResult.stderr());
    }
    // create resultRoot, PVRoot, etc
    Files.createDirectories(Paths.get(resultRoot));
    Files.createDirectories(Paths.get(resultDir));
    Files.createDirectories(Paths.get(userProjectsDir));

    // create file handler
    FileHandler fh = new FileHandler(resultDir + "/java_test_suite.out");
    SimpleFormatter formatter = new SimpleFormatter();
    fh.setFormatter(formatter);
    logger.addHandler(fh);
    logger.info("Adding file handler, logging to file at " + resultDir + "/java_test_suite.out");

    // for manual/local run, create file handler, create PVROOT
    if (System.getenv("WERCKER") == null && System.getenv("JENKINS") == null) {
      logger.info("Creating PVROOT " + pvRoot);
      Files.createDirectories(Paths.get(pvRoot));
      ExecResult result = ExecCommand.exec("chmod 777 " + pvRoot);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: Couldn't change permissions for PVROOT " + result.stderr());
      }
    }

    logger.info("appProps = " + appProps);
    logger.info("maxIterationPod = " + appProps.getProperty("maxIterationsPod"));
    logger.info(
        "maxIterationPod with default= "
            + appProps.getProperty("maxIterationsPod", "" + maxIterationsPod));
    logger.info("RESULT_ROOT =" + resultRoot);
    logger.info("PV_ROOT =" + pvRoot);
    logger.info("userProjectsDir =" + userProjectsDir);
    logger.info("projectRoot =" + projectRoot);
    logger.info("branchName =" + branchName);

    logger.info("Env var RESULT_ROOT " + System.getenv("RESULT_ROOT"));
    logger.info("Env var PV_ROOT " + System.getenv("PV_ROOT"));
    logger.info("Env var K8S_NODEPORT_HOST " + System.getenv("K8S_NODEPORT_HOST"));
    logger.info("Env var IMAGE_NAME_OPERATOR= " + System.getenv("IMAGE_NAME_OPERATOR"));
    logger.info("Env var IMAGE_TAG_OPERATOR " + System.getenv("IMAGE_TAG_OPERATOR"));
    logger.info(
        "Env var IMAGE_PULL_POLICY_OPERATOR " + System.getenv("IMAGE_PULL_POLICY_OPERATOR"));
    logger.info(
        "Env var IMAGE_PULL_SECRET_OPERATOR " + System.getenv("IMAGE_PULL_SECRET_OPERATOR"));
    logger.info(
        "Env var IMAGE_PULL_SECRET_WEBLOGIC " + System.getenv("IMAGE_PULL_SECRET_WEBLOGIC"));
    logger.info("Env var BRANCH_NAME " + System.getenv("BRANCH_NAME"));
  }

  /**
   * Access Admin REST endpoint using admin node host and node port
   *
   * @throws Exception
   */
  public void testAdminServerExternalService(Domain domain) throws Exception {
    logger.info("Inside testAdminServerExternalService");
    domain.verifyAdminServerExternalService(getUsername(), getPassword());
    logger.info("Done - testAdminServerExternalService");
  }

  /**
   * Verify t3channel port by deploying webapp using the port
   *
   * @throws Exception
   */
  public void testAdminT3Channel(Domain domain) throws Exception {
    logger.info("Inside testAdminT3Channel");
    Map<String, Object> domainMap = domain.getDomainMap();
    // check if the property is set to true
    Boolean exposeAdmint3Channel = (Boolean) domainMap.get("exposeAdminT3Channel");

    if (exposeAdmint3Channel != null && exposeAdmint3Channel.booleanValue()) {
      domain.deployWebAppViaWLST(
          TESTWEBAPP,
          getProjectRoot() + "/src/integration-tests/apps/testwebapp.war",
          getUsername(),
          getPassword());
      domain.verifyWebAppLoadBalancing(TESTWEBAPP);
    } else {
      logger.info("exposeAdminT3Channel is false, can not test t3ChannelPort");
    }

    logger.info("Done - testAdminT3Channel");
  }

  /**
   * Restarting the domain should not have any impact on Operator managing the domain, web app load
   * balancing and node port service
   *
   * @throws Exception
   */
  public void testDomainLifecyle(Operator operator, Domain domain) throws Exception {
    logger.info("Inside testDomainLifecyle");
    domain.destroy();
    domain.create();
    operator.verifyExternalRESTService();
    operator.verifyDomainExists(domain.getDomainUid());
    domain.verifyDomainCreated();
    domain.verifyWebAppLoadBalancing(TESTWEBAPP);
    domain.verifyAdminServerExternalService(getUsername(), getPassword());
    logger.info("Done - testDomainLifecyle");
  }

  /**
   * Scale the cluster up/down using Operator REST endpoint, load balancing should adjust
   * accordingly.
   *
   * @throws Exception
   */
  public void testClusterScaling(Operator operator, Domain domain) throws Exception {
    logger.info("Inside testClusterScaling");
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = domainMap.get("namespace").toString();
    String managedServerNameBase = domainMap.get("managedServerNameBase").toString();
    int replicas = 3;
    String podName = domain.getDomainUid() + "-" + managedServerNameBase + replicas;
    String clusterName = domainMap.get("clusterName").toString();

    logger.info(
        "Scale domain " + domain.getDomainUid() + " Up to " + replicas + " managed servers");
    operator.scale(domainUid, domainMap.get("clusterName").toString(), replicas);

    logger.info("Checking if managed pod(" + podName + ") is Running");
    TestUtils.checkPodCreated(podName, domainNS);

    logger.info("Checking if managed server (" + podName + ") is Running");
    TestUtils.checkPodReady(podName, domainNS);

    logger.info("Checking if managed service(" + podName + ") is created");
    TestUtils.checkServiceCreated(podName, domainNS);

    int replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != replicas) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled up size "
              + replicaCnt
              + "/"
              + replicas);
    }

    domain.verifyWebAppLoadBalancing(TESTWEBAPP);

    replicas = 2;
    podName = domainUid + "-" + managedServerNameBase + (replicas + 1);
    logger.info("Scale down to " + replicas + " managed servers");
    operator.scale(domainUid, clusterName, replicas);

    logger.info("Checking if managed pod(" + podName + ") is deleted");
    TestUtils.checkPodDeleted(podName, domainNS);

    replicaCnt = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCnt != replicas) {
      throw new RuntimeException(
          "FAILURE: Cluster replica doesn't match with scaled down size "
              + replicaCnt
              + "/"
              + replicas);
    }

    domain.verifyWebAppLoadBalancing(TESTWEBAPP);
    logger.info("Done - testClusterScaling");
  }

  /**
   * Scale the cluster up using Weblogic WLDF scaling,
   *
   * @throws Exception
   */
  public void testWLDFScaling(Operator operator, Domain domain) throws Exception {
    logger.info("Inside testWLDFScaling");

    Map<String, Object> domainMap = domain.getDomainMap();
    String domainUid = domain.getDomainUid();
    String domainNS = (String) domainMap.get("namespace");
    String adminServerName = (String) domainMap.get("adminServerName");
    String adminPodName = domainUid + "-" + adminServerName;
    String domainName = (String) domainMap.get("domainName");

    String scriptsDir =
        "/scratch/acceptance_test_pv/persistentVolume-"
            + domainUid
            + "/domains/"
            + domainUid
            + "/bin/scripts";

    copyScalingScriptToPod(scriptsDir, domainUid, adminPodName, domainNS);
    TestUtils.createRBACPoliciesForWLDFScaling();

    // deploy opensessionapp
    domain.deployWebAppViaWLST(
        "opensessionapp",
        getProjectRoot() + "/src/integration-tests/apps/opensessionapp.war",
        getUsername(),
        getPassword());

    TestUtils.createWLDFModule(
        adminPodName, domainNS, ((Integer) domainMap.get("t3ChannelPort")).intValue());

    String clusterName = domainMap.get("clusterName").toString();
    int replicaCntBeforeScaleup = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    logger.info("replica count before scaleup " + replicaCntBeforeScaleup);

    logger.info("Scale domain " + domainUid + " by calling the webapp");

    int replicas = 3;
    callWebAppAndVerifyScaling(domain, replicas);

    int replicaCntAfterScaleup = TestUtils.getClusterReplicas(domainUid, clusterName, domainNS);
    if (replicaCntAfterScaleup <= replicaCntBeforeScaleup) {
      throw new RuntimeException(
          "FAILURE: Cluster replica count has not increased after scaling up, replicaCntBeforeScaleup/replicaCntAfterScaleup "
              + replicaCntBeforeScaleup
              + "/"
              + replicaCntAfterScaleup);
    }

    logger.info("Done - testWLDFScaling");
  }

  /**
   * Restarting Operator should not impact the running domain
   *
   * @throws Exception
   */
  public void testOperatorLifecycle(Operator operator, Domain domain) throws Exception {
    logger.info("Inside testOperatorLifecycle");
    operator.destroy();
    operator.create();
    operator.verifyExternalRESTService();
    operator.verifyDomainExists(domain.getDomainUid());
    domain.verifyDomainCreated();
    logger.info("Done - testOperatorLifecycle");
  }

  public static ExecResult cleanup() throws Exception {
    String cmd =
        "export RESULT_ROOT="
            + getResultRoot()
            + " export PV_ROOT="
            + getPvRoot()
            + " && "
            + getProjectRoot()
            + "/src/integration-tests/bash/cleanup.sh";
    logger.info("Command to call cleanup script " + cmd);
    return ExecCommand.exec(cmd);
  }

  protected void logTestBegin(String testName) throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN " + testName);
    // renew lease at the beginning for every test method, leaseId is set only for Wercker
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
  }

  public static String getResultRoot() {
    return resultRoot;
  }

  public static String getPvRoot() {
    return pvRoot;
  }

  public static String getUserProjectsDir() {
    return userProjectsDir;
  }

  public static String getProjectRoot() {
    return projectRoot;
  }

  public static String getUsername() {
    return username;
  }

  public static String getPassword() {
    return password;
  }

  public static int getMaxIterationsPod() {
    return maxIterationsPod;
  }

  public static int getWaitTimePod() {
    return waitTimePod;
  }

  public static Properties getAppProps() {
    return appProps;
  }

  public static String getLeaseId() {
    return leaseId;
  }

  public static String getBranchName() {
    return branchName;
  }

  private void copyScalingScriptToPod(
      String dirPathToCreate, String domainUID, String podName, String domainNS) throws Exception {

    // create scripts dir under domain pv
    TestUtils.createDirUnderDomainPV(dirPathToCreate);

    // copy script to pod
    TestUtils.kubectlcp(
        getProjectRoot() + "/src/scripts/scaling/scalingAction.sh",
        "/shared/domains/" + domainUID + "/bin/scripts/scalingAction.sh",
        // "/shared/scalingAction.sh",
        podName,
        domainNS);
  }

  private void callWebAppAndVerifyScaling(Domain domain, int replicas) throws Exception {
    Map<String, Object> domainMap = domain.getDomainMap();
    String domainNS = (String) domainMap.get("namespace");

    // call opensessionapp
    domain.callWebAppAndVerifyLoadBalancing("opensessionapp", false);
    logger.info("Sleeping for 30 seconds for scaleup");
    Thread.sleep(30 * 1000);

    String managedServerNameBase = (String) domainMap.get("managedServerNameBase");
    String podName = domain.getDomainUid() + "-" + managedServerNameBase + replicas;

    logger.info("Checking if managed pod(" + podName + ") is Running");
    TestUtils.checkPodCreated(podName, domainNS);

    logger.info("Checking if managed server (" + podName + ") is Running");
    TestUtils.checkPodReady(podName, domainNS);

    logger.info("Checking if managed service(" + podName + ") is created");
    TestUtils.checkServiceCreated(podName, domainNS);
  }
}

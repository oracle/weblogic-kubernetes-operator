// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;
import oracle.kubernetes.operator.utils.TestUtils;

/**
 * Base class which contains common methods to create/shutdown operator and domain. IT tests can
 * extend this class.
 */
public class BaseTest {
  public static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private static String resultRoot = "";
  private static String pvRoot = "";
  // private static String resultDir = "";
  private static String userProjectsDir = "";
  private static String projectRoot = "";
  private static String username = "weblogic";
  private static String password = "welcome1";
  private static int maxIterationsPod = 50;
  private static int waitTimePod = 5;
  private static String leaseId = "";

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
    // resultDir = resultRoot + "/acceptance_test_tmp";
    userProjectsDir = resultRoot + "/acceptance_test_tmp/user-projects";
    projectRoot = System.getProperty("user.dir") + "/..";
    logger.info("RESULT_ROOT =" + resultRoot);
    logger.info("PV_ROOT =" + pvRoot);
    logger.info("userProjectsDir =" + userProjectsDir);
    logger.info("projectRoot =" + projectRoot);

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

    // create resultRoot, PVRoot, etc
    Files.createDirectories(Paths.get(resultRoot));

    if (System.getenv("JENKINS") == null) {
      logger.info("Creating PVROOT " + pvRoot);
      Files.createDirectories(Paths.get(pvRoot));
      String output = TestUtils.executeCommand("chmod 777 " + pvRoot);
      if (!output.trim().equals("")) {
        // throw new RuntimeException("FAILURE: Couldn't change permissions for PVROOT " + output);
        logger.info("FAILURE: Couldn't change permissions for PVROOT " + output);
      }
    }

    logger.info("Output for ls -ltr / " + TestUtils.executeCommand("ls -ltr /"));

    // Files.createDirectories(Paths.get(resultDir));

    Files.createDirectories(Paths.get(userProjectsDir));
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

  protected void logTestBegin() throws Exception {
    logger.info("+++++++++++++++++++++++++++++++++---------------------------------+");
    logger.info("BEGIN");
    // renew lease at the begining for every test method, leaseId is set only for Wercker
    TestUtils.renewK8sClusterLease(getProjectRoot(), getLeaseId());
  }
}

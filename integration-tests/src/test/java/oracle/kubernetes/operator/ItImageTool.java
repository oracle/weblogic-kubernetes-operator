// Copyright (c) 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Map;
import java.util.Properties;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
import oracle.kubernetes.operator.utils.Operator;
import oracle.kubernetes.operator.utils.TestUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Simple JUnit test file used for testing Operator.
 *
 * <p>This test is used for creating Operator(s) and domain(s) which are managed by the Operator(s).
 * WebLogic Docker image is created by WebLogic Image Tool
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ItImageTool extends BaseTest {
  private static final String TEST_RESOURCE_LOC = "integration-tests/src/test/resources";
  private static String weblogicImageVersionWIT;
  private static String weblogicImageNameWIT;
  private static String weblogicImageTagWIT;

  private static Operator operator;
  private static Domain domain;

  /**
   * This method gets called only once before any of the test methods are executed. It creates
   * a WebLogic Docker image using WebLogic Image Tool. It does the initialization of the integration
   * test properties defined in OperatorIT.properties and setting the resultRoot, pvRoot and projectRoot attributes.
   * It also creates Operator, domain and a test domain yaml file.
   *
   * @throws Exception exception
   */
  @BeforeClass
  public static void staticPrepare() throws Exception {
    if (FULLTEST) {
      // Determine image name and version to be used
      // load app props defined
      logger.info("Loading props from: " + APP_PROPS_FILE);
      Properties appProps = TestUtils.loadProps(APP_PROPS_FILE);
      System.setProperty("WIT_TEST", "true");

      weblogicImageVersionWIT =
        System.getenv("IMAGE_TAG_WEBLOGIC_WIT") != null
          ? System.getenv("IMAGE_TAG_WEBLOGIC_WIT")
          : appProps.getProperty("weblogicImageTagWIT");
      weblogicImageNameWIT =
        System.getenv("IMAGE_NAME_WEBLOGIC_WIT") != null
          ? System.getenv("IMAGE_NAME_WEBLOGIC_WIT")
          : appProps.getProperty("weblogicImageNameWIT");

      weblogicImageTagWIT = weblogicImageNameWIT + ":" + weblogicImageVersionWIT;
      logger.info("WebLogic image name is: " + weblogicImageTagWIT);
      // Build WebLogic Docker image using imagetool
      buildWlsDockerImage();

      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE);

      // Create operator1
      if (operator == null) {
        logger.info("Creating Operator & waiting for the script to complete execution");
        operator = TestUtils.createOperator(OPERATOR1_YAML);
      }

      // create domain
      if (domain == null) {
        logger.info("Creating WLS Domain & waiting for the script to complete execution");
        domain = TestUtils.createDomain(DOMAINONPV_WLST_YAML);
        domain.verifyDomainCreated();
      }
    }
  }

  /**
   * Releases k8s cluster lease, archives result, pv directories and restore OperatorIT.properties.
   *
   * @throws Exception exception
   */
  @AfterClass
  public static void staticUnPrepare() throws Exception {
    if (FULLTEST) {
      logger.info("++++++++++++++++++++++++++++++++++");
      logger.info("BEGIN");
      logger.info("Run once, release cluster lease");

      tearDown(new Object() {}.getClass().getEnclosingClass().getSimpleName());

      logger.info("SUCCESS");
    }
  }

  /**
   * Verify that WebLogic Docker image created by using WebLogic Image Tool is used
   * to create WebLogic domain in Operator env.
   * There are two ways to use the WLS Docker image created by imagetool
   *  1. export IMAGE_NAME_WEBLOGIC = "name of your wls Docker image"
   *     export IMAGE_TAG_WEBLOGIC = "version of the image"
   *  2. use the values of weblogicImageName and weblogicImageTag in OperatorWIT.properties
   *
   * @throws Exception exception
   */
  @Test
  public void testCustomImageUsed() throws Exception {
    Assume.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    final Map<String, Object> domainMap = domain.getDomainMap();
    final String domainUid = domain.getDomainUid();
    final String adminServerName = (String) domainMap.get("adminServerName");
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String podNameSpace = (String) domainMap.get("namespace");
    ExecResult result = null;

    // Verify that the WebLogic Docker image created by WIT is used
    StringBuffer getImageNameCmd = new StringBuffer();
    String cmd =
        getImageNameCmd
          .append("kubectl get pod ")
          .append(adminServerPodName)
          .append(" -n ")
          .append(podNameSpace)
          .append(" -o=jsonpath='{.spec.containers[*].image}'")
          .toString();
    logger.info("Command to get pod's image name: " + cmd);

    result = TestUtils.exec(cmd);

    Assume.assumeTrue("Failed to use the image <" + weblogicImageTagWIT
        + "> built by imagetool", (result.stdout()).equals(weblogicImageTagWIT));

    logger.info("WebLogic Docker image used by pod <"
        + adminServerPodName + "> is <" + result.stdout() + ">");

    logger.info("SUCCESS - " + testMethodName);
  }

  private static void buildWlsDockerImage() throws Exception {
    //build wls Docker image using imagetool
    logger.info("Building a WebLogic Docker image using imagetool... ");
    final String projectRoot = System.getProperty("user.dir") + "/..";

    StringBuffer buildImage = new StringBuffer();
    String cmd =
        buildImage
          .append(" sh ")
          .append(projectRoot)
          .append("/")
          .append(TEST_RESOURCE_LOC)
          .append("/imagetool/build.sh")
          .toString();
    logger.info("Command to build image name: " + cmd);

    ExecResult result = ExecCommand.exec(cmd, true);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
        "FAILURE: Command "
          + cmd
          + " failed with stderr = "
          + result.stderr()
          + " \n stdout = "
          + result.stdout());
    }

    //check the image built successfully
    cmd = "docker image ls |grep " + weblogicImageNameWIT;
    result = ExecCommand.exec(cmd);

    Assume.assumeTrue("The image <" + weblogicImageTagWIT + "> doesn't exist!",
        result.exitValue() == 0);

    logger.info("A WebLogic Docker image <" + weblogicImageTagWIT
        + "> is created successfully by imagetool! ");
  }
}

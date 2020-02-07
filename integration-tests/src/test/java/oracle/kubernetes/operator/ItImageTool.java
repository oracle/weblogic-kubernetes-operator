// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import oracle.kubernetes.operator.utils.Domain;
import oracle.kubernetes.operator.utils.ExecCommand;
import oracle.kubernetes.operator.utils.ExecResult;
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
 * <p>This test is used for creating Operator(s) and domain(s) which are managed by the Operator(s).
 * WebLogic Docker image is created by WebLogic Image Tool
 */
@TestMethodOrder(Alphanumeric.class)
public class ItImageTool extends BaseTest {
  private static final String TEST_RESOURCE_LOC = "integration-tests/src/test/resources";
  private static String weblogicImageVersionWIT;
  private static String weblogicImageNameWIT;
  private static String weblogicImageTagWIT;

  private static Operator operator;
  private static Domain domain;
  private static String domainNS1;
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
    if (FULLTEST) {
      testClassName = new Object() {
      }.getClass().getEnclosingClass().getSimpleName();

      // Determine image name and version to be used
      // load app props defined
      LoggerHelper.getLocal().log(Level.INFO, "Loading props from: " + APP_PROPS_FILE);
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
      if (BaseTest.SHARED_CLUSTER) {
        weblogicImageNameWIT = System.getenv("REPO_REGISTRY")
            + "/weblogick8s/" + weblogicImageNameWIT;
      }
      weblogicImageTagWIT = weblogicImageNameWIT + ":" + weblogicImageVersionWIT;
      LoggerHelper.getLocal().log(Level.INFO, "WebLogic image name is: " + weblogicImageTagWIT);

      // Delete existing image
      deleteWlsDockerImage();
      // Build WebLogic Docker image using imagetool
      buildWlsDockerImage();

      if (BaseTest.SHARED_CLUSTER) {
        TestUtils.loginAndPushImageToOcir(weblogicImageTagWIT);
      }

      // initialize test properties and create the directories
      initialize(APP_PROPS_FILE, testClassName);
    }
  }

  /**
   * This method gets called before every test. It creates the result/pv root directories
   * for the test. Creates the operator and domain if its not running.
   *
   * @throws Exception exception if result/pv/operator/domain creation fails
   */
  @BeforeEach
  public void prepare() throws Exception {
    if (FULLTEST) {
      createResultAndPvDirs(testClassName);
      String testClassNameShort = "itimage";
      String imagePullSecretName = "ocir-domain";
      // create operator1
      if (operator == null) {
        Map<String, Object> operatorMap =
            createOperatorMap(getNewSuffixCount(), true, testClassNameShort);
        LoggerHelper.getLocal().log(Level.INFO, "Before createOperator ");
        operator = TestUtils.createOperator(operatorMap, Operator.RestCertType.SELF_SIGNED);
        LoggerHelper.getLocal().log(Level.INFO, "Aefore createOperator");
        Assertions.assertNotNull(operator);
        domainNS1 = ((ArrayList<String>) operatorMap.get("domainNamespaces")).get(0);
        namespaceList = new StringBuffer((String) operatorMap.get("namespace"));
        namespaceList.append(" ").append(domainNS1);
      }

      if (BaseTest.SHARED_CLUSTER) {
        TestUtils.createDockerRegistrySecret(
            imagePullSecretName,
            System.getenv("REPO_REGISTRY"),
            System.getenv("REPO_USERNAME"),
            System.getenv("REPO_PASSWORD"),
            System.getenv("REPO_EMAIL"),
            domainNS1);
      }

      // create domain
      if (domain == null) {
        LoggerHelper.getLocal().log(Level.INFO,
            "Creating WLS Domain & waiting for the script to complete execution");
        Map<String, Object> wlstDomainMap =
            createDomainMap(getNewSuffixCount(), testClassNameShort);
        wlstDomainMap.put("namespace", domainNS1);
        wlstDomainMap.put("weblogicImageTagWIT", weblogicImageTagWIT);
        wlstDomainMap.put("image", weblogicImageTagWIT);
        if (BaseTest.SHARED_CLUSTER) {
          wlstDomainMap.put("imagePullSecretName", imagePullSecretName);
          wlstDomainMap.put("imagePullPolicy", "Always");
        }
        domain = TestUtils.createDomain(wlstDomainMap);
        domain.verifyDomainCreated();
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
    if (FULLTEST) {
      if (namespaceList != null) {
        tearDown(new Object() {}.getClass()
            .getEnclosingClass().getSimpleName(), namespaceList.toString());
      } else {
        LoggerHelper.getLocal().log(Level.INFO, "namespaceList is null!!");
      }

      deleteWlsDockerImage();

      LoggerHelper.getLocal().log(Level.INFO, "SUCCESS");
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
    Assumptions.assumeTrue(FULLTEST);
    String testMethodName = new Object() {}.getClass().getEnclosingMethod().getName();
    logTestBegin(testMethodName);

    final Map<String, Object> domainMap = domain.getDomainMap();
    final String domainUid = domain.getDomainUid();
    final String adminServerName = (String) domainMap.get("adminServerName");
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String podNameSpace = (String) domainMap.get("namespace");
    ExecResult result = null;
    boolean testCompletedSuccessfully = false;

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
    LoggerHelper.getLocal().log(Level.INFO, "Command to get pod's image name: " + cmd);

    try {
      result = TestUtils.exec(cmd);

      Assumptions.assumeTrue((result.stdout()).equals(weblogicImageTagWIT),
          "Failed to use the image <" + weblogicImageTagWIT + "> built by imagetool");

      LoggerHelper.getLocal().log(Level.INFO, "WebLogic Docker image used by pod <"
          + adminServerPodName + "> is <" + result.stdout() + ">");
      testCompletedSuccessfully = true;
    } finally {
      if (domain != null && testCompletedSuccessfully) {
        LoggerHelper.getLocal().log(Level.INFO, "About to delete domain: " + domain.getDomainUid());
        TestUtils.deleteWeblogicDomainResources(domain.getDomainUid());
        TestUtils.verifyAfterDeletion(domain);
      }
      if (operator != null && testCompletedSuccessfully) {
        operator.destroy();
      }
    }

    LoggerHelper.getLocal().log(Level.INFO, "SUCCESS - " + testMethodName);
  }

  private static void buildWlsDockerImage() throws Exception {
    //build wls Docker image using imagetool
    LoggerHelper.getLocal().log(Level.INFO,
        "Building a WebLogic Docker image using imagetool... ");
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
    LoggerHelper.getLocal().log(Level.INFO, "Command to build image name: " + cmd);

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

    Assumptions.assumeTrue(result.exitValue() == 0,
        "The image <" + weblogicImageTagWIT + "> doesn't exist!");

    LoggerHelper.getLocal().log(Level.INFO, "A WebLogic Docker image <"
        + weblogicImageTagWIT + "> is created successfully by imagetool!");
  }

  private static void deleteWlsDockerImage() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO,
        "Deleting the WebLogic Docker image built by imagetool... ");

    //check the image exists before deleting it
    StringBuffer buildImage = new StringBuffer();
    String cmd =
        buildImage
        .append("docker image ls | egrep ")
        .append(weblogicImageNameWIT)
        .append(".*")
        .append(weblogicImageVersionWIT)
        .toString();
    LoggerHelper.getLocal().log(Level.INFO, "Command to verify that the image exists: " + cmd);
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() == 0) {
      LoggerHelper.getLocal().log(Level.INFO, "The image to delete: " + result.stdout());

      //delete the image built by imagetool
      cmd = "docker rmi -f " + weblogicImageTagWIT;
      LoggerHelper.getLocal().log(Level.INFO, "Command to delete image: " + cmd);
      result = ExecCommand.exec(cmd);

      if (result.exitValue() == 0) {
        LoggerHelper.getLocal().log(Level.INFO, "The WebLogic Docker image <"
            + weblogicImageTagWIT + "> is deleted successfully!");
      }
    } else {
      LoggerHelper.getLocal().log(Level.INFO, "No image to delete!");
    }
  }
}

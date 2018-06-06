// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.logging.Logger;
import oracle.kubernetes.operator.BaseTest;

/** Operator class with all the utility methods for Operator. */
public class Operator {

  public static final String CREATE_OPERATOR_SCRIPT_MESSAGE =
      "The Oracle WebLogic Server Kubernetes Operator is deployed";

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private Properties operatorProps = new Properties();

  // default values as in create-weblogic-operator-inputs.yaml,
  // if the property is not defined here, it takes the property and its value from
  // create-weblogic-operator-inputs.yaml
  private String operatorNS = "weblogic-operator";
  private String externalRestOption = "NONE";
  private String externalRestHttpsPort = "31001";
  private String userProjectsDir = "";

  private String createOperatorScript = "";
  private String inputTemplateFile = "";
  private String generatedInputYamlFile;

  private static int maxIterationsOp = BaseTest.getMaxIterationsPod(); // 50 * 5 = 250 seconds
  private static int waitTimeOp = BaseTest.getWaitTimePod();

  /**
   * Takes operator input properties which needs to be customized and generates a operator input
   * yaml file.
   *
   * @param inputProps
   * @throws Exception
   */
  public Operator(Properties inputProps) throws Exception {
    this.operatorProps = inputProps;
    initialize();
    generateInputYaml();
    callCreateOperatorScript();
  }

  /**
   * verifies operator is created
   *
   * @throws Exception
   */
  public void verifyPodCreated() throws Exception {
    logger.info("Checking if Operator pod is Running");
    // empty string for pod name as there is only one pod
    TestUtils.checkPodCreated("", operatorNS);
  }

  /**
   * verifies operator is ready
   *
   * @throws Exception
   */
  public void verifyOperatorReady() throws Exception {
    logger.info("Checking if Operator pod is Ready");
    // empty string for pod name as there is only one pod
    TestUtils.checkPodReady("", operatorNS);
  }

  /**
   * Start operator and makes sure it is deployed and ready
   *
   * @throws Exception
   */
  public void create() throws Exception {
    logger.info("Starting Operator");
    TestUtils.executeCommand(
        "kubectl create -f "
            + userProjectsDir
            + "/weblogic-operators/"
            + operatorNS
            + "/weblogic-operator.yaml");
    logger.info("Checking Operator deployment");

    String availableReplicaCmd =
        "kubectl get deploy weblogic-operator -n "
            + operatorNS
            + " -o jsonpath='{.status.availableReplicas}'";
    for (int i = 0; i < maxIterationsOp; i++) {
      String availableReplica = TestUtils.executeCommandStrArray(availableReplicaCmd).trim();
      if (!availableReplica.equals("1")) {
        if (i == maxIterationsOp - 1) {
          throw new RuntimeException(
              "FAILURE: The WebLogic operator deployment is not available, after waiting 300 seconds");
        }
        logger.info(
            "status is " + availableReplica + ", iteration " + i + " of " + maxIterationsOp);
        try {
          Thread.sleep(waitTimeOp * 1000);
        } catch (InterruptedException ignore) {
        }
      } else {
        break;
      }
    }

    verifyPodCreated();
    verifyOperatorReady();
    verifyExternalRESTService();
  }

  public void verifyExternalRESTService() {
    if (!externalRestOption.equals("NONE")) {
      logger.info("Checking REST service is running");
      String restCmd =
          "kubectl get services -n "
              + operatorNS
              + " -o jsonpath='{.items[?(@.metadata.name == \"external-weblogic-operator-svc\")]}'";
      logger.info("Cmd to check REST service " + restCmd);
      String restService = TestUtils.executeCommandStrArray(restCmd).trim();

      logger.info("cmd result for REST service " + restService);
      if (restService.equals("") || !restService.contains("name:external-weblogic-operator-svc")) {
        throw new RuntimeException("FAILURE: operator rest service was not created");
      }
    } else {
      logger.info("External REST service is not enabled");
    }
  }

  public void destroy() throws Exception {
    TestUtils.executeCommand(
        "kubectl delete -f "
            + userProjectsDir
            + "/weblogic-operators/"
            + operatorNS
            + "/weblogic-operator.yaml");

    logger.info("Checking REST service is deleted");
    runCommandInLoop(
        "kubectl get services -n " + operatorNS + " | egrep weblogic-operator-src | wc -l");
    runCommandInLoop("kubectl get all -n " + operatorNS);
  }

  public void cleanup(String userProjectsDir) {
    if (!operatorNS.trim().equals("")) {
      TestUtils.executeCommand("rm -rf " + userProjectsDir + "/weblogic-operators/" + operatorNS);
    }
  }

  public void scale(String domainUid, String clusterName, int numOfMS) throws Exception {
    String myJsonObjStr = "{\"managedServerCount\": " + numOfMS + "}";

    // Operator REST external API URL to scale
    StringBuffer myOpRestApiUrl =
        new StringBuffer("https://")
            .append(TestUtils.getHostName())
            .append(":")
            .append(externalRestHttpsPort)
            .append("/operator/v1/domains/")
            .append(domainUid)
            .append("/clusters/")
            .append(clusterName)
            .append("/scale");

    TestUtils.makeOperatorPostRestCall(
        operatorNS, myOpRestApiUrl.toString(), myJsonObjStr, userProjectsDir);
    // give sometime to complete
    logger.info("Wait 30 sec for scaling to complete...");
    Thread.sleep(30 * 1000);
  }

  public void verifyDomainExists(String domainUid) throws Exception {
    // Operator REST external API URL to scale
    StringBuffer myOpRestApiUrl =
        new StringBuffer("https://")
            .append(TestUtils.getHostName())
            .append(":")
            .append(externalRestHttpsPort)
            .append("/operator/latest/domains/")
            .append(domainUid);
    TestUtils.makeOperatorGetRestCall(operatorNS, myOpRestApiUrl.toString(), userProjectsDir);
  }

  public Properties getOperatorProps() {
    return operatorProps;
  }

  private void callCreateOperatorScript() {
    StringBuffer cmd = new StringBuffer(createOperatorScript);
    cmd.append(" -i ").append(generatedInputYamlFile).append(" -o ").append(userProjectsDir);
    logger.info("Running " + cmd);
    String outputStr = TestUtils.executeCommand(cmd.toString());
    logger.info("run " + outputStr);

    if (!outputStr.contains(CREATE_OPERATOR_SCRIPT_MESSAGE)) {
      throw new RuntimeException("FAILURE: Create Operator Script failed..");
    }
  }

  private void generateInputYaml() throws Exception {
    Path parentDir =
        Files.createDirectories(Paths.get(userProjectsDir + "/weblogic-operators/" + operatorNS));
    generatedInputYamlFile = parentDir + "/" + operatorNS + "-inputs.yaml";
    TestUtils.createInputFile(operatorProps, inputTemplateFile, generatedInputYamlFile);
  }

  private void runCommandInLoop(String command) throws Exception {
    for (int i = 0; i < maxIterationsOp; i++) {

      String output = TestUtils.executeCommandStrArray(command).trim();
      if (!output.contains("No resources found.")) {
        if (i == maxIterationsOp - 1) {
          throw new RuntimeException("FAILURE: Operator fail to be deleted");
        }
        logger.info("status is " + output + ", iteration " + i + " of " + maxIterationsOp);
        Thread.sleep(waitTimeOp * 1000);
      } else {
        break;
      }
    }
  }

  private void initialize() throws Exception {
    userProjectsDir = BaseTest.getUserProjectsDir();
    createOperatorScript = BaseTest.getProjectRoot() + "/kubernetes/create-weblogic-operator.sh";
    inputTemplateFile =
        BaseTest.getProjectRoot() + "/kubernetes/create-weblogic-operator-inputs.yaml";
    operatorNS = operatorProps.getProperty("namespace", operatorNS);

    // customize the inputs yaml file to generate a self-signed cert for the external Operator REST
    // https port
    if (operatorProps.getProperty("externalRestOption") != null) {
      externalRestOption = operatorProps.getProperty("externalRestOption");
    }
    externalRestOption = operatorProps.getProperty("externalRestOption");
    if (externalRestOption != null && externalRestOption.equals("SELF_SIGNED_CERT")) {
      if (operatorProps.getProperty("externalSans") == null) {
        operatorProps.put("externalSans", "DNS:" + TestUtils.getHostName());
      }
      if (operatorProps.getProperty("externalRestHttpsPort") != null) {
        externalRestHttpsPort = operatorProps.getProperty("externalRestHttpsPort");
        try {
          new Integer(externalRestHttpsPort).intValue();
        } catch (NumberFormatException nfe) {
          throw new IllegalArgumentException(
              "FAILURE: Invalid value for " + "externalRestHttpsPort " + externalRestHttpsPort);
        }
      } else {
        operatorProps.put("externalRestHttpsPort", externalRestHttpsPort);
      }
    }
    // customize the inputs yaml file to use our pre-built docker image
    if (System.getenv("IMAGE_NAME_OPERATOR") != null
        && System.getenv("IMAGE_TAG_OPERATOR") != null) {
      operatorProps.put(
          "weblogicOperatorImage",
          System.getenv("IMAGE_NAME_OPERATOR") + ":" + System.getenv("IMAGE_TAG_OPERATOR"));
    } else {
      operatorProps.put(
          "weblogicOperatorImage",
          "wlsldi-v2.docker.oraclecorp.com/weblogic-operator"
              + ":test_"
              + TestUtils.getGitBranchName().replaceAll("/", "_"));
    }

    if (System.getenv("IMAGE_PULL_POLICY_OPERATOR") != null) {
      operatorProps.put(
          "weblogicOperatorImagePullPolicy", System.getenv("IMAGE_PULL_POLICY_OPERATOR"));
    }
    
    ExecCommand.exec("kubectl create namespace "+operatorNS);
    
    if (System.getenv("IMAGE_PULL_SECRET_OPERATOR") != null) {
      operatorProps.put(
          "weblogicOperatorImagePullSecretName", System.getenv("IMAGE_PULL_SECRET_OPERATOR"));
      // create docker registry secrets
      TestUtils.createDockerRegistrySecret(
          System.getenv("IMAGE_PULL_SECRET_OPERATOR"),
          System.getenv("REPO_REGISTRY"),
          System.getenv("REPO_USERNAME"),
          System.getenv("REPO_PASSWORD"),
          System.getenv("REPO_EMAIL"),
          operatorNS);
    }
  }
}

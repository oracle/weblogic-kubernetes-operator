package oracle.kubernetes.operator.utils;

import java.io.File;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Operator class with all the utility methods for Operator.
 *
 * @author Vanajakshi Mukkara
 */
public class Operator {
  public static final String CREATE_OPERATOR_SCRIPT = "../kubernetes/create-weblogic-operator.sh";
  public static final String createScriptMessage =
      "The Oracle WebLogic Server Kubernetes Operator is deployed";
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");
  public static String opInputTemplateFile = "../kubernetes/create-weblogic-operator-inputs.yaml";

  private Path opInputYamlFilePath;
  Properties opProps = new Properties();
  private String operatorNS = "";
  private String externalRestOption = "NONE";
  private String externalRestHttpsPort = "31001";
  private String userProjectsDir = "";

  /**
   * Takes operator input properties and generates a operator input yaml file.
   *
   * @param inputProps
   * @throws Exception
   */
  public Operator(Properties inputProps, String userProjectsDir) throws Exception {
    this.opProps = inputProps;
    this.userProjectsDir = userProjectsDir;
    File d = new File(CREATE_OPERATOR_SCRIPT);
    if (!d.exists() || !d.canExecute()) {
      throw new IllegalArgumentException(
          "FAILURE: " + CREATE_OPERATOR_SCRIPT + " doesn't exist or is not executable");
    }
    operatorNS = opProps.getProperty("namespace");
    if (opProps.getProperty("externalRestOption") != null) {
      externalRestOption = opProps.getProperty("externalRestOption");
    }
    externalRestOption = opProps.getProperty("externalRestOption");
    if (externalRestOption != null && externalRestOption.equals("SELF_SIGNED_CERT")) {
      if (opProps.getProperty("externalSans") == null) {
        opProps.put("externalSans", "DNS:" + TestUtils.getHostName());
      }
      if (opProps.getProperty("externalRestHttpsPort") != null) {
        externalRestHttpsPort = opProps.getProperty("externalRestHttpsPort");
        try {
          new Integer(externalRestHttpsPort).intValue();
        } catch (NumberFormatException nfe) {
          throw new IllegalArgumentException(
              "FAILURE: Invalid value for " + "externalRestHttpsPort " + externalRestHttpsPort);
        }
      } else {
        opProps.put("externalRestHttpsPort", externalRestHttpsPort);
      }
    }

    String opInputYamlFileName = operatorNS + "-inputs.yaml";
    opInputYamlFilePath =
        new File(
                this.getClass().getClassLoader().getResource(".").getFile()
                    + "/../"
                    + opInputYamlFileName)
            .toPath();

    TestUtils.createInputFile(opProps, opInputTemplateFile, opInputYamlFilePath);
  }

  /**
   * Creates the operator and saves the operator yaml files at the given location
   *
   * @param userProjectsDir
   * @return
   */
  public boolean run() {
    StringBuffer cmd = new StringBuffer(CREATE_OPERATOR_SCRIPT);
    cmd.append(" -i ").append(opInputYamlFilePath).append(" -o ").append(userProjectsDir);
    logger.info("Running " + cmd);
    String outputStr = TestUtils.executeCommand(cmd.toString());
    logger.info("run " + outputStr);

    //TODo: Add check for the image name from the Pod as in run.sh

    if (!outputStr.contains(createScriptMessage)) {
      //use logger
      return false;
    } else {
      return true;
    }
  }
  /** verifies operator is created */
  public void verifyPodCreated() {
    logger.info("Checking if Operator pod is Running");
    //empty string for pod name as there is only one pod
    TestUtils.checkPodCreated("", operatorNS);
  }

  /** verifies operator is ready */
  public void verifyOperatorReady() {
    logger.info("Checking if Operator pod is Ready");
    //empty string for pod name as there is only one pod
    TestUtils.checkPodReady("", operatorNS);
  }

  /** Start operator and makes sure it is deployed and ready */
  public void startup() {
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
    int maxIterations = 30;
    for (int i = 0; i < maxIterations; i++) {
      String availableReplica =
          TestUtils.executeCommand(new String[] {"/bin/sh", "-c", availableReplicaCmd}).trim();
      if (!availableReplica.equals("1")) {
        if (i == maxIterations - 1) {
          throw new RuntimeException(
              "FAILURE: The WebLogic operator deployment is not available, after waiting 300 seconds");
        }
        logger.info("status is " + availableReplica + ", iteration " + i + " of " + maxIterations);
        try {
          Thread.sleep(10 * 1000);
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
              + " -o jsonpath='{.items[?(@.metadata.name == \"external-weblogic-operator-service\")]}'";
      String restService = TestUtils.executeCommand(restCmd).trim();
      if (restService.equals("")) {
        throw new RuntimeException("FAILURE: operator rest service was not created");
      }
    } else {
      logger.info("External REST service is not enabled");
    }
  }

  public void shutdown() {
    TestUtils.executeCommand(
        "kubectl delete -f "
            + userProjectsDir
            + "/weblogic-operators/"
            + operatorNS
            + "/weblogic-operator.yaml");

    logger.info("Checking REST service is deleted");
    String serviceCmd =
        "kubectl get services -n " + operatorNS + " | egrep weblogic-operator-service | wc -l";
    int maxIterations = 30;
    for (int i = 0; i < maxIterations; i++) {

      String servicenum =
          TestUtils.executeCommand(new String[] {"/bin/sh", "-c", serviceCmd}).trim();
      if (!servicenum.contains("No resources found.")) {
        if (i == maxIterations - 1) {
          throw new RuntimeException("FAILURE: Operator fail to be deleted");
        }
        logger.info("status is " + servicenum + ", iteration " + i + " of " + maxIterations);
        try {
          Thread.sleep(10 * 1000);
        } catch (InterruptedException ignore) {
        }
      } else {
        break;
      }
    }

    String getAllCmd = "kubectl get all -n " + operatorNS;

    for (int i = 0; i < maxIterations; i++) {

      String getAll = TestUtils.executeCommand(new String[] {"/bin/sh", "-c", getAllCmd}).trim();
      if (!getAll.contains("No resources found.")) {
        if (i == maxIterations - 1) {
          throw new RuntimeException("FAILURE: Operator shutdown failed.." + getAll);
        }
        logger.info("status is " + getAll + ", iteration " + i + " of " + maxIterations);
        try {
          Thread.sleep(10 * 1000);
        } catch (InterruptedException ignore) {
        }
      } else {
        break;
      }
    }
  }

  public void cleanup(String userProjectsDir) {
    if (!operatorNS.trim().equals("")) {
      TestUtils.executeCommand("rm -rf " + userProjectsDir + "/weblogic-operators/" + operatorNS);
    }
  }

  public void scale(String domainUid, String clusterName, int numOfMS) throws Exception {
    String myJsonObjStr = "{\"managedServerCount\": " + numOfMS + "}";

    //Operator REST external API URL to scale
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
    //give sometime to complete
    logger.info("Wait 30 sec for scaling to complete...");
    try {
      Thread.sleep(30 * 1000);
    } catch (InterruptedException ignore) {

    }
  }

  public void verifyDomainExists(String domainUid) throws Exception {
    //Operator REST external API URL to scale
    StringBuffer myOpRestApiUrl =
        new StringBuffer("https://")
            .append(TestUtils.getHostName())
            .append(":")
            .append(externalRestHttpsPort)
            .append("/operator/latest/domains/")
            .append(domainUid);
    TestUtils.makeOperatorGetRestCall(operatorNS, myOpRestApiUrl.toString(), userProjectsDir);
  }
}

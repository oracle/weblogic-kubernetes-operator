// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;
import oracle.kubernetes.operator.BaseTest;

/** Operator class with all the utility methods for Operator. */
public class Operator {

  public static enum RESTCertType {
    /*self-signed certificate and public key stored in a kubernetes tls secret*/
    SELF_SIGNED,
    /*Certificate signed by an auto-created CA signed by an auto-created root certificate,
     * both and stored in a kubernetes tls secret*/
    CHAIN,
    /*Certificate and public key, and stored in a kubernetes tls secret*/
    LEGACY,
    /* no Rest Support */
    NONE
  };

  public static final String CREATE_OPERATOR_SCRIPT_MESSAGE =
      "The Oracle WebLogic Server Kubernetes Operator is deployed";

  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private Map<String, Object> operatorMap;

  // default values as in create-weblogic-operator-inputs.yaml,
  // if the property is not defined here, it takes the property and its value from
  // create-weblogic-operator-inputs.yaml
  private String operatorNS = "weblogic-operator";
  private boolean externalRestEnabled = false;
  private int externalRestHttpsPort = 31001;
  private String userProjectsDir = "";

  private String generatedInputYamlFile;

  private static int maxIterationsOp = BaseTest.getMaxIterationsPod(); // 50 * 5 = 250 seconds
  private static int waitTimeOp = BaseTest.getWaitTimePod();
  private static RESTCertType restCertType = RESTCertType.SELF_SIGNED;

  /**
   * Takes operator input properties which needs to be customized and generates a operator input
   * yaml file.
   *
   * @param inputYaml
   * @throws Exception
   */
  public Operator(String inputYaml, RESTCertType restCertType) throws Exception {
    this.restCertType = restCertType;
    initialize(inputYaml);
    generateInputYaml();
    callHelmInstall();
  }

  /**
   * Takes operator input properties which needs to be customized and generates a operator input
   * yaml file.
   *
   * @param inputYaml
   * @throws Exception
   */
  public Operator(String inputYaml) throws Exception {
    initialize(inputYaml);
    generateInputYaml();
    callHelmInstall();
  }
  /**
   * Takes operator input properties which needs to be customized and generates a operator input
   * yaml file, with option to create operator namespace, serviceaccount, domain namespace.
   *
   * @param inputMap
   * @param opNS
   * @param opSA
   * @param targetdomainNS
   * @param restCertType
   * @throws Exception
   */
  public Operator(
      Map<String, Object> inputMap,
      boolean opNS,
      boolean opSA,
      boolean targetdomainNS,
      RESTCertType restCertType)
      throws Exception {
    this.restCertType = restCertType;
    initialize(inputMap, opNS, opSA, targetdomainNS);
    generateInputYaml();
  }
  /**
   * Takes operator input properties from a map which needs to be customized and generates a
   * operator input yaml file.
   *
   * @param inputMap
   * @throws Exception
   */
  public Operator(Map<String, Object> inputMap, RESTCertType restCertType) throws Exception {
    this.restCertType = restCertType;
    initialize(inputMap, true, true, true);
    generateInputYaml();
  }

  /**
   * verifies operator pod is created
   *
   * @throws Exception
   */
  public void verifyPodCreated() throws Exception {
    logger.info("Checking if Operator pod is Running");
    // empty string for pod name as there is only one pod
    TestUtils.checkPodCreated("", operatorNS);
  }

  /**
   * verifies operator pod is ready
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
    callHelmInstall();

    logger.info("Checking Operator deployment");

    String availableReplicaCmd =
        "kubectl get deploy weblogic-operator -n "
            + operatorNS
            + " -o jsonpath='{.status.availableReplicas}'";
    for (int i = 0; i < maxIterationsOp; i++) {
      ExecResult replicaResult = ExecCommand.exec(availableReplicaCmd);
      if (replicaResult.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command "
                + availableReplicaCmd
                + " failed, returned "
                + replicaResult.stderr());
      }
      String availableReplica = replicaResult.stdout().trim();
      if (!availableReplica.equals("1")) {
        if (i == maxIterationsOp - 1) {
          throw new RuntimeException(
              "FAILURE: The WebLogic operator deployment is not available, after waiting 300 seconds");
        }
        logger.info(
            "status is " + availableReplica + ", iteration " + i + " of " + maxIterationsOp);
        Thread.sleep(waitTimeOp * 1000);

      } else {
        break;
      }
    }

    verifyPodCreated();
    verifyOperatorReady();
    verifyExternalRESTService();
  }
  /**
   * Verify external REST service is running
   *
   * @throws Exception
   */
  public void verifyExternalRESTService() throws Exception {
    if (externalRestEnabled) {
      logger.info("Checking REST service is running");
      String restCmd =
          "kubectl get services -n "
              + operatorNS
              + " -o jsonpath='{.items[?(@.metadata.name == \"external-weblogic-operator-svc\")]}'";
      logger.info("Cmd to check REST service " + restCmd);
      ExecResult result = ExecCommand.exec(restCmd);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command " + restCmd + " failed, returned " + result.stderr());
      }
      String restService = result.stdout().trim();
      logger.info("cmd result for REST service " + restService);
      if (!restService.contains("name:external-weblogic-operator-svc")) {
        throw new RuntimeException("FAILURE: operator rest service was not created");
      }
    } else {
      logger.info("External REST service is not enabled");
    }
  }

  /**
   * delete operator helm release
   *
   * @throws Exception
   */
  public void destroy() throws Exception {
    String cmd = "helm del --purge " + operatorMap.get("releaseName");
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    logger.info("Checking REST service is deleted");
    runCommandInLoop("kubectl get services -n " + operatorNS + " | egrep weblogic-operator-svc ");
  }

  /**
   * scale the given cluster in a domain to the given number of servers using Operator REST API
   *
   * @param domainUid
   * @param clusterName
   * @param numOfMS
   * @throws Exception
   */
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

    TestUtils.makeOperatorPostRestCall(this, myOpRestApiUrl.toString(), myJsonObjStr);
    // give sometime to complete
    logger.info("Wait 30 sec for scaling to complete...");
    Thread.sleep(30 * 1000);
  }

  /**
   * Verify the domain exists using Operator REST Api
   *
   * @param domainUid
   * @throws Exception
   */
  public void verifyDomainExists(String domainUid) throws Exception {
    // Operator REST external API URL to scale
    StringBuffer myOpRestApiUrl =
        new StringBuffer("https://")
            .append(TestUtils.getHostName())
            .append(":")
            .append(externalRestHttpsPort)
            .append("/operator/latest/domains/")
            .append(domainUid);
    TestUtils.makeOperatorGetRestCall(this, myOpRestApiUrl.toString());
  }

  /**
   * Verify the Operator's REST Api is working fine over TLS
   *
   * @throws Exception
   */
  public void verifyOperatorExternalRESTEndpoint() throws Exception {
    // Operator REST external API URL to scale
    StringBuffer myOpRestApiUrl =
        new StringBuffer("https://")
            .append(TestUtils.getHostName())
            .append(":")
            .append(externalRestHttpsPort)
            .append("/operator/");
    TestUtils.makeOperatorGetRestCall(this, myOpRestApiUrl.toString());
  }

  public Map<String, Object> getOperatorMap() {
    return operatorMap;
  }

  public void callHelmInstall() throws Exception {
    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm install kubernetes/charts/weblogic-operator ");
    cmd.append(" --name ")
        .append(operatorMap.get("releaseName"))
        .append(" --values ")
        .append(generatedInputYamlFile)
        .append(" --namespace ")
        .append(operatorNS)
        .append(" --wait --timeout 60");
    logger.info("Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      reportHelmFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);
  }

  public void callHelmUpgrade(String upgradeSet) throws Exception {
    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm upgrade ")
        .append(operatorMap.get("releaseName"))
        .append(" kubernetes/charts/weblogic-operator ")
        .append(" --set \"")
        .append(upgradeSet)
        .append("\" --reuse-values ")
        .append(" --wait --timeout 60");
    logger.info("Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      reportHelmFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);
  }

  public String getHelmValues() throws Exception {
    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm get values ")
        .append(operatorMap.get("releaseName"));

    logger.info("Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      reportHelmFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);
    return outputStr;
  }

  private void reportHelmFailure(String cmd, ExecResult result) throws Exception {
    logger.info("reportHelmFailure " + result);
    throw new RuntimeException(getExecFailure(cmd, result));
  }

  private String getExecFailure(String cmd, ExecResult result) throws Exception {
    return "FAILURE: command "
        + cmd
        + " failed, stdout:\n"
        + result.stdout()
        + "stderr:\n"
        + result.stderr();
  }

  private void generateInputYaml() throws Exception {
    Path parentDir =
        Files.createDirectories(Paths.get(userProjectsDir + "/weblogic-operators/" + operatorNS));
    generatedInputYamlFile = parentDir + "/weblogic-operator-values.yaml";
    TestUtils.createInputFile(operatorMap, generatedInputYamlFile);
    StringBuilder sb = new StringBuilder(200);
    sb.append(BaseTest.getProjectRoot());
    switch (restCertType) {
      case LEGACY:
        sb.append(
            "/integration-tests/src/test/resources/scripts/legacy-generate-external-rest-identity.sh ");
        break;
      case CHAIN:
        sb.append(
            "/integration-tests/src/test/resources/scripts/generate-external-rest-identity-chain.sh ");
        sb.append(" -n ");
        sb.append(operatorNS);
        break;
      case SELF_SIGNED:
        sb.append("/kubernetes/samples/scripts/rest/generate-external-rest-identity.sh ");
        sb.append(" -n ");
        sb.append(operatorNS);
        break;
    }
    sb.append(" DNS:");
    sb.append(TestUtils.getHostName());
    sb.append(" >> ");
    sb.append(generatedInputYamlFile);
    logger.info("Invoking " + sb.toString());
    ExecCommand.exec(sb.toString());

    /* String content = new String(Files.readAllBytes(Paths.get(generatedInputYamlFile)));
    logger.info("Content of weblogic-operator-values.yaml \n" + content); */
  }

  private void runCommandInLoop(String command) throws Exception {
    for (int i = 0; i < maxIterationsOp; i++) {

      ExecResult result = ExecCommand.exec(command);
      if (result.exitValue() == 0) {

        if (i == maxIterationsOp - 1) {
          throw new RuntimeException("FAILURE: Operator fail to be deleted");
        }
        logger.info("status is " + result.stdout() + ", iteration " + i + " of " + maxIterationsOp);
        Thread.sleep(waitTimeOp * 1000);
      } else {
        break;
      }
    }
  }

  private void initialize(String inputYaml) throws Exception {
    initialize(TestUtils.loadYaml(inputYaml), true, true, true);
  }

  private void initialize(
      Map<String, Object> inputMap, boolean opNS, boolean opSA, boolean targetdomainNS)
      throws Exception {
    operatorMap = inputMap;
    userProjectsDir = BaseTest.getUserProjectsDir();
    operatorNS = (String) operatorMap.getOrDefault("namespace", operatorNS);

    if (operatorMap.get("releaseName") == null) {
      throw new RuntimeException("FAILURE: releaseName cann't be null");
    }
    if (opNS) {
      ExecCommand.exec("kubectl delete namespace " + operatorNS);

      // create operator namespace
      ExecCommand.exec("kubectl create namespace " + operatorNS);
    }
    if (opSA) {
      // create operator service account
      String serviceAccount = (String) operatorMap.get("serviceAccount");
      if (serviceAccount != null && !serviceAccount.equals("default")) {
        ExecResult result =
            ExecCommand.exec(
                "kubectl create serviceaccount " + serviceAccount + " -n " + operatorNS);
        if (result.exitValue() != 0) {
          throw new RuntimeException(
              "FAILURE: Couldn't create serviceaccount "
                  + serviceAccount
                  + ". Cmd returned "
                  + result.stdout()
                  + "\n"
                  + result.stderr());
        }
      }
    }
    if (targetdomainNS) {
      // create domain namespaces

      ArrayList<String> domainNamespaces = (ArrayList<String>) operatorMap.get("domainNamespaces");
      for (int i = 0; i < domainNamespaces.size(); i++) {
        String domainNS = domainNamespaces.get(i);
        logger.info("domainNamespace " + domainNS);
        if (!domainNS.equals("default")) {
          logger.info("Creating domain namespace " + domainNS);
          ExecCommand.exec("kubectl create namespace " + domainNS);
        }
      }
    }
    // customize the inputs yaml file to generate a self-signed cert for the external Operator REST
    // https port
    externalRestEnabled =
        (boolean) operatorMap.getOrDefault("externalRestEnabled", externalRestEnabled);
    if (externalRestEnabled) {
      if (operatorMap.get("externalRestHttpsPort") != null) {
        try {
          externalRestHttpsPort = ((Integer) operatorMap.get("externalRestHttpsPort")).intValue();

        } catch (NumberFormatException nfe) {
          throw new IllegalArgumentException(
              "FAILURE: Invalid value for " + "externalRestHttpsPort " + externalRestHttpsPort);
        }
      } else {
        operatorMap.put("externalRestHttpsPort", externalRestHttpsPort);
      }
    }

    // customize the inputs yaml file to use our pre-built docker image
    // IMAGE_NAME_OPERATOR & IMAGE_TAG_OPERATOR variables are used for wercker
    if (System.getenv("IMAGE_NAME_OPERATOR") != null
        && System.getenv("IMAGE_TAG_OPERATOR") != null) {
      operatorMap.put(
          "image",
          System.getenv("IMAGE_NAME_OPERATOR") + ":" + System.getenv("IMAGE_TAG_OPERATOR"));
    } else {
      operatorMap.put(
          "image",
          "weblogic-kubernetes-operator"
              + ":test_"
              + BaseTest.getBranchName().replaceAll("/", "_"));
    }

    if (System.getenv("IMAGE_PULL_POLICY_OPERATOR") != null) {
      operatorMap.put("imagePullPolicy", System.getenv("IMAGE_PULL_POLICY_OPERATOR"));
    }
  }

  public String getOperatorNamespace() {
    return operatorNS;
  }

  public String getUserProjectsDir() {
    return userProjectsDir;
  }

  public RESTCertType getRestCertType() {
    return restCertType;
  }
}

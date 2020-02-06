// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Level;

import oracle.kubernetes.operator.BaseTest;

/**
 * Operator class with all the utility methods for Operator.
 */
public class Operator {

  public static final String CREATE_OPERATOR_SCRIPT_MESSAGE =
      "The Oracle WebLogic Server Kubernetes Operator is deployed";

  private static int maxIterationsOp = BaseTest.getMaxIterationsPod(); // 50 * 5 = 250 seconds
  private static int waitTimeOp = BaseTest.getWaitTimePod();
  private RestCertType restCertType = RestCertType.SELF_SIGNED;
  private Map<String, Object> operatorMap;
  // default values as in create-weblogic-operator-inputs.yaml,
  // if the property is not defined here, it takes the property and its value from
  // create-weblogic-operator-inputs.yaml
  private String operatorNS = "weblogic-operator";
  private boolean externalRestEnabled = false;
  private int externalRestHttpsPort = 31001;
  private String userProjectsDir = "";
  private String generatedInputYamlFile;

  /**
   * Takes operator input properties which needs to be customized and generates a operator input
   * yaml file.
   *
   * @param inputYaml input
   * @throws Exception exception
   */
  public Operator(String inputYaml, RestCertType restCertType) throws Exception {
    this.restCertType = restCertType;
    initialize(inputYaml);
    generateInputYaml();
    callHelmInstall();
  }

  /**
   * Takes operator input properties which needs to be customized and generates a operator input
   * yaml file.
   *
   * @param inputYaml input
   * @throws Exception exception
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
   * @param inputMap       input
   * @param opNS           opNS
   * @param opSA           opSA
   * @param targetdomainNS target
   * @param restCertType   cert
   * @throws Exception exception
   */
  public Operator(
      Map<String, Object> inputMap,
      boolean opNS,
      boolean opSA,
      boolean targetdomainNS,
      RestCertType restCertType)
      throws Exception {
    this.restCertType = restCertType;
    initialize(inputMap, opNS, opSA, targetdomainNS);
    generateInputYaml();
  }

  /**
   * Takes operator input properties from a map which needs to be customized and generates a
   * operator input yaml file.
   *
   * @param inputMap input
   * @throws Exception exception
   */
  public Operator(Map<String, Object> inputMap, RestCertType restCertType) throws Exception {
    this.restCertType = restCertType;
    initialize(inputMap, true, true, true);
    generateInputYaml();
  }

  /**
   * verifies operator pod is created.
   *
   * @throws Exception exception
   */
  public void verifyPodCreated() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Checking if Operator pod is Running");
    // empty string for pod name as there is only one pod
    TestUtils.checkPodCreated("", operatorNS);
  }

  /**
   * verifies operator pod is deleted.
   *
   * @throws Exception exception
   */
  public void verifyPodDeleted() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Checking if Operator pod is deleted");
    // empty string for pod name as there is only one pod
    TestUtils.checkPodDeleted("", operatorNS);
  }

  /**
   * verifies operator pod is ready.
   *
   * @throws Exception exception
   */
  public void verifyOperatorReady() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Checking if Operator pod is Ready");
    // empty string for pod name as there is only one pod
    TestUtils.checkPodReady("", operatorNS);
  }

  /**
   * verifies operator pod is ready.
   *
   * @param containerNum - container number in a pod
   * @throws Exception exception
   */
  public void verifyOperatorReady(String containerNum) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Checking if Operator pod is Ready");
    // empty string for pod name as there is only one pod
    TestUtils.checkPodReady("", operatorNS, containerNum);
  }

  /**
   * Start operator and makes sure it is deployed and ready.
   *
   * @throws Exception exception
   */
  public void create() throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "Starting Operator");
    callHelmInstall();

    LoggerHelper.getLocal().log(Level.INFO, "Checking Operator deployment");

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
        LoggerHelper.getLocal().log(Level.INFO,
            "status is " + availableReplica + ", iteration " + i + " of " + maxIterationsOp);
        Thread.sleep(waitTimeOp * 1000);

      } else {
        break;
      }
    }

    verifyPodCreated();
    verifyOperatorReady();
    verifyExternalRestService();
  }

  /**
   * Verify external REST service is running.
   *
   * @throws Exception exception
   */
  public void verifyExternalRestService() throws Exception {
    if (externalRestEnabled) {
      LoggerHelper.getLocal().log(Level.INFO, "Checking REST service is running");
      String restCmd =
          "kubectl get services -n "
              + operatorNS
              + " -o jsonpath='{.items[?(@.metadata.name == \"external-weblogic-operator-svc\")]}'";
      LoggerHelper.getLocal().log(Level.INFO, "Cmd to check REST service " + restCmd);
      ExecResult result = ExecCommand.exec(restCmd);
      if (result.exitValue() != 0) {
        throw new RuntimeException(
            "FAILURE: command " + restCmd + " failed, returned " + result.stderr());
      }
      String restService = result.stdout().trim();
      LoggerHelper.getLocal().log(Level.INFO, "cmd result for REST service " + restService);
      if (!restService.contains("name:external-weblogic-operator-svc")) {
        throw new RuntimeException("FAILURE: operator rest service was not created");
      }
    } else {
      LoggerHelper.getLocal().log(Level.INFO, "External REST service is not enabled");
    }
  }

  /**
   * delete operator helm release.
   *
   * @throws Exception exception
   */
  public void destroy() throws Exception {
    String cmd = "helm del --purge " + operatorMap.get("releaseName");
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmd + " failed, returned " + result.stderr());
    }
    LoggerHelper.getLocal().log(Level.INFO, "Checking REST service is deleted");
    runCommandInLoop("kubectl get services -n " + operatorNS + " | egrep weblogic-operator-svc ");
  }

  /**
   * scale the given cluster in a domain to the given number of servers using Operator REST API.
   *
   * @param domainUid   uid
   * @param clusterName cluster
   * @param numOfMS     num
   * @throws Exception exception
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
    LoggerHelper.getLocal().log(Level.INFO, "Wait 30 sec for scaling to complete...");
    Thread.sleep(30 * 1000);
  }

  /**
   * Verify the domain exists using Operator REST Api.
   *
   * @param domainUid uid
   * @throws Exception exception
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
   * Verify the Operator's REST Api is working fine over TLS.
   *
   * @throws Exception exception
   */
  public void verifyOperatorExternalRestEndpoint() throws Exception {
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

  /**
   * Call Helm install.
   * @throws Exception on failure
   */
  public void callHelmInstall() throws Exception {
    String imagePullPolicy =
        System.getenv("IMAGE_PULL_POLICY_OPERATOR") != null
            ? System.getenv("IMAGE_PULL_POLICY_OPERATOR")
            : "IfNotPresent";
    StringBuffer cmd = new StringBuffer("");
    if (operatorMap.containsKey("operatorGitVersion")
        && operatorMap.containsKey("operatorGitVersionDir")) {
      TestUtils.exec(
          "cd "
              + operatorMap.get("operatorGitVersionDir")
              + " && git clone -b "
              + operatorMap.get("operatorGitVersion")
              + " https://github.com/oracle/weblogic-kubernetes-operator");
      cmd.append("cd ");
      cmd.append(operatorMap.get("operatorGitVersionDir"))
          .append("/weblogic-kubernetes-operator")
          .append(" && helm install kubernetes/charts/weblogic-operator ");
    } else {
      cmd.append("cd ");
      cmd.append(BaseTest.getProjectRoot())
          .append(" && helm install kubernetes/charts/weblogic-operator ");
    }
    cmd.append(" --name ")
        .append(operatorMap.get("releaseName"))
        .append(" --values ")
        .append(generatedInputYamlFile)
        .append(" --namespace ")
        .append(operatorNS)
        .append(" --set \"imagePullPolicy=")
        .append(imagePullPolicy)
        .append("\" --wait --timeout 180");
    LoggerHelper.getLocal().log(Level.INFO, "Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      reportHelmFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    LoggerHelper.getLocal().log(Level.INFO, "Command returned " + outputStr);
  }

  /**
   * Call Helm upgrade.
   * @param upgradeSet upgrade properties
   * @throws Exception on failure
   */
  public void callHelmUpgrade(String upgradeSet) throws Exception {
    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm upgrade ")
        .append(operatorMap.get("releaseName"))
        .append(" kubernetes/charts/weblogic-operator ")
        .append(" --set \"")
        .append(upgradeSet)
        .append("\" --reuse-values ")
        .append(" --wait --timeout 180");
    LoggerHelper.getLocal().log(Level.INFO, "Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      reportHelmFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    LoggerHelper.getLocal().log(Level.INFO, "Command returned " + outputStr);
  }

  /**
   * get Helm values.
   * @return values
   * @throws Exception on failure
   */
  public String getHelmValues() throws Exception {
    StringBuffer cmd = new StringBuffer("cd ");
    cmd.append(BaseTest.getProjectRoot())
        .append(" && helm get values ")
        .append(operatorMap.get("releaseName"));

    LoggerHelper.getLocal().log(Level.INFO, "Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      reportHelmFailure(cmd.toString(), result);
    }
    String outputStr = result.stdout().trim();
    LoggerHelper.getLocal().log(Level.INFO, "Command returned " + outputStr);
    return outputStr;
  }

  private void reportHelmFailure(String cmd, ExecResult result) throws Exception {
    LoggerHelper.getLocal().log(Level.INFO, "reportHelmFailure " + result);
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
      default:
        throw new IllegalArgumentException();
    }
    // here we are assuming that if the "host name" starts with a digit, then it is actually
    // an IP address, and so we need to use the "IP" prefix in the SANS.
    if (Character.isDigit(TestUtils.getHostName().charAt(0))) {
      sb.append(" IP:");
    } else {
      sb.append(" DNS:");
    }
    sb.append(TestUtils.getHostName());
    sb.append(" >> ");
    sb.append(generatedInputYamlFile);
    LoggerHelper.getLocal().log(Level.INFO, "Invoking " + sb.toString());
    ExecCommand.exec(sb.toString());

    /* String content = new String(Files.readAllBytes(Paths.get(generatedInputYamlFile)));
    LoggerHelper.getLocal().log(Level.INFO, "Content of weblogic-operator-values.yaml \n" + content); */
  }

  private void runCommandInLoop(String command) throws Exception {
    for (int i = 0; i < maxIterationsOp; i++) {

      ExecResult result = ExecCommand.exec(command);
      if (result.exitValue() == 0) {

        if (i == maxIterationsOp - 1) {
          throw new RuntimeException("FAILURE: Operator fail to be deleted");
        }
        LoggerHelper.getLocal().log(Level.INFO, "status is " + result.stdout()
            + ", iteration " + i + " of " + maxIterationsOp);
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
    userProjectsDir = (String) operatorMap.get("userProjectsDir");
    operatorNS = (String) operatorMap.getOrDefault("namespace", operatorNS);

    if (operatorMap.get("releaseName") == null) {
      throw new RuntimeException("FAILURE: releaseName cann't be null");
    }
    if (opNS) {
      ExecCommand.exec("kubectl --grace-period=1 --timeout=1s delete namespace " + operatorNS + " --ignore-not-found");
      Thread.sleep(10000);
      // create operator namespace
      TestUtils.exec("kubectl create namespace " + operatorNS, true);
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
      if (domainNamespaces != null) {
        for (int i = 0; i < domainNamespaces.size(); i++) {
          String domainNS = domainNamespaces.get(i);
          LoggerHelper.getLocal().log(Level.INFO, "domainNamespace " + domainNS);
          if (!domainNS.equals("default")) {
            LoggerHelper.getLocal().log(Level.INFO, "Creating domain namespace " + domainNS);
            ExecCommand.exec("kubectl create namespace " + domainNS);
          }
        }
      }
    }
    // customize the inputs yaml file to generate a self-signed cert for the external Operator REST
    // https port
    externalRestEnabled = operatorMap.containsKey("externalRestEnabled")
        ? (new Boolean((operatorMap.get("externalRestEnabled")).toString()).booleanValue()) : externalRestEnabled;
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
    // IMAGE_NAME_OPERATOR & IMAGE_TAG_OPERATOR variables are used for shared cluster
    if (operatorMap.containsKey("operatorImageName")
        && operatorMap.containsKey("operatorImageTag")) {
      operatorMap.put(
          "image",
          operatorMap.get("operatorImageName") + ":" + operatorMap.get("operatorImageTag"));
    } else if (System.getenv("IMAGE_NAME_OPERATOR") != null
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

  /**
   * restart operator pod using replicas.
   *
   * @throws Exception exception
   */
  public void restartUsingReplicas() throws Exception {
    stopUsingReplicas();
    startUsingReplicas();
  }

  /**
   * stop operator pod by scaling its replicaset to 0.
   *
   * @throws Exception exception
   */
  public void stopUsingReplicas() throws Exception {
    String cmd = "kubectl scale --replicas=0 deployment/weblogic-operator" + " -n " + operatorNS;
    LoggerHelper.getLocal().log(Level.INFO, "Undeploy Operator using command:\n" + cmd);

    ExecResult result = TestUtils.exec(cmd);

    LoggerHelper.getLocal().log(Level.INFO, "stdout : \n" + result.stdout());

    LoggerHelper.getLocal().log(Level.INFO, "Checking if operator pod is deleted");
    verifyPodDeleted();
  }

  /**
   * start operator pod by scaling its replicaset to 1.
   *
   * @throws Exception exception
   */
  public void startUsingReplicas() throws Exception {
    String cmd = "kubectl scale --replicas=1 deployment/weblogic-operator" + " -n " + operatorNS;
    LoggerHelper.getLocal().log(Level.INFO, "Deploy Operator using command:\n" + cmd);

    ExecResult result = TestUtils.exec(cmd);

    LoggerHelper.getLocal().log(Level.INFO, "Checking if operator pod is running");
    verifyPodCreated();
    verifyOperatorReady();
  }

  public String getOperatorNamespace() {
    return operatorNS;
  }

  public String getUserProjectsDir() {
    return userProjectsDir;
  }

  public RestCertType getRestCertType() {
    return restCertType;
  }

  /**
   * Retrieve Operator pod name.
   *
   * @return Operator pod name
   * @throws Exception exception
   */
  public String getOperatorPodName() throws Exception {
    String cmd =
        "kubectl get pod -n "
            + getOperatorNamespace()
            + " -o jsonpath=\"{.items[0].metadata.name}\"";
    LoggerHelper.getLocal().log(Level.INFO, "Command to query Operator pod name: " + cmd);
    ExecResult result = TestUtils.exec(cmd);

    return result.stdout();
  }

  public static enum RestCertType {
    /*self-signed certificate and public key stored in a kubernetes tls secret*/
    SELF_SIGNED,
    /*Certificate signed by an auto-created CA signed by an auto-created root certificate,
     * both and stored in a kubernetes tls secret*/
    CHAIN,
    /*Certificate and public key, and stored in a kubernetes tls secret*/
    LEGACY,
    /* no Rest Support */
    NONE
  }

}

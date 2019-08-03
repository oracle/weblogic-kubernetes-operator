// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import oracle.kubernetes.operator.BaseTest;
import oracle.kubernetes.operator.utils.Operator.RestCertType;
import org.glassfish.jersey.jsonp.JsonProcessingFeature;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class TestUtils {
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private static K8sTestUtils k8sTestUtils = new K8sTestUtils();

  /**
   * Checks if pod is ready.
   *
   * @param podName pod name
   * @param domainNS namespace
   * @throws Exception exception
   */
  public static void checkPodReady(String podName, String domainNS) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    // check for admin pod
    checkCmdInLoop(cmd.toString(), "1/1", podName);
  }

  /**
   * check pod is in Running state.
   *
   * @param podName - pod name
   * @param domainNS - domain namespace name
   * @param containerNum - container number in a pod
   * @throws Exception exception
   */
  public static void checkPodReady(String podName, String domainNS, String containerNum)
      throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    // check for the pod passed from parameter podName
    checkCmdInLoop(cmd.toString(), containerNum, podName);
  }

  /**
   * Checks that pod is created.
   *
   * @param podName - pod name
   * @param domainNS - domain namespace name
   */
  public static void checkPodCreated(String podName, String domainNS) throws Exception {

    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    // check for admin pod
    checkCmdInLoop(cmd.toString(), "Running", podName);
  }

  /**
   * Checks that pod is initializing.
   *
   * @param podName - pod name
   * @param domainNS - domain namespace name
   */
  public static void checkPodInitializing(String podName, String domainNS) throws Exception {

    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    // check for admin pod
    checkCmdInLoop(cmd.toString(), "Init", podName);
  }

  /**
   * check pod is in Terminating state.
   *
   * @param podName - pod name
   * @param domainNS - domain namespace name
   * @throws Exception exception
   */
  public static void checkPodTerminating(String podName, String domainNS) throws Exception {

    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    // check for admin pod
    checkCmdInLoop(cmd.toString(), "Terminating", podName);
  }

  /**
   * Checks that service is created.
   *
   * @param serviceName service name
   * @param domainNS namespace
   * @throws Exception exception
   */
  public static void checkServiceCreated(String serviceName, String domainNS) throws Exception {
    int i = 0;
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get service ").append(serviceName).append(" -n ").append(domainNS);

    // check for service
    while (i < BaseTest.getMaxIterationsPod()) {
      ExecResult result = ExecCommand.exec(cmd.toString());

      // service might not have been created
      if (result.exitValue() != 0
          || (result.exitValue() == 0 && !result.stdout().contains(serviceName))) {
        logger.info("Output for " + cmd + "\n" + result.stdout() + "\n " + result.stderr());

        // check for last iteration
        if (i == (BaseTest.getMaxIterationsPod() - 1)) {
          throw new RuntimeException("FAILURE: service is not created, exiting!");
        }
        logger.info(
            "Service is not created Ite ["
                + i
                + "/"
                + BaseTest.getMaxIterationsPod()
                + "], sleeping "
                + BaseTest.getWaitTimePod()
                + " seconds more");
        Thread.sleep(BaseTest.getWaitTimePod() * 1000);
        i++;
      } else {
        logger.info("Service " + serviceName + " is Created");
        break;
      }
    }
  }

  /**
   * Creates input file.
   *
   * @param map - map with attributes
   * @param generatedInputYamlFile - output file with replaced values
   * @throws Exception exception
   */
  public static void createInputFile(Map<String, Object> map, String generatedInputYamlFile)
      throws Exception {
    logger.info("Creating input yaml file at " + generatedInputYamlFile);

    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);

    Yaml yaml = new Yaml(options);
    java.io.FileWriter writer = new java.io.FileWriter(generatedInputYamlFile);
    yaml.dump(map, writer);
    writer.close();
  }

  public static String getHostName() throws Exception {
    if (System.getenv("K8S_NODEPORT_HOST") != null) {
      return System.getenv("K8S_NODEPORT_HOST");
    } else {
      // ExecResult result = ExecCommand.exec("hostname | awk -F. '{print $1}'");
      ExecResult result = ExecCommand.exec("hostname");
      return result.stdout().trim();
    }
  }

  public static int getClusterReplicas(String domainUid, String clusterName, String domainNS)
      throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get domain ")
        .append(domainUid)
        .append(" -n ")
        .append(domainNS)
        .append(" -o jsonpath='{.spec.clusters[?(@.clusterName == \"")
        .append(clusterName)
        .append("\")].replicas }'");
    logger.fine("getClusterReplicas cmd =" + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    int replicas = 0;
    if (result.exitValue() == 0) {
      try {
        replicas = new Integer(result.stdout().trim()).intValue();
      } catch (NumberFormatException nfe) {
        throw new RuntimeException(
            "FAILURE: Kubectl command " + cmd + " returned non-integer value " + replicas);
      }
    } else {
      throw new RuntimeException("FAILURE: Kubectl command " + cmd + " failed " + result.stderr());
    }
    return replicas;
  }

  public static void checkPodDeleted(String podName, String domainNS) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl -n ")
        .append(domainNS)
        .append(" get pod ")
        .append(podName)
        .append(" | grep \"^")
        .append(podName)
        .append(" \" | wc -l");
    checkCmdInLoopForDelete(cmd.toString(), "\"" + podName + "\" not found", podName);
  }

  public static void checkDomainDeleted(String domainUid, String domainNS) throws Exception {

    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get domain ")
        .append(domainUid)
        .append(" -n ")
        .append(domainNS)
        .append(" | egrep ")
        .append(domainUid)
        .append(" | wc -l");

    checkCmdInLoopForDelete(cmd.toString(), "\"" + domainUid + "\" not found", domainUid);
  }

  public static void checkNamespaceDeleted(String namespace) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get ns ").append(namespace);
    checkCmdInLoopForDelete(cmd.toString(), "\"" + namespace + "\" not found", namespace);
  }

  public static void deletePvc(String pvcName, String namespace, String domainUid, String jobName)
      throws Exception {
    StringBuffer cmdDelJob = new StringBuffer("kubectl delete job ");
    cmdDelJob.append(domainUid).append("-" + jobName + " -n ").append(namespace);
    logger.info("Deleting job " + cmdDelJob);
    exec(cmdDelJob.toString());

    StringBuffer cmdDelPvc = new StringBuffer("kubectl delete pvc ");
    cmdDelPvc.append(pvcName).append(" -n ").append(namespace);
    logger.info("Deleting PVC " + cmdDelPvc);
    exec(cmdDelPvc.toString());
  }

  public static ExecResult exec(String cmd) throws Exception {
    return exec(cmd, false);
  }

  public static ExecResult exec(String cmd, boolean debug) throws Exception {
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0 || debug) {
      logger.info(
          "\nCommand "
              + cmd
              + "\nreturn value: "
              + result.exitValue()
              + "\nstderr = "
              + result.stderr()
              + "\nstdout = "
              + result.stdout());
    }
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: Command "
              + cmd
              + " failed with stderr = "
              + result.stderr()
              + " \n stdout = "
              + result.stdout());
    }

    return result;
  }

  public static boolean checkPvReleased(String pvBaseName, String namespace) throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl get pv ");
    cmd.append(pvBaseName).append("-pv -n ").append(namespace);

    int i = 0;
    while (i < BaseTest.getMaxIterationsPod()) {
      logger.info("Iteration " + i + " Checking if PV is Released " + cmd);
      ExecResult result = ExecCommand.exec(cmd.toString());
      if (result.exitValue() != 0
          || result.exitValue() == 0 && !result.stdout().contains("Released")) {
        if (i == (BaseTest.getMaxIterationsPod() - 1)) {
          throw new RuntimeException("FAILURE: PV is not in Released status, exiting!");
        }
        logger.info("PV is not in Released status," + result.stdout() + "\n " + result.stderr());
        Thread.sleep(BaseTest.getWaitTimePod() * 1000);
        i++;

      } else {
        logger.info("PV is in Released status," + result.stdout());
        break;
      }
    }
    return true;
  }

  /**
   * NAME TYPE CLUSTER-IP EXTERNAL-IP PORT(S) domain1-cluster-cluster-1 ClusterIP 10.105.146.61
   * 30032/TCP,8001/TCP domain1-managed-server1 ClusterIP None 30032/TCP,8001/TCP.
   *
   * @param service service
   * @param namespace namespace
   * @param protocol portocol
   * @param port port
   * @return true, if service has channel port
   * @throws Exception exception
   */
  public static boolean checkHasServiceChannelPort(
      String service, String namespace, String protocol, int port) throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl get services ");
    cmd.append(" -n ").append(namespace);
    logger.info(" Find services in namespage " + namespace + " with command: '" + cmd + "'");

    ExecResult result = ExecCommand.exec(cmd.toString());
    String stdout = result.stdout();
    logger.info(" Services found: ");
    logger.info(stdout);
    String[] stdoutlines = stdout.split("\\r?\\n");
    if (result.exitValue() == 0 && stdoutlines.length > 0) {
      for (String stdoutline : stdoutlines) {
        if (stdoutline.contains(service) && stdoutline.contains(port + "/" + protocol)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * kubectl describe service serviceName -n namespace.
   *
   * @param namespace namespace where the service is located
   * @param serviceName name of the service to be described
   * @return String containing output of the kubectl describe service command
   * @throws Exception exception
   */
  public static String describeService(String namespace, String serviceName) throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl describe service ");
    cmd.append(serviceName);
    cmd.append(" -n ").append(namespace);
    logger.info(
        " Describe service "
            + serviceName
            + " in namespage "
            + namespace
            + " with command: '"
            + cmd
            + "'");

    ExecResult result = ExecCommand.exec(cmd.toString());
    String stdout = result.stdout();
    logger.info(" Service " + serviceName + " found: ");
    logger.info(stdout);
    return stdout;
  }

  /**
   * kubectl get pods -o wide -n namespace.
   *
   * @param namespace namespace in which the pods are to be listed
   * @return String containing output of the kubectl get pods command
   * @throws Exception exception
   */
  public static String getPods(String namespace) throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl get pods -o wide ");
    cmd.append(" -n ").append(namespace);
    logger.info(" Get pods in namespage " + namespace + " with command: '" + cmd + "'");

    ExecResult result = ExecCommand.exec(cmd.toString());
    String stdout = result.stdout();
    logger.info(" Pods found: ");
    logger.info(stdout);
    return stdout;
  }

  /**
   * First, kill the mgd server process in the container three times to cause the node manager to
   * mark the server 'failed not restartable'. This in turn is detected by the liveness probe, which
   * initiates a pod restart.
   *
   * @param domainUid uid
   * @param serverName server name
   * @param namespace namespace
   * @throws Exception exception
   */
  public static void testWlsLivenessProbe(String domainUid, String serverName, String namespace)
      throws Exception {
    String podName = domainUid + "-" + serverName;
    int initialRestartCnt = getPodRestartCount(podName, namespace);
    String filePath =
        BaseTest.getUserProjectsDir() + "/weblogic-domains/" + domainUid + "/killserver.sh";
    // create file to kill server process
    FileWriter fw = new FileWriter(filePath);
    fw.write("#!/bin/bash\n");
    fw.write("kill -9 `jps | grep Server | awk '{print $1}'`");
    fw.close();
    new File(filePath).setExecutable(true, false);

    // copy file to pod
    copyFileViaCat(filePath, "/shared/killserver.sh", podName, namespace);

    // kill server process 3 times
    for (int i = 0; i < 3; i++) {
      ExecResult result =
          kubectlexecNoCheck(
              podName,
              namespace,
              "-- bash -c 'chmod +x /shared/killserver.sh && /shared/killserver.sh'");
      logger.info("kill server process command exitValue " + result.exitValue());
      logger.info(
          "kill server process command result " + result.stdout() + " stderr " + result.stderr());
      Thread.sleep(2 * 1000);
    }
    // one more time so that liveness probe restarts
    kubectlexecNoCheck(podName, namespace, "/shared/killserver.sh");

    long startTime = System.currentTimeMillis();
    long maxWaitMillis = 180 * 1000;
    while (true) {
      final long currentTime = System.currentTimeMillis();
      int finalRestartCnt = getPodRestartCount(podName, namespace);
      logger.info("initialRestartCnt " + initialRestartCnt + " finalRestartCnt " + finalRestartCnt);
      if ((finalRestartCnt - initialRestartCnt) == 1) {
        logger.info("WLS liveness probe test is successful.");
        break;
      }
      logger.info("Waiting for liveness probe to restart the pod");
      if ((currentTime - startTime) > maxWaitMillis) {
        throw new RuntimeException(
            "WLS liveness probe is not working within " + maxWaitMillis / 1000 + " seconds");
      }
      Thread.sleep(5 * 1000);
    }
  }

  public static int getPodRestartCount(String podName, String namespace) throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl describe pod ");
    cmd.append(podName)
        .append(" --namespace ")
        .append(namespace)
        .append(" | egrep Restart | awk '{print $3}'");

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAIL: Couldn't find the pod " + podName + " in namespace " + namespace);
    }
    return new Integer(result.stdout().trim()).intValue();
  }

  public static void kubectlcp(
      String srcFileOnHost, String destLocationInPod, String podName, String namespace)
      throws Exception {
    StringBuffer cmdTocp = new StringBuffer("kubectl cp ");
    cmdTocp
        .append(srcFileOnHost)
        .append(" ")
        .append(namespace)
        .append("/")
        .append(podName)
        .append(":")
        .append(destLocationInPod);

    logger.info("Command to copy file " + cmdTocp);
    ExecResult result = ExecCommand.exec(cmdTocp.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: kubectl cp command " + cmdTocp + " failed, returned " + result.stderr());
    }
  }

  public static void copyFileViaCat(
      String srcFileOnHost, String destLocationInPod, String podName, String namespace)
      throws Exception {

    TestUtils.kubectlexec(
        podName, namespace, " -- bash -c 'cat > " + destLocationInPod + "' < " + srcFileOnHost);
  }

  public static ExecResult kubectlexecNoCheck(String podName, String namespace, String scriptPath)
      throws Exception {

    StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
        .append(namespace)
        .append(" exec -it ")
        .append(podName)
        .append(" ")
        .append(scriptPath);

    // ExecResult result = ExecCommand.exec("kubectl get pods -n " + namespace);
    // logger.info("get pods before killing the server " + result.stdout() + "\n " +
    // result.stderr());
    logger.info("Command to call kubectl sh file " + cmdKubectlSh);
    return ExecCommand.exec(cmdKubectlSh.toString());
  }

  /**
   * Copy all App files to the k8s pod.
   *
   * @param appLocationOnHost - App location on the local host
   * @param appLocationInPod - App location on the k8s pod
   * @param podName - the k8s pod name
   * @param namespace - namespace the k8s pod is in
   * @throws Exception exception
   */
  public static void copyAppFilesToPod(
      String appLocationOnHost, String appLocationInPod, String podName, String namespace)
      throws Exception {
    File appFileRoot = new File(appLocationOnHost);
    File[] appFileList = appFileRoot.listFiles();
    String fileLocationInPod = appLocationInPod;

    if (appFileList == null) return;

    for (File file : appFileList) {
      if (file.isDirectory()) {
        // Find dir recursively
        copyAppFilesToPod(file.getAbsolutePath(), appLocationInPod, podName, namespace);
      } else {
        logger.info("Copy file: " + file.getAbsoluteFile().toString() + " to the pod: " + podName);

        String fileParent = file.getParentFile().getName();
        logger.fine("file Parent: " + fileParent);

        if (!appLocationInPod.contains(fileParent)) {
          // Copy files in child dir of appLocationInPod
          fileLocationInPod = appLocationInPod + "/" + fileParent;
        }

        StringBuffer copyFileCmd = new StringBuffer(" -- bash -c 'cat > ");
        copyFileCmd
            .append(fileLocationInPod)
            .append("/")
            .append(file.getName())
            .append("' < ")
            .append(file.getAbsoluteFile().toString());

        kubectlexecNoCheck(podName, namespace, copyFileCmd.toString());
      }
    }
  }

  public static void kubectlexec(String podName, String namespace, String scriptPath)
      throws Exception {

    ExecResult result = kubectlexecNoCheck(podName, namespace, scriptPath);
    if (result.exitValue() != 0) {
      throw new RuntimeException("FAILURE: command failed, returned " + result.stderr());
    }
  }

  public static int makeOperatorPostRestCall(Operator operator, String url, String jsonObjStr)
      throws Exception {
    return makeOperatorRestCall(operator, url, jsonObjStr);
  }

  public static int makeOperatorGetRestCall(Operator operator, String url) throws Exception {
    return makeOperatorRestCall(operator, url, null);
  }

  private static int makeOperatorRestCall(Operator operator, String url, String jsonObjStr)
      throws Exception {
    // get access token
    String token = getAccessToken(operator);
    logger.info("token =" + token);

    KeyStore myKeyStore = createKeyStore(operator);

    Builder request = createRestRequest(myKeyStore, url, token);

    Response response = null;
    int i = 0;
    while (i < BaseTest.getMaxIterationsPod()) {
      try {
        // Post scaling request to Operator
        if (jsonObjStr != null) {
          response = request.post(Entity.json(jsonObjStr));
        } else {
          response = request.get();
        }
      } catch (Exception ex) {
        logger.info("Got exception, iteration " + i + " " + ex.getMessage());
        i++;
        if (ex.getMessage().contains("java.net.ConnectException: Connection refused")) {
          if (i == (BaseTest.getMaxIterationsPod() - 1)) {
            throw ex;
          }
          logger.info("Sleeping 5 more seconds and try again");
          Thread.sleep(5 * 1000);
          continue;
        } else {
          throw ex;
        }
      }
      break;
    }
    logger.info("response: " + response);

    int returnCode = response.getStatus();
    // Verify
    if (returnCode == 204 || returnCode == 200) {
      logger.info("response code is " + returnCode);
      logger.info("Response is " + response.readEntity(String.class));
    } else {
      throw new RuntimeException("Response " + response.readEntity(String.class));
    }
    response.close();
    // javaClient.close();
    return returnCode;
  }

  public static String getLegacyAccessToken(Operator operator) throws Exception {
    return null;
  }

  public static String getAccessToken(Operator operator) throws Exception {
    StringBuffer secretCmd =
        new StringBuffer(
            "kubectl get serviceaccount " + operator.getOperatorMap().get("serviceAccount"));
    secretCmd
        .append(" -n ")
        .append(operator.getOperatorNamespace())
        .append(" -o jsonpath='{.secrets[0].name}'");

    ExecResult result = ExecCommand.exec(secretCmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILED: command " + secretCmd + " failed to get the secret name for Operator");
    }
    // String secretName = TestUtils.executeCommandStrArray(secretCmd.toString()).trim();
    String secretName = result.stdout().trim();
    StringBuffer etokenCmd = new StringBuffer("kubectl get secret ");
    etokenCmd
        .append(secretName)
        .append(" -n ")
        .append(operator.getOperatorNamespace())
        .append(" -o jsonpath='{.data.token}'");
    result = ExecCommand.exec(etokenCmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILED: command " + etokenCmd + " failed to get secret token for Operator");
    }
    String etoken = result.stdout().trim();
    return ExecCommand.exec("echo " + etoken + " | base64 --decode").stdout().trim();
  }

  public static String getExternalOperatorCertificate(Operator operator) throws Exception {

    File certFile =
        new File(
            operator.getUserProjectsDir()
                + "/weblogic-operators/"
                + operator.getOperatorNamespace()
                + "/operator.cert.pem");

    StringBuffer opCertCmd;
    if (RestCertType.LEGACY == operator.getRestCertType()) {
      opCertCmd = new StringBuffer("kubectl get cm -n ");
      opCertCmd
          .append(operator.getOperatorNamespace())
          .append(" weblogic-operator-cm -o jsonpath='{.data.externalOperatorCert}'");
    } else {
      opCertCmd = new StringBuffer("kubectl get secret -n ");
      opCertCmd
          .append(operator.getOperatorNamespace())
          .append(
              " weblogic-operator-external-rest-identity -o yaml | grep tls.crt | cut -d':' -f 2");
    }
    ExecResult result = ExecCommand.exec(opCertCmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILED: command to get externalOperatorCert " + opCertCmd + " failed.");
    }

    // logger.info("opCertCmd ="+opCertCmd);
    String opCert = result.stdout().trim();
    // logger.info("opCert ="+opCert);

    StringBuffer opCertDecodeCmd = new StringBuffer("echo ");
    opCertDecodeCmd
        .append(opCert)
        .append(" | base64 --decode > ")
        .append(certFile.getAbsolutePath());

    ExecCommand.exec(opCertDecodeCmd.toString()).stdout().trim();
    return certFile.getAbsolutePath();
  }

  public static String getExternalOperatorKey(Operator operator) throws Exception {
    File keyFile =
        new File(
            operator.getUserProjectsDir()
                + "/weblogic-operators/"
                + operator.getOperatorNamespace()
                + "/operator.key.pem");

    StringBuffer opKeyCmd = new StringBuffer("kubectl get secret -n ");
    opKeyCmd
        .append(operator.getOperatorNamespace())
        .append(
            " weblogic-operator-external-rest-identity -o yaml | grep tls.key | cut -d':' -f 2");

    ExecResult result = ExecCommand.exec(opKeyCmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILED: command to get externalOperatorKey " + opKeyCmd + " failed.");
    }
    String opKey = result.stdout().trim();
    // logger.info("opKey ="+opKey);

    StringBuffer opKeyDecodeCmd = new StringBuffer("echo ");
    opKeyDecodeCmd.append(opKey).append(" | base64 --decode > ").append(keyFile.getAbsolutePath());

    ExecCommand.exec(opKeyDecodeCmd.toString()).stdout().trim();
    return keyFile.getAbsolutePath();
  }

  public static String getGitBranchName() throws Exception {
    String cmd = "git branch | grep \\* | cut -d ' ' -f2-";
    ExecResult result = ExecCommand.exec(cmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException("FAILED: command " + cmd + " failed");
    }
    return result.stdout().trim();
  }

  public static Operator createOperator(String opYamlFile, RestCertType restCertType)
      throws Exception {
    // create op
    Operator operator = new Operator(opYamlFile, restCertType);

    logger.info("Check Operator status");
    operator.verifyPodCreated();
    operator.verifyOperatorReady();
    operator.verifyExternalRestService();

    return operator;
  }

  public static Operator createOperator(Map<String, Object> inputMap, RestCertType restCertType)
      throws Exception {
    // create op
    Operator operator = new Operator(inputMap, restCertType);
    operator.callHelmInstall();

    logger.info("Check Operator status");
    operator.verifyPodCreated();
    operator.verifyOperatorReady();
    operator.verifyExternalRestService();

    return operator;
  }

  public static Operator createOperator(String opYamlFile) throws Exception {
    return createOperator(opYamlFile, RestCertType.SELF_SIGNED);
  }

  /**
   * Create operator pod with options for multiple container in it.
   *
   * @param opYamlFile - yaml file to create the Operator
   * @param containerNum - the number of containers in Operator pod
   * @throws Exception exception
   */
  public static Operator createOperator(String opYamlFile, String containerNum) throws Exception {
    // create op
    Operator operator = new Operator(opYamlFile, RestCertType.SELF_SIGNED);

    logger.info("Check Operator status");
    operator.verifyPodCreated();
    operator.verifyOperatorReady(containerNum);
    operator.verifyExternalRestService();

    return operator;
  }

  public static Domain createDomain(String inputYaml) throws Exception {
    logger.info("Creating domain with yaml, waiting for the script to complete execution");
    return new Domain(inputYaml);
  }

  public static Domain createDomain(String inputYaml, boolean createDomainResource)
      throws Exception {
    logger.info("Creating domain with yaml, waiting for the script to complete execution");
    return new Domain(inputYaml, createDomainResource);
  }

  public static Domain createDomain(Map<String, Object> inputDomainMap) throws Exception {
    logger.info("Creating domain with Map, waiting for the script to complete execution");
    return new Domain(inputDomainMap);
  }

  public static Domain createDomain(
      Map<String, Object> inputDomainMap, boolean createDomainResource) throws Exception {
    logger.info("Creating domain with Map, waiting for the script to complete execution");
    return new Domain(inputDomainMap, createDomainResource);
  }

  public static Map<String, Object> loadYaml(String yamlFile) throws Exception {
    // read input domain yaml to test
    Map<String, Object> map = new HashMap<String, Object>();
    Yaml yaml = new Yaml();
    InputStream is = TestUtils.class.getClassLoader().getResourceAsStream(yamlFile);
    map = yaml.load(is);
    is.close();
    return map;
  }

  public static Properties loadProps(String propsFile) throws Exception {
    Properties props = new Properties();
    // check file exists
    File f = new File(TestUtils.class.getClassLoader().getResource(propsFile).getFile());
    if (!f.exists()) {
      throw new IllegalArgumentException("FAILURE: Invalid properties file " + propsFile);
    }

    // load props
    FileInputStream inStream = new FileInputStream(f);
    props.load(inStream);
    inStream.close();

    return props;
  }

  public static void renewK8sClusterLease(String projectRoot, String leaseId) throws Exception {
    if (leaseId != "") {
      logger.info("Renewing lease for leaseId " + leaseId);
      String command = projectRoot + "/src/integration-tests/bash/lease.sh -r " + leaseId;
      ExecResult execResult = ExecCommand.exec(command);
      if (execResult.exitValue() != 0) {
        logger.info(
            "ERROR: Could not renew lease on k8s cluster for LEASE_ID="
                + leaseId
                + "Used "
                + projectRoot
                + "/src/integration-tests/bash/lease.sh -r "
                + leaseId
                + " to try renew the lease. "
                + "Some of the potential reasons for this failure are that another run"
                + "may have obtained the lease, the lease may have been externally "
                + "deleted, or the caller of the test may have forgotten to obtain the "
                + "lease before calling the test (using 'lease.sh -o \"$LEASE_ID\"'). "
                + "To force delete a lease no matter who owns the lease,"
                + "call 'lease.sh -f' or 'kubernetes delete cm acceptance-test-lease'"
                + "(this should only be done when sure there's no current java tests "
                + "that owns the lease).  To view the current lease holder,"
                + "use 'lease.sh -s'.  To disable this lease check, do not set"
                + "the LEASE_ID environment variable.");

        throw new RuntimeException("Could not renew lease on k8s cluster " + execResult.stderr());
      } else {
        logger.info("Renewed lease for leaseId " + leaseId);
      }
    }
  }

  public static void releaseLease(String projectRoot, String leaseId) throws Exception {
    String cmd = projectRoot + "/src/integration-tests/bash/lease.sh -d " + leaseId;
    ExecResult leaseResult = ExecCommand.exec(cmd);
    if (leaseResult.exitValue() != 0) {
      logger.info("FAILED: command to release lease " + cmd + " failed " + leaseResult.stderr());
    }
    logger.info(
        "Command " + cmd + " returned " + leaseResult.stdout() + "\n" + leaseResult.stderr());
  }

  private static Builder createRestRequest(KeyStore myKeyStore, String url, String token) {
    // Create REST Client obj and verify it's not null
    Client javaClient =
        ClientBuilder.newBuilder()
            .trustStore(myKeyStore)
            .register(JsonProcessingFeature.class)
            .build();

    if (javaClient == null) {
      throw new RuntimeException("Client Obj is null");
    }

    // Create a resource target identified by Operator ext REST API URL
    WebTarget target = javaClient.target(url.toString());
    logger.info("Invoking OP REST API URL: " + target.getUri().toString());

    // Obtain a client request invocation builder
    Builder request = target.request(MediaType.APPLICATION_JSON);
    request
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .header("X-Requested-By", "MyJavaClient")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    return request;
  }

  public static void createDockerRegistrySecret(
      String secretName,
      String dockerServer,
      String dockerUser,
      String dockerPassword,
      String dockerEmail,
      String namespace)
      throws Exception {

    ExecCommand.exec("kubectl delete secret " + secretName + " -n " + namespace);
    String command =
        "kubectl create secret docker-registry "
            + secretName
            + " --docker-server="
            + dockerServer
            + " --docker-username="
            + dockerUser
            + " --docker-password=\""
            + dockerPassword
            + "\" --docker-email="
            + dockerEmail
            + " -n "
            + namespace;

    String commandToLog =
        "kubectl create secret docker-registry "
            + secretName
            + " --docker-server="
            + dockerServer
            + " --docker-username="
            + "********"
            + " --docker-password=\""
            + "********"
            + "\" --docker-email="
            + "********"
            + " -n "
            + namespace;

    logger.info("Running command " + commandToLog);
    ExecResult result = ExecCommand.exec(command);
    if (result.exitValue() != 0) {
      throw new RuntimeException("Couldn't create secret " + result.stderr());
    }
  }

  public static Map<String, Object> createOperatorMap(int number, boolean restEnabled) {
    Map<String, Object> operatorMap = new HashMap<>();
    ArrayList<String> targetDomainsNS = new ArrayList<String>();
    targetDomainsNS.add("test" + number);
    operatorMap.put("releaseName", "op" + number);
    operatorMap.put("domainNamespaces", targetDomainsNS);
    operatorMap.put("serviceAccount", "weblogic-operator" + number);
    operatorMap.put("namespace", "weblogic-operator" + number);
    if (restEnabled) {
      operatorMap.put("externalRestHttpsPort", 31000 + number);
      operatorMap.put("externalRestEnabled", restEnabled);
    }
    return operatorMap;
  }

  public static Map<String, Object> createDomainMap(int number) {
    Map<String, Object> domainMap = new HashMap<>();
    ArrayList<String> targetDomainsNS = new ArrayList<String>();
    targetDomainsNS.add("test" + number);
    domainMap.put("domainUID", "test" + number);
    domainMap.put("namespace", "test" + number);
    domainMap.put("configuredManagedServerCount", 4);
    domainMap.put("initialManagedServerReplicas", 2);
    domainMap.put("exposeAdminT3Channel", true);
    domainMap.put("exposeAdminNodePort", true);
    domainMap.put("adminNodePort", 30700 + number);
    domainMap.put("t3ChannelPort", 30000 + number);
    if ((System.getenv("LB_TYPE") != null && System.getenv("LB_TYPE").equalsIgnoreCase("VOYAGER"))
        || (domainMap.containsKey("loadBalancer")
            && ((String) domainMap.get("loadBalancer")).equalsIgnoreCase("VOYAGER"))) {
      domainMap.put("voyagerWebPort", 30344 + number);
      logger.info("For this domain voyagerWebPort is set to: 30344 + " + number);
    }
    return domainMap;
  }

  public static String callShellScriptByExecToPod(
      String scriptPath, String arguments, String podName, String namespace) throws Exception {

    StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
        .append(namespace)
        .append(" exec -it ")
        .append(podName)
        .append(" -- bash -c 'chmod +x -R /shared && ")
        .append(scriptPath)
        .append(" ")
        .append(arguments)
        .append("'");
    logger.info("Command to call kubectl sh file " + cmdKubectlSh);
    ExecResult result = ExecCommand.exec(cmdKubectlSh.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmdKubectlSh + " failed, returned " + result.stderr());
    }
    return result.stdout().trim();
  }

  /**
   * exec into the pod and call the shell script with given arguments.
   *
   * @param podName pod name
   * @param domainNS namespace
   * @param scriptsLocInPod script location
   * @param shScriptName script name
   * @param args script arguments
   * @throws Exception exception
   */
  public static void callShellScriptByExecToPod(
      String podName, String domainNS, String scriptsLocInPod, String shScriptName, String[] args)
      throws Exception {
    StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
        .append(domainNS)
        .append(" exec -it ")
        .append(podName)
        .append(" -- bash -c 'chmod +x -R ")
        .append(scriptsLocInPod)
        .append("  && ")
        .append(scriptsLocInPod)
        .append("/")
        .append(shScriptName)
        .append(" ")
        .append(String.join(" ", args).toString())
        .append("'");

    logger.info("Command to call kubectl sh file " + cmdKubectlSh);
    TestUtils.exec(cmdKubectlSh.toString());
  }

  public static void createDirUnderDomainPV(String dirPath) throws Exception {
    dirPath = dirPath.replace(BaseTest.getPvRoot(), "/sharedparent/");
    String crdCmd = BaseTest.getProjectRoot()
        + "/src/integration-tests/bash/krun.sh -m " + BaseTest.getPvRoot() + ":/sharedparent -c 'mkdir -m 777 -p "
        + dirPath
        + "'";
    
    ExecResult result = ExecCommand.exec(crdCmd);
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command to create domain scripts directory "
              + crdCmd
              + " failed, returned "
              + result.stdout()
              + result.stderr());
    }
    logger.info("command result " + result.stdout().trim());
  }

  public static void createWldfModule(String adminPodName, String domainNS, int t3ChannelPort)
      throws Exception {

    // copy wldf.py script tp pod
    copyFileViaCat(
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/wldf/wldf.py",
        BaseTest.getAppLocationInPod() + "/wldf.py",
        adminPodName,
        domainNS);

    // copy callpyscript.sh to pod
    copyFileViaCat(
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/callpyscript.sh",
        BaseTest.getAppLocationInPod() + "/callpyscript.sh",
        adminPodName,
        domainNS);

    // arguments to shell script to call py script

    String[] args = {
        BaseTest.getAppLocationInPod() + "/wldf.py",
        BaseTest.getUsername(),
        BaseTest.getPassword(),
        " t3://"
            + adminPodName
            + ":"
            + t3ChannelPort,
        
    };
    
    // call callpyscript.sh in pod to deploy wldf module
    TestUtils.callShellScriptByExecToPod(
        adminPodName, domainNS, BaseTest.getAppLocationInPod(), "callpyscript.sh", args);
  }

  public static void createRbacPoliciesForWldfScaling() throws Exception {
    // create rbac policies
    StringBuffer cmd = new StringBuffer("kubectl apply -f ");
    cmd.append(BaseTest.getProjectRoot())
        .append("/integration-tests/src/test/resources/wldf/wldf-policy.yaml");
    logger.info("Running " + cmd);

    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command "
              + cmd
              + " failed, returned "
              + result.stdout()
              + "\n"
              + result.stderr());
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);
  }

  public static void deleteWeblogicDomainResources(String domainUid) throws Exception {
    StringBuilder cmd =
        new StringBuilder(BaseTest.getProjectRoot())
            .append(
                "/kubernetes/samples/scripts/delete-domain/delete-weblogic-domain-resources.sh ")
            .append("-d ")
            .append(domainUid);
    logger.info("Running " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command "
              + cmd
              + " failed, returned "
              + result.stdout()
              + "\n"
              + result.stderr());
    }
    String outputStr = result.stdout().trim();
    logger.info("Command returned " + outputStr);
  }

  public static void verifyBeforeDeletion(Domain domain) throws Exception {
    final String domainNs = String.class.cast(domain.getDomainMap().get("namespace"));
    final String domainUid = domain.getDomainUid();
    final String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    final String credentialsName =
        String.class.cast(domain.getDomainMap().get("weblogicCredentialsSecretName"));

    logger.info("Before deletion of domain: " + domainUid);

    k8sTestUtils.verifyDomainCrd();
    k8sTestUtils.verifyDomain(domainNs, domainUid, true);
    k8sTestUtils.verifyPods(domainNs, domain1LabelSelector, 4);
    k8sTestUtils.verifyJobs(domain1LabelSelector, 1);
    k8sTestUtils.verifyNoDeployments(domain1LabelSelector);
    k8sTestUtils.verifyNoReplicaSets(domain1LabelSelector);
    k8sTestUtils.verifyServices(domain1LabelSelector, 5);
    k8sTestUtils.verifyPvcs(domain1LabelSelector, 1);
    k8sTestUtils.verifyConfigMaps(domain1LabelSelector, 2);
    k8sTestUtils.verifyNoServiceAccounts(domain1LabelSelector);
    k8sTestUtils.verifyNoRoles(domain1LabelSelector);
    k8sTestUtils.verifyNoRoleBindings(domain1LabelSelector);
    k8sTestUtils.verifySecrets(credentialsName, 1);
    k8sTestUtils.verifyPvs(domain1LabelSelector, 1);
    k8sTestUtils.verifyNoClusterRoles(domain1LabelSelector);
    k8sTestUtils.verifyNoClusterRoleBindings(domain1LabelSelector);
  }

  public static void verifyAfterDeletion(Domain domain) throws Exception {
    final String domainNs = String.class.cast(domain.getDomainMap().get("namespace"));
    final String domainUid = domain.getDomainUid();
    final String domain1LabelSelector = String.format("weblogic.domainUID in (%s)", domainUid);
    final String credentialsName =
        String.class.cast(domain.getDomainMap().get("weblogicCredentialsSecretName"));

    logger.info("After deletion of domain: " + domainUid);
    k8sTestUtils.verifyDomainCrd();
    k8sTestUtils.verifyDomain(domainNs, domainUid, false);
    k8sTestUtils.verifyPods(domainNs, domain1LabelSelector, 0);
    k8sTestUtils.verifyJobs(domain1LabelSelector, 0);
    k8sTestUtils.verifyNoDeployments(domain1LabelSelector);
    k8sTestUtils.verifyNoReplicaSets(domain1LabelSelector);
    k8sTestUtils.verifyServices(domain1LabelSelector, 0);
    k8sTestUtils.verifyPvcs(domain1LabelSelector, 0);
    k8sTestUtils.verifyConfigMaps(domain1LabelSelector, 0);
    k8sTestUtils.verifyNoServiceAccounts(domain1LabelSelector);
    k8sTestUtils.verifyNoRoles(domain1LabelSelector);
    k8sTestUtils.verifyNoRoleBindings(domain1LabelSelector);
    k8sTestUtils.verifySecrets(credentialsName, 0);
    k8sTestUtils.verifyPvs(domain1LabelSelector, 0);
    k8sTestUtils.verifyNoClusterRoles(domain1LabelSelector);
    k8sTestUtils.verifyNoClusterRoleBindings(domain1LabelSelector);
  }

  /**
   * Replaces the string matching the given search pattern with a new string.
   *
   * @param filename - filename in which the string will be replaced
   * @param originalString - the string which needs to be replaced
   * @param newString - the new string to replace
   * @throws Exception - if any error occurs
   */
  public static void replaceStringInFile(String filename, String originalString, String newString)
      throws Exception {
    Path path = Paths.get(filename);

    String content = new String(Files.readAllBytes(path));
    content = content.replaceAll(originalString, newString);
    Files.write(path, content.getBytes());
  }

  private static KeyStore createKeyStore(Operator operator) throws Exception {
    // get operator external certificate from weblogic-operator.yaml
    String opExtCertFile = getExternalOperatorCertificate(operator);
    // logger.info("opExtCertFile =" + opExtCertFile);

    // NOTE: Operator's private key should not be added to a keystore
    // used for the client connection
    // get operator external key from weblogic-operator.yaml
    //    String opExtKeyFile = getExternalOperatorKey(operator);
    // logger.info("opExternalKeyFile =" + opExtKeyFile);

    if (!new File(opExtCertFile).exists()) {
      throw new RuntimeException("File " + opExtCertFile + " doesn't exist");
    }
    //    if (!new File(opExtKeyFile).exists()) {
    //      throw new RuntimeException("File " + opExtKeyFile + " doesn't exist");
    //    }
    logger.info("opExtCertFile " + opExtCertFile);
    // Create a java Keystore obj and verify it's not null
    KeyStore myKeyStore = PemImporter.createKeyStore(new File(opExtCertFile), "temp_password");
    if (myKeyStore == null) {
      throw new RuntimeException("Keystore Obj is null");
    }
    return myKeyStore;
  }


  /**
   * Check command in loop.
   * @param cmd command to run in the loop
   * @param matchStr expected string to match in the output
   * @throws Exception exception if fails to execute
   */
  public static void checkAnyCmdInLoop(String cmd, String matchStr)
      throws Exception {
    checkCmdInLoop(cmd,matchStr, "");
  }

  public static void checkCmdInLoop(String cmd, String matchStr, String k8sObjName)
          throws Exception {
    int i = 0;
    while (i < BaseTest.getMaxIterationsPod()) {
      ExecResult result = ExecCommand.exec(cmd);

      // loop command till condition
      if (result.exitValue() != 0
          || (result.exitValue() == 0 && !result.stdout().contains(matchStr))) {
        logger.info("Output for " + cmd + "\n" + result.stdout() + "\n " + result.stderr());
        // check for last iteration
        if (i == (BaseTest.getMaxIterationsPod() - 1)) {
          throw new RuntimeException(
                  "FAILURE: command " + cmd + " failed to execute or does not match the expected output "
                      + matchStr + " , exiting!");
        }
        logger.info(
                "did not receive the expected output "
                        + matchStr
                        + " from command "
                        + cmd
                        + " Ite ["
                        + i
                        + "/"
                        + BaseTest.getMaxIterationsPod()
                        + "], sleeping "
                        + BaseTest.getWaitTimePod()
                        + " seconds more");


        Thread.sleep(BaseTest.getWaitTimePod() * 1000);
        i++;
      } else {
        logger.info("Found expected output ");
        if (!k8sObjName.equals("")) {
          logger.info("Pod " + k8sObjName + " is Running");
        }
        break;
      }
    }
  }

  private static void checkCmdInLoopForDelete(String cmd, String matchStr, String k8sObjName)
      throws Exception {
    int i = 0;
    while (i < BaseTest.getMaxIterationsPod()) {
      ExecResult result = ExecCommand.exec(cmd.toString());
      if (result.exitValue() != 0) {
        if (result.stderr().contains(matchStr)) {
          logger.info("DEBUG: " + result.stderr());
          break;
        } else {
          throw new RuntimeException("FAILURE: Command " + cmd + " failed " + result.stderr());
        }
      }
      if (result.exitValue() == 0 && !result.stdout().trim().equals("0")) {
        logger.info("Command " + cmd + " returned " + result.stdout());
        // check for last iteration
        if (i == (BaseTest.getMaxIterationsPod() - 1)) {
          throw new RuntimeException(
              "FAILURE: K8s Object " + k8sObjName + " is not deleted, exiting!");
        }
        logger.info(
            "K8s object "
                + k8sObjName
                + " still exists, Ite ["
                + i
                + "/"
                + BaseTest.getMaxIterationsPod()
                + "], sleeping "
                + BaseTest.getWaitTimePod()
                + " seconds more");

        Thread.sleep(BaseTest.getWaitTimePod() * 1000);

        i++;
      } else {
        break;
      }
    }
  }

  /**
   * create yaml file with changed property.
   *
   * @param inputYamlFile input
   * @param generatedYamlFile generated
   * @param oldString old
   * @param newString new
   * @throws Exception exception
   */
  public static void createNewYamlFile(
      String inputYamlFile, String generatedYamlFile, String oldString, String newString)
      throws Exception {
    logger.info("Creating new  " + generatedYamlFile);

    Files.copy(
        new File(inputYamlFile).toPath(),
        Paths.get(generatedYamlFile),
        StandardCopyOption.REPLACE_EXISTING);

    // read each line in input domain file and replace with intended changed property
    BufferedReader reader = new BufferedReader(new FileReader(generatedYamlFile));
    String line = "";
    StringBuffer changedLines = new StringBuffer();
    boolean isLineChanged = false;
    while ((line = reader.readLine()) != null) {
      if (line.contains(oldString)) {
        String changedLine = line.replace(line.substring(line.indexOf(oldString)), newString);
        changedLines.append(changedLine).append("\n");
        isLineChanged = true;
      }

      if (!isLineChanged) {
        changedLines.append(line).append("\n");
      }
      isLineChanged = false;
    }
    reader.close();
    // writing to the file
    Files.write(Paths.get(generatedYamlFile), changedLines.toString().getBytes());
    logger.info("Done - generate the new yaml file ");
  }

  /**
   * copy file from source to target.
   *
   * @param fromFile from
   * @param toFile to
   * @throws Exception exception
   */
  public static void copyFile(String fromFile, String toFile) throws Exception {
    logger.info("Copying file from  " + fromFile + " to " + toFile);
    Files.copy(new File(fromFile).toPath(), Paths.get(toFile), StandardCopyOption.REPLACE_EXISTING);
  }

  /**
   * retrieve IP address info for cluster service.
   *
   * @param domainUid - name of domain.
   * @param clusterName - name Web Logic cluster
   * @param domainNS - domain namespace
   * @throws Exception - exception will be thrown if kubectl command will fail
   */
  public static String retrieveClusterIP(String domainUid, String clusterName, String domainNS)
      throws Exception {
    // kubectl get service domainonpvwlst-cluster-cluster-1 | grep ClusterIP | awk '{print $3}'
    StringBuffer cmd = new StringBuffer("kubectl get service ");
    cmd.append(domainUid);
    cmd.append("-cluster-");
    cmd.append(clusterName);
    cmd.append(" -n ").append(domainNS);
    cmd.append(" | grep ClusterIP | awk '{print $3}' ");
    logger.info(
        " Get ClusterIP for "
            + clusterName
            + " in namespace "
            + domainNS
            + " with command: '"
            + cmd
            + "'");

    ExecResult result = ExecCommand.exec(cmd.toString());
    String stdout = result.stdout();
    logger.info(" ClusterIP for cluster: " + clusterName + " found: ");
    logger.info(stdout);
    return stdout;
  }

  /**
   * Create dir to save Web Service App files. Copy the shell script file and all App files over to
   * the admin pod Run the shell script to build WARs files and deploy the Web Service App and it's
   * client Servlet App in the admin pod
   *
   * @param domain - Domain where to build and deploy app
   * @param appName - WebService App name to be deployed
   * @param scriptName - a shell script to build and deploy the App in the admin pod
   * @param username - weblogic user name
   * @param password - weblogc password
   * @param args - by default it use TestWsApp name for webservices impl files, or add arg for
   *     different name
   * @throws Exception - exception reported as a failure to build or deploy ws
   */
  public static void buildDeployWebServiceAppInPod(
      Domain domain,
      String appName,
      String scriptName,
      String username,
      String password,
      String... args)
      throws Exception {
    String adminServerPod = domain.getDomainUid() + "-" + domain.getAdminServerName();
    final String appLocationOnHost = BaseTest.getAppLocationOnHost() + "/" + appName;
    final String appLocationInPod = BaseTest.getAppLocationInPod() + "/" + appName;
    final String scriptPathOnHost = BaseTest.getAppLocationOnHost() + "/" + scriptName;
    final String scriptPathInPod = BaseTest.getAppLocationInPod() + "/" + scriptName;

    // Default values to build archive file
    final String initInfoDirName = "WEB-INF";
    String archiveExt = "war";
    String infoDirName = initInfoDirName;
    String domainNS = domain.getDomainNs();
    int managedServerPort = ((Integer) (domain.getDomainMap()).get("managedServerPort")).intValue();
    String wsServiceName = (args.length == 0) ? BaseTest.TESTWSSERVICE : args[0];
    final String clusterUrl =
        retrieveClusterIP(domain.getDomainUid(), domain.getClusterName(), domainNS)
            + ":"
            + managedServerPort;
    logger.info(
        "Build and deploy WebService App: "
            + appName
            + "."
            + archiveExt
            + " in the admin pod with web service name "
            + wsServiceName);

    // Create app dir in the admin pod
    StringBuffer mkdirCmd = new StringBuffer(" -- bash -c 'mkdir -p ");
    mkdirCmd.append(appLocationInPod + "'");

    // Create app dir in the admin pod
    kubectlexec(adminServerPod, domainNS, mkdirCmd.toString());

    // Create WEB-INF in the app dir
    mkdirCmd = new StringBuffer(" -- bash -c 'mkdir -p ");
    mkdirCmd.append(appLocationInPod + "/WEB-INF'");
    kubectlexec(adminServerPod, domainNS, mkdirCmd.toString());

    // Copy shell script to the admin pod
    copyFileViaCat(scriptPathOnHost, scriptPathInPod, adminServerPod, domainNS);

    // Copy all App files to the admin pod
    copyAppFilesToPod(appLocationOnHost, appLocationInPod, adminServerPod, domainNS);

    // Copy all App files to the admin pod
    copyAppFilesToPod(
        appLocationOnHost + "/WEB-INF", appLocationInPod + "/WEB-INF", adminServerPod, domainNS);

    logger.info("Creating WebService and WebService Servlet Client Applications");

    // Run the script to build WAR, EAR or JAR file and deploy the App in the admin pod
    domain.callShellScriptToBuildDeployAppInPod(
        appName, scriptName, username, password, clusterUrl, wsServiceName);
  }

  public static ExecResult loginAndPushImageToOcir(String image) throws Exception {
    String dockerLoginAndPushCmd =
        "docker login "
            + System.getenv("REPO_REGISTRY")
            + " -u "
            + System.getenv("REPO_USERNAME")
            + " -p \""
            + System.getenv("REPO_PASSWORD")
            + "\" && docker push "
            + image;
    ExecResult result = TestUtils.exec(dockerLoginAndPushCmd);
    logger.info(
        "cmd "
            + dockerLoginAndPushCmd
            + "\n result "
            + result.stdout()
            + "\n err "
            + result.stderr());
    return result;
  }

  public static ExecResult kubectlpatch(String domainUid, String domainNS, String patchStr)
      throws Exception {
    String cmd =
        "kubectl patch domain "
            + domainUid
            + " -n "
            + domainNS
            + " -p "
            + patchStr
            + " --type merge";
    return exec(cmd, true);
  }
}

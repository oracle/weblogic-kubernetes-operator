// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.security.KeyStore;
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
import org.glassfish.jersey.jsonp.JsonProcessingFeature;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

public class TestUtils {
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private static K8sTestUtils k8sTestUtils = new K8sTestUtils();

  /**
   * @param cmd - kubectl get pod <podname> -n namespace
   * @throws Exception
   */
  public static void checkPodReady(String podName, String domainNS) throws Exception {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    // check for admin pod
    checkCmdInLoop(cmd.toString(), "1/1", podName);
  }

  /** @param cmd - kubectl get pod <podname> -n namespace */
  public static void checkPodCreated(String podName, String domainNS) throws Exception {

    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    // check for admin pod
    checkCmdInLoop(cmd.toString(), "Running", podName);
  }

  /**
   * @param cmd - kubectl get service <servicename> -n namespace
   * @throws Exception
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
   * @param map - map with attributes
   * @param generatedInputYamlFile - output file with replaced values
   * @throws Exception
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

  public static void deletePVC(String pvcName, String namespace) throws Exception {
    StringBuffer cmd = new StringBuffer("kubectl delete pvc ");
    cmd.append(pvcName).append(" -n ").append(namespace);
    logger.info("Deleting PVC " + cmd);
    ExecResult result = ExecCommand.exec(cmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: delete PVC failed with " + result.stderr() + " \n " + result.stdout());
    }
  }

  public static boolean checkPVReleased(String pvBaseName, String namespace) throws Exception {
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
   * First, kill the mgd server process in the container three times to cause the node manager to
   * mark the server 'failed not restartable'. This in turn is detected by the liveness probe, which
   * initiates a pod restart.
   *
   * @param domainUid
   * @param serverName
   * @param namespace
   * @throws Exception
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
    kubectlcp(filePath, "/shared/killserver.sh", podName, namespace);

    // kill server process 3 times
    for (int i = 0; i < 3; i++) {
      ExecResult result = kubectlexecNoCheck(podName, namespace, "/shared/killserver.sh");
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
      long currentTime = System.currentTimeMillis();
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

  public static ExecResult kubectlexecNoCheck(String podName, String namespace, String scriptPath)
      throws Exception {

    StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
        .append(namespace)
        .append(" exec -it ")
        .append(podName)
        .append(" ")
        .append(scriptPath);

    ExecResult result = ExecCommand.exec("kubectl get pods -n " + namespace);
    logger.info("get pods before killing the server " + result.stdout() + "\n " + result.stderr());
    logger.info("Command to call kubectl sh file " + cmdKubectlSh);
    return ExecCommand.exec(cmdKubectlSh.toString());
  }

  public static void kubectlexec(String podName, String namespace, String scriptPath)
      throws Exception {

    ExecResult result = kubectlexecNoCheck(podName, namespace, scriptPath);
    if (result.exitValue() != 0) {
      throw new RuntimeException("FAILURE: command failed, returned " + result.stderr());
    }
  }

  public static int makeOperatorPostRestCall(
      String operatorNS, String url, String jsonObjStr, String userProjectsDir) throws Exception {
    return makeOperatorRestCall(operatorNS, url, jsonObjStr, userProjectsDir);
  }

  public static int makeOperatorGetRestCall(String operatorNS, String url, String userProjectsDir)
      throws Exception {
    return makeOperatorRestCall(operatorNS, url, null, userProjectsDir);
  }

  private static int makeOperatorRestCall(
      String operatorNS, String url, String jsonObjStr, String userProjectsDir) throws Exception {
    // get access token
    String token = getAccessToken(operatorNS);
    logger.info("token =" + token);

    KeyStore myKeyStore = createKeyStore(operatorNS, userProjectsDir);

    Builder request = createRESTRequest(myKeyStore, url, token);

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

  public static String getAccessToken(String operatorNS) throws Exception {
    StringBuffer secretCmd = new StringBuffer("kubectl get serviceaccount weblogic-operator ");
    secretCmd.append(" -n ").append(operatorNS).append(" -o jsonpath='{.secrets[0].name}'");

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
        .append(operatorNS)
        .append(" -o jsonpath='{.data.token}'");
    result = ExecCommand.exec(etokenCmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILED: command " + etokenCmd + " failed to get secret token for Operator");
    }
    String etoken = result.stdout().trim();
    return ExecCommand.exec("echo " + etoken + " | base64 --decode").stdout().trim();
  }

  public static String getExternalOperatorCertificate(String operatorNS, String userProjectsDir)
      throws Exception {

    File certFile =
        new File(userProjectsDir + "/weblogic-operators/" + operatorNS + "/operator.cert.pem");

    StringBuffer opCertCmd = new StringBuffer("kubectl get secret -n ");
    opCertCmd
        .append(operatorNS)
        .append(
            " weblogic-operator-external-rest-identity -o yaml | grep tls.crt | cut -d':' -f 2");

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

  public static String getExternalOperatorKey(String operatorNS, String userProjectsDir)
      throws Exception {
    File keyFile =
        new File(userProjectsDir + "/weblogic-operators/" + operatorNS + "/operator.key.pem");

    StringBuffer opKeyCmd = new StringBuffer("kubectl get secret -n ");
    opKeyCmd
        .append(operatorNS)
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

  public static Operator createOperator(String opYamlFile, boolean useLegacyRESTIdentity)
      throws Exception {
    // create op
    Operator operator = new Operator(opYamlFile, useLegacyRESTIdentity);

    logger.info("Check Operator status");
    operator.verifyPodCreated();
    operator.verifyOperatorReady();
    operator.verifyExternalRESTService();

    return operator;
  }

  public static Operator createOperator(String opYamlFile) throws Exception {
    return createOperator(opYamlFile, false);
  }

  public static Domain createDomain(String inputYaml) throws Exception {
    logger.info("Creating domain with yaml, waiting for the script to complete execution");
    return new Domain(inputYaml);
    /* domain.verifyDomainCreated();
    return domain; */
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

  private static Builder createRESTRequest(KeyStore myKeyStore, String url, String token) {
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

  public static String callShellScriptByExecToPod(
      String scriptPath, String arguments, String podName, String namespace) throws Exception {

    StringBuffer cmdKubectlSh = new StringBuffer("kubectl -n ");
    cmdKubectlSh
        .append(namespace)
        .append(" exec -it ")
        .append(podName)
        .append(" ")
        .append(scriptPath)
        .append(" ")
        .append(arguments);
    logger.info("Command to call kubectl sh file " + cmdKubectlSh);
    ExecResult result = ExecCommand.exec(cmdKubectlSh.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILURE: command " + cmdKubectlSh + " failed, returned " + result.stderr());
    }
    return result.stdout().trim();
  }

  public static void createDirUnderDomainPV(String dirPath) throws Exception {

    String crdCmd =
        BaseTest.getProjectRoot()
            + "/src/integration-tests/bash/job.sh \"mkdir -p "
            + dirPath
            + "\"";
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

  public static void createWLDFModule(String adminPodName, String domainNS, int t3ChannelPort)
      throws Exception {

    // copy wldf.py script tp pod
    TestUtils.kubectlcp(
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/wldf/wldf.py",
        "/shared/wldf.py",
        adminPodName,
        domainNS);

    // copy callpyscript.sh to pod
    TestUtils.kubectlcp(
        BaseTest.getProjectRoot() + "/integration-tests/src/test/resources/callpyscript.sh",
        "/shared/callpyscript.sh",
        adminPodName,
        domainNS);

    // arguments to shell script to call py script
    String arguments =
        "/shared/wldf.py "
            + BaseTest.getUsername()
            + " "
            + BaseTest.getPassword()
            + " t3://"
            + adminPodName
            + ":"
            + t3ChannelPort;

    // call callpyscript.sh in pod to deploy wldf module
    TestUtils.callShellScriptByExecToPod(
        "/shared/callpyscript.sh", arguments, adminPodName, domainNS);
  }

  public static void createRBACPoliciesForWLDFScaling() throws Exception {
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

  private static KeyStore createKeyStore(String operatorNS, String userProjectsDir)
      throws Exception {
    // get operator external certificate from weblogic-operator.yaml
    String opExtCertFile = getExternalOperatorCertificate(operatorNS, userProjectsDir);
    // logger.info("opExtCertFile =" + opExtCertFile);

    // get operator external key from weblogic-operator.yaml
    String opExtKeyFile = getExternalOperatorKey(operatorNS, userProjectsDir);
    // logger.info("opExternalKeyFile =" + opExtKeyFile);

    if (!new File(opExtCertFile).exists()) {
      throw new RuntimeException("File " + opExtCertFile + " doesn't exist");
    }
    if (!new File(opExtKeyFile).exists()) {
      throw new RuntimeException("File " + opExtKeyFile + " doesn't exist");
    }
    logger.info("opExtCertFile " + opExtCertFile);
    // Create a java Keystore obj and verify it's not null
    KeyStore myKeyStore =
        PEMImporter.createKeyStore(
            new File(opExtKeyFile), new File(opExtCertFile), "temp_password");
    if (myKeyStore == null) {
      throw new RuntimeException("Keystore Obj is null");
    }
    return myKeyStore;
  }

  private static void checkCmdInLoop(String cmd, String matchStr, String k8sObjName)
      throws Exception {
    int i = 0;
    while (i < BaseTest.getMaxIterationsPod()) {
      ExecResult result = ExecCommand.exec(cmd);

      // pod might not have been created or if created loop till condition
      if (result.exitValue() != 0
          || (result.exitValue() == 0 && !result.stdout().contains(matchStr))) {
        logger.info("Output for " + cmd + "\n" + result.stdout() + "\n " + result.stderr());
        // check for last iteration
        if (i == (BaseTest.getMaxIterationsPod() - 1)) {
          throw new RuntimeException(
              "FAILURE: pod " + k8sObjName + " is not running/ready, exiting!");
        }
        logger.info(
            "Pod "
                + k8sObjName
                + " is not Running Ite ["
                + i
                + "/"
                + BaseTest.getMaxIterationsPod()
                + "], sleeping "
                + BaseTest.getWaitTimePod()
                + " seconds more");

        Thread.sleep(BaseTest.getWaitTimePod() * 1000);
        i++;
      } else {
        logger.info("Pod " + k8sObjName + " is Running");
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
        throw new RuntimeException("FAILURE: Command " + cmd + " failed " + result.stderr());
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
}

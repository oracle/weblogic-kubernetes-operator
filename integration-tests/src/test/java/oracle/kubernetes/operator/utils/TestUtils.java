// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.util.Enumeration;
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

public class TestUtils {
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  private static int maxIterationsPod = BaseTest.getMaxIterationsPod(); // 50 * 5 = 250 seconds
  private static int waitTimePod = BaseTest.getWaitTimePod();

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
    while (i < maxIterationsPod) {
      ExecResult result = ExecCommand.exec(cmd.toString());

      // service might not have been created
      if (result.exitValue() != 0
          || (result.exitValue() == 0 && !result.stdout().contains(serviceName))) {
        logger.info("Output for " + cmd + "\n" + result.stdout() + "\n " + result.stderr());

        // check for last iteration
        if (i == (maxIterationsPod - 1)) {
          throw new RuntimeException("FAILURE: service is not created, exiting!");
        }
        logger.info(
            "Service is not created Ite ["
                + i
                + "/"
                + maxIterationsPod
                + "], sleeping "
                + waitTimePod
                + " seconds more");
        Thread.sleep(waitTimePod * 1000);
        i++;
      } else {
        logger.info("Service " + serviceName + " is Created");
        break;
      }
    }
  }

  /**
   * @param propsFile - input props file
   * @param inputFileTemplate - operator/domain inputs template file
   * @param generatedInputYamlFile - output file with replaced values
   * @throws Exception
   */
  public static void createInputFile(
      Properties props, String inputFileTemplate, String generatedInputYamlFile) throws Exception {
    logger.info("Creating input yaml file at " + generatedInputYamlFile);

    // copy input template file and modify it
    Files.copy(
        new File(inputFileTemplate).toPath(),
        Paths.get(generatedInputYamlFile),
        StandardCopyOption.REPLACE_EXISTING);

    // read each line in input template file and replace only customized props
    BufferedReader reader = new BufferedReader(new FileReader(generatedInputYamlFile));
    String line = "";
    StringBuffer changedLines = new StringBuffer();
    boolean isLineChanged = false;
    while ((line = reader.readLine()) != null) {
      Enumeration enuKeys = props.keys();
      while (enuKeys.hasMoreElements()) {
        String key = (String) enuKeys.nextElement();
        // if a line starts with the props key then replace
        // the line with key:value in the file
        if (line.startsWith(key + ":") || line.startsWith("#" + key + ":")) {
          changedLines.append(key).append(":").append(props.getProperty(key)).append("\n");
          isLineChanged = true;
          break;
        }
      }
      if (!isLineChanged) {
        changedLines.append(line).append("\n");
      }
      isLineChanged = false;
    }
    reader.close();
    // writing to the file
    Files.write(Paths.get(generatedInputYamlFile), changedLines.toString().getBytes());
  }

  public static String getHostName() throws Exception {
    if (System.getenv("K8S_NODEPORT_HOST") != null) {
      return System.getenv("K8S_NODEPORT_HOST");
    } else {
      ExecResult result = ExecCommand.exec("hostname | awk -F. '{print $1}'");
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
        .append(" -o jsonpath='{.spec.clusterStartup[?(@.clusterName == \"")
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
    // Post scaling request to Operator
    if (jsonObjStr != null) {
      response = request.post(Entity.json(jsonObjStr));
    } else {
      response = request.get();
    }
    logger.info("response: " + response.toString());

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
        new File(
            TestUtils.class.getClassLoader().getResource(".").getFile() + "/../operator.cert.pem");

    StringBuffer opCertCmd = new StringBuffer("grep externalOperatorCert ");
    opCertCmd
        .append(userProjectsDir)
        .append("/weblogic-operators/")
        .append(operatorNS)
        .append("/weblogic-operator.yaml | awk '{ print $2 }'");

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

    String decodedOpCert = ExecCommand.exec(opCertDecodeCmd.toString()).stdout().trim();
    return certFile.getAbsolutePath();
  }

  public static String getExternalOperatorKey(String operatorNS, String userProjectsDir)
      throws Exception {
    File keyFile =
        new File(
            TestUtils.class.getClassLoader().getResource(".").getFile() + "/../operator.key.pem");

    StringBuffer opKeyCmd = new StringBuffer("grep externalOperatorKey ");
    opKeyCmd
        .append(userProjectsDir)
        .append("/weblogic-operators/")
        .append(operatorNS)
        .append("/weblogic-operator.yaml | awk '{ print $2 }'");

    ExecResult result = ExecCommand.exec(opKeyCmd.toString());
    if (result.exitValue() != 0) {
      throw new RuntimeException(
          "FAILED: command to get externalOperatorKey " + opKeyCmd + " failed.");
    }
    String opKey = result.stdout().trim();
    // logger.info("opKey ="+opKey);

    StringBuffer opKeyDecodeCmd = new StringBuffer("echo ");
    opKeyDecodeCmd.append(opKey).append(" | base64 --decode > ").append(keyFile.getAbsolutePath());

    String decodedOpKey = ExecCommand.exec(opKeyDecodeCmd.toString()).stdout().trim();
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

  public static Operator createOperator(String opPropsFile) throws Exception {
    // load operator props defined
    Properties operatorProps = loadProps(opPropsFile);
    // create op
    Operator operator = new Operator(operatorProps);

    logger.info("Check Operator status");
    operator.verifyPodCreated();
    operator.verifyOperatorReady();
    operator.verifyExternalRESTService();

    return operator;
  }

  public static Domain createDomain(String domainPropsFile) throws Exception {
    Properties domainProps = loadProps(domainPropsFile);
    return createDomain(domainProps);
  }

  public static Domain createDomain(Properties domainProps) throws Exception {
    logger.info("Creating domain, waiting for the script to complete execution");
    Domain domain = new Domain(domainProps);
    domain.verifyDomainCreated();
    return domain;
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
                + "deleted, or the caller of run.sh may have forgotten to obtain the"
                + "lease before calling run.sh (using 'lease.sh -o \"$LEASE_ID\"'). "
                + "To force delete a lease no matter who owns the lease,"
                + "call 'lease.sh -f' or 'kubernetes delete cm acceptance-test-lease'"
                + "(this should only be done when sure there's no current run.sh "
                + "that owns the lease).  To view the current lease holder,"
                + "use 'lease.sh -s'.  To disable this lease check, do not set"
                + "the LEASE_ID environment variable.");

        throw new RuntimeException("Could not renew lease on k8s cluster");
      } else {
        logger.info("Renewed lease for leaseId " + leaseId);
      }
    }
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
    logger.info("Running command " + command);
    ExecResult result = ExecCommand.exec(command);
    if (result.exitValue() != 0) {
      throw new RuntimeException("Couldn't create secret " + result.stderr());
    }
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
    while (i < maxIterationsPod) {
      ExecResult result = ExecCommand.exec(cmd);

      // pod might not have been created or if created loop till condition
      if (result.exitValue() != 0
          || (result.exitValue() == 0 && !result.stdout().contains(matchStr))) {
        logger.info("Output for " + cmd + "\n" + result.stdout() + "\n " + result.stderr());
        // check for last iteration
        if (i == (maxIterationsPod - 1)) {
          throw new RuntimeException(
              "FAILURE: pod " + k8sObjName + " is not running/ready, exiting!");
        }
        logger.info(
            "Pod "
                + k8sObjName
                + " is not Running Ite ["
                + i
                + "/"
                + maxIterationsPod
                + "], sleeping "
                + waitTimePod
                + " seconds more");

        Thread.sleep(waitTimePod * 1000);
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
    while (i < maxIterationsPod) {
      ExecResult result = ExecCommand.exec(cmd.toString());
      if (result.exitValue() != 0) {
        throw new RuntimeException("FAILURE: Command " + cmd + " failed " + result.stderr());
      }
      if (result.exitValue() == 0 && !result.stdout().trim().equals("0")) {
        logger.info("Command " + cmd + " returned " + result.stdout());
        // check for last iteration
        if (i == (maxIterationsPod - 1)) {
          throw new RuntimeException(
              "FAILURE: K8s Object " + k8sObjName + " is not deleted, exiting!");
        }
        logger.info(
            "K8s object "
                + k8sObjName
                + " still exists, Ite ["
                + i
                + "/"
                + maxIterationsPod
                + "], sleeping "
                + waitTimePod
                + " seconds more");

        Thread.sleep(waitTimePod * 1000);

        i++;
      } else {
        break;
      }
    }
  }
}

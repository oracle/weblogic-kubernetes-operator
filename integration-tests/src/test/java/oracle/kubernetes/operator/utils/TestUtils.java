// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
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

  public static String executeCommand(String command) {
    StringBuffer output = new StringBuffer();
    Process p;
    try {
      p = Runtime.getRuntime().exec(command);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

      // in some cases u may want to read process error stream as well
      String line = "";
      while ((line = reader.readLine()) != null) {
        output.append(line + "\n");
      }

      while ((line = errReader.readLine()) != null) {
        output.append(line + "\n");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return output.toString();
  }

  public static String executeCommandStrArray(String command) {
    StringBuffer output = new StringBuffer();
    Process p;
    try {
      p = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", command});
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

      // in some cases u may want to read process error stream as well
      String line = "";
      while ((line = reader.readLine()) != null) {
        output.append(line + "\n");
      }

      while ((line = errReader.readLine()) != null) {
        output.append(line + "\n");
      }

    } catch (Exception e) {
      e.printStackTrace();
    }
    return output.toString();
  }

  public static boolean executeCommand(String command, String resultString) {
    String output = executeCommand(command);
    if (output.contains(resultString)) return true;
    else return false;
  }
  /** @param cmd - kubectl get pod <podname> -n namespace */
  public static void checkPodReady(String podName, String domainNS) {

    int i = 0;
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    while (i < maxIterationsPod) {
      String outputStr = TestUtils.executeCommand(cmd.toString());
      if (!outputStr.contains("1/1")) {
        // check for last iteration
        if (i == (maxIterationsPod - 1)) {
          throw new RuntimeException(
              "FAILURE: pod " + podName + " is not running and ready, exiting!");
        }
        logger.info(
            "Pod "
                + podName
                + " is not Ready Ite ["
                + i
                + "/"
                + maxIterationsPod
                + "], sleeping "
                + waitTimePod
                + " seconds more");
        try {
          Thread.sleep(waitTimePod * 1000);
        } catch (InterruptedException ignore) {
        }
        i++;
      } else {
        logger.info("Pod " + podName + " is Ready");
        break;
      }
    }
  }

  /** @param cmd - kubectl get pod <podname> -n namespace */
  public static void checkPodCreated(String podName, String domainNS) {
    int i = 0;
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get pod ").append(podName).append(" -n ").append(domainNS);

    // check for admin pod
    while (i < maxIterationsPod) {
      String outputStr = TestUtils.executeCommand(cmd.toString());
      logger.info("Output for " + cmd + "\n" + outputStr);
      if (!outputStr.contains("Running")) {
        // check for last iteration
        if (i == (maxIterationsPod - 1)) {
          throw new RuntimeException("FAILURE: pod " + podName + " is not running, exiting!");
        }
        logger.info(
            "Pod "
                + podName
                + " is not Running Ite ["
                + i
                + "/"
                + maxIterationsPod
                + "], sleeping "
                + waitTimePod
                + " seconds more");
        try {
          Thread.sleep(waitTimePod * 1000);
        } catch (InterruptedException ignore) {
        }

        i++;
      } else {
        logger.info("Pod " + podName + " is Running");
        break;
      }
    }
  }

  /** @param cmd - kubectl get service <servicename> -n namespace */
  public static void checkServiceCreated(String serviceName, String domainNS) {
    int i = 0;
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get service ").append(serviceName).append(" -n ").append(domainNS);

    // check for service
    while (i < maxIterationsPod) {
      String outputStr = TestUtils.executeCommand(cmd.toString());
      logger.fine("Output for " + cmd + "\n" + outputStr);
      if (outputStr.equals("")) {
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
        try {
          Thread.sleep(waitTimePod * 1000);
        } catch (InterruptedException ignore) {
        }

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

  public static String getHostName() {
    return executeCommandStrArray("hostname | awk -F. '{print $1}'").trim();
  }

  public static int getClusterReplicas(String domainUid, String clusterName, String domainNS) {
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get domain ")
        .append(domainUid)
        .append(" -n ")
        .append(domainNS)
        .append(" -o jsonpath='{.spec.clusterStartup[?(@.clusterName == \"")
        .append(clusterName)
        .append("\")].replicas }'");
    logger.fine("getClusterReplicas cmd =" + cmd);
    String output = TestUtils.executeCommandStrArray(cmd.toString());
    int replicas = 0;
    if (output != "") {
      try {
        replicas = new Integer(output.trim()).intValue();
      } catch (NumberFormatException nfe) {
        throw new RuntimeException(
            "FAILURE: Kubectl command " + cmd + " returned non-integer value " + replicas);
      }
    }
    return replicas;
  }

  public static void checkPodDeleted(String podName, String domainNS) {
    int i = 0;
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl -n ")
        .append(domainNS)
        .append(" get pod ")
        .append(podName)
        .append(" | grep \"^")
        .append(podName)
        .append(" \" | wc -l");

    // check for admin pod
    while (i < maxIterationsPod) {
      String outputStr = TestUtils.executeCommandStrArray(cmd.toString());
      // logger.info("Output for "+cmd + "\n"+outputStr);
      if (!outputStr.trim().contains("\"" + podName + "\" not found")) {
        // check for last iteration
        if (i == (maxIterationsPod - 1)) {
          throw new RuntimeException("FAILURE: Pod " + podName + " is not deleted, exiting!");
        }
        logger.info(
            "Pod "
                + podName
                + " still exists, Ite ["
                + i
                + "/"
                + maxIterationsPod
                + "], sleeping "
                + waitTimePod
                + " seconds more");
        try {
          Thread.sleep(waitTimePod * 1000);
        } catch (InterruptedException ignore) {
        }

        i++;
      } else {
        break;
      }
    }
  }

  public static void checkDomainDeleted(String domainUid, String domainNS) {
    int i = 0;
    StringBuffer cmd = new StringBuffer();
    cmd.append("kubectl get domain ")
        .append(domainUid)
        .append(" -n ")
        .append(domainNS)
        .append(" | egrep ")
        .append(domainUid)
        .append(" | wc -l");

    while (i < maxIterationsPod) {
      String outputStr = TestUtils.executeCommandStrArray(cmd.toString());
      // logger.info("Output for "+cmd + "\n"+outputStr);
      if (!outputStr.trim().contains("\"" + domainUid + "\" not found")) {
        // check for last iteration
        if (i == (maxIterationsPod - 1)) {
          throw new RuntimeException("FAILURE: domain still exists, exiting!");
        }
        logger.info(
            "Domain still exists, Ite ["
                + i
                + "/"
                + maxIterationsPod
                + "], sleeping "
                + waitTimePod
                + " seconds more");
        try {
          Thread.sleep(waitTimePod * 1000);
        } catch (InterruptedException ignore) {
        }

        i++;
      } else {
        break;
      }
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

    // get operator external certificate from weblogic-operator.yaml
    String opExtCertFile = getExternalOperatorCertificate(operatorNS, userProjectsDir);
    // logger.info("opExternalCertificateFile ="+opExtCertFile);

    // get operator external key from weblogic-operator.yaml
    String opExtKeyFile = getExternalOperatorKey(operatorNS, userProjectsDir);
    // logger.info("opExternalKeyFile ="+opExtKeyFile);

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
    javaClient.close();

    return returnCode;
  }

  public static String getAccessToken(String operatorNS) {
    StringBuffer secretCmd = new StringBuffer("kubectl get serviceaccount weblogic-operator ");
    secretCmd.append(" -n ").append(operatorNS).append(" -o jsonpath='{.secrets[0].name}'");

    String secretName = TestUtils.executeCommandStrArray(secretCmd.toString()).trim();
    String token = "";
    if (!secretName.equals("")) {
      StringBuffer etokenCmd = new StringBuffer("kubectl get secret ");
      etokenCmd
          .append(secretName)
          .append(" -n ")
          .append(operatorNS)
          .append(" -o jsonpath='{.data.token}'");
      String etoken = TestUtils.executeCommandStrArray(etokenCmd.toString()).trim();

      if (!etoken.equals("")) {
        token = TestUtils.executeCommandStrArray("echo " + etoken + " | base64 --decode").trim();
        // logger.info("Token is "+token);
        return token;
      } else {
        throw new RuntimeException(
            "FAILURE: Invalid secret token for Operator, " + "secret token can't be empty string");
      }

    } else {
      throw new RuntimeException(
          "FAILURE: Invalid secret name for Operator, " + "secret name can't be empty string");
    }
  }

  public static String getExternalOperatorCertificate(String operatorNS, String userProjectsDir) {

    File certFile =
        new File(
            TestUtils.class.getClassLoader().getResource(".").getFile() + "/../operator.cert.pem");

    StringBuffer opCertCmd = new StringBuffer("grep externalOperatorCert ");
    opCertCmd
        .append(userProjectsDir)
        .append("/weblogic-operators/")
        .append(operatorNS)
        .append("/weblogic-operator.yaml | awk '{ print $2 }'");

    // logger.info("opCertCmd ="+opCertCmd);
    String opCert = TestUtils.executeCommandStrArray(opCertCmd.toString()).trim();
    // logger.info("opCert ="+opCert);

    if (opCert.trim().equals("")) {
      throw new RuntimeException("externalOperatorCert is not set");
    }

    StringBuffer opCertDecodeCmd = new StringBuffer("echo ");
    opCertDecodeCmd
        .append(opCert)
        .append(" | base64 --decode > ")
        .append(certFile.getAbsolutePath());

    String decodedOpCert = TestUtils.executeCommandStrArray(opCertDecodeCmd.toString());
    return certFile.getAbsolutePath();
  }

  public static String getExternalOperatorKey(String operatorNS, String userProjectsDir) {
    File keyFile =
        new File(
            TestUtils.class.getClassLoader().getResource(".").getFile() + "/../operator.key.pem");

    StringBuffer opKeyCmd = new StringBuffer("grep externalOperatorKey ");
    opKeyCmd
        .append(userProjectsDir)
        .append("/weblogic-operators/")
        .append(operatorNS)
        .append("/weblogic-operator.yaml | awk '{ print $2 }'");

    String opKey = TestUtils.executeCommandStrArray(opKeyCmd.toString()).trim();
    // logger.info("opKey ="+opKey);

    if (opKey.trim().equals("")) {
      throw new RuntimeException("externalOperatorKey is not set");
    }

    StringBuffer opKeyDecodeCmd = new StringBuffer("echo ");
    opKeyDecodeCmd.append(opKey).append(" | base64 --decode > ").append(keyFile.getAbsolutePath());

    String decodedOpKey = TestUtils.executeCommandStrArray(opKeyDecodeCmd.toString());
    return keyFile.getAbsolutePath();
  }

  public static void cleanupAll(String projectRoot) {
    // cleanup.sh - This script does a best-effort delete of acceptance test k8s artifacts, the
    // local test tmp directory, and the potentially remote domain pv directories.
    TestUtils.executeCommandStrArray(projectRoot + "/src/integration-tests/bash/cleanup.sh");
  }

  public static String getGitBranchName() {
    return executeCommandStrArray("git branch | grep \\* | cut -d ' ' -f2-").trim();
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
}

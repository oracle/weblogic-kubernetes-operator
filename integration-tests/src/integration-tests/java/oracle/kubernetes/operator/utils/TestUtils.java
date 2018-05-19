// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;
import java.security.KeyStore;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MultivaluedMap;

import org.glassfish.jersey.jsonp.JsonProcessingFeature;

public class TestUtils {
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");

  public static final int MAX_ITERATIONS_POD = 50; //50 * 5 = 250 seconds
  public static final int WAIT_TIME_POD = 5;

  public static String executeCommand(String command) {
    StringBuffer output = new StringBuffer();
    Process p;
    try {
      p = Runtime.getRuntime().exec(command);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

      //in some cases u may want to read process error stream as well
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

  public static String executeCommand(String commandArgs[]) {
    StringBuffer output = new StringBuffer();
    Process p;
    try {
      p = Runtime.getRuntime().exec(commandArgs);
      p.waitFor();
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedReader errReader = new BufferedReader(new InputStreamReader(p.getErrorStream()));

      //in some cases u may want to read process error stream as well
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

    while (i < MAX_ITERATIONS_POD) {
      String outputStr = TestUtils.executeCommand(cmd.toString());
      if (!outputStr.contains("1/1")) {
        //check for last iteration
        if (i == (MAX_ITERATIONS_POD - 1)) {
          throw new RuntimeException(
              "FAILURE: pod " + podName + " is not running and ready, exiting!");
        }
        logger.info(
            "Pod "
                + podName
                + " is not Ready Ite ["
                + i
                + "/"
                + MAX_ITERATIONS_POD
                + "], sleeping "
                + WAIT_TIME_POD
                + " seconds more");
        try {
          Thread.sleep(WAIT_TIME_POD * 1000);
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

    //check for admin pod
    while (i < MAX_ITERATIONS_POD) {
      String outputStr = TestUtils.executeCommand(cmd.toString());
      logger.info("Output for " + cmd + "\n" + outputStr);
      if (!outputStr.contains("Running")) {
        //check for last iteration
        if (i == (MAX_ITERATIONS_POD - 1)) {
          throw new RuntimeException("FAILURE: pod " + podName + " is not running, exiting!");
        }
        logger.info(
            "Pod "
                + podName
                + " is not Running Ite ["
                + i
                + "/"
                + MAX_ITERATIONS_POD
                + "], sleeping "
                + WAIT_TIME_POD
                + " seconds more");
        try {
          Thread.sleep(WAIT_TIME_POD * 1000);
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

    //check for service
    while (i < MAX_ITERATIONS_POD) {
      String outputStr = TestUtils.executeCommand(cmd.toString());
      logger.fine("Output for " + cmd + "\n" + outputStr);
      if (outputStr.equals("")) {
        //check for last iteration
        if (i == (MAX_ITERATIONS_POD - 1)) {
          throw new RuntimeException("FAILURE: service is not created, exiting!");
        }
        logger.info(
            "Service is not created Ite ["
                + i
                + "/"
                + MAX_ITERATIONS_POD
                + "], sleeping "
                + WAIT_TIME_POD
                + " seconds more");
        try {
          Thread.sleep(WAIT_TIME_POD * 1000);
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
   * @param inputYamlFilePath - output file with replaced values
   * @throws Exception
   */
  public static void createInputFile(
      Properties props, String inputFileTemplate, Path inputYamlFilePath) throws Exception {
    logger.info("Creating input yaml file at " + inputYamlFilePath);

    //copy create-operator-inputs.yaml and modify it
    Files.copy(
        new File(inputFileTemplate).toPath(),
        inputYamlFilePath,
        StandardCopyOption.REPLACE_EXISTING);
    inputYamlFilePath.toFile().setWritable(true);

    //read each line in input template file and replace with op props
    BufferedReader reader = new BufferedReader(new FileReader(inputYamlFilePath.toString()));
    String line = "";
    StringBuffer changedLines = new StringBuffer();
    boolean isLineChanged = false;
    while ((line = reader.readLine()) != null) {
      Enumeration enuKeys = props.keys();
      while (enuKeys.hasMoreElements()) {
        String key = (String) enuKeys.nextElement();
        //if a line starts with the props key then replace
        //the line with key:value in the file
        if (line.startsWith(key+":") || line.startsWith("#" + key+":")) {
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
    //writing to the file
    FileWriter writer = new FileWriter(inputYamlFilePath.toString());
    writer.write(changedLines.toString());
    writer.close();
  }

  public static String getHostName() {
    return executeCommand(new String[] {"/bin/sh", "-c", "hostname | awk -F. '{print $1}'"}).trim();
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
    String output = TestUtils.executeCommand(new String[] {"/bin/sh", "-c", cmd.toString()});
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

    //check for admin pod
    while (i < MAX_ITERATIONS_POD) {
      String outputStr = TestUtils.executeCommand(new String[] {"/bin/sh", "-c", cmd.toString()});
      //logger.info("Output for "+cmd + "\n"+outputStr);
      if (!outputStr.trim().contains("\"" + podName + "\" not found")) {
        //check for last iteration
        if (i == (MAX_ITERATIONS_POD - 1)) {
          throw new RuntimeException("FAILURE: Pod " + podName + " is not deleted, exiting!");
        }
        logger.info(
            "Pod "
                + podName
                + " still exists, Ite ["
                + i
                + "/"
                + MAX_ITERATIONS_POD
                + "], sleeping "
                + WAIT_TIME_POD
                + " seconds more");
        try {
          Thread.sleep(WAIT_TIME_POD * 1000);
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

    while (i < MAX_ITERATIONS_POD) {
      String outputStr = TestUtils.executeCommand(new String[] {"/bin/sh", "-c", cmd.toString()});
      //logger.info("Output for "+cmd + "\n"+outputStr);
      if (!outputStr.trim().contains("\"" + domainUid + "\" not found")) {
        //check for last iteration
        if (i == (MAX_ITERATIONS_POD - 1)) {
          throw new RuntimeException("FAILURE: domain still exists, exiting!");
        }
        logger.info(
            "Domain still exists, Ite ["
                + i
                + "/"
                + MAX_ITERATIONS_POD
                + "], sleeping "
                + WAIT_TIME_POD
                + " seconds more");
        try {
          Thread.sleep(WAIT_TIME_POD * 1000);
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
    //get access token
    String token = getAccessToken(operatorNS);

    //get operator external certificate from weblogic-operator.yaml
    String opExtCertFile = getExternalOperatorCertificate(operatorNS, userProjectsDir);
    //logger.info("opExternalCertificateFile ="+opExtCertFile);

    //get operator external key from weblogic-operator.yaml
    String opExtKeyFile = getExternalOperatorKey(operatorNS, userProjectsDir);
    //logger.info("opExternalKeyFile ="+opExtKeyFile);

    if (!new File(opExtCertFile).exists()) {
      throw new RuntimeException("File " + opExtCertFile + " doesn't exist");
    }
    if (!new File(opExtKeyFile).exists()) {
      throw new RuntimeException("File " + opExtKeyFile + " doesn't exist");
    }
    logger.info("opExtCertFile " + opExtCertFile);
    //Create a java Keystore obj and verify it's not null
    KeyStore myKeyStore =
        PEMImporter.createKeyStore(
            new File(opExtKeyFile), new File(opExtCertFile), "temp_password");
    if (myKeyStore == null) {
      throw new RuntimeException("Keystore Obj is null");
    }

    //Create REST Client obj and verify it's not null
    Client javaClient =
        ClientBuilder.newBuilder()
            .trustStore(myKeyStore)
            .register(JsonProcessingFeature.class)
            .build();

    if (javaClient == null) {
      throw new RuntimeException("Client Obj is null");
    }

    //Create a resource target identified by Operator ext REST API URL
    WebTarget target = javaClient.target(url.toString());
    logger.info("Invoking OP REST API URL: " + target.getUri().toString());

    //Obtain a client request invocation builder
    Builder request = target.request(MediaType.APPLICATION_JSON);
    request
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)
        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON)
        .header("X-Requested-By", "MyJavaClient")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);

    Response response = null;
    //Post scaling request to Operator
    if (jsonObjStr != null) {
      response = request.post(Entity.json(jsonObjStr));
    } else {
      response = request.get();
    }
    logger.info("response: " + response.toString());

    int returnCode = response.getStatus();
    //Verify
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

    String secretName =
        TestUtils.executeCommand(new String[] {"/bin/sh", "-c", secretCmd.toString()}).trim();
    String token = "";
    if (!secretName.equals("")) {
      StringBuffer etokenCmd = new StringBuffer("kubectl get secret ");
      etokenCmd
          .append(secretName)
          .append(" -n ")
          .append(operatorNS)
          .append(" -o jsonpath='{.data.token}'");
      String etoken =
          TestUtils.executeCommand(new String[] {"/bin/sh", "-c", etokenCmd.toString()}).trim();

      if (!etoken.equals("")) {
        token =
            TestUtils.executeCommand(
                    new String[] {"/bin/sh", "-c", "echo " + etoken + " | base64 --decode"})
                .trim();
        //logger.info("Token is "+token);
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

    //logger.info("opCertCmd ="+opCertCmd);
    String opCert =
        TestUtils.executeCommand(new String[] {"/bin/sh", "-c", opCertCmd.toString()}).trim();
    //logger.info("opCert ="+opCert);

    if (opCert.trim().equals("")) {
      throw new RuntimeException("externalOperatorCert is not set");
    }

    StringBuffer opCertDecodeCmd = new StringBuffer("echo ");
    opCertDecodeCmd
        .append(opCert)
        .append(" | base64 --decode > ")
        .append(certFile.getAbsolutePath());

    String decodedOpCert =
        TestUtils.executeCommand(new String[] {"/bin/sh", "-c", opCertDecodeCmd.toString()});
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

    String opKey =
        TestUtils.executeCommand(new String[] {"/bin/sh", "-c", opKeyCmd.toString()}).trim();
    //logger.info("opKey ="+opKey);

    if (opKey.trim().equals("")) {
      throw new RuntimeException("externalOperatorKey is not set");
    }

    StringBuffer opKeyDecodeCmd = new StringBuffer("echo ");
    opKeyDecodeCmd.append(opKey).append(" | base64 --decode > ").append(keyFile.getAbsolutePath());

    String decodedOpKey =
        TestUtils.executeCommand(new String[] {"/bin/sh", "-c", opKeyDecodeCmd.toString()});
    return keyFile.getAbsolutePath();
  }

  public static void cleanupAll() {
    String cmdResult =
        TestUtils.executeCommand(
            new String[] {"/bin/sh", "-c", "../src/integration-tests/bash/cleanup.sh"});
    //logger.info("cleanup.sh result "+cmdResult);
    //check if cmd executed successfully
    /*if(!cmdResult.contains("Exiting with status 0")){
    	throw new RuntimeException("FAILURE: Couldn't create domain PV directory "+cmdResult);
    }*/
  }
  
  public static String getGitBranchName() {
	  return executeCommand(new String[] {"/bin/sh", "-c", "git branch | grep \\* | cut -d ' ' -f2-"}).trim();
  }
}

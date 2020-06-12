// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;

import static oracle.weblogic.kubernetes.TestConstants.ISTIO_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The istio utility class for tests.
 */
public class IstioUtils {

  /**
   * Install istio.
  */
  public static void installIstio() {

    Path istioInstallPath = 
         Paths.get(RESOURCE_DIR, "bash-scripts", "install-istio.sh");
    String installScript = istioInstallPath.toString();
    String command =
        String.format("%s %s %s", installScript, ISTIO_VERSION, WORK_DIR);
    logger.info("Istio installation command {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute());
  }

  /**
   * Uninstall istio.
  */
  public static void uninstallIstio() {

    Path istioInstallPath = 
         Paths.get(RESOURCE_DIR, "bash-scripts", "uninstall-istio.sh");
    String installScript = istioInstallPath.toString();
    String command =
        String.format("%s %s %s", installScript, ISTIO_VERSION, WORK_DIR);
    logger.info("Istio uninstallation command {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute());
  }

  /**
   * Get the http ingress port of istio installation.
   * @returns ingress port for istio-ingressgateway
  */
  public static int getIstioHttpIngressPort() {
    ExecResult result = null;
    StringBuffer getIngressPort = null;
    getIngressPort = new StringBuffer("kubectl -n istio-system get service istio-ingressgateway ");
    getIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"http2\")].nodePort}'");
    logger.info("getIngressPort: kubectl command {0}", new String(getIngressPort));
    try {
      result = exec(new String(getIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getIngressPort: kubectl returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return new Integer(result.stdout());
    }
  }

  /**
   * Get the secure https ingress port of istio installation.
   * @returns secure ingress https port for istio-ingressgateway
  */
  public static int getIstioSecureIngressPort() {
    ExecResult result = null;
    StringBuffer getSecureIngressPort = null;
    getSecureIngressPort = new StringBuffer("kubectl -n istio-system get service istio-ingressgateway ");
    getSecureIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"https\")].nodePort}'");
    logger.info("getSecureIngressPort: kubectl command {0}", new String(getSecureIngressPort));
    try {
      result = exec(new String(getSecureIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getSecureIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getSecureIngressPort: kubectl returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return new Integer(result.stdout());
    }
  }

  /**
   * Get the tcp ingress port of istio installation.
   * @returns tcp ingress port for istio-ingressgateway
   */
  public static int getIstioTcpIngressPort() {
    ExecResult result = null;
    StringBuffer getTcpIngressPort = null;
    getTcpIngressPort = new StringBuffer("kubectl -n istio-system get service istio-ingressgateway ");
    getTcpIngressPort.append("-o jsonpath='{.spec.ports[?(@.name==\"tcp\")].nodePort}'");
    logger.info("getTcpIngressPort: kubectl command {0}", new String(getTcpIngressPort));
    try {
      result = exec(new String(getTcpIngressPort), true);
    } catch (Exception ex) {
      logger.info("Exception in getTcpIngressPort() {0}", ex);
      return 0;
    }
    logger.info("getTcpIngressPort: kubectl returned {0}", result.toString());
    if (result.stdout() == null) {
      return 0;
    } else {
      return new Integer(result.stdout());
    }
  }

  /**
   * TODO: remove when infra for Istio Gateway and Virtual Service is ready.
   * Replace a file with a String Replacement.
   * @param in  input file
   * @param out output file
   * @param oldString the old String to be replaced 
   * @param newString the new String 
   */
  private static void updateFileWithStringReplacement(String in, String out, String oldString, String newString) {

    File fileToBeModified = new File(in);
    File fileToBeReplaced = new File(out);
    String oldContent = "";
    BufferedReader reader = null;
    FileWriter writer = null;
    try {
      reader = new BufferedReader(new FileReader(fileToBeModified));
      //Reading all the lines of input text file into oldContent
      String line = reader.readLine();
      while (line != null) {
        oldContent = oldContent + line + System.lineSeparator();
        line = reader.readLine();
      }
      //Replacing oldString with newString in the oldContent
      String newContent = oldContent.replaceAll(oldString, newString);
      //Rewriting the input text file with newContent
      writer = new FileWriter(fileToBeReplaced);
      writer.write(newContent);
      reader.close();
      writer.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Deploy the Http Istio Gateway and Istio Virtualservice.
   * @param domainNamespace name of the domain namespace
   * @param clusterServiceName  name of WebLogic cluster
   * @param adminServiceName name of the admin service
   * @returns true if deployment is success otherwise false
  */
  public static boolean deployHttpIstioGatewayAndVirtualservice(
       String domainNamespace, 
       String adminServiceName, String clusterServiceName) {
    String input = RESOURCE_DIR + "/istio/istio-http-template.service.yaml";
    String output = RESOURCE_DIR + "/istio/istio-http-service.yaml";
    updateFileWithStringReplacement(input, output, "NAMESPACE", domainNamespace); 
    updateFileWithStringReplacement(output, output, "ADMIN_SERVICE", adminServiceName); 
    updateFileWithStringReplacement(output, output, "CLUSTER_SERVICE", clusterServiceName); 
    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(RESOURCE_DIR)
        .append("/istio/istio-http-service.yaml");
    logger.info("deployIstioGateway: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioGateway: kubectl returned {0}", result.toString());
    if (result.stdout().contains("istio-http-gateway created")) {
      return true;
    } else {
      return false;
    }
  }
  
  /**
   * Deploy the tcp Istio Gateway and Istio Virtualservice.
   * @param domainNamespace name of the domain namespace
   * @param adminServiceName name of the admin service
   * @returns true if deployment is success otherwise false
  */
  public static boolean deployTcpIstioGatewayAndVirtualservice(
        String domainNamespace, String adminServiceName) {
    String input = RESOURCE_DIR + "/istio/istio-tcp-template.service.yaml";
    String output = RESOURCE_DIR + "/istio/istio-tcp-service.yaml";
    String adminService = adminServiceName + ".svc.cluster.local";
    updateFileWithStringReplacement(input, output, "NAMESPACE", domainNamespace); 
    updateFileWithStringReplacement(output, output, "ADMIN_SERVICE", adminServiceName); 
    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(RESOURCE_DIR)
        .append("/istio/istio-tcp-service.yaml");
    logger.info("deployIstioGateway: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioGateway: kubectl returned {0}", result.toString());
    if (result.stdout().contains("istio-tcp-gateway created")) {
      return true;
    } else {
      return false;
    }
  }
}

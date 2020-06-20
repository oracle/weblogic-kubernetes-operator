// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;

import static oracle.weblogic.kubernetes.TestConstants.ISTIO_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
   * Deploy the Http Istio Gateway and Istio Virtualservice.
   * @param domainNamespace name of the domain namespace
   * @param domainUid name of the domain identifier
   * @param clusterServiceName  name of WebLogic cluster
   * @param adminServiceName name of the admin service
   * @returns true if deployment is success otherwise false
  */
  public static boolean deployHttpIstioGatewayAndVirtualservice(
       String domainNamespace, String domainUid, 
       String adminServiceName, String clusterServiceName) throws IOException {

    logger.info("Create a staging location for istio configuration objects");
    Path fileTemp = Paths.get(RESULTS_ROOT, "tmp", "istio");
    deleteDirectory(fileTemp.toFile());
    Files.createDirectories(fileTemp);

    logger.info("Copy istio-http-template.service.yaml to staging location");
    Path srcFile = Paths.get(RESOURCE_DIR, "istio", "istio-http-template.service.yaml");
    Path targetFile = Paths.get(fileTemp.toString(), "istio-http-service.yaml");
    Files.copy(srcFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

    String out = targetFile.toString();
    replaceStringInFile(out, "NAMESPACE", domainNamespace); 
    replaceStringInFile(out, "DUID", domainUid); 
    replaceStringInFile(out, "ADMIN_SERVICE", adminServiceName); 
    replaceStringInFile(out, "CLUSTER_SERVICE", clusterServiceName); 
    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(targetFile);
    logger.info("deployIstioGateway: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioHttpGateway: kubectl returned {0}", result.toString());
    if (result.stdout().contains("istio-http-gateway created")) {
      return true;
    } else {
      return false;
    }
  }
  
  /**
   * Deploy the tcp Istio Gateway and Istio Virtualservice.
   * @param domainNamespace name of the domain namespace
   * @param domainUid name of the domain identifier
   * @param adminServiceName name of the admin service
   * @returns true if deployment is success otherwise false
  */
  public static boolean deployTcpIstioGatewayAndVirtualservice(
        String domainNamespace, String domainUid, String adminServiceName) throws IOException  {

    logger.info("Create a staging location for istio configuration objects");
    Path fileTemp = Paths.get(RESULTS_ROOT, "tmp", "istio");
    deleteDirectory(fileTemp.toFile());
    Files.createDirectories(fileTemp);

    logger.info("Copy istio-tcp-template.service.yaml to staging location");
    Path srcFile = Paths.get(RESOURCE_DIR, "istio", "istio-tcp-template.service.yaml");
    Path targetFile = Paths.get(fileTemp.toString(), "istio-tcp-service.yaml");
    Files.copy(srcFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

    String out = targetFile.toString();
    String adminService = adminServiceName + ".svc.cluster.local";
    replaceStringInFile(out, "NAMESPACE", domainNamespace); 
    replaceStringInFile(out, "DUID", domainUid); 
    replaceStringInFile(out, "ADMIN_SERVICE", adminServiceName); 

    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(targetFile.toString());
    logger.info("deployIstioGateway: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioTcpGateway: kubectl returned {0}", result.toString());
    if (result.stdout().contains("istio-tcp-gateway created")) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * Deploy the Istio DestinationRule. 
   * @param domainNamespace name of the domain namespace
   * @param domainUid name of the domain identifier
   * @returns true if deployment is success otherwise false
  */
  public static boolean deployIstioDestinationRule(
        String domainNamespace, String domainUid) throws IOException  {

    logger.info("Create a staging location for istio configuration objects");
    Path fileTemp = Paths.get(RESULTS_ROOT, "tmp", "istio");
    deleteDirectory(fileTemp.toFile());
    Files.createDirectories(fileTemp);

    logger.info("Copy istio-dr-template.yaml to staging location");
    Path srcFile = Paths.get(RESOURCE_DIR, "istio", "istio-dr-template.yaml");
    Path targetFile = Paths.get(fileTemp.toString(), "istio-dr.yaml");
    Files.copy(srcFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

    String out = targetFile.toString();
    replaceStringInFile(out, "NAMESPACE", domainNamespace); 
    replaceStringInFile(out, "DUID", domainUid); 

    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(targetFile.toString());
    logger.info("deployIstioDestinationRule: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioDestinationRule() {0}", ex);
      return false;
    }
    logger.info("deployIstioDestinationRule: kubectl returned {0}", result.toString());
    if (result.stdout().contains("destination-rule created")) {
      return true;
    } else {
      return false;
    }
  }
}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.ISTIO_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The istio utility class for tests.
 */
public class IstioUtils {

  /**
   * Install istio.
   */
  public static void installIstio() {
    LoggingFacade logger = getLogger();
    Path istioInstallPath =
        Paths.get(RESOURCE_DIR, "bash-scripts", "install-istio.sh");
    String installScript = istioInstallPath.toString();
    String command =
        String.format("%s %s %s", installScript, ISTIO_VERSION, RESULTS_ROOT);
    logger.info("Istio installation command {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute());
    
    // Copy the istio (un)intsall scripts to RESULTS_ROOT, so that istio
    // can be (un)installed manually when SKIP_CLEANUP is set to true
    assertDoesNotThrow(() -> Files.copy(
        Paths.get(RESOURCE_DIR, "bash-scripts", "install-istio.sh"),
        Paths.get(RESULTS_ROOT, "install-istio.sh"), 
        StandardCopyOption.REPLACE_EXISTING),
        "Copy install-istio.sh to RESULTS_ROOT failed");

    assertDoesNotThrow(() -> Files.copy(
        Paths.get(RESOURCE_DIR, "bash-scripts", "uninstall-istio.sh"),
        Paths.get(RESULTS_ROOT, "uninstall-istio.sh"), 
        StandardCopyOption.REPLACE_EXISTING),
        "Copy uninstall-istio.sh to RESULTS_ROOT failed");
  }

  /**
   * Uninstall istio.
   */
  public static void uninstallIstio() {
    LoggingFacade logger = getLogger();
    Path istioInstallPath = 
        Paths.get(RESOURCE_DIR, "bash-scripts", "uninstall-istio.sh");
    String installScript = istioInstallPath.toString();
    String command =
        String.format("%s %s %s", installScript, ISTIO_VERSION, RESULTS_ROOT);
    logger.info("Istio uninstallation command {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute());
  }

  /**
   * Get the http ingress port of istio installation.
   *
   * @return ingress port for istio-ingressgateway
   */
  public static int getIstioHttpIngressPort() {
    LoggingFacade logger = getLogger();
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
      return Integer.valueOf(result.stdout());
    }
  }

  /**
   * Get the secure https ingress port of istio installation.
   *
   * @return secure ingress https port for istio-ingressgateway
   */
  public static int getIstioSecureIngressPort() {
    LoggingFacade logger = getLogger();
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
      return Integer.valueOf(result.stdout());
    }
  }

  /**
   * Get the tcp ingress port of istio installation.
   *
   * @return tcp ingress port for istio-ingressgateway
   */
  public static int getIstioTcpIngressPort() {
    LoggingFacade logger = getLogger();
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
      return Integer.valueOf(result.stdout());
    }
  }

  /**
   * Deploy the Http Istio Gateway and Istio virtual service.
   *
   * @param configPath path to k8s configuration file
   * @return true if deployment is success otherwise false
   */
  public static boolean deployHttpIstioGatewayAndVirtualservice(Path configPath) {
    LoggingFacade logger = getLogger();
    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(configPath);
    logger.info("deployIstioGateway: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioHttpGateway: kubectl returned {0}", result.toString());
    return !(result.stdout().contains("Error"));
  }

  /**
   * Deploy the tcp Istio Gateway and Istio virtual service.
   *
   * @param configPath path to k8s configuration file
   * @return true if deployment is success otherwise false
   */
  public static boolean deployTcpIstioGatewayAndVirtualservice(
      Path configPath) {
    LoggingFacade logger = getLogger();
    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(configPath);
    logger.info("deployIstioGateway: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioGateway() {0}", ex);
      return false;
    }
    logger.info("deployIstioTcpGateway: kubectl returned {0}", result.toString());
    return result.stdout().contains("istio-tcp-gateway created");
  }

  /**
   * Deploy the Istio DestinationRule.
   *
   * @param configPath path to k8s configuration file
   * @return true if deployment is success otherwise false
   */
  public static boolean deployIstioDestinationRule(
      Path configPath) {
    LoggingFacade logger = getLogger();
    ExecResult result = null;
    StringBuffer deployIstioGateway = null;
    deployIstioGateway = new StringBuffer("kubectl apply -f ");
    deployIstioGateway.append(configPath);
    logger.info("deployIstioDestinationRule: kubectl command {0}", new String(deployIstioGateway));
    try {
      result = exec(new String(deployIstioGateway), true);
    } catch (Exception ex) {
      logger.info("Exception in deployIstioDestinationRule() {0}", ex);
      return false;
    }
    logger.info("deployIstioDestinationRule: kubectl returned {0}", result.toString());
    return result.stdout().contains("destination-rule created");
  }

}

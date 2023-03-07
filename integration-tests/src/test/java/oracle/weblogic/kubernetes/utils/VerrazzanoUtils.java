// Copyright (c) 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.replaceNamespace;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A utility class for verrazzano tests.
 */
public class VerrazzanoUtils {

  static LoggingFacade logger = getLogger();

  /**
   * set labels to domain namespace for the WKO to manage it through namespace label selection strategy.
   *
   * @param domainNS domain namespace to label
   * @throws ApiException throws exception when label cannot be set
   */
  public static void setLabelToNamespace(String domainNS) throws ApiException {
    //add label to domain namespace
    assertDoesNotThrow(() -> TimeUnit.MINUTES.sleep(1));
    Map<String, String> labels = new java.util.HashMap<>();
    labels.put("verrazzano-managed", "true");
    labels.put("istio-injection", "enabled");
    V1Namespace namespaceObject = assertDoesNotThrow(() -> Kubernetes.getNamespace(domainNS));
    logger.info(Yaml.dump(namespaceObject));
    assertNotNull(namespaceObject, "Can't find namespace with name " + domainNS);
    namespaceObject.getMetadata().setLabels(labels);
    assertDoesNotThrow(() -> replaceNamespace(namespaceObject));
    logger.info(Yaml.dump(Kubernetes.getNamespace(domainNS)));
  }

  /**
   * Get istio gateway host name for accessing the deployed applications in verrazzano.
   *
   * @param namespace namespace in which gateway is deployed
   * @return istio host name
   */
  public static String getIstioHost(String namespace) {
    String curlCmd = KUBERNETES_CLI + " get gateways.networking.istio.io -n "
        + namespace + " -o jsonpath='{.items[0].spec.servers[0].hosts[0]}'";
    ExecResult result = null;
    logger.info("curl command {0}", curlCmd);
    result = assertDoesNotThrow(() -> exec(curlCmd, true));
    logger.info(String.valueOf(result.exitValue()));
    logger.info(result.stdout());
    logger.info(result.stderr());
    assertEquals(0, result.exitValue(), "Failed to get istio host details");
    return result.stdout();
  }

  /**
   * Get istio loadbalancer address for accessing the deployed applications in verrazzano.
   *
   * @return loadbalancer address
   */
  public static String getLoadbalancerAddress() {
    String curlCmd = KUBERNETES_CLI + " get service -n istio-system "
        + "istio-ingressgateway -o jsonpath='{.status.loadBalancer.ingress[0].ip}'";
    logger.info("curl command {0}", curlCmd);
    ExecResult result = assertDoesNotThrow(() -> exec(curlCmd, true));
    logger.info(String.valueOf(result.exitValue()));
    logger.info(result.stdout());
    logger.info(result.stderr());
    assertEquals(0, result.exitValue(), "Failed to get loadbalancer address");
    return result.stdout();
  }

  /**
   * Access applications runnign in WebLogic pods and verify the returned page has expected message.
   *
   * @param url url to access
   * @param message message to be present in returned page
   * @return true if expected message is in returned page, otherwise false
   */
  public static boolean verifyVzApplicationAccess(String url, String message) {
    String curlCmd = "curl -sk " + url;
    logger.info("curl command {0}", curlCmd);
    ExecResult result = assertDoesNotThrow(() -> exec(curlCmd, true));
    logger.info(String.valueOf(result.exitValue()));
    logger.info(result.stdout());
    logger.info(result.stderr());
    return result.stdout().contains(message);
  }

}

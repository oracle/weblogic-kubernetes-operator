// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import org.joda.time.DateTime;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

public class Pod {

  /**
   * Delete Kubernetes Pod.
   *
   * @param name      name of the pod
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean deletePod(String name, String namespace) throws ApiException {
    return Kubernetes.deletePod(name, namespace);
  }

  /**
   * List Kubernetes pods in a namespace.
   *
   * @param namespace      name of namespace
   * @param labelSelectors with which pods are decorated
   * @return V1PodList list of pods
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1PodList listPods(String namespace, String labelSelectors) throws ApiException {
    return Kubernetes.listPods(namespace, labelSelectors);
  }

  /**
   * Get a pod's log.
   *
   * @param podName   name of the pod
   * @param namespace name of the namespace
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String podName, String namespace) throws ApiException {
    return Kubernetes.getPodLog(podName, namespace);
  }

  /**
   * Get the creationTimestamp for a given pod with following parameters.
   *
   * @param namespace     namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName       name of the pod
   * @return creationTimestamp from metadata section of the pod
   * @throws ApiException if Kubernetes client API call fails
   */
  public static DateTime getPodCreationTimestamp(String namespace, String labelSelector, String podName)
      throws ApiException {
    return Kubernetes.getPodCreationTimestamp(namespace, labelSelector, podName);
  }

  /**
   * Get the Kubernetes pod object with following parameters.
   *
   * @param namespace     namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName       name of the pod
   * @return V1Pod pod object
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1Pod getPod(String namespace, String labelSelector, String podName) throws ApiException {
    return Kubernetes.getPod(namespace, labelSelector, podName);
  }

  /**
   * Patch domain to shutdown a WebLogic  server by changing the value of
   * its serverStartPolicy property to NEVER.
   *
   * @param domainUid  unique domain identifier
   * @param namespace  name of the namespace
   * @param serverName name of the WebLogic server to shutdown
   * @return true if patching domain operation succeeds or false if the operation fails
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static boolean shutdownManagedServerUsingServerStartPolicy(String domainUid,
                                                                    String namespace,
                                                                    String serverName) throws ApiException {
    return patchDomainUsingServerStartPolicy(domainUid, namespace, serverName, "NEVER");
  }

  /**
   * Patch domain to start a WebLogic server by changing the value of
   * its serverStartPolicy property to IF_NEEDED.
   *
   * @param domainUid  unique domain identifier
   * @param namespace  name of the namespace
   * @param serverName name of the WebLogic server to start
   * @return true if patching domain operation succeeds or false if the operation fails
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static boolean startManagedServerUsingServerStartPolicy(String domainUid,
                                                                 String namespace,
                                                                 String serverName) throws ApiException {
    return patchDomainUsingServerStartPolicy(domainUid, namespace, serverName, "IF_NEEDED");
  }

  /**
   * Patch domain to change the serverStartPolicy property of a WebLogic server.
   *
   * @param domainUid  unique domain identifier
   * @param namespace  name of the namespace
   * @param serverName name of the WebLogic server
   * @param policy     value for serverStartPolicy property
   * @return true if patching domain operation succeeds or false if the operation fails
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static boolean patchDomainUsingServerStartPolicy(String domainUid,
                                                          String namespace,
                                                          String serverName,
                                                          String policy) throws ApiException {
    final String patchFormat = "application/json-patch+json";
    StringBuffer patchData = new StringBuffer("[{");
    patchData.append("\"op\": \"replace\",")
        .append(" \"path\": \"/spec/managedServers\",")
        .append(" \"value\":[{\"serverName\":\"")
        .append(serverName)
        .append("\",\"serverStartPolicy\": \"")
        .append(policy)
        .append("\"}]}]");
    getLogger().info("Patch data to change serverStartPolicy is: {0}", patchData);

    // call Kubernetes API GenericKubernetesApi.patch to patch the domain
    V1Patch patch = new V1Patch(new String(patchData));

    return Kubernetes.patchDomainCustomResource(domainUid, namespace, patch, patchFormat);
  }
}

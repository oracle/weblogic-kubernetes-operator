// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

public class Pod {

  /**
   * Delete Kubernetes Pod.
   *
   * @param name name of the pod
   * @param namespace name of namespace
   * @return true if successful, false otherwise
  */
  public static boolean deletePod(String name, String namespace) {
    return Kubernetes.deletePod(name, namespace);
  }

  /**
   * List Kubernetes Pod(s) in a namesapce.
   *
   * @param namespace name of namespace
   * @param labelSelectors with which pods are decorated
   * @return V1PodList list of pods
  */
  public static V1PodList listPods(String namespace, String labelSelectors) {
    V1PodList retPodList = null;
    try {
      retPodList = Kubernetes.listPods(namespace,labelSelectors);
    } catch (ApiException api) {
      return null;
    }
    return retPodList;
  }

  /**
   * Get a pod's log.
   *  
   * @param podName name of the Pod
   * @param namespace name of the Namespace
   * @return log as a String
  */
  public static String getPodLog(String podName, String namespace) {
    try {
      return Kubernetes.getPodLog(podName,namespace);
    } catch (ApiException api) {
      return null;
    }
  }

  /**
   * Get the creationTimestamp for a given pod with following parameters.
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod 
   * @return creationTimestamp from metadata section of the Pod
   */
  public static String getPodCreationTime(String namespace, String labelSelector, String podName) {
    try {
      return Kubernetes.getPodCreationTime(namespace,labelSelector,podName);
    } catch (ApiException api) {
      return null;
    }
  }

  /**
   * Get the Pod object with following parameters.
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod 
   * @return V1Pod pod object
   */
  public static V1Pod getPod(String namespace, String labelSelector, String podName) {
    try {
      V1PodList pods = Kubernetes.listPods(namespace, labelSelector);
      for (var pod : pods.getItems()) {
        if (podName.equals(pod.getMetadata().getName())) {
          return pod;
        }
      }
    } catch (ApiException api) {
      return null;
    }
    return null;
  }
}

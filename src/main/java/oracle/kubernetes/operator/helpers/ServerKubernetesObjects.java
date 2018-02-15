// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;

import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;

/**
 * Kubernetes pods and services associated with a single WebLogic server
 *
 */
public class ServerKubernetesObjects {
  private V1Pod pod = null;
  private V1Service service = null;
  private Map<String, V1Service> channels = null;
  
  /**
   * The Pod
   * @return Pod
   */
  public V1Pod getPod() {
    return pod;
  }
  
  /**
   * Sets pod
   * @param pod Pod
   */
  public void setPod(V1Pod pod) {
    this.pod = pod;
  }
  
  /**
   * The Service
   * @return Service
   */
  public V1Service getService() {
    return service;
  }
  
  /**
   * Sets service
   * @param service Service
   */
  public void setService(V1Service service) {
    this.service = service;
  }
  
  /**
   * Channel map
   * @return Map from channel name to Service
   */
  public Map<String, V1Service> getChannels() {
    if (channels == null) {
      channels = new HashMap<String, V1Service>();
    }
    return channels;
  }
}

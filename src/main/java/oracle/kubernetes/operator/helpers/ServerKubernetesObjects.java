// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;

/**
 * Kubernetes pods and services associated with a single WebLogic server
 *
 */
public class ServerKubernetesObjects {
  private final AtomicReference<V1Pod> pod = new AtomicReference<>(null);
  private final AtomicReference<V1Service> service = new AtomicReference<>(null);
  private Map<String, V1Service> channels = null;
  
  /**
   * The Pod
   * @return Pod
   */
  public AtomicReference<V1Pod> getPod() {
    return pod;
  }
  
  /**
   * The Service
   * @return Service
   */
  public AtomicReference<V1Service> getService() {
    return service;
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

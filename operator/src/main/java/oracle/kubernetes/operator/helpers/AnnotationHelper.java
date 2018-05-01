// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1ObjectMeta;

/**
 * Annotates pods, services with details about the Domain instance and checks these annotations.
 * 
 */
public class AnnotationHelper {
  /**
   * Marks metadata with annotations that let Prometheus know how to retrieve metrics from
   * the wls-exporter web-app.  The specified httpPort should be the listen port of the WebLogic server
   * running in the pod.
   * @param meta Metadata
   * @param httpPort HTTP listen port
   */
  public static void annotateForPrometheus(V1ObjectMeta meta, int httpPort) {
     meta.putAnnotationsItem("prometheus.io/port", "" + httpPort); // should be the ListenPort of the server in the pod
     meta.putAnnotationsItem("prometheus.io/path", "/wls-exporter/metrics");
     meta.putAnnotationsItem("prometheus.io/scrape", "true");
  }
}

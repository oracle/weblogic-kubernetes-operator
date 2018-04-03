// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Objects;

import io.kubernetes.client.models.V1ObjectMeta;

/**
 * Annotates pods, services with details about the Domain instance and checks these annotations.
 * 
 */
public class AnnotationHelper {
  // Make these public so that the tests can use them:
  public static final String FORMAT_ANNOTATION = "weblogic.oracle/operator-formatVersion";
  public static final String FORMAT_VERSION = "1";
  
  /**
   * Marks metadata object with an annotation saying that it was created for this format version
   * @param meta Metadata object that will be included in a newly created resource, e.g. pod or service
   */
  public static void annotateWithFormat(V1ObjectMeta meta) {
    meta.putAnnotationsItem(FORMAT_ANNOTATION, FORMAT_VERSION);
  }


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

  /**
   * Check the metadata object for the presence of an annotation matching the expected format version.
   * @param meta The metadata object
   * @return true, if the metadata includes an annotation matching the expected format version
   */
  public static boolean checkFormatAnnotation(V1ObjectMeta meta) {
    String metaResourceVersion = meta.getAnnotations().get(FORMAT_ANNOTATION);
    return Objects.equals(FORMAT_VERSION, metaResourceVersion);
  }
}

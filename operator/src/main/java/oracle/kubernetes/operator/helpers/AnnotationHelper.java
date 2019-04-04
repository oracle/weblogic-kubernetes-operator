// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1Service;
import io.kubernetes.client.util.Yaml;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;

/** Annotates pods, services with details about the Domain instance and checks these annotations. */
public class AnnotationHelper {
  private static final boolean DEBUG = false;
  static final String SHA256_ANNOTATION = "weblogic.sha256";
  private static final String HASHED_STRING = "hashedString";
  private static Function<Object, String> HASH_FUNCTION = o -> DigestUtils.sha256Hex(Yaml.dump(o));

  /**
   * Marks metadata with annotations that let Prometheus know how to retrieve metrics from the
   * wls-exporter web-app. The specified httpPort should be the listen port of the WebLogic server
   * running in the pod.
   *
   * @param meta Metadata
   * @param httpPort HTTP listen port
   */
  static void annotateForPrometheus(V1ObjectMeta meta, int httpPort) {
    meta.putAnnotationsItem(
        "prometheus.io/port", "" + httpPort); // should be the ListenPort of the server in the pod
    meta.putAnnotationsItem("prometheus.io/path", "/wls-exporter/metrics");
    meta.putAnnotationsItem("prometheus.io/scrape", "true");
  }

  public static V1Pod withSha256Hash(V1Pod pod) {
    return DEBUG ? addHashAndDebug(pod) : addHash(pod);
  }

  public static V1Service withSha256Hash(V1Service service) {
    return addHash(service);
  }

  private static V1Pod addHashAndDebug(V1Pod pod) {
    String dump = Yaml.dump(pod);
    addHash(pod);
    pod.getMetadata().putAnnotationsItem(HASHED_STRING, dump);
    return pod;
  }

  private static V1Pod addHash(V1Pod pod) {
    pod.getMetadata().putAnnotationsItem(SHA256_ANNOTATION, HASH_FUNCTION.apply(pod));
    return pod;
  }

  private static V1Service addHash(V1Service service) {
    service.getMetadata().putAnnotationsItem(SHA256_ANNOTATION, HASH_FUNCTION.apply(service));
    return service;
  }

  static String getDebugString(V1Pod pod) {
    return pod.getMetadata().getAnnotations().get(HASHED_STRING);
  }

  static String getHash(V1Pod pod) {
    return pod.getMetadata().getAnnotations().get(SHA256_ANNOTATION);
  }

  static String getHash(V1Service service) {
    return service.getMetadata().getAnnotations().get(SHA256_ANNOTATION);
  }
}

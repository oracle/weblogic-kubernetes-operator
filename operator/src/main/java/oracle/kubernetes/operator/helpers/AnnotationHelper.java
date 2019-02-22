// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.models.V1ObjectMeta;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.util.Yaml;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;

/** Annotates pods, services with details about the Domain instance and checks these annotations. */
public class AnnotationHelper {
  private static final boolean DEBUG = true;
  static final String SHA256_ANNOTATION = "weblogic.sha256";
  private static Function<V1Pod, String> HASH_FUNCTION =
      pod -> DigestUtils.sha256Hex(Yaml.dump(pod));

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

  static V1Pod withSha256Hash(V1Pod pod) {
    String dump = Yaml.dump(pod);
    pod.getMetadata().putAnnotationsItem(SHA256_ANNOTATION, HASH_FUNCTION.apply(pod));
    if (DEBUG) pod.getMetadata().putAnnotationsItem("hashedString", dump);
    return pod;
  }

  static String getHash(V1Pod pod) {
    return pod.getMetadata().getAnnotations().get(SHA256_ANNOTATION);
  }
}

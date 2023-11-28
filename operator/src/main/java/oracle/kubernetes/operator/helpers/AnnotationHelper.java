// Copyright (c) 2017, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.Yaml;
import org.apache.commons.codec.digest.DigestUtils;

/** Annotates pods, services with details about the Domain instance and checks these annotations. */
public class AnnotationHelper {

  private AnnotationHelper() {
    // no-op
  }

  static final String SHA256_ANNOTATION = "weblogic.sha256";
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Function<Object, String> hashFunction = o -> DigestUtils.sha256Hex(Yaml.dump(o));

  /**
   * Marks metadata with annotations that let Prometheus know how to retrieve metrics from the
   * wls-exporter web-app. The specified httpPort should be the listen port of the WebLogic server
   * running in the pod.
   * @param meta Metadata
   * @param applicationContext the context for the exporter application
   * @param httpPort HTTP listen port
   */
  static void annotateForPrometheus(V1ObjectMeta meta, String applicationContext, int httpPort) {
    meta.putAnnotationsItem(
        "prometheus.io/port", String.valueOf(httpPort)); // should be the ListenPort of the server in the pod
    meta.putAnnotationsItem("prometheus.io/path", applicationContext + "/metrics");
    meta.putAnnotationsItem("prometheus.io/scrape", "true");
  }

  public static <K extends KubernetesObject> K withSha256Hash(K kubernetesObject) {
    return withSha256Hash(kubernetesObject, kubernetesObject);
  }

  static <K extends KubernetesObject> K withSha256Hash(K kubernetesObject, Object objectToHash) {
    return addHash(kubernetesObject, objectToHash);
  }

  private static <K extends KubernetesObject> K addHash(K kubernetesObject, Object objectToHash) {
    kubernetesObject.getMetadata().putAnnotationsItem(SHA256_ANNOTATION, createHash(objectToHash));
    return kubernetesObject;
  }

  static String createHash(Object objectToHash) {
    return hashFunction.apply(objectToHash);
  }

  static String getHash(KubernetesObject kubernetesObject) {
    return getAnnotation(kubernetesObject.getMetadata(), AnnotationHelper::getSha256Annotation);
  }

  private static String getAnnotation(
      V1ObjectMeta metadata, Function<Map<String, String>, String> annotationGetter) {
    return Optional.ofNullable(metadata)
        .map(V1ObjectMeta::getAnnotations)
        .map(annotationGetter)
        .orElse("");
  }

  private static String getSha256Annotation(Map<String, String> annotations) {
    return annotations.get(SHA256_ANNOTATION);
  }
}
// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.Yaml;
import org.apache.commons.codec.digest.DigestUtils;

/** Annotates pods, services with details about the Domain instance and checks these annotations. */
public class AnnotationHelper {
  static final String SHA256_ANNOTATION = "weblogic.sha256";
  private static final boolean DEBUG = false;
  private static final String HASHED_STRING = "hashedString";
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
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

  public static <K extends KubernetesObject> K withSha256Hash(K kubernetesObject) {
    return withSha256Hash(kubernetesObject, kubernetesObject);
  }

  public static <K extends KubernetesObject> K withSha256Hash(K kubernetesObject, Object objectToHash) {
    return addHash(kubernetesObject, objectToHash);
  }

  private static V1Pod addHashAndDebug(V1Pod pod) {
    String dump = Yaml.dump(pod);
    addHash(pod);
    pod.getMetadata().putAnnotationsItem(HASHED_STRING, dump);
    return pod;
  }

  private static <K extends KubernetesObject> K addHash(K kubernetesObject) {
    return addHash(kubernetesObject, kubernetesObject);
  }

  private static <K extends KubernetesObject> K addHash(K kubernetesObject, Object objectToHash) {
    kubernetesObject.getMetadata().putAnnotationsItem(SHA256_ANNOTATION, HASH_FUNCTION.apply(objectToHash));
    return kubernetesObject;
  }

  static String getHash(KubernetesObject kubernetesObject) {
    return getAnnotation(kubernetesObject.getMetadata(), AnnotationHelper::getSha256Annotation);
  }

  static String getDebugString(V1Pod pod) {
    return getAnnotation(pod.getMetadata(), AnnotationHelper::getDebugHashAnnotation);
  }

  private static String getAnnotation(
      V1ObjectMeta metadata, Function<Map<String, String>, String> annotationGetter) {
    return Optional.ofNullable(metadata)
        .map(V1ObjectMeta::getAnnotations)
        .map(annotationGetter)
        .orElse("");
  }

  private static String getDebugHashAnnotation(Map<String, String> annotations) {
    return annotations.get(HASHED_STRING);
  }

  private static String getSha256Annotation(Map<String, String> annotations) {
    return annotations.get(SHA256_ANNOTATION);
  }
}
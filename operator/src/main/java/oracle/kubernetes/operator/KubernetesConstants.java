// Copyright (c) 2017, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.net.HttpURLConnection;

/** Kubernetes constants. */
public interface KubernetesConstants {
  String DEFAULT_IMAGE = "container-registry.oracle.com/middleware/weblogic:12.2.1.4";
  String DEFAULT_EXPORTER_IMAGE = "ghcr.io/oracle/weblogic-monitoring-exporter:2.3.0";
  String DEFAULT_FLUENTD_IMAGE = "fluent/fluentd-kubernetes-daemonset:v1.16.1-debian-elasticsearch7-1.2";
  String EXPORTER_CONTAINER_NAME = "monitoring-exporter";
  String LATEST_IMAGE_SUFFIX = ":latest";

  String DOMAIN_CRD_NAME = "domains.weblogic.oracle";
  String DOMAIN = "Domain";
  String DOMAIN_GROUP = "weblogic.oracle";
  String DOMAIN_PLURAL = "domains";
  String DOMAIN_SINGULAR = "domain";
  String DOMAIN_SHORT = "dom";
  String DOMAIN_VERSION = "v9";
  String OLD_DOMAIN_VERSION = "v8";

  String CLUSTER_CRD_NAME = "clusters.weblogic.oracle";
  String CLUSTER = "Cluster";
  String CLUSTER_PLURAL = "clusters";
  String CLUSTER_SINGULAR = "cluster";
  String SCALE = "Scale";
  String CLUSTER_SHORT = "clu";
  String CLUSTER_VERSION = "v1";
  String API_VERSION_CLUSTER_WEBLOGIC_ORACLE = DOMAIN_GROUP + "/" + CLUSTER_VERSION;

  String API_VERSION_WEBLOGIC_ORACLE = DOMAIN_GROUP + "/" + DOMAIN_VERSION;

  boolean DEFAULT_HTTP_ACCESS_LOG_IN_LOG_HOME = true;
  boolean DEFAULT_INCLUDE_SERVER_OUT_IN_POD_LOG = true;
  boolean DEFAULT_REPLACE_VARIABLES_IN_JAVA_OPTIONS = false;
  int DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP = 0;
  int DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN = 1;
  int DEFAULT_MAX_CLUSTER_UNAVAILABLE = 1;
  int MINIMUM_CLUSTER_COUNT = 0;

  String WLS_CONTAINER_NAME = "weblogic-server";

  String SCRIPT_CONFIG_MAP_NAME = "weblogic-scripts-cm";
  String DOMAIN_DEBUG_CONFIG_MAP_SUFFIX = "-weblogic-domain-debug-cm";
  String OPERATOR_ENABLE_REST_ENDPOINT_ENV = "ENABLE_REST_ENDPOINT";

  String OPERATOR_NAMESPACE_ENV = "OPERATOR_NAMESPACE";
  String OPERATOR_POD_NAME_ENV = "OPERATOR_POD_NAME";
  String WEBHOOK_NAMESPACE_ENV = "WEBHOOK_NAMESPACE";
  String OPERATOR_POD_UID_ENV = "OPERATOR_POD_UID";
  String WEBHOOK_POD_NAME_ENV = "WEBHOOK_POD_NAME";
  String WEBHOOK_POD_UID_ENV = "WEBHOOK_POD_UID";
  String NAMESPACE = "Namespace";
  String POD = "Pod";
  String EVICTED_REASON = "Evicted";
  String UNSCHEDULABLE_REASON = "Unschedulable";
  String POD_SCHEDULED = "PodScheduled";
  int DEFAULT_EXPORTER_SIDECAR_PORT = 8080;

  //---------- HTTP statuses returned from Kubernetes ----------

  // Operation was performed successfully
  int HTTP_OK = HttpURLConnection.HTTP_OK;

  // The server does not understand the request
  int HTTP_BAD_REQUEST = HttpURLConnection.HTTP_BAD_REQUEST;

  // The client could not be authenticated
  int HTTP_UNAUTHORIZED = HttpURLConnection.HTTP_UNAUTHORIZED;

  // The request conflicted with some other processing being performed at the same time.
  int HTTP_CONFLICT = HttpURLConnection.HTTP_CONFLICT;

  // The client is not permitted to perform the specified operation
  int HTTP_FORBIDDEN = HttpURLConnection.HTTP_FORBIDDEN;
  
  // The requested resource was not found
  int HTTP_NOT_FOUND = HttpURLConnection.HTTP_NOT_FOUND;

  // The HTTP method used is not supported for this resource
  int HTTP_BAD_METHOD = HttpURLConnection.HTTP_BAD_METHOD;

  // The specified version of the requested resource is no longer available
  int HTTP_GONE = HttpURLConnection.HTTP_GONE;

  // Kubernetes is unable to perform the request. The associated exception contains more details.
  int HTTP_UNPROCESSABLE_ENTITY = 422;

  // More requests have been received by Kubernetes than it can handle. Clients may retry the failed operation.
  int HTTP_TOO_MANY_REQUESTS = 429;

  // An unspecified error has occurred inside Kubernetes. Clients may retry the failed operation.
  int HTTP_INTERNAL_ERROR = HttpURLConnection.HTTP_INTERNAL_ERROR;

  // Kubernetes is temporarily unavailable to respond. Clients may retry the failed operation.
  int HTTP_UNAVAILABLE = HttpURLConnection.HTTP_UNAVAILABLE;

  // A (possibly Ingress-related) timeout occurred internally to Kubernetes. Clients may retry the failed operation.
  int HTTP_GATEWAY_TIMEOUT = HttpURLConnection.HTTP_GATEWAY_TIMEOUT;

  String ADMISSION_REVIEW_API_VERSION = "admission.k8s.io/v1";
  String ADMISSION_REVIEW_KIND = "AdmissionReview";

  String DOMAIN_IMAGE = "spec.image";
  String DOMAIN_INTROSPECT_VERSION = "spec.introspectVersion";
  String AUXILIARY_IMAGES = "spec.configuration.model.auxiliaryImages";
  String PV_PVC_API_VERSION = "v1";
}

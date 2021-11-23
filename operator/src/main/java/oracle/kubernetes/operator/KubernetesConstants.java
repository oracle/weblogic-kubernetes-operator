// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.net.HttpURLConnection;

/** Kubernetes constants. */
public interface KubernetesConstants {
  String DEFAULT_IMAGE = "container-registry.oracle.com/middleware/weblogic:12.2.1.4";
  String DEFAULT_EXPORTER_IMAGE = "ghcr.io/oracle/weblogic-monitoring-exporter:2.0.3";
  String EXPORTER_CONTAINER_NAME = "monitoring-exporter";
  String ALWAYS_IMAGEPULLPOLICY = ImagePullPolicy.Always.name();
  String IFNOTPRESENT_IMAGEPULLPOLICY = ImagePullPolicy.IfNotPresent.name();
  String LATEST_IMAGE_SUFFIX = ":latest";

  String CRD_NAME = "domains.weblogic.oracle";
  String DOMAIN = "Domain";
  String DOMAIN_GROUP = "weblogic.oracle";
  String DOMAIN_PLURAL = "domains";
  String DOMAIN_SINGULAR = "domain";
  String DOMAIN_SHORT = "dom";
  String DOMAIN_VERSION = "v9";

  String API_VERSION_WEBLOGIC_ORACLE = DOMAIN_GROUP + "/" + DOMAIN_VERSION;

  String DOMAIN_PATH = "/apis/" + DOMAIN_GROUP + "/" + DOMAIN_VERSION + "/namespaces/{namespace}/" + DOMAIN_PLURAL;
  String DOMAIN_SPECIFIC_PATH = DOMAIN_PATH + "/{name}";
  String DOMAIN_SCALE_PATH = DOMAIN_SPECIFIC_PATH + "/scale";
  String DOMAIN_STATUS_PATH = DOMAIN_SPECIFIC_PATH + "/status";

  boolean DEFAULT_HTTP_ACCESS_LOG_IN_LOG_HOME = true;
  boolean DEFAULT_INCLUDE_SERVER_OUT_IN_POD_LOG = true;
  boolean DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE = true;
  int DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP = 0;
  int DEFAULT_MAX_CLUSTER_CONCURRENT_SHUTDOWN = 1;

  String WLS_CONTAINER_NAME = "weblogic-server";

  String SCRIPT_CONFIG_MAP_NAME = "weblogic-scripts-cm";
  String DOMAIN_DEBUG_CONFIG_MAP_SUFFIX = "-weblogic-domain-debug-cm";

  String GRACEFUL_SHUTDOWNTYPE = ShutdownType.Graceful.name();

  String OPERATOR_NAMESPACE_ENV = "OPERATOR_NAMESPACE";
  String OPERATOR_POD_NAME_ENV = "OPERATOR_POD_NAME";
  String OPERATOR_POD_UID_ENV = "OPERATOR_POD_UID";
  String NAMESPACE = "Namespace";
  String POD = "Pod";

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
}

// Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Kubernetes constants. */
public interface KubernetesConstants {
  String DEFAULT_IMAGE = "container-registry.oracle.com/middleware/weblogic:12.2.1.4";
  String ALWAYS_IMAGEPULLPOLICY = ImagePullPolicy.Always.name();
  String IFNOTPRESENT_IMAGEPULLPOLICY = ImagePullPolicy.IfNotPresent.name();
  String LATEST_IMAGE_SUFFIX = ":latest";

  String CRD_NAME = "domains.weblogic.oracle";
  String DOMAIN = "Domain";
  String DOMAIN_GROUP = "weblogic.oracle";
  String DOMAIN_PLURAL = "domains";
  String DOMAIN_SINGULAR = "domain";
  String DOMAIN_SHORT = "dom";
  String DOMAIN_VERSION = "v8";

  String DOMAIN_PATH = "/apis/" + DOMAIN_GROUP + "/" + DOMAIN_VERSION + "/namespaces/{namespace}/" + DOMAIN_PLURAL;
  String DOMAIN_SPECIFIC_PATH = DOMAIN_PATH + "/{name}";
  String DOMAIN_SCALE_PATH = DOMAIN_SPECIFIC_PATH + "/scale";
  String DOMAIN_STATUS_PATH = DOMAIN_SPECIFIC_PATH + "/status";

  boolean DEFAULT_HTTP_ACCESS_LOG_IN_LOG_HOME = true;
  boolean DEFAULT_INCLUDE_SERVER_OUT_IN_POD_LOG = true;
  boolean DEFAULT_ALLOW_REPLICAS_BELOW_MIN_DYN_CLUSTER_SIZE = true;
  int DEFAULT_MAX_CLUSTER_CONCURRENT_START_UP = 0;

  String CONTAINER_NAME = "weblogic-server";

  String SCRIPT_CONFIG_MAP_NAME = "weblogic-scripts-cm";
  String DOMAIN_DEBUG_CONFIG_MAP_SUFFIX = "-weblogic-domain-debug-cm";
  String INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX = "-weblogic-domain-introspect-cm";

  String GRACEFUL_SHUTDOWNTYPE = ShutdownType.Graceful.name();
}

// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Kubernetes constants. */
public interface KubernetesConstants {
  String DEFAULT_IMAGE = "store/oracle/weblogic:19.1.0.0";
  String ALWAYS_IMAGEPULLPOLICY = "Always";
  String IFNOTPRESENT_IMAGEPULLPOLICY = "IfNotPresent";
  String NEVER_IMAGEPULLPOLICY = "Never";
  String LATEST_IMAGE_SUFFIX = ":latest";

  String EXTENSIONS_API_VERSION = "extensions/v1beta1";
  String KIND_INGRESS = "Ingress";
  String CLASS_INGRESS = "kubernetes.io/ingress.class";
  String CLASS_INGRESS_VALUE = "traefik";

  String DOMAIN_GROUP = "weblogic.oracle";
  String DOMAIN_VERSION = "v2";
  String DOMAIN_PLURAL = "domains";
  String DOMAIN_SINGULAR = "domain";
  String DOMAIN_SHORT = "dom";

  String DEFAULT_INCLUDE_SERVER_OUT_IN_POD_LOG = "true";

  String CONTAINER_NAME = "weblogic-server";

  String DOMAIN_CONFIG_MAP_NAME = "weblogic-domain-cm";
  String DOMAIN_DEBUG_CONFIG_MAP_SUFFIX = "-weblogic-domain-debug-cm";
  String API_VERSION_ORACLE_V2 = "weblogic.oracle/v2";
  String INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX = "-weblogic-domain-introspect-cm";
}

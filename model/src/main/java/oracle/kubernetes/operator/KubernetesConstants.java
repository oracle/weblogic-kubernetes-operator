// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Kubernetes constants. */
public interface KubernetesConstants {
  String DEFAULT_IMAGE = "store/oracle/weblogic:12.2.1.3";
  String ALWAYS_IMAGEPULLPOLICY = ImagePullPolicy.Always.name();
  String IFNOTPRESENT_IMAGEPULLPOLICY = ImagePullPolicy.IfNotPresent.name();
  String LATEST_IMAGE_SUFFIX = ":latest";

  String EXTENSIONS_API_VERSION = "extensions/v1beta1";
  String KIND_INGRESS = "Ingress";
  String CLASS_INGRESS = "kubernetes.io/ingress.class";
  String CLASS_INGRESS_VALUE = "traefik";

  String CRD_NAME = "domains.weblogic.oracle";
  String DOMAIN = "Domain";
  String DOMAIN_GROUP = "weblogic.oracle";
  String DOMAIN_PLURAL = "domains";
  String DOMAIN_SINGULAR = "domain";
  String DOMAIN_SHORT = "dom";
  String DOMAIN_VERSION = "v3";
  String[] DOMAIN_ALTERNATE_VERSIONS = {"v2"};

  boolean DEFAULT_INCLUDE_SERVER_OUT_IN_POD_LOG = true;

  String CONTAINER_NAME = "weblogic-server";

  String DOMAIN_CONFIG_MAP_NAME = "weblogic-domain-cm";
  String DOMAIN_DEBUG_CONFIG_MAP_SUFFIX = "-weblogic-domain-debug-cm";
  String INTROSPECTOR_CONFIG_MAP_NAME_SUFFIX = "-weblogic-domain-introspect-cm";
}

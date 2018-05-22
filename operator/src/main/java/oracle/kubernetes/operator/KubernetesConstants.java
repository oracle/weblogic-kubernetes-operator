// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Kubernetes constants. */
public interface KubernetesConstants {
  public static final String DEFAULT_IMAGE = "store/oracle/weblogic:12.2.1.3";
  public static final String ALWAYS_IMAGEPULLPOLICY = "Always";
  public static final String IFNOTPRESENT_IMAGEPULLPOLICY = "IfNotPresent";
  public static final String NEVER_IMAGEPULLPOLICY = "Never";
  public static final String LATEST_IMAGE_SUFFIX = ":latest";

  public static final String EXTENSIONS_API_VERSION = "extensions/v1beta1";
  public static final String KIND_INGRESS = "Ingress";
  public static final String CLASS_INGRESS = "kubernetes.io/ingress.class";
  public static final String CLASS_INGRESS_VALUE = "traefik";

  public static final String DOMAIN_GROUP = "weblogic.oracle";
  public static final String DOMAIN_VERSION = "v1";
  public static final String DOMAIN_PLURAL = "domains";
  public static final String DOMAIN_SINGULAR = "domain";
  public static final String DOMAIN_SHORT = "dom";

  public static final String CONTAINER_NAME = "weblogic-server";

  public static final String DOMAIN_CONFIG_MAP_NAME = "weblogic-domain-cm";
}

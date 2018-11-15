// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

public interface VersionConstants {
  String OPERATOR_V1 = "operator-v1";
  String OPERATOR_V2 = "operator-v2";
  String DEFAULT_OPERATOR_VERSION = OPERATOR_V2;
  String DOMAIN_V1 = "domain-v1";
  String DOMAIN_V2 = "domain-v2";
  String DEFAULT_DOMAIN_VERSION = DOMAIN_V2;
  String VOYAGER_LOAD_BALANCER_V1 = "voyager-load-balancer-v1";
  String APACHE_LOAD_BALANCER_V1 = "apache-load-balancer-v1";
  String TRAEFIK_LOAD_BALANCER_V1 = "traefik-load-balancer-v1";
}

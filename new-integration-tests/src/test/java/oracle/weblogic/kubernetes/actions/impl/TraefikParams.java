// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

/**
 * All parameters needed to install Traefik Operator.
 */
public class TraefikParams {

  // only add the values which need to be updated here
  // the default traefik values are here: https://github.com/helm/charts/blob/master/stable/traefik/values.yaml
  private static final String NAMESPACES = "kubernetes.namespaces";

  // Adding some of the most commonly used params for now
  private String namespaces;
  private HelmParams helmParams;

  public TraefikParams namespaces(String namespaces) {
    this.namespaces = namespaces;
    return this;
  }

  public TraefikParams helmParams(HelmParams helmParams) {
    this.helmParams = helmParams;
    return this;
  }

  public HelmParams getHelmParams() {
    return helmParams;
  }

  /**
   * Loads Helm values into a value map.
   *
   * @return Map of values
   */
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();
    values.put(NAMESPACES, namespaces);

    values.values().removeIf(Objects::isNull);
    return values;
  }

}
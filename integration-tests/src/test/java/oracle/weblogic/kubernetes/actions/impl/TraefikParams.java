// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_IMAGE_TAG;

// All parameters needed to install Traefik Operator
public class TraefikParams {

  // Adding some of the most commonly used params for now
  private int nodePortsHttp;
  private int nodePortsHttps;
  private HelmParams helmParams;
  private String traefikImage = TRAEFIK_INGRESS_IMAGE_NAME;
  private String traefikImageTag = TRAEFIK_INGRESS_IMAGE_TAG;
  private String traefikRegistry = TRAEFIK_INGRESS_IMAGE_REGISTRY;

  private static final String NODEPORTS_HTTP = "ports.web.nodePort";
  private static final String NODEPORTS_HTTPS = "ports.websecure.nodePort";
  private static final String TRAEFIK_IMAGE = "image.repository";
  private static final String TRAEFIK_IMAGE_REGISTRY = "image.registry";
  private static final String TRAEFIK_IMAGE_TAG = "image.tag";

  public TraefikParams nodePortsHttp(int nodePortsHttp) {
    this.nodePortsHttp = nodePortsHttp;
    return this;
  }

  public TraefikParams nodePortsHttps(int nodePortsHttps) {
    this.nodePortsHttps = nodePortsHttps;
    return this;
  }

  public TraefikParams helmParams(HelmParams helmParams) {
    this.helmParams = helmParams;
    return this;
  }

  public HelmParams getHelmParams() {
    return helmParams;
  }

  public TraefikParams traefikImage(String traefikImage) {
    this.traefikImage = traefikImage;
    return this;
  }

  public TraefikParams traefikRegistry(String traefikRegistry) {
    this.traefikRegistry = traefikRegistry;
    return this;
  }

  public TraefikParams traefikImageTag(String traefikImageTag) {
    this.traefikImageTag = traefikImageTag;
    return this;
  }

  /**
   * Loads Helm values into a value map.
   *
   * @return Map of values
   */
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();

    if (nodePortsHttp > 0) {
      values.put(NODEPORTS_HTTP, nodePortsHttp);
    }
    if (nodePortsHttps > 0) {
      values.put(NODEPORTS_HTTPS, nodePortsHttps);
    }

    values.put(TRAEFIK_IMAGE, traefikImage);
    values.put(TRAEFIK_IMAGE_REGISTRY, traefikRegistry);
    values.put(TRAEFIK_IMAGE_TAG, traefikImageTag);

    values.values().removeIf(Objects::isNull);
    return values;
  }

}

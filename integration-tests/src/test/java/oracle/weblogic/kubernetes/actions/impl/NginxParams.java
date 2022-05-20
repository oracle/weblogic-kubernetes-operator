// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

import static oracle.weblogic.kubernetes.TestConstants.NGINX_INGRESS_IMAGE_DIGEST;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_INGRESS_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_NGINX_IMAGE_NAME;

/**
 * All parameters needed to install NGINX ingress controller.
 */
public class NginxParams {

  // Only add the values which need to be updated here.
  // The default values can be found here:
  // https://github.com/helm/charts/blob/master/stable/nginx-ingress/values.yaml
  private static final String NODEPORTS_HTTP = "controller.service.nodePorts.http";
  private static final String NODEPORTS_HTTPS = "controller.service.nodePorts.https";
  private static final String ADMISSIONWEBHOOKS_ENABLED = "controller.admissionWebhooks.enabled";
  private static final String INGRESS_CLASS_NAME = "controller.ingressClassResource.name";
  private static final String NGINX_IMAGE_REGISTRY = "controller.image.registry";
  private static final String NGINX_IMAGE = "controller.image.image";
  private static final String NGINX_IMAGE_TAG = "controller.image.tag";
  private static final String NGINX_IMAGE_DIGEST = "controller.image.digest";  

  // Adding some of the most commonly used params for now
  private int nodePortsHttp;
  private int nodePortsHttps;
  private boolean webhooksEnabled = false;
  private HelmParams helmParams;
  private String ingressClassName;
  private final String imageRegistry = OCIR_DEFAULT;
  private final String nginxImage = OCIR_NGINX_IMAGE_NAME;
  private final String nginxImageTag = NGINX_INGRESS_IMAGE_TAG;
  private final String nginxImageDigest = NGINX_INGRESS_IMAGE_DIGEST;

  public NginxParams() {
    ingressClassName = UniqueName.uniqueName("nginx-");
  }

  public NginxParams nodePortsHttp(int nodePortsHttp) {
    this.nodePortsHttp = nodePortsHttp;
    return this;
  }

  public NginxParams nodePortsHttps(int nodePortsHttps) {
    this.nodePortsHttps = nodePortsHttps;
    return this;
  }

  public NginxParams webhooksEnabled(boolean webhooksEnabled) {
    this.webhooksEnabled = webhooksEnabled;
    return this;
  }

  public String getIngressClassName() {
    return ingressClassName;
  }

  public NginxParams helmParams(HelmParams helmParams) {
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

    if (nodePortsHttp > 0) {
      values.put(NODEPORTS_HTTP, nodePortsHttp);
    }
    if (nodePortsHttps > 0) {
      values.put(NODEPORTS_HTTPS, nodePortsHttps);
    }

    values.put(ADMISSIONWEBHOOKS_ENABLED, webhooksEnabled);
    values.put(INGRESS_CLASS_NAME, ingressClassName);
    values.put(NGINX_IMAGE_REGISTRY, imageRegistry);
    values.put(NGINX_IMAGE, nginxImage);
    values.put(NGINX_IMAGE_TAG, nginxImageTag);
    values.put(NGINX_IMAGE_DIGEST, nginxImageDigest);    

    values.values().removeIf(Objects::isNull);
    return values;
  }
}

// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

import static oracle.weblogic.kubernetes.TestConstants.NGINX_INGRESS_IMAGE_DIGEST;
import static oracle.weblogic.kubernetes.TestConstants.NGINX_INGRESS_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.TEST_NGINX_IMAGE_NAME;

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
  private static final String IMAGE_PULL_SECRET = "imagePullSecrets[0].name";
  private static final String IP_FAMILY_POLICY = "controller.service.ipFamilyPolicy";
  private static final String IP_FAMILIES = "controller.service.ipFamilies";
  private static final String TYPE = "controller.service.type";

  // Adding some of the most commonly used params for now
  private int nodePortsHttp;
  private int nodePortsHttps;
  private boolean webhooksEnabled = false;
  private HelmParams helmParams;
  private String ingressClassName;
  private String imageRegistry = TEST_IMAGES_REPO;
  private String nginxImage = TEST_NGINX_IMAGE_NAME;
  private String nginxImageTag = NGINX_INGRESS_IMAGE_TAG;
  private String nginxImageDigest = NGINX_INGRESS_IMAGE_DIGEST;
  private String imageRepoSecret;
  private List<String> ipFamilies;
  private String ipFamilyPolicy = "SingleStack";
  private String type;

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

  public HelmParams getHelmParams() {
    return helmParams;
  }

  public NginxParams imageRegistry(String imageRegistry) {
    this.imageRegistry = imageRegistry;
    return this;
  }

  public NginxParams nginxImage(String nginxImage) {
    this.nginxImage = nginxImage;
    return this;
  }

  public NginxParams nginxImageTag(String nginxImageTag) {
    this.nginxImageTag = nginxImageTag;
    return this;
  }

  public NginxParams nginxImageDigest(String nginxImageDigest) {
    this.nginxImageDigest = nginxImageDigest;
    return this;
  }
  
  public NginxParams imageRepoSecret(String imageRepoSecret) {
    this.imageRepoSecret = imageRepoSecret;
    return this;
  }
  
  public NginxParams ipFamilies(List<String> ipFamilies) {
    this.ipFamilies = ipFamilies;
    return this;
  }
  
  public NginxParams ipFamilyPolicy(String ipFamilyPolicy) {
    this.ipFamilyPolicy = ipFamilyPolicy;
    return this;
  }
  
  public NginxParams type(String type) {
    this.type = type;
    return this;
  }
  
  public String getType() {
    return type;
  }

  public NginxParams helmParams(HelmParams helmParams) {
    this.helmParams = helmParams;
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

    values.put(ADMISSIONWEBHOOKS_ENABLED, webhooksEnabled);
    values.put(INGRESS_CLASS_NAME, ingressClassName);
    values.put(NGINX_IMAGE_REGISTRY, imageRegistry);
    values.put(NGINX_IMAGE, nginxImage);
    values.put(NGINX_IMAGE_TAG, nginxImageTag);
    values.put(NGINX_IMAGE_DIGEST, nginxImageDigest);    
    if (imageRepoSecret != null) {
      values.put(IMAGE_PULL_SECRET, imageRepoSecret);
    }
    values.put(IP_FAMILY_POLICY, ipFamilyPolicy);
    values.put(IP_FAMILIES, ipFamilies);
    values.put(TYPE, type);
    values.values().removeIf(Objects::isNull);
    return values;
  }
}

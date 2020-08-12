// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * All parameters needed to install Apache ingress controller.
 */
public class ApacheParams {

  // Only add the values which need to be updated here.
  // The default values can be found here:
  // weblogic-kubernetes-operator/kubernetes/samples/charts/apache-webtier/values.yaml
  private String image = null;
  private String imagePullSecretsName = null;
  private String volumePath = null;
  private int httpNodePort = 0;
  private int httpsNodePort = 0;
  private String virtualHostName = null;
  private String customCert = null;
  private String customKey = null;
  private String domainUID = null;
  private HelmParams helmParams;

  public ApacheParams image(String image) {
    this.image = image;
    return this;
  }

  public ApacheParams imagePullSecretsName(String imagePullSecretsName) {
    this.imagePullSecretsName = imagePullSecretsName;
    return this;
  }

  public ApacheParams volumePath(String volumePath) {
    this.volumePath = volumePath;
    return this;
  }

  public ApacheParams httpNodePort(int httpNodePort) {
    this.httpNodePort = httpNodePort;
    return this;
  }

  public ApacheParams httpsNodePort(int httpsNodePort) {
    this.httpsNodePort = httpsNodePort;
    return this;
  }

  public ApacheParams virtualHostName(String virtualHostName) {
    this.virtualHostName = virtualHostName;
    return this;
  }

  public ApacheParams customCert(String customCert) {
    this.customCert = customCert;
    return this;
  }

  public ApacheParams customKey(String customKey) {
    this.customKey = customKey;
    return this;
  }

  public ApacheParams domainUID(String domainUID) {
    this.domainUID = domainUID;
    return this;
  }

  public ApacheParams helmParams(HelmParams helmParams) {
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

    values.put("image", image);
    values.put("imagePullSecrets.name", imagePullSecretsName);
    values.put("volumePath", volumePath);

    if (httpNodePort >= 0) {
      values.put("httpNodePort", httpNodePort);
    }
    if (httpsNodePort >= 0) {
      values.put("httpsNodePort", httpsNodePort);
    }

    values.put("virtualHostName", virtualHostName);
    values.put("customCert", customCert);
    values.put("customKey", customKey);
    values.put("domainUID", domainUID);

    values.values().removeIf(Objects::isNull);
    return values;
  }
}

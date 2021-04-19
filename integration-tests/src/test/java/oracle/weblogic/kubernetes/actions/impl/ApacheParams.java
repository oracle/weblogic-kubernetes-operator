// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

/**
 * All parameters needed to install Apache load balancer.
 */
public class ApacheParams {

  // The default values can be found here:
  // weblogic-kubernetes-operator/kubernetes/samples/charts/apache-webtier/values.yaml
  private static final String IMAGE = "image";
  private static final String IMAGE_PULL_POLICY = "imagePullPolicy";
  private static final String IMAGE_PULL_SECRETS = "imagePullSecrets";
  private static final String PVC_NAME = "persistentVolumeClaimName";
  private static final String HTTP_NODEPORT = "httpNodePort";
  private static final String HTTPS_NODEPORT = "httpsNodePort";
  private static final String MANAGED_SERVER_PORT = "managedServerPort";
  private static final String VIRTUAL_HOSTNAME = "virtualHostName";
  private static final String CUSTOM_CERT = "customCert";
  private static final String CUSTOM_KEY = "customKey";
  private static final String DOMAIN_UID = "domainUID";

  private String image = null;
  private String imagePullPolicy = null;
  private Map<String, Object> imagePullSecrets = null;
  private String pvcName = null;
  private int httpNodePort = 0;
  private int httpsNodePort = 0;
  private int managedServerPort = 0;
  private String virtualHostName = null;
  private String customCert = null;
  private String customKey = null;
  private String domainUID = null;
  private HelmParams helmParams;

  public ApacheParams image(String image) {
    this.image = image;
    return this;
  }

  public ApacheParams imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public ApacheParams imagePullSecrets(Map<String, Object> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
    return this;
  }

  public ApacheParams pvcName(String pvcName) {
    this.pvcName = pvcName;
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

  public ApacheParams managedServerPort(int managedServerPort) {
    this.managedServerPort = managedServerPort;
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

    values.put(IMAGE, image);
    values.put(IMAGE_PULL_POLICY, imagePullPolicy);
    values.put(IMAGE_PULL_SECRETS, imagePullSecrets);
    values.put(PVC_NAME, pvcName);

    if (httpNodePort >= 0) {
      values.put(HTTP_NODEPORT, httpNodePort);
    }
    if (httpsNodePort >= 0) {
      values.put(HTTPS_NODEPORT, httpsNodePort);
    }
    if (managedServerPort >= 0) {
      values.put(MANAGED_SERVER_PORT, managedServerPort);
    }

    values.put(VIRTUAL_HOSTNAME, virtualHostName);
    values.put(CUSTOM_CERT, customCert);
    values.put(CUSTOM_KEY, customKey);
    values.put(DOMAIN_UID, domainUID);

    values.values().removeIf(Objects::isNull);
    return values;
  }
}

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;

// All parameters needed to install Operator from test

public class OperatorParams {

  private static final String DOMAIN_NAMESPACES = "domainNamespaces";
  private static final String IMAGE = "image";
  private static final String SERVICE_ACCOUNT = "serviceAccount";
  private static final String EXTERNAL_REST_ENABLED = "externalRestEnabled";
  private static final String EXTERNAL_REST_IDENTITY_SECRET = "externalRestIdentitySecret";
  private static final String EXTERNAL_REST_HTTPS_PORT = "externalRestHttpsPort";
  private static final String IMAGE_PULL_POLICY = "imagePullPolicy";
  private static final String IMAGE_PULL_SECRETS = "imagePullSecrets";

  // Adding some of the most commonly used params for now
  private List<String> domainNamespaces;
  private String image;
  private String serviceAccount;
  private boolean externalRestEnabled;
  private String externalRestIdentitySecret;
  private int externalRestHttpsPort = 0;
  private String imagePullPolicy;
  private Map<String, Object> imagePullSecrets;
  private HelmParams helmParams;

  public OperatorParams domainNamespaces(List<String> domainNamespaces) {
    this.domainNamespaces = domainNamespaces;
    return this;
  }

  public OperatorParams image(String image) {
    this.image = image;
    return this;
  }

  public OperatorParams serviceAccount(String serviceAccount) {
    this.serviceAccount = serviceAccount;
    return this;
  }

  public OperatorParams externalRestEnabled(boolean externalRestEnabled) {
    this.externalRestEnabled = externalRestEnabled;
    return this;
  }

  public OperatorParams externalRestHttpsPort(int externalRestHttpsPort) {
    this.externalRestHttpsPort = externalRestHttpsPort;
    return this;
  }

  public OperatorParams imagePullPolicy(String imagePullPolicy) {
    this.imagePullPolicy = imagePullPolicy;
    return this;
  }

  public OperatorParams imagePullSecrets(Map<String, Object> imagePullSecrets) {
    this.imagePullSecrets = imagePullSecrets;
    return this;
  }

  public OperatorParams externalRestIdentitySecret(String externalRestIdentitySecret) {
    this.externalRestIdentitySecret = externalRestIdentitySecret;
    return this;
  }

  public OperatorParams helmParams(HelmParams helmParams) {
    this.helmParams = helmParams;
    return this;
  }

  public String getServiceAccount() {
    return serviceAccount;
  }

  public HelmParams getHelmParams() {
    return helmParams;
  }

  /**
   * Loads Helm values into a value map.
   * @return Map of values
   */
  public Map<String, Object> getValues() {
    Map<String, Object> values = new HashMap<>();
    values.put(DOMAIN_NAMESPACES, domainNamespaces);
    values.put(IMAGE, image);
    values.put(SERVICE_ACCOUNT, serviceAccount);
    values.put(EXTERNAL_REST_ENABLED, Boolean.valueOf(externalRestEnabled));
    if (externalRestHttpsPort > 0) {
      values.put(EXTERNAL_REST_HTTPS_PORT, Integer.valueOf(externalRestHttpsPort));
    }
    values.put(EXTERNAL_REST_IDENTITY_SECRET, externalRestIdentitySecret);
    values.put(IMAGE_PULL_POLICY, imagePullPolicy);
    values.put(IMAGE_PULL_SECRETS, imagePullSecrets);
    values.values().removeIf(Objects::isNull);
    return values;
  }
}
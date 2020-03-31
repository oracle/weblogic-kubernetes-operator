// Copyright 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl;

import java.util.HashMap;
import java.util.List;

// All parameters needed to install Operator from test

public class OperatorParams {

  // Adding some of the most commonly used params for now
  private String releaseName;
  private String namespace;
  private List<String> domainNamespaces;
  private String image;
  private String serviceAccount;
  private boolean externalRestEnabled;
  private int externalRestHttpsPort;

  public OperatorParams releaseName(String releaseName) {
    this.releaseName = releaseName;
    return this;
  }

  public OperatorParams namespace(String namespace) {
    this.namespace = namespace;
    return this;
  }

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

  public String getReleaseName() {
    return releaseName;
  }

  public String getNamespace() {
    return namespace;
  }

  public HashMap<String, String> values() {
    // add all params into map ?
    return new HashMap<String, String>();
  }
}
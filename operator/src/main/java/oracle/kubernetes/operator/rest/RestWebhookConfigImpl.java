// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import oracle.kubernetes.operator.utils.Certificates;

/** RestConfigImpl provides the WebLogic Operator REST api configuration. */
public class RestWebhookConfigImpl implements RestWebhookConfig {

  public static final int INTERNAL_HTTPS_PORT = 8084;

  /**
   * Constructs a RestConfigImpl.
   */
  public RestWebhookConfigImpl() {
  }

  @Override
  public String getHost() {
    return "0.0.0.0";
  }

  @Override
  public int getExternalHttpsPort() {
    return 8083;
  }

  @Override
  public int getInternalHttpsPort() {
    return INTERNAL_HTTPS_PORT;
  }

  @Override
  public String getOperatorExternalCertificateData() {
    return Certificates.getOperatorExternalCertificateData();
  }

  @Override
  public String getOperatorInternalCertificateData() {
    return Certificates.getOperatorInternalCertificateData();
  }

  @Override
  public String getOperatorExternalCertificateFile() {
    return null;
  }

  @Override
  public String getOperatorInternalCertificateFile() {
    return null;
  }

  @Override
  public String getOperatorExternalKeyData() {
    return null;
  }

  @Override
  public String getOperatorInternalKeyData() {
    return null;
  }

  @Override
  public String getOperatorExternalKeyFile() {
    return Certificates.getOperatorExternalKeyFile();
  }

  @Override
  public String getOperatorInternalKeyFile() {
    return Certificates.getOperatorInternalKeyFile();
  }

}

// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import oracle.kubernetes.operator.utils.Certificates;

/** RestConfigImpl provides the REST api configuration for Webhook for WebLogic Operator. */
public class RestWebhookConfigImpl implements RestWebhookConfig {

  public static final int HTTPS_PORT = 8084;

  /**
   * Constructs a RestWebhookConfigImpl.
   */
  public RestWebhookConfigImpl() {
  }

  @Override
  public String getHost() {
    return "0.0.0.0";
  }

  @Override
  public int getHttpsPort() {
    return HTTPS_PORT;
  }

  @Override
  public String getWebhookCertificateData() {
    return Certificates.getWebhookCertificateData();
  }

  @Override
  public String getWebhookCertificateFile() {
    return null;
  }

  @Override
  public String getWebhookKeyData() {
    return null;
  }

  @Override
  public String getWebhookKeyFile() {
    return Certificates.getWebhookKeyFile();
  }

}

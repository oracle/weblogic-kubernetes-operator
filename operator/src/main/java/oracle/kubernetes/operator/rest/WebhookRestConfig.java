// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

/**
 * The RestWebhookConfig interface is used to pass the WebLogic Operator's WebHook REST configuration to the
 * RestWebhookServer.
 */
public interface WebhookRestConfig {

  /**
   * Gets the in-pod hostname of the WebLogic operator webhook REST api.
   *
   * @return the in-pod hostname
   */
  String getHost();

  /**
   * Gets the https port's in-pod port number.
   *
   * @return the port number
   */
  int getHttpsPort();

  /**
   * Gets the https port's certificate.
   *
   * @return base64 encoded PEM containing the certificate, or null if
   *     getWebhookCertificateFile should be used instead to get the certificate.
   */
  String getWebhookCertificateData();

  /**
   * Gets internal https port's certificate.
   *
   * @return the pathname of a PEM file containing the certificate or null if
   *     getWebhookCertificateData should be used instead to get the certificate.
   */
  String getWebhookCertificateFile();

  /**
   * Gets the internal https port's private key.
   *
   * @return base64 encoded PEM containing the private key, or null if getWebhookKeyFile
   *     should be used instead to get the private key.
   */
  String getWebhookKeyData();

  /**
   * Gets internal https port's private key.
   *
   * @return the pathname of a PEM file containing the private key or null if
   *     getWebhookKeyData should be used instead to get the private key.
   */
  String getWebhookKeyFile();

}

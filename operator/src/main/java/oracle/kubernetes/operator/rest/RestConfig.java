// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import oracle.kubernetes.operator.rest.backend.RestBackend;

/**
 * The RestConfig interface is used to pass the WebLogic Operator's REST configuration to the
 * RestServer.
 */
public interface RestConfig {

  /**
   * This constant is used internally to pass the RestConfig instance from the RestServer to the
   * filters and resources so that they can access it.
   */
  public static final String REST_CONFIG_PROPERTY = "RestConfig";

  /**
   * Gets the in-pod hostname of the WebLogic operator REST api.
   *
   * @return the in-pod hostname
   */
  String getHost();

  /**
   * Gets the external https port's in-pod port number.
   *
   * @return the port number
   */
  int getExternalHttpsPort();

  /**
   * Gets the internal https port's in-pod port number.
   *
   * @return the port number
   */
  int getInternalHttpsPort();

  /**
   * Gets the external https port's certificate.
   *
   * @return base64 encoded PEM containing the certificate, or null if
   *     getOperatorExternalCertificateFile should be used instead to get the certificate.
   */
  String getOperatorExternalCertificateData();

  /**
   * Gets the internal https port's certificate.
   *
   * @return base64 encoded PEM containing the certificate, or null if
   *     getOperatorInternalCertificateFile should be used instead to get the certificate.
   */
  String getOperatorInternalCertificateData();

  /**
   * Gets external https port's certificate.
   *
   * @return the pathname of a PEM file containing the certificate or null if
   *     getOperatorExternalCertificateData should be used instead to get the certificate.
   */
  String getOperatorExternalCertificateFile();

  /**
   * Gets internal https port's certificate.
   *
   * @return the pathname of a PEM file containing the certificate or null if
   *     getOperatorInternalCertificateData should be used instead to get the certificate.
   */
  String getOperatorInternalCertificateFile();

  /**
   * Gets the external https port's private key.
   *
   * @return base64 encoded PEM containing the private key, or null if getOperatorExternalKeyFile
   *     should be used instead to get the private key.
   */
  String getOperatorExternalKeyData();

  /**
   * Gets the internal https port's private key.
   *
   * @return base64 encoded PEM containing the private key, or null if getOperatorInternalKeyFile
   *     should be used instead to get the private key.
   */
  String getOperatorInternalKeyData();

  /**
   * Gets external https port's private key.
   *
   * @return the pathname of a PEM file containing the private key or null if
   *     getOperatorExternalKeyData should be used instead to get the private key.
   */
  String getOperatorExternalKeyFile();

  /**
   * Gets internal https port's private key.
   *
   * @return the pathname of a PEM file containing the private key or null if
   *     getOperatorInternalKeyData should be used instead to get the private key.
   */
  String getOperatorInternalKeyFile();

  /**
   * Gets a RestBackend instance that does the real work behind a single WebLogic Operator REST api
   * request.
   *
   * @param accessToken contains the Kubernetes service account token that should be used to
   *     authenticate and authorize this request.
   * @return a RestBackend instance that can be used to process this request (but not other
   *     requests).
   */
  RestBackend getBackend(String accessToken);
}

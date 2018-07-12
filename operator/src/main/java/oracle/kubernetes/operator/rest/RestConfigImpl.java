// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.backend.RestBackend;

/** RestConfigImpl provides the WebLogic Operator REST api configuration. */
public class RestConfigImpl implements RestConfig {

  private static LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String principal;
  private final Collection<String> targetNamespaces;

  /**
   * Constructs a RestConfigImpl.
   *
   * @param principal is the name of the Kubernetes User or Service Account to use when calling the
   *     Kubernetes REST API.
   * @param targetNamespaces is a list of the Kubernetes Namespaces covered by this Operator.
   */
  public RestConfigImpl(String principal, Collection<String> targetNamespaces) {
    LOGGER.entering(principal, targetNamespaces);
    this.principal = principal;
    this.targetNamespaces = targetNamespaces;
    LOGGER.exiting();
  }

  /** {@inheritDoc} */
  @Override
  public String getHost() {
    return "0.0.0.0";
  }

  /** {@inheritDoc} */
  @Override
  public int getExternalHttpsPort() {
    return 8081;
  }

  /** {@inheritDoc} */
  @Override
  public int getInternalHttpsPort() {
    return 8082;
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatorExternalCertificateData() {
    return getCertificate("externalOperatorCert");
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatorInternalCertificateData() {
    return getCertificate("internalOperatorCert");
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatorExternalCertificateFile() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatorInternalCertificateFile() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatorExternalKeyData() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatorInternalKeyData() {
    return null;
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatorExternalKeyFile() {
    return getKey("externalOperatorKey");
  }

  /** {@inheritDoc} */
  @Override
  public String getOperatorInternalKeyFile() {
    return getKey("internalOperatorKey");
  }

  /** {@inheritDoc} */
  @Override
  public RestBackend getBackend(String accessToken) {
    LOGGER.entering();
    RestBackend result = new RestBackendImpl(principal, accessToken, targetNamespaces);
    LOGGER.exiting();
    return result;
  }

  private String getCertificate(String property) {
    LOGGER.entering(property);
    String path =
        "config/"
            + property; // a file containing a base64 encoded string containing the operator's cert
    // in pem format
    String result = null;
    if (checkFileExists(path)) {
      try {
        result = new String(Files.readAllBytes(Paths.get(path)));
      } catch (Throwable t) {
        LOGGER.warning("Can't read " + path, t);
      }
    }
    // do not include the certificate data in the log message
    LOGGER.exiting();
    return result;
  }

  private String getKey(String property) {
    LOGGER.entering(property);
    String path = "secrets/" + property; // a pem file containing the operator's private key
    if (!checkFileExists(path)) {
      path = null;
    }
    LOGGER.exiting(path);
    return path;
  }

  private boolean checkFileExists(String path) {
    LOGGER.entering(path);
    File f = new File(path);
    boolean result = false;
    if (f.exists()) {
      if (f.isFile()) {
        result = true;
      } else {
        LOGGER.warning(path + " is not a file");
      }
    } else {
      LOGGER.warning(path + " does not exist");
    }
    LOGGER.exiting(result);
    return result;
  }
}

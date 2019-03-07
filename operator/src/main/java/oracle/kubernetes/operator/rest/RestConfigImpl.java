// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.Collection;
import oracle.kubernetes.operator.OperatorConstants;
import oracle.kubernetes.operator.TuningParameters;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.backend.RestBackend;

/** RestConfigImpl provides the WebLogic Operator REST api configuration. */
public class RestConfigImpl implements RestConfig, OperatorConstants {

  private static LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  private final String principal;
  private final Collection<String> targetNamespaces;

  private static final String INTERNAL_CERTIFICATE_KEY =
      INTERNAL_REST_IDENTITY_DIR + "internalOperatorKey";
  private static final String EXTERNAL_REST_IDENTITY_DIR = OPERATOR_DIR + "external-identity/";
  private static final String EXTERNAL_CERTIFICATE =
      EXTERNAL_REST_IDENTITY_DIR + "externalOperatorCert";
  private static final String EXTERNAL_CERTIFICATE_KEY =
      EXTERNAL_REST_IDENTITY_DIR + "externalOperatorKey";

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

  @Override
  public String getHost() {
    return "0.0.0.0";
  }

  @Override
  public int getExternalHttpsPort() {
    return 8081;
  }

  @Override
  public int getInternalHttpsPort() {
    return 8082;
  }

  @Override
  public String getOperatorExternalCertificateData() {
    return TuningParameters.getInstance().getFileContents(EXTERNAL_CERTIFICATE);
  }

  @Override
  public String getOperatorInternalCertificateData() {
    return TuningParameters.getInstance().getFileContents(INTERNAL_CERTIFICATE);
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
    return getKey(EXTERNAL_CERTIFICATE_KEY);
  }

  @Override
  public String getOperatorInternalKeyFile() {
    return getKey(INTERNAL_CERTIFICATE_KEY);
  }

  @Override
  public RestBackend getBackend(String accessToken) {
    LOGGER.entering();
    RestBackend result = new RestBackendImpl(principal, accessToken, targetNamespaces);
    LOGGER.exiting();
    return result;
  }

  // path - a file containing the operator's private key in pem format (cleartext)
  private String getKey(String path) {
    LOGGER.entering(path);
    if (!TuningParameters.getInstance().checkFileExists(path)) {
      path = null;
    }
    LOGGER.exiting(path);
    return path;
  }
}

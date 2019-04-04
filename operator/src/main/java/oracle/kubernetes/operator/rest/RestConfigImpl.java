// Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.Collection;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.utils.Certificates;

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

  @Override
  public RestBackend getBackend(String accessToken) {
    LOGGER.entering();
    RestBackend result = new RestBackendImpl(principal, accessToken, targetNamespaces);
    LOGGER.exiting();
    return result;
  }
}

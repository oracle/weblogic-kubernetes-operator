// Copyright (c) 2017, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.rest;

import java.util.Collection;
import java.util.function.Supplier;

import oracle.kubernetes.operator.RestServerType;
import oracle.kubernetes.operator.rest.backend.RestBackend;
import oracle.kubernetes.operator.utils.Certificates;
import oracle.kubernetes.operator.utils.WebhookCertificates;

import static oracle.kubernetes.operator.RestServerType.Operator;

/** RestConfigImpl provides the WebLogic Operator REST api configuration. */
public class RestConfigImpl implements RestConfig {

  public static final Integer CONVERSION_WEBHOOK_HTTPS_PORT = 8084;

  private final String principal;
  private final Supplier<Collection<String>> domainNamespaces;
  private final Certificates certificates;
  private final RestServerType restServerType;

  public RestConfigImpl(RestServerType restServerType) {
    this(null, null, null, restServerType);
  }

  public RestConfigImpl(String principal, Supplier<Collection<String>> domainNamespaces, Certificates certificates) {
    this(principal, domainNamespaces, certificates, Operator);
  }

  /**
   * Constructs a RestConfigImpl.
   *  @param principal is the name of the Kubernetes User or Service Account to use when calling the
   *     Kubernetes REST API.
   * @param domainNamespaces returns a list of the Kubernetes Namespaces covered by this Operator.
   * @param certificates Certificates
   */
  public RestConfigImpl(String principal, Supplier<Collection<String>> domainNamespaces, Certificates certificates,
                        RestServerType restServerType) {
    this.domainNamespaces = domainNamespaces;
    this.principal = principal;
    this.certificates = certificates;
    this.restServerType = restServerType;
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
  public int getWebhookHttpsPort() {
    return CONVERSION_WEBHOOK_HTTPS_PORT;
  }

  @Override
  public String getOperatorExternalCertificateData() {
    return certificates.getOperatorExternalCertificateData();
  }

  @Override
  public String getOperatorInternalCertificateData() {
    return certificates.getOperatorInternalCertificateData();
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
    return certificates.getOperatorExternalKeyFilePath();
  }

  @Override
  public String getOperatorInternalKeyFile() {
    return certificates.getOperatorInternalKeyFilePath();
  }

  @Override
  public RestBackend getBackend(String accessToken) {
    return new RestBackendImpl(principal, accessToken, domainNamespaces);
  }

  @Override
  public RestServerType getRestServerType() {
    return restServerType;
  }

  @Override
  public String getWebhookCertificateData() {
    return WebhookCertificates.getWebhookCertificateData();
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
    return WebhookCertificates.getWebhookKeyFile();
  }
}

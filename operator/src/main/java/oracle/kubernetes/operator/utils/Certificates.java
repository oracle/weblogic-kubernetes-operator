// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import oracle.kubernetes.operator.CoreDelegate;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

import static oracle.kubernetes.common.logging.MessageKeys.NO_EXTERNAL_CERTIFICATE;
import static oracle.kubernetes.common.logging.MessageKeys.NO_INTERNAL_CERTIFICATE;
import static oracle.kubernetes.common.logging.MessageKeys.NO_WEBHOOK_CERTIFICATE;

public class Certificates {
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Function<String, Path> getPath = Paths::get;

  final File externalCertificateKey;
  final File externalCertificate;
  public final File internalCertificateKey;
  public final File internalCertificate;
  private final File webhookCertificateKey;
  private final File webhookCertificate;

  /**
   * Initialize certificates utility with core delegate.
   * @param delegate Core delegate
   */
  public Certificates(CoreDelegate delegate) {
    File externalIdDir = new File(delegate.getDeploymentHome(), "external-identity");
    externalCertificateKey = new File(externalIdDir, "externalOperatorKey");
    externalCertificate = new File(externalIdDir, "externalOperatorCert");
    File internalIdDir = new File(delegate.getDeploymentHome(), "internal-identity");
    internalCertificateKey = new File(internalIdDir, "internalOperatorKey");
    internalCertificate = new File(internalIdDir, "internalOperatorCert");
    File webhookIdDir = new File(delegate.getDeploymentHome(), "webhook-identity");
    webhookCertificateKey = new File(webhookIdDir, "webhookKey");
    webhookCertificate  = new File(webhookIdDir, "webhookCert");
  }

  public String getOperatorExternalKeyFilePath() {
    return getKeyOrNull(externalCertificateKey.getAbsolutePath());
  }

  public String getOperatorInternalKeyFilePath() {
    return getKeyOrNull(internalCertificateKey.getAbsolutePath());
  }

  public File getOperatorInternalKeyFile() {
    return internalCertificateKey;
  }

  private String getKeyOrNull(String path) {
    return isFileExists(getPath.apply(path)) ? path : null;
  }

  public String getOperatorExternalCertificateData() {
    return getCertificate(externalCertificate.getAbsolutePath(), NO_EXTERNAL_CERTIFICATE);
  }

  public String getOperatorInternalCertificateData() {
    return getCertificate(internalCertificate.getAbsolutePath(), NO_INTERNAL_CERTIFICATE);
  }

  public File getOperatorInternalCertificateFile() {
    return internalCertificate;
  }

  public File getWebhookKeyFile() {
    return webhookCertificateKey;
  }

  public String getWebhookKeyFilePath() {
    return getKeyOrNull(webhookCertificateKey.getAbsolutePath());
  }

  public String getWebhookCertificateData() {
    return getCertificate(webhookCertificate.getAbsolutePath(), NO_WEBHOOK_CERTIFICATE);
  }

  public File getWebhookCertificateFile() {
    return webhookCertificate;
  }

  private static String getCertificate(String path, String failureMessage) {
    try {
      return new String(Files.readAllBytes(getPath.apply(path)));
    } catch (IOException e) {
      LOGGER.config(failureMessage, path + " due to: " + e.getMessage());
      return null;
    }
  }

  private static boolean isFileExists(Path path) {
    return Files.isRegularFile(path);
  }
}

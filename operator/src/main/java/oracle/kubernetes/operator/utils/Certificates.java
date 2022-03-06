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

import static oracle.kubernetes.operator.logging.MessageKeys.NO_EXTERNAL_CERTIFICATE;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_INTERNAL_CERTIFICATE;

public class Certificates {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Function<String, Path> GET_PATH = Paths::get;

  private final File externalIdDir;
  final File externalCertificateKey;
  final File externalCertificate;
  private final File internalIdDir;
  public final File internalCertificateKey;
  public final File internalCertificate;

  /**
   * Initialize certificates utility with core delegate.
   * @param delegate Core delegate
   */
  public Certificates(CoreDelegate delegate) {
    externalIdDir = new File(delegate.getOperatorHome(), "external-identity");
    externalCertificateKey = new File(externalIdDir, "externalOperatorKey");
    externalCertificate = new File(externalIdDir, "externalOperatorCert");
    internalIdDir = new File(delegate.getOperatorHome(), "internal-identity");
    internalCertificateKey = new File(internalIdDir, "internalOperatorKey");
    internalCertificate = new File(internalIdDir, "internalOperatorCert");
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
    return isFileExists(GET_PATH.apply(path)) ? path : null;
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

  private static String getCertificate(String path, String failureMessage) {
    try {
      return new String(Files.readAllBytes(GET_PATH.apply(path)));
    } catch (IOException e) {
      LOGGER.config(failureMessage, path);
      return null;
    }
  }

  private static boolean isFileExists(Path path) {
    return Files.isRegularFile(path);
  }
}

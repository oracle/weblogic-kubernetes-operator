// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

import static oracle.kubernetes.operator.logging.MessageKeys.NO_EXTERNAL_CERTIFICATE;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_INTERNAL_CERTIFICATE;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_WEBHOOK_CERTIFICATE;

public class Certificates {
  public static final String OPERATOR_DIR = "/operator/";
  public static final String WEBHOOK_DIR = "/webhook/";
  private static final String EXTERNAL_ID_DIR = OPERATOR_DIR + "external-identity/";
  static final String EXTERNAL_CERTIFICATE_KEY = EXTERNAL_ID_DIR + "externalOperatorKey";
  static final String EXTERNAL_CERTIFICATE = EXTERNAL_ID_DIR + "externalOperatorCert";
  private static final String INTERNAL_ID_DIR = OPERATOR_DIR + "internal-identity/";
  public static final String INTERNAL_CERTIFICATE_KEY = INTERNAL_ID_DIR + "internalOperatorKey";
  public static final String INTERNAL_CERTIFICATE = INTERNAL_ID_DIR + "internalOperatorCert";
  public static final String WEBHOOK_CERTIFICATE_KEY = INTERNAL_ID_DIR + "webhookKey";
  public static final String WEBHOOK_CERTIFICATE = INTERNAL_ID_DIR + "webhookCert";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Function<String, Path> GET_PATH = Paths::get;

  public static String getOperatorExternalKeyFile() {
    return getKeyOrNull(Certificates.EXTERNAL_CERTIFICATE_KEY);
  }

  public static String getOperatorInternalKeyFile() {
    return getKeyOrNull(INTERNAL_CERTIFICATE_KEY);
  }

  public static String getWebhookKeyFile() {
    return getKeyOrNull(WEBHOOK_CERTIFICATE_KEY);
  }

  private static String getKeyOrNull(String path) {
    return isFileExists(GET_PATH.apply(path)) ? path : null;
  }

  public static String getOperatorExternalCertificateData() {
    return getCertificate(Certificates.EXTERNAL_CERTIFICATE, NO_EXTERNAL_CERTIFICATE);
  }

  public static String getOperatorInternalCertificateData() {
    return getCertificate(INTERNAL_CERTIFICATE, NO_INTERNAL_CERTIFICATE);
  }

  public static String getWebhookCertificateData() {
    return getCertificate(WEBHOOK_CERTIFICATE, NO_WEBHOOK_CERTIFICATE);
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

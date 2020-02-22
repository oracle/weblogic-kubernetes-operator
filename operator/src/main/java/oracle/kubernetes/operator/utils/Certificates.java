// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
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

public class Certificates {
  private static final String OPERATOR_DIR = "/operator/";
  private static final String EXTERNAL_ID_DIR = OPERATOR_DIR + "external-identity/";
  static final String EXTERNAL_CERTIFICATE_KEY = EXTERNAL_ID_DIR + "externalOperatorKey";
  static final String EXTERNAL_CERTIFICATE = EXTERNAL_ID_DIR + "externalOperatorCert";
  private static final String INTERNAL_ID_DIR = OPERATOR_DIR + "internal-identity/";
  static final String INTERNAL_CERTIFICATE_KEY = INTERNAL_ID_DIR + "internalOperatorKey";
  static final String INTERNAL_CERTIFICATE = INTERNAL_ID_DIR + "internalOperatorCert";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");
  private static Function<String, Path> GET_PATH = p -> Paths.get(p);

  public static String getOperatorExternalKeyFile() {
    return getKeyOrNull(Certificates.EXTERNAL_CERTIFICATE_KEY);
  }

  public static String getOperatorInternalKeyFile() {
    return getKeyOrNull(Certificates.INTERNAL_CERTIFICATE_KEY);
  }

  private static String getKeyOrNull(String path) {
    return isFileExists(GET_PATH.apply(path)) ? path : null;
  }

  public static String getOperatorExternalCertificateData() {
    return getCertificate(Certificates.EXTERNAL_CERTIFICATE, NO_EXTERNAL_CERTIFICATE);
  }

  public static String getOperatorInternalCertificateData() {
    return getCertificate(Certificates.INTERNAL_CERTIFICATE, NO_INTERNAL_CERTIFICATE);
  }

  private static String getCertificate(String path, String failureMessage) {
    try {
      return new String(Files.readAllBytes(GET_PATH.apply(path)));
    } catch (IOException e) {
      LOGGER.info(failureMessage, path);
      return null;
    }
  }

  private static boolean isFileExists(Path path) {
    return Files.isRegularFile(path);
  }
}

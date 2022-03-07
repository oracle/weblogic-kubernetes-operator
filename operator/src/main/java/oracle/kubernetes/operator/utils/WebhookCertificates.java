// Copyright (c) 2019, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;

import static oracle.kubernetes.operator.logging.MessageKeys.NO_WEBHOOK_CERTIFICATE;

public class WebhookCertificates {
  public static final String WEBHOOK_DIR = "/webhook/";
  public static final String WEBHOOK_CERTIFICATE_KEY = WEBHOOK_DIR + "webhookKey";
  public static final String WEBHOOK_CERTIFICATE = WEBHOOK_DIR + "webhookCert";
  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Webhook", "Operator");
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Function<String, Path> GET_PATH = Paths::get;

  public static String getWebhookKeyFile() {
    return getKeyOrNull(WEBHOOK_CERTIFICATE_KEY);
  }

  private static String getKeyOrNull(String path) {
    return isFileExists(GET_PATH.apply(path)) ? path : null;
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

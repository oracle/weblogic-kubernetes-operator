// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.TestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static oracle.kubernetes.operator.logging.MessageKeys.NO_EXTERNAL_CERTIFICATE;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_INTERNAL_CERTIFICATE;
import static oracle.kubernetes.utils.LogMatcher.containsWarning;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CertificatesTest {

  private final TestUtils.ConsoleHandlerMemento consoleHandlerMemento = TestUtils.silenceOperatorLogger();
  private final Collection<LogRecord> logRecords = new ArrayList<>();
  private final List<Memento> mementos = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    mementos.add(consoleHandlerMemento
          .collectLogMessages(logRecords, NO_INTERNAL_CERTIFICATE, NO_EXTERNAL_CERTIFICATE)
          .withLogLevel(Level.FINE));
    mementos.add(InMemoryCertificates.installWithoutData());
  }

  @AfterEach
  public void tearDown() {
    mementos.forEach(Memento::revert);
  }

  @Test
  public void whenNoExternalKeyFile_returnNull() {
    assertThat(Certificates.getOperatorExternalKeyFile(), nullValue());
  }

  @Test
  public void whenExternalKeyFileDefined_returnPath() {
    InMemoryCertificates.defineOperatorExternalKeyFile("asdf");

    assertThat(
        Certificates.getOperatorExternalKeyFile(), equalTo(Certificates.EXTERNAL_CERTIFICATE_KEY));
  }

  @Test
  public void whenNoInternalKeyFile_returnNull() {
    assertThat(Certificates.getOperatorInternalKeyFile(), nullValue());
  }

  @Test
  public void whenInternalKeyFileDefined_returnPath() {
    InMemoryCertificates.defineOperatorInternalKeyFile("asdf");

    assertThat(
        Certificates.getOperatorInternalKeyFile(), equalTo(Certificates.INTERNAL_CERTIFICATE_KEY));
  }

  @Test
  public void whenNoExternalCertificateFile_returnNull() {
    consoleHandlerMemento.ignoreMessage(NO_EXTERNAL_CERTIFICATE);

    assertThat(Certificates.getOperatorExternalCertificateData(), nullValue());
  }

  @Test
  public void whenNoExternalCertificateFile_logWarningMessage() {
    assertThat(Certificates.getOperatorExternalCertificateData(), nullValue());

    assertThat(logRecords, containsWarning(NO_EXTERNAL_CERTIFICATE));
  }

  @Test
  public void whenExternalCertificateFileDefined_returnData() {
    InMemoryCertificates.defineOperatorExternalCertificateFile("asdf");

    assertThat(Certificates.getOperatorExternalCertificateData(), notNullValue());
  }

  @Test
  public void whenNoInternalCertificateFile_returnNull() {
    consoleHandlerMemento.ignoreMessage(NO_INTERNAL_CERTIFICATE);

    assertThat(Certificates.getOperatorInternalCertificateData(), nullValue());
  }

  @Test
  public void whenNoInternalCertificateFile_logWarningMessage() {
    Certificates.getOperatorInternalCertificateData();

    assertThat(logRecords, containsWarning(NO_INTERNAL_CERTIFICATE));
  }

  @Test
  public void whenInternalCertificateFileDefined_returnPath() {
    InMemoryCertificates.defineOperatorInternalCertificateFile("asdf");

    assertThat(Certificates.getOperatorInternalCertificateData(), notNullValue());
  }
}

// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import com.meterware.simplestub.Memento;
import oracle.kubernetes.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static oracle.kubernetes.operator.logging.MessageKeys.NO_EXTERNAL_CERTIFICATE;
import static oracle.kubernetes.operator.logging.MessageKeys.NO_INTERNAL_CERTIFICATE;
import static oracle.kubernetes.utils.LogMatcher.containsInfo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

public class CertificatesTest {

  private final TestUtils.ConsoleHandlerMemento consoleHandlerMemento = TestUtils.silenceOperatorLogger();
  private Collection<LogRecord> logRecords = new ArrayList<>();
  private List<Memento> mementos = new ArrayList<>();

  /**
   * Setup test environment.
   * @throws Exception if test support fails to install.
   */
  @Before
  public void setUp() throws Exception {
    mementos.add(consoleHandlerMemento
          .collectLogMessages(logRecords, NO_INTERNAL_CERTIFICATE, NO_EXTERNAL_CERTIFICATE)
          .withLogLevel(Level.INFO));
    mementos.add(InMemoryCertificates.installWithoutData());
  }

  /**
   * Cleanup test environment.
   */
  @After
  public void tearDown() {
    for (Memento memento : mementos) {
      memento.revert();
    }
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
  public void whenNoExternalCertificateFile_logInfoMessage() {
    assertThat(Certificates.getOperatorExternalCertificateData(), nullValue());

    assertThat(logRecords, containsInfo(NO_EXTERNAL_CERTIFICATE));
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
  public void whenNoInternalCertificateFile_logInfoMessage() {
    Certificates.getOperatorInternalCertificateData();

    assertThat(logRecords, containsInfo(NO_INTERNAL_CERTIFICATE));
  }

  @Test
  public void whenInternalCertificateFileDefined_returnPath() {
    InMemoryCertificates.defineOperatorInternalCertificateFile("asdf");

    assertThat(Certificates.getOperatorInternalCertificateData(), notNullValue());
  }
}

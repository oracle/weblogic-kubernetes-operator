// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.junit.MatcherAssert.assertThat;

import com.meterware.simplestub.Memento;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import oracle.kubernetes.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CertificatesTest {
  private List<Memento> mementos = new ArrayList<>();

  @Before
  public void setUp() throws Exception {
    mementos.add(
        TestUtils.silenceOperatorLogger().ignoringLoggedExceptions(FileNotFoundException.class));
    mementos.add(InMemoryCertificates.installWithoutData());
  }

  @After
  public void tearDown() {
    for (Memento memento : mementos) memento.revert();
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
    assertThat(Certificates.getOperatorExternalCertificateData(), nullValue());
  }

  @Test
  public void whenExternalCertificateFileDefined_returnData() {
    InMemoryCertificates.defineOperatorExternalCertificateFile("asdf");

    assertThat(Certificates.getOperatorExternalCertificateData(), notNullValue());
  }

  @Test
  public void whenNoInternalCertificateFile_returnNull() {
    assertThat(Certificates.getOperatorInternalCertificateData(), nullValue());
  }

  @Test
  public void whenInternalCertificateFileDefined_returnPath() {
    InMemoryCertificates.defineOperatorInternalCertificateFile("asdf");

    assertThat(Certificates.getOperatorInternalCertificateData(), notNullValue());
  }
}

// Copyright (c) 2019, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.nio.file.Path;
import java.util.function.Function;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;

@SuppressWarnings("SameParameterValue")
public class InMemoryCertificates {
  public static final String INTERNAL_CERT_DATA = "encoded-cert-data";

  private static InMemoryFileSystem fileSystem;
  @SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
  private static Function<String, Path> getInMemoryPath = p -> fileSystem.getPath(p);

  /**
   * install memento.
   * @return memento
   * @throws NoSuchFieldException on no such field
   */
  public static Memento install() throws NoSuchFieldException {
    fileSystem = InMemoryFileSystem.createInstance();
    defineOperatorInternalCertificateFile(INTERNAL_CERT_DATA);
    return StaticStubSupport.install(Certificates.class, "GET_PATH", getInMemoryPath);
  }

  static Memento installWithoutData() throws NoSuchFieldException {
    fileSystem = InMemoryFileSystem.createInstance();
    return StaticStubSupport.install(Certificates.class, "GET_PATH", getInMemoryPath);
  }

  static void defineOperatorExternalKeyFile(String contents) {
    fileSystem.defineFile(Certificates.EXTERNAL_CERTIFICATE_KEY, contents);
  }

  static void defineOperatorInternalKeyFile(String contents) {
    fileSystem.defineFile(Certificates.INTERNAL_CERTIFICATE_KEY, contents);
  }

  static void defineOperatorExternalCertificateFile(String contents) {
    fileSystem.defineFile(Certificates.EXTERNAL_CERTIFICATE, contents);
  }

  static void defineOperatorInternalCertificateFile(String contents) {
    fileSystem.defineFile(Certificates.INTERNAL_CERTIFICATE, contents);
  }
}

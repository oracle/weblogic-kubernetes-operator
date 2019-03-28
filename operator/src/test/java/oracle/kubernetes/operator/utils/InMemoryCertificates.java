// Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import com.meterware.simplestub.Memento;
import com.meterware.simplestub.StaticStubSupport;
import java.nio.file.Path;
import java.util.function.Function;

@SuppressWarnings("SameParameterValue")
public class InMemoryCertificates {
  public static final String INTERNAL_CERT_DATA = "encoded-cert-data";

  private static InMemoryFileSystem fileSystem;
  private static Function<String, Path> getInMemoryPath = p -> fileSystem.getPath(p);

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

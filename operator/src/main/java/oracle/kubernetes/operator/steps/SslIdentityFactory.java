// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.steps;

import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import javax.annotation.Nonnull;

import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.operator.OperatorCreationException;

public interface SslIdentityFactory {

  @Nonnull
  KeyPair createKeyPair() throws NoSuchAlgorithmException, InvalidKeySpecException;

  String convertToPEM(Object object) throws IOException;

  X509Certificate createCertificate(String name, KeyPair keyPair)
      throws OperatorCreationException, CertificateException, CertIOException;
}

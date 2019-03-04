// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;

public class PEMImporter {
  private static final Logger logger = Logger.getLogger("OperatorIT", "OperatorIT");
  /**
   * Create a KeyStore from standard PEM files
   *
   * @param privateKeyPem the private key PEM file
   * @param certificatePem the certificate(s) PEM file
   * @param the password to set to protect the private key
   */
  public static KeyStore createKeyStore(File certificatePem, final String password)
      throws Exception, KeyStoreException, IOException, NoSuchAlgorithmException,
          CertificateException {
    // Import certificate pem file
    final X509Certificate[] certChain = createCertificates(certificatePem);

    // Create a Keystore obj if the type "JKS"
    final KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());

    // Make an empty store
    keystore.load(null);

    // Import private key
    //    final PrivateKey key = createPrivateKey(privateKeyPem);

    // Load cert and key files into the Keystore obj and create it
    //    keystore.setKeyEntry(privateKeyPem.getName(), key, password.toCharArray(), cert);
    for (Certificate cert : certChain) {
      keystore.setCertificateEntry("operator", cert);
    }

    return keystore;
  }

  private static PrivateKey createPrivateKey(File privateKeyPem) throws Exception {
    final BufferedReader bufferedReader = new BufferedReader(new FileReader(privateKeyPem));

    String errormsg = "===== No PRIVATE KEY found";

    final StringBuilder stringBuilder = new StringBuilder();
    String line = bufferedReader.readLine();

    while (line != null) {
      if (line.contains("BEGIN PRIVATE KEY")) {
        break;
      }

      line = bufferedReader.readLine();
    }

    line = "";
    while (line != null) {
      if (line.contains("END PRIVATE KEY")) {
        break;
      }

      if (!line.isEmpty()) {
        stringBuilder.append(line);
      }

      line = bufferedReader.readLine();
    }

    bufferedReader.close();

    final String hexString = stringBuilder.toString();
    final byte[] bytes = DatatypeConverter.parseBase64Binary(hexString);

    return generatePrivateKeyFromDER(bytes);
  }

  private static X509Certificate[] createCertificates(File certificatePem) throws Exception {
    final List<X509Certificate> result = new ArrayList<X509Certificate>();
    final BufferedReader bufferedReader = new BufferedReader(new FileReader(certificatePem));
    String errormsg = "===== No CERTIFICATE found";

    String line = bufferedReader.readLine();

    if (!line.contains("BEGIN CERTIFICATE")) {
      bufferedReader.close();
      throw new IllegalArgumentException(errormsg);
    }

    StringBuilder stringBuilder = new StringBuilder();
    while (line != null) {
      if (line.contains("END CERTIFICATE")) {
        String hexString = stringBuilder.toString();
        final byte[] bytes = DatatypeConverter.parseBase64Binary(hexString);
        X509Certificate cert = generateCertificateFromDER(bytes);
        result.add(cert);
        stringBuilder = new StringBuilder();
      } else {
        if (!line.startsWith("----")) {
          stringBuilder.append(line);
        }
      }

      line = bufferedReader.readLine();
    }

    bufferedReader.close();

    return result.toArray(new X509Certificate[result.size()]);
  }

  private static RSAPrivateKey generatePrivateKeyFromDER(byte[] keyBytes)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    final KeyFactory factory = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) factory.generatePrivate(spec);
  }

  private static X509Certificate generateCertificateFromDER(byte[] certBytes)
      throws CertificateException {
    final CertificateFactory factory = CertificateFactory.getInstance("X.509");
    return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(certBytes));
  }
}

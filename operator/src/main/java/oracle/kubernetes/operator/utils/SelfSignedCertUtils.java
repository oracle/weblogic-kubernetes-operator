// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.annotation.Nonnull;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.RFC4519Style;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.crypto.params.RSAPrivateCrtKeyParameters;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;
import static oracle.kubernetes.operator.helpers.NamespaceHelper.getWebhookNamespace;

/**
 * Utility class for generating key-pair and self-signed certificates.
 *
 */
public final class SelfSignedCertUtils {

  private SelfSignedCertUtils() {
    // no-op
  }

  public static final String INTERNAL_WEBLOGIC_OPERATOR_SVC = "internal-weblogic-operator-svc";
  public static final String WEBLOGIC_OPERATOR_WEBHOOK_SVC = "weblogic-operator-webhook-svc";
  public static final String WEBHOOK_CERTIFICATE = "webhookCert";

  /**
   * Generates a key pair using the BouncyCastle lib.
   *
   * @return Key pair
   *
   * @throws InvalidKeySpecException on generating the key if specs are invalid.
   * @throws NoSuchAlgorithmException in KeyFactory.getInstance() if algorithm is invalid.
   */
  public static KeyPair createKeyPair() throws NoSuchAlgorithmException, InvalidKeySpecException {

    RSAKeyPairGenerator rsaKeyPairGenerator = new RSAKeyPairGenerator();

    rsaKeyPairGenerator.init(new RSAKeyGenerationParameters(BigInteger.valueOf(3), new SecureRandom(), 2048, 80));
    AsymmetricCipherKeyPair keypair = rsaKeyPairGenerator.generateKeyPair();

    RSAKeyParameters publicKey = (RSAKeyParameters) keypair.getPublic();
    RSAPrivateCrtKeyParameters privateKey = (RSAPrivateCrtKeyParameters) keypair.getPrivate();

    PublicKey pubKey = KeyFactory.getInstance("RSA").generatePublic(
            new RSAPublicKeySpec(publicKey.getModulus(), publicKey.getExponent()));

    PrivateKey privKey = KeyFactory.getInstance("RSA").generatePrivate(
            new RSAPrivateCrtKeySpec(publicKey.getModulus(), publicKey.getExponent(),
                    privateKey.getExponent(), privateKey.getP(), privateKey.getQ(),
                    privateKey.getDP(), privateKey.getDQ(), privateKey.getQInv()));

    return new KeyPair(pubKey, privKey);
  }

  /**
   * Generates a self signed certificate using the BouncyCastle lib.
   *
   * @param keyPair used for signing the certificate with PrivateKey
   * @param hashAlgorithm Hash function
   * @param commonName Common Name to be used in the subject dn
   * @param certificateValidityDays validity period in days of the certificate
   *
   * @return self-signed X509Certificate
   *
   * @throws OperatorCreationException on creating a key id
   * @throws CertIOException on building JcaContentSignerBuilder
   * @throws CertificateException on getting certificate from provider
   */
  public static X509Certificate generateCertificate(String cert, KeyPair keyPair, String hashAlgorithm,
                                                    String commonName, int certificateValidityDays)
          throws OperatorCreationException, CertificateException, CertIOException {

    Instant now = Instant.now();
    Date notBefore = Date.from(now);
    Date notAfter = Date.from(now.plus(Duration.ofDays(certificateValidityDays)));

    ContentSigner contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());
    X500Name x500Name = createX500NameBuilder(commonName).build();
    X509v3CertificateBuilder certificateBuilder =
            new JcaX509v3CertificateBuilder(x500Name,
                    BigInteger.valueOf(now.toEpochMilli()),
                    notBefore,
                    notAfter,
                    x500Name,
                    keyPair.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .addExtension(Extension.subjectAlternativeName, false, getSAN(cert));

    return new JcaX509CertificateConverter()
            .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
  }

  @Nonnull
  private static GeneralNames getSAN(String cert) {
    String host = INTERNAL_WEBLOGIC_OPERATOR_SVC;
    String namespace = getOperatorNamespace();
    if (WEBHOOK_CERTIFICATE.equals(cert)) {
      host = WEBLOGIC_OPERATOR_WEBHOOK_SVC;
      namespace = getWebhookNamespace();
    }
    return new GeneralNames(new GeneralName[]{
        new GeneralName(GeneralName.dNSName, host),
        new GeneralName(GeneralName.dNSName, host + "." + namespace),
        new GeneralName(GeneralName.dNSName, host + "." + namespace + ".svc"),
        new GeneralName(GeneralName.dNSName, host + "." + namespace + ".svc.cluster.local")});
  }

  private static X500NameBuilder createX500NameBuilder(String commonName) {
    X500NameBuilder builder = new X500NameBuilder(RFC4519Style.INSTANCE);
    builder.addRDN(RFC4519Style.cn, commonName);
    builder.addRDN(RFC4519Style.c, "US");
    builder.addRDN(RFC4519Style.st, "CALIFORNIA");
    builder.addRDN(RFC4519Style.l, "REDWOOD CITY");
    builder.addRDN(RFC4519Style.o, "WebLogic");
    builder.addRDN(RFC4519Style.ou, "Development");
    return builder;
  }

}
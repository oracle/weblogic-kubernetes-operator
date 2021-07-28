// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.Key;
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
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jetbrains.annotations.NotNull;

import static oracle.kubernetes.operator.helpers.NamespaceHelper.getOperatorNamespace;

/**
 * Utility class for generating self-signed certificates.
 *
 */
public final class SelfSignedCertGenerator {

  public static final String INTERNAL_WEBLOGIC_OPERATOR_SVC = "internal-weblogic-operator-svc";

  private SelfSignedCertGenerator() {
  }

  /**
   * Generates a key pair using the BouncyCastle lib.
   *
   * @return Key pair
   *
   * @throws InvalidKeySpecException on generating the key if specs are invalid.
   * @throws NoSuchAlgorithmException in KeyFactory.getInstance() if algorithm is invalid.
   */
  public static KeyPair createKeyPair() throws NoSuchAlgorithmException, InvalidKeySpecException {

    final RSAKeyPairGenerator gen = new RSAKeyPairGenerator();

    gen.init(new RSAKeyGenerationParameters(BigInteger.valueOf(3), new SecureRandom(), 2048, 80));
    final AsymmetricCipherKeyPair keypair = gen.generateKeyPair();

    final RSAKeyParameters publicKey = (RSAKeyParameters) keypair.getPublic();
    final RSAPrivateCrtKeyParameters privateKey = (RSAPrivateCrtKeyParameters) keypair.getPrivate();

    final PublicKey pubKey = KeyFactory.getInstance("RSA").generatePublic(
            new RSAPublicKeySpec(publicKey.getModulus(), publicKey.getExponent()));

    final PrivateKey privKey = KeyFactory.getInstance("RSA").generatePrivate(
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
   * @param cn Common Name to be used in the subject dn
   * @param days validity period in days of the certificate
   *
   * @return self-signed X509Certificate
   *
   * @throws OperatorCreationException on creating a key id
   * @throws CertIOException on building JcaContentSignerBuilder
   * @throws CertificateException on getting certificate from provider
   */
  public static X509Certificate generate(final KeyPair keyPair,
                                         final String hashAlgorithm,
                                         final String cn,
                                         final int days)
          throws OperatorCreationException, CertificateException, CertIOException {
    final Instant now = Instant.now();
    final Date notBefore = Date.from(now);
    final Date notAfter = Date.from(now.plus(Duration.ofDays(days)));

    final ContentSigner contentSigner = new JcaContentSignerBuilder(hashAlgorithm).build(keyPair.getPrivate());
    final X500Name x500Name = createStdBuilder(cn).build();
    final X509v3CertificateBuilder certificateBuilder =
            new JcaX509v3CertificateBuilder(x500Name,
                    BigInteger.valueOf(now.toEpochMilli()),
                    notBefore,
                    notAfter,
                    x500Name,
                    keyPair.getPublic())
                    .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                    .addExtension(Extension.subjectAlternativeName, false, getSAN());

    return new JcaX509CertificateConverter()
            .setProvider(new BouncyCastleProvider()).getCertificate(certificateBuilder.build(contentSigner));
  }

  @NotNull
  private static GeneralNames getSAN() {
    String host = INTERNAL_WEBLOGIC_OPERATOR_SVC;
    return new GeneralNames(new GeneralName[] {
        new GeneralName(GeneralName.dNSName, host),
        new GeneralName(GeneralName.dNSName, host + "." + getOperatorNamespace()),
        new GeneralName(GeneralName.dNSName, host + "." + getOperatorNamespace() + ".svc"),
        new GeneralName(GeneralName.dNSName, host + "." + getOperatorNamespace() + ".svc.cluster.local")});
  }

  private static X500NameBuilder createStdBuilder(String cn) {
    X500NameBuilder builder = new X500NameBuilder(RFC4519Style.INSTANCE);

    builder.addRDN(RFC4519Style.c, "US");
    builder.addRDN(RFC4519Style.st, "CALIFORNIA");
    builder.addRDN(RFC4519Style.l, "REDWOOD CITY");
    builder.addRDN(RFC4519Style.o, "WebLogic");
    builder.addRDN(RFC4519Style.ou, "Development");
    builder.addRDN(RFC4519Style.cn, cn);

    return builder;
  }

  /**
   * Writes the specified key to the file in PEM format.
   *
   * @param key Key to be written
   * @param path File path for the file containing Key in PEM format
   *
   * @throws IOException when writing the PEM contents to file.
   */
  public static void writePem(Key key, File path) throws IOException {
    path.getParentFile().mkdirs();
    JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(path));
    writer.writeObject(key);
    writer.flush();
  }

  /**
   * Writes the specified String to the file.
   *
   * @param encodedContent String to be written
   * @param path File path for the file containing string
   *
   * @throws IOException when writing the PEM contents to file.
   */
  public static void writeStringToFile(String encodedContent, File path) throws IOException {
    path.getParentFile().mkdirs();
    Writer wr = Files.newBufferedWriter(path.toPath());
    wr.write(encodedContent);
    wr.flush();
  }
}
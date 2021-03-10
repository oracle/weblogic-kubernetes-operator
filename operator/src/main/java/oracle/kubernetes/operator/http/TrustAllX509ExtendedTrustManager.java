// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.http;

import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

class TrustAllX509ExtendedTrustManager extends X509ExtendedTrustManager {

  public static SSLContext getTrustingSSLContext() {
    try {
      TrustManager[] trustAllCerts = new TrustManager[]{
          new TrustAllX509ExtendedTrustManager()
      };
      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, null);

      return sslContext;
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new IllegalStateException(e);
    }
  }

  @Override
  public void checkClientTrusted(
      X509Certificate[] certs, String authType) {
  }

  @Override
  public void checkClientTrusted(
      X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
  }

  @Override
  public void checkClientTrusted(
      X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
  }

  @Override
  public void checkServerTrusted(
      X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
  }

  @Override
  public void checkServerTrusted(
      X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
  }

  @Override
  public void checkServerTrusted(
      X509Certificate[] certs, String authType) {
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return null;
  }
}

// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;

import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 * A simple Http client.
 */
public class OracleHttpClient {

  /**
   * Http GET request.
   *
   * @param url URL of the web resource
   * @param headers map of HTTP headers
   * @param debug if true prints status code and response body
   * @return HttpResponse object
   * @throws IOException when cannot connect to the URL
   * @throws InterruptedException when connection to web resource times out
   */
  public static HttpResponse<String> get(String url, Map<String, String> headers, boolean debug)
      throws IOException, InterruptedException {
    LoggingFacade logger = getLogger();

    SSLContext sslContext = null;
    try {
      sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, new TrustManager[]{MOCK_TRUST_MANAGER}, new SecureRandom());
    } catch (Exception ex) {
      logger.severe(ex.getLocalizedMessage());
    }
    HttpClient httpClient = HttpClient.newBuilder()
        .sslContext(sslContext)
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    requestBuilder
        .GET()
        .uri(URI.create(url));
    if (headers != null) {
      for (Entry<String, String> entry : headers.entrySet()) {
        if (entry.getKey().equals("Authorization")) {
          requestBuilder.header("Authorization",
              "Basic " + Base64.getEncoder().encodeToString(entry.getValue().getBytes()));
        } else {
          requestBuilder = requestBuilder.header(entry.getKey(), entry.getValue());
        }
      }
    }
    HttpRequest request = requestBuilder.build();
    logger.info("Sending http request {0}", url);

    HttpResponse<String> response = httpClient.send(request,
        HttpResponse.BodyHandlers.ofString());
    if (debug) {
      logger.info("HTTP_STATUS: {0}", response.statusCode());
      logger.info("Response Body: {0}", response.body());
    }
    return response;
  }

  /**
   * Http GET request.
   *
   * @param url URL of the web resource
   * @return HttpResponse object
   * @throws IOException when cannot connect to the URL
   * @throws InterruptedException when connection to web resource times out
   */
  public static HttpResponse<String> get(String url) throws IOException,
      InterruptedException {
    return get(url, null, false);
  }

  /**
   * Http GET request with debug printing.
   *
   * @param url URL of the web resource
   * @param debug if true prints status code and response body
   * @return HttpResponse object
   * @throws IOException when cannot connect to the URL
   * @throws InterruptedException when connection to web resource times out
   */
  public static HttpResponse<String> get(String url, boolean debug) throws IOException,
      InterruptedException {
    return get(url, null, debug);
  }

  /**
   * Http GET request with custom headers.
   *
   * @param url URL of the web resource
   * @param headers map of HTTP headers
   * @return HttpResponse object
   * @throws IOException when cannot connect to the URL
   * @throws InterruptedException when connection to web resource times out
   */
  public static HttpResponse<String> get(String url, Map<String, String> headers)
      throws IOException, InterruptedException {
    return get(url, headers, false);
  }

  /**
   * Http GET request to download a file and save it in the give destination directory.
   *
   * @param url URL of the file to download
   * @param destLocation detination directory where to save the downloaded file
   * @param proxyHost optional proxy host, can be null for no proxy
   * @param proxyPort optional proxy port, can be null for no proxy
   * @param maxRetries the maximum number of retries before it can return false
   * @return true if download succeeds otherwide false
   */
  public static boolean downloadFile(String url, String destLocation, String proxyHost,
      String proxyPort, int maxRetries) {
    LoggingFacade logger = getLogger();

    // Build HttpClient with optional proxy and retry policy
    HttpClient.Builder clientBuilder = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10));

    // Configure proxy if PROXY_HOST and PROXY_PORT are specified
    if (proxyHost != null && proxyPort != null) {
      clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, Integer.valueOf(proxyPort))));
      logger.info("Proxy configured: {0}:{1}", proxyHost, proxyPort);
    } else {
      logger.info("No proxy configuration provided.");
    }

    HttpClient client = clientBuilder.build();

    // Prepare the HttpRequest
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofMinutes(2))
        .GET()
        .build();

    // Attempt to download the file
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
      try {
        logger.info("Starting download...");
        if (client.send(request, HttpResponse.BodyHandlers.ofFile(Paths.get(destLocation))).statusCode() != 200) {
          logger.info("Failed download retrying...");
          TimeUnit.SECONDS.sleep(10);
          continue;
        } else {
          logger.info("Download completed successfully.");
          return true;
        }
      } catch (HttpTimeoutException e) {
        logger.severe("Request timed out: {0}", e.getMessage());
      } catch (IOException | InterruptedException e) {
        logger.severe("Download failed: {0}", e.getMessage());
      }
    }
    return false;
  }
  
  private static final TrustManager MOCK_TRUST_MANAGER = new X509ExtendedTrustManager() {
    @Override
    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
      return new java.security.cert.X509Certificate[0];
    }

    @Override
    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
        throws CertificateException {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] xcs, String string, Socket socket) throws CertificateException {
    }

    @Override
    public void checkServerTrusted(X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException {
    }

    @Override
    public void checkClientTrusted(X509Certificate[] xcs, String string, Socket socket) throws CertificateException {
    }

    @Override
    public void checkClientTrusted(X509Certificate[] xcs, String string, SSLEngine ssle) throws CertificateException {
    }

    @Override
    public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {
    }
  };

}

// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.wlsconfig;

import oracle.kubernetes.operator.http.HttpClient;
import oracle.kubernetes.operator.logging.LoggingFacade;
import oracle.kubernetes.operator.logging.LoggingFactory;
import org.junit.Ignore;
import org.junit.Test;


@Ignore
public class WlsConfigRetreiverTest {

  private static final LoggingFacade LOGGER = LoggingFactory.getLogger("Operator", "Operator");

  @Test
  public void testWlsConfigRetriever() {

    final String namespace = "default";
    final String SECRET_NAME = "wls-admin-server-credentials"; // TODO: will be getting this from configuration
    String principal = "system:serviceaccount:default:weblogic-operator";

    // set the timeout to a reasonable value so that the build is not held up by this test
    System.setProperty("read.config.timeout.ms", "3000");

    try {
      WlsDomainConfig wlsDomainConfig = new WlsRetriever(namespace, "wls-admin-service", SECRET_NAME).readConfig(principal);

      LOGGER.finer("Read config " + wlsDomainConfig);

      LOGGER.finer("sleeping.....");
      Thread.sleep(10000);

      LOGGER.finer("--- trying update REST call ---");
      HttpClient httpClient = HttpClient.createAuthenticatedClientForServer(principal, namespace, SECRET_NAME);
      String url = "/management/weblogic/latest/edit/servers/ms-3";
      String payload = "{listenAddress: 'ms-3.wls-subdomain.default.svc.cluster.local'}";
      String serviceURL = HttpClient.getServiceURL(principal, "wls-admin-service", namespace);
      String result = httpClient.executePostUrlOnServiceClusterIP(url, serviceURL, "default", payload);
      LOGGER.finer("REST call returns: " + result);

      LOGGER.finer("Read config again: " + new WlsRetriever("default", serviceURL, SECRET_NAME).readConfig(principal));
    } catch (Exception e) {
      LOGGER.finer("namespace query failed: " + e);
      e.printStackTrace();
    } finally {
      LOGGER.finer("End of this test");
    }
  }
}

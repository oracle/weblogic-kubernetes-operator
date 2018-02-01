/* Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved. */
package oracle.kubernetestests;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.models.V1APIResourceList;
import oracle.kubernetes.operator.authentication.Authenticator;
import org.junit.Test;

/**
 * Test CustomResourceDefinitions and custom objects
 */
public class TestEnhancedAuthenticationAT {

  private ApiClient client;
  private ApiextensionsV1beta1Api apiExtensions;
  private CustomObjectsApi customObjects;

  static public void main(String[] args) throws Exception {
    new TestEnhancedAuthenticationAT().testCreateClientByServiceAccount();
  }

  @Test
  public void testCreateClientByServiceAccount() throws Exception {

    try {
      // First authenticate using the default service account in default
      // namespace.  The resultant client is used to do a trivial call
      // to demonstrate that the rest API is available weith the new client.
      Authenticator auth1 = new Authenticator();
      ApiClient client = auth1.createClientByServiceAccountName("default", "default");
      CoreV1Api coreApi = new CoreV1Api(client);
      System.out.println("authenticated by service account");
      V1APIResourceList rslist = coreApi.getAPIResources();
      System.out.println(rslist);

      // Pickup the token from the first authentication and use it to
      // find the same service account that was just authenticated. Then
      // do a REST api call with the returned client to verify the API
      // is available through the new client.
      Authenticator auth2 = new Authenticator(client);
      client = auth2.createClientByToken(auth1.getServiceToken());
      coreApi = new CoreV1Api(client);
      System.out.println("authenticated by token");
      rslist = coreApi.getAPIResources();
      System.out.println(rslist);

      System.out.println("Successful end of this test");
    } catch (Exception e) {
      System.out.println("Test failed with exception - " + e);
      e.printStackTrace();
    }
  }
}

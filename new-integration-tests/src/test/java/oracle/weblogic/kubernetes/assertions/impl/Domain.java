// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.ApiextensionsV1Api;
import io.kubernetes.client.openapi.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.openapi.apis.ApisApi;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import oracle.weblogic.kubernetes.extensions.LoggedTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Domain implements LoggedTest {

  private static CustomObjectsApi customObjectsApi = new CustomObjectsApi();
  private static ApiextensionsV1Api apiextensionsV1Api = new ApiextensionsV1Api();
  private static ApiextensionsV1beta1Api apiextensionsV1beta1Api = new ApiextensionsV1beta1Api();
  private static ApisApi apisApi = new ApisApi();


  /**
   * Check if the Domain CRD exists
   * @return true if domains.weblogic.oracle CRD exists otherwise false
   * @throws Exception
   */
  public static boolean isCRDExists() throws Exception {
    try {
      V1beta1CustomResourceDefinition domainBetaCrd =
          apiextensionsV1beta1Api.readCustomResourceDefinition(
              "domains.weblogic.oracle", null, null, null);
      assertNotNull(domainBetaCrd);
      logger.info("domainBetaCrd is not null");
      return true;
    } catch (ApiException aex) {
      if (aex.getCode() == 404) {
        assertTrue(false, "Expected CRD domains.weblogic.oracle existed.");
      } else {
        throw aex;
      }
    }
    return false;
  }

  /**
   * Checks if weblogic.oracle CRD domain object exists.
   * @param domainUID domain UID of the domain object
   * @param namespace in which the domain object exists
   * @return true if domain object exists otherwise false
   */
  public static Callable<Boolean> exists(String domainUID, String namespace) {
    return () -> {
      Object domainObject =
          customObjectsApi.getNamespacedCustomObject(
              "weblogic.oracle", "v2", namespace, "domains", domainUID);
      logger.info("Domain Object exists : " + (domainObject != null));
      return domainObject != null;
    };
  }

  public static boolean adminT3ChannelAccessible(String domainUID, String namespace) {
    return true;
  }

  public static boolean adminNodePortAccessible(String domainUID, String namespace) {
    return true;
  }

}

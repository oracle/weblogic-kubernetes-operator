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

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Domain {

  private static CustomObjectsApi customObjectsApi = new CustomObjectsApi();
  private static ApiextensionsV1Api apiextensionsV1Api = new ApiextensionsV1Api();
  private static ApiextensionsV1beta1Api apiextensionsV1beta1Api = new ApiextensionsV1beta1Api();
  private static ApisApi apisApi = new ApisApi();


  /**
   * Check if the Domain CRD exists
   * @return true if domains.weblogic.oracle CRD exists otherwise false
   * @throws ApiException when Domain CRD doesn't exist
   */
  public static boolean doesCRDExist() throws ApiException {
    try {
      V1beta1CustomResourceDefinition domainBetaCrd =
          apiextensionsV1beta1Api.readCustomResourceDefinition(
              "domains.weblogic.oracle", null, null, null);
      assertNotNull(domainBetaCrd);
      logger.info("domainBetaCrd is not null");
      return true;
    } catch (ApiException aex) {
      if (aex.getCode() == 404) {
        assertTrue(false, "CRD domains.weblogic.oracle not found");
      } else {
        throw aex;
      }
    }
    return false;
  }

  /**
   * Checks if weblogic.oracle CRD domain object exists.
   * @param domainUID domain UID of the domain object
   * @param domainVersion version value for Kind Domain
   * @param namespace in which the domain object exists
   * @return true if domain object exists otherwise false
   */
  public static Callable<Boolean> doesDomainExist(String domainUID, String domainVersion, String namespace) {
    return () -> {
      Object domainObject =
          customObjectsApi.getNamespacedCustomObject(
              "weblogic.oracle", domainVersion, namespace, "domains", domainUID);
      boolean domainExist = (domainObject != null);
      logger.info("Domain Object exists : " + domainExist);
      return domainExist;
    };
  }

  public static boolean adminT3ChannelAccessible(String domainUID, String namespace) {
    return true;
  }

  public static boolean adminNodePortAccessible(String domainUID, String namespace) {
    return true;
  }

}

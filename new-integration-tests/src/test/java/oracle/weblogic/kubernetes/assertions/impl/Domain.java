// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1APIGroup;
import io.kubernetes.client.openapi.models.V1APIGroupList;
import io.kubernetes.client.openapi.models.V1CustomResourceDefinition;
import io.kubernetes.client.openapi.models.V1GroupVersionForDiscovery;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import oracle.weblogic.kubernetes.extensions.LoggedTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.kubernetes.client.openapi.apis.ApiextensionsV1Api;
import io.kubernetes.client.openapi.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.openapi.apis.ApisApi;


public class Domain implements LoggedTest {

  private static CustomObjectsApi customObjectsApi = new CustomObjectsApi();
  private static ApiextensionsV1Api apiextensionsV1Api = new ApiextensionsV1Api();
  private static ApiextensionsV1beta1Api apiextensionsV1beta1Api = new ApiextensionsV1beta1Api();
  private static ApisApi apisApi = new ApisApi();

    /**
   * verify domain CRD.
   * @throws Exception on failure
   */
  public static void isCRDExists() throws Exception {
    try {
      V1APIGroupList apis = apisApi.getAPIVersions();
      for (V1APIGroup group : apis.getGroups()) {
        if ("apiextensions.k8s.io".equals(group.getName())) {
          for (V1GroupVersionForDiscovery version : group.getVersions()) {
            logger.info("CRD versions :" + version.getVersion());
            if ("v1".equals(version.getVersion())) {
              // v1 is supported
              V1CustomResourceDefinition domainCrd =
                  apiextensionsV1Api.readCustomResourceDefinition(
                      "domains.weblogic.oracle", null, null, null);
              assertNotNull(domainCrd, "v1 Domain CRD exists");
              return;
            }
          }
          break;
        }
      }
      V1beta1CustomResourceDefinition domainBetaCrd =
          apiextensionsV1beta1Api.readCustomResourceDefinition(
              "domains.weblogic.oracle", null, null, null);
      assertNotNull(domainBetaCrd, "beta1 Domain CRD exists");
    } catch (ApiException aex) {
      if (aex.getCode() == 404) {
        assertTrue(false, "Expected CRD domains.weblogic.oracle existed.");
      } else {
        throw aex;
      }
    }
  }

  public static Callable<Boolean> exists(String domainUID, String namespace) {
    return () -> {
      Object domainObject =
          customObjectsApi.getNamespacedCustomObject(
              "weblogic.oracle", "v2", namespace, "domains", domainUID);
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

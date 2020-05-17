// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.assertions.impl;

import java.io.IOException;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.util.ClientBuilder;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.getPod;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.doesPodExist;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.doesServiceExist;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.isPodReady;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Domain {

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  private static final CustomObjectsApi customObjectsApi = new CustomObjectsApi();
  private static final ApiextensionsV1beta1Api apiextensionsV1beta1Api = new ApiextensionsV1beta1Api();

  /**
   * Check if the Domain CRD exists.
   *
   * @return true if domains.weblogic.oracle CRD exists otherwise false
   * @throws ApiException when Domain CRD doesn't exist
   */
  public static boolean doesCrdExist() throws ApiException {
    try {
      V1beta1CustomResourceDefinition domainBetaCrd
          = apiextensionsV1beta1Api.readCustomResourceDefinition(
          "domains.weblogic.oracle", null, null, null);
      assertNotNull(domainBetaCrd, "Domain CRD is null");
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
   *
   * @param domainUid domain UID of the domain object
   * @param domainVersion version value for Kind Domain
   * @param namespace in which the domain object exists
   * @return true if domain object exists otherwise false
   */
  public static Callable<Boolean> doesDomainExist(String domainUid, String domainVersion, String namespace) {
    return () -> {
      Object domainObject = null;
      try {
        domainObject
            = customObjectsApi.getNamespacedCustomObject(
            "weblogic.oracle", domainVersion, namespace, "domains", domainUid);
      } catch (ApiException apex) {
        logger.info(apex.getMessage());
      }
      boolean domainExist = (domainObject != null);
      logger.info("Domain Object exists : " + domainExist);
      return domainExist;
    };
  }

  /**
   * Check if the domain resource has been patched with a new image.
   *
   * @param domainUID identifier of the domain resource
   * @param namespace Kubernetes namespace in which the domain exists
   * @param image name of the image that the pod is expected to be using
   * @return true if domain resource's image matches the expected value
   */
  public static Callable<Boolean> domainResourceImagePatched(
      String domainUID,
      String namespace,
      String image
  ) {
    return () -> {
      oracle.weblogic.domain.Domain domain = null;
      try {
        domain = oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes
                .getDomainCustomResource(domainUID, namespace);
      } catch (ApiException apex) {
        logger.severe("Failed to obtain the domain resource object from the API server", apex);
        return false;
      }
      
      boolean domainPatched = (domain.spec().image().equals(image));
      logger.info("Domain Object patched : " + domainPatched + " domain image = " + domain.spec().image());
      return domainPatched;
    };
  }

  public static boolean adminT3ChannelAccessible(String domainUid, String namespace) {
    return true;
  }

  public static boolean adminNodePortAccessible(String domainUid, String namespace) {
    return true;
  }

  /**
   * Verify the original managed server pod state is not changed during scaling the cluster.
   * @param podName the name of the managed server pod to check
   * @param domainUid the domain uid of the domain in which the managed server pod exists
   * @param domainNamespace the domain namespace in which the domain exists
   * @param podCreationTimestampBeforeScale the managed server pod creation time stamp before the scale
   * @return true if the managed server pod state is not change during scaling the cluster, false otherwise
   */
  public static boolean podStateNotChangedDuringScalingCluster(String podName,
                                                               String domainUid,
                                                               String domainNamespace,
                                                               String podCreationTimestampBeforeScale) {

    // check that the original managed server pod still exists
    logger.info("Checking that the managed server pod {0} still exists in namespace {1}",
        podName, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> doesPodExist(domainNamespace, domainUid, podName),
        String.format("podExists failed with ApiException for pod %s in namespace %s",
            podName, domainNamespace)),
        String.format("pod %s does not exist in namespace %s", podName, domainNamespace));

    // check that the original managed server pod is in ready state
    logger.info("Checking that the managed server pod {0} is in ready state in namespace {1}",
        podName, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> isPodReady(domainNamespace, domainUid, podName),
        String.format(
            "isPodReady failed with ApiException for pod %s in namespace %s", podName, domainNamespace)),
        String.format("pod %s is not ready in namespace %s", podName, domainNamespace));

    // check that the original managed server service still exists
    logger.info("Checking that the managed server service {0} still exists in namespace {1}",
        podName, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> doesServiceExist(podName, null, domainNamespace),
        String.format("doesServiceExist failed with ApiException for pod %s in namespace %s",
            podName, domainNamespace)),
        String.format("service %s does not exist in namespace %s", podName, domainNamespace));

    // check the pod timestamp of pod is the same as before
    logger.info("Checking that the managed server pod creation timestamp is not changed");
    String podCreationTimeStampDuringScale;
    DateTimeFormatter dtf = DateTimeFormat.forPattern("HHmmss");
    V1Pod pod =
        assertDoesNotThrow(() -> getPod(domainNamespace, "", podName),
            String.format("getPod failed with ApiException for pod %s in namespace %s",
                podName, domainNamespace));
    if (pod != null && pod.getMetadata() != null) {
      podCreationTimeStampDuringScale = dtf.print(pod.getMetadata().getCreationTimestamp());
    } else {
      logger.info("Pod doesn't exist or pod metadata is null");
      return false;
    }

    if (Long.parseLong(podCreationTimestampBeforeScale) != Long.parseLong(podCreationTimeStampDuringScale)) {
      logger.info("The creation timestamp of managed server pod {0} is changed from {1} to {2}",
          podName, podCreationTimestampBeforeScale, podCreationTimeStampDuringScale);
      return false;
    }
    return true;
  }

}

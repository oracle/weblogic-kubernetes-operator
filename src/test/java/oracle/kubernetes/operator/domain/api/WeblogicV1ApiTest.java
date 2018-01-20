// Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.

package oracle.kubernetes.operator.domain.api;

/*
import io.kubernetes.client.ApiException;
*/

import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1Scale;
import oracle.kubernetes.operator.domain.api.v1.WeblogicApi;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import org.junit.Ignore;
import org.junit.Test;

/*
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.Domain;
import oracle.kubernetes.operator.domain.model.oracle.kubernetes.weblogic.domain.v1.DomainList;
*/

/**
 * API tests for WeblogicV1Api
 */
@Ignore
public class WeblogicV1ApiTest {

  private final WeblogicApi api = new WeblogicApi();

  /*

      /#*
       *
       *
       * create a Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void createWebLogicOracleV1NamespacedDomainTest() throws ApiException {
          String namespace = null;
          Domain body = null;
          String pretty = null;
          Domain response = api.createWebLogicOracleV1NamespacedDomain(namespace, body, pretty);

          // TODO: test validations
      }

      /#*
       *
       *
       * delete collection of Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void deleteWebLogicOracleV1CollectionNamespacedDomainTest() throws ApiException {
          String namespace = null;
          String pretty = null;
          String _continue = null;
          String fieldSelector = null;
          Boolean includeUninitialized = null;
          String labelSelector = null;
          Integer limit = null;
          String resourceVersion = null;
          Integer timeoutSeconds = null;
          Boolean watch = null;
          oracle.kubernetes.model.io.k8s.apimachinery.pkg.apis.meta.v1.Status response = api.deleteWebLogicOracleV1CollectionNamespacedDomain(namespace, pretty, _continue, fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);

          // TODO: test validations
      }

      /#*
       *
       *
       * delete a Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void deleteWebLogicOracleV1NamespacedDomainTest() throws ApiException {
          String name = null;
          String namespace = null;
          oracle.kubernetes.model.io.k8s.apimachinery.pkg.apis.meta.v1.DeleteOptions body = null;
          String pretty = null;
          Integer gracePeriodSeconds = null;
          Boolean orphanDependents = null;
          String propagationPolicy = null;
          oracle.kubernetes.model.io.k8s.apimachinery.pkg.apis.meta.v1.Status response = api.deleteWebLogicOracleV1NamespacedDomain(name, namespace, body, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy);

          // TODO: test validations
      }

      /#*
       *
       *
       * list or watch objects of kind Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void listWebLogicOracleV1NamespacedDomainTest() throws ApiException {
          String namespace = null;
          String pretty = null;
          String _continue = null;
          String fieldSelector = null;
          Boolean includeUninitialized = null;
          String labelSelector = null;
          Integer limit = null;
          String resourceVersion = null;
          Integer timeoutSeconds = null;
          Boolean watch = null;
          DomainList response = api.listWebLogicOracleV1NamespacedDomain(namespace, pretty, _continue, fieldSelector, includeUninitialized, labelSelector, limit, resourceVersion, timeoutSeconds, watch);

          // TODO: test validations
      }

      /#*
       *
       *
       * partially update the specified Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void patchWebLogicOracleV1NamespacedDomainTest() throws ApiException {
          String name = null;
          String namespace = null;
          oracle.kubernetes.model.io.k8s.apimachinery.pkg.apis.meta.v1.Patch body = null;
          String pretty = null;
          Domain response = api.patchWebLogicOracleV1NamespacedDomain(name, namespace, body, pretty);

          // TODO: test validations
      }

      /#*
       *
       *
       * partially update scale of the specified Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void patchWebLogicOracleV1NamespacedDomainScaleTest() throws ApiException {
          String name = null;
          String namespace = null;
          oracle.kubernetes.model.io.k8s.apimachinery.pkg.apis.meta.v1.Patch body = null;
          String pretty = null;
          oracle.kubernetes.model.io.k8s.api.autoscaling.v1.Scale response = api.patchWebLogicOracleV1NamespacedDomainScale(name, namespace, body, pretty);

          // TODO: test validations
      }

      /#*
       *
       *
       * partially update status of the specified Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void patchWebLogicOracleV1NamespacedDomainStatusTest() throws ApiException {
          String name = null;
          String namespace = null;
          oracle.kubernetes.model.io.k8s.apimachinery.pkg.apis.meta.v1.Patch body = null;
          String pretty = null;
          Domain response = api.patchWebLogicOracleV1NamespacedDomainStatus(name, namespace, body, pretty);

          // TODO: test validations
      }

      /#*
       *
       *
       * read the specified Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void readWebLogicOracleV1NamespacedDomainTest() throws ApiException {
          String name = null;
          String namespace = null;
          String pretty = null;
          Boolean exact = null;
          Boolean export = null;
          Domain response = api.readWebLogicOracleV1NamespacedDomain(name, namespace, pretty, exact, export);

          // TODO: test validations
      }

      /#*
       *
       *
       * read scale of the specified Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void readWebLogicOracleV1NamespacedDomainScaleTest() throws ApiException {
          String name = null;
          String namespace = null;
          String pretty = null;
          oracle.kubernetes.model.io.k8s.api.autoscaling.v1.Scale response = api.readWebLogicOracleV1NamespacedDomainScale(name, namespace, pretty);

          // TODO: test validations
      }

      /#*
       *
       *
       * read status of the specified Domain
       *
       * @throws ApiException
       *          if the Api call fails
       #/
      @Test
      public void readWebLogicOracleV1NamespacedDomainStatusTest() throws ApiException {
          String name = null;
          String namespace = null;
          String pretty = null;
          Domain response = api.readWebLogicOracleV1NamespacedDomainStatus(name, namespace, pretty);

          // TODO: test validations
      }

      /**
       *
       *
       * replace the specified Domain
       *
       * @throws ApiException
       *          if the Api call fails
       */
  @Test
  public void replaceWebLogicOracleV1NamespacedDomainTest() throws ApiException {
    String name = null;
    String namespace = null;
    Domain body = null;
    String pretty = null;
    Domain response = api.replaceWebLogicOracleV1NamespacedDomain(name, namespace, body, pretty);

    // TODO: test validations
  }

  /**
   * replace scale of the specified Domain
   *
   * @throws ApiException if the Api call fails
   */
  @Test
  public void replaceWebLogicOracleV1NamespacedDomainScaleTest() throws ApiException {
    String name = null;
    String namespace = null;
    V1Scale body = null;
    String pretty = null;
    V1Scale response = api.replaceWebLogicOracleV1NamespacedDomainScale(name, namespace, body, pretty);

    // TODO: test validations
  }

  /**
   * replace status of the specified Domain
   *
   * @throws ApiException if the Api call fails
   */
  @Test
  public void replaceWebLogicOracleV1NamespacedDomainStatusTest() throws ApiException {
    String name = null;
    String namespace = null;
    Domain body = null;
    String pretty = null;
    Domain response = api.replaceWebLogicOracleV1NamespacedDomainStatus(name, namespace, body, pretty);

    // TODO: test validations
  }

}

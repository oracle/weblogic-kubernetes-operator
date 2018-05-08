// Copyright 2018 Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.helpers;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1SelfSubjectRulesReview;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.VersionInfo;
import oracle.kubernetes.weblogic.domain.v1.DomainList;

public interface SynchronousCallFactory {

  V1beta1CustomResourceDefinition readCustomResourceDefinition(
      ApiClient client, String name, String pretty, Boolean exact, Boolean export)
      throws ApiException;

  V1beta1CustomResourceDefinition createCustomResourceDefinition(
      ApiClient client, V1beta1CustomResourceDefinition body, String pretty) throws ApiException;

  V1SelfSubjectRulesReview createSelfSubjectRulesReview(
      ApiClient client, V1SelfSubjectRulesReview body, String pretty) throws ApiException;

  V1PersistentVolumeList listPersistentVolumes(
      ApiClient client,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException;

  VersionInfo getVersionCode(ApiClient client) throws ApiException;

  DomainList getDomainList(
      ApiClient client,
      String namespace,
      String pretty,
      String _continue,
      String fieldSelector,
      Boolean includeUninitialized,
      String labelSelector,
      Integer limit,
      String resourceVersion,
      Integer timeoutSeconds,
      Boolean watch)
      throws ApiException;
}

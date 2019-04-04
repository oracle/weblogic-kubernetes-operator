// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.BatchV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.CustomObjectsApi;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.models.V1ClusterRoleBindingList;
import io.kubernetes.client.models.V1ClusterRoleList;
import io.kubernetes.client.models.V1ConfigMapList;
import io.kubernetes.client.models.V1DeploymentList;
import io.kubernetes.client.models.V1JobList;
import io.kubernetes.client.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.models.V1PersistentVolumeList;
import io.kubernetes.client.models.V1PodList;
import io.kubernetes.client.models.V1ReplicaSetList;
import io.kubernetes.client.models.V1RoleBindingList;
import io.kubernetes.client.models.V1RoleList;
import io.kubernetes.client.models.V1SecretList;
import io.kubernetes.client.models.V1ServiceAccountList;
import io.kubernetes.client.models.V1ServiceList;
import io.kubernetes.client.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.models.V1beta1IngressList;
import io.kubernetes.client.util.ClientBuilder;
import java.io.IOException;

public class K8sTestUtils {
  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  private CustomObjectsApi customObjectsApi = new CustomObjectsApi();
  private CoreV1Api coreV1Api = new CoreV1Api();
  private BatchV1Api batchV1Api = new BatchV1Api();
  private AppsV1Api appsV1Api = new AppsV1Api();
  private ExtensionsV1beta1Api extensionsV1beta1Api = new ExtensionsV1beta1Api();
  private RbacAuthorizationV1Api rbacAuthorizationV1Api = new RbacAuthorizationV1Api();
  private ApiextensionsV1beta1Api apiextensionsV1beta1Api = new ApiextensionsV1beta1Api();

  public void verifyDomainCrd() throws Exception {
    try {
      V1beta1CustomResourceDefinition domainCrd =
          apiextensionsV1beta1Api.readCustomResourceDefinition(
              "domains.weblogic.oracle", null, null, null);
      assertNotNull("Domain CRD exists", domainCrd);
    } catch (ApiException aex) {
      if (aex.getCode() == 404) {
        assertTrue("Expected CRD domains.weblogic.oracle existed.", false);
      } else {
        throw aex;
      }
    }
  }

  public void verifyDomain(String namespace, String domainUid, boolean existed) throws Exception {
    try {
      // TODO all resources may be derived from the domain object.
      Object domainObject =
          customObjectsApi.getNamespacedCustomObject(
              "weblogic.oracle", "v2", namespace, "domains", domainUid);

      assertTrue("Domain exists", existed);
    } catch (ApiException aex) {
      if (aex.getCode() == 404) {
        assertTrue(String.format("Expected CRD domain existed in %s.", namespace), !existed);
      } else {
        throw aex;
      }
    }
  }

  public void verifyPods(String namespace, String labelSelectors, int expected) throws Exception {
    V1PodList v1PodList =
        coreV1Api.listNamespacedPod(
            namespace,
            Boolean.FALSE,
            Boolean.FALSE.toString(),
            null,
            null,
            labelSelectors,
            null,
            null,
            null,
            Boolean.FALSE);
    // 1 AS, 2 MS', 1 job
    // TODO verify {domainUID}-admin-server, {domainUID}-managed-server#.
    assertEquals("Number of Pods", v1PodList.getItems().size(), expected);
  }

  public void verifyJobs(String labelSelectors, int expected) throws Exception {
    V1JobList v1JobList =
        batchV1Api.listJobForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    // TODO verify name pattern "{domainUID}-create-weblogic-sample-domain-job"
    assertEquals("Number of jobs", v1JobList.getItems().size(), expected);
  }

  public void verifyNoDeployments(String labelSelectors) throws Exception {
    V1DeploymentList v1DeploymentList =
        appsV1Api.listDeploymentForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals("No deployments", v1DeploymentList.getItems().size(), 0);
  }

  public void verifyNoReplicaSets(String labelSelectors) throws Exception {
    V1ReplicaSetList v1ReplicaSetList =
        appsV1Api.listReplicaSetForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals("No ReplicaSets", v1ReplicaSetList.getItems().size(), 0);
  }

  public void verifyServices(String labelSelectors, int expected) throws Exception {
    // Verify services
    V1ServiceList v1ServiceList =
        coreV1Api.listServiceForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    /**
     * TODO verify name pattern {domainUID}-admin-server {domainUID}-admin-server-external
     * {domainUID}-cluster-cluster-1 {domainUID}-managed-server1 {domainUID}-managed-server2
     */
    assertEquals("Number of services", v1ServiceList.getItems().size(), expected);
  }

  public void verifyPvcs(String labelSelectors, int expected) throws Exception {
    V1PersistentVolumeClaimList v1PersistentVolumeClaimList =
        coreV1Api.listPersistentVolumeClaimForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    // TODO verify {domainUID}-weblogic-sample-pvc
    assertEquals("Number of PVCs", v1PersistentVolumeClaimList.getItems().size(), expected);
  }

  public void verifyIngresses(
      String domainNs, String domainUid, String labelSelectors, int expectedLabeled)
      throws Exception {
    V1beta1IngressList labeledIngressList =
        extensionsV1beta1Api.listIngressForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    // TODO verify {domainUID}-cluster-1
    assertEquals(
        "Number of labeled ingress", labeledIngressList.getItems().size(), expectedLabeled);
    labeledIngressList.getItems().stream()
        .forEach(li -> li.getMetadata().getNamespace().equals(domainNs));
    V1beta1IngressList traefikIngressList =
        extensionsV1beta1Api.listIngressForAllNamespaces(
            null,
            String.format("metadata.name=traefik-hostrouting-%s", domainUid),
            Boolean.TRUE,
            null,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals("Number of Traefik ingress", traefikIngressList.getItems().size(), 1);
    traefikIngressList.getItems().stream()
        .forEach(ti -> ti.getMetadata().getNamespace().equals(domainNs));
  }

  public void verifyConfigMaps(String labelSelectors, int expected) throws Exception {
    V1ConfigMapList v1ConfigMapList =
        coreV1Api.listConfigMapForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    // TODO verify {domainUID}-create-weblogic-sample-domain-job-cm
    assertEquals("Number of config maps", v1ConfigMapList.getItems().size(), expected);
  }

  public void verifyNoServiceAccounts(String labelSelectors) throws Exception {
    V1ServiceAccountList v1ServiceAccountList =
        coreV1Api.listServiceAccountForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals("Number of service accounts", v1ServiceAccountList.getItems().size(), 0);
  }

  public void verifyNoRoles(String labelSelectors) throws Exception {
    V1RoleList v1RoleList =
        rbacAuthorizationV1Api.listRoleForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals("Number of roles", v1RoleList.getItems().size(), 0);
  }

  public void verifyNoRoleBindings(String labelSelectors) throws Exception {
    V1RoleBindingList v1RoleBindingList =
        rbacAuthorizationV1Api.listRoleBindingForAllNamespaces(
            null,
            null,
            Boolean.TRUE,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals("Number of role bindings", v1RoleBindingList.getItems().size(), 0);
  }

  public void verifySecrets(String secretName, int expected) throws Exception {
    V1SecretList v1SecretList =
        coreV1Api.listSecretForAllNamespaces(
            null,
            "metadata.name=" + secretName,
            Boolean.TRUE,
            null,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals("Number of secrets", v1SecretList.getItems().size(), expected);
  }

  public void verifyPvs(String labelSelectors, int expected) throws Exception {
    V1PersistentVolumeList v1PersistentVolumeList =
        coreV1Api.listPersistentVolume(
            Boolean.TRUE,
            Boolean.FALSE.toString(),
            null,
            null,
            labelSelectors,
            null,
            null,
            null,
            Boolean.FALSE);
    assertEquals("Number of PVs", v1PersistentVolumeList.getItems().size(), expected);
  }

  public void verifyNoClusterRoles(String domain1Ls) throws Exception {
    V1ClusterRoleList v1ClusterRoleList =
        rbacAuthorizationV1Api.listClusterRole(
            Boolean.TRUE,
            Boolean.FALSE.toString(),
            null,
            null,
            domain1Ls,
            null,
            null,
            null,
            Boolean.FALSE);
    assertEquals("Number of cluster roles", v1ClusterRoleList.getItems().size(), 0);
  }

  public void verifyNoClusterRoleBindings(String labelSelectors) throws Exception {
    V1ClusterRoleBindingList v1ClusterRoleBindingList =
        rbacAuthorizationV1Api.listClusterRoleBinding(
            Boolean.TRUE,
            Boolean.FALSE.toString(),
            null,
            null,
            labelSelectors,
            null,
            null,
            null,
            Boolean.FALSE);
    assertEquals("Number of cluster role bindings", v1ClusterRoleBindingList.getItems().size(), 0);
  }
}

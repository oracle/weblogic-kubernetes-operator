// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.ApiextensionsV1beta1Api;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.ExtensionsV1beta1IngressList;
import io.kubernetes.client.openapi.models.V1ClusterRoleBindingList;
import io.kubernetes.client.openapi.models.V1ClusterRoleList;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ReplicaSetList;
import io.kubernetes.client.openapi.models.V1RoleBindingList;
import io.kubernetes.client.openapi.models.V1RoleList;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.openapi.models.V1beta1CustomResourceDefinition;
import io.kubernetes.client.util.ClientBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
      assertNotNull(domainCrd, "Domain CRD exists");
    } catch (ApiException aex) {
      if (aex.getCode() == 404) {
        assertTrue(false, "Expected CRD domains.weblogic.oracle existed.");
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

      assertTrue(existed, "Domain exists");
    } catch (ApiException aex) {
      if (aex.getCode() == 404) {
        assertTrue(!existed, String.format("Expected CRD domain existed in %s.", namespace));
      } else {
        throw aex;
      }
    }
  }

  public void verifyPods(String namespace, String labelSelectors, int expected) throws Exception {
    V1PodList v1PodList =
        coreV1Api.listNamespacedPod(
            namespace,
            Boolean.FALSE.toString(),
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            null,
            null,
            Boolean.FALSE);
    // 1 AS, 2 MS', 1 job
    // TODO verify {domainUID}-admin-server, {domainUID}-managed-server#.
    assertEquals(v1PodList.getItems().size(), expected, "Number of Pods");
  }

  public void verifyJobs(String labelSelectors, int expected) throws Exception {
    V1JobList v1JobList =
        batchV1Api.listJobForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    // TODO verify name pattern "{domainUID}-create-weblogic-sample-domain-job"
    assertEquals(v1JobList.getItems().size(), expected, "Number of jobs");
  }

  public void verifyNoDeployments(String labelSelectors) throws Exception {
    V1DeploymentList v1DeploymentList =
        appsV1Api.listDeploymentForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1DeploymentList.getItems().size(), 0, "No deployments");
  }

  public void verifyNoReplicaSets(String labelSelectors) throws Exception {
    V1ReplicaSetList v1ReplicaSetList =
        appsV1Api.listReplicaSetForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1ReplicaSetList.getItems().size(), 0, "No ReplicaSets");
  }

  public void verifyServices(String labelSelectors, int expected) throws Exception {
    // Verify services
    V1ServiceList v1ServiceList =
        coreV1Api.listServiceForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
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
    assertEquals(v1ServiceList.getItems().size(), expected, "Number of services");
  }

  public void verifyPvcs(String labelSelectors, int expected) throws Exception {
    V1PersistentVolumeClaimList v1PersistentVolumeClaimList =
        coreV1Api.listPersistentVolumeClaimForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    // TODO verify {domainUID}-weblogic-sample-pvc
    assertEquals(v1PersistentVolumeClaimList.getItems().size(), expected, "Number of PVCs");
  }

  public void verifyIngresses(
      String domainNs, String domainUid, String labelSelectors, int expectedLabeled)
      throws Exception {
    ExtensionsV1beta1IngressList labeledIngressList =
        extensionsV1beta1Api.listIngressForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    // TODO verify {domainUID}-cluster-1
    assertEquals(
        labeledIngressList.getItems().size(), expectedLabeled, "Number of labeled ingress");
    labeledIngressList.getItems().stream()
        .forEach(li -> li.getMetadata().getNamespace().equals(domainNs));
    ExtensionsV1beta1IngressList traefikIngressList =
        extensionsV1beta1Api.listIngressForAllNamespaces(
            Boolean.FALSE,
            null,
            String.format("metadata.name=traefik-hostrouting-%s", domainUid),
            null,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals(traefikIngressList.getItems().size(), 1, "Number of Traefik ingress");
    traefikIngressList.getItems().stream()
        .forEach(ti -> ti.getMetadata().getNamespace().equals(domainNs));
  }

  public void verifyConfigMaps(String labelSelectors, int expected) throws Exception {
    V1ConfigMapList v1ConfigMapList =
        coreV1Api.listConfigMapForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    // TODO verify {domainUID}-create-weblogic-sample-domain-job-cm
    assertEquals(v1ConfigMapList.getItems().size(), expected, "Number of config maps");
  }

  public void verifyNoServiceAccounts(String labelSelectors) throws Exception {
    V1ServiceAccountList v1ServiceAccountList =
        coreV1Api.listServiceAccountForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1ServiceAccountList.getItems().size(), 0, "Number of service accounts");
  }

  public void verifyNoRoles(String labelSelectors) throws Exception {
    V1RoleList v1RoleList =
        rbacAuthorizationV1Api.listRoleForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1RoleList.getItems().size(), 0, "Number of roles");
  }

  public void verifyNoRoleBindings(String labelSelectors) throws Exception {
    V1RoleBindingList v1RoleBindingList =
        rbacAuthorizationV1Api.listRoleBindingForAllNamespaces(
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1RoleBindingList.getItems().size(), 0, "Number of role bindings");
  }

  public void verifySecrets(String secretName, int expected) throws Exception {
    V1SecretList v1SecretList =
        coreV1Api.listSecretForAllNamespaces(
            Boolean.FALSE,
            null,
            "metadata.name=" + secretName,
            null,
            null,
            Boolean.FALSE.toString(),
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1SecretList.getItems().size(), expected, "Number of secrets");
  }

  public void verifyPvs(String labelSelectors, int expected) throws Exception {
    V1PersistentVolumeList v1PersistentVolumeList =
        coreV1Api.listPersistentVolume(
            Boolean.FALSE.toString(),
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1PersistentVolumeList.getItems().size(), expected, "Number of PVs");
  }

  public void verifyNoClusterRoles(String domain1Ls) throws Exception {
    V1ClusterRoleList v1ClusterRoleList =
        rbacAuthorizationV1Api.listClusterRole(
            Boolean.FALSE.toString(),
            Boolean.FALSE,
            null,
            null,
            domain1Ls,
            null,
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1ClusterRoleList.getItems().size(), 0, "Number of cluster roles");
  }

  public void verifyNoClusterRoleBindings(String labelSelectors) throws Exception {
    V1ClusterRoleBindingList v1ClusterRoleBindingList =
        rbacAuthorizationV1Api.listClusterRoleBinding(
            Boolean.FALSE.toString(),
            Boolean.FALSE,
            null,
            null,
            labelSelectors,
            null,
            null,
            null,
            Boolean.FALSE);
    assertEquals(v1ClusterRoleBindingList.getItems().size(), 0, "Number of cluster role bindings");
  }

  /**
   * Utility method to get the pods in a namespace filtered by given label.
   *
   * @param namespace      - String namespace in which to look for the pods
   * @param labelSelectors - String selector to filter the pods in the name space
   * @return - V1PodList List of the pods in the given name space.
   */
  public V1PodList getPods(String namespace, String labelSelectors) {
    V1PodList v1PodList = null;
    try {
      v1PodList =
          coreV1Api.listNamespacedPod(
              namespace,
              Boolean.FALSE.toString(),
              Boolean.FALSE,
              null,
              null,
              labelSelectors,
              null,
              null,
              null,
              Boolean.FALSE);

    } catch (ApiException ex) {
      LoggerHelper.getLocal().log(Level.SEVERE, null, ex);
    }
    LoggerHelper.getLocal().log(
        Level.INFO,
        "Pods in namespace :{0} and label :{1} :{2}",
        new Object[]{namespace, labelSelectors, v1PodList.getItems().size()});
    return v1PodList;
  }

  /**
   * Utility method to get a pod matching the given name.
   *
   * @param namespace      - String namespace in which to look for the pods
   * @param labelSelectors - String selector to filter the pods in the name space
   * @param podName        - String name of the pod to query for
   * @return V1Pod object matching the podName
   */
  public V1Pod getPod(String namespace, String labelSelectors, String podName) {
    List<V1Pod> pods = getPods(namespace, labelSelectors).getItems();
    for (V1Pod pod : pods) {
      if (pod.getMetadata().getName().equals(podName)) {
        return pod;
      }
    }
    LoggerHelper.getLocal().log(Level.INFO, "POD NOT FOUND");
    return null;
  }

  /**
   * Utility method to determine if a pod is in Terminating status It detects the Terminating status
   * by looking at the metadata.getDeletionTimestamp field, a non null value means the pod is
   * terminating.
   *
   * @param namespace      - String namespace in which to look for the pods
   * @param labelSelectors - String selector to filter the pods in the name space
   * @param podName        - String name of the pod to query for
   * @return boolean true if the pod is in Terminating status
   */
  public boolean isPodTerminating(String namespace, String labelSelectors, String podName) {
    V1ObjectMeta metadata;
    V1Pod pod = getPod(namespace, labelSelectors, podName);
    metadata = pod != null ? pod.getMetadata() : null;
    if (metadata == null) {
      return false;
    }
    if (metadata.getDeletionTimestamp() != null) {
      LoggerHelper.getLocal().log(Level.INFO, metadata.getDeletionTimestamp().toString());
    } else {
      LoggerHelper.getLocal().log(Level.INFO, "DeletionTimestamp is null, which means pod is Running");
    }
    return metadata.getDeletionTimestamp() != null;
  }

  /**
   * Utility method to determine if a pod is in Running status It detects the Running status by
   * looking at the metadata.getDeletionTimestamp field, a null value means the pod is Running.
   *
   * @param namespace      - String namespace in which to look for the pods
   * @param labelSelectors - String selector to filter the pods in the name space
   * @param podName        - String name of the pod to query for
   * @return boolean true if the pod is in Terminating status
   */
  public boolean isPodRunning(String namespace, String labelSelectors, String podName) {
    return !isPodTerminating(namespace, labelSelectors, podName);
  }

}

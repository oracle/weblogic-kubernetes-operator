// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import java.util.List;

import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.kubernetes.actions.impl.AppBuilder;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.ClusterRoleBinding;
import oracle.weblogic.kubernetes.actions.impl.ConfigMap;
import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolume;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.Service;
import oracle.weblogic.kubernetes.actions.impl.ServiceAccount;
import oracle.weblogic.kubernetes.actions.impl.Traefik;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Docker;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WITParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WebLogicImageTool;


// this class essentially delegates to the impl classes, and "hides" all of the
// detail impl classes - tests would only ever call methods in here, never
// directly call the methods in the impl classes
public class TestActions {

  // ----------------------   operator  ---------------------------------

  /**
   * Install WebLogic Kubernetes Operator.
   *
   * @param params operator parameters for helm values
   * @return true if the operator is successfully installed, false otherwise.
   */
  public static boolean installOperator(OperatorParams params) {
    return Operator.install(params);
  }

  /**
   * Upgrade existing Operator release.
   *
   * @param params operator parameters for helm values
   * @return true if the operator is successfully upgraded, false otherwise.
   */
  public static boolean upgradeOperator(OperatorParams params) {
    return Operator.upgrade(params);
  }

  /**
   * Makes a REST call to the Operator to scale the domain.
   *
   * @param domainUID domainUid of the domain
   * @param clusterName cluster in the domain to scale
   * @param numOfServers number of servers to scale upto.
   * @return true on success, false otherwise
   */
  public static boolean scaleDomain(String domainUID, String clusterName, int numOfServers) {
    return Operator.scaleDomain(domainUID, clusterName, numOfServers);
  }

  /**
   * Uninstall the Operator release.
   *
   * @param params the parameters to helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */

  public static boolean uninstallOperator(HelmParams params) {
    return Operator.uninstall(params);
  }

  // ----------------------   domain  -----------------------------------

  /**
   * Create Domain Custom Resource.
   *
   * @param domain Domain custom resource model object
   * @return true on success, false otherwise
   */
  public static boolean createDomainCustomResource(oracle.weblogic.domain.Domain domain)
      throws ApiException {
    return Domain.createDomainCustomResource(domain);
  }

  /**
   * List Domain Custom Resources.
   *
   * @param namespace name of namespace
   * @return List of Domain Custom Resources
   */
  public static DomainList listDomainCustomResources(String namespace) {
    return Domain.listDomainCustomResources(namespace);
  }

  /**
   * Get the Domain Custom Resource.
   *
   * @param domainUID unique domain identifier
   * @param namespace name of namespace
   * @return Domain Custom Resource or null if Domain does not exist
   * @throws ApiException if Kubernetes client API call fails
   */
  public static oracle.weblogic.domain.Domain getDomainCustomResource(String domainUID,
      String namespace) throws ApiException {
    return Domain.getDomainCustomResource(domainUID, namespace);
  }

  /**
   * Shutdown the domain.
   *
   * @param domainUID unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean shutdown(String domainUID, String namespace) {
    return Domain.shutdown(domainUID, namespace);
  }

  /**
   * Restart the domain.
   *
   * @param domainUID unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean restart(String domainUID, String namespace) {
    return Domain.restart(domainUID, namespace);
  }

  /**
   * Delete a Domain Custom Resource.
   *
   * @param domainUID unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean deleteDomainCustomResource(String domainUID, String namespace) {
    return Domain.deleteDomainCustomResource(domainUID, namespace);
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUID unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainCustomResource(String domainUID, String namespace, V1Patch patch,
      String patchFormat) {
    return Domain.patchDomainCustomResource(domainUID, namespace, patch, patchFormat);
  }

  // ------------------------   ingress controller ----------------------

  /**
   * Install Traefik Operator.
   *
   * @param params parameters for helm values
   * @return true on success, false otherwise
   */
  public static boolean installTraefik(TraefikParams params) {
    return Traefik.install(params);
  }

  /**
   * Create Treafik Ingress.
   *
   * @param valuesYaml values yaml file to be used
   * @return true on success, false otherwise
   */
  public static boolean createIngress(String valuesYaml) {
    return Traefik.createIngress(valuesYaml);
  }

  // -------------------------  namespaces -------------------------------

  /**
   * Create Kubernetes namespace.
   *
   * @param name the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name) throws ApiException {
    return new Namespace().name(name).create();
  }

  /**
   * Create a namespace with unique name.
   *
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String createUniqueNamespace() throws ApiException {
    String name = Namespace.uniqueName();
    new Namespace().name(name).create();
    return name;
  }

  /**
   * List of namespaces in Kubernetes cluster.
   *
   * @return List of names of all namespaces in Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    return Namespace.listNamespaces();
  }

  /**
   * Delete a Kubernetes namespace.
   *
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteNamespace(String namespace) {
    return Namespace.delete(namespace);
  }

  // ------------------------ Docker image  -------------------------

  /**
   * Create a WITParams that contains the parameters for executing a WIT command.
   *
   * @return an instance of WITParams that contains the default values
   */
  public static WITParams defaultWITParams() {
    return
        WebLogicImageTool.defaultWITParams();
  }

  /**
   * Create an image using WDT models using WebLogic Image Tool.
   *
   * @param params - the parameters for creating a model-in-image Docker image
   * @return true if the operation succeeds
   */
  public static boolean createMIIImage(WITParams params) {
    return
        WebLogicImageTool
            .withParams(params)
            .updateImage();
  }

  // -------------------------   pv/pvc  ---------------------------------

  /**
   * Create a Kubernetes Persistent Volume.
   *
   * @param persistentVolume V1PersistentVolume object containing persistent volume
   *     configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPersistentVolume(V1PersistentVolume persistentVolume) throws ApiException {
    return PersistentVolume.create(persistentVolume);
  }

  /**
   * Delete the Kubernetes Persistent Volume.
   *
   * @param name name of the Persistent Volume
   * @return true if successful, false otherwise
   */
  public static boolean deletePersistentVolume(String name) {
    return PersistentVolume.delete(name);
  }

  /**
   * Create a Kubernetes Persistent Volume Claim.
   *
   * @param persistentVolumeClaim V1PersistentVolumeClaim object containing Kubernetes
   *     persistent volume claim configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPersistentVolumeClaim(V1PersistentVolumeClaim persistentVolumeClaim)
      throws ApiException {
    return PersistentVolumeClaim.create(persistentVolumeClaim);
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim.
   *
   * @param name name of the Persistent Volume Claim
   * @param namespace name of the namespace
   * @return true if successful, false otherwise
   */
  public static boolean deletePersistentVolumeClaim(String name, String namespace) {
    return PersistentVolumeClaim.delete(name, namespace);
  }

  // --------------------------  secret  ----------------------------------

  /**
   * Create a Kubernetes Secret.
   *
   * @param secret V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createSecret(V1Secret secret) throws ApiException {
    return Secret.create(secret);
  }

  /**
   * Delete Kubernetes Secret.
   *
   * @param name name of the Secret
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteSecret(String name, String namespace) {
    return Secret.delete(name, namespace);
  }

  // -------------------------- config map ---------------------------------

  /**
   * Create Kubernetes Config Map.
   *
   * @param configMap V1ConfigMap object containing config map configuration data
   * @return true on success
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createConfigMap(V1ConfigMap configMap) throws ApiException {
    return ConfigMap.create(configMap);
  }

  /**
   /**
   * Delete Kubernetes Config Map.
   *
   * @param name name of the Config Map
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteConfigMap(String name, String namespace) {
    return ConfigMap.delete(name, namespace);
  }

  // -------------------------- Service ---------------------------------

  /**
   * Create Kubernetes Service.
   *
   * @param service V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createService(V1Service service) throws ApiException {
    return Service.create(service);
  }

  /**
   * Delete Kubernetes Service.
   *
   * @param name name of the Service
   * @param namespace name of namespace
   * @return true if successful
   */
  public static boolean deleteService(String name, String namespace) {
    return Service.delete(name, namespace);
  }

  // ------------------------ service account  --------------------------

  /**
   * Create a Kubernetes Service Account.
   *
   * @param serviceAccount V1ServiceAccount object containing service account configuration data
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createServiceAccount(V1ServiceAccount serviceAccount) throws ApiException {
    return ServiceAccount.create(serviceAccount);
  }

  /**
   * Delete a Kubernetes Service Account.
   *
   * @param name name of the Service Account
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteServiceAccount(String name, String namespace) {
    return ServiceAccount.delete(name, namespace);
  }

  // ----------------------- Role-based access control (RBAC)   ---------------------------

  /**
   * Create a Cluster Role Binding.
   *
   * @param clusterRoleBinding V1ClusterRoleBinding object containing role binding configuration
   *     data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createClusterRoleBinding(V1ClusterRoleBinding clusterRoleBinding)
      throws ApiException {
    return ClusterRoleBinding.create(clusterRoleBinding);
  }

  /**
   * Delete Cluster Role Binding.
   *
   * @param name name of cluster role binding
   * @return true if successful, false otherwise
   */
  public static boolean deleteClusterRoleBinding(String name) {
    return ClusterRoleBinding.delete(name);
  }

  // ----------------------- helm -----------------------------------

  /**
   * List releases.
   *
   * @param params namespace
   * @return true on success
   */
  public static boolean helmList(HelmParams params) {
    return Helm.list(params);
  }
  
  // ------------------------ Application Builder  -------------------------

  /**
   * Create an AppParams instance that contains the default values.
   *
   * @return an AppParams instance that contains the default values
   */
  public static AppParams defaultAppParams() {
    return
        AppBuilder.defaultAppParams();
  }

  /**
   * Create an application archive that can be used by WebLogic Image Tool
   * to create an image with the application for a model-in-image use case.
   *
   * @param params the parameters for creating a model-in-image Docker image
   * @return true if the operation succeeds
   */
  public static boolean buildAppArchive(AppParams params) {
    return
        AppBuilder
            .withParams(params)
            .build();
  }

  // ------------------------ Docker --------------------------------------

  /**
   * Log in to a Docker registry.
   * @param registryName registry name
   * @param username user
   * @param password password
   * @return true if successfull
   */
  public static boolean dockerLogin(String registryName, String username, String password) {
    return Docker.login(registryName, username, password);
  }

  /**
   * Push an image to a registry.
   * @param image fully qualified docker image, image name:image tag
   * @return true if successfull
   */
  public static boolean dockerPush(String image) {
    return Docker.push(image);
  }

  /**
   * Delete docker image.
   * @param image image name:image tag
   * @return true if delete image is successful
   */
  public static boolean deleteImage(String image) {
    return Docker.deleteImage(image);
  }

  // ------------------------ where does this go  -------------------------

  /**
   * Deploy the application to the given target.
   *
   * @param appName     the name of the application
   * @param appLocation location of the war/ear file
   * @param t3Url       the t3 url to connect
   * @param username    username
   * @param password    password
   * @param target      the name of the target
   * @return true on success, false otherwise
   */
  public static boolean deployApplication(String appName, String appLocation, String t3Url,
                                          String username, String password, String target) {
    return true;
  }
}

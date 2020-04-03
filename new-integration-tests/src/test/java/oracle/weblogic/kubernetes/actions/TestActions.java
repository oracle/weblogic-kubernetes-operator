// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import oracle.weblogic.kubernetes.actions.impl.ConfigMap;
import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolume;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.Service;
import oracle.weblogic.kubernetes.actions.impl.Traefik;
import oracle.weblogic.kubernetes.actions.impl.primitive.InstallParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Installer;
import oracle.weblogic.kubernetes.actions.impl.primitive.WITParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.WebLogicImageTool;

// this class essentially delegates to the impl classes, and "hides" all of the
// detail impl classes - tests would only ever call methods in here, never
// directly call the methods in the impl classes
public class TestActions {

  // ----------------------   operator  ---------------------------------

  /**
   * Install WebLogic Kubernetes Operator
   *
   * @param name operator release name
   * @param namespace the name of the namespace
   * @param params operator parameters for helm values
   * @return true if the operator is successfully installed, false otherwise.
   */
  public static boolean installOperator(String name, String namespace, OperatorParams params) {
    return Operator.install(name, namespace, params);
  }

  /**
   * Upgrade existing Operator release
   *
   * @param name operator release name
   * @param namespace the name of the namespace
   * @param params operator parameters for helm values
   * @return true if the operator is successfully upgraded, false otherwise.
   */
  public static boolean upgradeOperator(String name, String namespace, OperatorParams params) {
    return Operator.upgrade(name, namespace, params);
  }

  /**
   * Makes a REST call to the Operator to scale the domain.
   *
   * @param domainUID - domainUid of the domain
   * @param clusterName - cluster in the domain to scale
   * @param numOfServers - number of servers to scale upto.
   * @return true on success, false otherwise
   */
  public static boolean scaleDomain(String domainUID, String clusterName, int numOfServers) {
    return Operator.scaleDomain(domainUID, clusterName, numOfServers);
  }

  /**
   * Delete the Operator release
   *
   * @param name operator release name
   * @param namespace the name of the namespace
   * @return true on success, false otherwise
   */

  public static boolean deleteOperator(String name, String namespace) {
    return Operator.delete(name, namespace);
  }

  // ----------------------   domain  -----------------------------------

  /**
   * Create domain custom resource from the given domain yaml file.
   *
   * @param domainUID - unique domain identifier
   * @param namespace - name of namespace
   * @param domainYAML - path to a file containing domain custom resource spec in yaml format
   * @return true on success, false otherwise
   */
  public static boolean createDomainCustomResource(String domainUID, String namespace,
      String domainYAML) {
    return Domain.createDomainCustomResource(domainUID, namespace, domainYAML);
  }

  /**
   * List domain custom resources for a given namespace.
   *
   * @param namespace - name of namespace
   * @return List of names of domain custom resources
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static List<String> listDomainCustomResources(String namespace) throws ApiException {
    return Domain.listDomainCustomResources(namespace);
  }

  /**
   * Shutdown the domain
   *
   * @return true on success, false otherwise
   */
  public static boolean shutdown(String domainUID, String namespace) {
    return Domain.shutdown(domainUID, namespace);
  }

  /**
   * @return true on success, false otherwise
   */
  public static boolean restart(String domainUID, String namespace) {
    return Domain.restart(domainUID, namespace);
  }

  /**
   * Delete a domain custom resource for a given unique domain identifier in a
   * given namespace.
   *
   * @param domainUID - unique domain identifier
   * @param namespace - name of namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deleteDomainCustomResource(String domainUID, String namespace)
      throws ApiException {
    return Domain.deleteDomainCustomResource(domainUID, namespace);
  }

  // ------------------------   ingress controller ----------------------

  /**
   * Install Traefik Operator
   *
   * @param valuesYaml values yaml file to be used
   * @return true on success, false otherwise
   */
  public static boolean installTraefik(String valuesYaml) {
    return Traefik.install(valuesYaml);
  }

  /**
   * Create Treafik Ingress
   *
   * @param valuesYaml values yaml file to be used
   * @return true on success, false otherwise
   */
  public static boolean createIngress(String valuesYaml) {
    return Traefik.createIngress(valuesYaml);
  }

  // -------------------------  namespaces -------------------------------

  /**
   * Create Kubernetes namespace
   *
   * @param name - V1Namespace object containing namespace configuration data
   * @return true on success, false otherwise
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createNamespace(V1Namespace name) throws ApiException {
    return Namespace.create(name);
  }

  /**
   * Create a namespace with unique name
   *
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static String createUniqueNamespace() throws ApiException {
    return Namespace.createUniqueNamespace();
  }

  /**
   * List of namespaces in Kubernetes cluster
   *
   * @return - List of names of all namespaces in Kubernetes cluster
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    return Namespace.listNamespaces();
  }

  /**
   * Delete a Kubernetes namespace
   *
   * @param namespace - V1Namespace object containing name space configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deleteNamespace(V1Namespace namespace) throws ApiException {
    return Namespace.delete(namespace);
  }
  // ------------------------ Docker image  -------------------------

  public static boolean createMIIImage(WITParams params) {
    return
        new WebLogicImageTool()
            .with(params)
            .updateImage();
  }

  // ------------------------ Installer  -------------------------

  public static boolean verifyAndInstallWIT(String version, String location) {
    return new Installer()
        .with(new InstallParams()
            .type("WIT")
            .version(version)
            .location(location))
        .download();
  }

  public static boolean verifyAndInstallWDT(String version, String location) {
    return new Installer()
        .with(new InstallParams()
            .type("WDT")
            .version(version)
            .location(location))
        .download();
  }

  // -------------------------   pv/pvc  ---------------------------------

  /**
   * Create Kubernetes Persistent Volume using the yaml provided
   *
   * @param pvYaml the persistent volume yaml file
   * @return true on success, false otherwise
   */
  public static boolean createPersistentVolume(String pvYaml) {
    return PersistentVolume.create(pvYaml);
  }

  /**
   *
   * @param persistentVolume - V1PersistentVolume object containing Kubernetes persistent volume
   *     configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createPersistentVolume(V1PersistentVolume persistentVolume) throws ApiException {
    return PersistentVolume.create(persistentVolume);
  }

  /**
   * Delete the Kubernetes Persistent Volume
   *
   * @param persistentVolume - V1PersistentVolume object containing PV configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deletePersistentVolume(V1PersistentVolume persistentVolume)
      throws ApiException {
    return PersistentVolume.delete(persistentVolume);
  }

  /**
   * Create a Kubernetes Persistent Volume Claim using the specified path to a file in yaml format
   *
   * @param pvcYaml the persistent volume claim yaml file
   * @return true on success, false otherwise
   */
  public static boolean createPersistentVolumeClaim(String pvcYaml) {
    return PersistentVolumeClaim.create(pvcYaml);
  }

  /**
   * @param persistentVolumeClaim - V1PersistentVolumeClaim object containing Kubernetes
   *     persistent volume claim configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createPersistentVolumeClaim(V1PersistentVolumeClaim persistentVolumeClaim)
      throws ApiException {
    return PersistentVolumeClaim.create(persistentVolumeClaim);
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim
   *
   * @param persistentVolumeClaim - V1PersistentVolumeClaim object containing PVC configuration
   *     data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deletePersistentVolumeClaim(V1PersistentVolumeClaim persistentVolumeClaim)
      throws ApiException {
    return PersistentVolumeClaim.delete(persistentVolumeClaim);
  }

  // --------------------------  secret  ----------------------------------

  /**
   * Create Kubernetes Secret
   *
   * @param secret - V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createSecret(V1Secret secret) throws ApiException {
    return Secret.create(secret);
  }

  /**
   * Delete Kubernetes Secret
   *
   * @param secret - V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deleteSecret(V1Secret secret) throws ApiException {
    return Secret.delete(secret);
  }

  // -------------------------- config map ---------------------------------

  /**
   * Create Kubernetes Config Map
   *
   * @param configMap - V1ConfigMap object containing config map configuration data
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean createConfigMap(V1ConfigMap configMap) throws ApiException {
    return ConfigMap.create(configMap);
  }

  /**
   * Delete Kubernetes Config Map
   *
   * @param configMap - V1ConfigMap object containing config map configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deleteConfigMap(V1ConfigMap configMap) throws ApiException {
    return ConfigMap.delete(configMap);
  }

  // -------------------------- Service ---------------------------------

  /**
   * Create Kubernetes Service
   *
   * @param service - V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createService(V1Service service) throws ApiException {
    return Service.create(service);
  }

  /**
   * Delete Kubernetes Service
   *
   * @param service - V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deleteService(V1Service service) throws ApiException {
    return Service.delete(service);
  }

  // ------------------------ where does this go  -------------------------

  /**
   * Deploy the application to the given target
   *
   * @param appName the name of the application
   * @param appLocation location of the war/ear file
   * @param t3Url the t3 url to connect
   * @param username username
   * @param password password
   * @param target the name of the target
   * @return true on success, false otherwise
   */
  public static boolean deployApplication(String appName, String appLocation, String t3Url,
      String username, String password, String target) {
    return true;
  }

  // etc...

}

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import java.io.IOException;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.ConfigMap;
import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolume;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.Traefik;
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
   * @param name the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name) throws ApiException {
    return Namespace.createNamespace(name);
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
   * Delete a namespace for the given name
   *
   * @param name - name of namespace
   * @return true if successful delete, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deleteNamespace(String name) throws ApiException {
    return Namespace.deleteNamespace(name);
  }

  // ------------------------ Docker image  -------------------------

  /**
   * Create a WITParams that contains the parameters for executing a WIT command
   *
   * @return an instance of WITParams that contains the default values
   */
  public static WITParams withWITParams() {
    return 
        WebLogicImageTool.withDefaults();
  }
 
  /**
   * Create an image using WDT models using WebLogic Image Tool
   *
   * @param params - the parameters for creating a model-in-image Docker image
   * @return true if successful delete, false otherwise
   */
  public static boolean createMIIImage(WITParams params) {
    return 
        WebLogicImageTool
            .withParams(params)
            .updateImage();
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
   * Delete the Kubernetes Persistent Volume
   *
   * @param pvName the name of the Persistent Volume
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deletePersistentVolume(String pvName) throws ApiException {
    return PersistentVolume.delete(pvName);
  }

  /**
   * Create Kubernetes Persistent Volume Claim using the yaml provided
   *
   * @param pvcYaml the persistent volume claim yaml file
   * @return true on success, false otherwise
   */
  public static boolean createPersistentVolumeClaim(String pvcYaml) {
    return PersistentVolumeClaim.create(pvcYaml);
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim
   *
   * @param pvcName the name of the Persistent Volume Claim
   * @param namespace the namespace of the Persistent Volume Claim
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deletePersistentVolumeClaim(String pvcName, String namespace)
      throws ApiException {
    return PersistentVolumeClaim.delete(pvcName, namespace);
  }

  // --------------------------  secret  ----------------------------------

  /**
   * Create Kubernetes Secret
   *
   * @param secretName the name of the secret
   * @param userName username
   * @param password password
   * @param namespace the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   *
   */
  public static boolean createSecret(String secretName,
      String userName, String password, String namespace) throws ApiException {
    return Secret.create(secretName, userName, password, namespace);
  }

  /**
   * Delete Kubernetes Secret
   *
   * @param secretName the name of the secret
   * @param namespace the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deleteSecret(String secretName, String namespace) throws ApiException {
    return Secret.delete(secretName, namespace);
  }
  // -------------------------- config map ---------------------------------

  /**
   * Create Kubernetes Config Map
   *
   * @param cmName the name of the Config Map
   * @param namespace the name of the namespace
   * @param fromFile file or dir path
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   * @throws IOException - if fail to read fromFile
   */
  public static boolean createConfigMap(String cmName, String namespace, String fromFile)
      throws IOException, ApiException {
    return ConfigMap.create(cmName, namespace, fromFile);
  }

  /**
   * Delete Kubernetes Config Map
   *
   * @param cmName the name of the Config Map
   * @param namespace the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deleteConfigMap(String cmName, String namespace) throws ApiException {
    return ConfigMap.delete(cmName, namespace);
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

}

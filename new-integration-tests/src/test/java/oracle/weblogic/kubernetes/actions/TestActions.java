// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.kubernetes.actions.impl.AppBuilder;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.ClusterRoleBinding;
import oracle.weblogic.kubernetes.actions.impl.ConfigMap;
import oracle.weblogic.kubernetes.actions.impl.Domain;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.actions.impl.Job;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Nginx;
import oracle.weblogic.kubernetes.actions.impl.NginxParams;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolume;
import oracle.weblogic.kubernetes.actions.impl.PersistentVolumeClaim;
import oracle.weblogic.kubernetes.actions.impl.Pod;
import oracle.weblogic.kubernetes.actions.impl.Secret;
import oracle.weblogic.kubernetes.actions.impl.Service;
import oracle.weblogic.kubernetes.actions.impl.ServiceAccount;
import oracle.weblogic.kubernetes.actions.impl.primitive.Docker;
import oracle.weblogic.kubernetes.actions.impl.primitive.Helm;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.actions.impl.primitive.WebLogicImageTool;
import oracle.weblogic.kubernetes.actions.impl.primitive.WitParams;
import oracle.weblogic.kubernetes.extensions.ImageBuilders;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.joda.time.DateTime;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

// this class essentially delegates to the impl classes, and "hides" all of the
// detail impl classes - tests would only ever call methods in here, never
// directly call the methods in the impl classes
public class TestActions {
  // ----------------------   operator  ---------------------------------

  /**
   * Install WebLogic Kubernetes Operator.
   *
   * @param params operator parameters for Helm values
   * @return true if the operator is successfully installed, false otherwise.
   */
  public static boolean installOperator(OperatorParams params) {
    return Operator.install(params);
  }

  /**
   * Upgrade existing Operator release.
   *
   * @param params operator parameters for Helm values
   * @return true if the operator is successfully upgraded, false otherwise.
   */
  public static boolean upgradeOperator(OperatorParams params) {
    return Operator.upgrade(params);
  }

  /**
   * Uninstall the Operator release.
   *
   * @param params the parameters to Helm uninstall command, release name and namespace
   * @return true on success, false otherwise
   */

  public static boolean uninstallOperator(HelmParams params) {
    return Operator.uninstall(params);
  }

  /**
   * Image Name for the Operator. Uses branch name for image tag in local runs
   * and branch name, build id for image tag in Jenkins runs.
   *
   * @return image name
   */
  public static String getOperatorImageName() {
    return Operator.getImageName();
  }

  /**
   * Builds a Docker Image for the Oracle WebLogic Kubernetes Operator.
   *
   * @param image image name and tag in 'name:tag' format
   * @return true on success
   */
  public static boolean buildOperatorImage(String image) {
    return Operator.buildImage(image);
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
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return Domain Custom Resource or null if Domain does not exist
   * @throws ApiException if Kubernetes client API call fails
   */
  public static oracle.weblogic.domain.Domain getDomainCustomResource(String domainUid,
                                                                      String namespace) throws ApiException {
    return Domain.getDomainCustomResource(domainUid, namespace);
  }

  /**
   * Shutdown the domain.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean shutdown(String domainUid, String namespace) {
    return Domain.shutdown(domainUid, namespace);
  }

  /**
   * Restart the domain.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean restart(String domainUid, String namespace) {
    return Domain.restart(domainUid, namespace);
  }

  /**
   * Delete a Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true on success, false otherwise
   */
  public static boolean deleteDomainCustomResource(String domainUid, String namespace) {
    return Domain.deleteDomainCustomResource(domainUid, namespace);
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *                    "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainCustomResource(String domainUid, String namespace, V1Patch patch,
                                                  String patchFormat) {
    return Domain.patchDomainCustomResource(domainUid, namespace, patch, patchFormat);
  }

  /**
   * Scale the cluster of the domain in the specified namespace .
   *
   * @param domainUid domainUid of the domain to be scaled
   * @param clusterName cluster in the domain to be scaled
   * @param numOfServers number of servers to be scaled to.
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean scaleCluster(String domainUid, String namespace, String clusterName, int numOfServers)
      throws ApiException {
    return Domain.scaleCluster(domainUid, namespace, clusterName, numOfServers);
  }

  // ------------------------   Ingress Controller ----------------------

  /**
   * Install NGINX ingress controller.
   *
   * @param params the parameters to Helm install command, such as release name, namespace, repo url,
   *               repo name and chart name
   * @return true on success, false otherwise
   */
  public static boolean installNginx(NginxParams params) {
    return Nginx.install(params);
  }

  /**
   * Create an ingress for the WebLogic domain with domainUid in the specified domain namespace.
   * The ingress host is set to 'domainUid.clusterName.test'.
   *
   * @param ingressName the name of the ingress to be created
   * @param domainNamespace the WebLogic domain namespace in which to create the ingress
   * @param domainUid WebLogic domainUid which is backend to the ingress
   * @param clusterNameMsPortMap the map with key as cluster name and value as managed server port of the cluster
   * @return list of ingress hosts or null if got ApiException when calling Kubernetes client API to create ingress
   */
  public static List<String> createIngress(String ingressName, String domainNamespace, String domainUid,
                                           Map<String, Integer> clusterNameMsPortMap) {
    return Nginx.createIngress(ingressName, domainNamespace, domainUid, clusterNameMsPortMap);
  }

  /**
   * Upgrade NGINX release.
   *
   * @param params the parameters to Helm upgrade command, such as release name and http/https nodeport
   * @return true on success, false otherwise
   */
  public static boolean upgradeNginx(NginxParams params) {
    return Nginx.upgrade(params);
  }

  /**
   * Uninstall the NGINX release.
   *
   * @param params the parameters to Helm uninstall command, such as release name and namespace
   * @return true on success, false otherwise
   */
  public static boolean uninstallNginx(HelmParams params) {
    return Nginx.uninstall(params);
  }

  /**
   * Get a list of ingresses in the specified namespace.
   *
   * @param namespace in which to list all the ingresses
   * @return list of ingress names in the specified namespace
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listIngresses(String namespace) throws ApiException {
    return Nginx.listIngresses(namespace);
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
  public static WitParams defaultWitParams() {
    return
        WebLogicImageTool.defaultWitParams();
  }

  /**
   * Create an image using WDT models using WebLogic Image Tool.
   *
   * @param params - the parameters for creating a model-in-image Docker image
   * @return true if the operation succeeds
   */
  public static boolean createImage(WitParams params) {
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

  /**
   * Get node port of a namespaced service given the channel name.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service
   * @param channelName name of the channel for which to get the nodeport
   * @return node port if service and channel is found, otherwise -1
   */
  public static int getServiceNodePort(String namespace, String serviceName, String channelName) {
    return Service.getServiceNodePort(namespace, serviceName, channelName);
  }

  /**
   * Get namespaced service object.
   *
   * @param namespace name of the namespace in which to get the service
   * @param serviceName name of the service object to get
   * @return V1Service object if found, otherwise null
   */
  public static V1Service getNamespacedService(String namespace, String serviceName) {
    return Service.getNamespacedService(namespace, serviceName);
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

  // ----------------------- Helm -----------------------------------

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
   *
   * @param registryName name of Docker registry
   * @param username username for the Docker registry
   * @param password password for the Docker registry
   * @return true if successful, false otherwise
   */
  public static boolean dockerLogin(String registryName, String username, String password) {
    return Docker.login(registryName, username, password);
  }

  /**
   * Push an image to a registry.
   *
   * @param image fully qualified docker image, image name:image tag
   * @return true if successful
   */
  public static boolean dockerPush(String image) {
    boolean result = Docker.push(image);
    if (result) {
      ImageBuilders.registerPushedImage(image);
    }
    return result;
  }

  /**
   * Tag an image.
   *
   * @param originalImage fully qualified original docker image, image name:image tag
   * @param taggedImage fully qualified tagged docker image, image name:image tag
   * @return true if successful
   */
  public static boolean dockerTag(String originalImage, String taggedImage) {
    return Docker.tag(originalImage, taggedImage);
  }

  /**
   * Pull an image from a registry.
   *
   * @param image fully qualified docker image, image name:image tag
   * @return true if successful
   */
  public static boolean dockerPull(String image) {
    return Docker.pull(image);
  }

  /**
   * Delete docker image.
   *
   * @param image image name:image tag
   * @return true if delete image is successful
   */
  public static boolean deleteImage(String image) {
    return Docker.deleteImage(image);
  }

  /**
   * Create Docker registry configuration in json object.
   *
   * @param username username for the docker registry
   * @param password password for the docker registry
   * @param email email for the docker registry
   * @param registry docker registry name
   * @return json object for the Docker registry configuration
   */
  public static JsonObject createDockerConfigJson(String username, String password, String email, String registry) {
    return Docker.createDockerConfigJson(username, password, email, registry);
  }

  // ----------------------- Execute a Command   ---------------------------

  /**
   * Execute a command in a container of a Kubernetes pod.
   *
   * @param namespace The Kubernetes namespace that the pod is in
   * @param podName The name of the Kubernetes pod where the command is expected to run
   * @param containerName The container in the Pod where the command is to be run. If no
   *                         container name is provided than the first container in the Pod is used.
   * @param redirectToStdout copy process output to stdout
   * @param command The command to run
   * @return result of command execution
   * @throws IOException if an I/O error occurs.
   * @throws ApiException if Kubernetes client API call fails
   * @throws InterruptedException if any thread has interrupted the current thread
   */
  public static ExecResult execCommand(
      String namespace,
      String podName,
      String containerName,
      boolean redirectToStdout,
      String... command
  ) throws IOException, ApiException, InterruptedException {
    // get the pod given the namespace and name of the pod
    // no label selector is needed (thus null below)
    final V1Pod pod = Kubernetes.getPod(namespace, null, podName);
    if (pod == null) {
      throw new IllegalArgumentException(
          String.format("The pod %s does not exist in namespace %s!", podName, namespace));
    }
    return Exec.exec(pod, containerName, redirectToStdout, command);
  }

  // ------------------------ Jobs ----------------------------------

  /**
   * Create a job.
   *
   * @param jobBody V1Job object containing job configuration data
   * @return String job name if job creation is successful
   * @throws ApiException when create job fails
   */
  public static String createNamespacedJob(V1Job jobBody) throws ApiException {
    return Job.createNamespacedJob(jobBody);
  }

  /**
   * Get V1Job object if any exists in the namespace with given job name.
   *
   * @param jobName name of the job
   * @param namespace name of the namespace in which to get the job object
   * @return V1Job object if any exists otherwise null
   * @throws ApiException when Kubernetes cluster query fails
   */
  public static V1Job getJob(String jobName, String namespace) throws ApiException {
    return Job.getJob(jobName, namespace);
  }

  // ----------------------   pod  ---------------------------------

  /**
   * Get the creationTimestamp for a given pod with following parameters.
   *
   * @param namespace namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod
   * @return creationTimestamp from metadata section of the Pod
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static DateTime getPodCreationTimestamp(String namespace, String labelSelector, String podName)
      throws ApiException {
    return Pod.getPodCreationTimestamp(namespace, labelSelector, podName);
  }

  /**
   * Get the Pod object with following parameters.
   *
   * @param namespace namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName name of the pod
   * @return V1Pod pod object
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static V1Pod getPod(String namespace, String labelSelector, String podName) throws ApiException {
    return Pod.getPod(namespace, labelSelector, podName);
  }

  /**
   * Get a pod's log.
   *
   * @param podName name of the pod
   * @param namespace name of the namespace
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   **/
  public static String getPodLog(String podName, String namespace) throws ApiException {
    return Pod.getPodLog(podName, namespace);
  }

  /**
   * List Kubernetes pods in a namespace.
   *
   * @param namespace name of namespace
   * @param labelSelectors with which pods are decorated
   * @return V1PodList list of pods
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1PodList listPods(String namespace, String labelSelectors) throws ApiException {
    return Pod.listPods(namespace, labelSelectors);
  }

  /**
   * Get the weblogic.domainRestartVersion label from a given pod.
   *
   * @param namespace in which to check for the pod existence
   * @param labelSelector in the format "weblogic.domainUID in (%s)"
   * @param podName  name of the pod
   * @return value of weblogic.domainRestartVersion label, null if unset or the pod is not available
   * @throws ApiException when there is error in querying the cluster
   */
  public static String getPodRestartVersion(String namespace, String labelSelector, String podName)
      throws ApiException {
    return Kubernetes.getPodRestartVersion(namespace, labelSelector, podName);
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

  /**
   * Patch the domain resource with a new restartVersion.
   *
   * @param domainResourceName name of the domain resource
   * @param namespace Kubernetes namespace that the domain is hosted
   * @return restartVersion new restartVersion of the domain resource
   */
  public static String patchDomainResourceWithNewRestartVersion(
      String domainResourceName, String namespace) {
    String oldVersion = assertDoesNotThrow(
        () -> getDomainCustomResource(domainResourceName, namespace).getSpec().getRestartVersion(),
        String.format("Failed to get the restartVersion of %s in namespace %s", domainResourceName, namespace));
    int newVersion = oldVersion == null ? 1 : Integer.valueOf(oldVersion) + 1;
    logger.info("Update domain resource {0} in namespace {1} restartVersion from {2} to {3}",
        domainResourceName, namespace, oldVersion, newVersion);

    StringBuffer patchStr = new StringBuffer("[{");
    patchStr.append(" \"op\": \"replace\",")
        .append(" \"path\": \"/spec/restartVersion\",")
        .append(" \"value\": \"")
        .append(newVersion)
        .append("\"")
        .append(" }]");

    logger.info("Restart version patch string: {0}", patchStr);
    V1Patch patch = new V1Patch(new String(patchStr));
    boolean rvPatched = assertDoesNotThrow(() ->
            patchDomainCustomResource(domainResourceName, namespace, patch, "application/json-patch+json"),
        "patchDomainCustomResource(restartVersion)  failed ");
    assertTrue(rvPatched, "patchDomainCustomResource(restartVersion) failed");

    return String.valueOf(newVersion);
  }

}

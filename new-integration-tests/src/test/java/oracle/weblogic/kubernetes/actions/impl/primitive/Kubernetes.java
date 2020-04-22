// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.extended.generic.GenericKubernetesApi;
import io.kubernetes.client.extended.generic.KubernetesApiResponse;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.apis.RbacAuthorizationV1Api;
import io.kubernetes.client.openapi.models.V1ClusterRoleBinding;
import io.kubernetes.client.openapi.models.V1ClusterRoleBindingList;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentList;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobList;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceBuilder;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimList;
import io.kubernetes.client.openapi.models.V1PersistentVolumeList;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import io.kubernetes.client.openapi.models.V1ReplicaSetList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.ClientBuilder;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainList;
import oracle.weblogic.kubernetes.extensions.LoggedTest;

// TODO ryan - in here we want to implement all of the kubernetes
// primitives that we need, using the API, not spawning a process
// to run kubectl.
public class Kubernetes implements LoggedTest {

  public static Random RANDOM = new Random(System.currentTimeMillis());
  private static String PRETTY = "false";
  private static Boolean ALLOW_WATCH_BOOKMARKS = false;
  private static String RESOURCE_VERSION = "";
  private static Integer TIMEOUT_SECONDS = 5;
  private static String DOMAIN_GROUP = "weblogic.oracle";
  private static String DOMAIN_VERSION = "v7";
  private static String DOMAIN_PLURAL = "domains";

  // Core Kubernetes API clients
  private static ApiClient apiClient = null;
  private static CoreV1Api coreV1Api = null;
  private static CustomObjectsApi customObjectsApi = null;
  private static RbacAuthorizationV1Api rbacAuthApi = null;

  // Extended GenericKubernetesApi clients
  private static GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configMapClient = null;
  private static GenericKubernetesApi<V1ClusterRoleBinding, V1ClusterRoleBindingList> roleBindingClient = null;
  private static GenericKubernetesApi<Domain, DomainList> crdClient = null;
  private static GenericKubernetesApi<V1Deployment, V1DeploymentList> deploymentClient = null;
  private static GenericKubernetesApi<V1Job, V1JobList> jobClient = null;
  private static GenericKubernetesApi<V1Namespace, V1NamespaceList> namespaceClient = null;
  private static GenericKubernetesApi<V1Pod, V1PodList> podClient = null;
  private static GenericKubernetesApi<V1PersistentVolume, V1PersistentVolumeList> pvClient = null;
  private static GenericKubernetesApi<V1PersistentVolumeClaim, V1PersistentVolumeClaimList> pvcClient = null;
  private static GenericKubernetesApi<V1ReplicaSet, V1ReplicaSetList> rsClient = null;
  private static GenericKubernetesApi<V1Secret, V1SecretList> secretClient = null;
  private static GenericKubernetesApi<V1Service, V1ServiceList> serviceClient = null;
  private static GenericKubernetesApi<V1ServiceAccount, V1ServiceAccountList> serviceAccountClient = null;

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
      apiClient = Configuration.getDefaultApiClient();
      coreV1Api = new CoreV1Api();
      customObjectsApi = new CustomObjectsApi();
      rbacAuthApi = new RbacAuthorizationV1Api();
      initializeGenericKubernetesApiClients();
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  /**
   * Create static instances of GenericKubernetesApi clients.
   */
  private static void initializeGenericKubernetesApiClients() {
    // Invocation parameters aren't changing so create them as statics
    configMapClient =
        new GenericKubernetesApi<>(
            V1ConfigMap.class,  // the api type class
            V1ConfigMapList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "configmaps", // the resource plural
            apiClient //the api client
        );

    crdClient =
        new GenericKubernetesApi<>(
            Domain.class,  // the api type class
            DomainList.class, // the api list type class
            DOMAIN_GROUP, // the api group
            DOMAIN_VERSION, // the api version
            DOMAIN_PLURAL, // the resource plural
            apiClient //the api client
        );

    deploymentClient =
        new GenericKubernetesApi<>(
            V1Deployment.class,  // the api type class
            V1DeploymentList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "deployments", // the resource plural
            apiClient //the api client
        );

    jobClient =
        new GenericKubernetesApi<>(
            V1Job.class,  // the api type class
            V1JobList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "jobs", // the resource plural
            apiClient //the api client
        );

    namespaceClient =
        new GenericKubernetesApi<>(
            V1Namespace.class, // the api type class
            V1NamespaceList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "namespaces", // the resource plural
            apiClient //the api client
        );

    podClient =
        new GenericKubernetesApi<>(
            V1Pod.class,  // the api type class
            V1PodList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "pods", // the resource plural
            apiClient //the api client
        );

    pvClient =
        new GenericKubernetesApi<>(
            V1PersistentVolume.class,  // the api type class
            V1PersistentVolumeList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "persistentvolumes", // the resource plural
            apiClient //the api client
        );

    pvcClient =
        new GenericKubernetesApi<>(
            V1PersistentVolumeClaim.class,  // the api type class
            V1PersistentVolumeClaimList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "persistentvolumeclaims", // the resource plural
            apiClient //the api client
        );

    rsClient =
        new GenericKubernetesApi<>(
            V1ReplicaSet.class, // the api type class
            V1ReplicaSetList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "replicasets", // the resource plural
            apiClient //the api client
        );

    roleBindingClient =
        new GenericKubernetesApi<>(
            V1ClusterRoleBinding.class, // the api type class
            V1ClusterRoleBindingList.class, // the api list type class
            "rbac.authorization.k8s.io", // the api group
            "v1", // the api version
            "clusterrolebindings", // the resource plural
            apiClient //the api client
        );

    secretClient =
        new GenericKubernetesApi<>(
            V1Secret.class,  // the api type class
            V1SecretList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "serviceaccounts", // the resource plural
            apiClient //the api client
        );

    serviceClient =
        new GenericKubernetesApi<>(
            V1Service.class,  // the api type class
            V1ServiceList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "services", // the resource plural
            apiClient //the api client
        );

    serviceAccountClient =
        new GenericKubernetesApi<>(
            V1ServiceAccount.class,  // the api type class
            V1ServiceAccountList.class, // the api list type class
            "", // the api group
            "v1", // the api version
            "serviceaccounts", // the resource plural
            apiClient //the api client
        );
  }

  // ------------------------  deployments -----------------------------------
  public static boolean createDeployment(String deploymentYaml) {
    // do something with the command!!!
    return true;
  }

  /**
   * List all deployments in a given namespace.
   * @param namespace Namespace in which to list the deployments
   * @return V1DeploymentList of deployments in the Kubernetes cluster
   */
  public static V1DeploymentList listDeployments(String namespace) {
    KubernetesApiResponse<V1DeploymentList> list = deploymentClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      logger.warning("Failed to list deployments, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  // --------------------------- pods -----------------------------------------
  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name, String namespace) throws ApiException {
    return getPodLog(name, namespace, null);
  }

  /**
   * Get a pod's log.
   *
   * @param name name of the Pod
   * @param namespace name of the Namespace
   * @param container name of container for which to stream logs
   * @return log as a String
   * @throws ApiException if Kubernetes client API call fails
   */
  public static String getPodLog(String name, String namespace, String container)
      throws ApiException {
    String log = null;
    try {
      log = coreV1Api.readNamespacedPodLog(
          name, // name of the Pod
          namespace, // name of the Namespace
          container, // container for which to stream logs
          null, //  Boolean Follow the log stream of the pod
          null, // number of bytes to read from the server before terminating the log output
          PRETTY, // pretty print output
          null, // Boolean, Return previous terminated container logs
          null, // relative time (seconds) before the current time from which to show logs
          null, // number of lines from the end of the logs to show
          null // Boolean, add timestamp at the beginning of every line of log output
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return log;
  }

  /**
   * Delete a Kubernetes Pod.
   *
   * @param name name of the pod
   * @param namespace name of namespace
   * @return true if successful
   */
  public static boolean deletePod(String name, String namespace) {

    KubernetesApiResponse<V1Pod> response = podClient.delete(namespace, name);

    if (!response.isSuccess()) {
      logger.warning("Failed to delete pod '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
              + "pod in background!");
    }

    return true;
  }

  /**
   * List all pods in given namespace.
   *
   * @param namespace Namespace in which to list all pods
   * @param labelSelectors with which the pods are decorated
   * @return V1PodList list of pods
   * @throws ApiException when there is error in querying the cluster
   */
  public static V1PodList listPods(String namespace, String labelSelectors) throws ApiException {
    V1PodList v1PodList = null;
    try {
      v1PodList
          = coreV1Api.listNamespacedPod(
              namespace, // namespace in which to look for the pods.
              Boolean.FALSE.toString(), // pretty print output.
              Boolean.FALSE, // allowWatchBookmarks requests watch events with type "BOOKMARK".
              null, // continue to query when there is more results to return.
              null, // selector to restrict the list of returned objects by their fields
              labelSelectors, // selector to restrict the list of returned objects by their labels.
              null, // maximum number of responses to return for a list call.
              null, // shows changes that occur after that particular version of a resource.
              null, // Timeout for the list/watch call.
              Boolean.FALSE // Watch for changes to the described resources.
          );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }
    return v1PodList;
  }

  // --------------------------- namespaces -----------------------------------
  /**
   * Create a Kubernetes namespace.
   *
   * @param name the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name) throws ApiException {
    V1ObjectMeta meta = new V1ObjectMetaBuilder().withName(name).build();
    V1Namespace namespace = new V1NamespaceBuilder().withMetadata(meta).build();

    try {
      namespace = coreV1Api.createNamespace(
          namespace, // name of the Namespace
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // name associated with the actor or entity that is making these changes
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a Kubernetes namespace.
   *
   * @param namespace - V1Namespace object containing namespace configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createNamespace(V1Namespace namespace) throws ApiException {
    if (namespace == null) {
      throw new IllegalArgumentException(
          "Parameter 'namespace' cannot be null when calling createNamespace()");
    }

    V1Namespace ns = null;
    try {
      ns = coreV1Api.createNamespace(
          namespace, // V1Namespace configuration data object
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // name associated with the actor or entity that is making these changes
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * List namespaces in the Kubernetes cluster.
   * @return List of all Namespace names in the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    ArrayList<String> nameSpaces = new ArrayList<>();
    V1NamespaceList namespaceList;
    try {
      namespaceList = coreV1Api.listNamespace(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    for (V1Namespace namespace : namespaceList.getItems()) {
      nameSpaces.add(namespace.getMetadata().getName());
    }

    return nameSpaces;
  }

  /**
   * List namespaces in the Kubernetes cluster as V1NamespaceList.
   * @return V1NamespaceList of Namespace in the Kubernetes cluster
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1NamespaceList listNamespacesAsObjects() throws ApiException {
    V1NamespaceList namespaceList;
    try {
      namespaceList = coreV1Api.listNamespace(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return namespaceList;
  }

  /**
   * Delete a namespace for the given name.
   *
   * @param name name of namespace
   * @return true if successful delete, false otherwise
   */
  public static boolean deleteNamespace(String name) {

    KubernetesApiResponse<V1Namespace> response = namespaceClient.delete(name);

    if (!response.isSuccess()) {
      logger.warning("Failed to delete namespace: "
          + name + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting namespace"
              + " in background!");
    }

    return true;
  }

  // --------------------------- Custom Resource Domain -----------------------------------
  /**
   * Create a Domain Custom Resource.
   *
   * @param domain Domain custom resource model object
   * @return true on success, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createDomainCustomResource(Domain domain) throws ApiException {
    if (domain == null) {
      throw new IllegalArgumentException(
          "Parameter 'domain' cannot be null when calling createDomainCustomResource()");
    }

    if (domain.metadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'domain' cannot be null when calling createDomainCustomResource()");
    }

    if (domain.metadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createDomainCustomResource()");
    }

    String namespace = domain.metadata().getNamespace();

    JsonElement json = convertToJson(domain);

    Object response;
    try {
      response = customObjectsApi.createNamespacedCustomObject(
          DOMAIN_GROUP, // custom resource's group name
          DOMAIN_VERSION, // //custom resource's version
          namespace, // custom resource's namespace
          DOMAIN_PLURAL, // custom resource's plural name
          json, // JSON schema of the Resource to create
          null // pretty print output
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Converts a Java Object to a JSON element.
   *
   * @param obj java object to be converted
   * @return object representing Json element
   */
  private static JsonElement convertToJson(Object obj) {
    Gson gson = apiClient.getJSON().getGson();
    return gson.toJsonTree(obj);
  }

  /**
   * Delete the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteDomainCustomResource(String domainUid, String namespace) {

    KubernetesApiResponse<Domain> response = crdClient.delete(namespace, domainUid);

    if (!response.isSuccess()) {
      logger.warning(
          "Failed to delete Domain Custom Resource '" + domainUid + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
          + "domain custom resource in background!");
    }

    return true;
  }

  /**
   * Get the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @return domain custom resource or null if Domain does not exist
   * @throws ApiException if Kubernetes request fails
   */
  public static Domain getDomainCustomResource(String domainUid, String namespace)
      throws ApiException {
    Object domain;
    try {
      domain = customObjectsApi.getNamespacedCustomObject(
          DOMAIN_GROUP, // custom resource's group name
          DOMAIN_VERSION, // //custom resource's version
          namespace, // custom resource's namespace
          DOMAIN_PLURAL, // custom resource's plural name
          domainUid // custom object's name
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    if (domain != null) {
      return handleResponse(domain, Domain.class);
    }

    logger.warning("Domain Custom Resource '" + domainUid + "' not found in namespace " + namespace);
    return null;
  }

  /**
   * Patch the Domain Custom Resource using JSON Patch.JSON Patch is a format for describing changes to a JSON document
   * using a series of operations. JSON Patch is specified in RFC 6902 from the IETF. For example, the following
   * operation will replace the "spec.restartVersion" to a value of "2".
   *
   * <p>[
   *      {"op": "replace", "path": "/spec/restartVersion", "value": "2" }
   *    ]
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patchString JSON Patch document as a String
   * @return true if patch is successful otherwise false
   */
  public static boolean patchCustomResourceDomainJsonPatch(String domainUid, String namespace,
      String patchString) {
    return patchDomainCustomResource(
        domainUid, // name of custom resource domain
        namespace, // name of namespace
        new V1Patch(patchString), // patch data
        V1Patch.PATCH_FORMAT_JSON_PATCH // "application/json-patch+json" patch format
    );
  }

  /**
   * Patch the Domain Custom Resource using JSON Merge Patch.JSON Merge Patch is a format for describing a changed
   * version to a JSON document. JSON Merge Patch is specified in RFC 7396 from the IETF. For example, the following
   * JSON object fragment would add/replace the "spec.restartVersion" to a value of "1".
   *
   * <p>{
   *      "spec" : { "restartVersion" : "1" }
   *    }
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patchString JSON Patch document as a String
   * @return true if patch is successful otherwise false
   */
  public static boolean patchCustomResourceDomainJsonMergePatch(String domainUid, String namespace,
      String patchString) {
    return patchDomainCustomResource(
        domainUid, // name of custom resource domain
        namespace, // name of namespace
        new V1Patch(patchString), // patch data
        V1Patch.PATCH_FORMAT_JSON_MERGE_PATCH // "application/merge-patch+json" patch format
    );
  }

  /**
   * Patch the Domain Custom Resource.
   *
   * @param domainUid unique domain identifier
   * @param namespace name of namespace
   * @param patch patch data in format matching the specified media type
   * @param patchFormat one of the following types used to identify patch document:
   *     "application/json-patch+json", "application/merge-patch+json",
   * @return true if successful, false otherwise
   */
  public static boolean patchDomainCustomResource(String domainUid, String namespace,
      V1Patch patch, String patchFormat) {

    // GenericKubernetesApi uses CustomObjectsApi calls
    KubernetesApiResponse<Domain> response = crdClient.patch(
        namespace, // name of namespace
        domainUid, // name of custom resource domain
        patchFormat, // "application/json-patch+json" or "application/merge-patch+json"
        patch // patch data
    );

    if (!response.isSuccess()) {
      logger.warning(
          "Failed to patch " + domainUid + " in namespace " + namespace + " using patch format: "
              + patchFormat);
      return false;
    }

    return true;
  }

  /**
   * Converts the response to appropriate type.
   *
   * @param response response object to convert
   * @param type the type to convert into
   * @return the Java object of the type the response object is converted to
   */
  @SuppressWarnings("unchecked")
  private static <T> T handleResponse(Object response, Class<T> type) {
    JsonElement jsonElement = convertToJson(response);
    return apiClient.getJSON().getGson().fromJson(jsonElement, type);
  }

  /**
   * List Domain Custom Resources for a given namespace.
   *
   * @param namespace name of namespace
   * @return List of Domain Custom Resources
   */
  public static DomainList listDomains(String namespace) {
    KubernetesApiResponse<DomainList> response = null;
    try {
      response = crdClient.list(namespace);
    } catch (Exception ex) {
      logger.warning(ex.getMessage());
      throw ex;
    }
    return response != null ? response.getObject() : new DomainList();
  }

  // --------------------------- config map ---------------------------
  /**
   * Create a Kubernetes Config Map.
   *
   * @param configMap V1ConfigMap object containing config map configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createConfigMap(V1ConfigMap configMap) throws ApiException {
    if (configMap == null) {
      throw new IllegalArgumentException(
          "Parameter 'configMap' cannot be null when calling createConfigMap()");
    }

    if (configMap.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'configMap' cannot be null when calling createConfigMap()");
    }

    if (configMap.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createConfigMap()");
    }

    String namespace = configMap.getMetadata().getNamespace();

    V1ConfigMap cm;
    try {
      cm = coreV1Api.createNamespacedConfigMap(
          namespace, // config map's namespace
          configMap, // config map configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // name associated with the actor or entity that is making these changes
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * List Config Maps in the Kubernetes cluster.
   *
   * @param namespace Namespace in which to query
   * @return V1ConfigMapList of Config Maps
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1ConfigMapList listConfigMaps(String namespace) throws ApiException {

    V1ConfigMapList configMapList;
    try {
      configMapList = coreV1Api.listNamespacedConfigMap(
          namespace, // config map's namespace
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          null, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return configMapList;
  }

  /**
   * Delete Kubernetes Config Map.
   *
   * @param name name of the Config Map
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteConfigMap(String name, String namespace) {

    KubernetesApiResponse<V1ConfigMap> response = configMapClient.delete(namespace, name);

    if (!response.isSuccess()) {
      logger.warning("Failed to delete config map '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
          + "config map in background!");
    }

    return true;
  }

  // --------------------------- secret ---------------------------
  /**
   * Create a Kubernetes Secret.
   *
   * @param secret V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createSecret(V1Secret secret) throws ApiException {
    if (secret == null) {
      throw new IllegalArgumentException(
          "Parameter 'secret' cannot be null when calling createSecret()");
    }

    if (secret.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'secret' cannot be null when calling createSecret()");
    }

    if (secret.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createSecret()");
    }

    String namespace = secret.getMetadata().getNamespace();

    V1Secret v1Secret;
    try {
      v1Secret = coreV1Api.createNamespacedSecret(
          namespace, // name of the Namespace
          secret, // secret configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete a Kubernetes Secret.
   *
   * @param name name of the Secret
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteSecret(String name, String namespace) {

    KubernetesApiResponse<V1Secret> response = secretClient.delete(namespace, name);

    if (!response.isSuccess()) {
      logger.warning("Failed to delete secret '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
          + "secret in background!");
    }

    return true;
  }

  /**
   * List secrets in the Kubernetes cluster.
   * @param namespace Namespace in which to query
   * @return V1SecretList of secrets in the Kubernetes cluster
   */
  public static V1SecretList listSecrets(String namespace) {
    KubernetesApiResponse<V1SecretList> list = secretClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      logger.warning("Failed to list secrets, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  // --------------------------- pv/pvc ---------------------------
  /**
   * Create a Kubernetes Persistent Volume.
   *
   * @param persistentVolume V1PersistentVolume object containing persistent volume configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPv(V1PersistentVolume persistentVolume) throws ApiException {
    if (persistentVolume == null) {
      throw new IllegalArgumentException(
          "Parameter 'persistentVolume' cannot be null when calling createPv()");
    }

    V1PersistentVolume pv;
    try {
      pv = coreV1Api.createPersistentVolume(
          persistentVolume, // persistent volume configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Create a Kubernetes Persistent Volume Claim.
   *
   * @param persistentVolumeClaim V1PersistentVolumeClaim object containing Kubernetes persistent volume claim
    configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createPvc(V1PersistentVolumeClaim persistentVolumeClaim) throws ApiException {
    if (persistentVolumeClaim == null) {
      throw new IllegalArgumentException(
          "Parameter 'persistentVolume' cannot be null when calling createPvc()");
    }

    if (persistentVolumeClaim.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'persistentVolumeClaim' cannot be null when calling createPvc()");
    }

    if (persistentVolumeClaim.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createPvc()");
    }

    String namespace = persistentVolumeClaim.getMetadata().getNamespace();

    V1PersistentVolumeClaim pvc;
    try {
      pvc = coreV1Api.createNamespacedPersistentVolumeClaim(
          namespace, // name of the Namespace
          persistentVolumeClaim, // persistent volume claim configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete the Kubernetes Persistent Volume.
   *
   * @param name name of the Persistent Volume
   * @return true if successful
   */
  public static boolean deletePv(String name) {

    KubernetesApiResponse<V1PersistentVolume> response = pvClient.delete(name);

    if (!response.isSuccess()) {
      logger.warning("Failed to delete persistent volume '" + name + "' "
          + "with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
          + "persistent volume in background!");
    }

    return true;
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim.
   *
   * @param name name of the Persistent Volume Claim
   * @param namespace name of the namespace
   * @return true if successful
   */
  public static boolean deletePvc(String name, String namespace) {

    KubernetesApiResponse<V1PersistentVolumeClaim> response = pvcClient.delete(namespace, name);

    if (!response.isSuccess()) {
      logger.warning(
          "Failed to delete persistent volume claim '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
          + "persistent volume claim in background!");
    }

    return true;
  }

  /**
   * List all persistent volumes in the Kubernetes cluster.
   * @return V1PersistentVolumeList of Persistent Volumes in Kubernetes cluster
   */
  public static V1PersistentVolumeList listPersistentVolumes() {
    KubernetesApiResponse<V1PersistentVolumeList> list = pvClient.list();
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      logger.warning("Failed to list Persistent Volumes,"
          + " status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  /**
   * List persistent volumes in the Kubernetes cluster based on the label.
   * @param labels String containing the labels the PV is decorated with
   * @return V1PersistentVolumeList list of Persistent Volumes
   * @throws ApiException when listing fails
   */
  public static V1PersistentVolumeList listPersistentVolumes(String labels) throws ApiException {
    V1PersistentVolumeList listPersistentVolume;
    try {
      listPersistentVolume = coreV1Api.listPersistentVolume(
          PRETTY, // pretty print output
          ALLOW_WATCH_BOOKMARKS, // allowWatchBookmarks requests watch events with type "BOOKMARK"
          null, // set when retrieving more results from the server
          null, // selector to restrict the list of returned objects by their fields
          labels, // selector to restrict the list of returned objects by their labels
          null, // maximum number of responses to return for a list call
          RESOURCE_VERSION, // shows changes that occur after that particular version of a resource
          TIMEOUT_SECONDS, // Timeout for the list/watch call
          false // Watch for changes to the described resources
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }
    return listPersistentVolume;
  }

  /**
   * List persistent volume claims in the namespace.
   * @param namespace name of the namespace in which to list
   * @return V1PersistentVolumeClaimList of Persistent Volume Claims in namespace
   */
  public static V1PersistentVolumeClaimList listPersistentVolumeClaims(String namespace) {
    KubernetesApiResponse<V1PersistentVolumeClaimList> list = pvcClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      logger.warning("Failed to list Persistent Volumes claims,"
          + " status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  // --------------------------- service account ---------------------------
  /**
   * Create a Kubernetes Service Account.
   *
   * @param serviceAccount V1ServiceAccount object containing service account configuration data
   * @return created service account
   * @throws ApiException if Kubernetes client API call fails
   */
  public static V1ServiceAccount createServiceAccount(V1ServiceAccount serviceAccount)
      throws ApiException {
    if (serviceAccount == null) {
      throw new IllegalArgumentException(
          "Parameter 'serviceAccount' cannot be null when calling createServiceAccount()");
    }

    if (serviceAccount.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'serviceAccount' cannot be null when calling createServiceAccount()");
    }

    if (serviceAccount.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createServiceAccount()");
    }

    String namespace = serviceAccount.getMetadata().getNamespace();

    try {
      serviceAccount = coreV1Api.createNamespacedServiceAccount(
          namespace, // name of the Namespace
          serviceAccount, // service account configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return serviceAccount;
  }

  /**
   * Delete a Kubernetes Service Account.
   *
   * @param name name of the Service Account
   * @param namespace name of namespace
   * @return true if successful, false otherwise
   */
  public static boolean deleteServiceAccount(String name, String namespace) {

    KubernetesApiResponse<V1ServiceAccount> response = serviceAccountClient.delete(namespace, name);

    if (!response.isSuccess()) {
      logger.warning("Failed to delete Service Account '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
          + "service account in background!");
    }

    return true;
  }

  /**
   * List all service accounts in the Kubernetes cluster.
   *
   * @param namespace Namespace in which to list all service accounts
   * @return V1ServiceAccountList of service accounts
   */
  public static V1ServiceAccountList listServiceAccounts(String namespace) {
    KubernetesApiResponse<V1ServiceAccountList> list = serviceAccountClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      logger.warning("Failed to list service accounts, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }
  // --------------------------- Services ---------------------------

  /**
   * Create a Kubernetes Service.
   *
   * @param service V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean createService(V1Service service) throws ApiException {
    if (service == null) {
      throw new IllegalArgumentException(
          "Parameter 'service' cannot be null when calling createService()");
    }

    if (service.getMetadata() == null) {
      throw new IllegalArgumentException(
          "'metadata' field of the parameter 'service' cannot be null when calling createService()");
    }

    if (service.getMetadata().getNamespace() == null) {
      throw new IllegalArgumentException(
          "'namespace' field in the metadata cannot be null when calling createService()");
    }

    String namespace = service.getMetadata().getNamespace();

    V1Service svc;
    try {
      svc = coreV1Api.createNamespacedService(
          namespace, // name of the Namespace
          service, // service configuration data
          PRETTY, // pretty print output
          null, // indicates that modifications should not be persisted
          null // fieldManager is a name associated with the actor
      );
    } catch (ApiException apex) {
      logger.severe(apex.getResponseBody());
      throw apex;
    }

    return true;
  }

  /**
   * Delete a Kubernetes Service.
   *
   * @param name name of the Service
   * @param namespace name of namespace
   * @return true if successful
   */
  public static boolean deleteService(String name, String namespace) {

    KubernetesApiResponse<V1Service> response = serviceClient.delete(namespace, name);

    if (!response.isSuccess()) {
      logger.warning("Failed to delete Service '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
          + "service in background!");
    }

    return true;
  }

  // --------------------------- jobs ---------------------------
  /**
   * Get a list of all jobs in the given namespace.
   *
   * @param namespace in which to list the jobs
   * @return V1JobList of jobs from Kubernetes cluster
   */
  public static V1JobList listJobs(String namespace) {
    KubernetesApiResponse<V1JobList> list = jobClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      logger.warning("Failed to list jobs, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  // --------------------------- replica sets ---------------------------
  /**
   * Get a list of all replica sets in the given namespace.
   *
   * @param namespace in which to list the replica sets
   * @return V1ReplicaSetList of replica sets
   */
  public static V1ReplicaSetList listReplicaSets(String namespace) {
    KubernetesApiResponse<V1ReplicaSetList> list = rsClient.list(namespace);
    if (list.isSuccess()) {
      return list.getObject();
    } else {
      logger.warning("Failed to list replica sets, status code {0}", list.getHttpStatusCode());
      return null;
    }
  }

  // --------------------------- Role-based access control (RBAC)   ---------------------------

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

    V1ClusterRoleBinding crb = rbacAuthApi.createClusterRoleBinding(
        clusterRoleBinding, // role binding configuration data
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null // fieldManager is a name associated with the actor
    );

    return true;
  }

  /**
   * Delete Cluster Role Binding.
   *
   * @param name name of cluster role binding
   * @return true if successful, false otherwise
   */
  public static boolean deleteClusterRoleBinding(String name) {
    KubernetesApiResponse<V1ClusterRoleBinding> response = roleBindingClient.delete(name);

    if (!response.isSuccess()) {
      logger.warning(
          "Failed to delete Cluster Role Binding '" + name + " with HTTP status code: " + response
              .getHttpStatusCode());
      return false;
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
              + "Cluster Role Binding " + name + " in background!");
    }

    return true;
  }

  //------------------------
}

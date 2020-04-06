// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import io.kubernetes.client.extended.generic.GenericKubernetesApi;
import io.kubernetes.client.extended.generic.KubernetesApiResponse;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
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
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretList;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import io.kubernetes.client.openapi.models.V1ServiceAccountList;
import io.kubernetes.client.openapi.models.V1ServiceList;
import io.kubernetes.client.util.ClientBuilder;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.json.internal.json_simple.parser.JSONParser;
import org.jose4j.json.internal.json_simple.parser.ParseException;

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

  // Extended GenericKubernetesApi clients
  private static GenericKubernetesApi<V1ConfigMap, V1ConfigMapList> configMapClient = null;
  private static GenericKubernetesApi<V1Namespace, V1NamespaceList> namespaceClient = null;
  private static GenericKubernetesApi<V1Pod, V1PodList> podClient = null;
  private static GenericKubernetesApi<V1PersistentVolume, V1PersistentVolumeList> pvClient = null;
  private static GenericKubernetesApi<V1PersistentVolumeClaim, V1PersistentVolumeClaimList> pvcClient = null;
  private static GenericKubernetesApi<V1Secret, V1SecretList> secretClient = null;
  private static GenericKubernetesApi<V1Service, V1ServiceList> serviceClient = null;
  private static GenericKubernetesApi<V1ServiceAccount, V1ServiceAccountList> serviceAccountClient = null;

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
      apiClient = Configuration.getDefaultApiClient();
      coreV1Api = new CoreV1Api();
      customObjectsApi = new CustomObjectsApi();
      initializeGenericKubernetesApiClients();
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
  }

  /**
   * Create static instances of GenericKubernetesApi clients
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

  public static List listDeployments() {
    return new ArrayList();
  }

  // --------------------------- pods -----------------------------------------

  /**
   * Get a pod's log
   *
   * @param name - name of the Pod
   * @param namespace - name of the Namespace
   * @return log as a String
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static String getPodLog(String name, String namespace) throws ApiException {
    return getPodLog(name, namespace, null);
  }

  /**
   * Get a pod's log
   *
   * @param name - name of the Pod
   * @param namespace - name of the Namespace
   * @param container - name of container for which to stream logs
   * @return log as a String
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static String getPodLog(String name, String namespace, String container)
      throws ApiException {
    String log = coreV1Api.readNamespacedPodLog(
        name, // name of the Pod
        namespace, // name of the Namespace
        container, // container for which to stream logs
        null, //  true/false Follow the log stream of the pod
        null, // number of bytes to read from the server before terminating the log output
        PRETTY, // pretty print output
        null, // true/false, Return previous terminated container logs
        null, // relative time (seconds) before the current time from which to show logs
        null, // number of lines from the end of the logs to show
        null // true/false, add timestamp at the beginning of every line of log output
    );

    return log;
  }

  /**
   * Delete Kubernetes Pod
   *
   * @param pod - V1Pod object containing pod's configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deletePod(V1Pod pod) throws ApiException {
    if (pod == null) {
      throw new ApiException(
          "Parameter 'pod' is null when calling deletePod()");
    }

    if (pod.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling deletePod()");
    }

    if (pod.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deletePod()");
    }

    if (pod.getMetadata().getName() == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deletePod()");
    }

    String namespace = pod.getMetadata().getNamespace();
    String name = pod.getMetadata().getName();

    KubernetesApiResponse<V1Pod> response = podClient.delete(namespace, name);

    if (!response.isSuccess()) {
      throw new ApiException("Failed to delete pod '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
              + "pod in background!");
    }

    return true;
  }

  // --------------------------- namespaces -----------------------------------

  /**
   * Create Kubernetes namespace
   *
   * @param name the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name) throws ApiException {
    V1ObjectMeta meta = new V1ObjectMetaBuilder().withName(name).build();
    V1Namespace namespace = new V1NamespaceBuilder().withMetadata(meta).build();

    namespace = coreV1Api.createNamespace(
        namespace, // name of the Namespace
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null // name associated with the actor or entity that is making these changes
    );

    return true;
  }

  /**
   * Create Kubernetes namespace
   *
   * @param namespace - V1Namespace object containing namespace configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createNamespace(V1Namespace namespace) throws ApiException {
    if (namespace == null) {
      throw new ApiException(
          "Parameter 'namespace' is null when calling createNamespace()");
    }

    V1Namespace ns = coreV1Api.createNamespace(
        namespace, // V1Namespace configuration data object
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null // name associated with the actor or entity that is making these changes
    );

    return true;
  }

  /**
   * List of namespaces in Kubernetes cluster
   *
   * @return - List of names of all namespaces in Kubernetes cluster
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static List<String> listNamespaces() throws ApiException {
    ArrayList<String> nameSpaces = new ArrayList<>();
    V1NamespaceList namespaceList = coreV1Api.listNamespace(
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

    for (V1Namespace namespace : namespaceList.getItems()) {
      nameSpaces.add(namespace.getMetadata().getName());
    }

    return nameSpaces;
  }

  /**
   * Delete a namespace for the given name
   *
   * @param name - name of namespace
   * @return true if successful delete, false otherwise
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deleteNamespace(String name) throws ApiException {
    V1ObjectMeta metdata = new V1ObjectMetaBuilder().withName(name).build();
    V1Namespace namespace = new V1NamespaceBuilder().withMetadata(metdata).build();
    return deleteNamespace(namespace);
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
    if (namespace == null) {
      throw new ApiException(
          "Parameter 'namespace' is null when calling deleteNamespace()");
    }

    if (namespace.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling deleteNamespace()");
    }

    if (namespace.getMetadata().getName() == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deleteNamespace()");
    }

    String name = namespace.getMetadata().getName();

    KubernetesApiResponse<V1Namespace> response = namespaceClient.delete(name);

    if (!response.isSuccess()) {
      throw new ApiException("Failed to delete namespace: "
          + name + " with HTTP status code: " + response.getHttpStatusCode());
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
   * Create domain custom resource from the given domain yaml file.
   *
   * @param namespace  - name of namespace
   * @param domainYAML - path to a file containing domain custom resource spec in yaml format
   * @return true on success, false otherwise
   * @throws IOException  - on failure to convert domain YAML spec to JSON object
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean createDomainCustomResource(String namespace,
      String domainYAML) throws IOException, ApiException {
    Object json = null;
    try {
      json = convertYamlFileToJson(domainYAML);
    } catch (ParseException e) {
      throw new IOException("Failed to parse " + domainYAML, e);
    }

    Object response = customObjectsApi.createNamespacedCustomObject(
        DOMAIN_GROUP, // custom resource's group name
        DOMAIN_VERSION, // //custom resource's version
        namespace, // custom resource's namespace
        DOMAIN_PLURAL, // custom resource's plural name
        json, // JSON schema of the Resource to create
        null // pretty print output
    );

    return true;
  }

  /**
   * Delete the domain custom resource
   *
   * @param domainUID - unique domain identifier
   * @param namespace - name of namespace
   * @return true if successful, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deleteDomainCustomResource(String domainUID, String namespace)
      throws ApiException {
    V1DeleteOptions deleteOptions = new V1DeleteOptions();

    Object status = customObjectsApi.deleteNamespacedCustomObject(
        DOMAIN_GROUP, // custom resource's group name
        DOMAIN_VERSION, // //custom resource's version
        namespace, // custom resource's namespace
        DOMAIN_PLURAL, // custom resource's plural name
        domainUID, // custom object's name
        0, // duration in seconds before the object should be deleted
        false, // Should the dependent objects be orphaned
        "Foreground", // Whether and how garbage collection will be performed
        deleteOptions
    );

    return true;
  }

  /**
   * Get the domain custom resource
   *
   * @param domainUID - unique domain identifier
   * @param namespace - name of namespace
   * @return domain custom resource
   * @throws ApiException - if Kubernetes request fails or domain does not exist
   */
  public static Domain getDomainCustomResource(String domainUID, String namespace)
      throws ApiException {
    Object domain = customObjectsApi.getNamespacedCustomObject(
        DOMAIN_GROUP, // custom resource's group name
        DOMAIN_VERSION, // //custom resource's version
        namespace, // custom resource's namespace
        DOMAIN_PLURAL, // custom resource's plural name
        domainUID // custom object's name
    );

    if (domain != null) {
      return handleResponse(domain, Domain.class);
    }

    throw new ApiException("Domain Custom Resource '" + domainUID + "' not found in namespace " + namespace);
  }

  /**
   * Converts the response to appropriate type
   *
   * @param response - response object to convert
   * @param type - the type to convert into
   * @return the Java object of the type the response object is converted to
   */
  @SuppressWarnings("unchecked")
  private static <T> T handleResponse(Object response, Class<T> type) {
    Gson gson = apiClient.getJSON().getGson();
    JsonElement jsonElement = gson.toJsonTree(response);
    return gson.fromJson(jsonElement, type);
  }

  /**
   * Converts YAML file content to JSON object
   *
   * @param yamlFile - path to file containing YAML spec
   * @return JSON object
   * @throws IOException    - failure to load the YAML file
   * @throws ParseException - failure to parse JSON formatted String object
   */
  private static Object convertYamlFileToJson(String yamlFile) throws IOException, ParseException {
    // Read the yaml from file
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object yamlObj = yamlReader.readValue(new File(yamlFile), Object.class);

    // Convert to JSON object
    ObjectMapper jsonWriter = new ObjectMapper();
    String writeValueAsString = jsonWriter.writerWithDefaultPrettyPrinter()
        .writeValueAsString(yamlObj);
    JSONParser parser = new JSONParser();
    JSONObject json = (JSONObject) parser.parse(writeValueAsString);
    return json;
  }

  /**
   * List domain custom resources for a given namespace.
   *
   * @param namespace - name of namespace
   * @return List of names of domain custom resources
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static List<String> listDomains(String namespace) throws ApiException {
    ArrayList<String> domains = new ArrayList<>();

    Map response = (Map) customObjectsApi.listNamespacedCustomObject(
        DOMAIN_GROUP, // custom resource's group name
        DOMAIN_VERSION, //custom resource's version
        namespace, // custom resource's namespace
        DOMAIN_PLURAL, // custom resource's plural name
        null, // pretty print output
        null, // set when retrieving more results from the server
        null, // selector to restrict the list of returned objects by their fields
        null, // selector to restrict the list of returned objects by their labels
        null, // maximum number of responses to return for a list call
        null, // shows changes that occur after that particular version of a resource
        TIMEOUT_SECONDS, // Timeout for the list/watch call
        false // Watch for changes to the described resources
    );

    // Get all domain names from response as a list
    domains = getDomainNames(namespace, domains, response);

    return domains;
  }

  /**
   * Extracts domain names from response map
   */
  private static ArrayList<String> getDomainNames(String namespace, ArrayList<String> domains,
                                                  Map result) {
    List items = (List) result.get("items");
    for (Object item : items) {
      Map metadata = (Map) ((Map) item).get("metadata");
      if (namespace.equals(metadata.get("namespace"))) {
        domains.add((String) metadata.get("name"));
      }
    }
    return domains;
  }

  // --------------------------- create, delete resource using yaml --------------------------

  public static boolean create(String yaml) {
    return true;
  }

  public static boolean delete(String yaml) {
    return true;
  }

  // --------------------------- config map ---------------------------

  /**
   * Create a Kubernetes Config Map
   *
   * @param configMap - V1ConfigMap object containing config map configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createConfigMap(V1ConfigMap configMap) throws ApiException {
    if (configMap == null) {
      throw new ApiException(
          "Parameter 'configMap' is null when calling createConfigMap()");
    }

    if (configMap.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling createConfigMap()");
    }

    if (configMap.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createConfigMap()");
    }

    String namespace = configMap.getMetadata().getNamespace();

    V1ConfigMap cm = coreV1Api.createNamespacedConfigMap(
        namespace, // config map's namespace
        configMap, // config map configuration data
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null  // name associated with the actor or entity that is making these changes
    );

    return true;
  }

  /**
   * List names of all Config Maps for given namespace.
   *
   * @param namespace - name of namespace for config map
   * @return List of names of config maps
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static List<String> listConfigMaps(String namespace) throws ApiException {
    ArrayList<String> configMaps = new ArrayList<>();

    V1ConfigMapList configMapList = coreV1Api.listNamespacedConfigMap(
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

    for (V1ConfigMap cm : configMapList.getItems()) {
      configMaps.add(cm.getMetadata().getName());
    }

    return configMaps;
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
    if (configMap == null) {
      throw new ApiException(
          "Parameter 'configMap' is null when calling deleteConfigMap()");
    }

    if (configMap.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling deleteConfigMap()");
    }

    if (configMap.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteConfigMap()");
    }

    if (configMap.getMetadata().getName() == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deleteConfigMap()");
    }

    String namespace = configMap.getMetadata().getNamespace();
    String name = configMap.getMetadata().getName();

    KubernetesApiResponse<V1ConfigMap> response = configMapClient.delete(namespace, name);

    if (!response.isSuccess()) {
      throw new ApiException("Failed to delete config map '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
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
   * Create Kubernetes Secret
   *
   * @param secret - V1Secret object containing Kubernetes secret configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createSecret(V1Secret secret) throws ApiException {
    if (secret == null) {
      throw new ApiException(
          "Parameter 'secret' is null when calling createSecret()");
    }

    if (secret.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling createSecret()");
    }

    if (secret.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createSecret()");
    }

    String namespace = secret.getMetadata().getNamespace();

    V1Secret v1Secret = coreV1Api.createNamespacedSecret(
        namespace, // name of the Namespace
        secret, // secret configuration data
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null // fieldManager is a name associated with the actor
    );

    return true;
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
    if (secret == null) {
      throw new ApiException(
          "Parameter 'secret' is null when calling deleteSecret()");
    }

    if (secret.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling deleteSecret()");
    }

    if (secret.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteSecret()");
    }

    if (secret.getMetadata().getName() == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deleteSecret()");
    }

    String namespace = secret.getMetadata().getNamespace();
    String name = secret.getMetadata().getName();

    KubernetesApiResponse<V1Secret> response = secretClient.delete(namespace, name);

    if (!response.isSuccess()) {
      throw new ApiException("Failed to delete secret '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
              + "secret in background!");
    }

    return true;
  }

  // --------------------------- pv/pvc ---------------------------

  /**
   *
   * @param persistentVolume - V1PersistentVolume object containing Kubernetes persistent volume
   *     configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createPv(V1PersistentVolume persistentVolume) throws ApiException {
    if (persistentVolume == null) {
      throw new ApiException(
          "Parameter 'persistentVolume' is null when calling createPv()");
    }

    V1PersistentVolume pv = coreV1Api.createPersistentVolume(
        persistentVolume, // persistent volume configuration data
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null // fieldManager is a name associated with the actor
    );

    return true;
  }

  /**
   * @param persistentVolumeClaim - V1PersistentVolumeClaim object containing Kubernetes
   *     persistent volume claim configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createPvc(V1PersistentVolumeClaim persistentVolumeClaim) throws ApiException {
    if (persistentVolumeClaim == null) {
      throw new ApiException(
          "Parameter 'persistentVolume' is null when calling createPv()");
    }

    if (persistentVolumeClaim.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling createPvc()");
    }

    if (persistentVolumeClaim.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createPvc()");
    }

    String namespace = persistentVolumeClaim.getMetadata().getNamespace();

    V1PersistentVolumeClaim pvc = coreV1Api.createNamespacedPersistentVolumeClaim(
        namespace, // name of the Namespace
        persistentVolumeClaim, // persistent volume claim configuration data
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null // fieldManager is a name associated with the actor
    );

    return true;
  }


  /**
   * Delete the Kubernetes Persistent Volume
   *
   * @param persistentVolume - V1PersistentVolume object containing PV configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deletePv(V1PersistentVolume persistentVolume) throws ApiException {
    if (persistentVolume == null) {
      throw new ApiException(
          "Parameter 'persistentVolume' is null when calling deletePv()");
    }

    if (persistentVolume.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling deletePv()");
    }

    if (persistentVolume.getMetadata().getName() == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deletePv()");
    }

    String name = persistentVolume.getMetadata().getName();

    KubernetesApiResponse<V1PersistentVolume> response = pvClient.delete(name);

    if (!response.isSuccess()) {
      throw new ApiException("Failed to delete persistent volume '" + name + "' "
          + "with HTTP status code: " + response.getHttpStatusCode());
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
              + "persistent volume in background!");
    }

    return true;
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
  public static boolean deletePvc(V1PersistentVolumeClaim persistentVolumeClaim)
      throws ApiException {
    if (persistentVolumeClaim == null) {
      throw new ApiException(
          "Parameter 'persistentVolumeClaim' is null when calling deletePvc()");
    }

    if (persistentVolumeClaim.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling deletePvc()");
    }

    if (persistentVolumeClaim.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deletePvc()");
    }

    if (persistentVolumeClaim.getMetadata().getName() == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deletePvc()");
    }

    String namespace = persistentVolumeClaim.getMetadata().getNamespace();
    String name = persistentVolumeClaim.getMetadata().getName();

    KubernetesApiResponse<V1PersistentVolumeClaim> response = pvcClient.delete(namespace, name);

    if (!response.isSuccess()) {
      throw new ApiException(
          "Failed to delete persistent volume claim '" + name + "' from namespace: "
              + namespace + " with HTTP status code: " + response.getHttpStatusCode());
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
              + "persistent volume claim in background!");
    }

    return true;
  }

  // --------------------------- service account ---------------------------

  /**
   * Create a service account
   *
   * @param serviceAccount - V1ServiceAccount object containing service account configuration data
   * @return created service account
   * @throws ApiException - missing required configuration data or if Kubernetes request fails
   */
  public static V1ServiceAccount createServiceAccount(V1ServiceAccount serviceAccount)
      throws ApiException {
    if (serviceAccount == null) {
      throw new ApiException(
          "Parameter 'serviceAccount' is null when calling createServiceAccount()");
    }

    if (serviceAccount.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling createServiceAccount()");
    }

    if (serviceAccount.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createServiceAccount()");
    }

    String namespace = serviceAccount.getMetadata().getNamespace();

    serviceAccount = coreV1Api.createNamespacedServiceAccount(
        namespace, // name of the Namespace
        serviceAccount, // service account configuration data
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null // fieldManager is a name associated with the actor
    );

    return serviceAccount;
  }

  /**
   * Delete a service account
   *
   * @param serviceAccount - V1ServiceAccount object containing service account configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deleteServiceAccount(V1ServiceAccount serviceAccount) throws ApiException {
    if (serviceAccount == null) {
      throw new ApiException(
          "Parameter 'serviceAccount' is null when calling deleteServiceAccount()");
    }


    if (serviceAccount.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling deleteServiceAccount()");
    }

    if (serviceAccount.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteServiceAccount()");
    }

    if (serviceAccount.getMetadata().getName() == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deleteServiceAccount()");
    }

    String namespace = serviceAccount.getMetadata().getNamespace();
    String name = serviceAccount.getMetadata().getName();

    KubernetesApiResponse<V1ServiceAccount> response = serviceAccountClient.delete(namespace, name);

    if (!response.isSuccess()) {
      throw new ApiException("Failed to delete Service Account '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
              + "service account in background!");
    }

    return true;
  }

  // --------------------------- Services ---------------------------

  /**
   * Create Kubernetes Service
   *
   * @param service - V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean createService(V1Service service) throws ApiException {
    if (service == null) {
      throw new ApiException(
          "Parameter 'service' is null when calling createService()");
    }

    if (service.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling createService()");
    }

    if (service.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling createService()");
    }

    String namespace = service.getMetadata().getNamespace();

    V1Service svc = coreV1Api.createNamespacedService(
        namespace, // name of the Namespace
        service, // service configuration data
        PRETTY, // pretty print output
        null, // indicates that modifications should not be persisted
        null // fieldManager is a name associated with the actor
    );

    return true;
  }

  /**
   * Delete Service
   *
   * @param service - V1Service object containing service configuration data
   * @return true if successful
   * @throws ApiException - missing required configuration data, if Kubernetes request fails or
   *     unsuccessful
   */
  public static boolean deleteService(V1Service service) throws ApiException {
    if (service == null) {
      throw new ApiException(
          "Parameter 'service' is null when calling deleteService()");
    }

    if (service.getMetadata() == null) {
      throw new ApiException(
          "Missing the required parameter 'metadata' when calling deleteService()");
    }

    if (service.getMetadata().getNamespace() == null) {
      throw new ApiException(
          "Missing the required parameter 'namespace' when calling deleteService()");
    }

    if (service.getMetadata().getName() == null) {
      throw new ApiException(
          "Missing the required parameter 'name' when calling deleteService()");
    }

    String namespace = service.getMetadata().getNamespace();
    String name = service.getMetadata().getName();

    KubernetesApiResponse<V1Service> response = serviceClient.delete(namespace, name);

    if (!response.isSuccess()) {
      throw new ApiException("Failed to delete Service '" + name + "' from namespace: "
          + namespace + " with HTTP status code: " + response.getHttpStatusCode());
    }

    if (response.getObject() != null) {
      logger.info(
          "Received after-deletion status of the requested object, will be deleting "
              + "service in background!");
    }

    return true;
  }

  //------------------------

  /**
   * TODO:  This should go in a utilities class? load properties.
   *
   * @param propsFile properties file
   * @return properties
   * @throws Exception on failure
   */
  private static Properties loadProps(String propsFile) throws IOException {
    Properties props = new Properties();

    // load props
    FileInputStream inStream = new FileInputStream(propsFile);
    props.load(inStream);
    inStream.close();

    return props;
  }
}

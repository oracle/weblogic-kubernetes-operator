// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapList;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.ClientBuilder;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.json.internal.json_simple.parser.JSONParser;
import org.jose4j.json.internal.json_simple.parser.ParseException;

// TODO ryan - in here we want to implement all of the kubernetes
// primitives that we need, using the API, not spawning a process
// to run kubectl.

public class Kubernetes implements LoggedTest {

  public static Random random = new Random(System.currentTimeMillis());
  private static String pretty = "false";
  private static Boolean allowWatchBookmarks = false;
  private static String resourceVersion = "";
  private static Integer timeoutSeconds = 5;
  private static String DOMAIN_GROUP = "weblogic.oracle";
  private static String NAME = "domains.weblogic.oracle";
  private static String DOMAIN_VERSION = "v7";
  private static String DOMAIN_PLURAL = "domains";
  private static String DOMAIN_PATH = "namespaces/{namespace}/" + DOMAIN_PLURAL;
  // the CoreV1Api loads default api-client from global configuration.
  private static ApiClient apiClient = null;
  private static CoreV1Api coreV1Api = null;
  private static CustomObjectsApi customObjectsApi = null;

  static {
    try {
      Configuration.setDefaultApiClient(ClientBuilder.defaultClient());
      apiClient = Configuration.getDefaultApiClient();
      coreV1Api = new CoreV1Api();
      customObjectsApi = new CustomObjectsApi();
    } catch (IOException ioex) {
      throw new ExceptionInInitializerError(ioex);
    }
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

  // --------------------------- namespaces -----------------------------------

  /**
   * Create Kubernetes namespace
   *
   * @param name the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean createNamespace(String name) throws ApiException {
    V1ObjectMeta meta = new V1ObjectMeta().name(name);
    V1Namespace namespace = new V1Namespace().metadata(meta);

    namespace = coreV1Api.createNamespace(
        namespace, // name of the Namespace
        pretty, // pretty print output
        null, // indicates that modifications should not be persisted
        null // name associated with the actor or entity that is making these changes
    );

    return true;
  }

  /**
   * Create a new namespace with a "unique" name. This method will create a "unique" name by
   * choosing a random name from 26^4 possible combinations, and create a namespace using that
   * random name.
   *
   * @return the name of the new unique namespace.
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static String createUniqueNamespace() throws ApiException {
    char[] name = new char[4];
    for (int i = 0; i < name.length; i++) {
      name[i] = (char) (random.nextInt(25) + (int) 'a');
    }
    String namespace = "ns-" + new String(name);
    if (createNamespace(namespace)) {
      return namespace;
    } else {
      return "";
    }
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
        pretty, // pretty print output
        allowWatchBookmarks, // allowWatchBookmarks requests watch events with type "BOOKMARK"
        null, // set when retrieving more results from the server
        null, // selector to restrict the list of returned objects by their fields
        null, // selector to restrict the list of returned objects by their labels
        null, // maximum number of responses to return for a list call
        resourceVersion, // shows changes that occur after that particular version of a resource
        timeoutSeconds, // Timeout for the list/watch call
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
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deleteNamespace(String name) throws ApiException {
    V1DeleteOptions deleteOptions = new V1DeleteOptions();

    V1Status status = coreV1Api.deleteNamespace(
        name, // name of the Namespace
        pretty, // pretty print output
        null, // indicates that modifications should not be persisted
        0, // duration in seconds before the object should be deleted
        false, // Should the dependent objects be orphaned
        "Foreground", // Whether and how garbage collection will be performed
        deleteOptions
    );

    if (status.getCode() == 200 || status.getCode() == 202) {
      // status code 200 = OK, 202 = Accepted
      return true;
    }

    return false;
  }

  // --------------------------- Custom Resource Domain -----------------------------------

  /**
   * Create domain custom resource from the given domain yaml file.
   *
   * @param domainUID - unique domain identifier
   * @param namespace - name of namespace
   * @param domainYAML - path to a file containing domain custom resource spec in yaml format
   * @return true on success, false otherwise
   * @throws IOException - on failure to convert domain YAML spec to JSON object
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean createDomainCustomResource(String domainUID, String namespace, String domainYAML)
      throws IOException, ApiException {
    Object json = null;
    try {
      json = convertYamlToJson(domainYAML);
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
   * Converts YAML file content to JSON object
   *
   * @param yamlFile - path to file containing YAML spec
   * @return JSON object
   * @throws IOException - failure to load the YAML file
   * @throws ParseException - failure to parse JSON formatted String object
   */
  private static Object convertYamlToJson(String yamlFile) throws IOException, ParseException {
    // Read the yaml from file
    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object yamlObj = yamlReader.readValue(new File(yamlFile), Object.class);

    // Convert to JSON object
    ObjectMapper jsonWriter = new ObjectMapper();
    String writeValueAsString = jsonWriter.writerWithDefaultPrettyPrinter().writeValueAsString(yamlObj);
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
        timeoutSeconds, // Timeout for the list/watch call
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
   * @param cmName - name of the config map
   * @param namespace - name of namespace for config map
   * @param fromFile - path to file containing config map data, as name - value pairs
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   * @throws IOException - failure to load config map data from file
   */
  public static boolean createConfigMap(String cmName, String namespace, String fromFile)
      throws ApiException,
      IOException {
    // Initialize config map meta data
    V1ObjectMeta meta = new V1ObjectMeta().name(cmName);
    V1ConfigMap body = new V1ConfigMap().metadata(meta);

    // Load config map data
    Properties cmProperties = loadProps(fromFile);
    body.data((Map) cmProperties);

    V1ConfigMap configMap = coreV1Api.createNamespacedConfigMap(
        namespace, // config map's namespace
        body, // config map configuration data
        pretty, // pretty print output
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
        pretty, // pretty print output
        allowWatchBookmarks, // allowWatchBookmarks requests watch events with type "BOOKMARK"
        null, // set when retrieving more results from the server
        null, // selector to restrict the list of returned objects by their fields
        null, // selector to restrict the list of returned objects by their labels
        null, // maximum number of responses to return for a list call
        resourceVersion, // shows changes that occur after that particular version of a resource
        timeoutSeconds, // Timeout for the list/watch call
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
   * @param cmName the name of the Config Map
   * @param namespace the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deleteConfigMap(String cmName, String namespace) throws ApiException {
    V1DeleteOptions deleteOptions = new V1DeleteOptions();

    V1Status status = coreV1Api.deleteNamespacedConfigMap(
        cmName, // name of config map
        namespace,  // name of the Namespace
        pretty, // pretty print output
        null, // indicates that modifications should not be persisted
        0, // duration in seconds before the object should be deleted
        false, // Should the dependent objects be orphaned
        "Foreground", // Whether and how garbage collection will be performed
        deleteOptions
    );

    if (status.getCode() == 200 || status.getCode() == 202) {
      // status code 200 = OK, 202 = Accepted
      return true;
    }

    return false;
  }

  // --------------------------- secret ---------------------------

  /**
   * Create Kubernetes Secret
   *
   * @param secretName the name of the secret
   * @param username username of the domain
   * @param password password for the domain
   * @param namespace the name of the namespace
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   *
   */
  public static boolean createSecret(String secretName,
      String username, String password, String namespace) throws ApiException {
    V1ObjectMeta meta = new V1ObjectMeta().name(secretName);
    HashMap<String, byte[]> data = new HashMap<>();
    data.put("username", username.getBytes(StandardCharsets.UTF_8));
    data.put("password", password.getBytes(StandardCharsets.UTF_8));
    V1Secret body = new V1Secret().metadata(meta).type("Opaque").data(data);

    // TODO: what about labels?

    V1Secret secret = coreV1Api.createNamespacedSecret(
        namespace, // name of the Namespace
        body, // secret configuration data
        pretty, // pretty print output
        null, // indicates that modifications should not be persisted
        null // fieldManager is a name associated with the actor
    );

    return true;
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
    V1DeleteOptions deleteOptions = new V1DeleteOptions();

    V1Status status = coreV1Api.deleteNamespacedSecret(
        secretName,// name of secret
        namespace,  // name of the Namespace
        pretty, // pretty print output
        null, // indicates that modifications should not be persisted
        0, // duration in seconds before the object should be deleted
        false, // Should the dependent objects be orphaned
        "Foreground", // Whether and how garbage collection will be performed
        deleteOptions
    );

    if (status.getCode() == 200 || status.getCode() == 202) {
      // status code 200 = OK, 202 = Accepted
      return true;
    }

    return false;
  }

  // --------------------------- pv/pvc ---------------------------

  /**
   * Delete the Kubernetes Persistent Volume
   *
   * @param pvName the name of the Persistent Volume
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deletePv(String pvName) throws ApiException {
    V1DeleteOptions deleteOptions = new V1DeleteOptions();

    V1Status status = coreV1Api.deletePersistentVolume(
        pvName, // persistent volume (PV) name
        pretty, // pretty print output
        null, // indicates that modifications should not be persisted
        0, // duration in seconds before the object should be deleted
        false, // Should the dependent objects be orphaned
        "Foreground", // Whether and how garbage collection will be performed
        deleteOptions
    );

    if (status.getCode() == 200 || status.getCode() == 202) {
      // status code 200 = OK, 202 = Accepted
      return true;
    }

    return false;
  }

  /**
   * Delete the Kubernetes Persistent Volume Claim
   *
   * @param pvcName the name of the Persistent Volume Claim
   * @param namespace the namespace of the Persistent Volume Claim
   * @return true on success, false otherwise
   * @throws ApiException - if Kubernetes client API call fails
   */
  public static boolean deletePvc(String pvcName, String namespace) throws ApiException {
    V1DeleteOptions deleteOptions = new V1DeleteOptions();

    V1Status status = coreV1Api.deleteNamespacedPersistentVolumeClaim(
        pvcName, // persistent volume claim (PV) name
        namespace,
        pretty, // pretty print output
        null, // indicates that modifications should not be persisted
        0, // duration in seconds before the object should be deleted
        false, // Should the dependent objects be orphaned
        "Foreground", // Whether and how garbage collection will be performed
        deleteOptions
    );

    if (status.getCode() == 200 || status.getCode() == 202) {
      // status code 200 = OK, 202 = Accepted
      return true;
    }

    return false;
  }
  // --------------------------

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

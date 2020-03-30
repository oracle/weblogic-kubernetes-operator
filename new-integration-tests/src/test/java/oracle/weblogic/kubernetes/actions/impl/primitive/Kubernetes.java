// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.actions.impl.primitive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.models.V1DeleteOptions;
import io.kubernetes.client.openapi.models.V1Namespace;
import io.kubernetes.client.openapi.models.V1NamespaceList;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Status;
import io.kubernetes.client.util.ClientBuilder;
import oracle.weblogic.kubernetes.extensions.LoggedTest;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

// TODO ryan - in here we want to implement all of the kubernetes
// primitives that we need, using the API, not spawning a process
// to run kubectl.

public class Kubernetes implements LoggedTest {

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

    public static Random random = new Random(System.currentTimeMillis());

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

    public static boolean createNamespace(String name) throws ApiException {
        V1ObjectMeta meta = new V1ObjectMeta().name(name);
        V1Namespace namespace = new V1Namespace().metadata(meta);

        namespace = coreV1Api.createNamespace(
                namespace, // name of the Namespace
                pretty, // pretty print output
                null, // indicates that modifications should not be persisted
                null // fieldManager is a name associated with the actor
                // or entity that is making these changes
        );

        return true;
    }

    /**
     * Create a new namespace with a "unique" name.
     * This method will create a "unique" name by choosing a random name from
     * 26^4 possible combinations, and create a namespace using that random name.
     * @return the name of the new namespace.
     */
    public static String createUniqueNamespace() throws ApiException {
        char[] name = new char[4];
        for (int i = 0; i < name.length; i++) {
            name[i] = (char)(random.nextInt(25) + (int)'a');
        }
        String namespace = "ns-" + new String( name);
        if (createNamespace(namespace)) {
            return namespace;
        } else {
            return "";
        }
    }

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
        return true;

    }

    // --------------------------- Custom Resource Domain -----------------------------------

    public static boolean createDomain(String domainUID, String namespace, String domainYAML) throws IOException, ApiException {
        final String localVarPath =
                DOMAIN_PATH.replaceAll("\\{namespace\\}", apiClient.escapeString(namespace));

        Object json = null;

        json = convertYamlToJson(domainYAML);
        Object response = customObjectsApi.createNamespacedCustomObject(
                DOMAIN_GROUP, // custom resource's group name
                DOMAIN_VERSION, // //custom resource's version
                namespace, // custom resource's namespace
                localVarPath, // custom resource's plural name
                json, // JSON schema of the Resource to create
                null // pretty print output
        );
        return true;

    }

    private static Object convertYamlToJson(String yamlFile) throws IOException {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        Object yamlObj = yamlReader.readValue(new File(yamlFile), Object.class);
        logger.info("Kubernetes.convertYamlToJson yaml: " + yamlObj);

        ObjectMapper jsonWriter = new ObjectMapper();
        String writeValueAsString = jsonWriter.writeValueAsString(yamlObj);
        logger.info("Kubernetes.convertYamlToJson writeValueAsString: " + writeValueAsString);
        JsonNode root = new ObjectMapper().readTree(writeValueAsString);
        return root;
    }

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
        domains = getDomainNames(namespace, domains, response);

        return domains;
    }

    private static ArrayList<String> getDomainNames(String namespace, ArrayList<String> domains, Map result) {
        List items = (List) result.get("items");
        for (Object item : items) {
            Map metadata = (Map) ((Map) item).get("metadata");
            if (namespace.equals(metadata.get("namespace"))) {
                domains.add((String) metadata.get("name"));
            }
        }
        return domains;
    }
}

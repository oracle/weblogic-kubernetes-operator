// Copyright 2020, Oracle Corporation and/or its affiliates.
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

public class Kubernetes {

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

    public static boolean createNamespace(String name) {
        V1ObjectMeta meta = new V1ObjectMeta();
        meta.name(name);
        V1Namespace namespace = new V1Namespace();
        namespace.metadata(meta);
        try {
            namespace = coreV1Api.createNamespace(namespace, pretty, null, null);
            System.out.println("Kubernetes.createNamespace namespace: " + namespace);
            return true;
        } catch (ApiException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Create a new namespace with a "unique" name.
     * This method will create a "unique" name by choosing a random name from
     * 26^4 possible combinations, and create a namespace using that random name.
     * @return the name of the new namespace.
     */
    public static String createUniqueNamespace() {
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

    public static List<String> listNamespaces() {
        ArrayList<String> nameSpaces = new ArrayList<>();
        try {
            V1NamespaceList namespaceList = coreV1Api.listNamespace(pretty, allowWatchBookmarks, null, null, null, null, resourceVersion, timeoutSeconds, false);

            for (V1Namespace namespace : namespaceList.getItems()) {
                nameSpaces.add(namespace.getMetadata().getName());
            }
        } catch (ApiException e) {
            e.printStackTrace();
        }

        return nameSpaces;
    }

    public static boolean deleteNamespace(String name) {
        V1DeleteOptions deleteOptions = new V1DeleteOptions();
        try {
            V1Status status = coreV1Api.deleteNamespace(name, pretty, null, timeoutSeconds, false, "Foreground", deleteOptions);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // --------------------------- Custom Resource Domain -----------------------------------

    public static boolean createDomain(String domainUID, String namespace, String domainYAML) {
        final String localVarPath =
                DOMAIN_PATH.replaceAll("\\{namespace\\}", apiClient.escapeString(namespace));

        Object json = null;
        try {
            json = convertYamlToJson(domainYAML);
            Object response = customObjectsApi.createClusterCustomObject(DOMAIN_GROUP, DOMAIN_VERSION, localVarPath, json, null);
            return true;
        } catch (ApiException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static Object convertYamlToJson(String yamlFile) throws IOException {
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        Object yamlObj = yamlReader.readValue(new File(yamlFile), Object.class);
        System.out.println("Kubernetes.convertYamlToJson yaml: " + yamlObj);

        ObjectMapper jsonWriter = new ObjectMapper();
        String writeValueAsString = jsonWriter.writeValueAsString(yamlObj);
        System.out.println("Kubernetes.convertYamlToJson writeValueAsString: " + writeValueAsString);
        JsonNode root = new ObjectMapper().readTree(writeValueAsString);
        return root;
    }

    public static List<String> listDomains(String namespace) {
        ArrayList<String> domains = new ArrayList<>();
        try {
            Map response = (Map) customObjectsApi.listNamespacedCustomObject(DOMAIN_GROUP, DOMAIN_VERSION, namespace, DOMAIN_PLURAL, null,
                    null, null, null,null, null, timeoutSeconds,
                    false);
            domains = getDomainNames(namespace, domains, response);
        } catch (ApiException e) {
            e.printStackTrace();
        }

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

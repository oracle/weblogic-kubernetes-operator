// Copyright (c) 2019, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.logging.Level;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * A Domain CRD utility class to manipulate domain yaml files.
 */
public class DomainCrd {


  private final ObjectMapper objectMapper;
  private final JsonNode root;

  /**
   * Constructor to read the yaml file and initialize the root JsonNode with yaml equivalent of JSON
   * tree.
   *
   * @param yamlFile - Name of the yaml file containing the Domain CRD.
   * @throws IOException exception
   */
  public DomainCrd(String yamlFile) throws IOException {
    this.objectMapper = new ObjectMapper();

    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(new File(yamlFile), Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    String writeValueAsString = jsonWriter.writeValueAsString(obj);
    this.root = objectMapper.readTree(writeValueAsString);
  }

  /**
   * To convert the JSON tree back into Yaml and return it as a String.
   *
   * @return - Domain CRD in Yaml format
   * @throws JsonProcessingException when JSON tree cannot be converted as a Yaml string
   */
  public String getYamlTree() throws JsonProcessingException {
    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
    return jsonAsYaml;
  }

  /**
   * A utility method to add attributes to domain in domain.yaml.
   *
   * @param attributes - A HashMap of key value pairs
   */
  public void addObjectNodeToDomain(Map<String, String> attributes) {
    JsonNode specNode = getSpecNode();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) specNode).put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * A utility method to add attributes to domain in domain.yaml.
   *
   * @param attributes - A HashMap of key value pairs
   */
  public void addShutdownOptionToDomain(Map<String, Object> attributes) throws Exception {
    JsonNode specNode = getSpecNode();
    addShutdownOptionToObjectNode(specNode, attributes);
  }

  /**
   * A utility method to add attributes to domain in domain.yaml.
   *
   * @param attributes - A HashMap of key value pairs
   */
  public void addEnvOption(Map<String, String> attributes) throws Exception {
    JsonNode specNode = getSpecNode();
    JsonNode envNode = null;
    JsonNode podOptions = specNode.path("serverPod");
    JsonNode envNodes = (ArrayNode) specNode.path("serverPod").path("env");

    if (specNode.path("serverPod").isMissingNode()) {
      LoggerHelper.getLocal().log(Level.INFO, "Missing serverPod Node");
      podOptions = objectMapper.createObjectNode();
      envNodes = objectMapper.createArrayNode();
      ((ObjectNode) podOptions).set("env", envNodes);
      ((ObjectNode) specNode).set("serverPod", podOptions);

    } else if (podOptions.path("env").isMissingNode()) {
      envNodes = objectMapper.createArrayNode();
      ((ObjectNode) podOptions).set("env", envNodes);
    }
    envNodes = (ArrayNode) podOptions.path("env");
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      if (envNodes.size() != 0) {
        for (JsonNode envNode1 : envNodes) {

          LoggerHelper.getLocal().log(Level.INFO, " checking node " + envNode1.get("name"));
          if (envNode1.get("name").equals(entry.getKey())) {
            ((ObjectNode) envNode1).put("value", entry.getValue());
          }
        }
        envNode = objectMapper.createObjectNode();
        ((ObjectNode) envNode).put("name", entry.getKey());
        ((ObjectNode) envNode).put("value", entry.getValue());
        ((ArrayNode) envNodes).add(envNode);

      } else {
        envNode = objectMapper.createObjectNode();
        ((ObjectNode) envNode).put("name", entry.getKey());
        ((ObjectNode) envNode).put("value", entry.getValue());
        ((ArrayNode) envNodes).add(envNode);
      }
    }
  }

  /**
   * A utility method to add attributes to adminServer node in domain.yaml.
   *
   * @param attributes - A HashMap of key value pairs
   */
  public void addObjectNodeToAdminServer(Map<String, String> attributes) {

    JsonNode adminServerNode = getAdminServerNode();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) adminServerNode).put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * A utility method to add attributes to cluster node in domain.yaml.
   *
   * @param clusterName - Name of the cluster to which the attributes to be added
   * @param attributes  - A HashMap of key value pairs
   */
  public void addObjectNodeToCluster(String clusterName, Map<String, Object> attributes) {

    JsonNode clusterNode = getClusterNode(clusterName);
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      Object entryValue = entry.getValue();
      if (entryValue instanceof String) {
        ((ObjectNode) clusterNode).put(entry.getKey(), (String) entryValue);
      } else if (entryValue instanceof Integer) {
        ((ObjectNode) clusterNode).put(entry.getKey(), ((Integer) entryValue).intValue());
      }
    }
  }

  /**
   * A utility method to add attributes to cluster node's server pod in domain.yaml.
   *
   * @param clusterName - Name of the cluster to which the attributes to be added
   * @param objectName  - name of the object in cluster to which the attributes to be added
   * @param attributes  - A HashMap of key value pairs
   */
  public void addObjectNodeToClusterServerPod(
      String clusterName, String objectName, Map<String, String> attributes) {

    JsonNode objectNode = getObjectNodeFromServerPod(getClusterNode(clusterName), objectName);

    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      Object entryValue = entry.getValue();
      ((ObjectNode) objectNode).put(entry.getKey(), (String) entryValue);
    }
  }

  /**
   * A utility method to add shutdown element and attributes to cluster node in domain.yaml.
   *
   * @param clusterName - Name of the cluster to which the attributes to be added
   * @param attributes  - A HashMap of key value pairs
   */
  public void addShutdownOptionsToCluster(String clusterName, Map<String, Object> attributes)
      throws Exception {

    JsonNode clusterNode = getClusterNode(clusterName);
    addShutdownOptionToObjectNode(clusterNode, attributes);
  }

  /**
   * A utility method to add attributes to managed server node in domain.yaml.
   *
   * @param managedServerName - Name of the managed server to which the attributes to be added
   * @param attributes        - A HashMap of key value pairs
   */
  public void addObjectNodeToMS(String managedServerName, Map<String, String> attributes) {
    JsonNode managedServerNode = getManagedServerNode(managedServerName);
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) managedServerNode).put(entry.getKey(), entry.getValue());
    }
    try {
      String jsonString =
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(managedServerNode);
      System.out.println(jsonString);
    } catch (Exception ex) {
      // no-op
    }
  }

  /**
   * A utility method to add attributes to server pod node in domain.yaml.
   *
   * @param objectName - Name of the node to which the attributes to be added
   * @param attributes - A HashMap of key value pairs
   */
  public void addObjectNodeToServerPod(String objectName, Map<String, String> attributes) {
    JsonNode objectNode = getObjectNodeFromServerPod(getSpecNode(), objectName);
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) objectNode).put(entry.getKey(), entry.getValue());
    }
    try {
      String jsonString =
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode);
      System.out.println(jsonString);
    } catch (Exception ex) {
      // no-op
    }
  }

  /**
   * add init container node.
   * @param parentNodeName parent node name
   * @param clusterName cluster name
   * @param msName managed server name
   * @param containerName container name
   * @param command command
   * @return JSON node content
   */
  public JsonNode addInitContNode(
      String parentNodeName,
      String clusterName,
      String msName,
      java.lang.String containerName,
      String command) {
    ArrayNode initContNode = null;
    switch (parentNodeName) {
      case "spec":
        initContNode = getArrayNodeFromServerPod(getSpecNode(), "initContainers");
        break;
      case "adminServer":
        initContNode = getArrayNodeFromServerPod(getAdminServerNode(), "initContainers");
        break;
      case "clusters":
        initContNode = getArrayNodeFromServerPod(getClusterNode(clusterName), "initContainers");
        break;
      case "managedServers":
        initContNode = getArrayNodeFromServerPod(getManagedServerNode(msName), "initContainers");
        break;
      default:
        System.out.println("no match");
    }
    ObjectNode busybox = objectMapper.createObjectNode();
    busybox.put("name", containerName);
    busybox.put("imagePullPolicy", "IfNotPresent");
    busybox.put("image", "busybox");
    ArrayNode commandArrayNode = objectMapper.createArrayNode();
    commandArrayNode.add(command);
    commandArrayNode.add("30");
    busybox.put("command", commandArrayNode);
    ArrayNode add = initContNode.add(busybox);
    try {
      String jsonString =
          objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(initContNode);
      System.out.println(jsonString);
    } catch (Exception ex) {
      // no-op
    }
    return add;
  }

  /**
   * A utility method to add attributes to managed server node in domain.yaml.
   *
   * @param managedServerName - Name of the managed server to which the attributes to be added
   * @param attributes        - A HashMap of key value pairs
   */
  public void addShutDownOptionToMS(String managedServerName, Map<String, Object> attributes)
      throws Exception {
    JsonNode managedServerNode = getManagedServerNode(managedServerName);
    addShutdownOptionToObjectNode(managedServerNode, attributes);
  }

  /**
   * A utility method to add attributes to managed server node in domain.yaml.
   *
   * @param jsonNode   - the json node (domain,cluster,or manserver to which the attributes to be
   *                   added
   * @param attributes - A HashMap of key value pairs
   */
  private void addShutdownOptionToObjectNode(JsonNode jsonNode, Map<String, Object> attributes)
      throws Exception {
    String[] propNames = {"serverPod", "shutdown"};
    JsonNode myNode = jsonNode;
    for (int i = 0; i < propNames.length; i++) {
      if (myNode.path(propNames[i]).isMissingNode()) {

        LoggerHelper.getLocal().log(Level.INFO, "  property " + propNames[i] + " is not present, adding it  crd ");

        ObjectNode someSubNode = objectMapper.createObjectNode();
        ((ObjectNode) myNode).put(propNames[i], someSubNode);
        myNode = someSubNode;

      } else {
        myNode = myNode.path(propNames[i]);
      }
    }

    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      String dataType = entry.getValue().getClass().getSimpleName();

      if (dataType.equalsIgnoreCase("Integer")) {
        LoggerHelper.getLocal().log(Level.INFO,
            "Read Json Key :" + entry.getKey() + " | type :int | value:" + entry.getValue());
        ((ObjectNode) myNode).put(entry.getKey(), (int) entry.getValue());

      } else if (dataType.equalsIgnoreCase("Boolean")) {
        LoggerHelper.getLocal().log(Level.INFO,
            "Read Json Key :" + entry.getKey() + " | type :boolean | value:" + entry.getValue());
        ((ObjectNode) myNode).put(entry.getKey(), (boolean) entry.getValue());

      } else if (dataType.equalsIgnoreCase("String")) {
        LoggerHelper.getLocal().log(Level.INFO,
            "Read Json Key :" + entry.getKey() + " | type :string | value:" + entry.getValue());
        ((ObjectNode) myNode).put(entry.getKey(), (String) entry.getValue());
      }
    }
    String jsonString = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(myNode);
    System.out.println(jsonString);
  }

  /**
   * Gets the spec node entry from Domain CRD JSON tree.
   */
  private JsonNode getSpecNode() {
    return root.path("spec");
  }

  /**
   * Gets the serverPod node entry from Domain CRD JSON tree.
   *
   * @return serverPod node
   */
  private JsonNode getServerPodNode() {
    return root.path("spec").path("serverPod");
  }

  /**
   * Gets the administration server node entry from Domain CRD JSON tree.
   *
   * @return admin server node
   */
  private JsonNode getAdminServerNode() {
    return root.path("spec").path("adminServer");
  }

  /**
   * Gets the cluster node entry from Domain CRD JSON tree for the given cluster name.
   *
   * @param clusterName - Name of the cluster
   * @return - JsonNode of named cluster
   */
  private JsonNode getClusterNode(String clusterName) {
    ArrayNode clusters = (ArrayNode) root.path("spec").path("clusters");
    JsonNode clusterNode = null;
    for (JsonNode cluster : clusters) {
      if (cluster.get("clusterName").asText().equals(clusterName)) {
        LoggerHelper.getLocal().log(Level.INFO, "Got the cluster");
        clusterNode = cluster;
      }
    }
    return clusterNode;
  }

  /**
   * Gets the managed server node entry from Domain CRD JSON tree.
   *
   * @param managedServerName Name of the managed server for which to get the JSON node
   * @return managed server node entry from Domain CRD JSON tree
   */
  private JsonNode getManagedServerNode(String managedServerName) {

    ArrayNode managedservers = null;
    JsonNode managedserverNode = null;
    JsonNode specNode = getSpecNode();
    if (root.path("spec").path("managedServers").isMissingNode()) {
      LoggerHelper.getLocal().log(Level.INFO, "Missing MS Node");
      managedservers = objectMapper.createArrayNode();
      ObjectNode managedserver = objectMapper.createObjectNode();
      managedserver.put("serverName", managedServerName);
      managedservers.add(managedserver);

      ((ObjectNode) specNode).set("managedServers", managedservers);
      managedserverNode = managedserver;
    } else {
      managedservers = (ArrayNode) root.path("spec").path("managedServers");
      if (managedservers.size() != 0) {
        for (JsonNode managedserver : managedservers) {
          if (managedserver.get("serverName").asText().equals(managedServerName)) {
            LoggerHelper.getLocal().log(Level.INFO, "Found managedServer with name " + managedServerName);
            managedserverNode = managedserver;
          }
        }
      } else {
        LoggerHelper.getLocal().log(Level.INFO, "Creating node for managedServer with name " + managedServerName);
        ObjectNode managedserver = objectMapper.createObjectNode();
        managedserver.put("serverName", managedServerName);
        managedservers.add(managedserver);
      }
    }
    return managedserverNode;
  }

  /**
   * Gets the object node entry from the server pod of the given parent node in Domain CRD JSON
   * tree.
   *
   * @param serverPodsParentNode parent node of the server pod
   * @param objectName           Name of the object for which to get the JSON node
   * @return object node
   */
  private JsonNode getObjectNodeFromServerPod(JsonNode serverPodsParentNode, String objectName) {
    JsonNode serverPodNode = null;
    JsonNode objectNode = null;
    if (serverPodsParentNode.path("serverPod").isMissingNode()) {
      LoggerHelper.getLocal().log(Level.INFO, "Missing serverPod Node");
      serverPodNode = objectMapper.createObjectNode();
      ((ObjectNode) serverPodsParentNode).set("serverPod", serverPodNode);
      objectNode = objectMapper.createObjectNode();
      ((ObjectNode) serverPodNode).set(objectName, objectNode);

    } else {
      serverPodNode = serverPodsParentNode.path("serverPod");
      if (serverPodNode.path(objectName).isMissingNode()) {
        LoggerHelper.getLocal().log(Level.INFO, "Creating node with name " + objectName);
        objectNode = objectMapper.createObjectNode();
        ((ObjectNode) serverPodNode).set(objectName, objectNode);
      } else {
        objectNode = serverPodNode.path(objectName);
      }
    }
    return objectNode;
  }

  /**
   * Gets the object node entry from the server pod of the given parent node in Domain CRD JSON
   * tree.
   *
   * @param serverPodsParentNode parent node of the server pod
   * @param arrayNodeName        Name of the object for which to get the JSON node
   * @return object node
   */
  private ArrayNode getArrayNodeFromServerPod(JsonNode serverPodsParentNode, String arrayNodeName) {
    JsonNode serverPodNode = null;
    ArrayNode arrayNode = null;
    if (serverPodsParentNode.path("serverPod").isMissingNode()) {
      LoggerHelper.getLocal().log(Level.INFO, "Missing serverPod Node");
      serverPodNode = objectMapper.createObjectNode();
      ((ObjectNode) serverPodsParentNode).set("serverPod", serverPodNode);
      arrayNode = objectMapper.createArrayNode();
      ((ObjectNode) serverPodNode).set(arrayNodeName, arrayNode);
    } else {
      serverPodNode = serverPodsParentNode.path("serverPod");
      if (serverPodNode.path(arrayNodeName).isMissingNode()) {
        LoggerHelper.getLocal().log(Level.INFO, "Creating node with name {0}", arrayNodeName);
        arrayNode = objectMapper.createArrayNode();
        ((ObjectNode) serverPodNode).set(arrayNodeName, arrayNode);
      } else {
        return (ArrayNode) serverPodsParentNode.path("serverPod").path(arrayNodeName);
      }
    }
    return arrayNode;
  }

  /**
   * Utility method to create a file and write to it.
   *
   * @param dir     - Directory in which to create the file
   * @param file    - Name of the file to write the content to
   * @param content - String content to write to the file
   * @throws IOException - When file cannot opened or written
   */
  public void writeToFile(String dir, String file, String content) throws IOException {
    Path path = Paths.get(dir, file);
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, content.getBytes(charset));
  }

}

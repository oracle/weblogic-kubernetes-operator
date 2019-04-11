package oracle.kubernetes.operator.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/** A Parser utility class to manipulate domain.yaml file */
public class DomainCRD {

  private final ObjectMapper objectMapper;
  private final JsonNode root;

  public static void main(String args[]) throws IOException {
    DomainCRD crd = new DomainCRD("c:\\Users\\Sankar\\Downloads\\res.yaml");

    Map<String, String> admin = new HashMap();
    admin.put("restartVersion", "v1.1");
    crd.addObjectNodeToAdminServer(admin);

    Map<String, String> domain = new HashMap();
    domain.put("restartVersion", "v1.1");
    crd.addObjectNodeToDomain(domain);

    Map<String, String> cluster = new HashMap();
    cluster.put("restartVersion", "v1.1");
    crd.addObjectNodeToCluster("cluster-1", cluster);
    cluster = new HashMap();
    cluster.put("restartVersion2", "v1.2");
    crd.addObjectNodeToCluster("cluster-1", cluster);
    System.out.println(crd.getYamlTree());

    Map<String, String> ms = new HashMap();
    ms.put("restartVersion", "v1.1");
    crd.addObjectNodeToMS("managedserver-1", ms);
    ms = new HashMap();
    ms.put("serverStartPolicy", "IF_NEEDED");
    crd.addObjectNodeToMS("managedserver-1", ms);
    ms = new HashMap();
    ms.put("serverStartState", "RUNNING");
    crd.addObjectNodeToMS("managedserver-1", ms);

    System.out.println(crd.getYamlTree());
  }

  public DomainCRD(String yamlFile) throws IOException {
    this.objectMapper = new ObjectMapper();

    ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
    Object obj = yamlReader.readValue(new File(yamlFile), Object.class);

    ObjectMapper jsonWriter = new ObjectMapper();
    String writeValueAsString = jsonWriter.writeValueAsString(obj);
    this.root = objectMapper.readTree(writeValueAsString);
  }

  public String getYamlTree() throws JsonProcessingException {
    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
    return jsonAsYaml;
  }

  /**
   * A utility method to add restartVersion attribute to domain in domain.yaml
   *
   * @param key Object key to add as a String
   * @param value Version value for the restartVersion attribute
   * @return Yaml String with added restartVersion attribute in domain
   * @throws IOException
   */
  public void addObjectNodeToDomain(Map<String, String> attributes) throws IOException {
    // modify admin server restartVersion
    JsonNode specNode = getSpecNode();
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) specNode).put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * A utility method to add restartVersion attribute to administration server in domain.yaml
   *
   * @param key Object key to add as a String
   * @param value Version value for the restartVersion attribute
   * @return Yaml String with added restartVersion attribute in administration server
   * @throws IOException
   */
  public void addObjectNodeToAdminServer(Map<String, String> attributes) throws IOException {
    // modify admin server restartVersion
    JsonNode adminServerNode = getAdminServerNode(root);
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) adminServerNode).put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * A utility method to add restartVersion attribute to cluster in domain.yaml
   *
   * @param domainCRD String representation of the domain.yaml as JSON tree
   * @param ClusterName Name of the cluster in which to add the restartVersion attribute
   * @param version Version value for the restartVersion attribute
   * @return Yaml String with added restartVersion attribute in cluster
   * @throws IOException when the JSON tree cannot be read
   */
  public void addObjectNodeToCluster(String ClusterName, Map<String, String> attributes)
      throws IOException {
    // modify cluster restartVersion
    JsonNode clusterNode = getClusterNode(ClusterName);
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) clusterNode).put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * A utility method to add restartVersion attribute to managed server in domain.yaml
   *
   * @param domainCRD domainCRD String representation of the domain.yaml as JSON tree
   * @param managedServerName Name of the managed server in which to add the restartVersion
   *     attribute
   * @param version Version value for the restartVersion attribute
   * @return Yaml String with added restartVersion attribute in managed server
   * @throws IOException when the JSON tree cannot be read
   */
  public void addObjectNodeToMS(String managedServerName, Map<String, String> attributes)
      throws IOException {
    JsonNode managedServerNode = getManagedServerNode(managedServerName);
    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      ((ObjectNode) managedServerNode).put(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Gets the spec node entry from Domain CRD JSON tree
   *
   * @param root - Root JSON node of the Domain CRD JSON tree
   * @return - spec node entry from Domain CRD JSON tree
   */
  private JsonNode getSpecNode() {
    return root.path("spec");
  }

  /**
   * Gets the administration server node entry from Domain CRD JSON tree
   *
   * @param root - Root JSON node of the Domain CRD JSON tree
   * @return - administration server node entry from Domain CRD JSON tree
   */
  private JsonNode getAdminServerNode(JsonNode root) {
    return root.path("spec").path("adminServer");
  }

  /**
   * Gets the cluster node entry from Domain CRD JSON tree for the given cluster name
   *
   * @param root - Root JSON node of the Domain CRD JSON tree
   * @param clusterName - Name of the cluster
   * @return - cluster node entry from Domain CRD JSON tree
   */
  private JsonNode getClusterNode(String clusterName) {
    ArrayNode clusters = (ArrayNode) root.path("spec").path("clusters");
    JsonNode clusterNode = null;
    for (JsonNode cluster : clusters) {
      if (cluster.get("clusterName").asText().equals(clusterName)) {
        System.out.println("Got the cluster");
        clusterNode = cluster;
      }
    }
    return clusterNode;
  }

  /**
   * Gets the managed server node entry from Domain CRD JSON tree
   *
   * @param objectMapper - Instance of the ObjectMapper to use for creating a missing node
   * @param root - Root JSON node of the Domain CRD JSON tree
   * @param managedServerName - Name of the managed server for which to get the JSON node
   * @return administration server node entry from Domain CRD JSON tree
   */
  private JsonNode getManagedServerNode(String managedServerName) {
    ArrayNode managedservers = null;
    JsonNode managedserverNode = null;
    if (root.path("spec").path("managedServers").isMissingNode()) {
      System.out.println("Missing MS Node");
      managedservers = objectMapper.createArrayNode();
      ObjectNode managedserver = objectMapper.createObjectNode();
      managedserver.put("serverName", managedServerName);
      managedservers.add(managedserver);
      ((ObjectNode) root).set("managedServers", managedservers);
      managedserverNode = managedserver;
    } else {
      managedservers = (ArrayNode) root.path("spec").path("managedServers");
      if (managedservers.size() != 0) {
        for (JsonNode managedserver : managedservers) {
          if (managedserver.get("serverName").equals(managedServerName)) {
            managedserverNode = managedserver;
          }
        }
      } else {
        ObjectNode managedserver = objectMapper.createObjectNode();
        managedserver.put("serverName", managedServerName);
        managedservers.add(managedserver);
      }
    }
    if (root.path("spec").path("managedServers").isMissingNode()) {
      System.out.println("YES IT IS STILL MISSING");
    }
    return managedserverNode;
  }

  /**
   * Utility method to create a file and write to it.
   *
   * @param dir - Directory in which to create the file
   * @param file - Name of the file to write the content to
   * @param content - String content to write to the file
   * @throws IOException - When file cannot opened or written
   */
  public void writeToFile(String dir, String file, String content) throws IOException {
    Path path = Paths.get(dir, file);
    Charset charset = StandardCharsets.UTF_8;
    Files.write(path, content.getBytes(charset));
  }
}

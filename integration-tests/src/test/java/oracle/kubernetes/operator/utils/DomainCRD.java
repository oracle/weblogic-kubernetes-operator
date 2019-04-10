package oracle.kubernetes.operator.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** A Parser utility class to manipulate domain.yaml file */
public class DomainCRD {

  /**
   * A utility method to add restartVersion attribute to domain in domain.yaml
   *
   * @param domainCRD String representation of the domain.yaml as JSON tree
   * @param version Version value for the restartVersion attribute
   * @return Yaml String with added restartVersion attribute in domain
   * @throws IOException
   */
  public String addRestartVersionToDomain(String domainCRD, String version) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode root = objectMapper.readTree(domainCRD);

    // modify admin server restartVersion
    JsonNode specNode = getSpecNode(root);
    ((ObjectNode) specNode).put("restartVersion", version);

    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
    return jsonAsYaml;
  }

  /**
   * A utility method to add restartVersion attribute to administration server in domain.yaml
   *
   * @param domainCRD String representation of the domain.yaml as JSON tree
   * @param version Version value for the restartVersion attribute
   * @return Yaml String with added restartVersion attribute in administration server
   * @throws IOException
   */
  public String addRestartVersionToAdminServer(String domainCRD, String version)
      throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode root = objectMapper.readTree(domainCRD);

    // modify admin server restartVersion
    JsonNode adminServerNode = getAdminServerNode(root);
    ((ObjectNode) adminServerNode).put("restartVersion", version);

    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
    return jsonAsYaml;
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
  public String addRestartVersionToCluster(String domainCRD, String ClusterName, String version)
      throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode root = objectMapper.readTree(domainCRD);

    // modify cluster restartVersion
    JsonNode clusterNode = getClusterNode(root, ClusterName);
    ((ObjectNode) clusterNode).put("restartVersion", version);

    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
    return jsonAsYaml;
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
  public String addRestartVersionToMS(String domainCRD, String managedServerName, String version)
      throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode root = objectMapper.readTree(domainCRD);

    // modify managedserver restartVersion
    JsonNode managedServerNode = getManagedServerNode(objectMapper, root, managedServerName);
    ((ObjectNode) managedServerNode).put("restartVersion", version);

    String jsonAsYaml = new YAMLMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
    return jsonAsYaml;
  }

  /**
   * Gets the spec node entry from Domain CRD JSON tree
   *
   * @param root - Root JSON node of the Domain CRD JSON tree
   * @return - spec node entry from Domain CRD JSON tree
   */
  private JsonNode getSpecNode(JsonNode root) {
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
  private JsonNode getClusterNode(JsonNode root, String clusterName) {
    ArrayNode clusters = (ArrayNode) root.path("spec").path("clusters");
    JsonNode clusterNode = null;
    for (JsonNode cluster : clusters) {
      if (cluster.get("clusterName").asText().equals(clusterName)) {
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
  private JsonNode getManagedServerNode(
      ObjectMapper objectMapper, JsonNode root, String managedServerName) {
    ArrayNode managedservers = null;
    JsonNode managedserverNode = null;
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
      managedserverNode = managedserver;
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

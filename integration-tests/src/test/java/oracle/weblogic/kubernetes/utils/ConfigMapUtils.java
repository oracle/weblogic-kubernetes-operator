// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static java.nio.file.Files.readString;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfigMapUtils {
  /**
   * Create ConfigMap from the specified files.
   * @param configMapName name of the ConfigMap to create
   * @param files files to be added in ConfigMap
   * @param namespace the namespace in which the ConfigMap to be created
   */
  public static void createConfigMapFromFiles(String configMapName,
                                              List<Path> files,
                                              String namespace) {

    // create a ConfigMap of the domain
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      data.put(file.getFileName().toString(),
          assertDoesNotThrow(() -> readString(file), "readString failed with IOException"));
    }

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .name(configMapName)
            .namespace(namespace));

    assertTrue(assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("createConfigMap failed with ApiException for ConfigMap %s with files %s in namespace %s",
            configMapName, files, namespace)),
        String.format("createConfigMap failed while creating ConfigMap %s in namespace %s", configMapName, namespace));
  }

  /**
   * Create a Kubernetes ConfigMap with the given parameters and verify that the operation succeeds.
   *
   * @param configMapName the name of the Kubernetes ConfigMap to be created
   * @param domainUid the domain to which the cluster belongs
   * @param namespace Kubernetes namespace that the domain is hosted
   * @param modelFiles list of the file names along with path for the WDT model files in the ConfigMap
   */
  public static void createConfigMapAndVerify(
      String configMapName,
      String domainUid,
      String namespace,
      List<String> modelFiles) {
    LoggingFacade logger = getLogger();
    assertNotNull(configMapName, "ConfigMap name cannot be null");

    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);

    assertNotNull(configMapName, "ConfigMap name cannot be null");

    logger.info("Create ConfigMap {0} that contains model files {1}",
        configMapName, modelFiles);

    Map<String, String> data = new HashMap<>();

    for (String modelFile : modelFiles) {
      addModelFile(data, modelFile);
    }

    V1ObjectMeta meta = new V1ObjectMeta()
        .labels(labels)
        .name(configMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    assertTrue(assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Create ConfigMap %s failed due to Kubernetes client  ApiException", configMapName)),
        String.format("Failed to create ConfigMap %s", configMapName));
  }

  /**
   * Create configmap containing domain creation scripts.
   *
   * @param configMapName name of the configmap to create
   * @param files files to add in configmap
   * @param namespace name of the namespace in which to create configmap
   * @param className name of the class to call this method
   * @throws IOException when reading the domain script files fail
   * @throws ApiException if create configmap fails
   */
  public static void createConfigMapForDomainCreation(String configMapName, List<Path> files,
                                                      String namespace, String className)
      throws ApiException, IOException {

    LoggingFacade logger = getLogger();
    logger.info("Creating configmap {0}, namespace {1}, className {2}", configMapName, namespace, className);

    Path domainScriptsDir = Files.createDirectories(
        Paths.get(TestConstants.LOGS_DIR, className, namespace));

    // add domain creation scripts and properties files to the configmap
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      logger.info("Adding file {0} in configmap", file);
      data.put(file.getFileName().toString(), Files.readString(file));
      logger.info("Making a copy of file {0} to {1} for diagnostic purposes", file,
          domainScriptsDir.resolve(file.getFileName()));
      Files.copy(file, domainScriptsDir.resolve(file.getFileName()));
    }
    V1ObjectMeta meta = new V1ObjectMeta()
        .name(configMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Failed to create configmap %s with files %s", configMapName, files));
    assertTrue(cmCreated, String.format("Failed while creating ConfigMap %s", configMapName));
  }

  /**
   * Read the content of a model file as a String and add it to a map.
   */
  private static void addModelFile(Map<String, String> data, String modelFile) {
    LoggingFacade logger = getLogger();
    logger.info("Add model file {0}", modelFile);

    String cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(modelFile)),
        String.format("Failed to read model file %s", modelFile));
    assertNotNull(cmData,
        String.format("Failed to read model file %s", modelFile));

    data.put(modelFile.substring(modelFile.lastIndexOf("/") + 1), cmData);
  }
}

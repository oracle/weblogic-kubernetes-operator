// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.


package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static java.nio.file.Files.readString;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.impl.ConfigMap.doesCMExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
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

    // wait until the CM is created
    testUntil(withLongRetryPolicy,
        () -> doesCMExist(configMapName, namespace),
        logger,
        "configmap {0} is created in namespace {1}",
        configMapName,
        namespace);
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
   * Check if Config Map exists.
   * @param nameSpace namespace for the pod
   * @param configMapName name of Config Map to modify
   */
  public static Callable<Boolean> configMapExist(String nameSpace, String configMapName) throws ApiException {
    List<V1ConfigMap> cmList = Kubernetes.listConfigMaps(nameSpace).getItems();
    V1ConfigMap configMapToModify = cmList.stream()
        .filter(cm -> configMapName.equals(cm.getMetadata().getName()))
        .findAny()
        .orElse(null);

    return () -> {
      if (configMapToModify != null) {
        return true;
      } else {
        return false;
      }
    };
  }

  /**
   * Check if Config Map doesn't exist.
   * @param nameSpace namespace for the pod
   * @param configMapName name of Config Map to modify
   */
  public static Callable<Boolean> configMapDoesNotExist(String nameSpace, String configMapName) throws ApiException {
    List<V1ConfigMap> cmList = Kubernetes.listConfigMaps(nameSpace).getItems();
    V1ConfigMap configMapToModify = cmList.stream()
        .filter(cm -> configMapName.equals(cm.getMetadata().getName()))
        .findAny()
        .orElse(null);

    return () -> {
      if (configMapToModify == null) {
        return true;
      } else {
        return false;
      }
    };
  }

  /**
   * replace Config Map.
   * @param nameSpace namespace for the pod
   * @param configMapName name of Config Map to replace
   * @param fileName new config map file name
   */
  public static boolean replaceConfigMap(String nameSpace, String configMapName, String fileName) {
    LoggingFacade logger = getLogger();
    boolean result = false;
    StringBuffer editConfigMap = new StringBuffer(KUBERNETES_CLI + " create configmap ");
    editConfigMap.append(configMapName);
    editConfigMap.append(" -n ");
    editConfigMap.append(nameSpace);
    editConfigMap.append(" --from-file ");
    editConfigMap.append(Paths.get(fileName));
    editConfigMap.append(" -o yaml --dry-run=client | " + KUBERNETES_CLI + " replace -f -");

    logger.info(KUBERNETES_CLI + " replace command is {0}", editConfigMap.toString());
    // replace configMap with new file
    ExecResult execResult = assertDoesNotThrow(() -> exec(new String(editConfigMap), true));

    if (execResult.exitValue() == 0) {
      logger.info(KUBERNETES_CLI + " replace returned {0}", execResult.toString());
      result = true;
    } else {
      logger.info("Failed to replace the configmap: " + execResult.stderr());
    }

    return result;
  }

  /**
   * Edit Config Map.
   * @param oldRegex search for existed value to replace
   * @param newRegex new value
   * @param nameSpace namespace for the pod
   * @param cmName name of Config Map to modify
   * @param configFileName name of Config file to modify
   * @throws ApiException when update fails
   */
  public static void editConfigMap(String oldRegex, String newRegex,
                                   String nameSpace, String cmName,
                                   String configFileName) throws ApiException {
    List<V1ConfigMap> cmList = Kubernetes.listConfigMaps(nameSpace).getItems();
    V1ConfigMap configMapToModify = cmList.stream()
        .filter(cm -> cmName.equals(cm.getMetadata().getName()))
        .findAny()
        .orElse(null);

    assertNotNull(configMapToModify,"Can't find cm for " + cmName);
    Map<String, String> cmData = configMapToModify.getData();

    String values = cmData.get("logstash.conf").replace(oldRegex,newRegex);
    assertNotNull(values, "can't find values for key prometheus.yml");
    cmData.replace(configFileName, values);

    configMapToModify.setData(cmData);
    Kubernetes.replaceConfigMap(configMapToModify);

    cmList = Kubernetes.listConfigMaps(nameSpace).getItems();

    configMapToModify = cmList.stream()
        .filter(cm -> cmName.equals(cm.getMetadata().getName()))
        .findAny()
        .orElse(null);

    assertNotNull(configMapToModify,"Can't find cm for " + cmName);
    assertNotNull(configMapToModify.getData(), "Can't retreive the cm data for " + cmName + " after modification");
  }

  /**
   * Read the content of a model file as a String and add it to a map.
   */
  public static void addModelFile(Map<String, String> data, String modelFile) {
    LoggingFacade logger = getLogger();
    logger.info("Add model file {0}", modelFile);

    String cmData = assertDoesNotThrow(() -> Files.readString(Paths.get(modelFile)),
        String.format("Failed to read model file %s", modelFile));
    assertNotNull(cmData,
        String.format("Failed to read model file %s", modelFile));

    data.put(modelFile.substring(modelFile.lastIndexOf("/") + 1), cmData);
  }
}

// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility class to create MySQL database.
 */
public class MySQLDBUtils {

  /**
   * Create and start a MySQL database pod.
   *
   * @param name              name of the db pod
   * @param user              username for the database
   * @param password          password for the database
   * @param nodePort          node port of db service
   * @param namespace         name of the namespace in which to create MySQL database
   * @param mySQLImageVersion version of the MySQL db image to use, when null uses version from
   *                          TestConstants.MYSQL_VERSION
   */
  public static void createMySQLDB(String name, String user, String password, int nodePort,
                                   String namespace, String mySQLImageVersion) {

    String uniqueName = Namespace.uniqueName();
    String secretName = name.concat("-secret-").concat(uniqueName);
    String serviceName = name.concat("-external-").concat(uniqueName);

    createMySQLDBSecret(secretName, user, password, namespace);
    createMySQLDBService(serviceName, name, namespace, nodePort);
    startMySQLDB(name, secretName, namespace,
        mySQLImageVersion != null ? mySQLImageVersion : TestConstants.MYSQL_VERSION);

  }

  //create the database pod
  private static void startMySQLDB(String name, String secretName, String namespace, String mySQLVImageVersion) {
    Map<String, String> labels = new HashMap<>();
    labels.put("app", name);
    V1Pod mysqlPod = new V1Pod()
        .metadata(new V1ObjectMeta()
            .name(name)
            .namespace(namespace)
            .labels(labels))
        .spec(new V1PodSpec()
            .terminationGracePeriodSeconds(5L)
            .containers(Arrays.asList(new V1Container()
                .image("mysql:".concat(mySQLVImageVersion))
                .name("mysql")
                .addEnvItem(new V1EnvVar()
                    .name("MYSQL_ROOT_PASSWORD")
                    .valueFrom(new V1EnvVarSource()
                        .secretKeyRef(new V1SecretKeySelector()
                            .name(secretName)
                            .key("root-password"))))
                .ports(Arrays.asList(new V1ContainerPort()
                    .name("mysql")
                    .containerPort(3306))))));
    V1Pod pod = assertDoesNotThrow(() -> Kubernetes.createPod(namespace, mysqlPod));
    CommonTestUtils.checkPodReady(pod.getMetadata().getName(), null, namespace);
  }

  //create services for MySQL database
  private static void createMySQLDBService(String serviceName, String selectorName, String namespace, int port) {

    boolean service = false;
    try {
      Map<String, String> selector = new HashMap<>();
      selector.put("app", selectorName);
      service = TestActions.createService(new V1Service()
          .metadata(new V1ObjectMeta()
              .name(serviceName)
              .namespace(namespace))
          .spec(new V1ServiceSpec()
              .type("NodePort")
              .ports(Arrays.asList(new V1ServicePort()
                  .port(3306)
                  .protocol("TCP")
                  .targetPort(new IntOrString(3306))
                  .nodePort(port)))
              .selector(selector)));
    } catch (ApiException ex) {
      Logger.getLogger(MySQLDBUtils.class.getName()).log(Level.SEVERE, null, ex);
    }
    assertTrue(service, "Service creation for mysql failed");
  }

  //create secrets
  private static void createMySQLDBSecret(String secretName, String user, String password, String namespace) {
    HashMap<String, String> secrets = new HashMap<>();
    secrets.put("root-user", user);
    secrets.put("root-password", password);

    boolean secret = false;
    try {
      secret = TestActions.createSecret(
          new V1Secret()
              .metadata(new V1ObjectMeta()
                  .name(secretName)
                  .namespace(namespace))
              .stringData(secrets));
    } catch (ApiException ex) {
      Logger.getLogger(MySQLDBUtils.class.getName()).log(Level.SEVERE, null, ex);
    }
    assertTrue(secret, "Secret creation for mysql failed");
  }
}

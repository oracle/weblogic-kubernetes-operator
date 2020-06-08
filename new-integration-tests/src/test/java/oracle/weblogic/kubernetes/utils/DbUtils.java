// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1DeploymentStrategy;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodCondition;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1RollingUpdateDeployment;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import org.awaitility.core.ConditionFactory;
//import io.kubernetes.client.openapi.models.V1PodList;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.REPO_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.FMW_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.FMW_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ORACLE_DB_BASE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ORACLE_DB_BASE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.getPod;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility class to start DB service and RCU schema.
 */
public class DbUtils {

  private static final String dbBaseImageName = ORACLE_DB_BASE_IMAGE_NAME + ":" + ORACLE_DB_BASE_IMAGE_TAG;
  private static final String fmwBaseImageName = FMW_BASE_IMAGE_NAME + ":" + FMW_BASE_IMAGE_TAG;
  private static V1Service oracleDBService = null;
  private static V1Deployment oracleDbDepl = null;


  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await();
  /**
   * Start DB instance, create Oracle rcu pod and load database schema in the specified namespace.
   *
   * @param rcuSchemaPrefix rcu SchemaPrefixe
   * @param dbNamespace namespace where DB and RCU schema are going to start
   * @param dbPort NodePort of DB
   * @param dbUrl URL of DB
   * @throws Exception if any error occurs when setting up RCU database
   */

  public static void setupDBandRCUschema(String rcuSchemaPrefix, String dbNamespace,
      int dbPort, String dbUrl) throws ApiException {


    //TODO different secret name?
    CommonTestUtils.createDockerRegistrySecret(OCR_USERNAME, OCR_PASSWORD,
        OCR_EMAIL, OCR_REGISTRY, OCR_SECRET_NAME, dbNamespace);

    //TODO for Kind cluter?
    String imagePullPolicy = "IfNotPresent";
    if (!REPO_NAME.isEmpty()) {
      imagePullPolicy = "Always";
    }

    startOracleDB(dbBaseImageName, imagePullPolicy, dbPort, dbNamespace);
    createRcuSchema(rcuSchemaPrefix, imagePullPolicy, dbUrl, dbNamespace);

  }

  /**
   * Start Oracle DB pod and service in the specified namespace.
   *
   * @param dbBaseImageName full image name for DB deployment
   * @param imagePullPolicy policy for image pull
   * @param dbPort NodePort of DB
   * @param dbNamespace namespace where DB instance is going to start
   */
  public static void startOracleDB(String dbBaseImageName, String imagePullPolicy, int dbPort, String dbNamespace)
      throws ApiException {

    Map labels = new HashMap<String, String>();
    labels.put("app", "database");
    //labels.put(" version", "12.1.0.2");
    Map limits = new HashMap<String, String>();
    limits.put("cpu", "2");
    limits.put("memory", "10Gi");
    limits.put("ephemeral-storage", "8Gi");
    Map requests = new HashMap<String, String>();
    requests.put("cpu", "500m");
    requests.put("ephemeral-storage", "8Gi");
    //create V1Deployment  for Oracle DB
    logger.info("Configure V1Deployment in namespace {0} using image {1}", dbNamespace,  dbBaseImageName);
    oracleDbDepl = new V1Deployment()
        .apiVersion("apps/v1")
        .kind("Deployment")
        .metadata(new V1ObjectMeta()
            .name("oracle.db")
            .namespace(dbNamespace)
            .labels(labels))
        .spec(new V1DeploymentSpec()
            .replicas(1)
            .selector(new V1LabelSelector()
                .matchLabels(labels))
            .strategy(new V1DeploymentStrategy()
                 .rollingUpdate(new V1RollingUpdateDeployment()
                     .maxSurge(new IntOrString(1)) //TODO
                     .maxUnavailable(new IntOrString(1)))
                 .type("RollingUpdate"))
            .template(new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta()
                    .labels(labels))
                .spec(new V1PodSpec()
                    .containers(Arrays.asList(
                        new V1Container()
                            .addEnvItem(new V1EnvVar().name("DB_SID").value("devcdb"))
                            .addEnvItem(new V1EnvVar().name("DB_PDB").value("devpdb"))
                            .addEnvItem(new V1EnvVar().name("DB_DOMAIN").value("k8s"))
                            .addEnvItem(new V1EnvVar().name("DB_BUNDLE").value("basic"))
                            .image(dbBaseImageName)
                            .imagePullPolicy(imagePullPolicy)
                            .name("oracle-db")
                            .ports(Arrays.asList(
                                new V1ContainerPort()
                                .containerPort(1521)
                                .name("tns")
                                .protocol("TCP")
                                .hostPort(1521)))
                            .resources(new V1ResourceRequirements()
                                .limits(limits)
                                .requests(requests))
                            .terminationMessagePath("/dev/termination-log")
                            .terminationMessagePolicy("File")))
                    .dnsPolicy("ClusterFirst")
                    .restartPolicy("Always")
                    .schedulerName("default-scheduler")
                    .terminationGracePeriodSeconds(30L)
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))))));

    logger.info("Create deployment for Oracle DB in namespace {0}",
        dbNamespace);
    boolean deploymentCreated = assertDoesNotThrow(() -> Kubernetes.createDeployment(oracleDbDepl),
        String.format("Create deployment failed with ApiException for Oracle DB in namespace %s",
            dbNamespace));
    assertTrue(deploymentCreated, String.format(
        "Create deployment failed with ApiException for Oracle DB in namespace %s ",
        dbNamespace));

    //create V1Service for Oracle DB
    oracleDBService = new V1Service()
        .apiVersion("v1")
        .kind("Service")
        .metadata(new V1ObjectMeta()
            .name("oracle-db")
            .namespace(dbNamespace)
            .labels(labels))
        .spec(new V1ServiceSpec()
            .ports(Arrays.asList(
                new V1ServicePort()
                    .name("tns")
                    .port(8080)
                    .protocol("TCP")
                    .targetPort(new IntOrString(1521))
                    .nodePort(dbPort)))
            .selector(labels)
            .sessionAffinity("None")
            .type("LoadBalancer"));

    logger.info("Create service for Oracle DB service in namespace {0}", dbNamespace);
    boolean serviceCreated = assertDoesNotThrow(() -> Kubernetes.createService(oracleDBService),
        String.format("Create service failed with ApiException for oracleDBService in namespace %s",
            dbNamespace));
    assertTrue(serviceCreated, String.format(
        "Create service failed with ApiException for oracleDBService in namespace %s ", dbNamespace));

    // wait for the Oracle DB pod to be running
    String dbPodName = assertDoesNotThrow(() -> getPodNameOfDb(dbNamespace),
        String.format("Get Oracle DB pod name failed with ApiException for oracleDBService in namespace %s",
            dbNamespace));
    logger.info("Wait for the oracle Db pod: {0} running in namespace {1}", dbPodName, dbNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for Oracle DB to be running in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                dbNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podIsReady(dbNamespace, "app=database", dbPodName),
            "oracleDBService podRunning failed with ApiException"));

    // check if DB is ready to be used by searching pod log
    logger.info("Check for DB pod {0} log contains ready message  in namespace {1}",
        dbPodName, dbNamespace);
    String msg = "The database is ready for use";
    checkDbReady(msg, dbPodName, dbNamespace);

  }

  /**
   * Create a RCU where createRepository script runs.
   *
   * @param rcuPrefix prefix of RCU schema
   * @param imagePullPolicy image pull policy
   * @param dbUrl URL of DB
   * @param dbNamespace namespace of DB where RCU is
   * @throws ApiException when create RCU pod fails
   */
  public static void createRcuSchema(String rcuPrefix, String imagePullPolicy, String dbUrl, String dbNamespace)
      throws ApiException {

    logger.info("Create RCU pod for RCU prefix {0}", rcuPrefix);
    assertDoesNotThrow(() -> createRcuPod(rcuPrefix, imagePullPolicy, dbUrl, dbNamespace),
        String.format("Create RCU pod failed with ApiException for rcuPrefix: %s, imagePullPolicy: %s, "
                + "dbUrl: %s in namespace: %s", rcuPrefix, imagePullPolicy, dbUrl, dbNamespace));

    //createRepository();

  }

  /**
   * Create a RCU where createRepository script runs.
   *
   * @param rcuPrefix prefix of RCU schema
   * @param imagePullPolicy image pull policy
   * @param dbUrl URL of DB
   * @param dbNamespace namespace of DB where RCU is
   * @throws ApiException when create RCU pod fails
   */
  public static V1Pod createRcuPod(String rcuPrefix, String imagePullPolicy, String dbUrl, String dbNamespace)
      throws ApiException {

    ConditionFactory withStandardRetryPolicy = with().pollDelay(10, SECONDS)
        .and().with().pollInterval(2, SECONDS)
        .atMost(1, MINUTES).await();


    Map labels = new HashMap<String, String>();
    labels.put("ruc", "rcu");
    final String podName = "rcu";
    V1Pod podBody = new V1Pod()
        .apiVersion("v1")
        .kind("Pod")
        .metadata(new V1ObjectMeta()
            .name(podName)
            .namespace(dbNamespace)
            .labels(labels))
        .spec(new V1PodSpec()
            .containers(Arrays.asList(
                new V1Container()
                    .name("rcu")
                    .image(fmwBaseImageName)
                    .imagePullPolicy(imagePullPolicy)
                    .addArgsItem("sleep")
                    .addArgsItem("infinity")))
            .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))));
    V1Pod pvPod = Kubernetes.createPod(dbNamespace, podBody);

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for {0} to be ready in namespace {1}, "
                + "(elapsed time {2} , remaining time {3}",
                podName,
                dbNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(podReady(podName, null, dbNamespace));

    return pvPod;
  }


  private static String getPodNameOfDb(String dbNamespace) throws ApiException {

    V1PodList  pod = null;
    pod = Kubernetes.listPods(dbNamespace, null);

    //There is only one pod in the given DB namespace
    return pod.getItems().get(0).getMetadata().getName();
  }

  private static boolean checkPodLogContains(String matchStr, String podName, String namespace)
      throws ApiException {
    if (Kubernetes.getPodLog(podName,namespace,null).contains(matchStr)) {
      return true;
    }
    return false;
  }

  private static Callable<Boolean> podLogContains(String matchStr, String podName, String dbNamespace)
      throws ApiException {
    return () -> {
      return checkPodLogContains(matchStr, podName, dbNamespace);
    };
  }

  private static void checkDbReady(String matchStr, String podName, String dbNamespace) {
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for pod {0} log contain message {1} in namespace {2} "
                    + "(elapsed time {3}ms, remaining time {4}ms)",
                podName,
                matchStr,
                dbNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podLogContains(matchStr, podName, dbNamespace),
            String.format("podLogContains failed with ApiException for pod %s in namespace %s",
               podName, dbNamespace)));
  }

  /**
   * Checks if a pod is ready in a given namespace.
   *
   * @param namespace in which to check if the pod is ready
   * @param labelSelector the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if the pod is in the ready condition, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static boolean isPodReady(String namespace, String labelSelector, String podName) throws ApiException {
    boolean status = false;
    V1Pod pod = getPod(namespace, labelSelector, podName);
    if (pod != null) {

      // get the podCondition with the 'Ready' type field
      V1PodCondition v1PodReadyCondition = pod.getStatus().getConditions().stream()
          .filter(v1PodCondition -> "Ready".equals(v1PodCondition.getType()))
          .findAny()
          .orElse(null);

      if (v1PodReadyCondition != null) {
        status = v1PodReadyCondition.getStatus().equalsIgnoreCase("true");
        if (status) {
          logger.info("Pod {0} is READY in namespace {1}", podName, namespace);
        }
      }
    } else {
      logger.info("Pod {0} does not exist in namespace {1}", podName, namespace);
    }
    return status;
  }

  /**
   * Check if Pod is ready.
   *
   * @param namespace in which to check if the pod is ready
   * @param labelSelector the label the pod is decorated with
   * @param podName name of the pod to check for
   * @return true if the pod is in the ready condition, false otherwise
   * @throws ApiException if Kubernetes client API call fails
   */
  public static Callable<Boolean> podIsReady(String namespace,
                                             String labelSelector,
                                             String podName) throws ApiException {
    return () -> {
      return isPodReady(namespace, labelSelector, podName);
    };
  }



}

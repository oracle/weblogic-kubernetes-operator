// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.net.ssl.SSLProtocolException;

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
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.OCR_EMAIL;
import static oracle.weblogic.kubernetes.TestConstants.OCR_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.OCR_REGISTRY;
import static oracle.weblogic.kubernetes.TestConstants.OCR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCR_USERNAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.execCommand;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podReady;
import static oracle.weblogic.kubernetes.assertions.impl.Kubernetes.getPod;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Utility class to start DB service and RCU schema.
 */
public class DbUtils {

  private static final String CREATE_REPOSITORY_SCRIPT = "createRepository.sh";
  private static final String PASSWORD_FILE = "pwd.txt";
  private static final String RCUTYPE = "fmw";
  private static final String RCUPODNAME = "rcu";
  private static final String SYSPASSWORD = "Oradoc_db1";

  private static V1Service oracleDBService = null;
  private static V1Deployment oracleDbDepl = null;

  private static ConditionFactory withStandardRetryPolicy =
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(15, MINUTES).await();
  /**
   * Start Oracle DB instance, create rcu pod and load database schema in the specified namespace.
   *
   * @param dbImage image name of database
   * @param fmwImage image name of FMW
   * @param rcuSchemaPrefix rcu SchemaPrefix
   * @param dbNamespace namespace where DB and RCU schema are going to start
   * @param dbPort NodePort of DB
   * @param dbUrl URL of DB
   * @throws Exception if any error occurs when setting up RCU database
   */

  public static void setupDBandRCUschema(String dbImage, String fmwImage, String rcuSchemaPrefix, String dbNamespace,
      int dbPort, String dbUrl, boolean isUseSecret) throws ApiException {
    LoggingFacade logger = getLogger();
    // create pull secrets when running in non Kind Kubernetes cluster
    if (isUseSecret) {
      CommonTestUtils.createDockerRegistrySecret(OCR_USERNAME, OCR_PASSWORD,
          OCR_EMAIL, OCR_REGISTRY, OCR_SECRET_NAME, dbNamespace);
    }

    logger.info("Start Oracle DB with dbImage: {0}, dbPort: {1}, dbNamespace: {2}, isUseSecret: {3}",
        dbImage, dbPort, dbNamespace, isUseSecret);
    startOracleDB(dbImage, dbPort, dbNamespace, isUseSecret);
    logger.info("Create RCU schema with fmwImage: {0}, rcuSchemaPrefix: {1}, dbUrl: {2}, "
        + " dbNamespace: {3}, isUseSecret {4}:", fmwImage, rcuSchemaPrefix, dbUrl, dbNamespace, isUseSecret);
    createRcuSchema(fmwImage, rcuSchemaPrefix, dbUrl, dbNamespace, isUseSecret);

  }

  /**
   * Start Oracle DB pod and service in the specified namespace.
   *
   * @param dbBaseImageName full image name for DB deployment
   * @param dbPort NodePort of DB
   * @param dbNamespace namespace where DB instance is going to start
   */
  public static void startOracleDB(String dbBaseImageName, int dbPort, String dbNamespace, boolean isUseSecret)
      throws ApiException {
    LoggingFacade logger = getLogger();
    Map labels = new HashMap<String, String>();
    labels.put("app", "database");

    Map limits = new HashMap<String, String>();
    limits.put("cpu", "2");
    limits.put("memory", "10Gi");
    limits.put("ephemeral-storage", "8Gi");
    Map requests = new HashMap<String, String>();
    requests.put("cpu", "500m");
    requests.put("ephemeral-storage", "8Gi");

    //create V1Service for Oracle DB
    oracleDBService = new V1Service()
        .apiVersion("v1")
        .kind("Service")
        .metadata(new V1ObjectMeta()
            .name("oracledb")
            .namespace(dbNamespace)
            .labels(labels))
        .spec(new V1ServiceSpec()
            .ports(Arrays.asList(
                new V1ServicePort()
                    .name("tns")
                    .port(1521)
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
        "Create service failed for OracleDbService in namespace %s ", dbNamespace));

    //create V1Deployment for Oracle DB
    logger.info("Configure V1Deployment in namespace {0} using image {1}", dbNamespace,  dbBaseImageName);
    oracleDbDepl = new V1Deployment()
        .apiVersion("apps/v1")
        .kind("Deployment")
        .metadata(new V1ObjectMeta()
            .name("oracledb")
            .namespace(dbNamespace)
            .labels(labels))
        .spec(new V1DeploymentSpec()
            .replicas(1)
            .selector(new V1LabelSelector()
                .matchLabels(labels))
            .strategy(new V1DeploymentStrategy()
                 .rollingUpdate(new V1RollingUpdateDeployment()
                     .maxSurge(new IntOrString(1))
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
                            .imagePullPolicy("IfNotPresent")
                            .name("oracledb")
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
                    .imagePullSecrets(isUseSecret ? Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))
                        : null))));

    logger.info("Create deployment for Oracle DB in namespace {0}",
        dbNamespace);
    boolean deploymentCreated = assertDoesNotThrow(() -> Kubernetes.createDeployment(oracleDbDepl),
        String.format("Create deployment failed with ApiException for Oracle DB in namespace %s",
            dbNamespace));
    assertTrue(deploymentCreated, String.format(
        "Create deployment failed for oracleDbDepl in namespace %s ",
        dbNamespace));

    // sleep for a while to make sure the DB pod is created
    try {
      Thread.sleep(2 * 1000);
    } catch (InterruptedException ie) {
        // ignore
    }

    // wait for the Oracle DB pod to be ready
    String dbPodName = assertDoesNotThrow(() -> getPodNameOfDb(dbNamespace),
        String.format("Get Oracle DB pod name failed with ApiException for oracleDBService in namespace %s",
            dbNamespace));
    logger.info("Wait for the oracle Db pod: {0} ready in namespace {1}", dbPodName, dbNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for Oracle DB to be ready in namespace {0} "
                    + "(elapsed time {1}ms, remaining time {2}ms)",
                dbNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> podIsReady(dbNamespace, "app=database", dbPodName),
            "oracleDBService podReady failed with ApiException"));

    // check if DB is ready to be used by searching pod log
    logger.info("Check for DB pod {0} log contains ready message in namespace {1}",
        dbPodName, dbNamespace);
    String msg = "The database is ready for use";
    checkDbReady(msg, dbPodName, dbNamespace);

  }

  /**
   * Create a RCU schema in the namespace.
   *
   * @param fmwBaseImageName the FMW image name
   * @param rcuPrefix prefix of RCU schema
   * @param dbUrl URL of DB
   * @param dbNamespace namespace of DB where RCU is
   * @throws ApiException when create RCU pod fails
   */
  public static void createRcuSchema(String fmwBaseImageName, String rcuPrefix, String dbUrl,
      String dbNamespace, boolean isUseSecret) throws ApiException {
    LoggingFacade logger = getLogger();
    logger.info("Create RCU pod for RCU prefix {0}", rcuPrefix);
    assertDoesNotThrow(() -> createRcuPod(fmwBaseImageName, dbUrl, dbNamespace, isUseSecret),
        String.format("Creating RCU pod failed with ApiException for image: %s, rcuPrefix: %s, dbUrl: %s, "
                + "in namespace: %s", fmwBaseImageName, rcuPrefix, dbUrl, dbNamespace));

    assertTrue(assertDoesNotThrow(
        () -> createRcuRepository(dbNamespace, dbUrl, rcuPrefix),
        String.format("createRcuRepository failed for dbNamespace: %s, dbUrl: %s, rcuPrefix: %s",
            dbNamespace, dbUrl, rcuPrefix)));
  }

  /**
   * Create a RCU where createRepository script runs.
   *
   * @param fmwBaseImageName the FMW image name
   * @param dbUrl URL of DB
   * @param dbNamespace namespace of DB where RCU is
   * @throws ApiException when create RCU pod fails
   */
  public static V1Pod createRcuPod(String fmwBaseImageName, String dbUrl, String dbNamespace, boolean isUseSecret)
      throws ApiException {
    LoggingFacade logger = getLogger();
    ConditionFactory withStandardRetryPolicy = with().pollDelay(10, SECONDS)
        .and().with().pollInterval(2, SECONDS)
        .atMost(10, MINUTES).await();

    Map labels = new HashMap<String, String>();
    labels.put("ruc", "rcu");

    V1Pod podBody = new V1Pod()
        .apiVersion("v1")
        .kind("Pod")
        .metadata(new V1ObjectMeta()
            .name(RCUPODNAME)
            .namespace(dbNamespace)
            .labels(labels))
        .spec(new V1PodSpec()
            .containers(Arrays.asList(
                new V1Container()
                    .name("rcu")
                    .image(fmwBaseImageName)
                    .imagePullPolicy("IfNotPresent")
                    .addArgsItem("sleep")
                    .addArgsItem("infinity")))
            .imagePullSecrets(isUseSecret ? Arrays.asList(
                        new V1LocalObjectReference()
                            .name(OCR_SECRET_NAME))
                        : null));

    V1Pod pvPod = Kubernetes.createPod(dbNamespace, podBody);

    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for {0} to be ready in namespace {1}, "
                + "(elapsed time {2} , remaining time {3}",
                RCUPODNAME,
                dbNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(podReady(RCUPODNAME, null, dbNamespace));

    return pvPod;
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
    LoggingFacade logger = getLogger();
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
    return () -> isPodReady(namespace, labelSelector, podName);
  }

  private static boolean createRcuRepository(String dbNamespace, String dbUrl,
                                         String rcuSchemaPrefix)
      throws ApiException, IOException {
    LoggingFacade logger = getLogger();
    // copy the script and helper files into the RCU pod
    Path createRepositoryScript = Paths.get(RESOURCE_DIR, "bash-scripts", CREATE_REPOSITORY_SCRIPT);
    Path passwordFile = Paths.get(RESOURCE_DIR, "helper-files", PASSWORD_FILE);
    Path podCreateRepositoryScript = Paths.get("/u01/oracle", CREATE_REPOSITORY_SCRIPT);
    Path podPasswordFile = Paths.get("/u01/oracle", PASSWORD_FILE);

    logger.info("source file is: {0}, target file is: {1}", createRepositoryScript, podCreateRepositoryScript);
    FileUtils.copyFileToPod(dbNamespace, RCUPODNAME, null, createRepositoryScript, podCreateRepositoryScript);
    logger.info("source file is: {0}, target file is: {1}", passwordFile, podPasswordFile);
    FileUtils.copyFileToPod(dbNamespace, RCUPODNAME, null, passwordFile, podPasswordFile);

    String createRepository = "/u01/oracle/createRepository.sh";
    logger.info("Running the createRepository command: {0},  dbUrl: {1}, rcuSchemaPrefix: {2}, RCU type: {3}, "
        + "SYSPASSWORD: {4} ", createRepository, dbUrl, rcuSchemaPrefix, RCUTYPE, SYSPASSWORD);

    /* TODO The original code without encountering SSLProtocolException. Rollback to this oneWhen the bug is fixed.
    ExecResult execResult = assertDoesNotThrow(
        () -> execCommand(dbNamespace, RCUPODNAME,
            null, true, "/bin/bash", createRepository, dbUrl, rcuSchemaPrefix,
            RCUTYPE, SYSPASSWORD));
    logger.info("Inside RCU pod command createRepository return value: {0}", execResult.exitValue());
    if (execResult.exitValue() != 0) {
      logger.info("Inside RCU pod command createRepository return error {0}", execResult.stderr());
      return false;
    */
    try {
      execCommand(dbNamespace, RCUPODNAME,
          null, true, "/bin/bash", createRepository, dbUrl, rcuSchemaPrefix,
          RCUTYPE, SYSPASSWORD);

    } catch (SSLProtocolException e) {
      /* TODO For Api 8.0.2 it looks that there is a bug on the web socket code or a timing bug
      where it doesn't properly handle closing a socket that has already been closed by the other
      side. Sometimes on remote Jenkins cluster 10 when RCU creation is completed java.net.ssl.SSLProtocolException
      is thrown. Ignore it for now */
      return true;
    } catch (InterruptedException e) {
      return false;
    } catch (ApiException e) {
      return false;
    }

    return true;
  }

  private static String getPodNameOfDb(String dbNamespace) throws ApiException {

    V1PodList  pod = null;
    pod = Kubernetes.listPods(dbNamespace, null);

    String podName = null;

    //There is only one pod in the given DB namespace
    if (pod != null) {
      podName = pod.getItems().get(0).getMetadata().getName();
    }

    return podName;
  }

  private static boolean checkPodLogContains(String matchStr, String podName, String namespace)
      throws ApiException {

    return Kubernetes.getPodLog(podName,namespace,null).contains(matchStr);

  }

  private static Callable<Boolean> podLogContains(String matchStr, String podName, String dbNamespace)
      throws ApiException {
    return () -> checkPodLogContains(matchStr, podName, dbNamespace);
  }

  private static void checkDbReady(String matchStr, String podName, String dbNamespace) {
    LoggingFacade logger = getLogger();
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
            String.format("podLogContains failed with ApiException for pod %s in namespace %s", podName, dbNamespace)));
  }

}

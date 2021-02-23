// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespacedJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.jobCompleted;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utility class to deploy application to WebLogic server.
 */
public class DeployUtil {

  private static final String MOUNT_POINT = "/deployScripts/";
  private static final String DEPLOY_SCRIPT = "application_deployment.py";
  private static final String DOMAIN_PROPERTIES = "domain.properties";

  private static final ConditionFactory withStandardRetryPolicy
      = with().pollDelay(2, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(5, MINUTES).await();

  /**
   * Deploy application.
   *
   * @param host name of the admin server host
   * @param port default channel node port of admin server
   * @param userName admin server user name
   * @param password admin server password
   * @param targets comma separated list of targets to deploy applications
   * @param archivePath local path of the application archive
   * @param namespace name of the namespace in which WebLogic server pods running
   */
  public static void deployUsingWlst(String host, String port, String userName,
                                     String password, String targets, Path archivePath, String namespace) {
    final LoggingFacade logger = getLogger();

    // this secret is used only for non-kind cluster
    createSecretForBaseImages(namespace);


    // create a temporary WebLogic domain property file
    File domainPropertiesFile = assertDoesNotThrow(() -> File.createTempFile("domain", "properties"),
        "Creating domain properties file failed");
    Properties p = new Properties();
    p.setProperty("node_archive_path", MOUNT_POINT + archivePath.getFileName());
    p.setProperty("admin_host", host);
    p.setProperty("admin_port", port);
    p.setProperty("admin_username", userName);
    p.setProperty("admin_password", password);
    p.setProperty("targets", targets);
    assertDoesNotThrow(() -> p.store(new FileOutputStream(domainPropertiesFile), "wlst properties file"),
        "Failed to write the domain properties to file");

    // WLST py script for deploying application
    Path deployScript = Paths.get(RESOURCE_DIR, "python-scripts", DEPLOY_SCRIPT);

    logger.info("Creating a config map to hold deployment files");
    String uniqueName = Namespace.uniqueName();
    String deployScriptConfigMapName = "wlst-deploy-scripts-cm-" + uniqueName;

    Map<String, String> data = new HashMap<>();
    Map<String, byte[]> binaryData = new HashMap<>();
    assertDoesNotThrow(() -> {
      data.put(DEPLOY_SCRIPT, Files.readString(deployScript));
      data.put(DOMAIN_PROPERTIES, Files.readString(domainPropertiesFile.toPath()));
      binaryData.put(archivePath.getFileName().toString(),
          Base64.getMimeEncoder().encode(Files.readAllBytes(archivePath)));
    });

    V1ObjectMeta meta = new V1ObjectMeta()
        .name(deployScriptConfigMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .binaryData(binaryData)
        .metadata(meta);

    assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Failed to create configmap %s with files", configMap));

    // deploy application with deploy scripts and domain properties on persistent volume
    deploy(namespace, deployScriptConfigMapName);
  }

  /**
   * Deploy application by creating a job.
   *
   * @param namespace namespace in which to create job
   * @param deployScriptConfigMapName configmap containing deployment scripts
   */
  private static void deploy(String namespace, String deployScriptConfigMapName) {
    LoggingFacade logger = getLogger();
    logger.info("Preparing to run deploy job using WLST");
    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
        .addArgsItem(MOUNT_POINT + "/" + DEPLOY_SCRIPT) //wlst deploy py script
        .addArgsItem("-skipWLSModuleScanning")
        .addArgsItem("-loadProperties")
        .addArgsItem(MOUNT_POINT + "/" + DOMAIN_PROPERTIES); //domain property file

    logger.info("Running a Kubernetes job to deploy");
    assertDoesNotThrow(()
        -> createDeployJob(deployScriptConfigMapName, namespace, jobCreationContainer),
        "Deployment failed");
  }

  /**
   * Create a job to deploy the application to domain.
   *
   * @param deployScriptConfigMap configmap holding deployment files
   * @param namespace name of the namespace in which the job is created
   * @param jobContainer V1Container with job commands to deploy archive
   * @throws ApiException when Kubernetes cluster query fails
   */
  private static void createDeployJob(String deployScriptConfigMap, String namespace,
                                      V1Container jobContainer) throws ApiException {
    LoggingFacade logger = getLogger();
    logger.info("Running Kubernetes job to deploy application");
    String uniqueName = Namespace.uniqueName();
    String name = "wlst-deploy-job-" + uniqueName;

    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name(name)
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .containers(Arrays.asList(jobContainer
                        .name("deploy-application-container")
                        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                        .imagePullPolicy("IfNotPresent")
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("deploy-job-cm-volume") // deployment files scripts volume
                                .mountPath(MOUNT_POINT))))) // mounted under /deploySctipts inside pod
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name("deploy-job-cm-volume") // deployment scripts volume
                            .configMap(new V1ConfigMapVolumeSource()
                                .name(deployScriptConfigMap)))) //config map containing deployment scripts
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET)))))); // this secret is used only for non-kind cluster
    String jobName = assertDoesNotThrow(()
        -> createNamespacedJob(jobBody), "Failed to create deploy Job");

    logger.info("Checking if the deploy job {0} completed in namespace {1}",
        jobName, namespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for job {0} to be completed in namespace {1} "
                    + "(elapsed time {2} ms, remaining time {3} ms)",
                jobName,
                namespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(jobCompleted(jobName, null, namespace));

    // check job status and fail test if the job failed to deploy
    V1Job job = getJob(jobName, namespace);
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to do deployment", jobName);
        List<V1Pod> pods = listPods(namespace, "job-name=" + jobName).getItems();
        if (!pods.isEmpty()) {
          logger.severe(getPodLog(pods.get(0).getMetadata().getName(), namespace));
          fail("Deployment job failed");
        }
      }
    }

  }

  /**
   * Deploy an application to a cluster using REST API with curl utility.
   * @param host name of the admin server host
   * @param port node port of admin server
   * @param userName admin server user name
   * @param password admin server password
   * @param cluster name of the cluster to deploy application
   * @param archivePath local path of the application archive
   * @param hostHeader Host header for the curl command 
   * @param appName name of the application
   * @return ExecResult 
   */
  public static ExecResult deployToClusterUsingRest(String host, String port,
            String userName, String password, String cluster, 
            Path archivePath, String hostHeader, String appName) {
    String target = "{identity: [clusters,'" + cluster + "']}";
    return deployUsingRest(host, port, userName, password, target, archivePath, hostHeader, appName);
  }

  /**
   * Deploy an application to a server using REST API with curl utility.
   * @param host name of the admin server host
   * @param port node port of admin server
   * @param userName admin server user name
   * @param password admin server password
   * @param server name of the server to deploy application
   * @param archivePath local path of the application archive
   * @param hostHeader Host header for the curl command 
   * @param appName name of the application
   * @return ExecResult 
   */
  public static ExecResult deployToServerUsingRest(String host, String port,
            String userName, String password, String server, 
            Path archivePath, String hostHeader, String appName) {
    String target = "{identity: [servers,'" + server + "']}";
    return deployUsingRest(host, port, userName, password, target, archivePath, hostHeader, appName);
  }

  /**
   * Deploy an application to a set of target using REST API with curl utility.
   * Note the targets parameter should be a string with following format
   *  {identity: [clusters,'cluster-1']}   for cluster target 
   *  {identity: [servers, 'admin-server']} for server target 
   *  OR a combination for multiple targets, for example 
   *  {identity: [clusters,'mycluster']}, {identity: [servers,'admin-server']}
   * @param host name of the admin server host
   * @param port node port of admin server
   * @param userName admin server user name
   * @param password admin server password
   * @param targets  the target string to deploy application
   * @param archivePath local path of the application archive
   * @param hostHeader Host header for the curl command 
   * @param appName name of the application
   * @return ExecResult 
   */
  public static ExecResult deployUsingRest(String host, String port,
            String userName, String password, String targets, 
            Path archivePath, String hostHeader, String appName) {
    final LoggingFacade logger = getLogger();
    ExecResult result = null;
    StringBuffer headerString = null;
    if (hostHeader != null) {
      headerString = new StringBuffer("-H 'host: ");
      headerString.append(hostHeader)
                  .append(" ' ");
    } else {
      headerString = new StringBuffer("");
    }
    StringBuffer curlString = new StringBuffer("status=$(curl --noproxy '*' ");
    curlString.append(" --user " + userName + ":" + password);
    curlString.append(" -w %{http_code} --show-error -o /dev/null ")
        .append(headerString.toString())
        .append("-H X-Requested-By:MyClient ")
        .append("-H Accept:application/json  ")
        .append("-H Content-Type:multipart/form-data ")
        .append("-H Prefer:respond-async ")
        .append("-F \"model={ name: '")
        .append(appName)
        .append("', targets: [ ")
        .append(targets)
        .append(" ] }\" ")
        .append(" -F \"sourcePath=@")
        .append(archivePath.toString() + "\" ")
        .append("-X POST http://" + host + ":" + port)
        .append("/management/weblogic/latest/edit/appDeployments); ")
        .append("echo ${status}");

    logger.info("deployUsingRest: curl command {0}", new String(curlString));
    try {
      result = exec(new String(curlString), true);
    } catch (Exception ex) {
      logger.info("deployUsingRest: caught unexpected exception {0}", ex);
      return null;
    }
    return result;
  }
}

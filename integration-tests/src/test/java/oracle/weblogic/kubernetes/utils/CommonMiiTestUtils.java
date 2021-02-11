// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretReference;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;
import org.joda.time.DateTime;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_DEPLOYMENT_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listConfigMaps;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getIntrospectJobName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The common utility class for model-in-image tests.
 */
public class CommonMiiTestUtils {
  /**
   * Create a basic Kubernetes domain resource and wait until the domain is fully up.
   *
   * @param domainNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param imageName name of the image including its tag
   * @param adminServerPodName name of the admin server pod
   * @param managedServerPrefix prefix of the managed server pods
   * @param replicaCount number of managed servers to start
   */
  public static void createMiiDomainAndVerify(
      String domainNamespace,
      String domainUid,
      String imageName,
      String adminServerPodName,
      String managedServerPrefix,
      int replicaCount
  ) {
    LoggingFacade logger = getLogger();
    // this secret is used only for non-kind cluster
    logger.info("Create the repo secret {0} to pull the image", OCIR_SECRET_NAME);
    assertDoesNotThrow(() -> createOcirRepoSecret(domainNamespace),
            String.format("createSecret failed for %s", OCIR_SECRET_NAME));

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        encryptionSecretName,
        domainNamespace,
        "weblogicenc",
        "weblogicenc"),
        String.format("createSecret failed for %s", encryptionSecretName));

    // create the domain custom resource
    logger.info("Create domain resource {0} object in namespace {1} and verify that it is created",
        domainUid, domainNamespace);
    Domain domain = createDomainResource(
        domainUid,
        domainNamespace,
        imageName,
        adminSecretName,
        OCIR_SECRET_NAME,
        encryptionSecretName,
        replicaCount,
        "cluster-1");

    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }

    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed server service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }
  }

  /**
   * Create a domain object for a Kubernetes domain custom resource using the basic model-in-image
   * image.
   *
   * @param domainResourceName name of the domain resource
   * @param domNamespace Kubernetes namespace that the domain is hosted
   * @param imageName name of the image including its tag
   * @param adminSecretName name of the new WebLogic admin credentials secret
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param replicaCount number of managed servers to start
   * @param clusterName name of the cluster to add in domain
   * @return domain object of the domain resource
   */
  public static Domain createDomainResource(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      String clusterName) {

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new io.kubernetes.client.openapi.models.V1ObjectMeta()
            .name(domainResourceName)
            .namespace(domNamespace))
        .spec(new oracle.weblogic.domain.DomainSpec()
            .domainUid(domainResourceName)
            .domainHomeSourceType("FromModel")
            .image(imageName)
            .addImagePullSecretsItem(new io.kubernetes.client.openapi.models.V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new io.kubernetes.client.openapi.models.V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new oracle.weblogic.domain.ServerPod()
                .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new oracle.weblogic.domain.AdminServer()
                .serverStartState("RUNNING")
                .adminService(new oracle.weblogic.domain.AdminService()
                    .addChannelsItem(new oracle.weblogic.domain.Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new oracle.weblogic.domain.Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new oracle.weblogic.domain.Configuration()
                .model(new oracle.weblogic.domain.Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    setPodAntiAffinity(domain);
    return domain;
  }

  /**
   * Create a domain object for a Kubernetes domain custom resource using the basic model-in-image
   * image.
   *
   * @param domainResourceName name of the domain resource
   * @param domNamespace Kubernetes namespace that the domain is hosted
   * @param imageName name of the image including its tag
   * @param adminSecretName name of the new WebLogic admin credentials secret
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param replicaCount number of managed servers to start
   * @param pvName Name of persistent volume
   * @param pvcName Name of persistent volume claim
   * @param clusterName name of the cluster to add in domain
   * @param configMapName name of the configMap containing Weblogic Deploy Tooling model
   * @param dbSecretName name of the Secret for WebLogic configuration overrides
   * @param onlineUpdateEnabled whether to enable onlineUpdate feature for mii dynamic update
   * @return domain object of the domain resource
   */
  public static Domain createDomainResourceWithLogHome(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      String pvName,
      String pvcName,
      String clusterName,
      String configMapName,
      String dbSecretName,
      boolean onlineUpdateEnabled) {
    LoggingFacade logger = getLogger();

    List<String> securityList = new ArrayList<>();
    securityList.add(dbSecretName);

    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainResourceName)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainResourceName)
            .domainHomeSourceType("FromModel")
            .image(imageName)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/logs")
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .secrets(securityList)
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configMapName)
                    .runtimeEncryptionSecret(encryptionSecretName)
                    .onlineUpdate(new OnlineUpdate()
                        .enabled(onlineUpdateEnabled)))
                .introspectorJobActiveDeadlineSeconds(300L)));

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainResourceName, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainResourceName, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainResourceName, domNamespace));

    setPodAntiAffinity(domain);
    return domain;
  }

  /**
   * Replace contents of an existing configMap, by deleting and recreating the configMap
   * with the provided list of model file(s).
   *
   * @param configMapName name of the configMap containing Weblogic Deploy Tooling model to have its
   *                      contents replaced
   * @param domainResourceName name of the domain resource
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param modelFiles list of the names of the WDT mode files in the ConfigMap
   * @param retryPolicy ConditionFactory used for checking if the configMap has been deleted
   */
  public static void replaceConfigMapWithModelFiles(
      String configMapName,
      String domainResourceName,
      String domainNamespace,
      List<String> modelFiles,
      ConditionFactory retryPolicy) {
    LoggingFacade logger = getLogger();

    deleteConfigMap(configMapName, domainNamespace);
    retryPolicy
        .conditionEvaluationListener(
            condition ->
                logger.info(
                    "Waiting for configmap {0} to be deleted. Elapsed time{1}, remaining time {2}",
                    configMapName,
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS()))
        .until(
            () -> {
              return listConfigMaps(domainNamespace).getItems().stream()
                  .noneMatch((cm) -> (cm.getMetadata().getName().equals(configMapName)));
            });

    createConfigMapAndVerify(configMapName, domainResourceName, domainNamespace, modelFiles);
  }

  /**
   * Use REST APIs to return the JdbcRuntime mbean from the WebLogic server.
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcesName Name of the JDBC system resource for which that mbean data to be queried
   * @return An ExecResult containing the output of the REST API exec request
   */
  public static ExecResult readJdbcRuntime(
      String domainNamespace, String adminServerPodName, String resourcesName) {
    return readRuntimeResource(
        domainNamespace,
        adminServerPodName,
        "/management/wls/latest/datasources/id/" + resourcesName,
        "checkJdbcRuntime");
  }

  /**
   * Use REST APIs to return the MinThreadsConstraint runtime mbean associated with
   * the specified work manager from the WebLogic server.
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param serverName Name of the server from which to look for the runtime mbean
   * @param workManagerName Name of the work manager for which its associated
   *                       min threads constraint runtime mbean data to be queried
   * @return An ExecResult containing the output of the REST API exec request
   */
  public static ExecResult readMinThreadsConstraintRuntimeForWorkManager(
      String domainNamespace, String adminServerPodName,
      String serverName, String workManagerName) {
    return readRuntimeResource(
        domainNamespace,
        adminServerPodName,
        "/management/weblogic/latest/domainRuntime/serverRuntimes/"
            + serverName
            + "/applicationRuntimes/" + MII_BASIC_APP_DEPLOYMENT_NAME
            + "/workManagerRuntimes/" + workManagerName
            + "/minThreadsConstraintRuntime",
        "checkMinThreadsConstraintRuntime");
  }

  /**
   * Use REST APIs to return the MaxThreadsConstraint runtime mbean associated with
   * the specified work manager from the WebLogic server.
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param serverName Name of the server from which to look for the runtime mbean
   * @param workManagerName Name of the work manager for which its associated
   *                       max threads constraint runtime mbean data to be queried
   * @return An ExecResult containing the output of the REST API exec request
   */
  public static ExecResult readMaxThreadsConstraintRuntimeForWorkManager(
      String domainNamespace, String adminServerPodName,
      String serverName, String workManagerName) {
    return readRuntimeResource(
        domainNamespace,
        adminServerPodName,
        "/management/weblogic/latest/domainRuntime/serverRuntimes/"
            + serverName
            + "/applicationRuntimes/" + MII_BASIC_APP_DEPLOYMENT_NAME
            + "/workManagerRuntimes/" + workManagerName
            + "/maxThreadsConstraintRuntime",
        "checkMaxThreadsConstraintRuntime");
  }

  /**
   * Use REST APIs to check the WorkManager runtime mbean from the WebLogic server.
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param serverName Name of the server from which to look for the runtime mbean
   * @param workManagerName Name of the work manager for which its runtime mbean is to be verified
   * @param expectedStatusCode the expected response to verify
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkWorkManagerRuntime(
      String domainNamespace, String adminServerPodName,
      String serverName, String workManagerName, String expectedStatusCode) {
    return checkWeblogicMBean(
        domainNamespace,
        adminServerPodName,
        "/management/weblogic/latest/domainRuntime/serverRuntimes/"
            + serverName
            + "/applicationRuntimes/" + MII_BASIC_APP_DEPLOYMENT_NAME
            + "/workManagerRuntimes/" + workManagerName,
        expectedStatusCode);
  }

  /**
   * Use REST APIs to check the application runtime mbean from the WebLogic server.
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param serverName Name of the server from which to look for the runtime mbean
   * @param expectedStatusCode the expected response to verify
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkApplicationRuntime(
      String domainNamespace, String adminServerPodName,
      String serverName, String expectedStatusCode) {
    return checkWeblogicMBean(
        domainNamespace,
        adminServerPodName,
        "/management/weblogic/latest/domainRuntime/serverRuntimes/"
            + serverName
            + "/applicationRuntimes/" + MII_BASIC_APP_DEPLOYMENT_NAME,
        expectedStatusCode);
  }

  private static ExecResult readRuntimeResource(String domainNamespace, String adminServerPodName,
      String resourcePath, String callerName) {
    LoggingFacade logger = getLogger();

    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    ExecResult result = null;

    StringBuffer curlString = new StringBuffer("curl --user "
        + ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT + " ");
    curlString.append("http://" + K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
        .append(resourcePath)
        .append("/")
        .append(" --silent --show-error ");
    logger.info(callerName + ": curl command {0}", new String(curlString));
    try {
      result = exec(new String(curlString), true);
      logger.info(callerName + ": exec curl command {0} got: {1}", new String(curlString), result);
    } catch (Exception ex) {
      logger.info(callerName + ": caught unexpected exception {0}", ex);
      return null;
    }
    return result;
  }

  /**
   * Use REST APIs to check a runtime mbean from the WebLogic server.
   *
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcePath Path of the system resource to be used in the REST API call
   * @param expectedStatusCode the expected response to verify
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkWeblogicMBean(String domainNamespace,
         String adminServerPodName,  String resourcePath, String expectedStatusCode) {
    return checkWeblogicMBean(domainNamespace, adminServerPodName, resourcePath, expectedStatusCode, false, "");
  }

  /**
   * Use REST APIs to check a runtime mbean from the WebLogic server.
   *
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcePath Path of the system resource to be used in the REST API call
   * @param expectedStatusCode the expected response to verify
   * @param isSecureMode whether use SSL
   * @param sslChannelName the channel name for SSL
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkWeblogicMBean(String domainNamespace,
                                           String adminServerPodName,
                                           String resourcePath,
                                           String expectedStatusCode,
                                           boolean isSecureMode,
                                           String sslChannelName) {
    LoggingFacade logger = getLogger();

    int adminServiceNodePort;
    if (isSecureMode) {
      adminServiceNodePort =
          getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), sslChannelName);
    } else {
      adminServiceNodePort =
          getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    }

    StringBuffer curlString;
    if (isSecureMode) {
      curlString = new StringBuffer("status=$(curl -k --user weblogic:welcome1 https://");
    } else {
      curlString = new StringBuffer("status=$(curl --user weblogic:welcome1 http://");
    }

    curlString.append(K8S_NODEPORT_HOST + ":" + adminServiceNodePort)
        .append(resourcePath)
        .append(" --silent --show-error ")
        .append(" -o /dev/null ")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkSystemResource: curl command {0}", new String(curlString));
    return new Command()
        .withParams(new CommandParams()
            .command(curlString.toString()))
        .executeAndVerify(expectedStatusCode);
  }

  /**
   * Use REST APIs to check the system resource runtime mbean from the WebLogic server.
   *
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcesType Type of the system resource to be checked
   * @param resourcesName Name of the system resource to be checked
   * @param expectedStatusCode the expected response to verify
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkSystemResourceConfiguration(String domainNamespace,
      String adminServerPodName, String resourcesType,
      String resourcesName, String expectedStatusCode) {
    return checkWeblogicMBean(domainNamespace, adminServerPodName,
        "/management/weblogic/latest/domainConfig/"
            + resourcesType + "/" + resourcesName + "/",
        expectedStatusCode);
  }

  /**
   * Create a job to change the permissions on the pv host path.
   *
   * @param pvName Name of the persistent volume
   * @param pvcName Name of the persistent volume claim
   * @param namespace Namespace containing the persistent volume claim and where the job should be created in
   */
  public static void createJobToChangePermissionsOnPvHostPath(String pvName, String pvcName, String namespace) {
    LoggingFacade logger = getLogger();

    logger.info("Running Kubernetes job to create domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("change-permissions-onpv-job-" + pvName) // name of the job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .addContainersItem(new V1Container()
                        .name("fix-pvc-owner") // change the ownership of the pv to opc:opc
                        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                        .addCommandItem("/bin/sh")
                        .addArgsItem("-c")
                        .addArgsItem("chown -R 1000:1000 /shared")
                        .addVolumeMountsItem(
                            new V1VolumeMount()
                                .name(pvName)
                                .mountPath("/shared"))
                        .securityContext(new V1SecurityContext()
                            .runAsGroup(0L)
                            .runAsUser(0L))) // mounted under /shared inside pod
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName))))
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET)))))); // this secret is used only for non-kind cluster

    String jobName = createJobAndWaitUntilComplete(jobBody, namespace);

    // check job status and fail test if the job failed
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        logger.severe("Job {0} failed to change permissions on PV hostpath", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty()) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          logger.severe(podLog);
          fail("Change permissions on PV hostpath job failed");
        }
      }
    }
  }

  /**
   * Check logs are written on PV by running the specified command on the pod.
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param commandToExecuteInsidePod The command to be run inside the pod
   * @param podName Name of the pod
   */
  public static void checkLogsOnPV(String domainNamespace, String commandToExecuteInsidePod, String podName) {
    LoggingFacade logger = getLogger();

    logger.info("Checking logs are written on PV by running the command {0} on pod {1}, namespace {2}",
        commandToExecuteInsidePod, podName, domainNamespace);
    V1Pod serverPod = assertDoesNotThrow(() ->
            Kubernetes.getPod(domainNamespace, null, podName),
        String.format("Could not get the server Pod {0} in namespace {1}",
            podName, domainNamespace));

    ExecResult result = assertDoesNotThrow(() -> Kubernetes.exec(serverPod, null, true,
        "/bin/sh", "-c", commandToExecuteInsidePod),
        String.format("Could not execute the command %s in pod %s, namespace %s",
            commandToExecuteInsidePod, podName, domainNamespace));
    logger.info("Command {0} returned with exit value {1}, stderr {2}, stdout {3}",
        commandToExecuteInsidePod, result.exitValue(), result.stderr(), result.stdout());

    // checking for exitValue 0 for success fails sometimes as k8s exec api returns non-zero exit value even on success,
    // so checking for exitValue non-zero and stderr not empty for failure, otherwise its success
    assertFalse(result.exitValue() != 0 && result.stderr() != null && !result.stderr().isEmpty(),
        String.format("Command %s failed with exit value %s, stderr %s, stdout %s",
            commandToExecuteInsidePod, result.exitValue(), result.stderr(), result.stdout()));

  }

  /**
   * Create a database secret.
   * @param secretName Name of the secret
   * @param username username to be added to the secret
   * @param password password to be added to the secret
   * @param dburl url of the database to be added to the secret
   * @param domNamespace Kubernetes namespace to create the secret in
   */
  public static void createDatabaseSecret(
      String secretName, String username, String password,
      String dburl, String domNamespace)  {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    secretMap.put("url", dburl);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(domNamespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s", secretName, domNamespace));
  }

  /**
   * Create a domain secret.
   * @param secretName Name of the secret
   * @param username username to be added to the secret
   * @param password password to be added to the secret
   * @param domNamespace Kubernetes namespace to create the secret in
   */
  public static void createDomainSecret(String secretName, String username, String password, String domNamespace) {
    Map<String, String> secretMap = new HashMap();
    secretMap.put("username", username);
    secretMap.put("password", password);
    boolean secretCreated = assertDoesNotThrow(() -> createSecret(new V1Secret()
        .metadata(new V1ObjectMeta()
            .name(secretName)
            .namespace(domNamespace))
        .stringData(secretMap)), "Create secret failed with ApiException");
    assertTrue(secretCreated, String.format("create secret failed for %s in namespace %s", secretName, domNamespace));
  }

  /**
   * Verify the introspector runs, completed and deleted.
   * @param domainUid domain uid for which the introspector runs
   * @param domainNamespace domain namespace where the domain exists
   */
  public static void verifyIntrospectorRuns(String domainUid, String domainNamespace) {
    //verify the introspector pod is created and runs
    LoggingFacade logger = getLogger();
    logger.info("Verifying introspector pod is created, runs and deleted");
    String introspectJobName = getIntrospectJobName(domainUid);
    checkPodExists(introspectJobName, domainUid, domainNamespace);
    checkPodDoesNotExist(introspectJobName, domainUid, domainNamespace);
  }

  /**
   * Verify the pods of the domain are not rolled.
   * @param domainNamespace namespace where pods exists
   * @param podsCreationTimes map of pods name and pod creation times
   */
  public static void verifyPodsNotRolled(String domainNamespace, Map<String, DateTime> podsCreationTimes) {
    // wait for 2 minutes before checking the pods, make right decision logic
    // that runs every two minutes in the  Operator
    try {
      getLogger().info("Sleep 2 minutes for operator make right decision logic");
      Thread.sleep(120 * 1000);
    } catch (InterruptedException ie) {
      getLogger().info("InterruptedException while sleeping for 2 minutes");
    }
    for (Map.Entry<String, DateTime> entry : podsCreationTimes.entrySet()) {
      assertEquals(
          entry.getValue(),
          getPodCreationTime(domainNamespace, entry.getKey()),
          "pod '" + entry.getKey() + "' should not roll");
    }
  }

  /**
   * Verify the pod introspect version is updated.
   * @param podNames name of the pod
   * @param expectedIntrospectVersion expected introspect version
   * @param domainNamespace domain namespace where pods exist
   */
  public static void verifyPodIntrospectVersionUpdated(Set<String> podNames,
                                                 String expectedIntrospectVersion,
                                                 String domainNamespace) {

    for (String podName : podNames) {
      with().pollDelay(2, SECONDS)
          .and().with().pollInterval(10, SECONDS)
          .atMost(5, MINUTES).await()
          .conditionEvaluationListener(
              condition ->
                  getLogger().info(
                      "Checking for updated introspectVersion for pod {0}. "
                          + "Elapsed time {1}ms, remaining time {2}ms",
                      podName, condition.getElapsedTimeInMS(), condition.getRemainingTimeInMS()))
          .until(
              () ->
                  podIntrospectVersionUpdated(podName, domainNamespace, expectedIntrospectVersion));
    }
  }
}

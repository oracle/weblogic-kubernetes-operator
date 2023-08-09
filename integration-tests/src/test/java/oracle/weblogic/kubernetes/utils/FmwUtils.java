// Copyright (c) 2021, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.CreateIfNotExists;
import oracle.weblogic.domain.DomainCreationImage;
import oracle.weblogic.domain.DomainOnPV;
import oracle.weblogic.domain.DomainOnPVType;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.InitializeDomainOnPV;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.Opss;
import oracle.weblogic.domain.PersistentVolume;
import oracle.weblogic.domain.PersistentVolumeClaim;
import oracle.weblogic.domain.PersistentVolumeClaimSpec;
import oracle.weblogic.domain.PersistentVolumeSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.jetbrains.annotations.NotNull;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.YAML_MAX_FILE_SIZE_PROPERTY;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVHostPathDir;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Common utility methods for FMW Domain.
 */
public class FmwUtils {

  private static LoggingFacade logger = null;

  /**
   * Construct a domain object with the given parameters that can be used to create a domain resource.
   * @param domainUid unique Uid of the domain
   * @param domNamespace  namespace where the domain exists
   * @param adminSecretName  name of admin secret
   * @param repoSecretName name of repository secret
   * @param encryptionSecretName name of encryption secret
   * @param rcuAccessSecretName name of RCU access secret
   * @param opssWalletPasswordSecretName name of opss wallet password secret
   * @param miiImage name of model in image
   * @return Domain WebLogic domain
   */
  public static DomainResource createDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, String rcuAccessSecretName,
      String opssWalletPasswordSecretName, String miiImage) {

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .introspectVersion("1")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addEnvItem(new V1EnvVar()
                    .name("WLSDEPLOY_PROPERTIES")
                    .value(YAML_MAX_FILE_SIZE_PROPERTY)))
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .configuration(new Configuration()
                .opss(new Opss()
                    .walletPasswordSecret(opssWalletPasswordSecretName))
                .model(new Model()
                    .domainType("JRF")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .addSecretsItem(rcuAccessSecretName)
                .introspectorJobActiveDeadlineSeconds(900L)));

    return domain;
  }

  /**
   * Construct a domain object with the given parameters that can be used to create a domain resource.
   * @param domainUid unique Uid of the domain
   * @param domNamespace  namespace where the domain exists
   * @param adminSecretName  name of admin secret
   * @param repoSecretName name of repository secret
   * @param encryptionSecretName name of encryption secret
   * @param rcuAccessSecretName name of RCU access secret
   * @param opssWalletPasswordSecretName name of opss wallet password secret
   * @param replicaCount count of replicas
   * @param miiImage name of model in image
   * @param maxServerPodReadyWaitTime maximum time to wait for a server pod to be ready
   * @return Domain WebLogic domain
   */
  public static DomainResource createDomainResourceWithMaxServerPodReadyWaitTime(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, String rcuAccessSecretName,
      String opssWalletPasswordSecretName, int replicaCount, String miiImage, long maxServerPodReadyWaitTime) {
    // create the domain CR
    DomainResource domain = createDomainResource(domainUid, domNamespace,
        adminSecretName, repoSecretName, encryptionSecretName,
        rcuAccessSecretName, opssWalletPasswordSecretName, miiImage);
    domain.getSpec().getServerPod().setMaxReadyWaitTimeSeconds(maxServerPodReadyWaitTime);

    return domain;
  }

  /**
   * Construct a domain object with the given parameters that can be used to create a domain resource.
   * @param domainUid unique Uid of the domain
   * @param domNamespace  namespace where the domain exists
   * @param adminSecretName  name of admin secret
   * @param repoSecretName name of repository secret
   * @param encryptionSecretName name of encryption secret
   * @param rcuAccessSecretName name of RCU access secret
   * @param opssWalletPasswordSecretName name of opss wallet password secret
   * @param replicaCount count of replicas
   * @param miiImage name of model in image
   * @return Domain WebLogic domain
   */
  public static DomainResource createIstioDomainResource(
      String domainUid, String domNamespace, String adminSecretName,
      String repoSecretName, String encryptionSecretName, String rcuAccessSecretName,
      String opssWalletPasswordSecretName, int replicaCount, String miiImage, String configmapName) {

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .introspectVersion("1")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .configuration(new Configuration()
                .opss(new Opss()
                    .walletPasswordSecret(opssWalletPasswordSecretName))
                .model(new Model()
                    .domainType("JRF")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .addSecretsItem(rcuAccessSecretName)
                .introspectorJobActiveDeadlineSeconds(600L)));

    return domain;
  }

  /**
   * Construct a domain object with the given parameters that can be used to create a domain resource.
   * @param domainUid unique Uid of the domain
   * @param domNamespace  namespace where the domain exists
   * @param adminSecretName  name of admin secret
   * @param clusterName name of the WebLogic cluster to be created in the domain
   * @param pvName name of the persistent volume to create
   * @param pvcName name of the persistent volume claim to create
   * @param domainInHomePrefix prefix of path of domain in home
   * @param replicaCount count of replicas
   * @param t3ChannelPort port number of t3 channel
   * @return Domain WebLogic domain
   */
  public static DomainResource createDomainResourceOnPv(String domainUid,
                                                        String domNamespace,
                                                        String adminSecretName,
                                                        String clusterName,
                                                        String pvName,
                                                        String pvcName,
                                                        String domainInHomePrefix,
                                                        int replicaCount,
                                                        int t3ChannelPort) {

    // create a domain custom resource configuration object
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(domainInHomePrefix + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(FMWINFRA_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .imagePullSecrets(Arrays.asList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME)))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domNamespace + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod() //serverpod
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom"))
                .addVolumesItem(new V1Volume()
                    .name(pvName)
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name(pvName)))
            .adminServer(new AdminServer() //admin server
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort)))));

    return domain;
  }

  /**
   * Verify Pod is ready and service exists for both admin server and managed servers.
   * Verify EM console is accessible.
   * @param domainUid unique Uid of the domain
   * @param domainNamespace  namespace where the domain exists
   * @param replicaCount number of running managed servers
   * @param args arguments to determine append -c1 to managed server name or not.
   *             append -c1 if it's not set. Otherwise not appending -c1
   */
  public static void verifyDomainReady(String domainNamespace, String domainUid, int replicaCount, String... args) {
    LoggingFacade logger = getLogger();
    String adminServerPodName = domainUid + "-admin-server";
    String managedServerPrefix = domainUid + "-managed-server";

    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerName = (args.length == 0) ? managedServerPrefix + i + "-c1" : managedServerPrefix + i;
      logger.info("Checking managed server service {0} is created in namespace {1}",
          managedServerName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerName, domainUid, domainNamespace);
    }
  }

  /**
   * Verify EM console is accessible.
   * @param domainUid unique Uid of the domain
   * @param domainNamespace  namespace where the domain exists
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   */
  public static void verifyEMconsoleAccess(String domainNamespace, String domainUid, String adminSvcExtHost) {

    LoggingFacade logger = getLogger();
    String adminServerPodName = domainUid + "-admin-server";
    int nodePort = getServiceNodePort(
        domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    assertNotEquals(-1, nodePort,
        "Could not get the default external service node port");
    logger.info("Found the default service nodePort {0}", nodePort);

    if (adminSvcExtHost == null) {
      adminSvcExtHost = createRouteForOKD(getExternalServicePodName(adminServerPodName), domainNamespace);
    }
    logger.info("admin svc host = {0}", adminSvcExtHost);
    String hostAndPort = getHostAndPort(adminSvcExtHost, nodePort);
    String curlCmd1 = "curl -s -L --show-error --noproxy '*' "
        + " http://" + hostAndPort
        + "/em --write-out %{http_code} -o /dev/null";
    logger.info("Executing default nodeport curl command {0}", curlCmd1);
    assertTrue(callWebAppAndWaitTillReady(curlCmd1, 5), "Calling web app failed");
    logger.info("EM console is accessible thru default service");
  }

  /**
   * Construct a domain object with the given parameters that can be used to create a domain resource.
   * @param domainUid unique Uid of the domain
   * @param domainNamespace  namespace where the domain exists
   * @param adminSecretName  name of admin secret
   * @param repoSecretName name of repository secret
   * @param rcuAccessSecretName name of RCU access secret
   * @param opssWalletPasswordSecretName name of opss wallet password secret
   * @param opssWalletFileSecretName name of opss wallet file secret
   * @param domainCreationImages list of domainCreationImage
   * @param pvName name of persistent volume
   * @param pvcName name of persistent volume claim
   * @return Domain WebLogic domain
   */
  public static DomainResource createDomainResourceSimplifyJrfPv(
      String domainUid, String domainNamespace, String adminSecretName,
      String repoSecretName, String rcuAccessSecretName, String opssWalletPasswordSecretName,
      String opssWalletFileSecretName,
      String pvName, String pvcName,
      List<DomainCreationImage> domainCreationImages,
      String domainCreationConfigMap) {

    Map<String, Quantity> capacity = new HashMap<>();
    capacity.put("storage", Quantity.fromString("10Gi"));

    Map<String, Quantity> request = new HashMap<>();
    request.put("storage", Quantity.fromString("10Gi"));

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(FMWINFRA_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .introspectVersion("1")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod()
                .addVolumesItem(new V1Volume()
                    .name("weblogic-domain-storage-volume")
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name("weblogic-domain-storage-volume"))
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addEnvItem(new V1EnvVar()
                    .name("WLSDEPLOY_PROPERTIES")
                    .value(YAML_MAX_FILE_SIZE_PROPERTY)))
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .configuration(new Configuration()
                /* might turn on later to test
                .opss(new Opss()
                    .walletPasswordSecret(opssWalletPasswordSecretName))
                .model(new Model()
                    .domainType("JRF")
                    .runtimeEncryptionSecret(encryptionSecretName))*/
                .addSecretsItem(rcuAccessSecretName)
                .introspectorJobActiveDeadlineSeconds(3000L)
                .initializeDomainOnPV((new InitializeDomainOnPV()
                    .domain(new DomainOnPV()
                        .createMode(CreateIfNotExists.DOMAIN)
                        .domainType(DomainOnPVType.JRF)
                        .domainCreationImages(domainCreationImages)
                        .domainCreationConfigMap(domainCreationConfigMap)
                        .opss(new Opss()
                            .walletPasswordSecret(opssWalletPasswordSecretName)
                            .walletFileSecret(opssWalletFileSecretName))

                        )))));
    InitializeDomainOnPV initializeDomainOnPV = getInitializeDomainOnPV(pvName, pvcName, capacity, request, domain);
    domain.getSpec().getConfiguration().initializeDomainOnPV(initializeDomainOnPV);
    return domain;
  }

  /**
   * Construct a domain and RCU with the given parameters that can be used to create a domain resource.
   * @param domainUid unique Uid of the domain
   * @param domainNamespace  namespace where the domain exists
   * @param adminSecretName  name of admin secret
   * @param repoSecretName name of repository secret
   * @param rcuAccessSecretName name of RCU access secret
   * @param opssWalletPasswordSecretName name of opss wallet password secret
   * @param opssWalletFileSecretName name of opss wallet file secret
   * @param domainCreationImages list of domainCreationImage
   * @param pvName name of persistent volume
   * @param pvcName name of persistent volume claim
   * @return Domain WebLogic domain
   */
  public static DomainResource createSimplifyJrfPvDomainAndRCU(
      String domainUid, String domainNamespace, String adminSecretName,
      String repoSecretName, String rcuAccessSecretName, String opssWalletPasswordSecretName,
      String opssWalletFileSecretName,
      String pvName, String pvcName,
      List<DomainCreationImage> domainCreationImages,
      String domainCreationConfigMap) {

    Map<String, Quantity> capacity = new HashMap<>();
    capacity.put("storage", Quantity.fromString("10Gi"));

    Map<String, Quantity> request = new HashMap<>();
    request.put("storage", Quantity.fromString("10Gi"));

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(FMWINFRA_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(repoSecretName))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .introspectVersion("1")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod()
                .addVolumesItem(new V1Volume()
                    .name("weblogic-domain-storage-volume")
                    .persistentVolumeClaim(new V1PersistentVolumeClaimVolumeSource()
                        .claimName(pvcName)))
                .addVolumeMountsItem(new V1VolumeMount()
                    .mountPath("/shared")
                    .name("weblogic-domain-storage-volume"))
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .addEnvItem(new V1EnvVar()
                    .name("WLSDEPLOY_PROPERTIES")
                    .value(YAML_MAX_FILE_SIZE_PROPERTY)))
            .adminServer(new AdminServer()
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .configuration(new Configuration()
                .addSecretsItem(rcuAccessSecretName)
                .introspectorJobActiveDeadlineSeconds(3000L)
                .initializeDomainOnPV((new InitializeDomainOnPV()
                    .domain(new DomainOnPV()
                        .createMode(CreateIfNotExists.DOMAIN_AND_RCU)
                        .domainType(DomainOnPVType.JRF)
                        .domainCreationImages(domainCreationImages)
                        .domainCreationConfigMap(domainCreationConfigMap)
                        .opss(new Opss()
                            .walletPasswordSecret(opssWalletPasswordSecretName)
                            .walletFileSecret(opssWalletFileSecretName))

                        )))));

    InitializeDomainOnPV initializeDomainOnPV = getInitializeDomainOnPV(pvName, pvcName, capacity, request, domain);
    domain.getSpec().getConfiguration().initializeDomainOnPV(initializeDomainOnPV);
    return domain;
  }

  private static InitializeDomainOnPV getInitializeDomainOnPV(String pvName,
                                                              String pvcName,
                                                              Map<String, Quantity> capacity,
                                                              Map<String, Quantity> request,
                                                              DomainResource domain) {
    InitializeDomainOnPV initializeDomainOnPV = domain
        .getSpec()
        .getConfiguration()
        .getInitializeDomainOnPV();
    if (OKE_CLUSTER) {
      initializeDomainOnPV = initializeDomainOnPV.persistentVolumeClaim(new PersistentVolumeClaim()
          .metadata(new V1ObjectMeta()
              .name(pvcName))
          .spec(new PersistentVolumeClaimSpec()
              .storageClassName("oci-fss")
              .resources(new V1ResourceRequirements()
              .requests(request))));
    } else {
      initializeDomainOnPV = initializeDomainOnPV
          .persistentVolume(new PersistentVolume()
              .metadata(new V1ObjectMeta()
                  .name(pvName))
              .spec(new PersistentVolumeSpec()
                  .storageClassName("weblogic-domain-storage-class")
                  .hostPath(new V1HostPathVolumeSource()
                      .path("/shared"))
                  .capacity(capacity)))
          .persistentVolumeClaim(new PersistentVolumeClaim()
              .metadata(new V1ObjectMeta()
                  .name(pvcName))
              .spec(new PersistentVolumeClaimSpec()
                  .volumeName(pvName)
                  .storageClassName("weblogic-domain-storage-class")
                  .resources(new V1ResourceRequirements()
                  .requests(request))));
    }
    return initializeDomainOnPV;
  }

  /**
   * Save and restore the OPSS key wallet from a running JRF domain's introspector configmap to a file.
   * @param namespace namespace where JRF domain exists
   * @param domainUid unique domain Uid
   * @param walletfileSecretName name of wallet file secret
   */
  public static void saveAndRestoreOpssWalletfileSecret(String namespace, String domainUid,
       String walletfileSecretName) {

    logger = getLogger();
    Path saveAndRestoreOpssPath =
         Paths.get(RESOURCE_DIR, "bash-scripts", "opss-wallet.sh");
    String script = saveAndRestoreOpssPath.toString();
    logger.info("Script for saveAndRestoreOpss is {0)", script);

    //save opss wallet file
    String command1 = script + " -d " + domainUid + " -n " + namespace + " -s";
    logger.info("Save wallet file command: {0}", command1);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command1)
            .saveResults(true)
            .redirect(true))
        .execute());

    //restore opss wallet password secret
    String command2 = script + " -d " + domainUid + " -n " + namespace + " -r" + " -ws " + walletfileSecretName;
    logger.info("Restore wallet file command: {0}", command2);
    assertTrue(() -> Command.withParams(
          defaultCommandParams()
            .command(command2)
            .saveResults(true)
            .redirect(true))
        .execute());

  }

  /**
   * Restore the OPSS key wallet from a running JRF domain's introspector configmap to a file.
   * @param namespace namespace where JRF domain exists
   * @param domainUid unique domain Uid
   * @param walletfileSecretName name of wallet file secret
   * @return ExecResult result of running corresponding script
   */
  public static ExecResult restoreOpssWalletfileSecret(String namespace, String domainUid,
       String walletfileSecretName) {

    logger = getLogger();
    Path saveAndRestoreOpssPath =
         Paths.get(RESOURCE_DIR, "bash-scripts", "opss-wallet.sh");
    String script = saveAndRestoreOpssPath.toString();
    logger.info("Script for saveAndRestoreOpss is {0)", script);

    //restore opss wallet password secret
    String command = script + " -d " + domainUid + " -n " + namespace + " -r" + " -ws " + walletfileSecretName;
    logger.info("Restore wallet file command: {0}", command);
    ExecResult result = Command.withParams(
          defaultCommandParams()
            .command(command)
            .saveResults(true)
            .redirect(true))
        .executeAndReturnResult();

    return result;

  }

  /** Create configuration with provided pv and pvc values.
   *
   * @param pvName name of pv
   * @param pvcName name of pvc
   * @param pvCapacity pv capacity
   * @param pvcRequest pvc request
   * @param storageClassName storage name
   * @return configuration object with pv and pvc setup
   */
  @NotNull
  public static Configuration getConfiguration(String pvName, String pvcName,
                                         Map<String, Quantity> pvCapacity, Map<String, Quantity> pvcRequest,
                                         String storageClassName, String testClass) {
    Configuration configuration = new Configuration();
    PersistentVolume pv = null;
    if (OKE_CLUSTER) {
      storageClassName = "oci-fss";
    }

    pv = new PersistentVolume()
        .spec(new PersistentVolumeSpec()
            .capacity(pvCapacity)
            .storageClassName(storageClassName)
            .persistentVolumeReclaimPolicy("Retain"))
        .metadata(new V1ObjectMeta()
            .name(pvName));
    if (!OKE_CLUSTER) {
      pv.getSpec().hostPath(new V1HostPathVolumeSource()
          .path(getHostPath(pvName, testClass)));
    }
    configuration
        .initializeDomainOnPV(new InitializeDomainOnPV()
            .persistentVolume(pv)
            .persistentVolumeClaim(new PersistentVolumeClaim()
                .metadata(new V1ObjectMeta()
                    .name(pvcName))
                .spec(new PersistentVolumeClaimSpec()
                    .storageClassName(storageClassName)
                    .resources(new V1ResourceRequirements()
                        .requests(pvcRequest)))));

    return configuration;
  }

  /** Create configuration with pvc only provided values.
   *
   * @param pvcName name of pvc
   * @param pvcRequest pvc request
   * @param storageClassName storage name
   * @return configuration object with pv and pvc setup
   */
  @NotNull
  public static Configuration getConfiguration(String pvcName,
                                         Map<String, Quantity> pvcRequest,
                                         String storageClassName) {
    Configuration configuration = new Configuration()
        .initializeDomainOnPV(new InitializeDomainOnPV()
            .persistentVolumeClaim(new PersistentVolumeClaim()
                .metadata(new V1ObjectMeta()
                    .name(pvcName))
                .spec(new PersistentVolumeClaimSpec()
                    .storageClassName(storageClassName)
                    .resources(new V1ResourceRequirements()
                        .requests(pvcRequest)))));

    return configuration;
  }

  // get the host path for multiple environment
  private static String getHostPath(String pvName, String className) {
    Path hostPVPath = createPVHostPathDir(pvName, className);
    return hostPVPath.toString();
  }
}
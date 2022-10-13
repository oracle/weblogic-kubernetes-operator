// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Arrays;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.Opss;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.YAML_MAX_FILE_SIZE_PROPERTY;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Common utility methods for FMW Domain.
 */
public class FmwUtils {

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
                .introspectorJobActiveDeadlineSeconds(600L)));

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

}

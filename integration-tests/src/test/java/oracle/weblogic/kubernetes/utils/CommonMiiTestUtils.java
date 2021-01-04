// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import oracle.weblogic.domain.Domain;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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
   * Create a domain object for a Kubernetes domain custom resource using the basic model-in-image image.
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
}

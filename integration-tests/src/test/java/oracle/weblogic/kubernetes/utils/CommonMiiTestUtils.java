// Copyright (c) 2020, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.V1Patch;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
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
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.AuxiliaryImage;
import oracle.weblogic.domain.AuxiliaryImageVolume;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterList;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.OnlineUpdate;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.domain.ServerService;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.Cluster;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_PATCH;
import static oracle.weblogic.kubernetes.TestConstants.CRIO;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_DEPLOYMENT_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTPS_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createSecret;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodCreationTimestamp;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.actions.TestActions.getServicePort;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.patchDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes.listConfigMaps;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.podIntrospectVersionUpdated;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.secretExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.verifyRollingRestartOccurred;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyCredentials;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.IstioUtils.createIstioDomainResource;
import static oracle.weblogic.kubernetes.utils.JobUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.PatchDomainUtils.patchDomainWithNewSecretAndVerify;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getIntrospectorPodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.getPodCreationTime;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
   * @return DomainResource
   */
  public static DomainResource createMiiDomainAndVerify(
      String domainNamespace,
      String domainUid,
      String imageName,
      String adminServerPodName,
      String managedServerPrefix,
      int replicaCount
  ) {
    return createMiiDomainAndVerify(domainNamespace, domainUid, imageName, 
        adminServerPodName, managedServerPrefix, replicaCount, Arrays.asList("cluster-1"), false, null);
  }

  /**
   * Create a basic Kubernetes domain resource and wait until the domain is fully up.
   *
   * @param domainNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param imageName name of the image including its tag
   * @param adminServerPodName name of the admin server pod
   * @param managedServerPrefix prefix of the managed server pods
   * @param replicaCount number of managed servers to start
   * @param clusterNames names of clusters
   * @return DomainResource
   */
  public static DomainResource createMiiDomainAndVerify(
      String domainNamespace,
      String domainUid,
      String imageName,
      String adminServerPodName,
      String managedServerPrefix,
      int replicaCount,
      List<String> clusterNames) {
    return createMiiDomainAndVerify(domainNamespace, domainUid, imageName,
        adminServerPodName, managedServerPrefix, replicaCount, clusterNames, false, null);
  }

  /**
   * Create a basic Kubernetes domain resource and wait until the domain is fully up.
   *
   * @param domainNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param imageName name of the image including its tag
   * @param adminServerPodName name of the admin server pod
   * @param managedServerPrefix prefix of the managed server pods
   * @param replicaCount number of managed servers to start
   * @param clusterNames names of clusters
   * @param setDataHome whether to set dataHome in the domain spec
   * @param dataHome dataHome override in the domain spec
   * @return DomainResource
   */
  public static DomainResource createMiiDomainAndVerify(
      String domainNamespace,
      String domainUid,
      String imageName,
      String adminServerPodName,
      String managedServerPrefix,
      int replicaCount,
      List<String> clusterNames,
      boolean setDataHome,
      String dataHome) {
    return createMiiDomainAndVerify(domainNamespace, domainUid, imageName, adminServerPodName, managedServerPrefix,
        replicaCount, clusterNames, setDataHome, dataHome, false);
  }

  /**
   * Create a basic Kubernetes domain resource and wait until the domain is fully up.
   *
   * @param domainNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param imageName name of the image including its tag
   * @param adminServerPodName name of the admin server pod
   * @param managedServerPrefix prefix of the managed server pods
   * @param replicaCount number of managed servers to start
   * @param clusterNames names of clusters
   * @param setDataHome whether to set dataHome in the domain spec
   * @param dataHome dataHome override in the domain spec
   * @param prefixDomainName whether prefix the domain name in front of cluster name
   * @return DomainResource
   */
  public static DomainResource createMiiDomainAndVerify(
      String domainNamespace,
      String domainUid,
      String imageName,
      String adminServerPodName,
      String managedServerPrefix,
      int replicaCount, 
      List<String> clusterNames,
      boolean setDataHome,
      String dataHome,
      boolean prefixDomainName) {

    LoggingFacade logger = getLogger();
    DomainResource domain =
        createMiiDomain(domainNamespace, domainUid, imageName, replicaCount, clusterNames, setDataHome, dataHome,
            prefixDomainName);

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

    return domain;
  }

  /**
   * Create a basic Kubernetes domain resource and verify the domain is created.
   *
   * @param domainNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param imageName name of the image including its tag
   * @param replicaCount number of managed servers to start
   * @param clusterNames names of clusters
   * @param setDataHome whether to set dataHome in the domain spec
   * @param dataHome dataHome override in the domain spec
   * @return DomainResource
   */
  public static DomainResource createMiiDomain(
      String domainNamespace,
      String domainUid,
      String imageName,
      int replicaCount,
      List<String> clusterNames,
      boolean setDataHome,
      String dataHome) {
    return createMiiDomain(domainNamespace, domainUid, imageName, replicaCount, clusterNames,
        setDataHome, dataHome, false);
  }

  /**
   * Create a basic Kubernetes domain resource and verify the domain is created.
   *
   * @param domainNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param imageName name of the image including its tag
   * @param replicaCount number of managed servers to start
   * @param clusterNames names of clusters
   * @param setDataHome whether to set dataHome in the domain spec
   * @param dataHome dataHome override in the domain spec
   * @param prefixDomainName whether prefix domain name in front of cluster name
   * @return DomainResource
   */
  public static DomainResource createMiiDomain(
      String domainNamespace,
      String domainUid,
      String imageName,
      int replicaCount,
      List<String> clusterNames,
      boolean setDataHome,
      String dataHome,
      boolean prefixDomainName) {

    LoggingFacade logger = getLogger();
    // this secret is used only for non-kind cluster
    logger.info("Create the repo secret {0} to pull the image", TEST_IMAGES_REPO_SECRET_NAME);
    assertDoesNotThrow(() -> createTestRepoSecret(domainNamespace),
        String.format("createSecret failed for %s", TEST_IMAGES_REPO_SECRET_NAME));

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
    DomainResource domain = createDomainResource(domainUid,
        domainNamespace,
        imageName,
        adminSecretName,
        new String[]{TEST_IMAGES_REPO_SECRET_NAME},
        encryptionSecretName,
        replicaCount,
        clusterNames,
        prefixDomainName
    );

    // set the dataHome in the domain spec
    if (setDataHome) {
      DomainSpec domainSpec = domain.getSpec();
      domainSpec.dataHome(dataHome);
      domain.spec(domainSpec);
    }

    createDomainAndVerify(domain, domainNamespace);

    return domain;
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
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResource(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName) {

    return createDomainResource(domainResourceName, domNamespace, imageName,
        adminSecretName, repoSecretName, encryptionSecretName, -1, Collections.<String>emptyList());
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
   * @param replicaCount replica count of the cluster
   * @param clusterNames names of cluster resources to create
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResource(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      List<String> clusterNames) {
    return createDomainResource(domainResourceName, domNamespace, imageName, adminSecretName,
        repoSecretName, encryptionSecretName, replicaCount, clusterNames, false);
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
   * @param replicaCount replica count of the cluster
   * @param clusterNames names of cluster resources to create
   * @param prefixDomainName prefix the domainUID to cluster resource name
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResource(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      List<String> clusterNames,
      boolean prefixDomainName) {
    return createDomainResource(domainResourceName, domNamespace, imageName, adminSecretName, repoSecretName,
        encryptionSecretName, replicaCount, clusterNames, prefixDomainName, 0);
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
   * @param replicaCount replica count of the cluster
   * @param clusterNames names of cluster resources to create
   * @param prefixDomainName prefix the domainUID to cluster resource name
   * @param nodePort Node port
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResource(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      int replicaCount,
      List<String> clusterNames,
      boolean prefixDomainName,
      int nodePort) {

    // create secrets
    List<V1LocalObjectReference> secrets = new ArrayList<>();
    for (String secret : repoSecretName) {
      secrets.add(new V1LocalObjectReference().name(secret));
    }

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new io.kubernetes.client.openapi.models.V1ObjectMeta()
            .name(domainResourceName)
            .namespace(domNamespace))
        .spec(new oracle.weblogic.domain.DomainSpec()
            .domainUid(domainResourceName)
            .domainHomeSourceType("FromModel")
            .image(imageName)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new oracle.weblogic.domain.ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new oracle.weblogic.domain.AdminServer()
                .adminService(new oracle.weblogic.domain.AdminService()
                    .addChannelsItem(new oracle.weblogic.domain.Channel()
                        .channelName("default")
                        .nodePort(nodePort))))
            .configuration(new oracle.weblogic.domain.Configuration()
                .model(new oracle.weblogic.domain.Model()
                    .domainType("WLS")
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(3000L)));

    domain.spec().setImagePullSecrets(secrets);

    ClusterList clusters = Cluster.listClusterCustomResources(domNamespace);

    if (clusterNames != null) {
      for (String clusterName : clusterNames) {
        String clusterResName = prefixDomainName ? domainResourceName + "-" + clusterName : clusterName;
        if (clusters.getItems().stream().anyMatch(cluster -> cluster.getClusterName().equals(clusterResName))) {
          getLogger().info("!!!Cluster {0} in namespace {1} already exists, skipping...", clusterResName, domNamespace);
        } else {
          getLogger().info("Creating cluster {0} in namespace {1}", clusterResName, domNamespace);
          ClusterSpec spec =
              new ClusterSpec().withClusterName(clusterName).replicas(replicaCount).serverStartPolicy("IfNeeded");
          createClusterAndVerify(createClusterResource(clusterResName, domNamespace, spec));
        }
        // set cluster references
        domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));
      }
    }

    setPodAntiAffinity(domain);
    return domain;
  }

  /**
   * Create a domain object for a Kubernetes domain custom resource using the basic WLS image and MII auxiliary image
   * image.
   *
   * @param domainResourceName name of the domain resource
   * @param domNamespace Kubernetes namespace that the domain is hosted
   * @param baseImageName name of the base image to use
   * @param adminSecretName name of the new WebLogic admin credentials secret
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param auxiliaryImagePath auxiliary image path, parent location for Model in Image model and WDT installation files
   * @param auxiliaryImageVolumeName auxiliary image volume name
   * @param auxiliaryImageName image names including tags, image contains the domain model, application archive if any
   *                   and WDT installation files
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResource(
      String domainResourceName,
      String domNamespace,
      String baseImageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      String auxiliaryImagePath,
      String auxiliaryImageVolumeName,
      String... auxiliaryImageName) {

    return createDomainResourceWithAuxiliaryImageAndVolume(domainResourceName,
        domNamespace, baseImageName, adminSecretName, repoSecretName, encryptionSecretName,
        auxiliaryImagePath, auxiliaryImageVolumeName, auxiliaryImageName);
  }

  /**
   * Create a domain object for a Kubernetes domain custom resource using the basic WLS image and MII auxiliary image
   * image.
   *
   * @param domainResourceName name of the domain resource
   * @param domNamespace Kubernetes namespace that the domain is hosted
   * @param baseImageName name of the base image to use
   * @param adminSecretName name of the new WebLogic admin credentials secret
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param auxiliaryImagePath auxiliary image path, parent location for Model in Image model and WDT installation files
   * @param auxiliaryImageVolumeName auxiliary image volume name
   * @param auxiliaryImageName image names including tags, image contains the domain model, application archive if any
   *                   and WDT installation files
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResourceWithAuxiliaryImageAndVolume(
      String domainResourceName,
      String domNamespace,
      String baseImageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      String auxiliaryImagePath,
      String auxiliaryImageVolumeName,
      String... auxiliaryImageName) {

    DomainResource domainCR = CommonMiiTestUtils.createDomainResource(domainResourceName, domNamespace,
        baseImageName, adminSecretName, repoSecretName,
        encryptionSecretName);
    domainCR.spec().addAuxiliaryImageVolumesItem(new AuxiliaryImageVolume()
        .mountPath(auxiliaryImagePath)
        .name(auxiliaryImageVolumeName));
    domainCR.spec().configuration().model()
        .withModelHome(auxiliaryImagePath + "/models")
        .withWdtInstallHome(auxiliaryImagePath + "/weblogic-deploy");

    return domainCR;
  }

  /**
   * Create a domain object for a Kubernetes domain custom resource using the basic WLS image and MII auxiliary image
   * image.
   *
   * @param domainResourceName name of the domain resource
   * @param domNamespace Kubernetes namespace that the domain is hosted
   * @param baseImageName name of the base image to use
   * @param adminSecretName name of the new WebLogic admin credentials secret
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param auxiliaryImagePath auxiliary image path, parent location for Model in Image model and WDT installation files
   * @param auxiliaryImageName image names including tags, image contains the domain model, application archive if any
   *                   and WDT installation files
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResourceWithAuxiliaryImage(
      String domainResourceName,
      String domNamespace,
      String baseImageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName,
      String auxiliaryImagePath,
      String... auxiliaryImageName) {

    DomainResource domainCR = CommonMiiTestUtils.createDomainResource(domainResourceName, domNamespace,
        baseImageName, adminSecretName, repoSecretName,
        encryptionSecretName);
    int index = 0;
    for (String cmImageName: auxiliaryImageName) {
      AuxiliaryImage auxImage = new AuxiliaryImage()
          .image(cmImageName).imagePullPolicy(IMAGE_PULL_POLICY);
      //Only add the sourceWDTInstallHome and sourceModelHome for the first aux image.
      if (index == 0) {
        auxImage.sourceWDTInstallHome(auxiliaryImagePath + "/weblogic-deploy")
            .sourceModelHome(auxiliaryImagePath + "/models");
      }
      domainCR.spec().configuration().model().withAuxiliaryImage(auxImage);
      index++;
    }
    return domainCR;
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
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResourceWithAuxiliaryImage(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String[] repoSecretName,
      String encryptionSecretName) {

    // create secrets
    List<V1LocalObjectReference> secrets = new ArrayList<>();
    for (String secret : repoSecretName) {
      secrets.add(new V1LocalObjectReference().name(secret));
    }

    // create the domain CR
    DomainResource domain = new DomainResource()
            .apiVersion(DOMAIN_API_VERSION)
            .kind("Domain")
            .metadata(new io.kubernetes.client.openapi.models.V1ObjectMeta()
                    .name(domainResourceName)
                    .namespace(domNamespace))
            .spec(new oracle.weblogic.domain.DomainSpec()
                    .domainUid(domainResourceName)
                    .domainHomeSourceType("FromModel")
                    .image(imageName)
                    .imagePullPolicy(IMAGE_PULL_POLICY)
                    .webLogicCredentialsSecret(new V1LocalObjectReference()
                            .name(adminSecretName))
                    .includeServerOutInPodLog(true)
                    .serverStartPolicy("IfNeeded")
                    .serverPod(new oracle.weblogic.domain.ServerPod()
                            .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                                    .name("JAVA_OPTIONS")
                                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                            .addEnvItem(new io.kubernetes.client.openapi.models.V1EnvVar()
                                    .name("USER_MEM_ARGS")
                                    .value("-Djava.security.egd=file:/dev/./urandom ")))
                    .adminServer(new oracle.weblogic.domain.AdminServer()
                            .adminService(new oracle.weblogic.domain.AdminService()
                                    .addChannelsItem(new oracle.weblogic.domain.Channel()
                                            .channelName("default")
                                            .nodePort(0))))
                    .configuration(new oracle.weblogic.domain.Configuration()
                            .model(new oracle.weblogic.domain.Model()
                                    .domainType("WLS")
                                    .runtimeEncryptionSecret(encryptionSecretName))
                            .introspectorJobActiveDeadlineSeconds(3000L)));
    domain.spec().setImagePullSecrets(secrets);
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
   * @param pvName Name of persistent volume
   * @param pvcName Name of persistent volume claim
   * @param configMapName name of the configMap containing Weblogic Deploy Tooling model
   * @param dbSecretName name of the Secret for WebLogic configuration overrides
   * @param onlineUpdateEnabled whether to enable onlineUpdate feature for mii dynamic update
   * @param setDataHome whether to set data home at domain resource
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResourceWithLogHome(
          String domainResourceName,
          String domNamespace,
          String imageName,
          String adminSecretName,
          String repoSecretName,
          String encryptionSecretName,
          String pvName,
          String pvcName,
          String configMapName,
          String dbSecretName,
          boolean onlineUpdateEnabled,
          boolean setDataHome) {
    return createDomainResourceWithLogHome(domainResourceName,
        domNamespace,
        imageName,
        adminSecretName,
        repoSecretName,
        encryptionSecretName,
        pvName,
        pvcName,
        configMapName,
        dbSecretName,
        "-Dweblogic.security.SSL.ignoreHostnameVerification=true -Dweblogic.security.remoteAnonymousRMIT3Enabled=true",
    onlineUpdateEnabled,
    setDataHome);
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
   * @param pvName Name of persistent volume
   * @param pvcName Name of persistent volume claim
   * @param configMapName name of the configMap containing Weblogic Deploy Tooling model
   * @param dbSecretName name of the Secret for WebLogic configuration overrides
   * @param javaOpt sting of all java options to be set
   * @param onlineUpdateEnabled whether to enable onlineUpdate feature for mii dynamic update
   * @param setDataHome whether to set data home at domain resource
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResourceWithLogHome(
      String domainResourceName,
      String domNamespace,
      String imageName,
      String adminSecretName,
      String repoSecretName,
      String encryptionSecretName,
      String pvName,
      String pvcName,
      String configMapName,
      String dbSecretName,
      String javaOpt,
      boolean onlineUpdateEnabled,
      boolean setDataHome) {

    List<String> securityList = new ArrayList<>();
    if (dbSecretName != null) {
      securityList.add(dbSecretName);
    }

    String uniquePath = "/shared/" + domNamespace + "/" + domainResourceName;
    DomainSpec domainSpec = new DomainSpec()
        .domainUid(domainResourceName)
        .domainHomeSourceType("FromModel")
        .image(imageName)
        .imagePullPolicy(IMAGE_PULL_POLICY)
        .addImagePullSecretsItem(new V1LocalObjectReference()
            .name(repoSecretName))
        .webLogicCredentialsSecret(new V1LocalObjectReference()
            .name(adminSecretName))
        .includeServerOutInPodLog(true)
        .logHomeEnabled(Boolean.TRUE)
        .logHome(uniquePath + "/logs")
        .serverStartPolicy("IfNeeded")
        .serverPod(new ServerPod()
            .addEnvItem(new V1EnvVar()
                .name("JAVA_OPTIONS")
                .value(javaOpt))
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
            .adminService(new AdminService()
                .addChannelsItem(new Channel()
                    .channelName("default")
                    .nodePort(0))))
        .configuration(new Configuration()
            .secrets(securityList)
            .model(new Model()
                .domainType("WLS")
                .configMap(configMapName)
                .runtimeEncryptionSecret(encryptionSecretName)
                .onlineUpdate(new OnlineUpdate()
                    .enabled(onlineUpdateEnabled)))
            .introspectorJobActiveDeadlineSeconds(3000L));

    if (setDataHome) {
      domainSpec.dataHome(uniquePath + "/data");
    }
    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainResourceName)
            .namespace(domNamespace))
        .spec(domainSpec);

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
   * @param replicaCount number of managed servers to start, set at domain level
   * @param pvName Name of persistent volume
   * @param pvcName Name of persistent volume claim
   * @param configMapName name of the configMap containing Weblogic Deploy Tooling model
   * @param dbSecretName name of the Secret for WebLogic configuration overrides
   * @param onlineUpdateEnabled whether to enable onlineUpdate feature for mii dynamic update
   * @param setDataHome whether to set data home at domain resource
   * @return domain object of the domain resource
   */
  public static DomainResource createDomainResourceWithLogHome(
          String domainResourceName,
          String domNamespace,
          String imageName,
          String adminSecretName,
          String repoSecretName,
          String encryptionSecretName,
          int replicaCount,
          String pvName,
          String pvcName,
          String configMapName,
          String dbSecretName,
          boolean onlineUpdateEnabled,
          boolean setDataHome) {
    DomainResource domain = createDomainResourceWithLogHome(domainResourceName, domNamespace, imageName,
        adminSecretName, repoSecretName, encryptionSecretName, pvName, pvcName, configMapName,
        dbSecretName, onlineUpdateEnabled, setDataHome);
    DomainSpec spec = domain.getSpec().replicas(replicaCount);
    return domain.spec(spec);
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
    testUntil(
        retryPolicy,
        () -> listConfigMaps(domainNamespace).getItems().stream()
          .noneMatch((cm) -> (cm.getMetadata().getName().equals(configMapName))),
        logger,
        "configmap {0} to be deleted",
        configMapName);

    createConfigMapAndVerify(configMapName, domainResourceName, domainNamespace, modelFiles);
  }

  /**
   * Use REST APIs to return the JdbcRuntime mbean from the WebLogic server.
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcesName Name of the JDBC system resource for which that mbean data to be queried
   * @return An ExecResult containing the output of the REST API exec request
   */
  public static String readJdbcRuntime(
      String adminSvcExtHost,
      String domainNamespace, String adminServerPodName, String resourcesName) {
    return readRuntimeResource(
        adminSvcExtHost,
        domainNamespace,
        adminServerPodName,
        "/management/wls/latest/datasources/id/" + resourcesName,
        "checkJdbcRuntime");
  }

  /**
   * Use REST APIs to return the MinThreadsConstraint runtime mbean associated with
   * the specified work manager from the WebLogic server.
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param serverName Name of the server from which to look for the runtime mbean
   * @param workManagerName Name of the work manager for which its associated
   *                       min threads constraint runtime mbean data to be queried
   * @return An ExecResult containing the output of the REST API exec request
   */
  public static String readMinThreadsConstraintRuntimeForWorkManager(
      String adminSvcExtHost, String domainNamespace, String adminServerPodName,
      String serverName, String workManagerName) {
    return readRuntimeResource(
        adminSvcExtHost,
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
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param serverName Name of the server from which to look for the runtime mbean
   * @param workManagerName Name of the work manager for which its associated
   *                       max threads constraint runtime mbean data to be queried
   * @return An ExecResult containing the output of the REST API exec request
   */
  public static String readMaxThreadsConstraintRuntimeForWorkManager(
      String adminSvcExtHost, String domainNamespace, String adminServerPodName,
      String serverName, String workManagerName) {
    return readRuntimeResource(
        adminSvcExtHost,
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
    return checkWorkManagerRuntime(null, domainNamespace, adminServerPodName, serverName,
                                   workManagerName, expectedStatusCode);
  }

  /**
   * Use REST APIs to check the WorkManager runtime mbean from the WebLogic server.
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param serverName Name of the server from which to look for the runtime mbean
   * @param workManagerName Name of the work manager for which its runtime mbean is to be verified
   * @param expectedStatusCode the expected response to verify
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkWorkManagerRuntime(
      String adminSvcExtHost, String domainNamespace, String adminServerPodName,
      String serverName, String workManagerName, String expectedStatusCode) {
    return checkWeblogicMBean(
        adminSvcExtHost,
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
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param serverName Name of the server from which to look for the runtime mbean
   * @param expectedStatusCode the expected response to verify
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkApplicationRuntime(
      String adminSvcExtHost, String domainNamespace, String adminServerPodName,
      String serverName, String expectedStatusCode) {
    return checkWeblogicMBean(
        adminSvcExtHost,
        domainNamespace,
        adminServerPodName,
        "/management/weblogic/latest/domainRuntime/serverRuntimes/"
            + serverName
            + "/applicationRuntimes/" + MII_BASIC_APP_DEPLOYMENT_NAME,
        expectedStatusCode);
  }

  /**
   * Use REST APIs to check the application runtime mbean from the WebLogic server.
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcePath resource path
   * @param callerName caller name
   * @return  the expected response
   */
  public static String readRuntimeResource(String adminSvcExtHost, String domainNamespace,
      String adminServerPodName, String resourcePath, String callerName) {
    LoggingFacade logger = getLogger();
    String returnString = "";
    String curlString = null;
    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      int port = getServicePort(domainNamespace, adminServerPodName, "internal-t3");
      String domainName = adminServerPodName.split("-" + ADMIN_SERVER_NAME_BASE)[0];
      String serviceName = ADMIN_SERVER_NAME_BASE;
      String ingressName = domainNamespace + "-" + domainName + "-" + serviceName + "-" + port;
      String hostHeader = domainNamespace + "." + domainName + "." + serviceName;
      Optional<String> ingressFound;
      try {
        List<String> ingresses = TestActions.listIngresses(domainNamespace);
        ingressFound = ingresses.stream().filter(ingress -> ingress.equals(ingressName)).findAny();
        if (ingressFound.isEmpty()) {
          createIngressHostRouting(domainNamespace, domainName, serviceName, port);
        }
      } catch (Exception ex) {
        logger.severe(ex.getMessage());
      }
      String hostAndPort = "localhost:" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
      Map<String, String> headers = new HashMap<>();
      headers.put("host", hostHeader);
      headers.put("Authorization", ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT);
      String url = "http://" + hostAndPort + resourcePath;
      HttpResponse<String> response;
      try {
        response = OracleHttpClient.get(url, headers, true);
        returnString = response.body();
        assertEquals(200, response.statusCode());
      } catch (Exception ex) {
        ex.printStackTrace();
      } catch (AssertionError e) {
        e.printStackTrace();
      }
    } else {
      if (OKE_CLUSTER_PRIVATEIP || OCNE) {
        String protocol = "http";
        String port = "7001";

        curlString = String.format(
          KUBERNETES_CLI + " exec -n " + domainNamespace + "  " + adminServerPodName + " -- curl -g -k %s://"
              + ADMIN_USERNAME_DEFAULT
              + ":"
              + ADMIN_PASSWORD_DEFAULT
              + "@" + adminServerPodName + ":%s/%s", protocol, port, resourcePath);
        curlString = curlString + " --silent --show-error ";
      } else {
        int adminServiceNodePort
            = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
        String host = formatIPv6Host(K8S_NODEPORT_HOST);
        String hostAndPort = (OKD) ? adminSvcExtHost : host + ":" + adminServiceNodePort;
        logger.info("hostAndPort = {0} ", hostAndPort);

        curlString = String.format("curl -g --user "
            + ADMIN_USERNAME_DEFAULT
            + ":"
            + ADMIN_PASSWORD_DEFAULT
            + " http://%s%s/ --silent --show-error ", hostAndPort, resourcePath);

        logger.info(callerName + ": curl command {0}", curlString);
      }
      try {
        String result = exec(curlString, true).stdout();
        logger.info(callerName + ": exec curl command {0} got: {1}", curlString, result);
        returnString = result;
      } catch (Exception ex) {
        logger.info(callerName + ": caught unexpected exception {0}", ex);
      }
    }
    return returnString;
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
    return checkWeblogicMBean(null, domainNamespace, adminServerPodName, resourcePath, expectedStatusCode, false, "");
  }

  /**
   * Use REST APIs to check a runtime mbean from the WebLogic server.
   *
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcePath Path of the system resource to be used in the REST API call
   * @param expectedStatusCode the expected response to verify
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkWeblogicMBean(String adminSvcExtHost, String domainNamespace,
         String adminServerPodName,  String resourcePath, String expectedStatusCode) {
    return checkWeblogicMBean(adminSvcExtHost, domainNamespace, adminServerPodName,
                              resourcePath, expectedStatusCode, false, "");
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
    return checkWeblogicMBean(null,  domainNamespace, adminServerPodName, resourcePath,
                              expectedStatusCode, isSecureMode, sslChannelName);
  }

  /**
   * Use REST APIs to check a runtime mbean from the WebLogic server.
   *
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcePath Path of the system resource to be used in the REST API call
   * @param expectedStatusCode the expected response to verify
   * @param isSecureMode whether use SSL
   * @param sslChannelName the channel name for SSL
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkWeblogicMBean(String adminSvcExtHost,
                                           String domainNamespace,
                                           String adminServerPodName,
                                           String resourcePath,
                                           String expectedStatusCode,
                                           boolean isSecureMode,
                                           String sslChannelName) {
    LoggingFacade logger = getLogger();
    if (OKE_CLUSTER_PRIVATEIP || OCNE || CRIO) {
      return checkWeblogicMBeanInAdminPod(domainNamespace,
          adminServerPodName,
          resourcePath,
          expectedStatusCode,
          isSecureMode);
    }
    int adminServiceNodePort;
    if (isSecureMode) {
      adminServiceNodePort
          = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), sslChannelName);
    } else {
      adminServiceNodePort
          = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    }

    StringBuffer curlString;
    if (isSecureMode) {
      curlString = new StringBuffer("status=$(curl -g -k --user weblogic:welcome1 https://");
    } else {
      curlString = new StringBuffer("status=$(curl -g --user weblogic:welcome1 http://");
    }

    String host = formatIPv6Host(K8S_NODEPORT_HOST);
    String hostAndPort = (OKD) ? adminSvcExtHost : host + ":" + adminServiceNodePort;
    logger.info("hostAndPort = {0} ", hostAndPort);

    if (TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
      String channel = "internal-t3";
      int port = getServicePort(domainNamespace, adminServerPodName,
          sslChannelName.isEmpty() ? channel : sslChannelName);
      String domainName = adminServerPodName.split("-" + ADMIN_SERVER_NAME_BASE)[0];
      String serviceName = ADMIN_SERVER_NAME_BASE;
      String ingressName = domainNamespace + "-" + domainName + "-" + serviceName + "-" + port;
      String hostHeader = domainNamespace + "." + domainName + "." + serviceName;
      Optional<String> ingressFound;
      try {
        List<String> ingresses = TestActions.listIngresses(domainNamespace);
        ingressFound = ingresses.stream().filter(ingress -> ingress.equals(ingressName)).findAny();
        if (ingressFound.isEmpty() && sslChannelName.isEmpty()) {
          createIngressHostRouting(domainNamespace, domainName, serviceName, port);
        } else {
          logger.info("Ingress {0} found or secure channel , skipping ingress resource creation...", ingressFound);
        }
      } catch (ApiException ex) {
        logger.severe(ex.getMessage());
      }
      hostAndPort = assertDoesNotThrow(()
          -> formatIPv6Host(InetAddress.getLocalHost().getHostAddress()) + ":"
          + (isSecureMode ? TRAEFIK_INGRESS_HTTPS_HOSTPORT : TRAEFIK_INGRESS_HTTP_HOSTPORT));
      Map<String, String> headers = new HashMap<>();
      headers.put("host", hostHeader);
      headers.put("Authorization", ADMIN_USERNAME_DEFAULT + ":" + ADMIN_PASSWORD_DEFAULT);
      String url = (isSecureMode ? "https" : "http") + "://" + hostAndPort + resourcePath;
      HttpResponse<String> response;
      try {
        response = OracleHttpClient.get(url, headers, true);
        return Integer.parseInt(expectedStatusCode) == response.statusCode();
      } catch (IOException | InterruptedException | NumberFormatException ex) {
        return false;
      }
    } else {
      curlString.append(hostAndPort)
          .append(resourcePath)
          .append(" -g --silent --show-error ")
          .append(" -o /dev/null ")
          .append(" -w %{http_code});")
          .append("echo ${status}");
      logger.info("checkSystemResource: curl command {0}", new String(curlString));
      return Command
          .withParams(new CommandParams()
              .command(curlString.toString()))
          .executeAndVerify(expectedStatusCode);
    }
  }

  /**
   * Use REST APIs to check a runtime mbean from the WebLogic server inside admin pod.
   *
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcePath Path of the system resource to be used in the REST API call
   * @param expectedStatusCode the expected response to verify
   * @param isSecureMode whether use SSL
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkWeblogicMBeanInAdminPod(String domainNamespace,
                                                     String adminServerPodName,
                                                     String resourcePath,
                                                     String expectedStatusCode,
                                                     boolean isSecureMode) {
    String protocol = "http";
    String port = "7001";
    if (isSecureMode) {
      protocol = "https";
      port = "7002";
    }
    LoggingFacade logger = getLogger();
    String curlString = String.format(
        KUBERNETES_CLI + " exec -n " + domainNamespace + "  " + adminServerPodName + " -- curl -k %s://"
            + ADMIN_USERNAME_DEFAULT
            + ":"
            + ADMIN_PASSWORD_DEFAULT
            + "@" + adminServerPodName + ":%s/%s", protocol, port, resourcePath);
    curlString = curlString + " -g --silent --show-error -o /dev/null -w %{http_code}";
    logger.info("checkSystemResource: curl command {0}", curlString);
    return Command
        .withParams(new CommandParams()
            .command(curlString))
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
    return checkSystemResourceConfiguration(null, domainNamespace, adminServerPodName, resourcesType,
                                     resourcesName, expectedStatusCode);
  }

  /**
   * Use REST APIs to check the system resource runtime mbean from the WebLogic server.
   *
   * @param adminSvcExtHost Used only in OKD env - this is the route host created for AS external service
   * @param domainNamespace Kubernetes namespace that the domain is hosted
   * @param adminServerPodName Name of the admin server pod to which the REST requests should be sent to
   * @param resourcesType Type of the system resource to be checked
   * @param resourcesName Name of the system resource to be checked
   * @param expectedStatusCode the expected response to verify
   * @return true if the REST API reply contains the expected response
   */
  public static boolean checkSystemResourceConfiguration(String adminSvcExtHost, String domainNamespace,
      String adminServerPodName, String resourcesType,
      String resourcesName, String expectedStatusCode) {
    return checkWeblogicMBean(adminSvcExtHost, domainNamespace, adminServerPodName,
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

    if (!OKD) {
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
                      .addContainersItem(
                          createfixPVCOwnerContainer(pvName,
                              "/shared")) // mounted under /shared inside pod
                      .volumes(Arrays.asList(
                          new V1Volume()
                              .name(pvName)
                              .persistentVolumeClaim(
                                  new V1PersistentVolumeClaimVolumeSource()
                                      .claimName(pvcName))))
                      .imagePullSecrets(Arrays.asList(
                          new V1LocalObjectReference()
                              .name(TEST_IMAGES_REPO_SECRET_NAME)))))); // this secret is used only for non-kind cluster

      String jobName = createJobAndWaitUntilComplete(jobBody, namespace);

      // check job status and fail test if the job failed
      V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
          "Getting the job failed");
      if (job != null && job.getStatus() != null && job.getStatus().getConditions() != null) {
        V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
            v1JobCondition -> "Failed".equals(v1JobCondition.getType()))
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
  }

  /**
   * Create a job to change the permissions on the pv host path.
   *
   * @param pvName Name of the persistent volume
   * @param pvcName Name of the persistent volume claim
   * @param namespace Namespace containing the persistent volume claim and where the job should be created in
   * @param mountPath path
   * @param command to change permission
   */
  public static void createJobToChangePermissionsOnPvHostPath(String pvName, String pvcName,
                                                              String namespace, String mountPath, String command) {
    LoggingFacade logger = getLogger();

    if (!OKD) {
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
                      .addContainersItem(
                          createfixPVCOwnerContainer(pvName,
                              mountPath,
                              command))
                      .volumes(Arrays.asList(
                          new V1Volume()
                              .name(pvName)
                              .persistentVolumeClaim(
                                  new V1PersistentVolumeClaimVolumeSource()
                                      .claimName(pvcName))))
                      .imagePullSecrets(Arrays.asList(
                          new V1LocalObjectReference()
                              .name(TEST_IMAGES_REPO_SECRET_NAME)))))); // this secret is used only for non-kind cluster

      String jobName = createJobAndWaitUntilComplete(jobBody, namespace);

      // check job status and fail test if the job failed
      V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
          "Getting the job failed");
      if (job != null && job.getStatus() != null && job.getStatus().getConditions() != null) {
        V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
                v1JobCondition -> "Failed".equals(v1JobCondition.getType()))
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
        assertDoesNotThrow(() -> deleteJob(jobName, namespace));
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
        String.format("Could not get the server Pod %s in namespace %s",
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
    Map<String, String> secretMap = new HashMap<>();
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
    Map<String, String> secretMap = new HashMap<>();
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
    String introspectorPodName = assertDoesNotThrow(() -> getIntrospectorPodName(domainUid, domainNamespace));
    checkPodReady(introspectorPodName, domainUid, domainNamespace);
    logger.info("Introspector pod log START");
    String introspectorLog = assertDoesNotThrow(() -> getPodLog(introspectorPodName,
        domainNamespace, domainUid + "-introspector", null, null, true), "Could not get introspector pod log");
    logger.info(introspectorLog);
    logger.info("Introspector pod log END");
    checkPodDoesNotExist(introspectorPodName, domainUid, domainNamespace);
  }

  /**
   * Verify the pods of the domain are not rolled.
   * @param domainNamespace namespace where pods exists
   * @param podsCreationTimes map of pods name and pod creation times
   */
  public static void verifyPodsNotRolled(String domainNamespace, Map<String, OffsetDateTime> podsCreationTimes) {
    // wait for 2 minutes before checking the pods, make right decision logic
    // that runs every two minutes in the  Operator
    try {
      getLogger().info("Sleep 2 minutes for operator make right decision logic");
      Thread.sleep(120 * 1000);
    } catch (InterruptedException ie) {
      getLogger().info("InterruptedException while sleeping for 2 minutes");
    }
    for (Map.Entry<String, OffsetDateTime> entry : podsCreationTimes.entrySet()) {
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
      testUntil(
          () -> podIntrospectVersionUpdated(podName, domainNamespace, expectedIntrospectVersion),
          getLogger(),
          "Checking for updated introspectVersion for pod {0}",
          podName);
    }
  }

  /**
   * Change the WebLogic Admin credential of the domain.
   * Patch the domain CRD with a new credentials secret.
   * Update domainRestartVersion to trigger a rolling restart of server pods.
   * Make sure all the server pods are re-started in a rolling fashion.
   * Check the validity of new credentials by accessing WebLogic RESTful Service.
   * @param domainNamespace namespace where the domain is
   * @param domainUid domain uid for which WebLogic Admin credential is being changed
   * @param adminServerPodName pod name of admin server
   * @param managedServerPrefix prefix of the managed server
   * @param replicaCount replica count of the domain
   * @param args arguments to determine appending suffix to managed server pod name or not.
   *             Append suffix if it's set. Otherwise do not append.
   */
  public static void verifyUpdateWebLogicCredential(String domainNamespace, String domainUid,
       String adminServerPodName, String managedServerPrefix, int replicaCount, String... args) {
    verifyUpdateWebLogicCredential(7001, domainNamespace, domainUid, adminServerPodName,
                               managedServerPrefix, replicaCount, args);
  }

  /**
   * Change the WebLogic Admin credential of the domain.
   * Patch the domain CRD with a new credentials secret.
   * Update domainRestartVersion to trigger a rolling restart of server pods.
   * Make sure all the server pods are re-started in a rolling fashion.
   * Check the validity of new credentials by accessing WebLogic RESTful Service.
   * @param domainNamespace namespace where the domain is
   * @param domainUid domain uid for which WebLogic Admin credential is being changed
   * @param adminServerPodName pod name of admin server
   * @param managedServerPrefix prefix of the managed server
   * @param replicaCount replica count of the domain
   * @param args arguments to determine appending suffix to managed server pod name or not.
   *             Append suffix if it's set. Otherwise do not append.
   */
  public static void verifyUpdateWebLogicCredential(int adminListenPort, String domainNamespace, String domainUid,
       String adminServerPodName, String managedServerPrefix, int replicaCount, String... args) {
    final boolean VALID = true;
    final boolean INVALID = false;

    getLogger().info("verifyMiiUpdateWebLogicCredential for domainNamespace: {0}, domainUid: {1}, "
        + "adminServerPodName {2}, managedServerPrefix: {3}, replicaCount: {4}", domainNamespace, domainUid,
        adminServerPodName, managedServerPrefix, replicaCount);
    LinkedHashMap<String, OffsetDateTime> pods = new LinkedHashMap<>();
    // get the creation time of the admin server pod before patching
    OffsetDateTime adminPodCreationTime = getPodCreationTime(domainNamespace,adminServerPodName);
    pods.put(adminServerPodName, adminPodCreationTime);

    // get the creation time of the managed server pods before patching
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = (args.length == 0) ? managedServerPrefix + i : managedServerPrefix + i
          + args[0];
      getLogger().info("managedServer pod name is: " + managedServerPodName);
      pods.put(managedServerPodName, getPodCreationTime(domainNamespace, managedServerPodName));
    }

    getLogger().info("Check that before patching current credentials are valid and new credentials are not");
    verifyCredentials(adminListenPort, adminServerPodName, domainNamespace,
                      ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, VALID, args);
    verifyCredentials(adminListenPort, adminServerPodName, domainNamespace, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH,
        INVALID, args);

    // create a new secret for admin credentials
    getLogger().info("Create a new secret that contains new WebLogic admin credentials");
    String adminSecretName = "weblogic-credentials-new";
    assertDoesNotThrow(() -> createSecretWithUsernamePassword(
        adminSecretName,
        domainNamespace,
        ADMIN_USERNAME_PATCH,
        ADMIN_PASSWORD_PATCH),
        String.format("createSecret failed for %s", adminSecretName));

    // patch the domain resource with the new secret and verify that the domain resource is patched.
    getLogger().info("Patch domain {0} in namespace {1} with the secret {2}, and verify the result",
        domainUid, domainNamespace, adminSecretName);
    String restartVersion = patchDomainWithNewSecretAndVerify(
        domainUid,
        domainNamespace,
        adminSecretName);

    getLogger().info("Wait for domain {0} admin server pod {1} in namespace {2} to be restarted with "
        + "restartVersion {3}", domainUid, adminServerPodName, domainNamespace, restartVersion);

    assertTrue(verifyRollingRestartOccurred(pods, 1, domainNamespace),
        "Rolling restart failed");

    // check if the new credentials are valid and the old credentials are not valid any more
    getLogger().info("Check that after patching current credentials are not valid and new credentials are");
    verifyCredentials(adminListenPort, adminServerPodName, domainNamespace,
                      ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, INVALID, args);
    verifyCredentials(adminListenPort, adminServerPodName, domainNamespace, ADMIN_USERNAME_PATCH, ADMIN_PASSWORD_PATCH,
        VALID, args);

    getLogger().info("Domain {0} in namespace {1} is fully started after changing WebLogic credentials secret",
        domainUid, domainNamespace);
  }


  /**
   * Patch the domain CRD with a new auxiliary image to add new or replace existing
   * auxiliary images at cluster scope. Verify the server pods in cluster are rolling
   * restarted and back to ready state.
   * @param domainNamespace namespace where the domain is
   * @param managedServerPrefix prefix of the managed server
   * @param replicaCount replica count of the domain
   * @param clusterIndex index of cluster to add or replace the auxiliary image cluster config
   * @param auxiliaryImageVolumeName auxiliary image volume name
   * @param auxiliaryImageName image names containing the files to config cluster scope auxiliary image
   * @param auxiliaryImageIndex location to add or replace the auxiliary image cluster config
   * @param addOrReplace add or replace the auxiliary image cluster config
   */
  public static void patchDomainClusterWithAuxImageAndVerify(String domainUid,
                                                             String domainNamespace,
                                                             String managedServerPrefix,
                                                             int replicaCount,
                                                             int clusterIndex,
                                                             String auxiliaryImageVolumeName,
                                                             String auxiliaryImageName,
                                                             int auxiliaryImageIndex,
                                                             String addOrReplace) {

    LoggingFacade logger = getLogger();

    // create the map with server pods and their original creation timestamps
    Map<String, OffsetDateTime> podsWithTimeStamps = new LinkedHashMap<>();
    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPrefix + i;
      podsWithTimeStamps.put(managedServerPodName,
          assertDoesNotThrow(() -> getPodCreationTimestamp(domainNamespace, "", managedServerPodName),
          String.format("getPodCreationTimestamp failed with ApiException for pod %s in namespace %s",
          managedServerPodName, domainNamespace)));
    }
    String imagePullPolicy = "IfNotPresent";
    // create patch string
    StringBuffer patchStr = new StringBuffer("[")
        .append("{\"op\":  \"" + addOrReplace + "\",")
        .append(" \"path\": \"/spec/configuration/model")
        .append("/auxiliaryImages/")
        .append(auxiliaryImageIndex)
        .append("\", ")
        .append("\"value\":  {\"image\": \"")
        .append(auxiliaryImageName)
        .append("\", ")
        .append("\"imagePullPolicy\": \"" + imagePullPolicy + "\" ")
        .append("\"}}]");

    logger.info("Patch domain with auxiliary image patch string: " + patchStr);

    // patch the domain and verify
    V1Patch patch = new V1Patch((patchStr).toString());
    boolean aiPatched = assertDoesNotThrow(() ->
        patchDomainCustomResource(domainUid, domainNamespace, patch, "application/json-patch+json"),
        "patchDomainClusterWithAuxiliaryImageAndVerify failed ");
    assertTrue(aiPatched, "patchDomainClusterWithAuxiliaryImageAndVerify failed");

    DomainResource domain1 = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace),
        String.format("getDomainCustomResource failed with ApiException when tried to get domain %s in namespace %s",
        domainUid, domainNamespace));
    assertNotNull(domain1, "Got null domain resource after patching");
    /*
    assertNotNull(domain1.getSpec().getClusters().get(clusterIndex).getServerPod().getAuxiliaryImages(),
        domain1 + "/spec/serverPod/auxiliaryImages is null");

    //verify that the domain is patched with new image
    List<AuxiliaryImage> auxiliaryImageListAf =
        domain1.getSpec().getClusters().get(clusterIndex).getServerPod().getAuxiliaryImages();
    boolean doMainPatched = false;
    for (AuxiliaryImage auxImage : auxiliaryImageListAf) {
      if (auxImage.getImage().equals(auxiliaryImageName)) {
        logger.info("Domain patched and cluster config {0} found", auxImage);
        doMainPatched = true;
        break;
      }
    }
    assertTrue(doMainPatched, String.format("Image name %s should be patched", auxiliaryImageName));
    */

    // verify the server pods in cluster are rolling restarted and back to ready state
    logger.info("Verifying rolling restart occurred for domain {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(verifyRollingRestartOccurred(podsWithTimeStamps, 1, domainNamespace),
        String.format("Rolling restart failed for domain %s in namespace %s", domainUid, domainNamespace));
  }

  /**
   * Read a file in a given pod.
   * @param domainNamespace namespace where the domain is
   * @param serverPodName WLS server pod name
   * @param fileName file to read from
   * @return ExecResult containing the content of the given file
   */
  public static ExecResult readFilesInPod(String domainNamespace,
                                          String serverPodName,
                                          String fileName) {
    LoggingFacade logger = getLogger();
    StringBuffer readFileCmd = new StringBuffer(KUBERNETES_CLI + " exec -n ")
        .append(domainNamespace)
        .append(" ")
        .append(serverPodName)
        .append(" -- cat \"")
        .append(fileName)
        .append("\"");
    logger.info("command to read file in pod {0} is: {1}", serverPodName, readFileCmd.toString());

    ExecResult result = assertDoesNotThrow(() -> exec(readFileCmd.toString(), true));

    return result;
  }

  /**
   * Create mii image and push it to the registry.
   *
   * @param miiImageNameBase the base mii image name used in local or to construct the image name in repository
   * @param wdtModelFile  wdt model file used to build the image
   * @param appName application source directory used to build sample app ear files
   * @param wdtModelPropFile wdt model properties file used to build the image
   * @return mii image created
   */
  public static String createAndPushMiiImage(String miiImageNameBase,
                                             String wdtModelFile,
                                             String appName,
                                             String wdtModelPropFile) {
    // create image with model files
    LoggingFacade logger = getLogger();
    logger.info("Creating image with model file {0} and verify", wdtModelFile);
    List<String> appSrcDirList = Collections.singletonList(appName);
    List<String> wdtModelList = Collections.singletonList(MODEL_DIR + "/" + wdtModelFile);
    List<String> modelPropList = Collections.singletonList(MODEL_DIR + "/" + wdtModelPropFile);

    String miiImage =
        createImageAndVerify(miiImageNameBase, wdtModelList, appSrcDirList, modelPropList, WEBLOGIC_IMAGE_NAME,
            WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, true, null, false);

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(miiImage);

    return miiImage;
  }

  /**
   * Create model in image istio enabled domain with multiple clusters.
   *
   * @param domainUid the uid of the domain
   * @param domainNamespace namespace in which the domain will be created
   * @param miiImage model in image domain image
   * @param numOfClusters number of clusters in the domain
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain objects
   */
  public static DomainResource createMiiDomainWithIstioMultiClusters(String domainUid,
                                                                     String domainNamespace,
                                                                     String miiImage,
                                                                     int numOfClusters,
                                                                     int replicaCount) {
    return createMiiDomainWithIstioMultiClusters(domainUid, domainNamespace, miiImage, numOfClusters,
        replicaCount, null);
  }

  /**
   * Create model in image istio enabled domain with multiple clusters.
   *
   * @param domainUid the uid of the domain
   * @param domainNamespace namespace in which the domain will be created
   * @param miiImage model in image domain image
   * @param numOfClusters number of clusters in the domain
   * @param replicaCount replica count of the cluster
   * @param serverPodLabels the labels for the server pod
   * @return oracle.weblogic.domain.Domain objects
   */
  public static DomainResource createMiiDomainWithIstioMultiClusters(String domainUid,
                                                                     String domainNamespace,
                                                                     String miiImage,
                                                                     int numOfClusters,
                                                                     int replicaCount,
                                                                     Map<String, String> serverPodLabels) {

    LoggingFacade logger = getLogger();
    // admin/managed server name here should match with WDT model yaml file
    String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;

    // create registry secret to pull the image from registry
    // this secret is used only for non-kind cluster
    logger.info("Creating registry secret in namespace {0}", domainNamespace);
    if (!secretExists(TEST_IMAGES_REPO_SECRET_NAME, domainNamespace)) {
      createTestRepoSecret(domainNamespace);
    }

    // create secret for admin credentials
    logger.info("Creating secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    if (!secretExists(adminSecretName, domainNamespace)) {
      createSecretWithUsernamePassword(adminSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT,
          ADMIN_PASSWORD_DEFAULT);
    }

    // create encryption secret
    logger.info("Creating encryption secret");
    String encryptionsecret = "encryptionsecret";
    if (!secretExists(encryptionsecret, domainNamespace)) {
      createSecretWithUsernamePassword(encryptionsecret, domainNamespace, "weblogicenc", "weblogicenc");
    }

    // construct the cluster list used for domain custom resource
    List<V1LocalObjectReference> clusterRefList = new ArrayList<>();
    for (int i = numOfClusters; i >= 1; i--) {
      String clusterName = "cluster-" + i;
      String clusterResName = domainUid + "-" + clusterName;
      ClusterSpec clusterSpec = new ClusterSpec()
          .clusterName(clusterName)
          .replicas(replicaCount);

      if (serverPodLabels != null) {
        clusterSpec.serverPod(new ServerPod()
            .labels(serverPodLabels));
      }

      clusterRefList.add(new V1LocalObjectReference().name(clusterResName));

      createClusterAndVerify(createClusterResource(clusterResName, domainNamespace, clusterSpec));
    }

    // set resource request and limit
    Map<String, Quantity> resourceRequest = new HashMap<>();
    Map<String, Quantity> resourceLimit = new HashMap<>();
    resourceRequest.put("cpu", new Quantity("250m"));
    resourceRequest.put("memory", new Quantity("768Mi"));
    resourceLimit.put("cpu", new Quantity("2"));
    resourceLimit.put("memory", new Quantity("2Gi"));

    // create the domain CR
    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/u01/domains/" + domainUid)
            .domainHomeSourceType("FromModel")
            .image(miiImage)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(TEST_IMAGES_REPO_SECRET_NAME))
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(adminSecretName))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IfNeeded")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom "))
                .resources(new V1ResourceRequirements()
                    .requests(resourceRequest)
                    .limits(resourceLimit)))
            .clusters(clusterRefList)
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE)
                    .runtimeEncryptionSecret(encryptionsecret))
                .introspectorJobActiveDeadlineSeconds(3000L)));

    setPodAntiAffinity(domain);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImage);
    createDomainAndVerify(domain, domainNamespace);

    // check that admin server pod is ready and service exists in domain namespace
    logger.info("Checking that admin server pod {0} is ready and service exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // check the readiness for the managed servers in each cluster
    for (int i = 1; i <= numOfClusters; i++) {
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName =
            domainUid + "-cluster-" + i + "-" + MANAGED_SERVER_NAME_BASE + j;

        // check managed server pod is ready and service exists in the namespace
        logger.info("Checking that managed server pod {0} is ready and service exists in namespace {1}",
            managedServerPodName, domainNamespace);
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }
    }

    return domain;
  }

  /**
   * create a ConfigMap with a model that enable SSL on the Administration server.
   * @param configMapName the name of configMap
   * @param model the model configMap will be created with
   * @param domainUid the uid of the domain
   * @param domainNamespace namespace in which the domain will be created
   */
  public static void createModelConfigMapSSLenable(String configMapName, String model, String domainUid,
      String domainNamespace) {
    Map<String, String> labels = new HashMap<>();
    labels.put("weblogic.domainUid", domainUid);
    Map<String, String> data = new HashMap<>();
    data.put("model.ssl.yaml", model);

    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(new V1ObjectMeta()
            .labels(labels)
            .name(configMapName)
            .namespace(domainNamespace));

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Can't create ConfigMap %s", configMapName));
    assertTrue(cmCreated, String.format("createConfigMap failed with name: %s, domainNamespace: %s ",
        configMapName, domainNamespace));
  }

  /**
   * Create a WebLogic domain with SSL enabled in WebLogic configuration by
   * configuring an additional configmap to the domain resource.
   * Add two channels to the domain resource with name `default-secure` and `default`.
   * @param domainUid the uid of the domain
   * @param domNamespace Kubernetes namespace that the domain is hosted
   * @param adminSecretName the name of the secret for admin credentials
   * @param repoSecretName name of the secret for pulling the WebLogic image
   * @param encryptionSecretName name of the secret used to encrypt the models
   * @param replicaCount number of managed servers to start
   * @param miiImage the name if mii image
   * @param configmapName the name of configMap
   *
   * @return domain object of the domain resource
   */
  public static DomainResource create2channelsDomainResourceWithConfigMap(String domainUid,
                                                String domNamespace, String adminSecretName,
                                                String repoSecretName, String encryptionSecretName,
                                                int replicaCount, String miiImage, String configmapName) {

    Map<String, String> keyValueMap = new HashMap<>();
    keyValueMap.put("testkey", "testvalue");

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
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.security.SSL.ignoreHostnameVerification=true"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverService(new ServerService()
                    .annotations(keyValueMap)
                    .labels(keyValueMap))
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default-secure")
                        .nodePort(0))
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(0))))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(3000L)));
    setPodAntiAffinity(domain);
    return domain;
  }

  /**
   * Create a WebLogic domain with SSL enabled in WebLogic configuration by
   * configuring an additional configmap to the domain resource.
   * Add two channels to the domain resource with name `default-secure` and `default`.
   *
   * @param domainNamespace Kubernetes namespace that the pod is running in
   * @param domainUid identifier of the domain
   * @param miiImageName name of the miiImage including its tag
   * @param adminServerPodName name of the admin server pod
   * @param managedServerPrefix prefix of the managed server pods
   * @param replicaCount number of managed servers to start
   */
  public static void createSSLenabledMiiDomainAndVerify(
      String domainNamespace,
      String domainUid,
      String miiImageName,
      String adminServerPodName,
      String managedServerPrefix,
      int replicaCount
  ) {

    LoggingFacade logger = getLogger();

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    createSecretWithUsernamePassword(adminSecretName, domainNamespace,
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    createSecretWithUsernamePassword(encryptionSecretName, domainNamespace,
            "weblogicenc", "weblogicenc");

    String configMapName = "default-secure-configmap";
    String yamlString = "topology:\n"
        + "  Server:\n"
        + "    'admin-server':\n"
        + "       SSL: \n"
        + "         Enabled: true \n"
        + "         ListenPort: '7008' \n";
    createModelConfigMapSSLenable(configMapName, yamlString, domainUid, domainNamespace);

    // create the domain object
    DomainResource domain = create2channelsDomainResourceWithConfigMap(domainUid,
               domainNamespace, adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME, encryptionSecretName,
               replicaCount,
               miiImageName, configMapName);

    // create model in image domain
    logger.info("Creating model in image domain {0} in namespace {1} using image {2}",
        domainUid, domainNamespace, miiImageName);
    createDomainAndVerify(domain, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);
    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed server pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }

  /**
   * Create a MII domain with Istio enabled and wait up to five minutes until the domain exists.
   *
   * @param miiImage image name to config
   * @param domainNamespace namespace in which the domain will be created
   * @param domainUid unique domain identifier
   * @param managedServerPrefix prefix of managed server name
   * @param clusterName cluster name
   * @param configMapName WDT config map to create domain resource
   * @param replicaCount fully qualified URL to the server on which the web app is running
   */
  public static void configIstioModelInImageDomain(String miiImage,
                                                   String domainNamespace,
                                                   String domainUid,
                                                   String managedServerPrefix,
                                                   String clusterName,
                                                   String configMapName,
                                                   int replicaCount) {
    LoggingFacade logger = getLogger();

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

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

    // create WDT config map without any files
    createConfigMapAndVerify(configMapName, domainUid, domainNamespace, Collections.emptyList());

    // create the domain object
    DomainResource domain = createIstioDomainResource(domainUid,
        domainNamespace,
        adminSecretName,
        TEST_IMAGES_REPO_SECRET_NAME,
        encryptionSecretName,
        replicaCount,
        miiImage,
        configMapName,
        clusterName);

    // create model in image domain
    createDomainAndVerify(domain, domainNamespace);
    String adminServerPodName = domainUid + "-admin-server";
    logger.info("Check admin service {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkServiceExists(adminServerPodName, domainNamespace);

    // check admin server pod is ready
    logger.info("Wait for admin server pod {0} to be ready in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReady(adminServerPodName, domainUid, domainNamespace);

    // check managed server services created
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Check managed service {0} is created in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkServiceExists(managedServerPrefix + i, domainNamespace);
    }

    // check managed server pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      logger.info("Wait for managed pod {0} to be ready in namespace {1}",
          managedServerPrefix + i, domainNamespace);
      checkPodReady(managedServerPrefix + i, domainUid, domainNamespace);
    }
  }
}

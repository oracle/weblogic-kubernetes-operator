// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobCondition;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1SecretReference;
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
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static java.io.File.createTempFile;
import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Files.readString;
import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.HTTPS_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_IMAGE_DOMAINHOME_BASE_DIR;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLS_DOMAIN_TYPE;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.TestActions.createConfigMap;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getJob;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.listPods;
import static oracle.weblogic.kubernetes.actions.TestActions.shutdownDomain;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusReasonMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withStandardRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.ImageUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.JobUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.getUniquePvOrPvcName;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class DomainUtils {

  /**
   * Create a domain in the specified namespace and wait up to five minutes until the domain exists.
   *
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param domVersion custom resource's version
   */
  public static void createDomainAndVerify(Domain domain,
                                           String domainNamespace,
                                           String... domVersion) {
    String domainVersion = (domVersion.length == 0) ? DOMAIN_VERSION : domVersion[0];

    LoggingFacade logger = getLogger();
    // create the domain CR
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), "domain spec is null");
    String domainUid = domain.getSpec().getDomainUid();

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain, domainVersion),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for domain {0} to be created in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNamespace,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainExists(domainUid, domainVersion, domainNamespace));

  }

  /**
   * Create a domain in the specified namespace, wait up to five minutes until the domain exists and
   * verify the servers are running.
   *
   * @param domainUid domain
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName admin server pod name
   * @param managedServerPodNamePrefix managed server pod prefix
   * @param replicaCount replica count
   */
  public static void createDomainAndVerify(String domainUid, Domain domain,
                                           String domainNamespace, String adminServerPodName,
                                           String managedServerPodNamePrefix, int replicaCount) {
    LoggingFacade logger = getLogger();

    // create domain and verify
    createDomainAndVerify(domain, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    logger.info("Checking that admin service/pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodNamePrefix + i;

      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

  }

  /**
   * Check the status reason of the domain matches the given reason.
   *
   * @param domain  oracle.weblogic.domain.Domain object
   * @param namespace the namespace in which the domain exists
   * @param statusReason the expected status reason of the domain
   */
  public static void checkDomainStatusReasonMatches(Domain domain, String namespace, String statusReason) {
    LoggingFacade logger = getLogger();
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> logger.info("Waiting for the status reason of the domain {0} in namespace {1} "
                    + "is {2} (elapsed time {3}ms, remaining time {4}ms)",
                domain,
                namespace,
                statusReason,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(assertDoesNotThrow(() -> domainStatusReasonMatches(domain, statusReason)));
  }

  /**
   * Delete a domain in the specified namespace.
   * @param domainNS the namespace in which the domain exists
   * @param domainUid domain uid
   */
  public static void deleteDomainResource(String domainNS, String domainUid) {
    //clean up domain resources in namespace and set namespace to label , managed by operator
    getLogger().info("deleting domain custom resource {0}", domainUid);
    assertTrue(deleteDomainCustomResource(domainUid, domainNS));

    // wait until domain was deleted
    withStandardRetryPolicy
        .conditionEvaluationListener(
            condition -> getLogger().info("Waiting for domain {0} to be deleted in namespace {1} "
                    + "(elapsed time {2}ms, remaining time {3}ms)",
                domainUid,
                domainNS,
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .until(domainDoesNotExist(domainUid, DOMAIN_VERSION, domainNS));
  }

  /**
   * Create a domain in PV using WDT.
   *
   * @param domainNamespace namespace in which the domain will be created
   * @return oracle.weblogic.domain.Domain objects
   */
  public static Domain createDomainOnPvUsingWdt(String domainUid,
                                                String domainNamespace,
                                                String wlSecretName,
                                                String clusterName,
                                                int replicaCount,
                                                String testClassName) {

    int t3ChannelPort = getNextFreePort();

    final String pvName = getUniquePvOrPvcName(domainUid + "-pv"); // name of the persistent volume
    final String pvcName = getUniquePvOrPvcName(domainUid + "-pvc"); // name of the persistent volume claim

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createSecretForBaseImages(domainNamespace);

    // create WebLogic domain credential secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create persistent volume and persistent volume claim for domain
    // these resources should be labeled with domainUid for cleanup after testing
    Path pvHostPath = get(PV_ROOT, testClassName, pvcName);

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("5Gi"))
            .persistentVolumeReclaimPolicy("Recycle"))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvName)
            .build()
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeName(pvName)
            .resources(new V1ResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("5Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(pvcName)
            .withNamespace(domainNamespace)
            .build()
            .putLabelsItem("weblogic.resourceVersion", "domain-v2")
            .putLabelsItem("weblogic.domainUid", domainUid));

    String labelSelector = String.format("weblogic.domainUid in (%s)", domainUid);
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector, domainNamespace,
        domainUid + "-weblogic-domain-storage-class", pvHostPath);

    // create a temporary WebLogic domain property file as a input for WDT model file
    File domainPropertiesFile = assertDoesNotThrow(() -> createTempFile("domainonpv", "properties"),
        "Failed to create domain properties file");

    Properties p = new Properties();
    p.setProperty("adminUsername", ADMIN_USERNAME_DEFAULT);
    p.setProperty("adminPassword", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("domainName", domainUid);
    p.setProperty("adminServerName", ADMIN_SERVER_NAME_BASE);
    p.setProperty("productionModeEnabled", "true");
    p.setProperty("clusterName", clusterName);
    p.setProperty("configuredManagedServerCount", "4");
    p.setProperty("managedServerNameBase", MANAGED_SERVER_NAME_BASE);
    p.setProperty("t3ChannelPort", Integer.toString(t3ChannelPort));
    p.setProperty("t3PublicAddress", K8S_NODEPORT_HOST);
    p.setProperty("managedServerPort", "8001");
    assertDoesNotThrow(() ->
            p.store(new FileOutputStream(domainPropertiesFile), "WDT properties file"),
        "Failed to write domain properties file");

    // shell script to download WDT and run the WDT createDomain script
    Path wdtScript = get(RESOURCE_DIR, "bash-scripts", "setup_wdt.sh");
    // WDT model file containing WebLogic domain configuration
    Path wdtModelFile = get(RESOURCE_DIR, "wdt-models", "domain-onpv-wdt-model.yaml");

    // create configmap and domain in persistent volume using WDT
    runCreateDomainOnPVJobUsingWdt(wdtScript, wdtModelFile, domainPropertiesFile.toPath(),
        domainUid, pvName, pvcName, domainNamespace, testClassName);

    Domain domain = createDomainResourceForDomainOnPV(domainUid, domainNamespace, wlSecretName, pvName, pvcName,
        clusterName, replicaCount);

    // Verify the domain custom resource is created.
    // Also verify the admin server pod and managed server pods are up and running.
    createDomainAndVerify(domainUid, domain, domainNamespace, domainUid + "-" + ADMIN_SERVER_NAME_BASE,
        domainUid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount);

    return domain;
  }

  /**
   * Create domain with domain-on-pv type and verify the domain is created.
   * Also verify the admin server pod and managed server pods are up and running.
   * @param domainUid - domain uid
   * @param domainNamespace - domain namespace
   * @param wlSecretName - wls administrator secret name
   * @param pvName - PV name
   * @param pvcName - PVC name
   * @param clusterName - cluster name
   * @param replicaCount - repica count of the clsuter
   * @return oracle.weblogic.domain.Domain object
   */
  public static Domain createDomainResourceForDomainOnPV(String domainUid,
                                                         String domainNamespace,
                                                         String wlSecretName,
                                                         String pvName,
                                                         String pvcName,
                                                         String clusterName,
                                                         int replicaCount) {
    // create the domain custom resource
    getLogger().info("Creating domain custom resource");
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome("/u01/shared/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy("IfNotPresent")
            .imagePullSecrets(Collections.singletonList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET)))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/u01/shared/logs/" + domainUid)
            .dataHome("")
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
                    .mountPath("/u01/shared")
                    .name(pvName)))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .addClustersItem(new Cluster() //cluster
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING")));
    setPodAntiAffinity(domain);

    return domain;
  }

  /**
   * Create a WebLogic domain in a persistent volume by doing the following.
   * Create a configmap containing WDT model file, property file and shell script to download and run WDT.
   * Create a Kubernetes job to create domain on persistent volume.
   *
   * @param domainCreationScriptFile path of the shell script to download and run WDT
   * @param modelFile path of the WDT model file
   * @param domainPropertiesFile property file holding properties referenced in WDT model file
   * @param domainUid unique id of the WebLogic domain
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param namespace name of the domain namespace in which the job is created
   * @param testClassName the test class name which calls this method
   */
  private static void runCreateDomainOnPVJobUsingWdt(Path domainCreationScriptFile,
                                                     Path modelFile,
                                                     Path domainPropertiesFile,
                                                     String domainUid,
                                                     String pvName,
                                                     String pvcName,
                                                     String namespace,
                                                     String testClassName) {
    getLogger().info("Preparing to run create domain job using WDT");

    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(domainCreationScriptFile);
    domainScriptFiles.add(domainPropertiesFile);
    domainScriptFiles.add(modelFile);

    getLogger().info("Creating a config map to hold domain creation scripts");
    String domainScriptConfigMapName = "create-domain-scripts-cm-" + testClassName.toLowerCase();
    assertDoesNotThrow(
        () -> createConfigMapForDomainCreation(domainScriptConfigMapName, domainScriptFiles, namespace, testClassName),
        "Create configmap for domain creation failed");

    // create a V1Container with specific scripts and properties for creating domain
    V1Container jobCreationContainer = new V1Container()
        .addCommandItem("/bin/sh")
        .addArgsItem("/u01/weblogic/" + domainCreationScriptFile.getFileName())
        .addEnvItem(new V1EnvVar()
            .name("WDT_VERSION")
            .value(WDT_VERSION))
        .addEnvItem(new V1EnvVar()
            .name("WDT_MODEL_FILE")
            .value("/u01/weblogic/" + modelFile.getFileName()))
        .addEnvItem(new V1EnvVar()
            .name("WDT_VAR_FILE")
            .value("/u01/weblogic/" + domainPropertiesFile.getFileName()))
        .addEnvItem(new V1EnvVar()
            .name("WDT_DIR")
            .value("/u01/shared/wdt"))
        .addEnvItem(new V1EnvVar()
            .name("http_proxy")
            .value(System.getenv("http_proxy")))
        .addEnvItem(new V1EnvVar()
            .name("https_proxy")
            .value(System.getenv("http_proxy")))
        .addEnvItem(new V1EnvVar()
            .name("DOMAIN_HOME_DIR")
            .value("/u01/shared/domains/" + domainUid))
        .addEnvItem(new V1EnvVar()
            .name("https_proxy")
            .value(HTTPS_PROXY));

    getLogger().info("Running a Kubernetes job to create the domain");
    createDomainJob(pvName, pvcName,
        domainScriptConfigMapName, namespace, jobCreationContainer);
  }

  /**
   * Create ConfigMap containing domain creation scripts.
   *
   * @param configMapName name of the ConfigMap to create
   * @param files files to add in ConfigMap
   * @param namespace name of the namespace in which to create ConfigMap
   * @param testClassName the test class name to call this method
   * @throws IOException when reading the domain script files fail
   */
  private static void createConfigMapForDomainCreation(String configMapName,
                                                       List<Path> files,
                                                       String namespace,
                                                       String testClassName)
      throws IOException {
    getLogger().info("Creating ConfigMap {0}", configMapName);

    Path domainScriptsDir = createDirectories(
        get(TestConstants.LOGS_DIR, testClassName, namespace));

    // add domain creation scripts and properties files to the configmap
    Map<String, String> data = new HashMap<>();
    for (Path file : files) {
      getLogger().info("Adding file {0} in ConfigMap", file);
      data.put(file.getFileName().toString(), readString(file));
      getLogger().info("Making a copy of file {0} to {1} for diagnostic purposes", file,
          domainScriptsDir.resolve(file.getFileName()));
      copy(file, domainScriptsDir.resolve(file.getFileName()));
    }
    V1ObjectMeta meta = new V1ObjectMeta()
        .name(configMapName)
        .namespace(namespace);
    V1ConfigMap configMap = new V1ConfigMap()
        .data(data)
        .metadata(meta);

    boolean cmCreated = assertDoesNotThrow(() -> createConfigMap(configMap),
        String.format("Failed to create ConfigMap %s with files %s", configMapName, files));
    assertTrue(cmCreated, String.format("Failed while creating ConfigMap %s", configMapName));
  }

  /**
   * Create a job to create a domain in persistent volume.
   *
   * @param pvName name of the persistent volume to create domain in
   * @param pvcName name of the persistent volume claim
   * @param domainScriptCM ConfigMap holding domain creation script files
   * @param namespace name of the domain namespace in which the job is created
   * @param jobContainer V1Container with job commands to create domain
   */
  private static void createDomainJob(String pvName,
                                      String pvcName,
                                      String domainScriptCM,
                                      String namespace,
                                      V1Container jobContainer) {
    getLogger().info("Running Kubernetes job to create domain");
    V1PodSpec podSpec = new V1PodSpec()
        .restartPolicy("Never")
        .addContainersItem(jobContainer  // container containing WLST or WDT details
               .name("create-weblogic-domain-onpv-container")
                        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                        .imagePullPolicy("IfNotPresent")
                        .addPortsItem(new V1ContainerPort()
                            .containerPort(7001))
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                                  .mountPath("/u01/weblogic"), // availble under /u01/weblogic inside pod
                            new V1VolumeMount()
                                .name(pvName) // location to write domain
                                .mountPath("/u01/shared")))) // mounted under /u01/shared inside pod
                    .volumes(Arrays.asList(
                        new V1Volume()
                            .name(pvName)
                            .persistentVolumeClaim(
                                new V1PersistentVolumeClaimVolumeSource()
                                    .claimName(pvcName)),
                        new V1Volume()
                            .name("create-weblogic-domain-job-cm-volume")
                            .configMap(
                                new V1ConfigMapVolumeSource()
                                    .name(domainScriptCM)))) //config map containing domain scripts
                    .imagePullSecrets(Arrays.asList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET)));  // this secret is used only for non-kind cluster
    if (!OKD) {
      podSpec.initContainers(Arrays.asList(createfixPVCOwnerContainer(pvName, "/u01/shared")));
    }

    V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec();
    podTemplateSpec.spec(podSpec);
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name("create-domain-onpv-job-" + pvName) // name of the create domain job
                .namespace(namespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(podTemplateSpec));

    String jobName = createJobAndWaitUntilComplete(jobBody, namespace);

    // check job status and fail test if the job failed to create domain
    V1Job job = assertDoesNotThrow(() -> getJob(jobName, namespace),
        "Getting the job failed");
    if (job != null && job.getStatus() != null && job.getStatus().getConditions() != null) {
      V1JobCondition jobCondition = job.getStatus().getConditions().stream().filter(
          v1JobCondition -> "Failed".equalsIgnoreCase(v1JobCondition.getType()))
          .findAny()
          .orElse(null);
      if (jobCondition != null) {
        getLogger().severe("Job {0} failed to create domain", jobName);
        List<V1Pod> pods = assertDoesNotThrow(() -> listPods(
            namespace, "job-name=" + jobName).getItems(),
            "Listing pods failed");
        if (!pods.isEmpty() && pods.get(0) != null && pods.get(0).getMetadata() != null
            && pods.get(0).getMetadata().getName() != null) {
          String podLog = assertDoesNotThrow(() -> getPodLog(pods.get(0).getMetadata().getName(), namespace),
              "Failed to get pod log");
          getLogger().severe(podLog);
          fail("Domain create job failed");
        }
      }
    }
  }

  /**
   * Create a WebLogic domain in image using WDT.
   *
   * @param domainUid domain uid
   * @param domainNamespace namespace in which the domain to be created
   * @param wdtModelFileForDomainInImage WDT model file used to create domain image
   * @param appSrcDirList list of the app src in WDT model file
   * @param wlSecretName wls admin secret name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain object
   */
  public static Domain createAndVerifyDomainInImageUsingWdt(String domainUid,
                                                            String domainNamespace,
                                                            String wdtModelFileForDomainInImage,
                                                            List<String> appSrcDirList,
                                                            String wlSecretName,
                                                            String clusterName,
                                                            int replicaCount) {

    // create secret for admin credentials
    getLogger().info("Create secret for admin credentials");
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);

    // create image with model files
    getLogger().info("Creating image with model file and verify");
    String domainInImageWithWDTImage = createImageAndVerify("domaininimage-wdtimage",
        Collections.singletonList(MODEL_DIR + "/" + wdtModelFileForDomainInImage), appSrcDirList,
        Collections.singletonList(MODEL_DIR + "/" + WDT_BASIC_MODEL_PROPERTIES_FILE),
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, WLS_DOMAIN_TYPE, false,
        domainUid, false);

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(domainInImageWithWDTImage);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    return createDomainInImageAndVerify(domainUid, domainNamespace, domainInImageWithWDTImage, wlSecretName,
        clusterName, replicaCount);
  }

  /**
   * Create domain resource with domain-in-image type.
   *
   * @param domainUid domain uid
   * @param domainNamespace domain namespace
   * @param imageName image name used to create domain-in-image domain
   * @param wlSecretName wls admin secret name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain object
   */
  public static Domain createDomainResourceForDomainInImage(String domainUid,
                                                            String domainNamespace,
                                                            String imageName,
                                                            String wlSecretName,
                                                            String clusterName,
                                                            int replicaCount) {
    // create the domain custom resource
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .domainHome(WDT_IMAGE_DOMAINHOME_BASE_DIR + "/" + domainUid)
            .dataHome("/u01/oracle/mydata")
            .domainHomeSourceType("Image")
            .image(imageName)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(OCIR_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(wlSecretName)
                .namespace(domainNamespace))
            .includeServerOutInPodLog(true)
            .serverStartPolicy("IF_NEEDED")
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=false"))
                .addEnvItem(new V1EnvVar()
                    .name("USER_MEM_ARGS")
                    .value("-Djava.security.egd=file:/dev/./urandom ")))
            .adminServer(new AdminServer()
                .serverStartState("RUNNING")
                .adminService(new AdminService()
                    .addChannelsItem(new Channel()
                        .channelName("default")
                        .nodePort(getNextFreePort()))))
            .addClustersItem(new Cluster()
                .clusterName(clusterName)
                .replicas(replicaCount)
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .model(new Model()
                    .domainType(WLS_DOMAIN_TYPE))
                .introspectorJobActiveDeadlineSeconds(300L)));
    setPodAntiAffinity(domain);

    return domain;
  }

  /**
   * Create domain with domain-in-image type and verify the domain is created.
   * Also verify the admin server pod and managed server pods are up and running.
   * @param domainUid domain uid
   * @param domainNamespace domain namespace
   * @param imageName image name used to create domain-in-image domain
   * @param wlSecretName wls admin secret name
   * @param clusterName cluster name
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain object
   */
  public static Domain createDomainInImageAndVerify(String domainUid,
                                                    String domainNamespace,
                                                    String imageName,
                                                    String wlSecretName,
                                                    String clusterName,
                                                    int replicaCount) {
    // create the domain custom resource
    Domain domain = createDomainResourceForDomainInImage(domainUid, domainNamespace, imageName, wlSecretName,
        clusterName, replicaCount);

    createDomainAndVerify(domainUid, domain, domainNamespace, domainUid + "-" + ADMIN_SERVER_NAME_BASE,
        domainUid + "-" + MANAGED_SERVER_NAME_BASE, replicaCount);

    return domain;
  }

  /**
   * Shutdown domain and verify all the server pods were shutdown.
   *
   * @param domainNamespace the namespace where the domain exists
   * @param domainUid the uid of the domain to shutdown
   * @param replicaCount replica count of the domain cluster
   */
  public static void shutdownDomainAndVerify(String domainNamespace, String domainUid, int replicaCount) {
    // shutdown domain
    getLogger().info("Shutting down domain {0} in namespace {1}", domainUid, domainNamespace);
    shutdownDomain(domainUid, domainNamespace);

    // verify all the pods were shutdown
    getLogger().info("Verifying all server pods were shutdown for the domain");
    // check admin server pod was shutdown
    checkPodDoesNotExist(domainUid + "-" + ADMIN_SERVER_NAME_BASE,
        domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + i;
      checkPodDoesNotExist(managedServerPodName, domainUid, domainNamespace);
    }
  }

}

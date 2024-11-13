// Copyright (c) 2022, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerPort;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ObjectMetaBuilder;
import io.kubernetes.client.openapi.models.V1PersistentVolume;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeSpec;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1VolumeResourceRequirements;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.ClusterResource;
import oracle.weblogic.domain.ClusterSpec;
import oracle.weblogic.domain.DomainResource;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static java.nio.file.Files.copy;
import static java.nio.file.Files.createDirectories;
import static java.nio.file.Paths.get;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_SERVER_PORT_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.CRIO;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_INTERVAL_SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.FAILURE_RETRY_LIMIT_MINUTES;
import static oracle.weblogic.kubernetes.TestConstants.IMAGE_PULL_POLICY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.MANAGED_SERVER_NAME_BASE;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER_PRIVATEIP;
import static oracle.weblogic.kubernetes.TestConstants.PV_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER_DEFAULT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.APP_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.callWebAppAndWaitTillReady;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.verifyAdminServerRESTAccess;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterAndVerify;
import static oracle.weblogic.kubernetes.utils.ClusterUtils.createClusterResource;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.END_PORT;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.START_PORT;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getNextFreePort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getUniqueName;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.verifyServerCommunication;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapFromFiles;
import static oracle.weblogic.kubernetes.utils.DeployUtil.deployUsingWlst;
import static oracle.weblogic.kubernetes.utils.DomainUtils.createDomainAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.JobUtils.createJobAndWaitUntilComplete;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createPVPVCAndVerify;
import static oracle.weblogic.kubernetes.utils.PersistentVolumeUtils.createfixPVCOwnerContainer;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodReady;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.SecretUtils.createSecretWithUsernamePassword;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The common utility class for LoadBalancer tests.
 */
public class CommonLBTestUtils {

  /**
   * Create multiple domains on PV using WLST.
   * @param domainNamespace domain namespace
   * @param wlSecretName wls secret name
   * @param testClassName test class name which will call this method
   * @param numberOfDomains number of domains to create
   * @param domainUids list of domain uids
   * @param replicaCount replica count of the domain cluster
   * @param clusterName cluster name of the domain
   * @param adminServerPort admin server port
   * @param managedServerPort managed server port
   * @return List of PV and PVC name
   */
  public static List<String> createMultipleDomainsSharingPVUsingWlstAndVerify(String domainNamespace,
                                                                              String wlSecretName,
                                                                              String testClassName,
                                                                              int numberOfDomains,
                                                                              List<String> domainUids,
                                                                              int replicaCount,
                                                                              String clusterName,
                                                                              int adminServerPort,
                                                                              int managedServerPort) {

    // create pull secrets for WebLogic image
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);

    // create WebLogic credentials secret
    createSecretWithUsernamePassword(wlSecretName, domainNamespace, ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT);
    Path pvHostPath = get(PV_ROOT, testClassName, "sharing-persistentVolume");

    List<String> pvPvcPair = new ArrayList<>();
    String sharingPvName = getUniqueName("sharing-pv-");
    String sharingPvcName = getUniqueName("sharing-pvc-");
    pvPvcPair.add(sharingPvName);
    pvPvcPair.add(sharingPvcName);

    V1PersistentVolume v1pv = new V1PersistentVolume()
        .spec(new V1PersistentVolumeSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeMode("Filesystem")
            .putCapacityItem("storage", Quantity.fromString("6Gi"))
            .persistentVolumeReclaimPolicy("Retain"))
        .metadata(new V1ObjectMetaBuilder()
            .withName(sharingPvName)
            .build()
            .putLabelsItem("sharing-pv", "true"));

    V1PersistentVolumeClaim v1pvc = new V1PersistentVolumeClaim()
        .spec(new V1PersistentVolumeClaimSpec()
            .addAccessModesItem("ReadWriteMany")
            .volumeName(sharingPvName)
            .resources(new V1VolumeResourceRequirements()
                .putRequestsItem("storage", Quantity.fromString("6Gi"))))
        .metadata(new V1ObjectMetaBuilder()
            .withName(sharingPvcName)
            .withNamespace(domainNamespace)
            .build()
            .putLabelsItem("sharing-pvc", "true"));

    // create pv and pvc
    String labelSelector = String.format("sharing-pv in (%s)", "true");
    createPVPVCAndVerify(v1pv, v1pvc, labelSelector,
        domainNamespace, "default-sharing-weblogic-domain-storage-class", pvHostPath);

    for (int i = 0; i < numberOfDomains; i++) {
      String domainUid = domainUids.get(i);
      String domainScriptConfigMapName = getUniqueName("create-domain" + i + "-scripts-cm-");
      String createDomainInPVJobName = getUniqueName("create-domain" + i + "-onpv-job-");

      int  t3ChannelPort = getNextFreePort(START_PORT, START_PORT + 380);

      getLogger().info("t3ChannelPort for domain {0} is {1}", domainUid, t3ChannelPort);

      // run create a domain on PV job using WLST
      runCreateDomainOnPVJobUsingWlst(sharingPvName, sharingPvcName, domainUid, domainNamespace,
          domainScriptConfigMapName, createDomainInPVJobName, testClassName, clusterName, adminServerPort,
          managedServerPort, t3ChannelPort);

      // create the domain custom resource configuration object
      getLogger().info("Creating domain custom resource");
      DomainResource domain = createDomainCustomResource(domainUid, domainNamespace, sharingPvName,
          sharingPvcName, t3ChannelPort, wlSecretName, clusterName, replicaCount);

      getLogger().info("Creating domain custom resource {0} in namespace {1}", domainUid, domainNamespace);
      createDomainAndVerify(domain, domainNamespace);

      String adminServerPodName = domainUid + "-" + ADMIN_SERVER_NAME_BASE;
      // check admin server pod is ready and service exists in domain namespace
      checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

      // check for managed server pods are ready and services exist in domain namespace
      for (int j = 1; j <= replicaCount; j++) {
        String managedServerPodName = domainUid + "-" + MANAGED_SERVER_NAME_BASE + j;
        checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
      }

      int serviceNodePort =
          getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
      getLogger().info("Getting admin service node port: {0}", serviceNodePort);

      getLogger().info("Validating WebLogic admin server access by login to console");
      if (OKE_CLUSTER_PRIVATEIP || OCNE || CRIO) {
        assertTrue(assertDoesNotThrow(
            () -> adminLoginPageAccessible(adminServerPodName, "7001", domainNamespace,
                ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT),
            "Access to admin server node port failed"), "Console login validation failed");
      } else {
        if (TestConstants.KIND_CLUSTER
            && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
          String hostHeader = createIngressHostRouting(domainNamespace, domainUid, ADMIN_SERVER_NAME_BASE, 7001);
          Map<String, String> headers = new HashMap<>();
          headers.put("host", hostHeader);
          assertDoesNotThrow(()
              -> verifyAdminServerRESTAccess("localhost", TRAEFIK_INGRESS_HTTP_HOSTPORT, false, hostHeader));
        } else {
          assertTrue(assertDoesNotThrow(
              () -> adminNodePortAccessible(serviceNodePort),
              "Access to admin server node port failed"), "Console login validation failed");
        }
      }
    }

    return pvPvcPair;
  }

  /**
   * Run a job to create a WebLogic domain on a persistent volume by doing the following.
   * Copies the WLST domain script to a temp location.
   * Creates a domain properties in the temp location.
   * Creates a configmap containing domain scripts and property files.
   * Runs a job to create domain on persistent volume.
   *
   * @param pvName persistence volume on which the WebLogic domain home will be hosted
   * @param pvcName persistence volume claim for the WebLogic domain
   * @param domainUid the Uid of the domain to create
   * @param domainNamespace the namespace in which the domain will be created
   * @param domainScriptConfigMapName the configMap name for domain script
   * @param createDomainInPVJobName the job name for creating domain in PV
   * @param testClassName the test class name which calls this method
   * @param clusterName cluster name of the domain
   * @param adminServerPort admin server port
   * @param managedServerPort managed server port
   * @param t3ChannelPort t3 channel port for admin server
   */
  private static void runCreateDomainOnPVJobUsingWlst(String pvName,
                                                      String pvcName,
                                                      String domainUid,
                                                      String domainNamespace,
                                                      String domainScriptConfigMapName,
                                                      String createDomainInPVJobName,
                                                      String testClassName,
                                                      String clusterName,
                                                      int adminServerPort,
                                                      int managedServerPort,
                                                      int t3ChannelPort) {

    getLogger().info("Creating a staging location for domain creation scripts");
    Path pvTemp = get(RESULTS_ROOT, testClassName, "domainCreateTempPV");
    assertDoesNotThrow(() -> deleteDirectory(pvTemp.toFile()),"deleteDirectory failed with IOException");
    assertDoesNotThrow(() -> createDirectories(pvTemp), "createDirectories failed with IOException");

    getLogger().info("Copying the domain creation WLST script to staging location");
    Path srcWlstScript = get(RESOURCE_DIR, "python-scripts", "wlst-create-domain-onpv.py");
    Path targetWlstScript = get(pvTemp.toString(), "create-domain.py");
    assertDoesNotThrow(() -> copy(srcWlstScript, targetWlstScript, StandardCopyOption.REPLACE_EXISTING),
        "copy failed with IOException");

    getLogger().info("Creating WebLogic domain properties file");
    Path domainPropertiesFile = get(pvTemp.toString(), "domain.properties");
    createDomainProperties(
            domainPropertiesFile, domainUid, domainNamespace,
            clusterName, adminServerPort, managedServerPort, t3ChannelPort);

    getLogger().info("Adding files to a ConfigMap for domain creation job");
    List<Path> domainScriptFiles = new ArrayList<>();
    domainScriptFiles.add(targetWlstScript);
    domainScriptFiles.add(domainPropertiesFile);

    getLogger().info("Creating a ConfigMap to hold domain creation scripts");
    createConfigMapFromFiles(domainScriptConfigMapName, domainScriptFiles, domainNamespace);

    getLogger().info("Running a Kubernetes job to create the domain");
    V1Job jobBody = new V1Job()
        .metadata(
            new V1ObjectMeta()
                .name(createDomainInPVJobName)
                .namespace(domainNamespace))
        .spec(new V1JobSpec()
            .backoffLimit(0) // try only once
            .template(new V1PodTemplateSpec()
                .spec(new V1PodSpec()
                    .restartPolicy("Never")
                    .initContainers(Collections.singletonList(createfixPVCOwnerContainer(pvName, "/shared")))
                    .containers(Collections.singletonList(new V1Container()
                        .name("create-weblogic-domain-onpv-container")
                        .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
                        .ports(Collections.singletonList(new V1ContainerPort()
                            .containerPort(adminServerPort)))
                        .volumeMounts(Arrays.asList(
                            new V1VolumeMount()
                                .name("create-weblogic-domain-job-cm-volume") // domain creation scripts volume
                                .mountPath("/u01/weblogic"), // availble under /u01/weblogic inside pod
                            new V1VolumeMount()
                                .name(pvName) // location to write domain
                                .mountPath("/shared"))) // mounted under /shared inside pod
                        .addCommandItem("/bin/sh") //call wlst.sh script with py and properties file
                        .addArgsItem("/u01/oracle/oracle_common/common/bin/wlst.sh")
                        .addArgsItem("/u01/weblogic/create-domain.py")
                        .addArgsItem("-skipWLSModuleScanning")
                        .addArgsItem("-loadProperties")
                        .addArgsItem("/u01/weblogic/domain.properties")))
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
                                    .name(domainScriptConfigMapName))))  //ConfigMap containing domain scripts
                    .imagePullSecrets(Collections.singletonList(
                        new V1LocalObjectReference()
                            .name(BASE_IMAGES_REPO_SECRET_NAME))))));  // this secret is used only for non-kind cluster

    assertNotNull(jobBody.getMetadata());
    getLogger().info("Running a job {0} to create a domain on PV for domain {1} in namespace {2}",
        jobBody.getMetadata().getName(), domainUid, domainNamespace);
    createJobAndWaitUntilComplete(jobBody, domainNamespace);
  }

  /**
   * Create a domain custom resource object.
   *
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param pvName name of persistence volume
   * @param pvcName name of persistence volume claim
   * @param t3ChannelPort t3 channel port for admin server
   * @param wlSecretName wls secret name
   * @param clusterName cluster name of the domain
   * @param replicaCount replica count of the cluster
   * @return oracle.weblogic.domain.Domain object
   */
  private static DomainResource createDomainCustomResource(String domainUid,
                                                           String domainNamespace,
                                                           String pvName,
                                                           String pvcName,
                                                           int t3ChannelPort,
                                                           String wlSecretName,
                                                           String clusterName,
                                                           int replicaCount) {

    DomainResource domain = new DomainResource()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domainNamespace))
        .spec(new DomainSpec()
            .domainUid(domainUid)
            .replicas(replicaCount)
            .domainHome("/shared/" + domainNamespace + "/" + domainUid + "/domains/" + domainUid)
            .domainHomeSourceType("PersistentVolume")
            .image(WEBLOGIC_IMAGE_TO_USE_IN_SPEC)
            .imagePullPolicy(IMAGE_PULL_POLICY)
            .imagePullSecrets(Collections.singletonList(
                new V1LocalObjectReference()
                    .name(BASE_IMAGES_REPO_SECRET_NAME)))  // this secret is used only for non-kind cluster
            .webLogicCredentialsSecret(new V1LocalObjectReference()
                .name(wlSecretName))
            .includeServerOutInPodLog(true)
            .logHomeEnabled(Boolean.TRUE)
            .logHome("/shared/" + domainNamespace + "/" + domainUid + "/logs/" + domainUid)
            .dataHome("")
            .serverStartPolicy("IfNeeded")
            .failureRetryIntervalSeconds(FAILURE_RETRY_INTERVAL_SECONDS)
            .failureRetryLimitMinutes(FAILURE_RETRY_LIMIT_MINUTES)
            .serverPod(new ServerPod()
                .addEnvItem(new V1EnvVar()
                    .name("JAVA_OPTIONS")
                    .value("-Dweblogic.StdoutDebugEnabled=true "
                        + "-Dweblogic.http.isWLProxyHeadersAccessible=true "
                        + "-Dweblogic.debug.DebugHttp=true "
                        + "-Dweblogic.rjvm.allowUnknownHost=true "
                        + "-Dweblogic.ResolveDNSName=true "
                        + "-Dweblogic.MaxMessageSize=20000000"))
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
                        .nodePort(getNextFreePort(START_PORT + 381, END_PORT)))
                    .addChannelsItem(new Channel()
                        .channelName("T3Channel")
                        .nodePort(t3ChannelPort)))));

    // create cluster resource
    String clusterResName = domainUid + "-" + clusterName;
    ClusterResource cluster = createClusterResource(clusterResName, domainNamespace,
        new ClusterSpec().withClusterName(clusterName).replicas(replicaCount));
    getLogger().info("Creating cluster {0} in namespace {1}", clusterResName, domainNamespace);
    createClusterAndVerify(cluster);

    // set cluster references
    domain.getSpec().withCluster(new V1LocalObjectReference().name(clusterResName));

    setPodAntiAffinity(domain);
    return domain;
  }

  /**
   * Verify admin node port(default/t3channel) is accessible by login to WebLogic console
   * using the node port and validate its the Home page.
   *
   * @param nodePort the node port that needs to be tested for access
   * @return true if login to WebLogic administration console is successful
   * @throws IOException when connection to console fails
   */
  private static boolean adminNodePortAccessible(int nodePort)
      throws IOException {
    getLogger().info("Check REST Console for WebLogic Image");
    StringBuffer curlCmd = new StringBuffer("status=$(curl -g --user ");
    String host = K8S_NODEPORT_HOST;
    if (host.contains(":")) {
      host = "[" + host + "]";
    }
    curlCmd.append(ADMIN_USERNAME_DEFAULT)
        .append(":")
        .append(ADMIN_PASSWORD_DEFAULT)
        .append(" http://")
        .append(host)
        .append(":")
        .append(nodePort)
        .append("/management/tenant-monitoring/servers/ --silent --show-error -o /dev/null -w %{http_code}); ")
        .append("echo ${status}");
    getLogger().info("checkRestConsole : curl command {0}", new String(curlCmd));
    try {
      ExecResult result = ExecCommand.exec(new String(curlCmd), true);
      String response = result.stdout().trim();
      getLogger().info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      return response.contains("200");
    } catch (IOException | InterruptedException ex) {
      getLogger().info("Exception in checkRestConsole {0}", ex);
      return false;
    }
  }


  /**
   * Verify REST console is accessible by login to WebLogic Server.
   *
   * @param adminServerPodName admin server pod
   * @param adminPort admin port
   * @param namespace admin server pod namespace
   * @param userName WebLogic administration server user name
   * @param password WebLogic administration server password
   * @return true if login to WebLogic REST console is successful
   * @throws IOException when connection to console fails
   */
  public static boolean adminLoginPageAccessible(String adminServerPodName, String adminPort, String namespace,
                                                 String userName, String password)
      throws IOException {
    LoggingFacade logger = getLogger();
    logger.info("Check REST Console for WebLogic Image");
    StringBuffer curlCmd = new StringBuffer(KUBERNETES_CLI + " exec -n "
        + namespace + " " + adminServerPodName)
        .append(" -- /bin/bash -c \"")
        .append("curl -g --user ")
        .append(userName)
        .append(":")
        .append(password)
        .append(" http://" + adminServerPodName + ":" + adminPort)
        .append("/management/tenant-monitoring/servers/ --silent --show-error -o /dev/null -w %{http_code} && ")
        .append("echo ${status}\"");
    logger.info("checkRestConsole : k8s exec command {0}", new String(curlCmd));
    try {
      ExecResult result = ExecCommand.exec(new String(curlCmd), true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      return response.contains("200");
    } catch (IOException | InterruptedException ex) {
      logger.info("Exception in checkRestConsole {0}", ex);
      return false;
    }
  }

  /**
   * Create a properties file for WebLogic domain configuration.
   * @param wlstPropertiesFile path of the properties file
   * @param domainUid the WebLogic domain for which the properties file is created
   * @param domainNamespace the WebLogic domain namespace
   * @param clusterName cluster name of the domain
   * @param adminServerPort admin server port
   * @param managedServerPort managed server port
   * @param t3ChannelPort t3 channel port of the admin server
   */
  private static void createDomainProperties(Path wlstPropertiesFile,
                                             String domainUid,
                                             String domainNamespace,
                                             String clusterName,
                                             int adminServerPort,
                                             int managedServerPort,
                                             int t3ChannelPort) {
    // create a list of properties for the WebLogic domain configuration
    Properties p = new Properties();

    p.setProperty("domain_path", "/shared/" + domainNamespace + "/" + domainUid + "/domains");
    p.setProperty("domain_name", domainUid);
    p.setProperty("cluster_name", clusterName);
    p.setProperty("admin_server_name", ADMIN_SERVER_NAME_BASE);
    p.setProperty("managed_server_port", "" + managedServerPort);
    p.setProperty("admin_server_port", "" + adminServerPort);
    p.setProperty("admin_username", ADMIN_USERNAME_DEFAULT);
    p.setProperty("admin_password", ADMIN_PASSWORD_DEFAULT);
    p.setProperty("admin_t3_public_address", K8S_NODEPORT_HOST);
    p.setProperty("admin_t3_channel_port", Integer.toString(t3ChannelPort));
    p.setProperty("number_of_ms", "4");
    p.setProperty("managed_server_name_base", MANAGED_SERVER_NAME_BASE);
    p.setProperty("domain_logs", "/shared/" + domainNamespace + "/"
            + domainUid + "/logs/" + domainUid);
    p.setProperty("production_mode_enabled", "true");

    FileOutputStream fileOutputStream =
        assertDoesNotThrow(() -> new FileOutputStream(wlstPropertiesFile.toFile()),
            "new FileOutputStream failed with FileNotFoundException");
    assertDoesNotThrow(() -> p.store(fileOutputStream, "WLST properties file"),
        "Writing the property list to the specified output stream failed with IOException");
  }

  /**
   * Build and deplopy ClusterView app to the domains.
   * @param domainNamespace domain namespace
   * @param domainUids uid of the domains
   */
  public static void buildAndDeployClusterviewApp(String domainNamespace,
                                                  List<String> domainUids) {
    // build the clusterview application
    getLogger().info("Building clusterview application");
    Path distDir = BuildApplication.buildApplication(Paths.get(APP_DIR, "clusterview"),
        null, null, "dist", domainNamespace);
    assertTrue(Paths.get(distDir.toString(),
        "clusterview.war").toFile().exists(),
        "Application archive is not available");
    Path clusterViewAppPath = Paths.get(distDir.toString(), "clusterview.war");

    // deploy clusterview application in namespace
    for (String domainUid : domainUids) {
      // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
      String adminServerPodName = domainUid + "-admin-server";
      testUntil(() -> deployApplication(domainNamespace, domainUid, adminServerPodName, clusterViewAppPath),
          getLogger(),
          "deploying application {0} to pod {1} in namespace {2} succeeds",
          clusterViewAppPath,
          adminServerPodName,
          domainNamespace);
    }
  }

  private static boolean deployApplication(String namespace,
                                           String domainUid,
                                           String adminServerPodName,
                                           Path clusterViewAppPath,
                                           String... clusterName) {
    getLogger().info("Getting node port for admin server default channel");

    if (OKE_CLUSTER_PRIVATEIP) {
      // In internal OKE env, deploy App using WLST
      assertDoesNotThrow(() -> deployUsingWlst(adminServerPodName,
          String.valueOf(7001),
          ADMIN_USERNAME_DEFAULT,
          ADMIN_PASSWORD_DEFAULT,
          "cluster-1",
          clusterViewAppPath,
          namespace),"Deploying the application");
      return true;
    } else if (KIND_CLUSTER && !WLSIMG_BUILDER.equals(WLSIMG_BUILDER_DEFAULT)) {
      getLogger().info("Deploying webapp {0} to domain {1}", clusterViewAppPath, domainUid);
      deployUsingWlst(adminServerPodName, Integer.toString(ADMIN_SERVER_PORT_DEFAULT),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, "cluster-1",
          clusterViewAppPath, namespace);
      return true;
    } else {
      int serviceNodePort = assertDoesNotThrow(() ->
          getServiceNodePort(namespace, getExternalServicePodName(adminServerPodName), "default"),
              "Getting admin server node port failed");
      assertNotEquals(-1, serviceNodePort, "admin server default node port is not valid");
      getLogger().info("Deploying application {0} to domain {1} cluster target cluster-1 in namespace {2}",
          clusterViewAppPath, domainUid, namespace);
      String targets = "{ identity: [ clusters, 'cluster-1' ] }";

      // In non-internal OKE env, deploy App using WebLogic restful management services
      ExecResult result = DeployUtil.deployUsingRest(K8S_NODEPORT_HOST,
          String.valueOf(serviceNodePort),
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT,
          targets, clusterViewAppPath, null, domainUid + "clusterview");
      assertNotNull(result, "Application deployment failed");
      getLogger().info("Application deployment returned {0}", result.toString());
      return result.stdout().equals("202");
    }
  }

  /**
   * Verify headers in admin server log.
   * @param podName admin server pod name
   * @param namespace domain namespace
   */
  public static void verifyHeadersInAdminServerLog(String podName, String namespace) {
    getLogger().info("Getting admin server pod log from pod {0} in namespace {1}", podName, namespace);

    testUntil(
        () -> assertDoesNotThrow(() ->
            getPodLog(podName, namespace, "weblogic-server", null, 120)) != null,
        getLogger(),
        "Getting admin server pod log {0} in namespace {1}",
        podName,
        namespace);

    String adminServerPodLog0 = assertDoesNotThrow(() ->
        getPodLog(podName, namespace, "weblogic-server", null, 120));

    assertNotNull(adminServerPodLog0,
        String.format("failed to get admin server log from pod %s in namespace %s, returned null",
            podName, namespace));

    String adminServerPodLog = adminServerPodLog0.toLowerCase();

    // verify the admin server log does not contain WL-Proxy-Client-IP header
    getLogger().info("Checking that the admin server log does not contain 'WL-Proxy-Client-IP' header");
    assertFalse(adminServerPodLog.contains("WL-Proxy-Client-IP".toLowerCase()),
        String.format("found WL-Proxy-Client-IP in the admin server pod log, pod: %s; namespace: %s; pod log: %s",
            podName, namespace, adminServerPodLog0));

    // verify the admin server log does not contain header "WL-Proxy-SSL: false"
    getLogger().info("Checking that the admin server log does not contain header 'WL-Proxy-SSL: false'");
    assertFalse(adminServerPodLog.contains("WL-Proxy-SSL: false".toLowerCase()),
        String.format("found 'WL-Proxy-SSL: false' in the admin server pod log, pod: %s; namespace: %s; pod log: %s",
            podName, namespace, adminServerPodLog0));

    // verify the admin server log contains header "WL-Proxy-SSL: true"
    getLogger().info("Checking that the admin server log contains header 'WL-Proxy-SSL: true'");
    assertTrue(adminServerPodLog.contains("WL-Proxy-SSL: true".toLowerCase()),
        String.format(
            "Did not find 'WL-Proxy-SSL: true' in the admin server pod log, pod: %s; namespace: %s; pod log: %s",
            podName, namespace, adminServerPodLog0));
  }

  /**
   * Check whether the ingress is ready.
   * @param isHostRouting whether it is a host routing
   * @param ingressHost ingress host
   * @param isTLS whether the ingress is tls type
   * @param httpNodeport http nodeport
   * @param httpsNodeport https nodeport
   * @param pathString path string in path routing
   */
  public static void checkIngressReady(boolean isHostRouting, String ingressHost, boolean isTLS,
                                        int httpNodeport, int httpsNodeport, String pathString,
                                       String... ingressExtIP) {
    String host = ingressExtIP.length != 0 ? ingressExtIP[0] : K8S_NODEPORT_HOST;
    String hostAndPort;
    if (isTLS) {
      hostAndPort = getHostAndPort(host, httpsNodeport);
    } else {
      hostAndPort = getHostAndPort(host, httpNodeport);
    }
    getLogger().info("hostAndPort to check ingress ready is: {0}", hostAndPort);

    // check the ingress is ready to route the app to the server pod
    if (httpNodeport != 0 && httpsNodeport != 0) {
      String curlCmd;
      if (isHostRouting) {
        if (isTLS) {
          curlCmd = "curl -g -k --silent --show-error --noproxy '*' -H 'host: " + ingressHost
              + "' https://" + hostAndPort
              + "/weblogic/ready --write-out %{http_code} -o /dev/null";
        } else {
          curlCmd = "curl -g --silent --show-error --noproxy '*' -H 'host: " + ingressHost
              + "' http://" + hostAndPort
              + "/weblogic/ready --write-out %{http_code} -o /dev/null";
        }
      } else {
        if (isTLS) {
          curlCmd = "curl -g -k --silent --show-error --noproxy '*' https://" + hostAndPort
              + "/" + pathString + "/weblogic/ready --write-out %{http_code} -o /dev/null";
        } else {
          curlCmd = "curl -g --silent --show-error --noproxy '*' http://" + hostAndPort
              + "/" + pathString + "/weblogic/ready --write-out %{http_code} -o /dev/null";
        }
      }
      getLogger().info("Executing curl command {0}", curlCmd);
      assertTrue(callWebAppAndWaitTillReady(curlCmd, 60));
    }
  }

  /**
   * Verify cluster load balancing with ClusterViewServlet app.
   *
   * @param domainUid uid of the domain in which the cluster exists
   * @param ingressHostName ingress host name
   * @param protocol protocol used to test, accepted value: http or https
   * @param lbPort  load balancer service port
   * @param replicaCount replica count of the managed servers in the cluster
   * @param hostRouting whether it is a host base routing
   * @param locationString location string in apache configuration or path prefix in path routing
   */
  public static void verifyClusterLoadbalancing(String domainUid,
                                          String ingressHostName,
                                          String protocol,
                                          int lbPort,
                                          int replicaCount,
                                          boolean hostRouting,
                                          String locationString,
                                          String... args) {
    String host = K8S_NODEPORT_HOST;
    if (host.contains(":")) {
      host = "[" + host + "]";
    }
    String hostName = (args.length == 0) ? host : args[0];
    verifyClusterLoadbalancing(domainUid,
        ingressHostName,
        protocol,
        lbPort,
        replicaCount,
        hostRouting,
        locationString,
        hostName);
  }

  /**
   * Verify cluster load balancing with ClusterViewServlet app.
   *
   * @param domainUid uid of the domain in which the cluster exists
   * @param ingressHostName ingress host name
   * @param protocol protocol used to test, accepted value: http or https
   * @param lbPort  load balancer service port
   * @param replicaCount replica count of the managed servers in the cluster
   * @param hostRouting whether it is a host base routing
   * @param locationString location string in apache configuration or path prefix in path routing
   * @param host hostname
   */
  public static void verifyClusterLoadbalancing(String domainUid,
                                                String ingressHostName,
                                                String protocol,
                                                int lbPort,
                                                int replicaCount,
                                                boolean hostRouting,
                                                String locationString,
                                                String host) {
    // access application in managed servers through load balancer
    getLogger().info("Accessing the clusterview app through load balancer to verify all servers in cluster");
    String curlRequest;
    String uri = "clusterview/ClusterViewServlet" + "\"?user=" + ADMIN_USERNAME_DEFAULT
        + "&password=" + ADMIN_PASSWORD_DEFAULT
        + ((host != null) && host.contains(":") ? "&ipv6=true" : "&ipv6=false") + "\"";
    if (hostRouting) {
      curlRequest = OKE_CLUSTER_PRIVATEIP ? String.format("curl -g --show-error -ks --noproxy '*' "
          + "-H 'host: %s' %s://%s/" + uri, ingressHostName, protocol, host)
        : String.format("curl -g --show-error -ks --noproxy '*' "
          + "-H 'host: %s' %s://%s/" + uri, ingressHostName, protocol, getHostAndPort(host, lbPort));
    } else {
      curlRequest = OKE_CLUSTER_PRIVATEIP ? String.format("curl -g --show-error -ks --noproxy '*' "
          + "%s://%s" + locationString + "/" + uri, protocol, host)
        : String.format("curl -g --show-error -ks --noproxy '*' "
          + "%s://%s" + locationString + "/" + uri, protocol, getHostAndPort(host, lbPort));
    }

    List<String> managedServers = new ArrayList<>();
    for (int i = 1; i <= replicaCount; i++) {
      managedServers.add(MANAGED_SERVER_NAME_BASE + i);
    }

    // verify each managed server can see other member in the cluster
    verifyServerCommunication(curlRequest, managedServers);


    boolean containsCorrectDomainUid = false;
    getLogger().info("Verifying the requests are routed to correct domain and cluster");
    for (int i = 0; i < 10; i++) {
      ExecResult result;
      try {
        getLogger().info("executing curl command: {0}", curlRequest);
        result = ExecCommand.exec(curlRequest, true);
        String response = result.stdout().trim();
        getLogger().info("Response for iteration {0}: exitValue {1}, stdout {2}, stderr {3}",
            i, result.exitValue(), response, result.stderr());
        if (response.contains(domainUid)) {
          containsCorrectDomainUid = true;
          break;
        }
      } catch (IOException | InterruptedException ex) {
        // ignore
      }
    }
    assertTrue(containsCorrectDomainUid, "The request was not routed to the correct domain");
  }

  /**
   * Verify the admin server access.
   * @param isTLS whether is TLS
   * @param lbNodePort loadbalancer node port
   * @param isHostRouting whether it is host routing
   * @param ingressHostName ingress host name
   * @param pathLocation path location in the console url
   * @param hostName external IP address on OKE or k8s node IP address on non-OKE env
   */
  public static void verifyAdminServerAccess(boolean isTLS,
                                             int lbNodePort,
                                             boolean isHostRouting,
                                             String ingressHostName,
                                             String pathLocation,
                                             String hostName) {
    StringBuffer readyAppUrl = new StringBuffer();
    String hostAndPort = OKE_CLUSTER_PRIVATEIP ? hostName : getHostAndPort(hostName, lbNodePort);

    if (isTLS) {
      readyAppUrl.append("https://");
    } else {
      readyAppUrl.append("http://");
    }
    readyAppUrl.append(hostAndPort);
    if (!isHostRouting) {
      readyAppUrl.append(pathLocation);
    }

    readyAppUrl.append("/management/tenant-monitoring/servers/");
    String curlCmd;
    if (isHostRouting) {
      curlCmd = String.format("curl -g --user %s:%s -ks --show-error --noproxy '*' -H 'host: %s' %s",
          ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, ingressHostName, readyAppUrl.toString());
    } else {
      if (isTLS) {
        curlCmd = String.format("curl -g --user %s:%s -ks --show-error --noproxy '*' -H 'WL-Proxy-Client-IP: 1.2.3.4' "
            + "-H 'WL-Proxy-SSL: false' %s", ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, readyAppUrl.toString());
      } else {
        curlCmd = String.format("curl -g --user %s:%s -ks --show-error --noproxy '*' %s",
            ADMIN_USERNAME_DEFAULT, ADMIN_PASSWORD_DEFAULT, readyAppUrl.toString());
      }
    }

    boolean consoleAccessible = false;
    for (int i = 0; i < 10; i++) {
      assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(1));
      ExecResult result;
      try {
        getLogger().info("Accessing app on admin server using curl request, iteration {0}: {1}", i, curlCmd);
        result = ExecCommand.exec(curlCmd, true);
        String response = result.stdout().trim();
        getLogger().info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
            result.exitValue(), response, result.stderr());
        if (response.contains("RUNNING")) {
          consoleAccessible = true;
          break;
        }
      } catch (IOException | InterruptedException ex) {
        getLogger().severe(ex.getMessage());
      }
    }
    if (OKE_CLUSTER) {
      LoggingFacade logger = getLogger();
      try {
        if (!consoleAccessible) {
          ExecResult result = ExecCommand.exec(KUBERNETES_CLI + " get all -A");
          logger.info(result.stdout());
          //restart core-dns service
          result = ExecCommand.exec(KUBERNETES_CLI + " rollout restart deployment coredns -n kube-system");
          logger.info(result.stdout());
          checkPodReady("coredns", null, "kube-system");
        }
      } catch (Exception ex) {
        logger.warning(ex.getLocalizedMessage());
      }
      for (int i = 0; i < 10; i++) {
        assertDoesNotThrow(() -> TimeUnit.SECONDS.sleep(1));
        ExecResult result;
        try {
          getLogger().info("Accessing app on admin server using curl request, iteration {0}: {1}", i, curlCmd);
          result = ExecCommand.exec(curlCmd, true);
          String response = result.stdout().trim();
          getLogger().info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
              result.exitValue(), response, result.stderr());
          if (response.contains("RUNNING")) {
            consoleAccessible = true;
            break;
          }

          try {
            Thread.sleep(5000);
          } catch (InterruptedException ignore) {
            // ignore
          }
        } catch (IOException | InterruptedException ex) {
          getLogger().severe(ex.getMessage());
        }
      }
    }
    assertTrue(consoleAccessible, "Couldn't access admin server app");
  }
}

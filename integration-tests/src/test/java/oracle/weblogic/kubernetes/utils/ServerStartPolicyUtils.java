// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1SecretReference;
import oracle.weblogic.domain.AdminServer;
import oracle.weblogic.domain.AdminService;
import oracle.weblogic.domain.Channel;
import oracle.weblogic.domain.Cluster;
import oracle.weblogic.domain.Configuration;
import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainSpec;
import oracle.weblogic.domain.ManagedServer;
import oracle.weblogic.domain.Model;
import oracle.weblogic.domain.ServerPod;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_API_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ITTESTS_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.CommonMiiTestUtils.createDomainSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkClusterReplicaCountMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.ConfigMapUtils.createConfigMapAndVerify;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDeleted;
import static oracle.weblogic.kubernetes.utils.PodUtils.checkPodDoesNotExist;
import static oracle.weblogic.kubernetes.utils.PodUtils.getExternalServicePodName;
import static oracle.weblogic.kubernetes.utils.PodUtils.setPodAntiAffinity;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ServerStartPolicyUtils {

  public static final String SERVER_LIFECYCLE = "Server";
  public static final String CLUSTER_LIFECYCLE = "Cluster";
  public static final String DOMAIN = "DOMAIN";
  public static final String STOP_SERVER_SCRIPT = "stopServer.sh";
  public static final String START_SERVER_SCRIPT = "startServer.sh";
  public static final String STOP_CLUSTER_SCRIPT = "stopCluster.sh";
  public static final String START_CLUSTER_SCRIPT = "startCluster.sh";
  public static final String STOP_DOMAIN_SCRIPT = "stopDomain.sh";
  public static final String START_DOMAIN_SCRIPT = "startDomain.sh";
  public static final String SCALE_CLUSTER_SCRIPT = "scaleCluster.sh";
  public static final String STATUS_CLUSTER_SCRIPT = "clusterStatus.sh";
  public static final String ROLLING_DOMAIN_SCRIPT = "rollDomain.sh";
  public static final String ROLLING_CLUSTER_SCRIPT = "rollCluster.sh";
  public static final String managedServerNamePrefix = "managed-server";
  public static final String DYNAMIC_CLUSTER = "cluster-1";
  public static final String CONFIG_CLUSTER = "cluster-2";


  private static final int replicaCount = 1;

  private static LoggingFacade logger = getLogger();

  /**
   * Create operator and domain in specified namespaces, setup sample scripts directory.
   * @param domainNamespace - domain namespace
   * @param domainUid - domain uid
   * @param opNamespace - operator namespace
   * @param samplePath -name for samples script directory
   */
  public static void prepare(String domainNamespace, String domainUid, String opNamespace, String samplePath) {
    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);

    // create secret for admin credentials
    logger.info("Create secret for admin credentials");
    String adminSecretName = "weblogic-credentials";
    assertDoesNotThrow(() -> createDomainSecret(adminSecretName,ADMIN_USERNAME_DEFAULT,
        ADMIN_PASSWORD_DEFAULT, domainNamespace),
        String.format("createSecret failed for %s", adminSecretName));

    // create encryption secret
    logger.info("Create encryption secret");
    String encryptionSecretName = "encryptionsecret";
    assertDoesNotThrow(() -> createDomainSecret(encryptionSecretName, "weblogicenc",
        "weblogicenc", domainNamespace),
        String.format("createSecret failed for %s", encryptionSecretName));

    String configMapName = "wls-ext-configmap";
    createConfigMapAndVerify(
        configMapName, domainUid, domainNamespace,
        Collections.singletonList(MODEL_DIR + "/model.wls.ext.config.yaml"));

    // create the domain CR with a pre-defined configmap
    createDomainResource(domainNamespace, domainUid, adminSecretName,
        encryptionSecretName,
        configMapName);

    // wait for the domain to exist
    logger.info("Check for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);
    String adminServerPodName = domainUid + "-admin-server";
    logger.info("Check admin service/pod {0} is created in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName,
        domainUid, domainNamespace);
    //copy the samples directory to a temporary location
    setupSample(samplePath);
  }


  /**
   *  Scaling cluster util method.
   * @param domainUid - domain uid
   * @param domainNamespace - domain namespace
   * @param clusterName - cluster name
   * @param serverPodName -server pod name
   * @param replicaNum -number of servers to scale
   * @param regex - regex
   * @param checkPodExist - to check if pod exists
   * @param samplePathDir - name of sample script dir
   */
  public static void scalingClusters(String domainUid, String domainNamespace,
                                     String clusterName, String serverPodName, int replicaNum,
                               String regex, boolean checkPodExist, String samplePathDir) {
    // use scaleCluster.sh to scale a given cluster
    logger.info("Scale cluster {0} using the script scaleCluster.sh", clusterName);
    String result =  assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePathDir,
                SCALE_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, clusterName, " -r " + replicaNum, false),
        String.format("Failed to run %s", SCALE_CLUSTER_SCRIPT));

    if (checkPodExist) {
      checkPodReadyAndServiceExists(serverPodName, domainUid, domainNamespace);
    } else {
      checkPodDoesNotExist(serverPodName, domainUid, domainNamespace);
    }

    // verify that scaleCluster.sh does scale to a required replica number
    assertDoesNotThrow(() -> assertTrue(checkClusterReplicaCountMatches(clusterName,
        domainUid, domainNamespace, replicaNum)));

    // use clusterStatus.sh to verify scaling results
    testUntil(checkClusterStatus(domainUid, domainNamespace, samplePathDir,clusterName, regex), logger,
        "Checking for cluster status for cluster: " + clusterName);
    logger.info("The cluster {0} scaled successfully.", clusterName);
  }

  /**
   * Restore env to original.
   * @param domainUid - domain uid
   * @param domainNamespace - domain namespace
   * @param samplePathDir -name of sample script dir
   */
  public static void restoreEnv(String domainUid, String domainNamespace, String samplePathDir) {
    int newReplicaCount = 2;
    String configServerName = "config-cluster-server" + newReplicaCount;
    String configServerPodName = domainUid + "-" + configServerName;
    String dynamicServerName = "managed-server" + newReplicaCount;
    String dynamicServerPodName = domainUid + "-" + dynamicServerName;

    // restore test env
    assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePathDir,
                STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, configServerName),
        String.format("Failed to run %s", STOP_SERVER_SCRIPT));
    checkPodDeleted(configServerPodName, domainUid, domainNamespace);
    logger.info("managed server " + configServerPodName + " stopped successfully.");

    assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePathDir,
                STOP_SERVER_SCRIPT, SERVER_LIFECYCLE, dynamicServerName),
        String.format("Failed to run %s", STOP_SERVER_SCRIPT));
    checkPodDeleted(dynamicServerPodName, domainUid, domainNamespace);
    logger.info("managed server " + dynamicServerPodName + " stopped successfully.");
  }

  /** Create domain resource.
   * @param domNamespace - domain namespace
   * @param domainUid -domain uid
   * @param adminSecretName - adminserver secret name
   * @param encryptionSecretName - encryption secret name
   * @param configmapName - config map name
   */
  public static void createDomainResource(
      String domNamespace, String domainUid, String adminSecretName,
      String encryptionSecretName,
      String configmapName) {
    List<String> securityList = new ArrayList<>();
    // create the domain CR
    Domain domain = new Domain()
        .apiVersion(DOMAIN_API_VERSION)
        .kind("Domain")
        .metadata(new V1ObjectMeta()
            .name(domainUid)
            .namespace(domNamespace))
        .spec(new DomainSpec()
            .allowReplicasBelowMinDynClusterSize(false)
            .domainUid(domainUid)
            .domainHomeSourceType("FromModel")
            .image(MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG)
            .addImagePullSecretsItem(new V1LocalObjectReference()
                .name(OCIR_SECRET_NAME))
            .webLogicCredentialsSecret(new V1SecretReference()
                .name(adminSecretName)
                .namespace(domNamespace))
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
                        .nodePort(0))))
            .addClustersItem(new Cluster()
                .clusterName(DYNAMIC_CLUSTER)
                .replicas(replicaCount)
                .serverStartPolicy("IF_NEEDED")
                .serverStartState("RUNNING"))
            .addClustersItem(new Cluster()
                .clusterName(CONFIG_CLUSTER)
                .replicas(replicaCount)
                .serverStartPolicy("IF_NEEDED")
                .serverStartState("RUNNING"))
            .addManagedServersItem(new ManagedServer()
                .serverName("standalone-managed")
                .serverStartPolicy("IF_NEEDED")
                .serverStartState("RUNNING"))
            .addManagedServersItem(new ManagedServer()
                .serverName("config-cluster-server2")
                .serverStartPolicy("IF_NEEDED")
                .serverStartState("RUNNING"))
            .addManagedServersItem(new ManagedServer()
                .serverName("managed-server2")
                .serverStartPolicy("IF_NEEDED")
                .serverStartState("RUNNING"))
            .addManagedServersItem(new ManagedServer()
                .serverName("config-cluster-server1")
                .serverStartPolicy("IF_NEEDED")
                .serverStartState("RUNNING"))
            .addManagedServersItem(new ManagedServer()
                .serverName("managed-server1")
                .serverStartState("RUNNING"))
            .configuration(new Configuration()
                .secrets(securityList)
                .model(new Model()
                    .domainType("WLS")
                    .configMap(configmapName)
                    .runtimeEncryptionSecret(encryptionSecretName))
                .introspectorJobActiveDeadlineSeconds(300L)));

    setPodAntiAffinity(domain);

    logger.info("Create domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domNamespace);
    boolean domCreated = assertDoesNotThrow(() -> createDomainCustomResource(domain),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domNamespace));
    assertTrue(domCreated, String.format("Create domain custom resource failed with ApiException "
        + "for %s in namespace %s", domainUid, domNamespace));
  }

  /**
   * Verify the server MBEAN configuration through rest API.
   * @param managedServer name of the managed server
   * @returns true if MBEAN is found otherwise false
   **/
  public static boolean checkManagedServerConfiguration(String ingressHost, String managedServer,
                                                        String domainNamespace, String adminServerPodName) {
    ExecResult result;
    int adminServiceNodePort
        = getServiceNodePort(domainNamespace, getExternalServicePodName(adminServerPodName), "default");
    String url = getHostAndPort(ingressHost, adminServiceNodePort);
    logger.info("url = {0}", url);
    StringBuffer checkCluster = new StringBuffer("status=$(curl --user weblogic:welcome1 ");
    checkCluster.append("http://" + url)
        .append("/management/tenant-monitoring/servers/")
        .append(managedServer)
        .append(" --silent --show-error ")
        .append(" -o /dev/null")
        .append(" -w %{http_code});")
        .append("echo ${status}");
    logger.info("checkManagedServerConfiguration: curl command {0}",
        new String(checkCluster));
    try {
      result = exec(new String(checkCluster), true);
    } catch (Exception ex) {
      logger.info("Exception in checkManagedServerConfiguration() {0}", ex);
      return false;
    }
    logger.info("checkManagedServerConfiguration: curl command returned {0}", result.toString());
    return result.stdout().equals("200");
  }

  /**
   * copy samples directory to a temporary location.
   * @param samplePathDir - name of sample script dir
   */
  public static void setupSample(String samplePathDir) {
    Path samplePath = Paths.get(ITTESTS_DIR, "../kubernetes/samples");
    Path tempSamplePath = Paths.get(WORK_DIR, samplePathDir);
    assertDoesNotThrow(() -> {
      logger.info("Deleting and recreating {0}", tempSamplePath);
      deleteDirectory(tempSamplePath.toFile());
      Files.createDirectories(tempSamplePath);
      logger.info("Copying {0} to {1}", samplePath, tempSamplePath);
      copyDirectory(samplePath.toFile(), tempSamplePath.toFile());
    });
  }

  /**
   * Function to execute domain lifecyle scripts.
   * @param domainUid - domain uid
   * @param domainNamespace - domain namespace
   * @param samplePathDir - name of sample script dir
   * @param script - script name
   * @param scriptType -script type
   * @param entityName - entity name
   * @return status
   */
  public static String executeLifecycleScript(String domainUid, String domainNamespace, String samplePathDir,
                                              String script, String scriptType, String entityName) {
    return executeLifecycleScript(domainUid, domainNamespace, samplePathDir,
        script, scriptType, entityName, "");
  }

  /**
   * Function to execute domain lifecyle scripts.
   * @param domainUid - domain uid
   * @param domainNamespace - domain namespace
   * @param samplePathDir - name of sample script dir
   * @param script - script name
   * @param scriptType -script type
   * @param entityName entity name
   * @param extraParams - extra params
   * @return result
   */
  public static String executeLifecycleScript(String domainUid, String domainNamespace,
                                              String samplePathDir, String script,
                                              String scriptType, String entityName, String extraParams) {
    return executeLifecycleScript(domainUid, domainNamespace, samplePathDir,
        script, scriptType, entityName, extraParams, true);
  }

  /**
   * Function to execute domain lifecyle scripts.
   * @param domainUid - domain uid
   * @param domainNamespace - domain namespace
   * @param samplePathDir - name of sample script dir
   * @param script - script name
   * @param scriptType -script type
   * @param entityName - entity name
   * @param extraParams -extra params
   * @param checkResult -specify if need to check result
   * @param args - extra args
   * @return result
   */
  public static String executeLifecycleScript(String domainUid, String domainNamespace, String samplePathDir,
                                        String script,
                                        String scriptType,
                                        String entityName,
                                        String extraParams,
                                        boolean checkResult,
                                        String... args) {
    String domainName = (args.length == 0) ? domainUid : args[0];

    CommandParams params;
    Path tempSamplePath = Paths.get(WORK_DIR, samplePathDir);
    Path domainLifecycleSamplePath = Paths.get(tempSamplePath + "/scripts/domain-lifecycle");
    String commonParameters = " -d " + domainName + " -n " + domainNamespace;
    params = new CommandParams().defaults();
    if (scriptType.equals(SERVER_LIFECYCLE)) {
      params.command("sh "
          + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
          + commonParameters + " -s " + entityName + " " + extraParams);
    } else if (scriptType.equals(CLUSTER_LIFECYCLE)) {
      if (extraParams.contains("-r")) {
        commonParameters += " " + extraParams;
      }

      params.command("sh "
          + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
          + commonParameters + " -c " + entityName);
    } else {
      params.command("sh "
          + Paths.get(domainLifecycleSamplePath.toString(), "/" + script).toString()
          + commonParameters);
    }

    ExecResult execResult = Command.withParams(params).executeAndReturnResult();
    if (checkResult) {
      if (execResult.stdout() != null) {
        logger.info("stdout:\n{0}", execResult.stdout());
      }
      if (execResult.stderr() != null) {
        logger.info("stderr:\n{0}", execResult.stderr());
      }
      assertEquals(0, execResult.exitValue(),
          String.format("Failed to execute script  %s ", script));
    }
    return execResult.toString();
  }

  /**
   *  Verify result.
   * @param result - result object
   * @param regex - check string
   * @return true or false
   */
  public static boolean verifyExecuteResult(String result, String regex) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(result);

    return matcher.find();
  }

  private static Callable<Boolean> checkClusterStatus(String domainUid, String domainNamespace,
                                               String samplePathDir, String clusterName,
                                               String regex) {
    // use clusterStatus.sh to verify scaling results
    String result = assertDoesNotThrow(() ->
            executeLifecycleScript(domainUid, domainNamespace, samplePathDir,
                STATUS_CLUSTER_SCRIPT, CLUSTER_LIFECYCLE, clusterName),
        String.format("Failed to run %s", STATUS_CLUSTER_SCRIPT));
    logger.info("Status of cluster {0} retured {1}, expected {2}", clusterName, result, regex);
    return () -> verifyExecuteResult(result, regex);
  }

}

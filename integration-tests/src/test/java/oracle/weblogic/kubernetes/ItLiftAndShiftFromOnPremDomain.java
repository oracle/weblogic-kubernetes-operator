// Copyright (c) 2021, 2024, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.exception.CopyNotSupportedException;
import oracle.weblogic.kubernetes.actions.impl.AppParams;
import oracle.weblogic.kubernetes.actions.impl.Exec;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.ADMIN_PASSWORD_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.ADMIN_USERNAME_DEFAULT;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.TestConstants.HTTPS_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.HTTP_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.NO_PROXY;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPDEMO;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_HOSTPORT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.impl.Service.getServiceNodePort;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.utils.ApplicationUtils.checkAppIsRunning;
import static oracle.weblogic.kubernetes.utils.BuildApplication.setupWebLogicPod;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createIngressHostRouting;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.formatIPv6Host;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getActualLocationIfNeeded;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getHostAndPort;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getServiceExtIPAddrtOke;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withQuickRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ExecCommand.exec;
import static oracle.weblogic.kubernetes.utils.FileUtils.copy;
import static oracle.weblogic.kubernetes.utils.FileUtils.copyFolder;
import static oracle.weblogic.kubernetes.utils.FileUtils.createZipFile;
import static oracle.weblogic.kubernetes.utils.FileUtils.replaceStringInFile;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createImageAndVerify;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.apache.commons.io.FileUtils.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test to verify that wko managed weblogic domain can be created with the domain resource generated by
 * running WDT's discoverDomain tool on an on-prem domain.
 * The config files from an on-prem domain can be found in the resources directory.
 * Since WDT discoverDomain tool requires weblogic binaries, we run the discoverDomain tool inside
 * of a weblogic pod and copy the files generated to workdir.
 * There are a few variables (example imageName) that needs to fixed in the domain resource before
 * running KUBERNETES_CLI + " apply" the domain resource file.
 */

@DisplayName("Test to validate on-prem to k8s use case")
@Tag("kind-parallel")
@Tag("toolkits-srg")
@Tag("okd-wls-mrg")
@Tag("olcne-mrg")
@Tag("oke-sequential")

@IntegrationTest
@Disabled
class ItLiftAndShiftFromOnPremDomain {
  private static String traefikNamespace = null;
  private static String domainNamespace = null;
  private static final String LIFT_AND_SHIFT_WORK_DIR = WORK_DIR + "/liftandshiftworkdir";
  private static final String ON_PREM_DOMAIN = "onpremdomain";
  private static final String DISCOVER_DOMAIN_OUTPUT_DIR = "wkomodelfilesdir";
  private static final String DOMAIN_TEMP_DIR = LIFT_AND_SHIFT_WORK_DIR + "/" + ON_PREM_DOMAIN;
  private static final String DOMAIN_SRC_DIR = RESOURCE_DIR + "/" + ON_PREM_DOMAIN;
  private static final String WKO_IMAGE_FILES = LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR;
  private static final String WKO_IMAGE_NAME = "onprem_to_wko_image";
  private static final String WKO_DOMAIN_YAML = "wko-domain.yaml";
  private static final String BUILD_SCRIPT = "discover_domain.sh";
  private static final Path BUILD_SCRIPT_SOURCE_PATH = Paths.get(RESOURCE_DIR, "bash-scripts", BUILD_SCRIPT);
  private static final String domainUid = "onprem-domain";
  private static final String adminServerName = "admin-server";
  private static String imageName = null;
  private static LoggingFacade logger = null;

  private static HelmParams traefikHelmParams = null;
  private int traefikNodePort = 0;
  private String hostName = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *                   JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(3) List<String> namespaces) {
    logger = getLogger();

    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    String opNamespace = namespaces.get(0);

    // get a unique traefik namespace
    logger.info("Get a unique namespace for traefik");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    traefikNamespace = namespaces.get(1);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    domainNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    if (!OKD && !(TestConstants.KIND_CLUSTER
        && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT))) {
      // install and verify Traefik
      logger.info("Installing Traefik controller using helm");
      traefikHelmParams = installAndVerifyTraefik(traefikNamespace, 0, 0).getHelmParams();
    }

  }

  /**
   * Create a MiiDomain from an on prem domain.
   * This test first uses WDT DiscoverDomain tool on an on-prem domain. This tool when used with
   * target option wko, will create the necessary wdt model file, properties file, an archive file
   * and a domain yaml file. The test then use the resulting model file to create an MiiDomain
   */
  @Test
  @DisplayName("Create model in image domain and verify external admin services")
  void testCreateMiiDomainWithClusterFromOnPremDomain() throws UnknownHostException {
    // admin/managed server name here should match with model yaml in MII_BASIC_WDT_MODEL_FILE
    final String adminServerPodName = domainUid + "-" + adminServerName;
    final String managedServerPrefix = domainUid + "-managed-server";
    final String clusterService = domainUid + "-cluster-cluster-1";
    final int replicaCount = 5;

    assertDoesNotThrow(() -> {
      logger.info("Deleting and recreating {0}", LIFT_AND_SHIFT_WORK_DIR);
      deleteDirectory(Paths.get(LIFT_AND_SHIFT_WORK_DIR).toFile());
      Files.createDirectories(Paths.get(LIFT_AND_SHIFT_WORK_DIR));
    });

    // Copy the on-prem domain files to a temporary directory
    try {
      copyFolder(DOMAIN_SRC_DIR, DOMAIN_TEMP_DIR);
    } catch (IOException  ioex) {
      logger.info("Exception while copying domain files from " + DOMAIN_SRC_DIR + " to " + DOMAIN_TEMP_DIR, ioex);
    }

    // We need to build the app so that we can pass it into the weblogic pod along with the config files,
    // so that wdt discoverDomain could be run.
    List<String> appDirList = Collections.singletonList("onprem-app");

    logger.info("Build the application archive using what is in {0}", appDirList);
    AppParams appParams = defaultAppParams()
        .appArchiveDir(ARCHIVE_DIR + this.getClass().getSimpleName());
    assertTrue(
        buildAppArchive(
            appParams
                .srcDirList(appDirList)
                .appName("opdemo")),
        String.format("Failed to create application archive for %s",
            "opdemo"));

    //copy file from stage dir to where the config files are
    try {
      copy(Paths.get(appParams.appArchiveDir(), "/wlsdeploy/applications/opdemo.ear"),
          Paths.get(DOMAIN_TEMP_DIR, "/opdemo.ear"));
    } catch (IOException ioex) {
      logger.info("Copy of the application to the domain directory failed");
    }

    Path tempDomainDir = Paths.get(DOMAIN_TEMP_DIR);
    String tmpDomainDirZip = createZipFile(tempDomainDir);
    assertNotNull(tmpDomainDirZip, String.format("failed to create zip file %s", DOMAIN_TEMP_DIR));
    Path zipFile = Paths.get(tmpDomainDirZip);
    logger.info("zipfile is in {0}", zipFile.toString());

    // Call WDT DiscoverDomain tool with wko target to get the required file to create a
    // Mii domain image. Since WDT requires weblogic installation, we start a pod and run
    // wdt discoverDomain tool in the pod
    V1Pod webLogicPod = callSetupWebLogicPod(domainNamespace);
    assertNotNull(webLogicPod, "webLogicPod is null");
    assertNotNull(webLogicPod.getMetadata(), "webLogicPod metadata is null");

    // copy the onprem domain zip file to /u01 location inside pod
    try {
      Kubernetes.copyFileToPod(domainNamespace, webLogicPod.getMetadata().getName(),
          null, zipFile, Paths.get("/u01/", zipFile.getFileName().toString()));
    } catch (ApiException | IOException ioex) {
      logger.info("Exception while copying file " + zipFile + " to pod", ioex);
    }

    //copy the build script discover_domain.sh to /u01 location inside pod
    try {
      Kubernetes.copyFileToPod(domainNamespace, webLogicPod.getMetadata().getName(),
          null, BUILD_SCRIPT_SOURCE_PATH, Paths.get("/u01", BUILD_SCRIPT));
    } catch (ApiException | IOException  ioex) {
      logger.info("Exception while copying file " + zipFile + " to pod", ioex);
    }
    logger.info(KUBERNETES_CLI + " copied " + BUILD_SCRIPT + " into the pod");

    // Check that all the required files have been copied into the pod
    try {
      ExecResult ex = Exec.exec(webLogicPod, null, false, "/bin/ls", "-ls", "/u01");
      if (ex.stdout() != null) {
        logger.info("Exec stdout {0}", ex.stdout());
      }
      if (ex.stderr() != null) {
        logger.info("Exec stderr {0}", ex.stderr());
      }
    } catch (ApiException | IOException | InterruptedException ioex) {
      logger.info("Exception while listing the files in /u01", ioex);
    }

    // Run the discover_domain.sh script in the pod
    try {
      ExecResult exec = Exec.exec(webLogicPod, null, false, "/bin/sh", "/u01/" + BUILD_SCRIPT);
      if (exec.stdout() != null) {
        logger.info("Exec stdout {0}", exec.stdout());
      }
      if (exec.stderr() != null) {
        logger.info("Exec stderr {0}", exec.stderr());
      }

      // WDT discoverDomain tool creates a model file, a variable file, domain.yaml and script that creates the secrets.
      // Copy the directory that contains the files to workdir
      Kubernetes.copyDirectoryFromPod(webLogicPod,
          Paths.get("/u01", DISCOVER_DOMAIN_OUTPUT_DIR).toString(), Paths.get(LIFT_AND_SHIFT_WORK_DIR));
    } catch (ApiException | IOException | InterruptedException | CopyNotSupportedException ioex) {
      logger.info("Exception while copying file "
          + Paths.get("/u01", DISCOVER_DOMAIN_OUTPUT_DIR) + " from pod", ioex);
    }

    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);

    //create a MII image
    imageName = createImageAndVerify(WKO_IMAGE_NAME, Collections.singletonList(WKO_IMAGE_FILES + "/onpremdomain.yaml"),
        Collections.singletonList(DOMAIN_TEMP_DIR + "/opdemo.ear"),
        Collections.singletonList(WKO_IMAGE_FILES + "/onpremdomain.properties"),
        WEBLOGIC_IMAGE_NAME, WEBLOGIC_IMAGE_TAG, "WLS", true,
        "onpremdomain", false);

    // repo login and push image to registry if necessary
    logger.info("Push the image {0} to image repo", imageName);
    imageRepoLoginAndPushImageToRegistry(imageName);

    // Namespace and password needs to be updated in Create_k8s_secrets.sh
    updateCreateSecretsFile();

    // Namespace, domainHome, domainHomeSourceType, imageName and modelHome needs to be updated in wko-domain.yaml
    updateDomainYamlFile();

    // run create_k8s_secrets.sh that discoverDomain created to create necessary secrets
    CommandParams params = new CommandParams().defaults();
    params.command("sh "
        + Paths.get(LIFT_AND_SHIFT_WORK_DIR, "/u01/", DISCOVER_DOMAIN_OUTPUT_DIR, "/create_k8s_secrets.sh").toString());

    logger.info("Run create_k8s_secrets.sh to create secrets");
    boolean result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create secrets");

    logger.info("Run " + KUBERNETES_CLI + " to create the domain");
    params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " apply -f "
        + Paths.get(LIFT_AND_SHIFT_WORK_DIR, "/u01/", DISCOVER_DOMAIN_OUTPUT_DIR + "/" + WKO_DOMAIN_YAML).toString());

    result = Command.withParams(params).execute();
    assertTrue(result, "Failed to create domain custom resource");

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, DOMAIN_VERSION, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);

    // verify the admin server service and pod is created
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    // verify managed server services created and pods are ready
    for (int i = 1; i <= replicaCount; i++) {
      checkPodReadyAndServiceExists(managedServerPrefix + i, domainUid, domainNamespace);
    }

    String hostHeader = "";
    if (OKD) {
      hostName = createRouteForOKD(clusterService, domainNamespace);
    } else {
      // create ingress rules with path routing for Traefik
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostHeader = createIngressHostRouting(domainNamespace, domainUid, "cluster-cluster-1", 8001);
        hostHeader = " --header 'Host: " + hostHeader + "'";
      } else {
        createTraefikIngressRoutingRules(domainNamespace);
        traefikNodePort = getServiceNodePort(traefikNamespace, traefikHelmParams.getReleaseName(), "web");
        assertNotEquals(-1, traefikNodePort,
            "Could not get the default external service node port");
        logger.info("Found the Traefik service nodePort {0}", traefikNodePort);
        logger.info("The K8S_NODEPORT_HOST is {0}", K8S_NODEPORT_HOST);
      }
    }

    String hostAndPort;
    if (OKD) {
      hostAndPort = getHostAndPort(hostName, traefikNodePort);
    } else {
      if (TestConstants.KIND_CLUSTER
          && !TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT)) {
        hostAndPort = formatIPv6Host(InetAddress.getLocalHost().getHostAddress()) + ":" + TRAEFIK_INGRESS_HTTP_HOSTPORT;
      } else {
        final String ingressServiceName = traefikHelmParams.getReleaseName();
        hostAndPort = getServiceExtIPAddrtOke(ingressServiceName, traefikNamespace) != null
            ? getServiceExtIPAddrtOke(ingressServiceName, traefikNamespace) : getHostAndPort(hostName, traefikNodePort);
      }
    }
    logger.info("hostAndPort = {0} ", hostAndPort);

    String curlString = String.format("curl -v --show-error --noproxy '*' %s "
            + "http://%s/opdemo/?dsName=testDatasource", hostHeader, hostAndPort);

    // check and wait for the application to be accessible in admin pod
    checkAppIsRunning(
        withQuickRetryPolicy,
        domainNamespace,
        adminServerPodName,
        "7001",
        "opdemo/index.jsp",
        "WebLogic on prem to wko App");

    ExecResult execResult;
    logger.info("curl command {0}", curlString);

    execResult = assertDoesNotThrow(
        () -> exec(curlString, true));

    if (execResult.exitValue() == 0) {
      logger.info("\n HTTP response is \n " + execResult.stdout());
      logger.info("curl command returned {0}", execResult.toString());
      assertTrue(execResult.stdout().contains("WebLogic on prem to wko App"),
          "Not able to access the application");
    } else {
      fail("HTTP request to the app failed" + execResult.stderr());
    }
  }

  private static V1Pod callSetupWebLogicPod(String namespace) {
    getLogger().info("The input WDT_DOWNLOAD_URL is: {0}", WDT_DOWNLOAD_URL);
    String wdtDownloadurl = getActualLocationIfNeeded(WDT_DOWNLOAD_URL, WDT);
    getLogger().info("The actual download location for lifeAndShift is {0}", wdtDownloadurl);
    // create a V1Container with specific scripts and properties for creating domain
    V1Container container = new V1Container()
        .addEnvItem(new V1EnvVar()
            .name("WDT_INSTALL_ZIP_URL")
            .value(wdtDownloadurl))
        .addEnvItem(new V1EnvVar()
            .name("DOMAIN_SRC")
            .value("onpremdomain"))
        .addEnvItem(new V1EnvVar()
            .name("DISCOVER_DOMAIN_OUTPUT_DIR")
            .value(DISCOVER_DOMAIN_OUTPUT_DIR))
        .addEnvItem(new V1EnvVar()
            .name("APP")
            .value(OPDEMO));

    if (HTTP_PROXY != null) {
      container.addEnvItem(new V1EnvVar().name("http_proxy").value(HTTP_PROXY));
    }
    if (HTTPS_PROXY != null) {
      container.addEnvItem(new V1EnvVar().name("https_proxy").value(HTTPS_PROXY));
    }
    if (NO_PROXY != null) {
      container.addEnvItem(new V1EnvVar().name("no_proxy").value(NO_PROXY));
    }

    // create secret for internal OKE cluster
    if (OKE_CLUSTER) {
      createBaseRepoSecret(namespace);
    }

    return setupWebLogicPod(namespace, container);
  }

  private static void updateCreateSecretsFile() {
    try {
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/create_k8s_secrets.sh",
          "NAMESPACE=onprem-domain", "NAMESPACE=" + domainNamespace);
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/create_k8s_secrets.sh",
          "weblogic-credentials \"<user>\" <password>", "weblogic-credentials " + ADMIN_USERNAME_DEFAULT
              + " " + ADMIN_PASSWORD_DEFAULT);
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/create_k8s_secrets.sh",
          "\"scott\" <password>", "scott tiger");
      replaceStringInFile(LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/create_k8s_secrets.sh",
          "runtime-encryption-secret <password>", "runtime-encryption-secret welcome1");
    } catch (IOException ioex) {
      logger.info("Exception while replacing user password in the script file");
    }
  }

  private static void updateDomainYamlFile() {
    try {
      String filePath = LIFT_AND_SHIFT_WORK_DIR + "/u01/" + DISCOVER_DOMAIN_OUTPUT_DIR + "/" + WKO_DOMAIN_YAML;

      replaceStringInFile(filePath,
          "namespace: onprem-domain", "namespace: " + domainNamespace);
      replaceStringInFile(filePath,
          "\\{\\{\\{domainHome\\}\\}\\}", "/u01/" + domainNamespace + "/domains/" + domainUid);
      replaceStringInFile(filePath,
          "\\{\\{\\{domainHomeSourceType\\}\\}\\}", "FromModel");
      replaceStringInFile(filePath,
          "\\{\\{\\{imageName\\}\\}\\}", imageName);
      replaceStringInFile(filePath,
          "imagePullSecrets: \\[\\]", "imagePullSecrets:\n    - name: " + TEST_IMAGES_REPO_SECRET_NAME);
      replaceStringInFile(filePath,
          "\\{\\{\\{modelHome\\}\\}\\}", "/u01/wdt/models");
      replaceStringInFile(filePath,
          "# replicas: 99", "replicas: 5");
    } catch (IOException ioex) {
      logger.info("Exception while replacing user password in the script file");
    }
  }

  private static void createTraefikIngressRoutingRules(String domainNamespace) {
    logger.info("Creating ingress rules for domain traffic routing");
    Path srcFile = Paths.get(RESOURCE_DIR, "traefik/traefik-ingress-rules-onprem.yaml");
    Path dstFile = Paths.get(RESULTS_ROOT, "traefik/traefik-ingress-rules-onprem.yaml");
    
    assertDoesNotThrow(() -> {
      Files.deleteIfExists(dstFile);
      Files.createDirectories(dstFile.getParent());
      Files.write(dstFile, Files.readString(srcFile).replaceAll("@NS@", domainNamespace)
          .replaceAll("@domainuid@", domainUid)
          .getBytes(StandardCharsets.UTF_8));
    });
    String command = KUBERNETES_CLI + " create -f " + dstFile;
    logger.info("Running {0}", command);
    ExecResult result;
    try {
      result = ExecCommand.exec(command, true);
      String response = result.stdout().trim();
      logger.info("exitCode: {0}, \nstdout: {1}, \nstderr: {2}",
          result.exitValue(), response, result.stderr());
      assertEquals(0, result.exitValue(), "Command didn't succeed");
    } catch (IOException | InterruptedException ex) {
      logger.severe(ex.getMessage());
    }
  }


}



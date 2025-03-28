// Copyright (c) 2020, 2025, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.Namespace;
import oracle.weblogic.kubernetes.actions.impl.Operator;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecCommand;
import oracle.weblogic.kubernetes.utils.ExecResult;
import oracle.weblogic.kubernetes.utils.PortInuseEventWatcher;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.TestConstants.ARM;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_PASSWORD;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_USERNAME;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_TENANCY;
import static oracle.weblogic.kubernetes.TestConstants.CERT_MANAGER;
import static oracle.weblogic.kubernetes.TestConstants.CRIO;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.INGRESS_CLASS_FILE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.INSTALL_WEBLOGIC;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.TestConstants.LOCALE_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.LOCALE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_DOMAINTYPE;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_WDT_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.OCNE;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_CHART_DIR;
import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.ORACLE_OPERATOR_NS;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_BUILD_IMAGES_IF_EXISTS;
import static oracle.weblogic.kubernetes.TestConstants.SKIP_CLEANUP;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTPS_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_INGRESS_HTTP_NODEPORT;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_NAMESPACE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_APP_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINHOME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_DOMAINTYPE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WDT_BASIC_MODEL_PROPERTIES_FILE;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SHIPHOME;
import static oracle.weblogic.kubernetes.TestConstants.WLSIMG_BUILDER;
import static oracle.weblogic.kubernetes.actions.ActionConstants.ARCHIVE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.DOWNLOAD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.MODEL_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.RESOURCE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.STAGE_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_VERSION;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_BUILD_DIR;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WORK_DIR;
import static oracle.weblogic.kubernetes.actions.TestActions.buildAppArchive;
import static oracle.weblogic.kubernetes.actions.TestActions.createImage;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultAppParams;
import static oracle.weblogic.kubernetes.actions.TestActions.defaultWitParams;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteImage;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePull;
import static oracle.weblogic.kubernetes.actions.TestActions.imagePush;
import static oracle.weblogic.kubernetes.actions.TestActions.imageRepoLogin;
import static oracle.weblogic.kubernetes.actions.TestActions.imageTag;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallOperator;
import static oracle.weblogic.kubernetes.actions.impl.primitive.Command.defaultCommandParams;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.imageExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.DbUtils.installDBOperator;
import static oracle.weblogic.kubernetes.utils.FileUtils.checkDirectory;
import static oracle.weblogic.kubernetes.utils.FileUtils.cleanupDirectory;
import static oracle.weblogic.kubernetes.utils.IstioUtils.installIstio;
import static oracle.weblogic.kubernetes.utils.IstioUtils.uninstallIstio;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.deleteLoadBalancer;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.installAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

/**
 * Class to build the required images for the tests.
 */
public class InitializationTasks implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {
  private static final AtomicBoolean started = new AtomicBoolean(false);
  private static final CountDownLatch initializationLatch = new CountDownLatch(1);
  private static String operatorImage;
  private static String miiBasicImage;
  private static String wdtBasicImage;

  private static Collection<String> pushedImages = new ArrayList<>();
  private static Set<String> lbIPs = new HashSet<>();
  private static boolean isInitializationSuccessful = false;

  ConditionFactory withVeryLongRetryPolicy
      = with().pollDelay(0, SECONDS)
      .and().with().pollInterval(10, SECONDS)
      .atMost(30, MINUTES).await();

  PortInuseEventWatcher portInuseEventWatcher;

  @Override
  public void beforeAll(ExtensionContext context) {
    LoggingFacade logger = getLogger();

    /* The pattern is that we have initialization code that we want to run once to completion
     * before any tests are executed. This method will be called before every test method. Therefore, the
     * very first time this method is called we will do the initialization. Since we assume that the tests
     * will be run concurrently in many threads, we need a guard to ensure that only the first thread arriving
     * attempts to do the initialization *and* that any other threads *wait* for that initialization to complete
     * before running their tests.
     */
    if (!started.getAndSet(true)) {
      try {
        portInuseEventWatcher = new PortInuseEventWatcher();
        portInuseEventWatcher.start();
        // clean up the download directory so that we always get the latest
        // versions of the WDT and WIT tools in every run of the test suite.
        try {
          cleanupDirectory(DOWNLOAD_DIR);
        } catch (IOException ioe) {
          logger.severe("Failed to cleanup the download directory " + DOWNLOAD_DIR, ioe);
        }

        //Install cert-manager for Database installation through DB operator
        String certManager = CERT_MANAGER;
        CommandParams params = new CommandParams().defaults();
        params.command(KUBERNETES_CLI + " apply -f " + certManager);
        boolean response = Command.withParams(params).execute();
        assertTrue(response, "Failed to install cert manager");

        // Only the first thread will enter this block.
        logger.info("Building Images before any integration test classes are run");
        context.getRoot().getStore(GLOBAL).put("BuildSetup", this);

        // build the operator image
        operatorImage = Operator.getImageName();
        logger.info("Operator image name {0}", operatorImage);
        assertFalse(operatorImage.isEmpty(), "Image name can not be empty");
        if (!ARM) {
          assertTrue(Operator.buildImage(operatorImage), "image build failed for Operator");
        }

        // login to BASE_IMAGES_REPO 
        logger.info(WLSIMG_BUILDER + " login to BASE_IMAGES_REPO {0}", BASE_IMAGES_REPO);
        testUntil(withVeryLongRetryPolicy,
                () -> imageRepoLogin(BASE_IMAGES_REPO, BASE_IMAGES_REPO_USERNAME, BASE_IMAGES_REPO_PASSWORD),
                logger,
                WLSIMG_BUILDER + " login to BASE_IMAGES_REPO to be successful");
        // The following code is for pulling WLS images if running tests in Kind cluster
        if (KIND_REPO != null) {
          // The kind clusters can't pull images from BASE_IMAGES_REPO using the image pull secret.
          // It may be a containerd bug. We are going to workaround this issue.
          // The workaround will be to:
          //   1. WLSIMG_BUILDER login
          //   2. WLSIMG_BUILDER pull
          //   3. WLSIMG_BUILDER tag with the KIND_REPO value
          //   4. WLSIMG_BUILDER push this new image name
          //   5. use this image name to create the domain resource
          Collection<String> images = new ArrayList<>();

          images.add(WEBLOGIC_IMAGE_NAME + ":" + WEBLOGIC_IMAGE_TAG);
          images.add(FMWINFRA_IMAGE_NAME + ":" + FMWINFRA_IMAGE_TAG);
          images.add(DB_IMAGE_NAME + ":" + DB_IMAGE_TAG);
          images.add(LOCALE_IMAGE_NAME + ":" + LOCALE_IMAGE_TAG);

          for (String image : images) {
            testUntil(
                withVeryLongRetryPolicy,
                pullImageFromBaseRepoAndPushToKind(image),
                logger,
                "pullImageFromBaseRepoAndPushToKind for image {0} to be successful",
                image);
          }
        }

        miiBasicImage = MII_BASIC_IMAGE_NAME + ":" + MII_BASIC_IMAGE_TAG;
        wdtBasicImage = WDT_BASIC_IMAGE_NAME + ":" + WDT_BASIC_IMAGE_TAG;

        // build MII basic image if does not exits
        logger.info("Build/Check mii-basic image with tag {0}", MII_BASIC_IMAGE_TAG);
        if (! imageExists(MII_BASIC_IMAGE_NAME, MII_BASIC_IMAGE_TAG)) {
          logger.info("Building mii-basic image {0}", miiBasicImage);
          testUntil(
                withVeryLongRetryPolicy,
                createBasicImage(MII_BASIC_IMAGE_NAME, MII_BASIC_IMAGE_TAG, MII_BASIC_WDT_MODEL_FILE,
                null, MII_BASIC_APP_NAME, MII_BASIC_IMAGE_DOMAINTYPE),
                logger,
                "createBasicImage to be successful");
        } else {
          logger.info("!!!! domain image {0} exists !!!!", miiBasicImage);
        }

        logger.info("Build/Check wdt-basic image with tag {0}", WDT_BASIC_IMAGE_TAG);
        // build WDT basic image if does not exits
        if (!imageExists(WDT_BASIC_IMAGE_NAME, WDT_BASIC_IMAGE_TAG) && !CRIO && !OCNE) {
          logger.info("Building wdt-basic image {0}", wdtBasicImage);
          testUntil(
                withVeryLongRetryPolicy,
                createBasicImage(WDT_BASIC_IMAGE_NAME, WDT_BASIC_IMAGE_TAG, WDT_BASIC_MODEL_FILE,
                WDT_BASIC_MODEL_PROPERTIES_FILE, WDT_BASIC_APP_NAME, WDT_BASIC_IMAGE_DOMAINTYPE),
                logger,
                "createBasicImage to be successful");
        } else {
          logger.info("!!!! domain image {0} exists !!!! or env is not OCNE based", wdtBasicImage);
        }

        /* Check image exists using WLSIMG_BUILDER images | grep image tag.
         * Tag name is unique as it contains date and timestamp.
         * This is a workaround for the issue on Jenkins machine
         * as WLSIMG_BUILDER images imagename:imagetag is not working and
         * the test fails even though the image exists.
         */
        assertTrue(doesImageExist(MII_BASIC_IMAGE_TAG),
              String.format("Image %s doesn't exist", miiBasicImage));

        if (!CRIO && !OCNE) {
          assertTrue(doesImageExist(WDT_BASIC_IMAGE_TAG),
              String.format("Image %s doesn't exist", wdtBasicImage));
        }

        logger.info(WLSIMG_BUILDER + " login");
        testUntil(withVeryLongRetryPolicy,
              () -> imageRepoLogin(BASE_IMAGES_REPO, BASE_IMAGES_REPO_USERNAME, BASE_IMAGES_REPO_PASSWORD),
              logger,
              WLSIMG_BUILDER + " login to BASE_IMAGES_REPO to be successful");

        // push the images to test images repository
        if (!DOMAIN_IMAGES_REPO.isEmpty()) {

          List<String> images = new ArrayList<>();
          if (!ARM) {
            images.add(operatorImage);
          }
          // add images only if SKIP_BUILD_IMAGES_IF_EXISTS is not set
          if (!SKIP_BUILD_IMAGES_IF_EXISTS) {
            images.add(miiBasicImage);
            if (!CRIO && !OCNE) {
              images.add(wdtBasicImage);
            }
          }

          for (String image : images) {
            if (KIND_REPO != null) {
              logger.info("kind load docker-image {0} --name kind", image);
            } else {
              logger.info(WLSIMG_BUILDER + " push image {0} to {1}", image, DOMAIN_IMAGES_REPO);
            }
            testUntil(
                withVeryLongRetryPolicy,
                () -> imagePush(image),
                logger,
                WLSIMG_BUILDER + " push to TEST_IMAGES_REPO/kind for image {0} to be successful",
                image);
          }

          // list images for Kind cluster
          if (KIND_REPO != null) {
            Command
                .withParams(new CommandParams()
                    .command(WLSIMG_BUILDER + " exec kind-worker crictl images")
                    .verbose(true)
                    .saveResults(true))
                .execute();
          }
        }
        
        //install webhook to prevent every operator installation trying to update crd
        installWebHookOnlyOperator("DomainOnPvSimplification=true");
        //install traefik when running with podman container runtime
        if (!TestConstants.WLSIMG_BUILDER.equals(TestConstants.WLSIMG_BUILDER_DEFAULT) && !CRIO) {
          installTraefikLB();
        }
        //install Oracle Database operator as a one time task
        if (!OCNE && !OKD && !CRIO && !ARM) {
          installOracleDBOperator();
        }

        // set initialization success to true, not counting the istio installation as not all tests use istio
        isInitializationSuccessful = true;
        if (!OKD && !CRIO) {
          logger.info("Installing istio before any test suites are run");
          installIstio();
        }
        if (INSTALL_WEBLOGIC && !CRIO && !ARM && !OKE_CLUSTER) {
          installOnPremWebLogic();
        }
      } finally {
        // Initialization is done. Release all waiting other threads. The latch is now disabled so
        // other threads
        // arriving later will immediately proceed.
        initializationLatch.countDown();
      }
    } else {
      // Other threads will enter here and wait on the latch. Once the latch is released, any threads arriving
      // later will immediately proceed.
      try {
        initializationLatch.await();
      } catch (InterruptedException e) {
        throw new IllegalStateException(e);
      }
    }

    // check initialization is already done and is not successful
    assertTrue(started.get() && isInitializationSuccessful,
        "Initialization(login/push to BASE_IMAGES_REPO) failed, "
            + "check the actual error or stack trace in the first test that failed in the test suite");

  }

  /**
   * Called when images are pushed, allowing conditional cleanup of images that are pushed
   * to a remote registry.
   *
   * @param imageName Image name
   */
  public static void registerPushedImage(String imageName) {
    pushedImages.add(imageName);
  }

  /**
   * Called when load balancer is created in OCI, allowing conditional cleanup of load balancers.
   *
   * @param lbIP external IP of load balancer.
   */
  public static void registerLoadBalancerExternalIP(String lbIP) {
    lbIPs.add(lbIP);
  }

  @Override
  public void close() {
    LoggingFacade logger = getLogger();
    // check SKIP_CLEANUP environment variable to skip cleanup
    if (SKIP_CLEANUP) {
      logger.info("Skipping RESULTS_ROOT clean up after test execution");
    } else {
      if (!OKD && !CRIO) {
        logger.info("Uninstall istio after all test suites are run");
        uninstallIstio();
      }
      if (!OKD && !OKE_CLUSTER && !CRIO) {
        logger.info("Delete istio-system namespace after all test suites are run");
        deleteNamespace("istio-system");
        deleteNamespace(ORACLE_OPERATOR_NS);
      }
      logger.info("Cleanup WIT/WDT binary form {0}", RESULTS_ROOT);
      try {
        Files.deleteIfExists(Paths.get(RESULTS_ROOT, "wlthint3client.jar"));
        cleanupDirectory(DOWNLOAD_DIR);
        cleanupDirectory(WIT_BUILD_DIR);
        cleanupDirectory(STAGE_DIR);
        cleanupDirectory((Paths.get(WORK_DIR, "imagetool")).toString());
        // remove empty directory
        Files.deleteIfExists(Paths.get(WORK_DIR, "imagetool"));
        Files.deleteIfExists(Paths.get(STAGE_DIR));
        Files.deleteIfExists(Paths.get(WIT_BUILD_DIR));
        Files.deleteIfExists(Paths.get(DOWNLOAD_DIR));
      } catch (IOException ioe) {
        logger.severe("Failed to cleanup files @ " + RESULTS_ROOT, ioe);
      }
      logger.info("Uninstalling webhook only operator");
      uninstallOperator(opHelmParams);
      deleteNamespace(webhookNamespace);

      logger.info("Cleanup images after all test suites are run");
      // delete all the images from local repo
      for (String image : pushedImages) {
        deleteImage(image);
      }

      if (OKE_CLUSTER) {
        logger.info("Cleanup created in OCI load balancers after all test suites are run");
        // delete all load balancers in OCI
        for (String ip : lbIPs) {
          deleteLoadBalancer(ip);
        }
      }
    }

    // delete images from TEST_IMAGES_REPO, if necessary
    if (DOMAIN_IMAGES_REPO.contains("ocir.io")) {
      String token = getOcirToken();
      if (token != null) {
        logger.info("Deleting these images from REPO_REGISTRY");
        logger.info(String.join(", ", pushedImages));
        for (String image : pushedImages.stream().distinct().collect(Collectors.toList())) {
          deleteImageOcir(token, image);
        }
      }
    }

    //delete certificate manager
    String certManager = CERT_MANAGER;
    CommandParams params = new CommandParams().defaults();
    params.command(KUBERNETES_CLI + " delete -f " + certManager);
    if (!Command.withParams(params).execute()) {
      logger.warning("Failed to uninstall cert manager");
    }

    for (Handler handler : logger.getUnderlyingLogger().getHandlers()) {
      handler.close();
    }
    portInuseEventWatcher.interrupt();
  }

  private String getOcirToken() {
    LoggingFacade logger = getLogger();
    Path scriptPath = Paths.get(RESOURCE_DIR, "bash-scripts", "ocirtoken.sh");
    StringBuilder cmd = new StringBuilder()
        .append(scriptPath.toFile().getAbsolutePath())
        .append(" -u " + BASE_IMAGES_REPO_USERNAME)
        .append(" -p \"" + BASE_IMAGES_REPO_PASSWORD + "\"")
        .append(" -e " + BASE_IMAGES_REPO);
    ExecResult result = null;
    try {
      result = ExecCommand.exec(cmd.toString(), true);
    } catch (Exception e) {
      logger.info("Got exception while running command: {0}", cmd);
      logger.info(e.toString());
    }
    if (result != null) {
      logger.info("result.stdout: \n{0}", result.stdout());
      logger.info("result.stderr: \n{0}", result.stderr());
    }

    return result != null ? result.stdout().trim() : null;
  }

  private void deleteImageOcir(String token, String imageName) {
    LoggingFacade logger = getLogger();
    int firstSlashIdx = imageName.indexOf('/');
    String registry = imageName.substring(0, firstSlashIdx);
    int secondSlashIdx = imageName.indexOf('/', firstSlashIdx + 1);
    String tenancy = imageName.substring(firstSlashIdx + 1, secondSlashIdx);
    String imageAndTag = imageName.substring(secondSlashIdx + 1);
    String curlCmd = "curl -skL -X \"DELETE\" -H \"Authorization: Bearer " + token
        + "\" \"https://" + registry + "/20180419/docker/images/"
        + tenancy + "/" + imageAndTag.replace(':', '/') + "\"";
    logger.info("About to invoke: " + curlCmd);
    ExecResult result = null;
    try {
      result = ExecCommand.exec(curlCmd, true);
    } catch (Exception e) {
      logger.info("Got exception while running command: {0}", curlCmd);
      logger.info(e.toString());
    }
    if (result != null) {
      logger.info("result.stdout: \n{0}", result.stdout());
      String stdout = result.stdout();
      logger.info("result.stdout: \n{0}", stdout);
      logger.info("result.stderr: \n{0}", result.stderr());

      // check if delete was successful and respond if tag couldn't be deleted because there is only one image
      if (!stdout.isEmpty()) {
        ObjectMapper mapper = new ObjectMapper();
        try {
          JsonNode root = mapper.readTree(stdout);
          JsonNode errors = root.path("errors");
          if (errors != null) {
            Iterator<JsonNode> it = errors.elements();
            while (it.hasNext()) {
              JsonNode entry = it.next();
              if (entry != null) {
                JsonNode code = entry.path("code");
                if (code != null) {
                  if ("SEMANTIC_VALIDATION_ERROR".equals(code.asText())) {
                    // The delete of the tag failed because there is only one tag remaining in the
                    // repository
                    // Note: there are probably other semantic validation errors, but I don't think
                    // it's worth
                    // checking now because our use cases are fairly simple

                    int colonIdx = imageAndTag.indexOf(':');
                    String repo = imageAndTag.substring(0, colonIdx);

                    // Delete the repository
                    curlCmd =
                        "curl -skL -X \"DELETE\" -H \"Authorization: Bearer "
                            + token
                            + "\" \"https://"
                            + registry
                            + "/20180419/docker/repos/"
                            + tenancy
                            + "/"
                            + repo
                            + "\"";
                    logger.info("About to invoke: " + curlCmd);
                    result = null;
                    try {
                      result = ExecCommand.exec(curlCmd, true);
                    } catch (Exception e) {
                      logger.info("Got exception while running command: {0}", curlCmd);
                      logger.info(e.toString());
                    }
                    if (result != null) {
                      logger.info("result.stdout: \n{0}", result.stdout());
                      logger.info("result.stderr: \n{0}", result.stderr());
                    }
                  }
                }
              }
            }
          }
        } catch (JsonProcessingException e) {
          logger.info("Got exception, parsing failed with errors " + e.getMessage());
        }
      }
    }
  }

  /**
   * Create image with basic domain model yaml, variable file and sample application.
   *
   * @param imageName  name of the image
   * @param imageTag   tag of the image
   * @param modelFile  model file to build the image
   * @param varFile    variable file to build the image
   * @param appName    name of the application to build the image
   * @param domainType domain type to be built
   * @return true if image is created successfully
   */

  public Callable<Boolean> createBasicImage(String imageName, String imageTag, String modelFile, String varFile,
                                            String appName, String domainType) {
    return (() -> {
      LoggingFacade logger = getLogger();
      final String image = imageName + ":" + imageTag;

      // build the model file list
      final List<String> modelList = Collections.singletonList(MODEL_DIR + "/" + modelFile);

      // build an application archive using what is in resources/apps/APP_NAME
      logger.info("Build an application archive using resources/apps/{0}", appName);
      assertTrue(buildAppArchive(defaultAppParams()
              .srcDirList(Collections.singletonList(appName))),
          String.format("Failed to create app archive for %s", appName));

      // build the archive list
      String zipFile = String.format("%s/%s.zip", ARCHIVE_DIR, appName);
      final List<String> archiveList = Collections.singletonList(zipFile);

      // Set additional environment variables for WIT
      checkDirectory(WIT_BUILD_DIR);
      Map<String, String> env = new HashMap<>();
      env.put("WLSIMG_BLDDIR", WIT_BUILD_DIR);

      // For k8s 1.16 support and as of May 6, 2020, we presently need a different JDK for these
      // tests and for image tool. This is expected to no longer be necessary once JDK 11.0.8 or
      // the next JDK 14 versions are released.
      if (WIT_JAVA_HOME != null) {
        env.put("JAVA_HOME", WIT_JAVA_HOME);
      }

      String witTarget = ((OKD) ? "OpenShift" : "Default");

      // build an image using WebLogic Image Tool
      boolean imageCreation = false;
      logger.info("Create image {0} using model directory {1}", image, MODEL_DIR);
      if (domainType.equalsIgnoreCase("wdt")) {
        final List<String> modelVarList = Collections.singletonList(MODEL_DIR + "/" + varFile);
        imageCreation = createImage(
            defaultWitParams()
                .modelImageName(imageName)
                .modelImageTag(WDT_BASIC_IMAGE_TAG)
                .modelFiles(modelList)
                .modelArchiveFiles(archiveList)
                .modelVariableFiles(modelVarList)
                .domainHome(WDT_BASIC_IMAGE_DOMAINHOME)
                .wdtOperation("CREATE")
                .wdtVersion(WDT_VERSION)
                .target(witTarget)
                .env(env)
                .redirect(true));
      } else if (domainType.equalsIgnoreCase("mii")) {
        imageCreation = createImage(
            defaultWitParams()
                .modelImageName(imageName)
                .modelImageTag(MII_BASIC_IMAGE_TAG)
                .modelFiles(modelList)
                .modelArchiveFiles(archiveList)
                .wdtModelOnly(true)
                .wdtVersion(WDT_VERSION)
                .target(witTarget)
                .env(env)
                .redirect(true));
      }
      return imageCreation;
    });
  }

  private Callable<Boolean> pullImageFromBaseRepoAndPushToKind(String image) {
    return (() -> {
      String kindRepoImage = KIND_REPO + image.substring(BASE_IMAGES_REPO.length() + BASE_IMAGES_TENANCY.length() + 2);
      return imagePull(image) && imageTag(image, kindRepoImage) && imagePush(kindRepoImage);
    });
  }
  
  HelmParams opHelmParams;
  String webhookNamespace = "ns-webhook";

  private OperatorParams installWebHookOnlyOperator() {
    return installWebHookOnlyOperator(null);
  }

  private OperatorParams installWebHookOnlyOperator(String featureGates) {
    // recreate WebHook namespace
    deleteNamespace(webhookNamespace);
    assertDoesNotThrow(() -> new Namespace().name(webhookNamespace).create());
    String webhookSa = webhookNamespace + "-sa";
    getLogger().info("Installing webhook only operator in namespace {0}", webhookNamespace);
    opHelmParams
        = new HelmParams().releaseName(OPERATOR_RELEASE_NAME)
            .namespace(webhookNamespace)
            .chartDir(OPERATOR_CHART_DIR);
    return installAndVerifyOperator(
        webhookNamespace, // webhook namespace
        webhookSa, //webhook service account
        false, // with REST api enabled
        0, // externalRestHttpPort
        opHelmParams, // operator helm parameters
        null, // elasticsearchHost
        false, // ElkintegrationEnabled
        false, // createLogStashconfigmap
        null, // domainspaceSelectionStrategy
        null, // domainspaceSelector
        true, // enableClusterRolebinding
        "INFO", // webhook pod log level
        featureGates, // the name of featureGates
        true, // webhookOnly
        "null" // domainNamespace
    );
  }
  
  private void installTraefikLB() {
    deleteNamespace(TRAEFIK_NAMESPACE);
    assertDoesNotThrow(() -> new Namespace().name(TRAEFIK_NAMESPACE).create());
    getLogger().info("Installing traefik in namespace {0}", TRAEFIK_NAMESPACE);
    TraefikParams traefikParams = installAndVerifyTraefik(TRAEFIK_NAMESPACE, TRAEFIK_INGRESS_HTTP_NODEPORT,
        TRAEFIK_INGRESS_HTTPS_NODEPORT, "NodePort");    
    assertDoesNotThrow(() -> Files.writeString(INGRESS_CLASS_FILE_NAME, traefikParams.getIngressClassName()));    
    String cmd = KUBERNETES_CLI + " get all -A";
    try {
      ExecResult result = ExecCommand.exec(cmd, true);
      getLogger().info(result.stdout());
    } catch (IOException | InterruptedException ex) {
      getLogger().info("Exception in get all {0}", ex);
    }
    //TO-DO for OKD to use traefik for all service access
    //expose traefik node port service and get route host
    //oc -n ns-abcdef expose service nginx-release-nginx-ingress-nginx-controller
    //oc -n ns-abcdef get routes nginx-release-nginx-ingress-nginx-controller '-o=jsonpath={.spec.host}'
  }
  
  private void installOracleDBOperator() {
    //install Oracle Database Operator
    String namespace = ORACLE_OPERATOR_NS;
    assertDoesNotThrow(() -> new Namespace().name(namespace).create());
    assertDoesNotThrow(() -> installDBOperator(), "Failed to install database operator");
  }

  private void installOnPremWebLogic() {
    Path installScript = Paths.get(RESOURCE_DIR, "bash-scripts", "install-wls.sh");
    String command
        = String.format("%s %s %s %s", "/bin/bash", installScript, RESULTS_ROOT, WEBLOGIC_SHIPHOME);
    getLogger().info("WebLogic installation command {0}", command);
    assertTrue(() -> Command.withParams(
        defaultCommandParams()
            .command(command)
            .redirect(false))
        .execute());
  }

}

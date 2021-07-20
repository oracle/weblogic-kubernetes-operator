// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.utils.ExecResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_REPO;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.OCIR_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createOcirRepoSecret;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.createSecretForBaseImages;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.TestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Helper class to test and verify MII sample using or not-using auxiliary image.
 */
@DisplayName("Helper class to test model in image sample")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
public class ItMiiSampleHelper {

  private static final String MII_SAMPLES_WORK_DIR = RESULTS_ROOT
      + "/model-in-image-sample-work-dir";
  private static final String MII_SAMPLES_SCRIPT =
      "../operator/integration-tests/model-in-image/run-test.sh";

  private static final String CURRENT_DATE_TIME = getDateAndTimeStamp();
  private static final String MII_SAMPLE_WLS_IMAGE_NAME_V1 = DOMAIN_IMAGES_REPO + "mii-" + CURRENT_DATE_TIME + "-wlsv1";
  private static final String MII_SAMPLE_WLS_IMAGE_NAME_V2 = DOMAIN_IMAGES_REPO + "mii-" + CURRENT_DATE_TIME + "-wlsv2";
  private static final String MII_SAMPLE_JRF_IMAGE_NAME_V1 = DOMAIN_IMAGES_REPO + "mii-" + CURRENT_DATE_TIME + "-jrfv1";
  private static final String MII_SAMPLE_JRF_IMAGE_NAME_V2 = DOMAIN_IMAGES_REPO + "mii-" + CURRENT_DATE_TIME + "-jrfv2";
  private static final String SUCCESS_SEARCH_STRING = "Finished without errors";

  private static String opNamespace = null;
  private static String domainNamespace = null;
  private static String traefikNamespace = null;
  private static String dbNamespace = null;
  private static Map<String, String> envMap = null;
  private static LoggingFacade logger = null;
  private static boolean previousTestSuccessful = true;
  private static DomainType domainType = null;
  private static ImageType imageType = null;

  public enum DomainType {
    JRF,
    WLS
  }

  public enum ImageType {
    MAIN,
    AUX
  }

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   * @param domainTypeParam domain type names
   * @param imageTypeParam image type names
   */
  public static void initAll(List<String> namespaces, DomainType domainTypeParam, ImageType imageTypeParam) {
    logger = getLogger();
    ItMiiSampleHelper.domainType = domainTypeParam;
    ItMiiSampleHelper.imageType = imageTypeParam;
    // get a new unique opNamespace
    logger.info("Creating unique namespace for Operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);

    logger.info("Creating unique namespace for Domain");
    assertNotNull(namespaces.get(1), "Namespace list is null");
    domainNamespace = namespaces.get(1);

    logger.info("Creating unique namespace for Traefik");
    assertNotNull(namespaces.get(2), "Namespace list is null");
    traefikNamespace = namespaces.get(2);

    // install and verify operator
    installAndVerifyOperator(opNamespace, domainNamespace);

    // env variables to override default values in sample scripts
    envMap = new HashMap<String, String>();
    envMap.put("DOMAIN_NAMESPACE", domainNamespace);
    envMap.put("TRAEFIK_NAMESPACE", traefikNamespace);
    envMap.put("TRAEFIK_HTTP_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("TRAEFIK_HTTPS_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("WORKDIR", MII_SAMPLES_WORK_DIR);
    envMap.put("BASE_IMAGE_NAME", WEBLOGIC_IMAGE_NAME);
    envMap.put("BASE_IMAGE_TAG", WEBLOGIC_IMAGE_TAG);
    envMap.put("IMAGE_PULL_SECRET_NAME", OCIR_SECRET_NAME); //ocir secret
    envMap.put("K8S_NODEPORT_HOST", K8S_NODEPORT_HOST);
    envMap.put("OKD", "" +  OKD);
    envMap.put("DO_AI", String.valueOf(imageType == ImageType.AUX));

    // kind cluster uses openjdk which is not supported by image tool
    String witJavaHome = System.getenv("WIT_JAVA_HOME");
    if (witJavaHome != null) {
      envMap.put("JAVA_HOME", witJavaHome);
    }

    String witInstallerUrl = System.getProperty("wit.download.url");
    if (witInstallerUrl != null) {
      envMap.put("WIT_INSTALLER_URL", witInstallerUrl);
    }

    String wdtInstallerUrl = System.getProperty("wdt.download.url");
    if (wdtInstallerUrl != null) {
      envMap.put("WDT_INSTALLER_URL", wdtInstallerUrl);
    }
    logger.info("Env. variables to the script {0}", envMap);

    // install traefik using the mii sample script
    execTestScriptAndAssertSuccess(DomainType.WLS, "-traefik", "Traefik deployment failure");

    logger.info("Setting up docker secrets");
    // Create the repo secret to pull the image
    // this secret is used only for non-kind cluster
    createOcirRepoSecret(domainNamespace);
    logger.info("Docker registry secret {0} created successfully in namespace {1}",
        OCIR_SECRET_NAME, domainNamespace);

    if (domainType.equals(DomainType.JRF)) {
      // install db for FMW test cases
      logger.info("Creating unique namespace for Database");
      assertNotNull(namespaces.get(3), "Namespace list is null");
      dbNamespace = namespaces.get(3);

      envMap.put("dbNamespace", dbNamespace);

      // create ocr/ocir docker registry secret to pull the db images
      // this secret is used only for non-kind cluster
      createSecretForBaseImages(dbNamespace);
      logger.info("Docker registry secret {0} created successfully in namespace {1}",
          BASE_IMAGES_REPO_SECRET, dbNamespace);
    }
  }

  /**
   * Verify that the image exists and push it to docker registry if necessary.
   */
  public static void assertImageExistsAndPushIfNeeded() {
    String imageName = envMap.get("MODEL_IMAGE_NAME");
    String imageVer = "notset";
    String decoration = (envMap.get("DO_AI") != null && envMap.get("DO_AI").equalsIgnoreCase("true"))  ? "AI-" : "";

    if (imageName.equals(MII_SAMPLE_WLS_IMAGE_NAME_V1)) {
      imageVer = "WLS-" + decoration + "v1";
    }
    if (imageName.equals(MII_SAMPLE_WLS_IMAGE_NAME_V2)) {
      imageVer = "WLS-" + decoration + "v2";
    }
    if (imageName.equals(MII_SAMPLE_JRF_IMAGE_NAME_V1)) {
      imageVer = "JRF-" + decoration + "v1";
    }
    if (imageName.equals(MII_SAMPLE_JRF_IMAGE_NAME_V2)) {
      imageVer = "JRF-" + decoration + "v2";
    }

    String image = imageName + ":" + imageVer;

    // Check image exists using docker images | grep image image.
    assertTrue(doesImageExist(imageName), String.format("Image %s does not exist", image));

    // docker login and push image to docker registry if necessary
    dockerLoginAndPushImageToRegistry(image);
  }

  /**
   * Run script run-test.sh.
   * @param domainType domain type
   * @param args arguments to execute script
   * @param errString a string of detailed error
   */
  public static void execTestScriptAndAssertSuccess(DomainType domainType,
                                                    String args,
                                                    String errString) {
    for (String arg : args.split(",")) {
      Assumptions.assumeTrue(previousTestSuccessful);
      previousTestSuccessful = false;

      if (arg.equals("-check-image-and-push")) {
        assertImageExistsAndPushIfNeeded();

      } else {
        String command = MII_SAMPLES_SCRIPT
            + " "
            + arg
            + (domainType == DomainType.JRF ? " -jrf " : "");

        ExecResult result = Command.withParams(
            new CommandParams()
                .command(command)
                .env(envMap)
                .redirect(true)
        ).executeAndReturnResult();

        boolean success =
            result != null
                && result.exitValue() == 0
                && result.stdout() != null
                && result.stdout().contains(SUCCESS_SEARCH_STRING);

        String outStr = errString;
        outStr += ", domainType=" + domainType + "\n";
        outStr += ", imageType=" + imageType + "\n";
        outStr += ", command=\n{\n" + command + "\n}\n";
        outStr += ", stderr=\n{\n" + (result != null ? result.stderr() : "") + "\n}\n";
        outStr += ", stdout=\n{\n" + (result != null ? result.stdout() : "") + "\n}\n";

        assertTrue(success, outStr);
      }

      previousTestSuccessful = true;
    }
  }

  /**
   * Test MII sample WLS or JRF initial use case.
   */
  public static void callInitialUseCase() {
    String imageName = (domainType.equals(DomainType.WLS))
        ? MII_SAMPLE_WLS_IMAGE_NAME_V1 : MII_SAMPLE_JRF_IMAGE_NAME_V1;
    previousTestSuccessful = true;
    envMap.put("MODEL_IMAGE_NAME", imageName);

    if (domainType.equals(DomainType.JRF)) {
      String dbImageName = (KIND_REPO != null
          ? KIND_REPO + DB_IMAGE_NAME.substring(BASE_IMAGES_REPO.length() + 1) : DB_IMAGE_NAME);
      String jrfBaseImageName = (KIND_REPO != null
          ? KIND_REPO + FMWINFRA_IMAGE_NAME.substring(BASE_IMAGES_REPO.length() + 1) : FMWINFRA_IMAGE_NAME);
      String dbNamespace = envMap.get("dbNamespace");

      envMap.put("DB_IMAGE_NAME", dbImageName);
      envMap.put("DB_IMAGE_TAG", DB_IMAGE_TAG);
      envMap.put("DB_NODE_PORT", "none");
      envMap.put("BASE_IMAGE_NAME", jrfBaseImageName);
      envMap.put("BASE_IMAGE_TAG", FMWINFRA_IMAGE_TAG);
      envMap.put("POD_WAIT_TIMEOUT_SECS", "1000"); // JRF pod waits on slow machines, can take at least 650 seconds
      envMap.put("DB_NAMESPACE", dbNamespace);
      envMap.put("DB_IMAGE_PULL_SECRET", BASE_IMAGES_REPO_SECRET); //ocr/ocir secret
      envMap.put("INTROSPECTOR_DEADLINE_SECONDS", "600"); // introspector needs more time for JRF

      // run JRF use cases irrespective of WLS use cases fail/pass
      previousTestSuccessful = true;
      execTestScriptAndAssertSuccess(domainType, "-db,-rcu", "DB/RCU creation failed");
    }

    execTestScriptAndAssertSuccess(
        domainType,
        "-initial-image,-check-image-and-push,-initial-main",
        "Initial use case failed"
    );
  }

  /**
   * Test MII sample WLS or JRF update1 use case.
   */
  public static void callUpdateUseCase(String args,
                                       String errString) {
    if (args.contains("update3")) {
      String imageName = (domainType.equals(DomainType.WLS))
          ? MII_SAMPLE_WLS_IMAGE_NAME_V2 : MII_SAMPLE_JRF_IMAGE_NAME_V2;
      envMap.put("MODEL_IMAGE_NAME", imageName);
    }

    execTestScriptAndAssertSuccess(domainType, args, errString);
  }

  /**
   * Test MII sample WLS or JRF update1 use case.
   */
  public static void callCheckMiiSampleSource(String args,
                                              String errString) {
    final String baseImageNameKey = "BASE_IMAGE_NAME";
    envMap.remove(baseImageNameKey);
    execTestScriptAndAssertSuccess(domainType, args, errString);
    envMap.put(baseImageNameKey, WEBLOGIC_IMAGE_NAME);
  }

  /**
   * Delete DB deployment for FMW test cases and Uninstall traefik.
   */
  public static void tearDownAll() {
    logger = getLogger();
    // uninstall traefik
    if (traefikNamespace != null) {
      logger.info("Uninstall traefik");
      Command.withParams(new CommandParams()
          .command("helm uninstall traefik-operator -n " + traefikNamespace)
          .redirect(true)).execute();
    }

    // db cleanup or deletion
    if (domainType.equals(DomainType.JRF) && envMap != null) {
      logger.info("Running samples DB cleanup");
      Command.withParams(new CommandParams()
          .command(MII_SAMPLES_SCRIPT + " -precleandb")
          .env(envMap)
          .redirect(true)).execute();
    }
  }
}
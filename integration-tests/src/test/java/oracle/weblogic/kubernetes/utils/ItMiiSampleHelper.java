// Copyright (c) 2021, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import static oracle.weblogic.kubernetes.TestConstants.BASE_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.DB_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_IMAGES_PREFIX;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.FMWINFRA_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.TestConstants.K8S_NODEPORT_HOST;
import static oracle.weblogic.kubernetes.TestConstants.MII_BASIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.OKD;
import static oracle.weblogic.kubernetes.TestConstants.OKE_CLUSTER;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.TestConstants.TEST_IMAGES_REPO_SECRET_NAME;
import static oracle.weblogic.kubernetes.TestConstants.TRAEFIK_RELEASE_NAME;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TO_USE_IN_SPEC;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WDT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_DOWNLOAD_URL;
import static oracle.weblogic.kubernetes.actions.ActionConstants.WIT_JAVA_HOME;
import static oracle.weblogic.kubernetes.actions.TestActions.inspectImage;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.getDateAndTimeStamp;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createTestRepoSecret;
import static oracle.weblogic.kubernetes.utils.ImageUtils.imageRepoLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.LoadBalancerUtils.upgradeAndVerifyTraefik;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
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

  private final String miiSampleScript = "../operator/integration-tests/model-in-image/run-test.sh";
  private final String currentDateTime = getDateAndTimeStamp();
  private final String successSearchString = "Finished without errors";

  private String opNamespace = null;
  private String domainNamespace = null;
  private String traefikNamespace = null;
  private String dbNamespace = null;
  private Map<String, String> envMap = null;
  private LoggingFacade logger = null;
  private boolean previousTestSuccessful = true;
  private DomainType domainType = null;
  private ImageType imageType = null;

  public enum DomainType {
    JRF,
    WLS
  }

  public enum ImageType {
    MAIN,
    AUX
  }

  private String getModelImageName(String suffix) {
    return new StringBuffer(DOMAIN_IMAGES_PREFIX)
        .append("mii-")
        .append(suffix).toString();
  }

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   * @param domainTypeParam domain type names
   * @param imageTypeParam image type names
   */
  public void initAll(List<String> namespaces, DomainType domainTypeParam, ImageType imageTypeParam) {
    logger = getLogger();
    domainType = domainTypeParam;
    imageType = imageTypeParam;
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

    String miiSampleWorkDir =
        RESULTS_ROOT + "/" + domainNamespace + "/model-in-image-sample-work-dir";

    // env variables to override default values in sample scripts
    envMap = new HashMap<>();
    envMap.put("DOMAIN_NAMESPACE", domainNamespace);
    envMap.put("TRAEFIK_NAMESPACE", traefikNamespace);
    envMap.put("TRAEFIK_HTTP_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("TRAEFIK_HTTPS_NODEPORT", "0"); // 0-->dynamically choose the np
    envMap.put("TRAEFIK_NAME", TRAEFIK_RELEASE_NAME + "-" + traefikNamespace.substring(3));
    envMap.put("WORKDIR", miiSampleWorkDir);
    envMap.put("BASE_IMAGE_NAME", WEBLOGIC_IMAGE_TO_USE_IN_SPEC
        .substring(0, WEBLOGIC_IMAGE_TO_USE_IN_SPEC.lastIndexOf(":")));
    envMap.put("BASE_IMAGE_TAG", WEBLOGIC_IMAGE_TAG);
    envMap.put("IMAGE_PULL_SECRET_NAME", BASE_IMAGES_REPO_SECRET_NAME);
    envMap.put("DOMAIN_IMAGE_PULL_SECRET_NAME", TEST_IMAGES_REPO_SECRET_NAME);
    envMap.put("K8S_NODEPORT_HOST", K8S_NODEPORT_HOST);
    envMap.put("OKD", "" +  OKD);
    envMap.put("DO_AI", String.valueOf(imageType == ImageType.AUX));

    // kind cluster uses openjdk which is not supported by image tool
    if (WIT_JAVA_HOME != null) {
      envMap.put("JAVA_HOME", WIT_JAVA_HOME);
    }

    if (WIT_DOWNLOAD_URL != null) {
      envMap.put("WIT_INSTALLER_URL", WIT_DOWNLOAD_URL);
    }

    if (WDT_DOWNLOAD_URL != null) {
      envMap.put("WDT_INSTALLER_URL", WDT_DOWNLOAD_URL);
    }
    logger.info("Env. variables to the script {0}", envMap);

    // install traefik using the mii sample script
    execTestScriptAndAssertSuccess(DomainType.WLS, "-traefik", "Traefik deployment failure");
    //Helm upgrade traefik and verify traefik pod is ready
    upgradeAndVerifyTraefik(traefikNamespace);

    logger.info("Setting up image registry secrets");
    // Create the repo secret to pull the domain image
    // this secret is used only for non-kind cluster
    createTestRepoSecret(domainNamespace);
    logger.info("Registry secret {0} created for domain image successfully in namespace {1}",
        TEST_IMAGES_REPO_SECRET_NAME, domainNamespace);
    // Create the repo secret to pull the base image
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace);
    logger.info("Registry secret {0} for base image created successfully in namespace {1}",
        BASE_IMAGES_REPO_SECRET_NAME, domainNamespace);


    if (domainType.equals(DomainType.JRF)) {
      // install db for FMW test cases
      logger.info("Creating unique namespace for Database");
      assertNotNull(namespaces.get(3), "Namespace list is null");
      dbNamespace = namespaces.get(3);

      envMap.put("dbNamespace", dbNamespace);

      // create ocr/ocir image registry secret to pull the db images
      // this secret is used only for non-kind cluster
      createBaseRepoSecret(dbNamespace);
      logger.info("Registry secret {0} created successfully in namespace {1}",
              TestConstants.BASE_IMAGES_REPO_SECRET_NAME, dbNamespace);
    }

    callCheckMiiSampleSource();
  }

  /**
   * Verify that the image exists and push it to image registry if necessary.
   */
  public void assertImageExistsAndPushIfNeeded() {
    String imageName = envMap.get("MODEL_IMAGE_NAME");
    String imageVer = "notset";
    String decoration = (envMap.get("DO_AI") != null && envMap.get("DO_AI").equalsIgnoreCase("true"))  ? "AI-" : "";

    if (imageName.contains("-wlsv1")) {
      imageVer = MII_BASIC_IMAGE_TAG + "-WLS-" + decoration + "v1";
    }
    if (imageName.contains("-wlsv2")) {
      imageVer = MII_BASIC_IMAGE_TAG + "-WLS-" + decoration + "v2";
    }
    if (imageName.contains("-jrfv1")) {
      imageVer = MII_BASIC_IMAGE_TAG + "-JRF-" + decoration + "v1";
    }
    if (imageName.contains("-jrfv2")) {
      imageVer = MII_BASIC_IMAGE_TAG + "-JRF-" + decoration + "v2";
    }

    String image = imageName + ":" + imageVer;

    // Check image exists using 'WLSIMG_BUILDER images | grep image image'.
    assertTrue(doesImageExist(imageName), String.format("Image %s does not exist", image));

    // repo login and push image to registry if necessary
    imageRepoLoginAndPushImageToRegistry(image);
  }

  /**
   * Run script run-test.sh.
   * @param domainType domain type
   * @param args arguments to execute script
   * @param errString a string of detailed error
   */
  private void execTestScriptAndAssertSuccess(DomainType domainType,
                                                    String args,
                                                    String errString) {
    for (String arg : args.split(",")) {
      Assumptions.assumeTrue(previousTestSuccessful);
      previousTestSuccessful = false;

      if (arg.equals("-check-image-and-push")) {
        assertImageExistsAndPushIfNeeded();

      } else {
        String command = miiSampleScript
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
                && result.stdout().contains(successSearchString);

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
   * @param testClassName the test class name
   */
  public void callInitialUseCase(String testClassName) {
    String imageName = (domainType.equals(DomainType.WLS))
        ? getModelImageName(testClassName + "-wlsv1") : getModelImageName(testClassName + "-jrfv1");
    previousTestSuccessful = true;
    envMap.put("MODEL_IMAGE_NAME", imageName);
    String decoration = (envMap.get("DO_AI") != null && envMap.get("DO_AI").equalsIgnoreCase("true"))  ? "AI-" : "";
    envMap.put("MODEL_IMAGE_TAG",
        MII_BASIC_IMAGE_TAG + "-" + domainType + "-" + decoration + "v1");
    envMap.put("MODEL_DIR", "wdt-artifacts/wdt-model-files/" + domainType + "-v1");

    if (domainType.equals(DomainType.JRF)) {
      String dbImageName = DB_IMAGE_TO_USE_IN_SPEC
          .substring(0, DB_IMAGE_TO_USE_IN_SPEC.lastIndexOf(":"));
      String jrfBaseImageName = FMWINFRA_IMAGE_TO_USE_IN_SPEC
          .substring(0, FMWINFRA_IMAGE_TO_USE_IN_SPEC.lastIndexOf(":"));

      String dbNamespace = envMap.get("dbNamespace");

      envMap.put("DB_IMAGE_NAME", dbImageName);
      envMap.put("DB_IMAGE_TAG", DB_IMAGE_TAG);
      envMap.put("DB_NODE_PORT", "none");
      envMap.put("BASE_IMAGE_NAME", jrfBaseImageName);
      envMap.put("BASE_IMAGE_TAG", FMWINFRA_IMAGE_TAG);
      envMap.put("POD_WAIT_TIMEOUT_SECS", "1000"); // JRF pod waits on slow machines, can take at least 650 seconds
      envMap.put("DB_NAMESPACE", dbNamespace);
      envMap.put("DB_IMAGE_PULL_SECRET", TestConstants.BASE_IMAGES_REPO_SECRET_NAME); //ocr/ocir secret
      envMap.put("INTROSPECTOR_DEADLINE_SECONDS", "600"); // introspector needs more time for JRF

      logger.info("dbImageName used for JRF domain is: {0}, imageTag is: {1}", dbImageName, DB_IMAGE_TAG);
      logger.info("jrfBaseImageName used for JRF domain is: {0}, imageTag is: {1}",
          jrfBaseImageName, FMWINFRA_IMAGE_TAG);

      if (OKE_CLUSTER) {
        String output = inspectImage(jrfBaseImageName, FMWINFRA_IMAGE_TAG);
        assertNotNull(output, String.format("Can't inspect image %s:%s", jrfBaseImageName, FMWINFRA_IMAGE_TAG));
        if (!output.contains("root")) {
          envMap.put("CHOWN_ROOT","--chown oracle:oracle");
        }
      }
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
   * @param args test arguments
   * @param errString a string of detailed error
   * @param testClassName the test class name which will call this method
   */
  public void callUpdateUseCase(String args, String errString, String testClassName) {

    if (args.contains("update3")) {
      String imageName = (domainType.equals(DomainType.WLS))
          ? getModelImageName(testClassName + "-wlsv2") : getModelImageName(testClassName + "-jrfv2");
      envMap.put("MODEL_IMAGE_NAME", imageName);
      String decoration = (envMap.get("DO_AI") != null && envMap.get("DO_AI").equalsIgnoreCase("true"))  ? "AI-" : "";
      envMap.put("MODEL_IMAGE_TAG", MII_BASIC_IMAGE_TAG + "-" + domainType + "-" + decoration + "v2");
      envMap.put("MODEL_DIR", "wdt-artifacts/wdt-model-files/" + domainType + "-v2");
    }

    execTestScriptAndAssertSuccess(domainType, args, errString);
  }

  /**
   * Verify that the "hard coded" sample domain resources in git
   * align with the templates used by the tests.
   */
  private void callCheckMiiSampleSource() {
    Map<String, String> localMap = new HashMap<>();
    localMap.put("WORKDIR", envMap.get("WORKDIR"));

    Map<String, String> saveMap = envMap;
    envMap = localMap;
    try {
      logger.info("Comparing integration test domain resource templates to hard coded resources in the sample source.");
      execTestScriptAndAssertSuccess(
          DomainType.WLS,
          "-check-sample",
          "Error: the MII sample domain resources in git do not align with integration test templates."
          + " To run this check locally: './operator/integration-tests/model-in-image/run-test.sh -check-sample'."
      );
    } finally {
      envMap = saveMap;
    }
  }

  /**
   * Delete DB deployment for FMW test cases and Uninstall traefik.
   */
  public void tearDownAll() {
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
          .command(miiSampleScript + " -precleandb")
          .env(envMap)
          .redirect(true)).execute();
    }
  }
}

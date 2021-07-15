// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.Map;

import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
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
import static oracle.weblogic.kubernetes.TestConstants.KIND_REPO;
import static oracle.weblogic.kubernetes.TestConstants.RESULTS_ROOT;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.doesImageExist;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.dockerLoginAndPushImageToRegistry;
import static oracle.weblogic.kubernetes.utils.TestUtils.getDateAndTimeStamp;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Helper class to test to verify MII sample.
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

  private Map<String, String> envMap = null;
  private boolean previousTestSuccessful = true;
  private String domainTypeName = null;
  private String imageTypeName = null;

  private enum DomainType {
    JRF,
    WLS
  }

  private enum ImageType {
    MAIN,
    AUX
  }

  /**
   * Set env variables map.
   * @param envMap a map contains env variables
   */
  public void setEnvMap(Map<String, String> envMap) {
    this.envMap = envMap;
  }

  /**
   * Set domain type.
   * @param domainTypeName domain type name
   */
  public void setDomainType(String domainTypeName) {
    this.domainTypeName = domainTypeName;
  }

  /**
   * Set image type.
   * @param imageTypeName image type names
   */
  public void setImageType(String imageTypeName) {
    this.imageTypeName = imageTypeName;
  }

  /**
   * Get env variables map.
   * @return a map containing env variables
   */
  public Map<String, String> getEnvMap() {
    return this.envMap;
  }

  /**
   * Get domain type.
   * @return domain type name
   */
  public String getDomainType() {
    return this.domainTypeName;
  }

  /**
   * Get image type.
   * @return  image type names
   */
  public String getImageType() {
    return this.imageTypeName;
  }

  /**
   * Verify that the image exists and push it to docker registry if necessary.
   */
  public void assertImageExistsAndPushIfNeeded() {
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
   * @param args arguments to execute script
   * @param errString a string of detailed error
   */
  public void execTestScriptAndAssertSuccess(String args, String errString) {
    // WLS is the the test script's default
    execTestScriptAndAssertSuccess(DomainType.WLS, args, errString);
  }

  /**
   * Run script run-test.sh.
   * @param domainType domain type
   * @param args arguments to execute script
   * @param errString a string of detailed error
   */
  public void execTestScriptAndAssertSuccess(DomainType domainType,
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
  public void callInitialUseCase() {
    String useAuxiliaryImage = (getImageType().equalsIgnoreCase(ImageType.AUX.toString()))  ? "true" : "false";
    String imageName = (getDomainType().equalsIgnoreCase(DomainType.WLS.toString()))
        ? MII_SAMPLE_WLS_IMAGE_NAME_V1 : MII_SAMPLE_JRF_IMAGE_NAME_V1;
    previousTestSuccessful = true;
    envMap.put("DO_AI", useAuxiliaryImage);
    envMap.put("MODEL_IMAGE_NAME", imageName);

    if (getDomainType().equalsIgnoreCase(DomainType.WLS.toString())) {
      execTestScriptAndAssertSuccess("-initial-image,-check-image-and-push,-initial-main", "Initial use case failed");
    } else if (getDomainType().equalsIgnoreCase(DomainType.JRF.toString())) {
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
      execTestScriptAndAssertSuccess(DomainType.JRF, "-db,-rcu", "DB/RCU creation failed");
      execTestScriptAndAssertSuccess(
          DomainType.JRF,
          "-initial-image,-check-image-and-push,-initial-main",
          "Initial use case failed"
      );
    }
  }

  /**
   * Test MII sample WLS or JRF update1 use case.
   */
  public void callUpdate1UseCase() {
    String useAuxiliaryImage = (getImageType().equalsIgnoreCase(ImageType.AUX.toString()))  ? "true" : "false";
    envMap.put("DO_AI", useAuxiliaryImage);

    if (getDomainType().equalsIgnoreCase(DomainType.WLS.toString())) {
      execTestScriptAndAssertSuccess("-update1", "Update1 use case failed");
    } else if (getDomainType().equalsIgnoreCase(DomainType.JRF.toString())) {
      execTestScriptAndAssertSuccess(DomainType.JRF,"-update1", "Update1 use case failed");
    }
  }

  /**
   * Test MII sample WLS or JRF update2 use case.
   */
  public void callUpdate2UseCase() {
    String useAuxiliaryImage = (getImageType().equalsIgnoreCase(ImageType.AUX.toString()))  ? "true" : "false";
    envMap.put("DO_AI", useAuxiliaryImage);

    if (getDomainType().equalsIgnoreCase(DomainType.WLS.toString())) {
      execTestScriptAndAssertSuccess("-update2", "Update2 use case failed");
    } else if (getDomainType().equalsIgnoreCase(DomainType.JRF.toString())) {
      execTestScriptAndAssertSuccess(DomainType.JRF,"-update2", "Update2 use case failed");
    }
  }

  /**
   * Test MII sample WLS or JRF update3 use case.
   */
  public void callUpdate3UseCase() {
    String useAuxiliaryImage = (getImageType().equalsIgnoreCase(ImageType.AUX.toString()))  ? "true" : "false";
    String imageName = (getDomainType().equalsIgnoreCase(DomainType.WLS.toString()))
        ? MII_SAMPLE_WLS_IMAGE_NAME_V2 : MII_SAMPLE_JRF_IMAGE_NAME_V2;
    envMap.put("MODEL_IMAGE_NAME", imageName);
    envMap.put("DO_AI", useAuxiliaryImage);

    if (getDomainType().equalsIgnoreCase(DomainType.WLS.toString())) {
      execTestScriptAndAssertSuccess("-update3-image,-check-image-and-push,-update3-main", "Update3 use case failed");
    } else if (getDomainType().equalsIgnoreCase(DomainType.JRF.toString())) {
      execTestScriptAndAssertSuccess(
          DomainType.JRF,
          "-update3-image,-check-image-and-push,-update3-main",
          "Update3 use case failed"
      );
    }
  }

  /**
   * Test MII sample WLS or JRF update4 use case.
   */
  public void callUpdate4UseCase() {
    String useAuxiliaryImage = (getImageType().equalsIgnoreCase(ImageType.AUX.toString()))  ? "true" : "false";
    envMap.put("DO_AI", useAuxiliaryImage);

    if (getDomainType().equalsIgnoreCase(DomainType.WLS.toString())) {
      execTestScriptAndAssertSuccess("-update4", "Update4 use case failed");
    } else if (getDomainType().equalsIgnoreCase(DomainType.JRF.toString())) {
      execTestScriptAndAssertSuccess(DomainType.JRF,"-update4", "Update4 use case failed");
    }
  }
}
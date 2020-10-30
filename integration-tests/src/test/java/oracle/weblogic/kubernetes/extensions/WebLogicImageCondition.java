// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_LATEST_IMAGE_TAG;
import static oracle.weblogic.kubernetes.TestConstants.WLS_UPDATE_IMAGE_TAG;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;

/**
 *
 *  JUnit5 extension class to implement ExecutionCondition for the custom
 *  annotation @AssumeWebLogicImage.
 */
public class WebLogicImageCondition implements ExecutionCondition {

  /**
   * Determine if the the test "testUpdateImageName" will be skipped based on WebLogic image tag.
   * Skip the test if the image tag is the latest one.
   * @param context the current extension context
   * @return ConditionEvaluationResult disabled if the image tag is the latest one, enabled if the
   * image tag is not the latest one
   */
  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (WEBLOGIC_IMAGE_TAG.equals(WLS_LATEST_IMAGE_TAG)) {
      getLogger().info("WebLogic image tag is {0}. No latest image available to continue test. Skipping test",
          WLS_LATEST_IMAGE_TAG);
      return ConditionEvaluationResult
          .disabled(String.format("No latest image available to continue test. Skipping test!"));
    } else {
      getLogger().info("Updating image to {0}. Continuing test!", WLS_UPDATE_IMAGE_TAG);
      return ConditionEvaluationResult
          .enabled(String.format("Updating image to {0}. Continuing test!", WLS_UPDATE_IMAGE_TAG));
    }
  }
}

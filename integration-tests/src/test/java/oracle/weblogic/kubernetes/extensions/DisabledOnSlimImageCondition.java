// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_SLIM;

public class DisabledOnSlimImageCondition implements ExecutionCondition {

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (WEBLOGIC_SLIM) {
      return ConditionEvaluationResult.disabled("Test disabled on WebLogic slim image");
    } else {
      return ConditionEvaluationResult.enabled("Test enabled on full WebLogic image");
    }
  }
}
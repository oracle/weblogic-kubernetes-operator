// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import static oracle.weblogic.kubernetes.TestConstants.WEBLOGIC_12213;

public class DisabledOn12213ImageCondition implements ExecutionCondition {

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    // disable the test because of application deployment through REST interface issue
    // and is included in WebLogic 12.2.1.3.0 CPU's"
    if (WEBLOGIC_12213) {
      return ConditionEvaluationResult.disabled("Test disabled on WebLogic 12.2.1.3 image");
    } else {
      return ConditionEvaluationResult.enabled("Test enabled on non-12.2.1.3 WebLogic image");
    }
  }
}
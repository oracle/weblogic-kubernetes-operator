// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public interface LoggedTest {
  Logger logger = Logger.getLogger(LoggedTest.class.getName());

  @BeforeEach
  default void beforeEachTest(TestInfo testInfo) {
    logger.info(() -> String.format("About to execute [%s] in %s",
        testInfo.getDisplayName(),
        getMethodName(testInfo)));
  }

  @AfterEach
  default void afterEachTest(TestInfo testInfo) {
    logger.info(() -> String.format("Finished executing [%s] in %s",
        testInfo.getDisplayName(),
        getMethodName(testInfo)));
  }

  private String getMethodName(TestInfo testInfo) {
    if (testInfo.getTestMethod().isPresent()) {
      String[] tempMethodName = testInfo.getTestMethod().get().toString().split(" ");
      return tempMethodName[tempMethodName.length - 1];
    } else {
      return "NO_METHOD";
    }
  }
}

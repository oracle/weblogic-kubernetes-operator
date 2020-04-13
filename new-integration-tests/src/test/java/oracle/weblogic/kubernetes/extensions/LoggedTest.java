// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public interface LoggedTest {
  LoggingFacade logger = LoggingFactory.getLogger(LoggedTest.class);

  @BeforeEach
  default void beforeEachTest(TestInfo testInfo) {
    logger.info("About to execute [{0}] in {1}", testInfo.getDisplayName(), getMethodName(testInfo));
  }

  @AfterEach
  default void afterEachTest(TestInfo testInfo) {
    logger.info("Finished executing [{0}] in {1}", testInfo.getDisplayName(), getMethodName(testInfo));
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

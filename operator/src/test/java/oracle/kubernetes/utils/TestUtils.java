// Copyright (c) 2018, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

import oracle.kubernetes.common.utils.BaseTestUtils;
import oracle.kubernetes.operator.logging.LoggingFactory;

import static com.meterware.simplestub.Stub.createStub;

public class TestUtils extends BaseTestUtils {
  /**
   * Removes the console handlers from the specified logger, in order to silence them during a test.
   *
   * @return a collection of the removed handlers
   */
  public static ConsoleHandlerMemento silenceOperatorLogger() {
    Logger logger = LoggingFactory.getLogger("Operator", "Operator").getUnderlyingLogger();
    Arrays.stream(logger.getHandlers()).filter(BaseTestUtils::isTestLogHandler).forEach(BaseTestUtils::rejectCall);
    List<Handler> savedHandlers = new ArrayList<>();
    for (Handler handler : logger.getHandlers()) {
      if (handler instanceof ConsoleHandler) {
        savedHandlers.add(handler);
      }
    }

    for (Handler handler : savedHandlers) {
      logger.removeHandler(handler);
    }

    TestLogHandler testHandler = createStub(TestLogHandler.class);
    logger.addHandler(testHandler);

    return new ConsoleHandlerMemento(logger, testHandler, savedHandlers);
  }

}

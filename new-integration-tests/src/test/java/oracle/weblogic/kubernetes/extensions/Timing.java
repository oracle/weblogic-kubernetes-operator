// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.lang.reflect.Method;

import oracle.weblogic.kubernetes.logging.LoggingFacade;
import oracle.weblogic.kubernetes.logging.LoggingFactory;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

public class Timing implements BeforeTestExecutionCallback, AfterTestExecutionCallback {
  private static final LoggingFacade logger = LoggingFactory.getLogger(Timing.class);

  private static final String START_TIME = "start time";

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    getStore(context).put(START_TIME, System.currentTimeMillis());
  }

  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    Method testMethod = context.getRequiredTestMethod();
    long startTime = getStore(context).remove(START_TIME, long.class);
    long duration = System.currentTimeMillis() - startTime;

    logger.info("Method [{0}] took {1} ms.", testMethod.getName(), duration);
  }

  private Store getStore(ExtensionContext context) {
    return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestMethod()));
  }
}

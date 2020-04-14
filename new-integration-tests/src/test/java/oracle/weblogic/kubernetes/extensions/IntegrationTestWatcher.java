// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.utils.LoggingUtil;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;


public class IntegrationTestWatcher  implements
    TestWatcher,
    BeforeAllCallback,
    AfterAllCallback,
    AfterEachCallback,
    AfterTestExecutionCallback,
    InvocationInterceptor,
    TestExecutionExceptionHandler {

  private Namespace methodNamespace;
  private Store globalStore;
  private Store testStore;
  private String className;
  private String methodName;
  private static final String DIAG_LOGS_DIR = System.getProperty("java.io.tmpdir");

  @Override
  public void beforeAll(ExtensionContext context) {
    className = context.getTestClass().get().getName();
    globalStore = context.getStore(Namespace.GLOBAL);
    globalStore.put("BEFOREALL", Boolean.FALSE);
    logger.info(getHeader("Starting Test Suite   ", className, "+"));
    logger.info(getHeader("Starting beforeAll for ", className, "-"));
  }

  @Override
  public void interceptBeforeEachMethod(
      Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    methodName = context.getRequiredTestMethod().getName();
    methodNamespace = Namespace.create(methodName);
    testStore = context.getStore(methodNamespace);

    // if execution reaches here the beforeAll is succeeded.
    globalStore.put("BEFOREALL", Boolean.TRUE);
    // assume beforeEach is failed and set it to true if execution reaches test
    testStore.put("BEFOREEACH", Boolean.FALSE);
    logger.info(getHeader("Starting beforeEach for ", className + "." + methodName, "-"));
    invocation.proceed();
  }

  @Override
  public void interceptTestMethod​(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    logger.info(getHeader("Ending beforeEach for ", className + "." + methodName, "-"));
    // if execution reaches here the beforeEach is succeeded.
    testStore.put("BEFOREEACH", Boolean.TRUE);
    // assume the test is passed and set it to false in handleTestExecutionException​ if it fails
    testStore.put("TEST", Boolean.TRUE);
    logger.info(getHeader("Starting Test   ", className + "." + methodName, "-"));
    invocation.proceed();
  }

  @Override
  public void handleTestExecutionException​(ExtensionContext context, Throwable throwable) throws Throwable {
    testStore.put("TEST", Boolean.FALSE);
    logger.info(getHeader("Test failed   ", className + "." + methodName, "!"));
    logger.info("Collect logs...");
    collectLogs(context, "test");
    throw throwable;
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
    logger.info(getHeader("Ending Test   ", className + "." + methodName, "-"));
  }

  @Override
  public void interceptAfterEachMethod(
      Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    // if BEFOREEACH is false then beforeEach failed, test was not run
    if (!(Boolean) testStore.get("BEFOREEACH")) {
      logger.info("beforeEach failed for test " + className + "." + methodName);
      logger.info("skipped test " + className + "." + methodName);
      logger.info("Collecting logs...");
      collectLogs(context, "beforeEach");
    }
    logger.info(getHeader("Starting afterEach for ", className + "." + methodName, "-"));
    invocation.proceed();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    logger.info(getHeader("Ending afterEach for ", className + "." + methodName, "-"));
  }

  @Override
  public void interceptAfterAllMethod(
      Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    logger.info(getHeader("Starting afterAll for  ", className, "-"));
    if (!(Boolean) globalStore.get("BEFOREALL")) {
      logger.info("beforeAll failed for class " + className);
      logger.info("Collecting logs...");
      collectLogs(context, "beforeAll");
    }
    invocation.proceed();
  }

  @Override
  public void afterAll(ExtensionContext context) {
    if (context.getExecutionException().isPresent()) {
      logger.info("Exception thrown, collecting logs...");
      collectLogs(context, "afterAll");
    }
    logger.info(getHeader("Ending Test Suite  ", className, "+"));
  }

  @Override
  public void testSuccessful(ExtensionContext extensionContext) {
    logger.info(getHeader("Test passed  ", className + "." + methodName, "-"));
  }

  @Override
  public void testFailed(ExtensionContext context, Throwable throwable) {
    // if both beforeEach and Test is passed then execution must have failed in afterEach
    if ((Boolean) testStore.get("BEFOREEACH") && (Boolean) testStore.get("TEST")) {
      logger.info("afterEach failed for test " + methodName);
      logger.info("Collecting logs...");
      collectLogs(context, "afterEach");
    }
    logger.info(getHeader("Test failed  ", className + "." + methodName, "-"));
  }

  private void collectLogs(ExtensionContext extensionContext, String failedStage) {
    Path resultDir = null;
    try {
      resultDir = Files.createDirectories(
          Paths.get(
              DIAG_LOGS_DIR,
              extensionContext.getRequiredTestClass().getSimpleName(),
              getExtDir(failedStage)));
    } catch (IOException ex) {
      logger.warning(ex.getMessage());
    }
    try {
      for (var namespace : LoggingUtil.getNamespaceList(extensionContext.getRequiredTestInstance())) {
        LoggingUtil.generateLog((String)namespace, resultDir);
      }
    } catch (IllegalArgumentException | IllegalAccessException | IOException | ApiException ex) {
      logger.warning(ex.getMessage());
    }
  }

  private String getExtDir(String failedStage) {
    String ext;
    switch (failedStage) {
      case "beforeEach":
      case "afterEach":
        ext = methodName + "_" + failedStage;
        break;
      case "test":
        ext = methodName;
        break;
      default:
        ext = failedStage;
    }
    return ext;
  }

  private String getHeader(String header, String name, String rc) {
    String line = header + "   " + name;
    return "\n" + rc.repeat(line.length()) + "\n" + line + "\n" + rc.repeat(line.length()) + "\n";
  }
}
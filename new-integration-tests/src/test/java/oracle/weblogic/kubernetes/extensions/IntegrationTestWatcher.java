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
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;

import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;

/**
 * JUnit5 extension class to intercept test execution at various
 * levels and collect logs in Kubernetes cluster for various artifacts
 * in the namespace used by the tests. The tests has to tag their classes
 * with @ExtendWith(IntegrationTestWatcher.class)
 */
public class IntegrationTestWatcher  implements
    TestWatcher,
    BeforeAllCallback,
    AfterAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    InvocationInterceptor,
    BeforeTestExecutionCallback,
    TestExecutionExceptionHandler,
    LifecycleMethodExecutionExceptionHandler {

  private String className;
  private String methodName;
  private static final String DIAG_LOGS_DIR = System.getProperty("java.io.tmpdir");


  /**
   * When a integration test suite starts to execute, this method gets called first.
   * Prints log messages to separate the beforeAll methods.
   * @param context the current extension context
   */
  @Override
  public void beforeAll(ExtensionContext context) {
    className = context.getRequiredTestClass().getName();
    logger.info(getHeader("Starting Test Suite   ", className, "+"));
    logger.info(getHeader("Starting beforeAll for ", className, "-"));
  }

  /**
   * 
   * @param context
   * @param throwable
   * @throws Throwable
   */
  @Override
  public void handleBeforeAllMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    logger.info(getHeader("BeforeAll failed   ", className, "!"));
    collectLogs(context, "beforeAll");
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    methodName = context.getRequiredTestMethod().getName();
    logger.info(getHeader("Starting beforeEach for ", className + "." + methodName, "-"));
  }

  @Override
  public void handleBeforeEachMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    logger.info(getHeader("BeforeEach failed   ", className + "." + methodName, "!"));
    collectLogs(context, "beforeEach");
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) {
    logger.info(getHeader("Ending beoforeEach for ", className + "." + methodName, "-"));
  }

  /**
   * Intercept the invocation of a @Test method.
   * Prints log messages to separate the test methods.
   * At this point the test passed beforeEach setup.
   * @param invocation the invocation that is being intercepted
   * @param invocationContext  the context of the invocation that is being intercepted
   * @param context the current extension context
   * @throws Throwable in case of failures
   */
  @Override
  public void interceptTestMethod​(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    logger.info(getHeader("Starting Test   ", className + "." + methodName, "-"));
    invocation.proceed();
  }

  @Override
  public void handleTestExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    logger.info(getHeader("Test failed   ", className + "." + methodName, "!"));
    collectLogs(context, "test");
    throw throwable;
  }

  @Override
  public void interceptAfterEachMethod​(InvocationInterceptor.Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext extensionContext) throws Throwable {
    logger.info(getHeader("Starting afterEach  ", className + "." + methodName, "-"));
    invocation.proceed();
  }

  @Override
  public void afterEach(ExtensionContext context) {
    logger.info(getHeader("Ending afterEach for ", className + "." + methodName, "-"));
  }

  @Override
  public void handleAfterEachMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    logger.info(getHeader("AfterEach failed   ", className + "." + methodName, "!"));
    collectLogs(context, "afterEach");
  }

  @Override
  public void interceptAfterAllMethod​(InvocationInterceptor.Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext extensionContext) throws Throwable {
    logger.info(getHeader("Starting afterAll  ", className, "-"));
    invocation.proceed();
  }

  @Override
  public void afterAll(ExtensionContext context) {
    logger.info(getHeader("Ending Test Suite  ", className, "+"));
  }


  @Override
  public void handleAfterAllMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    logger.info(getHeader("AfterAll failed   ", className, "!"));
    logger.info(throwable.getMessage());
    collectLogs(context, "afterAll");

  }

  private void collectLogs(ExtensionContext extensionContext, String failedStage) {
    logger.info("Collecting logs...");
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
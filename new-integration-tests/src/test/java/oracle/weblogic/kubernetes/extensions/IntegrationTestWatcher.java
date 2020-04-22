// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.extensions;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.utils.LoggingUtil;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.LifecycleMethodExecutionExceptionHandler;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.junit.jupiter.api.extension.TestWatcher;

import static oracle.weblogic.kubernetes.actions.TestActions.createUniqueNamespace;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * JUnit5 extension class to intercept test execution at various
 * levels and collect logs in Kubernetes cluster for all artifacts
 * in the namespace used by the tests. The tests has to tag their classes
 * with @ExtendWith(IntegrationTestWatcher.class) for the automatic log
 * collection to work.
 */
public class IntegrationTestWatcher implements
    AfterAllCallback,
    AfterEachCallback,
    AfterTestExecutionCallback,
    BeforeAllCallback,
    BeforeEachCallback,
    BeforeTestExecutionCallback,
    InvocationInterceptor,
    LifecycleMethodExecutionExceptionHandler,
    ParameterResolver,
    TestExecutionExceptionHandler,
    TestWatcher {

  private String className;
  private String methodName;
  private List namespaces = null;
  private static final String START_TIME = "start time";

  /**
   * Directory to store logs.
   */
  private static final String LOGS_DIR = System.getProperty("RESULT_ROOT",
        System.getProperty("java.io.tmpdir"));

  /**
   * Determine if this resolver supports resolution of an argument for the
   * Parameter in the supplied ParameterContext for the supplied ExtensionContext.
   * @param parameterContext the context for the parameter for which an argument should be resolved
   * @param extensionContext the current extension context
   * @return true if this resolver can resolve an argument for the parameter
   * @throws ParameterResolutionException when parameter resolution fails
   */
  @Override
  public boolean supportsParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    return parameterContext.getParameter().getType() == List.class;
  }

  /**
   * Resolve an argument for the Parameter in the supplied ParameterContext for the supplied ExtensionContext.
   * @param parameterContext the context for the parameter for which an argument should be resolved
   * @param extensionContext the extension context for the Executable about to be invoked
   * @return Object the resolved argument for the parameter
   * @throws ParameterResolutionException Thrown if an error is encountered in the execution of a ParameterResolver.
   */
  @Override
  public Object resolveParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    Namespaces ns = parameterContext.findAnnotation(Namespaces.class).get();
    List<String> namespaces = new ArrayList();
    for (int i = 1; i <= ns.value(); i++) {
      String namespace = assertDoesNotThrow(() -> createUniqueNamespace(),
          "Failed to create unique namespace due to ApiException");
      namespaces.add(namespace);
      logger.info("Created a new namespace called {0}", namespace);
    }
    if (this.namespaces == null) {
      this.namespaces = namespaces;
    } else {
      this.namespaces.addAll(namespaces);
    }
    logger.info(this.namespaces.toString());
    return namespaces;
  }

  /**
   * Prints log messages to separate the beforeAll methods.
   * @param context the current extension context
   */
  @Override
  public void beforeAll(ExtensionContext context) {
    className = context.getRequiredTestClass().getName();

    printHeader(String.format("Starting Test Suite %s", className), "+");
    printHeader(String.format("Starting beforeAll for %s", className), "-");
  }

  /**
   * Gets called when any exception is thrown in beforeAll and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleBeforeAllMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("BeforeAll failed %s", className), "!");
    collectLogs(context, "beforeAll");
  }

  /**
   * Prints log message to separate the beforeEach messages.
   * @param context the current extension context
   */
  @Override
  public void beforeEach(ExtensionContext context) {
    String[] tempMethodName = context.getRequiredTestMethod().toString().split(" ");
    methodName = tempMethodName[tempMethodName.length - 1];
    printHeader(String.format("Starting beforeEach for %s", methodName), "-");
  }

  /**
   * Gets called when any exception is thrown in beforeEach and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleBeforeEachMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("BeforeEach failed for %s", methodName), "!");
    collectLogs(context, "beforeEach");
  }

  /**
   * Prints log messages to mark the beginning of test method execution.
   * @param context the current extension context
   * @throws Exception when store interaction fails
   */

  @Override
  public void beforeTestExecution(ExtensionContext context) throws Exception {
    printHeader(String.format("Ending beforeEach for %s", methodName), "-");
    logger.info("About to execute [{0}] in {1}", context.getDisplayName(), methodName);
    getStore(context).put(START_TIME, System.currentTimeMillis());
  }

  /**
   * Prints log messages to mark the end of test method execution.
   * @param context the current extension context
   * @throws Exception when store interaction fails
   */
  @Override
  public void afterTestExecution(ExtensionContext context) throws Exception {
    Method testMethod = context.getRequiredTestMethod();
    long startTime = getStore(context).remove(START_TIME, long.class);
    long duration = System.currentTimeMillis() - startTime;
    logger.info("Finished executing [{0}] {1}", context.getDisplayName(), methodName);
    logger.info("Method [{0}] took {1} ms.", testMethod.getName(), duration);
  }

  private ExtensionContext.Store getStore(ExtensionContext context) {
    return context.getStore(ExtensionContext.Namespace.create(getClass(), context.getRequiredTestMethod()));
  }

  /**
   * Intercept the invocation of a @Test method.
   * Prints log messages to separate the test method logs.
   * @param invocation the invocation that is being intercepted
   * @param invocationContext  the context of the invocation that is being intercepted
   * @param context the current extension context
   * @throws Throwable in case of failures
   */
  @Override
  public void interceptTestMethod​(Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    printHeader(String.format("Starting Test %s", methodName), "-");
    invocation.proceed();
  }

  /**
   * Gets called when any exception is thrown in test and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleTestExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("Test failed %s", methodName), "!");
    collectLogs(context, "test");
    throw throwable;
  }

  /**
   * Intercept the invocation of a @AfterEach method.
   * Prints log messages to separate the afterEach method logs.
   * @param invocation the invocation that is being intercepted
   * @param invocationContext  the context of the invocation that is being intercepted
   * @param context the current extension context
   * @throws Throwable in case of failures
   */
  @Override
  public void interceptAfterEachMethod​(InvocationInterceptor.Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    printHeader(String.format("Starting afterEach for %s", methodName), "-");
    invocation.proceed();
  }

  /**
   * Prints log message to mark the end of afterEach methods.
   * @param context the current extension context
   */
  @Override
  public void afterEach(ExtensionContext context) {
    printHeader(String.format("Ending afterEach for %s", methodName), "-");
  }

  /**
   * Gets called when any exception is thrown in afterEach and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleAfterEachMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("AfterEach failed for %s", methodName), "!");
    collectLogs(context, "afterEach");
  }

  /**
   * Called when the test method is successful.
   * @param context the current extension context
   */
  @Override
  public void testSuccessful(ExtensionContext context) {
    printHeader(String.format("Test PASSED %s", methodName), "+");
  }

  /**
   * Called when the test method fails.
   * @param context the current extension context
   * @param cause of failures throwable
   */
  @Override
  public void testFailed(ExtensionContext context, Throwable cause) {
    printHeader(String.format("Test FAILED %s", methodName), "!");
  }

  /**
   * Intercept the invocation of a @AfterAll method.
   * Prints log messages to separate the afterAll method logs.
   * @param invocation the invocation that is being intercepted
   * @param invocationContext  the context of the invocation that is being intercepted
   * @param context the current extension context
   * @throws Throwable in case of failures
   */
  @Override
  public void interceptAfterAllMethod​(InvocationInterceptor.Invocation<Void> invocation,
      ReflectiveInvocationContext<Method> invocationContext,
      ExtensionContext context) throws Throwable {
    printHeader(String.format("Starting afterAll for %s", className), "-");
    invocation.proceed();
  }

  /**
   * Prints log message to mark end of test suite.
   * @param context the current extension context
   */
  @Override
  public void afterAll(ExtensionContext context) {
    printHeader(String.format("Ending Test Suite %s", className), "+");
  }


  /**
   * Gets called when any exception is thrown in afterAll and collects logs.
   * @param context current extension context
   * @param throwable to handle
   * @throws Throwable in case of failures
   */
  @Override
  public void handleAfterAllMethodExecutionException​(ExtensionContext context, Throwable throwable)
      throws Throwable {
    printHeader(String.format("AfterAll failed for %s", className), "!");
    collectLogs(context, "afterAll");
  }

  /**
   * Collects logs in namespaces used by the current running test and writes in the LOGS_DIR.
   * @param extensionContext current extension context
   * @param failedStage the stage in which the test failed
   */
  private void collectLogs(ExtensionContext extensionContext, String failedStage) {
    logger.info("Collecting logs...");
    if (namespaces == null || namespaces.isEmpty()) {
      logger.warning("Namespace list is empty, "
          + "see if the methods in the tests is(are) annotated with Namespaces(<n>)");
      return;
    }
    Path resultDir = null;
    try {
      resultDir = Files.createDirectories(Paths.get(LOGS_DIR,
              extensionContext.getRequiredTestClass().getSimpleName(),
              getExtDir(extensionContext, failedStage)));
    } catch (IOException ex) {
      logger.warning(ex.getMessage());
    }
    try {
      for (var namespace : namespaces) {
        LoggingUtil.generateLog((String)namespace, resultDir);
      }
    } catch (IOException | ApiException ex) {
      logger.warning(ex.getMessage());
    }
  }

  /**
   * Gets the extension name for the directory based on where the test failed.
   * @param failedStage the test execution failed stage
   * @return String extension directory name
   */
  private String getExtDir(ExtensionContext extensionContext, String failedStage) {
    String ext;
    switch (failedStage) {
      case "beforeEach":
      case "afterEach":
        ext = extensionContext.getRequiredTestMethod().getName() + "_" + failedStage;
        break;
      case "test":
        ext = extensionContext.getRequiredTestMethod().getName();
        break;
      default:
        ext = failedStage;
    }
    return ext;
  }

  /**
   * Print start/end/failure messages highlighted.
   * @param message to print
   * @param rc repeater string
   */
  private void printHeader(String message, String rc) {
    logger.info("\n" + rc.repeat(message.length()) + "\n" + message + "\n" + rc.repeat(message.length()) + "\n");
  }
}

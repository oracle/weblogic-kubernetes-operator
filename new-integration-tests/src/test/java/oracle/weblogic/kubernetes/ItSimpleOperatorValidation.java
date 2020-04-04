// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.Arrays;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.extensions.Timing;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.actions.TestActions.installOperator;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.operatorIsRunning;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


// this is a POC for a new way of writing tests.
// this is meant to be a simple test.  later i will add more complex tests and deal
// with parallelization and parameterization.
// this is build on standard JUnit 5, including JUnit 5 assertions, plus the 'awaitability'
// library for handling async operations, plus a library of our own test actions and assertions.
// the idea is that tests would *only* use these three things, and nothing else.
// so all of the reusable logic is in our "actions" and "assertions" packages.
// these in turn might depend on a set of "primitives" for things like running a helm command,
// running a kubectl command, and so on.
// tests would only call methods in TestActions and TestAssertions and never on an impl class
// hidden behind those.
// this is an example of a test suite (class) where the tests need to be run in a certain
// order. this is controlled with the TestMethodOrder annotation
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Simple validation of basic operator functions")
// this is an example of registering an extension that will time how long each test takes.
@ExtendWith(Timing.class)
// by implementing the LoggedTest, we will automatically get a logger injected and it
// will also automatically log entry/exit messages for each test method.
class ItSimpleOperatorValidation implements LoggedTest {

  @Test
  @Order(1)
  @DisplayName("Install the operator")
  // tags are used to filter which tests to run, we can define whatever tags we need,
  // like these two:
  @Slow
  @MustNotRunInParallel
  public void testInstallingOperator() {
    // this first example is an operation that we wait for.
    // installOperator() is one of our custom, reusable actions.
    // imagine that installOperator() will try to install the operator, by creating
    // the kubernetes deployment.  this will complete quickly, and will either be
    // successful or not.

    // get a new unique opNamespace
    final String opNamespace = assertDoesNotThrow(TestActions::createUniqueNamespace,
        "Failed to create unique namespace due to ApiException");
    logger.info(String.format("Got a new namespace called %s", opNamespace));

    final String domainNamespace1 = assertDoesNotThrow(TestActions::createUniqueNamespace,
        "Failed to create unique namespace due to ApiException");
    logger.info(String.format("Got a new namespace called %s", domainNamespace1));

    final String domainNamespace2 = assertDoesNotThrow(TestActions::createUniqueNamespace,
        "Failed to create unique namespace due to ApiException");
    logger.info(String.format("Got a new namespace called %s", domainNamespace2));

    // Create a service account for the unique opNamespace
    final String serviceAccountName = opNamespace + "-sa";
    assertDoesNotThrow(() -> TestActions.createServiceAccount(new V1ServiceAccount()
        .metadata(new V1ObjectMeta().namespace(opNamespace).name(serviceAccountName))));
    logger.info("Created service account: " + serviceAccountName);

    OperatorParams opParams =
        new OperatorParams().releaseName("weblogic-operator")
            .namespace(opNamespace)
            .chartDir("../kubernetes/charts/weblogic-operator")
            .image("phx.ocir.io/weblogick8s/weblogic-kubernetes-operator:develop")
            .domainNamespaces(Arrays.asList(domainNamespace1, domainNamespace2))
            .serviceAccount(serviceAccountName);

    try {
      assertThat(installOperator(opParams)).as("Operator installation failed").isTrue();
      logger.info(String.format("Operator installed in namespace %s", opNamespace));

      // we can use a standard JUnit assertion to check on the result
      // assertEquals(true, success, "Operator successfully installed in opNamespace " + opNamespace);

      // this is an example of waiting for an async operation to complete.
      // after the previous step was completed, kubernetes will try to pull the image,
      // start the pod, check the readiness/health probes, etc.  this will take some
      // period of time and either the operator will come to a running state, or it
      // will not.
      // in this example, we first wait 30 seconds, since it is unlikely this operation
      // will complete in less than 30 seconds, then we check if the operator is running.
      with().pollDelay(30, SECONDS)
          // we check again every 10 seconds.
          .and().with().pollInterval(10, SECONDS)
          // this listener lets us report some status with each poll
          .conditionEvaluationListener(
              condition -> logger.info(()
                  -> String.format("Waiting for operator to be running (elapsed time %dms, remaining time %dms)",
                  condition.getElapsedTimeInMS(),
                  condition.getRemainingTimeInMS())))
          // and here we can set the maximum time we are prepared to wait
          .await().atMost(5, MINUTES)
          // operatorIsRunning() is one of our custom, reusable assertions
          .until(operatorIsRunning(opNamespace));
    } finally {

      // uninstall operator release
      assertThat(TestActions.uninstallOperator(opParams))
          .as("Operator uninstall failed")
          .isTrue();

      // Delete service account from unique opNamespace
      assertDoesNotThrow(
          () -> TestActions.deleteServiceAccount(new V1ServiceAccount()
              .metadata(new V1ObjectMeta().namespace(opNamespace).name(serviceAccountName))));

      logger.info("Deleted service account " + serviceAccountName);

      // Delete domain namespaces
      assertDoesNotThrow(
          () -> TestActions.deleteNamespace(domainNamespace1));
      logger.info("Deleted namespace: " + domainNamespace1);
      assertDoesNotThrow(
          () -> TestActions.deleteNamespace(domainNamespace2));
      logger.info("Deleted namespace: " + domainNamespace2);

      // Delete opNamespace
      assertDoesNotThrow(
          () -> TestActions.deleteNamespace(opNamespace));
      logger.info("Deleted namespace: " + opNamespace);


    }
  }

}

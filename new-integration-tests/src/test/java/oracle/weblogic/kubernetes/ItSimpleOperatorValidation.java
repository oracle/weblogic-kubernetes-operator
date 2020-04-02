// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.assertions.impl.Domain;
import oracle.weblogic.kubernetes.assertions.impl.Kubernetes;
import oracle.weblogic.kubernetes.assertions.impl.Operator;
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
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.extensions.LoggedTest.logger;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    String domainNS = "itmodelinimageconfigupdate-domainns-1";
    String opns = "itmodelinimageconfigupdate-opns-1";
    String domainUid = "itmodelinimageconfigupdate-domain-2";
    String podName = "itmodelinimageconfigupdate-domain-2-managed-server2";
    HashMap label = new HashMap();
    label.put("weblogic.serverName", "managed-server2");
    try {
      Kubernetes.listPods(domainNS, null);
      assertTrue(Kubernetes.isPodExists(domainNS, domainUid, podName));
      assertTrue(Kubernetes.isPodRunning(domainNS, domainUid, podName));
      assertFalse(Kubernetes.isPodTerminating(domainNS, domainUid, podName));
      assertTrue(Kubernetes.isOperatorPodRunning(opns));
      assertTrue(Kubernetes.isServiceCreated(podName, label, domainNS));
      Kubernetes.listServices(domainNS, null);
      assertTrue(Operator.isExternalRestServiceCreated(opns));
      Domain.isCRDExists();
      with().pollDelay(30, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .conditionEvaluationListener(
            condition -> logger.info(() ->
                String.format(
                    "Waiting for domain to be running (elapsed time %dms, remaining time %dms)",
                    condition.getElapsedTimeInMS(),
                    condition.getRemainingTimeInMS())))
        // and here we can set the maximum time we are prepared to wait
        .await().atMost(5, MINUTES)
        // operatorIsRunning() is one of our custom, reusable assertions
        .until(domainExists(domainUid, domainNS));
    } catch (ApiException ex) {
      Logger.getLogger(ItSimpleOperatorValidation.class.getName()).log(Level.SEVERE, null, ex);
    } catch (Exception ex) {
      Logger.getLogger(ItSimpleOperatorValidation.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

}

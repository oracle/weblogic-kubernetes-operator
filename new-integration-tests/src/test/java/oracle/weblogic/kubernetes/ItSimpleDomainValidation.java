// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.createUniqueNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Simple validation of basic domain functions")
@IntegrationTest
class ItSimpleDomainValidation implements LoggedTest {

  @Test
  @DisplayName("Create a domain")
  @Slow
  public void testCreatingDomain() {

    String domainUID = "domain1";
    String domainYAML = "something";

    // get a new unique namespace
    String namespace = null;
    try {
      namespace = createUniqueNamespace();
      logger.info(String.format("Got a new namespace called %s", namespace));
    } catch (ApiException e) {
      // TODO: test in the calling method where we say something like
      //
      //  assert.DoesNotThrow(whatever(), ApiException.class, e,
      //    String.format("could not do whatever, got exception %s", e))
      //
      //  so we have the exception and we can print out a meaningful error message
    }

    // create the domain CR
    boolean success = createDomainCustomResource(domainUID, namespace, domainYAML);
    assertTrue(success);

    // wait for the domain to exist
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
        .until(domainExists(domainUID, namespace));

    // wait for the admin server pod to exist

    // wait for the managed servers to exist

    // Delete namespace
    try {
      if (deleteNamespace(namespace)) {
        logger.info("Deleted namespace: " + namespace);
      }
    } catch (Exception e) {
      // TODO: Fix as there is a known bug that delete can return either the object
      //  just deleted or a status.  We can workaround by either retrying or using
      //  the general GenericKubernetesApi client class and doing our own type checks
    }

  }

}

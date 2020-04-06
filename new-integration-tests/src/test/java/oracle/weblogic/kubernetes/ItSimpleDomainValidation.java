// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1ServiceAccount;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
    final String namespace = assertDoesNotThrow(TestActions::createUniqueNamespace,
        "Failed to create unique namespace due to ApiException");
    logger.info(String.format("Got a new namespace called %s", namespace));

    // Create a service account for the unique namespace
    final String serviceAccountName = namespace + "-sa";
    final V1ServiceAccount serviceAccount = assertDoesNotThrow(
        () -> Kubernetes.createServiceAccount(new V1ServiceAccount()
            .metadata(new V1ObjectMeta().namespace(namespace).name(serviceAccountName))));
    logger.info("Created service account: " + serviceAccount.getMetadata().getName());

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
        .until(domainExists(domainUID, "v7", namespace));

    // wait for the admin server pod to exist

    // wait for the managed servers to exist

    // Delete service account from unique namespace
    assertDoesNotThrow(
        () -> Kubernetes.deleteServiceAccount(serviceAccount));
    logger.info("Deleted service account \'" + serviceAccount.getMetadata().getName()
        + "' in namespace: " + serviceAccount.getMetadata().getNamespace());

    // Delete namespace
    assertDoesNotThrow(
        () -> TestActions.deleteNamespace(namespace));
    logger.info("Deleted namespace: " + namespace);
  }

}

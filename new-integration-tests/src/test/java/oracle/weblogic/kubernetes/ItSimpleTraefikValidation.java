// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.TraefikParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.HelmParams;
import oracle.weblogic.kubernetes.annotations.tags.MustNotRunInParallel;
import oracle.weblogic.kubernetes.annotations.tags.Slow;
import oracle.weblogic.kubernetes.extensions.LoggedTest;
import oracle.weblogic.kubernetes.extensions.Timing;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static oracle.weblogic.kubernetes.actions.TestActions.createUniqueNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.helmGetExpectedValues;
import static oracle.weblogic.kubernetes.actions.TestActions.helmList;
import static oracle.weblogic.kubernetes.actions.TestActions.installTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeTraefik;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.traefikIsRunning;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.with;

// this is an example of a test suite (class) where the tests need to be run in a certain
// order. this is controlled with the TestMethodOrder annotation
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Simple validation of Traefik functions")
@ExtendWith(Timing.class)
class ItSimpleTraefikValidation implements LoggedTest {

  private HelmParams tfHelmParams = null;
  private String tfNamespace = null;
  private String domainNamespace1 = null;

  @Test
  @Order(1)
  @DisplayName("Install the Traefik")
  // tags are used to filter which tests to run, we can define whatever tags we need,
  // like these two:
  @Slow
  @MustNotRunInParallel
  public void testInstallingTraefik() {

    // get a new unique traefik Namespace
    tfNamespace = createNamespace();
    logger.info(String.format("Created a new namespace called %s", tfNamespace));

    String projectRoot = System.getProperty("user.dir");

    // helm install parameters
    tfHelmParams = new HelmParams()
        .releaseName("traefik-operator")
        .namespace(tfNamespace)
        .repoUrl("https://kubernetes-charts.storage.googleapis.com/")
        .repoName("stable")
        .chartName("traefik")
        .chartValuesFile(projectRoot + "/../kubernetes/samples/charts/traefik/values.yaml");

    TraefikParams tfParams =
        new TraefikParams()
            .helmParams(tfHelmParams)
            .nameSpaces("{" + tfNamespace + "}");

    // install Traefik
    assertThat(installTraefik(tfParams))
        .as("Test installTraefik returns true")
        .withFailMessage("installTraefik() did not return true")
        .isTrue();
    logger.info(String.format("Traefik is installed in namespace %s", tfNamespace));

    assertThat(helmList(tfHelmParams))
        .as("Test helmList returns true")
        .withFailMessage("helmList() did not return true")
        .isTrue();

    // waiting for an async operation to complete.
    // we first wait 30 seconds, then we check if the traefik pod is running.
    with().pollDelay(30, SECONDS)
        // we check again every 10 seconds.
        .and().with().pollInterval(10, SECONDS)
        // this listener lets us report some status with each poll
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for operator to be running (elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        // and here we can set the maximum time we are prepared to wait
        .await().atMost(5, MINUTES)
        .until(traefikIsRunning(tfNamespace));
  }

  @Test
  @Order(2)
  @DisplayName("Upgrade the Traefik release")
  public void testUpgradingTraefik() {

    // create a new unique namespace for wls domain
    domainNamespace1 = createNamespace();
    logger.info(String.format("Created a new namespace called %s", domainNamespace1));

    // helm install parameters
    tfHelmParams = new HelmParams()
            .releaseName("traefik-operator")
            .namespace(tfNamespace)
            .chartDir("stable/traefik");

    TraefikParams tfParams =
            new TraefikParams()
                    .helmParams(tfHelmParams)
                    .nameSpaces("{" + tfNamespace + "," + domainNamespace1 + "}");

    // upgrade Traefik
    assertThat(upgradeTraefik(tfParams))
            .as("Test upgradeTraefik returns true")
            .withFailMessage("upgradeTraefik() did not return true")
            .isTrue();
    logger.info(String.format("Traefik is upgraded in namespace %s", tfNamespace));

    assertThat(helmGetExpectedValues(tfHelmParams, domainNamespace1))
            .as("Test helmGetExpectedValues returns true")
            .withFailMessage("helmGetExpectedValues() did not return true")
            .isTrue();
  }

  @AfterAll
  public void tearDown() {

    // uninstall traefik release
    if (tfHelmParams != null) {
      assertThat(uninstallTraefik(tfHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik() did not return true")
          .isTrue();
    }

    // Delete tfNamespace
    if (tfNamespace != null) {
      assertThatCode(
          () -> deleteNamespace(tfNamespace))
          .as("Test that deleteNamespace does not throw an exception")
          .withFailMessage("deleteNamespace() threw an exception")
          .doesNotThrowAnyException();
      logger.info("Deleted namespace: {0}", tfNamespace);
    }

    // delete domainNamespace1
    if (domainNamespace1 != null) {
      assertThatCode(
          () -> deleteNamespace(domainNamespace1))
          .as("Test that deleteNamespace does not throw an exception")
          .withFailMessage("deleteNamespace() threw an exception")
          .doesNotThrowAnyException();
      logger.info("Deleted namespace: {0}", domainNamespace1);
    }
  }

  private String createNamespace() {
    String namespace = null;
    try {
      namespace = createUniqueNamespace();
    } catch (Exception e) {
      e.printStackTrace();
      assertThat(e)
          .as("Test that createUniqueNamespace does not throw an exception")
          .withFailMessage("createUniqueNamespace() threw an unexpected exception")
          .isNotInstanceOf(ApiException.class);
    }
    return namespace;
  }

}

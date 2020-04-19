// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

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
import static oracle.weblogic.kubernetes.actions.TestActions.createIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.createUniqueNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.getExpectedResult;
import static oracle.weblogic.kubernetes.actions.TestActions.installTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallIngress;
import static oracle.weblogic.kubernetes.actions.TestActions.uninstallTraefik;
import static oracle.weblogic.kubernetes.actions.TestActions.upgradeTraefik;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.isTraefikReady;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.with;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;


// this is a simple traefik validation test
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Simple validation of Traefik functions")
@ExtendWith(Timing.class)
class ItSimpleTraefikValidation implements LoggedTest {

  private HelmParams tfHelmParams = null;
  private HelmParams ingressParam = null;
  private String tfNamespace = null;
  private String domainNamespace1 = null;
  private String projectRoot = System.getProperty("user.dir");

  @Test
  @Order(1)
  @DisplayName("Install the Traefik")
  @Slow
  @MustNotRunInParallel
  public void testInstallingTraefik() {

    // create a new unique namespace for traefik
    logger.info("Creating an unique namespace for Traefil");
    tfNamespace = assertDoesNotThrow(() -> createUniqueNamespace(),
            "Fail to create an unique namespace due to ApiException");
    logger.info("Created a new namespace called {0}", tfNamespace);

    // helm install parameters
    tfHelmParams = new HelmParams()
        .releaseName("traefik-operator")
        .namespace(tfNamespace)
        .repoUrl("https://kubernetes-charts.storage.googleapis.com/")
        .repoName("stable")
        .chartName("traefik")
        .chartValuesFile(projectRoot + "/../kubernetes/samples/charts/traefik/values.yaml");

    TraefikParams tfParams = new TraefikParams()
        .helmParams(tfHelmParams)
        .nameSpaces("{" + tfNamespace + "}");

    // install Traefik
    assertThat(installTraefik(tfParams))
        .as("Test installTraefik returns true")
        .withFailMessage("installTraefik() did not return true")
        .isTrue();
    logger.info("Traefik is installed in namespace {0}", tfNamespace);

    // verify that the trafik is installed
    String cmd = "helm ls -n " + tfNamespace;
    assertThat(getExpectedResult(cmd, tfHelmParams.getReleaseName()))
        .as("traefik is installed")
        .withFailMessage("did not find traefik-operator in " + tfNamespace)
        .isTrue();

    // first wait 30 seconds, then check if the traefik pod is ready.
    with().pollDelay(30, SECONDS)
        .and().with().pollInterval(10, SECONDS)
        .conditionEvaluationListener(
            condition -> logger.info(
                "Waiting for traefik to be ready (elapsed time {0}ms, remaining time {1}ms)",
                condition.getElapsedTimeInMS(),
                condition.getRemainingTimeInMS()))
        .await().atMost(5, MINUTES)
        .until(isTraefikReady(tfNamespace));
  }

  @Test
  @Order(2)
  @DisplayName("Upgrade the Traefik release")
  public void testUpgradingTraefik() {

    // create a new unique namespace for wls domain
    logger.info("Creating an unique namespace for wls domain");
    domainNamespace1 = assertDoesNotThrow(() -> createUniqueNamespace(),
            "Failed to create an unique namespace due to ApiException");
    logger.info("Created a new namespace called {0}", domainNamespace1);

    // helm install parameters
    tfHelmParams = new HelmParams()
            .releaseName("traefik-operator")
            .namespace(tfNamespace)
            .chartDir("stable/traefik");

    TraefikParams tfParams = new TraefikParams()
            .helmParams(tfHelmParams)
            .nameSpaces("{" + tfNamespace + "," + domainNamespace1 + "}");

    // upgrade Traefik
    assertThat(upgradeTraefik(tfParams))
            .as("Test upgradeTraefik returns true")
            .withFailMessage("upgradeTraefik() did not return true")
            .isTrue();
    logger.info("Traefik is upgraded in namespace {0}", tfNamespace);

    // verify the value of the release 'kubernetes.namespaces' got updated
    String helmCmd = String.format("helm get values %1s -n %2s", tfHelmParams.getReleaseName(), tfNamespace);
    assertThat(getExpectedResult(helmCmd, domainNamespace1))
            .as("Get the expected value")
            .withFailMessage("Did not get the expected value")
            .isTrue();
  }

  @Test
  @Order(3)
  @DisplayName("Create an ingress per domain")
  public void testCreatingIngress() {
    // helm install parameters for ingress
    ingressParam = new HelmParams()
            .releaseName("sample-domain1-ingress")
            .namespace(domainNamespace1)
            .chartDir(projectRoot + "/../kubernetes/samples/charts/ingress-per-domain");

    assertThat(createIngress(ingressParam, "sample-domain1", "sample-domain1.org"))
            .as("Test createIngress returns true")
            .withFailMessage("createIngress() did not return true")
            .isTrue();
    logger.info("ingress is created in namespace {0}", domainNamespace1);

    // check the ingress is created
    String cmd = "kubectl get ingress -n " + domainNamespace1;
    assertThat(getExpectedResult(cmd, "sample-domain1-traefik"))
            .as("ingress is created")
            .withFailMessage("ingress is not created in namespace " + domainNamespace1)
            .isTrue();
  }

  @AfterAll
  public void tearDown() {

    // uninstall ingress
    if (ingressParam != null) {
      assertThat(uninstallIngress(ingressParam))
              .as("Test uninstallIngress returns true")
              .withFailMessage("uninstallIngress() did not return true")
              .isTrue();
    }

    // uninstall traefik release
    if (tfHelmParams != null) {
      assertThat(uninstallTraefik(tfHelmParams))
          .as("Test uninstallTraefik returns true")
          .withFailMessage("uninstallTraefik() did not return true")
          .isTrue();
    }

    // Delete traefik Namespace
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

}

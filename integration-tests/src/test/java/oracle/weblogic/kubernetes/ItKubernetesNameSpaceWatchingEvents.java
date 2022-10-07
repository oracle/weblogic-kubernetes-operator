// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import io.kubernetes.client.openapi.ApiException;
import oracle.weblogic.kubernetes.actions.impl.OperatorParams;
import oracle.weblogic.kubernetes.actions.impl.primitive.Command;
import oracle.weblogic.kubernetes.actions.impl.primitive.CommandParams;
import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static oracle.weblogic.kubernetes.TestConstants.KUBERNETES_CLI;
import static oracle.weblogic.kubernetes.actions.TestActions.createNamespace;
import static oracle.weblogic.kubernetes.actions.TestActions.now;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ImageUtils.createBaseRepoSecret;
import static oracle.weblogic.kubernetes.utils.K8sEvents.NAMESPACE_WATCHING_STARTED;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEvent;
import static oracle.weblogic.kubernetes.utils.K8sEvents.checkDomainEventWatchingStopped;
import static oracle.weblogic.kubernetes.utils.K8sEvents.domainEventExists;
import static oracle.weblogic.kubernetes.utils.OKDUtils.createRouteForOKD;
import static oracle.weblogic.kubernetes.utils.OKDUtils.setTlsTerminationForRoute;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.upgradeAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests related to Domain events logged by operator.
 * The tests checks for the following events in the domain name space.
 * NamespaceWatchingStarted, and NamespaceWatchingStopped.
 */
@DisplayName("Verify the Kubernetes events for watching namespace")
@IntegrationTest
@Tag("olcne")
@Tag("oke-parallel")
@Tag("kind-parallel")
class ItKubernetesNameSpaceWatchingEvents {

  private static String opNamespace = null;
  private static String domainNamespace1 = null;
  private static String domainNamespace2 = null;
  private static String domainNamespace3 = null;
  private static String domainNamespace4 = null;
  private static String domainNamespace5 = null;
  private static String opServiceAccount = null;
  private static OperatorParams opParams = null;
  private static LoggingFacade logger = null;
  private static OperatorParams opParamsOriginal = null;

  /**
   * Assigns unique namespaces for operator and domains.
   * Pull WebLogic image if running tests in Kind cluster.
   * Installs operator.
   *
   * @param namespaces injected by JUnit
   */
  @BeforeAll
  public static void initAll(@Namespaces(6) List<String> namespaces) {
    logger = getLogger();
    logger.info("Assign a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace is null");
    opNamespace = namespaces.get(0);
    logger.info("Assign a unique namespace for WebLogic domain");
    assertNotNull(namespaces.get(1), "Namespace is null");
    domainNamespace1 = namespaces.get(1);
    assertNotNull(namespaces.get(2), "Namespace is null");
    domainNamespace2 = namespaces.get(2);
    assertNotNull(namespaces.get(3), "Namespace is null");
    domainNamespace3 = namespaces.get(3);
    assertNotNull(namespaces.get(4), "Namespace is null");
    domainNamespace4 = namespaces.get(4);
    assertNotNull(namespaces.get(5), "Namespace is null");
    domainNamespace5 = namespaces.get(5);

    // set the service account name for the operator
    opServiceAccount = opNamespace + "-sa";

    // install and verify operator with REST API
    opParams = installAndVerifyOperator(opNamespace, opServiceAccount, true, 0, domainNamespace1);
    opParamsOriginal = opParams;
    // This test uses the operator restAPI to scale the domain. To do this in OKD cluster,
    // we need to expose the external service as route and set tls termination to  passthrough
    logger.info("Create a route for the operator external service - only for OKD");
    String opExternalSvc = createRouteForOKD("external-weblogic-operator-svc", opNamespace);
    // Patch the route just created to set tls termination to passthrough
    setTlsTerminationForRoute("external-weblogic-operator-svc", opNamespace);

    // create pull secrets for WebLogic image when running in non Kind Kubernetes cluster
    // this secret is used only for non-kind cluster
    createBaseRepoSecret(domainNamespace1);
  }

  /**
   * Test verifies the operator logs a NamespaceWatchingStarted event in the respective domain namespace
   * when it starts watching a new domain namespace with domainNamespaceSelectionStrategy default to List and
   * operator logs a NamespaceWatchingStopped event in the respective domain namespace
   * when it stops watching a domain namespace.
   * The test upgrades the operator instance through helm to add or remove another domain namespace
   * in the operator watch list.
   * This is a parameterized test with enableClusterRoleBinding set to either true or false.
   *<p>
   *<pre>{@literal
   * helm upgrade weblogic-operator kubernetes/charts/weblogic-operator
   * --namespace ns-ipqy
   * --reuse-values
   * --set "domainNamespaces={ns-xghr,ns-idir}"
   * }
   * </pre>
   * </p>
   */
  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testK8SEventsStartStopWatchingNS(boolean enableClusterRoleBinding) {
    logger.info("testing testK8SEventsStartStopWatchingNS with enableClusterRoleBinding={0}",
        enableClusterRoleBinding);
    OffsetDateTime timestamp = now();

    logger.info("Adding a new domain namespace in the operator watch list");
    List<String> domainNamespaces = new ArrayList<>();
    domainNamespaces.add(domainNamespace1);
    domainNamespaces.add(domainNamespace2);
    OperatorParams opTestParams = opParamsOriginal;
    opTestParams = opTestParams.domainNamespaces(domainNamespaces).enableClusterRoleBinding(enableClusterRoleBinding)
    .domainNamespaceSelectionStrategy("List");
    upgradeAndVerifyOperator(opNamespace, opTestParams);

    logger.info("verify NamespaceWatchingStarted event is logged in namespace {0}", domainNamespace2);
    checkEvent(opNamespace, domainNamespace2, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

    timestamp = now();

    logger.info("Removing domain namespace {0} in the operator watch list", domainNamespace2);
    domainNamespaces.clear();
    domainNamespaces.add(domainNamespace1);
    opTestParams = opTestParams.domainNamespaces(domainNamespaces).domainNamespaceSelectionStrategy("List");
    upgradeAndVerifyOperator(opNamespace, opTestParams);

    logger.info("verify NamespaceWatchingStopped event is logged in namespace {0}", domainNamespace2);
    checkNamespaceWatchingStoppedEvent(opNamespace, domainNamespace2, null, "Normal", timestamp,
        enableClusterRoleBinding);
  }

  /**
   * Test verifies the operator logs a NamespaceWatchingStarted event in the respective domain namespace
   * when it starts watching a new domain namespace with domainNamespaceSelectionStrategy set to LabelSelector and
   * operator logs a NamespaceWatchingStopped event in the respective domain namespace
   * when it stops watching a domain namespace.
   * If set to LabelSelector, then the operator will manage the set of namespaces discovered by a list of namespaces
   * using the value specified by domainNamespaceLabelSelector as a label selector.
   * The test upgrades the operator instance through helm to add or remove another domain namespace
   * in the operator watch list.
   *<p>
   *<pre>{@literal
   * helm upgrade weblogic-operator kubernetes/charts/weblogic-operator
   * --namespace ns-ipqy
   * --reuse-values
   * --set "domainNamespaceSelectionStrategy=LabelSelector"
   * --set "domainNamespaceLabelSelector=weblogic-operator\=enabled"
   * }
   * </pre>
   * </p>
   */
  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testK8SEventsStartStopWatchingNSWithLabelSelector(boolean enableClusterRoleBinding) {
    logger.info("testing testK8SEventsStartStopWatchingNSWithLabelSelector with enableClusterRoleBinding={0}",
        enableClusterRoleBinding);
    OffsetDateTime timestamp = now();

    logger.info("Labeling namespace {0} to enable it in the operator watch list", domainNamespace3);
    // label domainNamespace3
    Command
        .withParams(new CommandParams()
            .command(KUBERNETES_CLI + " label ns " + domainNamespace3 + " weblogic-operator=enabled --overwrite"))
        .execute();
    OperatorParams opTestParams = opParamsOriginal;
    // Helm upgrade parameters
    opTestParams = opTestParams
        .domainNamespaceSelectionStrategy("LabelSelector")
        .domainNamespaceLabelSelector("weblogic-operator=enabled")
        .enableClusterRoleBinding(enableClusterRoleBinding);
    upgradeAndVerifyOperator(opNamespace, opTestParams);

    logger.info("verify NamespaceWatchingStarted event is logged in namespace {0}", domainNamespace3);
    checkEvent(opNamespace, domainNamespace3, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

    // verify there is no event logged in domainNamespace4
    logger.info("verify NamespaceWatchingStarted event is not logged in {0}", domainNamespace4);
    assertFalse(domainEventExists(opNamespace, domainNamespace4, null, NAMESPACE_WATCHING_STARTED,
        "Normal", timestamp), "domain event " + NAMESPACE_WATCHING_STARTED + " is logged in "
        + domainNamespace4 + ", expected no such event will be logged");

    timestamp = now();
    logger.info("Labelling namespace {0} to \"weblogic-operator=disabled\" to disable it in the operator "
        + "watch list", domainNamespace3);

    // label domainNamespace3 to weblogic-operator=disabled
    Command
        .withParams(new CommandParams()
            .command(KUBERNETES_CLI + " label ns " + domainNamespace3 + " weblogic-operator=disabled --overwrite"))
        .execute();

    logger.info("verify NamespaceWatchingStopped event is logged in namespace {0}", domainNamespace3);
    checkNamespaceWatchingStoppedEvent(opNamespace, domainNamespace3, null, "Normal", timestamp,
        enableClusterRoleBinding);

    if (enableClusterRoleBinding) {
      String newNSWithoutLabels = "ns-newnamespace1";
      String newNSWithLabels = "ns-newnamespace2";

      assertDoesNotThrow(() -> createNamespaces(newNSWithoutLabels, newNSWithLabels),
          "Failed to create new namespaces");

      Command
          .withParams(new CommandParams()
              .command(KUBERNETES_CLI + " label ns " + newNSWithLabels + " weblogic-operator=enabled --overwrite"))
          .execute();

      logger.info("verify NamespaceWatchingStarted event is logged in namespace {0}", newNSWithLabels);
      checkEvent(opNamespace, newNSWithLabels, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

      // verify there is no event logged in domainNamespace4
      logger.info("verify NamespaceWatchingStarted event is not logged in {0}", domainNamespace4);
      assertFalse(domainEventExists(opNamespace, newNSWithoutLabels, null, NAMESPACE_WATCHING_STARTED,
          "Normal", timestamp), "domain event " + NAMESPACE_WATCHING_STARTED + " is logged in "
          + newNSWithoutLabels + ", expected no such event will be logged");
    }
  }

  private void createNamespaces(String newNSWithoutLabels, String newNSWithLabels) throws ApiException {
    createNamespace(newNSWithoutLabels);
    createNamespace(newNSWithLabels);
  }

  /**
   * Test verifies the operator logs a NamespaceWatchingStarted event in the respective domain namespace
   * when it starts watching a new domain namespace with domainNamespaceSelectionStrategy set to RegExp and
   * operator logs a NamespaceWatchingStopped event in the respective domain namespace
   * when it stops watching a domain namespace.
   * If set to RegExp, then the operator will manage the set of namespaces discovered by a list of namespaces
   * using the value specified by domainNamespaceRegExp as a regular expression matched against the namespace names.
   * The test upgrades the operator instance through helm to add or remove another domain namespace
   * in the operator watch list.
   *<p>
   *<pre>{@literal
   * helm upgrade weblogic-operator kubernetes/charts/weblogic-operator
   * --namespace ns-ipqy
   * --reuse-values
   * --set "domainNamespaceSelectionStrategy=RegExp"
   * --set "domainNamespaceRegExp=abcd"
   * }
   * </pre>
   * </p>
   */
  @ParameterizedTest
  @ValueSource(booleans = { true, false })
  void testK8SEventsStartStopWatchingNSWithRegExp(boolean enableClusterRoleBinding) {
    OffsetDateTime timestamp = now();
    logger.info("Adding a new domain namespace {0} in the operator watch list", domainNamespace5);
    // Helm upgrade parameters
    OperatorParams opTestParams = opParamsOriginal;
    opTestParams = opTestParams
        .domainNamespaceSelectionStrategy("RegExp")
        .domainNamespaceRegExp(domainNamespace5.substring(3))
        .enableClusterRoleBinding(enableClusterRoleBinding);

    upgradeAndVerifyOperator(opNamespace, opTestParams);

    logger.info("verify NamespaceWatchingStarted event is logged in {0}", domainNamespace5);
    checkEvent(opNamespace, domainNamespace5, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

    // verify there is no event logged in domainNamespace4
    logger.info("verify NamespaceWatchingStarted event is not logged in {0}", domainNamespace4);
    assertFalse(domainEventExists(opNamespace, domainNamespace4, null, NAMESPACE_WATCHING_STARTED,
        "Normal", timestamp), "domain event " + NAMESPACE_WATCHING_STARTED + " is logged in "
        + domainNamespace4 + ", expected no such event will be logged");

    timestamp = now();
    logger.info("Setting the domainNamesoaceRegExp to a new value {0}", domainNamespace4.substring(3));

    // Helm upgrade parameters
    opTestParams = opTestParams
        .domainNamespaceSelectionStrategy("RegExp")
        .domainNamespaceRegExp(domainNamespace4.substring(3));

    upgradeAndVerifyOperator(opNamespace, opTestParams);

    logger.info("verify NamespaceWatchingStopped event is logged in namespace {0}", domainNamespace5);
    checkNamespaceWatchingStoppedEvent(opNamespace, domainNamespace5, null, "Normal", timestamp,
        enableClusterRoleBinding);

    logger.info("verify NamespaceWatchingStarted event is logged in namespace {0}", domainNamespace4);
    checkEvent(opNamespace, domainNamespace4, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);
  }

  /**
   * Operator helm parameter domainNamespaceSelectionStrategy is set to Dedicated.
   * If set to Dedicated, then operator will manage WebLogic Domains only in the same namespace which the operator
   * itself is deployed, which is the namespace of the Helm release.
   * Operator logs a NamespaceWatchingStopped in the operator domain namespace and
   * NamespaceWatchingStopped event in the other domain namespaces when it stops watching a domain namespace.
   *
   * Test verifies NamespaceWatchingStopped event is logged when operator stops watching a domain namespace.
   */
  @Test
  void testK8SEventsStartStopWatchingNSWithDedicated() {
    OffsetDateTime timestamp = now();

    // Helm upgrade parameters
    OperatorParams opTestParams = opParamsOriginal;
    opTestParams = opTestParams.domainNamespaceSelectionStrategy("Dedicated")
                .enableClusterRoleBinding(false);

    upgradeAndVerifyOperator(opNamespace, opTestParams);

    logger.info("verify NamespaceWatchingStarted event is logged in {0}", opNamespace);
    checkEvent(opNamespace, opNamespace, null, NAMESPACE_WATCHING_STARTED, "Normal", timestamp);

    logger.info("verify NamespaceWatchingStopped event is logged in {0}", domainNamespace4);
    checkNamespaceWatchingStoppedEvent(opNamespace, domainNamespace4, null, "Normal", timestamp, false);
  }

  // Utility method to check event
  private static void checkEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String reason, String type, OffsetDateTime timestamp) {
    testUntil(withLongRetryPolicy,
        checkDomainEvent(opNamespace, domainNamespace, domainUid, reason, type, timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        reason,
        domainNamespace);
  }

  private static void checkNamespaceWatchingStoppedEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String type, OffsetDateTime timestamp, boolean enableClusterRoleBinding) {
    testUntil(
        checkDomainEventWatchingStopped(
            opNamespace, domainNamespace, domainUid, type, timestamp, enableClusterRoleBinding),
        logger,
        "domain event NamespaceWatchingStopped to be logged in namespace {0}",
        domainNamespace);
  }
}

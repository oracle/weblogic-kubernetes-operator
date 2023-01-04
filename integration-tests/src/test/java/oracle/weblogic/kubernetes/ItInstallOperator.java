// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.utils.OperatorUtils.installAndVerifyOperator;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Simple JUnit test file used for testing operator namespace management, Dedicated usecase is covered by other test
 * class.
 */
@DisplayName("Test operator namespace management usability using Helm chart")
@IntegrationTest
class ItInstallOperator {

  private static String opNamespace;
  private static LoggingFacade logger = null;
  private String domainNamespaceLabelSelector = "wko.operator.v8o";
  private String domainNamespaceSelectionStrategy = "LabelSelector";

  /**
   * Get namespaces for operator.
   *
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the JUnit engine parameter resolution
   *      mechanism
   */
  @BeforeAll
  public static void initAll(@Namespaces(1) List<String> namespaces) {
    logger = getLogger();
    // get a unique operator namespace
    logger.info("Getting a unique namespace for operator");
    assertNotNull(namespaces.get(0), "Namespace list is null");
    opNamespace = namespaces.get(0);
  }

  /**
   * Install the Operator successfully and verify it is deployed successfully with
   * domainNamespaceSelectionStrategy=LabelSelector, and domainNamespaceLabelSelector=label1.
   */
  @Test
  @DisplayName("install operator helm chart and domain, using label namespace management")
  void testNameSpaceManagedByLabelSelector() {
    // install and verify operator set to manage domains based on LabelSelector strategy,
    // domainNamespaces value expected to be ignored
    installAndVerifyOperator(OPERATOR_RELEASE_NAME, opNamespace, 
        domainNamespaceSelectionStrategy, domainNamespaceLabelSelector, true);
  }
}

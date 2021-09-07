// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes;

import java.util.List;

import oracle.weblogic.kubernetes.annotations.IntegrationTest;
import oracle.weblogic.kubernetes.annotations.Namespaces;
import oracle.weblogic.kubernetes.utils.ItMiiSampleHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * Tests to verify MII sample with WLS domain using auxiliary image.
 */
@DisplayName("Test model in image sample with WLS domain using auxiliary image")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@IntegrationTest
class ItMiiSampleWlsAux {

  private static ItMiiSampleHelper myItMiiSampleHelper = null;

  /**
   * Install Operator.
   * @param namespaces list of namespaces created by the IntegrationTestWatcher by the
   *        JUnit engine parameter resolution mechanism
   */
  @BeforeAll
  public static void init(@Namespaces(3) List<String> namespaces) {
    myItMiiSampleHelper = new ItMiiSampleHelper();
    myItMiiSampleHelper.initAll(namespaces, ItMiiSampleHelper.DomainType.WLS, ItMiiSampleHelper.ImageType.AUX);
  }

  /**
   * Test to verify MII sample WLS initial use case using auxiliary image.
   * Builds image required for the initial use case, creates secrets, and
   * creates domain resource.
   * Verifies all WebLogic Server pods are ready, are at the expected
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * (response includes "Hello World!").
   */
  @Test
  @Order(1)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS initial use case using auxiliary image")
  void testAIWlsInitialUseCase() {
    myItMiiSampleHelper.callInitialUseCase();
  }

  /**
   * Test to verify WLS update1 use case using auxiliary image.
   * Adds a data source to initial domain via a configmap and updates the
   * domain resource restartVersion.
   * Verifies all WebLogic Server pods roll to ready, roll to the expected
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * and detects the new datasource (response includes
   * "mynewdatasource").
   */
  @Test
  @Order(2)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update1 use case using auxiliary image")
  void testAIWlsUpdate1UseCase() {
    myItMiiSampleHelper.callUpdateUseCase("-update1", "Update1 use case failed");
  }

  /**
   * Test to verify WLS update2 use case using auxiliary image.
   * Deploys a second domain 'domain2' with a different domain UID,
   * different secrets, and different datasource config map,
   * but that is otherwise the same as the update1 domain.
   * Verifies all WebLogic Server pods are ready, have the expected
   * restartVersion, and have the expected image.
   * For each domain, verifies the sample application is running
   * (response includes "domain1" or "domain2" depending on domain).
   */
  @Test
  @Order(3)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update2 use case using auxiliary image")
  void testAIWlsUpdate2UseCase() {
    myItMiiSampleHelper.callUpdateUseCase("-update2", "Update2 use case failed");
  }

  /**
   * Test to verify update3 use case using auxiliary image.
   * Deploys an updated WebLogic application to the running
   * domain from update1 using an updated Docker image,
   * and updates the domain resource restartVersion.
   * Verifies all WebLogic Server pods roll to ready, roll to the expected
   * restartVersion, and have the expected image.
   * Verifies the sample application is running
   * and is at the new version (response includes "v2").
   */
  @Test
  @Order(4)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update3 use case using auxiliary image")
  void testAIWlsUpdate3UseCase() {
    myItMiiSampleHelper.callUpdateUseCase("-update3-image,-check-image-and-push,-update3-main",
        "Update3 use case failed");
  }

  /**
   * Test to verify WLS update4 use case using auxiliary image.
   * Update Work Manager Min and Max Threads Constraints via a configmap and updates the
   * domain resource introspectVersion.
   * Verifies the sample application is running
   * and detects the updated configured count for the Min and Max Threads Constraints.
   */
  @Test
  @Order(5)
  @DisabledIfEnvironmentVariable(named = "SKIP_WLS_SAMPLES", matches = "true")
  @DisplayName("Test to verify MII sample WLS update4 use case using auxiliary image")
  void testAIWlsUpdate4UseCase() {
    myItMiiSampleHelper.callUpdateUseCase("-update4", "Update4 use case failed");
  }

  /**
   * Uninstall traefik.
   */
  @AfterAll
  public void tearDown() {
    // uninstall traefik
    myItMiiSampleHelper.tearDownAll();
  }
}

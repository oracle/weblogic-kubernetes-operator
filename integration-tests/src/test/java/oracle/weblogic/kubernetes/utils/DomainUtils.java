// Copyright (c) 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.List;

import oracle.weblogic.domain.Domain;
import oracle.weblogic.domain.DomainCondition;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.DOMAIN_VERSION;
import static oracle.weblogic.kubernetes.actions.TestActions.createDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.deleteDomainCustomResource;
import static oracle.weblogic.kubernetes.actions.TestActions.getDomainCustomResource;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainDoesNotExist;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusConditionTypeExists;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusConditionTypeHasExpectedStatus;
import static oracle.weblogic.kubernetes.assertions.TestAssertions.domainStatusReasonMatches;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.checkPodReadyAndServiceExists;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DomainUtils {

  /**
   * Create a domain in the specified namespace and wait up to five minutes until the domain exists.
   *
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param domVersion custom resource's version
   */
  public static void createDomainAndVerify(Domain domain,
                                           String domainNamespace,
                                           String... domVersion) {
    String domainVersion = (domVersion.length == 0) ? DOMAIN_VERSION : domVersion[0];

    LoggingFacade logger = getLogger();
    // create the domain CR
    assertNotNull(domain, "domain is null");
    assertNotNull(domain.getSpec(), "domain spec is null");
    String domainUid = domain.getSpec().getDomainUid();

    logger.info("Creating domain custom resource for domainUid {0} in namespace {1}",
        domainUid, domainNamespace);
    assertTrue(assertDoesNotThrow(() -> createDomainCustomResource(domain, domainVersion),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace)),
        String.format("Create domain custom resource failed with ApiException for %s in namespace %s",
            domainUid, domainNamespace));

    // wait for the domain to exist
    logger.info("Checking for domain custom resource in namespace {0}", domainNamespace);
    testUntil(
        domainExists(domainUid, domainVersion, domainNamespace),
        logger,
        "domain {0} to be created in namespace {1}",
        domainUid,
        domainNamespace);
  }

  /**
   * Create a domain in the specified namespace, wait up to five minutes until the domain exists and
   * verify the servers are running.
   *
   * @param domainUid domain
   * @param domain the oracle.weblogic.domain.Domain object to create domain custom resource
   * @param domainNamespace namespace in which the domain will be created
   * @param adminServerPodName admin server pod name
   * @param managedServerPodNamePrefix managed server pod prefix
   * @param replicaCount replica count
   */
  public static void createDomainAndVerify(String domainUid, Domain domain,
                                           String domainNamespace, String adminServerPodName,
                                           String managedServerPodNamePrefix, int replicaCount) {
    LoggingFacade logger = getLogger();

    // create domain and verify
    createDomainAndVerify(domain, domainNamespace);

    // check that admin service/pod exists in the domain namespace
    logger.info("Checking that admin service/pod {0} exists in namespace {1}",
        adminServerPodName, domainNamespace);
    checkPodReadyAndServiceExists(adminServerPodName, domainUid, domainNamespace);

    for (int i = 1; i <= replicaCount; i++) {
      String managedServerPodName = managedServerPodNamePrefix + i;

      // check that ms service/pod exists in the domain namespace
      logger.info("Checking that clustered ms service/pod {0} exists in namespace {1}",
          managedServerPodName, domainNamespace);
      checkPodReadyAndServiceExists(managedServerPodName, domainUid, domainNamespace);
    }

  }

  /**
   * Check the status reason of the domain matches the given reason.
   *
   * @param domain  oracle.weblogic.domain.Domain object
   * @param namespace the namespace in which the domain exists
   * @param statusReason the expected status reason of the domain
   */
  public static void checkDomainStatusReasonMatches(Domain domain, String namespace, String statusReason) {
    LoggingFacade logger = getLogger();
    testUntil(
        assertDoesNotThrow(() -> domainStatusReasonMatches(domain, statusReason)),
        logger,
        "the status reason of the domain {0} in namespace {1}",
        domain,
        namespace,
        statusReason);
  }

  /**
   * Check the domain status condition has expected status value.
   * @param domainUid Uid of the domain
   * @param namespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @param expectedStatus the expected value of the status, either True or False
   */
  public static void checkDomainStatusConditionTypeHasExpectedStatus(String domainUid,
                                                                     String namespace,
                                                                     String conditionType,
                                                                     String expectedStatus) {
    testUntil(
        withLongRetryPolicy,
        domainStatusConditionTypeHasExpectedStatus(domainUid, namespace, conditionType, expectedStatus),
        getLogger(),
        "domain status condition type {0} has expected status {1}",
        conditionType,
        expectedStatus);
  }

  /**
   * Check the domain status condition type exists.
   * @param domainUid uid of the domain
   * @param namespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   */
  public static void checkDomainStatusConditionTypeExists(String domainUid,
                                                          String namespace,
                                                          String conditionType) {
    testUntil(
        domainStatusConditionTypeExists(domainUid, namespace, conditionType),
        getLogger(),
        "waiting for domain status condition type {0} exists",
        conditionType
    );
  }

  /**
   * Check the domain status condition type does not exist.
   * @param domainUid uid of the domain
   * @param domainNamespace namespace of the domain
   * @param conditionType the type name of condition, accepted value: Completed, Available, Failed and
   *                      ConfigChangesPendingRestart
   * @return true if the condition type does not exist, false otherwise
   */
  public static boolean verifyDomainStatusConditionTypeDoesNotExist(String domainUid,
                                                                    String domainNamespace,
                                                                    String conditionType) {
    Domain domain = assertDoesNotThrow(() -> getDomainCustomResource(domainUid, domainNamespace));

    if (domain != null && domain.getStatus() != null) {
      List<DomainCondition> domainConditionList = domain.getStatus().getConditions();
      for (DomainCondition domainCondition : domainConditionList) {
        if (domainCondition.getType().equalsIgnoreCase(conditionType)) {
          return false;
        }
      }
    } else {
      if (domain == null) {
        getLogger().info("domain is null");
      } else {
        getLogger().info("domain status is null");
      }
    }
    return true;
  }

  /**
   * Delete a domain in the specified namespace.
   * @param domainNS the namespace in which the domain exists
   * @param domainUid domain uid
   */
  public static void deleteDomainResource(String domainNS, String domainUid) {
    //clean up domain resources in namespace and set namespace to label , managed by operator
    getLogger().info("deleting domain custom resource {0}", domainUid);
    assertTrue(deleteDomainCustomResource(domainUid, domainNS));

    // wait until domain was deleted
    testUntil(
        domainDoesNotExist(domainUid, DOMAIN_VERSION, domainNS),
        getLogger(),
        "domain {0} to be deleted in namespace {1}",
        domainUid,
        domainNS);
  }
}

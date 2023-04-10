// Copyright (c) 2020, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.awaitility.core.ConditionFactory;

import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.actions.TestActions.getOperatorPodName;
import static oracle.weblogic.kubernetes.actions.TestActions.getPodLog;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.testUntil;
import static oracle.weblogic.kubernetes.utils.CommonTestUtils.withLongRetryPolicy;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Helper class for Kubernetes Events checking.
 */
public class K8sEvents {

  private static final LoggingFacade logger = getLogger();

  /**
   * Utility method to check event.
   *
   * @param opNamespace Operator namespace
   * @param domainNamespace Domain namespace
   * @param domainUid domainUid
   * @param reason EventName
   * @param type Type of the event
   * @param timestamp event timestamp
   * @param withStandardRetryPolicy conditionfactory object
   */
  public static void checkEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String reason, String type, OffsetDateTime timestamp, ConditionFactory withStandardRetryPolicy) {
    testUntil(
        withStandardRetryPolicy,
        checkDomainEvent(opNamespace, domainNamespace, domainUid, reason, type, timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        reason,
        domainNamespace);
  }

  /**
   * Wait until a given event is logged by the operator.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal of Warning
   * @param timestamp the timestamp after which to see events
   */
  public static void checkEvent(
      String opNamespace, String domainNamespace, String domainUid,
      String reason, String type, OffsetDateTime timestamp) {
    testUntil(
        checkDomainEvent(opNamespace, domainNamespace, domainUid, reason, type, timestamp),
        logger,
        "domain event {0} to be logged in namespace {1}",
        reason,
        domainNamespace);
  }


  /**
   * Check if a given DomainFailed event is logged by the operator.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param failureReason DomainFailureReason to check
   * @param type type of event, Normal of Warning
   * @param timestamp the timestamp after which to see events
   */
  public static Callable<Boolean> checkDomainFailedEventWithReason(
      String opNamespace, String domainNamespace, String domainUid, String failureReason,
      String type, OffsetDateTime timestamp) {
    return () -> {
      return domainFailedEventExists(opNamespace, domainNamespace, domainUid, failureReason, type, timestamp);
    };
  }

  /**
   * Check if a given event is logged by the operator.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal of Warning
   * @param timestamp the timestamp after which to see events
   */
  public static Callable<Boolean> checkDomainEvent(
      String opNamespace, String domainNamespace, String domainUid, String reason,
      String type, OffsetDateTime timestamp) {
    return () -> {
      return domainEventExists(opNamespace, domainNamespace, domainUid, reason, type, timestamp);
    };
  }

  /**
   * Check if NamespaceWatchingStopped event is logged by the operator.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param type type of event, Normal of Warning
   * @param timestamp the timestamp after which to see events
   * @param enableClusterRoleBinding whether the enableClusterRoleBinding is set to true of false
   */
  public static Callable<Boolean> checkDomainEventWatchingStopped(
      String opNamespace, String domainNamespace, String domainUid,
      String type, OffsetDateTime timestamp, boolean enableClusterRoleBinding) {
    return () -> {
      if (enableClusterRoleBinding) {
        if (domainEventExists(opNamespace, domainNamespace, domainUid, NAMESPACE_WATCHING_STOPPED, type, timestamp)
            && domainEventExists(opNamespace, opNamespace, null, STOP_MANAGING_NAMESPACE, type, timestamp)) {
          logger.info("Got event {0} in namespace {1} and event {2} in namespace {3}",
              NAMESPACE_WATCHING_STOPPED, domainNamespace, STOP_MANAGING_NAMESPACE, opNamespace);
          return true;
        } else {
          logger.info("Did not get the {0} event in namespace {1} or {2} event in namespace {3}",
              NAMESPACE_WATCHING_STOPPED, domainNamespace, STOP_MANAGING_NAMESPACE, opNamespace);
        }
      } else {
        if (domainEventExists(opNamespace, domainNamespace, domainUid, NAMESPACE_WATCHING_STOPPED, type, timestamp)
            && domainEventExists(opNamespace, opNamespace, null, STOP_MANAGING_NAMESPACE, type, timestamp)) {
          logger.info("Got event {0} in namespace {1} and event {2} in namespace {3}",
              NAMESPACE_WATCHING_STOPPED, domainNamespace, STOP_MANAGING_NAMESPACE, opNamespace);
          return true;
        } else {
          logger.info("Did not get the {0} event in namespace {1} or {2} event in namespace {3}",
              NAMESPACE_WATCHING_STOPPED, domainNamespace, STOP_MANAGING_NAMESPACE, opNamespace);

          // check if there is a warning message in operator's log
          String operatorPodName = getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace);
          String expectedErrorMsg = String.format(
              "Cannot create or replace NamespaceWatchingStopped event in namespace %s due to an authorization error",
              domainNamespace);
          String operatorLog = getPodLog(operatorPodName, opNamespace);
          if (operatorLog.contains(expectedErrorMsg)
              && domainEventExists(opNamespace, opNamespace, null, STOP_MANAGING_NAMESPACE, type, timestamp)) {
            logger.info("Got expected error msg \"{0}\" in operator log and event {1} is logged in namespace {2}",
                expectedErrorMsg, STOP_MANAGING_NAMESPACE, opNamespace);
            return true;
          } else {
            logger.info("Did not get the expected error msg {0} in operator log", expectedErrorMsg);
            logger.info("Operator log: {0}", operatorLog);
          }
        }
      }
      return false;
    };
  }

  /**
   * Check if a given event is logged by the operator in the given namespace.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the event is logged
   * @param domainUid UID of the domain
   * @param failureReason failure reason to check
   * @param type type of event, Normal or Warning
   * @param timestamp the timestamp after which to see events
   */
  public static boolean domainFailedEventExists(
      String opNamespace, String domainNamespace, String domainUid, String failureReason,
      String type, OffsetDateTime timestamp) {

    try {
      List<CoreV1Event> events = Kubernetes.listOpGeneratedNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        if (DOMAIN_FAILED.equals(event.getReason()) && (isEqualOrAfter(timestamp, event))
            && event.getMessage().contains(failureReason)) {
          logger.info(Yaml.dump(event));
          verifyOperatorDetails(event, opNamespace, domainUid);
          //verify type
          logger.info("Verifying domain event type {0}", type);
          assertEquals(event.getType(), type);
          return true;
        }
      }
    } catch (ApiException ex) {
      logger.log(Level.SEVERE, null, ex);
    }
    return false;
  }

  /**
   * Check if a given event is logged by the operator in the given namespace.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the event is logged
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal or Warning
   * @param timestamp the timestamp after which to see events
   */
  public static boolean domainEventExists(
      String opNamespace, String domainNamespace, String domainUid, String reason,
      String type, OffsetDateTime timestamp) {

    try {
      List<CoreV1Event> events = Kubernetes.listOpGeneratedNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        if (isDomainEvent(domainUid, event) && reason.equals(event.getReason()) && isEqualOrAfter(timestamp, event)) {
          logger.info(Yaml.dump(event));
          verifyOperatorDetails(event, opNamespace, domainUid);
          //verify type
          logger.info("Verifying domain event type {0} with reason {1} for domain {2} in namespace {3}",
                  type, reason, domainUid, domainNamespace);
          assertEquals(event.getType(), type);
          return true;
        }
      }
    } catch (ApiException ex) {
      logger.log(Level.SEVERE,
          String.format("Failed to verify domain event type %s with reason %s for domain %s in namespace %s",
              type, reason, domainUid, domainNamespace), ex);
    }
    return false;
  }

  private static boolean isDomainEvent(String domainUid, CoreV1Event event) {
    return domainUid == null || isEventForSpecifiedDomain(domainUid, event);
  }

  private static boolean isEventForSpecifiedDomain(String domainUid, CoreV1Event event) {
    return event.getMetadata().getLabels() != null && event.getMetadata().getLabels().containsValue(domainUid);
  }

  /**
   * Get matching operator generated event object.
   * @param domainNamespace namespace in which the event is logged
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal or Warning
   * @param timestamp the timestamp after which to see events
   * @return CoreV1Event matching event object
   */
  public static CoreV1Event getOpGeneratedEvent(
      String domainNamespace, String reason, String type, OffsetDateTime timestamp) {

    try {
      List<CoreV1Event> events = Kubernetes.listOpGeneratedNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        if (event.getReason().equals(reason) && (isEqualOrAfter(timestamp, event))) {
          logger.info(Yaml.dump(event));
          if (event.getType().equals(type)) {
            return event;
          }
        }
      }
    } catch (ApiException ex) {
      Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE, null, ex);
    }
    return null;
  }

  /**
   * Check if a given event is logged by the operator.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal of Warning
   * @param timestamp the timestamp after which to see events
   * @param countBefore the count to check against
   */
  public static Callable<Boolean> checkDomainEventWithCount(
      String opNamespace, String domainNamespace, String domainUid, String reason,
      String type, OffsetDateTime timestamp, int countBefore) {
    return () -> {
      logger.info("Verifying {0} event is logged by the operator in domain namespace {1}", reason, domainNamespace);
      try {
        List<CoreV1Event> events = Kubernetes.listOpGeneratedNamespacedEvents(domainNamespace);
        for (CoreV1Event event : events) {
          if (((domainUid != null && event.getMetadata().getLabels().containsValue(domainUid))
                  || domainUid == null)
                  && event.getReason().equals(reason) && (isEqualOrAfter(timestamp, event))) {
            logger.info(Yaml.dump(event));
            verifyOperatorDetails(event, opNamespace, domainUid);
            //verify type
            logger.info("Verifying domain event type {0}", type);
            assertEquals(type, event.getType());
            int countAfter = getDomainEventCount(domainNamespace, domainUid, reason, "Normal");
            return (countAfter >= countBefore + 1);
          }
        }
      } catch (ApiException ex) {
        Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE, null, ex);
      }
      return false;
    };
  }

  /**
   * Get the count for a particular event with specified reason, type and domainUid in a given namespace.
   *
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid       UID of the domain
   * @param reason          event reason to get the count for
   * @param type            type of event, Normal of Warning
   * @return count          Event count
   */
  public static int getDomainEventCount(
          String domainNamespace, String domainUid, String reason, String type) {
    try {
      List<CoreV1Event> events = Kubernetes.listOpGeneratedNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        Map<String, String> labels = event.getMetadata().getLabels();
        if (event.getReason().equals(reason)
                && event.getType().equals(type)
                && labels.get("weblogic.domainUID").equals(domainUid)) {
          return event.getCount();
        }
      }
    } catch (ApiException ex) {
      Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE, null, ex);
    }
    return 0;
  }

  /**
   * Get the event count between a specific timestamp.
   *
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param timestamp the timestamp after which to see events
   * @return count number of events count
   */
  public static int getOpGeneratedEventCount(
      String domainNamespace, String domainUid, String reason, OffsetDateTime timestamp) {
    int count = 0;
    try {
      List<CoreV1Event> events = Kubernetes.listOpGeneratedNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        Map<String, String> labels = event.getMetadata().getLabels();
        if (event.getReason().equals(reason)
            && labels.get("weblogic.domainUID").equals(domainUid)
            && (isEqualOrAfter(timestamp, event))) {
          logger.info(Yaml.dump(event));
          count++;
        }
      }
    } catch (ApiException ex) {
      Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE, null, ex);
      return -1;
    }
    return count;
  }

  /**
   * Get the event count between a specific timestamp for the given resource (domain or cluster).
   *
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param resourceName the Uid/name of the resource
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param timestamp the timestamp after which to see events
   * @return count number of events count
   */
  public static int getOpGeneratedEventCountForResource(
      String domainNamespace, String domainUid, String resourceName, String reason, OffsetDateTime timestamp) {
    int count = 0;
    try {
      List<CoreV1Event> events = Kubernetes.listOpGeneratedNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        Map<String, String> labels = event.getMetadata().getLabels();
        if (event.getReason().equals(reason)
            && labels.get("weblogic.domainUID").equals(domainUid)
            && event.getInvolvedObject().getName().equals(resourceName)
            && (isEqualOrAfter(timestamp, event))) {
          logger.info(Yaml.dump(event));
          count++;
        }
      }
    } catch (ApiException ex) {
      Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE, null, ex);
      return -1;
    }
    return count;
  }

  /**
   * Check if a given event is logged only once for the given pod.
   *
   * @param domainNamespace namespace in which the domain exists
   * @param serverName server pod name for which event is checked
   * @param reason event to check for Started, Killing etc
   * @param timestamp the timestamp after which to see events
   */
  public static Callable<Boolean> checkPodEventLoggedOnce(
          String domainNamespace, String serverName, String reason, OffsetDateTime timestamp) {
    return () -> {
      logger.info("Verifying {0} event is logged for {1} pod in the domain namespace {2}",
              reason, serverName, domainNamespace);
      try {
        return isEventLoggedOnce(serverName, Kubernetes.listNamespacedEvents(domainNamespace).stream()
                .filter(e -> e.getInvolvedObject().getName().equals(serverName))
                .filter(e -> e.getReason().contains(reason))
                .filter(e -> isEqualOrAfter(timestamp, e)).collect(Collectors.toList()).size());
      } catch (ApiException ex) {
        Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE, null, ex);
      }
      return false;
    };
  }

  /**
   * Check the domain event contains the expected error msg.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal of Warning
   * @param timestamp the timestamp after which to see events
   * @param expectedMsg the expected message in the domain event message
   */
  public static void checkDomainEventContainsExpectedMsg(String opNamespace,
                                                         String domainNamespace,
                                                         String domainUid,
                                                         String reason,
                                                         String type,
                                                         OffsetDateTime timestamp,
                                                         String expectedMsg) {
    testUntil(
        withLongRetryPolicy,
        domainEventContainsExpectedMsg(domainNamespace, domainUid, reason, type, timestamp, expectedMsg),
        logger,
        "domain event {0} in namespace {1} contains expected msg {2}",
        reason,
        domainNamespace,
        expectedMsg);
  }

  private static Callable<Boolean> domainEventContainsExpectedMsg(String domainNamespace,
                                                                  String domainUid,
                                                                  String reason,
                                                                  String type,
                                                                  OffsetDateTime timestamp,
                                                                  String expectedMsg) {
    return () -> {
      for (CoreV1Event event : getEvents(domainNamespace, domainUid, reason, type, timestamp)) {
        if (event != null && event.getMessage() != null && event.getMessage().contains(expectedMsg)) {
          return true;
        }
      }
      return false;
    };
  }

  /**
   * Get matching event object with specific reason and type.
   * @param domainNamespace namespace in which the event is logged
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal or Warning
   * @param timestamp the timestamp after which to see events
   * @return CoreV1Event matching event object
   */
  public static List<CoreV1Event> getEvents(String domainNamespace,
                                            String domainUid,
                                            String reason,
                                            String type,
                                            OffsetDateTime timestamp) {

    List<CoreV1Event> events = new ArrayList<>();

    try {
      List<CoreV1Event> allEvents = Kubernetes.listOpGeneratedNamespacedEvents(domainNamespace);
      for (CoreV1Event event : allEvents) {
        Map<String, String> labels = event.getMetadata().getLabels();
        if (event.getReason().equals(reason)
            && (isEqualOrAfter(timestamp, event))
            && event.getType().equals(type)
            && (labels != null)
            && (labels.get("weblogic.domainUID") != null)
            && labels.get("weblogic.domainUID").equals(domainUid)) {

          events.add(event);
        }
      }
    } catch (ApiException ex) {
      Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE, null, ex);
    }
    return events;
  }

  /**
   * Get matching event objects after specific timestamp.
   * @param namespace namespace in which the event is logged
   * @param timestamp the timestamp after which to see events
   * @return CoreV1Event matching event object
   */
  public static List<CoreV1Event> getEvents(String namespace,
                                            OffsetDateTime timestamp) {

    List<CoreV1Event> events = new ArrayList<>();

    try {
      List<CoreV1Event> allEvents = Kubernetes.listNamespacedEvents(namespace);
      for (CoreV1Event event : allEvents) {
        if ((isEqualOrAfter(timestamp, event))) {
          events.add(event);
        }
      }
    } catch (ApiException ex) {
      Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE, null, ex);
    }
    return events;
  }

  private static boolean isEqualOrAfter(OffsetDateTime timestamp, CoreV1Event event) {
    return event.getLastTimestamp().isEqual(timestamp)
            || event.getLastTimestamp().isAfter(timestamp);
  }

  private static Boolean isEventLoggedOnce(String serverName, int count) {
    return count == 1 ? true : logErrorAndFail(serverName, count);
  }

  private static Boolean logErrorAndFail(String serverName, int count) {
    Logger.getLogger(K8sEvents.class.getName()).log(Level.SEVERE,
            "Pod " + serverName + " restarted " + count + " times");
    return false;
  }

  // Verify the operator instance details are correct
  private static void verifyOperatorDetails(
      CoreV1Event event, String opNamespace, String domainUid) throws ApiException {
    logger.info("Verifying operator details");
    String operatorPodName = TestActions.getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace);
    //verify DOMAIN_API_VERSION
    if (domainUid != null && event.getInvolvedObject().getKind().equals("Domain")) {
      assertEquals(TestConstants.DOMAIN_API_VERSION, event.getInvolvedObject().getApiVersion(),
          "Expected " + TestConstants.DOMAIN_API_VERSION + " ,Got " + event.getInvolvedObject().getApiVersion());
    }
    //verify reporting component to be operator release
    assertEquals("weblogic.operator", event.getReportingComponent(),
        "Didn't get reporting component as " + "weblogic.operator");
    //verify reporting instance to be operator instance
    assertEquals(operatorPodName, event.getReportingInstance(),
        "Didn't get reporting instance as " + operatorPodName);
    //verify the event was created by operator
    Map<String, String> labels = event.getMetadata().getLabels();
    assertTrue(labels.containsKey("weblogic.createdByOperator")
        && labels.get("weblogic.createdByOperator").equals("true"));
    //verify the domainUID matches
    if (domainUid != null) {
      assertTrue(labels.containsKey("weblogic.domainUID")
          && labels.get("weblogic.domainUID").equals(domainUid));
    }
  }

  public static final String DOMAIN_AVAILABLE = "Available";
  public static final String DOMAIN_CREATED = "Created";
  public static final String DOMAIN_DELETED = "Deleted";
  public static final String DOMAIN_CHANGED = "Changed";
  public static final String DOMAIN_COMPLETED = "Completed";
  public static final String DOMAIN_FAILED = "Failed";
  public static final String CLUSTER_AVAILABLE = "ClusterAvailable";
  public static final String CLUSTER_DELETED = "ClusterDeleted";
  public static final String CLUSTER_CHANGED = "ClusterChanged";
  public static final String CLUSTER_COMPLETED = "ClusterCompleted";
  public static final String DOMAIN_ROLL_STARTING = "RollStarting";
  public static final String DOMAIN_ROLL_COMPLETED = "RollCompleted";
  public static final String NAMESPACE_WATCHING_STARTED = "NamespaceWatchingStarted";
  public static final String NAMESPACE_WATCHING_STOPPED = "NamespaceWatchingStopped";
  public static final String STOP_MANAGING_NAMESPACE = "StopManagingNamespace";
  public static final String POD_TERMINATED = "Killing";
  public static final String POD_STARTED = "Started";
  public static final String POD_CYCLE_STARTING = "PodCycleStarting";

  public static final String TOPOLOGY_MISMATCH_ERROR
      = "Domain resource and WebLogic domain configuration mismatch error";
  public static final String INTROSPECTION_ERROR = "Introspection error";
  public static final String KUBERNETES_ERROR = "Kubernetes Api call error";
  public static final String SERVER_POD_ERROR = "Server pod error";
  public static final String REPLICAS_TOO_HIGH_ERROR = "Replicas too high";
  public static final String INTERNAL_ERROR = "Internal error";
  public static final String ABORTED_ERROR = "Domain processing is aborted";

}

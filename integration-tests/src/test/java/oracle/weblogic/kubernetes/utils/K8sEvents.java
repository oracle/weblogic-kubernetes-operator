// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.CoreV1Event;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.kubernetes.ItKubernetesEvents;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;

import static oracle.weblogic.kubernetes.TestConstants.OPERATOR_RELEASE_NAME;
import static oracle.weblogic.kubernetes.utils.ThreadSafeLogger.getLogger;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Helper class for Kubernetes Events checking.
 */
public class K8sEvents {

  private static final LoggingFacade logger = getLogger();

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
      logger.info("Verifying {0} event is logged by the operator in domain namespace {1}", reason, domainNamespace);
      try {
        List<CoreV1Event> events = Kubernetes.listNamespacedEvents(domainNamespace);
        for (CoreV1Event event : events) {
          if (event.getReason().equals(reason)
              && (isEqualOrAfter(timestamp, event))) {
            logger.info(Yaml.dump(event));
            verifyOperatorDetails(event, opNamespace, domainUid);
            //verify type
            logger.info("Verifying domain event type {0}", type);
            assertTrue(event.getType().equals(type));
            return true;
          }
        }
      } catch (ApiException ex) {
        Logger.getLogger(ItKubernetesEvents.class.getName()).log(Level.SEVERE, null, ex);
      }
      return false;
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
   * @param countBefore the count to check against
   */
  public static Callable<Boolean> checkDomainEventWithCount(
      String opNamespace, String domainNamespace, String domainUid, String reason,
      String type, OffsetDateTime timestamp, int countBefore) {
    return () -> {
      logger.info("Verifying {0} event is logged by the operator in domain namespace {1}", reason, domainNamespace);
      try {
        List<CoreV1Event> events = Kubernetes.listNamespacedEvents(domainNamespace);
        for (CoreV1Event event : events) {
          if (event.getReason().equals(reason)
              && (isEqualOrAfter(timestamp, event))) {
            logger.info(Yaml.dump(event));
            verifyOperatorDetails(event, opNamespace, domainUid);
            //verify type
            logger.info("Verifying domain event type {0}", type);
            assertTrue(event.getType().equals(type));
            int countAfter = getDomainEventCount(domainNamespace, domainUid, reason, "Normal");
            assertTrue(countAfter == countBefore + 1, "Event count doesn't match expected event count, "
                + "Count before is -> " + countBefore + " and count after is -> " + countAfter);
            return true;
          }
        }
      } catch (ApiException ex) {
        Logger.getLogger(ItKubernetesEvents.class.getName()).log(Level.SEVERE, null, ex);
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
      List<CoreV1Event> events = Kubernetes.listNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        Map<String, String> labels = event.getMetadata().getLabels();
        if (event.getReason().equals(reason)
                && event.getType().equals(type)
                && labels.containsKey("weblogic.createdByOperator")
                && labels.get("weblogic.domainUID").equals(domainUid)) {
          return event.getCount();
        }
      }
    } catch (ApiException ex) {
      Logger.getLogger(ItKubernetesEvents.class.getName()).log(Level.SEVERE, null, ex);
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
  public static int getEventCount(
      String domainNamespace, String domainUid, String reason, OffsetDateTime timestamp) {
    int count = 0;
    try {
      List<CoreV1Event> events = Kubernetes.listNamespacedEvents(domainNamespace);
      for (CoreV1Event event : events) {
        if (event.getReason().contains(reason)
            && (isEqualOrAfter(timestamp, event))) {
          logger.info(Yaml.dump(event));
          count++;
        }
      }
    } catch (ApiException ex) {
      Logger.getLogger(ItKubernetesEvents.class.getName()).log(Level.SEVERE, null, ex);
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
        Logger.getLogger(ItKubernetesEvents.class.getName()).log(Level.SEVERE, null, ex);
      }
      return false;
    };
  }

  private static boolean isEqualOrAfter(OffsetDateTime timestamp, CoreV1Event event) {
    return event.getLastTimestamp().isEqual(timestamp)
            || event.getLastTimestamp().isAfter(timestamp);
  }

  private static Boolean isEventLoggedOnce(String serverName, int count) {
    return count == 1 ? true : logErrorAndFail(serverName, count);
  }

  private static Boolean logErrorAndFail(String serverName, int count) {
    Logger.getLogger(ItKubernetesEvents.class.getName()).log(Level.SEVERE,
            "Pod " + serverName + " restarted " + count + " times");
    return false;
  }

  // Verify the operator instance details are correct
  private static void verifyOperatorDetails(
      CoreV1Event event, String opNamespace, String domainUid) throws ApiException {
    logger.info("Verifying operator details");
    String operatorPodName = TestActions.getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace);
    //verify DOMAIN_API_VERSION
    if (domainUid != null) {
      assertTrue(event.getInvolvedObject().getApiVersion().equals(TestConstants.DOMAIN_API_VERSION),
          "Expected " + TestConstants.DOMAIN_API_VERSION + " ,Got " + event.getInvolvedObject().getApiVersion());
    }
    //verify reporting component to be operator release
    assertTrue(event.getReportingComponent().equals("weblogic.operator"),
        "Didn't get reporting component as " + "weblogic.operator");
    //verify reporting instance to be operator instance
    assertTrue(event.getReportingInstance().equals(operatorPodName),
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


  public static final String DOMAIN_CREATED = "DomainCreated";
  public static final String DOMAIN_DELETED = "DomainDeleted";
  public static final String DOMAIN_CHANGED = "DomainChanged";
  public static final String DOMAIN_PROCESSING_STARTING = "DomainProcessingStarting";
  public static final String DOMAIN_PROCESSING_COMPLETED = "DomainProcessingCompleted";
  public static final String DOMAIN_PROCESSING_FAILED = "DomainProcessingFailed";
  public static final String DOMAIN_PROCESSING_RETRYING = "DomainProcessingRetrying";
  public static final String DOMAIN_PROCESSING_ABORTED = "DomainProcessingAborted";
  public static final String DOMAIN_VALIDATION_ERROR = "DomainValidationError";
  public static final String NAMESPACE_WATCHING_STARTED = "NamespaceWatchingStarted";
  public static final String NAMESPACE_WATCHING_STOPPED = "NamespaceWatchingStopped";
  public static final String POD_TERMINATED = "Killing";
  public static final String POD_STARTED = "Started";

}

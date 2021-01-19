// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Event;
import io.kubernetes.client.util.Yaml;
import oracle.weblogic.kubernetes.ItKubernetesEvents;
import oracle.weblogic.kubernetes.TestConstants;
import oracle.weblogic.kubernetes.actions.TestActions;
import oracle.weblogic.kubernetes.actions.impl.primitive.Kubernetes;
import oracle.weblogic.kubernetes.logging.LoggingFacade;
import org.joda.time.DateTime;

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
      String opNamespace, String domainNamespace, String domainUid, String reason, String type, DateTime timestamp) {
    return () -> {
      logger.info("Verifying {0} event is logged by the operator in domain namespace {1}", reason, domainNamespace);
      try {
        List<V1Event> events = Kubernetes.listNamespacedEvents(domainNamespace);
        for (V1Event event : events) {
          if (event.getReason().contains(reason)
              && (isEqualOrAfter(timestamp, event))) {
            logger.info(Yaml.dump(event));
            verifyOperatorDetails(event, opNamespace, domainUid);
            //verify reason
            logger.info("Verifying domain event {0}", reason);
            assertTrue(event.getReason().equals(reason));
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
   * Check if a given event is logged only once for the given pod.
   *
   * @param domainNamespace namespace in which the domain exists
   * @param serverName server pod name for which event is checked
   * @param reason event to check for Started, Killing etc
   * @param timestamp the timestamp after which to see events
   */
  public static Callable<Boolean> checkPodEventLoggedOnce(
          String domainNamespace, String serverName, String reason, DateTime timestamp) {
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

  private static boolean isEqualOrAfter(DateTime timestamp, V1Event event) {
    return event.getLastTimestamp().isEqual(timestamp.getMillis())
            || event.getLastTimestamp().isAfter(timestamp.getMillis());
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
  private static void verifyOperatorDetails(V1Event event, String opNamespace, String domainUid) throws ApiException {
    logger.info("Verifying operator details");
    String operatorPodName = TestActions.getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace);
    //verify DOMAIN_API_VERSION
    assertTrue(event.getInvolvedObject().getApiVersion().equals(TestConstants.DOMAIN_API_VERSION),
        "Expected " + TestConstants.DOMAIN_API_VERSION + " ,Got " + event.getInvolvedObject().getApiVersion());
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
    assertTrue(labels.containsKey("weblogic.domainUID")
        && labels.get("weblogic.domainUID").equals(domainUid));
  }


  public static final String DOMAIN_CREATED = "DomainCreated";
  public static final String DOMAIN_DELETED = "DomainDeleted";
  public static final String DOMAIN_CHANGED = "DomainChanged";
  public static final String DOMAIN_PROCESSING_STARTING = "DomainProcessingStarting";
  public static final String DOMAIN_PROCESSING_COMPLETED = "DomainProcessingCompleted";
  public static final String DOMAIN_PROCESSING_FAILED = "DomainProcessingFailed";
  public static final String DOMAIN_PROCESSING_RETRYING = "DomainProcessingRetrying";
  public static final String DOMAIN_PROCESSING_ABORTED = "DomainProcessingAborted";
  public static final String POD_TERMINATED = "Killing";
  public static final String POD_STARTED = "Started";

}

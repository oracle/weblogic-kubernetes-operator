// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

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
              && event.getMetadata().getCreationTimestamp().isAfter(timestamp.getMillis())) {
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

}

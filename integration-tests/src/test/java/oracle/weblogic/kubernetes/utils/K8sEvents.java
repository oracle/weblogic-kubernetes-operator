// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.weblogic.kubernetes.utils;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Event;
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

  /**
   * Checks if a given event is logged by the operator.
   *
   * @param opNamespace namespace in which the operator is running
   * @param domainNamespace namespace in which the domain exists
   * @param domainUid UID of the domain
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param type type of event, Normal of Warning
   */
  public static void checkDomainEvent(
      String opNamespace, String domainNamespace, String domainUid, String reason, String type) {
    LoggingFacade logger = getLogger();
    logger.info("Verifying {0} event is logged by the operator in domain namespace {1}", reason, domainNamespace);
    boolean eventLogged = false;
    try {
      List<V1Event> events = Kubernetes.listNamespacedEvents(domainNamespace);
      for (V1Event event : events) {
        if (event.getReason().contains(reason)) {
          verifyOperatorDetails(event, opNamespace, domainUid);
          //verify reason
          assertTrue(event.getReason().equals(reason));
          //verify messages
          assertTrue(event.getMessage().equals(getDomainEventMessage(reason, domainUid)));
          //verify type
          assertTrue(event.getType().equals(type));
          eventLogged = true;
        }
      }
      assertTrue(eventLogged);
    } catch (ApiException ex) {
      Logger.getLogger(ItKubernetesEvents.class.getName()).log(Level.SEVERE, null, ex);
    }
  }

  private static void verifyOperatorDetails(V1Event event, String opNamespace, String domainUid) throws ApiException {
    String operatorPodName = TestActions.getOperatorPodName(OPERATOR_RELEASE_NAME, opNamespace);
    //verify DOMAIN_API_VERSION
    assertTrue(event.getInvolvedObject().getApiVersion().equals(TestConstants.DOMAIN_API_VERSION));
    //verify reporting component to be operator release
    assertTrue(event.getReportingComponent().equals(OPERATOR_RELEASE_NAME));
    //verify reporting instance to be operator instance
    assertTrue(event.getReportingInstance().equals(operatorPodName));
    //verify the event was created by operator
    Map<String, String> labels = event.getMetadata().getLabels();
    assertTrue(labels.containsKey("weblogic.createdByOperator")
        && labels.get("weblogic.createdByOperator").equals("true"));
    //verify the domainUID matches
    assertTrue(labels.containsKey("weblogic.domainUID")
        && labels.get("weblogic.domainUID").equals(domainUid));
  }

  /**
   * Returns the description for different event types.
   *
   * @param reason event to check for Created, Changed, deleted, processing etc
   * @param domainUid UID of the domain
   * @return String containing the complete description
   */
  public static String getDomainEventMessage(String reason, String domainUid) {
    String message = "";
    switch (reason) {
      case DOMAIN_CREATED:
        message = "Domain resource " + domainUid + " was created";
        break;
      case DOMAIN_DELETED:
        message = "Domain resource " + domainUid + " was deleted";
        break;
      case DOMAIN_CHANGED:
        message = "Domain resource " + domainUid + " was changed";
        break;
      case DOMAIN_PROCESSING_STARTING:
        message = "Creating or updating Kubernetes presence for WebLogic Domain with UID " + domainUid;
        break;
      case DOMAIN_PROCESSING_COMPLETED:
        message = "Successfully completed processing domain resource " + domainUid;
        break;
      case DOMAIN_PROCESSING_FAILED:
        message = "Failed to process domain resource " + domainUid
            + " due to xxxx, the processing  will be retried if required";
        break;
      case DOMAIN_PROCESSING_RETRYING:
        message = "Retrying the processing of domain resource " + domainUid + " after one or more failed attempts";
        break;
      case DOMAIN_PROCESSING_ABORTED:
        message = "Aborting the processing of domain resource " + domainUid + " permanently due to: %s";
        break;
      default:
        message = "None matched";
    }
    return message;
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

// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in Operator events. */
public interface EventConstants {

  String DOMAIN_CHANGED_EVENT = "DomainChanged";
  String DOMAIN_CREATED_EVENT = "DomainCreated";
  String DOMAIN_DELETED_EVENT = "DomainDeleted";
  String DOMAIN_PROCESSING_STARTED_EVENT = "DomainProcessingStarted";
  String DOMAIN_PROCESSING_SUCCEEDED_EVENT = "DomainProcessingSucceeded";
  String DOMAIN_PROCESSING_FAILED_EVENT = "DomainProcessingFailed";
  String DOMAIN_PROCESSING_RETRYING_EVENT = "DomainProcessingRetrying";
  String DOMAIN_PROCESSING_ABORTED_EVENT = "DomainProcessingAborted";
  String EVENT_NORMAL = "Normal";
  String EVENT_WARNING = "Warning";
  String WEBLOGIC_OPERATOR_COMPONENT = "weblogic.operator";

  String DOMAIN_CREATED_PATTERN = "Domain resource %s was created";
  String DOMAIN_CHANGED_PATTERN = "Domain resource %s was changed";
  String DOMAIN_DELETED_PATTERN = "Domain resource %s was deleted";
  String DOMAIN_PROCESSING_STARTED_PATTERN =
      "Creating or updating Kubernetes presence for WebLogic Domain with UID %s";
  String DOMAIN_PROCESSING_SUCCEEDED_PATTERN =
      "Successfully completed processing domain resource %s";
  String DOMAIN_PROCESSING_FAILED_PATTERN
      = "Failed to complete processing domain resource %s due to: %s";
  String DOMAIN_PROCESSING_RETRYING_PATTERN
      = "Retrying the processing of domain resource %s after one or more failed attempts";
  String DOMAIN_PROCESSING_ABORTED_PATTERN
      = "Aborting the processing of domain resource %s permanently due to: %s";
  String DOMAIN_PROCESSING_FAILED_ACTION = "Check operator pod log and introspector pod log";
  String DOMAIN_PROCESSING_ABORTED_ACTION = "Check domain resource configuration";
}

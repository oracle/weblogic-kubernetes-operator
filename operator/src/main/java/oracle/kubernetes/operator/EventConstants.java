// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in Operator events. */
public interface EventConstants {

  String DOMAIN_CHANGED_EVENT = "DomainChanged";
  String DOMAIN_CREATED_EVENT = "DomainCreated";
  String DOMAIN_DELETED_EVENT = "DomainDeleted";
  String DOMAIN_PROCESSING_COMPLETED_EVENT = "DomainProcessingCompleted";
  String DOMAIN_AVAILABLE_EVENT = "DomainAvailable";
  String DOMAIN_COMPLETED_EVENT = "DomainCompleted";
  String DOMAIN_PROCESSING_FAILED_EVENT = "DomainProcessingFailed";
  String DOMAIN_PROCESSING_ABORTED_EVENT = "DomainProcessingAborted";
  String DOMAIN_ROLL_COMPLETED_EVENT = "DomainRollCompleted";
  String DOMAIN_ROLL_STARTING_EVENT = "DomainRollStarting";
  String DOMAIN_VALIDATION_ERROR_EVENT = "DomainValidationError";
  String POD_CYCLE_STARTING_EVENT = "PodCycleStarting";
  String EVENT_NORMAL = "Normal";
  String EVENT_WARNING = "Warning";
  String WEBLOGIC_OPERATOR_COMPONENT = "weblogic.operator";

  String DOMAIN_AVAILABLE_PATTERN = "Domain %s became available";
  String DOMAIN_CREATED_PATTERN = "Domain resource %s was created";
  String DOMAIN_CHANGED_PATTERN = "Domain resource %s was changed";
  String DOMAIN_COMPLETED_PATTERN = "Domain %s is completely ready";
  String DOMAIN_DELETED_PATTERN = "Domain resource %s was deleted";
  String DOMAIN_PROCESSING_COMPLETED_PATTERN =
      "Successfully completed processing domain resource %s";
  String DOMAIN_PROCESSING_FAILED_PATTERN
      = "Failed to complete processing domain resource %s due to: %s, the processing will be retried if needed";
  String DOMAIN_PROCESSING_ABORTED_PATTERN
      = "Aborting the processing of domain resource %s permanently due to: %s";
  String DOMAIN_VALIDATION_ERROR_PATTERN
      = "Validation error in domain resource %s: %s";
  String POD_CYCLE_STARTING_PATTERN = "Replacing pod %s because: %s";
  String NAMESPACE_WATCHING_STARTED_EVENT = "NamespaceWatchingStarted";
  String NAMESPACE_WATCHING_STARTED_PATTERN = "Started watching namespace %s";
  String NAMESPACE_WATCHING_STOPPED_EVENT = "NamespaceWatchingStopped";
  String NAMESPACE_WATCHING_STOPPED_PATTERN = "Stopped watching namespace %s";
  String EVENT_KIND_POD = "Pod";
  String EVENT_KIND_DOMAIN = "Domain";
  String EVENT_KIND_NAMESPACE = "Namespace";
  String START_MANAGING_NAMESPACE_EVENT = "StartManagingNamespace";
  String START_MANAGING_NAMESPACE_PATTERN = "Start managing namespace %s";
  String STOP_MANAGING_NAMESPACE_EVENT = "StopManagingNamespace";
  String STOP_MANAGING_NAMESPACE_PATTERN = "Stop managing namespace %s";
  String START_MANAGING_NAMESPACE_FAILED_EVENT = "StartManagingNamespaceFailed";
  String START_MANAGING_NAMESPACE_FAILED_PATTERN = "Start managing namespace %s failed due to an authorization error";
  String DOMAIN_ROLL_STARTING_PATTERN = "Rolling restart WebLogic server pods in domain %s because: %s";
  String DOMAIN_ROLL_COMPLETED_PATTERN = "Rolling restart of domain %s completed";
  String ROLL_REASON_DOMAIN_RESOURCE_CHANGED = "domain resource changed";
  String ROLL_REASON_WEBLOGIC_CONFIGURATION_CHANGED
      = "WebLogic domain configuration changed due to a Model in Image model update";
}

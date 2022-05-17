// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in Operator events. */
public interface EventConstants {

  String DOMAIN_CHANGED_EVENT = "Changed";
  String DOMAIN_CREATED_EVENT = "Created";
  String DOMAIN_DELETED_EVENT = "Deleted";
  String DOMAIN_AVAILABLE_EVENT = "Available";
  String DOMAIN_COMPLETED_EVENT = "Completed";
  String DOMAIN_FAILED_EVENT = "Failed";
  String CONVERSION_WEBHOOK_FAILED_EVENT = "ConversionWebhookFailed";
  String DOMAIN_ROLL_COMPLETED_EVENT = "RollCompleted";
  String DOMAIN_ROLL_STARTING_EVENT = "RollStarting";
  String POD_CYCLE_STARTING_EVENT = "PodCycleStarting";
  String DOMAIN_UNAVAILABLE_EVENT = "Unavailable";
  String DOMAIN_INCOMPLETE_EVENT = "Incomplete";
  String DOMAIN_FAILURE_RESOLVED_EVENT = "FailureResolved";
  String EVENT_NORMAL = "Normal";
  String EVENT_WARNING = "Warning";
  String WEBLOGIC_OPERATOR_COMPONENT = "weblogic.operator";
  String CONVERSION_WEBHOOK_COMPONENT = "weblogic.conversion.webhook";

  String NAMESPACE_WATCHING_STARTED_EVENT = "NamespaceWatchingStarted";
  String NAMESPACE_WATCHING_STOPPED_EVENT = "NamespaceWatchingStopped";
  String EVENT_KIND_POD = "Pod";
  String EVENT_KIND_DOMAIN = "Domain";
  String EVENT_KIND_NAMESPACE = "Namespace";
  String START_MANAGING_NAMESPACE_EVENT = "StartManagingNamespace";
  String STOP_MANAGING_NAMESPACE_EVENT = "StopManagingNamespace";
  String START_MANAGING_NAMESPACE_FAILED_EVENT = "StartManagingNamespaceFailed";
}

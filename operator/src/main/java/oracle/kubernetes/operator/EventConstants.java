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
  String ABORTED_ERROR_SUGGESTION = "The reported problem should be corrected, and the domain will not be retried "
      + "until the domain resource is updated.";
  String DOMAIN_INVALID_ERROR_SUGGESTION = "Update the domain resource to correct the validation error.";
  String TOPOLOGY_MISMATCH_ERROR_SUGGESTION
      = "Update the domain resource or change the WebLogic domain configuration to correct the error.";
  String KUBERNETES_ERROR_SUGGESTION = "";
  String SERVER_POD_ERROR_SUGGESTION = "";
  String REPLICAS_TOO_HIGH_ERROR_SUGGESTION = "Lower replicas in the domain resource, or increase the number "
      + "of WebLogic servers in the WebLogic domain configuration for the cluster.";
}

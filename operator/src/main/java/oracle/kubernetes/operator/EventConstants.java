// Copyright (c) 2020, 2021, Oracle and/or its affiliates.
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
  String DOMAIN_ROLL_COMPLETED_EVENT = "RollCompleted";
  String DOMAIN_ROLL_STARTING_EVENT = "RollStarting";
  String POD_CYCLE_STARTING_EVENT = "PodCycleStarting";
  String DOMAIN_UNAVAILABLE_EVENT = "Unavailable";
  String DOMAIN_INCOMPLETE_EVENT = "Incomplete";
  String DOMAIN_FAILURE_RESOLVED_EVENT = "FailureResolved";
  String EVENT_NORMAL = "Normal";
  String EVENT_WARNING = "Warning";
  String WEBLOGIC_OPERATOR_COMPONENT = "weblogic.operator";

  String DOMAIN_AVAILABLE_PATTERN = "Domain %s became available";
  String DOMAIN_CREATED_PATTERN = "Domain %s was created";
  String DOMAIN_CHANGED_PATTERN = "Domain %s was changed";
  String DOMAIN_COMPLETED_PATTERN = "Domain %s is completely ready";
  String DOMAIN_DELETED_PATTERN = "Domain %s was deleted";
  String DOMAIN_FAILED_PATTERN = "Domain %s failed due to %s: %s. %s";
  String DOMAIN_UNAVAILABLE_PATTERN
      = "Domain %s became unavailable because none of the servers is up although some of them are supposed to be up";
  String DOMAIN_INCOMPLETE_PATTERN
      = "Domain %s became incomplete because some of the servers is not up although they are supposed to be up";
  String DOMAIN_FAILURE_RESOLVED_PATTERN
      = "Domain %s encountered some failures before, and those failures have been resolved";
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
  String DOMAIN_INVALID_ERROR = "Domain validation error";
  String TOPOLOGY_MISMATCH_ERROR = "Domain resource and WebLogic domain configuration mismatch error";
  String INTROSPECTION_ERROR = "Introspection error";
  String KUBERNETES_ERROR = "Kubernetes Api call error";
  String SERVER_POD_ERROR = "Server pod error";
  String REPLICAS_TOO_HIGH_ERROR = "Replicas too high";
  String INTERNAL_ERROR = "Internal error";
  String ABORTED_ERROR = "Domain processing is aborted";
  String ABORTED_ERROR_SUGGESTION = "The domain will not be retried unless it is corrected.";
  String WILL_NOT_RETRY = ABORTED_ERROR_SUGGESTION;
  String WILL_RETRY_SECONDS = " Will retry in %s seconds.";
  String DOMAIN_INVALID_ERROR_SUGGESTION = "Update the domain resource to correct the validation error.";
  String TOPOLOGY_MISMATCH_ERROR_SUGGESTION
      = "Update the domain resource or change the WebLogic domain configuration to correct the error.";
  String KUBERNETES_ERROR_SUGGESTION = "";
  String SERVER_POD_ERROR_SUGGESTION = "";
  String REPLICAS_TOO_HIGH_ERROR_SUGGESTION
      = "Lower replicas in domain resource or increase MaxDynamicClusterSize in WebLogic domain configuration";
}

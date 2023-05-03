// Copyright (c) 2020, 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in Operator events. */
public interface EventConstants {

  String CLUSTER_AVAILABLE_EVENT = "ClusterAvailable";
  String CLUSTER_CHANGED_EVENT = "ClusterChanged";
  String CLUSTER_COMPLETED_EVENT = "ClusterCompleted";
  String CLUSTER_CREATED_EVENT = "ClusterCreated";
  String CLUSTER_DELETED_EVENT = "ClusterDeleted";
  String CLUSTER_INCOMPLETE_EVENT = "ClusterIncomplete";
  String CLUSTER_UNAVAILABLE_EVENT = "ClusterUnavailable";
  String DOMAIN_CHANGED_EVENT = "Changed";
  String DOMAIN_CREATED_EVENT = "Created";
  String DOMAIN_DELETED_EVENT = "Deleted";
  String DOMAIN_AVAILABLE_EVENT = "Available";
  String DOMAIN_COMPLETED_EVENT = "Completed";
  String DOMAIN_FAILED_EVENT = "Failed";
  String WEBHOOK_STARTUP_FAILED_EVENT = "WebhookStartupFailed";
  String CONVERSION_WEBHOOK_FAILED_EVENT = "DomainConversionFailed";
  String DOMAIN_ROLL_COMPLETED_EVENT = "RollCompleted";
  String DOMAIN_ROLL_STARTING_EVENT = "RollStarting";
  String POD_CYCLE_STARTING_EVENT = "PodCycleStarting";
  String DOMAIN_UNAVAILABLE_EVENT = "Unavailable";
  String DOMAIN_INCOMPLETE_EVENT = "Incomplete";
  String DOMAIN_FAILURE_RESOLVED_EVENT = "FailureResolved";
  String PERSISTENT_VOUME_CLAIM_BOUND_EVENT = "PersistentVolumeClaimBound";
  String EVENT_NORMAL = "Normal";
  String EVENT_WARNING = "Warning";
  String WEBLOGIC_OPERATOR_COMPONENT = "weblogic.operator";
  String OPERATOR_WEBHOOK_COMPONENT = "weblogic.operator.webhook";

  String NAMESPACE_WATCHING_STARTED_EVENT = "NamespaceWatchingStarted";
  String NAMESPACE_WATCHING_STOPPED_EVENT = "NamespaceWatchingStopped";
  String EVENT_KIND_POD = "Pod";
  String EVENT_KIND_CLUSTER = "Cluster";
  String EVENT_KIND_DOMAIN = "Domain";
  String EVENT_KIND_NAMESPACE = "Namespace";
  String START_MANAGING_NAMESPACE_EVENT = "StartManagingNamespace";
  String STOP_MANAGING_NAMESPACE_EVENT = "StopManagingNamespace";
  String START_MANAGING_NAMESPACE_FAILED_EVENT = "StartManagingNamespaceFailed";
}

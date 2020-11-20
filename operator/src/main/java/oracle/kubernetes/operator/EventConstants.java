// Copyright (c) 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in Operator events. */
public interface EventConstants {

  public static final String DOMAIN_CHANGED_EVENT = "DomainChanged";
  public static final String DOMAIN_CREATED_EVENT = "DomainCreated";
  public static final String DOMAIN_DELETED_EVENT = "DomainDeleted";
  public static final String DOMAIN_PROCESSING_STARTED_EVENT = "DomainProcessingStarted";
  public static final String DOMAIN_PROCESSING_SUCCEEDED_EVENT = "DomainProcessingSucceeded";
  public static final String DOMAIN_PROCESSING_FAILED_EVENT = "DomainProcessingFailed";
  public static final String DOMAIN_PROCESSING_RETRYING_EVENT = "DomainProcessingRetrying";
  public static final String DOMAIN_PROCESSING_ABORTED_EVENT = "DomainProcessingAborted";
  public static final String EVENT_NORMAL = "Normal";
  public static final String EVENT_WARNING = "Warning";
  public static final String WEBLOGIC_OPERATOR_COMPONENT = "weblogic.operator";

  public static final String DOMAIN_CREATED_PATTERN = "Domain resource %s was created";
  public static final String DOMAIN_CHANGED_PATTERN = "Domain resource %s was changed";
  public static final String DOMAIN_DELETED_PATTERN = "Domain resource %s was deleted";
  public static final String DOMAIN_PROCESSING_STARTED_PATTERN =
      "Creating or updating Kubernetes presence for WebLogic Domain with UID %s";
  public static final String DOMAIN_PROCESSING_SUCCEEDED_PATTERN =
      "Successfully completed processing domain resource %s";
  public static final String DOMAIN_PROCESSING_FAILED_PATTERN
      = "Failed to complete processing domain resource %s due to: %s";
  public static final String DOMAIN_PROCESSING_RETRYING_PATTERN
      = "Retrying the processing of domain resource %s after one or more failed attempts";
  public static final String DOMAIN_PROCESSING_ABORTED_PATTERN
      = "Aborting the processing of domain resource %s permanently due to: %s";
  String DOMAIN_PROCESSING_FAILED_ACTION = "Check operator pod log and introspector pod log";
  String DOMAIN_PROCESSING_ABORTED_ACTION = "Check domain resource configuration";
}

// Copyright (c) 2018, 2023, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in asynchronous processing. */
public interface ProcessingConstants {

  String MAIN_COMPONENT_NAME = "main";
  String DELEGATE_COMPONENT_NAME = "delegate";
  String DOMAIN_COMPONENT_NAME = "domain";
  String PODWATCHER_COMPONENT_NAME = "podWatcher";
  String JOBWATCHER_COMPONENT_NAME = "jobWatcher";
  String PVCWATCHER_COMPONENT_NAME = "pvcWatcher";

  /** key to an object of type WlsServerConfig. */
  String SERVER_SCAN = "serverScan";
  String ENVVARS = "envVars";

  String SERVER_NAME = "serverName";
  String CLUSTER_NAME = "clusterName";

  String SERVERS_TO_ROLL = "roll";

  String SCRIPT_CONFIG_MAP = "scriptConfigMap";
  String SERVER_STATE_MAP = "serverStateMap";
  String SERVER_HEALTH_MAP = "serverHealthMap";

  String DOMAIN_TOPOLOGY = "domainTopology";
  String JOB_POD = "jobPod";
  String JOB_POD_INTROSPECT_CONTAINER_TERMINATED = "JOB_POD_CONTAINER_TERMINATED";
  String JOB_POD_INTROSPECT_CONTAINER_TERMINATED_MARKER = "done";
  String JOB_POD_FLUENTD_CONTAINER_TERMINATED = "JOB_POD_FLUENTD_CONTAINER_TERMINATED";
  String DOMAIN_INTROSPECTOR_JOB = "domainIntrospectorJob";
  String DOMAIN_INTROSPECTOR_LOG_RESULT = "domainIntrospectorLogResult";
  String DOMAIN_INTROSPECT_REQUESTED = "domainIntrospectRequested";
  String REMAINING_SERVERS_HEALTH_TO_READ = "serverHealthRead";
  String MII_DYNAMIC_UPDATE = "miiDynamicUpdate";
  String MII_DYNAMIC_UPDATE_WDTROLLBACKFILE = "miiDynamicUpdateRollbackFile";
  String MII_DYNAMIC_UPDATE_SUCCESS = "0";
  String MII_DYNAMIC_UPDATE_RESTART_REQUIRED = "103";
  String DOMAIN_ROLL_START_EVENT_GENERATED = "domainRollStartEventGenerated";

  String DOMAIN_VALIDATION_ERRORS = "domainValidationErrors";
  String INTROSPECTOR_JOB_FAILURE_LOGGED = "introspectorJobFailureLogged";
  String INTROSPECTOR_JOB_FAILURE_THROWABLE = "introspectorJobFailureThrowable";

  String WAIT_FOR_POD_READY = "waitForPodReady";

  /** Key to an object of type MakeRightDomainOperation. */
  String MAKE_RIGHT_DOMAIN_OPERATION = "makeRightOp";

  /** Field selectors to filter the events the operator will watch. */
  String READINESS_PROBE_FAILURE_EVENT_FILTER =
      "reason=Unhealthy,type=Warning,involvedObject.fieldPath=spec.containers{weblogic-server}";

  String FATAL_INTROSPECTOR_ERROR = "FatalIntrospectorError";

  String FATAL_INTROSPECTOR_ERROR_MSG = "Stop introspection retry - Fatal Error: ";

  String FATAL_DOMAIN_INVALID_ERROR = "FatalDomainInvalidError";

  String OPERATOR_EVENT_LABEL_FILTER = LabelConstants.getCreatedByOperatorSelector();

  String WEBHOOK = "Webhook";

  String SHUTDOWN_WITH_HTTP_SUCCEEDED = "SHUTDOWN_WITH_HTTP_SUCCEEDED";
  String DOMAIN_INTROSPECTION_COMPLETE = "Domain introspection complete";
  String SKIP_STATUS_UPDATE = "skipStatusUpdate";
  String END_OF_PROCESSING = "lastStatusUpdate";
  String AUTHORIZATION_SOURCE = "AuthorizationSource";
  String PENDING = "Pending";
  String BOUND = "Bound";
  int PVC_WAIT_STATUS_UPDATE_COUNT = 6;
  long DEFAULT_JRF_INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS = 1800L;
  long DEFAULT_WLS_OR_RESTRICTED_JRF_INTROSPECTOR_JOB_ACTIVE_DEADLINE_SECONDS = 600L;
}

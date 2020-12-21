// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in asynchronous processing. */
public interface ProcessingConstants {

  String MAIN_COMPONENT_NAME = "main";
  String DOMAIN_COMPONENT_NAME = "domain";
  String FIBER_COMPONENT_NAME = "fiber";
  String PODWATCHER_COMPONENT_NAME = "podWatcher";
  String JOBWATCHER_COMPONENT_NAME = "jobWatcher";

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
  String JOB_POD_NAME = "jobPodName";
  String DOMAIN_INTROSPECTOR_JOB = "domainIntrospectorJob";
  String DOMAIN_INTROSPECTOR_LOG_RESULT = "domainIntrospectorLogResult";
  String DOMAIN_INTROSPECT_REQUESTED = "domainIntrospectRequested";
  String REMAINING_SERVERS_HEALTH_TO_READ = "serverHealthRead";
  String MII_DYNAMIC_UPDATE = "miiDynamicUpdate";
  String MII_DYNAMIC_UPDATE_WDTROLLBACKFILE = "miiDynamicUpdateRollBackFile";
  String MII_DYNAMIC_UPDATE_SUCCESS = "0";
  String MII_DYNAMIC_UPDATE_RESTART_REQUIRED = "103";
  String MII_DYNAMIC_UPDATE_UPDATES_CANCELED = "104";

  String ENCODED_CREDENTIALS = "encodedCredentials";
  String DOMAIN_VALIDATION_ERRORS = "domainValidationErrors";
  String INTROSPECTOR_JOB_FAILURE_LOGGED = "introspectorJobfailureLogged";
  String WAIT_FOR_POD_READY = "waitForPodReady";

  /** Key to an object of type MakeRightDomainOperation. */
  String MAKE_RIGHT_DOMAIN_OPERATION = "makeRightOp";

  /** Field selectors to filter the events the operator will watch. */
  String READINESS_PROBE_FAILURE_EVENT_FILTER =
      "reason=Unhealthy,type=Warning,involvedObject.fieldPath=spec.containers{weblogic-server}";

  String EVENT_TYPE = "eventType";
  String FATAL_INTROSPECTOR_ERROR = "FatalIntrospectorError";

  String EXCEEDED_INTROSPECTOR_MAX_RETRY_COUNT_ERROR_MSG = "Stop introspection retry - "
      + "exceeded configured domainPresenceFailureRetryMaxCount: "
      + DomainPresence.getDomainPresenceFailureRetryMaxCount()
      + " The domainPresenceFailureRetryMaxCount is an operator tuning parameter and can be controlled"
      + " by adding it to the weblogic-operator-cm configmap.";

  String FATAL_INTROSPECTOR_ERROR_MSG = "Stop introspection retry - MII Fatal Error: ";
}

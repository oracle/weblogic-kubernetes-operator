// Copyright (c) 2018, 2020, Oracle Corporation and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in asynchronous processing. */
public interface ProcessingConstants {

  public static final String MAIN_COMPONENT_NAME = "main";
  public static final String DOMAIN_COMPONENT_NAME = "domain";
  public static final String FIBER_COMPONENT_NAME = "fiber";
  public static final String PODWATCHER_COMPONENT_NAME = "podWatcher";

  public static final String SERVER_SCAN = "serverScan";
  public static final String ENVVARS = "envVars";

  public static final String SERVER_NAME = "serverName";
  public static final String CLUSTER_NAME = "clusterName";

  public static final String SERVERS_TO_ROLL = "roll";

  public static final String SCRIPT_CONFIG_MAP = "scriptConfigMap";
  public static final String SERVER_STATE_MAP = "serverStateMap";
  public static final String SERVER_HEALTH_MAP = "serverHealthMap";

  public static final String DOMAIN_TOPOLOGY = "domainTopology";
  public static final String JOB_POD_NAME = "jobPodName";
  public static final String DOMAIN_INTROSPECTOR_JOB = "domainIntrospectorJob";
  public static final String DOMAIN_INTROSPECTOR_LOG_RESULT = "domainIntrospectorLogResult";
  public static final String SIT_CONFIG_MAP = "sitConfigMap";
  public static final String DOMAIN_HASH = "domainHash";
  public static final String SECRETS_HASH = "secretsHash";
  public static final String DOMAIN_RESTART_VERSION = "weblogic.domainRestartVersion";
  public static final String DOMAIN_INTROSPECT_REQUESTED = "domainIntrospectRequested";
  public static final String DOMAIN_INPUTS_HASH = "weblogic.domainInputsHash";
  public static final String REMAINING_SERVERS_HEALTH_TO_READ = "serverHealthRead";

  public static final String ENCODED_CREDENTIALS = "encodedCredentials";
}

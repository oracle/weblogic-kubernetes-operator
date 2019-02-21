// Copyright 2018, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/** Constants used in asynchronous processing. */
public interface ProcessingConstants {

  public static final String MAIN_COMPONENT_NAME = "main";
  public static final String DOMAIN_COMPONENT_NAME = "domain";
  public static final String FIBER_COMPONENT_NAME = "fiber";
  public static final String PODWATCHER_COMPONENT_NAME = "podWatcher";

  public static final String PRINCIPAL = "principal";
  public static final String SERVER_SCAN = "serverScan";
  public static final String ENVVARS = "envVars";

  public static final String SERVER_NAME = "serverName";
  public static final String CLUSTER_NAME = "clusterName";
  public static final String NETWORK_ACCESS_POINT = "nap";

  public static final String SERVERS_TO_ROLL = "roll";

  public static final String SCRIPT_CONFIG_MAP = "scriptConfigMap";
  public static final String SERVER_STATE_MAP = "serverStateMap";
  public static final String SERVER_HEALTH_MAP = "serverHealthMap";

  public static final String STATUS_UNCHANGED = "statusUnchanged";

  public static final String DOMAIN_TOPOLOGY = "domainTopology";
  public static final String JOB_POD_NAME = "jobPodName";
  public static final String DOMAIN_INTROSPECTOR_JOB = "domainIntrospectorJob";
  public static final String DOMAIN_INTROSPECTOR_LOG_RESULT = "domainIntrospectorLogResult";
  public static final String SIT_CONFIG_MAP = "sitConfigMap";
}

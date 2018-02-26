// Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/**
 * Constants used in asynchronous processing
 * 
 */
public interface ProcessingConstants {

  public static final String DOMAIN_COMPONENT_NAME = "domain";
  public static final String FIBER_COMPONENT_NAME = "fiber";
  
  public static final String PRINCIPAL = "principal";
  public static final String SERVER_SCAN = "serverScan";
  public static final String CLUSTER_SCAN = "clusterScan";
  public static final String ENVVARS = "envVars";
  
  public static final String SERVER_NAME = "serverName";
  public static final String CLUSTER_NAME = "clusterName";
  public static final String PORT = "port";
  public static final String NODE_PORT = "nodePort";
  public static final String NETWORK_ACCESS_POINT = "nap";
  
  public static final String SERVERS_TO_ROLL = "roll";
  public static final String EXPLICIT_RESTART_ADMIN = "explicitRestartAdmin";
  public static final String EXPLICIT_RESTART_SERVERS = "explicitRestartServers";
  public static final String EXPLICIT_RESTART_CLUSTERS = "explicitRestartClusters";
  
}

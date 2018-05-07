// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/**
 * Startup Control constants representing the legal property values: "NONE", "ALL", "ADMIN",
 * "SPECIFIED", or "AUTO" These property values determine which WebLogic Servers the Operator will
 * start up when it discovers the Domain. - "NONE" will start up no servers, including not starting
 * the administration server. - "ALL" will start up all defined servers. - "ADMIN" will start up
 * only the AdminServer (no managed servers will be started). - "SPECIFIED" will start the
 * AdminServer and then will look at the "serverStartup" and "clusterStartup" entries to determine
 * which servers to start. - "AUTO" will start the servers as with "SPECIFIED" but then also start
 * servers from other clusters up to the replicas count.
 */
public interface StartupControlConstants {
  public static final String NONE_STARTUPCONTROL = "NONE";
  public static final String AUTO_STARTUPCONTROL = "AUTO";
  public static final String ADMIN_STARTUPCONTROL = "ADMIN";
  public static final String ALL_STARTUPCONTROL = "ALL";
  public static final String SPECIFIED_STARTUPCONTROL = "SPECIFIED";
}

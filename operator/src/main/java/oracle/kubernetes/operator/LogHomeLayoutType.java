// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

/**
 * Log home layout type determines how the log files are created under logHome.
 *   FLAT - all log files are under a single directory logHome.
 *   BY_SERVERS - introspector and domain log files are under root of logHome,
 *     server log files are under logHome/servers/{serverName}/logs
 */
public enum LogHomeLayoutType {
  FLAT,
  BY_SERVERS
}

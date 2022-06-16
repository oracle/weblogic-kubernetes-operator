// Copyright (c) 2022, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import com.google.gson.annotations.SerializedName;

/**
 * Log home layout type determines how the log files are created under logHome.
 *   Flat - all log files are under a single directory logHome.
 *   ByServers - introspector and domain log files are under root of logHome,
 *     server log files are under logHome/servers/{serverName}/logs
 */
public enum LogHomeLayoutType {
  @SerializedName("Flat")
  FLAT("Flat"),

  @SerializedName("ByServers")
  BY_SERVERS("ByServers");

  private final String value;

  LogHomeLayoutType(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return String.valueOf(this.value);
  }
}

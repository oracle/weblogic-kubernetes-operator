// Copyright (c) 2017, 2021, Oracle and/or its affiliates.
// Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public interface WebLogicConstants {
  String UNKNOWN_STATE = "UNKNOWN";
  String STARTING_STATE = "STARTING";
  String SHUTTING_DOWN_STATE = "SHUTTING_DOWN";
  String SHUTDOWN_STATE = "SHUTDOWN";
  String STANDBY_STATE = "STANDBY";
  String ADMIN_STATE = "ADMIN";
  String RESUMING_STATE = "RESUMING";
  String RUNNING_STATE = "RUNNING";
  String SUSPENDING_STATE = "SUSPENDING";
  String FORCE_SUSPENDING_STATE = "FORCE_SUSPENDING";

  Set<String> STATES_SUPPORTING_REST =
      new HashSet<>(
          Arrays.asList(
              STANDBY_STATE,
              ADMIN_STATE,
              RESUMING_STATE,
              RUNNING_STATE,
              SUSPENDING_STATE,
              FORCE_SUSPENDING_STATE));

  String READINESS_PROBE_NOT_READY_STATE = "Not ready: WebLogic Server state: ";
}

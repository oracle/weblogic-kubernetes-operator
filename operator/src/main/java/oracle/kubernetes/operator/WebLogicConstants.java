// Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
// Licensed under the Universal Permissive License v 1.0 as shown at
// http://oss.oracle.com/licenses/upl.

package oracle.kubernetes.operator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public interface WebLogicConstants {
  public static final String UNKNOWN_STATE = "UNKNOWN";
  public static final String SHUTDOWN_STATE = "SHUTDOWN";
  public static final String STANDBY_STATE = "STANDBY";
  public static final String ADMIN_STATE = "ADMIN";
  public static final String RESUMING_STATE = "RESUMING";
  public static final String RUNNING_STATE = "RUNNING";
  public static final String SUSPENDING_STATE = "SUSPENDING";
  public static final String FORCE_SUSPENDING_STATE = "FORCE_SUSPENDING";
  public static final String FAILED_NOT_RESTARTABLE_STATE = "FAILED_NOT_RESTARTABLE";

  public static final Set<String> STATES_SUPPORTING_REST =
      new HashSet<>(
          Arrays.asList(
              STANDBY_STATE,
              ADMIN_STATE,
              RESUMING_STATE,
              RUNNING_STATE,
              SUSPENDING_STATE,
              FORCE_SUSPENDING_STATE));

  public static final String READINESS_PROBE_NOT_READY_STATE = "Not ready: WebLogic Server state: ";
}

#!/bin/bash
# Copyright (c) 2017, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# if the livenessProbeSuccessOverride file is available, treat failures as success
#
RETVAL=$(test -f /operator/debug-config/livenessProbeSuccessOverride ; echo $?)

FILE=/deployment/.alive
AUTH_FAILURE_FILE=/deployment/.api-authentication-failure

exitWithFailureDetails() {
  if [ "$RETVAL" -ne 0 ] && [ -f "$AUTH_FAILURE_FILE" ]; then
    cat "$AUTH_FAILURE_FILE" >&2
  fi
  exit "$RETVAL"
}

if [ ! -f ${FILE} ]; then
  exitWithFailureDetails
fi
OLDTIME=60
CURTIME=$(date +%s)
FILETIME=$(stat $FILE -c %Y)
TIMEDIFF=$(expr $CURTIME - $FILETIME)

if [ $TIMEDIFF -gt $OLDTIME ]; then
  exitWithFailureDetails
fi
exit 0

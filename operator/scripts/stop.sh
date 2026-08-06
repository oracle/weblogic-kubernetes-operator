#!/bin/bash
# Copyright (c) 2022, 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

echo "Setting stop signal"

SHUTDOWN_MARKER_FILE="/deployment/marker.shutdown"
SHUTDOWN_COMPLETE_MARKER_FILE="/deployment/marker.shutdown-complete"
SHUTDOWN_RESTART_LIMIT_EXCEEDED_MARKER_FILE="/deployment/marker.shutdown-restart-limit-exceeded"

rm -f "${SHUTDOWN_COMPLETE_MARKER_FILE}"
rm -f "${SHUTDOWN_RESTART_LIMIT_EXCEEDED_MARKER_FILE}"
touch ${SHUTDOWN_MARKER_FILE}

while true; do
  if [ -e ${SHUTDOWN_COMPLETE_MARKER_FILE} ] ; then
    exit 0
  fi
  sleep 1
done

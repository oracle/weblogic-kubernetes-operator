#!/bin/bash
# Copyright (c) 2017, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

echo "Launching Oracle WebLogic Server Kubernetes Operator..."

# Relays SIGTERM to all java processes
relay_SIGTERM() {
  pid=`grep java /proc/[0-9]*/comm | awk -F / '{ print $3; }'`
  echo "Sending SIGTERM to java process " $pid
  kill -SIGTERM $pid
  exit 0
}

trap relay_SIGTERM SIGTERM

# Relays SIGKILL to all java processes
relay_SIGKILL() {
  pid=`grep java /proc/[0-9]*/comm | awk -F / '{ print $3; }'`
  echo "Sending SIGKILL to java process " $pid
  kill -SIGKILL $pid
  exit 0
}

trap relay_SIGKILL SIGKILL

exitMessage() {
  echo "Exiting container for the Oracle WebLogic Server Kubernetes Operator..."
}

trap exitMessage EXIT

/operator/initialize-external-operator-identity.sh

if [[ ! -z "$REMOTE_DEBUG_PORT" ]]; then
  DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$DEBUG_SUSPEND,address=*:$REMOTE_DEBUG_PORT"
  echo "DEBUG=$DEBUG"
else
  DEBUG=""
fi

if [ "${MOCK_WLS}" == 'true' ]; then
  MOCKING_WLS="-DmockWLS=true"
fi

# Start operator
java $JVM_OPTIONS $MOCKING_WLS $DEBUG -jar /operator/weblogic-kubernetes-operator.jar &
PID=$!
wait $PID

SHUTDOWN_COMPLETE_MARKER_FILE="/deployment/marker.shutdown-complete"

touch ${SHUTDOWN_COMPLETE_MARKER_FILE}

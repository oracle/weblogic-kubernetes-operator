#!/bin/bash
# Copyright (c) 2022, 2025, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

echo "Launching the Schema Conversion Webhook for Oracle WebLogic Server Kubernetes Operator..."

# Relays SIGTERM to all java processes
relay_SIGTERM() {
  pid=`grep java /proc/[0-9]*/comm | awk -F / '{ print $3; }'`
  echo "Sending SIGTERM to java process " $pid
  kill -SIGTERM $pid
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
  echo "Exiting container for the Schema Conversion Webhook for Oracle WebLogic Server Kubernetes Operator..."
}

trap exitMessage EXIT

if [[ ! -z "$REMOTE_DEBUG_PORT" ]]; then
  DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=$DEBUG_SUSPEND,address=*:$REMOTE_DEBUG_PORT"
  echo "DEBUG=$DEBUG"
else
  DEBUG=""
fi

# Container memory optimization flags
HEAP="-XshowSettings:vm"

# Start operator
java -cp /operator/weblogic-kubernetes-operator.jar $HEAP $MOCKING_WLS $DEBUG oracle.kubernetes.operator.WebhookMain &
PID=$!
wait $PID

SHUTDOWN_COMPLETE_MARKER_FILE="/deployment/marker.shutdown-complete"

touch ${SHUTDOWN_COMPLETE_MARKER_FILE}

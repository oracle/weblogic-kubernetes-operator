#!/bin/bash
# Copyright (c) 2017, 2026, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

echo "Launching Oracle WebLogic Server Kubernetes Operator..."

SHUTDOWN_MARKER_FILE="/deployment/marker.shutdown"
SHUTDOWN_COMPLETE_MARKER_FILE="/deployment/marker.shutdown-complete"
SHUTDOWN_RESTART_LIMIT_EXCEEDED_MARKER_FILE="/deployment/marker.shutdown-restart-limit-exceeded"
CONTAINER_RESTART_STATE_FILE="/deployment/container-restart-state"
CONTAINER_RESTART_WINDOW_SECONDS="${CONTAINER_RESTART_WINDOW_SECONDS:-1800}"
CONTAINER_RESTART_LIMIT="${CONTAINER_RESTART_LIMIT:-3}"

initialize_shutdown_markers() {
  local now window_start restart_count

  case "${CONTAINER_RESTART_WINDOW_SECONDS}" in
    ''|*[!0-9]*) CONTAINER_RESTART_WINDOW_SECONDS=1800 ;;
  esac
  case "${CONTAINER_RESTART_LIMIT}" in
    ''|*[!0-9]*) CONTAINER_RESTART_LIMIT=3 ;;
  esac

  now=$(date +%s)
  window_start="${now}"
  restart_count=0

  if [ -f "${CONTAINER_RESTART_STATE_FILE}" ]; then
    read -r window_start restart_count < "${CONTAINER_RESTART_STATE_FILE}"
    case "${window_start}" in
      ''|*[!0-9]*) window_start="${now}" ;;
    esac
    case "${restart_count}" in
      ''|*[!0-9]*) restart_count=0 ;;
    esac
    if [ $((now - window_start)) -gt "${CONTAINER_RESTART_WINDOW_SECONDS}" ]; then
      window_start="${now}"
      restart_count=0
    fi
  fi

  restart_count=$((restart_count + 1))
  printf '%s %s\n' "${window_start}" "${restart_count}" > "${CONTAINER_RESTART_STATE_FILE}"

  if [ "${restart_count}" -le "${CONTAINER_RESTART_LIMIT}" ]; then
    rm -f "${SHUTDOWN_MARKER_FILE}" "${SHUTDOWN_COMPLETE_MARKER_FILE}" \
      "${SHUTDOWN_RESTART_LIMIT_EXCEEDED_MARKER_FILE}"
  else
    printf 'container starts=%s, limit=%s, windowSeconds=%s\n' \
      "${restart_count}" "${CONTAINER_RESTART_LIMIT}" "${CONTAINER_RESTART_WINDOW_SECONDS}" \
      > "${SHUTDOWN_RESTART_LIMIT_EXCEEDED_MARKER_FILE}"
    echo "Not clearing shutdown markers after ${restart_count} container starts" \
      "in ${CONTAINER_RESTART_WINDOW_SECONDS} seconds"
  fi
}

initialize_shutdown_markers

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

if [ -f /operator/logging.sh ]; then
  . /operator/logging.sh
else
  . "$(dirname "$0")/logging.sh"
fi

if [ "${MOCK_WLS}" == 'true' ]; then
  MOCKING_WLS="-DmockWLS=true"
fi

configure_logging

# Start operator
java $JVM_OPTIONS $MOCKING_WLS $DEBUG $LOGGING -jar /operator/weblogic-kubernetes-operator.jar &
PID=$!
wait $PID

touch ${SHUTDOWN_COMPLETE_MARKER_FILE}

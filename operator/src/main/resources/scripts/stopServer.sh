#!/bin/bash

# Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script is used to attempt to gracefully shutdown a WL Server
# before its pod is deleted.  It requires the SERVER_NAME env var.
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1

# setup ".out" location for a WL server
serverLogHome="${LOG_HOME:-${DOMAIN_HOME}/servers/${SERVER_NAME}/logs}"
STOP_OUT_FILE="${serverLogHome}/${SERVER_NAME}.stop.out"
SHUTDOWN_MARKER_FILE="${serverLogHome}/${SERVER_NAME}.shutdown"
SERVER_PID_FILE="${serverLogHome}/${SERVER_NAME}.pid"


trace "Stop server ${SERVER_NAME}" &>> ${STOP_OUT_FILE}

checkEnv SERVER_NAME || exit 1

if [ "${MOCK_WLS}" == 'true' ]; then
  touch ${SHUTDOWN_MARKER_FILE}
  exit 0
fi

function check_for_shutdown() {
  [ ! -f "${SCRIPTPATH}/readState.sh" ] && trace SEVERE "Missing file '${SCRIPTPATH}/readState.sh'." && exit 1

  state=`${SCRIPTPATH}/readState.sh`
  exit_status=$?
  if [ $exit_status -ne 0 ]; then
    trace "Node manager not running or server instance not found; assuming shutdown" &>> ${STOP_OUT_FILE}
    return 0
  fi

  if [ "$state" = "SHUTDOWN" ]; then
    trace "Server is shutdown" &>> ${STOP_OUT_FILE}
    return 0
  fi

  if [[ "$state" =~ ^FAILED ]]; then
    trace "Server in failed state" &>> ${STOP_OUT_FILE}
    return 0
  fi

  trace "Server is currently in state $state" &>> ${STOP_OUT_FILE}
  return 1
}


# Check if the server is already shutdown
check_for_shutdown
[ $? -eq 0 ] && trace "Server already shutdown or failed" &>>  ${STOP_OUT_FILE} && exit 0

# Otherwise, connect to the node manager and stop the server instance
[ ! -f "${SCRIPTPATH}/wlst.sh" ] && trace SEVERE "Missing file '${SCRIPTPATH}/wlst.sh'." && exit 1

# Arguments for shutdown
export SHUTDOWN_PORT_ARG=${LOCAL_ADMIN_PORT:-${MANAGED_SERVER_PORT:-8001}}
export SHUTDOWN_PROTOCOL_ARG=${LOCAL_ADMIN_PROTOCOL:-t3}
export SHUTDOWN_TIMEOUT_ARG=${SHUTDOWN_TIMEOUT:-30}
export SHUTDOWN_IGNORE_SESSIONS_ARG=${SHUTDOWN_IGNORE_SESSIONS:-false}
export SHUTDOWN_TYPE_ARG=${SHUTDOWN_TYPE:-Graceful}

trace "Before stop-server.py [${SERVER_NAME}] ${SCRIPTDIR}" &>> ${STOP_OUT_FILE}
${SCRIPTPATH}/wlst.sh /weblogic-operator/scripts/stop-server.py &>> ${STOP_OUT_FILE}
trace "After stop-server.py" &>> ${STOP_OUT_FILE}

# at this point node manager should have terminated the server
# but let's try looking for the server process and
# kill the server if the process still exists,
# just in case we failed to stop it via wlst

# Adjust PATH if necessary before calling jps
adjustPath

pid=$(jps -v | grep " -Dweblogic.Name=${SERVER_NAME} " | awk '{print $1}')
if [ ! -z $pid ]; then
  echo "Killing the server process $pid" &>> ${STOP_OUT_FILE}
  kill -15 $pid
fi

touch ${SHUTDOWN_MARKER_FILE}

if [ -f ${SERVER_PID_FILE} ]; then
  kill -2 $(< ${SERVER_PID_FILE} )
fi

trace "Exit script"  &>> ${STOP_OUT_FILE}

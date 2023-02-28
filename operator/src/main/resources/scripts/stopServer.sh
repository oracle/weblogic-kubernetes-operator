#!/bin/bash

# Copyright (c) 2017, 2023, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script is used to attempt to gracefully shutdown a WL Server
# before its pod is deleted.  It requires the SERVER_NAME env var.
# It writes it's output to ${SERVER_NAME}.stop.out file in the LOG_HOME dir.
# STOP_OUT_FILE_MAX = max number of stop server .out files to keep around (default=11)
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1

# DEBUG
trace "Entering stopServer.sh" >> /proc/1/fd/1


# setup ".out" location for a WL server
serverLogHome="${LOG_HOME:-${DOMAIN_HOME}}"
if [ -z ${LOG_HOME_LAYOUT} ] || [ "BY_SERVERS" = ${LOG_HOME_LAYOUT} ] ; then
  serverLogHome="${serverLogHome}/servers/${SERVER_NAME}/logs"
fi
STOP_OUT_FILE="${serverLogHome}/${SERVER_NAME}.stop.out"
SHUTDOWN_MARKER_FILE="${serverLogHome}/${SERVER_NAME}.shutdown"
SERVER_PID_FILE="${serverLogHome}/${SERVER_NAME}.pid"

logFileRotate ${STOP_OUT_FILE} ${STOP_OUT_FILE_MAX:-11}


# DEBUG
trace "Location 1" >> /proc/1/fd/1


exit_script () {
  trace "Exit script" &>> ${STOP_OUT_FILE}

  # K8S does not print any logs in the PreStop stage in kubectl logs.
  # The workaround is to print script's output to the main process' stdout using /proc/1/fd/1
  # See https://github.com/kubernetes/kubernetes/issues/25766 for more details.
  trace "Stop Server: === Contents of the script output file ${STOP_OUT_FILE} ===" >> /proc/1/fd/1
  cat ${STOP_OUT_FILE} >> /proc/1/fd/1
  trace "Stop Server: === End of ${STOP_OUT_FILE} contents. ===" >> /proc/1/fd/1

  touch ${SHUTDOWN_MARKER_FILE}

  if [ -f ${SERVER_PID_FILE} ]; then
    kill -2 $(< ${SERVER_PID_FILE} )
  fi
}
trap exit_script EXIT

trace "Stop server ${SERVER_NAME}" &>> ${STOP_OUT_FILE}

checkEnv SERVER_NAME || exit 1

if [ "${MOCK_WLS}" == 'true' ]; then
  exit 0
fi

# DEBUG
trace "Location 2" >> /proc/1/fd/1


# Arguments for shutdown
export SHUTDOWN_PORT_ARG=${LOCAL_ADMIN_PORT:-${MANAGED_SERVER_PORT:-8001}}
export SHUTDOWN_PROTOCOL_ARG=${LOCAL_ADMIN_PROTOCOL:-t3}
export SHUTDOWN_IGNORE_SESSIONS_ARG=${SHUTDOWN_IGNORE_SESSIONS:-false}
export SHUTDOWN_WAIT_FOR_ALL_SESSIONS_ARG=${SHUTDOWN_WAIT_FOR_ALL_SESSIONS:-false}
export SHUTDOWN_TYPE_ARG=${SHUTDOWN_TYPE:-Graceful}
export SHUTDOWN_TIMEOUT_ARG=${SHUTDOWN_TIMEOUT:-30}

# Calculate the wait timeout to issue "kill -9" before the pod is destroyed. 
# Allow 3 seconds for the NFS v3 manager to detect the process destruction and release file locks.
export SIGKILL_WAIT_TIMEOUT=$(expr $SHUTDOWN_TIMEOUT_ARG - 3)
wait_and_kill_after_timeout () {
  trace "Wait up to ${SIGKILL_WAIT_TIMEOUT} seconds for ${SERVER_NAME} to shut down." >> ${STOP_OUT_FILE}
  sleep ${SIGKILL_WAIT_TIMEOUT}
  trace "The server ${SERVER_NAME} didn't shut down in ${SIGKILL_WAIT_TIMEOUT} seconds, " \
        "killing the server processes." >> ${STOP_OUT_FILE}
  # Adjust PATH if necessary before calling jps
  adjustPath

  kill -9 `jps -v | grep -v Jps | awk '{ print $1 }'`
  exit 0
}

# Wait for the timeout value to issue "kill -9" and then kill the WebLogic server processes.
wait_and_kill_after_timeout &

check_for_shutdown () {

  # DEBUG
  trace "Location 3" >> /proc/1/fd/1

  [ ! -f "${SCRIPTPATH}/readState.sh" ] && trace SEVERE "Missing file '${SCRIPTPATH}/readState.sh'." && exit 1

  state=$(${SCRIPTPATH}/readState.sh)
  exit_status=$?

  # DEBUG
  trace "Location 4" >> /proc/1/fd/1


  if [ $exit_status -ne 0 ]; then
    trace "Server instance not found; assuming shutdown"
    return 0
  fi

  if [ "$state" = "SHUTTING_DOWN" ]; then
    trace "Server is shutting shutdown"
    return 0
  fi

  if [ "$state" = "SHUTDOWN" ]; then
    trace "Server is shutdown"
    return 0
  fi

  if [[ "$state" =~ ^FAILED ]]; then
    trace "Server in failed state"
    return 0
  fi

  if [ -e /tmp/diefast ]; then
    trace "Found '/tmp/diefast' file; skipping clean shutdown"

    # Adjust PATH if necessary before calling jps
    adjustPath

    kill -9 `jps -v | grep " -Dweblogic.Name=${SERVER_NAME} " | awk '{ print $1 }'`
    return 0
  fi

  trace "Server is currently in state $state"
  return 1
} &>> ${STOP_OUT_FILE}


# DEBUG
trace "Location 5" >> /proc/1/fd/1


# Check if the server is already shutdown
check_for_shutdown
[ $? -eq 0 ] && trace "Server is already shutting down, is shutdown or failed" &>>  ${STOP_OUT_FILE} && exit 0


# DEBUG
trace "Location 6" >> /proc/1/fd/1


do_shutdown () {
  # Otherwise, connect to and stop the server instance
  [ ! -f "${SCRIPTPATH}/wlst.sh" ] && trace SEVERE "Missing file '${SCRIPTPATH}/wlst.sh'." && exit 1

  trace "Before stop-server.py [${SERVER_NAME}] ${SCRIPTDIR}"
  ${SCRIPTPATH}/wlst.sh /weblogic-operator/scripts/stop-server.py
  trace "After stop-server.py"

  # Adjust PATH if necessary before calling jps
  adjustPath

  pid=$(jps -v | grep " -Dweblogic.Name=${SERVER_NAME} " | awk '{print $1}')
  if [ ! -z $pid ]; then
    echo "Killing the server process $pid"
    kill -15 $pid
  fi
} &>> ${STOP_OUT_FILE}

# DEBUG
trace "Location 7" >> /proc/1/fd/1


do_shutdown

# DEBUG
trace "Location 8" >> /proc/1/fd/1

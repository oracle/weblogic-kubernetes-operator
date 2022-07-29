#!/bin/bash

# Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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

# setup ".out" location for a WL server
serverLogHome="${LOG_HOME:-${DOMAIN_HOME}}"
if [ -z ${LOG_HOME_LAYOUT} ] || [ "BY_SERVERS" = ${LOG_HOME_LAYOUT} ] ; then
  serverLogHome="${serverLogHome}/servers/${SERVER_NAME}/logs"
fi
STOP_OUT_FILE="${serverLogHome}/${SERVER_NAME}.stop.out"
SHUTDOWN_MARKER_FILE="${serverLogHome}/${SERVER_NAME}.shutdown"
SERVER_PID_FILE="${serverLogHome}/${SERVER_NAME}.pid"


logFileRotate ${STOP_OUT_FILE} ${STOP_OUT_FILE_MAX:-11}

trace "Stop server ${SERVER_NAME}" &> ${STOP_OUT_FILE}

checkEnv SERVER_NAME || exit 1

if [ "${MOCK_WLS}" == 'true' ]; then
  touch ${SHUTDOWN_MARKER_FILE}
  exit 0
fi

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
wait_and_kill_after_timeout(){
  trace "Wait for ${SIGKILL_WAIT_TIMEOUT} seconds for ${SERVER_NAME} to shut down." >> ${STOP_OUT_FILE}
  sleep ${SIGKILL_WAIT_TIMEOUT}
  trace "The server ${SERVER_NAME} didn't shut down in ${SIGKILL_WAIT_TIMEOUT} seconds, " \
        "killing the server processes." >> ${STOP_OUT_FILE}
  # Adjust PATH if necessary before calling jps
  adjustPath

  #Specifically killing the NM first as it can auto-restart a killed WL server.
  kill -9 `jps -v | grep " NodeManager " | awk '{ print $1 }'`
  kill -9 `jps -v | grep -v Jps | awk '{ print $1 }'`
  touch ${SHUTDOWN_MARKER_FILE}
}

# Wait for the timeout value to issue "kill -9" and then kill the WebLogic server processes.
wait_and_kill_after_timeout &

check_for_shutdown() {
  [ ! -f "${SCRIPTPATH}/readState.sh" ] && trace SEVERE "Missing file '${SCRIPTPATH}/readState.sh'." && exit 1

  state=`${SCRIPTPATH}/readState.sh`
  exit_status=$?
  if [ $exit_status -ne 0 ]; then
    trace "Node manager not running or server instance not found; assuming shutdown" &>> ${STOP_OUT_FILE}
    return 0
  fi

  if [ "$state" = "SHUTTING_DOWN" ]; then
    trace "Server is shutting shutdown" &>> ${STOP_OUT_FILE}
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

  if [ -e /tmp/diefast ]; then
    trace "Found '/tmp/diefast' file; skipping clean shutdown" &>> ${STOP_OUT_FILE}

    # Adjust PATH if necessary before calling jps
    adjustPath

    kill -9 `jps -v | grep " NodeManager " | awk '{ print $1 }'`
    kill -9 `jps -v | grep " -Dweblogic.Name=${SERVER_NAME} " | awk '{ print $1 }'`
    touch ${SHUTDOWN_MARKER_FILE}
    return 0
  fi

  trace "Server is currently in state $state" &>> ${STOP_OUT_FILE}
  return 1
}


# Check if the server is already shutdown
check_for_shutdown
[ $? -eq 0 ] && trace "Server is already shutting down, is shutdown or failed" &>>  ${STOP_OUT_FILE} && exit 0

# Otherwise, connect to the node manager and stop the server instance
[ ! -f "${SCRIPTPATH}/wlst.sh" ] && trace SEVERE "Missing file '${SCRIPTPATH}/wlst.sh'." && exit 1

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

# K8S does not print any logs in the PreStop stage in kubectl logs.
# The workaround is to print script's output to the main process' stdout using /proc/1/fd/1
# See https://github.com/kubernetes/kubernetes/issues/25766 for more details.
trace "Stop Server: === Contents of the script output file ${STOP_OUT_FILE} ===" > /proc/1/fd/1
cat ${STOP_OUT_FILE} >> /proc/1/fd/1
trace "Stop Server: === End of ${STOP_OUT_FILE} contents. ===" >> /proc/1/fd/1

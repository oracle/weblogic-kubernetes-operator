#!/bin/bash

# Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

#
# This script is used to attempt to gracefully shutdown a WL Server
# before its pod is deleted.  It requires the SERVER_NAME env var.
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/traceUtils.sh
[ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit 1

trace "Stop server ${SERVER_NAME}" &>> /u01/oracle/stopserver.out

checkEnv SERVER_NAME || exit 1

if [ "${MOCK_WLS}" == 'true' ]; then
  touch /u01/doShutdown
  exit 0
fi

function check_for_shutdown() {
  [ ! -f "${SCRIPTPATH}/readState.sh" ] && trace "Error: missing file '${SCRIPTPATH}/readState.sh'." && exit 1

  state=`${SCRIPTPATH}/readState.sh`
  exit_status=$?
  if [ $exit_status -ne 0 ]; then
    trace "Node manager not running or server instance not found; assuming shutdown" &>> /u01/oracle/stopserver.out
    return 0
  fi

  if [ "$state" = "SHUTDOWN" ]; then
    trace "Server is shutdown" &>> /u01/oracle/stopserver.out
    return 0
  fi

  if [[ "$state" =~ ^FAILED ]]; then
    trace "Server in failed state" &>> /u01/oracle/stopserver.out
    return 0
  fi

  trace "Server is currently in state $state" &>> /u01/oracle/stopserver.out
  return 1
}

# Check if the server is already shutdown
check_for_shutdown
[ $? -eq 0 ] && trace "Server already shutdown or failed" &>> /u01/oracle/stopserver.out && exit 0

# Otherwise, connect to the node manager and stop the server instance
[ ! -f "${SCRIPTPATH}/wlst.sh" ] && trace "Error: missing file '${SCRIPTPATH}/wlst.sh'." && exit 1

# Arguments for shutdown
SHUTDOWN_PORT=${LOCAL_ADMIN_PORT:-${MANAGED_SERVER_PORT:-8001}}
SHUTDOWN_PROTOCOL=${LOCAL_ADMIN_PROTOCOL:-t3}
SHUTDOWN_TIMEOUT=${SHUTDOWN_TIMEOUT:-30}
SHUTDOWN_IGNORE_SESSIONS=${SHUTDOWN_IGNORE_SESSIONS:-false}
SHUTDOWN_TYPE=${SHUTDOWN_TYPE:-Graceful}

trace "Before stop-server.py [${SERVER_NAME}] ${SCRIPTDIR}" &>> /u01/oracle/stopserver.out
${SCRIPTPATH}/wlst.sh /weblogic-operator/scripts/stop-server.py &>> /u01/oracle/stopserver.out
trace "After stop-server.py" &>> /u01/oracle/stopserver.out

# at this point node manager should have terminated the server
# but let's try looking for the server process and
# kill the server if the process still exists,
# just in case we failed to stop it via wlst
pid=$(jps -v | grep " -Dweblogic.Name=${SERVER_NAME} " | awk '{print $1}')
if [ ! -z $pid ]; then
  echo "Killing the server process $pid" &>> /u01/oracle/stopserver.out
  kill -15 $pid
fi

# stop node manager process
#
trace "Stopping NodeManager" &>> /u01/oracle/stopserver.out
pid=$(jps | grep "weblogic.NodeManager" | awk '{print $1}')
trace "PID=[${pid}]" &>> /u01/oracle/stopserver.out
if [ ! -z $pid ]; then
  echo "Killing NodeManager process $pid" &>> /u01/oracle/stopserver.out
  kill -9 $pid
fi

trace "Exit script"  &>> /u01/oracle/stopserver.out
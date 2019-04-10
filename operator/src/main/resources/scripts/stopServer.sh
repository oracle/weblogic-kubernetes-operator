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

trace "Stop server ${SERVER_NAME}"

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
    trace "Node manager not running or server instance not found; assuming shutdown"
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

  trace "Server is currently in state $state"
  return 1
}

# Check if the server is already shutdown
check_for_shutdown
[ $? -eq 0 ] && trace "Server already shutdown or failed" && exit 0

# Otherwise, connect to the node manager and stop the server instance
[ ! -f "${SCRIPTPATH}/wlst.sh" ] && trace "Error: missing file '${SCRIPTPATH}/wlst.sh'." && exit 1

# Arguments for shutdown
localAdminPort=${LOCAL_ADMIN_PORT:-${MANAGED_SERVER_PORT:-8001}}
localAdminProtocol=${LOCAL_ADMIN_PROTOCOL:-t3}
timeout=${SHUTDOWN_TIMEOUT:-30}
ignoreSessions=${SHUTDOWN_IGNORE_SESSIONS:-false}
force=${SHUTDOWN_FORCED:-true}

${SCRIPTPATH}/wlst.sh /weblogic-operator/scripts/stop-server.py $localAdminPort $localAdminProtocol $timeout $ignoreSessions $force

# Return status of 2 means failed to stop a server through the NodeManager.
# Look to see if there is a server process that can be killed.
if [ $? -eq 2 ]; then
  pid=$(jps -v | grep '[D]weblogic.Name=${SERVER_NAME}' | awk '{print $1}')
  if [ ! -z $pid ]; then
    trace "Killing the server process $pid"
    kill -15 $pid
  fi
fi

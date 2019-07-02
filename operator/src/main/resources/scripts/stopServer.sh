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

trace "Stop server ${SERVER_NAME}" &>> /weblogic-operator/stopserver.out

checkEnv SERVER_NAME || exit 1

if [ "${MOCK_WLS}" == 'true' ]; then
  touch /weblogic-operator/doShutdown
  exit 0
fi

function check_for_shutdown() {
  [ ! -f "${SCRIPTPATH}/readState.sh" ] && trace "Error: missing file '${SCRIPTPATH}/readState.sh'." && exit 1

  state=`${SCRIPTPATH}/readState.sh`
  exit_status=$?
  if [ $exit_status -ne 0 ]; then
    trace "Node manager not running or server instance not found; assuming shutdown" &>> /weblogic-operator/stopserver.out
    return 0
  fi

  if [ "$state" = "SHUTDOWN" ]; then
    trace "Server is shutdown" &>> /weblogic-operator/stopserver.out
    return 0
  fi

  if [[ "$state" =~ ^FAILED ]]; then
    trace "Server in failed state" &>> /weblogic-operator/stopserver.out
    return 0
  fi

  trace "Server is currently in state $state" &>> /weblogic-operator/stopserver.out
  return 1
}

# Check if the server is already shutdown
check_for_shutdown
[ $? -eq 0 ] && trace "Server already shutdown or failed" &>> /weblogic-operator/stopserver.out && exit 0

# Otherwise, connect to the node manager and stop the server instance
[ ! -f "${SCRIPTPATH}/wlst.sh" ] && trace "Error: missing file '${SCRIPTPATH}/wlst.sh'." && exit 1

# Arguments for shutdown
export SHUTDOWN_PORT_ARG=${LOCAL_ADMIN_PORT:-${MANAGED_SERVER_PORT:-8001}}
export SHUTDOWN_PROTOCOL_ARG=${LOCAL_ADMIN_PROTOCOL:-t3}
export SHUTDOWN_TIMEOUT_ARG=${SHUTDOWN_TIMEOUT:-30}
export SHUTDOWN_IGNORE_SESSIONS_ARG=${SHUTDOWN_IGNORE_SESSIONS:-false}
export SHUTDOWN_TYPE_ARG=${SHUTDOWN_TYPE:-Graceful}

trace "Before stop-server.py [${SERVER_NAME}] ${SCRIPTDIR}" &>> /weblogic-operator/stopserver.out
${SCRIPTPATH}/wlst.sh /weblogic-operator/scripts/stop-server.py &>> /weblogic-operator/stopserver.out
trace "After stop-server.py" &>> /weblogic-operator/stopserver.out

# at this point node manager should have terminated the server
# but let's try looking for the server process and
# kill the server if the process still exists,
# just in case we failed to stop it via wlst
pid=$(jps -v | grep " -Dweblogic.Name=${SERVER_NAME} " | awk '{print $1}')
if [ ! -z $pid ]; then
  echo "Killing the server process $pid" &>> /weblogic-operator/stopserver.out
  kill -15 $pid
fi

touch ${DOMAIN_HOME}/doShutdown
if [ -f /weblogic-operator/pid ]; then
  kill -2 $(</weblogic-operator/pid)
fi

trace "Exit script"  &>> /weblogic-operator/stopserver.out
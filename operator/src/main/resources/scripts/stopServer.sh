#!/bin/bash

# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
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

[ ! -f "${SCRIPTPATH}/wlst.sh" ]             && trace "Error: missing file '${SCRIPTPATH}/wlst.sh'."             && exit 1

trace "Before stop-server.py [${SERVER_NAME}] ${SCRIPTDIR}"  &>> /u01/oracle/stopserver.out
${SCRIPTPATH}/wlst.sh /weblogic-operator/scripts/stop-server.py  &>> /u01/oracle/stopserver.out
trace "After stop-server.py" &>> /u01/oracle/stopserver.out

# at this point node manager should have terminated the server
# but let's try looking for the server process and
# kill the server if the process still exists, 
# just in case NodeManager failed to stop it
pid=$(jps -v | grep "[D]weblogic.Name=${SERVER_NAME}" | awk '{print $1}')
if [ ! -z $pid ]; then
  echo "Killing the server process $pid"  &>> /u01/oracle/stopserver.out
  kill -15 $pid
fi

# stop node manager process
#
trace "Stopping NodeManager"  &>> /u01/oracle/stopserver.out
pid=$(jps | grep "NodeManager" | awk '{print $1}')
trace "PID=[${pid}]"  &>> /u01/oracle/stopserver.out
if [ ! -z $pid ]; then
  echo "Killing NodeManager process $pid"  &>> /u01/oracle/stopserver.out
  kill -15 $pid
fi

trace "Exit script"  &>> /u01/oracle/stopserver.out

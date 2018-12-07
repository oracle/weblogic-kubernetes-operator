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

trace "Stop server ${SERVER_NAME}"

checkEnv SERVER_NAME || exit 1

if [ "${MOCK_WLS}" == 'true' ]; then
  touch /u01/doShutdown
  exit 0
fi

[ ! -f "${SCRIPTPATH}/wlst.sh" ]             && trace "Error: missing file '${SCRIPTPATH}/wlst.sh'."             && exit 1

${SCRIPTDIR}/wlst.sh /weblogic-operator/scripts/stop-server.py 

# Return status of 2 means failed to stop a server through the NodeManager.
# Look to see if there is a server process that can be killed.
if [ $? -eq 2 ]; then
  pid=$(jps -v | grep '[D]weblogic.Name=${SERVER_NAME}' | awk '{print $1}')
  if [ ! -z $pid ]; then
    echo "Killing the server process $pid"
    kill -15 $pid
  fi
fi



#!/bin/bash

# Copyright 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

#
# This script is used to monitor the server log until the server is
# started successfully.
# It is separate from startServer.sh so that it is easier to quickly 
# kill the process running this script.
#

echo $$ > /tmp/monitorLog-pid

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/traceUtils.sh

trace "Monitoring server log file $1 with sleep interval of $2 seconds"

while true; do
  if grep -q "BEA-141335" $1 ; then
    msg=("WebLogic server failed to start due to missing or invalid"
         "situational configuration files, which may be due to invalid"
         "configOverride templates (these are specified via the"
         "configOverride attribute in the Domain custom resource)."
         "For details, please search your pod log or"
         "${SERVER_OUT_FILE} for the keyword 'situational'."
        )
    trace "${msg[*]}"
    exit 0
  fi
  if grep -q "BEA-000360" $1 ; then
    trace "WebLogic Server started successfully. Script exiting."
    exit 0
  fi
  sleep $2
done


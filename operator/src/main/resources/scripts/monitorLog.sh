#!/bin/bash

# Copyright (c) 2019, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

#
# This script is used to monitor the server log until the server is
# started successfully.
# It is separate from startServer.sh so that it is easier to quickly 
# kill the process running this script.
#

echo $$ > /tmp/monitorLog-pid

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1

trace "Monitoring server log file $1 every $2 seconds for selected known log messages."

while true; do
  if grep -q "BEA-141335" $1 ; then
    msg=("WebLogic server failed to start due to missing or invalid"
         "situational configuration files, which may be due to invalid"
         "configOverride templates (these are specified via the"
         "configOverride attribute in the Domain custom resource)."
         "For details, please search your pod log or"
         "${SERVER_OUT_FILE} for the keyword 'situational'."
        )
    trace SEVERE "${msg[*]}"
    exit 0
  fi
  if grep -q "BEA-000360" $1 ; then
    trace "WebLogic Server started successfully."
    exit 0
  fi
  sleep $2
done


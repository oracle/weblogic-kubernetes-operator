#!/bin/bash

# Copyright (c) 2017, 2020, Oracle Corporation and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# Reads the current state of a server. The script checks a WebLogic Server state
# file which is updated by the node manager.

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/utils.sh
[ $? -ne 0 ] && echo "[SEVERE] Missing file ${SCRIPTPATH}/utils.sh" && exit 1

# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed:
exportEffectiveDomainHome || exit 1

DN=${DOMAIN_NAME?}
SN=${SERVER_NAME?}
DH=${DOMAIN_HOME?}

STATEFILE=/${DH}/servers/${SN}/data/nodemanager/${SN}.state

# Adjust PATH if necessary before calling jps
adjustPath

if [ `jps -v | grep -c " -Dweblogic.Name=${SERVER_NAME} "` -eq 0 ]; then
  trace "WebLogic server process not found"
  exit 1
fi

if [ ! -f ${STATEFILE} ]; then
  trace "WebLogic Server state file not found."
  exit 2
fi

cat ${STATEFILE} | cut -f 1 -d ':'
exit 0

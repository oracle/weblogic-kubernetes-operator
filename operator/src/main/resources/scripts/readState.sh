#!/bin/bash

# Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

# Reads the current state of a server. The script checks a WebLogic Server state
# file which is updated by the node manager.

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/traceUtils.sh
[ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit 1

# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed:
exportEffectiveDomainHome || exit 1

DN=${DOMAIN_NAME?}
SN=${SERVER_NAME?}
DH=${DOMAIN_HOME?}

STATEFILE=/${DH}/servers/${SN}/data/nodemanager/${SN}.state

if [ `jps -l | grep -c " weblogic.NodeManager"` -eq 0 ]; then
  trace "Error: WebLogic NodeManager process not found."
  exit 1
fi

if [ ! -f ${STATEFILE} ]; then
  trace "Error: WebLogic Server state file not found."
  exit 2
fi

cat ${STATEFILE} | cut -f 1 -d ':'
exit 0

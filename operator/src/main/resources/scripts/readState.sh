#!/bin/bash

# Reads the current state of a server. The script checks a WebLogic Server state
# file which is updated by the node manager.

DN=${DOMAIN_NAME:-$1}
SN=${SERVER_NAME:-$2}
STATEFILE=/shared/domain/${DN}/servers/${SN}/data/nodemanager/${SN}.state

if [ `jps -l | grep -c " weblogic.NodeManager"` -eq 0 ]; then
  echo "Error: WebLogic NodeManager process not found."
  exit 1
fi

if [ ! -f ${STATEFILE} ]; then
  echo "Error: WebLogic Server state file not found."
  exit 2
fi

cat ${STATEFILE} | cut -f 1 -d ':'
exit 0

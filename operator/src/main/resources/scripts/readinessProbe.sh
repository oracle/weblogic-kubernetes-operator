#!/bin/bash

# Kubernetes periodically calls this readiness probe script to determine whether
# the pod should be included in load balancing. The script checks a WebLogic Server state
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

state=$(cat ${STATEFILE} | cut -f 1 -d ':')
if [ "$state" != "RUNNING" ]; then
  echo "Not ready: WebLogic Server state: ${state}"
  exit 3
fi
exit 0

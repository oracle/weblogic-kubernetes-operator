#!/bin/bash
# Kubernetes periodically calls this liveness probe script to determine whether
# the pod should be restarted. The script checks a WebLogic Server state file which
# is updated by the node manager.
DN=${DOMAIN_NAME:-$1}
SN=${SERVER_NAME:-$2}
STATEFILE=/shared/domain/${DN}/servers/${SN}/data/nodemanager/${SN}.state
if [ `jps -l | grep -c " weblogic.NodeManager"` -eq 0 ]; then
  echo "Error: WebLogic NodeManager process not found."
  exit 1
fi
if [ -f ${STATEFILE} ] && [ `grep -c "FAILED_NOT_RESTARTABLE" ${STATEFILE}` -eq 1 ]; then
  echo "Error: WebLogic Server state is FAILED_NOT_RESTARTABLE."
  exit 1
fi
exit 0

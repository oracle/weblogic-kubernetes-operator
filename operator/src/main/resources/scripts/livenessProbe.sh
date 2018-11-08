#!/bin/bash

# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

# Kubernetes periodically calls this liveness probe script to determine whether
# the pod should be restarted. The script checks a WebLogic Server state file which
# is updated by the node manager.

DN=${DOMAIN_NAME?}
SN=${SERVER_NAME?}
DH=${DOMAIN_HOME?}

STATEFILE=/${DH}/servers/${SN}/data/nodemanager/${SN}.state

# if the livenessProbeSuccessOverride file is available, treat failures as success
#
RETVAL=$(test -f /weblogic-operator/debug/livenessProbeSuccessOverride ; echo $?)

if [ `jps -l | grep -c " weblogic.NodeManager"` -eq 0 ]; then
  echo "Error: WebLogic NodeManager process not found."
  exit $RETVAL
fi
if [ -f ${STATEFILE} ] && [ `grep -c "FAILED_NOT_RESTARTABLE" ${STATEFILE}` -eq 1 ]; then
  echo "Error: WebLogic Server state is FAILED_NOT_RESTARTABLE."
  exit $RETVAL
fi
exit 0

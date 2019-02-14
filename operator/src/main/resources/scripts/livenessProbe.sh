#!/bin/bash

# Copyright 2017, 2019, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

# Kubernetes periodically calls this liveness probe script to determine whether
# the pod should be restarted. The script checks a WebLogic Server state file which
# is updated by the node manager.

# if the livenessProbeSuccessOverride file is available, treat failures as success:
RETVAL=$(test -f /weblogic-operator/debug/livenessProbeSuccessOverride ; echo $?)

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
source ${SCRIPTPATH}/traceUtils.sh
[ $? -ne 0 ] && echo "Error: missing file ${SCRIPTPATH}/traceUtils.sh" && exit $RETVAL

# check DOMAIN_HOME for a config/config.xml, reset DOMAIN_HOME if needed:
exportEffectiveDomainHome || exit $RETVAL

DN=${DOMAIN_NAME?}
SN=${SERVER_NAME?}
DH=${DOMAIN_HOME?}

STATEFILE=${DH}/servers/${SN}/data/nodemanager/${SN}.state

if [ "${MOCK_WLS}" != 'true' ]; then
  if [ `jps -l | grep -c " weblogic.NodeManager"` -eq 0 ]; then
    trace "Error: WebLogic NodeManager process not found."
    exit $RETVAL
  fi
fi
if [ -f ${STATEFILE} ] && [ `grep -c "FAILED_NOT_RESTARTABLE" ${STATEFILE}` -eq 1 ]; then
  trace "Error: WebLogic Server state is FAILED_NOT_RESTARTABLE."
  exit $RETVAL
fi
exit 0

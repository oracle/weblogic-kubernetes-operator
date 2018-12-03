#!/bin/bash

# Copyright 2017, 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at
# http://oss.oracle.com/licenses/upl.

# Kubernetes periodically calls this readiness probe script to determine whether
# the pod should be included in load balancing. The script checks a WebLogic Server state
# file which is updated by the node manager.

DN=${DOMAIN_NAME?}
SN=${SERVER_NAME?}
DH=${DOMAIN_HOME?}

if [ -z $MOCK_WLS ]; then
  exit 0
fi

STATEFILE=${DH}/servers/${SN}/data/nodemanager/${SN}.state

if [ "${MOCK_WLS}" != 'true' ]; then
  if [ `jps -l | grep -c " weblogic.NodeManager"` -eq 0 ]; then
    echo "Error: WebLogic NodeManager process not found."
    exit $RETVAL
  fi
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

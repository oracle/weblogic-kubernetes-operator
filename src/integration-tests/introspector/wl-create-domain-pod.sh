#!/bin/sh

# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This script runs within a wl-create-domain-pod.yamlt k8s pod and creates a WL domain.
#

while [ 1 -eq 1 ] ; do
  SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
  traceFile=${SCRIPTPATH}/traceUtils.sh
  source ${traceFile}
  [ $? -ne 0 ] && echo "Error: missing file ${traceFile}" && break

  trace "Creating domain home '${DOMAIN_HOME}' with log home '${LOG_HOME}'"

  [ -d "${DOMAIN_HOME}" ] && echo "Domain home already exists" && break

  mkdir -p `dirname ${DOMAIN_HOME}` || break

  [ -d "${LOG_HOME?}" ] || mkdir -p ${LOG_HOME} || break

  wlst.sh /test-scripts/wl-create-domain-pod.py || break

  echo "CREATE_DOMAIN_EXIT=0"

  # The pod's readiness probe checks for this file:
  touch /tmp/ready

  while [ 1 -eq 1 ] ; do
    sleep 10
  done
done

echo "CREATE_DOMAIN_EXIT=1"

while [ 1 -eq 1 ] ; do
  sleep 10
done

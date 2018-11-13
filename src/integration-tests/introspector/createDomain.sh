#!/bin/sh

# Copyright 2018, Oracle Corporation and/or its affiliates. All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

#
# This script runs within a wl-job.yamlt k8s job and creates a WL domain.
#

SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
traceFile=${SCRIPTPATH}/traceUtils.sh
source ${traceFile}
[ $? -ne 0 ] && echo "Error: missing file ${traceFile}" && exit 1

trace "Creating domain home '${DOMAIN_HOME}' with log home '${LOG_HOME}'"

[ -d "${DOMAIN_HOME?}" ] && echo "Domain home already exists" && exit 1

mkdir -p `dirname ${DOMAIN_HOME}` || exit 1

[ -d "${LOG_HOME?}" ] || mkdir -p ${LOG_HOME} || exit 1

wlst.sh /test-scripts/createDomain.py || exit 1

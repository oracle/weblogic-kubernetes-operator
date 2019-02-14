#!/bin/bash
# Copyright 2017, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

# if the livenessProbeSuccessOverride file is available, treat failures as success
#
RETVAL=$(test -f /operator/debug-config/livenessProbeSuccessOverride ; echo $?)

test `find /operator/.alive -mmin -1`

if(($?==0)); then
    exit 0
else 
    exit $RETVAL
fi




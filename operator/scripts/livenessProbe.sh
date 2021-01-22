#!/bin/bash
# Copyright (c) 2017, 2021, Oracle and/or its affiliates.
# Licensed under the Universal Permissive License v 1.0 as shown at https://oss.oracle.com/licenses/upl.

# if the livenessProbeSuccessOverride file is available, treat failures as success
#
RETVAL=$(test -f /operator/debug-config/livenessProbeSuccessOverride ; echo $?)

FILE=/operator/.alive
OLDTIME=60
CURTIME=$(date +%s)
FILETIME=$(stat $FILE -c %Y)
TIMEDIFF=$(expr $CURTIME - $FILETIME)

if [ $TIMEDIFF -gt $OLDTIME ]; then
  exit $RETVAL
fi
exit 0
